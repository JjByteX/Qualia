package com.example.qualia.util;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Read-only lookup for pre-generated Kokoro TTS audio bundled inside the APK.
 *
 * Audio is generated on the desktop with {@code qualia_voice_downloader.py}
 * (af_sarah voice only) and dropped into {@code app/src/main/assets/voice_cache/}.
 * AAPT2 stores .mp3 files uncompressed by default, so they can be opened
 * directly via {@link AssetManager#openFd(String)} and streamed without an
 * extra runtime copy.
 *
 * Filenames are SHA-1 hashes of {@code text|voice|model|speed} — the same
 * formula used by the downloader script, so lookups always match.
 */
public final class VoiceCache {

    private static final String TAG       = "VoiceCache";
    private static final String ASSET_DIR = "voice_cache";

    private VoiceCache() {}

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns an {@link AssetFileDescriptor} for the cached audio matching
     * (text, voice), or {@code null} if it isn't bundled in the APK. The
     * caller is responsible for closing the returned descriptor.
     */
    public static AssetFileDescriptor openCached(Context context, String text, String voice) {
        String assetPath = assetPathFor(text, voice);
        try {
            return context.getAssets().openFd(assetPath);
        } catch (IOException e) {
            Log.w(TAG, "Not bundled: " + assetPath
                    + "  text=" + text.substring(0, Math.min(40, text.length())));
            return null;
        }
    }

    /**
     * Cheap existence probe: returns {@code true} when the audio asset for
     * (text, voice) is present in the APK. Used by SessionActivity to decide,
     * before showing a line, whether to take the voice path or fall back to
     * the typewriter animation for that one line. We open and immediately
     * close the descriptor — there is no lighter way to check, but this is
     * fast (asset table lookup, no I/O on the audio bytes themselves).
     */
    public static boolean hasCached(Context context, String text, String voice) {
        AssetFileDescriptor afd = openCached(context, text, voice);
        if (afd == null) return false;
        try { afd.close(); } catch (IOException ignored) {}
        return true;
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    /** Asset path: voice_cache/<sha1(text|voice|model|speed)>.mp3 */
    static String assetPathFor(String text, String voice) {
        String key = text + "|" + voice
                         + "|" + KokoroConfig.MODEL
                         + "|" + KokoroConfig.SPEED;
        return ASSET_DIR + "/" + sha1(key) + ".mp3";
    }

    private static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(Math.abs(input.hashCode()));
        }
    }
}
