package com.example.qualia.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.qualia.R;
import com.example.qualia.data.model.Attachment;
import com.example.qualia.data.repository.JournalRepository;
import com.example.qualia.util.PrefsManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Read-only view of a saved journal entry.
 *
 * <p>The same single-paper surface used for writing. The body text fills the
 * page; drawings are overlaid (with stroke replay if a JSON sidecar exists,
 * else a static PNG fallback); polaroids float positioned with their natural
 * tilt. The time-of-day tint applies the same way as on the write screen.
 *
 * <p><b>Fading drawings.</b> When the user has the fading-journal toggle on,
 * drawings and polaroids fade with age — a recent entry is fully present, a
 * year-old entry is at ~30% opacity, three years at ~10%. The viewer leans in
 * to remember.
 */
public class EntryDetailActivity extends BaseActivity {

    /** Time over which a drawing decays to {@link #FADING_FLOOR_ALPHA}. After
     *  this point the alpha holds steady — we never disappear an entry. */
    private static final long FADING_HORIZON_MS = 365L * 24 * 60 * 60 * 1000;
    private static final float FADING_FLOOR_ALPHA = 0.10f;

    /** Per-stroke replay duration when a drawing has JSON. The total animation
     *  is roughly numStrokes * this, capped at REPLAY_TOTAL_CAP_MS. */
    private static final long REPLAY_PER_STROKE_MS = 240;
    private static final long REPLAY_TOTAL_CAP_MS  = 4500;

    /** "Let it go" hold duration — the time the user must keep their finger
     *  on the release button before the entry is actually released. The fade
     *  runs for this long; releasing early returns the page to full opacity. */
    private static final long LET_GO_HOLD_MS = 2500;

    private DrawingView replayView;
    private FrameLayout attachmentsContainer;
    private View        pageFrame;
    private TextView    btnEdit;
    private TextView    btnLetGo;
    private float       fadingAlpha = 1f;
    private int         entryId;
    private long        entryTimestamp;

