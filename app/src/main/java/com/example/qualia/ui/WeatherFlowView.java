package com.example.qualia.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import com.example.qualia.util.PrefsManager;
import com.example.qualia.util.SoundManager;

import java.util.Random;

/**
 * Ambient canvas: weather-matched particle drift, breath-phase modulation,
 * and an aurora pass for the long exhale and the graduation climax.
 *
 * Visual languages (particles):
 *   Clear      — warm amber motes drifting upward. Presence.
 *   Overcast   — pale grey wisps moving sideways. Stillness inside uncertainty.
 *   Rain       — cool silver threads falling and curving. Impermanence.
 *   Wind/Snow  — pale swirling points, no single path. Wu wei.
 *   Storm      — dense dark-silver turbulence. Acceptance.
 *
 * Memory density:
 *   Particle count starts at 6 (session 0) and grows to 48 (session 70).
 *   The screen accumulates quietly, the way experience does.
 *
 * Breath layer (BreathActivity, ClosingActivity):
 *   Inhale  → particles pull radially inward, slow ~40%.
 *   Hold    → near-still; a single faint warm seed point at centre.
 *   Exhale  → particles drift outward; aurora ribbon eases in across the
 *             upper third, peaks softly, fades out. The screen is breathing
 *             with the user, not at them.
 *
 * Environmental layer (SessionActivity):
 *   Luminance pulse at ~0.1 Hz (six breaths per minute, the resonant rate
 *   used in HRV / cyclic-sigh research). Amplitude is small enough to be
 *   physiological, not perceptual.
 *
 *   Slow warmth drift over the seven minutes shifts the particle hue ~200K
 *   toward amber — the "sitting by a fire as evening comes" effect.
 *
 *   A small downward bias boost can be enabled to nudge gaze downward —
 *   the classic relaxation-cue used in clinical settings.
 */
public class WeatherFlowView extends View {

    // ── Density ──────────────────────────────────────────────────────────────
    private static final int COUNT_MIN = 6;
    private static final int COUNT_MAX = 48;
    private static final int GRADUATION = 70;

    /** Full density (default). */
    public static final int DENSITY_FULL = 0;
    /** Half density — for Home, where the dust is a quieter companion. */
    public static final int DENSITY_HALF = 1;
    /** Low density — for Onboarding, where the world is still mostly empty. */
    public static final int DENSITY_LOW  = 2;

    // ── Breath phase machine ─────────────────────────────────────────────────
    /**
     * Single-knob breath progress, 0..1, mapped over the full 15s breath
     * window in BreathActivity. The view interpolates inward/outward pull,
     * speed, radius, seed visibility, and aurora intensity from this.
     *
     * 0.00–0.04 → settle (defaults)
     * 0.04–0.253 → inhale (pull inward, slow, dim)
     * 0.253–0.413 → hold (almost still; seed appears)
     * 0.413–0.867 → exhale (outward; aurora ribbon eases in/out)
     * 0.867–1.00  → settle back to defaults
     */
    private int   count;
    private float densityScale = 1f;

    private float[] px, py, life, dl;

    private final Paint  paint        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint  seedPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng          = new Random();

    private int   w, h;
    private float cx, cy;
    private long  t0;

    // Weather defaults (resolved from current code)
    private int   weatherColor;
    private float weatherSpeed;
    private float weatherRadius;
    private float weatherBx, weatherBy;

    // Live, possibly modulated values (read by onDraw)
    private int   color;
    private float speed;
    private float radius;
    private float bx, by;

    // ── Aurora ribbon (graduation climax) ───────────────────────────────────
    // A real flowing ribbon: cubic Bezier curves form the upper/lower edges,
    // animated control points provide the undulation, and a LinearGradient
    // shader paints the colour transition across the ribbon's height.
    private static final int RIBBON_SEGMENTS = 7;
    private final Path   ribbonPath  = new Path();
    private final Paint  ribbonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float   auroraExternal  = 0f;       // 0..1, set by GraduationActivity

