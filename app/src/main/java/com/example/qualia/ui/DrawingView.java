package com.example.qualia.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Colored-pencil / crayon drawing surface for Qualia journal pages.
 *
 * <p>Strokes render as two-pass crayon: an even-coverage underlay sets the
 * pigment, then a textured pass tinted to the brush colour multiplies a tileable
 * paper-grain noise on top. Layered strokes build up density the way real wax
 * does.
 *
 * <p><b>Gesture routing</b>: this view sits on top of an EditText in z-order on
 * the journal page. It distinguishes <em>tap</em> from <em>drag</em> at runtime:
 * <ul>
 *   <li>A drag (movement &gt; touchSlop) is treated as a stroke.</li>
 *   <li>A tap (no drag) is forwarded to the registered tap-target via
 *       {@link #setTapForwardTarget(View)} — typically the EditText below — so
 *       that tapping the page raises the keyboard and places the cursor.</li>
 * </ul>
 * No mode toggle is needed: write to write, drag to draw.
 *
 * <p><b>Stroke memory</b>: each stroke retains its coordinate list so we can
 * serialize it to JSON and replay the drawing later as an animation in
 * {@link EntryDetailActivity}.
 */
public class DrawingView extends View {

    // ── Stroke data ───────────────────────────────────────────────────────────

    /** Rendering inputs + serializable point list for a single stroke. */
    public static class Stroke {
        final List<float[]> points;     // {x, y} per sample
        final int           color;
        final float         size;
        final boolean       erasing;
        Path                path;       // built lazily from points

        Stroke(List<float[]> points, int color, float size, boolean erasing) {
            this.points  = points;
            this.color   = color;
            this.size    = size;
            this.erasing = erasing;
        }

        /** Builds (or rebuilds) the path from the point list. */
        Path buildPath() {
            Path p = new Path();
            if (points.isEmpty()) return p;
            float[] first = points.get(0);
            p.moveTo(first[0], first[1]);
            float lastX = first[0], lastY = first[1];
            for (int i = 1; i < points.size() - 1; i++) {
                float[] pt = points.get(i);
                p.quadTo(lastX, lastY,
                         (pt[0] + lastX) / 2f, (pt[1] + lastY) / 2f);
                lastX = pt[0]; lastY = pt[1];
            }
            float[] last = points.get(points.size() - 1);
            p.lineTo(last[0], last[1]);
            return p;
        }
    }

    private final List<Stroke>  strokes      = new ArrayList<>();
    private       List<float[]> currentPoints;
    private       Path          currentPath;

    // ── Ghost-stroke demo (first journal visit only) ──────────────────────────
    /** A non-persistent path used by {@link #playGhostStrokeDemo} to teach
     *  the drag-to-draw gesture diegetically. Drawn over the offscreen
     *  bitmap, never added to {@link #strokes}, never persisted. */
    private Path           ghostPath;
    /** Partial-path slice currently visible, fed by a PathMeasure. */
    private Path           ghostVisible;
    /** 0..1 alpha multiplier applied to the ghost; controlled by the animator. */
    private float          ghostAlpha;
    private ValueAnimator  ghostAnimator;
    private Runnable       ghostOnFinish;

    /** The last set of strokes wiped by {@link #clear()}. {@link #undo()}
     *  restores them when called against an empty stroke list — the user can
     *  always change their mind once. Reset to null after the restore so it
     *  can't be re-applied. */
    private List<Stroke> lastClearedStrokes;

    // ── Brush state ───────────────────────────────────────────────────────────

    private int     brushColor = 0xFF2E7D4A;
    private float   brushSize  = 14f;
    private boolean erasing    = false;

    // ── Tap forwarding (gesture-distinguished routing) ────────────────────────

    /** Where to send taps that didn't become drags. The journal activity wires
     *  this to the EditText so a tap on the page raises the keyboard. */
    private View           tapForwardTarget;

    /** Optional richer handler for taps. When set, this is called instead of
     *  the dispatch-touch path, with the tap coordinates in {@code this}'s
     *  coordinate system. Lets the journal activity place the EditText
     *  cursor on whichever visual line the user tapped, extending the text
     *  with blank lines if the tap is below where they've typed. */
    public interface TapHandler { void onTap(float x, float y); }
    private TapHandler     tapHandler;

    private final int      touchSlop;
    private boolean        isDragging;
    private float          downX, downY;
    private MotionEvent    downEvent;

    // ── Replay state (used by EntryDetailActivity) ────────────────────────────

    private Stroke  currentReplayStroke;
    private Path    currentReplayPath;

    // ── Offscreen buffer ──────────────────────────────────────────────────────

    private Bitmap offscreen;
    private Canvas offscreenCanvas;

    // ── Paper-grain noise (shared) ────────────────────────────────────────────

    private static Bitmap noiseTex;
    private        Shader noiseShader;

    // ── Constructors ──────────────────────────────────────────────────────────

    public DrawingView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        init();
    }
    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        init();
    }

    private void init() {
        setBackground(null);
        ensureNoiseTexture();
        noiseShader = new BitmapShader(noiseTex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }

    // ── Size ──────────────────────────────────────────────────────────────────

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        Bitmap newBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas newCanvas = new Canvas(newBitmap);
        if (offscreen != null) {
            newCanvas.drawBitmap(offscreen, 0, 0, null);
            offscreen.recycle();
        }
        offscreen       = newBitmap;
        offscreenCanvas = newCanvas;
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (offscreen != null) canvas.drawBitmap(offscreen, 0, 0, null);

        // Live preview while a stroke is in progress.
        if (currentPath != null && isDragging) {
            Paint preview = new Paint(Paint.ANTI_ALIAS_FLAG);
            preview.setStyle(Paint.Style.STROKE);
            preview.setStrokeCap(Paint.Cap.ROUND);
            preview.setStrokeJoin(Paint.Join.ROUND);
            preview.setStrokeWidth(brushSize);
            if (erasing) preview.setColor(0x33000000);
            else { preview.setColor(brushColor); preview.setAlpha(190); }
            canvas.drawPath(currentPath, preview);
        }

        // Replay-in-progress: a partial stroke from PathMeasure.getSegment().
        if (currentReplayPath != null && currentReplayStroke != null) {
            renderStrokeOnCanvas(canvas, currentReplayPath, currentReplayStroke);
        }

        // Ghost-stroke demo: a faint, slowly-drawn line that teaches the
        // user "you can drag to draw here". Never persisted, never added to
        // strokes — purely visual.
        if (ghostVisible != null && ghostAlpha > 0f) {
            Paint ghost = new Paint(Paint.ANTI_ALIAS_FLAG);
            ghost.setStyle(Paint.Style.STROKE);
            ghost.setStrokeCap(Paint.Cap.ROUND);
            ghost.setStrokeJoin(Paint.Join.ROUND);
            ghost.setStrokeWidth(brushSize);
            ghost.setColor(brushColor);
            ghost.setAlpha((int) (ghostAlpha * 140));
            canvas.drawPath(ghostVisible, ghost);
        }
    }

    /**
     * Plays a one-time ghost-stroke animation across the page to teach the
     * drag-to-draw affordance. The stroke fades in as it's drawn, holds for
     * a moment, then fades out. Pure demonstration — no data is recorded.
     *
     * <p>Cancelled silently the moment the user touches the surface.
     */
    public void playGhostStrokeDemo(Runnable onFinish) {
        ghostOnFinish = onFinish;
        if (ghostAnimator != null) ghostAnimator.cancel();

        // Wait until the view has a size — we need width/height to build the
        // path. If we're not laid out yet, retry on the next frame.
        if (getWidth() == 0 || getHeight() == 0) {
            post(() -> playGhostStrokeDemo(onFinish));
            return;
        }

        // A gentle, hand-like arc across the lower-middle of the page.
        // Resembles a tentative first stroke, not a flourish.
        float w = getWidth();
        float h = getHeight();
        float startX = w * 0.25f;
        float endX   = w * 0.72f;
        float baseY  = h * 0.58f;
        Path full = new Path();
        full.moveTo(startX, baseY);
        full.cubicTo(
                w * 0.40f, baseY - h * 0.04f,
                w * 0.58f, baseY + h * 0.05f,
                endX,       baseY - h * 0.01f);
        ghostPath = full;
        final PathMeasure measure = new PathMeasure(full, false);
        final float length = measure.getLength();
        ghostVisible = new Path();

        // 0.00 → 0.55  : draw + fade in   (alpha ramps to 1)
        // 0.55 → 0.80  : hold              (alpha = 1)
        // 0.80 → 1.00  : fade out          (alpha ramps to 0)
        ghostAnimator = ValueAnimator.ofFloat(0f, 1f);
        ghostAnimator.setDuration(3200L);
        ghostAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float drawT = Math.min(1f, t / 0.55f);
            ghostVisible = new Path();
            measure.getSegment(0f, length * drawT, ghostVisible, true);
            if (t < 0.55f)       ghostAlpha = drawT;
            else if (t < 0.80f)  ghostAlpha = 1f;
            else                 ghostAlpha = 1f - (t - 0.80f) / 0.20f;
            invalidate();
        });
        ghostAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                ghostVisible = null;
                ghostPath = null;
                ghostAlpha = 0f;
                invalidate();
                if (ghostOnFinish != null) {
                    Runnable r = ghostOnFinish;
                    ghostOnFinish = null;
                    r.run();
                }
            }
            @Override public void onAnimationCancel(Animator a) {
                ghostVisible = null;
                ghostPath = null;
                ghostAlpha = 0f;
                ghostOnFinish = null;
                invalidate();
            }
        });
        ghostAnimator.start();
    }

    /** Cancels the ghost demo immediately. Called when the user touches the
     *  surface — they've already figured it out, the lesson is over. */
    public void cancelGhostStrokeDemo() {
        if (ghostAnimator != null && ghostAnimator.isRunning()) {
            ghostAnimator.cancel();
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // The moment the user touches the page, the ghost demo is
                // unnecessary — they've already engaged.
                cancelGhostStrokeDemo();
                downX = x; downY = y;
                if (downEvent != null) downEvent.recycle();
                downEvent = MotionEvent.obtain(event);
                isDragging = false;
                currentPoints = new ArrayList<>();
                currentPoints.add(new float[]{x, y});
                currentPath = new Path();
                currentPath.moveTo(x, y);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!isDragging) {
                    if (Math.hypot(x - downX, y - downY) > touchSlop) {
                        isDragging = true;
                    }
                }
                if (isDragging) {
                    float[] last = currentPoints.get(currentPoints.size() - 1);
                    currentPath.quadTo(last[0], last[1],
                                       (x + last[0]) / 2f, (y + last[1]) / 2f);
                    currentPoints.add(new float[]{x, y});
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (isDragging) {
                    currentPath.lineTo(x, y);
                    currentPoints.add(new float[]{x, y});
                    Stroke s = new Stroke(currentPoints, brushColor, brushSize, erasing);
                    s.path = currentPath;
                    strokes.add(s);
                    if (offscreenCanvas != null) renderStroke(offscreenCanvas, s);
                    if (onStrokeFinished != null) onStrokeFinished.run();
                } else {
                    // No drag — was a tap. Prefer the rich handler (which
                    // can extend text + place the cursor on the tapped
                    // line); fall back to dispatch-touch for legacy callers.
                    if (tapHandler != null) {
                        tapHandler.onTap(x, y);
                    } else {
                        forwardTap(downEvent, event);
                    }
                }
                if (downEvent != null) { downEvent.recycle(); downEvent = null; }
                currentPath = null;
                currentPoints = null;
                isDragging = false;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (downEvent != null) { downEvent.recycle(); downEvent = null; }
                currentPath = null;
                currentPoints = null;
                isDragging = false;
                return true;
        }
        return false;
    }

    private void forwardTap(MotionEvent down, MotionEvent up) {
        if (tapForwardTarget == null || down == null) return;
        int[] mine   = new int[2]; getLocationOnScreen(mine);
        int[] target = new int[2]; tapForwardTarget.getLocationOnScreen(target);
        float dx = mine[0] - target[0];
        float dy = mine[1] - target[1];

        MotionEvent fakeDown = MotionEvent.obtain(down);
        fakeDown.offsetLocation(dx, dy);
        tapForwardTarget.dispatchTouchEvent(fakeDown);
        fakeDown.recycle();

        MotionEvent fakeUp = MotionEvent.obtain(up);
        fakeUp.offsetLocation(dx, dy);
        tapForwardTarget.dispatchTouchEvent(fakeUp);
        fakeUp.recycle();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setColor(int color) {
        brushColor = color;
        erasing    = false;
    }

    public int getCurrentColor() { return brushColor; }

    public void setBrushSize(float size) {
        brushSize = size;
    }

    public void setErasing(boolean on) { erasing = on; }
    public boolean isErasing()         { return erasing; }

    public void setTapForwardTarget(View v) { this.tapForwardTarget = v; }

    public void setTapHandler(TapHandler h) { this.tapHandler = h; }

    /** Listener invoked on the UI thread immediately after a stroke is
     *  committed (finger lifts after a drag). Useful for the journal screen
     *  to refresh "exit doors" visibility without polling. Tap-only events
     *  do NOT fire this. */
    public void setOnStrokeFinished(Runnable r) { this.onStrokeFinished = r; }
    private Runnable onStrokeFinished;

    public boolean hasStrokes() { return !strokes.isEmpty(); }

    public List<Stroke> getStrokes() { return new ArrayList<>(strokes); }

    public void undo() {
        // If the canvas is empty but we just cleared something, restore it.
        if (strokes.isEmpty()
                && lastClearedStrokes != null
                && !lastClearedStrokes.isEmpty()) {
            strokes.addAll(lastClearedStrokes);
            lastClearedStrokes = null;
            rebuildOffscreen();
            if (onStrokeFinished != null) onStrokeFinished.run();
            return;
        }
        if (strokes.isEmpty()) return;
        strokes.remove(strokes.size() - 1);
        rebuildOffscreen();
        if (onStrokeFinished != null) onStrokeFinished.run();
    }

    public void clear() {
        if (!strokes.isEmpty()) {
            // Snapshot for one-step undo. We only keep the most recent clear
            // — repeated clear-on-empty doesn't bury the recoverable strokes.
            lastClearedStrokes = new ArrayList<>(strokes);
        }
        strokes.clear();
        if (offscreen != null) {
            offscreen.eraseColor(Color.TRANSPARENT);
            invalidate();
        }
        if (onStrokeFinished != null) onStrokeFinished.run();
    }

    /** Transparent-background bitmap with just the strokes. */
    public Bitmap getBitmap() {
        Bitmap result = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        result.eraseColor(Color.TRANSPARENT);
        Canvas c = new Canvas(result);
        if (offscreen != null) c.drawBitmap(offscreen, 0, 0, null);
        return result;
    }

    // ── Stroke serialization ──────────────────────────────────────────────────

    /** Encodes the current strokes as a compact JSON document. The activity
     *  saves this alongside the PNG so EntryDetailActivity can replay them. */
    public String strokesToJson() {
        try {
            JSONArray arr = new JSONArray();
            for (Stroke s : strokes) {
                JSONObject obj = new JSONObject();
                obj.put("color", s.color);
                obj.put("size",  s.size);
                obj.put("erase", s.erasing);
                JSONArray pts = new JSONArray();
                for (float[] p : s.points) {
                    JSONArray pt = new JSONArray();
                    pt.put(p[0]); pt.put(p[1]);
                    pts.put(pt);
                }
                obj.put("pts", pts);
                arr.put(obj);
            }
            JSONObject doc = new JSONObject();
            doc.put("w", getWidth());
            doc.put("h", getHeight());
            doc.put("strokes", arr);
            return doc.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    /** Parses the JSON written by {@link #strokesToJson()} into a stroke list. */
    public static List<Stroke> strokesFromJson(String json) {
        List<Stroke> out = new ArrayList<>();
        try {
            JSONObject doc = new JSONObject(json);
            JSONArray arr = doc.getJSONArray("strokes");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                int     color   = obj.getInt("color");
                float   size    = (float) obj.getDouble("size");
                boolean erasing = obj.getBoolean("erase");
                JSONArray pts   = obj.getJSONArray("pts");
                List<float[]> points = new ArrayList<>();
                for (int j = 0; j < pts.length(); j++) {
                    JSONArray pt = pts.getJSONArray(j);
                    points.add(new float[]{
                            (float) pt.getDouble(0),
                            (float) pt.getDouble(1)});
                }
                Stroke s = new Stroke(points, color, size, erasing);
                s.path = s.buildPath();
                out.add(s);
            }
        } catch (JSONException ignored) {}
        return out;
    }

    /** Returns {original_w, original_h} from the JSON, or {0,0} on parse error.
     *  Lets the caller scale the strokes to the current view size. */
    public static int[] originalSize(String json) {
        try {
            JSONObject doc = new JSONObject(json);
            return new int[]{doc.getInt("w"), doc.getInt("h")};
        } catch (JSONException e) {
            return new int[]{0, 0};
        }
    }

    /**
     * Loads the strokes from a JSON document into this view's offscreen canvas
     * synchronously (no replay animation) and makes them part of the active
     * stroke list — so subsequent strokes the user draws append to the loaded
     * set, and {@link #strokesToJson()} round-trips the combined drawing.
     *
     * <p>Used by the edit-window flow when re-opening a today-old entry: the
     * existing drawing reappears on the page exactly as it was, ready to be
     * extended or modified. Strokes are scaled to the current view size if it
     * differs from the size the drawing was originally made at.
     *
     * <p>Safe to call before the view has been laid out — the work is posted
     * once {@link #getWidth()} is non-zero.
     */
    public void setStrokesFromJson(String json) {
        if (json == null) return;
        final List<Stroke> loaded = strokesFromJson(json);
        if (loaded.isEmpty()) return;
        final int[] orig = originalSize(json);
        Runnable apply = () -> {
            int viewW = getWidth();
            int viewH = getHeight();
            if (viewW > 0 && viewH > 0
                    && orig[0] > 0 && orig[1] > 0
                    && (orig[0] != viewW || orig[1] != viewH)) {
                float sx = viewW / (float) orig[0];
                float sy = viewH / (float) orig[1];
                for (Stroke s : loaded) {
                    for (float[] pt : s.points) {
                        pt[0] *= sx;
                        pt[1] *= sy;
                    }
                    s.path = s.buildPath();
                }
            }
            strokes.clear();
            strokes.addAll(loaded);
            // The offscreen buffer is created lazily in onSizeChanged, so it
            // may not exist yet on the first post(). Defer rendering until it
            // does — without dropping the loaded strokes.
            if (offscreen != null) {
                rebuildOffscreen();
            } else {
                post(this::rebuildOffscreen);
            }
            if (onStrokeFinished != null) onStrokeFinished.run();
        };
        if (getWidth() > 0 && getHeight() > 0) {
            apply.run();
        } else {
            post(apply);
        }
    }

    // ── Replay (for EntryDetailActivity) ──────────────────────────────────────

    /** Sets the stroke list directly without rendering — used by the replay
     *  driver before it animates each stroke onto the offscreen canvas. */
    public void replayBegin() {
        strokes.clear();
        if (offscreen != null) {
            offscreen.eraseColor(Color.TRANSPARENT);
            invalidate();
        }
    }

    /** Animates a single stroke's path being drawn over {@code durationMs} and
     *  invokes {@code onDone} on the main thread when complete. */
    public void replayStroke(Stroke s, long durationMs, Runnable onDone) {
        if (s.path == null) s.path = s.buildPath();
        PathMeasure pm = new PathMeasure(s.path, false);
        final float length = pm.getLength();
        if (length <= 0f) {
            if (onDone != null) onDone.run();
            return;
        }
        ValueAnimator anim = ValueAnimator.ofFloat(0f, length);
        anim.setDuration(durationMs);
        anim.addUpdateListener(va -> {
            float current = (float) va.getAnimatedValue();
            Path partial = new Path();
            pm.getSegment(0, current, partial, true);
            currentReplayPath   = partial;
            currentReplayStroke = s;
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                strokes.add(s);
                if (offscreenCanvas != null) renderStroke(offscreenCanvas, s);
                currentReplayPath   = null;
                currentReplayStroke = null;
                invalidate();
                if (onDone != null) onDone.run();
            }
        });
        anim.start();
    }

    // ── Crayon rendering ──────────────────────────────────────────────────────

    private void rebuildOffscreen() {
        if (offscreen == null) return;
        offscreen.eraseColor(Color.TRANSPARENT);
        for (Stroke s : strokes) renderStroke(offscreenCanvas, s);
        invalidate();
    }

    /** Two-pass crayon: even underlay + textured grain body. */
    private void renderStroke(Canvas canvas, Stroke s) {
        if (s.path == null) s.path = s.buildPath();
        renderStrokeOnCanvas(canvas, s.path, s);
    }

    /** Renders the given path with stroke s's brush parameters. Used both for
     *  committed strokes (where path == s.path) and for in-flight replay frames
     *  (where path is a partial path from PathMeasure.getSegment). */
    private void renderStrokeOnCanvas(Canvas canvas, Path path, Stroke s) {
        if (s.erasing) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(s.size * 1.4f);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            canvas.drawPath(path, p);
            return;
        }

        // Pass 1: even-coverage underlay at low alpha.
        Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
        base.setStyle(Paint.Style.STROKE);
        base.setStrokeCap(Paint.Cap.ROUND);
        base.setStrokeJoin(Paint.Join.ROUND);
        base.setStrokeWidth(s.size);
        base.setColor(s.color);
        base.setAlpha(95);
        canvas.drawPath(path, base);

        // Pass 2: textured grain body, tinted to brush colour, ~78% opacity.
        Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        body.setStyle(Paint.Style.STROKE);
        body.setStrokeCap(Paint.Cap.ROUND);
        body.setStrokeJoin(Paint.Join.ROUND);
        body.setStrokeWidth(s.size);
        body.setShader(noiseShader);
        int filterColor = (s.color & 0x00FFFFFF) | (220 << 24);
        body.setColorFilter(new PorterDuffColorFilter(
                filterColor, PorterDuff.Mode.MULTIPLY));
        canvas.drawPath(path, body);
    }

    // ── Noise texture ─────────────────────────────────────────────────────────

    private static synchronized void ensureNoiseTexture() {
        if (noiseTex != null) return;
        final int size = 96;
        java.util.Random rnd = new java.util.Random(0xC4A1C);
        int[] base = new int[size * size];
        for (int i = 0; i < base.length; i++) base[i] = rnd.nextInt(256);

        int[] blurred = new int[base.length];
        int radius = 1;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int sum = 0, count = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int nx = (x + dx + size) % size;
                        int ny = (y + dy + size) % size;
                        sum += base[ny * size + nx];
                        count++;
                    }
                }
                blurred[y * size + x] = sum / count;
            }
        }

        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float v = blurred[y * size + x] / 255f;
                float curved = (float) Math.pow(v, 0.75);
                int   alpha  = 60 + (int) (curved * 195);
                if (alpha > 255) alpha = 255;
                bmp.setPixel(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }
        noiseTex = bmp;
    }
}
