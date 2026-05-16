package com.example.qualia.util;

/**
 * Kokoro TTS configuration constants.
 *
 * Audio is served exclusively from pre-generated cache files bundled inside
 * the APK as assets ({@code assets/voice_cache/}). No network access or ADB
 * push is required at runtime.
 *
 * MODEL, SPEED and VOICE must stay in sync with qualia_voice_downloader.py
 * because the cache filename is SHA-1(text|voice|model|speed). Changing any
 * of these values here would break lookups for existing cached files.
 */
public final class KokoroConfig {

    private KokoroConfig() {}

    /** Model name used when the cache files were generated. Do not change. */
    public static final String MODEL = "model_q8f16";

    /** The single voice shipped with the app. */
    public static final String VOICE = "af_sarah";

    /** Kept for backwards compatibility with any code that iterates voices. */
    public static final String[] VOICES = { VOICE };

    /**
     * Returns the voice for the given session key. The app now ships with a
     * single voice, so this always returns {@link #VOICE} regardless of key.
     */
    public static String voiceForSession(String sessionKey) {
        return VOICE;
    }

    /**
     * Speaking speed used when cache files were generated. Do not change.
     * Range: 0.5 (very slow) to 2.0 (fast).
     *
     * 0.90 keeps af_sarah balanced — calm enough for night, awake enough
     * for day. Lower felt sleep-meditation-only, higher felt too chipper.
     */
    public static final float SPEED = 0.90f;
}
