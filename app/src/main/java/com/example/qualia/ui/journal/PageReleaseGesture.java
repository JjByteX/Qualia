package com.example.qualia.ui.journal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Hold-to-fade gesture handler for the journal page's "let go" button.
 *
 * <p>The button isn't a tap target — it's a long-press whose duration is the
 * gesture. As long as the user keeps holding, the page frame fades toward
 * fully transparent; when it reaches zero, {@link #onCommit} fires and the
 * page is gone. Releasing early springs the alpha back to 1. Haptic feedback
 * fires at the start of the hold so the user feels the page commit to
 * "trying to leave."
 *
 * <p>Encapsulates {@code pageReleasing} flag + the {@link ValueAnimator}, so
 * the activity doesn't have to manage either. {@link #attach()} installs the
 * touch listener; the activity should call {@link #detach()} from
 * {@code onDestroy} if it cares about leaking the listener (the gesture
 * cleans itself up on commit).
 */
public final class PageReleaseGesture {

    private final View button;
    private final View pageFrame;
    private final long holdMs;
    private final Runnable onCommit;

    private boolean       releasing;
    private ValueAnimator anim;

    /**
     * @param button    the view the user holds to release the page
     * @param pageFrame the view whose alpha is animated toward 0 during the hold
     * @param holdMs    maximum hold duration before commit fires
     * @param onCommit  called on the main thread once the fade reaches 0 — the
     *                  activity should use this to delete scratch files and
     *                  finish() the screen
     */
    public PageReleaseGesture(View button, View pageFrame, long holdMs, Runnable onCommit) {
        this.button    = button;
        this.pageFrame = pageFrame;
        this.holdMs    = holdMs;
        this.onCommit  = onCommit;
    }

    /** Installs the touch listener. Call once from {@code onCreate}. */
    public void attach() {
        button.setOnTouchListener((v, ev) -> onTouch(ev));
    }

    /** Removes the listener. Optional — only useful if you keep the activity
     *  but want the gesture to stop responding. */
    public void detach() {
        button.setOnTouchListener(null);
        if (anim != null) { anim.cancel(); anim = null; }
        releasing = false;
    }

    /** True while a hold is currently in flight. Activities can use this to
     *  decide whether to let other input through. */
    public boolean isReleasing() { return releasing; }

    // ── Implementation ────────────────────────────────────────────────────────

    private boolean onTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                begin();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!releasing) return false;
                cancel();
                return true;
            default:
                return false;
        }
    }

    private void begin() {
        if (releasing) return;
        releasing = true;
        button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

        if (anim != null) anim.cancel();
        float startAlpha = pageFrame.getAlpha();
        anim = ValueAnimator.ofFloat(startAlpha, 0f);
        // Scale the duration by current alpha so a re-press picks up where
        // the previous hold left off (instead of starting a full hold from
        // a faded page).
        anim.setDuration((long) (holdMs * startAlpha));
        anim.addUpdateListener(va ->
                pageFrame.setAlpha((float) va.getAnimatedValue()));
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(Animator a) {
                if (!cancelled) {
                    // Disable further input so we don't double-fire on
                    // sub-millisecond DOWN events between commit and finish.
                    button.setOnTouchListener(null);
                    button.setEnabled(false);
                    onCommit.run();
                }
            }
        });
        anim.start();
    }

    private void cancel() {
        releasing = false;
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
        pageFrame.animate().alpha(1f).setDuration(220).start();
    }
}
