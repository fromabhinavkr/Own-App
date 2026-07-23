package com.abhinav.ownapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

        View cardMazeBall = findViewById(R.id.cardMazeBall);
        TextView textMazeBall = findViewById(R.id.textMazeBall);

        // NEW: Link Play My Planet Views
        View cardMyPlanet = findViewById(R.id.cardMyPlanet);
        TextView textMyPlanet = findViewById(R.id.textMyPlanet);
        TextView btnHelpMyPlanet = findViewById(R.id.btnHelpMyPlanet);

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

        // NEW: Play My Planet Logic & Theme
        if (cardMyPlanet != null) {
            cardMyPlanet.setBackground(cardBg);
            textMyPlanet.setTextColor(textColor);

            // Programmatically draw the circular outline for the "?" icon
            GradientDrawable helpBg = new GradientDrawable();
            helpBg.setShape(GradientDrawable.OVAL);
            helpBg.setStroke(3, subTextColor);
            btnHelpMyPlanet.setTextColor(subTextColor);
            btnHelpMyPlanet.setBackground(helpBg);

            // Show instructions when the "?" is tapped
            btnHelpMyPlanet.setOnClickListener(v -> showInstructionsDialog(isDarkTheme));

            // Launch the game when the card is tapped
            cardMyPlanet.setOnClickListener(v -> {
                startActivity(new Intent(GamesGalleryActivity.this, GlobeGameActivity.class));
            });
        }
    }

    // --- NEW: Professional Instructions Pop-Up ---
    private void showInstructionsDialog(boolean isDarkTheme) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Dialog Theme Colors
        int bgColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        int textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#1C1C1E");
        int subTextColor = isDarkTheme ? Color.parseColor("#B0B0B8") : Color.parseColor("#666666");

        // Main Layout
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);

        GradientDrawable bgGd = new GradientDrawable();
        bgGd.setColor(bgColor);
        bgGd.setCornerRadius(50f); // Sleek rounded corners
        layout.setBackground(bgGd);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("How to Play");
        tvTitle.setTextSize(24f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(textColor);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, 40);

        // Instructions Body
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
        tvMessage.setTextColor(subTextColor);
        tvMessage.setLineSpacing(0, 1.3f);
        tvMessage.setPadding(0, 0, 0, 60);

        // "Got it!" Button
        Button btnGotIt = new Button(this);
        btnGotIt.setText("Got it!");
        btnGotIt.setTextColor(Color.WHITE);
        btnGotIt.setAllCaps(false);
        btnGotIt.setTextSize(16f);
        btnGotIt.setTypeface(null, Typeface.BOLD);

        GradientDrawable btnGd = new GradientDrawable();
        btnGd.setColor(Color.parseColor("#4A90E2")); // Standard App Blue
        btnGd.setCornerRadius(100f);
        btnGotIt.setBackground(btnGd);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 130);
        btnGotIt.setLayoutParams(btnLp);

        // Assemble Layout
        layout.addView(tvTitle);
        layout.addView(tvMessage);
        layout.addView(btnGotIt);

        builder.setView(layout);
        AlertDialog dialog = builder.create();

        // Make the square dialog background transparent so our rounded corners show
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Dismiss action
        btnGotIt.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}