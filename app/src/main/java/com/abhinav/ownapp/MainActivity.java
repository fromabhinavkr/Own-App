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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

@SuppressWarnings("all")
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);

        // --- 3-STATE THEME LOGIC ---
        // 0 = Light, 1 = Dark, 2 = AMOLED Black (Star)
        int themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            // Migrate legacy boolean users to the new int-based state
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = oldDark ? 1 : 0;
            prefs.edit().putInt("app_theme_state", themeState).apply();
        }

        AppCompatDelegate.setDefaultNightMode(themeState == 0 ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, true);
        setContentView(R.layout.activity_main);

        // --- Navigation Bar Clipping Fix ---
        View gridScrollView = findViewById(R.id.grid_scroll_view);
        if (gridScrollView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(gridScrollView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        v.getPaddingLeft(),
                        v.getPaddingTop(),
                        v.getPaddingRight(),
                        systemBars.bottom + 48
                );
                return insets;
            });
        }
        // -----------------------------------

        // Dashboard requires a boolean. Both state 1 and 2 are considered "Dark" for the stats dashboard.
        boolean isDarkTheme = (themeState != 0);
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
                "Welcome!", "Hello again!", "Hey there!", "Think Twice", "Wanna Play?",
                "Chill Out!", "Let's go!", "Don't Panic!", "Stay Strong!", "Enjoy Life",
                "Inhale, Exhale", "Well Done!", "Great day!", "Hello! Hello!", "Time's running",
                "Get ready!", "Hey Master!", "Let's Play", "How's life?", "What's up?",
                "All good?", "Think Differently", "Just Imagine", "Hey Mate!", "Howdy Partner"
        };
        int greetingIndex = prefs.getInt("greeting_index", 0);
        tvWelcome.setText(dynamicGreetings[greetingIndex]);
        int nextIndex = (greetingIndex + 1) % dynamicGreetings.length;
        prefs.edit().putInt("greeting_index", nextIndex).apply();

        // Apply Theme Toggle Icon based on Current State
        if (themeState == 0) {
            themeToggleBtn.setImageResource(R.drawable.ic_sun);
            themeToggleBtn.setColorFilter(Color.BLACK);
        } else if (themeState == 1) {
            themeToggleBtn.setImageResource(R.drawable.ic_moon);
            themeToggleBtn.setColorFilter(Color.WHITE);
        } else {
            // Using Android's default built-in star.
            themeToggleBtn.setImageResource(android.R.drawable.star_on);
            themeToggleBtn.setColorFilter(Color.WHITE);
        }

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
            int currentState = prefs.getInt("app_theme_state", 1);
            int nextState = (currentState + 1) % 3; // Cycles: 0 -> 1 -> 2 -> 0

            prefs.edit().putInt("app_theme_state", nextState).apply();
            // Preserve legacy boolean mapping for widgets/other activities dependent on it
            prefs.edit().putBoolean(SnakeWidget.PREF_IS_DARK, nextState != 0).apply();

            updateAllWidgets();
            AppCompatDelegate.setDefaultNightMode(nextState == 0 ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
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
            int rootBg;
            int accentColor = Color.parseColor("#5A9AF4");

            // --- 3-STATE THEME COLOR INJECTION ---
            if (themeState == 0) { // Light Mode
                rootBg = Color.WHITE;
                themeBg = ColorStateList.valueOf(Color.parseColor("#F2F2F7"));
                themeText = Color.parseColor("#333333");
                secondaryText = Color.parseColor("#666666");
            } else if (themeState == 1) { // Standard Dark Mode
                rootBg = Color.parseColor("#1C1C1E");
                themeBg = ColorStateList.valueOf(Color.parseColor("#2C2C2E"));
                themeText = Color.WHITE;
                secondaryText = Color.parseColor("#BBBBBB");
            } else { // Star Mode (AMOLED PURE BLACK)
                rootBg = Color.parseColor("#000000"); // Infinite Pure Black Canvas
                themeBg = ColorStateList.valueOf(Color.parseColor("#1C1C1E")); // Deep grey floating cards
                themeText = Color.WHITE;
                secondaryText = Color.parseColor("#BBBBBB");
            }

            // Apply the Background dynamically
            findViewById(R.id.main_root).setBackgroundColor(rootBg);

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

            // --- Calculate click coordinates and launch Circular Reveal for Widgets ---
            btnPlaceWidget.setOnClickListener(v -> {
                int[] location = new int[2];
                btnPlaceWidget.getLocationOnScreen(location);
                int cx = location[0] + (btnPlaceWidget.getWidth() / 2);
                int cy = location[1] + (btnPlaceWidget.getHeight() / 2);

                Intent intent = new Intent(MainActivity.this, WidgetGalleryActivity.class);
                intent.putExtra("REVEAL_X", cx);
                intent.putExtra("REVEAL_Y", cy);
                startActivity(intent);

                overridePendingTransition(0, 0);
            });

            // --- Calculate click coordinates and launch Circular Reveal for Games ---
            btnGames.setOnClickListener(v -> {
                int[] location = new int[2];
                btnGames.getLocationOnScreen(location);
                int cx = location[0] + (btnGames.getWidth() / 2);
                int cy = location[1] + (btnGames.getHeight() / 2);

                Intent intent = new Intent(MainActivity.this, GamesGalleryActivity.class);
                intent.putExtra("REVEAL_X", cx);
                intent.putExtra("REVEAL_Y", cy);
                startActivity(intent);

                overridePendingTransition(0, 0);
            });

            // --- Calculate click coordinates and launch Circular Reveal for Tools ---
            btnTools.setOnClickListener(v -> {
                int[] location = new int[2];
                btnTools.getLocationOnScreen(location);
                int cx = location[0] + (btnTools.getWidth() / 2);
                int cy = location[1] + (btnTools.getHeight() / 2);

                Intent intent = new Intent(MainActivity.this, ToolsGalleryActivity.class);
                intent.putExtra("REVEAL_X", cx);
                intent.putExtra("REVEAL_Y", cy);
                startActivity(intent);

                overridePendingTransition(0, 0);
            });

            // --- Calculate click coordinates and launch Circular Reveal for Utilities ---
            btnUtilities.setOnClickListener(v -> {
                int[] location = new int[2];
                btnUtilities.getLocationOnScreen(location);
                int cx = location[0] + (btnUtilities.getWidth() / 2);
                int cy = location[1] + (btnUtilities.getHeight() / 2);

                Intent intent = new Intent(MainActivity.this, UtilitiesGalleryActivity.class);
                intent.putExtra("REVEAL_X", cx);
                intent.putExtra("REVEAL_Y", cy);
                startActivity(intent);

                overridePendingTransition(0, 0);
            });
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
            gridParams.width = (int) (screenWidth * 0.5f);
            gridParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            gridParams.removeRule(RelativeLayout.BELOW);
            gridParams.addRule(RelativeLayout.BELOW, R.id.header_layout);
            gridParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

            dashParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            dashParams.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
            dashParams.addRule(RelativeLayout.LEFT_OF, R.id.grid_scroll_view);
        } else {
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