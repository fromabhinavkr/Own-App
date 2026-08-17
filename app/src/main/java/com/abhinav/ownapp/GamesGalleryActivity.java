package com.abhinav.ownapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;

public class GamesGalleryActivity extends AppCompatActivity {

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

        setContentView(R.layout.activity_games_gallery);

        // --- 3-STATE THEME LOGIC ---
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = oldDark ? 1 : 0;
        }

        // Apply Theme Colors dynamically based on the 3 states
        final int cardColor;
        final int textColor;
        final int subTextColor;

        if (themeState == 0) { // Light Mode (Pure White BG, Light Grey Cards)
            bgColor = Color.WHITE;
            cardColor = Color.parseColor("#F2F2F7");
            textColor = Color.parseColor("#333333");
            subTextColor = Color.parseColor("#555555");
        } else if (themeState == 1) { // Standard Dark Mode
            bgColor = Color.parseColor("#1C1C1E");
            cardColor = Color.parseColor("#2C2C2E");
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#8E8E93");
        } else { // Star Mode (AMOLED Pure Black)
            bgColor = Color.parseColor("#000000");
            cardColor = Color.parseColor("#1C1C1E");
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#8E8E93");
        }

        // Link Views
        root = findViewById(R.id.gamesGalleryRoot);
        TextView title = findViewById(R.id.tvGamesTitle);
        TextView subtitle = findViewById(R.id.tvGamesSubtitle);

        View cardSnakeGame = findViewById(R.id.cardSnakeGame);
        TextView textSnake = findViewById(R.id.textSnakeGame);

        View cardFlappyBird = findViewById(R.id.cardFlappyBird);
        TextView textFlappy = findViewById(R.id.textFlappyBird);

        View cardTetris = findViewById(R.id.cardTetris);
        TextView textTetris = findViewById(R.id.textTetris);

        View cardBreakout = findViewById(R.id.cardBreakout);
        TextView textBreakout = findViewById(R.id.textBreakout);

        View cardMazeBall = findViewById(R.id.cardMazeBall);
        TextView textMazeBall = findViewById(R.id.textMazeBall);

        View cardMyPlanet = findViewById(R.id.cardMyPlanet);
        TextView textMyPlanet = findViewById(R.id.textMyPlanet);
        TextView btnHelpMyPlanet = findViewById(R.id.btnHelpMyPlanet);

        if (root != null) root.setBackgroundColor(bgColor);
        if (title != null) title.setTextColor(textColor);
        if (subtitle != null) subtitle.setTextColor(subTextColor);

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

        // Apply dynamic background to the cards to match theme
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(cardColor);
        cardBg.setCornerRadius(32f);

        if (cardSnakeGame != null) {
            cardSnakeGame.setBackground(cardBg);
            textSnake.setTextColor(textColor);
            cardSnakeGame.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, SnakeGameActivity.class)));
        }

        if (cardFlappyBird != null) {
            cardFlappyBird.setBackground(cardBg);
            textFlappy.setTextColor(textColor);
            cardFlappyBird.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, FlappyBirdActivity.class)));
        }

        if (cardTetris != null) {
            cardTetris.setBackground(cardBg);
            textTetris.setTextColor(textColor);
            cardTetris.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, TetrisActivity.class)));
        }

        if (cardBreakout != null) {
            cardBreakout.setBackground(cardBg);
            textBreakout.setTextColor(textColor);
            cardBreakout.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, BreakoutActivity.class)));
        }

        if (cardMazeBall != null) {
            cardMazeBall.setBackground(cardBg);
            textMazeBall.setTextColor(textColor);
            cardMazeBall.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, MazeBallActivity.class)));
        }

        // Play My Planet Logic & Theme
        if (cardMyPlanet != null) {
            cardMyPlanet.setBackground(cardBg);
            textMyPlanet.setTextColor(textColor);

            GradientDrawable helpBg = new GradientDrawable();
            helpBg.setShape(GradientDrawable.OVAL);
            helpBg.setStroke(3, subTextColor);
            btnHelpMyPlanet.setTextColor(subTextColor);
            btnHelpMyPlanet.setBackground(helpBg);

            // Pass the current state to the dialog so it matches
            final int finalThemeState = themeState;
            btnHelpMyPlanet.setOnClickListener(v -> showInstructionsDialog(finalThemeState));

            cardMyPlanet.setOnClickListener(v -> {
                startActivity(new Intent(GamesGalleryActivity.this, GlobeGameActivity.class));
            });
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
        // Only target the status bar color, leaving the window background transparent
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

    private void showInstructionsDialog(int themeState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        int dialogBgColor, dialogTextColor, dialogSubTextColor;

        if (themeState == 0) { // Light Mode
            dialogBgColor = Color.WHITE;
            dialogTextColor = Color.parseColor("#1C1C1E");
            dialogSubTextColor = Color.parseColor("#666666");
        } else if (themeState == 1) { // Standard Dark Mode
            dialogBgColor = Color.parseColor("#2C2C2E");
            dialogTextColor = Color.WHITE;
            dialogSubTextColor = Color.parseColor("#B0B0B8");
        } else { // Star Mode (AMOLED Black)
            dialogBgColor = Color.parseColor("#1C1C1E"); // Keep dialog card visible against pure black
            dialogTextColor = Color.WHITE;
            dialogSubTextColor = Color.parseColor("#B0B0B8");
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);

        GradientDrawable bgGd = new GradientDrawable();
        bgGd.setColor(dialogBgColor);
        bgGd.setCornerRadius(50f);
        layout.setBackground(bgGd);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("How to Play");
        tvTitle.setTextSize(24f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(dialogTextColor);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, 40);

        TextView tvMessage = new TextView(this);
        tvMessage.setText(
                "🌍 Explore Phase:\n" +
                        "Swipe to rotate the globe and double-tap to cycle through different planets. Enjoy the relaxing view of space!\n\n" +
                        "🛸 Alien Invasion:\n" +
                        "Ready for a challenge? Long-press the planet to trigger an invasion! Tap incoming UFOs to destroy them before they crash into the surface and take your lives.\n\n" +
                        "🛡️ Divine Shield:\n" +
                        "For every 25 points you score, you will unleash a majestic celestial shockwave that instantly destroys all nearby enemies."
        );
        tvMessage.setTextSize(15f);
        tvMessage.setTextColor(dialogSubTextColor);
        tvMessage.setLineSpacing(0, 1.3f);
        tvMessage.setPadding(0, 0, 0, 60);

        Button btnGotIt = new Button(this);
        btnGotIt.setText("Got it!");
        btnGotIt.setTextColor(Color.WHITE);
        btnGotIt.setAllCaps(false);
        btnGotIt.setTextSize(16f);
        btnGotIt.setTypeface(null, Typeface.BOLD);

        GradientDrawable btnGd = new GradientDrawable();
        btnGd.setColor(Color.parseColor("#4A90E2"));
        btnGd.setCornerRadius(100f);
        btnGotIt.setBackground(btnGd);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 130);
        btnGotIt.setLayoutParams(btnLp);

        layout.addView(tvTitle);
        layout.addView(tvMessage);
        layout.addView(btnGotIt);

        builder.setView(layout);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnGotIt.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}