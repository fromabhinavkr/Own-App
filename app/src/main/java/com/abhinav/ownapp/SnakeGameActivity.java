package com.abhinav.ownapp;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.ArrayList;
import java.util.Random;

@SuppressWarnings("all")
public class SnakeGameActivity extends AppCompatActivity {

    private SnakeGameEngine gameEngine;

    // Updated UI components
    private LinearLayout btnPause;
    private FrameLayout pauseIconContainer;
    private GameIconView pauseIconView;
    private TextView tvScore, tvBestScore;
    private LinearLayout topUiBar;

    // Track dynamic speed icon
    private GameIconView closedSpeedIconView;
    private GameIconView openSpeedIconView;
    private int currentSpeedIcon = GameIconView.ICON_RUN;

    // --- 3-STATE THEME VARIABLES ---
    private int themeState; // 0 = Light, 1 = Dark, 2 = Star

    // Extracted colors for icon swapping
    private int iconColor;

    private static final int SPEED_EASY = 220;
    private static final int SPEED_MEDIUM = 130;
    private static final int SPEED_HARD = 70;
    private static final int SPEED_MAX_PINK = 40;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Smooth opening animation for the Activity
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_snake_game);
        View rootLayout = findViewById(R.id.snake_root);
        topUiBar = findViewById(R.id.top_ui_bar);

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (topUiBar != null) {
                topUiBar.setPadding(
                        topUiBar.getPaddingLeft(),
                        insets.top + (int) (8 * getResources().getDisplayMetrics().density),
                        topUiBar.getPaddingRight(),
                        topUiBar.getPaddingBottom()
                );
            }
            return WindowInsetsCompat.CONSUMED;
        });

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);

        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = oldDark ? 1 : 0;
        }

        // Initialize UI bindings
        btnPause = findViewById(R.id.btnPause);
        pauseIconContainer = findViewById(R.id.pauseIconContainer);
        tvScore = findViewById(R.id.tvScore);
        tvBestScore = findViewById(R.id.tvBestScore);

        // Inject custom Vector Icon Views
        pauseIconView = new GameIconView(this);
        pauseIconContainer.addView(pauseIconView);

        int pillBgColor;
        int boxBgColor;
        int textColor;

        // Apply Custom Theme Colors perfectly matching Android 12+ Quick Settings
        if (themeState == 0) { // Light Mode
            pillBgColor = Color.parseColor("#E5E5EA");
            boxBgColor = Color.parseColor("#FFFFFF");
            iconColor = Color.parseColor("#333333");
            textColor = Color.parseColor("#333333");
            rootLayout.setBackgroundColor(Color.parseColor("#FFFFFF"));
        } else { // Dark Mode / Star Mode
            pillBgColor = Color.parseColor("#2D313A");
            boxBgColor = Color.parseColor("#D8E2FF");
            iconColor = Color.parseColor("#001C3A");
            textColor = Color.parseColor("#E3E2E6");
            rootLayout.setBackgroundColor(themeState == 1 ? Color.parseColor("#1C1C1E") : Color.parseColor("#000000"));
        }

        if (btnPause != null) {
            btnPause.setBackground(createPillShape(pillBgColor));
            pauseIconContainer.setBackground(createBoxShape(boxBgColor));
            pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
        }
        if (tvScore != null) {
            tvScore.setTextColor(textColor);
        }
        if (tvBestScore != null) {
            tvBestScore.setTextColor(themeState == 0 ? Color.parseColor("#666666") : Color.parseColor("#A0A0A5"));
        }

        FrameLayout container = findViewById(R.id.game_container);
        gameEngine = new SnakeGameEngine(this, themeState, prefs);
        container.addView(gameEngine);

        topUiBar.post(() -> {
            if (gameEngine != null) {
                gameEngine.setTopUiHeight(topUiBar.getHeight());
            }
        });

        btnPause.setOnClickListener(v -> {
            boolean isNowPaused = gameEngine.togglePause();
            pauseIconView.setIcon(isNowPaused ? GameIconView.ICON_PLAY : GameIconView.ICON_PAUSE, iconColor);
        });

        showGameModeDialog(prefs);
    }

    // --- SMOOTH CLOSING ANIMATION ---
    @Override
    public void finish() {
        super.finish();
        // Uses 0 for enter anim and fade_out for exit to completely eliminate the white flash!
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    // --- INTERCEPT BACK BUTTON FOR PAUSE AND EXACT EXIT LOGIC ---
    @Override
    public void onBackPressed() {
        if (gameEngine == null) {
            super.onBackPressed();
            return;
        }

        // If waiting for mode selection or Game Over, back button exits
        if (gameEngine.isWaitingForMode() || gameEngine.isGameOver()) {
            finish();
            return;
        }

        // If on Pause Canvas, back button EXITS the game completely (2nd press logic)
        if (gameEngine.isPaused()) {
            finish();
            return;
        }

        // If currently playing, back button PAUSES the game (1st press logic)
        if (gameEngine.isPlaying() && !gameEngine.isPaused()) {
            if (btnPause != null) {
                btnPause.performClick();
            }
            return;
        }

        super.onBackPressed();
    }

    // --- BUG FIX: DO NOT AUTO-CLICK PAUSE IF ALREADY PAUSED ---
    @Override
    protected void onPause() {
        super.onPause();
        if (gameEngine != null && gameEngine.isPlaying() && !gameEngine.isGameOver() && !gameEngine.isPaused()) {
            if (btnPause != null) {
                btnPause.performClick();
            }
        }
    }

    private GradientDrawable createPillShape(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(1000f);
        return shape;
    }

    private GradientDrawable createBoxShape(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(30f);
        return shape;
    }

    @Override public void onConfigurationChanged(@NonNull Configuration newConfig) { super.onConfigurationChanged(newConfig); }

    private void showGameModeDialog(SharedPreferences prefs) {
        AlertDialog dialog = new AlertDialog.Builder(this).setCancelable(false).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        int popupBg = themeState == 0 ? Color.parseColor("#FFFFFF") : (themeState == 1 ? Color.parseColor("#2C2C2E") : Color.parseColor("#1C1C1E"));
        int pillBgColor = themeState == 0 ? Color.parseColor("#E5E5EA") : Color.parseColor("#2D313A");
        int boxBgColor = themeState == 0 ? Color.parseColor("#FFFFFF") : Color.parseColor("#D8E2FF");
        int titleColor = themeState == 0 ? Color.BLACK : Color.WHITE;
        int subTextColor = themeState == 0 ? Color.parseColor("#666666") : Color.parseColor("#A0A0A5");

        LinearLayout dialogRoot = new LinearLayout(this);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        dialogRoot.setPadding(60, 60, 60, 60);
        GradientDrawable dialogShape = new GradientDrawable();
        dialogShape.setColor(popupBg);
        dialogShape.setCornerRadius(60f);
        dialogRoot.setBackground(dialogShape);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Select Game Mode");
        tvTitle.setTextColor(titleColor);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, 50);
        dialogRoot.addView(tvTitle);

        int closedPlays = prefs.getInt("snake_plays_closed", 0);
        int closedHigh = prefs.getInt("snake_high_score_closed", 0);
        int openPlays = prefs.getInt("snake_plays_open", 0);
        int openHigh = prefs.getInt("snake_high_score_open", 0);

        closedSpeedIconView = new GameIconView(this);
        openSpeedIconView = new GameIconView(this);

        // Closed Mode Button with Speed Icon
        LinearLayout btnClosed = createModeButton("Closed Ground", "Played: " + closedPlays + "  |  Best: " + closedHigh,
                pillBgColor, boxBgColor, titleColor, subTextColor, GameIconView.ICON_CLOSED, closedSpeedIconView);
        btnClosed.setOnClickListener(v -> {
            prefs.edit().putInt("snake_plays_closed", closedPlays + 1).apply();
            gameEngine.setGameMode(false);
            dialog.dismiss();
        });
        dialogRoot.addView(btnClosed);

        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 30));
        dialogRoot.addView(space);

        // Open Mode Button with Speed Icon
        LinearLayout btnOpen = createModeButton("Open Ground", "Played: " + openPlays + "  |  Best: " + openHigh,
                pillBgColor, boxBgColor, titleColor, subTextColor, GameIconView.ICON_OPEN, openSpeedIconView);
        btnOpen.setOnClickListener(v -> {
            prefs.edit().putInt("snake_plays_open", openPlays + 1).apply();
            gameEngine.setGameMode(true);
            dialog.dismiss();
        });
        dialogRoot.addView(btnOpen);

        dialog.setView(dialogRoot);
        // Exits smoothly if user dismisses mode selection dialog via system back button
        dialog.setOnCancelListener(d -> finish());
        dialog.show();
    }

    private LinearLayout createModeButton(String title, String subtitle, int pillColor, int boxColor, int titleColor, int subColor, int iconType, GameIconView speedIconView) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setBackground(createPillShape(pillColor));
        button.setPadding(20, 20, 20, 20);
        button.setClickable(true);
        button.setFocusable(true);

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setLayoutParams(new LinearLayout.LayoutParams(110, 110));
        iconBox.setBackground(createBoxShape(boxColor));
        GameIconView iconView = new GameIconView(this);
        iconView.setIcon(iconType, iconColor);
        iconBox.addView(iconView);
        button.addView(iconBox);

        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(30, 0, 30, 0);
        textContainer.setLayoutParams(params);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(titleColor);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextColor(subColor);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        textContainer.addView(tvTitle);
        textContainer.addView(tvSub);
        button.addView(textContainer);

        // Add Speed settings icon inside the mode button
        FrameLayout speedIconBox = new FrameLayout(this);
        speedIconBox.setLayoutParams(new LinearLayout.LayoutParams(110, 110));
        speedIconBox.setBackground(createBoxShape(boxColor));
        speedIconView.setIcon(currentSpeedIcon, iconColor);
        speedIconBox.addView(speedIconView);
        button.addView(speedIconBox);

        // Clicking the speed icon opens difficulty menu, preventing mode selection click
        speedIconBox.setOnClickListener(v -> showDifficultyDialog());

        return button;
    }

    private void showDifficultyDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_difficulty, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LinearLayout dialogRoot = dialogView.findViewById(R.id.dialogRoot);
        TextView tvTitle = dialogView.findViewById(R.id.tvTitle);
        Button btnEasy = dialogView.findViewById(R.id.btnEasy);
        Button btnMedium = dialogView.findViewById(R.id.btnMedium);
        Button btnHard = dialogView.findViewById(R.id.btnHard);

        int popupBg = themeState == 0 ? Color.parseColor("#FFFFFF") : (themeState == 1 ? Color.parseColor("#2C2C2E") : Color.parseColor("#1C1C1E"));
        int btnBgColor = themeState == 0 ? Color.parseColor("#333333") : Color.parseColor("#E5E5EA");
        int btnTextColor = themeState == 0 ? Color.WHITE : Color.BLACK;
        int titleColor = themeState == 0 ? Color.BLACK : Color.WHITE;

        GradientDrawable dialogShape = new GradientDrawable();
        dialogShape.setColor(popupBg);
        dialogShape.setCornerRadius(60f);
        if (dialogRoot != null) dialogRoot.setBackground(dialogShape);

        if (tvTitle != null) tvTitle.setTextColor(titleColor);

        Button[] buttons = {btnEasy, btnMedium, btnHard};
        for (Button b : buttons) {
            if (b != null) {
                b.setBackgroundTintList(null);
                b.setBackground(createPillShape(btnBgColor));
                b.setTextColor(btnTextColor);
            }
        }

        if (btnEasy != null) btnEasy.setOnClickListener(v -> {
            currentSpeedIcon = GameIconView.ICON_WALK;
            gameEngine.setSpeed(SPEED_EASY);
            updateSpeedIcons();
            dialog.dismiss();
        });
        if (btnMedium != null) btnMedium.setOnClickListener(v -> {
            currentSpeedIcon = GameIconView.ICON_RUN;
            gameEngine.setSpeed(SPEED_MEDIUM);
            updateSpeedIcons();
            dialog.dismiss();
        });
        if (btnHard != null) btnHard.setOnClickListener(v -> {
            currentSpeedIcon = GameIconView.ICON_TURBO;
            gameEngine.setSpeed(SPEED_HARD);
            updateSpeedIcons();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void updateSpeedIcons() {
        if (closedSpeedIconView != null) closedSpeedIconView.setIcon(currentSpeedIcon, iconColor);
        if (openSpeedIconView != null) openSpeedIconView.setIcon(currentSpeedIcon, iconColor);
    }

    // --- MATHEMATICAL VECTOR ICON ENGINE ---
    public static class GameIconView extends View {
        public static final int ICON_PAUSE = 0;
        public static final int ICON_PLAY = 1;
        public static final int ICON_WALK = 2;
        public static final int ICON_RUN = 3;
        public static final int ICON_TURBO = 4;
        public static final int ICON_CLOSED = 5;
        public static final int ICON_OPEN = 6;
        public static final int ICON_SCORE = 7;

        private int iconType = ICON_PAUSE;
        private Paint paint;

        public GameIconView(Context context) { super(context); init(); }

        private void init() {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        public void setIcon(int type, int color) {
            this.iconType = type;
            paint.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;

            if (iconType == ICON_PAUSE) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(cx - 7f, cy - 8f, cx - 2f, cy + 8f, 3f, 3f, paint);
                canvas.drawRoundRect(cx + 2f, cy - 8f, cx + 7f, cy + 8f, 3f, 3f, paint);
            }
            else if (iconType == ICON_PLAY) {
                paint.setStyle(Paint.Style.FILL);
                Path p = new Path();
                p.moveTo(cx - 4f, cy - 9f);
                p.lineTo(cx + 7f, cy);
                p.lineTo(cx - 4f, cy + 9f);
                p.close();
                canvas.drawPath(p, paint);
            }
            // Enlarge and thicken the Speed Icons for a Premium look
            else if (iconType == ICON_WALK) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(6.0f);
                canvas.drawCircle(cx, cy - 16f, 4.5f, paint);
                canvas.drawLine(cx, cy - 11f, cx, cy + 6f, paint);
                canvas.drawLine(cx, cy - 6f, cx - 10f, cy + 6f, paint);
                canvas.drawLine(cx, cy - 6f, cx + 10f, cy + 6f, paint);
                canvas.drawLine(cx, cy + 6f, cx - 7f, cy + 20f, paint);
                canvas.drawLine(cx, cy + 6f, cx + 7f, cy + 20f, paint);
            }
            else if (iconType == ICON_RUN) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(6.0f);
                canvas.drawCircle(cx + 6f, cy - 16f, 4.5f, paint);
                canvas.drawLine(cx + 2f, cy - 11f, cx - 3f, cy + 5f, paint);
                canvas.drawLine(cx, cy - 4f, cx + 13f, cy - 1f, paint);
                canvas.drawLine(cx, cy - 4f, cx - 13f, cy - 8f, paint);
                canvas.drawLine(cx - 3f, cy + 5f, cx - 13f, cy + 16f, paint);
                canvas.drawLine(cx - 3f, cy + 5f, cx + 8f, cy + 13f, paint);
                canvas.drawLine(cx + 8f, cy + 13f, cx + 8f, cy + 22f, paint);
            }
            else if (iconType == ICON_TURBO) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(6.0f);
                canvas.drawCircle(cx + 12f, cy - 13f, 4.5f, paint);
                canvas.drawLine(cx + 7f, cy - 8f, cx - 8f, cy + 7f, paint);
                canvas.drawLine(cx, cy - 1f, cx + 15f, cy + 5f, paint);
                canvas.drawLine(cx, cy - 1f, cx - 16f, cy - 11f, paint);
                canvas.drawLine(cx - 8f, cy + 7f, cx - 20f, cy + 12f, paint);
                canvas.drawLine(cx - 8f, cy + 7f, cx + 7f, cy + 18f, paint);

                paint.setStrokeWidth(3.5f); // Keep motion lines slightly thinner for visual speed effect
                canvas.drawLine(cx - 24f, cy - 12f, cx - 13f, cy - 12f, paint);
                canvas.drawLine(cx - 27f, cy + 2f, cx - 16f, cy + 2f, paint);
                canvas.drawLine(cx - 21f, cy + 16f, cx - 8f, cy + 16f, paint);
            }
            else if (iconType == ICON_CLOSED) {
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawRect(cx - 12f, cy - 12f, cx + 12f, cy + 12f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, 4f, paint);
            }
            else if (iconType == ICON_OPEN) {
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(cx - 12f, cy - 12f, cx + 12f, cy - 12f, paint);
                canvas.drawLine(cx - 12f, cy + 12f, cx + 12f, cy + 12f, paint);

                canvas.drawLine(cx - 8f, cy, cx + 8f, cy, paint);
                canvas.drawLine(cx + 2f, cy - 5f, cx + 8f, cy, paint);
                canvas.drawLine(cx + 2f, cy + 5f, cx + 8f, cy, paint);
            }
            else if (iconType == ICON_SCORE) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(4.5f);
                canvas.drawCircle(cx, cy, 7f, paint);
            }
        }
    }

    private class SnakeGameEngine extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayList<Point> snake = new ArrayList<>();
        private Point apple;
        private final Random random = new Random();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final SharedPreferences prefs;

        private int direction = 0;
        private int nextDirection = 0;
        private boolean isGameOver = false;
        private boolean isPaused = false;
        private boolean isOpenGroundMode = false;
        private boolean isWaitingForMode = true;

        // --- SCORE VARIABLES ---
        private int score = 0;
        private int currentModeHighScore = 0;
        private boolean isNewHighScore = false;

        private int currentSpeed = SPEED_MEDIUM;
        private int gridCols = 20;
        private int gridRows = 20;
        private int blockSize = 30;
        private int topUiHeightPixels = 0;

        private final int bgColor;
        private final int normalSnakeColor;
        private final int appleColor;
        private final int textColor;

        // --- AUTO-PILOT PINK POWER-UP VARIABLES ---
        private Point pinkApple;
        private long pinkAppleSpawnTime = 0;
        private boolean isPinkMode = false;
        private int redApplesEatenForPink = 0; // Strict tracker for spawning the Pink Apple every 5th regular point!
        private int redApplesEatenInPinkMode = 0;

        // Animation variables
        private int currentSnakeColor;
        private float currentGlowRadius = 0f;
        private ValueAnimator colorAnimator;
        private final int PINK_POWER_COLOR = Color.parseColor("#FF007F");

        private float startX, startY;
        private final RectF itemRect = new RectF();

        public SnakeGameEngine(Context context, int themeState, SharedPreferences preferences) {
            super(context);
            this.prefs = preferences;

            if (themeState == 0) { // Light
                bgColor = Color.parseColor("#FFFFFF");
                normalSnakeColor = Color.parseColor("#000000");
                appleColor = Color.parseColor("#FF3B30");
                textColor = Color.BLACK;
            } else if (themeState == 1) { // Dark
                bgColor = Color.parseColor("#1C1C1E");
                normalSnakeColor = Color.parseColor("#FFFFFF");
                appleColor = Color.parseColor("#FF3B30");
                textColor = Color.WHITE;
            } else { // Star
                bgColor = Color.parseColor("#000000");
                normalSnakeColor = Color.parseColor("#FFFFFF");
                appleColor = Color.parseColor("#FF3B30");
                textColor = Color.WHITE;
            }

            this.currentSnakeColor = normalSnakeColor;
            this.isWaitingForMode = true;
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        // Getters for Activity back-button logic
        public boolean isPaused() { return isPaused; }
        public boolean isPlaying() { return !isWaitingForMode && !isGameOver; }
        public boolean isWaitingForMode() { return isWaitingForMode; }
        public boolean isGameOver() { return isGameOver; }

        public void setTopUiHeight(int height) {
            this.topUiHeightPixels = height;
        }

        public void setSpeed(int speed) { this.currentSpeed = speed; }

        public void setGameMode(boolean openGround) {
            this.isOpenGroundMode = openGround;
            this.isWaitingForMode = false;

            // Set High Score specifically for the chosen mode
            if (openGround) {
                currentModeHighScore = prefs.getInt("snake_high_score_open", 0);
            } else {
                currentModeHighScore = prefs.getInt("snake_high_score_closed", 0);
            }

            updateScoreUI();
            initGame();
        }

        public boolean togglePause() {
            if (isWaitingForMode || isGameOver) return false;
            isPaused = !isPaused;
            if (!isPaused) {
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(gameLoop, isPinkMode ? SPEED_MAX_PINK : currentSpeed);
            }
            invalidate();
            return isPaused;
        }

        private void initGame() {
            snake.clear(); snake.add(new Point(5, 5)); snake.add(new Point(4, 5)); snake.add(new Point(3, 5));
            direction = 0; nextDirection = 0; score = 0; isGameOver = false; isPaused = false; isNewHighScore = false;

            isPinkMode = false;
            pinkApple = null;
            redApplesEatenForPink = 0;
            redApplesEatenInPinkMode = 0;
            currentSnakeColor = normalSnakeColor;
            currentGlowRadius = 0f;
            if (colorAnimator != null) colorAnimator.cancel();

            if (pauseIconView != null) pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);

            updateScoreUI();
            spawnApple();

            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(gameLoop, currentSpeed);
        }

        private void updateScoreUI() {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (tvScore != null) tvScore.setText("Score: " + score);
                if (tvBestScore != null) tvBestScore.setText("Best: " + currentModeHighScore);
            });
        }

        private void spawnApple() {
            int maxCol = Math.max(1, gridCols);
            int maxRow = Math.max(1, gridRows);

            int topOffset = 0;
            if (blockSize > 0 && topUiHeightPixels > 0) {
                topOffset = (int) Math.ceil(topUiHeightPixels / (double) blockSize) + 1;
            }
            if (topOffset >= maxRow) topOffset = maxRow / 4;

            int x, y;
            boolean valid = false;
            int safetyCounter = 0;
            while (!valid && safetyCounter < 500) {
                x = random.nextInt(maxCol);
                y = random.nextInt(Math.max(1, maxRow - topOffset)) + topOffset;
                valid = true;
                if (pinkApple != null && pinkApple.x == x && pinkApple.y == y) valid = false;
                for (Point p : snake) { if (p.x == x && p.y == y) valid = false; }
                if (valid) apple = new Point(x, y);
                safetyCounter++;
            }
        }

        private void spawnPinkApple() {
            int maxCol = Math.max(1, gridCols);
            int maxRow = Math.max(1, gridRows);

            int topOffset = 0;
            if (blockSize > 0 && topUiHeightPixels > 0) {
                topOffset = (int) Math.ceil(topUiHeightPixels / (double) blockSize) + 1;
            }
            if (topOffset >= maxRow) topOffset = maxRow / 4;

            int x, y;
            boolean valid = false;
            int attempts = 0;
            while (!valid && attempts < 50) {
                x = random.nextInt(maxCol);
                y = random.nextInt(Math.max(1, maxRow - topOffset)) + topOffset;
                valid = true;
                if (apple != null && apple.x == x && apple.y == y) valid = false;
                for (Point p : snake) { if (p.x == x && p.y == y) valid = false; }
                if (valid) {
                    pinkApple = new Point(x, y);
                    pinkAppleSpawnTime = System.currentTimeMillis();
                }
                attempts++;
            }
        }

        private void animateSnakeColor(int fromColor, int toColor, float fromGlow, float toGlow) {
            if (colorAnimator != null) colorAnimator.cancel();
            colorAnimator = ValueAnimator.ofFloat(0f, 1f);
            colorAnimator.setDuration(400);
            colorAnimator.addUpdateListener(anim -> {
                float fraction = anim.getAnimatedFraction();
                currentSnakeColor = (int) new ArgbEvaluator().evaluate(fraction, fromColor, toColor);
                currentGlowRadius = fromGlow + (toGlow - fromGlow) * fraction;
                invalidate();
            });
            colorAnimator.start();
        }

        private final Runnable gameLoop = new Runnable() {
            @Override public void run() {
                if (!isWaitingForMode && !isGameOver) {
                    if (!isPaused) {
                        if (pinkApple != null && System.currentTimeMillis() - pinkAppleSpawnTime > 3000) {
                            pinkApple = null;
                        }

                        if (isPinkMode) {
                            autoPilotTowardsApple();
                        }

                        moveSnake();
                        checkCollisions();
                    }
                    invalidate();
                    handler.postDelayed(this, isPinkMode ? SPEED_MAX_PINK : currentSpeed);
                }
            }
        };

        private void autoPilotTowardsApple() {
            Point head = snake.get(0);
            Point target = apple;
            if (target == null) return;

            int forbiddenDir = (direction + 2) % 4;
            int bestDir = direction;
            int minDistance = Integer.MAX_VALUE;

            ArrayList<Integer> safeDirs = new ArrayList<>();

            for (int d = 0; d < 4; d++) {
                if (d == forbiddenDir) continue;

                Point nextStep = new Point(head.x, head.y);
                switch (d) {
                    case 0: nextStep.x++; break;
                    case 1: nextStep.y++; break;
                    case 2: nextStep.x--; break;
                    case 3: nextStep.y--; break;
                }

                if (isOpenGroundMode || isPinkMode) {
                    if (nextStep.x < 0) nextStep.x = gridCols - 1;
                    else if (nextStep.x >= gridCols) nextStep.x = 0;
                    if (nextStep.y < 0) nextStep.y = gridRows - 1;
                    else if (nextStep.y >= gridRows) nextStep.y = 0;
                } else {
                    if (nextStep.x < 0 || nextStep.x >= gridCols || nextStep.y < 0 || nextStep.y >= gridRows) {
                        continue;
                    }
                }

                boolean hitsBody = false;
                for (int i = 0; i < snake.size() - 1; i++) {
                    if (snake.get(i).x == nextStep.x && snake.get(i).y == nextStep.y) {
                        hitsBody = true;
                        break;
                    }
                }

                if (hitsBody) continue;
                safeDirs.add(d);

                int dist;
                if (isOpenGroundMode || isPinkMode) {
                    int dx = Math.abs(nextStep.x - target.x);
                    int dy = Math.abs(nextStep.y - target.y);
                    dx = Math.min(dx, gridCols - dx);
                    dy = Math.min(dy, gridRows - dy);
                    dist = dx + dy;
                } else {
                    dist = Math.abs(nextStep.x - target.x) + Math.abs(nextStep.y - target.y);
                }

                if (dist < minDistance) {
                    minDistance = dist;
                    bestDir = d;
                }
            }

            if (minDistance == Integer.MAX_VALUE && !safeDirs.isEmpty()) {
                bestDir = safeDirs.get(0);
            }

            nextDirection = bestDir;
        }

        private void moveSnake() {
            direction = nextDirection; Point head = snake.get(0); Point newHead = new Point(head.x, head.y);
            switch (direction) { case 0: newHead.x++; break; case 1: newHead.y++; break; case 2: newHead.x--; break; case 3: newHead.y--; break; }

            if (isOpenGroundMode || isPinkMode) {
                if (newHead.x < 0) newHead.x = gridCols - 1; else if (newHead.x >= gridCols) newHead.x = 0;
                if (newHead.y < 0) newHead.y = gridRows - 1; else if (newHead.y >= gridRows) newHead.y = 0;
            }

            snake.add(0, newHead);

            // Did we eat the regular RED apple?
            if (apple != null && newHead.x == apple.x && newHead.y == apple.y) {
                // REGULAR RED APPLES ARE NOW WORTH EXACTLY 1 POINT
                score += 1;
                if (score > currentModeHighScore) currentModeHighScore = score;
                updateScoreUI();
                spawnApple();

                if (!isPinkMode) {
                    redApplesEatenForPink++;
                }

                // EXACTLY EVERY 5th POINT: Spawn the Pink Powerup Apple
                if (redApplesEatenForPink >= 5 && !isPinkMode && pinkApple == null) {
                    spawnPinkApple();
                    redApplesEatenForPink = 0; // Reset counter. If missed, it takes exactly 5 more regular apples to try again!
                }

                if (isPinkMode) {
                    redApplesEatenInPinkMode++;
                    if (redApplesEatenInPinkMode >= 5) {
                        isPinkMode = false;
                        animateSnakeColor(currentSnakeColor, normalSnakeColor, currentGlowRadius, 0f);
                    }
                }
            }
            // Did we eat the PINK power-up?
            else if (pinkApple != null && newHead.x == pinkApple.x && newHead.y == pinkApple.y) {
                score += 5; // Pink Apple gives a flat 5 points bonus
                if (score > currentModeHighScore) currentModeHighScore = score;
                updateScoreUI();
                pinkApple = null;
                isPinkMode = true;
                redApplesEatenInPinkMode = 0;
                redApplesEatenForPink = 0; // Failsafe counter reset
                animateSnakeColor(currentSnakeColor, PINK_POWER_COLOR, currentGlowRadius, 25f);
            }
            else {
                snake.remove(snake.size() - 1);
            }
        }

        private void checkCollisions() {
            if (isPinkMode) return;

            Point head = snake.get(0);
            if (!isOpenGroundMode) {
                if (head.x < 0 || head.x >= gridCols || head.y < 0 || head.y >= gridRows) {
                    triggerGameOver(); return;
                }
            }
            for (int i = 1; i < snake.size(); i++) {
                if (head.x == snake.get(i).x && head.y == snake.get(i).y) { triggerGameOver(); return; }
            }
        }

        private void triggerGameOver() {
            isGameOver = true;
            if (score > currentModeHighScore) {
                currentModeHighScore = score;
                isNewHighScore = true;
            }
            if (isOpenGroundMode) {
                prefs.edit().putInt("snake_high_score_open", currentModeHighScore).apply();
            } else {
                prefs.edit().putInt("snake_high_score_closed", currentModeHighScore).apply();
            }
            updateScoreUI();
        }

        @Override protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            blockSize = Math.max(1, Math.min(w, h) / 20);
            gridCols = Math.max(10, w / blockSize);
            gridRows = Math.max(10, h / blockSize);
            if (!isWaitingForMode && apple != null && (apple.x >= gridCols || apple.y >= gridRows)) spawnApple();
            for (Point p : snake) { if (p.x >= gridCols) p.x = gridCols - 1; if (p.y >= gridRows) p.y = gridRows - 1; }
        }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas); canvas.drawColor(bgColor);
            if (isWaitingForMode) { return; }

            paint.clearShadowLayer();

            if (apple != null) {
                paint.setColor(appleColor);
                itemRect.set(apple.x * blockSize + 2, apple.y * blockSize + 2, (apple.x + 1) * blockSize - 2, (apple.y + 1) * blockSize - 2);
                canvas.drawRoundRect(itemRect, 16f, 16f, paint);
            }

            if (pinkApple != null) {
                paint.setColor(PINK_POWER_COLOR);
                paint.setShadowLayer(15f, 0, 0, PINK_POWER_COLOR);
                itemRect.set(pinkApple.x * blockSize + 2, pinkApple.y * blockSize + 2, (pinkApple.x + 1) * blockSize - 2, (pinkApple.y + 1) * blockSize - 2);

                Path applePath = new Path();
                float cx = itemRect.centerX();
                float cy = itemRect.centerY();
                float r = itemRect.width() / 2f;

                applePath.moveTo(cx, cy - r * 0.5f);
                applePath.cubicTo(cx + r * 1.2f, cy - r * 1.2f, cx + r * 1.2f, cy + r * 0.8f, cx, cy + r * 0.9f);
                applePath.cubicTo(cx - r * 1.2f, cy + r * 0.8f, cx - r * 1.2f, cy - r * 1.2f, cx, cy - r * 0.5f);

                applePath.moveTo(cx, cy - r * 0.5f);
                applePath.quadTo(cx + r * 0.5f, cy - r * 1.2f, cx + r * 0.8f, cy - r * 1.0f);
                applePath.quadTo(cx + r * 0.4f, cy - r * 0.6f, cx, cy - r * 0.5f);

                canvas.drawPath(applePath, paint);
                paint.clearShadowLayer();
            }

            paint.setColor(currentSnakeColor);
            if (currentGlowRadius > 0f) {
                paint.setShadowLayer(currentGlowRadius, 0, 0, currentSnakeColor);
            }
            for (Point p : snake) {
                itemRect.set(p.x * blockSize + 2, p.y * blockSize + 2, (p.x + 1) * blockSize - 2, (p.y + 1) * blockSize - 2);
                canvas.drawRoundRect(itemRect, 16f, 16f, paint);
            }
            paint.clearShadowLayer();

            if (isPaused && !isGameOver) {
                // Dimming overlay to ensure crisp contrast
                paint.setColor(themeState == 0 ? Color.argb(210, 255, 255, 255) : Color.argb(210, 0, 0, 0));
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

                paint.setColor(textColor); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(100f);
                canvas.drawText("PAUSED", getWidth() / 2f, getHeight() / 2f, paint); paint.setTextAlign(Paint.Align.LEFT);
            }
            if (isGameOver) {
                // Dimming overlay to ensure crisp contrast
                paint.setColor(themeState == 0 ? Color.argb(210, 255, 255, 255) : Color.argb(210, 0, 0, 0));
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

                paint.setColor(textColor); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(100f);
                canvas.drawText("GAME OVER", getWidth() / 2f, getHeight() / 2f - 120f, paint); paint.setTextSize(60f);
                canvas.drawText("Score: " + score, getWidth() / 2f, getHeight() / 2f - 20f, paint); canvas.drawText("High Score: " + currentModeHighScore, getWidth() / 2f, getHeight() / 2f + 60f, paint);
                if (isNewHighScore) { paint.setColor(Color.parseColor("#4CD964")); paint.setTextSize(65f); canvas.drawText("🏆 New High Score! 🏆", getWidth() / 2f, getHeight() / 2f + 160f, paint); paint.setColor(textColor); }
                paint.setTextSize(45f); paint.setFakeBoldText(false); canvas.drawText("Tap anywhere to Restart", getWidth() / 2f, getHeight() / 2f + 280f, paint); paint.setTextAlign(Paint.Align.LEFT);
            }
        }

        @Override public boolean performClick() { return super.performClick(); }

        @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
            if (isWaitingForMode) return true;
            if (isGameOver && event.getAction() == MotionEvent.ACTION_DOWN) { initGame(); return true; }
            if (isPaused) return true;

            if (isPinkMode) return true;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: startX = event.getX(); startY = event.getY(); return true;
                case MotionEvent.ACTION_UP:
                    performClick(); float endX = event.getX(); float endY = event.getY(); float dx = endX - startX; float dy = endY - startY;
                    if (Math.abs(dx) > 50 || Math.abs(dy) > 50) {
                        if (Math.abs(dx) > Math.abs(dy)) { if (dx > 0 && direction != 2) nextDirection = 0; else if (dx < 0 && direction != 0) nextDirection = 2; }
                        else { if (dy > 0 && direction != 3) nextDirection = 1; else if (dy < 0 && direction != 1) nextDirection = 3; }
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }
}