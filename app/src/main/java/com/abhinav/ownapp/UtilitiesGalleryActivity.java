package com.abhinav.ownapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
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

@SuppressWarnings("all")
public class UtilitiesGalleryActivity extends AppCompatActivity {

    private LinearLayout root;
    private int revealX;
    private int revealY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Make the window transparent so MainActivity renders underneath during reveal animations
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_utilities_gallery);

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
        int cardColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        int textColor = isDarkTheme ? Color.WHITE : Color.BLACK;
        int subtitleColor = isDarkTheme ? Color.parseColor("#AAAAAA") : Color.parseColor("#555555");
        int divColor = isDarkTheme ? Color.parseColor("#33FFFFFF") : Color.parseColor("#1A000000");

        root = findViewById(R.id.utilitiesGalleryRoot);
        TextView title = findViewById(R.id.tvUtilitiesTitle);
        TextView subtitle = findViewById(R.id.tvUtilitiesSubtitle);

        // Tool 1: Browser
        LinearLayout cardBrowser = findViewById(R.id.cardPrivateBrowser);
        TextView textBrowser = findViewById(R.id.textPrivateBrowser);
        View divBrowser = findViewById(R.id.divPrivateBrowser);

        // Tool 2: Doc Reader
        LinearLayout cardDocReader = findViewById(R.id.cardDocReader);
        TextView textDocReader = findViewById(R.id.textDocReader);
        View divDocReader = findViewById(R.id.divDocReader);

        // Tool 3: Slate
        LinearLayout cardSlate = findViewById(R.id.cardSlate);
        TextView textSlate = findViewById(R.id.textSlate);
        View divSlate = findViewById(R.id.divSlate);

        // Apply Global Themes
        if (root != null) root.setBackgroundColor(bgColor);
        if (title != null) title.setTextColor(textColor);
        if (subtitle != null) subtitle.setTextColor(subtitleColor);

        // Apply Themes to Individual Cards
        if (cardBrowser != null) {
            cardBrowser.setBackgroundTintList(ColorStateList.valueOf(cardColor));
            textBrowser.setTextColor(textColor);
            divBrowser.setBackgroundColor(divColor);
        }

        if (cardDocReader != null) {
            cardDocReader.setBackgroundTintList(ColorStateList.valueOf(cardColor));
            textDocReader.setTextColor(textColor);
            divDocReader.setBackgroundColor(divColor);
        }

        if (cardSlate != null) {
            cardSlate.setBackgroundTintList(ColorStateList.valueOf(cardColor));
            textSlate.setTextColor(textColor);
            divSlate.setBackgroundColor(divColor);
        }

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

        // Help Button Logic for Browser
        TextView btnBrowserHelp = findViewById(R.id.btnBrowserHelp);
        if (btnBrowserHelp != null) {
            GradientDrawable helpGd = new GradientDrawable();
            helpGd.setShape(GradientDrawable.OVAL);
            helpGd.setColor(isDarkTheme ? Color.parseColor("#4A90E2") : Color.parseColor("#007AFF"));

            btnBrowserHelp.setBackground(helpGd);
            btnBrowserHelp.setTextColor(Color.WHITE);

            btnBrowserHelp.setOnClickListener(v -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(UtilitiesGalleryActivity.this, R.style.ModernDialogStyle);
                builder.setTitle("Privacy & Network Notice");

                LinearLayout dialogLayout = new LinearLayout(UtilitiesGalleryActivity.this);
                dialogLayout.setOrientation(LinearLayout.VERTICAL);
                dialogLayout.setPadding(60, 40, 60, 40);

                TextView message = new TextView(UtilitiesGalleryActivity.this);
                message.setText("This tool requires an active Internet connection to function. In accordance with strict privacy standards, Own does not collect, track, or store any of your browsing history, personal data, or usage metrics.");
                message.setTextColor(textColor);
                message.setTextSize(16f);
                message.setLineSpacing(0, 1.2f);
                dialogLayout.addView(message);

                builder.setView(dialogLayout);
                builder.setPositiveButton("Understood", null);

                AlertDialog dialog = builder.create();
                dialog.setOnShowListener(d -> {
                    if (dialog.getWindow() != null) {
                        GradientDrawable gd = new GradientDrawable();
                        gd.setColor(cardColor);
                        gd.setCornerRadius(60f);
                        dialog.getWindow().getDecorView().setBackground(gd);

                        int titleId = UtilitiesGalleryActivity.this.getResources().getIdentifier("alertTitle", "id", "android");
                        TextView titleView = dialog.findViewById(titleId);
                        if (titleView != null) titleView.setTextColor(textColor);

                        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#4A90E2"));
                        }
                    }
                });
                dialog.show();
            });
        }

        // --- Click Listeners to Launch Activities ---
        if (cardBrowser != null) {
            cardBrowser.setOnClickListener(v -> startActivity(new Intent(UtilitiesGalleryActivity.this, PrivateBrowserActivity.class)));
        }
        if (cardDocReader != null) {
            cardDocReader.setOnClickListener(v -> startActivity(new Intent(UtilitiesGalleryActivity.this, DocReaderActivity.class)));
        }
        if (cardSlate != null) {
            cardSlate.setOnClickListener(v -> startActivity(new Intent(UtilitiesGalleryActivity.this, SlateActivity.class)));
        }
    }
}