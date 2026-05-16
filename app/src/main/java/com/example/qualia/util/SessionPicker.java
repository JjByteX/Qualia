package com.example.qualia.util;

import com.example.qualia.data.model.Session;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Picks the next session for the user.
 *
 * <p>The picker is intentionally random within a sliding-window exclusion
 * — sessions in {@code lastSessionKeys} (the user's most recent ~6
 * picks) are excluded so the user doesn't immediately re-encounter
 * material they just sat with. Beyond that exclusion, every remaining
 * session has equal probability. The user does not choose; the picker
 * does, and the chosen session is the one for today. This is the
 * "freedom from choice" the proposal frames as part of the practice.
 *
 * <p><b>First-three guard (v6).</b> Sessions tagged {@code heavy=true}
 * in {@code sessions.json} touch grief, mortality, or other material
 * that is intentionally heavy for users who have already settled into
 * the practice. For a user's first three sittings the picker excludes
 * heavy sessions so the entry experience is gentler. This is the
 * smallest possible curation move — it does not change the
 * "no-choice" model, it only puts a floor under the very beginning.
 * From the fourth session onward, every session is eligible again.
 *
 * <p>The 3-session threshold is deliberate: too short (1 or 2) and a
 * user who only does three sessions never encounters heavy material at
 * all, which understates what the deck is; too long (5+) and the
 * gentling becomes its own kind of curation. Three is the smallest
 * window that lets a first impression land before harder material
 * arrives.
 */
public class SessionPicker {

    /** Below this session count, the picker excludes heavy-tagged sessions.
     *  At or above this count, all sessions are eligible. */
    public static final int HEAVY_GUARD_SESSION_THRESHOLD = 3;

    // One Random per process is more than enough; we were creating a fresh
    // instance per call and reseeding from the clock every time. java.util.Random
    // is thread-safe (synchronized on its internal seed) and we never need
    // cryptographic randomness here — only "pick something the user hasn't
    // seen recently."
    private static final Random RANDOM = new Random();

    /**
     * Legacy signature, kept so existing call sites compile unchanged.
     * Equivalent to calling {@link #pick(List, String[], int)} with a
     * session count high enough to disable the heavy-guard — i.e. the
     * old "everything is eligible" behaviour.
     *
     * @deprecated callers should pass the user's current session count
     *     so the first-three guard can run.
     */
    @Deprecated
    public static Session pick(List<Session> sessions, String[] lastSessionKeys) {
        return pick(sessions, lastSessionKeys, Integer.MAX_VALUE);
    }

    /**
     * Pick the next session.
     *
     * @param sessions          full session deck
     * @param lastSessionKeys   recently-played keys to exclude from the pick
     * @param sessionCount      user's current completed-session count.
     *     If below {@link #HEAVY_GUARD_SESSION_THRESHOLD}, heavy sessions
     *     are excluded; at or above, all sessions are eligible.
     */
    public static Session pick(List<Session> sessions,
                               String[] lastSessionKeys,
                               int sessionCount) {
        if (sessions == null || sessions.isEmpty()) return null;

        List<String> excluded = lastSessionKeys == null
                ? java.util.Collections.<String>emptyList()
                : Arrays.asList(lastSessionKeys);

        boolean firstThreeGuard = sessionCount < HEAVY_GUARD_SESSION_THRESHOLD;

        List<Session> available = new ArrayList<>();
        for (Session s : sessions) {
            if (s == null || s.key == null) continue;
            if (excluded.contains(s.key)) continue;
            if (firstThreeGuard && s.heavy) continue;
            available.add(s);
        }

        // First fallback: if the heavy-guard left nothing (extremely
        // unlikely given the deck size, but the deck is configurable
        // via sessions.json), drop the guard but keep the recency
        // exclusion. The user still doesn't repeat a recent session.
        if (available.isEmpty() && firstThreeGuard) {
            for (Session s : sessions) {
                if (s == null || s.key == null) continue;
                if (excluded.contains(s.key)) continue;
                available.add(s);
            }
        }

        // Second fallback: if even the recency exclusion left nothing
        // (deck smaller than the sliding window — happens only in tests
        // or with a deliberately-shrunk deck), pick from everything.
        if (available.isEmpty()) {
            available = new ArrayList<>(sessions);
        }

        return available.get(RANDOM.nextInt(available.size()));
    }
}
