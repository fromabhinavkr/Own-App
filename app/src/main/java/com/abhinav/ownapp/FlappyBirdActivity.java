package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("all")
@SuppressLint("SetTextI18n")
public class FlappyBirdActivity extends AppCompatActivity {

    private int themeState; // 3-State Theme Variable
    private int highScore = 0;
    private boolean isVibrationEnabled = true;
    private SharedPreferences prefs;

    // UI Elements
    private TextView tvCurrentScore, tvHighScore, tvFinalScore, tvTapToStart, tvGameOverTitle, tvNewHighScoreBanner;
    private RelativeLayout pauseOverlay, gameOverOverlay;
    private FlappyGameEngine gameEngine;

    // Smart Pill Button Components
    private LinearLayout btnPause;
    private FrameLayout pauseIconContainer;
    private GameIconView pauseIconView;
    private int iconColor;

    private FrameLayout snapshotContainer;
    private ImageView ivDeathSnapshot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Smooth opening animation for the Activity
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_flappy_bird);

        View root = findViewById(R.id.flappyRoot);
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

        highScore = prefs.getInt("flappy_high_score", 0);
        isVibrationEnabled = prefs.getBoolean("flappy_vibration_enabled", true);

        FrameLayout gameContainer = findViewById(R.id.gameContainer);

        // Map UI
        btnPause = findViewById(R.id.btnPause);
        pauseIconContainer = findViewById(R.id.pauseIconContainer);
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

        Button btnResume = findViewById(R.id.btnResume);
        Button btnRestart = findViewById(R.id.btnRestart);
        Button btnQuit = findViewById(R.id.btnQuit);
        Button btnQuitFromPause = findViewById(R.id.btnQuitFromPause);
        Button btnToggleVibration = findViewById(R.id.btnToggleVibration);

        // --- Snapshot Elements ---
        snapshotContainer = findViewById(R.id.snapshotContainer);
        ivDeathSnapshot = findViewById(R.id.ivDeathSnapshot);

        // Define colors based on the 3-state theme
        int bgColor, cardColor, textColor, subTextColor, quitBtnColor;
        int pillBgColor, boxBgColor;

        if (themeState == 0) { // Light Mode
            bgColor = Color.parseColor("#F2F2F7");
            cardColor = Color.WHITE;
            textColor = Color.parseColor("#333333");
            subTextColor = Color.parseColor("#888888");
            quitBtnColor = Color.parseColor("#E5E5EA");
            pillBgColor = Color.parseColor("#E5E5EA");
            boxBgColor = Color.parseColor("#FFFFFF");
            iconColor = Color.parseColor("#333333");
        } else if (themeState == 1) { // Standard Dark Mode
            bgColor = Color.parseColor("#1C1C1E");
            cardColor = Color.parseColor("#2C2C2E");
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#8E8E93");
            quitBtnColor = Color.parseColor("#3A3A3C");
            pillBgColor = Color.parseColor("#2D313A");
            boxBgColor = Color.parseColor("#D8E2FF");
            iconColor = Color.parseColor("#001C3A");
        } else { // Star Mode (AMOLED Pure Black)
            bgColor = Color.parseColor("#000000"); // Pure Black background
            cardColor = Color.parseColor("#1C1C1E"); // Elevated dark gray
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#8E8E93");
            quitBtnColor = Color.parseColor("#2C2C2E");
            pillBgColor = Color.parseColor("#2D313A");
            boxBgColor = Color.parseColor("#D8E2FF");
            iconColor = Color.parseColor("#001C3A");
        }

        // Apply background and text colors
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

        // Apply Custom "Smart Pill" styling for Pause/Score
        if (btnPause != null) {
            btnPause.setBackground(createPillShape(pillBgColor));
            pauseIconContainer.setBackground(createBoxShape(boxBgColor));

            // Inject mathematical Vector Icon
            pauseIconView = new GameIconView(this);
            pauseIconContainer.addView(pauseIconView);
            pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
        }

        GradientDrawable gdCard = new GradientDrawable();
        gdCard.setColor(cardColor);
        gdCard.setCornerRadius(60f);
        pauseCard.setBackground(gdCard);
        gameOverCard.setBackground(gdCard);

        // Apply Themed Styling to the new Death Snapshot Container
        GradientDrawable snapBg = new GradientDrawable();
        snapBg.setColor(bgColor); // Set background to match theme sky fallback
        snapBg.setCornerRadius(50f); // Beautifully rounded corners
        snapBg.setStroke(8, quitBtnColor); // Border color strictly matches the theme button color
        snapshotContainer.setBackground(snapBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            snapshotContainer.setClipToOutline(true); // Clips the inner ImageView perfectly to the rounded borders!
        }

        // Setup Vibration Button
        btnToggleVibration.setText("Vibration: " + (isVibrationEnabled ? "ON" : "OFF"));
        btnToggleVibration.setOnClickListener(v -> {
            isVibrationEnabled = !isVibrationEnabled;
            prefs.edit().putBoolean("flappy_vibration_enabled", isVibrationEnabled).apply();
            btnToggleVibration.setText("Vibration: " + (isVibrationEnabled ? "ON" : "OFF"));
            if (gameEngine != null) {
                gameEngine.setVibrationEnabled(isVibrationEnabled);
            }
        });

        // Initialize engine with the new 3-State integer
        gameEngine = new FlappyGameEngine(this, themeState);
        gameEngine.setVibrationEnabled(isVibrationEnabled);
        gameContainer.addView(gameEngine);

        gameEngine.setGameListener(new FlappyGameEngine.GameListener() {
            @Override
            public void onScoreUpdated(int score) {
                tvCurrentScore.setText("Score: " + score);
            }

            @Override
            public void onGameOver(int finalScore, Bitmap snapshot) {
                tvGameOverTitle.setText("GAME OVER");
                tvGameOverTitle.setTextColor(textColor);

                if (finalScore > highScore && finalScore > 0) {
                    highScore = finalScore;
                    prefs.edit().putInt("flappy_high_score", highScore).apply();
                    tvHighScore.setText("Best: " + highScore);
                    tvNewHighScoreBanner.setVisibility(View.VISIBLE);
                } else {
                    tvNewHighScoreBanner.setVisibility(View.GONE);
                }

                // Show the proof of hit!
                if (snapshot != null) {
                    ivDeathSnapshot.setImageBitmap(snapshot);
                    snapshotContainer.setVisibility(View.VISIBLE);
                } else {
                    snapshotContainer.setVisibility(View.GONE);
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

        btnPause.setOnClickListener(v -> {
            gameEngine.pauseGame();
            pauseIconView.setIcon(GameIconView.ICON_PLAY, iconColor);
            showOverlaySmoothly(pauseOverlay);
            fadeOutHudSmoothly(btnPause);
        });

        btnResume.setOnClickListener(v -> {
            hideOverlaySmoothly(pauseOverlay);
            fadeInHudSmoothly(btnPause);
            pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
            gameEngine.resumeGame();
        });

        btnRestart.setOnClickListener(v -> {
            hideOverlaySmoothly(gameOverOverlay);
            tvNewHighScoreBanner.setVisibility(View.GONE);
            tvCurrentScore.setText("Score: 0");
            tvTapToStart.setVisibility(View.VISIBLE);
            fadeInHudSmoothly(btnPause);
            pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
            gameEngine.resetGame();
        });

        btnQuit.setOnClickListener(v -> finish());
        btnQuitFromPause.setOnClickListener(v -> finish());
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

    private void fadeOutHudSmoothly(View hudView) {
        if (hudView != null) {
            hudView.animate().alpha(0f).setDuration(250).withEndAction(() -> hudView.setVisibility(View.INVISIBLE)).start();
        }
    }

    private void fadeInHudSmoothly(View hudView) {
        if (hudView != null) {
            hudView.setAlpha(0f);
            hudView.setVisibility(View.VISIBLE);
            hudView.animate().alpha(1f).setDuration(250).start();
        }
    }

    @Override
    public void finish() {
        super.finish();
        // Removes the android.R.anim.fade_in to completely eliminate the "white flash" bug when exiting back to the menu!
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
            } else if (iconType == ICON_PLAY) {
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

    // =========================================================================================
    // PURE CANVAS ENGINE (Dynamic Parallax BG, Programmatic Pipes, Sprite Bird, Immunity)
    // =========================================================================================
    private static class FlappyGameEngine extends View implements Choreographer.FrameCallback {

        // --- Core Physics ---
        private float screenW, screenH, refW, refH;
        private float birdX, birdY, birdVelocity;
        private float birdRadius, wormRadius;
        private float gravity, jumpStrength, pipeWidth, pipeGap, terminalVelocity;
        private float pipeSpeed, basePipeSpeed, maxPipeSpeed;
        private float parallaxScroll = 0f;

        private final List<Pipe> pipes = new ArrayList<>();
        private boolean playing = false, paused = false, gameOver = false;
        private int score = 0;

        // --- Settings & Immunity Variables ---
        private boolean vibrationEnabled = true;
        private int pointsSinceLastWorm = 0;
        private boolean isImmune = false;
        private long immunityEndTime = 0;
        private float sparkleAngle = 0f;

        // --- BIRD SPRITE ANIMATION LOGIC ---
        private final Bitmap[] birdBitmaps = new Bitmap[3];
        private final Matrix birdMatrix = new Matrix();
        private int currentFrameIndex = 1; // Start with middle wing
        private int animationTick = 0;
        private final int[] flapSequence = {0, 1, 2, 1}; // Pendulum flap cycle (Up, Mid, Down, Mid)
        private int sequenceIndex = 0;

        // --- Trail Effect ---
        private final LinkedList<TrailPoint> trail = new LinkedList<>();
        private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // --- Programmatic Environment Paints ---
        private final Paint skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mountainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // --- Starry Night Elements ---
        private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Star> stars = new ArrayList<>();

        private final Paint pipeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pipeHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pipeShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pipeOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fallbackBirdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // --- Seamless Looping Paths ---
        private final Path mountainPath = new Path();
        private final Path hillPath = new Path();
        private final Path cloudPath = new Path();

        private final int themeState;
        private GameListener listener;

        public interface GameListener {
            void onScoreUpdated(int score);
            void onGameOver(int finalScore, Bitmap snapshot);
            void onGameStarted();
        }

        public FlappyGameEngine(Context context, int themeState) {
            super(context);
            this.themeState = themeState;

            // Trail Effect
            trailPaint.setColor(Color.WHITE);
            trailPaint.setStyle(Paint.Style.FILL);

            // Fallback bird color
            fallbackBirdPaint.setColor(Color.parseColor("#3498DB"));

            // Setup gorgeous programmatic 3D pipe colors
            pipeFillPaint.setColor(Color.parseColor("#73BF2E"));
            pipeHighlightPaint.setColor(Color.parseColor("#9AE05B"));
            pipeShadowPaint.setColor(Color.parseColor("#528A22"));
            pipeOutlinePaint.setColor(Color.parseColor("#3A5B1D"));
            pipeOutlinePaint.setStyle(Paint.Style.STROKE);
            pipeOutlinePaint.setStrokeWidth(6f);

            // Setup Parallax Environment Colors based on Theme State
            if (themeState == 0) { // Light
                mountainPaint.setColor(Color.parseColor("#A1D4E6"));
                hillPaint.setColor(Color.parseColor("#7CB342"));
                cloudPaint.setColor(Color.parseColor("#FFFFFF"));
            } else if (themeState == 1) { // Dark
                mountainPaint.setColor(Color.parseColor("#2C5364"));
                hillPaint.setColor(Color.parseColor("#182825"));
                cloudPaint.setColor(Color.parseColor("#2A3B4C"));
            } else { // AMOLED Star Mode
                starPaint.setColor(Color.WHITE);
                mountainPaint.setColor(Color.parseColor("#0A0A0A")); // Silhouette
                hillPaint.setColor(Color.parseColor("#111111")); // Silhouette
                cloudPaint.setColor(Color.parseColor("#1A1A1A")); // Very dark, barely visible clouds
            }
        }

        public void setGameListener(GameListener listener) { this.listener = listener; }

        public void setVibrationEnabled(boolean enabled) { this.vibrationEnabled = enabled; }

        private void loadBirdSprite() {
            try {
                int[] resIds = {
                        getResources().getIdentifier("flappy_bird_blue_up", "drawable", getContext().getPackageName()),
                        getResources().getIdentifier("flappy_bird_blue", "drawable", getContext().getPackageName()),
                        getResources().getIdentifier("flappy_bird_blue_down", "drawable", getContext().getPackageName())
                };

                int birdSize = (int) (birdRadius * 2.8f);
                for (int i = 0; i < 3; i++) {
                    if (resIds[i] != 0) {
                        Bitmap raw = BitmapFactory.decodeResource(getResources(), resIds[i]);
                        birdBitmaps[i] = Bitmap.createScaledBitmap(raw, birdSize, birdSize, true);
                    }
                }
            } catch (Exception ignored) {}
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            float oldBirdYRatio = (oldHeight > 0) ? (birdY / (float) oldHeight) : 0.5f;
            screenW = w; screenH = h;

            refH = Math.max(screenW, screenH);
            refW = Math.min(screenW, screenH);

            birdRadius = refH * 0.035f;
            wormRadius = refH * 0.025f; // Perfect size for the worm to fit between pipes
            birdX = screenW * 0.3f;

            gravity = refH * 0.0008f;
            jumpStrength = refH * -0.015f;
            terminalVelocity = refH * 0.018f;
            basePipeSpeed = refW * 0.006f;
            maxPipeSpeed = refW * 0.015f;
            pipeWidth = refW * 0.18f;
            pipeGap = (screenH > screenW) ? (screenH * 0.30f) : (screenH * 0.48f);

            // 1. Generate Beautiful Sky Gradient & Stars
            if (themeState == 0) {
                skyPaint.setShader(new LinearGradient(0, 0, 0, screenH, Color.parseColor("#6DD5FA"), Color.parseColor("#E0F6FF"), Shader.TileMode.CLAMP));
            } else if (themeState == 1) {
                skyPaint.setShader(new LinearGradient(0, 0, 0, screenH, Color.parseColor("#0F2027"), Color.parseColor("#203A43"), Shader.TileMode.CLAMP));
            } else { // Star Mode
                skyPaint.setShader(new LinearGradient(0, 0, 0, screenH, Color.parseColor("#000000"), Color.parseColor("#05050A"), Shader.TileMode.CLAMP));
                stars.clear();
                for (int i = 0; i < 80; i++) {
                    stars.add(new Star(
                            (float) Math.random() * screenW,
                            (float) Math.random() * (screenH * 0.7f),
                            (float) Math.random() * 3f + 1f,
                            (float) Math.random(),
                            (float) Math.random() * 0.03f + 0.01f
                    ));
                }
            }

            // 2. Generate Seamless Looping Mountains
            mountainPath.reset();
            mountainPath.moveTo(0, screenH * 0.6f);
            mountainPath.lineTo(screenW * 0.25f, screenH * 0.35f);
            mountainPath.lineTo(screenW * 0.5f, screenH * 0.6f);
            mountainPath.lineTo(screenW * 0.75f, screenH * 0.45f);
            mountainPath.lineTo(screenW, screenH * 0.6f);
            mountainPath.lineTo(screenW, screenH);
            mountainPath.lineTo(0, screenH);
            mountainPath.close();

            // 3. Generate Seamless Looping Foreground Hills
            hillPath.reset();
            hillPath.moveTo(0, screenH * 0.8f);
            hillPath.quadTo(screenW * 0.25f, screenH * 0.7f, screenW * 0.5f, screenH * 0.8f);
            hillPath.quadTo(screenW * 0.75f, screenH * 0.9f, screenW, screenH * 0.8f);
            hillPath.lineTo(screenW, screenH);
            hillPath.lineTo(0, screenH);
            hillPath.close();

            // 4. Generate Fluffy Clouds
            cloudPath.reset();
            cloudPath.addCircle(screenW * 0.2f, screenH * 0.2f, screenH * 0.05f, Path.Direction.CW);
            cloudPath.addCircle(screenW * 0.28f, screenH * 0.22f, screenH * 0.04f, Path.Direction.CW);
            cloudPath.addCircle(screenW * 0.15f, screenH * 0.22f, screenH * 0.03f, Path.Direction.CW);

            cloudPath.addCircle(screenW * 0.7f, screenH * 0.3f, screenH * 0.06f, Path.Direction.CW);
            cloudPath.addCircle(screenW * 0.8f, screenH * 0.32f, screenH * 0.04f, Path.Direction.CW);
            cloudPath.addCircle(screenW * 0.62f, screenH * 0.32f, screenH * 0.04f, Path.Direction.CW);

            loadBirdSprite();

            if (oldWidth == 0 || oldHeight == 0) {
                resetGame();
            } else {
                birdY = oldBirdYRatio * screenH;
                if (birdY < birdRadius) birdY = birdRadius + 10;
                if (birdY > screenH - birdRadius) birdY = screenH - birdRadius - 10;
                pipeSpeed = basePipeSpeed + (score * (refW * 0.0004f));
                if (pipeSpeed > maxPipeSpeed) pipeSpeed = maxPipeSpeed;
                for (Pipe p : pipes) {
                    float maxTop = screenH - pipeGap - (screenH * 0.1f);
                    if (p.topHeight > maxTop) p.topHeight = Math.max(screenH * 0.1f, maxTop);
                }
            }
        }

        public void resetGame() {
            birdY = screenH / 2f;
            birdVelocity = 0;
            pipes.clear();
            trail.clear();
            score = 0;
            pipeSpeed = basePipeSpeed;
            pointsSinceLastWorm = 0;
            isImmune = false;
            immunityEndTime = 0;
            playing = false;
            paused = false;
            gameOver = false;
            currentFrameIndex = 1;
            sequenceIndex = 0;
            animationTick = 0;
            invalidate();
        }

        public void pauseGame() { paused = true; }
        public void resumeGame() { paused = false; Choreographer.getInstance().postFrameCallback(this); }
        public boolean isPlaying() { return playing; }
        public boolean isGameOver() { return gameOver; }

        private void spawnPipe() {
            float minTop = screenH * 0.1f;
            float maxTop = screenH - pipeGap - minTop;
            float topHeight = minTop + (float) Math.random() * (maxTop - minTop);

            Pipe newPipe = new Pipe(screenW, topHeight);

            if (!isImmune && pointsSinceLastWorm >= 5) {
                newPipe.hasWorm = true;
                pointsSinceLastWorm = 0;
            }
            pipes.add(newPipe);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (gameOver || paused) return true;
                if (!playing) {
                    playing = true;
                    if (listener != null) listener.onGameStarted();
                    Choreographer.getInstance().postFrameCallback(this);
                }
                birdVelocity = jumpStrength;
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!playing || paused || gameOver) return;

            if (isImmune && System.currentTimeMillis() >= immunityEndTime) {
                isImmune = false;
            }

            sparkleAngle += 12f;
            if (sparkleAngle > 360f) sparkleAngle -= 360f;

            parallaxScroll -= pipeSpeed;

            birdVelocity += gravity;
            if (birdVelocity > terminalVelocity) birdVelocity = terminalVelocity;
            birdY += birdVelocity;

            // --- SMOOTH WING ANIMATION PHYSICS ---
            int flapSpeedThreshold = 8;

            if (birdVelocity < terminalVelocity * 0.4f) {
                animationTick++;
                if (animationTick >= flapSpeedThreshold) {
                    animationTick = 0;
                    sequenceIndex = (sequenceIndex + 1) % flapSequence.length;
                    currentFrameIndex = flapSequence[sequenceIndex];
                }
            } else {
                animationTick++;
                if (animationTick >= flapSpeedThreshold) {
                    currentFrameIndex = 0;
                    sequenceIndex = 0;
                }
            }

            float tiltAngle;
            if (birdVelocity < 0) tiltAngle = -25f;
            else { tiltAngle = (birdVelocity / terminalVelocity) * 90f; if (tiltAngle > 90f) tiltAngle = 90f; }

            float tailOffset = birdRadius * 0.75f;
            float tailX = birdX - (float) Math.cos(Math.toRadians(tiltAngle)) * tailOffset;
            float tailY = birdY - (float) Math.sin(Math.toRadians(tiltAngle)) * tailOffset;

            trail.addFirst(new TrailPoint(tailX, tailY));
            if (trail.size() > 15) trail.removeLast();
            for (TrailPoint t : trail) { t.x -= pipeSpeed; }

            for (int i = 0; i < pipes.size(); i++) {
                Pipe p = pipes.get(i);
                p.x -= pipeSpeed;

                if (p.hasWorm && !p.wormEaten) {
                    float wormX = p.x + (pipeWidth / 2f);
                    float wormY = p.topHeight + (pipeGap / 2f);
                    float dx = birdX - wormX;
                    float dy = birdY - wormY;
                    if ((dx * dx) + (dy * dy) <= ((birdRadius + wormRadius) * (birdRadius + wormRadius))) {
                        p.wormEaten = true;
                        isImmune = true;
                        immunityEndTime = System.currentTimeMillis() + 4000;

                        if (vibrationEnabled) {
                            Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
                            if (vibrator != null && vibrator.hasVibrator()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                                } else {
                                    vibrator.vibrate(50);
                                }
                            }
                        }
                    }
                }

                if (!p.passed && p.x + pipeWidth < birdX) {
                    p.passed = true;
                    score++;

                    if (!isImmune) {
                        pointsSinceLastWorm++;
                    }

                    pipeSpeed = basePipeSpeed + (score * (refW * 0.0004f));
                    if (pipeSpeed > maxPipeSpeed) pipeSpeed = maxPipeSpeed;
                    if (listener != null) listener.onScoreUpdated(score);
                }
            }

            if (!pipes.isEmpty() && pipes.get(0).x + pipeWidth < 0) pipes.remove(0);
            if (pipes.isEmpty() || screenW - pipes.get(pipes.size() - 1).x > (screenW * 0.55f)) spawnPipe();

            checkCollisions();
            invalidate();

            if (!gameOver) Choreographer.getInstance().postFrameCallback(this);
        }

        private void checkCollisions() {
            if (birdY + birdRadius >= screenH || birdY - birdRadius <= 0) {
                if (isImmune) {
                    if (birdY - birdRadius <= 0) { birdY = birdRadius + 1; birdVelocity = 0; }
                    if (birdY + birdRadius >= screenH) { birdY = screenH - birdRadius - 1; birdVelocity = jumpStrength; }
                    return;
                } else {
                    triggerGameOver(); return;
                }
            }

            if (isImmune) return;

            float hitRadius = birdRadius * 0.65f;
            RectF birdRect = new RectF(birdX - hitRadius, birdY - hitRadius, birdX + hitRadius, birdY + hitRadius);

            for (Pipe p : pipes) {
                RectF topPipe = new RectF(p.x, 0, p.x + pipeWidth, p.topHeight);
                RectF bottomPipe = new RectF(p.x, p.topHeight + pipeGap, p.x + pipeWidth, screenH);
                if (RectF.intersects(birdRect, topPipe) || RectF.intersects(birdRect, bottomPipe)) {
                    triggerGameOver(); return;
                }
            }
        }

        // --- Method to smoothly capture the exact moment of death without lag ---
        private Bitmap getDeathSnapshot() {
            try {
                // Instantly grab the screen
                Bitmap fullBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(fullBitmap);
                this.draw(canvas);

                // Define the square crop size (about 40% of the screen width for a good zoomed-in look)
                int size = (int) (Math.min(getWidth(), getHeight()) * 0.40f);
                if (size <= 0) size = 300;

                // Center the crop box exactly on the bird
                int left = (int) (birdX - size / 2f);
                int top = (int) (birdY - size / 2f);

                // Clamp to screen edges so it doesn't crash if hitting the top/bottom boundary
                if (left < 0) left = 0;
                if (top < 0) top = 0;
                if (left + size > getWidth()) left = getWidth() - size;
                if (top + size > getHeight()) top = getHeight() - size;

                Bitmap cropped = Bitmap.createBitmap(fullBitmap, left, top, size, size);
                fullBitmap.recycle(); // Free huge screen memory immediately
                return cropped;
            } catch (Exception e) {
                return null;
            }
        }

        private void triggerGameOver() {
            if (gameOver) return; // Prevent multiple triggers
            gameOver = true;
            playing = false;

            if (vibrationEnabled) {
                Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(800);
                    }
                }
            }

            // Capture the proof of death before notifying the menu
            Bitmap snapshot = getDeathSnapshot();
            if (listener != null) listener.onGameOver(score, snapshot);
        }

        private void drawTiledPath(Canvas canvas, Path path, float rawScroll, float scrollSpeedMultiplier, Paint paint) {
            float scrollOffset = (rawScroll * scrollSpeedMultiplier) % screenW;
            if (scrollOffset > 0) scrollOffset -= screenW;

            canvas.save();
            canvas.translate(scrollOffset, 0);
            canvas.drawPath(path, paint);
            canvas.translate(screenW, 0);
            canvas.drawPath(path, paint);
            canvas.restore();
        }

        private void drawProgrammaticPipe(Canvas canvas, RectF rect) {
            canvas.drawRect(rect, pipeFillPaint);

            float highlightWidth = rect.width() * 0.15f;
            canvas.drawRect(rect.left + 8f, rect.top + 4f, rect.left + 8f + highlightWidth, rect.bottom - 4f, pipeHighlightPaint);

            float shadowWidth = rect.width() * 0.25f;
            canvas.drawRect(rect.right - 8f - shadowWidth, rect.top + 4f, rect.right - 8f, rect.bottom - 4f, pipeShadowPaint);

            canvas.drawRect(rect, pipeOutlinePaint);
        }

        private void drawGoldenWorm(Canvas canvas, float cx, float cy, float r) {
            canvas.save();
            Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG); glowPaint.setColor(Color.parseColor("#FFD700")); glowPaint.setAlpha(80);
            canvas.drawCircle(cx, cy, r * 1.8f, glowPaint);

            canvas.save();
            canvas.rotate(sparkleAngle, cx, cy);
            Paint sparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG); sparklePaint.setColor(Color.WHITE); sparklePaint.setStrokeWidth(3f);
            for (int i = 0; i < 4; i++) {
                canvas.rotate(90, cx, cy); canvas.drawLine(cx, cy - r * 1.5f, cx, cy - r * 2.2f, sparklePaint);
                canvas.drawCircle(cx, cy - r * 1.8f, 3f, sparklePaint);
            }
            canvas.restore();

            Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG); bodyPaint.setColor(Color.parseColor("#FFD700"));
            Paint detailPaint = new Paint(Paint.ANTI_ALIAS_FLAG); detailPaint.setColor(Color.parseColor("#F39C12"));
            canvas.drawCircle(cx - r * 0.6f, cy + r * 0.3f, r * 0.5f, detailPaint); canvas.drawCircle(cx - r * 0.6f, cy + r * 0.3f, r * 0.4f, bodyPaint);
            canvas.drawCircle(cx, cy, r * 0.6f, detailPaint); canvas.drawCircle(cx, cy, r * 0.5f, bodyPaint);
            canvas.drawCircle(cx + r * 0.6f, cy - r * 0.2f, r * 0.7f, detailPaint); canvas.drawCircle(cx + r * 0.6f, cy - r * 0.2f, r * 0.6f, bodyPaint);

            Paint eyeWhite = new Paint(Paint.ANTI_ALIAS_FLAG); eyeWhite.setColor(Color.WHITE);
            canvas.drawCircle(cx + r * 0.8f, cy - r * 0.35f, r * 0.2f, eyeWhite);
            Paint eyeBlack = new Paint(Paint.ANTI_ALIAS_FLAG); eyeBlack.setColor(Color.BLACK);
            canvas.drawCircle(cx + r * 0.85f, cy - r * 0.35f, r * 0.1f, eyeBlack);
            canvas.restore();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);

            canvas.drawRect(0, 0, screenW, screenH, skyPaint);

            if (themeState == 2) {
                for (Star s : stars) {
                    s.alpha += s.twinkleSpeed;
                    if (s.alpha > 1f) { s.alpha = 1f; s.twinkleSpeed = -s.twinkleSpeed; }
                    else if (s.alpha < 0.1f) { s.alpha = 0.1f; s.twinkleSpeed = -s.twinkleSpeed; }

                    starPaint.setAlpha((int) (s.alpha * 255));
                    canvas.drawCircle(s.x, s.y, s.radius, starPaint);
                }
            }

            drawTiledPath(canvas, cloudPath, parallaxScroll, 0.15f, cloudPaint);
            drawTiledPath(canvas, mountainPath, parallaxScroll, 0.35f, mountainPaint);
            drawTiledPath(canvas, hillPath, parallaxScroll, 0.6f, hillPaint);

            float capHeight = refH * 0.04f;
            float capExtend = refW * 0.015f;

            for (Pipe p : pipes) {
                RectF topBody = new RectF(p.x, -20f, p.x + pipeWidth, p.topHeight - capHeight);
                RectF topCap = new RectF(p.x - capExtend, p.topHeight - capHeight, p.x + pipeWidth + capExtend, p.topHeight);
                RectF bottomCap = new RectF(p.x - capExtend, p.topHeight + pipeGap, p.x + pipeWidth + capExtend, p.topHeight + pipeGap + capHeight);
                RectF bottomBody = new RectF(p.x, p.topHeight + pipeGap + capHeight, p.x + pipeWidth, screenH + 20f);

                drawProgrammaticPipe(canvas, topBody);
                drawProgrammaticPipe(canvas, topCap);
                drawProgrammaticPipe(canvas, bottomBody);
                drawProgrammaticPipe(canvas, bottomCap);

                if (p.hasWorm && !p.wormEaten) {
                    drawGoldenWorm(canvas, p.x + (pipeWidth / 2f), p.topHeight + (pipeGap / 2f), wormRadius);
                }
            }

            if (!trail.isEmpty()) {
                int index = 0;
                for (TrailPoint t : trail) {
                    int alpha = (int) (200 * (1f - ((float) index / trail.size())));
                    float radius = birdRadius * 0.35f * (1f - ((float) index / trail.size()));
                    trailPaint.setAlpha(alpha);
                    canvas.drawCircle(t.x, t.y, radius, trailPaint);
                    index++;
                }
            }

            if (isImmune) {
                if (themeState == 2) {
                    Paint domePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    domePaint.setColor(Color.parseColor("#4400FFFF"));
                    canvas.drawCircle(birdX, birdY, birdRadius * 2.0f, domePaint);

                    domePaint.setStyle(Paint.Style.STROKE);
                    domePaint.setStrokeWidth(4f);
                    domePaint.setColor(Color.parseColor("#8800FFFF"));
                    canvas.drawCircle(birdX, birdY, birdRadius * 2.0f, domePaint);

                    Paint saucerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    saucerPaint.setColor(Color.parseColor("#B0C4DE"));
                    RectF saucerRect = new RectF(birdX - birdRadius * 2.8f, birdY - birdRadius * 0.6f, birdX + birdRadius * 2.8f, birdY + birdRadius * 0.6f);
                    canvas.drawOval(saucerRect, saucerPaint);

                    saucerPaint.setStyle(Paint.Style.STROKE);
                    saucerPaint.setStrokeWidth(6f);
                    saucerPaint.setColor(Color.parseColor("#39FF14"));
                    canvas.drawOval(saucerRect, saucerPaint);

                    Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    lightPaint.setColor(Color.parseColor("#FFFFFF"));
                    canvas.drawCircle(birdX - birdRadius * 1.8f, birdY, 6f, lightPaint);
                    canvas.drawCircle(birdX + birdRadius * 1.8f, birdY, 6f, lightPaint);
                    canvas.drawCircle(birdX, birdY + birdRadius * 0.4f, 7f, lightPaint);

                } else {
                    Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    auraPaint.setColor(Color.parseColor("#FFD700")); auraPaint.setAlpha(80);
                    canvas.drawCircle(birdX, birdY, birdRadius * 1.8f, auraPaint);
                    auraPaint.setStyle(Paint.Style.STROKE); auraPaint.setStrokeWidth(5f); auraPaint.setAlpha(220);
                    canvas.drawCircle(birdX, birdY, birdRadius * 1.8f, auraPaint);

                    canvas.save();
                    canvas.translate(birdX, birdY);
                    canvas.rotate(sparkleAngle);
                    Paint starP = new Paint(Paint.ANTI_ALIAS_FLAG); starP.setColor(Color.WHITE);
                    for (int s = 0; s < 4; s++) { canvas.rotate(90); canvas.drawCircle(0, -birdRadius * 1.8f, 6f, starP); }
                    canvas.restore();
                }
            }

            float tiltAngle;
            if (birdVelocity < 0) tiltAngle = -25f;
            else { tiltAngle = (birdVelocity / terminalVelocity) * 90f; if (tiltAngle > 90f) tiltAngle = 90f; }

            if (birdBitmaps[0] != null && birdBitmaps[1] != null && birdBitmaps[2] != null) {
                Bitmap currentFrame = birdBitmaps[currentFrameIndex];
                birdMatrix.reset();
                birdMatrix.postTranslate(-currentFrame.getWidth() / 2f, -currentFrame.getHeight() / 2f);
                birdMatrix.postRotate(tiltAngle);
                birdMatrix.postTranslate(birdX, birdY);
                canvas.drawBitmap(currentFrame, birdMatrix, null);
            } else {
                canvas.save();
                canvas.rotate(tiltAngle, birdX, birdY);
                canvas.drawCircle(birdX, birdY, birdRadius, fallbackBirdPaint);
                canvas.restore();
            }

            if (isImmune && immunityEndTime > System.currentTimeMillis()) {
                long timeLeft = immunityEndTime - System.currentTimeMillis();
                float progress = Math.max(0f, Math.min(1f, timeLeft / 4000f));

                float barW = screenW * 0.55f;
                float barH = 22f;
                float barX = (screenW - barW) / 2f;
                float barY = screenH * 0.14f;

                Paint hudBg = new Paint(Paint.ANTI_ALIAS_FLAG);
                hudBg.setColor(Color.parseColor("#99000000"));
                canvas.drawRoundRect(barX - 24f, barY - 48f, barX + barW + 24f, barY + barH + 16f, 30f, 30f, hudBg);

                Paint barBg = new Paint(Paint.ANTI_ALIAS_FLAG);
                barBg.setColor(Color.parseColor("#40FFFFFF"));
                canvas.drawRoundRect(barX, barY, barX + barW, barY + barH, 11f, 11f, barBg);

                Paint barFill = new Paint(Paint.ANTI_ALIAS_FLAG);
                barFill.setColor(themeState == 2 ? Color.parseColor("#00FFFF") : Color.parseColor("#FFD700"));
                canvas.drawRoundRect(barX, barY, barX + (barW * progress), barY + barH, 11f, 11f, barFill);

                Paint timeText = new Paint(Paint.ANTI_ALIAS_FLAG);
                timeText.setColor(Color.WHITE);
                timeText.setTextSize(34f);
                timeText.setTypeface(Typeface.DEFAULT_BOLD);
                timeText.setTextAlign(Paint.Align.CENTER);

                String emoji = (themeState == 2) ? "🛸" : "⭐";
                String timeStr = String.format(Locale.US, "%s IMMUNITY: %.1fs %s", emoji, timeLeft / 1000f, emoji);
                canvas.drawText(timeStr, screenW / 2f, barY - 14f, timeText);
            }
        }

        private static class Pipe {
            float x, topHeight; boolean passed = false;
            boolean hasWorm = false; boolean wormEaten = false;
            Pipe(float x, float topHeight) { this.x = x; this.topHeight = topHeight; }
        }

        private static class TrailPoint {
            float x, y;
            TrailPoint(float x, float y) { this.x = x; this.y = y; }
        }

        // --- FIXED: ADDED THE MISSING 'float' BEFORE 'y' ---
        private static class Star {
            float x, y, radius, alpha, twinkleSpeed;
            Star(float x, float y, float r, float a, float ts) {
                this.x = x; this.y = y; this.radius = r; this.alpha = a; this.twinkleSpeed = ts;
            }
        }
    }
}