package com.example.qualia.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import com.example.qualia.R;

/**
 * A small, plain about/context sheet reachable from the home screen by
 * tapping the X-of-70 counter.
 *
 * <p>Rewritten in v5 to lift the actual writing voice of the project
 * proposal — the "small clearing in that noise" framing and the "door
 * stays open. you walk through when you want to." sentence — into the
 * place a new user looks when they ask "what is this?" The structure is
 * the same as before (what it is → what it isn't → roots → who we are →
 * crisis), with the proposal's own language doing the heavy lifting on
 * the first two blocks and a new "why we built this" block sitting above
 * the crisis line.
 *
 * <p>Updated in v6: the crisis line now surfaces three tappable targets
 * (NCMH 1553, Hopeline PH, findahelpline.com) instead of a single URL.
 * The Philippine numbers come first because the intended user base is
 * Philippine; the URL is kept as the international fallback. Same
 * approach as ClosingActivity#applyHeavyCrisisLine.
 */
public class AboutActivity extends BaseActivity {

    /** International fallback — routes to local resources by IP geolocation. */
    private static final String CRISIS_HELPLINE_URL      = "https://findahelpline.com/";
    /** NCMH Crisis Hotline (24/7 national mental-health hotline). */
    private static final String CRISIS_PH_NCMH           = "1553";
    /** Hopeline PH — In Touch Community Services / 24/7 suicide-prevention line. */
    private static final String CRISIS_PH_HOPELINE       = "+639175584673";
    /** Display label for Hopeline (kept in its usual format). */
    private static final String CRISIS_PH_HOPELINE_LABEL = "0917 558 4673";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        WeatherFlowView weatherFlow = findViewById(R.id.weatherFlow);
        if (weatherFlow != null) weatherFlow.setDensityMode(WeatherFlowView.DENSITY_LOW);

        TextView txtAbout    = findViewById(R.id.txtAbout);
        TextView txtNot      = findViewById(R.id.txtNot);
        TextView txtRoots    = findViewById(R.id.txtRoots);
        TextView txtWho      = findViewById(R.id.txtWho);
        TextView txtCrisis   = findViewById(R.id.txtCrisis);
        TextView btnBack     = findViewById(R.id.btnBack);

        // Each block fades in 400ms apart — the same staggered arrival used
        // on the home screen so the sheet reads like part of the same room.
        // The lead line lasts 1000ms (matching the Home greeting / Closing
        // line / Onboarding lines) so it has the same "first breath" weight
        // as the rest of the app's lead text. The new "who we are" block
        // slots in between roots and crisis at the same cadence.
        txtAbout .animate().alpha(1f).setStartDelay(300).setDuration(1000).start();
        txtNot   .animate().alpha(1f).setStartDelay(700).setDuration(700).start();
        txtRoots .animate().alpha(1f).setStartDelay(1100).setDuration(700).start();
        txtWho   .animate().alpha(1f).setStartDelay(1500).setDuration(700).start();
        txtCrisis.animate().alpha(1f).setStartDelay(1900).setDuration(700).start();
        btnBack  .animate().alpha(1f).setStartDelay(2400).setDuration(900).start();

        // Build the multi-target crisis line. The block opens with the
        // disclaimer ("this isn't the right place") and surfaces the
        // tappable resources beneath it. Each segment is its own tap
        // target — tapping a number opens the dialer (with the number
        // pre-filled, user still has to press call), tapping the URL
        // opens the system browser.
        applyCrisisLine(txtCrisis);

        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });
    }

    /**
     * Replaces the placeholder crisis text with a three-target tappable
     * block. The number-and-URL row uses warmer #A89880 to mark itself as
     * actionable, while the lead line stays dim (#8A8480) so the eye
     * lands on the lead first and the resources second.
     */
    private void applyCrisisLine(TextView tv) {
        final int leadColor = 0xFF8A8480;
        final int linkColor = 0xFFA89880;

        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append("If you're in crisis, this isn't the right place.\n");

        int ncmhStart = sb.length();
        sb.append(CRISIS_PH_NCMH);
        sb.setSpan(new ClickableSpan() {
            @Override public void onClick(View v) { dial(CRISIS_PH_NCMH); }
        }, ncmhStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(linkColor),
                ncmhStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        sb.append("   \u00B7   ");

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
            @Override public void onClick(View v) { openUrl(CRISIS_HELPLINE_URL); }
        }, urlStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(linkColor),
                urlStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        tv.setText(sb);
        tv.setTextColor(leadColor);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * Launches the system dialer with the given number pre-filled. We use
     * ACTION_DIAL rather than ACTION_CALL so the user still has to confirm
     * the call. This avoids requiring the CALL_PHONE permission entirely,
     * which we never want to ask for.
     */
    private void dial(String phoneNumber) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {
            // No dialer (extremely unlikely on a phone) — silent no-op.
            // The number is still on screen for the user to read.
        }
    }

    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {
            // No browser, or restricted device — silently no-op. The line
            // is still on screen; the user can read findahelpline.com and
            // look it up themselves.
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
