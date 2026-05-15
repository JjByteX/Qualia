package com.example.qualia.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.example.qualia.R;
import com.example.qualia.util.SoundManager;

public class BreathActivity extends BaseActivity {

    private final Handler handler = new Handler();

    // Request location permission on first breath screen — it's the natural
    // moment before sound starts (user is already settling in).
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                // Whether granted or denied, start sound — SoundManager falls back to
                // a default sound if location is unavailable.
                SoundManager.start(this);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_breath);

        TextView txt1     = findViewById(R.id.txtBreath1);
        TextView txt2     = findViewById(R.id.txtBreath2);
        TextView txt3     = findViewById(R.id.txtBreath3);
        TextView btnReady = findViewById(R.id.btnReady);

        // Request location, then start sound
        requestLocationAndStartSound();

        // Breath guidance timing (unchanged)
        handler.postDelayed(() -> txt1.animate().alpha(1f).setDuration(800).start(), 600);
        handler.postDelayed(() -> txt2.animate().alpha(1f).setDuration(800).start(), 3800);
        handler.postDelayed(() -> txt3.animate().alpha(1f).setDuration(800).start(), 6200);
        handler.postDelayed(() -> btnReady.animate().alpha(1f).setDuration(800).start(), 15000);

        btnReady.setOnClickListener(v -> {
            handler.removeCallbacksAndMessages(null);
            startActivity(new Intent(this, SessionActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        });
    }

    private void requestLocationAndStartSound() {
        boolean hasFine   = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (hasFine || hasCoarse) {
            SoundManager.start(this);
        } else {
            // Ask — the callback calls SoundManager.start() regardless of outcome
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}