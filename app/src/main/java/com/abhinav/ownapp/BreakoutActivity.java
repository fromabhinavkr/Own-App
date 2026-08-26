package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("all")
@SuppressLint("SetTextI18n")
public class BreakoutActivity extends AppCompatActivity {

    private int themeState; // --- 3-STATE THEME VARIABLE ---
    private int highScore = 0;
    private boolean isVibrationEnabled = true;
    private boolean isMusicEnabled = true; // --- NEW MUSIC FLAG ---
    private int currentDifficulty = 0; // 0 = Easy, 1 = Medium, 2 = Difficult
    private SharedPreferences prefs;

    private TextView tvScore, tvBestScore, tvFinalScore, tvTapToStart, tvGameOverTitle, tvNewHighScoreBanner;
    private RelativeLayout pauseOverlay, gameOverOverlay;
    private BreakoutEngine gameEngine;

    // --- NEW UI COMPONENTS FOR PAUSE PILL ---
    private LinearLayout btnPause;
    private FrameLayout pauseIconContainer;
    private GameIconView pauseIconView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Smooth opening animation for the Activity
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_breakout);

        View root = findViewById(R.id.breakoutRoot);
        RelativeLayout topHUD = findViewById(R.id.topHud);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
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

        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);

        // --- 3-STATE THEME SYNC LOGIC ---
        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = oldDark ? 1 : 0;
        }

        highScore = prefs.getInt("breakout_high_score", 0);
        isVibrationEnabled = prefs.getBoolean("breakout_vibration_enabled", true);
        isMusicEnabled = prefs.getBoolean("breakout_music_enabled", true); // Fetch music state
        currentDifficulty = prefs.getInt("breakout_difficulty", 0); // Default to Easy

        FrameLayout gameContainer = findViewById(R.id.gameContainer);

        tvScore = findViewById(R.id.tvScore);
        tvBestScore = findViewById(R.id.tvBestScore);
        tvFinalScore = findViewById(R.id.tvFinalScore);
        tvTapToStart = findViewById(R.id.tvTapToStart);
        tvGameOverTitle = findViewById(R.id.tvGameOverTitle);
        tvNewHighScoreBanner = findViewById(R.id.tvNewHighScoreBanner);

        pauseOverlay = findViewById(R.id.pauseOverlay);
        gameOverOverlay = findViewById(R.id.gameOverOverlay);
        LinearLayout pauseCard = findViewById(R.id.pauseCard);
        LinearLayout gameOverCard = findViewById(R.id.gameOverCard);

        // Modern Pill Bindings
        btnPause = findViewById(R.id.btnPause);
        pauseIconContainer = findViewById(R.id.pauseIconContainer);

        pauseIconView = new GameIconView(this);
        pauseIconContainer.addView(pauseIconView);

        Button btnResume = findViewById(R.id.btnResume);
        Button btnRestart = findViewById(R.id.btnRestart);
        Button btnQuit = findViewById(R.id.btnQuit);
        Button btnQuitFromPause = findViewById(R.id.btnQuitFromPause);
        Button btnToggleVibration = findViewById(R.id.btnToggleVibration);
        Button btnToggleMusic = findViewById(R.id.btnToggleMusic); // Bind new music button
        Button btnToggleSpeed = findViewById(R.id.btnToggleSpeed);

        // Apply Theming strictly based on 3-State
        int bgColor, cardColor, textColor, subTextColor, quitBtnColor;
        int pillBgColor, boxBgColor, iconColor, pauseTextColor;

        if (themeState == 0) { // Light Mode
            bgColor = Color.parseColor("#FFFFFF");
            cardColor = Color.parseColor("#F2F2F7");
            textColor = Color.parseColor("#333333");
            subTextColor = Color.parseColor("#888888");
            quitBtnColor = Color.parseColor("#E5E5EA");

            pillBgColor = Color.parseColor("#F2F2F7");
            boxBgColor = Color.parseColor("#FFFFFF");
            iconColor = Color.parseColor("#333333");
            pauseTextColor = Color.parseColor("#333333");
        } else if (themeState == 1) { // Standard Dark Mode
            bgColor = Color.parseColor("#1C1C1E");
            cardColor = Color.parseColor("#2C2C2E");
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#8E8E93");
            quitBtnColor = Color.parseColor("#3A3A3C");

            pillBgColor = Color.parseColor("#2D313A");
            boxBgColor = Color.parseColor("#D8E2FF");
            iconColor = Color.parseColor("#001C3A");
            pauseTextColor = Color.parseColor("#E3E2E6");
        } else { // Star Mode (AMOLED Pure Black)
            bgColor = Color.parseColor("#000000");
            cardColor = Color.parseColor("#1C1C1E");
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#8E8E93");
            quitBtnColor = Color.parseColor("#2C2C2E");

            pillBgColor = Color.parseColor("#2D313A");
            boxBgColor = Color.parseColor("#D8E2FF");
            iconColor = Color.parseColor("#001C3A");
            pauseTextColor = Color.parseColor("#E3E2E6");
        }

        root.setBackgroundColor(bgColor);
        tvTapToStart.setTextColor(textColor);

        tvScore.setTextColor(textColor);
        tvBestScore.setTextColor(themeState == 0 ? Color.parseColor("#666666") : Color.parseColor("#A0A0A5"));
        tvBestScore.setText("Best: " + highScore);

        ((TextView) findViewById(R.id.tvPauseTitle)).setTextColor(textColor);
        tvGameOverTitle.setTextColor(textColor);
        tvFinalScore.setTextColor(textColor);

        btnQuit.setBackgroundTintList(ColorStateList.valueOf(quitBtnColor));
        btnQuit.setTextColor(textColor);
        if (btnQuitFromPause != null) {
            btnQuitFromPause.setBackgroundTintList(ColorStateList.valueOf(quitBtnColor));
            btnQuitFromPause.setTextColor(textColor);
            btnQuitFromPause.setText("Exit Game");
        }

        // ========================================================
        // UNIQUE DRAWABLES FIX FOR MENUS
        // ========================================================
        if (pauseCard != null) {
            GradientDrawable gdPause = new GradientDrawable();
            gdPause.setColor(cardColor);
            gdPause.setCornerRadius(60f);
            pauseCard.setBackground(gdPause);

            ViewGroup.LayoutParams lp = pauseCard.getLayoutParams();
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                pauseCard.setLayoutParams(lp);
            }
        }

        if (gameOverCard != null) {
            GradientDrawable gdGameOver = new GradientDrawable();
            gdGameOver.setColor(cardColor);
            gdGameOver.setCornerRadius(60f);
            gameOverCard.setBackground(gdGameOver);

            ViewGroup.LayoutParams lp = gameOverCard.getLayoutParams();
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                gameOverCard.setLayoutParams(lp);
            }
        }
        // ========================================================

        if (btnPause != null) {
            btnPause.setBackground(createPillShape(pillBgColor));
            pauseIconContainer.setBackground(createBoxShape(boxBgColor));
            pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
        }

        if (btnToggleVibration != null) {
            btnToggleVibration.setText("Vib: " + (isVibrationEnabled ? "ON" : "OFF"));
            btnToggleVibration.setOnClickListener(v -> {
                isVibrationEnabled = !isVibrationEnabled;
                prefs.edit().putBoolean("breakout_vibration_enabled", isVibrationEnabled).apply();
                btnToggleVibration.setText("Vib: " + (isVibrationEnabled ? "ON" : "OFF"));
                if (gameEngine != null) {
                    gameEngine.setVibrationEnabled(isVibrationEnabled);
                }
            });
        }

        // --- MUSIC BUTTON LOGIC ---
        if (btnToggleMusic != null) {
            btnToggleMusic.setText("Music: " + (isMusicEnabled ? "ON" : "OFF"));
            btnToggleMusic.setOnClickListener(v -> {
                isMusicEnabled = !isMusicEnabled;
                prefs.edit().putBoolean("breakout_music_enabled", isMusicEnabled).apply();
                btnToggleMusic.setText("Music: " + (isMusicEnabled ? "ON" : "OFF"));
                if (gameEngine != null) {
                    gameEngine.setMusicEnabled(isMusicEnabled);
                }
            });
        }

        // --- SPEED BUTTON LOGIC ---
        if (btnToggleSpeed != null) {
            updateSpeedButtonText(btnToggleSpeed);
            btnToggleSpeed.setOnClickListener(v -> {
                currentDifficulty = (currentDifficulty + 1) % 3;
                prefs.edit().putInt("breakout_difficulty", currentDifficulty).apply();
                updateSpeedButtonText(btnToggleSpeed);
                if (gameEngine != null) {
                    gameEngine.setDifficulty(currentDifficulty);
                }
            });
        }

        // Initialize Game Engine
        gameEngine = new BreakoutEngine(this, themeState);
        gameEngine.setVibrationEnabled(isVibrationEnabled);
        gameEngine.setMusicEnabled(isMusicEnabled); // Pass music state
        gameEngine.setDifficulty(currentDifficulty);
        gameContainer.addView(gameEngine);

        if (topHUD != null) {
            topHUD.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (gameEngine != null) {
                        gameEngine.setTopHUDHeight(topHUD.getHeight());
                    }
                }
            });
        }

        gameEngine.setGameListener(new BreakoutEngine.GameListener() {
            @Override
            public void onScoreUpdated(int score) {
                tvScore.setText("Score: " + score);
            }

            @Override
            public void onGameOver(int finalScore) {
                tvGameOverTitle.setText("GAME OVER");
                tvGameOverTitle.setTextColor(textColor);

                if (finalScore > highScore && finalScore > 0) {
                    highScore = finalScore;
                    prefs.edit().putInt("breakout_high_score", highScore).apply();
                    tvBestScore.setText("Best: " + highScore);
                    tvNewHighScoreBanner.setVisibility(View.VISIBLE);
                } else {
                    tvNewHighScoreBanner.setVisibility(View.GONE);
                }

                tvFinalScore.setText("Score: " + finalScore);
                showOverlaySmoothly(gameOverOverlay);
                fadeOutHudSmoothly(btnPause);
            }

            @Override
            public void onGameStarted() {
                tvTapToStart.setVisibility(View.GONE);
            }
        });

        if (btnPause != null) {
            btnPause.setOnClickListener(v -> {
                gameEngine.pauseGame();
                showOverlaySmoothly(pauseOverlay);
                fadeOutHudSmoothly(btnPause);
                pauseIconView.setIcon(GameIconView.ICON_PLAY, iconColor);
            });
        }

        if (btnResume != null) {
            btnResume.setOnClickListener(v -> {
                hideOverlaySmoothly(pauseOverlay);
                fadeInHudSmoothly(btnPause);
                pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
                gameEngine.resumeGame();
            });
        }

        if (btnRestart != null) {
            btnRestart.setOnClickListener(v -> {
                hideOverlaySmoothly(gameOverOverlay);
                tvNewHighScoreBanner.setVisibility(View.GONE);
                tvScore.setText("Score: 0");
                tvTapToStart.setVisibility(View.VISIBLE);
                fadeInHudSmoothly(btnPause);
                pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
                gameEngine.resetGame();
            });
        }

        if (btnQuit != null) btnQuit.setOnClickListener(v -> finish());
        if (btnQuitFromPause != null) btnQuitFromPause.setOnClickListener(v -> finish());
    }

    private void updateSpeedButtonText(Button btn) {
        String[] levels = {"Easy", "Medium", "Difficult"};
        btn.setText("Speed: " + levels[currentDifficulty]);
    }

    @Override
    public void onBackPressed() {
        RelativeLayout pauseOverlay = findViewById(R.id.pauseOverlay);
        RelativeLayout gameOverOverlay = findViewById(R.id.gameOverOverlay);

        if (gameOverOverlay != null && gameOverOverlay.getVisibility() == View.VISIBLE) {
            finish();
            return;
        }

        if (pauseOverlay != null && pauseOverlay.getVisibility() == View.VISIBLE) {
            finish();
            return;
        }

        if (gameEngine != null && gameEngine.isPlaying() && !gameEngine.isGameOver()) {
            LinearLayout btnPause = findViewById(R.id.btnPause);
            if (btnPause != null) {
                btnPause.performClick();
                return;
            }
        }

        super.onBackPressed();
    }

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

    private void fadeOutHudSmoothly(View pill) {
        if (pill != null) pill.animate().alpha(0f).setDuration(250).withEndAction(() -> pill.setVisibility(View.INVISIBLE)).start();
    }

    private void fadeInHudSmoothly(View pill) {
        if (pill != null) {
            pill.setAlpha(0f);
            pill.setVisibility(View.VISIBLE);
            pill.animate().alpha(1f).setDuration(250).start();
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
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

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onPause() {
        super.onPause();
        RelativeLayout pauseOverlay = findViewById(R.id.pauseOverlay);
        boolean isPauseMenuVisible = (pauseOverlay != null && pauseOverlay.getVisibility() == View.VISIBLE);

        if (gameEngine != null && gameEngine.isPlaying() && !gameEngine.isGameOver() && !isPauseMenuVisible) {
            View btnPause = findViewById(R.id.btnPause);
            if (btnPause != null) {
                btnPause.performClick();
            }
        }
    }

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

    private static class BreakoutEngine extends View implements Choreographer.FrameCallback {

        private final Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paddlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint brickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trajectoryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private BitmapShader lavaShader = null;
        private final Matrix textureMatrix = new Matrix();
        private float lavaOffset = 0f;
        private float texturePhase = 0f;

        private float screenW, screenH;

        private float ballX, ballY, ballRadius;
        private float ballDX, ballDY, baseSpeed, currentBallSpeed;
        private float paddleX, paddleY, paddleW, paddleH;
        private float lastPaddleX, paddleVelocity;

        private float difficultyMultiplier = 1.0f; // Stores level completion speed boosts

        private boolean isAiming = false;
        private float aimAngle = 0f;
        private float aimStartX, aimStartY;
        private long touchCooldown = 0; // Prevent instant restart touch bugs

        private static final int BRICK_ROWS = 6;
        private static final int BRICK_COLS = 7;
        private boolean[][] bricks = new boolean[BRICK_ROWS][BRICK_COLS];
        private float brickW, brickH, brickPadding, brickOffsetTop;
        private int[] brickColors;
        private float dynamicTopPadding = 250f;

        private final List<Particle> particles = new ArrayList<>();

        private boolean playing = false, paused = false, gameOver = false;
        private boolean vibrationEnabled = true;
        private boolean musicEnabled = true; // --- NEW MUSIC STATE ---
        private int currentDifficulty = 0;
        private int score = 0;
        private int bricksRemaining = 0;

        // --- CODE-GENERATED SOUND EFFECT ---
        private ToneGenerator toneGenerator;
        // --- LAG FIX: DEDICATED BACKGROUND AUDIO EXECUTOR ---
        private java.util.concurrent.ExecutorService audioExecutor;

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

            int trajColor = (themeState == 0) ? Color.parseColor("#66000000") : Color.parseColor("#77FFFFFF");
            trajectoryPaint.setColor(trajColor);
            trajectoryPaint.setStyle(Paint.Style.FILL);

            brickColors = new int[]{
                    Color.parseColor("#FF3B30"),
                    Color.parseColor("#FF9500"),
                    Color.parseColor("#FFCC00"),
                    Color.parseColor("#4CD964"),
                    Color.parseColor("#5AC8FA"),
                    Color.parseColor("#5856D6")
            };

            try {
                @SuppressLint("DiscouragedApi") int lavaResId = getContext().getResources().getIdentifier("lava_texture", "drawable", getContext().getPackageName());
                if (lavaResId != 0) {
                    Bitmap rawLava = BitmapFactory.decodeResource(getContext().getResources(), lavaResId);
                    Bitmap scaledLava = Bitmap.createScaledBitmap(rawLava, 400, 400, true);
                    lavaShader = new BitmapShader(scaledLava, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                }
            } catch (Exception ignored) { }

            // Initialize the audio generator and the non-blocking background thread
            try {
                audioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
                toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);
            } catch (Exception e) {
                toneGenerator = null;
                audioExecutor = null;
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            // Safely release the audio generator to prevent memory leaks
            if (audioExecutor != null) {
                audioExecutor.shutdown();
                audioExecutor = null;
            }
            if (toneGenerator != null) {
                toneGenerator.release();
                toneGenerator = null;
            }
        }

        public void setGameListener(GameListener listener) { this.listener = listener; }
        public void setVibrationEnabled(boolean enabled) { this.vibrationEnabled = enabled; }
        public void setMusicEnabled(boolean enabled) { this.musicEnabled = enabled; }
        public boolean isPlaying() { return playing; }
        public boolean isGameOver() { return gameOver; }

        public void setDifficulty(int difficulty) {
            this.currentDifficulty = difficulty;

            float oldBaseSpeed = baseSpeed;
            updateBaseSpeed();

            if (playing && oldBaseSpeed > 0) {
                float ratio = baseSpeed / oldBaseSpeed;
                currentBallSpeed *= ratio;
                ballDX *= ratio;
                ballDY *= ratio;
            }
        }

        private void updateBaseSpeed() {
            if (screenH > 0) {
                if (currentDifficulty == 0) baseSpeed = screenH * 0.0065f;
                else if (currentDifficulty == 1) baseSpeed = screenH * 0.0085f;
                else if (currentDifficulty == 2) baseSpeed = screenH * 0.0185f;

                baseSpeed *= difficultyMultiplier;
            }
        }

        public void setTopHUDHeight(int heightInPixels) {
            if (this.dynamicTopPadding == heightInPixels) return;
            this.dynamicTopPadding = heightInPixels;
            if (screenW > 0 && screenH > 0) {
                onSizeChanged((int)screenW, (int)screenH, (int)screenW, (int)screenH);
                invalidate();
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            screenW = w;
            screenH = h;

            ballRadius = screenW * 0.02f;
            paddleW = screenW * 0.25f;
            paddleH = screenH * 0.015f;
            paddleY = screenH - (screenH * 0.1f);

            brickPadding = screenW * 0.02f;
            brickW = (screenW - (brickPadding * (BRICK_COLS + 1))) / BRICK_COLS;
            brickH = screenH * 0.035f;
            brickOffsetTop = dynamicTopPadding + 40f;

            if (oldw == 0 || oldh == 0) {
                resetGame();
            } else {
                paddleX = (paddleX / oldw) * screenW;
                ballX = (ballX / oldw) * screenW;
                ballY = (ballY / oldh) * screenH;
                updateBaseSpeed();
            }
        }

        public void resetGame() {
            score = 0;
            difficultyMultiplier = 1.0f;
            updateBaseSpeed();
            initLevel();

            playing = false;
            paused = false;
            gameOver = false;

            touchCooldown = System.currentTimeMillis() + 600;

            Choreographer.getInstance().removeFrameCallback(this);
            Choreographer.getInstance().postFrameCallback(this);
            invalidate();
        }

        private void initLevel() {
            paddleX = (screenW / 2f) - (paddleW / 2f);
            ballX = screenW / 2f;
            ballY = paddleY - ballRadius - 5f;
            particles.clear();

            currentBallSpeed = baseSpeed;
            lastPaddleX = paddleX;

            isAiming = false;
            aimAngle = 0f;

            bricksRemaining = 0;

            // PROCEDURAL GENERATOR: Creates millions of random shapes
            // and strictly enforces a minimum of 25 bricks per layout!
            while (bricksRemaining < 25) {
                bricksRemaining = 0;

                boolean useSymmetry = Math.random() > 0.5;

                boolean[][] leftHalf = new boolean[BRICK_ROWS][(BRICK_COLS / 2) + 1];
                if (useSymmetry) {
                    for (int r = 0; r < BRICK_ROWS; r++) {
                        for (int c = 0; c < leftHalf[r].length; c++) {
                            leftHalf[r][c] = Math.random() > 0.35;
                        }
                    }
                }

                for (int r = 0; r < BRICK_ROWS; r++) {
                    for (int c = 0; c < BRICK_COLS; c++) {
                        boolean isBrickActive;

                        if (useSymmetry) {
                            int mirroredC = (c <= BRICK_COLS / 2) ? c : (BRICK_COLS - 1 - c);
                            isBrickActive = leftHalf[r][mirroredC];
                        } else {
                            isBrickActive = Math.random() > 0.35;
                        }

                        bricks[r][c] = isBrickActive;
                        if (isBrickActive) {
                            bricksRemaining++;
                        }
                    }
                }
            }
        }

        public void pauseGame() { paused = true; }
        public void resumeGame() {
            paused = false;
            lastPaddleX = paddleX;
            touchCooldown = System.currentTimeMillis() + 600;
            Choreographer.getInstance().removeFrameCallback(this);
            Choreographer.getInstance().postFrameCallback(this);
        }

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

        private void createBurstEffect(float cx, float cy, int color) {
            int numParticles = 12 + (int) (Math.random() * 6);
            for (int i = 0; i < numParticles; i++) {
                float angle = (float) (Math.random() * 2 * Math.PI);
                float speed = (float) (Math.random() * baseSpeed * 0.8f) + (baseSpeed * 0.2f);
                float pdx = (float) Math.cos(angle) * speed;
                float pdy = (float) Math.sin(angle) * speed;
                float pradius = (float) (Math.random() * (screenW * 0.008f)) + (screenW * 0.004f);
                int life = 20 + (int) (Math.random() * 15);
                particles.add(new Particle(cx, cy, pdx, pdy, pradius, color, life));
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (System.currentTimeMillis() < touchCooldown) return true;
            if (gameOver || paused) return true;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (!playing) {
                        aimStartX = event.getX();
                        aimStartY = event.getY();
                        isAiming = true;
                        aimAngle = 0f;
                        invalidate();
                    } else {
                        paddleX = event.getX() - (paddleW / 2f);
                        if (paddleX < 0) paddleX = 0;
                        if (paddleX + paddleW > screenW) paddleX = screenW - paddleW;
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (!playing) {
                        if (isAiming) {
                            float dx = event.getX() - aimStartX;
                            float dy = Math.max(10f, aimStartY - event.getY());

                            if (Math.hypot(dx, aimStartY - event.getY()) > 10) {
                                aimAngle = (float) Math.atan2(dx, dy);

                                float maxAngle = (float) (Math.PI / 2.4);
                                if (aimAngle > maxAngle) aimAngle = maxAngle;
                                if (aimAngle < -maxAngle) aimAngle = -maxAngle;

                                invalidate();
                            }
                        }
                    } else {
                        paddleX = event.getX() - (paddleW / 2f);
                        if (paddleX < 0) paddleX = 0;
                        if (paddleX + paddleW > screenW) paddleX = screenW - paddleW;
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!playing && isAiming) {
                        isAiming = false;
                        playing = true;
                        if (listener != null) listener.onGameStarted();

                        currentBallSpeed = baseSpeed;
                        ballDX = (float) Math.sin(aimAngle) * currentBallSpeed;
                        ballDY = -(float) Math.cos(aimAngle) * currentBallSpeed;
                    } else if (!playing) {
                        isAiming = false;
                        invalidate();
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            Choreographer.getInstance().removeFrameCallback(this);

            if (paused || gameOver) return;

            texturePhase += 0.012f;
            if (texturePhase > Math.PI * 2) {
                texturePhase -= Math.PI * 2;
            }

            lavaOffset += 1.5f;
            if (lavaOffset >= 400f) lavaOffset -= 400f;

            if (!playing) {
                invalidate();
                Choreographer.getInstance().postFrameCallback(this);
                return;
            }

            paddleVelocity = paddleX - lastPaddleX;
            lastPaddleX = paddleX;

            for (int i = particles.size() - 1; i >= 0; i--) {
                Particle p = particles.get(i);
                p.x += p.dx;
                p.y += p.dy;
                p.dy += screenH * 0.0005f;
                p.life--;
                if (p.life <= 0) particles.remove(i);
            }

            ballX += ballDX;
            ballY += ballDY;

            if (ballX - ballRadius < 0) { ballX = ballRadius; ballDX = -ballDX; }
            if (ballX + ballRadius > screenW) { ballX = screenW - ballRadius; ballDX = -ballDX; }
            if (ballY - ballRadius < 0) { ballY = ballRadius; ballDY = -ballDY; }

            if (ballY + ballRadius > screenH) {
                if (gameOver) return;
                gameOver = true;
                playing = false;
                triggerVibration(800);
                if (listener != null) listener.onGameOver(score);
                return;
            }

            RectF ballRect = new RectF(ballX - ballRadius, ballY - ballRadius, ballX + ballRadius, ballY + ballRadius);
            RectF paddleRect = new RectF(paddleX, paddleY, paddleX + paddleW, paddleY + paddleH);

            if (ballDY > 0 && RectF.intersects(ballRect, paddleRect)) {
                float hitPoint = ballX - (paddleX + paddleW / 2f);
                float normalizedHit = hitPoint / (paddleW / 2f);

                float maxBounceAngle = (float) (Math.PI / 3);
                float bounceAngle = normalizedHit * maxBounceAngle;

                float addedSpeed = Math.abs(paddleVelocity) * 0.1f;
                currentBallSpeed = baseSpeed + addedSpeed;

                float maxSpeedLimit = baseSpeed * 2.0f;
                if (currentBallSpeed > maxSpeedLimit) {
                    currentBallSpeed = maxSpeedLimit;
                }

                ballDX = (float) Math.sin(bounceAngle) * currentBallSpeed;
                ballDY = -(float) Math.cos(bounceAngle) * currentBallSpeed;
                ballY = paddleY - ballRadius;

                triggerVibration(10);
            }

            boolean hitBrick = false;
            for (int r = 0; r < BRICK_ROWS && !hitBrick; r++) {
                for (int c = 0; c < BRICK_COLS && !hitBrick; c++) {
                    if (bricks[r][c]) {
                        float bx = c * (brickW + brickPadding) + brickPadding;
                        float by = r * (brickH + brickPadding) + brickOffsetTop;
                        RectF brickRect = new RectF(bx, by, bx + brickW, by + brickH);

                        if (RectF.intersects(ballRect, brickRect)) {
                            bricks[r][c] = false;
                            hitBrick = true;
                            bricksRemaining--;

                            // --- LAG FIX: FIRE SOUND ON A NON-BLOCKING BACKGROUND THREAD ---
                            if (musicEnabled && toneGenerator != null && audioExecutor != null) {
                                audioExecutor.execute(() -> {
                                    try {
                                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 30);
                                    } catch (Exception ignored) {}
                                });
                            }

                            int baseColor = brickColors[r % brickColors.length];
                            createBurstEffect(bx + (brickW / 2f), by + (brickH / 2f), baseColor);

                            triggerVibration(20);

                            score += 1;
                            if (listener != null) listener.onScoreUpdated(score);

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

            if (bricksRemaining <= 0) {
                difficultyMultiplier *= 1.15f;
                updateBaseSpeed();
                initLevel();
                playing = false;
                if (listener != null) listener.onGameStarted();
            }

            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }

        private void drawTrajectoryMarker(Canvas canvas) {
            float simX = ballX;
            float simY = ballY;
            float dirX = (float) Math.sin(aimAngle);
            float dirY = -(float) Math.cos(aimAngle);

            float dotSpacing = screenH * 0.04f;
            int numDots = 18;

            for (int i = 1; i <= numDots; i++) {
                simX += dirX * dotSpacing;
                simY += dirY * dotSpacing;

                if (simX - ballRadius < 0) {
                    simX = ballRadius + (ballRadius - simX);
                    dirX = -dirX;
                } else if (simX + ballRadius > screenW) {
                    simX = screenW - ballRadius - (simX + ballRadius - screenW);
                    dirX = -dirX;
                }

                if (simY - ballRadius < 0) {
                    simY = ballRadius + (ballRadius - simY);
                    dirY = -dirY;
                }

                int alpha = (int) (255 * (1f - (float) i / numDots));
                trajectoryPaint.setAlpha(alpha);
                canvas.drawCircle(simX, simY, ballRadius * 0.35f, trajectoryPaint);
            }
        }

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

            for (int r = 0; r < BRICK_ROWS; r++) {
                int baseColor = brickColors[r % brickColors.length];

                if (lavaShader != null) {
                    textureMatrix.reset();
                    textureMatrix.postTranslate(lavaOffset, lavaOffset * 0.5f);
                    lavaShader.setLocalMatrix(textureMatrix);
                    brickPaint.setShader(lavaShader);
                    brickPaint.setColorFilter(new PorterDuffColorFilter(baseColor, PorterDuff.Mode.MULTIPLY));
                } else {
                    int lightColor = manipulateColor(baseColor, 1.4f);
                    int darkColor = manipulateColor(baseColor, 0.6f);

                    float animatedOffset = (float) Math.sin(texturePhase) * (brickW * 1.5f);

                    LinearGradient fluidGradient = new LinearGradient(
                            0, animatedOffset, brickW, brickH + animatedOffset,
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

            if (!playing && !gameOver && isAiming) {
                drawTrajectoryMarker(canvas);
            }

            for (Particle p : particles) {
                particlePaint.setColor(p.color);
                int alpha = (int) (255f * ((float) p.life / p.maxLife));
                particlePaint.setAlpha(alpha);
                canvas.drawCircle(p.x, p.y, p.radius, particlePaint);
            }

            canvas.drawRoundRect(paddleX, paddleY, paddleX + paddleW, paddleY + paddleH, 16f, 16f, paddlePaint);
            canvas.drawCircle(ballX, ballY, ballRadius, ballPaint);
        }

        private static class Particle {
            float x, y, dx, dy, radius;
            int color;
            int life, maxLife;

            Particle(float x, float y, float dx, float dy, float radius, int color, int life) {
                this.x = x; this.y = y; this.dx = dx; this.dy = dy; this.radius = radius;
                this.color = color; this.life = life; this.maxLife = life;
            }
        }
    }
}