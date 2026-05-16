package com.example.qualia.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import com.example.qualia.R;
import com.example.qualia.data.model.Session;
import com.example.qualia.data.repository.SessionRepository;
import com.example.qualia.util.PrefsManager;
import com.example.qualia.util.SoundManager;

import java.util.List;

public class ClosingActivity extends BaseActivity {

    /** How long the closing exhale takes. Mirrors the long exhale in BreathActivity. */
    private static final long CLOSING_EXHALE_MS = 4500L;

    /** Crisis-resource targets used by sessions flagged `heavy` in sessions.json.
     *  The Philippine hotlines are surfaced first because the app's intended
     *  user base (PLP psychology students, by way of the proposal's target
     *  population) is Philippine-based; findahelpline.com is kept as the
     *  international fallback. All three are tappable. */
    private static final String CRISIS_HELPLINE_URL = "https://findahelpline.com";
    /** NCMH Crisis Hotline (24/7 national mental-health hotline operated by the
     *  National Center for Mental Health). */
    private static final String CRISIS_PH_NCMH      = "1553";
    /** Hopeline PH — In Touch Community Services / 24/7 suicide-prevention line. */
    private static final String CRISIS_PH_HOPELINE  = "+639175584673";
    /** Display label for the Hopeline number (kept as it's normally written). */
    private static final String CRISIS_PH_HOPELINE_LABEL = "0917 558 4673";

    // Bind to the main thread explicitly — the no-arg constructor is deprecated.
    private final Handler handler = new Handler(Looper.getMainLooper());

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
        TextView txtClosing      = findViewById(R.id.txtClosing);
        TextView txtHeavyCrisis  = findViewById(R.id.txtHeavyCrisis);
        TextView btnJournal      = findViewById(R.id.btnJournal);
        TextView btnHome         = findViewById(R.id.btnHome);
        WeatherFlowView weatherFlow = findViewById(R.id.weatherFlow);

        // ── Closing exhale ────────────────────────────────────────────────────
        // The same flow that carried the session continues here for a few seconds,
        // drifting gently outward and dimming. The session has a beginning breath
        // and an ending breath; the body remembers both.
        weatherFlow.exhaleAndDim(CLOSING_EXHALE_MS);

        // ── Fade-in ───────────────────────────────────────────────────────────
        // "I'm here" lives alone for a full breath before the buttons arrive
        // and ask for a choice. The previous version crashed the moment by
        // showing the buttons 200ms after the text — reading it and being
        // asked to leave it in the same beat. Now the line is allowed to
        // settle, the room is quiet around it, and only then do the doors
        // appear.
        txtClosing.animate().alpha(1f).setStartDelay(400).setDuration(1000).start();
        btnJournal.animate().alpha(1f).setStartDelay(3800).setDuration(700).start();
        btnHome.animate().alpha(1f).setStartDelay(4100).setDuration(700).start();

        // "Write something" is offered, not demanded. A single, quiet warm
        // pulse after both buttons have landed — the journal button dims
        // slightly and returns, drawing the eye once without nagging. The
        // user is invited to write but never told to.
        handler.postDelayed(() ->
                btnJournal.animate().alpha(0.55f).setDuration(700)
                        .withEndAction(() ->
                                btnJournal.animate().alpha(1f).setDuration(900).start())
                        .start(),
                5800L);

        // ── Heavy session: surface the crisis line ────────────────────────────
        //
        // The proposal says the writing voice is "quiet, not absent." The
        // crisis-resource line used to live three screens deep, behind an
        // Easter-egg tap on the about screen. That is too quiet for the
        // moments that need it. Now, for sessions explicitly authored as
        // heavy (sessions.json `heavy: true` — grief, regret, the
        // unfixable, mortality), one extra line fades in below the closing
        // copy: "if today is heavy: findahelpline.com". Dim, small, never
        // narrated, never on a non-heavy session. It is there for the user
        // who needs it, invisible to the user who doesn't.
        if (isJustPlayedSessionHeavy(prefs)) {
            // Build a tappable line with three targets: NCMH 1553 (tel), the
            // Hopeline PH number (tel), and findahelpline.com (web fallback).
            // The PH numbers are first because the intended user base is
            // Philippine. The URL is kept for users outside PH. Each segment
            // has its own ClickableSpan so the tap target is the segment
            // itself, not the whole line.
            applyHeavyCrisisLine(txtHeavyCrisis);
            txtHeavyCrisis.setVisibility(View.VISIBLE);
            txtHeavyCrisis.animate().alpha(1f).setStartDelay(4800).setDuration(1100).start();
        }

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

