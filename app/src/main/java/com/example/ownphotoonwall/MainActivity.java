package com.example.ownphotoonwall;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
        AppCompatDelegate.setDefaultNightMode(isDarkTheme ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
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

        // --- 2. MENU BUTTONS LOGIC ---
        Button btnPlaceWidget = findViewById(R.id.btnPlaceWidget);
        Button btnGames = findViewById(R.id.btnGames);

        if (btnPlaceWidget != null && btnGames != null) {
            // DYNAMIC THEME SYNC FOR BOTH BUTTONS
            if (isDarkTheme) {
                ColorStateList darkBg = ColorStateList.valueOf(Color.parseColor("#D0BCFF"));
                int darkText = Color.parseColor("#381E72");

                btnPlaceWidget.setBackgroundTintList(darkBg);
                btnPlaceWidget.setTextColor(darkText);
                btnGames.setBackgroundTintList(darkBg);
                btnGames.setTextColor(darkText);
            } else {
                ColorStateList lightBg = ColorStateList.valueOf(Color.parseColor("#6750A4"));
                int lightText = Color.WHITE;

                btnPlaceWidget.setBackgroundTintList(lightBg);
                btnPlaceWidget.setTextColor(lightText);
                btnGames.setBackgroundTintList(lightBg);
                btnGames.setTextColor(lightText);
            }

            btnPlaceWidget.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, WidgetGalleryActivity.class)));
            btnGames.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, GamesGalleryActivity.class)));
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