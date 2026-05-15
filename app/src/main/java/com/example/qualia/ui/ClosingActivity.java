package com.example.qualia.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.example.qualia.R;
import com.example.qualia.util.PrefsManager;
import com.example.qualia.util.SoundManager;

public class ClosingActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_closing);

        // ── Session bookkeeping ───────────────────────────────────────────────
        // Stop ambient sound — the session is over.
        SoundManager.stop();

        PrefsManager prefs = new PrefsManager(this);
        prefs.incrementSessionCount();

        // Mark today as done — hides the Start button until tomorrow.
        prefs.setSessionDoneToday();

        // ── Views ─────────────────────────────────────────────────────────────
        TextView txtClosing = findViewById(R.id.txtClosing);
        TextView btnJournal = findViewById(R.id.btnJournal);
        TextView btnHome    = findViewById(R.id.btnHome);

        // ── Fade-in ───────────────────────────────────────────────────────────
        txtClosing.animate().alpha(1f).setStartDelay(400).setDuration(800).start();
        btnJournal.animate().alpha(1f).setStartDelay(1200).setDuration(600).start();
        btnHome.animate().alpha(1f).setStartDelay(1400).setDuration(600).start();

        // ── Navigation ────────────────────────────────────────────────────────
        if (prefs.hasGraduated()) {
            // Session 70 just finished — send them to the Graduation screen.
            btnHome.setText("something feels different");
            btnHome.setOnClickListener(v -> {
                startActivity(new Intent(this, GraduationActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
            });
        } else {
            btnHome.setOnClickListener(v -> {
                startActivity(new Intent(this, HomeActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
            });
        }

        btnJournal.setOnClickListener(v -> {
            startActivity(new Intent(this, JournalActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });
    }
}
