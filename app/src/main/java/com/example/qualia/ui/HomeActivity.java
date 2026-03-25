package com.example.qualia.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.example.qualia.R;
import com.example.qualia.util.PrefsManager;

public class HomeActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        PrefsManager prefs = new PrefsManager(this);

        TextView txtGreeting = findViewById(R.id.txtGreeting);
        TextView btnStart = findViewById(R.id.btnStart);
        TextView btnJournal = findViewById(R.id.btnJournal);
        TextView txtCount = findViewById(R.id.txtCount);

        // Subtle counter — only shows after first session
        int count = prefs.getSessionCount();
        if (count > 0) {
            txtCount.setText(count + " of 70");
            txtCount.animate().alpha(1f).setStartDelay(1600).setDuration(600).start();
        }

        txtGreeting.animate().alpha(1f).setStartDelay(300).setDuration(800).start();
        btnStart.animate().alpha(1f).setStartDelay(900).setDuration(600).start();
        btnJournal.animate().alpha(1f).setStartDelay(1100).setDuration(600).start();

        btnStart.setOnClickListener(v -> {
            startActivity(new Intent(this, BreathActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        btnJournal.setOnClickListener(v -> {
            startActivity(new Intent(this, JournalActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });
    }
}