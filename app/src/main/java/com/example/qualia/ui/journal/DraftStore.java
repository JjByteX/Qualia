package com.example.qualia.ui.journal;

import android.content.Context;

import com.example.qualia.util.PrefsManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistence for the journal page's "let it be" draft.
 *
 * <p>The draft is the unfinished page the user wanted to keep but not commit
 * as a real entry. JournalActivity used to own both the JSON schema and the
 * prefs key — this class lifts that out so the activity just hands over a
 * structured {@link Draft} and gets one back, no JSON in the activity. The
 * key shape is preserved exactly so drafts written by the previous build
 * still load correctly.
 *
 * <p>Polaroid positions are stored as fractions of the container size so the
 * page can be restored on a different device or after rotation without the
 * polaroids drifting off the visible page.
 */
public final class DraftStore {

    private final PrefsManager prefs;

    public DraftStore(Context context) {
        this.prefs = new PrefsManager(context);
    }

    /** Visible for tests / advanced callers that already have a PrefsManager. */
    public DraftStore(PrefsManager prefs) {
        this.prefs = prefs;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the saved draft, or {@code null} if none. Silently returns
     *  {@code null} on a malformed payload (and clears the bad draft so it
     *  doesn't keep failing). */
    public Draft load() {
        String raw = prefs.getDraft();
        if (raw == null) return null;
        try {
            JSONObject doc = new JSONObject(raw);
            Draft d = new Draft();
            d.text = doc.optString("text", "");
            String strokes = doc.optString("strokes", "");
            d.strokesJson = strokes.isEmpty() ? null : strokes;

            JSONArray polys = doc.optJSONArray("polaroids");
            if (polys != null) {
                for (int i = 0; i < polys.length(); i++) {
                    JSONObject p = polys.optJSONObject(i);
                    if (p == null) continue;
                    String rel = p.optString("path", "");
                    if (rel.isEmpty()) continue;
                    Polaroid pol = new Polaroid();
                    pol.relPath = rel;
                    pol.fx     = p.has("fx") ? (float) p.optDouble("fx") : null;
                    pol.fy     = p.has("fy") ? (float) p.optDouble("fy") : null;
                    pol.fw     = p.has("fw") ? (float) p.optDouble("fw") : null;
                    pol.tilt   = (float) p.optDouble("tilt", 0.0);
                    d.polaroids.add(pol);
                }
            }
            return d;
        } catch (Exception ignored) {
            // Bad draft? Drop it silently — the page just stays blank.
            prefs.clearDraft();
            return null;
        }
    }

    /** Serializes and writes the draft. Silently clears the draft on
     *  serialization failure so the next visit gets a clean page rather
     *  than a corrupt one. */
    public void save(Draft draft) {
        if (draft == null) { clear(); return; }
        try {
            JSONObject doc = new JSONObject();
            doc.put("text", draft.text == null ? "" : draft.text);
            if (draft.strokesJson != null && !draft.strokesJson.isEmpty()) {
                doc.put("strokes", draft.strokesJson);
            }
            JSONArray polys = new JSONArray();
            for (Polaroid p : draft.polaroids) {
                JSONObject jp = new JSONObject();
                jp.put("path", p.relPath);
                if (p.fx != null) jp.put("fx", p.fx);
                if (p.fy != null) jp.put("fy", p.fy);
                if (p.fw != null) jp.put("fw", p.fw);
                jp.put("tilt", p.tilt);
                polys.put(jp);
            }
            doc.put("polaroids", polys);
            prefs.setDraft(doc.toString());
        } catch (Exception ignored) {
            // If serialization fails, prefer losing the draft to crashing.
            prefs.clearDraft();
        }
    }

    public void clear() {
        prefs.clearDraft();
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    /** Snapshot of the journal page held in scratch space. */
    public static final class Draft {
        /** Body text (may be empty). */
        public String text = "";
        /** Drawing strokes JSON, or {@code null} when the page has no drawing. */
        public String strokesJson;
        /** Polaroids on the page, in z-order. */
        public final List<Polaroid> polaroids = new ArrayList<>();
    }

    /** One polaroid as persisted in the draft. */
    public static final class Polaroid {
        /** Path relative to {@code Context.getFilesDir()} — e.g. {@code "polaroids/abc.png"}. */
        public String relPath;
        /** Left position as a fraction of container width (0–1), or null
         *  if this draft predates fractional positions. */
        public Float fx;
        /** Top position as a fraction of container height (0–1), or null. */
        public Float fy;
        /** Polaroid width as a fraction of container width (0–1), or null. */
        public Float fw;
        /** Tilt in degrees. 0 if unset. */
        public float tilt;
    }
}
