package com.example.qualia.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * A simple HSV color picker view.
 * Draws a hue bar at the bottom and an SV gradient panel above it.
 */
public class ColorPickerView extends View {

    public interface OnColorChangedListener {
        void onColorChanged(int color);
    }

    // Hue bar height in dp
    private static final int HUE_BAR_HEIGHT_DP = 24;
    private static final int HUE_BAR_MARGIN_DP  = 8;

    private final Paint mSatValPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHuePaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTrackerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float mHue        = 0f;   // 0..360
    private float mSat        = 1f;   // 0..1
    private float mVal        = 1f;   // 0..1

    private float mHueBarHeight;
    private float mHueBarMargin;

    private OnColorChangedListener mListener;

    // Touch target tracking
    private boolean mDraggingHue = false;

    public ColorPickerView(Context context) {
        super(context);
        init(context);
    }

    public ColorPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ColorPickerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        mHueBarHeight  = HUE_BAR_HEIGHT_DP  * density;
        mHueBarMargin  = HUE_BAR_MARGIN_DP  * density;

        mTrackerPaint.setStyle(Paint.Style.STROKE);
        mTrackerPaint.setStrokeWidth(2f * density);
        mTrackerPaint.setColor(Color.WHITE);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        mListener = listener;
    }

    /** Set the picker to a specific color. */
    public void setColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        mHue = hsv[0];
        mSat = hsv[1];
        mVal = hsv[2];
        invalidate();
    }

    /** Get the currently selected color. */
    public int getColor() {
        return Color.HSVToColor(new float[]{mHue, mSat, mVal});
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();

        float svBottom = h - mHueBarHeight - mHueBarMargin;

        // --- Saturation/Value panel ---
        // Horizontal: white → pure hue
        Shader hueShader = new LinearGradient(0, 0, w, 0,
                Color.WHITE, Color.HSVToColor(new float[]{mHue, 1f, 1f}),
                Shader.TileMode.CLAMP);
        mSatValPaint.setShader(hueShader);
        canvas.drawRect(0, 0, w, svBottom, mSatValPaint);

        // Vertical: transparent → black (multiply blend via two passes)
        Shader darkShader = new LinearGradient(0, 0, 0, svBottom,
                Color.TRANSPARENT, Color.BLACK,
                Shader.TileMode.CLAMP);
        mSatValPaint.setShader(darkShader);
        canvas.drawRect(0, 0, w, svBottom, mSatValPaint);

        // SV tracker circle
        float cx = mSat * w;
        float cy = (1f - mVal) * svBottom;
        mTrackerPaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, 10f, mTrackerPaint);

        // --- Hue bar ---
        float hueTop = svBottom + mHueBarMargin;
        int[] hueColors = new int[]{
                0xFFFF0000, 0xFFFFFF00, 0xFF00FF00,
                0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
        };
        Shader hueBarShader = new LinearGradient(0, hueTop, w, hueTop,
                hueColors, null, Shader.TileMode.CLAMP);
        mHuePaint.setShader(hueBarShader);
        canvas.drawRect(0, hueTop, w, h, mHuePaint);

        // Hue tracker line
        float hx = (mHue / 360f) * w;
        mTrackerPaint.setColor(Color.WHITE);
        canvas.drawLine(hx, hueTop, hx, h, mTrackerPaint);
    }

    // -------------------------------------------------------------------------
    // Touch
    // -------------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        float w = getWidth();
        float h = getHeight();
        float svBottom = h - mHueBarHeight - mHueBarMargin;
        float hueTop   = svBottom + mHueBarMargin;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDraggingHue = (y >= hueTop);
                // fall through
            case MotionEvent.ACTION_MOVE:
                if (mDraggingHue) {
                    mHue = clamp(x / w, 0f, 1f) * 360f;
                } else {
                    mSat = clamp(x / w, 0f, 1f);
                    mVal = 1f - clamp(y / svBottom, 0f, 1f);
                }
                invalidate();
                if (mListener != null) mListener.onColorChanged(getColor());
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDraggingHue = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
