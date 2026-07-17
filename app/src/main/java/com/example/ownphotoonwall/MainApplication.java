package com.example.ownphotoonwall;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
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
                setupGlobalPadding(activity);
                applyGlobalStatusBarTheme(activity);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                applyGlobalStatusBarTheme(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
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
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

            // 3. CRITICAL FIX: Pull the global theme state using the EXACT same variable as your Main App!
            SharedPreferences appPrefs = activity.getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
            boolean isDarkTheme = appPrefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

            // 4. Set the solid background color for the status bar dynamically!
            int topBarBg = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.WHITE;
            window.setStatusBarColor(topBarBg);

            // 5. Instantly force the icon colors using the proven Legacy API
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();

            if (isDarkTheme) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; // White text for dark backgrounds
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;  // Dark text for light backgrounds
            }
            decorView.setSystemUiVisibility(flags);
        }
    }
}