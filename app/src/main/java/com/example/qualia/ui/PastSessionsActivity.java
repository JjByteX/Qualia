package com.example.qualia.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.example.qualia.R;
import com.example.qualia.data.model.Session;
import com.example.qualia.data.model.SessionLine;
import com.example.qualia.data.repository.SessionRepository;
import com.example.qualia.util.PrefsManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "What was said." The post-graduation archive of the writing the user has
 * already sat with, returned to them in chronological order, faded with
 * age the way the journal's fading toggle fades the user's own words.
 *
 * <p>Reachable only after graduation. Before session seventy, sessions
 * stay ephemeral — that's the discipline. After, the discipline has been
 * completed, and what remains is a quiet record. The proposal explicitly
 * frames anicca that way: "a moment was kept and is now releasing
 * itself." This screen lets the words release themselves the same way an
 * old journal entry does.
 *
 * <p>Aging curve (alpha by age in days):
 * <pre>
 *      0 –  30 d  : 1.00   (recent)
 *     30 – 365 d  : 1.00 → 0.35  (slow fade through the first year)
 *    365 – 1095 d : 0.35 → 0.15  (long, slow fade across years two and three)
 *   1095+      d  : 0.12        (almost gone)
 * </pre>
 * The shape matches the proposal claim: "barely visible after a year,
 * almost gone after three."
 *
 * <p>No audio. No order other than chronological. No counter. Tap any
 * card to expand it to full opacity for reading; tap again to return it
 * to its faded state. Releasing the cards on tap is the only "control" on
 * this screen — everything else is just the record.
 */
public class PastSessionsActivity extends BaseActivity {

