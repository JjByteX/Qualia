package com.example.qualia.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.example.qualia.R;
import com.example.qualia.util.PrefsManager;

public class ClosingActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_closing);

        PrefsManager prefs = new PrefsManager(this);
        prefs.incrementSessionCount();

        TextView txtClosing = findViewById(R.id.txtClosing);
        TextView btnJournal = findViewById(R.id.btnJournal);
        TextView btnHome = findViewById(R.id.btnHome);

        txtClosing.animate().alpha(1f).setStartDelay(400).setDuration(800).start();
        btnJournal.animate().alpha(1f).setStartDelay(1200).setDuration(600).start();
        btnHome.animate().alpha(1f).setStartDelay(1400).setDuration(600).start();

        // Check for graduation
        if (prefs.hasGraduated()) {
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