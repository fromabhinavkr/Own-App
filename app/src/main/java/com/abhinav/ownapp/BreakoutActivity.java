package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
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

@SuppressWarnings("all")
@SuppressLint("SetTextI18n")
public class BreakoutActivity extends AppCompatActivity {

    private int themeState; // --- 3-STATE THEME VARIABLE ---
    private int highScore = 0;
    private boolean isVibrationEnabled = true;
    private SharedPreferences prefs;

    private TextView tvCurrentScore, tvHighScore, tvFinalScore, tvTapToStart, tvGameOverTitle, tvNewHighScoreBanner;
    private RelativeLayout pauseOverlay, gameOverOverlay;
    private BreakoutEngine gameEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_breakout);

        View root = findViewById(R.id.breakoutRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);

        // --- 3-STATE THEME SYNC LOGIC ---
        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = oldDark ? 1 : 0;
        }

        highScore = prefs.getInt("breakout_high_score", 0);
        isVibrationEnabled = prefs.getBoolean("breakout_vibration_enabled", true);

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
        Button btnToggleVibration = findViewById(R.id.btnToggleVibration);

        // Apply Theming strictly based on 3-State
        int bgColor, cardColor, textColor, subTextColor, quitBtnColor;

        if (themeState == 0) { // Light Mode
            bgColor = Color.parseColor("#F2F2F7");
            cardColor = Color.WHITE;
            textColor = Color.parseColor("#333333");
            subTextColor = Color.parseColor("#888888");
            quitBtnColor = Color.parseColor("#E5E5EA");
        } else if (themeState == 1) { // Standard Dark Mode
            bgColor = Color.parseColor("#1C1C1E");
            cardColor = Color.parseColor("#2C2C2E");
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#8E8E93");
            quitBtnColor = Color.parseColor("#3A3A3C");
        } else { // Star Mode (AMOLED Pure Black)
            bgColor = Color.parseColor("#000000"); // Pure AMOLED Black
            cardColor = Color.parseColor("#1C1C1E"); // Elevated dark gray
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#8E8E93");
            quitBtnColor = Color.parseColor("#2C2C2E");
        }

        root.setBackgroundColor(bgColor);
        tvCurrentScore.setTextColor(textColor);
        tvHighScore.setTextColor(subTextColor);
        tvTapToStart.setTextColor(textColor);
        tvHighScore.setText("Best: " + highScore);

        ((TextView) findViewById(R.id.tvPauseTitle)).setTextColor(textColor);
        tvGameOverTitle.setTextColor(textColor);
        tvFinalScore.setTextColor(textColor);

        btnQuit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(quitBtnColor));
        btnQuit.setTextColor(textColor);
        btnQuitFromPause.setBackgroundTintList(android.content.res.ColorStateList.valueOf(quitBtnColor));
        btnQuitFromPause.setTextColor(textColor);

        GradientDrawable gdCard = new GradientDrawable();
        gdCard.setColor(cardColor);
        gdCard.setCornerRadius(60f);
        pauseCard.setBackground(gdCard);
        gameOverCard.setBackground(gdCard);

        // Setup Vibration Toggle Button
        btnToggleVibration.setText("Vibration: " + (isVibrationEnabled ? "ON" : "OFF"));
        btnToggleVibration.setOnClickListener(v -> {
            isVibrationEnabled = !isVibrationEnabled;
            prefs.edit().putBoolean("breakout_vibration_enabled", isVibrationEnabled).apply();
            btnToggleVibration.setText("Vibration: " + (isVibrationEnabled ? "ON" : "OFF"));
            if (gameEngine != null) {
                gameEngine.setVibrationEnabled(isVibrationEnabled);
            }
        });

        // Initialize Game Engine with Theme State
        gameEngine = new BreakoutEngine(this, themeState);
        gameEngine.setVibrationEnabled(isVibrationEnabled);
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
                tvGameOverTitle.setTextColor(textColor);

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
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
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

        // Premium Texture Variables
        private BitmapShader lavaShader = null;
        private final Matrix textureMatrix = new Matrix();
        private float textureOffset = 0f;

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
        private boolean vibrationEnabled = true;
        private int score = 0;
        private int bricksRemaining = 0;

        private GameListener listener;

        public interface GameListener {
            void onScoreUpdated(int score);
            void onGameOver(int finalScore);
            void onGameStarted();
        }

        public BreakoutEngine(Context context, int themeState) {
            super(context);
            ballPaint.setColor(themeState == 0 ? Color.parseColor("#333333") : Color.WHITE);
            paddlePaint.setColor(themeState == 0 ? Color.parseColor("#007AFF") : Color.parseColor("#4A90E2"));

            brickColors = new int[]{
                    Color.parseColor("#FF3B30"), // Red
                    Color.parseColor("#FF9500"), // Orange
                    Color.parseColor("#FFCC00"), // Yellow
                    Color.parseColor("#4CD964"), // Green
                    Color.parseColor("#5AC8FA"), // Light Blue
                    Color.parseColor("#5856D6")  // Purple
            };

            // Attempt to load the physical lava texture if the user added it to their drawable folder
            try {
                @SuppressLint("DiscouragedApi") int lavaResId = getContext().getResources().getIdentifier("lava_texture", "drawable", getContext().getPackageName());
                if (lavaResId != 0) {
                    Bitmap rawLava = BitmapFactory.decodeResource(getContext().getResources(), lavaResId);
                    Bitmap scaledLava = Bitmap.createScaledBitmap(rawLava, 400, 400, true);
                    lavaShader = new BitmapShader(scaledLava, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                }
            } catch (Exception ignored) { }
        }

        public void setGameListener(GameListener listener) {
            this.listener = listener;
        }

        public void setVibrationEnabled(boolean enabled) {
            this.vibrationEnabled = enabled;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            screenW = w;
            screenH = h;

            // Dimensions dynamically adapted to layout bounds
            ballRadius = screenW * 0.02f;
            paddleW = screenW * 0.25f;
            paddleH = screenH * 0.015f;
            paddleY = screenH - (screenH * 0.1f);

            brickPadding = screenW * 0.02f;
            brickW = (screenW - (brickPadding * (BRICK_COLS + 1))) / BRICK_COLS;
            brickH = screenH * 0.035f;
            brickOffsetTop = screenH * 0.18f; // Leave space for HUD

            if (oldw == 0 || oldh == 0) {
                resetGame();
            } else {
                // If rotating device, elegantly scale the object positions so the game doesn't restart
                paddleX = (paddleX / oldw) * screenW;
                ballX = (ballX / oldw) * screenW;
                ballY = (ballY / oldh) * screenH;
                baseSpeed = screenH * 0.012f;
            }
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

            bricksRemaining = 0;

            // Randomly select a pattern (0 to 5) for different building shapes
            int patternType = (int) (Math.random() * 6);

            for (int r = 0; r < BRICK_ROWS; r++) {
                for (int c = 0; c < BRICK_COLS; c++) {
                    boolean isBrickActive = false;

                    switch (patternType) {
                        case 0: // Solid Rectangle (Classic)
                            isBrickActive = true;
                            break;
                        case 1: // Checkerboard
                            isBrickActive = (r + c) % 2 == 0;
                            break;
                        case 2: // Pyramid (Triangle pointing up)
                            isBrickActive = Math.abs(c - (BRICK_COLS / 2)) <= r;
                            break;
                        case 3: // Inverted Pyramid (Triangle pointing down)
                            isBrickActive = Math.abs(c - (BRICK_COLS / 2)) <= (BRICK_ROWS - 1 - r);
                            break;
                        case 4: // X-Shape
                            isBrickActive = (c == r) || (c == (BRICK_COLS - 1 - r));
                            break;
                        case 5: // Hollow Box
                            isBrickActive = (r == 0 || r == BRICK_ROWS - 1 || c == 0 || c == BRICK_COLS - 1);
                            break;
                    }

                    bricks[r][c] = isBrickActive;
                    if (isBrickActive) {
                        bricksRemaining++;
                    }
                }
            }

            // Failsafe: Ensure at least one brick spawns just in case of odd grid scaling
            if (bricksRemaining == 0) {
                bricks[0][0] = true;
                bricksRemaining = 1;
            }
        }

        public void pauseGame() { paused = true; }
        public void resumeGame() { paused = false; Choreographer.getInstance().postFrameCallback(this); }
        public boolean isPlaying() { return playing; }
        public boolean isGameOver() { return gameOver; }

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

            // Increment the texture offset to create the "flowing lava" animation effect
            textureOffset += (screenW * 0.003f);
            if (textureOffset > 1000f) textureOffset = 0f;

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
                triggerVibration(800); // Heavy Game Over vibration
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

                            triggerVibration(20); // Tiny tactile pop on breaking block

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

        // Helper to smoothly brighten/darken the block colors for the procedural plasma effect
        private int manipulateColor(int color, float factor) {
            int a = Color.alpha(color);
            int r = Math.round(Color.red(color) * factor);
            int g = Math.round(Color.green(color) * factor);
            int b = Math.round(Color.blue(color) * factor);
            return Color.argb(a, Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);

            // Shift texture matrix to animate the flowing lava
            textureMatrix.reset();
            textureMatrix.postTranslate(textureOffset, textureOffset * 0.5f);

            // Draw Bricks with moving Premium Texture
            for (int r = 0; r < BRICK_ROWS; r++) {
                int baseColor = brickColors[r % brickColors.length];

                if (lavaShader != null) {
                    // Physical Image Approach: Map the Lava image & tint it to the row's color
                    lavaShader.setLocalMatrix(textureMatrix);
                    brickPaint.setShader(lavaShader);
                    brickPaint.setColorFilter(new PorterDuffColorFilter(baseColor, PorterDuff.Mode.MULTIPLY));
                } else {
                    // Programmatic Fallback: Generate a stunning procedural moving "liquid plasma" gradient
                    int lightColor = manipulateColor(baseColor, 1.4f);
                    int darkColor = manipulateColor(baseColor, 0.6f);

                    LinearGradient fluidGradient = new LinearGradient(
                            0, textureOffset, brickW, brickH + textureOffset,
                            new int[]{darkColor, lightColor, baseColor, lightColor, darkColor},
                            null, Shader.TileMode.MIRROR
                    );
                    brickPaint.setShader(fluidGradient);
                    brickPaint.setColorFilter(null);
                }

                for (int c = 0; c < BRICK_COLS; c++) {
                    if (bricks[r][c]) {
                        float bx = c * (brickW + brickPadding) + brickPadding;
                        float by = r * (brickH + brickPadding) + brickOffsetTop;
                        canvas.drawRoundRect(bx, by, bx + brickW, by + brickH, 12f, 12f, brickPaint);
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