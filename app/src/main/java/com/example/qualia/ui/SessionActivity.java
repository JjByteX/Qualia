package com.example.qualia.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import com.example.qualia.R;
import com.example.qualia.data.model.Session;
import com.example.qualia.data.model.SessionLine;
import com.example.qualia.data.repository.SessionRepository;
import com.example.qualia.util.KokoroConfig;
import com.example.qualia.util.PrefsManager;
import com.example.qualia.util.SessionPicker;
import com.example.qualia.util.TypingAnimator;
import com.example.qualia.util.VoiceManager;

import java.util.List;

public class SessionActivity extends BaseActivity {

    private static final long SESSION_DURATION     = 7 * 60 * 1000L;
    private static final long QUESTION_AUTO_RESUME = 30_000L;

    // Safety-net fallback for the rare case where a line is missing from
    // the bundled audio cache (typo in sessions.json, etc.). The typewriter
    // animation reuses these word-count timing constants so the session
    // still paces correctly without audio for that one line.
    private static final long MS_PER_WORD  = 600L;
    private static final long MIN_DELAY_MS = 2_500L;

    // Breath between lines once audio finishes. Voice determines main duration.
    private static final long VOICE_LINE_PAUSE = 350L;

    // Always target the main thread — the no-arg Handler() constructor is
    // deprecated since API 30 and ambiguous about which Looper it'll bind to.
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView       txtSessionLine;
    private TextView       txtTapHint;
    private View           progressFill;
    private View           tapOverlay;
    private ObjectAnimator progressAnimator;
    private ValueAnimator warmthAnimator;
    private ValueAnimator closingDimAnimator;
    private ObjectAnimator tapHintPulseAnimator;
    private TypingAnimator typingAnimator;
    private WeatherFlowView weatherFlow;

    private PrefsManager prefs;

    private List<SessionLine> sessionLines;
    private int     currentLineIndex = 0;
    private boolean isFirstLine      = true;
    private boolean isPaused         = false;

