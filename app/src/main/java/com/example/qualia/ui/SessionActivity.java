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
import com.example.qualia.util.PrefsManager;
import com.example.qualia.util.SessionPicker;

import java.util.List;

public class SessionActivity extends BaseActivity {

    private static final long SESSION_DURATION = 7 * 60 * 1000L;
    private final Handler handler = new Handler();
    private TextView txtSessionLine;
    private View progressFill;
    private ObjectAnimator progressAnimator;
    private boolean isFirstLine = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);

        txtSessionLine = findViewById(R.id.txtSessionLine);
        progressFill = findViewById(R.id.progressFill);
        TextView btnEnd = findViewById(R.id.btnEnd);

        SessionRepository repo = new SessionRepository(this);
        PrefsManager prefs = new PrefsManager(this);
        Session session = SessionPicker.pick(repo.getAll(), prefs.getLastSessions());

        if (session != null) {
            prefs.saveLastSession(session.key);
            startSession(session);
        } else {
            showLine("Today probably felt normal.");
        }

        handler.postDelayed(this::goToClosing, SESSION_DURATION);
        btnEnd.setOnClickListener(v -> goToClosing());
    }

    private void startSession(Session session) {
        List<SessionLine> lines = session.lines;
        if (lines == null || lines.isEmpty()) return;

        startProgressBar();

        // Space lines evenly, leaving last 30s for closing line
        long usableTime = SESSION_DURATION - 30000L;
        long interval = usableTime / lines.size();

        showLine(lines.get(0).text);

        for (int i = 1; i < lines.size(); i++) {
            final String lineText = lines.get(i).text;
            handler.postDelayed(() -> showLine(lineText), interval * i);
        }

        if (session.closingLine != null) {
            handler.postDelayed(
                    () -> showLine(session.closingLine),
                    SESSION_DURATION - 25000L
            );
        }
    }

    private void showLine(String text) {
        if (isFirstLine) {
            isFirstLine = false;
            txtSessionLine.setText(text);
            txtSessionLine.animate().alpha(1f).setDuration(800).start();
        } else {
            txtSessionLine.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction(() -> {
                        txtSessionLine.setText(text);
                        txtSessionLine.animate().alpha(1f).setDuration(800).start();
                    }).start();
        }
    }

    private void startProgressBar() {
        progressFill.post(() -> {
            progressFill.setPivotX(0f);
            progressAnimator = ObjectAnimator.ofFloat(progressFill, "scaleX", 0f, 1f);
            progressAnimator.setDuration(SESSION_DURATION);
            progressAnimator.setInterpolator(new LinearInterpolator());
            progressAnimator.start();
        });
    }

    private void goToClosing() {
        handler.removeCallbacksAndMessages(null);
        if (progressAnimator != null) progressAnimator.cancel();
        startActivity(new Intent(this, ClosingActivity.class));
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (progressAnimator != null) progressAnimator.cancel();
    }
}