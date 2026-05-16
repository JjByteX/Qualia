package com.example.qualia.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.example.qualia.R;

/**
 * "The room." A post-graduation presence-only screen.
 *
 * <p>The proposal frames Qualia as "designed to be completed and left
 * behind" — and that's still true. The graduation letter is still the end
 * of the course. But the proposal also says "the door stays open. you
 * walk through when you want to." Before this screen existed, the door
 * wasn't open: after session seventy, the home screen lost its session
 * button and offered no way back to the practice at all. The cliff was
 * clean, but cold.
 *
 * <p>This is the open door. No counter, no progression, no audio, no
 * timer. The particle field that carried every session is the whole
 * screen. The user sits with it for as long as they want. They leave when
 * they leave.
 *
 * <p>Rules of the room:
 * <ul>
 *   <li>Reachable only after graduation (gated by HomeActivity).</li>
 *   <li>Never increments the session count.</li>
 *   <li>Never touches the daily gate. The user can return any time, any
 *       number of times. This is not a sequel — it is a room.</li>
 *   <li>No voice. No closing line. No "what was said." The field is the
 *       whole thing.</li>
 *   <li>A single low-contrast "leave" caption appears after ~20 seconds
 *       so the screen is not a trap. The Android back button always
 *       works.</li>
 * </ul>
 */
public class RoomActivity extends BaseActivity {

    /** How long the user sits in the room before the quiet "leave" caption
     *  fades in. Long enough that the field is the whole screen for a real
     *  beat; short enough that the user is never stranded. */
    private static final long LEAVE_DELAY_MS = 20_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        WeatherFlowView weatherFlow = findViewById(R.id.weatherFlow);
        TextView        btnLeave    = findViewById(R.id.btnLeave);

        // Half density — the same level the home screen runs at. The room is
        // not louder than the home; it is the same field, with nothing else
        // on the screen.
        if (weatherFlow != null) weatherFlow.setDensityMode(WeatherFlowView.DENSITY_HALF);

        // The "leave" caption fades in long after the user has settled. The
        // user is meant to sit with the field first; the exit is just there
        // so they're not stranded. Tapping leaves the room the same way
        // hitting back does — silently, without ceremony.
        handler.postDelayed(() ->
                        btnLeave.animate().alpha(1f).setDuration(1400).start(),
                LEAVE_DELAY_MS);

        btnLeave.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
