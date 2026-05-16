package com.example.qualia.ui.journal;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.ExifInterface;

import java.io.IOException;

/**
 * Pure-function image helpers for the journal's polaroid attachment.
 *
 * <p>This class has no Android view dependencies and no instance state — it
 * exists to lift the image-processing weight out of {@code JournalActivity},
 * which had grown into a god-class. Anything that takes a {@link Bitmap} or
 * a file path and returns a {@link Bitmap} lives here; on-page placement,
 * drag handling, and the develop animation stay in the activity (they need
 * view trees and lifecycle).
 *
 * <p>The composition constants are exposed because the activity uses them to
 * compute the rotation pivot — the polaroid hangs from the baked-in pushpin,
 * and the pivot has to line up with that pin or rotation looks wrong.
 */
public final class PolaroidImage {

    // ── Composition constants (kept public so the activity's pivot maths
    // line up with the baked pin position; do not change without updating
    // applyPivotAtPin in JournalActivity too). ────────────────────────────────
    public static final int FRAME_TOP     = 24;
    public static final int FRAME_SIDES   = 24;
    public static final int FRAME_BOTTOM  = 88;
    public static final int SHADOW        = 18;
    public static final int PIN_RADIUS    = 14;
    /** Y-offset of the pin centre in the composed bitmap, in pixels. */
    public static final int PIN_Y_PX      = SHADOW + FRAME_TOP / 2;

    private PolaroidImage() {}

    /**
     * Decodes the file at {@code path}, downsampling to roughly 1280 px on
     * the long edge to keep memory in check, and rotates per the EXIF
     * orientation tag so portraits taken in landscape mode arrive upright.
     * Returns {@code null} on decode failure; missing/unreadable EXIF is
     * tolerated silently (we just return the un-rotated bitmap).
     */
    public static Bitmap decodeAndOrient(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);

        final int targetMax = 1280;
        int sample = 1;
        while ((bounds.outWidth / sample) > targetMax
                || (bounds.outHeight / sample) > targetMax) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap raw = BitmapFactory.decodeFile(path, opts);
        if (raw == null) return null;

        try {
            ExifInterface exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            float rotate = 0;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:  rotate = 90;  break;
                case ExifInterface.ORIENTATION_ROTATE_180: rotate = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: rotate = 270; break;
                default: /* nothing */
            }
            if (rotate != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotate);
                Bitmap rotated = Bitmap.createBitmap(raw, 0, 0,
                        raw.getWidth(), raw.getHeight(), m, true);
                if (rotated != raw) raw.recycle();
                return rotated;
            }
        } catch (IOException ignored) {
            // No EXIF or unreadable — fall through with the un-rotated bitmap.
        }
        return raw;
    }

    /**
     * Square-crops, frames in white (taller bottom strip), bakes a small red
     * pushpin at the top-centre, and adds a soft drop shadow. Returns an
     * opaque ARGB bitmap ready to overlay on the page.
     */
    public static Bitmap composePolaroid(Bitmap raw) {
        // Square crop from the centre.
        int side = Math.min(raw.getWidth(), raw.getHeight());
        int x = (raw.getWidth()  - side) / 2;
        int y = (raw.getHeight() - side) / 2;
        Bitmap square = Bitmap.createBitmap(raw, x, y, side, side);

        int top = FRAME_TOP, sides = FRAME_SIDES,
            bottom = FRAME_BOTTOM, shadow = SHADOW;
        int W = square.getWidth() + sides * 2;
        int H = square.getHeight() + top + bottom;

        Bitmap out = Bitmap.createBitmap(W + shadow * 2, H + shadow * 2,
                                         Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);

        // Soft shadow under the polaroid.
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0x66000000);
        shadowPaint.setMaskFilter(new BlurMaskFilter(
                shadow, BlurMaskFilter.Blur.NORMAL));
        RectF shadowRect = new RectF(shadow + 4, shadow + 6,
                                     shadow + W + 4, shadow + H + 6);
        c.drawRect(shadowRect, shadowPaint);

        // White frame.
        Paint frame = new Paint();
        frame.setColor(0xFFFAF6EE);
        c.drawRect(shadow, shadow, shadow + W, shadow + H, frame);

        // The photo.
        c.drawBitmap(square, shadow + sides, shadow + top, null);

        // Barely-there inner shadow on the photo edges (printing artifact).
        Paint inner = new Paint();
        inner.setColor(0x10000000);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(1.5f);
        c.drawRect(shadow + sides, shadow + top,
                   shadow + sides + square.getWidth(),
                   shadow + top  + square.getHeight(), inner);

        // Pushpin at top-centre. Three layers: a soft cast shadow, a domed
        // red head with a radial gradient, and a small highlight dot to sell
        // the dome's curvature.
        int pinCx = shadow + W / 2;
        int pinCy = PIN_Y_PX;
        int pinR  = PIN_RADIUS;

        Paint pinShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinShadow.setColor(0x55000000);
        pinShadow.setMaskFilter(new BlurMaskFilter(
                4, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(pinCx + 1.5f, pinCy + 2.5f, pinR, pinShadow);

        Paint pinHead = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinHead.setShader(new RadialGradient(
                pinCx - pinR * 0.35f, pinCy - pinR * 0.45f, pinR * 1.6f,
                0xFFFF8866, 0xFF8C1818,
                Shader.TileMode.CLAMP));
        c.drawCircle(pinCx, pinCy, pinR, pinHead);

        Paint pinHighlight = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinHighlight.setColor(0xCCFFFFFF);
        c.drawCircle(pinCx - pinR * 0.4f, pinCy - pinR * 0.45f,
                     pinR * 0.32f, pinHighlight);

        // A whisper of an outline so the head reads even on a busy photo.
        Paint pinRim = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinRim.setColor(0x33000000);
        pinRim.setStyle(Paint.Style.STROKE);
        pinRim.setStrokeWidth(1f);
        c.drawCircle(pinCx, pinCy, pinR, pinRim);

        return out;
    }
}
