package com.example.qualia.ui;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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

    // Used only when voice is OFF — word-count based reading delay
    private static final long MS_PER_WORD  = 600L;
    private static final long MIN_DELAY_MS = 2_500L;

    // Short breath between lines when voice is ON (audio determines main duration)
    private static final long VOICE_LINE_PAUSE = 350L;

    private final Handler handler = new Handler();

    private TextView       txtSessionLine;
    private TextView       txtTapHint;
    private View           progressFill;
    private View           tapOverlay;
    private ObjectAnimator progressAnimator;
    private TypingAnimator typingAnimator;

    private PrefsManager prefs;
    private boolean      voiceEnabled;

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
        TextView btnEnd = findViewById(R.id.btnEnd);

        typingAnimator = new TypingAnimator(handler);

        prefs        = new PrefsManager(this);
        voiceEnabled = prefs.isVoiceEnabled();

        Session session = resolveSession();

        if (session != null) {
            // Tell VoiceManager which voice to use — must match what VoiceCache
            // pre-downloaded for this session key.
            VoiceManager.setSessionVoice(KokoroConfig.voiceForSession(session.key));
            prefs.saveLastSession(session.key);
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

        // Fallback: normal random pick
        return SessionPicker.pick(all, prefs.getLastSessions());
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
                    () -> showLine(session.closingLine, false, () -> {}),
                    SESSION_DURATION - 25_000L
            );
        }
    }

    private void showNextLine() {
        if (currentLineIndex >= sessionLines.size()) return;
        SessionLine line = sessionLines.get(currentLineIndex);

        showLine(line.text, line.isQuestion, () -> {
            if (line.isQuestion) {
                enterQuestionPause();
            } else {
                currentLineIndex++;
                if (voiceEnabled) {
                    // Voice determines natural pacing — just a small breath after
                    scheduleNextLine(VOICE_LINE_PAUSE);
                } else {
                    scheduleNextLine(readingDelayMs(line.text));
                }
            }
        });
    }

    /**
     * Display a line and speak it. onAfterSpeech fires when:
     *   — voice ON:  after the audio finishes playing
     *   — voice OFF: immediately after the typing animation completes
     */
    private void showLine(String text, boolean isQuestion, Runnable onAfterSpeech) {
        if (voiceEnabled) {
            // Show the full text instantly — the voice carries it
            displayInstant(text, () ->
                    VoiceManager.speak(this, text, prefs, () ->
                            runOnUiThread(onAfterSpeech)));
        } else {
            typeText(text, isQuestion, onAfterSpeech::run);
        }
    }

    // ── Question pause ────────────────────────────────────────────────────────

    private void enterQuestionPause() {
        isPaused = true;
        tapOverlay.setVisibility(View.VISIBLE);
        txtTapHint.animate().cancel();
        txtTapHint.animate().alpha(0.45f).setStartDelay(400).setDuration(600).start();
        autoResumeRunnable = this::resumeFromPause;
        handler.postDelayed(autoResumeRunnable, QUESTION_AUTO_RESUME);
    }

    private void resumeFromPause() {
        if (!isPaused) return;
        isPaused = false;
        tapOverlay.setVisibility(View.GONE);
        if (autoResumeRunnable != null) {
            handler.removeCallbacks(autoResumeRunnable);
            autoResumeRunnable = null;
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

    /** Voice mode: show full text immediately, no typewriter. */
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

    /** No-voice mode: original typewriter animation. */
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

    // ── Reading time (voice OFF only) ─────────────────────────────────────────

    private static long readingDelayMs(String text) {
        if (text == null || text.isEmpty()) return MIN_DELAY_MS;
        int words = text.trim().split("\\s+").length;
        return Math.max(MIN_DELAY_MS, words * MS_PER_WORD);
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
    }
}
