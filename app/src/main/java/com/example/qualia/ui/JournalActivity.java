package com.example.qualia.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;

import com.example.qualia.R;
import com.example.qualia.data.model.Attachment;
import com.example.qualia.data.model.JournalEntry;
import com.example.qualia.data.repository.JournalRepository;
import com.example.qualia.ui.journal.DraftStore;
import com.example.qualia.ui.journal.PageReleaseGesture;
import com.example.qualia.ui.journal.PolaroidImage;
import com.example.qualia.util.PrefsManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single-page journal screen.
 *
 * <p>The whole page is one warm cream paper surface. The {@link DrawingView}
 * sits on top of an {@link EditText} in z-order so drawings always overlay
 * typed text — the way pencil overlays writing on a real notebook page.
 *
 * <p><b>Gesture routing (no mode toggle).</b> {@link DrawingView} distinguishes
 * tap from drag at runtime. Drags become strokes; taps are forwarded down to
 * the EditText, which raises the keyboard and places the cursor. The user
 * never selects "type" or "draw" — they just type or draw.
 *
 * <p><b>Polaroid.</b> Tapping the camera icon launches the system camera. On
 * return, the photo is square-cropped, framed in white with a slightly taller
 * bottom strip, tilted a few degrees, given a subtle drop shadow, and animated
 * in with a "develop" fade (gray → desaturated → full colour). Stored as
 * {@link Attachment#TYPE_POLAROID}.
 *
 * <p><b>Save flow.</b> Text + any drawing strokes + any polaroids are persisted
 * atomically through {@link JournalRepository#insertWithAttachments}. The
 * drawing is saved as both a transparent PNG (fast preview) and a stroke JSON
 * (for replay animation in {@link EntryDetailActivity}).
 */
public class JournalActivity extends BaseActivity {

    /** Optional intent extra: id of an existing entry to re-open in write mode.
     *  When present (and {@code > 0}) the activity loads the existing text,
     *  drawing strokes, and polaroids onto the page; the save button overwrites
     *  the entry instead of inserting a new one. Edits are gated by the
     *  edit-window check in {@link EntryDetailActivity}. */
    public static final String EXTRA_EDIT_ENTRY_ID = "edit_entry_id";

    private static final String DRAWINGS_DIR  = "drawings";
    private static final String POLAROIDS_DIR = "polaroids";

    /** Brush sizes for the three palette presets. */
    private static final float BRUSH_THIN   = 6f;
    private static final float BRUSH_MEDIUM = 14f;
    private static final float BRUSH_THICK  = 24f;

    /** ChalkZone-illustration palette. Index aligns with swatch IDs. */
    private static final int[] PALETTE = {
            0xFFFAF6EE,    // warm white  — highlights, paper, foam
            0xFF2E7D4A,    // forest green
            0xFF6EA8D4,    // sky blue
            0xFF9A5B2E,    // sienna / brown
            0xFFF0C84C,    // sun yellow
            0xFFE06E35,    // orange-red
            0xFF7B4E82,    // plum
    };

    private DrawingView   drawingView;
    private EditText      editJournal;
    private FrameLayout   polaroidContainer;

    /** Currently selected swatch ID (R.id.swatch0..6 or R.id.swatchEraser). */
    private int selectedSwatchId = R.id.swatch1;

    /** A polaroid captured this session: the staging PNG plus its on-screen
     *  view so we can read final layout (position + tilt) at save time. */
    private static final class PendingPolaroid {
        final File      file;
        final ImageView view;
        final float     tilt;
        PendingPolaroid(File file, ImageView view, float tilt) {
            this.file = file; this.view = view; this.tilt = tilt;
        }
    }
    private final List<PendingPolaroid> pendingPolaroids = new ArrayList<>();

    /** Non-null when this activity was opened in edit mode (re-opening an
     *  existing entry that's still inside its edit window). The original
     *  entry's id and createdAt are preserved through save so the entry
     *  stays on the day it was first written. */
    private JournalEntry editingEntry;

    /** Polaroid PNG files captured (or restored from a draft) during this
     *  session that are NOT yet bound to a saved entry. "let it go" on the
     *  whole page deletes these so the disk doesn't accumulate orphans;
     *  "let it be" persists their positions in the draft so they reappear
     *  next time. Loaded-from-an-existing-entry polaroid files are NOT in
     *  this set — they belong to the entry, not the draft. */
    private final Set<File> sessionPolaroids = new HashSet<>();

    /** When non-null, the next camera capture replaces this polaroid in-place
     *  instead of adding a new one. Set by the per-polaroid "retake" action;
     *  cleared after the replacement (or on cancel). */
    private ImageView pendingReplaceTarget;

    /** Currently visible per-polaroid action chip ("retake · let go"), if any.
     *  At most one is on screen — tapping a different polaroid dismisses the
     *  previous chip first. */
    private View    polaroidActionChip;
    /** Auto-dismiss runnable for {@link #polaroidActionChip}. */
    private Runnable polaroidActionDismiss;

    /** Page frame we fade during the page-level "let go" hold. */
    private View          pageFrame;
    /** Bottom-of-screen exit-the-page captions. */
    private TextView      btnLetBe;
    private TextView      btnLetGo;
    /** Top-right save button (cached so we can re-style it on draft restore). */
    private TextView      btnSaveRef;

    /** Hold-to-fade gesture on the "let go" button. The flag, the animator,
     *  and the touch listener live inside this object now — the activity just
     *  supplies the commit callback. */
    private PageReleaseGesture pageReleaseGesture;

    /** How long the user must hold "let go" for the page (or a polaroid) to
     *  actually be released. Same constant for both flows so the gesture
     *  feels consistent across the app. Tune if device feedback says it's
     *  too quick or too slow. */
    private static final long LET_GO_HOLD_MS = 2500L;

    /** How long an action chip stays visible before auto-dismissing. */
    private static final long POLAROID_ACTION_TIMEOUT_MS = 3500L;

    /** Output URI for the in-flight camera intent, if any. */
    private Uri  pendingCameraUri;
    /** Staging file the camera writes into. Same file as pendingCameraUri. */
    private File pendingStagingFile;

    /** Camera capture launcher. We register before onResume so the launcher
     *  is ready by the time the user taps the camera icon. */
    private ActivityResultLauncher<Intent> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journal);

        PrefsManager prefs = new PrefsManager(this);

        drawingView       = findViewById(R.id.drawingView);
        editJournal       = findViewById(R.id.editJournal);
        polaroidContainer = findViewById(R.id.polaroidContainer);
        pageFrame         = findViewById(R.id.pageFrame);
        View timeTint     = findViewById(R.id.timeTint);

        TextView btnSave        = findViewById(R.id.btnSave);
        TextView btnBack        = findViewById(R.id.btnBack);
        TextView btnPastEntries = findViewById(R.id.btnPastEntries);
        TextView btnFade        = findViewById(R.id.btnFade);
        TextView btnUndo        = findViewById(R.id.btnUndo);
        TextView btnClear       = findViewById(R.id.btnClear);
        TextView btnCamera      = findViewById(R.id.btnCamera);
        btnLetBe                = findViewById(R.id.btnLetBe);
        btnLetGo                = findViewById(R.id.btnLetGo);
        btnSaveRef              = btnSave;

        // ── Time-of-day page tint ─────────────────────────────────────────────
        applyTimeOfDayTint(timeTint);

        // ── Camera capture launcher (must be registered in onCreate) ──────────
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK
                            && pendingStagingFile != null
                            && pendingStagingFile.exists()) {
                        onPolaroidCaptured(pendingStagingFile);
                    } else {
                        if (pendingStagingFile != null
                                && pendingStagingFile.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            pendingStagingFile.delete();
                        }
                        // User cancelled (or capture failed): drop any
                        // in-flight retake so the next capture isn't routed
                        // to the wrong polaroid.
                        pendingReplaceTarget = null;
                    }
                    pendingCameraUri   = null;
                    pendingStagingFile = null;
                });

        // ── Gesture routing: tap on the page = focus EditText, drag = draw ────
        // The rich handler places the cursor on whichever visual line the
        // user tapped — extending the text with blank lines if the tap was
        // below where they've typed. Still left-aligned single paragraph,
        // just "your cursor goes wherever you point on the page".
        drawingView.setTapForwardTarget(editJournal);
        drawingView.setTapHandler(this::placeCursorAt);

        // ── Restore last brush colour and size ────────────────────────────────
        int   lastColor = prefs.getLastColor();
        float lastSize  = prefs.getLastBrushSize();
        drawingView.setColor(lastColor);
        drawingView.setBrushSize(lastSize);

        // Match the selected swatch to the persisted colour.
        selectedSwatchId = swatchIdForColor(lastColor);

        // Initial focus on the page so the keyboard rises and the user can
        // start typing immediately. Drawing is one drag away.
        editJournal.requestFocus();

        // ── Palette: colour swatches ──────────────────────────────────────────
        int[] swatchIds = {
                R.id.swatch0, R.id.swatch1, R.id.swatch2, R.id.swatch3,
                R.id.swatch4, R.id.swatch5, R.id.swatch6,
        };
        for (int i = 0; i < swatchIds.length; i++) {
            final int color    = PALETTE[i];
            final int swatchId = swatchIds[i];
            findViewById(swatchId).setOnClickListener(v -> {
                drawingView.setColor(color);
                drawingView.setErasing(false);
                selectedSwatchId = swatchId;
                refreshSwatchSelection();
                prefs.setLastColor(color);
            });
        }

        // ── Palette: custom colour swatch ─────────────────────────────────────
        // Tap: if there's a remembered custom colour, just use it; otherwise
        //      open the picker. Long-press: always open the picker so the
        //      user can change their custom colour without losing the current.
        final View swatchCustom = findViewById(R.id.swatchCustom);
        applyCustomSwatchBackground(swatchCustom, prefs.getCustomColor());
        swatchCustom.setOnClickListener(v -> {
            int existing = prefs.getCustomColor();
            if (existing != 0) {
                drawingView.setColor(existing);
                drawingView.setErasing(false);
                selectedSwatchId = R.id.swatchCustom;
                refreshSwatchSelection();
                prefs.setLastColor(existing);
            } else {
                showColorPickerDialog(prefs, swatchCustom);
            }
        });
        swatchCustom.setOnLongClickListener(v -> {
            showColorPickerDialog(prefs, swatchCustom);
            return true;
        });

        // ── Palette: eraser as a swatch ───────────────────────────────────────
        findViewById(R.id.swatchEraser).setOnClickListener(v -> {
            drawingView.setErasing(true);
            selectedSwatchId = R.id.swatchEraser;
            refreshSwatchSelection();
        });

        // ── Brush size presets ────────────────────────────────────────────────
        View sizeSmall  = findViewById(R.id.sizeSmall);
        View sizeMedium = findViewById(R.id.sizeMedium);
        View sizeLarge  = findViewById(R.id.sizeLarge);

        // Default selection from prefs.
        int initialSizeId = pickSizeIdForBrush(lastSize);
        applySize(prefs, sizeSmall,  R.id.sizeSmall,  BRUSH_THIN,   initialSizeId == R.id.sizeSmall);
        applySize(prefs, sizeMedium, R.id.sizeMedium, BRUSH_MEDIUM, initialSizeId == R.id.sizeMedium);
        applySize(prefs, sizeLarge,  R.id.sizeLarge,  BRUSH_THICK,  initialSizeId == R.id.sizeLarge);

        // ── Undo / Clear ──────────────────────────────────────────────────────
        btnUndo .setOnClickListener(v -> drawingView.undo());
        btnClear.setOnClickListener(v -> drawingView.clear());

        // ── Camera ────────────────────────────────────────────────────────────
        btnCamera.setOnClickListener(v -> launchCamera());

        // ── Save / nav ────────────────────────────────────────────────────────
        btnSave.setOnClickListener(v -> save());
        // Back button = "let it be" by default: keep the page in scratch space
        // for next session if there's anything on it, otherwise just close.
        // The two intentional doors (let-be / let-go) live at the bottom of
        // the page so the user can pick deliberately.
        btnBack.setOnClickListener(v -> exitWithLetItBe());
        btnPastEntries.setOnClickListener(v -> {
            startActivity(new Intent(this, PastEntriesActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        // ── Three doors out (visible when there's anything on the page) ───────
        btnLetBe.setOnClickListener(v -> exitWithLetItBe());
        // Hold-to-fade. Gesture owns its own state — we just describe what
        // "committed" means (clean up scratch files and finish).
        pageReleaseGesture = new PageReleaseGesture(
                btnLetGo, pageFrame, LET_GO_HOLD_MS, this::onPageReleaseCommitted);
        pageReleaseGesture.attach();

        // Watch text changes so the doors appear once anything's on the page.
        editJournal.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged   (CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable e) { applyDoorVisibility(); }
        });
        // Drawing view doesn't expose a stroke listener, so re-evaluate on
        // every touch-up: cheap, runs on the UI thread, only updates when
        // visibility actually changes.
        drawingView.setOnStrokeFinished(this::applyDoorVisibility);

        // ── Fading toggle (governs both text and drawings on detail) ──────────
        updateFadeLabel(btnFade, prefs.isFadingJournal());
        btnFade.setOnClickListener(v -> {
            boolean now = !prefs.isFadingJournal();
            prefs.setFadingJournal(now);
            updateFadeLabel(btnFade, now);
        });

        refreshSwatchSelection();

        // ── Edit mode: re-open an existing entry for revision ─────────────────
        // Triggered from EntryDetailActivity when the entry was written today.
        // Loads text, strokes, and polaroids onto the page; save() overwrites.
        int editId = getIntent().getIntExtra(EXTRA_EDIT_ENTRY_ID, 0);
        if (editId > 0) {
            loadEntryForEditing(editId);
        } else {
            // Fresh-create mode: if the user tapped "let it be" last time,
            // restore that draft so the page picks up where they left off.
            DraftStore.Draft draft = new DraftStore(this).load();
            if (draft != null) {
                restoreDraft(draft);
            }
        }

        // Initial pass — drives door visibility based on current page state.
        applyDoorVisibility();

        // ── First-visit ghost-stroke demo ─────────────────────────────────
        // The drawing affordance on this page is invisible — there's no
        // "draw" button, no tooltip, no toggle. A new user has no way to
        // know they can drag to draw. We teach it diegetically: after a
        // few seconds of inactivity, a faint stroke appears on the page,
        // is "drawn" by an unseen hand, then dissolves. Shown once, ever,
        // and only on a fresh page (no existing strokes or draft). The
        // moment the user touches the page, the demo is cancelled.
        if (editId == 0 && !prefs.hasShownJournalDemo()) {
            drawingView.postDelayed(() -> {
                if (drawingView.hasStrokes()) {
                    // User already drew while we were waiting — they
                    // figured it out on their own. Skip the demo.
                    prefs.setJournalDemoShown();
                    return;
                }
                drawingView.playGhostStrokeDemo(null);
                prefs.setJournalDemoShown();
            }, 3500L);
        }

        // ── First-visit text-and-draw hint (v5) ───────────────────────
        // The page hides two behaviours behind the same surface: tap
        // raises the keyboard, drag becomes a stroke. The ghost-stroke
        // demo above teaches the drawing half. This line names both
        // halves at once so the user knows what they're looking at
        // before they touch it. Shown once, ever, and only on a fresh
        // page; the moment the user types or draws, we mark it shown
        // and let it fade out so nothing lingers next to the cursor.
        TextView txtJournalHint = findViewById(R.id.txtJournalHint);
        if (txtJournalHint != null
                && editId == 0
                && !prefs.hasShownJournalHint()) {
            // Mark immediately so a fast app-restart doesn't show it twice.
            prefs.setJournalHintShown();

            txtJournalHint.setVisibility(View.VISIBLE);
            txtJournalHint.setAlpha(0f);

            // Cancel-on-interaction: if the user starts writing or drawing
            // before the hint has finished fading in, dismiss it so we
            // never compete with the work they just started.
            Runnable fadeOutNow = () -> txtJournalHint.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction(() -> txtJournalHint.setVisibility(View.GONE))
                    .start();

            // Dismiss the hint the moment the user actually engages —
            // either by typing or by finishing a stroke. The page is
            // already doing what the hint described; the hint has no
            // further job. We deliberately don't install a raw touch
            // listener on the DrawingView (which would interfere with
            // its own gesture routing); the stroke-finished callback is
            // a clean shoulder-tap.
            editJournal.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                    if (txtJournalHint.getVisibility() == View.VISIBLE) fadeOutNow.run();
                }
                @Override public void afterTextChanged(android.text.Editable e) {}
            });
            Runnable existingStrokeCallback = this::applyDoorVisibility;
            drawingView.setOnStrokeFinished(() -> {
                existingStrokeCallback.run();
                if (txtJournalHint.getVisibility() == View.VISIBLE) fadeOutNow.run();
            });

            txtJournalHint.postDelayed(() -> txtJournalHint.animate()
                    .alpha(0.65f)
                    .setDuration(900)
                    .start(), 1200L);
            txtJournalHint.postDelayed(fadeOutNow, 7600L);
        }
    }

    // ── Save flow ──────────────────────────────────────────────────────────────

    private void save() {
        String text  = editJournal.getText().toString().trim();
        boolean drew = drawingView.hasStrokes();
        boolean hasPolaroids = !pendingPolaroids.isEmpty();
        if (text.isEmpty() && !drew && !hasPolaroids) return;

        List<Attachment> attachments = new ArrayList<>();

        if (drew) {
            String relPath = saveDrawingPng();
            if (relPath != null) {
                // Save stroke JSON next to the PNG so EntryDetailActivity can
                // replay the drawing. We don't care if this fails — the static
                // PNG is the canonical artifact.
                saveStrokeJson(relPath);
                attachments.add(new Attachment(
                        /*entryId=*/ 0,
                        Attachment.TYPE_CHALK,
                        relPath,
                        System.currentTimeMillis()));
            } else {
                Toast.makeText(this, "Couldn't save drawing.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        for (PendingPolaroid pp : pendingPolaroids) {
            String rel = POLAROIDS_DIR + "/" + pp.file.getName();
            // Save the on-page position as a sidecar JSON so EntryDetailActivity
            // can put the polaroid back where the user left it.
            savePolaroidMeta(rel, pp);
            attachments.add(new Attachment(
                    /*entryId=*/ 0,
                    Attachment.TYPE_POLAROID,
                    rel,
                    System.currentTimeMillis()));
        }

        JournalRepository repo = new JournalRepository(this);

        if (editingEntry != null) {
            // Edit-window save: keep the original id and createdAt so the
            // entry stays on the day it was first written. The repository
            // diffs the new attachment list against the old one and removes
            // any files (and sidecars) the user dropped during editing.
            editingEntry.text = text;
            repo.updateEntryWithAttachments(
                    editingEntry, attachments,
                    sameId -> runOnUiThread(() -> {
                        Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show();
                        finish();
                    }));
        } else {
            JournalEntry entry = new JournalEntry(text, System.currentTimeMillis());
            repo.insertWithAttachments(
                    entry, attachments,
                    newId -> runOnUiThread(() -> {
                        // Page is committed — the draft (if any) is no longer
                        // needed; clear it so the next blank page is blank.
                        new DraftStore(this).clear();
                        Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show();
                        finish();
                    }));
        }
    }

    // ── Three doors out: save (above), let-it-be, let-it-go ───────────────────

    /**
     * True when the page has anything on it the user might care about: text
     * (after trim), at least one drawing stroke, or at least one polaroid.
     * Drives the visibility of the let-be / let-go captions.
     */
    private boolean hasPageContent() {
        if (drawingView != null && drawingView.hasStrokes()) return true;
        if (!pendingPolaroids.isEmpty()) return true;
        if (editJournal == null) return false;
        return editJournal.getText().toString().trim().length() > 0;
    }

    /** Show/hide the bottom-of-page intent captions and re-style "save" so
     *  it dims when the page is blank. Cheap; safe to call freely. */
    private void applyDoorVisibility() {
        boolean has = hasPageContent();
        if (btnLetBe != null) btnLetBe.setVisibility(has ? View.VISIBLE : View.GONE);
        if (btnLetGo != null) btnLetGo.setVisibility(has ? View.VISIBLE : View.GONE);
        if (btnSaveRef != null) btnSaveRef.setAlpha(has ? 1f : 0.45f);
    }

    /** "Let it be" — keep the page in scratch space for next session and close.
     *  Polaroid files captured this session stay on disk so the draft can
     *  re-render them. If the page is blank, this is just a clean exit. */
    private void exitWithLetItBe() {
        // In edit mode the page already corresponds to a saved entry — there
        // is no draft semantics. Just close.
        if (editingEntry != null) { finish(); return; }

        DraftStore store = new DraftStore(this);
        if (!hasPageContent()) {
            // Nothing to keep. Make sure no stale draft lingers either.
            store.clear();
            finish();
            return;
        }
        store.save(buildDraftSnapshot());
        finish();
    }

    /** Builds a {@link DraftStore.Draft} from the current page state — text,
     *  drawing strokes, and any polaroids with their fractional positions
     *  and tilts. */
    private DraftStore.Draft buildDraftSnapshot() {
        DraftStore.Draft d = new DraftStore.Draft();
        d.text = editJournal.getText().toString();
        if (drawingView.hasStrokes()) {
            d.strokesJson = drawingView.strokesToJson();
        }
        int containerW = polaroidContainer.getWidth();
        int containerH = polaroidContainer.getHeight();
        for (PendingPolaroid pp : pendingPolaroids) {
            DraftStore.Polaroid p = new DraftStore.Polaroid();
            p.relPath = POLAROIDS_DIR + "/" + pp.file.getName();
            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) pp.view.getLayoutParams();
            if (containerW > 0 && containerH > 0) {
                p.fx = lp.leftMargin / (float) containerW;
                p.fy = lp.topMargin  / (float) containerH;
                p.fw = pp.view.getWidth() / (float) containerW;
            }
            p.tilt = pp.view.getRotation();
            d.polaroids.add(p);
        }
        return d;
    }

    /** Inverse of {@link #buildDraftSnapshot()}: text into the EditText, strokes
     *  into the DrawingView, polaroids back onto the page. Polaroid files
     *  restored here become part of {@link #sessionPolaroids} so a subsequent
     *  "let it go" cleans them up. */
    private void restoreDraft(DraftStore.Draft draft) {
        if (draft.text != null && !draft.text.isEmpty()) {
            editJournal.setText(draft.text);
            editJournal.setSelection(editJournal.getText().length());
        }
        if (draft.strokesJson != null && !draft.strokesJson.isEmpty()) {
            drawingView.setStrokesFromJson(draft.strokesJson);
        }
        for (DraftStore.Polaroid p : draft.polaroids) {
            File pngFile = new File(getFilesDir(), p.relPath);
            if (!pngFile.exists()) continue;
            sessionPolaroids.add(pngFile);
            displayPolaroidExisting(pngFile);
        }
    }

    // ── Page "let go" commit ──────────────────────────────────────────────────────────

    /** Called by {@link PageReleaseGesture} when the user has held "let go"
     *  long enough that the page has faded to fully transparent. Cleans up
     *  scratch files (polaroids captured this session that don't belong to
     *  a saved entry) and finishes the activity. */
    private void onPageReleaseCommitted() {
        // Delete files captured this session that are NOT bound to a saved
        // entry. Edit-mode polaroid files are owned by the entry — never
        // touched here; the user already had a chance to "let go" the entry
        // itself from the entry-detail screen.
        if (editingEntry == null) {
            for (File f : sessionPolaroids) {
                deletePolaroidWithSidecars(f);
            }
            sessionPolaroids.clear();
            new DraftStore(this).clear();
        }
        btnLetGo.postDelayed(this::finish, 200);
    }

    /** Deletes a polaroid PNG and its {@code .meta.json} sidecar. Quiet about
     *  failure — what matters is the user's intent, not strict success. */
    private void deletePolaroidWithSidecars(File pngFile) {
        if (pngFile == null) return;
        String path = pngFile.getAbsolutePath();
        int dot = path.lastIndexOf('.');
        String base = (dot > 0) ? path.substring(0, dot) : path;
        File meta = new File(base + ".meta.json");
        //noinspection ResultOfMethodCallIgnored
        if (pngFile.exists()) pngFile.delete();
        //noinspection ResultOfMethodCallIgnored
        if (meta.exists())    meta.delete();
    }

    private String saveDrawingPng() {
        try {
            Bitmap bmp = drawingView.getBitmap();
            File dir = new File(getFilesDir(), DRAWINGS_DIR);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            String name = "draw_" + System.currentTimeMillis() + ".png";
            File out = new File(dir, name);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            return DRAWINGS_DIR + "/" + name;
        } catch (Exception e) {
            return null;
        }
    }

    /** Saves the stroke list as JSON next to the PNG (same basename, .json). */
    private void saveStrokeJson(String pngRelPath) {
        String json = drawingView.strokesToJson();
        if (json == null) return;
        try {
            String basename = pngRelPath.substring(0, pngRelPath.lastIndexOf('.'));
            File out = new File(getFilesDir(), basename + ".json");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(json.getBytes("UTF-8"));
            }
        } catch (IOException ignored) {
            // PNG is the canonical artifact; missing JSON just means no replay.
        }
    }

    /** Persists a polaroid's on-page position (fractional, so it reflows on
     *  different screens) and its tilt as a {@code .meta.json} sidecar next
     *  to the PNG. {@link EntryDetailActivity} reads this back when rendering. */
    private void savePolaroidMeta(String pngRelPath, PendingPolaroid pp) {
        try {
            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) pp.view.getLayoutParams();
            int containerW = polaroidContainer.getWidth();
            int containerH = polaroidContainer.getHeight();
            if (containerW <= 0 || containerH <= 0) return;

            org.json.JSONObject doc = new org.json.JSONObject();
            doc.put("fx",   lp.leftMargin / (float) containerW);
            doc.put("fy",   lp.topMargin  / (float) containerH);
            doc.put("fw",   pp.view.getWidth()  / (float) containerW);
            doc.put("tilt", pp.view.getRotation());

            String basename = pngRelPath.substring(0, pngRelPath.lastIndexOf('.'));
            File out = new File(getFilesDir(), basename + ".meta.json");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(doc.toString().getBytes("UTF-8"));
            }
        } catch (Exception ignored) {
            // Without metadata, EntryDetailActivity falls back to defaults.
        }
    }

    // ── Polaroid capture ──────────────────────────────────────────────────────

    private void launchCamera() {
        try {
            File dir = new File(getFilesDir(), POLAROIDS_DIR);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File staging = new File(dir, "staging_" + System.currentTimeMillis() + ".jpg");
            //noinspection ResultOfMethodCallIgnored
            staging.createNewFile();

            String authority = getPackageName() + ".fileprovider";
            pendingStagingFile = staging;
            pendingCameraUri   = FileProvider.getUriForFile(this, authority, staging);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraLauncher.launch(intent);
        } catch (IOException e) {
            Toast.makeText(this, "Couldn't open camera.", Toast.LENGTH_SHORT).show();
            pendingCameraUri   = null;
            pendingStagingFile = null;
        }
    }

    private void onPolaroidCaptured(File staged) {
        try {
            Bitmap raw = PolaroidImage.decodeAndOrient(staged.getAbsolutePath());
            if (raw == null) {
                Toast.makeText(this, "Couldn't read photo.", Toast.LENGTH_SHORT).show();
                //noinspection ResultOfMethodCallIgnored
                staged.delete();
                pendingReplaceTarget = null;
                return;
            }

            Bitmap polaroid = PolaroidImage.composePolaroid(raw);
            File dir = new File(getFilesDir(), POLAROIDS_DIR);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File out = new File(dir, "poly_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                polaroid.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            if (pendingReplaceTarget != null) {
                replacePolaroidContent(pendingReplaceTarget, polaroid, out);
                pendingReplaceTarget = null;
            } else {
                displayPolaroidWithDevelopAnimation(polaroid, out);
            }

            //noinspection ResultOfMethodCallIgnored
            staged.delete();
        } catch (IOException e) {
            Toast.makeText(this, "Couldn't process photo.", Toast.LENGTH_SHORT).show();
            pendingReplaceTarget = null;
        }
    }

    /** Swaps the bitmap and backing file of an existing polaroid in place,
     *  preserving its position and tilt. The old PNG (and its meta sidecar)
     *  are deleted from disk so we don't leave orphans. The view briefly
     *  pulses to confirm the swap took. */
    private void replacePolaroidContent(ImageView iv, Bitmap newBitmap, File newPngFile) {
        // Find the existing PendingPolaroid for this view.
        PendingPolaroid old = null;
        for (PendingPolaroid pp : pendingPolaroids) {
            if (pp.view == iv) { old = pp; break; }
        }
        if (old == null) {
            // Shouldn't happen; treat as a fresh capture so the photo isn't
            // lost.
            displayPolaroidWithDevelopAnimation(newBitmap, newPngFile);
            return;
        }

        // Update the view first so the user sees the new photo immediately.
        iv.setImageBitmap(newBitmap);
        applyPivotAtPin(iv, newBitmap);

        // Swap registrations: replace the PendingPolaroid entry. If the old
        // file was a session-only orphan, delete it now (no entry references
        // it). If it was loaded from an existing entry's attachments, leave
        // the file alone — the save-time diff in updateEntryWithAttachments
        // is the safe place to delete entry-owned files.
        int idx = pendingPolaroids.indexOf(old);
        PendingPolaroid replaced = new PendingPolaroid(newPngFile, iv, old.tilt);
        pendingPolaroids.set(idx, replaced);
        if (sessionPolaroids.remove(old.file)) {
            deletePolaroidWithSidecars(old.file);
        }
        // Either way, the new file is session-owned until save() promotes it.
        sessionPolaroids.add(newPngFile);

        // Brief flash to confirm replacement.
        iv.setAlpha(0f);
        iv.animate().alpha(1f).setDuration(220).start();

        applyDoorVisibility();
    }

    // Image processing (decode + EXIF rotation, square-crop + frame + pin)
    // moved to com.example.qualia.ui.journal.PolaroidImage so this activity
    // doesn't have to carry several hundred lines of paint code that nothing
    // else in here touches.

    /** Adds the polaroid to the page with a develop animation (gray → faded
     *  colour → full colour, ~2.5s) plus a slight tilt. The polaroid pivots
     *  around the baked pin and is draggable around the page. The view is
     *  registered in {@code pendingPolaroids} so its final position can be
     *  persisted at save time. */
    private void displayPolaroidWithDevelopAnimation(Bitmap polaroid, File pngFile) {
        final ImageView iv = new ImageView(this);
        iv.setImageBitmap(polaroid);
        iv.setAdjustViewBounds(true);

        // Centre the polaroid horizontally using absolute margins so the user
        // can drag it without us needing to reconcile gravity with leftMargin.
        int containerW = Math.max(polaroidContainer.getWidth(),  (int) dp(360));
        int containerH = Math.max(polaroidContainer.getHeight(), (int) dp(560));
        int targetWidth = (int) (containerW * 0.62f);
        if (targetWidth <= 0) targetWidth = (int) dp(220);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        lp.leftMargin = (containerW - targetWidth) / 2;
        lp.topMargin  = (int) dp(40);
        iv.setLayoutParams(lp);

        // Slight natural tilt — pivot at the pin so the polaroid hangs from it.
        final int idx = polaroidContainer.getChildCount();
        final float tilt = ((idx * 37 + (int)(System.currentTimeMillis() % 7)) % 7) - 3f;
        applyPivotAtPin(iv, polaroid);
        iv.setRotation(tilt);

        polaroidContainer.addView(iv);
        pendingPolaroids.add(new PendingPolaroid(pngFile, iv, tilt));
        // Track this file as a session-only polaroid so "let it go" cleans
        // it up. Save promotes it to a real entry attachment instead.
        sessionPolaroids.add(pngFile);
        applyDoorVisibility();

        // Drag handler — moves the polaroid around the page. Keeps the polaroid
        // inside container bounds (with a margin so the pin stays visible).
        attachDragHandler(iv);

        // Develop animation: ColorMatrix saturation 0 → 1, alpha 0 → 1, scale
        // 0.96 → 1.0. ~2.5s, accelerate-decelerate to feel chemical.
        iv.setAlpha(0f);
        iv.setScaleX(0.96f); iv.setScaleY(0.96f);

        Paint p = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0f);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        iv.setLayerType(View.LAYER_TYPE_HARDWARE, p);

        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(2500);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.addUpdateListener(va -> {
            float t = (float) va.getAnimatedValue();
            iv.setAlpha(t);
            float scale = 0.96f + 0.04f * t;
            iv.setScaleX(scale); iv.setScaleY(scale);
            ColorMatrix m = new ColorMatrix();
            m.setSaturation(t);   // 0 = gray, 1 = full colour
            Paint paint = new Paint();
            paint.setColorFilter(new ColorMatrixColorFilter(m));
            iv.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
        });
        anim.start();
    }

    // ── Edit-mode loading ─────────────────────────────────────────────────────

    /** Re-opens an existing entry on the journal page so the user can revise
     *  it. Loads the body text, the drawing strokes (so additional strokes
     *  append to the existing artwork), and any polaroids — pre-positioned
     *  with their persisted tilt and location, fully developed (no animation).
     *  The save flow then routes through {@code updateEntryWithAttachments}. */
    private void loadEntryForEditing(int entryId) {
        JournalRepository repo = new JournalRepository(this);
        repo.getEntryById(entryId, entry -> {
            if (entry == null) return;
            runOnUiThread(() -> {
                editingEntry = entry;
                editJournal.setText(entry.text == null ? "" : entry.text);
                editJournal.setSelection(editJournal.getText().length());
            });
            repo.getAttachmentsForEntry(entryId, attachments -> runOnUiThread(() -> {
                if (attachments == null) return;
                for (Attachment a : attachments) {
                    File file = new File(getFilesDir(), a.filePath);
                    if (!file.exists()) continue;
                    if (Attachment.TYPE_CHALK.equals(a.type)) {
                        loadStrokesFromSidecar(file);
                    } else if (Attachment.TYPE_POLAROID.equals(a.type)) {
                        displayPolaroidExisting(file);
                    }
                }
            }));
        });
    }

    /** Reads the stroke-JSON sidecar next to a drawing PNG and pushes the
     *  strokes into the live DrawingView. The PNG itself is not used —
     *  {@code DrawingView} renders directly from the stroke list, so any
     *  further strokes the user adds compose with the loaded ones cleanly. */
    private void loadStrokesFromSidecar(File pngFile) {
        File jsonFile = sidecarJsonForDrawing(pngFile);
        if (!jsonFile.exists()) return;
        String json = readFileToString(jsonFile);
        if (json != null) {
            drawingView.setStrokesFromJson(json);
        }
    }

    /** Renders an existing polaroid onto the page in edit mode. Mirrors the
     *  positioning logic in {@code EntryDetailActivity.renderPolaroid} but
     *  also wires the drag handler and registers the polaroid in
     *  {@code pendingPolaroids} so the save flow rewrites its meta sidecar
     *  with whatever new position/tilt the user lands on. No develop
     *  animation — the photo was developed long ago. */
    private void displayPolaroidExisting(File pngFile) {
        final Bitmap bmp = BitmapFactory.decodeFile(pngFile.getAbsolutePath());
        if (bmp == null) return;

        final ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setAdjustViewBounds(true);

        final org.json.JSONObject meta = readPolaroidMeta(pngFile);

        polaroidContainer.post(() -> {
            int containerW = polaroidContainer.getWidth();
            int containerH = polaroidContainer.getHeight();

            int targetWidth;
            int leftMargin, topMargin;
            float tilt;

            if (meta != null && containerW > 0 && containerH > 0) {
                float fx = (float) meta.optDouble("fx",   -1);
                float fy = (float) meta.optDouble("fy",   -1);
                float fw = (float) meta.optDouble("fw",   0.62);
                float t  = (float) meta.optDouble("tilt", 0);
                targetWidth = Math.max(1, (int) (fw * containerW));
                leftMargin  = (fx >= 0)
                        ? (int) (fx * containerW)
                        : (containerW - targetWidth) / 2;
                topMargin   = (fy >= 0)
                        ? (int) (fy * containerH)
                        : (int) dp(40);
                tilt = t;
            } else {
                targetWidth = (int) (containerW * 0.62f);
                if (targetWidth <= 0) targetWidth = (int) dp(220);
                int idx = polaroidContainer.getChildCount();
                leftMargin = (containerW - targetWidth) / 2;
                topMargin  = (int) dp(40 + 60 * idx);
                tilt = ((idx * 37 + (int) (System.currentTimeMillis() % 7)) % 7) - 3f;
            }

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
            lp.leftMargin = leftMargin;
            lp.topMargin  = topMargin;
            iv.setLayoutParams(lp);

            applyPivotAtPin(iv, bmp);
            iv.setRotation(tilt);

            polaroidContainer.addView(iv);
            pendingPolaroids.add(new PendingPolaroid(pngFile, iv, tilt));
            attachDragHandler(iv);
            applyDoorVisibility();
        });
    }

    private File sidecarJsonForDrawing(File pngFile) {
        String path = pngFile.getAbsolutePath();
        int dot = path.lastIndexOf('.');
        String base = (dot > 0) ? path.substring(0, dot) : path;
        return new File(base + ".json");
    }

    private org.json.JSONObject readPolaroidMeta(File pngFile) {
        String path = pngFile.getAbsolutePath();
        int dot = path.lastIndexOf('.');
        String base = (dot > 0) ? path.substring(0, dot) : path;
        File metaFile = new File(base + ".meta.json");
        if (!metaFile.exists()) return null;
        String s = readFileToString(metaFile);
        if (s == null) return null;
        try {
            return new org.json.JSONObject(s);
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    private String readFileToString(File f) {
        try (java.io.FileInputStream is = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), "UTF-8");
        } catch (IOException e) {
            return null;
        }
    }

    /** Tap-anywhere cursor placement. Called when the user taps the journal
     *  page (i.e. didn't drag a stroke and didn't tap a polaroid). Places
     *  the EditText cursor on the visual line that was tapped, extending
     *  the text with blank lines if the tap is below the last typed line.
     *  Still left-aligned single paragraph — the text just gets longer. */
    private void placeCursorAt(float x, float y) {
        editJournal.requestFocus();

        android.text.Layout layout = editJournal.getLayout();
        int paddingTop  = editJournal.getPaddingTop();
        int paddingLeft = editJournal.getPaddingLeft();

        if (layout == null) {
            // Layout not built yet (empty editor first run). Just focus + show.
            showSoftInputOn(editJournal);
            return;
        }

        int lineCount      = layout.getLineCount();
        int lastLineBottom = layout.getLineBottom(lineCount - 1) + paddingTop;

        if (y <= lastLineBottom) {
            // Tap inside existing rendered text. Use the layout to find the
            // exact character offset under the tap.
            int line   = layout.getLineForVertical(
                    Math.max(0, (int) (y - paddingTop)));
            int offset = layout.getOffsetForHorizontal(
                    line, Math.max(0, x - paddingLeft));
            editJournal.setSelection(
                    Math.max(0, Math.min(editJournal.getText().length(), offset)));
        } else {
            // Tap below all current text. Extend with blank lines so the
            // cursor lands on the visual line under the user's finger.
            int   lineHeight = Math.max(1, editJournal.getLineHeight());
            int   linesBelow = (int) ((y - lastLineBottom) / lineHeight) + 1;
            StringBuilder pad = new StringBuilder();
            for (int i = 0; i < linesBelow; i++) pad.append('\n');
            editJournal.append(pad);
            editJournal.setSelection(editJournal.getText().length());
        }

        showSoftInputOn(editJournal);
    }

    private void showSoftInputOn(View v) {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(v, 0);
    }

    /** Sets the rotation pivot to the pin's location inside the ImageView so
     *  rotations look like the polaroid is hanging from the pin. */
    private void applyPivotAtPin(ImageView iv, Bitmap polaroid) {
        iv.post(() -> {
            int viewW = iv.getWidth();
            int viewH = iv.getHeight();
            float bmpW = polaroid.getWidth();
            float bmpH = polaroid.getHeight();
            if (viewW <= 0 || viewH <= 0 || bmpW <= 0 || bmpH <= 0) return;
            iv.setPivotX(viewW / 2f);
            iv.setPivotY(viewH * (PolaroidImage.PIN_Y_PX / bmpH));
        });
    }

    /** Per-polaroid drag handler with a press-and-hold gate.
     *
     *  <p>A quick tap reveals a small "retake · let go" action chip next to
     *  the polaroid (auto-dismisses after a few seconds). Press and hold for
     *  ~280ms and the polaroid "lifts" — a slight scale-up plus a haptic
     *  buzz — then follows the finger around the page until released. Moving
     *  more than touch-slop before the long-press fires cancels the arming.
     */
    private void attachDragHandler(final ImageView iv) {
        final int touchSlopPx =
                android.view.ViewConfiguration.get(this).getScaledTouchSlop();
        final long longPressMs = 280L;

        final float[]   downPt    = new float[2];
        final int[]     downMrg   = new int[2];
        final boolean[] armed     = { false };
        final boolean[] cancelled = { false };
        final long[]    downTime  = { 0L };
        final Runnable[] arm      = new Runnable[1];

        iv.setOnTouchListener((v, event) -> {
            FrameLayout.LayoutParams lp2 = (FrameLayout.LayoutParams) v.getLayoutParams();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downPt[0]    = event.getRawX();
                    downPt[1]    = event.getRawY();
                    downMrg[0]   = lp2.leftMargin;
                    downMrg[1]   = lp2.topMargin;
                    armed[0]     = false;
                    cancelled[0] = false;
                    downTime[0]  = System.currentTimeMillis();
                    arm[0] = () -> {
                        armed[0] = true;
                        v.bringToFront();
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        v.animate().scaleX(1.04f).scaleY(1.04f)
                                   .setDuration(120).start();
                        // Drag-arming dismisses any visible action chip — the
                        // user committed to moving, not editing.
                        hidePolaroidActions();
                    };
                    v.postDelayed(arm[0], longPressMs);
                    return true;

                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - downPt[0];
                    float dy = event.getRawY() - downPt[1];
                    if (!armed[0]) {
                        // Cancel arming if the user smudged before the long-press.
                        if (Math.hypot(dx, dy) > touchSlopPx) {
                            v.removeCallbacks(arm[0]);
                            cancelled[0] = true;
                        }
                        return true;
                    }
                    int newLeft = (int) (downMrg[0] + dx);
                    int newTop  = (int) (downMrg[1] + dy);
                    // Loosely clamp into the container so the user can't fling
                    // a polaroid completely out of reach.
                    int containerW = polaroidContainer.getWidth();
                    int containerH = polaroidContainer.getHeight();
                    int viewW      = v.getWidth();
                    int viewH      = v.getHeight();
                    int minLeft = -viewW / 2;
                    int maxLeft = containerW - viewW / 2;
                    int minTop  = -(int) dp(8);
                    int maxTop  = containerH - viewH / 2;
                    if (newLeft < minLeft) newLeft = minLeft;
                    if (newLeft > maxLeft) newLeft = maxLeft;
                    if (newTop  < minTop)  newTop  = minTop;
                    if (newTop  > maxTop)  newTop  = maxTop;
                    lp2.leftMargin = newLeft;
                    lp2.topMargin  = newTop;
                    v.setLayoutParams(lp2);
                    return true;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    v.removeCallbacks(arm[0]);
                    boolean wasArmed = armed[0];
                    if (wasArmed) {
                        v.animate().scaleX(1f).scaleY(1f)
                                   .setDuration(120).start();
                    }
                    armed[0] = false;

                    // Quick tap → reveal action chip. Conditions: never armed
                    // for drag, no large smudge, brief duration, and not a
                    // CANCEL event.
                    if (!wasArmed && !cancelled[0]
                            && event.getActionMasked() == MotionEvent.ACTION_UP
                            && System.currentTimeMillis() - downTime[0] < longPressMs) {
                        showPolaroidActions(iv);
                    }
                    return true;
                }
            }
            return false;
        });
    }

    // ── Per-polaroid actions: retake · let go ─────────────────────────────────

    /** Reveals a small caption chip ("retake · let go") just above a polaroid.
     *  At most one chip is on screen — calling this dismisses any prior one
     *  first. Auto-dismisses after {@link #POLAROID_ACTION_TIMEOUT_MS}. */
    private void showPolaroidActions(final ImageView iv) {
        hidePolaroidActions();

        // Resolve the polaroid's current top-left within the container.
        FrameLayout.LayoutParams ivLp = (FrameLayout.LayoutParams) iv.getLayoutParams();
        if (ivLp == null) return;

        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setBackgroundColor(0xCCFAF6EE);
        int padH = (int) dp(10);
        int padV = (int) dp(6);
        chip.setPadding(padH, padV, padH, padV);

        TextView retake = makeChipCaption("retake");
        TextView dot    = makeChipDot();
        TextView letGo  = makeChipCaption("let go");
        chip.addView(retake);
        chip.addView(dot);
        chip.addView(letGo);

        FrameLayout.LayoutParams chipLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        // Place the chip just above the polaroid, slightly indented so it
        // doesn't cover the pin.
        int chipTop = Math.max(0, ivLp.topMargin - (int) dp(34));
        chipLp.leftMargin = ivLp.leftMargin + (int) dp(20);
        chipLp.topMargin  = chipTop;
        chip.setLayoutParams(chipLp);
        chip.setAlpha(0f);

        polaroidContainer.addView(chip);
        chip.bringToFront();
        chip.animate().alpha(1f).setDuration(160).start();
        polaroidActionChip = chip;

        retake.setOnClickListener(v -> { hidePolaroidActions(); retakePolaroid(iv); });
        letGo.setOnTouchListener((v, event) -> handlePolaroidLetGoTouch(event, iv, chip));

        polaroidActionDismiss = () -> hidePolaroidActions();
        chip.postDelayed(polaroidActionDismiss, POLAROID_ACTION_TIMEOUT_MS);
    }

    private TextView makeChipCaption(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF8A765E);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tv.setLetterSpacing(0.18f);
        try { tv.setTypeface(androidx.core.content.res.ResourcesCompat
                .getFont(this, R.font.lora)); } catch (Exception ignored) {}
        int padH = (int) dp(8);
        int padV = (int) dp(2);
        tv.setPadding(padH, padV, padH, padV);
        return tv;
    }

    private TextView makeChipDot() {
        TextView dot = new TextView(this);
        dot.setText("·");
        dot.setTextColor(0xFFA89880);
        dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        return dot;
    }

    private void hidePolaroidActions() {
        if (polaroidActionChip == null) return;
        final View chip = polaroidActionChip;
        polaroidActionChip = null;
        if (polaroidActionDismiss != null) {
            chip.removeCallbacks(polaroidActionDismiss);
            polaroidActionDismiss = null;
        }
        chip.animate().alpha(0f).setDuration(140).withEndAction(() -> {
            ViewGroup parent = (ViewGroup) chip.getParent();
            if (parent != null) parent.removeView(chip);
        }).start();
    }

    private void retakePolaroid(ImageView iv) {
        pendingReplaceTarget = iv;
        launchCamera();
    }

    /** Hold-to-fade gesture on a single polaroid's "let go" caption. Mirrors
     *  the page-level let-go but scoped to one photo. Hold through →
     *  polaroid file deleted, view removed. Release early → polaroid pops
     *  back. */
    private boolean handlePolaroidLetGoTouch(MotionEvent event, ImageView iv, View chip) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                chip.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                final ValueAnimator anim = ValueAnimator.ofFloat(iv.getAlpha(), 0f);
                anim.setDuration(LET_GO_HOLD_MS);
                anim.addUpdateListener(va ->
                        iv.setAlpha((float) va.getAnimatedValue()));
                anim.addListener(new AnimatorListenerAdapter() {
                    private boolean cancelled;
                    @Override public void onAnimationCancel(Animator a) {
                        cancelled = true;
                    }
                    @Override public void onAnimationEnd(Animator a) {
                        if (cancelled) return;
                        commitPolaroidLetGo(iv);
                    }
                });
                iv.setTag(R.id.btnLetGo, anim);
                anim.start();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                Object o = iv.getTag(R.id.btnLetGo);
                if (o instanceof ValueAnimator) {
                    ValueAnimator anim = (ValueAnimator) o;
                    if (anim.isRunning()) {
                        anim.cancel();
                        iv.animate().alpha(1f).setDuration(220).start();
                    }
                    iv.setTag(R.id.btnLetGo, null);
                }
                hidePolaroidActions();
                return true;
            }
        }
        return false;
    }

    private void commitPolaroidLetGo(ImageView iv) {
        // Remove from the active set + container, delete files (only if this
        // polaroid was captured this session — entry-owned polaroids have
        // their files deleted later via the save-time diff in the repo).
        PendingPolaroid target = null;
        for (PendingPolaroid pp : pendingPolaroids) {
            if (pp.view == iv) { target = pp; break; }
        }
        if (target != null) {
            pendingPolaroids.remove(target);
            if (sessionPolaroids.remove(target.file)) {
                deletePolaroidWithSidecars(target.file);
            }
        }
        ViewGroup parent = (ViewGroup) iv.getParent();
        if (parent != null) parent.removeView(iv);
        hidePolaroidActions();
        applyDoorVisibility();
    }

    // ── Palette helpers ────────────────────────────────────────────────────────

    private void refreshSwatchSelection() {
        int[] all = {
                R.id.swatch0, R.id.swatch1, R.id.swatch2, R.id.swatch3,
                R.id.swatch4, R.id.swatch5, R.id.swatch6,
                R.id.swatchCustom, R.id.swatchEraser,
        };
        for (int id : all) {
            View v = findViewById(id);
            if (v == null) continue;
            boolean selected = (id == selectedSwatchId);
            v.setAlpha(selected ? 1f : 0.45f);
            v.setScaleX(selected ? 1.25f : 1f);
            v.setScaleY(selected ? 1.25f : 1f);
        }
    }

    private int swatchIdForColor(int color) {
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == color) {
                switch (i) {
                    case 0: return R.id.swatch0;
                    case 1: return R.id.swatch1;
                    case 2: return R.id.swatch2;
                    case 3: return R.id.swatch3;
                    case 4: return R.id.swatch4;
                    case 5: return R.id.swatch5;
                    case 6: return R.id.swatch6;
                }
            }
        }
        // The persisted last colour might be the user's custom pick.
        int custom = new PrefsManager(this).getCustomColor();
        if (custom != 0 && custom == color) return R.id.swatchCustom;
        return R.id.swatch1;   // fall back to forest green
    }

    /** Sets the custom-swatch background to a solid colour once the user has
     *  picked one. Before that, the swatch keeps the rainbow drawable so it
     *  reads as "opens the picker". */
    private void applyCustomSwatchBackground(View swatch, int color) {
        if (color == 0) {
            swatch.setBackgroundResource(R.drawable.custom_swatch);
            return;
        }
        android.graphics.drawable.GradientDrawable gd =
                new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setColor(color);
        gd.setStroke((int) dp(1), 0xFFA89880);
        swatch.setBackground(gd);
    }

    /** Opens the HSV picker. The chosen colour becomes the active brush AND
     *  is persisted as the user's custom colour, lighting up the "+" swatch
     *  for one-tap re-pick. */
    private void showColorPickerDialog(PrefsManager prefs, View swatchCustom) {
        android.view.LayoutInflater inflater = getLayoutInflater();
        View content = inflater.inflate(R.layout.dialog_color_picker, null);
        ColorPickerView picker = content.findViewById(R.id.colorPicker);
        final View preview     = content.findViewById(R.id.colorPreview);
        TextView btnDone       = content.findViewById(R.id.btnPickerDone);
        TextView btnCancel     = content.findViewById(R.id.btnPickerCancel);

        int initial = prefs.getCustomColor();
        if (initial == 0) initial = drawingView.getCurrentColor();
        picker.setColor(initial);
        preview.setBackgroundColor(initial);

        picker.setOnColorChangedListener(c -> preview.setBackgroundColor(c));

        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setView(content)
                .setCancelable(true)
                .create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(0));
        }

        btnCancel.setOnClickListener(v -> dlg.dismiss());
        btnDone  .setOnClickListener(v -> {
            int picked = picker.getColor();
            prefs.setCustomColor(picked);
            prefs.setLastColor(picked);
            drawingView.setColor(picked);
            drawingView.setErasing(false);
            selectedSwatchId = R.id.swatchCustom;
            applyCustomSwatchBackground(swatchCustom, picked);
            refreshSwatchSelection();
            dlg.dismiss();
        });

        dlg.show();
    }

    private int pickSizeIdForBrush(float size) {
        if (size <= (BRUSH_THIN + BRUSH_MEDIUM) / 2f) return R.id.sizeSmall;
        if (size <= (BRUSH_MEDIUM + BRUSH_THICK) / 2f) return R.id.sizeMedium;
        return R.id.sizeLarge;
    }

    private void applySize(PrefsManager prefs, View dot, int dotId,
                           float brushSize, boolean initiallySelected) {
        dot.setAlpha(initiallySelected ? 1f : 0.45f);
        dot.setOnClickListener(v -> {
            drawingView.setBrushSize(brushSize);
            prefs.setLastBrushSize(brushSize);
            // Update visual selection on size dots.
            findViewById(R.id.sizeSmall) .setAlpha(dotId == R.id.sizeSmall  ? 1f : 0.45f);
            findViewById(R.id.sizeMedium).setAlpha(dotId == R.id.sizeMedium ? 1f : 0.45f);
            findViewById(R.id.sizeLarge) .setAlpha(dotId == R.id.sizeLarge  ? 1f : 0.45f);
        });
    }

    // ── Time-of-day tint ──────────────────────────────────────────────────────

    /** Subtle wash that biases the page colder in the morning, neutral midday,
     *  warmer in the evening, and slightly amber at night. The page background
     *  drawable does the heavy lifting; this is just a thin overlay. */
    private void applyTimeOfDayTint(View tintView) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int color;
        if (hour >= 5 && hour < 9) {
            color = 0x14BCDDFF;       // morning: cool blue, ~8% alpha
        } else if (hour >= 9 && hour < 17) {
            color = 0x00000000;       // midday: cream is enough on its own
        } else if (hour >= 17 && hour < 20) {
            color = 0x18FFC080;       // evening: warm orange, ~10% alpha
        } else {
            color = 0x22FF9050;       // night: amber, ~13% alpha
        }
        tintView.setBackgroundColor(color);
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private void updateFadeLabel(TextView btn, boolean enabled) {
        btn.setText(enabled ? "fading on" : "fading off");
    }

    private float dp(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                                         getResources().getDisplayMetrics());
    }
}
