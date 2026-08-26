package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
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
import android.os.VibratorManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@SuppressWarnings({"all", "deprecation", "SpellCheckingInspection", "FieldCanBeLocal"})
@SuppressLint("SetTextI18n")
public class MazeBallActivity extends Activity {

    private GameView gameView;
    private TextView tvLivesText, tvRingsText, tvBestText, tvFinalScore;
    private RelativeLayout gameOverOverlay, modeSelectionOverlay;

    private boolean isLeftPressed = false, isRightPressed = false;
    private int highScore = 0;
    private static final String PREFS_NAME = "MAZE_BALL_PREFS";
    private static final String HIGH_SCORE_KEY = "HIGH_SCORE";

    // Theme Variables
    private int themeState; // 0 = Light, 1 = Dark, 2 = Star
    private int brickColor, brickBorderColor, obstacleColor, ballColor;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maze_ball);

        // --- Override MainApplication's global padding to remove the left white bar in Landscape ---
        View decorView = getWindow().getDecorView();
        ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            View contentView = findViewById(android.R.id.content);
            if (contentView != null) {
                // Set left and right padding to 0 so the game canvas stretches edge-to-edge horizontally
                contentView.setPadding(0, systemBars.top, 0, systemBars.bottom);
            }
            return WindowInsetsCompat.CONSUMED;
        });
        // ---------------------------------------------------------------------------------------------

        SharedPreferences appPrefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);

        // --- 3-STATE THEME SYNC LOGIC ---
        themeState = appPrefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            boolean oldDark = appPrefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = oldDark ? 1 : 0;
        }

        SharedPreferences gamePrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        highScore = gamePrefs.getInt(HIGH_SCORE_KEY, 0);

        // Local UI styling variables
        int bgColor, overlayBgColor, titleColor, textColor;
        int pillBgColor, boxBgColor, iconColor, hudTextColor;
        GradientDrawable menuCardBgDrawable;

        if (themeState == 0) { // Light Mode
            bgColor = Color.parseColor("#87CEEB");
            brickColor = Color.parseColor("#222222");    // Black bricks
            brickBorderColor = Color.parseColor("#555555"); // Lighter shadow lines
            ballColor = Color.parseColor("#EE0000");
            obstacleColor = Color.parseColor("#EECC00");
            textColor = Color.BLACK;

            overlayBgColor = Color.parseColor("#E6FFFFFF");
            titleColor = Color.BLACK;

            menuCardBgDrawable = new GradientDrawable();
            menuCardBgDrawable.setColor(Color.parseColor("#F2F2F7"));
            menuCardBgDrawable.setCornerRadius(64f);

            pillBgColor = Color.parseColor("#E5E5EA");
            boxBgColor = Color.parseColor("#FFFFFF");
            iconColor = Color.parseColor("#333333");
            hudTextColor = Color.parseColor("#333333");

        } else if (themeState == 1) { // Standard Dark Mode
            bgColor = Color.parseColor("#1A1A2E");
            brickColor = Color.parseColor("#444444");    // Dark Gray bricks
            brickBorderColor = Color.parseColor("#1A1A1A"); // Deep shadow lines
            ballColor = Color.parseColor("#FF0000");
            obstacleColor = Color.parseColor("#FFD700");
            textColor = Color.WHITE;

            overlayBgColor = Color.parseColor("#E6000000"); // Deep blurred feel
            titleColor = Color.WHITE;

            menuCardBgDrawable = new GradientDrawable();
            menuCardBgDrawable.setColor(Color.parseColor("#1C1C1E"));
            menuCardBgDrawable.setCornerRadius(64f);

            pillBgColor = Color.parseColor("#2D313A");
            boxBgColor = Color.parseColor("#D8E2FF");
            iconColor = Color.parseColor("#001C3A");
            hudTextColor = Color.parseColor("#E3E2E6");
        } else { // Star Mode (AMOLED Pure Black)
            bgColor = Color.parseColor("#000000");
            brickColor = Color.parseColor("#2C2C2E");    // Slightly elevated black bricks
            brickBorderColor = Color.parseColor("#000000");
            ballColor = Color.parseColor("#FF0000");
            obstacleColor = Color.parseColor("#FFD700");
            textColor = Color.WHITE;

            overlayBgColor = Color.parseColor("#F2000000"); // Extremely dark overlay
            titleColor = Color.WHITE;

            menuCardBgDrawable = new GradientDrawable();
            menuCardBgDrawable.setColor(Color.parseColor("#121212"));
            menuCardBgDrawable.setCornerRadius(64f);

            pillBgColor = Color.parseColor("#2D313A");
            boxBgColor = Color.parseColor("#D8E2FF");
            iconColor = Color.parseColor("#001C3A");
            hudTextColor = Color.parseColor("#E3E2E6");
        }

        findViewById(R.id.rootMazeBall).setBackgroundColor(bgColor);

        // Map HUD Elements (Modern Quick Settings Pills)
        LinearLayout hudLivesPill = findViewById(R.id.hudLivesPill);
        FrameLayout livesIconBox = findViewById(R.id.livesIconBox);
        tvLivesText = findViewById(R.id.tvLivesText);

        LinearLayout hudRingsPill = findViewById(R.id.hudRingsPill);
        FrameLayout ringsIconBox = findViewById(R.id.ringsIconBox);
        tvRingsText = findViewById(R.id.tvRingsText);
        tvBestText = findViewById(R.id.tvBestText);

        // Inject Mathematical Vector Icons
        GameIconView livesIconView = new GameIconView(this);
        livesIconBox.addView(livesIconView);
        GameIconView ringsIconView = new GameIconView(this);
        ringsIconBox.addView(ringsIconView);

        // Apply Pill Styling
        hudLivesPill.setBackground(createPillShape(pillBgColor));
        livesIconBox.setBackground(createBoxShape(boxBgColor));
        tvLivesText.setTextColor(hudTextColor);
        livesIconView.setIcon(GameIconView.ICON_HEART, iconColor);

        hudRingsPill.setBackground(createPillShape(pillBgColor));
        ringsIconBox.setBackground(createBoxShape(boxBgColor));
        tvRingsText.setTextColor(hudTextColor);
        tvBestText.setTextColor(themeState == 0 ? Color.parseColor("#666666") : Color.parseColor("#A0A0A5"));
        ringsIconView.setIcon(GameIconView.ICON_RING, iconColor);

        tvBestText.setText(String.format(Locale.getDefault(), "Best: %04d", highScore));

        modeSelectionOverlay = findViewById(R.id.modeSelectionOverlay);
        gameOverOverlay = findViewById(R.id.gameOverOverlay);
        tvFinalScore = findViewById(R.id.tvFinalScore);

        LinearLayout modeSelectionCard = findViewById(R.id.modeSelectionCard);
        TextView tvModeTitle = findViewById(R.id.tvModeTitle);
        LinearLayout gameOverCard = findViewById(R.id.gameOverCard);
        TextView tvGameOverTitle = findViewById(R.id.tvGameOverTitle);

        Button btnModeLandscape = findViewById(R.id.btnModeLandscape);
        Button btnModePortrait = findViewById(R.id.btnModePortrait);
        Button btnPlayAgain = findViewById(R.id.btnPlayAgain);
        Button btnQuit = findViewById(R.id.btnQuit);

        modeSelectionOverlay.setBackgroundColor(overlayBgColor);
        modeSelectionCard.setBackground(menuCardBgDrawable);
        tvModeTitle.setTextColor(titleColor);

        gameOverOverlay.setBackgroundColor(overlayBgColor);
        gameOverCard.setBackground(menuCardBgDrawable);
        tvGameOverTitle.setTextColor(titleColor);
        tvFinalScore.setTextColor(titleColor);

        // Modern Buttons inside cards
        int primaryBlue = Color.parseColor("#0B57D0");
        btnModeLandscape.setBackground(createPillShape(primaryBlue));
        btnModeLandscape.setTextColor(Color.WHITE);

        btnModePortrait.setBackground(createPillShape(themeState != 0 ? Color.parseColor("#333333") : Color.parseColor("#E0E0E0")));
        btnModePortrait.setTextColor(textColor);

        btnPlayAgain.setBackground(createPillShape(primaryBlue));
        btnPlayAgain.setTextColor(Color.WHITE);

        btnQuit.setBackground(createPillShape(themeState != 0 ? Color.parseColor("#3A3A3C") : Color.parseColor("#D1D1D6")));
        btnQuit.setTextColor(textColor);

        Button btnLeft = findViewById(R.id.btnLeft);
        Button btnRight = findViewById(R.id.btnRight);
        Button btnJump = findViewById(R.id.btnJump);

        setupModernFAB(btnLeft, themeState != 0);
        setupModernFAB(btnRight, themeState != 0);
        setupModernFAB(btnJump, themeState != 0);

        FrameLayout container = findViewById(R.id.gameCanvasContainer);
        gameView = new GameView(this);
        container.addView(gameView);

        btnLeft.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) isLeftPressed = true;
            else if (event.getAction() == MotionEvent.ACTION_UP) isLeftPressed = false;
            return true;
        });

        btnRight.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) isRightPressed = true;
            else if (event.getAction() == MotionEvent.ACTION_UP) isRightPressed = false;
            return true;
        });

        btnJump.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) gameView.triggerJump();
            return true;
        });

        btnModeLandscape.setOnClickListener(v -> setOrientationAndStart(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, false));
        btnModePortrait.setOnClickListener(v -> setOrientationAndStart(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, true));

        btnPlayAgain.setOnClickListener(v -> {
            gameOverOverlay.setVisibility(View.GONE);
            isLeftPressed = false; isRightPressed = false;
            gameView.restartGame();
        });

        btnQuit.setOnClickListener(v -> finish());
    }

    // --- CINEMATIC SMOOTH TRANSITION ENGINE ---
    @SuppressLint("SourceLockedOrientationActivity")
    private void setOrientationAndStart(int orientation, boolean isPortrait) {
        setRequestedOrientation(orientation);

        // Prevent double taps during transition
        modeSelectionOverlay.setEnabled(false);

        // Smoothly fade out the menu card instantly
        View card = findViewById(R.id.modeSelectionCard);
        if (card != null) {
            card.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(250).start();
        }

        // Wait 400ms for Android system to physically rotate the screen bounds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            gameView.setMode(isPortrait);

            // Post ensuring the new layout bounds are perfectly measured before calculating camera
            gameView.post(() -> {
                gameView.prepareGame(); // Generates level and locks camera instantly without running physics

                // Crossfade the dark background overlay out to reveal the generated maze
                modeSelectionOverlay.animate()
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction(() -> {
                            modeSelectionOverlay.setVisibility(View.GONE);
                            modeSelectionOverlay.setAlpha(1f);
                            modeSelectionOverlay.setEnabled(true);
                            if (card != null) {
                                card.setAlpha(1f);
                                card.setScaleX(1f);
                                card.setScaleY(1f);
                            }
                            gameView.startGame(); // Start ball movement
                        }).start();
            });
        }, 400);
    }

    private GradientDrawable createPillShape(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(1000f);
        shape.setColor(color);
        return shape;
    }

    private GradientDrawable createBoxShape(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(30f);
        return shape;
    }

    private void setupModernFAB(Button btn, boolean isDarkTheme) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(isDarkTheme ? Color.parseColor("#4DFFFFFF") : Color.parseColor("#80000000"));
        btn.setBackground(shape);
        btn.setTextColor(Color.parseColor("#D3D3D3"));
    }

    private void saveHighScore(int newScore) {
        if (newScore > highScore) {
            highScore = newScore;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(HIGH_SCORE_KEY, highScore).apply();
            new Handler(Looper.getMainLooper()).post(() -> tvBestText.setText(String.format(Locale.getDefault(), "Best: %04d", highScore)));
        }
    }

    private void triggerDeathVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vibratorManager != null) {
                Vibrator vibrator = vibratorManager.getDefaultVibrator();
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } else {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(150);
                }
            }
        }
    }

    @Override
    protected void onResume() { super.onResume(); if (modeSelectionOverlay.getVisibility() == View.GONE) gameView.resume(); }

    @Override
    protected void onPause() { super.onPause(); gameView.pause(); }

    // --- FIX: Smooth Exit to prevent Gallery layout glitch ---
    @Override
    public void finish() {
        // Reset orientation to portrait so the underlying Gallery doesn't get stretched/glitched
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        super.finish();
        // Use a fade transition to seamlessly mask the system rotation step
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // --- MATHEMATICAL VECTOR ICON ENGINE ---
    public static class GameIconView extends View {
        public static final int ICON_HEART = 0;
        public static final int ICON_RING = 1;

        private int iconType = ICON_HEART;
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

            if (iconType == ICON_HEART) {
                paint.setStyle(Paint.Style.FILL);
                Path p = new Path();
                p.moveTo(cx, cy + 8f);
                p.cubicTo(cx - 16f, cy - 2f, cx - 8f, cy - 12f, cx, cy - 4f);
                p.cubicTo(cx + 8f, cy - 12f, cx + 16f, cy - 2f, cx, cy + 8f);
                canvas.drawPath(p, paint);
            }
            else if (iconType == ICON_RING) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(4.5f);
                canvas.drawCircle(cx, cy, 7f, paint);
            }
        }
    }

    // ==========================================
    // ZERO-ALLOCATION MULTI-MODE GAME ENGINE
    // ==========================================
    private class GameView extends View {
        private Thread gameThread;
        private boolean isPlaying = false;
        private boolean isGameOver = false;
        private boolean isPortraitMode = false;

        private final Object stateLock = new Object();

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path spikePath = new Path();
        private final Path spikeTipPath = new Path();
        private final Path flyspikePath = new Path();
        private final RectF tempRect = new RectF();

        private static final float GRAVITY = 1.1f;
        private static final float JUMP_FORCE = -23f;
        private static final float MOVE_SPEED = 1.3f;
        private static final float MAX_SPEED = 11f;
        private static final float FRICTION = 0.86f;
        private static final float BOUNCE_FACTOR = 0.35f;
        private static final float BALL_RADIUS = 32f;
        private static final int BLOCK_SIZE = 100;

        private float ballX = 200, ballY = 200, ballVX = 0, ballVY = 0;
        private boolean onGround = false;
        private int jumpCount = 0;
        private int maxJumps = 2;
        private float cameraX = 0, cameraY = 0;
        private int score = 0;
        private int lives = 3;

        private float lastSafeX = 200, lastSafeY = 200;
        private long immunityEndTime = 0;

        private float furthestGeneratedX = 0;
        private float furthestGeneratedY = 0;
        private float wallGeneratedY = 0;
        private int nextPlatformType = 0;
        private boolean justHadGap = false;

        private final Random random = new Random();

        private final List<RectF> blocks = new ArrayList<>();
        private final List<RectF> spikes = new ArrayList<>();
        private final List<Ring> rings = new ArrayList<>();
        private final List<Flyspike> flyspikes = new ArrayList<>();

        // --- PARALLAX BACKGROUND LIST ---
        private final List<Star> stars = new ArrayList<>();

        private class Ring {
            float x, y; boolean collected = false; float animTime = 0;
            Ring(float x, float y) { this.x = x; this.y = y; }
        }

        private class Flyspike {
            float x, y, radius; float rotation = 0;
            Flyspike(float x, float y, float radius) { this.x = x; this.y = y; this.radius = radius; }
        }

        private class Star {
            float x, y, radius, parallaxSpeed;
            int baseAlpha;
            float twinkleSpeed;
            float twinkleRange;
            float phase;
            Star() {
                x = random.nextFloat() * 2000;
                y = random.nextFloat() * 2000;
                radius = 1.5f + random.nextFloat() * 3.5f;
                parallaxSpeed = 0.02f + random.nextFloat() * 0.1f;
                baseAlpha = 100 + random.nextInt(80);
                twinkleSpeed = 1.5f + random.nextFloat() * 4f;
                twinkleRange = 50 + random.nextFloat() * 70f;
                phase = random.nextFloat() * (float)(Math.PI * 2);
            }
        }

        public GameView(Context context) {
            super(context);
            initBackgroundElements();
        }

        public void setMode(boolean portrait) { this.isPortraitMode = portrait; }

        private void initBackgroundElements() {
            stars.clear();
            if (themeState == 2) {
                for (int i = 0; i < 75; i++) stars.add(new Star());
            }
        }

        // Separate generation from starting the thread to allow seamless transitions
        public void prepareGame() {
            isPlaying = false;
            try { if (gameThread != null && gameThread.isAlive()) gameThread.join(); } catch (InterruptedException ignored) {}

            score = 0;
            lives = 3;
            isGameOver = false;
            initLevel();
            updateUI();
            postInvalidate(); // Draw the very first frame behind the overlay
        }

        public void startGame() {
            resume();
        }

        public void restartGame() {
            prepareGame();
            startGame();
        }

        private void initLevel() {
            synchronized (stateLock) {
                blocks.clear(); spikes.clear(); rings.clear(); flyspikes.clear();
                ballVX = 0; ballVY = 0;
                jumpCount = 0;
                justHadGap = false;
                immunityEndTime = 0;

                float screenW = getWidth() == 0 ? 1000 : getWidth();
                float screenH = getHeight() == 0 ? 2000 : getHeight();

                if (isPortraitMode) {
                    maxJumps = 3;
                    ballX = screenW / 2f;
                    ballY = -200;
                    lastSafeX = ballX;
                    lastSafeY = ballY;

                    // Pre-calculate precise camera to prevent the opening "Snap" jump
                    cameraY = ballY - (screenH / 1.6f);
                    cameraX = 0;

                    furthestGeneratedY = 0;
                    wallGeneratedY = 0;
                    nextPlatformType = random.nextBoolean() ? 0 : 1;
                    generatePortraitChunks(cameraY - screenH - 1000);
                } else {
                    maxJumps = 2;
                    ballX = 200;
                    ballY = 200;
                    lastSafeX = ballX;
                    lastSafeY = ballY;

                    // Pre-calculate precise camera
                    cameraX = ballX - screenW / 3f;
                    cameraY = 0;

                    furthestGeneratedX = cameraX - 500;
                    generateLandscapeChunks(cameraX + screenW + 1000);
                }
            }
        }

        public void triggerJump() {
            synchronized (stateLock) {
                if (!isGameOver && (onGround || jumpCount < maxJumps)) {
                    ballVY = JUMP_FORCE;
                    onGround = false;
                    jumpCount++;
                }
            }
        }

        private void generateLandscapeChunks(float targetX) {
            float startY = 750;
            while (furthestGeneratedX < targetX) {
                if (furthestGeneratedX > 600 && !justHadGap && random.nextInt(10) < 2) {
                    float gapSize = 80f + random.nextFloat() * 40f;
                    furthestGeneratedX += gapSize;
                    justHadGap = true;
                    continue;
                }

                blocks.add(new RectF(furthestGeneratedX, startY, furthestGeneratedX + BLOCK_SIZE, startY + BLOCK_SIZE));
                blocks.add(new RectF(furthestGeneratedX, startY + BLOCK_SIZE, furthestGeneratedX + BLOCK_SIZE, startY + BLOCK_SIZE * 2));

                if (furthestGeneratedX > 800) {
                    int rand = random.nextInt(15);
                    if (rand == 0 && !justHadGap) {
                        spikes.add(new RectF(furthestGeneratedX + 20, startY - 40, furthestGeneratedX + 80, startY));
                    }
                    else if (rand == 1 || rand == 2) rings.add(new Ring(furthestGeneratedX + 50, startY - 140));
                    else if (rand == 3) {
                        blocks.add(new RectF(furthestGeneratedX, startY - BLOCK_SIZE * 2, furthestGeneratedX + BLOCK_SIZE, startY - BLOCK_SIZE));
                        if (random.nextBoolean()) rings.add(new Ring(furthestGeneratedX + 50, startY - BLOCK_SIZE * 3 - 40));
                    }
                    else if (rand == 4) {
                        flyspikes.add(new Flyspike(furthestGeneratedX + 50, startY - 130, 38f));
                    }
                }

                justHadGap = false;
                furthestGeneratedX += BLOCK_SIZE;
            }
        }

        private void generatePortraitChunks(float targetY) {
            float screenW = getWidth() == 0 ? 1000 : getWidth();
            float wallW = BLOCK_SIZE;

            if (furthestGeneratedY == 0) {
                blocks.add(new RectF(wallW, 0, screenW - wallW, BLOCK_SIZE * 2));
                furthestGeneratedY = -BLOCK_SIZE;
                wallGeneratedY = BLOCK_SIZE;
            }

            while (wallGeneratedY > targetY - 1000) {
                blocks.add(new RectF(0, wallGeneratedY - BLOCK_SIZE, wallW, wallGeneratedY));
                blocks.add(new RectF(screenW - wallW, wallGeneratedY - BLOCK_SIZE, screenW, wallGeneratedY));
                wallGeneratedY -= BLOCK_SIZE;
            }

            while (furthestGeneratedY > targetY) {
                furthestGeneratedY -= (BLOCK_SIZE * 2.4f);

                float platW = BLOCK_SIZE * 2.5f;
                float thickness = BLOCK_SIZE * 0.6f;

                if (nextPlatformType == 0) {
                    blocks.add(new RectF(wallW, furthestGeneratedY, wallW + platW, furthestGeneratedY + thickness));
                    if (random.nextInt(3) != 0) rings.add(new Ring(wallW + platW - 40, furthestGeneratedY - 60));
                    if (random.nextInt(6) == 0) spikes.add(new RectF(wallW + 40, furthestGeneratedY - 40, wallW + 100, furthestGeneratedY));
                    else if (random.nextInt(6) == 1) flyspikes.add(new Flyspike(wallW + platW + 60, furthestGeneratedY - 60, 38f));
                    nextPlatformType = random.nextBoolean() ? 1 : 2;
                } else if (nextPlatformType == 1) {
                    blocks.add(new RectF(screenW - wallW - platW, furthestGeneratedY, screenW - wallW, furthestGeneratedY + thickness));
                    if (random.nextInt(3) != 0) rings.add(new Ring(screenW - wallW - platW + 40, furthestGeneratedY - 60));
                    if (random.nextInt(6) == 0) spikes.add(new RectF(screenW - wallW - 100, furthestGeneratedY - 40, screenW - wallW - 40, furthestGeneratedY));
                    else if (random.nextInt(6) == 1) flyspikes.add(new Flyspike(screenW - wallW - platW - 60, furthestGeneratedY - 60, 38f));
                    nextPlatformType = random.nextBoolean() ? 0 : 2;
                } else {
                    float cx = screenW / 2f;
                    blocks.add(new RectF(cx - platW/2f, furthestGeneratedY, cx + platW/2f, furthestGeneratedY + thickness));
                    rings.add(new Ring(cx, furthestGeneratedY - 60));
                    if (random.nextInt(4) == 0) flyspikes.add(new Flyspike(cx - platW/2f - 70, furthestGeneratedY - 40, 38f));
                    nextPlatformType = random.nextBoolean() ? 0 : 1;
                }
            }
        }

        private void update() {
            synchronized (stateLock) {
                if (isGameOver || !isPlaying) return;

                if (isLeftPressed) ballVX -= MOVE_SPEED;
                if (isRightPressed) ballVX += MOVE_SPEED;

                ballVX *= FRICTION;
                if (ballVX > MAX_SPEED) ballVX = MAX_SPEED;
                if (ballVX < -MAX_SPEED) ballVX = -MAX_SPEED;
                ballX += ballVX;

                if (isPortraitMode) {
                    if (ballX - BALL_RADIUS < 0) {
                        ballX = BALL_RADIUS; ballVX = Math.abs(ballVX) * BOUNCE_FACTOR;
                    } else if (ballX + BALL_RADIUS > getWidth()) {
                        ballX = getWidth() - BALL_RADIUS; ballVX = -Math.abs(ballVX) * BOUNCE_FACTOR;
                    }
                }

                tempRect.set(ballX - BALL_RADIUS, ballY - BALL_RADIUS, ballX + BALL_RADIUS, ballY + BALL_RADIUS);
                for (int i = 0; i < blocks.size(); i++) {
                    RectF b = blocks.get(i);
                    if (RectF.intersects(b, tempRect)) {
                        if (ballVX > 0) { ballX = b.left - BALL_RADIUS; ballVX = -ballVX * BOUNCE_FACTOR; }
                        else if (ballVX < 0) { ballX = b.right + BALL_RADIUS; ballVX = -ballVX * BOUNCE_FACTOR; }
                        tempRect.set(ballX - BALL_RADIUS, ballY - BALL_RADIUS, ballX + BALL_RADIUS, ballY + BALL_RADIUS);
                    }
                }

                ballVY += GRAVITY;
                ballY += ballVY;
                onGround = false;

                tempRect.set(ballX - BALL_RADIUS, ballY - BALL_RADIUS, ballX + BALL_RADIUS, ballY + BALL_RADIUS);
                for (int i = 0; i < blocks.size(); i++) {
                    RectF b = blocks.get(i);
                    if (RectF.intersects(b, tempRect)) {
                        if (ballVY > 0) {
                            ballY = b.top - BALL_RADIUS;
                            if (ballVY > 6f) ballVY = -ballVY * BOUNCE_FACTOR; else ballVY = 0;
                            onGround = true; jumpCount = 0;
                        }
                        else if (ballVY < 0) { ballY = b.bottom + BALL_RADIUS; ballVY = 0; }
                        tempRect.set(ballX - BALL_RADIUS, ballY - BALL_RADIUS, ballX + BALL_RADIUS, ballY + BALL_RADIUS);
                    }
                }

                // Spike Death Check
                tempRect.set(ballX - BALL_RADIUS + 10, ballY - BALL_RADIUS + 10, ballX + BALL_RADIUS - 10, ballY + BALL_RADIUS - 10);
                boolean touchingHazard = false;

                for (int i = 0; i < spikes.size(); i++) {
                    if (RectF.intersects(spikes.get(i), tempRect)) {
                        touchingHazard = true;
                        if (System.currentTimeMillis() >= immunityEndTime) {
                            die();
                            return;
                        }
                    }
                }

                // Flyspike Animation & Death Check
                for (Flyspike v : flyspikes) {
                    v.rotation += 2.5f;
                    if (Math.hypot(ballX - v.x, ballY - v.y) < BALL_RADIUS + v.radius - 5) {
                        touchingHazard = true;
                        if (System.currentTimeMillis() >= immunityEndTime) {
                            die();
                            return;
                        }
                    }
                }

                if (onGround && !touchingHazard) {
                    lastSafeX = ballX;
                    lastSafeY = ballY;
                }

                for (Ring r : rings) {
                    if (!r.collected && Math.hypot(ballX - r.x, ballY - r.y) < BALL_RADIUS + 35) {
                        r.collected = true;
                        score += 1;
                        saveHighScore(score);
                        updateUI();
                    }
                    r.animTime += 0.1f;
                }

                if (isPortraitMode) {
                    float autoScrollSpeed = 1.5f + (score * 0.05f);
                    if (autoScrollSpeed > 6.0f) autoScrollSpeed = 6.0f;
                    cameraY -= autoScrollSpeed;

                    float desiredCameraY = ballY - (getHeight() / 1.6f);
                    if (desiredCameraY < cameraY) {
                        // Smooth follow to keep ball centered without hard snapping during play
                        cameraY += (desiredCameraY - cameraY) * 0.15f;
                    }

                    if (ballY > cameraY + getHeight() + 100) {
                        if (System.currentTimeMillis() < immunityEndTime) {
                            respawnBallSafe();
                        } else {
                            die();
                            return;
                        }
                    }

                    generatePortraitChunks(cameraY - getHeight() - 1000);

                    blocks.removeIf(b -> b.top > cameraY + getHeight() + 500);
                    spikes.removeIf(s -> s.top > cameraY + getHeight() + 500);
                    rings.removeIf(r -> r.y > cameraY + getHeight() + 500);
                    flyspikes.removeIf(v -> v.y > cameraY + getHeight() + 500);
                } else {
                    cameraX = ballX - getWidth() / 3f;

                    if (ballY > 1600) {
                        if (System.currentTimeMillis() < immunityEndTime) {
                            respawnBallSafe();
                        } else {
                            die();
                            return;
                        }
                    }

                    generateLandscapeChunks(cameraX + getWidth() + 1000);
                    blocks.removeIf(b -> b.right < cameraX - 500);
                    spikes.removeIf(s -> s.right < cameraX - 500);
                    rings.removeIf(r -> r.x < cameraX - 500);
                    flyspikes.removeIf(v -> v.x < cameraX - 500);
                }
            }
        }

        private void die() {
            triggerDeathVibration();
            lives--;

            if (lives <= 0) {
                isGameOver = true;
                isPlaying = false;
                saveHighScore(score);

                new Handler(Looper.getMainLooper()).post(() -> {
                    tvFinalScore.setText("Score: " + score);
                    gameOverOverlay.setVisibility(View.VISIBLE);
                });
            } else {
                immunityEndTime = System.currentTimeMillis() + 3000;
                respawnBallSafe();
                updateUI();
            }
        }

        private void respawnBallSafe() {
            ballX = lastSafeX;
            ballY = lastSafeY - 60;
            ballVX = 0;
            ballVY = 0;

            if (isPortraitMode) {
                cameraY = ballY - (getHeight() / 1.6f);
            } else {
                cameraX = ballX - getWidth() / 3f;
            }
        }

        private void updateUI() {
            new Handler(Looper.getMainLooper()).post(() -> {
                tvRingsText.setText(String.format(Locale.getDefault(), "Rings: %04d", score));
                tvLivesText.setText(String.format(Locale.getDefault(), "Lives: %d", lives));
            });
        }

        // --- DYNAMIC BACKGROUND DRAWING ENGINE ---
        private void drawParallaxBackground(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL);

            if (themeState == 2) { // Star Mode (Black space with stars)
                canvas.drawColor(Color.BLACK);
                paint.setColor(Color.WHITE);

                // Extremely precise Double time calculation for perfect smooth sine waves
                double t = System.currentTimeMillis() / 1000.0;

                for (Star s : stars) {
                    float drawX = (s.x - cameraX * s.parallaxSpeed) % 2000;
                    float drawY = (s.y - cameraY * s.parallaxSpeed) % 2000;
                    if (drawX < 0) drawX += 2000;
                    if (drawY < 0) drawY += 2000;

                    // Advanced twinkle logic: smooth sine wave modulation
                    int currentAlpha = (int) (s.baseAlpha + Math.sin(t * s.twinkleSpeed + s.phase) * s.twinkleRange);
                    currentAlpha = Math.max(0, Math.min(255, currentAlpha)); // Clamp between 0 and 255

                    paint.setAlpha(currentAlpha);
                    canvas.drawCircle(drawX, drawY, s.radius, paint);
                }
            } else if (themeState == 1) { // Dark Mode
                canvas.drawColor(Color.parseColor("#1A1A2E")); // Twilight Sky
            } else { // Light Mode
                canvas.drawColor(Color.parseColor("#87CEEB")); // Blue Sky
            }

            paint.setAlpha(255); // Reset alpha
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            super.draw(canvas);

            synchronized (stateLock) {
                // 1. Draw Infinite Parallax Background FIRST
                drawParallaxBackground(canvas);

                canvas.save();

                if (isPortraitMode) canvas.translate(0, -cameraY);
                else canvas.translate(-cameraX, 0);

                // 2. Draw Bricks
                for (int i = 0; i < blocks.size(); i++) {
                    RectF b = blocks.get(i);

                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(brickColor);
                    canvas.drawRect(b, paint);

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(brickBorderColor);
                    paint.setStrokeWidth(3);

                    canvas.drawRect(b, paint);
                    canvas.drawLine(b.left, b.centerY(), b.right, b.centerY(), paint);
                    canvas.drawLine(b.centerX(), b.top, b.centerX(), b.centerY(), paint);

                    if (b.width() >= 100) {
                        canvas.drawLine(b.left + 25, b.centerY(), b.left + 25, b.bottom, paint);
                        canvas.drawLine(b.left + 75, b.centerY(), b.left + 75, b.bottom, paint);
                    } else {
                        canvas.drawLine(b.centerX(), b.centerY(), b.centerX(), b.bottom, paint);
                    }
                }

                // 3. Draw Spikes
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < spikes.size(); i++) {
                    RectF s = spikes.get(i);
                    paint.setColor(obstacleColor);
                    spikePath.reset();
                    spikePath.moveTo(s.left, s.bottom);
                    spikePath.lineTo(s.centerX(), s.top);
                    spikePath.lineTo(s.right, s.bottom);
                    spikePath.close();
                    canvas.drawPath(spikePath, paint);

                    paint.setColor(Color.parseColor("#FF4444"));
                    spikeTipPath.reset();
                    spikeTipPath.moveTo(s.left + 15, s.bottom - 15);
                    spikeTipPath.lineTo(s.centerX(), s.top);
                    spikeTipPath.lineTo(s.right - 15, s.bottom - 15);
                    spikeTipPath.close();
                    canvas.drawPath(spikeTipPath, paint);
                }

                // 4. Draw Flyspikes (8-way Blue Spike Star)
                for (Flyspike v : flyspikes) {
                    canvas.save();
                    canvas.translate(v.x, v.y);
                    canvas.rotate(v.rotation);

                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.parseColor("#001f3f"));
                    for(int i = 0; i < 4; i++) {
                        canvas.rotate(90);
                        canvas.drawRect(-3, -v.radius + 8, 3, 0, paint);

                        flyspikePath.reset();
                        flyspikePath.moveTo(0, -v.radius);
                        flyspikePath.lineTo(-8, -v.radius + 10);
                        flyspikePath.lineTo(8, -v.radius + 10);
                        flyspikePath.close();
                        canvas.drawPath(flyspikePath, paint);
                    }

                    canvas.rotate(45);
                    for(int i = 0; i < 4; i++) {
                        canvas.rotate(90);

                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(Color.parseColor("#3399FF"));
                        canvas.drawRect(-6, -v.radius + 10, 6, 0, paint);

                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(Color.parseColor("#001f3f"));
                        paint.setStrokeWidth(2);
                        canvas.drawRect(-6, -v.radius + 10, 6, 0, paint);

                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(Color.parseColor("#000000"));
                        flyspikePath.reset();
                        flyspikePath.moveTo(0, -v.radius);
                        flyspikePath.lineTo(-12, -v.radius + 14);
                        flyspikePath.lineTo(12, -v.radius + 14);
                        flyspikePath.close();
                        canvas.drawPath(flyspikePath, paint);
                    }

                    paint.setColor(Color.parseColor("#00509e"));
                    canvas.drawCircle(0, 0, 10, paint);
                    canvas.restore();
                }

                // 5. Draw Rings
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(obstacleColor);
                paint.setStrokeWidth(10);
                for (int i = 0; i < rings.size(); i++) {
                    Ring r = rings.get(i);
                    if (r.collected) continue;
                    float animWidth = (float) Math.abs(Math.cos(r.animTime)) * 22f + 4f;
                    canvas.drawOval(r.x - animWidth, r.y - 35, r.x + animWidth, r.y + 35, paint);
                }

                // 6. Draw Ball (Visual Immunity Blinking Effect)
                int currentBallAlpha = 255;
                if (System.currentTimeMillis() < immunityEndTime) {
                    currentBallAlpha = ((System.currentTimeMillis() / 150) % 2 == 0) ? 100 : 255;
                }

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ballColor);
                paint.setAlpha(currentBallAlpha);
                canvas.drawCircle(ballX, ballY, BALL_RADIUS, paint);

                paint.setColor(Color.parseColor("#FF8888"));
                paint.setAlpha(currentBallAlpha);
                canvas.drawCircle(ballX - 8, ballY - 8, BALL_RADIUS * 0.35f, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.BLACK);
                paint.setStrokeWidth(3);
                paint.setAlpha(currentBallAlpha);
                canvas.drawCircle(ballX, ballY, BALL_RADIUS, paint);

                paint.setAlpha(255);

                canvas.restore();
            }
        }

        @SuppressWarnings("BusyWait")
        public void resume() {
            if (isPlaying || isGameOver || modeSelectionOverlay.getVisibility() == View.VISIBLE) return;
            isPlaying = true;
            gameThread = new Thread(() -> {
                while (isPlaying) {
                    update(); postInvalidate();
                    try {
                        Thread.sleep(16);
                    } catch (InterruptedException e) {
                        Log.e("MazeBallActivity", "Game thread interrupted", e);
                    }
                }
            });
            gameThread.start();
        }

        public void pause() {
            isPlaying = false;
            try {
                if (gameThread != null && gameThread.isAlive() && gameThread != Thread.currentThread()) {
                    gameThread.join();
                }
            } catch (InterruptedException e) {
                Log.e("MazeBallActivity", "Game thread join interrupted", e);
            }
        }
    }
}