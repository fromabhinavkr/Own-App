package com.abhinav.ownapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class ToolsGalleryActivity extends AppCompatActivity {

    private LinearLayout root;
    private int revealX;
    private int revealY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Make the window transparent so MainActivity renders underneath during reveal animations
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tools_gallery);

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        int rootBgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
        int cardBgColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        int titleColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");
        int subtitleColor = isDarkTheme ? Color.parseColor("#8E8E93") : Color.parseColor("#888888");

        // The color for your new separator lines
        int dividerColor = isDarkTheme ? Color.parseColor("#33FFFFFF") : Color.parseColor("#1A000000");

        // 1. Root and Headers
        root = findViewById(R.id.toolsGalleryRoot);
        TextView tvTitle = findViewById(R.id.tvToolsTitle);
        TextView tvSubtitle = findViewById(R.id.tvToolsSubtitle);

        if (root != null) root.setBackgroundColor(rootBgColor);
        if (tvTitle != null) tvTitle.setTextColor(titleColor);
        if (tvSubtitle != null) tvSubtitle.setTextColor(subtitleColor);

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

                        float finalRadius = (float) (Math.max(root.getWidth(), root.getHeight()) * 1.1);

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
                    float startRadius = (float) (Math.max(root.getWidth(), root.getHeight()) * 1.1);
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

        // 6. Setup Audio Studio Card (NEW)
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
}