    // ── Centred radial halo (breath exhale — symmetric, mirrors the body) ───
    // A single thin warm ring that expands outward from centre on the exhale.
    // Mirrors how a breath actually feels — out from the chest, even in all
    // directions, returning to stillness. One ring done deliberately reads
    // calmer than three rings competing for attention.
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float bloomIntensity = 0f;          // 0..1, driven by breath exhale

    // ── Seed (the single point at the apex of the hold) ─────────────────────
    // The seed appears at the hold and crossfades from a low-saturation cool
    // shibori-indigo (#6B7E8A) — the single cool note in the otherwise warm
    // palette — into the warm white that the exhale bloom inherits. The
    // perceptual contrast makes the warm release feel warmer by comparison.
    private float seedAlpha = 0f;
    private float seedHue   = 1f;               // 1 = cool indigo, 0 = warm white

    // ── Breath focal point (vertical offset from centre) ────────────────────
    // The breath copy lives at the upper third of the screen, so seed/bloom
    // at exact centre puts the visuals dangerously close to the text. The
    // breath activity sets this to ~0.70f (lower third) so the focal point
    // anchors well below the text — "the title is up there, the
    // demonstration is down here." Activities that don't set it (Session,
    // Closing, etc.) keep visuals at exact centre via the -1f sentinel.
    private float breathFocalYFraction = -1f;

    // ── Environmental toggles ───────────────────────────────────────────────
    private boolean luminancePulseOn = false;
    private float   downwardBiasBoost = 0f;
    private float   externalDrain    = 0f;   // 0 = full, 1 = invisible (graduation scroll-drain)
    private float   ambientDim       = 1f;   // 1 = full, 0 = invisible (session-closing dim)
    private float   warmth = 0f;                 // 0..1, drift toward amber over a session
    private float   inwardPull = 0f;             // negative = outward, positive = inward (set by breath)
    private float   speedMul   = 1f;
    private float   radiusMul  = 1f;
    private float   alphaMul   = 1f;             // global alpha multiplier for particles

    // ── Grain overlay (wabi-sabi) ───────────────────────────────────────────
    // A faint static noise tile drawn as the topmost layer keeps the canvas
    // from looking like a CG render. Built once, shared across instances via a
    // BitmapShader at very low alpha (~3%). Free per frame.
    private static Bitmap   sGrainBitmap;
    private static final int GRAIN_TILE = 192;          // px; tiled via shader
    private final Paint grainPaint = new Paint();

