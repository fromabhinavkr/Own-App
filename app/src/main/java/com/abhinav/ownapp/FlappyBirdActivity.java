package com.abhinav.ownapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("all") @SuppressLint("SetTextI18n")
public class FlappyBirdActivity extends AppCompatActivity {

    private int themeState, highScore = 0;
    private int classicPlayed, classicBest, gooPlayed, gooBest;
    private boolean isVibrationEnabled = true;
    private SharedPreferences prefs;

    // --- FIX: Store state completely separate from the UI texts ---
    private int currentBestScore = 0;
    private ValueAnimator hudWidthAnimator;
    private ValueAnimator hudTextAnimator;

    private TextView tvCurrentScore, tvHighScore, tvFinalScore, tvTapToStart, tvGameOverTitle, tvNewHighScoreBanner, tvClassicStats, tvGooStats;
    private LinearLayout scoreContainer;
    private RelativeLayout pauseOverlay, gameOverOverlay, characterSelectionOverlay;
    private FlappyGameEngine gameEngine;
    private LinearLayout btnPause;
    private FrameLayout pauseIconContainer, snapshotContainer;
    private GameIconView pauseIconView;
    private ImageView ivDeathSnapshot;
    private int iconColor;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_flappy_bird);

        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            View contentView = findViewById(android.R.id.content);
            if (contentView != null) contentView.setPadding(0, 0, 0, 0);
            View root = findViewById(R.id.flappyRoot);
            if (root != null) root.setPadding(insets.left, 0, insets.right, insets.bottom);
            float density = getResources().getDisplayMetrics().density;
            View topHud = findViewById(R.id.topHud);
            if (topHud != null) topHud.setPadding((int)(24*density), insets.top + (int)(16*density), (int)(16*density), (int)(12*density));
            TextView[] headers = { findViewById(R.id.tvSelectTitle), findViewById(R.id.tvGameOverTitle), findViewById(R.id.tvPauseTitle) };
            for(TextView tv : headers) if (tv != null) tv.setPadding(0, insets.top + (int)(32 * density), 0, (int)(24 * density));
            return WindowInsetsCompat.CONSUMED;
        });

        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) themeState = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true) ? 1 : 0;

        highScore = prefs.getInt("flappy_high_score", 0);
        classicPlayed = prefs.getInt("classic_played", 0);
        classicBest = prefs.getInt("classic_best", 0);
        gooPlayed = prefs.getInt("goo_played", 0);
        gooBest = prefs.getInt("goo_best", 0);
        isVibrationEnabled = prefs.getBoolean("flappy_vibration_enabled", true);
        currentBestScore = classicBest; // Default init

        FrameLayout gameContainer = findViewById(R.id.gameContainer);
        btnPause = findViewById(R.id.btnPause);
        pauseIconContainer = findViewById(R.id.pauseIconContainer);
        tvCurrentScore = findViewById(R.id.tvCurrentScore);
        tvHighScore = findViewById(R.id.tvHighScore);
        tvFinalScore = findViewById(R.id.tvFinalScore);
        tvTapToStart = findViewById(R.id.tvTapToStart);
        scoreContainer = findViewById(R.id.scoreContainer);
        tvGameOverTitle = findViewById(R.id.tvGameOverTitle);
        tvNewHighScoreBanner = findViewById(R.id.tvNewHighScoreBanner);
        pauseOverlay = findViewById(R.id.pauseOverlay);
        gameOverOverlay = findViewById(R.id.gameOverOverlay);
        characterSelectionOverlay = findViewById(R.id.characterSelectionOverlay);

        if (characterSelectionOverlay != null) { characterSelectionOverlay.setClickable(true); characterSelectionOverlay.setFocusable(true); }

        LinearLayout pauseCard = findViewById(R.id.pauseCard), gameOverCard = findViewById(R.id.gameOverCard), characterSelectionCard = findViewById(R.id.characterSelectionCard);
        Button btnResume = findViewById(R.id.btnResume), btnRestart = findViewById(R.id.btnRestart), btnQuit = findViewById(R.id.btnQuit), btnQuitFromPause = findViewById(R.id.btnQuitFromPause), btnToggleVibration = findViewById(R.id.btnToggleVibration);
        LinearLayout btnSelectClassic = findViewById(R.id.btnSelectClassic), btnSelectGoo = findViewById(R.id.btnSelectGoo);
        TextView tvSelectTitle = findViewById(R.id.tvSelectTitle), tvClassicTitle = findViewById(R.id.tvClassicTitle), tvGooTitle = findViewById(R.id.tvGooTitle);
        tvClassicStats = findViewById(R.id.tvClassicStats); tvGooStats = findViewById(R.id.tvGooStats);

        if (tvGooTitle != null) tvGooTitle.setText("Clov Bird");

        ImageView ivClassicPreview = findViewById(R.id.ivClassicPreview);
        ImageView ivGooPreview = findViewById(R.id.ivGooPreview);
        snapshotContainer = findViewById(R.id.snapshotContainer);
        ivDeathSnapshot = findViewById(R.id.ivDeathSnapshot);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (btnResume != null) { btnResume.setStateListAnimator(null); btnResume.setElevation(0); }
            if (btnRestart != null) { btnRestart.setStateListAnimator(null); btnRestart.setElevation(0); }
            if (btnQuit != null) { btnQuit.setStateListAnimator(null); btnQuit.setElevation(0); }
            if (btnQuitFromPause != null) { btnQuitFromPause.setStateListAnimator(null); btnQuitFromPause.setElevation(0); }
            if (btnToggleVibration != null) { btnToggleVibration.setStateListAnimator(null); btnToggleVibration.setElevation(0); }
        }

        int bgColor, cardColor, textColor, subTextColor, quitBtnColor, pillBgColor, boxBgColor;
        if (themeState == 0) { bgColor = Color.parseColor("#FFFFFF"); cardColor = Color.parseColor("#F3F4F6"); textColor = Color.parseColor("#1C1C1E"); subTextColor = Color.parseColor("#6B7280"); quitBtnColor = Color.parseColor("#E5E5EA"); pillBgColor = Color.parseColor("#E5E5EA"); boxBgColor = Color.parseColor("#FFFFFF"); iconColor = Color.parseColor("#333333");
        } else if (themeState == 1) { bgColor = Color.parseColor("#1C1C1E"); cardColor = Color.parseColor("#2C2C2E"); textColor = Color.WHITE; subTextColor = Color.parseColor("#8E8E93"); quitBtnColor = Color.parseColor("#3A3A3C"); pillBgColor = Color.parseColor("#2D313A"); boxBgColor = Color.parseColor("#D8E2FF"); iconColor = Color.parseColor("#001C3A");
        } else { bgColor = Color.parseColor("#000000"); cardColor = Color.parseColor("#111827"); textColor = Color.WHITE; subTextColor = Color.parseColor("#9CA3AF"); quitBtnColor = Color.parseColor("#2C2C2E"); pillBgColor = Color.parseColor("#2D313A"); boxBgColor = Color.parseColor("#D8E2FF"); iconColor = Color.parseColor("#001C3A"); }

        View root = findViewById(R.id.flappyRoot);
        root.setBackgroundColor(bgColor);
        if (tvCurrentScore != null) tvCurrentScore.setTextColor(textColor);
        if (tvHighScore != null) { tvHighScore.setTextColor(subTextColor); tvHighScore.setText("Best: " + highScore); }
        if (tvTapToStart != null) tvTapToStart.setTextColor(textColor);
        TextView tvPauseTitle = findViewById(R.id.tvPauseTitle);
        if (tvPauseTitle != null) tvPauseTitle.setTextColor(textColor);
        if (tvGameOverTitle != null) tvGameOverTitle.setTextColor(textColor);
        if (tvFinalScore != null) tvFinalScore.setTextColor(textColor);
        if (tvSelectTitle != null) tvSelectTitle.setTextColor(textColor);
        if (tvClassicTitle != null) tvClassicTitle.setTextColor(textColor);
        if (tvGooTitle != null) tvGooTitle.setTextColor(textColor);

        if (tvClassicStats != null) { tvClassicStats.setTextColor(subTextColor); tvClassicStats.setText("Played: " + classicPlayed + "\nBest: " + classicBest); }
        if (tvGooStats != null) { tvGooStats.setTextColor(subTextColor); tvGooStats.setText("Played: " + gooPlayed + "\nBest: " + gooBest); }

        try {
            if (ivClassicPreview != null) ivClassicPreview.setImageResource(getResources().getIdentifier("flappy_bird_blue", "drawable", getPackageName()));
            if (ivGooPreview != null) ivGooPreview.setImageResource(getResources().getIdentifier("goobird5", "drawable", getPackageName()));
        } catch (Exception ignored){}

        if (btnQuit != null) { btnQuit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(quitBtnColor)); btnQuit.setTextColor(textColor); }
        if (btnQuitFromPause != null) { btnQuitFromPause.setBackgroundTintList(android.content.res.ColorStateList.valueOf(quitBtnColor)); btnQuitFromPause.setTextColor(textColor); }

        if (btnPause != null) {
            btnPause.setBackground(createPillShape(pillBgColor));
            pauseIconContainer.setBackground(createBoxShape(boxBgColor));
            pauseIconView = new GameIconView(this);
            pauseIconContainer.addView(pauseIconView);
            pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
        }

        float density = getResources().getDisplayMetrics().density;

        setupTopHeader(tvSelectTitle, characterSelectionOverlay, themeState, density);
        setupTopHeader(tvGameOverTitle, gameOverOverlay, themeState, density);
        setupTopHeader(tvPauseTitle, pauseOverlay, themeState, density);

        GradientDrawable gdCard = new GradientDrawable(); gdCard.setColor(cardColor); gdCard.setCornerRadius(60f);
        if (pauseCard != null) { pauseCard.setBackground(gdCard); pauseCard.setPadding((int)(24*density), (int)(32*density), (int)(24*density), (int)(32*density)); }
        if (gameOverCard != null) { gameOverCard.setBackground(gdCard); gameOverCard.setPadding((int)(24*density), (int)(32*density), (int)(24*density), (int)(32*density)); }
        if (characterSelectionCard != null) { characterSelectionCard.setBackground(gdCard); }

        GradientDrawable btnGd = new GradientDrawable(); btnGd.setColor(pillBgColor); btnGd.setCornerRadius(40f);
        if (btnSelectClassic != null) btnSelectClassic.setBackground(btnGd);
        if (btnSelectGoo != null) btnSelectGoo.setBackground(btnGd);

        if (snapshotContainer != null) {
            ViewGroup.LayoutParams slp = snapshotContainer.getLayoutParams();
            if (slp != null) { slp.width = (int)(100*density); slp.height = (int)(100*density); snapshotContainer.setLayoutParams(slp); }
            GradientDrawable snapBg = new GradientDrawable(); snapBg.setColor(bgColor); snapBg.setCornerRadius(40f); snapBg.setStroke(8, quitBtnColor);
            snapshotContainer.setBackground(snapBg);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) snapshotContainer.setClipToOutline(true);
        }

        if (btnToggleVibration != null) {
            btnToggleVibration.setText("Vibration: " + (isVibrationEnabled ? "ON" : "OFF"));
            btnToggleVibration.setOnClickListener(v -> {
                isVibrationEnabled = !isVibrationEnabled; prefs.edit().putBoolean("flappy_vibration_enabled", isVibrationEnabled).apply();
                btnToggleVibration.setText("Vibration: " + (isVibrationEnabled ? "ON" : "OFF"));
                if (gameEngine != null) gameEngine.setVibrationEnabled(isVibrationEnabled);
            });
        }

        gameEngine = new FlappyGameEngine(this, themeState);
        gameEngine.setVibrationEnabled(isVibrationEnabled);
        gameContainer.addView(gameEngine);

        // Character Selection Logic
        if (btnSelectClassic != null) {
            btnSelectClassic.setOnClickListener(v -> {
                if (characterSelectionOverlay != null) characterSelectionOverlay.setVisibility(View.GONE);
                scoreContainer.setVisibility(View.GONE);
                tvTapToStart.setText("Tap to Fly");
                tvTapToStart.setVisibility(View.VISIBLE);
                currentBestScore = classicBest; // Bind accurate score
                if (tvHighScore != null) tvHighScore.setText("Best: " + currentBestScore);
                if (gameEngine != null) gameEngine.setBirdType(0);
            });
        }

        if (btnSelectGoo != null) {
            btnSelectGoo.setOnClickListener(v -> {
                if (characterSelectionOverlay != null) characterSelectionOverlay.setVisibility(View.GONE);
                scoreContainer.setVisibility(View.GONE);
                tvTapToStart.setText("Tap to Fly");
                tvTapToStart.setVisibility(View.VISIBLE);
                currentBestScore = gooBest; // Bind accurate score
                if (tvHighScore != null) tvHighScore.setText("Best: " + currentBestScore);
                if (gameEngine != null) gameEngine.setBirdType(1);
            });
        }

        gameEngine.setGameListener(new FlappyGameEngine.GameListener() {
            @Override public void onScoreUpdated(int score) {
                if (tvCurrentScore != null) tvCurrentScore.setText("Score: " + score);
            }
            @Override public void onGameOver(int finalScore, Bitmap snapshot, int type) {
                int cBest = 0;
                if (type == 0) {
                    classicPlayed++; prefs.edit().putInt("classic_played", classicPlayed).apply();
                    if (finalScore > classicBest) { classicBest = finalScore; prefs.edit().putInt("classic_best", classicBest).apply(); if (tvNewHighScoreBanner != null) tvNewHighScoreBanner.setVisibility(View.VISIBLE); } else { if (tvNewHighScoreBanner != null) tvNewHighScoreBanner.setVisibility(View.GONE); }
                    cBest = classicBest;
                    if (tvClassicStats != null) tvClassicStats.setText("Played: " + classicPlayed + "\nBest: " + classicBest);
                } else {
                    gooPlayed++; prefs.edit().putInt("goo_played", gooPlayed).apply();
                    if (finalScore > gooBest) { gooBest = finalScore; prefs.edit().putInt("goo_best", gooBest).apply(); if (tvNewHighScoreBanner != null) tvNewHighScoreBanner.setVisibility(View.VISIBLE); } else { if (tvNewHighScoreBanner != null) tvNewHighScoreBanner.setVisibility(View.GONE); }
                    cBest = gooBest;
                    if (tvGooStats != null) tvGooStats.setText("Played: " + gooPlayed + "\nBest: " + gooBest);
                }
                currentBestScore = cBest; // Ensure our reference is up to date
                if (tvHighScore != null) tvHighScore.setText("Best: " + cBest);

                if (snapshot != null && ivDeathSnapshot != null && snapshotContainer != null) { ivDeathSnapshot.setImageBitmap(snapshot); snapshotContainer.setVisibility(View.VISIBLE); }
                else if (snapshotContainer != null) snapshotContainer.setVisibility(View.GONE);

                if (tvFinalScore != null) tvFinalScore.setText("Score: " + finalScore);
                showOverlaySmoothly(gameOverOverlay); fadeOutHudSmoothly(btnPause);
            }
            @Override public void onGameStarted() {
                // Cancel any running animators from rapid clicks to prevent layout distortion
                if (hudWidthAnimator != null) hudWidthAnimator.cancel();
                if (hudTextAnimator != null) hudTextAnimator.cancel();

                if (tvTapToStart != null && tvTapToStart.getVisibility() == View.VISIBLE) {
                    final int startWidth = btnPause.getWidth();

                    // Temporarily set target texts & visibility to measure accurate target dimensions
                    tvTapToStart.setVisibility(View.GONE);
                    scoreContainer.setVisibility(View.VISIBLE);
                    tvCurrentScore.setText("Score: 0");
                    tvHighScore.setText("Best: " + currentBestScore);

                    ViewGroup.LayoutParams tempLp = btnPause.getLayoutParams();
                    tempLp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    btnPause.setLayoutParams(tempLp);

                    btnPause.measure(
                            View.MeasureSpec.makeMeasureSpec(((View)btnPause.getParent()).getWidth(), View.MeasureSpec.AT_MOST),
                            View.MeasureSpec.makeMeasureSpec(((View)btnPause.getParent()).getHeight(), View.MeasureSpec.AT_MOST)
                    );
                    final int targetWidth = btnPause.getMeasuredWidth();

                    // Revert to start state to begin fluid animation
                    tempLp.width = startWidth;
                    btnPause.setLayoutParams(tempLp);

                    tvTapToStart.setVisibility(View.VISIBLE);
                    scoreContainer.setVisibility(View.GONE);
                    scoreContainer.setAlpha(1f);

                    String tapStr = "Tap to Fly";
                    String curScoreStr = "Score: 0";
                    String bestScoreStr = "Best: " + currentBestScore;

                    hudWidthAnimator = ValueAnimator.ofInt(startWidth, targetWidth);
                    hudWidthAnimator.addUpdateListener(animation -> {
                        ViewGroup.LayoutParams lp = btnPause.getLayoutParams();
                        lp.width = (Integer) animation.getAnimatedValue();
                        btnPause.setLayoutParams(lp);
                    });
                    hudWidthAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator animation) {
                            ViewGroup.LayoutParams lp = btnPause.getLayoutParams();
                            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                            btnPause.setLayoutParams(lp);
                        }
                    });
                    hudWidthAnimator.setDuration(400);
                    hudWidthAnimator.setInterpolator(new DecelerateInterpolator());

                    hudTextAnimator = ValueAnimator.ofFloat(0f, 1f);
                    hudTextAnimator.addUpdateListener(anim -> {
                        float fraction = anim.getAnimatedFraction();
                        if (fraction < 0.4f) { // Erase "Tap to Fly"
                            float eraseFraction = 1f - (fraction / 0.4f);
                            int tapLen = (int)(tapStr.length() * eraseFraction);
                            tvTapToStart.setText(tapStr.substring(0, Math.max(0, Math.min(tapLen, tapStr.length()))));
                        } else { // Type the Scores
                            if (tvTapToStart.getVisibility() == View.VISIBLE) {
                                tvTapToStart.setVisibility(View.GONE);
                                scoreContainer.setVisibility(View.VISIBLE);
                            }
                            float typeFraction = (fraction - 0.4f) / 0.6f;
                            int curLen = (int)(curScoreStr.length() * typeFraction);
                            int bestLen = (int)(bestScoreStr.length() * typeFraction);
                            tvCurrentScore.setText(curScoreStr.substring(0, Math.max(0, Math.min(curLen, curScoreStr.length()))));
                            tvHighScore.setText(bestScoreStr.substring(0, Math.max(0, Math.min(bestLen, bestScoreStr.length()))));
                        }
                    });
                    hudTextAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator animation) {
                            tvCurrentScore.setText(curScoreStr);
                            tvHighScore.setText(bestScoreStr);
                            tvTapToStart.setText(tapStr);
                            tvTapToStart.setVisibility(View.GONE);
                            scoreContainer.setVisibility(View.VISIBLE);
                        }
                    });
                    hudTextAnimator.setDuration(400);

                    hudWidthAnimator.start();
                    hudTextAnimator.start();
                }
            }
        });

        if (btnPause != null) btnPause.setOnClickListener(v -> { gameEngine.pauseGame(); pauseIconView.setIcon(GameIconView.ICON_PLAY, iconColor); showOverlaySmoothly(pauseOverlay); fadeOutHudSmoothly(btnPause); });
        if (btnResume != null) btnResume.setOnClickListener(v -> { hideOverlaySmoothly(pauseOverlay); fadeInHudSmoothly(btnPause); pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor); gameEngine.resumeGame(); });
        if (btnRestart != null) btnRestart.setOnClickListener(v -> {
            hideOverlaySmoothly(gameOverOverlay);
            if (tvNewHighScoreBanner != null) tvNewHighScoreBanner.setVisibility(View.GONE);

            // Cancel any mid-flight animators from spam clicking
            if (hudWidthAnimator != null) hudWidthAnimator.cancel();
            if (hudTextAnimator != null) hudTextAnimator.cancel();

            // --- REVERSE LETTER-BY-LETTER TYPING ANIMATION ---
            if (scoreContainer != null && scoreContainer.getVisibility() == View.VISIBLE) {
                final int startWidth = btnPause.getWidth();

                scoreContainer.setVisibility(View.GONE);
                tvTapToStart.setText("Tap to Fly");
                tvTapToStart.setVisibility(View.VISIBLE);

                ViewGroup.LayoutParams tempLp = btnPause.getLayoutParams();
                tempLp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                btnPause.setLayoutParams(tempLp);

                btnPause.measure(
                        View.MeasureSpec.makeMeasureSpec(((View)btnPause.getParent()).getWidth(), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(((View)btnPause.getParent()).getHeight(), View.MeasureSpec.AT_MOST)
                );
                final int targetWidth = btnPause.getMeasuredWidth();

                tempLp.width = startWidth;
                btnPause.setLayoutParams(tempLp);

                scoreContainer.setVisibility(View.VISIBLE);
                tvTapToStart.setVisibility(View.GONE);

                final String tapStr = "Tap to Fly";

                // Fetch safely, providing fallbacks before going final for the lambda
                String rawCurScore = tvCurrentScore.getText().toString();
                final String curScoreStr = rawCurScore.isEmpty() ? "Score: 0" : rawCurScore;
                final String bestScoreStr = "Best: " + currentBestScore;

                hudWidthAnimator = ValueAnimator.ofInt(startWidth, targetWidth);
                hudWidthAnimator.addUpdateListener(animation -> {
                    ViewGroup.LayoutParams lp = btnPause.getLayoutParams();
                    lp.width = (Integer) animation.getAnimatedValue();
                    btnPause.setLayoutParams(lp);
                });
                hudWidthAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        ViewGroup.LayoutParams lp = btnPause.getLayoutParams();
                        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                        btnPause.setLayoutParams(lp);
                    }
                });
                hudWidthAnimator.setDuration(400);
                hudWidthAnimator.setInterpolator(new DecelerateInterpolator());

                hudTextAnimator = ValueAnimator.ofFloat(0f, 1f);
                hudTextAnimator.addUpdateListener(anim -> {
                    float fraction = anim.getAnimatedFraction();
                    if (fraction < 0.4f) { // Erase Scores
                        float eraseFraction = 1f - (fraction / 0.4f);
                        int curLen = (int)(curScoreStr.length() * eraseFraction);
                        int bestLen = (int)(bestScoreStr.length() * eraseFraction);
                        tvCurrentScore.setText(curScoreStr.substring(0, Math.max(0, Math.min(curLen, curScoreStr.length()))));
                        tvHighScore.setText(bestScoreStr.substring(0, Math.max(0, Math.min(bestLen, bestScoreStr.length()))));
                    } else { // Type "Tap to Fly"
                        if (scoreContainer.getVisibility() == View.VISIBLE) {
                            scoreContainer.setVisibility(View.GONE);
                            tvTapToStart.setVisibility(View.VISIBLE);
                        }
                        float typeFraction = (fraction - 0.4f) / 0.6f;
                        int tapLen = (int)(tapStr.length() * typeFraction);
                        tvTapToStart.setText(tapStr.substring(0, Math.max(0, Math.min(tapLen, tapStr.length()))));
                    }
                });
                hudTextAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        tvCurrentScore.setText("Score: 0"); // Reset internal text for next run
                        tvHighScore.setText(bestScoreStr);
                        tvTapToStart.setText(tapStr);
                        scoreContainer.setVisibility(View.GONE);
                        tvTapToStart.setVisibility(View.VISIBLE);
                    }
                });
                hudTextAnimator.setDuration(400);

                hudWidthAnimator.start();
                hudTextAnimator.start();
            } else {
                if (scoreContainer != null) scoreContainer.setVisibility(View.GONE);
                if (tvTapToStart != null) {
                    tvTapToStart.setText("Tap to Fly");
                    tvTapToStart.setVisibility(View.VISIBLE);
                }
                if (tvCurrentScore != null) tvCurrentScore.setText("Score: 0");
            }

            fadeInHudSmoothly(btnPause);
            if (pauseIconView != null) pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
            gameEngine.resetGame();
        });
        if (btnQuit != null) btnQuit.setOnClickListener(v -> finish());
        if (btnQuitFromPause != null) btnQuitFromPause.setOnClickListener(v -> finish());

        applySeamlessStatusBar();
    }

    private void applySeamlessStatusBar() {
        getWindow().getDecorView().postDelayed(() -> {
            android.view.Window window = getWindow();
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

            window.setStatusBarColor(Color.TRANSPARENT);

            if (themeState == 0) {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.parseColor("#6DD5FA")));
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            } else if (themeState == 1) {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.parseColor("#0F2027")));
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            } else {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.parseColor("#000000")));
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            }
        }, 100);
    }

    private void setupTopHeader(TextView tv, RelativeLayout overlay, int themeState, float density) {
        if (tv == null || overlay == null) return;
        ViewGroup parent = (ViewGroup) tv.getParent();
        if (parent != null) parent.removeView(tv);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        tv.setLayoutParams(params);

        int bgColor, textColor;
        if (themeState == 0) { bgColor = Color.parseColor("#6DD5FA"); textColor = Color.parseColor("#1C1C1E"); }
        else if (themeState == 1) { bgColor = Color.parseColor("#0F2027"); textColor = Color.WHITE; }
        else { bgColor = Color.parseColor("#000000"); textColor = Color.WHITE; }

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadii(new float[]{0,0, 0,0, 60f,60f, 60f,60f});

        tv.setBackground(bg);
        tv.setTextColor(textColor);
        tv.setPadding(0, (int)(55 * density), 0, (int)(24 * density));
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setTextSize(26f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) tv.setElevation(10f * density);
        overlay.addView(tv);
    }

    @Override protected void onResume() { super.onResume(); applySeamlessStatusBar(); }
    @Override public void onBackPressed() {
        if (characterSelectionOverlay != null && characterSelectionOverlay.getVisibility() == View.VISIBLE) { finish(); return; }
        if (gameOverOverlay != null && gameOverOverlay.getVisibility() == View.VISIBLE) { finish(); return; }
        if (pauseOverlay != null && pauseOverlay.getVisibility() == View.VISIBLE) { finish(); return; }
        if (gameEngine != null && gameEngine.isPlaying() && !gameEngine.isGameOver()) { if (btnPause != null) { btnPause.performClick(); return; } }
        super.onBackPressed();
    }

    private void showOverlaySmoothly(View overlay) { if (overlay == null) return; overlay.setAlpha(0f); overlay.setVisibility(View.VISIBLE); overlay.animate().alpha(1f).setDuration(250).start(); }
    private void hideOverlaySmoothly(View overlay) { if (overlay == null) return; overlay.animate().alpha(0f).setDuration(250).withEndAction(() -> overlay.setVisibility(View.GONE)).start(); }
    private void fadeOutHudSmoothly(View hudView) { if (hudView != null) hudView.animate().alpha(0f).setDuration(250).withEndAction(() -> hudView.setVisibility(View.INVISIBLE)).start(); }
    private void fadeInHudSmoothly(View hudView) { if (hudView != null) { hudView.setAlpha(0f); hudView.setVisibility(View.VISIBLE); hudView.animate().alpha(1f).setDuration(250).start(); } }
    @Override public void finish() { super.finish(); overridePendingTransition(0, android.R.anim.fade_out); }
    private GradientDrawable createPillShape(int color) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(1000f); return shape; }
    private GradientDrawable createBoxShape(int color) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(30f); return shape; }
    @Override public void onConfigurationChanged(@NonNull Configuration newConfig) { super.onConfigurationChanged(newConfig); applySeamlessStatusBar(); }
    @Override protected void onPause() { super.onPause(); if (pauseOverlay != null) { boolean isPauseMenuVisible = (pauseOverlay.getVisibility() == View.VISIBLE); if (gameEngine != null && gameEngine.isPlaying() && !gameEngine.isGameOver() && !isPauseMenuVisible) { if (btnPause != null) btnPause.performClick(); } } }

    public static class GameIconView extends View {
        public static final int ICON_PAUSE = 0; public static final int ICON_PLAY = 1;
        private int iconType = ICON_PAUSE; private Paint paint;
        public GameIconView(Context context) { super(context); init(); }
        private void init() { paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4.5f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); }
        public void setIcon(int type, int color) { this.iconType = type; paint.setColor(color); invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (iconType == ICON_PAUSE) { paint.setStyle(Paint.Style.FILL); canvas.drawRoundRect(cx - 7f, cy - 8f, cx - 2f, cy + 8f, 3f, 3f, paint); canvas.drawRoundRect(cx + 2f, cy - 8f, cx + 7f, cy + 8f, 3f, 3f, paint);
            } else if (iconType == ICON_PLAY) { paint.setStyle(Paint.Style.FILL); Path p = new Path(); p.moveTo(cx - 4f, cy - 9f); p.lineTo(cx + 7f, cy); p.lineTo(cx - 4f, cy + 9f); p.close(); canvas.drawPath(p, paint); }
        }
    }

    public static class FlappyGameEngine extends View implements Choreographer.FrameCallback {

        private float screenW, screenH, refW, refH, birdX, birdY, birdVelocity, birdRadius, wormRadius, gravity, jumpStrength, pipeWidth, pipeGap, terminalVelocity, pipeSpeed, basePipeSpeed, maxPipeSpeed, parallaxScroll = 0f;
        private long lastFrameTime = 0, accumulator = 0;
        private static final long TIME_STEP_NS = 1000000000L / 60L;
        private final List<Pipe> pipes = new ArrayList<>();
        private boolean playing = false, paused = false, gameOver = false, vibrationEnabled = true, isImmune = false, isReady = false, gooDying = false;
        private int score = 0, pointsSinceLastWorm = 0, birdType = 0, currentFrameIndex = 0, sequenceIndex = 0, trailHead = 0, trailCount = 0;
        private long immunityEndTime = 0;
        private float sparkleAngle = 0f, animationTick = 0, gooDeathSpin = 0f;
        private Bitmap[] birdBitmaps;
        private Bitmap immuneBmp, suppressBmp;
        private final Matrix birdMatrix = new Matrix();
        private int[] flapSequence;
        private Bitmap deathSnapshot = null;

        private static final int MAX_TRAIL = 15;
        private final TrailPoint[] trail = new TrailPoint[MAX_TRAIL];
        private final DustParticle[] dustParticles = new DustParticle[10];

        private final RectF hitRect = new RectF(), tBodyRect = new RectF(), tCapRect = new RectF(), bCapRect = new RectF(), bBodyRect = new RectF(), topCollision = new RectF(), bottomCollision = new RectF(), saucerRect = new RectF();

        private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG), gooTrailPaint = new Paint(Paint.ANTI_ALIAS_FLAG), skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG), cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG), mountainPaint = new Paint(Paint.ANTI_ALIAS_FLAG), hillPaint = new Paint(Paint.ANTI_ALIAS_FLAG), starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pipeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG), pipeHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG), pipeShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG), pipeOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG), fallbackBirdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint wormGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG), wormSparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG), wormBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG), wormDetailPaint = new Paint(Paint.ANTI_ALIAS_FLAG), wormEyeWhite = new Paint(Paint.ANTI_ALIAS_FLAG), wormEyeBlack = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint domeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG), domeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG), saucerFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG), saucerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG), lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG), auraFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG), auraStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG), starP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hudBg = new Paint(Paint.ANTI_ALIAS_FLAG), barBg = new Paint(Paint.ANTI_ALIAS_FLAG), barFill = new Paint(Paint.ANTI_ALIAS_FLAG), timeText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint electricPaintOuter = new Paint(Paint.ANTI_ALIAS_FLAG), electricPaintInner = new Paint(Paint.ANTI_ALIAS_FLAG), sunPaintOuter = new Paint(Paint.ANTI_ALIAS_FLAG), sunPaintInner = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint moonPaintOuter = new Paint(Paint.ANTI_ALIAS_FLAG), moonPaintInner = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint cityBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG), cityMidPaint = new Paint(Paint.ANTI_ALIAS_FLAG), cityFrontPaint = new Paint(Paint.ANTI_ALIAS_FLAG), cityWindowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final List<Star> stars = new ArrayList<>();
        private final Path mountainPath = new Path(), hillPath = new Path(), cloudPath = new Path(), electricPath = new Path();
        private final Path cityBackPath = new Path(), cityMidPath = new Path(), cityFrontPath = new Path(), cityWindowPath = new Path();

        private final int themeState;
        private GameListener listener;

        public interface GameListener { void onScoreUpdated(int score); void onGameOver(int finalScore, Bitmap snapshot, int birdType); void onGameStarted(); }

        public FlappyGameEngine(Context context, int themeState) {
            super(context);
            this.themeState = themeState;
            for(int i=0; i<MAX_TRAIL; i++) trail[i] = new TrailPoint(0,0);
            for(int i=0; i<10; i++) dustParticles[i] = new DustParticle();

            trailPaint.setColor(Color.WHITE); trailPaint.setStyle(Paint.Style.FILL);
            gooTrailPaint.setColor(Color.parseColor("#888888")); gooTrailPaint.setStyle(Paint.Style.FILL);
            fallbackBirdPaint.setColor(Color.parseColor("#3498DB"));

            pipeOutlinePaint.setStyle(Paint.Style.STROKE); pipeOutlinePaint.setStrokeWidth(6f);
            electricPaintOuter.setColor(Color.parseColor("#00FFFF")); electricPaintOuter.setStyle(Paint.Style.STROKE); electricPaintOuter.setStrokeWidth(8f); electricPaintOuter.setAlpha(150);
            electricPaintInner.setColor(Color.WHITE); electricPaintInner.setStyle(Paint.Style.STROKE); electricPaintInner.setStrokeWidth(3f);

            sunPaintOuter.setColor(Color.parseColor("#FFCA28")); sunPaintOuter.setAlpha(120);
            sunPaintInner.setColor(Color.parseColor("#FFEE58"));

            moonPaintOuter.setColor(Color.parseColor("#90A4AE")); moonPaintOuter.setAlpha(120);
            moonPaintInner.setColor(Color.parseColor("#CFD8DC"));

            if (themeState == 0) {
                mountainPaint.setColor(Color.parseColor("#A1D4E6")); hillPaint.setColor(Color.parseColor("#7CB342")); cloudPaint.setColor(Color.parseColor("#FFFFFF"));
                cityBackPaint.setColor(Color.parseColor("#B0BEC5")); cityMidPaint.setColor(Color.parseColor("#90A4AE")); cityFrontPaint.setColor(Color.parseColor("#78909C")); cityWindowPaint.setColor(Color.parseColor("#FFFFFF"));
            } else if (themeState == 1) {
                mountainPaint.setColor(Color.parseColor("#2C5364")); hillPaint.setColor(Color.parseColor("#182825")); cloudPaint.setColor(Color.parseColor("#2A3B4C"));
                cityBackPaint.setColor(Color.parseColor("#263238")); cityMidPaint.setColor(Color.parseColor("#37474F")); cityFrontPaint.setColor(Color.parseColor("#1A232E")); cityWindowPaint.setColor(Color.parseColor("#FFF59D"));
            } else {
                starPaint.setColor(Color.WHITE); mountainPaint.setColor(Color.parseColor("#0A0A0A")); hillPaint.setColor(Color.parseColor("#111111")); cloudPaint.setColor(Color.parseColor("#1A1A1A"));
                cityBackPaint.setColor(Color.parseColor("#0D1117")); cityMidPaint.setColor(Color.parseColor("#111827")); cityFrontPaint.setColor(Color.parseColor("#0B0F19")); cityWindowPaint.setColor(Color.parseColor("#00E5FF"));
            }

            wormGlowPaint.setColor(Color.parseColor("#FFD700")); wormGlowPaint.setAlpha(80); wormSparklePaint.setColor(Color.WHITE); wormSparklePaint.setStrokeWidth(3f); wormBodyPaint.setColor(Color.parseColor("#FFD700")); wormDetailPaint.setColor(Color.parseColor("#F39C12")); wormEyeWhite.setColor(Color.WHITE); wormEyeBlack.setColor(Color.BLACK);
            domeFillPaint.setColor(Color.parseColor("#4400FFFF")); domeFillPaint.setStyle(Paint.Style.FILL); domeStrokePaint.setColor(Color.parseColor("#8800FFFF")); domeStrokePaint.setStyle(Paint.Style.STROKE); domeStrokePaint.setStrokeWidth(4f);
            saucerFillPaint.setColor(Color.parseColor("#B0C4DE")); saucerFillPaint.setStyle(Paint.Style.FILL); saucerStrokePaint.setColor(Color.parseColor("#39FF14")); saucerStrokePaint.setStyle(Paint.Style.STROKE); saucerStrokePaint.setStrokeWidth(6f);
            lightPaint.setColor(Color.WHITE); lightPaint.setStyle(Paint.Style.FILL); auraFillPaint.setColor(Color.parseColor("#FFD700")); auraFillPaint.setAlpha(80); auraFillPaint.setStyle(Paint.Style.FILL); auraStrokePaint.setColor(Color.parseColor("#FFD700")); auraStrokePaint.setAlpha(220); auraStrokePaint.setStyle(Paint.Style.STROKE); auraStrokePaint.setStrokeWidth(5f); starP.setColor(Color.WHITE); starP.setStyle(Paint.Style.FILL);
            hudBg.setColor(Color.parseColor("#99000000")); barBg.setColor(Color.parseColor("#40FFFFFF")); barFill.setColor(themeState == 2 ? Color.parseColor("#00FFFF") : Color.parseColor("#FFD700"));
            timeText.setColor(Color.WHITE); timeText.setTextSize(34f); timeText.setTypeface(Typeface.DEFAULT_BOLD); timeText.setTextAlign(Paint.Align.CENTER);
        }

        public void setGameListener(GameListener listener) { this.listener = listener; }
        public void setVibrationEnabled(boolean enabled) { this.vibrationEnabled = enabled; }

        public void setBirdType(int type) {
            this.birdType = type;
            if (type == 0) { pipeFillPaint.setColor(Color.parseColor("#73BF2E")); pipeHighlightPaint.setColor(Color.parseColor("#9AE05B")); pipeShadowPaint.setColor(Color.parseColor("#528A22")); pipeOutlinePaint.setColor(Color.parseColor("#3A5B1D"));
            } else { pipeFillPaint.setColor(Color.parseColor("#A9A9A9")); pipeHighlightPaint.setColor(Color.parseColor("#E0E0E0")); pipeShadowPaint.setColor(Color.parseColor("#696969")); pipeOutlinePaint.setColor(Color.parseColor("#2F2F2F")); }
            loadBirdSprite(); isReady = true; invalidate();
        }

        private void loadBirdSprite() {
            int birdSize;
            if (birdType == 0) {
                birdSize = (int) (birdRadius * 2.8f); birdBitmaps = new Bitmap[3]; flapSequence = new int[]{0, 1, 2, 1};
                int[] resIds = { getResources().getIdentifier("flappy_bird_blue_up", "drawable", getContext().getPackageName()), getResources().getIdentifier("flappy_bird_blue", "drawable", getContext().getPackageName()), getResources().getIdentifier("flappy_bird_blue_down", "drawable", getContext().getPackageName()) };
                for (int i = 0; i < 3; i++) { if (resIds[i] != 0) { Bitmap raw = BitmapFactory.decodeResource(getResources(), resIds[i]); birdBitmaps[i] = Bitmap.createScaledBitmap(raw, birdSize, birdSize, true); } }
            } else {
                birdSize = (int) (birdRadius * 3.0f); birdBitmaps = new Bitmap[9]; flapSequence = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
                for (int i = 0; i < 9; i++) {
                    int resId = getResources().getIdentifier("goobird" + (i + 1), "drawable", getContext().getPackageName());
                    if (resId != 0) { Bitmap raw = BitmapFactory.decodeResource(getResources(), resId); birdBitmaps[i] = Bitmap.createScaledBitmap(raw, birdSize, birdSize, true);
                    } else { int fallbackId = getResources().getIdentifier("flappy_bird_blue", "drawable", getContext().getPackageName()); if (fallbackId != 0) { Bitmap raw = BitmapFactory.decodeResource(getResources(), fallbackId); birdBitmaps[i] = Bitmap.createScaledBitmap(raw, birdSize, birdSize, true); } }
                }
            }
            sequenceIndex = 0; currentFrameIndex = flapSequence[0];
        }

        @Override protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            float oldBirdYRatio = (oldHeight > 0) ? (birdY / (float) oldHeight) : 0.5f;
            screenW = w; screenH = h; refH = Math.max(screenW, screenH); refW = Math.min(screenW, screenH);
            birdRadius = refH * 0.035f; wormRadius = refH * 0.025f; birdX = screenW * 0.3f;

            gravity = refH * 0.0008f; jumpStrength = refH * -0.015f; terminalVelocity = refH * 0.018f;
            basePipeSpeed = refW * 0.006f; maxPipeSpeed = refW * 0.015f; pipeWidth = refW * 0.18f;
            pipeGap = (screenH > screenW) ? (screenH * 0.30f) : (screenH * 0.48f);

            if (themeState == 0) { skyPaint.setShader(new LinearGradient(0, 0, 0, screenH, Color.parseColor("#6DD5FA"), Color.parseColor("#E0F6FF"), Shader.TileMode.CLAMP));
            } else if (themeState == 1) { skyPaint.setShader(new LinearGradient(0, 0, 0, screenH, Color.parseColor("#0F2027"), Color.parseColor("#203A43"), Shader.TileMode.CLAMP));
            } else { skyPaint.setShader(new LinearGradient(0, 0, 0, screenH, Color.parseColor("#000000"), Color.parseColor("#05050A"), Shader.TileMode.CLAMP)); stars.clear(); for (int i = 0; i < 80; i++) stars.add(new Star((float) Math.random() * screenW, (float) Math.random() * (screenH * 0.7f), (float) Math.random() * 3f + 1f, (float) Math.random(), (float) Math.random() * 0.03f + 0.01f)); }

            mountainPath.reset(); mountainPath.moveTo(0, screenH * 0.6f); mountainPath.lineTo(screenW * 0.25f, screenH * 0.35f); mountainPath.lineTo(screenW * 0.5f, screenH * 0.6f); mountainPath.lineTo(screenW * 0.75f, screenH * 0.45f); mountainPath.lineTo(screenW, screenH * 0.6f); mountainPath.lineTo(screenW, screenH); mountainPath.lineTo(0, screenH); mountainPath.close();
            hillPath.reset(); hillPath.moveTo(0, screenH * 0.8f); hillPath.quadTo(screenW * 0.25f, screenH * 0.7f, screenW * 0.5f, screenH * 0.8f); hillPath.quadTo(screenW * 0.75f, screenH * 0.9f, screenW, screenH * 0.8f); hillPath.lineTo(screenW, screenH); hillPath.lineTo(0, screenH); hillPath.close();
            cloudPath.reset(); cloudPath.addCircle(screenW * 0.2f, screenH * 0.2f, screenH * 0.05f, Path.Direction.CW); cloudPath.addCircle(screenW * 0.28f, screenH * 0.22f, screenH * 0.04f, Path.Direction.CW); cloudPath.addCircle(screenW * 0.15f, screenH * 0.22f, screenH * 0.03f, Path.Direction.CW); cloudPath.addCircle(screenW * 0.7f, screenH * 0.3f, screenH * 0.06f, Path.Direction.CW); cloudPath.addCircle(screenW * 0.8f, screenH * 0.32f, screenH * 0.04f, Path.Direction.CW); cloudPath.addCircle(screenW * 0.62f, screenH * 0.32f, screenH * 0.04f, Path.Direction.CW);

            cityBackPath.reset(); cityBackPath.moveTo(0, screenH); cityBackPath.lineTo(0, screenH * 0.55f); cityBackPath.lineTo(screenW * 0.15f, screenH * 0.55f); cityBackPath.lineTo(screenW * 0.15f, screenH * 0.45f); cityBackPath.lineTo(screenW * 0.3f, screenH * 0.45f); cityBackPath.lineTo(screenW * 0.3f, screenH * 0.6f); cityBackPath.lineTo(screenW * 0.45f, screenH * 0.6f); cityBackPath.lineTo(screenW * 0.45f, screenH * 0.4f); cityBackPath.lineTo(screenW * 0.6f, screenH * 0.4f); cityBackPath.lineTo(screenW * 0.6f, screenH * 0.5f); cityBackPath.lineTo(screenW * 0.8f, screenH * 0.5f); cityBackPath.lineTo(screenW * 0.8f, screenH * 0.55f); cityBackPath.lineTo(screenW, screenH * 0.55f); cityBackPath.lineTo(screenW, screenH); cityBackPath.close();

            cityMidPath.reset(); cityMidPath.moveTo(0, screenH); cityMidPath.lineTo(0, screenH * 0.7f); cityMidPath.lineTo(screenW * 0.1f, screenH * 0.7f); cityMidPath.lineTo(screenW * 0.1f, screenH * 0.45f); cityMidPath.lineTo(screenW * 0.25f, screenH * 0.45f); cityMidPath.lineTo(screenW * 0.25f, screenH * 0.75f); cityMidPath.lineTo(screenW * 0.35f, screenH * 0.75f); cityMidPath.lineTo(screenW * 0.35f, screenH * 0.35f); cityMidPath.lineTo(screenW * 0.55f, screenH * 0.35f); cityMidPath.lineTo(screenW * 0.55f, screenH * 0.7f); cityMidPath.lineTo(screenW * 0.7f, screenH * 0.7f); cityMidPath.lineTo(screenW * 0.7f, screenH * 0.55f); cityMidPath.lineTo(screenW * 0.9f, screenH * 0.55f); cityMidPath.lineTo(screenW * 0.9f, screenH * 0.7f); cityMidPath.lineTo(screenW, screenH * 0.7f); cityMidPath.lineTo(screenW, screenH); cityMidPath.close();

            cityFrontPath.reset(); cityFrontPath.moveTo(0, screenH); cityFrontPath.lineTo(0, screenH * 0.85f); cityFrontPath.lineTo(screenW * 0.2f, screenH * 0.85f); cityFrontPath.lineTo(screenW * 0.2f, screenH * 0.6f); cityFrontPath.lineTo(screenW * 0.4f, screenH * 0.6f); cityFrontPath.lineTo(screenW * 0.4f, screenH * 0.9f); cityFrontPath.lineTo(screenW * 0.6f, screenH * 0.9f); cityFrontPath.lineTo(screenW * 0.6f, screenH * 0.65f); cityFrontPath.lineTo(screenW * 0.85f, screenH * 0.65f); cityFrontPath.lineTo(screenW * 0.85f, screenH * 0.85f); cityFrontPath.lineTo(screenW, screenH * 0.85f); cityFrontPath.lineTo(screenW, screenH); cityFrontPath.close();

            cityWindowPath.reset();
            for(float wx = screenW * 0.38f; wx < screenW * 0.52f; wx += screenW * 0.05f) { for(float wy = screenH * 0.4f; wy < screenH * 0.7f; wy += screenH * 0.04f) { cityWindowPath.addRect(wx, wy, wx + screenW * 0.02f, wy + screenH * 0.02f, Path.Direction.CW); } }
            for(float wx = screenW * 0.12f; wx < screenW * 0.23f; wx += screenW * 0.05f) { for(float wy = screenH * 0.5f; wy < screenH * 0.65f; wy += screenH * 0.04f) { cityWindowPath.addRect(wx, wy, wx + screenW * 0.02f, wy + screenH * 0.02f, Path.Direction.CW); } }
            for(float wx = screenW * 0.73f; wx < screenW * 0.87f; wx += screenW * 0.05f) { for(float wy = screenH * 0.6f; wy < screenH * 0.7f; wy += screenH * 0.04f) { cityWindowPath.addRect(wx, wy, wx + screenW * 0.02f, wy + screenH * 0.02f, Path.Direction.CW); } }

            if (birdBitmaps == null && isReady) loadBirdSprite();

            try {
                if (immuneBmp == null) {
                    int resId = getResources().getIdentifier("immune", "drawable", getContext().getPackageName());
                    if (resId != 0) {
                        Bitmap raw = BitmapFactory.decodeResource(getResources(), resId);
                        int targetH = (int) (screenH * 0.035f);
                        if (targetH > 0) immuneBmp = Bitmap.createScaledBitmap(raw, (int)(targetH * ((float)raw.getWidth()/raw.getHeight())), targetH, true);
                    }
                }
                if (suppressBmp == null) {
                    int resId = getResources().getIdentifier("suppress", "drawable", getContext().getPackageName());
                    if (resId != 0) {
                        Bitmap raw = BitmapFactory.decodeResource(getResources(), resId);
                        int targetH = (int) (screenH * 0.035f);
                        if (targetH > 0) suppressBmp = Bitmap.createScaledBitmap(raw, (int)(targetH * ((float)raw.getWidth()/raw.getHeight())), targetH, true);
                    }
                }
            } catch (Exception ignored) {}

            if (oldWidth == 0 || oldHeight == 0) resetGame();
            else { birdY = oldBirdYRatio * screenH; if (birdY < birdRadius) birdY = birdRadius + 10; if (birdY > screenH - birdRadius) birdY = screenH - birdRadius - 10; pipeSpeed = basePipeSpeed + (score * (refW * 0.0001f)); if (pipeSpeed > maxPipeSpeed) pipeSpeed = maxPipeSpeed; for (Pipe p : pipes) { float maxTop = screenH - pipeGap - (screenH * 0.1f); if (p.topHeight > maxTop) p.topHeight = Math.max(screenH * 0.1f, maxTop); } }
        }

        public void resetGame() {
            birdX = screenW * 0.3f; birdY = screenH / 2f; birdVelocity = 0; pipes.clear(); trailHead = 0; trailCount = 0; score = 0; pipeSpeed = basePipeSpeed; pointsSinceLastWorm = 0; isImmune = false; immunityEndTime = 0; playing = false; paused = false; gameOver = false;
            gooDying = false; gooDeathSpin = 0f; deathSnapshot = null;
            for(DustParticle dp : dustParticles) dp.active = false;
            if (flapSequence != null && flapSequence.length > 0) currentFrameIndex = flapSequence[0];
            sequenceIndex = 0; animationTick = 0; lastFrameTime = 0; accumulator = 0; invalidate();
        }

        public void pauseGame() { paused = true; lastFrameTime = 0; accumulator = 0; }
        public void resumeGame() { paused = false; lastFrameTime = 0; accumulator = 0; Choreographer.getInstance().postFrameCallback(this); }
        public boolean isPlaying() { return playing; }
        public boolean isGameOver() { return gameOver; }

        private void spawnPipe() {
            float minTop = screenH * 0.1f, maxTop = screenH - pipeGap - minTop;
            float topHeight = minTop + (float) Math.random() * (maxTop - minTop);
            Pipe newPipe = new Pipe(screenW, topHeight);
            if (!isImmune && pointsSinceLastWorm >= 5) { newPipe.hasWorm = true; pointsSinceLastWorm = 0; }
            pipes.add(newPipe);
        }

        private void vibrateDeath() {
            if (vibrationEnabled) { Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE); if (v != null && v.hasVibrator()) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE)); else v.vibrate(800); } }
        }

        @SuppressLint("ClickableViewAccessibility") @Override public boolean onTouchEvent(MotionEvent event) {
            if (!isReady) return true;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (gameOver || paused || gooDying) return true;
                if (!playing) { playing = true; lastFrameTime = 0; accumulator = 0; if (listener != null) listener.onGameStarted(); Choreographer.getInstance().postFrameCallback(this); }
                birdVelocity = jumpStrength; return true;
            } return super.onTouchEvent(event);
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!playing || paused || gameOver) { lastFrameTime = 0; accumulator = 0; return; }
            if (lastFrameTime == 0) { lastFrameTime = frameTimeNanos; Choreographer.getInstance().postFrameCallback(this); return; }
            long elapsed = frameTimeNanos - lastFrameTime; lastFrameTime = frameTimeNanos; accumulator += elapsed;
            if (accumulator > TIME_STEP_NS * 5) accumulator = TIME_STEP_NS * 5;
            boolean stepped = false;
            while (accumulator >= TIME_STEP_NS) { physicsStep(); accumulator -= TIME_STEP_NS; stepped = true; }
            if (stepped) invalidate();
            if (!gameOver) Choreographer.getInstance().postFrameCallback(this);
        }

        private void physicsStep() {
            if (isImmune && System.currentTimeMillis() >= immunityEndTime) isImmune = false;
            sparkleAngle += 12f; if (sparkleAngle > 360f) sparkleAngle -= 360f;

            if (gooDying) {
                birdVelocity += gravity * 1.5f; birdY += birdVelocity; birdX -= basePipeSpeed * 0.8f; gooDeathSpin -= 15f;
                if (birdY > screenH + birdRadius * 2f) triggerGameOver();
                return;
            }

            parallaxScroll -= pipeSpeed; birdVelocity += gravity; if (birdVelocity > terminalVelocity) birdVelocity = terminalVelocity; birdY += birdVelocity;

            float speedFactor = 1f; int flapSpeedThreshold = (birdType == 0) ? 8 : 4;

            if (birdType == 0) {
                if (birdVelocity < terminalVelocity * 0.4f) {
                    animationTick += speedFactor;
                    if (animationTick >= flapSpeedThreshold && flapSequence != null) { animationTick = 0; sequenceIndex = (sequenceIndex + 1) % flapSequence.length; currentFrameIndex = flapSequence[sequenceIndex]; }
                } else {
                    animationTick += speedFactor;
                    if (animationTick >= flapSpeedThreshold && flapSequence != null) { currentFrameIndex = flapSequence[0]; sequenceIndex = 0; }
                }
            } else {
                if (birdVelocity > terminalVelocity * 0.15f && flapSequence != null) { currentFrameIndex = flapSequence[0]; animationTick = 0;
                } else {
                    animationTick += speedFactor;
                    if (animationTick >= flapSpeedThreshold && flapSequence != null) { animationTick = 0; sequenceIndex = (sequenceIndex + 1) % flapSequence.length; currentFrameIndex = flapSequence[sequenceIndex]; }
                }
            }

            float tiltAngle = 0f, tailOffset = birdRadius * 0.75f;
            if (birdType == 0) {
                if (birdVelocity < 0) tiltAngle = -25f; else { tiltAngle = (birdVelocity / terminalVelocity) * 90f; if (tiltAngle > 90f) tiltAngle = 90f; }
            } else tailOffset = birdRadius * 1.5f;

            float tailX = birdX - (float) Math.cos(Math.toRadians(tiltAngle)) * tailOffset;
            float tailY = birdY - (float) Math.sin(Math.toRadians(tiltAngle)) * tailOffset;

            if (birdType == 0) {
                trailHead = (trailHead + 1) % MAX_TRAIL; trail[trailHead].x = tailX; trail[trailHead].y = tailY;
                if (trailCount < MAX_TRAIL) trailCount++;
                for (int i = 0; i < trailCount; i++) { trail[i].x -= pipeSpeed; }
            } else {
                if (Math.random() < 0.2) {
                    for (int i=0; i<3; i++) {
                        if (!dustParticles[i].active) {
                            dustParticles[i].active = true; dustParticles[i].x = tailX; dustParticles[i].y = tailY;
                            dustParticles[i].vx = (float) ((Math.random() - 0.5) * refW * 0.001f) - (pipeSpeed * 0.2f);
                            dustParticles[i].vy = (float) ((Math.random() - 0.5) * refH * 0.002f);
                            dustParticles[i].maxLife = 15f + (float)(Math.random() * 10f); dustParticles[i].life = dustParticles[i].maxLife;
                            break;
                        }
                    }
                }
                for (int i=0; i<3; i++) {
                    if (dustParticles[i].active) { dustParticles[i].x += dustParticles[i].vx; dustParticles[i].y += dustParticles[i].vy; dustParticles[i].life -= 1f; if (dustParticles[i].life <= 0) dustParticles[i].active = false; }
                }
            }

            for (int i = 0; i < pipes.size(); i++) {
                Pipe p = pipes.get(i);
                p.x -= pipeSpeed;

                if (p.hasWorm && !p.wormEaten) {
                    float wormX = p.x + (pipeWidth / 2f), wormY = p.topHeight + (pipeGap / 2f);
                    float dxx = birdX - wormX, dyy = birdY - wormY;
                    if ((dxx * dxx) + (dyy * dyy) <= ((birdRadius + wormRadius) * (birdRadius + wormRadius))) {
                        p.wormEaten = true; isImmune = true; immunityEndTime = System.currentTimeMillis() + 4000;
                        if (vibrationEnabled) { Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE); if (vibrator != null && vibrator.hasVibrator()) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)); } else { vibrator.vibrate(50); } } }
                    }
                }

                if (!p.passed && p.x + pipeWidth < birdX) {
                    p.passed = true; score++; if (!isImmune) pointsSinceLastWorm++;
                    pipeSpeed = basePipeSpeed + (score * (refW * 0.0001f)); if (pipeSpeed > maxPipeSpeed) pipeSpeed = maxPipeSpeed;
                    if (listener != null) listener.onScoreUpdated(score);
                }
            }

            boolean canSpawnPipe = true;
            if (birdType == 1 && isImmune) canSpawnPipe = false;

            if (!pipes.isEmpty() && pipes.get(0).x + pipeWidth < 0) pipes.remove(0);
            if (canSpawnPipe && (pipes.isEmpty() || screenW - pipes.get(pipes.size() - 1).x > (screenW * 0.55f))) spawnPipe();

            checkCollisions();
        }

        private void checkCollisions() {
            if (gooDying) return;

            boolean hitFloorOrCeil = (birdY + birdRadius >= screenH || birdY - birdRadius <= 0);
            boolean hitPipe = false;

            float hitRx = birdRadius * (birdType == 0 ? 0.65f : 0.55f);
            float hitRy = birdRadius * (birdType == 0 ? 0.65f : 0.55f);
            hitRect.set(birdX - hitRx, birdY - hitRy, birdX + hitRx, birdY + hitRy);

            if (!hitFloorOrCeil) {
                for (Pipe p : pipes) {
                    topCollision.set(p.x, 0, p.x + pipeWidth, p.topHeight);
                    bottomCollision.set(p.x, p.topHeight + pipeGap, p.x + pipeWidth, screenH);
                    if (RectF.intersects(hitRect, topCollision) || RectF.intersects(hitRect, bottomCollision)) { hitPipe = true; break; }
                }
            }

            if (hitFloorOrCeil || hitPipe) {
                if (isImmune && birdType == 0) {
                    if (birdY - birdRadius <= 0) { birdY = birdRadius + 1; birdVelocity = 0; }
                    if (birdY + birdRadius >= screenH) { birdY = screenH - birdRadius - 1; birdVelocity = jumpStrength; }
                    hitPipe = false;
                } else if (birdType == 1) {
                    deathSnapshot = getDeathSnapshot();
                    gooDying = true; birdVelocity = jumpStrength * 0.8f; vibrateDeath();
                } else {
                    deathSnapshot = getDeathSnapshot(); triggerGameOver();
                }
            }
        }

        private Bitmap getDeathSnapshot() {
            try {
                Bitmap fullBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(fullBitmap); this.draw(canvas);
                int size = (int) (Math.min(getWidth(), getHeight()) * 0.40f); if (size <= 0) size = 300;
                int left = (int) (birdX - size / 2f), top = (int) (birdY - size / 2f);
                if (left < 0) left = 0; if (top < 0) top = 0; if (left + size > getWidth()) left = getWidth() - size; if (top + size > getHeight()) top = getHeight() - size;
                Bitmap cropped = Bitmap.createBitmap(fullBitmap, left, top, size, size); fullBitmap.recycle(); return cropped;
            } catch (Exception e) { return null; }
        }

        private void triggerGameOver() {
            if (gameOver) return; gameOver = true; playing = false; lastFrameTime = 0; accumulator = 0;
            if (birdType == 0) vibrateDeath();
            if (deathSnapshot == null) deathSnapshot = getDeathSnapshot();
            if (listener != null) listener.onGameOver(score, deathSnapshot, birdType);
        }

        private void drawTiledPath(Canvas canvas, Path path, float rawScroll, float speedMult, Paint paint) { float scrollOffset = (rawScroll * speedMult) % screenW; if (scrollOffset > 0) scrollOffset -= screenW; canvas.save(); canvas.translate(scrollOffset, 0); canvas.drawPath(path, paint); canvas.translate(screenW, 0); canvas.drawPath(path, paint); canvas.restore(); }

        private void drawElectricArcs(Canvas c, RectF r) {
            if(Math.random() > 0.05) return;
            electricPath.reset();
            float x = r.left - 20f + (float)Math.random() * (r.width() + 40f);
            float y = r.top; float st = r.height() / (4f + (float)Math.random() * 4f);
            electricPath.moveTo(x,y);
            while(y < r.bottom){
                float nx = r.left - 30f + (float)Math.random() * (r.width() + 60f);
                y += st; if(y > r.bottom) y = r.bottom;
                electricPath.lineTo(nx,y);
                if(Math.random() < 0.3f) { float bx = nx + (float)((Math.random() - 0.5) * 60f); float by = y + (float)((Math.random() - 0.5) * 60f); electricPath.moveTo(nx, y); electricPath.lineTo(bx, by); electricPath.moveTo(nx, y); }
            }
            c.drawPath(electricPath, electricPaintOuter); c.drawPath(electricPath, electricPaintInner);
        }

        private void drawProgrammaticPipe(Canvas canvas, RectF rect, boolean isBody) {
            canvas.drawRect(rect, pipeFillPaint); float highlightWidth = rect.width() * 0.15f;
            canvas.drawRect(rect.left + 8f, rect.top + 4f, rect.left + 8f + highlightWidth, rect.bottom - 4f, pipeHighlightPaint); float shadowWidth = rect.width() * 0.25f;
            canvas.drawRect(rect.right - 8f - shadowWidth, rect.top + 4f, rect.right - 8f, rect.bottom - 4f, pipeShadowPaint);
            canvas.drawRect(rect, pipeOutlinePaint);
            if (birdType == 1 && isBody) drawElectricArcs(canvas, rect);
        }

        private void drawGoldenWorm(Canvas canvas, float cx, float cy, float r) { canvas.save(); canvas.drawCircle(cx, cy, r * 1.8f, wormGlowPaint); canvas.save(); canvas.rotate(sparkleAngle, cx, cy); for (int i = 0; i < 4; i++) { canvas.rotate(90, cx, cy); canvas.drawLine(cx, cy - r * 1.5f, cx, cy - r * 2.2f, wormSparklePaint); canvas.drawCircle(cx, cy - r * 1.8f, 3f, wormSparklePaint); } canvas.restore(); canvas.drawCircle(cx - r * 0.6f, cy + r * 0.3f, r * 0.5f, wormDetailPaint); canvas.drawCircle(cx - r * 0.6f, cy + r * 0.3f, r * 0.4f, wormBodyPaint); canvas.drawCircle(cx, cy, r * 0.6f, wormDetailPaint); canvas.drawCircle(cx, cy, r * 0.5f, wormBodyPaint); canvas.drawCircle(cx + r * 0.6f, cy - r * 0.2f, r * 0.7f, wormDetailPaint); canvas.drawCircle(cx + r * 0.6f, cy - r * 0.2f, r * 0.6f, wormBodyPaint); canvas.drawCircle(cx + r * 0.8f, cy - r * 0.35f, r * 0.2f, wormEyeWhite); canvas.drawCircle(cx + r * 0.85f, cy - r * 0.35f, r * 0.1f, wormEyeBlack); canvas.restore(); }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawRect(0, 0, screenW, screenH, skyPaint);

            if (themeState == 2) { for (Star s : stars) { s.alpha += s.twinkleSpeed; if (s.alpha > 1f) { s.alpha = 1f; s.twinkleSpeed = -s.twinkleSpeed; } else if (s.alpha < 0.1f) { s.alpha = 0.1f; s.twinkleSpeed = -s.twinkleSpeed; } starPaint.setAlpha((int) (s.alpha * 255)); canvas.drawCircle(s.x, s.y, s.radius, starPaint); } }

            if (birdType == 0) {
                drawTiledPath(canvas, cloudPath, parallaxScroll, 0.15f, cloudPaint); drawTiledPath(canvas, mountainPath, parallaxScroll, 0.35f, mountainPaint); drawTiledPath(canvas, hillPath, parallaxScroll, 0.6f, hillPaint);
            } else {
                if (themeState == 0) { canvas.drawCircle(screenW * 0.8f, screenH * 0.2f, screenW * 0.12f, sunPaintOuter); canvas.drawCircle(screenW * 0.8f, screenH * 0.2f, screenW * 0.09f, sunPaintInner); }
                else if (themeState == 1) { canvas.drawCircle(screenW * 0.8f, screenH * 0.2f, screenW * 0.10f, moonPaintOuter); canvas.drawCircle(screenW * 0.8f, screenH * 0.2f, screenW * 0.07f, moonPaintInner); }

                drawTiledPath(canvas, cityBackPath, parallaxScroll, 0.15f, cityBackPaint); drawTiledPath(canvas, cityMidPath, parallaxScroll, 0.30f, cityMidPaint);
                drawTiledPath(canvas, cityWindowPath, parallaxScroll, 0.30f, cityWindowPaint); drawTiledPath(canvas, cityFrontPath, parallaxScroll, 0.50f, cityFrontPaint);
            }

            float capH = refH * 0.04f, capExt = refW * 0.015f;
            for (Pipe p : pipes) {
                tBodyRect.set(p.x, -20f, p.x + pipeWidth, p.topHeight - capH); tCapRect.set(p.x - capExt, p.topHeight - capH, p.x + pipeWidth + capExt, p.topHeight); bCapRect.set(p.x - capExt, p.topHeight + pipeGap, p.x + pipeWidth + capExt, p.topHeight + pipeGap + capH); bBodyRect.set(p.x, p.topHeight + pipeGap + capH, p.x + pipeWidth, screenH + 20f);
                drawProgrammaticPipe(canvas, tBodyRect, true); drawProgrammaticPipe(canvas, tCapRect, false); drawProgrammaticPipe(canvas, bCapRect, false); drawProgrammaticPipe(canvas, bBodyRect, true);
                if (p.hasWorm && !p.wormEaten) drawGoldenWorm(canvas, p.x + (pipeWidth / 2f), p.topHeight + (pipeGap / 2f), wormRadius);
            }

            if (birdType == 0) {
                if (trailCount > 0) {
                    for (int i = 0; i < trailCount; i++) { int idx = (trailHead - i + MAX_TRAIL) % MAX_TRAIL; TrailPoint t = trail[idx]; int alpha = (int) (200 * (1f - ((float) i / trailCount))); float radius = birdRadius * 0.35f * (1f - ((float) i / trailCount)); trailPaint.setAlpha(alpha); canvas.drawCircle(t.x, t.y, radius, trailPaint); }
                }
            } else {
                for (int i=0; i<3; i++) {
                    DustParticle dp = dustParticles[i];
                    if (dp.active) {
                        float progress = dp.life / dp.maxLife; int alpha = (int) (180 * progress); gooTrailPaint.setAlpha(alpha);
                        float radius = birdRadius * 0.02f;
                        canvas.drawCircle(dp.x, dp.y, radius, gooTrailPaint);
                    }
                }
            }

            if (isImmune && !gooDying) {
                if (themeState == 2) {
                    if (birdType == 0) { canvas.drawCircle(birdX, birdY, birdRadius * 2.0f, domeFillPaint); canvas.drawCircle(birdX, birdY, birdRadius * 2.0f, domeStrokePaint); saucerRect.set(birdX - birdRadius * 2.8f, birdY - birdRadius * 0.6f, birdX + birdRadius * 2.8f, birdY + birdRadius * 0.6f); canvas.drawOval(saucerRect, saucerFillPaint); canvas.drawOval(saucerRect, saucerStrokePaint); canvas.drawCircle(birdX - birdRadius * 1.8f, birdY, 6f, lightPaint); canvas.drawCircle(birdX + birdRadius * 1.8f, birdY, 6f, lightPaint); canvas.drawCircle(birdX, birdY + birdRadius * 0.4f, 7f, lightPaint);
                    } else { Path nonagon = new Path(); float shieldRadius = birdRadius * 2.6f; for (int i = 0; i < 9; i++) { float angle = (float) Math.toRadians((i * 40) + sparkleAngle); float px = birdX + (float) Math.cos(angle) * shieldRadius; float py = birdY + (float) Math.sin(angle) * shieldRadius; if (i == 0) { nonagon.moveTo(px, py); } else { nonagon.lineTo(px, py); } } nonagon.close(); canvas.drawPath(nonagon, saucerFillPaint); canvas.drawPath(nonagon, saucerStrokePaint); }
                } else { canvas.drawCircle(birdX, birdY, birdRadius * 1.8f, auraFillPaint); canvas.drawCircle(birdX, birdY, birdRadius * 1.8f, auraStrokePaint); canvas.save(); canvas.translate(birdX, birdY); canvas.rotate(sparkleAngle); for (int s = 0; s < 4; s++) { canvas.rotate(90); canvas.drawCircle(0, -birdRadius * 1.8f, 6f, starP); } canvas.restore(); }
            }

            float tiltAngle = 0f;
            if (gooDying) {
                tiltAngle = gooDeathSpin;
            } else if (birdType == 0) {
                if (birdVelocity < 0) tiltAngle = -25f; else { tiltAngle = (birdVelocity / terminalVelocity) * 90f; if (tiltAngle > 90f) tiltAngle = 90f; }
            }

            if (birdBitmaps != null && birdBitmaps.length > currentFrameIndex && birdBitmaps[currentFrameIndex] != null) { Bitmap currentFrame = birdBitmaps[currentFrameIndex]; birdMatrix.reset(); birdMatrix.postTranslate(-currentFrame.getWidth() / 2f, -currentFrame.getHeight() / 2f); birdMatrix.postRotate(tiltAngle); birdMatrix.postTranslate(birdX, birdY); canvas.drawBitmap(currentFrame, birdMatrix, null);
            } else { canvas.save(); canvas.rotate(tiltAngle, birdX, birdY); canvas.drawCircle(birdX, birdY, birdRadius, fallbackBirdPaint); canvas.restore(); }

            if (isImmune && immunityEndTime > System.currentTimeMillis() && !gooDying) {
                long timeLeft = immunityEndTime - System.currentTimeMillis(); float progress = Math.max(0f, Math.min(1f, timeLeft / 4000f)); float barW = screenW * 0.55f; float barH = 22f; float barX = (screenW - barW) / 2f; float barY = screenH * 0.22f;
                Bitmap labelBmp = (birdType == 1) ? suppressBmp : immuneBmp;
                if (labelBmp != null) {
                    String timeStr = String.format(Locale.US, " %.1fs", timeLeft / 1000f);
                    float textW = timeText.measureText(timeStr);
                    float totalW = labelBmp.getWidth() + textW;
                    float startX = (screenW - totalW) / 2f;
                    float bmpY = barY - labelBmp.getHeight() - 8f;
                    float boxTop = Math.min(barY - 48f, bmpY - 16f);
                    canvas.drawRoundRect(barX - 24f, boxTop, barX + barW + 24f, barY + barH + 16f, 30f, 30f, hudBg);
                    canvas.drawRoundRect(barX, barY, barX + barW, barY + barH, 11f, 11f, barBg);
                    canvas.drawRoundRect(barX, barY, barX + (barW * progress), barY + barH, 11f, 11f, barFill);
                    canvas.drawBitmap(labelBmp, startX, bmpY, null);
                    timeText.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText(timeStr, startX + labelBmp.getWidth(), barY - 12f, timeText);
                    timeText.setTextAlign(Paint.Align.CENTER);
                } else {
                    canvas.drawRoundRect(barX - 24f, barY - 48f, barX + barW + 24f, barY + barH + 16f, 30f, 30f, hudBg); canvas.drawRoundRect(barX, barY, barX + barW, barY + barH, 11f, 11f, barBg); canvas.drawRoundRect(barX, barY, barX + (barW * progress), barY + barH, 11f, 11f, barFill);
                    String effectName = (birdType == 1) ? "SUPPRESS" : "IMMUNITY"; String timeStr = String.format(Locale.US, "[ %s: %.1fs ]", effectName, timeLeft / 1000f); canvas.drawText(timeStr, screenW / 2f, barY - 14f, timeText);
                }
            }
        }

        private static class Pipe { float x, topHeight; boolean passed = false, hasWorm = false, wormEaten = false; Pipe(float x, float topHeight) { this.x = x; this.topHeight = topHeight; } }
        private static class TrailPoint { float x, y, vx, vy; TrailPoint(float x, float y) { this.x = x; this.y = y; } }
        private static class DustParticle { float x, y, vx, vy, life, maxLife; boolean active = false; }
        private static class Star { float x, y, radius, alpha, twinkleSpeed; Star(float x, float y, float r, float a, float ts) { this.x = x; this.y = y; this.radius = r; this.alpha = a; this.twinkleSpeed = ts; } }
    }
}