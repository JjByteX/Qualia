package com.example.qualia.ui;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import com.example.qualia.R;
import com.example.qualia.util.PrefsManager;

public class SplashActivity extends BaseActivity {

    /** Total splash duration. The bloom plays in full at a slower pace than
     *  feels "normal" for an app — the splash's first job is to teach the
     *  user the tempo of everything that follows. The wordmark emerges into
     *  the stillness after the bloom, then breathes once before transitioning. */
    private static final long SPLASH_DURATION_MS = 4400L;

    /** How long the seed→bloom plays for. Slower than a normal app loader —
     *  this pace IS the lesson. */
    private static final long SPLASH_BREATH_MS   = 2800L;

    /** Wordmark fades in after the bloom has fully settled. */
    private static final long WORDMARK_DELAY_MS  = 2900L;
    private static final long WORDMARK_DURATION  = 1100L;

    /** Wordmark breath pulse — a single, gentle dim+return after the fade-in
     *  completes. Teaches "this app has a heartbeat" without naming it. */
    private static final long WORDMARK_PULSE_DELAY_MS = 4000L;
    private static final long WORDMARK_PULSE_MS       = 350L;

    // Bind to the main thread explicitly — the no-arg constructor is deprecated.
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ValueAnimator breathAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        WeatherFlowView weatherFlow = findViewById(R.id.weatherFlow);
        TextView wordmark = findViewById(R.id.txtWordmark);

        // Lowest density so the seed/bloom is the focus, not a busy field.
        if (weatherFlow != null) weatherFlow.setDensityMode(WeatherFlowView.DENSITY_LOW);

        // Drive the breath phase machine through the hold→exhale arc — the
        // seed appears, grows into a brief bloom, then settles. The wordmark
        // fades in inside the bloom.
        if (weatherFlow != null) {
            breathAnimator = ValueAnimator.ofFloat(0.28f, 0.78f);
            breathAnimator.setDuration(SPLASH_BREATH_MS);
            breathAnimator.setInterpolator(new LinearInterpolator());
            breathAnimator.addUpdateListener(a ->
                    weatherFlow.setBreathProgress((float) a.getAnimatedValue()));
            breathAnimator.start();
        }

        // Wordmark fades in AFTER the bloom has fully arrived. The bloom is
        // the app's first breath; the wordmark is the app saying its name
        // into the stillness that follows. Sequence matters — these two
        // movements should not overlap.
        wordmark.animate()
                .alpha(1f)
                .setStartDelay(WORDMARK_DELAY_MS)
                .setDuration(WORDMARK_DURATION)
                .start();

        // Single, subtle breath of the wordmark before the transition. The
        // wordmark dims to ~0.55 alpha then returns to full — the visual
        // equivalent of a sigh after arrival. The user doesn't notice; their
        // body does.
        handler.postDelayed(() ->
                wordmark.animate()
                        .alpha(0.55f)
                        .setDuration(WORDMARK_PULSE_MS)
                        .withEndAction(() ->
                                wordmark.animate()
                                        .alpha(1f)
                                        .setDuration(WORDMARK_PULSE_MS)
                                        .start())
                        .start(),
                WORDMARK_PULSE_DELAY_MS);

        handler.postDelayed(() -> {
            if (breathAnimator != null) breathAnimator.cancel();
            if (weatherFlow != null) weatherFlow.setBreathProgress(-1f);

            PrefsManager prefs = new PrefsManager(this);
            if (prefs.isFirstLaunch()) {
                startActivity(new Intent(this, OnboardingActivity.class));
            } else {
                startActivity(new Intent(this, HomeActivity.class));
            }
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        }, SPLASH_DURATION_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (breathAnimator != null) breathAnimator.cancel();
    }
}
