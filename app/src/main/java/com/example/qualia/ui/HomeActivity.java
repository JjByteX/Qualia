package com.example.qualia.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.example.qualia.R;
import com.example.qualia.data.model.Session;
import com.example.qualia.data.repository.SessionRepository;
import com.example.qualia.util.PrefsManager;
import com.example.qualia.util.SessionPicker;

public class HomeActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        PrefsManager prefs = new PrefsManager(this);

        TextView txtGreeting = findViewById(R.id.txtGreeting);
        TextView btnStart    = findViewById(R.id.btnStart);
        TextView btnJournal  = findViewById(R.id.btnJournal);
        TextView txtCount    = findViewById(R.id.txtCount);
        TextView btnMute     = findViewById(R.id.btnMute);
        TextView btnVoice    = findViewById(R.id.btnVoice);

        boolean graduated     = prefs.hasGraduated();
        boolean doneToday     = prefs.wasSessionDoneToday();
        boolean sessionLocked = graduated || doneToday;

        if (doneToday && !graduated) txtGreeting.setText("You were here today.");

        int count = prefs.getSessionCount();
        if (count > 0 && !graduated) {
            txtCount.setText(count + " of 70");
            txtCount.animate().alpha(1f).setStartDelay(1600).setDuration(600).start();
        }

        if (sessionLocked) {
            btnStart.setVisibility(View.GONE);
        } else {
            // Pre-pick the next session so SessionActivity can reuse the key
            pickNextSession(prefs);

            btnStart.setOnClickListener(v -> {
                startActivity(new Intent(this, BreathActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            });
        }

        btnJournal.setOnClickListener(v -> {
            startActivity(new Intent(this, JournalActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        // ── Sound toggle ──────────────────────────────────────────────────────
        updateMuteLabel(btnMute, prefs.isSoundMuted());
        btnMute.setOnClickListener(v -> {
            boolean nowMuted = !prefs.isSoundMuted();
            prefs.setSoundMuted(nowMuted);
            updateMuteLabel(btnMute, nowMuted);
        });

        // ── Voice toggle ──────────────────────────────────────────────────────
        updateVoiceLabel(btnVoice, prefs.isVoiceEnabled());
        btnVoice.setOnClickListener(v -> {
            boolean now = !prefs.isVoiceEnabled();
            prefs.setVoiceEnabled(now);
            updateVoiceLabel(btnVoice, now);
        });

        // ── Animations ────────────────────────────────────────────────────────
        txtGreeting.animate().alpha(1f).setStartDelay(300).setDuration(800).start();

        if (!sessionLocked) {
            // Deliberate friction — 5 s wait before start appears
            btnStart.animate().alpha(1f).setStartDelay(5000).setDuration(800).start();
        }

        btnJournal.animate().alpha(1f).setStartDelay(sessionLocked ? 900 : 2000).setDuration(600).start();
        btnMute.animate().alpha(1f).setStartDelay(1800).setDuration(600).start();
        btnVoice.animate().alpha(1f).setStartDelay(1900).setDuration(600).start();
    }

    /**
     * Picks the next session and stores the key in prefs so SessionActivity
     * can reuse it without re-picking. No-op if a key is already stored.
     */
    private void pickNextSession(PrefsManager prefs) {
        if (prefs.getPreloadedSessionKey() != null) return;
        SessionRepository repo    = new SessionRepository(this);
        Session           session = SessionPicker.pick(repo.getAll(), prefs.getLastSessions());
        if (session == null) return;
        prefs.setPreloadedSessionKey(session.key);
    }

    private void updateMuteLabel(TextView btn, boolean muted) {
        btn.setText(muted ? "sound off" : "sound on");
    }

    private void updateVoiceLabel(TextView btn, boolean enabled) {
        btn.setText(enabled ? "voice on" : "voice off");
    }
}