    /** True while the user is holding the "let it go" button and the page is
     *  fading toward release. Held state is cleared on UP/CANCEL. */
    private boolean     releasing;
    /** Active fade animator while releasing. We cancel + reverse it if the
     *  user lifts their finger before the hold completes. */
    private ValueAnimator releaseAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entry_detail);

        TextView    btnBack   = findViewById(R.id.btnBack);
        TextView    txtDate   = findViewById(R.id.txtDate);
        TextView    txtBody   = findViewById(R.id.txtEntryBody);
        View        timeTint  = findViewById(R.id.timeTint);
        attachmentsContainer  = findViewById(R.id.attachmentsContainer);
        replayView            = findViewById(R.id.replayView);
        pageFrame             = findViewById(R.id.pageFrame);
        btnEdit               = findViewById(R.id.btnEdit);
        btnLetGo              = findViewById(R.id.btnLetGo);

        String text    = getIntent().getStringExtra("entry_text");
        entryTimestamp = getIntent().getLongExtra("entry_date", 0);
        entryId        = getIntent().getIntExtra("entry_id", 0);

        applyTimeOfDayTint(timeTint);

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
        txtDate.setText(sdf.format(new Date(entryTimestamp)));
        txtBody.setText(text);

        // Compute the fading multiplier once for this entry.
        boolean fadingEnabled = new PrefsManager(this).isFadingJournal();
        if (fadingEnabled && entryTimestamp > 0) {
            long age = System.currentTimeMillis() - entryTimestamp;
            float t = Math.min(1f, Math.max(0f, age / (float) FADING_HORIZON_MS));
            // ease-out: drawings fade fast at first, settle at the floor.
            fadingAlpha = 1f - (1f - FADING_FLOOR_ALPHA) * (float) Math.pow(t, 0.6);
        }

        // Body fade-in matches the writing rhythm.
        txtBody.setAlpha(0f);
        txtBody.animate()
               .alpha(fadingAlpha)
               .setStartDelay(300)
               .setDuration(800)
               .start();

        btnBack.setOnClickListener(v -> finish());

        if (entryId > 0) {
            new JournalRepository(this).getAttachmentsForEntry(entryId,
                    attachments -> runOnUiThread(() -> renderAttachments(attachments)));
        }

        // ── Edit window ───────────────────────────────────────────────────────
        // The edit affordance only appears for entries written today. Once the
        // day rolls over the entry settles into an artifact and edit hides.
        if (entryId > 0 && isWrittenToday(entryTimestamp)) {
            btnEdit.setVisibility(View.VISIBLE);
            btnEdit.setOnClickListener(v -> openInEditMode());
        } else {
            btnEdit.setVisibility(View.GONE);
        }

        // ── Let it go ─────────────────────────────────────────────────────────
        // Press and hold the "let it go" caption to release the entry. The
        // page fades during the hold; releasing early reverses the fade.
        if (entryId > 0) {
            btnLetGo.setVisibility(View.VISIBLE);
            btnLetGo.setOnTouchListener(this::onLetGoTouch);
        } else {
            btnLetGo.setVisibility(View.GONE);
        }
    }

    // ── Edit-window helpers ───────────────────────────────────────────────────

    /** True when {@code timestamp} falls on the current local day. The window
     *  closes at midnight — once the day rolls over the entry settles. */
    private boolean isWrittenToday(long timestamp) {
        if (timestamp <= 0) return false;
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(timestamp);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private void openInEditMode() {
        Intent intent = new Intent(this, JournalActivity.class);
        intent.putExtra(JournalActivity.EXTRA_EDIT_ENTRY_ID, entryId);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    // ── Let-it-go gesture ─────────────────────────────────────────────────────

    /** Hold-to-release. On finger DOWN we start a {@code LET_GO_HOLD_MS} fade
     *  on the page; on UP/CANCEL before completion we reverse the fade. When
     *  the fade completes the entry, its attachments, and their files are all
     *  released. The hold time IS the confirmation — no dialog, no trash. */
    private boolean onLetGoTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginRelease();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!releasing) return false;
                cancelRelease();
                return true;
        }
        return false;
    }

    private void beginRelease() {
        if (releasing) return;
        releasing = true;
        btnLetGo.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

        if (releaseAnim != null) releaseAnim.cancel();
        float startAlpha = pageFrame.getAlpha();
        releaseAnim = ValueAnimator.ofFloat(startAlpha, 0f);
        releaseAnim.setDuration((long) (LET_GO_HOLD_MS * startAlpha));
        releaseAnim.addUpdateListener(va ->
                pageFrame.setAlpha((float) va.getAnimatedValue()));
        releaseAnim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator a) {
                cancelled = true;
            }
            @Override public void onAnimationEnd(Animator a) {
                if (!cancelled) commitRelease();
            }
        });
        releaseAnim.start();
    }

    private void cancelRelease() {
        releasing = false;
        if (releaseAnim != null) {
            releaseAnim.cancel();
            releaseAnim = null;
        }
        // Snap back to full opacity over a short fade — feels like releasing
        // your grip; the page settles back into place.
        pageFrame.animate()
                 .alpha(1f)
                 .setDuration(220)
                 .start();
    }

    private void commitRelease() {
        // Disable further input on the let-go button so we don't double-fire.
        btnLetGo.setOnTouchListener(null);
        btnLetGo.setEnabled(false);

        new JournalRepository(this).letItGo(entryId, ignored -> runOnUiThread(() -> {
            // Briefly hold at zero opacity so the disappearance lands before
            // we tear down the activity, then return to past entries.
            btnLetGo.postDelayed(this::finish, 200);
        }));
    }

    private void renderAttachments(List<Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) return;
        File baseDir = getFilesDir();
        for (Attachment a : attachments) {
            File file = new File(baseDir, a.filePath);
            if (!file.exists()) continue;
            if (Attachment.TYPE_CHALK.equals(a.type)) {
                renderDrawing(file);
            } else if (Attachment.TYPE_POLAROID.equals(a.type)) {
                renderPolaroid(file);
            }
        }
    }

    /** Renders a drawing attachment. If the JSON sidecar exists, replay the
     *  strokes onto the replayView. Otherwise show the static PNG as a fading
     *  overlay. */
    private void renderDrawing(File pngFile) {
        File jsonFile = sidecarJsonFor(pngFile);
        if (jsonFile.exists()) {
            String json = readFileToString(jsonFile);
            if (json != null) {
                replayDrawingFromJson(json);
                return;
            }
        }
        // Fallback: static PNG.
        showStaticDrawing(pngFile);
    }

    private void replayDrawingFromJson(String json) {
        List<DrawingView.Stroke> strokes = DrawingView.strokesFromJson(json);
        int[] origSize = DrawingView.originalSize(json);
        if (strokes.isEmpty()) return;

        replayView.setAlpha(fadingAlpha);

        // The replay surface may have a different size than when the drawing
        // was originally made. Wait for layout, then scale strokes if needed.
        replayView.post(() -> {
            float sx = 1f, sy = 1f;
            if (origSize[0] > 0 && origSize[1] > 0) {
                sx = replayView.getWidth()  / (float) origSize[0];
                sy = replayView.getHeight() / (float) origSize[1];
            }
            // Apply scale to every point in every stroke. We mutate in place:
            // the strokes were just deserialized for this view's use.
            if (sx != 1f || sy != 1f) {
                for (DrawingView.Stroke s : strokes) {
                    for (float[] pt : s.points) {
                        pt[0] *= sx;
                        pt[1] *= sy;
                    }
                    s.path = s.buildPath();
                }
            }

            replayView.replayBegin();
            long perStroke = Math.max(80,
                    Math.min(REPLAY_PER_STROKE_MS,
                             REPLAY_TOTAL_CAP_MS / Math.max(1, strokes.size())));
            startReplayChain(strokes, 0, perStroke);
        });
    }

    private void startReplayChain(List<DrawingView.Stroke> strokes,
                                  int index, long perStrokeMs) {
        if (index >= strokes.size()) return;
        DrawingView.Stroke s = strokes.get(index);
        replayView.replayStroke(s, perStrokeMs,
                () -> startReplayChain(strokes, index + 1, perStrokeMs));
    }

    private void showStaticDrawing(File pngFile) {
        Bitmap bmp = BitmapFactory.decodeFile(pngFile.getAbsolutePath());
        if (bmp == null) return;

        ImageView iv = new ImageView(this);
        iv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        iv.setImageBitmap(bmp);
        iv.setAlpha(0f);
        iv.animate().alpha(fadingAlpha).setStartDelay(400).setDuration(900).start();
        attachmentsContainer.addView(iv);
    }

    // Pin Y-offset in the polaroid bitmap, in pixels. Must match the
    // POLAROID_PIN_Y_PX constant in JournalActivity. Used for tilt pivot.
    private static final int POLAROID_PIN_Y_PX = 18 + 24 / 2;

    /** Renders a polaroid as a positioned overlay with its persisted tilt and
     *  position. Falls back to a centred default when no metadata sidecar
     *  exists (e.g. legacy polaroids saved before v3.1). */
    private void renderPolaroid(File file) {
        final Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bmp == null) return;

        final ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setAdjustViewBounds(true);

        // Read the position metadata sidecar, if any.
        File metaFile = polaroidMetaFor(file);
        final org.json.JSONObject meta = readJsonOrNull(metaFile);

        attachmentsContainer.post(() -> {
            int containerW = attachmentsContainer.getWidth();
            int containerH = attachmentsContainer.getHeight();

            int targetWidth;
            int leftMargin, topMargin;
            float tilt;

            if (meta != null && containerW > 0 && containerH > 0) {
                float fx   = (float) meta.optDouble("fx",   -1);
                float fy   = (float) meta.optDouble("fy",   -1);
                float fw   = (float) meta.optDouble("fw",   0.62);
                float t    = (float) meta.optDouble("tilt", 0);
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
                int idx = attachmentsContainer.getChildCount();
                leftMargin = (containerW - targetWidth) / 2;
                topMargin  = (int) dp(40 + 60 * idx);
                tilt = ((idx * 37 + (int)(System.currentTimeMillis() % 7)) % 7) - 3f;
            }

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.leftMargin = leftMargin;
            lp.topMargin  = topMargin;
            iv.setLayoutParams(lp);

            // Pivot at the pin so rotations look like the photo hangs from it.
            iv.post(() -> {
                if (iv.getWidth() > 0 && bmp.getHeight() > 0) {
                    iv.setPivotX(iv.getWidth() / 2f);
                    iv.setPivotY(iv.getHeight()
                            * (POLAROID_PIN_Y_PX / (float) bmp.getHeight()));
                }
                iv.setRotation(tilt);
            });

            iv.setAlpha(0f);
            attachmentsContainer.addView(iv);
            iv.animate()
              .alpha(fadingAlpha)
              .setStartDelay(500)
              .setDuration(900)
              .start();
        });
    }

    private File polaroidMetaFor(File pngFile) {
        String path = pngFile.getAbsolutePath();
        int dot = path.lastIndexOf('.');
        String base = (dot > 0) ? path.substring(0, dot) : path;
        return new File(base + ".meta.json");
    }

    private org.json.JSONObject readJsonOrNull(File f) {
        if (!f.exists()) return null;
        String s = readFileToString(f);
        if (s == null) return null;
        try {
            return new org.json.JSONObject(s);
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    // ── Time-of-day tint (mirrors JournalActivity) ────────────────────────────

    private void applyTimeOfDayTint(View tintView) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int color;
        if (hour >= 5 && hour < 9) {
            color = 0x14BCDDFF;
        } else if (hour >= 9 && hour < 17) {
            color = 0x00000000;
        } else if (hour >= 17 && hour < 20) {
            color = 0x18FFC080;
        } else {
            color = 0x22FF9050;
        }
        tintView.setBackgroundColor(color);
    }

    // ── File helpers ──────────────────────────────────────────────────────────

    private File sidecarJsonFor(File pngFile) {
        String path = pngFile.getAbsolutePath();
        int dot = path.lastIndexOf('.');
        String base = (dot > 0) ? path.substring(0, dot) : path;
        return new File(base + ".json");
    }

    private String readFileToString(File f) {
        try (FileInputStream is = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), "UTF-8");
        } catch (IOException e) {
            return null;
        }
    }

    private float dp(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                                         getResources().getDisplayMetrics());
    }
}
