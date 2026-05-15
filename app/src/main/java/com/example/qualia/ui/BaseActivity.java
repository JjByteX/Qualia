package com.example.qualia.ui;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;

import androidx.appcompat.app.AppCompatActivity;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Post so the DecorView is fully attached before we touch it.
        // Some OEM builds (Nothing OS, ColorOS, etc.) return a null DecorView
        // if getInsetsController() is called synchronously inside onCreate.
        getWindow().getDecorView().post(this::applyFullscreen);
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
