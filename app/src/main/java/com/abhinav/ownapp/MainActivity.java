package com.abhinav.ownapp;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;

@SuppressWarnings("all")
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
        AppCompatDelegate.setDefaultNightMode(isDarkTheme ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, true);
        setContentView(R.layout.activity_main);

        DeviceStatsHelper.setupDashboard(this, isDarkTheme);

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
            ColorStateList themeBg;
            int themeText;
            int dividerColor;

            if (isDarkTheme) {
                themeBg = ColorStateList.valueOf(Color.parseColor("#2C2C2E")); // Dark Grey
                themeText = Color.WHITE;
                dividerColor = Color.parseColor("#33FFFFFF");
            } else {
                themeBg = ColorStateList.valueOf(Color.parseColor("#F4F4F5")); // Light flat Grey for contrast
                themeText = Color.parseColor("#333333");
                dividerColor = Color.parseColor("#1A000000");
            }

            btnPlaceWidget.setBackgroundTintList(themeBg);
            btnGames.setBackgroundTintList(themeBg);
            btnTools.setBackgroundTintList(themeBg);
            btnUtilities.setBackgroundTintList(themeBg);
            tvWidgetText.setTextColor(themeText);
            tvGamesText.setTextColor(themeText);
            tvToolsText.setTextColor(themeText);
            tvUtilitiesText.setTextColor(themeText);
            divWidget.setBackgroundColor(dividerColor);
            divGames.setBackgroundColor(dividerColor);
            divTools.setBackgroundColor(dividerColor);
            divUtilities.setBackgroundColor(dividerColor);

            btnPlaceWidget.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, WidgetGalleryActivity.class)));
            btnGames.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, GamesGalleryActivity.class)));
            btnTools.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ToolsGalleryActivity.class)));
            btnUtilities.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, UtilitiesGalleryActivity.class)));
        }
        applyOrientationLayout(getResources().getConfiguration().orientation);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientationLayout(newConfig.orientation);
    }

    private void applyOrientationLayout(int orientation) {
        View gridScrollView = findViewById(R.id.grid_scroll_view);
        if (gridScrollView == null) return;

        RelativeLayout.LayoutParams gridParams = (RelativeLayout.LayoutParams) gridScrollView.getLayoutParams();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            gridParams.width = (int) (screenWidth * 0.6f);
            gridParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            gridParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            gridParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        } else {
            gridParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            gridParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            gridParams.removeRule(RelativeLayout.CENTER_IN_PARENT);
            gridParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        }
        gridScrollView.setLayoutParams(gridParams);
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