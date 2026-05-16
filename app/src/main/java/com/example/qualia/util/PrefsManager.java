package com.example.qualia.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PrefsManager {

    private static final String PREFS_NAME            = "qualia_prefs";
    private static final String KEY_FIRST_LAUNCH      = "first_launch";
    private static final String KEY_LAST_SESSIONS     = "last_sessions";
    private static final String KEY_SESSION_COUNT     = "session_count";
    private static final String KEY_LAST_SESSION_DATE = "last_session_date";
    private static final String KEY_SOUND_MUTED          = "sound_muted";
    private static final String KEY_FADING_JOURNAL       = "fading_journal";
    // KEY_VOICE_ENABLED removed — voice is mandatory now (every shipped session
    // has bundled audio; there's no longer a meaningful 'off' state to store).
    private static final String KEY_PRELOADED_SESSION    = "preloaded_session_key";
    private static final String KEY_JOURNAL_DEMO_SHOWN    = "journal_demo_shown";
    private static final String KEY_JOURNAL_HINT_SHOWN    = "journal_hint_shown";
    private static final String KEY_SESSION_HISTORY      = "session_history";
    private static final int    GRADUATION_THRESHOLD  = 70;

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── Onboarding ────────────────────────────────────────────────────────────

    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    public void setFirstLaunchDone() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
    }

    // ── Session history (recent, for the picker exclusion) ────────────────────

    public void saveLastSession(String key) {
        String existing = prefs.getString(KEY_LAST_SESSIONS, "");
        String[] parts = existing.isEmpty() ? new String[0] : existing.split(",");
        StringBuilder updated = new StringBuilder();
        int start = Math.max(0, parts.length - 6);
        for (int i = start; i < parts.length; i++) updated.append(parts[i]).append(",");
        updated.append(key);
        prefs.edit().putString(KEY_LAST_SESSIONS, updated.toString()).apply();
    }

    public String[] getLastSessions() {
        String raw = prefs.getString(KEY_LAST_SESSIONS, "");
        if (raw.isEmpty()) return new String[0];
        return raw.split(",");
    }

    // ── Full session history (for the post-graduation archive) ────────────────
    //
    // We store every session the user has sat with, keyed by start-time. This
    // is the data behind the "what was said" archive — the writing they came
    // back to seventy times, returned to them in chronological order, faded
    // with age the way the journal does for their own words.
    //
    // Format: JSON array of objects { "k": "<sessionKey>", "t": <epochMs> }.
    // Append-only. We never trim — the archive is the record.

    public void recordSessionPlayed(String key, long timestampMs) {
        if (key == null || key.isEmpty()) return;
        JSONArray arr = readHistoryArray();
        try {
            JSONObject entry = new JSONObject();
            entry.put("k", key);
            entry.put("t", timestampMs);
            arr.put(entry);
            prefs.edit().putString(KEY_SESSION_HISTORY, arr.toString()).apply();
        } catch (JSONException ignored) {
            // Worst case: the archive is missing this one entry. The session
            // itself still plays; we don't want a bad write to crash the
            // session flow.
        }
    }

    /** Returns the user's full history of sat-with sessions, oldest first.
     *  Each entry is a (sessionKey, epochMs) pair. Returns an empty list if
     *  the user has never started a session or the prefs are corrupt. */
    public List<SessionHistoryEntry> getSessionHistory() {
        List<SessionHistoryEntry> out = new ArrayList<>();
        JSONArray arr = readHistoryArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String k = o.optString("k", null);
            long   t = o.optLong("t", 0L);
            if (k != null && !k.isEmpty()) out.add(new SessionHistoryEntry(k, t));
        }
        return out;
    }

    private JSONArray readHistoryArray() {
        String raw = prefs.getString(KEY_SESSION_HISTORY, "");
        if (raw.isEmpty()) return new JSONArray();
        try {
            return new JSONArray(raw);
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    /** A single (sessionKey, timestamp) pair from the user's history. */
    public static final class SessionHistoryEntry {
        public final String key;
        public final long timestampMs;
        public SessionHistoryEntry(String key, long timestampMs) {
            this.key = key;
            this.timestampMs = timestampMs;
        }
    }

    // ── Session count & graduation ─────────────────────────────────────────────

    public int getSessionCount() {
        return prefs.getInt(KEY_SESSION_COUNT, 0);
    }

    public void incrementSessionCount() {
        prefs.edit().putInt(KEY_SESSION_COUNT, getSessionCount() + 1).apply();
    }

    public boolean hasGraduated() {
        return getSessionCount() >= GRADUATION_THRESHOLD;
    }

    public int getSessionsRemaining() {
        return Math.max(0, GRADUATION_THRESHOLD - getSessionCount());
    }

    public int getGraduationThreshold() {
        return GRADUATION_THRESHOLD;
    }

    // ── Daily gate ────────────────────────────────────────────────────────────

    public void setSessionDoneToday() {
        prefs.edit().putString(KEY_LAST_SESSION_DATE, todayString()).apply();
    }

    public boolean wasSessionDoneToday() {
        return todayString().equals(prefs.getString(KEY_LAST_SESSION_DATE, ""));
    }

    private String todayString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    // ── Sound ─────────────────────────────────────────────────────────────────

    public boolean isSoundMuted() {
        return prefs.getBoolean(KEY_SOUND_MUTED, false);
    }

    public void setSoundMuted(boolean muted) {
        prefs.edit().putBoolean(KEY_SOUND_MUTED, muted).apply();
    }

    // ── Fading journal ────────────────────────────────────────────────────────

    public boolean isFadingJournal() {
        return prefs.getBoolean(KEY_FADING_JOURNAL, false);
    }

    public void setFadingJournal(boolean enabled) {
        prefs.edit().putBoolean(KEY_FADING_JOURNAL, enabled).apply();
    }

    // ── Preloaded session key ─────────────────────────────────────────────────

    /**
     * Returns the session key pre-selected by HomeActivity, or null if none.
     * SessionActivity calls this to reuse the same session whose audio was
     * pre-downloaded, then calls clearPreloadedSessionKey() to consume it.
     */
    public String getPreloadedSessionKey() {
        String key = prefs.getString(KEY_PRELOADED_SESSION, null);
        return (key == null || key.isEmpty()) ? null : key;
    }

    public void setPreloadedSessionKey(String key) {
        prefs.edit().putString(KEY_PRELOADED_SESSION, key).apply();
    }

    public void clearPreloadedSessionKey() {
        prefs.edit().remove(KEY_PRELOADED_SESSION).apply();
    }

    // ── Journal first-visit demo (ghost-stroke) ───────────────────────────────

    /**
     * The journal hides its drawing affordance — tap to type, drag to draw,
     * no mode toggle. A new user has no way to know about the drag-to-draw
     * behaviour. On first visit (if idle for a few seconds) we play a brief
     * ghost-stroke demo to teach it diegetically. Shown once, ever.
     */
    public boolean hasShownJournalDemo() {
        return prefs.getBoolean(KEY_JOURNAL_DEMO_SHOWN, false);
    }

    public void setJournalDemoShown() {
        prefs.edit().putBoolean(KEY_JOURNAL_DEMO_SHOWN, true).apply();
    }

    // ── Journal first-visit text hint ─────────────────────────────────────────
    //
    // A separate, gentler teaching: the first time the user opens a new
    // journal page, a single quiet line fades in at the bottom — "tap to
    // write. drag to draw." — and stays for ~6 seconds before fading out
    // forever. Stored under its own flag so the line and the ghost stroke
    // can be tuned independently.

    public boolean hasShownJournalHint() {
        return prefs.getBoolean(KEY_JOURNAL_HINT_SHOWN, false);
    }

    public void setJournalHintShown() {
        prefs.edit().putBoolean(KEY_JOURNAL_HINT_SHOWN, true).apply();
    }

    // ── Drawing palette ───────────────────────────────────────────────────────

    private static final String KEY_LAST_COLOR     = "drawing_last_color";
    private static final String KEY_LAST_BRUSH     = "drawing_last_brush";
    private static final String KEY_CUSTOM_COLOR   = "drawing_custom_color";

    /** Last colour the user picked, in 0xAARRGGBB. Defaults to forest green. */
    public int getLastColor() {
        return prefs.getInt(KEY_LAST_COLOR, 0xFF2E7D4A);
    }

    public void setLastColor(int color) {
        prefs.edit().putInt(KEY_LAST_COLOR, color).apply();
    }

    /** Last brush size in pixels. Defaults to medium. */
    public float getLastBrushSize() {
        return prefs.getFloat(KEY_LAST_BRUSH, 14f);
    }

    public void setLastBrushSize(float size) {
        prefs.edit().putFloat(KEY_LAST_BRUSH, size).apply();
    }

    /** Most recently chosen custom colour (from the picker), or 0 if the user
     *  has never opened the picker. The "+" swatch shows this colour once
     *  it's set, so re-picking it is one tap. */
    public int getCustomColor() {
        return prefs.getInt(KEY_CUSTOM_COLOR, 0);
    }

    public void setCustomColor(int color) {
        prefs.edit().putInt(KEY_CUSTOM_COLOR, color).apply();
    }

    // ── Journal draft ─────────────────────────────────────────────────────────

    private static final String KEY_JOURNAL_DRAFT = "journal_draft";

    /** A "let it be" draft saved when the user wanted to keep their unfinished
     *  page for next session without committing it as a real entry. The string
     *  is JSON encoded by JournalActivity (text + strokes + polaroid metas).
     *  Returns {@code null} when there is no draft to restore. */
    public String getDraft() {
        String raw = prefs.getString(KEY_JOURNAL_DRAFT, "");
        return raw.isEmpty() ? null : raw;
    }

    public void setDraft(String json) {
        prefs.edit().putString(KEY_JOURNAL_DRAFT, json == null ? "" : json).apply();
    }

    public void clearDraft() {
        prefs.edit().remove(KEY_JOURNAL_DRAFT).apply();
    }
}
