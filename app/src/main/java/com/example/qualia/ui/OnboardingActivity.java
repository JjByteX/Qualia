package com.example.qualia.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.widget.TextView;

import com.example.qualia.R;
import com.example.qualia.util.PrefsManager;

/**
 * Four lines arrive one at a time, each its own breath. The job of
 * onboarding is not to explain what the app does — it is to teach the user
 * the pacing of the app before they reach the first real screen. The slow
 * tempo here is the lesson; the text just makes that tempo legible.
 *
 * <p>The fourth line was added in v5. It names the shape of the thing the
 * user is about to enter: "Seventy sessions. One a day. Then the app
 * ends." The proposal is explicit that the arc is meant to be completed
 * and left behind — "the end is the point". Saying that on the first
 * screen tells the user up front that Qualia is not a subscription, not a
 * habit, not a feed. It is a course with a shape. Telling them that here
 * is the most philosophy-honest thing the app can do — the antithesis of
 * every other app on their phone, which all pretend they'll be there
 * forever.
 *
 * <p>Lines auto-advance. The user can tap anywhere to skip to the next
 * line. The fourth line stays visible alongside the "begin" button — it
 * pairs with the choice to begin.
 */
public class OnboardingActivity extends BaseActivity {

    // Timing for each breath. Each line: fade in (1000ms), dwell (2000ms),
    // fade out (800ms). Total per line ~= 3.8s. The dwell on the third line
    // is unchanged so the existing pacing through "you sit for a moment"
    // still feels right.
    private static final long FADE_IN_MS  = 1000L;
    private static final long DWELL_MS    = 2000L;
    private static final long FADE_OUT_MS = 800L;
    private static final long FIRST_DELAY = 800L;

    /** Extra breath before the fourth line arrives. The first three lines
     *  taught pacing; the fourth is a fact, so we let a slightly longer
     *  silence land first. */
    private static final long FOURTH_LINE_PREDELAY_MS = 200L;

    // Bind explicitly to the main thread — the no-arg Handler() constructor
    // is deprecated since API 30 (and was always ambiguous about which Looper
    // it picked up). Onboarding work all touches views, so main is correct.
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView txtLine1;
    private TextView txtLine2;
    private TextView txtLine3;
    private TextView txtLine4;
    private TextView btnBegin;

    private int currentLine = 0;     // 0 = nothing shown yet, 1..4 = which line is visible
    private boolean canSkip = false; // false while the current line is mid-transition

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        txtLine1 = findViewById(R.id.txtLine1);
        txtLine2 = findViewById(R.id.txtLine2);
        txtLine3 = findViewById(R.id.txtLine3);
        txtLine4 = findViewById(R.id.txtLine4);
        btnBegin = findViewById(R.id.btnBegin);

        WeatherFlowView weatherFlow = findViewById(R.id.weatherFlow);
        if (weatherFlow != null) weatherFlow.setDensityMode(WeatherFlowView.DENSITY_LOW);

        // Start the sequence after a small initial silence — the user just
        // came in from the splash; let the room be quiet for a beat first.
        handler.postDelayed(this::showLine1, FIRST_DELAY);

        btnBegin.setOnClickListener(v -> finishOnboarding());
    }

    /**
     * Tap anywhere on the screen to advance the current line early. The
     * begin button has its own handler that takes precedence.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && canSkip) {
            advance();
        }
        return super.dispatchTouchEvent(ev);
    }

    private void advance() {
        canSkip = false;
        handler.removeCallbacksAndMessages(null);
        if (currentLine == 1) {
            fadeOut(txtLine1, this::showLine2);
        } else if (currentLine == 2) {
            fadeOut(txtLine2, this::showLine3);
        } else if (currentLine == 3) {
            fadeOut(txtLine3, this::showLine4);
        }
        // Line 4 doesn't fade out on tap — it stays, the user uses the button.
    }

    private void showLine1() {
        currentLine = 1;
        fadeIn(txtLine1);
        handler.postDelayed(() -> fadeOut(txtLine1, this::showLine2),
                FADE_IN_MS + DWELL_MS);
    }

    private void showLine2() {
        currentLine = 2;
        fadeIn(txtLine2);
        handler.postDelayed(() -> fadeOut(txtLine2, this::showLine3),
                FADE_IN_MS + DWELL_MS);
    }

    private void showLine3() {
        currentLine = 3;
        fadeIn(txtLine3);
        handler.postDelayed(() -> fadeOut(txtLine3, this::showLine4),
                FADE_IN_MS + DWELL_MS);
    }

    /**
     * The shape-of-the-arc line. Stays visible — it pairs with the choice
     * to begin, the way line 3 used to. Telling the user, before they enter
     * the app, that the app is going to close itself when they're done is
     * the most philosophy-honest move available.
     */
    private void showLine4() {
        currentLine = 4;
        handler.postDelayed(() -> {
            fadeIn(txtLine4);
            // After the fourth line has landed, the button arrives quietly.
            // The line itself stays visible — it is the last thing the user
            // reads before they choose to begin.
            handler.postDelayed(() -> {
                btnBegin.animate().alpha(1f).setDuration(1100).start();
            }, FADE_IN_MS + 1200L);
        }, FOURTH_LINE_PREDELAY_MS);
    }

    private void fadeIn(TextView v) {
        canSkip = false;
        v.animate()
                .alpha(1f)
                .setDuration(FADE_IN_MS)
                .withEndAction(() -> canSkip = true)
                .start();
    }

    private void fadeOut(TextView v, Runnable next) {
        canSkip = false;
        v.animate()
                .alpha(0f)
                .setDuration(FADE_OUT_MS)
                .withEndAction(next)
                .start();
    }

    private void finishOnboarding() {
        handler.removeCallbacksAndMessages(null);
        new PrefsManager(this).setFirstLaunchDone();
        startActivity(new Intent(this, HomeActivity.class));
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
