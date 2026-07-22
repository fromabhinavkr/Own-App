package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

@SuppressLint("SetTextI18n")
public class BreakoutActivity extends AppCompatActivity {

    private boolean isDarkTheme;
    private int highScore = 0;
    private SharedPreferences prefs;

    private TextView tvCurrentScore, tvHighScore, tvFinalScore, tvTapToStart, tvGameOverTitle, tvNewHighScoreBanner;
    private RelativeLayout pauseOverlay, gameOverOverlay;
    private BreakoutEngine gameEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_breakout);

        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
        highScore = prefs.getInt("breakout_high_score", 0);

        View root = findViewById(R.id.breakoutRoot);
        FrameLayout gameContainer = findViewById(R.id.gameContainer);

        tvCurrentScore = findViewById(R.id.tvCurrentScore);
        tvHighScore = findViewById(R.id.tvHighScore);
        tvFinalScore = findViewById(R.id.tvFinalScore);
        tvTapToStart = findViewById(R.id.tvTapToStart);
        tvGameOverTitle = findViewById(R.id.tvGameOverTitle);
        tvNewHighScoreBanner = findViewById(R.id.tvNewHighScoreBanner);

        pauseOverlay = findViewById(R.id.pauseOverlay);
        gameOverOverlay = findViewById(R.id.gameOverOverlay);
        LinearLayout pauseCard = findViewById(R.id.pauseCard);
        LinearLayout gameOverCard = findViewById(R.id.gameOverCard);

        Button btnPause = findViewById(R.id.btnPause);
        Button btnResume = findViewById(R.id.btnResume);
        Button btnRestart = findViewById(R.id.btnRestart);
        Button btnQuit = findViewById(R.id.btnQuit);
        Button btnQuitFromPause = findViewById(R.id.btnQuitFromPause);

        // Apply Theming
        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
        int cardColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        int textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");

        root.setBackgroundColor(bgColor);
        tvCurrentScore.setTextColor(textColor);
        tvHighScore.setTextColor(textColor);
        tvTapToStart.setTextColor(textColor);
        tvHighScore.setText("Best: " + highScore);

        ((TextView) findViewById(R.id.tvPauseTitle)).setTextColor(textColor);
        tvGameOverTitle.setTextColor(textColor);
        tvFinalScore.setTextColor(textColor);

        btnQuit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA")));
        btnQuit.setTextColor(textColor);

        GradientDrawable gdCard = new GradientDrawable();
        gdCard.setColor(cardColor);
        gdCard.setCornerRadius(60f);
        pauseCard.setBackground(gdCard);
        gameOverCard.setBackground(gdCard);

        // Initialize Game Engine
        gameEngine = new BreakoutEngine(this, isDarkTheme);
        gameContainer.addView(gameEngine);

        // Callbacks
        gameEngine.setGameListener(new BreakoutEngine.GameListener() {
            @Override
            public void onScoreUpdated(int score) {
                tvCurrentScore.setText(String.valueOf(score));
            }

            @Override
            public void onGameOver(int finalScore) {
                tvGameOverTitle.setText("GAME OVER");
                tvGameOverTitle.setTextColor(isDarkTheme ? Color.WHITE : Color.parseColor("#333333"));

                if (finalScore > highScore && finalScore > 0) {
                    highScore = finalScore;
                    prefs.edit().putInt("breakout_high_score", highScore).apply();
                    tvHighScore.setText("Best: " + highScore);
                    tvNewHighScoreBanner.setVisibility(View.VISIBLE);
                } else {
                    tvNewHighScoreBanner.setVisibility(View.GONE);
                }

                tvFinalScore.setText("Score: " + finalScore);
                gameOverOverlay.setVisibility(View.VISIBLE);
                btnPause.setVisibility(View.GONE);
            }

            @Override
            public void onGameStarted() {
                tvTapToStart.setVisibility(View.GONE);
                btnPause.setVisibility(View.VISIBLE);
            }
        });

        // Buttons
        btnPause.setOnClickListener(v -> {
            gameEngine.pauseGame();
            pauseOverlay.setVisibility(View.VISIBLE);
            btnPause.setVisibility(View.GONE);
        });

        btnResume.setOnClickListener(v -> {
            pauseOverlay.setVisibility(View.GONE);
            btnPause.setVisibility(View.VISIBLE);
            gameEngine.resumeGame();
        });

        btnRestart.setOnClickListener(v -> {
            gameOverOverlay.setVisibility(View.GONE);
            tvNewHighScoreBanner.setVisibility(View.GONE);
            tvCurrentScore.setText("0");
            tvTapToStart.setVisibility(View.VISIBLE);
            btnPause.setVisibility(View.VISIBLE);
            gameEngine.resetGame();
        });

        btnQuit.setOnClickListener(v -> finish());
        btnQuitFromPause.setOnClickListener(v -> finish());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameEngine != null && gameEngine.isPlaying() && !gameEngine.isGameOver()) {
            findViewById(R.id.btnPause).performClick();
        }
    }

    // ==========================================
    // THE PHYSICS ENGINE & CANVAS RENDERER
    // ==========================================
    private static class BreakoutEngine extends View implements Choreographer.FrameCallback {

        private final Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paddlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint brickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float screenW, screenH;

        // Ball Physics
        private float ballX, ballY, ballRadius;
        private float ballDX, ballDY, baseSpeed;

        // Paddle Physics
        private float paddleX, paddleY, paddleW, paddleH;

        // Bricks
        private static final int BRICK_ROWS = 6;
        private static final int BRICK_COLS = 7;
        private boolean[][] bricks = new boolean[BRICK_ROWS][BRICK_COLS];
        private float brickW, brickH, brickPadding, brickOffsetTop;
        private int[] brickColors;

        private boolean playing = false, paused = false, gameOver = false;
        private int score = 0;
        private int bricksRemaining = 0;

        private GameListener listener;

        public interface GameListener {
            void onScoreUpdated(int score);
            void onGameOver(int finalScore);
            void onGameStarted();
        }

        public BreakoutEngine(Context context, boolean isDarkTheme) {
            super(context);
            ballPaint.setColor(isDarkTheme ? Color.WHITE : Color.parseColor("#333333"));
            paddlePaint.setColor(isDarkTheme ? Color.parseColor("#4A90E2") : Color.parseColor("#007AFF"));

            brickColors = new int[]{
                    Color.parseColor("#FF3B30"), // Red
                    Color.parseColor("#FF9500"), // Orange
                    Color.parseColor("#FFCC00"), // Yellow
                    Color.parseColor("#4CD964"), // Green
                    Color.parseColor("#5AC8FA"), // Light Blue
                    Color.parseColor("#5856D6")  // Purple
            };
        }

        public void setGameListener(GameListener listener) {
            this.listener = listener;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            screenW = w;
            screenH = h;

            // Dimensions
            ballRadius = screenW * 0.02f;
            paddleW = screenW * 0.25f;
            paddleH = screenH * 0.015f;
            paddleY = screenH - (screenH * 0.1f);

            brickPadding = screenW * 0.02f;
            brickW = (screenW - (brickPadding * (BRICK_COLS + 1))) / BRICK_COLS;
            brickH = screenH * 0.035f;
            brickOffsetTop = screenH * 0.15f; // Leave space for HUD

            resetGame();
        }

        public void resetGame() {
            score = 0;
            baseSpeed = screenH * 0.012f;
            initLevel();

            playing = false;
            paused = false;
            gameOver = false;
            invalidate();
        }

        private void initLevel() {
            // Reset paddle and ball
            paddleX = (screenW / 2f) - (paddleW / 2f);
            ballX = screenW / 2f;
            ballY = paddleY - ballRadius - 5f;

            // Randomize starting angle slightly
            ballDX = (float) (baseSpeed * (Math.random() > 0.5 ? 1 : -1) * 0.5f);
            ballDY = -baseSpeed;

            // Reset bricks
            bricksRemaining = BRICK_ROWS * BRICK_COLS;
            for (int r = 0; r < BRICK_ROWS; r++) {
                for (int c = 0; c < BRICK_COLS; c++) {
                    bricks[r][c] = true;
                }
            }
        }

        public void pauseGame() { paused = true; }
        public void resumeGame() { paused = false; Choreographer.getInstance().postFrameCallback(this); }
        public boolean isPlaying() { return playing; }
        public boolean isGameOver() { return gameOver; }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (gameOver || paused) return true;

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (!playing) {
                    playing = true;
                    if (listener != null) listener.onGameStarted();
                    Choreographer.getInstance().postFrameCallback(this);
                }
            }

            if (event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_DOWN) {
                // Drag the paddle (center it on finger)
                paddleX = event.getX() - (paddleW / 2f);
                // Clamp to screen bounds
                if (paddleX < 0) paddleX = 0;
                if (paddleX + paddleW > screenW) paddleX = screenW - paddleW;

                // If not playing yet, keep ball attached to paddle
                if (!playing) {
                    ballX = paddleX + (paddleW / 2f);
                    invalidate();
                }
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!playing || paused || gameOver) return;

            // 1. Move Ball
            ballX += ballDX;
            ballY += ballDY;

            // 2. Wall Collisions
            if (ballX - ballRadius < 0) { ballX = ballRadius; ballDX = -ballDX; } // Left Wall
            if (ballX + ballRadius > screenW) { ballX = screenW - ballRadius; ballDX = -ballDX; } // Right Wall
            if (ballY - ballRadius < 0) { ballY = ballRadius; ballDY = -ballDY; } // Ceiling

            // 3. Death (Fell through floor)
            if (ballY + ballRadius > screenH) {
                gameOver = true;
                playing = false;
                if (listener != null) listener.onGameOver(score);
                return;
            }

            // 4. Paddle Collision
            RectF ballRect = new RectF(ballX - ballRadius, ballY - ballRadius, ballX + ballRadius, ballY + ballRadius);
            RectF paddleRect = new RectF(paddleX, paddleY, paddleX + paddleW, paddleY + paddleH);

            if (ballDY > 0 && RectF.intersects(ballRect, paddleRect)) {
                // Dynamic bounce: hitting edges of paddle creates sharper angles
                float hitPoint = ballX - (paddleX + paddleW / 2f);
                float normalizedHit = hitPoint / (paddleW / 2f); // -1.0 (left) to 1.0 (right)

                ballDX = normalizedHit * (baseSpeed * 0.85f);
                ballDY = -baseSpeed;
                ballY = paddleY - ballRadius; // Pop it out of the paddle
            }

            // 5. Brick Collision
            boolean hitBrick = false;
            for (int r = 0; r < BRICK_ROWS && !hitBrick; r++) {
                for (int c = 0; c < BRICK_COLS && !hitBrick; c++) {
                    if (bricks[r][c]) {
                        float bx = c * (brickW + brickPadding) + brickPadding;
                        float by = r * (brickH + brickPadding) + brickOffsetTop;
                        RectF brickRect = new RectF(bx, by, bx + brickW, by + brickH);

                        if (RectF.intersects(ballRect, brickRect)) {
                            bricks[r][c] = false; // Destroy brick
                            hitBrick = true;
                            bricksRemaining--;

                            score += (BRICK_ROWS - r) * 10; // Higher bricks = more points
                            if (listener != null) listener.onScoreUpdated(score);

                            // Determine bounce direction (vertical vs horizontal hit)
                            boolean hitFromBottomOrTop = ballX > bx && ballX < bx + brickW;
                            if (hitFromBottomOrTop) {
                                ballDY = -ballDY;
                            } else {
                                ballDX = -ballDX;
                            }
                        }
                    }
                }
            }

            // 6. Level Complete Check (Endless Mode)
            if (bricksRemaining <= 0) {
                baseSpeed *= 1.15f; // Increase difficulty speed by 15%
                initLevel();
                playing = false; // Require tap to launch next wave
                if (listener != null) listener.onGameStarted(); // Hack to show "Tap to launch" state again
            }

            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);

            // Draw Bricks
            for (int r = 0; r < BRICK_ROWS; r++) {
                brickPaint.setColor(brickColors[r % brickColors.length]);
                for (int c = 0; c < BRICK_COLS; c++) {
                    if (bricks[r][c]) {
                        float bx = c * (brickW + brickPadding) + brickPadding;
                        float by = r * (brickH + brickPadding) + brickOffsetTop;
                        canvas.drawRoundRect(bx, by, bx + brickW, by + brickH, 8f, 8f, brickPaint);
                    }
                }
            }

            // Draw Paddle
            canvas.drawRoundRect(paddleX, paddleY, paddleX + paddleW, paddleY + paddleH, 16f, 16f, paddlePaint);

            // Draw Ball
            canvas.drawCircle(ballX, ballY, ballRadius, ballPaint);
        }
    }
}