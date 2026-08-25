package com.abhinav.ownapp;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // This lightweight global callback runs instantly when ANY screen is opened.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                // FIX: Skip exempted activities so transparent animations work smoothly
                if (isExemptedActivity(activity)) return;

                setupGlobalPadding(activity);
                applyGlobalStatusBarTheme(activity);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                if (isExemptedActivity(activity)) return;
                applyGlobalStatusBarTheme(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (isExemptedActivity(activity)) return;
                // Enforce the theme every time the screen comes to the foreground
                applyGlobalStatusBarTheme(activity);
            }

            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }

    private void setupGlobalPadding(Activity activity) {
        Window window = activity.getWindow();
        if (window != null) {
            View decorView = window.getDecorView();
            // Intercept the EXACT physical pixel height of the phone's Status Bar and Notch
            ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, windowInsets) -> {
                Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

                // Find the main root layout of whatever screen just opened
                View contentView = activity.findViewById(android.R.id.content);
                if (contentView != null) {
                    // Force a mathematical physical padding!
                    contentView.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                }

                // Consume the insets so no other XML or Activity accidentally double-pads it
                return WindowInsetsCompat.CONSUMED;
            });
        }
    }

    /**
     * This method perfectly replicates the exact proven logic from Collage Studio!
     */
    private void applyGlobalStatusBarTheme(Activity activity) {
        Window window = activity.getWindow();
        if (window != null) {

            // 1. Take manual control of the window drawing
            WindowCompat.setDecorFitsSystemWindows(window, false);

            // 2. Clear translucent flags to prevent the OS from blocking our color injection
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

            // --- CRITICAL FIX 1: Let the app draw into the camera notch area in landscape! ---
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams layoutParams = window.getAttributes();
                layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                window.setAttributes(layoutParams);
            }
            // ---------------------------------------------------------------------------------

            // 3. Pull the new 3-State Theme configuration
            SharedPreferences appPrefs = activity.getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);

            // Default to 1 (Standard Dark Mode) if no state is found
            int themeState = appPrefs.getInt("app_theme_state", 1);

            // 4. Set the solid background color for the status bar dynamically!
            int topBarBg;
            if (themeState == 0) {
                topBarBg = Color.WHITE; // Light Mode
            } else if (themeState == 1) {
                topBarBg = Color.parseColor("#1C1C1E"); // Standard Dark Mode
            } else {
                topBarBg = Color.parseColor("#000000"); // Star Mode (AMOLED PURE BLACK)
            }

            window.setStatusBarColor(topBarBg);

            // --- CRITICAL FIX 2: Make the Navigation Bar transparent so it doesn't leave a black void on rotation ---
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(false);
            }
            // --------------------------------------------------------------------------------------------------------

            // We must paint the underlying base window canvas so the system grey doesn't bleed through
            window.setBackgroundDrawable(new ColorDrawable(topBarBg));

            // 5. Instantly force the icon colors using the proven Legacy API
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();

            if (themeState == 0) {
                // Light mode needs dark icons for BOTH the Status Bar and Navigation Bar
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            } else {
                // Both Dark mode (1) and Star mode (2) need white icons
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            decorView.setSystemUiVisibility(flags);
        }
    }
    // Helper method to exempt games and galleries from global background painting
    private boolean isExemptedActivity(Activity activity) {
        return activity instanceof GlobeGameActivity ||
                activity instanceof GamesGalleryActivity ||
                activity instanceof ToolsGalleryActivity ||
                activity instanceof UtilitiesGalleryActivity ||
                activity instanceof WidgetGalleryActivity;
    }
}