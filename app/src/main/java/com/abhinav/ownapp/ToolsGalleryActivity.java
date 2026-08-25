package com.abhinav.ownapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;

@SuppressWarnings("all")
public class ToolsGalleryActivity extends AppCompatActivity {

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

        setContentView(R.layout.activity_tools_gallery);

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
            subtitleColor = Color.parseColor("#555555");
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

        // 1. Root and Headers
        root = findViewById(R.id.toolsGalleryRoot);
        TextView tvTitle = findViewById(R.id.tvToolsTitle);
        TextView tvSubtitle = findViewById(R.id.tvToolsSubtitle);

        if (root != null) root.setBackgroundColor(bgColor);
        if (tvTitle != null) tvTitle.setTextColor(titleColor);
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

        // 2. Setup Sticker Maker Card
        LinearLayout cardSticker = findViewById(R.id.cardStickerMaker);
        TextView textSticker = findViewById(R.id.textStickerMaker);
        View divSticker = findViewById(R.id.divStickerMaker);

        if (cardSticker != null) {
            applyModernCardStyle(cardSticker, cardBgColor);
            if (textSticker != null) textSticker.setTextColor(titleColor);
            if (divSticker != null) divSticker.setBackgroundColor(dividerColor);

            cardSticker.setOnClickListener(v -> startActivity(new Intent(this, StickerMakerActivity.class)));
        }

        // 3. Setup Image Editor Card
        LinearLayout cardEditor = findViewById(R.id.cardImageEditor);
        TextView textEditor = findViewById(R.id.textImageEditor);
        View divEditor = findViewById(R.id.divImageEditor);

        if (cardEditor != null) {
            applyModernCardStyle(cardEditor, cardBgColor);
            if (textEditor != null) textEditor.setTextColor(titleColor);
            if (divEditor != null) divEditor.setBackgroundColor(dividerColor);

            cardEditor.setOnClickListener(v -> startActivity(new Intent(this, ImageEditorActivity.class)));
        }

        // 4. Setup PDF Studio Card
        LinearLayout cardPdf = findViewById(R.id.cardPdfStudio);
        TextView textPdf = findViewById(R.id.textPdfStudio);
        View divPdf = findViewById(R.id.divPdfStudio);

        if (cardPdf != null) {
            applyModernCardStyle(cardPdf, cardBgColor);
            if (textPdf != null) textPdf.setTextColor(titleColor);
            if (divPdf != null) divPdf.setBackgroundColor(dividerColor);

            cardPdf.setOnClickListener(v -> startActivity(new Intent(this, PdfStudioActivity.class)));
        }

        // 5. Setup Collage Studio Card
        LinearLayout cardCollage = findViewById(R.id.cardCollageStudio);
        TextView textCollage = findViewById(R.id.textCollageStudio);
        View divCollage = findViewById(R.id.divCollageStudio);

        if (cardCollage != null) {
            applyModernCardStyle(cardCollage, cardBgColor);
            if (textCollage != null) textCollage.setTextColor(titleColor);
            if (divCollage != null) divCollage.setBackgroundColor(dividerColor);

            cardCollage.setOnClickListener(v -> startActivity(new Intent(this, CollageStudioActivity.class)));
        }

        // 6. Setup Audio Studio Card
        LinearLayout cardAudio = findViewById(R.id.cardAudioStudio);
        TextView textAudio = findViewById(R.id.textAudioStudio);
        View divAudio = findViewById(R.id.divAudioStudio);

        if (cardAudio != null) {
            applyModernCardStyle(cardAudio, cardBgColor);
            if (textAudio != null) textAudio.setTextColor(titleColor);
            if (divAudio != null) divAudio.setBackgroundColor(dividerColor);

            cardAudio.setOnClickListener(v -> startActivity(new Intent(this, AudioEditorActivity.class)));
        }
    }

    private void applyModernCardStyle(View card, int bgColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(50f); // Keeps the smooth rounded corners
        card.setBackground(gd);
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
}