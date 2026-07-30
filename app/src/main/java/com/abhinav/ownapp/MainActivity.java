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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

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

        // Header Elements
        ImageButton themeToggleBtn = findViewById(R.id.btn_app_theme_toggle);
        TextView tvGreeting = findViewById(R.id.tvGreeting);
        TextView tvWelcome = findViewById(R.id.tvWelcome);
        TextView tvDate = findViewById(R.id.tvDate);
        LinearLayout datePill = findViewById(R.id.date_pill);
        ImageView ivCalendar = findViewById(R.id.ivCalendar);

        // 25 General Rotating Two-Word Greetings Logic
        String[] dynamicGreetings = {
                "Welcome back",
                "Hello again!",
                "Hey there!",
                "Think Twice",
                "Wanna Play?",
                "Chill Out!",
                "Let's go!",
                "Don't Panic!",
                "Stay Strong!",
                "Enjoy Life",
                "Inhale, Exhale",
                "Well Done!",
                "Great day!",
                "Hello! Hello!",
                "Time's running",
                "Get ready!",
                "Hey Master!",
                "Let's Play",
                "How's life?",
                "What's up?",
                "All good?",
                "Think Differently",
                "Just Imagine",
                "Hey Mate!",
                "Howdy Partner"
        };
        int greetingIndex = prefs.getInt("greeting_index", 0);
        tvWelcome.setText(dynamicGreetings[greetingIndex]);
        int nextIndex = (greetingIndex + 1) % dynamicGreetings.length;
        prefs.edit().putInt("greeting_index", nextIndex).apply();

        themeToggleBtn.setImageResource(isDarkTheme ? R.drawable.ic_moon : R.drawable.ic_sun);
        themeToggleBtn.setColorFilter(isDarkTheme ? Color.WHITE : Color.BLACK);

        // Set Dynamic Date
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        tvDate.setText(sdf.format(new Date()));

        // Set Dynamic Greeting based on time
        Calendar c = Calendar.getInstance();
        int timeOfDay = c.get(Calendar.HOUR_OF_DAY);
        if(timeOfDay >= 0 && timeOfDay < 12){
            tvGreeting.setText("Good Morning");
        } else if(timeOfDay >= 12 && timeOfDay < 16){
            tvGreeting.setText("Good Afternoon");
        } else {
            tvGreeting.setText("Good Evening");
        }

        themeToggleBtn.setOnClickListener(v -> {
            boolean newDark = !prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            prefs.edit().putBoolean(SnakeWidget.PREF_IS_DARK, newDark).apply();
            updateAllWidgets();
            AppCompatDelegate.setDefaultNightMode(newDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            recreate();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        // Grid Elements
        LinearLayout btnPlaceWidget = findViewById(R.id.btnPlaceWidget);
        LinearLayout btnGames = findViewById(R.id.btnGames);
        LinearLayout btnTools = findViewById(R.id.btnTools);
        LinearLayout btnUtilities = findViewById(R.id.btnUtilities);

        TextView tvWidgetText = findViewById(R.id.tvWidgetText);
        TextView tvGamesText = findViewById(R.id.tvGamesText);
        TextView tvToolsText = findViewById(R.id.tvToolsText);
        TextView tvUtilitiesText = findViewById(R.id.tvUtilitiesText);

        TextView tvWidgetSub = findViewById(R.id.tvWidgetSub);
        TextView tvGamesSub = findViewById(R.id.tvGamesSub);
        TextView tvToolsSub = findViewById(R.id.tvToolsSub);
        TextView tvUtilitiesSub = findViewById(R.id.tvUtilitiesSub);

        if (btnPlaceWidget != null && btnGames != null && btnTools != null && btnUtilities != null) {
            ColorStateList themeBg;
            int themeText;
            int secondaryText;
            int accentColor = Color.parseColor("#5A9AF4"); // Modern Blue for Calendar Pill

            if (isDarkTheme) {
                themeBg = ColorStateList.valueOf(Color.parseColor("#2C2C2E")); // Dark Grey
                themeText = Color.WHITE;
                secondaryText = Color.parseColor("#BBBBBB"); // Light Grey Subtitle
            } else {
                themeBg = ColorStateList.valueOf(Color.parseColor("#F4F4F5")); // Light flat Grey
                themeText = Color.parseColor("#333333");
                secondaryText = Color.parseColor("#666666"); // Dark Grey Subtitle
            }

            // Apply Theme to Top Header
            tvWelcome.setTextColor(themeText);
            tvGreeting.setTextColor(secondaryText);
            datePill.setBackgroundTintList(themeBg);
            tvDate.setTextColor(accentColor);
            ivCalendar.setColorFilter(accentColor);

            // Apply Theme to Grid Cards
            btnPlaceWidget.setBackgroundTintList(themeBg);
            btnGames.setBackgroundTintList(themeBg);
            btnTools.setBackgroundTintList(themeBg);
            btnUtilities.setBackgroundTintList(themeBg);

            tvWidgetText.setTextColor(themeText);
            tvGamesText.setTextColor(themeText);
            tvToolsText.setTextColor(themeText);
            tvUtilitiesText.setTextColor(themeText);

            tvWidgetSub.setTextColor(secondaryText);
            tvGamesSub.setTextColor(secondaryText);
            tvToolsSub.setTextColor(secondaryText);
            tvUtilitiesSub.setTextColor(secondaryText);

            // Listeners
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
        View dashboardContainer = findViewById(R.id.dashboard_container);
        if (gridScrollView == null || dashboardContainer == null) return;

        RelativeLayout.LayoutParams gridParams = (RelativeLayout.LayoutParams) gridScrollView.getLayoutParams();
        RelativeLayout.LayoutParams dashParams = (RelativeLayout.LayoutParams) dashboardContainer.getLayoutParams();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Adaptive Landscape: Dashboard left, Grid right
            gridParams.width = (int) (screenWidth * 0.5f);
            gridParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            gridParams.removeRule(RelativeLayout.BELOW);
            gridParams.addRule(RelativeLayout.BELOW, R.id.header_layout);
            gridParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

            dashParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            dashParams.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
            dashParams.addRule(RelativeLayout.LEFT_OF, R.id.grid_scroll_view);
        } else {
            // Adaptive Portrait: Grid stays flexibly below Dashboard
            gridParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            gridParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            gridParams.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            gridParams.removeRule(RelativeLayout.BELOW);
            gridParams.addRule(RelativeLayout.BELOW, R.id.dashboard_container);

            dashParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            dashParams.removeRule(RelativeLayout.LEFT_OF);
        }
        gridScrollView.setLayoutParams(gridParams);
        dashboardContainer.setLayoutParams(dashParams);
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