    private Runnable nextLineRunnable;
    private Runnable autoResumeRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);

        txtSessionLine = findViewById(R.id.txtSessionLine);
        txtTapHint     = findViewById(R.id.txtTapHint);
        progressFill   = findViewById(R.id.progressFill);
        tapOverlay     = findViewById(R.id.tapOverlay);
        weatherFlow    = findViewById(R.id.weatherFlow);
        TextView btnEnd = findViewById(R.id.btnEnd);

        // ── Environmental layer ───────────────────────────────────────────────
        // Subliminal physiological cues — the proposal's anti-spectacle principle
        // applies to visuals too: guide via environment, never via event.
        //   - Luminance pulse at ~0.1 Hz (six breaths per minute, resonant rate).
        //   - Small downward bias to encourage gaze drop (relaxation cue).
        //   - Slow warmth drift over the seven minutes — by the closing line you
        //     are sitting by an invisible fire.
        if (weatherFlow != null) {
            weatherFlow.setLuminancePulse(true);
            weatherFlow.setDownwardBiasBoost(0.06f);
            startWarmthDrift();
        }

        typingAnimator = new TypingAnimator(handler);

        prefs = new PrefsManager(this);

        Session session = resolveSession();

        if (session != null) {
            // Tell VoiceManager which voice to use — must match what VoiceCache
            // pre-downloaded for this session key.
            VoiceManager.setSessionVoice(KokoroConfig.voiceForSession(session.key));
            prefs.saveLastSession(session.key);
            // Append to the long-term archive so the post-graduation
            // "what was said" screen has a chronological record. Done at
            // session start (not end) so a session the user walks out of
            // still counts as "sat with" — the practice happened, the
            // words landed. The picker exclusion above is separate; this
            // is the permanent record, never trimmed.
            prefs.recordSessionPlayed(session.key, System.currentTimeMillis());
            startSession(session);
        } else {
            showLine("Today probably felt normal.", false, () -> {});
        }

        handler.postDelayed(this::goToClosing, SESSION_DURATION);
        btnEnd.setOnClickListener(v -> goToClosing());

        tapOverlay.setOnClickListener(v -> {
            if (isPaused) resumeFromPause();
        });
    }

    /**
     * Belt and suspenders for the question pause tap. The original overlay
     * approach is fragile — anything that lays on top of it (a new background
     * view, a future overlay, an inset bar) will silently eat the touch. Here
     * we catch every ACTION_DOWN before child dispatch and, if we're paused,
     * advance. This guarantees the pause always responds.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (isPaused && ev.getAction() == MotionEvent.ACTION_DOWN) {
            resumeFromPause();
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    // ── Session selection ─────────────────────────────────────────────────────

    /**
     * Returns the session to play.
     *
     * If HomeActivity pre-picked a session (and pre-downloaded its audio),
     * we honour that choice so the cached files are guaranteed to match.
     * The stored key is cleared immediately so the next visit to HomeActivity
     * triggers a fresh pre-pick.
     *
     * If no pre-picked key is stored (e.g. voice was off when the user was
     * on the home screen, or they skipped straight here), we fall back to the
     * original random selection.
     */
    private Session resolveSession() {
        SessionRepository repo = new SessionRepository(this);
        List<Session> all = repo.getAll();

        String preloadedKey = prefs.getPreloadedSessionKey();
        prefs.clearPreloadedSessionKey(); // consume it regardless

        if (preloadedKey != null) {
            for (Session s : all) {
                if (preloadedKey.equals(s.key)) {
                    return s;
                }
            }
        }

        // Fallback: normal random pick. v6 passes the session count so
        // the picker's first-three heavy-guard runs (excludes sessions
        // tagged heavy:true until the user has done three sittings).
        return SessionPicker.pick(all, prefs.getLastSessions(), prefs.getSessionCount());
    }

    // ── Session flow ──────────────────────────────────────────────────────────

    private void startSession(Session session) {
        sessionLines = session.lines;
        if (sessionLines == null || sessionLines.isEmpty()) return;

        startProgressBar();

        nextLineRunnable = this::showNextLine;
        handler.postDelayed(nextLineRunnable, 800);

        if (session.closingLine != null) {
            handler.postDelayed(
                    () -> {
                        // The closing line gets its own quiet arrival: the
                        // particle field slowly dims around it (about 12s
                        // fall to ~50%), so the room visibly settles as the
                        // session ends. Not a manufactured climax — just
                        // the visual equivalent of the writing getting
                        // softer toward the close.
                        startClosingDim();
                        showClosingLine(session.closingLine);
                    },
                    SESSION_DURATION - 25_000L
            );
        }
    }

    private void startClosingDim() {
        if (weatherFlow == null) return;
        closingDimAnimator = ValueAnimator.ofFloat(1f, 0.50f);
        closingDimAnimator.setDuration(12_000L);
        closingDimAnimator.setInterpolator(new LinearInterpolator());
        closingDimAnimator.addUpdateListener(a ->
                weatherFlow.setAmbientDim((float) a.getAnimatedValue()));
        closingDimAnimator.start();
    }

    private void showNextLine() {
        if (currentLineIndex >= sessionLines.size()) return;
        SessionLine line = sessionLines.get(currentLineIndex);

        // For the safety-net path we need to know up front whether the line
        // had cached audio, because the inter-line pause is different in
        // each case (voice = small breath after audio finishes; fallback =
        // word-count reading delay so the user can finish reading the text).
        final boolean spoke = VoiceManager.hasCachedAudio(this, line.text);
        showLine(line.text, line.isQuestion, () -> {
            if (line.isQuestion) {
                enterQuestionPause();
            } else {
                currentLineIndex++;
                if (spoke) {
                    // Voice determined natural pacing — just a small breath after
                    scheduleNextLine(VOICE_LINE_PAUSE);
                } else {
                    // Missing audio: pace by reading time so the typing
                    // animation has room to be read.
                    scheduleNextLine(readingDelayMs(line.text));
                }
            }
        });
    }

    /**
     * Display a line. Voice is mandatory — every shipped line has bundled
     * audio. The {@link VoiceManager#hasCachedAudio} check is a safety net
     * for the rare case where a line is missing audio (typo in sessions.json,
     * mid-build cache gap): we fall back to the typewriter animation for
     * that one line so the session keeps flowing instead of jumping ahead in
     * silence. {@code onAfterSpeech} fires when audio finishes (voice path)
     * or when typing completes (fallback path).
     *
     * Question lines render in italics. This is the only visual signal the
     * user gets that the current line is one they're meant to sit with;
     * the body/eye picks up the difference without anyone naming it.
     */
    private void showLine(String text, boolean isQuestion, Runnable onAfterSpeech) {
        applyTypeface(isQuestion);
        if (VoiceManager.hasCachedAudio(this, text)) {
            displayInstant(text, () ->
                    VoiceManager.speak(this, text, () ->
                            runOnUiThread(onAfterSpeech)));
        } else {
            typeText(text, isQuestion, onAfterSpeech::run);
        }
    }

    /**
     * The closing line earns a slower, more deliberate arrival than any
     * other line in the session. The field has already started dimming
     * around it (see startClosingDim). Here we fade out the previous line
     * over a longer interval (800ms) and reveal the closing line at full
     * over 2.4 seconds — about three times the normal speed. The user
     * feels the room settle without being told the session is ending.
     */
    private void showClosingLine(String text) {
        applyTypeface(false);
        typingAnimator.cancel();
        Runnable reveal = () -> {
            txtSessionLine.setText(text);
            txtSessionLine.animate().alpha(1f).setDuration(2400)
                    .withEndAction(() -> {
                        // Voice is mandatory; fire-and-forget the audio. If
                        // the asset is missing we just let the line sit on
                        // screen — the closing tone is the dim, not the
                        // narration, and silence at the end is fine.
                        VoiceManager.speak(this, text, () -> {});
                    }).start();
        };
        if (isFirstLine) {
            isFirstLine = false;
            txtSessionLine.setAlpha(0f);
            reveal.run();
        } else {
            txtSessionLine.animate().alpha(0f).setDuration(800)
                    .withEndAction(reveal).start();
        }
    }

    private void applyTypeface(boolean isQuestion) {
        Typeface base = txtSessionLine.getTypeface();
        txtSessionLine.setTypeface(base, isQuestion ? Typeface.ITALIC : Typeface.NORMAL);
    }

    // ── Question pause ────────────────────────────────────────────────────────

    private void enterQuestionPause() {
        isPaused = true;
        tapOverlay.setVisibility(View.VISIBLE);
        txtTapHint.animate().cancel();
        // The tap hint breathes — a slow alpha pulse (~2.4s per cycle) that
        // tells the eye "this is alive, this is waiting for you." Static
        // hints read as broken; breathing hints read as invitation.
        txtTapHint.setAlpha(0f);
        txtTapHint.animate().alpha(0.50f).setStartDelay(400).setDuration(800)
                .withEndAction(this::startTapHintPulse).start();
        autoResumeRunnable = this::resumeFromPause;
        handler.postDelayed(autoResumeRunnable, QUESTION_AUTO_RESUME);
    }

    private void startTapHintPulse() {
        if (!isPaused) return;
        if (tapHintPulseAnimator != null) tapHintPulseAnimator.cancel();
        tapHintPulseAnimator = ObjectAnimator.ofFloat(txtTapHint, "alpha", 0.50f, 0.22f);
        tapHintPulseAnimator.setDuration(2400L);
        tapHintPulseAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        tapHintPulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        tapHintPulseAnimator.start();
    }

    private void resumeFromPause() {
        if (!isPaused) return;
        isPaused = false;
        tapOverlay.setVisibility(View.GONE);
        if (autoResumeRunnable != null) {
            handler.removeCallbacks(autoResumeRunnable);
            autoResumeRunnable = null;
        }
        if (tapHintPulseAnimator != null) {
            tapHintPulseAnimator.cancel();
            tapHintPulseAnimator = null;
        }
        txtTapHint.animate().alpha(0f).setDuration(400).start();
        currentLineIndex++;
        scheduleNextLine(600);
    }

    private void scheduleNextLine(long delay) {
        nextLineRunnable = this::showNextLine;
        handler.postDelayed(nextLineRunnable, delay);
    }

    // ── Text rendering ────────────────────────────────────────────────────────

    /** Voice path: show full text immediately, no typewriter — audio narrates. */
    private void displayInstant(String text, Runnable onShown) {
        typingAnimator.cancel();
        if (isFirstLine) {
            isFirstLine = false;
            txtSessionLine.setAlpha(0f);
            txtSessionLine.setText(text);
            txtSessionLine.animate().alpha(1f).setDuration(600)
                    .withEndAction(onShown).start();
        } else {
            txtSessionLine.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> {
                        txtSessionLine.setText(text);
                        txtSessionLine.animate().alpha(1f).setDuration(600)
                                .withEndAction(onShown).start();
                    }).start();
        }
    }

    /** Safety-net fallback when bundled audio for this line is missing. */
    private void typeText(String text, boolean isQuestion, TypingAnimator.OnComplete onDone) {
        typingAnimator.cancel();
        if (isFirstLine) {
            isFirstLine = false;
            txtSessionLine.setAlpha(1f);
            txtSessionLine.setText("");
            typingAnimator.type(txtSessionLine, text, onDone);
        } else {
            txtSessionLine.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> {
                        txtSessionLine.setText("");
                        txtSessionLine.setAlpha(1f);
                        typingAnimator.type(txtSessionLine, text, onDone);
                    }).start();
        }
    }

    // ── Reading time (safety-net fallback when audio is missing) ──────────────

    private static long readingDelayMs(String text) {
        if (text == null || text.isEmpty()) return MIN_DELAY_MS;
        int words = text.trim().split("\\s+").length;
        return Math.max(MIN_DELAY_MS, words * MS_PER_WORD);
    }

    // ── Warmth drift ──────────────────────────────────────────────────────────

    /**
     * Drift particle hue toward warm amber over the seven minutes. Imperceptible
     * minute-to-minute, but the closing line lives in a slightly different room
     * from the opening line.
     */
    private void startWarmthDrift() {
        warmthAnimator = ValueAnimator.ofFloat(0f, 0.70f);
        warmthAnimator.setDuration(SESSION_DURATION);
        warmthAnimator.setInterpolator(new LinearInterpolator());
        warmthAnimator.addUpdateListener(a -> {
            if (weatherFlow != null) weatherFlow.setWarmth((float) a.getAnimatedValue());
        });
        warmthAnimator.start();
    }

    // ── Progress bar ──────────────────────────────────────────────────────────

    private void startProgressBar() {
        progressFill.post(() -> {
            progressFill.setPivotX(0f);
            progressAnimator = ObjectAnimator.ofFloat(progressFill, "scaleX", 0f, 1f);
            progressAnimator.setDuration(SESSION_DURATION);
            progressAnimator.setInterpolator(new LinearInterpolator());
            progressAnimator.start();
        });
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void goToClosing() {
        handler.removeCallbacksAndMessages(null);
        typingAnimator.cancel();
        VoiceManager.stop();
        if (progressAnimator != null) progressAnimator.cancel();
        if (warmthAnimator != null) warmthAnimator.cancel();
        if (closingDimAnimator != null) closingDimAnimator.cancel();
        if (tapHintPulseAnimator != null) tapHintPulseAnimator.cancel();
        startActivity(new Intent(this, ClosingActivity.class));
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        typingAnimator.cancel();
        VoiceManager.release();
        if (progressAnimator != null) progressAnimator.cancel();
        if (warmthAnimator != null) warmthAnimator.cancel();
        if (closingDimAnimator != null) closingDimAnimator.cancel();
        if (tapHintPulseAnimator != null) tapHintPulseAnimator.cancel();
    }
}
