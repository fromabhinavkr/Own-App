package com.abhinav.ownapp;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class GlobeGameActivity extends AppCompatActivity {

    private UfoGameOverlayView ufoOverlay;
    private boolean isDarkTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fetch Main App Theme
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        // Apply true immersive full-screen mode before setting content view
        hideSystemUI();

        setContentView(R.layout.activity_globe_game);

        // Double check no residual padding exists from MainApplication
        View contentView = findViewById(android.R.id.content);
        if (contentView != null) {
            contentView.setPadding(0, 0, 0, 0);
        }

        ufoOverlay = findViewById(R.id.ufo_overlay);
        GlobeGLSurfaceView globeView = findViewById(R.id.globe_view_game);

        globeView.setOnGlobeLongPressListener(() -> {
            ufoOverlay.startGame();
        });

        // Listen for Game Over and show the themed dialog
        ufoOverlay.setOnGameOverListener(finalScore -> {
            runOnUiThread(() -> showGameOverDialog(finalScore));
        });
    }

    // Ensures the game stays in full screen even if the user swipes down the notification bar
    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    // Method to force absolute full screen and draw into the notch area
    private void hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // FIX: Aggressively force status bar color to transparent
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());

        // Hide both the status bar and the navigation bar
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        // Ensure the game draws completely into the notch/cutout area to prevent the white letterbox bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (ufoOverlay != null && ufoOverlay.handleTouch(ev.getX(), ev.getY())) {
                return true;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    // The custom themed Game Over Window with Best Score tracking
    private void showGameOverDialog(int score) {
        // --- 1. HANDLE BEST SCORE LOGIC ---
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        int bestScore = prefs.getInt("globe_game_best_score", 0);

        // If the current score beats the best score, update it!
        if (score > bestScore) {
            bestScore = score;
            prefs.edit().putInt("globe_game_best_score", bestScore).apply();
        }

        // --- 2. BUILD THE UI ---
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Define Theme Colors
        int bgColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        int textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#1C1C1E");
        int subTextColor = isDarkTheme ? Color.parseColor("#8E8E93") : Color.parseColor("#888888");
        int quitBtnColor = isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA");

        // Main Dialog Layout
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(70, 70, 70, 70);
        layout.setGravity(Gravity.CENTER);

        // Rounded background for the dialog
        GradientDrawable bgGd = new GradientDrawable();
        bgGd.setColor(bgColor);
        bgGd.setCornerRadius(60f);
        layout.setBackground(bgGd);

        // "GAME OVER" Text
        TextView tvGameOver = new TextView(this);
        tvGameOver.setText("GAME OVER");
        tvGameOver.setTextSize(32f);
        tvGameOver.setTypeface(null, Typeface.BOLD);
        tvGameOver.setTextColor(textColor);
        tvGameOver.setGravity(Gravity.CENTER);

        // "Score" Text
        TextView tvScore = new TextView(this);
        tvScore.setText("Score: " + score);
        tvScore.setTextSize(26f);
        tvScore.setTypeface(null, Typeface.BOLD);
        tvScore.setTextColor(textColor);
        tvScore.setGravity(Gravity.CENTER);
        tvScore.setPadding(0, 20, 0, 10);

        // "Best Score" Text
        TextView tvBestScore = new TextView(this);
        tvBestScore.setText("Best Score: " + bestScore);
        tvBestScore.setTextSize(16f);
        tvBestScore.setTypeface(null, Typeface.BOLD);
        tvBestScore.setTextColor(subTextColor);
        tvBestScore.setGravity(Gravity.CENTER);
        tvBestScore.setPadding(0, 0, 0, 60); // Padding before buttons

        // Play Again Button (Always Blue)
        Button btnPlayAgain = new Button(this);
        btnPlayAgain.setText("Play Again");
        btnPlayAgain.setTextColor(Color.WHITE);
        btnPlayAgain.setAllCaps(false);
        btnPlayAgain.setTextSize(18f);
        btnPlayAgain.setTypeface(null, Typeface.BOLD);
        GradientDrawable playGd = new GradientDrawable();
        playGd.setColor(Color.parseColor("#4A90E2"));
        playGd.setCornerRadius(100f);
        btnPlayAgain.setBackground(playGd);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 140);
        btnPlayAgain.setLayoutParams(playLp);

        // Quit Button (Themed)
        Button btnQuit = new Button(this);
        btnQuit.setText("Quit");
        btnQuit.setTextColor(textColor);
        btnQuit.setAllCaps(false);
        btnQuit.setTextSize(18f);
        btnQuit.setTypeface(null, Typeface.BOLD);
        GradientDrawable quitGd = new GradientDrawable();
        quitGd.setColor(quitBtnColor);
        quitGd.setCornerRadius(100f);
        btnQuit.setBackground(quitGd);
        LinearLayout.LayoutParams quitLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 140);
        quitLp.setMargins(0, 30, 0, 0);
        btnQuit.setLayoutParams(quitLp);

        // Assemble Layout
        layout.addView(tvGameOver);
        layout.addView(tvScore);
        layout.addView(tvBestScore);
        layout.addView(btnPlayAgain);
        layout.addView(btnQuit);

        builder.setView(layout);
        builder.setCancelable(false); // Prevents tapping outside to dismiss

        AlertDialog dialog = builder.create();

        // Makes the square dialog corners transparent so our rounded corners show
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            // Temporarily disable focus so the status bar doesn't pop down when the dialog opens
            dialog.getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }

        // Button Clicks
        btnPlayAgain.setOnClickListener(v -> {
            dialog.dismiss();
            ufoOverlay.startGame();
        });

        btnQuit.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        dialog.show();

        // Restore focus so buttons can be clicked
        if (dialog.getWindow() != null) {
            dialog.getWindow().getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility());
            dialog.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            // Ensure status bar hides again after dialog shows
            hideSystemUI();
        }
    }
}