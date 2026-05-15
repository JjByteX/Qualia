package com.example.qualia.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PrefsManager {

    private static final String PREFS_NAME            = "qualia_prefs";
    private static final String KEY_FIRST_LAUNCH      = "first_launch";
    private static final String KEY_LAST_SESSIONS     = "last_sessions";
    private static final String KEY_SESSION_COUNT     = "session_count";
    private static final String KEY_LAST_SESSION_DATE = "last_session_date";
    private static final String KEY_SOUND_MUTED          = "sound_muted";
    private static final String KEY_FADING_JOURNAL       = "fading_journal";
    private static final String KEY_VOICE_ENABLED        = "voice_enabled";
    private static final String KEY_PRELOADED_SESSION    = "preloaded_session_key";
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

    // ── Session history ───────────────────────────────────────────────────────

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

    // ── Voice ─────────────────────────────────────────────────────────────────

    /** Off by default. User opts in from the home screen. */
    public boolean isVoiceEnabled() {
        return prefs.getBoolean(KEY_VOICE_ENABLED, false);
    }

    public void setVoiceEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply();
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
