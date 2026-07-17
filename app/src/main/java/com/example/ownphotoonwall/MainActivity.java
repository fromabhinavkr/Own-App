package com.example.ownphotoonwall;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
        AppCompatDelegate.setDefaultNightMode(isDarkTheme ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);

        // =========================================================
        // GLOBAL FIX: POCO/MOTOROLA STATUS BAR OVERLAP PREVENTION
        // =========================================================
        // Enable proper window insets handling for all modern devices
        EdgeToEdge.enable(this);
        Window window = getWindow();
        // Force the app to safely fit within the system windows (below status bar, above nav bar)
        WindowCompat.setDecorFitsSystemWindows(window, true);
        // =========================================================

        setContentView(R.layout.activity_main);

        // --- 1. THEME TOGGLE LOGIC ---
        ImageButton themeToggleBtn = findViewById(R.id.btn_app_theme_toggle);
        themeToggleBtn.setImageResource(isDarkTheme ? R.drawable.ic_moon : R.drawable.ic_sun);
        themeToggleBtn.setColorFilter(isDarkTheme ? Color.WHITE : Color.BLACK);

        themeToggleBtn.setOnClickListener(v -> {
            boolean newDark = !prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            prefs.edit().putBoolean(SnakeWidget.PREF_IS_DARK, newDark).apply();
            updateAllWidgets();
            AppCompatDelegate.setDefaultNightMode(newDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            recreate();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        // --- 2. MENU BUTTONS LOGIC (UPDATED FOR TOOLS-STYLE CARDS) ---
        LinearLayout btnPlaceWidget = findViewById(R.id.btnPlaceWidget);
        LinearLayout btnGames = findViewById(R.id.btnGames);
        LinearLayout btnTools = findViewById(R.id.btnTools);
        LinearLayout btnUtilities = findViewById(R.id.btnUtilities);

        TextView tvWidgetText = findViewById(R.id.tvWidgetText);
        TextView tvGamesText = findViewById(R.id.tvGamesText);
        TextView tvToolsText = findViewById(R.id.tvToolsText);
        TextView tvUtilitiesText = findViewById(R.id.tvUtilitiesText);

        View divWidget = findViewById(R.id.divWidget);
        View divGames = findViewById(R.id.divGames);
        View divTools = findViewById(R.id.divTools);
        View divUtilities = findViewById(R.id.divUtilities);

        if (btnPlaceWidget != null && btnGames != null && btnTools != null && btnUtilities != null) {

            // DYNAMIC THEME SYNC MATCHING THE TOOLS SECTION
            ColorStateList themeBg;
            int themeText;
            int dividerColor;

            if (isDarkTheme) {
                themeBg = ColorStateList.valueOf(Color.parseColor("#2C2C2E")); // Dark Grey Card
                themeText = Color.WHITE; // White Text
                dividerColor = Color.parseColor("#33FFFFFF"); // Faint White Line
            } else {
                themeBg = ColorStateList.valueOf(Color.WHITE); // White Card
                themeText = Color.parseColor("#333333"); // Dark Text
                dividerColor = Color.parseColor("#1A000000"); // Faint Dark Line
            }

            // Apply Backgrounds
            btnPlaceWidget.setBackgroundTintList(themeBg);
            btnGames.setBackgroundTintList(themeBg);
            btnTools.setBackgroundTintList(themeBg);
            btnUtilities.setBackgroundTintList(themeBg);

            // Apply Text Colors
            tvWidgetText.setTextColor(themeText);
            tvGamesText.setTextColor(themeText);
            tvToolsText.setTextColor(themeText);
            tvUtilitiesText.setTextColor(themeText);

            // Apply Divider Colors
            divWidget.setBackgroundColor(dividerColor);
            divGames.setBackgroundColor(dividerColor);
            divTools.setBackgroundColor(dividerColor);
            divUtilities.setBackgroundColor(dividerColor);

            // *Note: We specifically DO NOT tint the ImageViews anymore so your new colorful logos display their real colors!*

            // Click Listeners
            btnPlaceWidget.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, WidgetGalleryActivity.class)));
            btnGames.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, GamesGalleryActivity.class)));
            btnTools.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ToolsGalleryActivity.class)));
            btnUtilities.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, UtilitiesGalleryActivity.class)));
        }
    }

    private void updateAllWidgets() {
        Class<?>[] widgetClasses = {SnakeWidget.class, WaterWidgetProvider.class, HourglassWidget.class};
        for (Class<?> widgetClass : widgetClasses) {
            Intent intent = new Intent(this, widgetClass);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            int[] ids = AppWidgetManager.getInstance(this).getAppWidgetIds(new ComponentName(this, widgetClass));
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            sendBroadcast(intent);
        }
    }
}