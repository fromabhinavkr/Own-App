package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Random;

@SuppressWarnings("all")
@SuppressLint("SetTextI18n")
public class TetrisActivity extends AppCompatActivity {

    private int highScore = 0;
    private SharedPreferences prefs;
    private TetrisEngine gameEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Smooth opening animation for the Activity
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_tetris);

        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);

        // --- 3-STATE THEME SYNC LOGIC ---
        int themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = oldDark ? 1 : 0;
        }

        highScore = prefs.getInt("tetris_high_score", 0);
        boolean isVibrationEnabled = prefs.getBoolean("tetris_vibration_enabled", true);

        View rootLayout = findViewById(R.id.tetrisRoot);
        RelativeLayout topHUD = findViewById(R.id.topHUD);

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (topHUD != null) {
                topHUD.setPadding(
                        topHUD.getPaddingLeft(),
                        insets.top + (int) (8 * getResources().getDisplayMetrics().density),
                        topHUD.getPaddingRight(),
                        topHUD.getPaddingBottom()
                );
            }
            return WindowInsetsCompat.CONSUMED;
        });

        FrameLayout gameContainer = findViewById(R.id.gameContainer);
        FrameLayout nextShapeContainer = findViewById(R.id.nextShapeContainer);

        TextView tvCurrentScore = findViewById(R.id.tvCurrentScore);
        TextView tvHighScore = findViewById(R.id.tvHighScore);
        TextView tvFinalScore = findViewById(R.id.tvFinalScore);
        TextView tvTapToStart = findViewById(R.id.tvTapToStart);
        TextView tvGameOverTitle = findViewById(R.id.tvGameOverTitle);
        TextView tvNewHighScoreBanner = findViewById(R.id.tvNewHighScoreBanner);
        TextView tvNextLabel = findViewById(R.id.tvNextLabel);

        RelativeLayout pauseOverlay = findViewById(R.id.pauseOverlay);
        RelativeLayout gameOverOverlay = findViewById(R.id.gameOverOverlay);
        LinearLayout pauseCard = findViewById(R.id.pauseCard);
        LinearLayout gameOverCard = findViewById(R.id.gameOverCard);

        // Modern Pill Bindings
        LinearLayout btnPause = findViewById(R.id.btnPause);
        LinearLayout nextBlockPill = findViewById(R.id.nextBlockPill);
        FrameLayout pauseIconContainer = findViewById(R.id.pauseIconContainer);

        GameIconView pauseIconView = new GameIconView(this);
        pauseIconContainer.addView(pauseIconView);

        Button btnResume = findViewById(R.id.btnResume);
        Button btnRestart = findViewById(R.id.btnRestart);
        Button btnQuit = findViewById(R.id.btnQuit);
        Button btnQuitFromPause = findViewById(R.id.btnQuitFromPause);
        Button btnToggleVibration = findViewById(R.id.btnToggleVibration);

        // Apply Theming strictly based on 3-State
        int bgColor, cardColor, textColor, quitBtnColor;
        int pillBgColor, boxBgColor, pauseTextColor, iconColor;

        if (themeState == 0) { // Light Mode
            bgColor = Color.WHITE;
            cardColor = Color.parseColor("#F2F2F7");
            textColor = Color.parseColor("#333333");
            quitBtnColor = Color.parseColor("#E5E5EA");

            pillBgColor = Color.parseColor("#F0F0F0");
            boxBgColor = Color.WHITE;
            iconColor = Color.parseColor("#333333");
            pauseTextColor = Color.parseColor("#333333");
        } else if (themeState == 1) { // Standard Dark Mode
            bgColor = Color.parseColor("#1C1C1E");
            cardColor = Color.parseColor("#2C2C2E");
            textColor = Color.WHITE;
            quitBtnColor = Color.parseColor("#3A3A3C");

            pillBgColor = Color.parseColor("#2D313A");
            boxBgColor = Color.parseColor("#D8E2FF");
            iconColor = Color.parseColor("#001C3A");
            pauseTextColor = Color.parseColor("#E3E2E6");
        } else { // Star Mode (AMOLED Pure Black)
            bgColor = Color.parseColor("#000000");
            cardColor = Color.parseColor("#1C1C1E");
            textColor = Color.WHITE;
            quitBtnColor = Color.parseColor("#2C2C2E");

            pillBgColor = Color.parseColor("#2D313A");
            boxBgColor = Color.parseColor("#D8E2FF");
            iconColor = Color.parseColor("#001C3A");
            pauseTextColor = Color.parseColor("#E3E2E6");
        }

        rootLayout.setBackgroundColor(bgColor);
        tvCurrentScore.setTextColor(textColor);
        tvNextLabel.setTextColor(pauseTextColor);
        tvTapToStart.setTextColor(textColor);

        tvHighScore.setTextColor(themeState == 0 ? Color.parseColor("#888888") : Color.parseColor("#A0A0A5"));
        tvHighScore.setText("Best: " + highScore);

        ((TextView) findViewById(R.id.tvPauseTitle)).setTextColor(textColor);
        tvGameOverTitle.setTextColor(textColor);
        tvFinalScore.setTextColor(textColor);

        btnQuit.setBackgroundTintList(ColorStateList.valueOf(quitBtnColor));
        btnQuit.setTextColor(textColor);
        if (btnQuitFromPause != null) {
            btnQuitFromPause.setBackgroundTintList(ColorStateList.valueOf(quitBtnColor));
            btnQuitFromPause.setTextColor(textColor);
        }

        GradientDrawable gdCard = new GradientDrawable();
        gdCard.setColor(cardColor);
        gdCard.setCornerRadius(60f);
        pauseCard.setBackground(gdCard);
        gameOverCard.setBackground(gdCard);

        if (btnPause != null) {
            btnPause.setBackground(createPillShape(pillBgColor));
            pauseIconContainer.setBackground(createBoxShape(boxBgColor));
            pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
        }
        if (nextBlockPill != null) {
            nextBlockPill.setBackground(createPillShape(pillBgColor));
        }

        // Extremely safe logic for the Vibration Button
        if (btnToggleVibration != null) {
            btnToggleVibration.setText("Vibration: " + (isVibrationEnabled ? "ON" : "OFF"));
            btnToggleVibration.setOnClickListener(v -> {
                boolean current = prefs.getBoolean("tetris_vibration_enabled", true);
                prefs.edit().putBoolean("tetris_vibration_enabled", !current).apply();
                btnToggleVibration.setText("Vibration: " + (!current ? "ON" : "OFF"));
                if (gameEngine != null) {
                    gameEngine.setVibrationEnabled(!current);
                }
            });
        }

        // Initialize Engines
        NextShapeView nextShapeView = new NextShapeView(this);
        nextShapeContainer.addView(nextShapeView);

        gameEngine = new TetrisEngine(this, themeState, nextShapeView);
        gameEngine.setVibrationEnabled(isVibrationEnabled);
        gameContainer.addView(gameEngine);

        topHUD.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (gameEngine != null) {
                    gameEngine.setTopHUDHeight(topHUD.getHeight());
                }
            }
        });

        // Callbacks
        gameEngine.setGameListener(new TetrisEngine.GameListener() {
            @Override
            public void onScoreUpdated(int score) {
                tvCurrentScore.setText("Score: " + score);
            }

            @Override
            public void onGameOver(int finalScore) {
                tvGameOverTitle.setText("GAME OVER");
                tvGameOverTitle.setTextColor(textColor);

                if (finalScore > highScore && finalScore > 0) {
                    highScore = finalScore;
                    prefs.edit().putInt("tetris_high_score", highScore).apply();
                    tvHighScore.setText("Best: " + highScore);
                    tvNewHighScoreBanner.setVisibility(View.VISIBLE);
                } else {
                    tvNewHighScoreBanner.setVisibility(View.GONE);
                }

                tvFinalScore.setText("Score: " + finalScore);
                showOverlaySmoothly(gameOverOverlay);
                fadeOutHudSmoothly(btnPause, nextBlockPill);
            }

            @Override
            public void onGameStarted() {
                tvTapToStart.setVisibility(View.GONE);
            }
        });

        // Buttons
        if (btnPause != null) {
            btnPause.setOnClickListener(v -> {
                gameEngine.pauseGame();
                showOverlaySmoothly(pauseOverlay);
                fadeOutHudSmoothly(btnPause, nextBlockPill);
                pauseIconView.setIcon(GameIconView.ICON_PLAY, iconColor);
            });
        }

        if (btnResume != null) {
            btnResume.setOnClickListener(v -> {
                hideOverlaySmoothly(pauseOverlay);
                fadeInHudSmoothly(btnPause, nextBlockPill);
                gameEngine.resumeGame();
                pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
            });
        }

        if (btnRestart != null) {
            btnRestart.setOnClickListener(v -> {
                hideOverlaySmoothly(gameOverOverlay);
                tvNewHighScoreBanner.setVisibility(View.GONE);
                tvCurrentScore.setText("Score: 0");
                tvTapToStart.setVisibility(View.VISIBLE);
                fadeInHudSmoothly(btnPause, nextBlockPill);
                pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
                gameEngine.resetGame();
            });
        }

        if (btnQuit != null) btnQuit.setOnClickListener(v -> finish());
        if (btnQuitFromPause != null) btnQuitFromPause.setOnClickListener(v -> finish());
    }

    // --- INTERCEPT BACK BUTTON FOR PAUSE AND EXACT EXIT LOGIC ---
    @Override
    public void onBackPressed() {
        RelativeLayout pauseOverlay = findViewById(R.id.pauseOverlay);
        RelativeLayout gameOverOverlay = findViewById(R.id.gameOverOverlay);

        // If on Game Over screen, back button exits
        if (gameOverOverlay != null && gameOverOverlay.getVisibility() == View.VISIBLE) {
            finish();
            return;
        }

        // If on Pause Menu, back button EXITS the game completely (2nd press logic)
        if (pauseOverlay != null && pauseOverlay.getVisibility() == View.VISIBLE) {
            finish();
            return;
        }

        // If currently playing, back button PAUSES the game (1st press logic)
        if (gameEngine != null && gameEngine.isPlaying() && !gameEngine.isGameOver()) {
            LinearLayout btnPause = findViewById(R.id.btnPause);
            if (btnPause != null) {
                btnPause.performClick();
                return;
            }
        }

        super.onBackPressed();
    }

    // --- SMOOTH FADE ANIMATION HELPERS ---
    private void showOverlaySmoothly(View overlay) {
        if (overlay == null) return;
        overlay.setAlpha(0f);
        overlay.setVisibility(View.VISIBLE);
        overlay.animate().alpha(1f).setDuration(250).start();
    }

    private void hideOverlaySmoothly(View overlay) {
        if (overlay == null) return;
        overlay.animate().alpha(0f).setDuration(250).withEndAction(() -> overlay.setVisibility(View.GONE)).start();
    }

    private void fadeOutHudSmoothly(View leftPill, View rightPill) {
        if (leftPill != null) leftPill.animate().alpha(0f).setDuration(250).withEndAction(() -> leftPill.setVisibility(View.INVISIBLE)).start();
        if (rightPill != null) rightPill.animate().alpha(0f).setDuration(250).withEndAction(() -> rightPill.setVisibility(View.INVISIBLE)).start();
    }

    private void fadeInHudSmoothly(View leftPill, View rightPill) {
        if (leftPill != null) {
            leftPill.setAlpha(0f);
            leftPill.setVisibility(View.VISIBLE);
            leftPill.animate().alpha(1f).setDuration(250).start();
        }
        if (rightPill != null) {
            rightPill.setAlpha(0f);
            rightPill.setVisibility(View.VISIBLE);
            rightPill.animate().alpha(1f).setDuration(250).start();
        }
    }

    @Override
    public void finish() {
        super.finish();
        // Uses standard fade_in and fade_out for a perfectly smooth closing animation
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // --- DRAWABLE FACTORIES ---
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

    // --- BUG FIX: DO NOT AUTO-CLICK PAUSE IF ALREADY PAUSED ---
    @Override
    protected void onPause() {
        super.onPause();
        RelativeLayout pauseOverlay = findViewById(R.id.pauseOverlay);
        boolean isPauseMenuVisible = (pauseOverlay != null && pauseOverlay.getVisibility() == View.VISIBLE);

        // Only trigger the pause click if the game is actively playing AND the menu is not already visible
        if (gameEngine != null && gameEngine.isPlaying() && !gameEngine.isGameOver() && !isPauseMenuVisible) {
            View btnPause = findViewById(R.id.btnPause);
            if (btnPause != null) {
                btnPause.performClick();
            }
        }
    }

    // --- MATHEMATICAL VECTOR ICON ENGINE ---
    public static class GameIconView extends View {
        public static final int ICON_PAUSE = 0;
        public static final int ICON_PLAY = 1;

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
        }
    }

    // ==========================================
    // TETRIS DATA STRUCTURES
    // ==========================================
    private static final int[][][] TETROMINOES = {
            {{1, 1, 1, 1}}, // I (Cyan)
            {{1, 1}, {1, 1}}, // O (Yellow)
            {{0, 1, 0}, {1, 1, 1}}, // T (Purple)
            {{1, 0, 0}, {1, 1, 1}}, // L (Orange)
            {{0, 0, 1}, {1, 1, 1}}, // J (Blue)
            {{0, 1, 1}, {1, 1, 0}}, // S (Green)
            {{1, 1, 0}, {0, 1, 1}}  // Z (Red)
    };

    private static final int[] COLORS = {
            Color.TRANSPARENT,
            Color.parseColor("#00BCD4"), // Cyan
            Color.parseColor("#FFEB3B"), // Yellow
            Color.parseColor("#9C27B0"), // Purple
            Color.parseColor("#FF9800"), // Orange
            Color.parseColor("#2196F3"), // Blue
            Color.parseColor("#4CAF50"), // Green
            Color.parseColor("#F44336")  // Red
    };

    // ==========================================
    // NEXT SHAPE PREVIEW CANVAS
    // ==========================================
    private static class NextShapeView extends View {
        private int[][] shape;
        private int color;
        private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final RectF tempRect = new RectF();

        public NextShapeView(Context context) {
            super(context);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Color.argb(100, 0, 0, 0));
            strokePaint.setStrokeWidth(2f); // Thinner stroke for smaller preview block
        }

        public void updateShape(int typeId) {
            this.shape = TETROMINOES[typeId];
            this.color = COLORS[typeId + 1];
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (shape == null) return;

            int rows = shape.length;
            int cols = shape[0].length;
            float cellSize = Math.min(getWidth() / 4f, getHeight() / 4f);

            float startX = (getWidth() - (cols * cellSize)) / 2f;
            float startY = (getHeight() - (rows * cellSize)) / 2f;

            blockPaint.setColor(color);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (shape[r][c] == 1) {
                        tempRect.set(startX + c * cellSize, startY + r * cellSize,
                                startX + (c + 1) * cellSize, startY + (r + 1) * cellSize);
                        canvas.drawRect(tempRect, blockPaint);
                        canvas.drawRect(tempRect, strokePaint);
                    }
                }
            }
        }
    }

    // ==========================================
    // MAIN GAME ENGINE & RENDERER
    // ==========================================
    private static class TetrisEngine extends View {

        private final int ROWS = 20;
        private final int COLS = 10;
        private final int[][] board = new int[ROWS][COLS];

        private int[][] currentPiece;
        private int currentType, currentX, currentY;
        private int nextType;

        private float screenW, cellSize;
        private float boardTop, boardLeft, boardBottom;
        private float dynamicTopPadding = 250f; // Default fallback

        private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint();
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final RectF tempRect = new RectF();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Random random = new Random();
        private GameListener listener;
        private final NextShapeView nextShapeView;

        private boolean playing = false, paused = false, gameOver = false;
        private boolean vibrationEnabled = true;
        private int score = 0;

        private float startX, startY;
        private boolean movedDuringTouch = false;
        private long touchStartTime;

        public interface GameListener {
            void onScoreUpdated(int score);
            void onGameOver(int finalScore);
            void onGameStarted();
        }

        public TetrisEngine(Context context, int themeState, NextShapeView nextShapeView) {
            super(context);
            this.nextShapeView = nextShapeView;

            gridPaint.setStyle(Paint.Style.STROKE);
            gridPaint.setColor(themeState == 0 ? Color.argb(30, 0, 0, 0) : Color.argb(30, 255, 255, 255));
            gridPaint.setStrokeWidth(2f);

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(8f);
            borderPaint.setColor(themeState == 0 ? Color.parseColor("#333333") : Color.WHITE);
        }

        public void setGameListener(GameListener listener) { this.listener = listener; }

        public void setVibrationEnabled(boolean enabled) { this.vibrationEnabled = enabled; }

        private void triggerVibration(int duration) {
            if (vibrationEnabled) {
                Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(duration);
                    }
                }
            }
        }

        public void setTopHUDHeight(int heightInPixels) {
            if (this.dynamicTopPadding == heightInPixels) return;

            this.dynamicTopPadding = heightInPixels;

            if (getWidth() > 0 && getHeight() > 0) {
                onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
                invalidate();
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            screenW = w;
            float screenH = h;

            float availableHeight = screenH - dynamicTopPadding - 30f; // reduced bottom buffer
            cellSize = Math.min(screenW / COLS, availableHeight / ROWS);

            boardLeft = (screenW - (COLS * cellSize)) / 2f;
            boardTop = dynamicTopPadding + 8f;
            boardBottom = boardTop + (ROWS * cellSize);

            if (oldW == 0 && oldH == 0) {
                resetGame();
            }
        }

        public void resetGame() {
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    board[r][c] = 0;
                }
            }
            score = 0;
            playing = false;
            paused = false;
            gameOver = false;

            nextType = random.nextInt(TETROMINOES.length);
            spawnPiece();
            invalidate();
        }

        private void spawnPiece() {
            currentType = nextType;
            currentPiece = TETROMINOES[currentType];
            currentX = COLS / 2 - currentPiece[0].length / 2;
            currentY = 0;

            nextType = random.nextInt(TETROMINOES.length);
            if (nextShapeView != null) nextShapeView.updateShape(nextType);

            if (!isValidMove(currentPiece, currentX, currentY)) {
                gameOver = true;
                playing = false;
                handler.removeCallbacks(gameLoop);
                triggerVibration(500); // Game Over vibration
                if (listener != null) listener.onGameOver(score);
            }
        }

        public void pauseGame() { paused = true; handler.removeCallbacks(gameLoop); }
        public void resumeGame() { paused = false; handler.postDelayed(gameLoop, getDelay()); }
        public boolean isPlaying() { return playing; }
        public boolean isGameOver() { return gameOver; }

        private long getDelay() {
            return 500L;
        }

        private final Runnable gameLoop = new Runnable() {
            @Override
            public void run() {
                if (playing && !paused && !gameOver) {
                    if (isValidMove(currentPiece, currentX, currentY + 1)) {
                        currentY++;
                    } else {
                        lockPiece();
                    }
                    invalidate();
                    handler.postDelayed(this, getDelay());
                }
            }
        };

        private boolean isValidMove(int[][] shape, int x, int y) {
            for (int r = 0; r < shape.length; r++) {
                for (int c = 0; c < shape[0].length; c++) {
                    if (shape[r][c] != 0) {
                        int boardX = x + c;
                        int boardY = y + r;
                        if (boardX < 0 || boardX >= COLS || boardY >= ROWS || (boardY >= 0 && board[boardY][boardX] != 0)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private void lockPiece() {
            for (int r = 0; r < currentPiece.length; r++) {
                for (int c = 0; c < currentPiece[0].length; c++) {
                    if (currentPiece[r][c] != 0 && currentY + r >= 0) {
                        board[currentY + r][currentX + c] = currentType + 1;
                    }
                }
            }
            triggerVibration(15); // Soft click when piece locks
            clearLines();
            spawnPiece();
        }

        private void clearLines() {
            int linesCleared = 0;
            for (int r = ROWS - 1; r >= 0; r--) {
                boolean full = true;
                for (int c = 0; c < COLS; c++) {
                    if (board[r][c] == 0) { full = false; break; }
                }
                if (full) {
                    linesCleared++;
                    for (int moveR = r; moveR > 0; moveR--) {
                        System.arraycopy(board[moveR - 1], 0, board[moveR], 0, COLS);
                    }
                    for (int c = 0; c < COLS; c++) { board[0][c] = 0; }
                    r++; // Re-check the shifted row
                }
            }
            if (linesCleared > 0) {
                triggerVibration(100); // Stronger satisfying buzz for lines cleared
                score += linesCleared;
                if (listener != null) listener.onScoreUpdated(score);
            }
        }

        private void rotatePiece() {
            int rows = currentPiece.length;
            int cols = currentPiece[0].length;
            int[][] rotated = new int[cols][rows];
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    rotated[c][rows - 1 - r] = currentPiece[r][c];
                }
            }
            if (isValidMove(rotated, currentX, currentY)) {
                currentPiece = rotated;
                invalidate();
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (gameOver || paused) return true;

            if (!playing) {
                playing = true;
                if (listener != null) listener.onGameStarted();
                handler.postDelayed(gameLoop, getDelay());
                return true;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    movedDuringTouch = false;
                    touchStartTime = System.currentTimeMillis();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - startX;
                    float dy = event.getY() - startY;

                    if (Math.abs(dx) > cellSize) {
                        int steps = (int) (dx / cellSize);
                        if (steps > 0 && isValidMove(currentPiece, currentX + 1, currentY)) {
                            currentX++; startX += cellSize; movedDuringTouch = true;
                        } else if (steps < 0 && isValidMove(currentPiece, currentX - 1, currentY)) {
                            currentX--; startX -= cellSize; movedDuringTouch = true;
                        }
                        invalidate();
                    }

                    // Soft Drop
                    if (dy > cellSize) {
                        if (isValidMove(currentPiece, currentX, currentY + 1)) {
                            currentY++; startY += cellSize; movedDuringTouch = true;
                        }
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    long duration = System.currentTimeMillis() - touchStartTime;
                    if (!movedDuringTouch && duration < 250) {
                        rotatePiece(); // Fast tap to rotate
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);

            // 1. Draw Clean Outer Border
            canvas.drawRect(boardLeft, boardTop, boardLeft + (COLS * cellSize), boardBottom, borderPaint);

            // 2. Draw Board Background and Grid
            canvas.drawRect(boardLeft, boardTop, boardLeft + (COLS * cellSize), boardBottom, gridPaint);
            for (int i = 1; i < COLS; i++) canvas.drawLine(boardLeft + i * cellSize, boardTop, boardLeft + i * cellSize, boardBottom, gridPaint);
            for (int i = 1; i < ROWS; i++) canvas.drawLine(boardLeft, boardTop + i * cellSize, boardLeft + (COLS * cellSize), boardTop + i * cellSize, gridPaint);

            // 3. Draw Settled Blocks
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    if (board[r][c] != 0) {
                        drawBlock(canvas, c, r, COLORS[board[r][c]]);
                    }
                }
            }

            // 4. Draw Current Falling Piece
            if (currentPiece != null) {
                for (int r = 0; r < currentPiece.length; r++) {
                    for (int c = 0; c < currentPiece[0].length; c++) {
                        if (currentPiece[r][c] != 0 && currentY + r >= 0) {
                            drawBlock(canvas, currentX + c, currentY + r, COLORS[currentType + 1]);
                        }
                    }
                }
            }
        }

        private void drawBlock(Canvas canvas, int c, int r, int color) {
            tempRect.set(boardLeft + c * cellSize, boardTop + r * cellSize,
                    boardLeft + (c + 1) * cellSize, boardTop + (r + 1) * cellSize);
            blockPaint.setColor(color);
            blockPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(tempRect, blockPaint);

            blockPaint.setColor(Color.argb(100, 0,0,0));
            blockPaint.setStyle(Paint.Style.STROKE);
            blockPaint.setStrokeWidth(4f);
            canvas.drawRect(tempRect, blockPaint);
        }
    }
}