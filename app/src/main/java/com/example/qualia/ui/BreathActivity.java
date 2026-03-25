package com.example.qualia.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.example.qualia.R;

public class BreathActivity extends BaseActivity {

    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_breath);

        TextView txt1 = findViewById(R.id.txtBreath1);
        TextView txt2 = findViewById(R.id.txtBreath2);
        TextView txt3 = findViewById(R.id.txtBreath3);
        TextView btnReady = findViewById(R.id.btnReady);

        handler.postDelayed(() -> txt1.animate().alpha(1f).setDuration(800).start(), 600);
// inhale 1 — appears, user inhales for ~3 seconds
        handler.postDelayed(() -> txt2.animate().alpha(1f).setDuration(800).start(), 3800);
// inhale 2 — appears, user takes second sip for ~2 seconds
        handler.postDelayed(() -> txt3.animate().alpha(1f).setDuration(800).start(), 6200);
// exhale — appears and STAYS for ~8-10 seconds before ready button
        handler.postDelayed(() -> btnReady.animate().alpha(1f).setDuration(800).start(), 15000);

        btnReady.setOnClickListener(v -> {
            handler.removeCallbacksAndMessages(null);
            startActivity(new Intent(this, SessionActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}