    public WeatherFlowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
        seedPaint.setStyle(Paint.Style.FILL);
        haloPaint.setStyle(Paint.Style.STROKE);
        ribbonPaint.setStyle(Paint.Style.FILL);
        ensureGrainBitmap();
        grainPaint.setShader(new BitmapShader(sGrainBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        grainPaint.setAlpha(8);                         // ~3% perceptual
    }

    /** Build the noise tile once. Mid-gray pixels with small variance — the
     *  shader's low alpha turns it into a hand-touched grain rather than
     *  visible noise. */
    private static synchronized void ensureGrainBitmap() {
        if (sGrainBitmap != null) return;
        int n = GRAIN_TILE;
        int[] px = new int[n * n];
        Random r = new Random(0x1A2B3C4D);             // deterministic so it's never "shimmery"
        for (int i = 0; i < px.length; i++) {
            int v = 96 + r.nextInt(64);                // 96–159
            px[i] = (0xFF << 24) | (v << 16) | (v << 8) | v;
        }
        sGrainBitmap = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        t0 = System.currentTimeMillis();
    }

    @Override
    protected void onSizeChanged(int newW, int newH, int oldW, int oldH) {
        w = newW;
        h = newH;
        cx = w * 0.5f;
        cy = h * 0.5f;

        int sessions = new PrefsManager(getContext()).getSessionCount();
        int base = COUNT_MIN + Math.round(
                (float) Math.min(sessions, GRADUATION) / GRADUATION * (COUNT_MAX - COUNT_MIN));
        count = Math.max(2, Math.round(base * densityScale));

        px   = new float[count];
        py   = new float[count];
        life = new float[count];
        dl   = new float[count];

        applyWeather(SoundManager.getWeatherCode());
        for (int i = 0; i < count; i++) seed(i, true);

    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (w == 0 || !isAttachedToWindow()) return;

        float t = (System.currentTimeMillis() - t0) * 0.001f;

        // ── Environmental modulations (live each frame) ──────────────────────
        // Luminance pulse: ~0.1 Hz (one breath every ~10s — the resonant rate).
        // Amplitude is 6% on the global alpha. Imperceptible to the conscious
        // mind, but real to the nervous system.
        float pulse = luminancePulseOn
                ? 0.94f + 0.06f * (float) Math.sin(t * 0.628f) // 2π / 10s ≈ 0.628
                : 1f;
        float a0 = alphaMul * pulse * (1f - externalDrain) * ambientDim;

        // Breath focal point on the y axis. -1f means use exact centre (most
        // screens); BreathActivity sets ~0.70f so the focal point sits in
        // the lower third and never overlaps the upper-third copy.
        float fy = (breathFocalYFraction < 0f) ? cy : (h * breathFocalYFraction);

        // ── Particles ────────────────────────────────────────────────────────
        int liveColor = applyWarmth(color, warmth);
        float liveBy = by + downwardBiasBoost;

        for (int i = 0; i < count; i++) {
            life[i] += dl[i] * speedMul;
            if (life[i] >= 1f) { seed(i, false); continue; }

            float a = (float) Math.sin(life[i] * Math.PI) * 0.55f * a0;
            if (a < 0.01f) continue;

            float vx = flowX(px[i], py[i], t);
            float vy = flowY(px[i], py[i], t);

            // Radial pull (inhale = inward, exhale = outward). Computed per
            // particle relative to the breath focal point so the field
            // converges/disperses where the seed actually appears.
            if (inwardPull > 0.001f || inwardPull < -0.001f) {
                float dxC = cx - px[i];
                float dyC = fy - py[i];
                float dist = (float) Math.sqrt(dxC * dxC + dyC * dyC);
                if (dist > 0.001f) {
                    float nxC = dxC / dist;
                    float nyC = dyC / dist;
                    vx += nxC * inwardPull * 0.9f;
                    vy += nyC * inwardPull * 0.9f;
                }
            }

            px[i] += (vx + bx) * speed * speedMul;
            py[i] += (vy + liveBy) * speed * speedMul;

            if (px[i] < -60)    px[i] = w + 60;
            if (px[i] > w + 60) px[i] = -60;
            if (py[i] < -60)    py[i] = h + 60;
            if (py[i] > h + 60) py[i] = -60;

            float r = radius * radiusMul * (0.6f + 0.4f * (float) Math.sin(life[i] * Math.PI));

            paint.setColor(liveColor);
            paint.setAlpha((int) (a * 255));
            canvas.drawCircle(px[i], py[i], r, paint);
        }

        // ── Seed (the still point at the hold) ────────────────────────────
        // Crossfades between a low-saturation shibori-indigo (the hold; the
        // single cool note) and a warm white (as the exhale begins and the
        // bloom inherits the light). The transition is the seed becoming the
        // bloom — same point of light, two temperatures.
        if (seedAlpha > 0.01f) {
            // Seed is the "light" of the breath copy ("Breathe with the light")
            // — it needs to be large enough and bright enough that the user
            // immediately finds it. Drawn at the breath focal point (fy),
            // which the BreathActivity sets to the lower third of the
            // screen so the seed lives below the text rather than under it.
            float seedR = Math.min(w, h) * 0.020f;
            int seedColor = lerpColor(0xFFF5F0E8, 0xFF6B7E8A, seedHue);
            // Soft glow via three stacked circles of decreasing alpha.
            // Multiplier bumped from 0.30/j to 0.40/j so the outer rings are
            // perceptually present, not just mathematically present.
            for (int j = 3; j >= 1; j--) {
                seedPaint.setColor(seedColor);
                seedPaint.setAlpha((int) (seedAlpha * 255 * (0.40f / j)));
                canvas.drawCircle(cx, fy, seedR * (j * 1.6f), seedPaint);
            }
            seedPaint.setColor(seedColor);
            seedPaint.setAlpha((int) (seedAlpha * 255 * 0.85f));
            canvas.drawCircle(cx, fy, seedR * 0.55f, seedPaint);
        }

        // ── Radial halo (breath exhale, symmetric, at breath focal point) ─────
        if (bloomIntensity > 0.01f) {
            drawRadialHalo(canvas, t, bloomIntensity, liveColor, fy);
        }

        // ── Aurora ribbon (graduation, asymmetric — reads as sky) ────────────
        if (auroraExternal > 0.01f) {
            drawAuroraRibbon(canvas, t, auroraExternal);
        }

        // ── Grain overlay (always last, very faint) ──────────────────────────
        // Wabi-sabi: the tiny imperfection that keeps the rendering from
        // looking like CG. Drawn after everything else so the texture lives
        // on the surface, not inside the elements.
        canvas.drawRect(0f, 0f, w, h, grainPaint);

        postInvalidateOnAnimation();
    }

    /**
     * Centred radial halo for the breath exhale. A single thin warm ring
     * expands deliberately outward from the centre — slower, larger, and
     * lonelier than the previous three-ring version. One thing done well.
     *
     * intensity: 0..1, peaks at the apex of the exhale and dissolves.
     */
    private void drawRadialHalo(Canvas canvas, float t, float intensity, int baseColor, float focalY) {
        // Reduced from 0.68 to 0.58 so the bloom can be anchored in the
        // lower third of the screen without its top edge reaching up
        // into the text region. Still a generous reach — ~60% of the
        // short dimension is plenty visible — just doesn't climb past
        // the upper-third copy.
        final float MAX_R    = Math.min(w, h) * 0.58f;
        final float STROKE   = 1.4f * getResources().getDisplayMetrics().density;
        haloPaint.setStrokeWidth(STROKE);

        // Warm core colour. We pull it slightly toward the seed white so the
        // halo reads as 'the same light as the seed, releasing outward.'
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >>  8) & 0xFF;
        int b =  baseColor        & 0xFF;
        int rr = (int) (r + (245 - r) * 0.50f);
        int gg = (int) (g + (240 - g) * 0.40f);
        int bb = (int) (b + (232 - b) * 0.35f);
        int warmCore = (clamp(rr) << 16) | (clamp(gg) << 8) | clamp(bb);

        // Expand outward; fade in then out (sin envelope).
        float radius = MAX_R * easeOut(intensity);
        float aEnv   = (float) Math.sin(intensity * Math.PI);
        int   alpha  = (int) (aEnv * 140);                     // max ~140/255 — slightly brighter than each of the 3 was

        if (alpha > 2) {
            haloPaint.setColor((alpha << 24) | warmCore);
            canvas.drawCircle(cx, focalY, radius, haloPaint);
        }

        // The core itself glows a touch brighter to keep the focal point
        // anchored — a faint warm point at (cx, focalY) so the eye doesn't
        // lose the anchor as the single ring expands outward.
        float coreR = Math.min(w, h) * 0.015f;
        int coreAlpha = (int) (intensity * 200);
        seedPaint.setColor((coreAlpha << 24) | warmCore);
        seedPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, focalY, coreR, seedPaint);
    }

    /**
     * Real aurora ribbon for the graduation climax. Built as a closed Path
     * traced by cubic Bezier curves — top edge left-to-right, bottom edge
     * right-to-left — and filled with a vertical LinearGradient (cool green
     * at the top, violet at the bottom, transparent at both extremes). The
     * control points slowly oscillate, producing the unmistakable curtain
     * shimmer of an actual aurora rather than drifting blobs.
     */
    private void drawAuroraRibbon(Canvas canvas, float t, float intensity) {
        final int BANDS = 2;

        for (int band = 0; band < BANDS; band++) {
            float yBase     = h * (0.13f + band * 0.10f);
            float bandH     = h * 0.075f;
            float phase     = band * 1.7f;
            float speed     = 0.18f + band * 0.04f;
            float amplitude = bandH * 0.85f;

            // Compute the top edge sample points.
            int   samples = RIBBON_SEGMENTS + 1;
            float[] tx = new float[samples];
            float[] ty = new float[samples];
            float[] by = new float[samples];
            float xStart = -w * 0.15f;
            float xEnd   = w * 1.15f;
            float dx     = (xEnd - xStart) / RIBBON_SEGMENTS;
            for (int i = 0; i < samples; i++) {
                tx[i] = xStart + i * dx;
                float w1 = (float) Math.sin(t * speed + i * 0.9f + phase);
                float w2 = (float) Math.sin(t * (speed * 1.6f) + i * 0.55f + phase * 0.7f);
                float wave = (w1 + 0.5f * w2) / 1.5f;
                ty[i] = yBase + wave * amplitude * 0.45f;
                // Slight bottom-edge waver, offset so the ribbon has organic thickness.
                float w3 = (float) Math.sin(t * speed + i * 0.9f + phase + 1.1f);
                by[i] = yBase + bandH + (w3 * amplitude * 0.30f);
            }

            // Trace the closed ribbon path.
            ribbonPath.reset();
            ribbonPath.moveTo(tx[0], ty[0]);
            for (int i = 0; i < RIBBON_SEGMENTS; i++) {
                float c1x = tx[i] + dx * 0.5f;
                float c1y = ty[i];
                float c2x = tx[i + 1] - dx * 0.5f;
                float c2y = ty[i + 1];
                ribbonPath.cubicTo(c1x, c1y, c2x, c2y, tx[i + 1], ty[i + 1]);
            }
            ribbonPath.lineTo(tx[RIBBON_SEGMENTS], by[RIBBON_SEGMENTS]);
            for (int i = RIBBON_SEGMENTS; i > 0; i--) {
                float c1x = tx[i] - dx * 0.5f;
                float c1y = by[i];
                float c2x = tx[i - 1] + dx * 0.5f;
                float c2y = by[i - 1];
                ribbonPath.cubicTo(c1x, c1y, c2x, c2y, tx[i - 1], by[i - 1]);
            }
            ribbonPath.close();

            // Vertical gradient across the ribbon: cool green → violet → dissolve.
            float top    = yBase - bandH * 0.3f;
            float bottom = yBase + bandH * 1.3f;
            int colorTop    = 0x007AC0A0;
            int colorMidT   = 0xCC7AC0A0;
            int colorMidB   = 0xCCA888C0;
            int colorBottom = 0x00A888C0;
            LinearGradient gradient = new LinearGradient(
                    0f, top, 0f, bottom,
                    new int[]{ colorTop, colorMidT, colorMidB, colorBottom },
                    new float[]{ 0f, 0.35f, 0.65f, 1f },
                    Shader.TileMode.CLAMP);
            ribbonPaint.setShader(gradient);
            ribbonPaint.setAlpha((int) (intensity * 200));
            canvas.drawPath(ribbonPath, ribbonPaint);
        }

        // Tear it down so we don't hold the shader between frames.
        ribbonPaint.setShader(null);
    }

    private void seed(int i, boolean scatter) {
        px[i]   = rng.nextFloat() * w;
        py[i]   = scatter ? rng.nextFloat() * h : entryY();
        life[i] = 0f;
        dl[i]   = 0.0018f + rng.nextFloat() * 0.0022f;
    }

    private float entryY() {
        if (by >  0.5f) return -20;
        if (by < -0.3f) return h + 20;
        return rng.nextFloat() * h;
    }

    private float flowX(float x, float y, float t) {
        return (float) (
            0.50 * Math.sin(x * 0.004  + t * 0.22) +
            0.30 * Math.cos(y * 0.006  + t * 0.17) +
            0.20 * Math.sin((x + y) * 0.003 + t * 0.29)
        );
    }

    private float flowY(float x, float y, float t) {
        return (float) (
            0.50 * Math.cos(y * 0.004  + t * 0.22) +
            0.30 * Math.sin(x * 0.005  + t * 0.17) +
            0.20 * Math.cos((x - y) * 0.003 + t * 0.29)
        );
    }

    private void applyWeather(int code) {
        float d = getResources().getDisplayMetrics().density;

        if (code <= 3) {
            weatherColor  = 0xFFB8962A;
            weatherSpeed  = 0.55f;
            weatherRadius = 3.5f * d;
            weatherBx     =  0.05f;
            weatherBy     = -0.45f;
        } else if (code <= 48) {
            weatherColor  = 0xFF8A8480;
            weatherSpeed  = 0.30f;
            weatherRadius = 6.5f * d;
            weatherBx     =  0.35f;
            weatherBy     =  0.08f;
        } else if (code <= 82) {
            weatherColor  = 0xFF7090A0;
            weatherSpeed  = 1.0f;
            weatherRadius = 2.0f * d;
            weatherBx     =  0.12f;
            weatherBy     =  1.0f;
        } else if (code <= 86) {
            weatherColor  = 0xFFB0C4CA;
            weatherSpeed  = 0.65f;
            weatherRadius = 2.8f * d;
            weatherBx     =  0.40f;
            weatherBy     =  0.25f;
        } else {
            weatherColor  = 0xFF506070;
            weatherSpeed  = 1.3f;
            weatherRadius = 2.5f * d;
            weatherBx     =  0.25f;
            weatherBy     =  0.70f;
        }

        // Live values default to weather values.
        color  = weatherColor;
        speed  = weatherSpeed;
        radius = weatherRadius;
        bx     = weatherBx;
        by     = weatherBy;
    }

    /**
     * Drift the particle hue toward warm amber. warmth in [0,1].
     * At 0 we use the weather colour as-is; at 1 we've shifted ~200K
     * toward fire-light. Used by SessionActivity to slowly warm the
     * screen over the seven minutes.
     */
    private int applyWarmth(int base, float warmth) {
        if (warmth <= 0.001f) return base;
        float k = Math.min(1f, warmth);
        int r = (base >> 16) & 0xFF;
        int g = (base >>  8) & 0xFF;
        int b =  base        & 0xFF;
        // Pull toward (210, 160, 90) — warm amber. Gentle blend, never replaces.
        int rr = (int) (r + (210 - r) * k * 0.45f);
        int gg = (int) (g + (160 - g) * k * 0.30f);
        int bb = (int) (b + ( 90 - b) * k * 0.35f);
        return (0xFF << 24) | (clamp(rr) << 16) | (clamp(gg) << 8) | clamp(bb);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    /** Channel-wise linear interpolation between two 0xAARRGGBB colours. */
    private static int lerpColor(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >>  8) & 0xFF;
        int ab =  a        & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >>  8) & 0xFF;
        int bb =  b        & 0xFF;
        int rr = (int) (ar + (br - ar) * k);
        int gg = (int) (ag + (bg - ag) * k);
        int rb = (int) (ab + (bb - ab) * k);
        int alpha = (int) (aa + (ba - aa) * k);
        return (clamp(alpha) << 24) | (clamp(rr) << 16) | (clamp(gg) << 8) | clamp(rb);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Set density mode. Must be called before the view is sized (typically in
     *  the layout XML or in onCreate before the activity is rendered). */
    public void setDensityMode(int mode) {
        densityScale = (mode == DENSITY_LOW)  ? 0.35f
                     : (mode == DENSITY_HALF) ? 0.55f
                     :                          1.00f;
        // If already sized, re-seed at the new count.
        if (w > 0 && h > 0) {
            int sessions = new PrefsManager(getContext()).getSessionCount();
            int base = COUNT_MIN + Math.round(
                    (float) Math.min(sessions, GRADUATION) / GRADUATION * (COUNT_MAX - COUNT_MIN));
            int newCount = Math.max(2, Math.round(base * densityScale));
            if (newCount != count) {
                count = newCount;
                px   = new float[count];
                py   = new float[count];
                life = new float[count];
                dl   = new float[count];
                for (int i = 0; i < count; i++) seed(i, true);
            }
        }
    }

    /**
     * Drive the breath phase machine. Pass 0..1 over the entire 15s breath
     * window. Pass a negative value to clear and return to weather defaults.
     */
    public void setBreathProgress(float progress) {
        if (progress < 0f) {
            inwardPull = 0f;
            speedMul   = 1f;
            radiusMul  = 1f;
            alphaMul   = 1f;
            seedAlpha  = 0f;
            seedHue    = 1f;
            bloomIntensity = 0f;
            return;
        }

        // Phase boundaries (mapped from the existing BreathActivity timings).
        final float TIN_START = 0.040f;  //  0.6s — first line appears
        final float TIN_END   = 0.253f;  //  3.8s — inhale done, second line ("top-off")
        final float TOP_END   = 0.413f;  //  6.2s — hold done, "let it go" begins
        final float TEX_END   = 0.867f;  // 13.0s — exhale finishes
        // 0.867 → 1.00 = settle back to weather defaults before "I'm ready".

        // Presentation beat: each line of copy appears, then ~600ms later
        // its corresponding visual response begins. The text is the title,
        // the visual is the demonstration. 0.04f progress ≈ 600ms in a 15s
        // cycle. The field's gradient motion (speedMul, radiusMul, alphaMul,
        // inwardPull) flows continuously through the whole phase — only the
        // discrete focal events (seed appearance, bloom rise) wait for the
        // beat. The atmosphere keeps breathing; the moments wait.
        final float BEAT = 0.040f;

        if (progress < TIN_START) {
            inwardPull = 0f; speedMul = 1f; radiusMul = 1f; alphaMul = 1f;
            seedAlpha = 0f;  bloomIntensity = 0f;
        } else if (progress < TIN_END) {
            // Inhale: field pulls inward across the whole phase. Seed waits
            // for the beat (~600ms after line 1 appears), then gathers from
            // nothing to peak brightness across the rest of the inhale. The
            // user reads "Breathe with the light" first, then watches a
            // small indigo focal point appear and grow as they breathe in.
            float u = norm(progress, TIN_START, TIN_END);
            float e = easeInOut(u);
            inwardPull = 0.55f * e;
            speedMul   = lerp(1f, 0.55f, e);
            radiusMul  = lerp(1f, 0.78f, e);
            alphaMul   = lerp(1f, 0.85f, e);
            if (progress < TIN_START + BEAT) {
                // Beat: text alone on screen, no seed yet.
                seedAlpha = 0f;
            } else {
                // Visual response: light gathers, reaching peak alpha 0.80
                // by the end of the inhale (so the hold can be "still").
                float us = norm(progress, TIN_START + BEAT, TIN_END);
                seedAlpha = lerp(0f, 0.80f, easeInOut(us));
            }
            seedHue    = 1f;                              // indigo throughout
            bloomIntensity = 0f;
        } else if (progress < TOP_END) {
            // Hold: the field settles to its quietest values; the seed sits
            // at peak brightness and does not move. "It is still." The seed's
            // un-changing alpha is the demonstration of stillness — nothing
            // is changing because we are at the still point.
            float u = norm(progress, TIN_END, TOP_END);
            float e = easeInOut(u);
            inwardPull = 0.55f;
            speedMul   = lerp(0.55f, 0.30f, e);
            radiusMul  = lerp(0.78f, 0.70f, e);
            alphaMul   = lerp(0.85f, 0.80f, e);
            seedAlpha  = 0.80f;                           // unmoving — the still point
            seedHue    = 1f;                              // fully cool indigo at the hold
            bloomIntensity = 0f;
        } else if (progress < TEX_END) {
            // Exhale: field eases outward through the whole phase. Seed and
            // bloom wait for the beat (~600ms after line 3 appears), then
            // the seed dissolves into a warm bloom expanding outward from
            // the focal point. The user reads "It is letting go" first,
            // then watches the release.
            float u = norm(progress, TOP_END, TEX_END);
            float e = easeInOut(u);
            // Inward → outward (radially): start at 0.55 pull-in, end at -0.55 push-out.
            inwardPull = lerp(0.55f, -0.55f, e);
            // Particles ease back up from the held 0.30x but stay UNDER baseline
            // (peak 0.85x). The exhale is the longest, slowest phase of the
            // breath — the field must not accelerate during it, or the visual
            // hurries the user's body and the breath feels rushed.
            speedMul   = lerp(0.30f, 0.85f, easeOut(u));
            // Radius puffs at the apex.
            float puff = (float) Math.sin(u * Math.PI);   // 0 → 1 → 0
            radiusMul  = 0.70f + 0.55f * puff;
            alphaMul   = lerp(0.80f, 1f, e);

            if (progress < TOP_END + BEAT) {
                // Beat: line 3 alone on screen. Seed still held at peak;
                // bloom hasn't started.
                seedAlpha = 0.80f;
                seedHue   = 1f;
                bloomIntensity = 0f;
            } else {
                // Release: seed dissolves, bloom rises and falls on a sin
                // envelope. ub is normalised over the post-beat exhale.
                float ub = norm(progress, TOP_END + BEAT, TEX_END);
                seedAlpha = lerp(0.80f, 0f, easeOut(ub));
                // Seed warms (indigo → warm white) as the bloom takes over
                // — the cool stillness becomes the warm release. Same point
                // of light, two temperatures.
                seedHue   = lerp(1f, 0f, easeOut(ub));
                // Radial bloom from the focal point: symmetric. Peaks at
                // the midpoint of the post-beat exhale, dissolves before
                // "I'm ready".
                bloomIntensity = (float) Math.sin(ub * Math.PI);
            }
        } else {
            // Settle back to weather defaults before "I'm ready".
            float u = norm(progress, TEX_END, 1f);
            float e = easeInOut(u);
            inwardPull = lerp(-0.55f, 0f, e);
            // Matches the new exhale-peak 0.85x; ramps back up to baseline.
            speedMul   = lerp(0.85f, 1f, e);
            radiusMul  = lerp(1.25f, 1f, e);
            alphaMul   = 1f;
            seedAlpha  = 0f;
            bloomIntensity = 0f;
        }
    }

    /** Where the breath focal point (seed + bloom) lives on the y axis, as
     *  a fraction of the view's height. -1f means "use exact centre" (the
     *  default for non-breath screens). 0.70f means the focal point sits
     *  in the lower third — below the breath copy at the upper third —
     *  so seed and bloom never overlap the text. Called once by
     *  BreathActivity.onCreate(). */
    public void setBreathFocalY(float fractionOfHeight) {
        this.breathFocalYFraction = fractionOfHeight;
    }

    /** Enable the subtle ~0.1 Hz luminance pulse. Used on session screens. */
    public void setLuminancePulse(boolean enabled) {
        luminancePulseOn = enabled;
    }

    /** Add a small downward bias to gaze direction (relaxation cue). */
    public void setDownwardBiasBoost(float boost) {
        downwardBiasBoost = boost;
    }

    /** 0..1, drift toward warm amber. Read every frame; safe to set repeatedly. */
    public void setWarmth(float warmth) {
        this.warmth = Math.max(0f, Math.min(1f, warmth));
    }

    /** Independent aurora intensity (for graduation, where no breath drives it). */
    public void setAuroraIntensity(float intensity) {
        auroraExternal = Math.max(0f, Math.min(1f, intensity));
    }

    /**
     * Drain factor used by GraduationActivity. As the user scrolls through
     * the closing letter, the accumulated dust of 70 sessions slowly drains
     * away — anicca, made visible in a single gesture. 0 = full, 1 = empty.
     */
    public void setDrain(float drain) {
        externalDrain = Math.max(0f, Math.min(1f, drain));
    }

    /**
     * Multiplies the entire particle field's alpha. Used by SessionActivity
     * to gently quiet the room around the closing line — the visual feels
     * the same way the writing does at the end of a session.
     * 1 = full visibility, 0 = invisible.
     */
    public void setAmbientDim(float dim) {
        ambientDim = Math.max(0f, Math.min(1f, dim));
    }

    /** Used by ClosingActivity: gentle outward drift + dim, mirroring the
     *  breath that opened the session. Returns after the duration completes. */
    public void exhaleAndDim(long durationMs) {
        final long start = System.currentTimeMillis();
        final long total = Math.max(500L, durationMs);
        post(new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                float u = Math.min(1f, (now - start) / (float) total);
                float e = easeOut(u);
                inwardPull = lerp(0f, -0.35f, e);  // gentle outward
                speedMul   = lerp(1f, 0.75f, e);
                alphaMul   = lerp(1f, 0.55f, e);
                if (u < 1f) postOnAnimation(this);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static float norm(float v, float a, float b) {
        if (b <= a) return 0f;
        float n = (v - a) / (b - a);
        return Math.max(0f, Math.min(1f, n));
    }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float easeInOut(float t) {
        // smoothstep
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3 - 2 * t);
    }
    private static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t);
    }
}
