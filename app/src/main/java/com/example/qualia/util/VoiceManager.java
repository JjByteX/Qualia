package com.example.qualia.util;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.util.Log;

/**
 * Plays pre-rendered Kokoro TTS audio bundled in the APK.
 *
 * <p>Voice is mandatory for the app — the previous opt-in toggle has been
 * removed because every shipped session has a baked audio counterpart, and
 * the writing was tuned to be heard, not read. {@link #speak} therefore
 * always attempts playback; the only reason it falls through silently is a
 * truly missing asset (typo in sessions.json, mid-build cache gap). For that
 * edge case, callers should pre-check with {@link #hasCachedAudio(Context, String)}
 * and fall back to a typewriter animation for that single line.
 */
public class VoiceManager {

    private static final String TAG = "VoiceManager";

    public interface OnComplete {
        void done();
    }

    private static MediaPlayer voicePlayer;

    /** Voice used for the current session — set by SessionActivity at start. */
    private static String currentVoice = KokoroConfig.VOICE;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call this when a session begins. The app now ships with a single voice,
     * so this is effectively a no-op, but the entry point is kept so callers
     * don't need to change.
     */
    public static void setSessionVoice(String voice) {
        currentVoice = (voice != null) ? voice : KokoroConfig.VOICE;
        Log.d(TAG, "Session voice set to: " + currentVoice);
    }

    /**
     * True when bundled audio exists for {@code text} in the current session
     * voice. Use this to gate the voice path before calling {@link #speak};
     * if it returns false, render the line yourself (typewriter, etc.) — the
     * caller knows the timing it wants for the visual fallback better than
     * we do.
     */
    public static boolean hasCachedAudio(Context context, String text) {
        return VoiceCache.hasCached(context, text, currentVoice);
    }

    /**
     * Plays the bundled audio for the given text. {@code onDone} fires when
     * playback completes (or on a hard error / missing asset). The caller is
     * still responsible for any visual pacing; {@code speak} does not draw.
     */
    public static void speak(Context context, String text, OnComplete onDone) {
        AssetFileDescriptor afd = VoiceCache.openCached(context, text, currentVoice);
        if (afd != null) {
            Log.d(TAG, "Asset hit — playing");
            playAsset(afd, onDone);
        } else {
            Log.w(TAG, "Asset miss — no audio for: " + text);
            onDone.done();
        }
    }

    public static void stop() {
        stopVoicePlayer();
    }

    public static void release() {
        stopVoicePlayer();
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private static synchronized void playAsset(AssetFileDescriptor afd, OnComplete onDone) {
        stopVoicePlayer();
        try {
            voicePlayer = new MediaPlayer();
            voicePlayer.setDataSource(afd.getFileDescriptor(),
                    afd.getStartOffset(), afd.getLength());
            voicePlayer.setVolume(1f, 1f);
            voicePlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "Playback complete");
                stopVoicePlayer();
                onDone.done();
            });
            voicePlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: what=" + what + " extra=" + extra);
                stopVoicePlayer();
                onDone.done();
                return true;
            });
            voicePlayer.prepare();
            voicePlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "playAsset failed: " + e.getMessage());
            stopVoicePlayer();
            onDone.done();
        } finally {
            try { afd.close(); } catch (Exception ignored) {}
        }
    }

    private static void stopVoicePlayer() {
        if (voicePlayer != null) {
            try {
                if (voicePlayer.isPlaying()) voicePlayer.stop();
                voicePlayer.release();
            } catch (Exception ignored) {}
            voicePlayer = null;
        }
    }
}
