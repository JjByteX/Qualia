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

/**
 * Home uses progressive disclosure: the interface gets fuller as the user's
 * relationship with the app deepens. A first-time visitor sees only the
 * greeting and "begin" — no journal button they can't use, no counter
 * they don't have a number for, no toggles for features they haven't met.
 *
 * <pre>
 *   Session 0 (first ever visit):   greeting + begin
 *   Session 1+:                     + journal, + day X of 70
 *   Session 2+:                     + count (X of 70) [bottom-right; tap → About]
 *   Session 3+:                     + sound toggle
 *   After session 70 (graduation):  greeting + "sit" + "what was said" + journal + toggles
 * </pre>
 *
 * <p>v5 additions:
 * <ul>
 *   <li>{@code txtDayCount} — a tiny, dim "day X of 70" line just under
 *       the start button. Tells the user where they are in the arc
 *       without streak-shaming them. Hidden first session, hidden after
 *       graduation.</li>
 *   <li>{@code btnSit} — replaces the missing start button after
 *       graduation. Opens {@link RoomActivity}: presence only, no
 *       progression, no counter increment. The "door stays open" the
 *       proposal promises.</li>
 *   <li>{@code btnArchive} — "what was said". Opens
 *       {@link PastSessionsActivity}: the writing the user has sat
 *       with, faded by age. Only visible after graduation.</li>
 * </ul>
 *
 * <p>The home screen remembers the user. The interface is its own kind of
 * acknowledgement — "we have done this together before."
 */
