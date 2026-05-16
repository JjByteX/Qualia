package com.example.qualia.ui;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keep the screen on across the entire app. Sessions, breath, and
        // closing are several minutes of passive watching — the Android
        // screen-off timeout would otherwise lock the device mid-session
        // and force the user to unlock. Home and the journal don't "need"
        // this strictly, but a meditative app keeping the screen on is
        // expected behaviour and harmless (the user closes the app when
        // they're done; no power leak).
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Keep content out of the display cutout area on phones with notches
        // or punch-holes. We're in immersive mode (bars hidden), but the
        // cutout itself still occupies real estate — without this attribute,
        // top-anchored text can slide up underneath the camera. NEVER mode
        // shrinks the window so the cutout never overlaps content.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
        }

        // Post so the DecorView is fully attached before we touch it.
        // Some OEM builds (Nothing OS, ColorOS, etc.) return a null DecorView
        // if getInsetsController() is called synchronously inside onCreate.
        getWindow().getDecorView().post(this::applyFullscreen);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        installSystemBarsInsetListener();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        installSystemBarsInsetListener();
    }

    /**
     * Apply padding equal to the system bars + display cutout safe area on
     * top of the activity's content root. This is a belt-and-suspenders
     * fix on top of LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER — it catches devices
     * or OEM skins that ignore the cutout flag, and it leaves room for any
     * transient status bar shown via swipe.
     */
    private void installSystemBarsInsetListener() {
        View root = findViewById(android.R.id.content);
        if (root == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int mask = WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout();
            androidx.core.graphics.Insets safe = insets.getInsets(mask);
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-apply after permission dialogs or overlays pull us out of
        // immersive mode. Safe to call directly here — window is attached.
        applyFullscreen();
    }

    private void applyFullscreen() {
        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#0D0D0D"));
        window.setNavigationBarColor(Color.parseColor("#0D0D0D"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ — WindowInsetsController.
            // BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE: swiping from an edge shows
            // bars briefly then hides them again automatically, like a game.
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController ctrl = window.getInsetsController();
            if (ctrl != null) {
                ctrl.hide(android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars());
                ctrl.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // API 26–29 fallback — same behaviour via legacy flags.
            //noinspection deprecation
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }
}
