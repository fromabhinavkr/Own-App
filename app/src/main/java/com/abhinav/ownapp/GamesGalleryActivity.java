package com.abhinav.ownapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class GamesGalleryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_games_gallery);

        // Read the theme from your main app's SharedPreferences
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        // Link Views
        LinearLayout root = findViewById(R.id.gamesGalleryRoot);
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

        // NEW: Link Maze Ball Views
        View cardMazeBall = findViewById(R.id.cardMazeBall);
        TextView textMazeBall = findViewById(R.id.textMazeBall);

        // Apply Theme Colors
        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
        int cardColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        int textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");
        int subTextColor = isDarkTheme ? Color.parseColor("#8E8E93") : Color.parseColor("#888888");

        root.setBackgroundColor(bgColor);
        title.setTextColor(textColor);
        subtitle.setTextColor(subTextColor);

        // Apply dynamic background to the cards to match theme
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(cardColor);
        cardBg.setCornerRadius(32f); // Matches your standard gallery card corners

        if (cardSnakeGame != null) {
            cardSnakeGame.setBackground(cardBg);
            textSnake.setTextColor(textColor);
        }

        if (cardFlappyBird != null) {
            cardFlappyBird.setBackground(cardBg);
            textFlappy.setTextColor(textColor);
        }

        if (cardTetris != null) {
            cardTetris.setBackground(cardBg);
            textTetris.setTextColor(textColor);
        }

        if (cardBreakout != null) {
            cardBreakout.setBackground(cardBg);
            textBreakout.setTextColor(textColor);
        }

        // NEW: Apply Theme to Maze Ball Card
        if (cardMazeBall != null) {
            cardMazeBall.setBackground(cardBg);
            textMazeBall.setTextColor(textColor);
        }

        // =====================================
        // GAME LAUNCHERS
        // =====================================

        if (cardSnakeGame != null) {
            cardSnakeGame.setOnClickListener(v -> {
                startActivity(new Intent(GamesGalleryActivity.this, SnakeGameActivity.class));
            });
        }

        if (cardFlappyBird != null) {
            cardFlappyBird.setOnClickListener(v -> {
                startActivity(new Intent(GamesGalleryActivity.this, FlappyBirdActivity.class));
            });
        }

        if (cardTetris != null) {
            cardTetris.setOnClickListener(v -> {
                startActivity(new Intent(GamesGalleryActivity.this, TetrisActivity.class));
            });
        }

        if (cardBreakout != null) {
            cardBreakout.setOnClickListener(v -> {
                startActivity(new Intent(GamesGalleryActivity.this, BreakoutActivity.class));
            });
        }

        // NEW: Maze Ball Launcher
        if (cardMazeBall != null) {
            cardMazeBall.setOnClickListener(v -> {
                startActivity(new Intent(GamesGalleryActivity.this, MazeBallActivity.class));
            });
        }
    }
}