    /** A floor on the alpha curve so the oldest sessions don't vanish
     *  outright. They are meant to be present but barely there. */
    private static final float ALPHA_FLOOR = 0.12f;

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_past_sessions);

        WeatherFlowView weatherFlow = findViewById(R.id.weatherFlow);
        if (weatherFlow != null) weatherFlow.setDensityMode(WeatherFlowView.DENSITY_LOW);

        TextView txtHeader = findViewById(R.id.txtHeader);
        TextView txtEmpty  = findViewById(R.id.txtEmpty);
        TextView btnBack   = findViewById(R.id.btnBack);
        LinearLayout cardContainer = findViewById(R.id.cardContainer);

        PrefsManager prefs = new PrefsManager(this);
        List<PrefsManager.SessionHistoryEntry> history = prefs.getSessionHistory();

        // Sessions are looked up by key. Build a map once so each card lookup
        // is O(1) instead of O(N) over the full session list. The repository
        // already loads the whole list eagerly in its constructor, so this is
        // just an index over an in-memory list.
        SessionRepository repo = new SessionRepository(this);
        Map<String, Session> byKey = new HashMap<>();
        List<Session> all = repo.getAll();
        if (all != null) {
            for (Session s : all) {
                if (s != null && s.key != null) byKey.put(s.key, s);
            }
        }

        if (history.isEmpty()) {
            // Shouldn't happen in practice — this screen is gated by
            // hasGraduated() — but if the prefs were wiped or never written
            // we don't want the screen to read as broken.
            txtEmpty.setVisibility(View.VISIBLE);
        } else {
            long nowMs = System.currentTimeMillis();
            for (PrefsManager.SessionHistoryEntry entry : history) {
                Session session = byKey.get(entry.key);
                if (session == null) continue;   // session removed from the deck since
                cardContainer.addView(buildCard(this, session, entry.timestampMs, nowMs));
            }
        }

        // Staggered arrival — header, then the cards as a whole, then the
        // exit. Same rhythm as the about sheet so the screen lands in the
        // same room.
        txtHeader.animate().alpha(1f).setStartDelay(300).setDuration(1000).start();
        btnBack  .animate().alpha(1f).setStartDelay(1500).setDuration(900).start();

        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    /**
     * Build a single faded card for one historical session.
     *
     * <p>The card is a small vertical block: date (very dim) → theme (dim)
     * → the session lines (paragraph-joined) → the closing line. The whole
     * card is faded by a single alpha value derived from how old the entry
     * is — text inside the card keeps its relative shade ordering, the
     * card just gets quieter as a whole.
     */
    private View buildCard(Context ctx, Session session, long playedAtMs, long nowMs) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(36);
        card.setLayoutParams(cardLp);

        float ageAlpha = alphaForAge(playedAtMs, nowMs);
        card.setAlpha(ageAlpha);

        // Date — very dim, no year. "march 14". Lower-cased to match the
        // existing register (every label in the app is lower-cased).
        TextView txtDate = new TextView(ctx);
        txtDate.setText(formatDate(playedAtMs));
        txtDate.setTextColor(0xFF6B6560);
        txtDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        txtDate.setLetterSpacing(0.18f);
        applyLora(txtDate);
        card.addView(txtDate);

        // Theme — slightly brighter. The label, not the body.
        if (session.theme != null && !session.theme.isEmpty()) {
            TextView txtTheme = new TextView(ctx);
            txtTheme.setText(session.theme.toLowerCase(Locale.US));
            txtTheme.setTextColor(0xFFA89880);
            txtTheme.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            txtTheme.setLetterSpacing(0.15f);
            applyLora(txtTheme);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(4);
            lp.bottomMargin = dp(14);
            txtTheme.setLayoutParams(lp);
            card.addView(txtTheme);
        }

        // The lines themselves. We join them as paragraphs so the user
        // reads them at the same cadence the session itself paced them —
        // each line is its own breath, separated by a small gap.
        if (session.lines != null && !session.lines.isEmpty()) {
            TextView txtBody = new TextView(ctx);
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (SessionLine line : session.lines) {
                if (line == null || line.text == null) continue;
                if (count > 0) sb.append("\n\n");
                sb.append(line.text);
                count++;
            }
            txtBody.setText(sb.toString());
            txtBody.setTextColor(0xFFF5F0E8);
            txtBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            txtBody.setLineSpacing(dp(2), 1f);
            applyLora(txtBody);
            card.addView(txtBody);
        }

        // Closing line — set apart, in italic, the way the closing screen
        // lives apart from the session body.
        if (session.closingLine != null && !session.closingLine.isEmpty()) {
            TextView txtClosing = new TextView(ctx);
            txtClosing.setText(session.closingLine);
            txtClosing.setTextColor(0xFFA89880);
            txtClosing.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            txtClosing.setTypeface(txtClosing.getTypeface(), Typeface.ITALIC);
            applyLora(txtClosing);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(18);
            txtClosing.setLayoutParams(lp);
            card.addView(txtClosing);
        }

        // A single tap reveals the card at full opacity for re-reading;
        // tapping again returns it to its age-faded state. No "permanent"
        // restore — the practice still releases. We just lend the user a
        // moment of clarity on demand.
        final float fadedAlpha = ageAlpha;
        card.setOnClickListener(v -> {
            float current = v.getAlpha();
            float target = (current < 0.95f) ? 1f : fadedAlpha;
            v.animate().alpha(target).setDuration(450).start();
        });

        return card;
    }

    /**
     * Maps the entry's age (in days) onto its display alpha. Matches the
     * proposal's claim that fading entries are "barely visible after a
     * year, almost gone after three."
     */
    private static float alphaForAge(long playedAtMs, long nowMs) {
        long ageMs = Math.max(0L, nowMs - playedAtMs);
        double days = ageMs / (double) DAY_MS;
        if (days <= 30.0) return 1.0f;
        if (days <= 365.0) {
            double t = (days - 30.0) / 335.0;          // 0..1 across day 30..365
            return (float) Math.max(ALPHA_FLOOR, 1.0 - 0.65 * t);
        }
        if (days <= 1095.0) {
            double t = (days - 365.0) / 730.0;         // 0..1 across day 365..1095
            return (float) Math.max(ALPHA_FLOOR, 0.35 - 0.20 * t);
        }
        return ALPHA_FLOOR;
    }

    private String formatDate(long ms) {
        // "march 14" — no year, lower-cased. Year would feel like a record
        // of distance; the fade already shows distance.
        return new SimpleDateFormat("MMMM d", Locale.US)
                .format(new Date(ms))
                .toLowerCase(Locale.US);
    }

    private int dp(float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }

    private void applyLora(TextView tv) {
        // Defensive — if the @font/lora resource isn't available for some
        // reason, we just leave the system typeface. Better silent than
        // crashing the archive.
        try {
            Typeface lora = ResourcesCompat.getFont(this, R.font.lora);
            if (lora != null) {
                Typeface existing = tv.getTypeface();
                int style = existing != null ? existing.getStyle() : Typeface.NORMAL;
                tv.setTypeface(lora, style);
            }
        } catch (Exception ignored) {
        }
    }
}
