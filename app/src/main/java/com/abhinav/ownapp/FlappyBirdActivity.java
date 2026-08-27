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
import android.view.ViewGroup;
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

    private static class FlappyGameEngine extends View implements Choreographer.FrameCallback {

        private float screenW, screenH, refW, refH;
        private float birdX, birdY, birdVelocity;
        private float birdRadius, wormRadius;
        private float gravity, jumpStrength, pipeWidth, pipeGap, terminalVelocity;
        private float pipeSpeed, basePipeSpeed, maxPipeSpeed;
        private float parallaxScroll = 0f;

        private long lastFrameTime = 0;

        private final List<Pipe> pipes = new ArrayList<>();
        private boolean playing = false, paused = false, gameOver = false;
        private int score = 0;

        private boolean vibrationEnabled = true;
        private int pointsSinceLastWorm = 0;
        private boolean isImmune = false;
        private long immunityEndTime = 0;
        private float sparkleAngle = 0f;

        private final Bitmap[] birdBitmaps = new Bitmap[3];
        private final Matrix birdMatrix = new Matrix();
        private int currentFrameIndex = 1;
        private float animationTick = 0;
        private final int[] flapSequence = {0, 1, 2, 1};
        private int sequenceIndex = 0;

        private static final int MAX_TRAIL = 15;
        private final TrailPoint[] trail = new TrailPoint[MAX_TRAIL];
        private int trailHead = 0;
        private int trailCount = 0;
        private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final RectF hitRect = new RectF();
        private final RectF tBodyRect = new RectF();
        private final RectF tCapRect = new RectF();
        private final RectF bCapRect = new RectF();
        private final RectF bBodyRect = new RectF();
        private final RectF topCollision = new RectF();
        private final RectF bottomCollision = new RectF();
        private final RectF saucerRect = new RectF();

        private final Paint skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mountainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint pipeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pipeHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pipeShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pipeOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fallbackBirdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint wormGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint wormSparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint wormBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint wormDetailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint wormEyeWhite = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint wormEyeBlack = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint domeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint domeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint saucerFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint saucerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint auraFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint auraStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint starP = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint hudBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint timeText = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final List<Star> stars = new ArrayList<>();
        private final Path mountainPath = new Path();
        private final Path hillPath = new Path();
        private final Path cloudPath = new Path();

        private final int themeState;

        // --- RESTORED GAME LISTENER INTERFACE ---
        private GameListener listener;

        public interface GameListener {
            void onScoreUpdated(int score);
            void onGameOver(int finalScore, Bitmap snapshot);
            void onGameStarted();
        }

        public FlappyGameEngine(Context context, int themeState) {
            super(context);
            this.themeState = themeState;

            for(int i=0; i<MAX_TRAIL; i++) {
                trail[i] = new TrailPoint(0,0);
            }

            trailPaint.setColor(Color.WHITE);
            trailPaint.setStyle(Paint.Style.FILL);
            fallbackBirdPaint.setColor(Color.parseColor("#3498DB"));

            pipeFillPaint.setColor(Color.parseColor("#73BF2E"));
            pipeHighlightPaint.setColor(Color.parseColor("#9AE05B"));
            pipeShadowPaint.setColor(Color.parseColor("#528A22"));
            pipeOutlinePaint.setColor(Color.parseColor("#3A5B1D"));
            pipeOutlinePaint.setStyle(Paint.Style.STROKE);
            pipeOutlinePaint.setStrokeWidth(6f);

            if (themeState == 0) {
                mountainPaint.setColor(Color.parseColor("#A1D4E6"));
                hillPaint.setColor(Color.parseColor("#7CB342"));
                cloudPaint.setColor(Color.parseColor("#FFFFFF"));
            } else if (themeState == 1) {
                mountainPaint.setColor(Color.parseColor("#2C5364"));
                hillPaint.setColor(Color.parseColor("#182825"));
                cloudPaint.setColor(Color.parseColor("#2A3B4C"));
            } else {
                starPaint.setColor(Color.WHITE);
                mountainPaint.setColor(Color.parseColor("#0A0A0A"));
                hillPaint.setColor(Color.parseColor("#111111"));
                cloudPaint.setColor(Color.parseColor("#1A1A1A"));
            }

            wormGlowPaint.setColor(Color.parseColor("#FFD700")); wormGlowPaint.setAlpha(80);
            wormSparklePaint.setColor(Color.WHITE); wormSparklePaint.setStrokeWidth(3f);
            wormBodyPaint.setColor(Color.parseColor("#FFD700"));
            wormDetailPaint.setColor(Color.parseColor("#F39C12"));
            wormEyeWhite.setColor(Color.WHITE);
            wormEyeBlack.setColor(Color.BLACK);

            domeFillPaint.setColor(Color.parseColor("#4400FFFF")); domeFillPaint.setStyle(Paint.Style.FILL);
            domeStrokePaint.setColor(Color.parseColor("#8800FFFF")); domeStrokePaint.setStyle(Paint.Style.STROKE); domeStrokePaint.setStrokeWidth(4f);
            saucerFillPaint.setColor(Color.parseColor("#B0C4DE")); saucerFillPaint.setStyle(Paint.Style.FILL);
            saucerStrokePaint.setColor(Color.parseColor("#39FF14")); saucerStrokePaint.setStyle(Paint.Style.STROKE); saucerStrokePaint.setStrokeWidth(6f);
            lightPaint.setColor(Color.WHITE); lightPaint.setStyle(Paint.Style.FILL);
            auraFillPaint.setColor(Color.parseColor("#FFD700")); auraFillPaint.setAlpha(80); auraFillPaint.setStyle(Paint.Style.FILL);
            auraStrokePaint.setColor(Color.parseColor("#FFD700")); auraStrokePaint.setAlpha(220); auraStrokePaint.setStyle(Paint.Style.STROKE); auraStrokePaint.setStrokeWidth(5f);
            starP.setColor(Color.WHITE); starP.setStyle(Paint.Style.FILL);

            hudBg.setColor(Color.parseColor("#99000000"));
            barBg.setColor(Color.parseColor("#40FFFFFF"));
            barFill.setColor(themeState == 2 ? Color.parseColor("#00FFFF") : Color.parseColor("#FFD700"));
            timeText.setColor(Color.WHITE); timeText.setTextSize(34f); timeText.setTypeface(Typeface.DEFAULT_BOLD); timeText.setTextAlign(Paint.Align.CENTER);
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
            wormRadius = refH * 0.025f;
            birdX = screenW * 0.3f;

            gravity = refH * 0.0008f;
            jumpStrength = refH * -0.015f;
            terminalVelocity = refH * 0.018f;

            basePipeSpeed = refW * 0.006f;
            maxPipeSpeed = refW * 0.015f;
            pipeWidth = refW * 0.18f;
            pipeGap = (screenH > screenW) ? (screenH * 0.30f) : (screenH * 0.48f);

            if (themeState == 0) {
                skyPaint.setShader(new LinearGradient(0, 0, 0, screenH, Color.parseColor("#6DD5FA"), Color.parseColor("#E0F6FF"), Shader.TileMode.CLAMP));
            } else if (themeState == 1) {
                skyPaint.setShader(new LinearGradient(0, 0, 0, screenH, Color.parseColor("#0F2027"), Color.parseColor("#203A43"), Shader.TileMode.CLAMP));
            } else {
                skyPaint.setShader(new LinearGradient(0, 0, 0, screenH, Color.parseColor("#000000"), Color.parseColor("#05050A"), Shader.TileMode.CLAMP));
                stars.clear();
                for (int i = 0; i < 80; i++) {
                    stars.add(new Star((float) Math.random() * screenW, (float) Math.random() * (screenH * 0.7f), (float) Math.random() * 3f + 1f, (float) Math.random(), (float) Math.random() * 0.03f + 0.01f));
                }
            }

            mountainPath.reset(); mountainPath.moveTo(0, screenH * 0.6f); mountainPath.lineTo(screenW * 0.25f, screenH * 0.35f); mountainPath.lineTo(screenW * 0.5f, screenH * 0.6f); mountainPath.lineTo(screenW * 0.75f, screenH * 0.45f); mountainPath.lineTo(screenW, screenH * 0.6f); mountainPath.lineTo(screenW, screenH); mountainPath.lineTo(0, screenH); mountainPath.close();
            hillPath.reset(); hillPath.moveTo(0, screenH * 0.8f); hillPath.quadTo(screenW * 0.25f, screenH * 0.7f, screenW * 0.5f, screenH * 0.8f); hillPath.quadTo(screenW * 0.75f, screenH * 0.9f, screenW, screenH * 0.8f); hillPath.lineTo(screenW, screenH); hillPath.lineTo(0, screenH); hillPath.close();
            cloudPath.reset(); cloudPath.addCircle(screenW * 0.2f, screenH * 0.2f, screenH * 0.05f, Path.Direction.CW); cloudPath.addCircle(screenW * 0.28f, screenH * 0.22f, screenH * 0.04f, Path.Direction.CW); cloudPath.addCircle(screenW * 0.15f, screenH * 0.22f, screenH * 0.03f, Path.Direction.CW); cloudPath.addCircle(screenW * 0.7f, screenH * 0.3f, screenH * 0.06f, Path.Direction.CW); cloudPath.addCircle(screenW * 0.8f, screenH * 0.32f, screenH * 0.04f, Path.Direction.CW); cloudPath.addCircle(screenW * 0.62f, screenH * 0.32f, screenH * 0.04f, Path.Direction.CW);

            loadBirdSprite();

            if (oldWidth == 0 || oldHeight == 0) {
                resetGame();
            } else {
                birdY = oldBirdYRatio * screenH;
                if (birdY < birdRadius) birdY = birdRadius + 10;
                if (birdY > screenH - birdRadius) birdY = screenH - birdRadius - 10;
                pipeSpeed = basePipeSpeed + (score * (refW * 0.0001f));
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
            trailHead = 0;
            trailCount = 0;
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
            lastFrameTime = 0;
            invalidate();
        }

        public void pauseGame() {
            paused = true;
            lastFrameTime = 0;
        }

        public void resumeGame() {
            paused = false;
            lastFrameTime = 0;
            Choreographer.getInstance().postFrameCallback(this);
        }

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
                    lastFrameTime = 0;
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
            if (!playing || paused || gameOver) {
                lastFrameTime = 0;
                return;
            }

            if (lastFrameTime == 0) {
                lastFrameTime = frameTimeNanos;
                Choreographer.getInstance().postFrameCallback(this);
                return;
            }
            float dt = (frameTimeNanos - lastFrameTime) / 1000000000f;
            lastFrameTime = frameTimeNanos;
            if (dt > 0.05f) dt = 0.05f;

            float speedFactor = dt * 60f;

            if (isImmune && System.currentTimeMillis() >= immunityEndTime) {
                isImmune = false;
            }

            sparkleAngle += 12f * speedFactor;
            if (sparkleAngle > 360f) sparkleAngle -= 360f;

            parallaxScroll -= pipeSpeed * speedFactor;

            birdVelocity += gravity * speedFactor;
            if (birdVelocity > terminalVelocity) birdVelocity = terminalVelocity;
            birdY += birdVelocity * speedFactor;

            float flapSpeedThreshold = 8f;

            if (birdVelocity < terminalVelocity * 0.4f) {
                animationTick += speedFactor;
                if (animationTick >= flapSpeedThreshold) {
                    animationTick = 0;
                    sequenceIndex = (sequenceIndex + 1) % flapSequence.length;
                    currentFrameIndex = flapSequence[sequenceIndex];
                }
            } else {
                animationTick += speedFactor;
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

            trailHead = (trailHead + 1) % MAX_TRAIL;
            trail[trailHead].x = tailX;
            trail[trailHead].y = tailY;
            if (trailCount < MAX_TRAIL) trailCount++;

            for (int i = 0; i < trailCount; i++) {
                trail[i].x -= pipeSpeed * speedFactor;
            }

            for (int i = 0; i < pipes.size(); i++) {
                Pipe p = pipes.get(i);
                p.x -= pipeSpeed * speedFactor;

                if (p.hasWorm && !p.wormEaten) {
                    float wormX = p.x + (pipeWidth / 2f);
                    float wormY = p.topHeight + (pipeGap / 2f);
                    float dxx = birdX - wormX;
                    float dyy = birdY - wormY;
                    if ((dxx * dxx) + (dyy * dyy) <= ((birdRadius + wormRadius) * (birdRadius + wormRadius))) {
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
                    if (!isImmune) pointsSinceLastWorm++;

                    pipeSpeed = basePipeSpeed + (score * (refW * 0.0001f));
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

            float hitR = birdRadius * 0.65f;
            hitRect.set(birdX - hitR, birdY - hitR, birdX + hitR, birdY + hitR);

            for (Pipe p : pipes) {
                topCollision.set(p.x, 0, p.x + pipeWidth, p.topHeight);
                bottomCollision.set(p.x, p.topHeight + pipeGap, p.x + pipeWidth, screenH);
                if (RectF.intersects(hitRect, topCollision) || RectF.intersects(hitRect, bottomCollision)) {
                    triggerGameOver(); return;
                }
            }
        }

        private Bitmap getDeathSnapshot() {
            try {
                Bitmap fullBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(fullBitmap);
                this.draw(canvas);

                int size = (int) (Math.min(getWidth(), getHeight()) * 0.40f);
                if (size <= 0) size = 300;

                int left = (int) (birdX - size / 2f);
                int top = (int) (birdY - size / 2f);

                if (left < 0) left = 0;
                if (top < 0) top = 0;
                if (left + size > getWidth()) left = getWidth() - size;
                if (top + size > getHeight()) top = getHeight() - size;

                Bitmap cropped = Bitmap.createBitmap(fullBitmap, left, top, size, size);
                fullBitmap.recycle();
                return cropped;
            } catch (Exception e) {
                return null;
            }
        }

        private void triggerGameOver() {
            if (gameOver) return;
            gameOver = true;
            playing = false;
            lastFrameTime = 0;

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

            Bitmap snapshot = getDeathSnapshot();
            if (listener != null) listener.onGameOver(score, snapshot);
        }

        private void drawTiledPath(Canvas canvas, Path path, float rawScroll, float speedMult, Paint paint) {
            float scrollOffset = (rawScroll * speedMult) % screenW;
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
            canvas.drawCircle(cx, cy, r * 1.8f, wormGlowPaint);
            canvas.save();
            canvas.rotate(sparkleAngle, cx, cy);
            for (int i = 0; i < 4; i++) {
                canvas.rotate(90, cx, cy);
                canvas.drawLine(cx, cy - r * 1.5f, cx, cy - r * 2.2f, wormSparklePaint);
                canvas.drawCircle(cx, cy - r * 1.8f, 3f, wormSparklePaint);
            }
            canvas.restore();

            canvas.drawCircle(cx - r * 0.6f, cy + r * 0.3f, r * 0.5f, wormDetailPaint);
            canvas.drawCircle(cx - r * 0.6f, cy + r * 0.3f, r * 0.4f, wormBodyPaint);
            canvas.drawCircle(cx, cy, r * 0.6f, wormDetailPaint);
            canvas.drawCircle(cx, cy, r * 0.5f, wormBodyPaint);
            canvas.drawCircle(cx + r * 0.6f, cy - r * 0.2f, r * 0.7f, wormDetailPaint);
            canvas.drawCircle(cx + r * 0.6f, cy - r * 0.2f, r * 0.6f, wormBodyPaint);

            canvas.drawCircle(cx + r * 0.8f, cy - r * 0.35f, r * 0.2f, wormEyeWhite);
            canvas.drawCircle(cx + r * 0.85f, cy - r * 0.35f, r * 0.1f, wormEyeBlack);
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

            float capH = refH * 0.04f;
            float capExt = refW * 0.015f;

            for (Pipe p : pipes) {
                tBodyRect.set(p.x, -20f, p.x + pipeWidth, p.topHeight - capH);
                tCapRect.set(p.x - capExt, p.topHeight - capH, p.x + pipeWidth + capExt, p.topHeight);
                bCapRect.set(p.x - capExt, p.topHeight + pipeGap, p.x + pipeWidth + capExt, p.topHeight + pipeGap + capH);
                bBodyRect.set(p.x, p.topHeight + pipeGap + capH, p.x + pipeWidth, screenH + 20f);

                canvas.drawRect(tBodyRect, pipeFillPaint);
                canvas.drawRect(tBodyRect.left + 8f, tBodyRect.top + 4f, tBodyRect.left + 8f + (tBodyRect.width() * 0.15f), tBodyRect.bottom - 4f, pipeHighlightPaint);
                canvas.drawRect(tBodyRect.right - 8f - (tBodyRect.width() * 0.25f), tBodyRect.top + 4f, tBodyRect.right - 8f, tBodyRect.bottom - 4f, pipeShadowPaint);
                canvas.drawRect(tBodyRect, pipeOutlinePaint);
                canvas.drawRect(tCapRect, pipeFillPaint);
                canvas.drawRect(tCapRect.left + 8f, tCapRect.top + 4f, tCapRect.left + 8f + (tCapRect.width() * 0.15f), tCapRect.bottom - 4f, pipeHighlightPaint);
                canvas.drawRect(tCapRect.right - 8f - (tCapRect.width() * 0.25f), tCapRect.top + 4f, tCapRect.right - 8f, tCapRect.bottom - 4f, pipeShadowPaint);
                canvas.drawRect(tCapRect, pipeOutlinePaint);
                canvas.drawRect(bCapRect, pipeFillPaint);
                canvas.drawRect(bCapRect.left + 8f, bCapRect.top + 4f, bCapRect.left + 8f + (bCapRect.width() * 0.15f), bCapRect.bottom - 4f, pipeHighlightPaint);
                canvas.drawRect(bCapRect.right - 8f - (bCapRect.width() * 0.25f), bCapRect.top + 4f, bCapRect.right - 8f, bCapRect.bottom - 4f, pipeShadowPaint);
                canvas.drawRect(bCapRect, pipeOutlinePaint);
                canvas.drawRect(bBodyRect, pipeFillPaint);
                canvas.drawRect(bBodyRect.left + 8f, bBodyRect.top + 4f, bBodyRect.left + 8f + (bBodyRect.width() * 0.15f), bBodyRect.bottom - 4f, pipeHighlightPaint);
                canvas.drawRect(bBodyRect.right - 8f - (bBodyRect.width() * 0.25f), bBodyRect.top + 4f, bBodyRect.right - 8f, bBodyRect.bottom - 4f, pipeShadowPaint);
                canvas.drawRect(bBodyRect, pipeOutlinePaint);

                if (p.hasWorm && !p.wormEaten) {
                    drawGoldenWorm(canvas, p.x + (pipeWidth / 2f), p.topHeight + (pipeGap / 2f), wormRadius);
                }
            }

            if (trailCount > 0) {
                for (int i = 0; i < trailCount; i++) {
                    int idx = (trailHead - i + MAX_TRAIL) % MAX_TRAIL;
                    TrailPoint t = trail[idx];
                    int alpha = (int) (200 * (1f - ((float) i / trailCount)));
                    float radius = birdRadius * 0.35f * (1f - ((float) i / trailCount));
                    trailPaint.setAlpha(alpha);
                    canvas.drawCircle(t.x, t.y, radius, trailPaint);
                }
            }

            if (isImmune) {
                if (themeState == 2) {
                    canvas.drawCircle(birdX, birdY, birdRadius * 2.0f, domeFillPaint);
                    canvas.drawCircle(birdX, birdY, birdRadius * 2.0f, domeStrokePaint);
                    saucerRect.set(birdX - birdRadius * 2.8f, birdY - birdRadius * 0.6f, birdX + birdRadius * 2.8f, birdY + birdRadius * 0.6f);
                    canvas.drawOval(saucerRect, saucerFillPaint);
                    canvas.drawOval(saucerRect, saucerStrokePaint);
                    canvas.drawCircle(birdX - birdRadius * 1.8f, birdY, 6f, lightPaint);
                    canvas.drawCircle(birdX + birdRadius * 1.8f, birdY, 6f, lightPaint);
                    canvas.drawCircle(birdX, birdY + birdRadius * 0.4f, 7f, lightPaint);
                } else {
                    canvas.drawCircle(birdX, birdY, birdRadius * 1.8f, auraFillPaint);
                    canvas.drawCircle(birdX, birdY, birdRadius * 1.8f, auraStrokePaint);
                    canvas.save();
                    canvas.translate(birdX, birdY);
                    canvas.rotate(sparkleAngle);
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

                canvas.drawRoundRect(barX - 24f, barY - 48f, barX + barW + 24f, barY + barH + 16f, 30f, 30f, hudBg);
                canvas.drawRoundRect(barX, barY, barX + barW, barY + barH, 11f, 11f, barBg);
                canvas.drawRoundRect(barX, barY, barX + (barW * progress), barY + barH, 11f, 11f, barFill);

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

        private static class Star {
            float x, y, radius, alpha, twinkleSpeed;
            Star(float x, float y, float r, float a, float ts) {
                this.x = x; this.y = y; this.radius = r; this.alpha = a; this.twinkleSpeed = ts;
            }
        }
    }
}