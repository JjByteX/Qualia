package com.example.qualia.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.example.qualia.R;
import com.example.qualia.util.PrefsManager;

public class SplashActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler().postDelayed(() -> {
            PrefsManager prefs = new PrefsManager(this);
            if (prefs.isFirstLaunch()) {
                startActivity(new Intent(this, OnboardingActivity.class));
            } else {
                startActivity(new Intent(this, HomeActivity.class));
            }
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        }, 1800);
    }
}
