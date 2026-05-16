package com.example.qualia.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;

import androidx.core.content.ContextCompat;

import com.example.qualia.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Ambient sound system.
 *
 * Matches live weather via Open-Meteo (no API key needed).
 * Maps WMO weather codes → one of four sound categories.
 *
 * Also stores the current weather code so other components (WeatherFlowView)
 * can read it without making a second network call.
 *
 * Required audio files in res/raw/:
 *   sound_clear.ogg    — soft birds / nature ambience
 *   sound_rain.ogg     — gentle rain loop
 *   sound_wind.ogg     — soft wind / snow ambience
 *   sound_overcast.ogg — low drone / cloudy day tone
 *
 * All files should be seamless loops, ~2–5 minutes, normalised to around -18 LUFS.
 */
public class SoundManager {

    private static MediaPlayer mediaPlayer;
    private static boolean isPlaying = false;

    /**
     * The WMO weather code from the last successful fetch.
     * Defaults to 0 (clear) — a safe, warm fallback if no fetch has run yet.
     * Read by WeatherFlowView to choose its visual mode.
     */
    private static int currentWeatherCode = 0;

    /** Returns the last fetched WMO weather code, or 0 (clear) if unknown. */
    public static int getWeatherCode() {
        return currentWeatherCode;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Start ambient sound (fetches weather, then plays matching loop). */
    public static void start(Context context) {
        PrefsManager prefs = new PrefsManager(context);
        if (prefs.isSoundMuted()) return;
        if (isPlaying) return;

        double[] latLon = getLastKnownLocation(context);
        if (latLon != null) {
            fetchWeatherAndPlay(context, latLon[0], latLon[1]);
        } else {
            // No location — fall back to clear/default sound
            play(context, R.raw.sound_clear);
        }
    }

    /** Stop playback and release the MediaPlayer. */
    public static void stop() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        isPlaying = false;
    }

    // ── Location ──────────────────────────────────────────────────────────────

    private static double[] getLastKnownLocation(Context context) {
        boolean hasFine   = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasFine && !hasCoarse) return null;

        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;

        Location loc = null;
        try {
            if (hasFine)   loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        } catch (Exception ignored) {}

        return loc != null ? new double[]{loc.getLatitude(), loc.getLongitude()} : null;
    }

    // ── Weather API ───────────────────────────────────────────────────────────

    private static void fetchWeatherAndPlay(Context context, double lat, double lon) {
        new Thread(() -> {
            try {
                String urlStr = "https://api.open-meteo.com/v1/forecast"
                        + "?latitude=" + lat
                        + "&longitude=" + lon
                        + "&current=weather_code";

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                int code = json.getJSONObject("current").getInt("weather_code");

                // Store so WeatherFlowView can read it without a second network call
                currentWeatherCode = code;

                play(context, weatherCodeToSound(code));

            } catch (Exception e) {
                // Network failure or parse error — use default
                play(context, R.raw.sound_clear);
            }
        }).start();
    }

    /**
     * WMO Weather Interpretation Codes → sound category.
     *
     * 0–3   Clear sky / mainly clear / partly cloudy    → sound_clear
     * 4–49  Overcast / fog / depositing rime fog         → sound_overcast
     * 50–82 Drizzle / rain / rain showers                → sound_rain
     * 83–86 Snow showers / heavy snow showers            → sound_wind
     * 95–99 Thunderstorm                                 → sound_rain
     */
    private static int weatherCodeToSound(int code) {
        if (code <= 3)  return R.raw.sound_clear;
        if (code <= 48) return R.raw.sound_overcast;
        if (code <= 82) return R.raw.sound_rain;
        if (code <= 86) return R.raw.sound_wind;
        return R.raw.sound_rain; // thunderstorm
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private static synchronized void play(Context context, int rawResId) {
        stop(); // release any existing player first
        try {
            mediaPlayer = MediaPlayer.create(context.getApplicationContext(), rawResId);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(0.30f, 0.30f); // subtle — 30% volume
                mediaPlayer.start();
                isPlaying = true;
            }
        } catch (Exception ignored) {}
    }
}
