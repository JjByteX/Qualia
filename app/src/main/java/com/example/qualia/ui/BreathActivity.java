package com.example.qualia.ui;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.example.qualia.R;
import com.example.qualia.util.SoundManager;

public class BreathActivity extends BaseActivity {

    /** Length of the full breath window. Must match the text-fade timings below. */
    private static final long BREATH_DURATION_MS = 15_000L;

    // Bind to the main thread explicitly — the no-arg constructor is deprecated.
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ValueAnimator breathAnimator;

    // Request location permission on first breath screen — it's the natural
    // moment before sound starts (user is already settling in).
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                // Whether granted or denied, start sound — SoundManager falls back to
                // a default sound if location is unavailable.
                SoundManager.start(this);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_breath);

        TextView txt1     = findViewById(R.id.txtBreath1);
        TextView txt2     = findViewById(R.id.txtBreath2);
        TextView txt3     = findViewById(R.id.txtBreath3);
        TextView btnReady = findViewById(R.id.btnReady);
        WeatherFlowView weatherFlow = findViewById(R.id.weatherFlow);

        // Move the breath focal point (seed + bloom) to the lower third so
        // it never overlaps the upper-third text. Text is the title at the
        // top; the visual demonstration lives below it.
        weatherFlow.setBreathFocalY(0.70f);

        // Request location, then start sound
        requestLocationAndStartSound();

        // ── Three text states, three visual states, presentation rhythm ─────
        // Each line of copy appears alone first, holds for ~600ms (the
        // "presentation beat"), THEN its corresponding visual response
        // begins. The text is the title, the visual is the demonstration.
        //
        //   1.  "Breathe with the light."  text appears → beat → seed gathers
        //   2.  "It is still."             text appears → beat → seed at peak, unmoving
        //   3.  "It is letting go."        text appears → beat → bloom releases outward
        //
        // Text alpha is driven by the SAME animator that drives the field,
        // so timing stays exact across devices. The 600ms beat is achieved
        // by offsetting the seed/bloom phase boundaries inside the flow
        // view's setBreathProgress(), not by Handler.postDelayed.
        final float TIN_START = 0.040f;
        final float TIN_END   = 0.253f;
        final float TOP_END   = 0.413f;
        final float TEX_END   = 0.867f;

        // The single knob: a 0→1 ramp over the full breath window. Feeds
        // both the flow view AND the three breath lines so the entire screen
        // breathes on one clock.
        breathAnimator = ValueAnimator.ofFloat(0f, 1f);
        breathAnimator.setDuration(BREATH_DURATION_MS);
        breathAnimator.setInterpolator(new LinearInterpolator());
        breathAnimator.addUpdateListener(a -> {
            float p = (float) a.getAnimatedValue();
            weatherFlow.setBreathProgress(p);

            // Line alphas. Each line uses the flat envelope (rise → hold →
            // fall) rather than a sin curve, so the text holds steady at
            // full alpha while its visual unfolds. Text 3 holds through the
            // bloom rise+fall and fades out near the end of the exhale.
            //
            // Text 2 shifted slightly later than the previous v5 timing so
            // the user has a clear beat after seed reaches peak before line
            // 2 ("It is still.") appears — the visual settles, then the
            // title arrives.
            txt1.setAlpha(alphaForLine(p, 0.020f, TIN_START + 0.005f, TIN_END - 0.015f, TIN_END + 0.020f));
            txt2.setAlpha(alphaForLine(p, TIN_END + 0.019f, TIN_END + 0.044f, TOP_END - 0.010f, TOP_END + 0.020f));
            txt3.setAlpha(alphaForLine(p, TOP_END + 0.005f, TOP_END + 0.030f, 0.800f, 0.847f));
        });
        breathAnimator.start();

        // "I'm ready" arrives dim, then brightens — a small invitation to
        // pause before tapping it rather than mashing through.
        handler.postDelayed(() -> {
            btnReady.animate().alpha(0.55f).setDuration(900).start();
        }, 15000);
        handler.postDelayed(() -> {
            btnReady.animate().alpha(1f).setDuration(1500).start();
        }, 16500);

        btnReady.setOnClickListener(v -> {
            handler.removeCallbacksAndMessages(null);
            if (breathAnimator != null) breathAnimator.cancel();
            // Hand off cleanly: leave the flow view at weather defaults so
            // SessionActivity inherits a calm field.
            weatherFlow.setBreathProgress(-1f);
            startActivity(new Intent(this, SessionActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        });
    }

    /**
     * Alpha envelope for the inhale and hold lines: linear ramp up between
     * fadeInStart and fullIn, plateau at 1, linear ramp down between
     * fadeOutStart and fadeOutEnd, otherwise 0.
     */
    private static float alphaForLine(float p, float fadeInStart, float fullIn,
                                      float fadeOutStart, float fadeOutEnd) {
        if (p <= fadeInStart || p >= fadeOutEnd) return 0f;
        if (p < fullIn) return (p - fadeInStart) / Math.max(1e-4f, (fullIn - fadeInStart));
        if (p < fadeOutStart) return 1f;
        return 1f - (p - fadeOutStart) / Math.max(1e-4f, (fadeOutEnd - fadeOutStart));
    }

    /**
     * Alpha envelope for the exhale line. Instead of a flat dwell, the text
     * rides a sin curve so it rises with the bloom and dissolves with it.
     * "Let it go." finishes letting go at the same moment the visual does.
     */
    private static float alphaForExhaleLine(float p, float start, float end) {
        if (p <= start || p >= end) return 0f;
        float u = (p - start) / (end - start);
        return (float) Math.sin(u * Math.PI);
    }

    private void requestLocationAndStartSound() {
        boolean hasFine   = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (hasFine || hasCoarse) {
            SoundManager.start(this);
        } else {
            // Ask — the callback calls SoundManager.start() regardless of outcome
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (breathAnimator != null) breathAnimator.cancel();
    }
}