    /**
     * Looks up the most-recently played session (the one whose closing
     * screen the user is currently looking at) and returns whether it was
     * authored as heavy in sessions.json. Returns false if anything is
     * missing — better to under-surface the crisis line than to over-surface
     * it. The "last session" comes from the same comma-list SessionActivity
     * writes at session start, so the latest entry is guaranteed to be the
     * one that just finished playing.
     */
    private boolean isJustPlayedSessionHeavy(PrefsManager prefs) {
        try {
            String[] recent = prefs.getLastSessions();
            if (recent == null || recent.length == 0) return false;
            String justPlayed = recent[recent.length - 1];
            if (justPlayed == null || justPlayed.isEmpty()) return false;

            SessionRepository repo = new SessionRepository(this);
            List<Session> all = repo.getAll();
            if (all == null) return false;
            for (Session s : all) {
                if (s != null && justPlayed.equals(s.key)) return s.heavy;
            }
        } catch (Exception ignored) {
            // Defensive — if any of this throws we silently skip the line.
            // The closing screen is the wrong place to crash on a metadata
            // lookup.
        }
        return false;
    }

    /**
     * Builds the multi-target heavy-session crisis line. The TextView gets a
     * three-line block:
     *
     * <pre>
     *   if today is heavy
     *   1553  ·  0917 558 4673
     *   or findahelpline.com
     * </pre>
     *
     * Each of the three crisis targets is its own tappable span. The label
     * line is plain. Tapping a number launches the dialer (the dialer
     * pre-fills the number; the user still has to press call, so this is not
     * a sneaky dial-out). Tapping the URL opens the system browser.
     */
    private void applyHeavyCrisisLine(TextView tv) {
        final int dimColor = 0xFF6B6560;
        final int linkColor = 0xFFA89880;

        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append("if today is heavy\n");

        int ncmhStart = sb.length();
        sb.append(CRISIS_PH_NCMH);
        sb.setSpan(new ClickableSpan() {
            @Override public void onClick(View v) { dial(CRISIS_PH_NCMH); }
        }, ncmhStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(linkColor),
                ncmhStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        sb.append("   \u00B7   ");   // middle dot, spaced

        int hopelineStart = sb.length();
        sb.append(CRISIS_PH_HOPELINE_LABEL);
        sb.setSpan(new ClickableSpan() {
            @Override public void onClick(View v) { dial(CRISIS_PH_HOPELINE); }
        }, hopelineStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(linkColor),
                hopelineStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        sb.append("\nor ");

        int urlStart = sb.length();
        sb.append("findahelpline.com");
        sb.setSpan(new ClickableSpan() {
            @Override public void onClick(View v) { openHelpline(); }
        }, urlStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(linkColor),
                urlStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        tv.setText(sb);
        tv.setTextColor(dimColor);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * Launches the system dialer with the given number pre-filled. We use
     * ACTION_DIAL rather than ACTION_CALL so the user still has to confirm
     * the call — this also avoids requiring the CALL_PHONE permission, which
     * we never want to ask for in a meditation app.
     */
    private void dial(String phoneNumber) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {
            // Defensive — if no dialer is installed (extremely unlikely on a
            // phone) the tap is a no-op rather than a crash. The text on
            // screen is still the resource.
        }
    }

    private void openHelpline() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(CRISIS_HELPLINE_URL));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {
            // If there's no browser, or the OS refuses, we don't want to
            // crash the closing screen. The text itself is the resource;
            // the user can copy it from memory if they need to.
        }
    }
}
