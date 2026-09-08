package com.abhinav.ownapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.CalendarView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

@SuppressWarnings("all")
public class MainActivity extends AppCompatActivity {

    public static Bitmap sThemeSnapshot = null;
    public static int sRevealX = -1;
    public static int sRevealY = -1;
    public static int sPillX = -1;
    public static int sPillY = -1;
    public static int sPillWidth = -1;
    public static int sPillHeight = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);

        int tempThemeState = prefs.getInt("app_theme_state", -1);
        if (tempThemeState == -1) {
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            tempThemeState = oldDark ? 1 : 0;
            prefs.edit().putInt("app_theme_state", tempThemeState).apply();
        }
        final int themeState = tempThemeState;

        AppCompatDelegate.setDefaultNightMode(themeState == 0 ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, true);
        setContentView(R.layout.activity_main);

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

        boolean isDarkTheme = (themeState != 0);
        DeviceStatsHelper.setupDashboard(this, isDarkTheme);

        ImageButton themeToggleBtn = findViewById(R.id.btn_app_theme_toggle);
        LinearLayout topCapsule = findViewById(R.id.top_capsule);

        FrameLayout themeTogglePill = findViewById(R.id.theme_toggle_pill);
        LinearLayout timePill = findViewById(R.id.time_pill);
        LinearLayout datePill = findViewById(R.id.date_pill);

        TextClock tvTime = findViewById(R.id.tvTime);
        TextClock tvDate = findViewById(R.id.tvDate);

        String[] dynamicGreetings = {
                "Welcome!", "Hello again!", "Hey there!", "Think Twice", "Wanna Play?",
                "Chill Out!", "Let's go!", "Don't Panic!", "Stay Strong!", "Enjoy Life",
                "Inhale, Exhale", "Well Done!", "Great day!", "Hello! Hello!", "Time's running",
                "Get ready!", "Hey Master!", "Let's Play", "How's life?", "What's up?",
                "All good?", "Think Differently", "Just Imagine", "Hey Mate!", "Howdy Partner"
        };
        int greetingIndex = prefs.getInt("greeting_index", 0);
        int nextIndex = (greetingIndex + 1) % dynamicGreetings.length;
        prefs.edit().putInt("greeting_index", nextIndex).apply();

        if (themeState == 0) {
            themeToggleBtn.setImageResource(R.drawable.ic_sun);
            themeToggleBtn.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
        } else if (themeState == 1) {
            themeToggleBtn.setImageResource(R.drawable.ic_moon);
            themeToggleBtn.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        } else {
            themeToggleBtn.setImageResource(android.R.drawable.star_on);
            themeToggleBtn.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }

        themeToggleBtn.setOnClickListener(v -> {
            if (sThemeSnapshot != null) return;
            themeToggleBtn.setEnabled(false);

            int[] location = new int[2];
            themeTogglePill.getLocationInWindow(location);
            sPillX = location[0];
            sPillY = location[1];
            sPillWidth = themeTogglePill.getWidth();
            sPillHeight = themeTogglePill.getHeight();
            sRevealX = sPillX + (sPillWidth / 2);
            sRevealY = sPillY + (sPillHeight / 2);

            ViewGroup root = findViewById(android.R.id.content);
            sThemeSnapshot = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(sThemeSnapshot);
            root.draw(canvas);

            int currentState = prefs.getInt("app_theme_state", 1);
            int nextState = (currentState + 1) % 3;
            prefs.edit().putInt("app_theme_state", nextState).apply();
            prefs.edit().putBoolean(SnakeWidget.PREF_IS_DARK, nextState != 0).apply();

            updateAllWidgets();

            AppCompatDelegate.setDefaultNightMode(nextState == 0 ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
            recreate();
            overridePendingTransition(0, 0);
        });

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

        RelativeLayout ramCardBg = findViewById(R.id.ramCardBg);
        LinearLayout storageCardBg = findViewById(R.id.storageCardBg);
        LinearLayout batteryCardBg = findViewById(R.id.batteryCardBg);

        TextView tvRamTitle = findViewById(R.id.tvRamTitle);
        TextView tvRamUsed = findViewById(R.id.tvRamUsed);
        TextView tvRamTotal = findViewById(R.id.tvRamTotal);
        TextView tvRamFree = findViewById(R.id.tvRamFree);
        TextView tvStorageTitle = findViewById(R.id.tvStorageTitle);
        TextView tvBatteryTitle = findViewById(R.id.tvBatteryTitle);

        RamGraphView ramGraphView = findViewById(R.id.ramGraphView);

        if (btnPlaceWidget != null && btnGames != null && btnTools != null && btnUtilities != null) {
            ColorStateList themeBg;
            ColorStateList innerBg;
            ColorStateList dashCardBg;
            int themeText;
            int secondaryText;
            int rootBg;

            if (themeState == 0) {
                rootBg = Color.WHITE;
                themeBg = ColorStateList.valueOf(Color.parseColor("#F2F2F7"));
                innerBg = ColorStateList.valueOf(Color.parseColor("#FFFFFF"));
                dashCardBg = ColorStateList.valueOf(Color.parseColor("#F2F2F7"));
                themeText = Color.parseColor("#333333");
                secondaryText = Color.parseColor("#666666");
            } else if (themeState == 1) {
                rootBg = Color.parseColor("#1C1C1E");
                themeBg = ColorStateList.valueOf(Color.parseColor("#2C2C2E"));
                innerBg = ColorStateList.valueOf(Color.parseColor("#1C1C1E"));
                dashCardBg = ColorStateList.valueOf(Color.parseColor("#2C2C2E"));
                themeText = Color.WHITE;
                secondaryText = Color.parseColor("#BBBBBB");
            } else {
                rootBg = Color.parseColor("#000000");
                themeBg = ColorStateList.valueOf(Color.parseColor("#1C1C1E"));
                innerBg = ColorStateList.valueOf(Color.parseColor("#000000"));
                dashCardBg = ColorStateList.valueOf(Color.parseColor("#1C1C1E"));
                themeText = Color.WHITE;
                secondaryText = Color.parseColor("#BBBBBB");
            }

            findViewById(R.id.main_root).setBackgroundColor(rootBg);

            GradientDrawable outerCapsuleGd = new GradientDrawable();
            outerCapsuleGd.setColor(themeBg.getDefaultColor());
            outerCapsuleGd.setCornerRadius(90f);
            topCapsule.setBackground(outerCapsuleGd);
            topCapsule.setElevation(0f);
            topCapsule.setClipToOutline(true);

            GradientDrawable innerCircleGd = new GradientDrawable();
            innerCircleGd.setShape(GradientDrawable.OVAL);
            innerCircleGd.setColor(innerBg.getDefaultColor());
            themeTogglePill.setBackground(innerCircleGd);
            themeTogglePill.setElevation(0f);
            themeTogglePill.setClipToOutline(true);

            GradientDrawable timePillGd = new GradientDrawable();
            timePillGd.setCornerRadius(200f);
            timePillGd.setColor(innerBg.getDefaultColor());
            timePill.setBackground(timePillGd);
            timePill.setElevation(0f);
            timePill.setClipToOutline(true);

            GradientDrawable datePillGd = new GradientDrawable();
            datePillGd.setCornerRadius(200f);
            datePillGd.setColor(innerBg.getDefaultColor());
            datePill.setBackground(datePillGd);
            datePill.setElevation(0f);
            datePill.setClipToOutline(true);

            tvTime.setTextColor(themeText);
            tvDate.setTextColor(secondaryText);

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

            if (ramCardBg != null) ramCardBg.setBackgroundTintList(dashCardBg);
            if (storageCardBg != null) storageCardBg.setBackgroundTintList(dashCardBg);
            if (batteryCardBg != null) batteryCardBg.setBackgroundTintList(dashCardBg);

            if (tvRamTitle != null) tvRamTitle.setTextColor(themeText);
            if (tvRamUsed != null) tvRamUsed.setTextColor(themeText);
            if (tvStorageTitle != null) tvStorageTitle.setTextColor(themeText);
            if (tvBatteryTitle != null) tvBatteryTitle.setTextColor(themeText);

            if (tvRamTotal != null) tvRamTotal.setTextColor(secondaryText);
            if (tvRamFree != null) tvRamFree.setTextColor(secondaryText);

            if (ramGraphView != null) ramGraphView.setThemeState(themeState);

            // ==============================================================================
            // ============ THE MAGIC: EXECUTING THE REVEAL & MORPH ANIMATION ===============
            // ==============================================================================
            if (sThemeSnapshot != null) {
                ViewGroup content = findViewById(android.R.id.content);
                View newLayout = findViewById(R.id.main_root);

                ImageView oldUiImage = new ImageView(this);
                oldUiImage.setImageBitmap(sThemeSnapshot);
                oldUiImage.setScaleType(ImageView.ScaleType.FIT_XY);
                content.addView(oldUiImage, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                final int prevState = (themeState + 2) % 3;
                FrameLayout clonePill = new FrameLayout(this);
                GradientDrawable cloneGd = new GradientDrawable();
                cloneGd.setShape(GradientDrawable.OVAL);

                if (prevState == 0) cloneGd.setColor(Color.parseColor("#FFFFFF"));
                else if (prevState == 1) cloneGd.setColor(Color.parseColor("#1C1C1E"));
                else cloneGd.setColor(Color.parseColor("#000000"));
                clonePill.setBackground(cloneGd);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    clonePill.setElevation(10000f);
                    newLayout.setElevation(10f);
                }

                ImageButton cloneIcon = new ImageButton(this);
                cloneIcon.setBackgroundResource(0);
                cloneIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cloneIcon.setPadding(themeToggleBtn.getPaddingLeft(), themeToggleBtn.getPaddingTop(), themeToggleBtn.getPaddingRight(), themeToggleBtn.getPaddingBottom());

                if (prevState == 0) {
                    cloneIcon.setImageResource(R.drawable.ic_sun);
                    cloneIcon.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                } else if (prevState == 1) {
                    cloneIcon.setImageResource(R.drawable.ic_moon);
                    cloneIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                } else {
                    cloneIcon.setImageResource(android.R.drawable.star_on);
                    cloneIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                }

                clonePill.addView(cloneIcon, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                FrameLayout.LayoutParams cloneParams = new FrameLayout.LayoutParams(sPillWidth, sPillHeight);
                cloneParams.leftMargin = sPillX;
                cloneParams.topMargin = sPillY;
                content.addView(clonePill, cloneParams);

                newLayout.setVisibility(View.INVISIBLE);
                themeTogglePill.setVisibility(View.INVISIBLE);

                newLayout.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        newLayout.getViewTreeObserver().removeOnPreDrawListener(this);
                        newLayout.setVisibility(View.VISIBLE);

                        float finalRadius = (float) Math.hypot(Math.max(sRevealX, newLayout.getWidth() - sRevealX), Math.max(sRevealY, newLayout.getHeight() - sRevealY));

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                            Animator anim = ViewAnimationUtils.createCircularReveal(newLayout, sRevealX, sRevealY, 0f, finalRadius);
                            anim.setDuration(600);
                            anim.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    content.removeView(oldUiImage);
                                    content.removeView(clonePill);
                                    sThemeSnapshot = null;
                                    themeTogglePill.setVisibility(View.VISIBLE);
                                }
                            });
                            anim.start();

                            // --- PILL SHRINK/SPIN ANIMATION FIX ---
                            // We now animate clonePill (the outer circle background) instead of cloneIcon
                            clonePill.animate()
                                    .rotation(180f)
                                    .scaleX(0f)
                                    .scaleY(0f)
                                    .setDuration(300)
                                    .withEndAction(() -> {
                                        // Swap colors and icons while the pill is invisible
                                        if (themeState == 0) {
                                            cloneIcon.setImageResource(R.drawable.ic_sun);
                                            cloneIcon.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                                            cloneGd.setColor(Color.parseColor("#FFFFFF"));
                                        } else if (themeState == 1) {
                                            cloneIcon.setImageResource(R.drawable.ic_moon);
                                            cloneIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                                            cloneGd.setColor(Color.parseColor("#1C1C1E"));
                                        } else {
                                            cloneIcon.setImageResource(android.R.drawable.star_on);
                                            cloneIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                                            cloneGd.setColor(Color.parseColor("#000000"));
                                        }
                                        clonePill.setBackground(cloneGd);

                                        clonePill.setRotation(-180f);
                                        clonePill.animate()
                                                .rotation(0f)
                                                .scaleX(1f)
                                                .scaleY(1f)
                                                .setDuration(300)
                                                .start();
                                    })
                                    .start();
                        } else {
                            content.removeView(oldUiImage);
                            content.removeView(clonePill);
                            sThemeSnapshot = null;
                            themeTogglePill.setVisibility(View.VISIBLE);
                        }
                        return true;
                    }
                });
            }
            // ==============================================================================

            datePill.setOnClickListener(v -> {
                Dialog dialog = new Dialog(MainActivity.this);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

                LinearLayout layout = new LinearLayout(MainActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (16 * getResources().getDisplayMetrics().density);
                layout.setPadding(pad, pad, pad, pad);

                GradientDrawable gd = new GradientDrawable();
                gd.setCornerRadius(60f);

                int popupBg;
                if (themeState == 0) {
                    popupBg = Color.parseColor("#E6FFFFFF");
                } else if (themeState == 1) {
                    popupBg = Color.parseColor("#E62C2C2E");
                } else {
                    popupBg = Color.parseColor("#E61C1C1E");
                }
                gd.setColor(popupBg);
                layout.setBackground(gd);

                TextView titleView = new TextView(MainActivity.this);
                titleView.setText("Calendar");
                titleView.setTextSize(18f);
                titleView.setTypeface(null, android.graphics.Typeface.BOLD);
                titleView.setGravity(android.view.Gravity.CENTER);
                titleView.setTextColor(themeText);
                titleView.setPadding(0, 0, 0, pad);
                layout.addView(titleView);

                CalendarView calendarView = new CalendarView(MainActivity.this);
                layout.addView(calendarView);

                dialog.setContentView(layout);
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    dialog.getWindow().setLayout(
                            (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                }
                dialog.show();
            });

            btnPlaceWidget.setOnClickListener(v -> {
                int[] loc = new int[2];
                btnPlaceWidget.getLocationOnScreen(loc);
                int cx = loc[0] + (btnPlaceWidget.getWidth() / 2);
                int cy = loc[1] + (btnPlaceWidget.getHeight() / 2);

                Intent intent = new Intent(MainActivity.this, WidgetGalleryActivity.class);
                intent.putExtra("REVEAL_X", cx);
                intent.putExtra("REVEAL_Y", cy);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });

            btnGames.setOnClickListener(v -> {
                int[] loc = new int[2];
                btnGames.getLocationOnScreen(loc);
                int cx = loc[0] + (btnGames.getWidth() / 2);
                int cy = loc[1] + (btnGames.getHeight() / 2);

                Intent intent = new Intent(MainActivity.this, GamesGalleryActivity.class);
                intent.putExtra("REVEAL_X", cx);
                intent.putExtra("REVEAL_Y", cy);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });

            btnTools.setOnClickListener(v -> {
                int[] loc = new int[2];
                btnTools.getLocationOnScreen(loc);
                int cx = loc[0] + (btnTools.getWidth() / 2);
                int cy = loc[1] + (btnTools.getHeight() / 2);

                Intent intent = new Intent(MainActivity.this, ToolsGalleryActivity.class);
                intent.putExtra("REVEAL_X", cx);
                intent.putExtra("REVEAL_Y", cy);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });

            btnUtilities.setOnClickListener(v -> {
                int[] loc = new int[2];
                btnUtilities.getLocationOnScreen(loc);
                int cx = loc[0] + (btnUtilities.getWidth() / 2);
                int cy = loc[1] + (btnUtilities.getHeight() / 2);

                Intent intent = new Intent(MainActivity.this, UtilitiesGalleryActivity.class);
                intent.putExtra("REVEAL_X", cx);
                intent.putExtra("REVEAL_Y", cy);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
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