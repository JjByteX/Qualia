package com.example.qualia.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.example.qualia.R;
import com.example.qualia.util.PrefsManager;

public class OnboardingActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        TextView txtWelcome = findViewById(R.id.txtWelcome);
        TextView btnBegin = findViewById(R.id.btnBegin);

        txtWelcome.animate().alpha(1f).setStartDelay(400).setDuration(800).start();
        btnBegin.animate().alpha(1f).setStartDelay(1400).setDuration(800).start();

        btnBegin.setOnClickListener(v -> {
            new PrefsManager(this).setFirstLaunchDone();
            startActivity(new Intent(this, HomeActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        });
    }
}