public class HomeActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        PrefsManager prefs = new PrefsManager(this);

        TextView txtGreeting = findViewById(R.id.txtGreeting);
        TextView btnStart    = findViewById(R.id.btnStart);
        TextView txtDayCount = findViewById(R.id.txtDayCount);
        TextView btnSit      = findViewById(R.id.btnSit);
        TextView btnJournal  = findViewById(R.id.btnJournal);
        TextView btnArchive  = findViewById(R.id.btnArchive);
        TextView txtCount    = findViewById(R.id.txtCount);
        TextView btnMute     = findViewById(R.id.btnMute);
        WeatherFlowView weatherFlow = findViewById(R.id.weatherFlow);

        // Home wears the dust at half density — quieter than a session, so the
        // first time the flow leans toward you is in Breath. The visual language
        // is already here from the moment you open the app.
        if (weatherFlow != null) weatherFlow.setDensityMode(WeatherFlowView.DENSITY_HALF);

        boolean graduated     = prefs.hasGraduated();
        boolean doneToday     = prefs.wasSessionDoneToday();
        boolean sessionLocked = graduated || doneToday;
        int     count         = prefs.getSessionCount();
        int     threshold     = prefs.getGraduationThreshold();

        // ── Progressive disclosure tiers ───────────────────────────────────
        // The interface reveals itself as the user's history grows.
        boolean showJournal  = count >= 1;
        boolean showDayCount = count >= 1 && !graduated;
        boolean showCount    = count >= 2 && !graduated;
        boolean showToggles  = count >= 3;

        if (doneToday && !graduated) txtGreeting.setText("You were here today.");
        if (graduated)              txtGreeting.setText("The room is still here.");

        // ── Counter ────────────────────────────────────────────────────────
        // The counter is shown only after a couple of sessions, but the tap
        // target that opens the small about sheet is always active — even
        // before the counter itself becomes visible. The view stays in the
        // layout at alpha 0 (not GONE, not INVISIBLE — INVISIBLE views drop
        // touch events), so the bottom-right corner is always a quiet door.
        if (showCount) {
            txtCount.setText(count + " of " + threshold);
        }
        // No setVisibility here: the view is laid out, transparent on first
        // launch (alpha=0 from XML), and still receives clicks.
        txtCount.setOnClickListener(v -> {
            startActivity(new Intent(this, AboutActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        // ── Start / locked / graduated ─────────────────────────────────────
        if (graduated) {
            // Graduated — the course is over, but the door stays open.
            // btnStart is gone for good; the "sit" door takes its place
            // (presence only, no progression). The arc is also over, so
            // the day count goes away too.
            btnStart.setVisibility(View.GONE);
            btnSit.setVisibility(View.VISIBLE);
            btnSit.setOnClickListener(v -> {
                startActivity(new Intent(this, RoomActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            });
        } else if (sessionLocked) {
            // Done today, not yet graduated — start button is gone until
            // tomorrow, but the day-count text remains so the user can see
            // where they are in the arc on a rest day.
            btnStart.setVisibility(View.GONE);
        } else {
            pickNextSession(prefs);
            btnStart.setOnClickListener(v -> {
                startActivity(new Intent(this, BreathActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            });
        }

        // ── Day X of 70 ────────────────────────────────────────────────────
        // The position-in-arc indicator. Quiet, factual. We show the day
        // the user is currently in:
        //   - if they have already done today's session, that's count.
        //   - if they haven't, the upcoming session is count + 1.
        // Clamped to threshold so we never display "day 71 of 70" if the
        // arithmetic ever drifts.
        if (showDayCount) {
            int day = doneToday ? count : Math.min(threshold, count + 1);
            txtDayCount.setText("day " + day + " of " + threshold);
            txtDayCount.setVisibility(View.VISIBLE);
        }

        // ── Journal ────────────────────────────────────────────────────────
        if (showJournal) {
            btnJournal.setOnClickListener(v -> {
                startActivity(new Intent(this, JournalActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            });
        } else {
            btnJournal.setVisibility(View.GONE);
        }

        // ── Post-graduation archive ────────────────────────────────────────
        if (graduated) {
            btnArchive.setVisibility(View.VISIBLE);
            btnArchive.setOnClickListener(v -> {
                startActivity(new Intent(this, PastSessionsActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            });
        }

        // ── Sound toggle ───────────────────────────────────────────────────
        // (Voice toggle removed — voice is mandatory now.)
        if (showToggles) {
            updateMuteLabel(btnMute, prefs.isSoundMuted());
            btnMute.setOnClickListener(v -> {
                boolean nowMuted = !prefs.isSoundMuted();
                prefs.setSoundMuted(nowMuted);
                updateMuteLabel(btnMute, nowMuted);
            });
        } else {
            btnMute.setVisibility(View.GONE);
        }

        // ── Animations ─────────────────────────────────────────────────────
        // The greeting lands first, then the primary action (begin session,
        // or "sit" after graduation) arrives as the second beat — the reason
        // you opened the app should not be the last thing to appear.
        // Secondary tiers (journal, count, toggles) trickle in afterwards,
        // in spatial order so the eye sweeps top-to-bottom once. Hidden
        // tiers don't animate (they're GONE).
        txtGreeting.animate().alpha(1f).setStartDelay(400).setDuration(1000).start();

        long secondaryBase;
        if (graduated) {
            // The "sit" door is the primary action after graduation. Same
            // arrival as the begin button used to have.
            btnSit.animate().alpha(1f).setStartDelay(1700).setDuration(900).start();
            secondaryBase = 3000;
        } else if (!sessionLocked) {
            btnStart.animate().alpha(1f).setStartDelay(1700).setDuration(900).start();
            secondaryBase = 3000;
        } else {
            secondaryBase = 2200;
        }

        if (showDayCount) {
            // Day count lands a beat after the primary action, in the same
            // upper-third column. Quiet enough that it never competes with
            // "begin session" / "sit"; close enough that the eye reads them
            // as a single thought.
            txtDayCount.animate().alpha(1f)
                    .setStartDelay(secondaryBase - 300)
                    .setDuration(700).start();
        }
        if (showJournal) {
            btnJournal.animate().alpha(1f)
                    .setStartDelay(secondaryBase)
                    .setDuration(700).start();
        }
        if (graduated) {
            btnArchive.animate().alpha(1f)
                    .setStartDelay(secondaryBase + 200)
                    .setDuration(700).start();
        }
        if (showCount) {
            txtCount.animate().alpha(1f)
                    .setStartDelay(secondaryBase + 400)
                    .setDuration(700).start();
        }
        if (showToggles) {
            btnMute.animate().alpha(1f)
                    .setStartDelay(secondaryBase + 700)
                    .setDuration(700).start();
        }
    }

    /**
     * Picks the next session and stores the key in prefs so SessionActivity
     * can reuse it without re-picking. No-op if a key is already stored.
     */
    private void pickNextSession(PrefsManager prefs) {
        if (prefs.getPreloadedSessionKey() != null) return;
        SessionRepository repo    = new SessionRepository(this);
        // v6: pass session count so the first-three heavy-guard runs.
        Session           session = SessionPicker.pick(repo.getAll(),
                                                       prefs.getLastSessions(),
                                                       prefs.getSessionCount());
        if (session == null) return;
        prefs.setPreloadedSessionKey(session.key);
    }

    private void updateMuteLabel(TextView btn, boolean muted) {
        btn.setText(muted ? "sound off" : "sound on");
    }
}
