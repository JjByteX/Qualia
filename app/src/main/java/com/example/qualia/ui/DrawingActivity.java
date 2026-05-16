package com.example.qualia.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.qualia.R;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Chalkboard sketch screen.
 *
 * <p>Toolbar (bottom): back · undo · clear · eraser · save.
 * Palette: warm white · blush · mint · sky · lavender · sun — chalk-bright
 * versions of the app's general palette so they actually read against the
 * dark slate background.
 *
 * <p>When the user taps "save", the rendered chalk bitmap is written to a
 * private file and the path is returned to {@link JournalActivity} via
 * {@link #setResult}. The journal screen then attaches that file to the
 * pending journal entry on save. Drawings produced this way are not orphaned
 * on disk the way they used to be.
 */
public class DrawingActivity extends BaseActivity {

    /** Result extra: relative file path under {@code getFilesDir()}. */
    public static final String RESULT_DRAWING_REL_PATH = "drawing_rel_path";

    /** Subdirectory of {@code getFilesDir()} where chalk drawings live. */
    public static final String DRAWINGS_DIR = "drawings";

    private DrawingView drawingView;
    private TextView    btnEraser;

    /**
     * Chalk-bright palette. Reads against a dark slate the way classroom chalk
     * does. The general-purpose Qualia palette (warm white / blush / sage /
     * sky / dusk / near-ink) is used elsewhere in the app and intentionally
     * kept dimmer; here we step up a notch so each colour is actually visible.
     */
    private static final int[] PALETTE = {
            0xFFFAF6EE, // chalk white
            0xFFE8B4AA, // blush
            0xFFA6CFB6, // mint
            0xFF92BBD9, // sky
            0xFFB6A4D4, // lavender
            0xFFF0DC8C, // sun
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawing);

        drawingView = findViewById(R.id.drawingView);
        btnEraser   = findViewById(R.id.btnEraser);

        // ── Back ──────────────────────────────────────────────────────────────
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // ── Undo ──────────────────────────────────────────────────────────────
        findViewById(R.id.btnUndo).setOnClickListener(v -> drawingView.undo());

        // ── Clear ─────────────────────────────────────────────────────────────
        findViewById(R.id.btnClear).setOnClickListener(v -> drawingView.clear());

        // ── Eraser toggle ─────────────────────────────────────────────────────
        btnEraser.setOnClickListener(v -> {
            boolean nowErasing = !drawingView.isErasing();
            drawingView.setErasing(nowErasing);
            btnEraser.setTextColor(nowErasing
                    ? 0xFFD4C68A    // active: chalk-yellow highlight
                    : 0xFF8DA89C);  // inactive: dim mint
        });

        // ── Save ──────────────────────────────────────────────────────────────
        findViewById(R.id.btnSave).setOnClickListener(v -> saveDrawing());

        // ── Colour swatches ───────────────────────────────────────────────────
        int[] swatchIds = {
                R.id.swatch0, R.id.swatch1, R.id.swatch2,
                R.id.swatch3, R.id.swatch4, R.id.swatch5
        };
        for (int i = 0; i < swatchIds.length; i++) {
            final int color    = PALETTE[i];
            final int swatchId = swatchIds[i];
            findViewById(swatchId).setOnClickListener(v -> {
                drawingView.setColor(color);
                drawingView.setErasing(false);
                btnEraser.setTextColor(0xFF8DA89C);
                updateSwatchSelection(swatchIds, swatchId);
            });
        }
        updateSwatchSelection(swatchIds, swatchIds[0]); // default: chalk white

        // ── Brush size ────────────────────────────────────────────────────────
        SeekBar seekBar = findViewById(R.id.seekBrushSize);
        seekBar.setMax(60);
        seekBar.setProgress(10);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                drawingView.setBrushSize(Math.max(2, p));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb)  {}
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateSwatchSelection(int[] ids, int selectedId) {
        for (int id : ids) {
            View v = findViewById(id);
            v.setAlpha(id == selectedId ? 1f : 0.40f);
            v.setScaleX(id == selectedId ? 1.25f : 1f);
            v.setScaleY(id == selectedId ? 1.25f : 1f);
        }
    }

    /**
     * Renders the chalkboard to a PNG, stores it in {@code getFilesDir()/drawings/},
     * and returns the relative path to the caller. The journal screen attaches the
     * file to the pending entry; if there is no caller (e.g. opened standalone in
     * the future) the file is still written so it isn't lost.
     */
    private void saveDrawing() {
        try {
            Bitmap bmp  = drawingView.getBitmap();
            File   dir  = new File(getFilesDir(), DRAWINGS_DIR);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            String name = "chalk_" + System.currentTimeMillis() + ".png";
            File out = new File(dir, name);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }

            String relPath = DRAWINGS_DIR + "/" + name;
            Intent data = new Intent();
            data.putExtra(RESULT_DRAWING_REL_PATH, relPath);
            setResult(Activity.RESULT_OK, data);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't save.", Toast.LENGTH_SHORT).show();
        }
    }
}
