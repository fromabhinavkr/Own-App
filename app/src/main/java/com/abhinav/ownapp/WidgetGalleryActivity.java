package com.abhinav.ownapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;

@SuppressWarnings("all")
public class WidgetGalleryActivity extends AppCompatActivity {

    private LinearLayout root;
    private int revealX;
    private int revealY;

    // --- State variables ---
    private int themeState;
    private int bgColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // FIX: Keep the underlying window PERMANENTLY transparent to avoid animation glitching
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }

        setContentView(R.layout.activity_widget_gallery);

        // --- 3-STATE THEME SYNC LOGIC ---
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = oldDark ? 1 : 0;
        }

        // Define colors based on the 3-state theme
        final int cardBgColor;
        final int titleColor;
        final int subtitleColor;
        final int dividerColor;

        if (themeState == 0) { // Light Mode (Pure White BG, Light Grey Cards)
            bgColor = Color.WHITE;
            cardBgColor = Color.parseColor("#F2F2F7");
            titleColor = Color.parseColor("#333333");
            subtitleColor = Color.parseColor("#888888");
            dividerColor = Color.parseColor("#1A000000");
        } else if (themeState == 1) { // Standard Dark Mode
            bgColor = Color.parseColor("#1C1C1E");
            cardBgColor = Color.parseColor("#2C2C2E");
            titleColor = Color.WHITE;
            subtitleColor = Color.parseColor("#8E8E93");
            dividerColor = Color.parseColor("#33FFFFFF");
        } else { // Star Mode (AMOLED Pure Black)
            bgColor = Color.parseColor("#000000");
            cardBgColor = Color.parseColor("#1C1C1E");
            titleColor = Color.WHITE;
            subtitleColor = Color.parseColor("#8E8E93");
            dividerColor = Color.parseColor("#33FFFFFF");
        }

        // Apply colors to root and headers
        root = findViewById(R.id.galleryRoot);
        if (root != null) root.setBackgroundColor(bgColor);

        TextView tvTitle = findViewById(R.id.tvGalleryTitle);
        if (tvTitle != null) tvTitle.setTextColor(titleColor);

        TextView tvSubtitle = findViewById(R.id.tvGallerySubtitle);
        if (tvSubtitle != null) tvSubtitle.setTextColor(subtitleColor);

        // Apply Status Bar Icons immediately
        enforceStatusBarIcons();

        // --- Handle Circular Reveal Entry Animation ---
        Intent intent = getIntent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setVisibility(View.INVISIBLE);

            ViewTreeObserver viewTreeObserver = root.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        root.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                        revealX = intent.getIntExtra("REVEAL_X", root.getWidth() / 2);
                        revealY = intent.getIntExtra("REVEAL_Y", root.getHeight() / 2);

                        // Use hypotenuse to ensure the circle covers the entire screen perfectly
                        float finalRadius = (float) Math.hypot(root.getWidth(), root.getHeight());

                        Animator circularReveal = ViewAnimationUtils.createCircularReveal(root, revealX, revealY, 0, finalRadius);
                        circularReveal.setDuration(350);
                        circularReveal.setInterpolator(new DecelerateInterpolator());

                        root.setVisibility(View.VISIBLE);
                        circularReveal.start();
                    }
                });
            }
        }

        // --- Handle Circular Reveal Exit Animation ---
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && root.isAttachedToWindow()) {

                    float startRadius = (float) Math.hypot(root.getWidth(), root.getHeight());
                    Animator circularReveal = ViewAnimationUtils.createCircularReveal(root, revealX, revealY, startRadius, 0);
                    circularReveal.setDuration(350);
                    circularReveal.setInterpolator(new DecelerateInterpolator());

                    circularReveal.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            root.setVisibility(View.INVISIBLE);
                            finish();
                            overridePendingTransition(0, 0);
                        }
                    });
                    circularReveal.start();
                } else {
                    finish();
                    overridePendingTransition(0, 0);
                }
            }
        });

        // Link the layout cards
        LinearLayout cardDrawing = findViewById(R.id.cardDrawing);
        LinearLayout cardPhoto = findViewById(R.id.cardPhoto);
        LinearLayout cardMaze = findViewById(R.id.cardMaze);
        LinearLayout cardSnake = findViewById(R.id.cardSnake);
        LinearLayout cardWater = findViewById(R.id.cardWater);
        LinearLayout cardGear = findViewById(R.id.cardGear);
        LinearLayout cardCoin = findViewById(R.id.cardCoin);
        LinearLayout cardHourglass = findViewById(R.id.cardHourglass);

        // Link the text inside the cards
        TextView textDrawing = findViewById(R.id.textDrawing);
        TextView textPhoto = findViewById(R.id.textPhoto);
        TextView textMaze = findViewById(R.id.textMaze);
        TextView textSnake = findViewById(R.id.textSnake);
        TextView textWater = findViewById(R.id.textWater);
        TextView textGear = findViewById(R.id.textGear);
        TextView textCoin = findViewById(R.id.textCoin);
        TextView textHourglass = findViewById(R.id.textHourglass);

        // Link the horizontal dividers inside the cards
        View divDrawing = findViewById(R.id.divDrawing);
        View divPhoto = findViewById(R.id.divPhoto);
        View divMaze = findViewById(R.id.divMaze);
        View divSnake = findViewById(R.id.divSnake);
        View divWater = findViewById(R.id.divWater);
        View divGear = findViewById(R.id.divGear);
        View divCoin = findViewById(R.id.divCoin);
        View divHourglass = findViewById(R.id.divHourglass);

        // Arrays to loop through and apply the modern theme colors
        LinearLayout[] cards = {cardDrawing, cardPhoto, cardMaze, cardSnake, cardWater, cardGear, cardCoin, cardHourglass};
        TextView[] textViews = {textDrawing, textPhoto, textMaze, textSnake, textWater, textGear, textCoin, textHourglass};
        View[] dividers = {divDrawing, divPhoto, divMaze, divSnake, divWater, divGear, divCoin, divHourglass};

        // Apply theme to every card, text, and divider
        for (int i = 0; i < cards.length; i++) {
            if (cards[i] != null) cards[i].setBackgroundTintList(ColorStateList.valueOf(cardBgColor));
            if (textViews[i] != null) textViews[i].setTextColor(titleColor);
            if (dividers[i] != null) dividers[i].setBackgroundColor(dividerColor);
        }

        // --- WIDGET PINNING LOGIC ---
        if (cardDrawing != null) cardDrawing.setOnClickListener(v -> requestToPinWidget(DrawingWidgetProvider.class));
        if (cardPhoto != null) cardPhoto.setOnClickListener(v -> requestToPinWidget(Photowidget.class));
        if (cardMaze != null) cardMaze.setOnClickListener(v -> requestToPinWidget(MazeWidgetProvider.class));
        if (cardSnake != null) cardSnake.setOnClickListener(v -> requestToPinWidget(SnakeWidget.class));
        if (cardWater != null) cardWater.setOnClickListener(v -> requestToPinWidget(WaterWidgetProvider.class));
        if (cardGear != null) cardGear.setOnClickListener(v -> requestToPinWidget(GearWidgetProvider.class));
        if (cardCoin != null) cardCoin.setOnClickListener(v -> requestToPinWidget(CoinWidget.class));
        if (cardHourglass != null) cardHourglass.setOnClickListener(v -> requestToPinWidget(HourglassWidget.class));
    }

    private void requestToPinWidget(Class<?> widgetProviderClass) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                ComponentName myProvider = new ComponentName(this, widgetProviderClass);
                appWidgetManager.requestPinAppWidget(myProvider, null, null);
            } else {
                Toast.makeText(this, "Your phone's launcher doesn't support automatic widget pinning. Please add it manually.", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "This feature requires Android 8.0 or higher. Please add manually.", Toast.LENGTH_SHORT).show();
        }
    }

    // --- Smart Lifecycle Interceptors ---
    @Override
    protected void onResume() {
        super.onResume();
        // Force the window to remain transparent to override any MainApplication callbacks seamlessly
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        enforceStatusBarIcons();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enforceStatusBarIcons();
        }
    }

    // Method to forcibly correct the icons for Light/Dark/Star mode safely
    private void enforceStatusBarIcons() {
        // Keep the main window background untouched (transparent), ONLY tint the status bar area
        getWindow().setStatusBarColor(bgColor);

        View decor = getWindow().getDecorView();
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), decor);

        if (themeState == 0) { // Light Mode -> Icons MUST be Dark
            controller.setAppearanceLightStatusBars(true);
            controller.setAppearanceLightNavigationBars(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getWindow().setNavigationBarColor(bgColor);
            }
        } else { // Dark or Star Mode -> Icons MUST be White
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getWindow().setNavigationBarColor(bgColor);
            }
        }
    }
}