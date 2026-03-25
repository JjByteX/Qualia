package com.example.qualia.util;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {

    private static final String PREFS_NAME = "qualia_prefs";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private static final String KEY_LAST_SESSIONS = "last_sessions";
    private static final String KEY_SESSION_COUNT = "session_count";
    private static final int GRADUATION_THRESHOLD = 70;

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    public void setFirstLaunchDone() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
    }

    public void saveLastSession(String key) {
        String existing = prefs.getString(KEY_LAST_SESSIONS, "");
        String[] parts = existing.isEmpty() ? new String[0] : existing.split(",");
        StringBuilder updated = new StringBuilder();
        int start = Math.max(0, parts.length - 6);
        for (int i = start; i < parts.length; i++) {
            updated.append(parts[i]).append(",");
        }
        updated.append(key);
        prefs.edit().putString(KEY_LAST_SESSIONS, updated.toString()).apply();
    }

    public String[] getLastSessions() {
        String raw = prefs.getString(KEY_LAST_SESSIONS, "");
        if (raw.isEmpty()) return new String[0];
        return raw.split(",");
    }

    public int getSessionCount() {
        return prefs.getInt(KEY_SESSION_COUNT, 0);
    }

    public void incrementSessionCount() {
        int current = getSessionCount();
        prefs.edit().putInt(KEY_SESSION_COUNT, current + 1).apply();
    }

    public boolean hasGraduated() {
        return getSessionCount() >= GRADUATION_THRESHOLD;
    }

    public int getSessionsRemaining() {
        return Math.max(0, GRADUATION_THRESHOLD - getSessionCount());
    }
}