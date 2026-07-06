package com.example.ownphotoonwall;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

public class GamesGalleryActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_games_gallery);

        // --- THEME SYNC LOGIC ---
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        int rootBgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
        int cardBgColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        int titleColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");
        int subtitleColor = isDarkTheme ? Color.parseColor("#8E8E93") : Color.parseColor("#888888");

        findViewById(R.id.gamesGalleryRoot).setBackgroundColor(rootBgColor);

        TextView tvTitle = findViewById(R.id.tvGamesTitle);
        tvTitle.setTextColor(titleColor);
        TextView tvSubtitle = findViewById(R.id.tvGamesSubtitle);
        tvSubtitle.setTextColor(subtitleColor);

        // Link and color the Snake Game Card
        LinearLayout cardSnakeGame = findViewById(R.id.cardSnakeGame);
        TextView textSnakeGame = findViewById(R.id.textSnakeGame);

        cardSnakeGame.setBackgroundTintList(ColorStateList.valueOf(cardBgColor));
        textSnakeGame.setTextColor(titleColor);

        // Launch the actual playable game!
        cardSnakeGame.setOnClickListener(v -> {
            startActivity(new Intent(GamesGalleryActivity.this, SnakeGameActivity.class));
        });
    }
}