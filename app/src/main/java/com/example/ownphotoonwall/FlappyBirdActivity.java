package com.example.ownphotoonwall;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SuppressLint("SetTextI18n")
public class FlappyBirdActivity extends AppCompatActivity {

    private boolean isDarkTheme;
    private int highScore = 0;
    private SharedPreferences prefs;

    private TextView tvCurrentScore, tvHighScore, tvFinalScore, tvTapToStart, tvGameOverTitle, tvNewHighScoreBanner;
    private RelativeLayout pauseOverlay, gameOverOverlay;
    private FlappyGameEngine gameEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flappy_bird);

        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
        highScore = prefs.getInt("flappy_high_score", 0);

        View root = findViewById(R.id.flappyRoot);
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
        gameEngine = new FlappyGameEngine(this, isDarkTheme);
        gameContainer.addView(gameEngine);

        // Callbacks
        gameEngine.setGameListener(new FlappyGameEngine.GameListener() {
            @Override
            public void onScoreUpdated(int score) {
                tvCurrentScore.setText(String.valueOf(score));
            }

            @Override
            public void onGameOver(int finalScore) {
                // Keep GAME OVER fixed, show banner for high score
                tvGameOverTitle.setText("GAME OVER");
                tvGameOverTitle.setTextColor(isDarkTheme ? Color.WHITE : Color.parseColor("#333333"));

                if (finalScore > highScore && finalScore > 0) {
                    highScore = finalScore;
                    prefs.edit().putInt("flappy_high_score", highScore).apply();
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
    private static class FlappyGameEngine extends View implements Choreographer.FrameCallback {

        private final Paint birdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pipePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float screenW, screenH;
        private float birdX, birdY, birdVelocity;
        private float birdRadius;

        // Dynamic Physics Variables
        private float gravity, jumpStrength, pipeWidth, pipeGap, terminalVelocity;

        // Speed Escalation Variables
        private float pipeSpeed;
        private float basePipeSpeed;
        private float maxPipeSpeed;

        private final List<Pipe> pipes = new ArrayList<>();
        private final Random random = new Random();

        private boolean playing = false;
        private boolean paused = false;
        private boolean gameOver = false;
        private int score = 0;

        private GameListener listener;

        public interface GameListener {
            void onScoreUpdated(int score);
            void onGameOver(int finalScore);
            void onGameStarted();
        }

        public FlappyGameEngine(Context context, boolean isDarkTheme) {
            super(context);

            birdPaint.setColor(isDarkTheme ? Color.parseColor("#FF9F0A") : Color.parseColor("#FF3B30"));
            pipePaint.setColor(isDarkTheme ? Color.parseColor("#32D74B") : Color.parseColor("#34C759"));
            bgPaint.setColor(isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7"));
        }

        public void setGameListener(GameListener listener) {
            this.listener = listener;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            screenW = w;
            screenH = h;

            birdRadius = screenH * 0.025f;
            birdX = screenW * 0.3f;

            // NEW: Floatier physics for that "slow to fast down" feel
            gravity = screenH * 0.0008f;       // Greatly reduced gravity for a gentler curve
            jumpStrength = screenH * -0.015f;  // Balanced jump strength to match the low gravity
            terminalVelocity = screenH * 0.018f; // Capped max fall speed so it doesn't get out of control

            // Speed Escalation Engine Initialization
            basePipeSpeed = screenW * 0.006f;  // Very Slow start speed
            maxPipeSpeed = screenW * 0.015f;   // Medium max speed cap
            pipeSpeed = basePipeSpeed;

            pipeWidth = screenW * 0.18f;
            pipeGap = screenH * 0.28f;

            resetGame();
        }

        public void resetGame() {
            birdY = screenH / 2f;
            birdVelocity = 0;
            pipes.clear();
            score = 0;
            pipeSpeed = basePipeSpeed; // Reset back to slow speed when game restarts
            playing = false;
            paused = false;
            gameOver = false;
            invalidate();
        }

        public void pauseGame() { paused = true; }
        public void resumeGame() { paused = false; Choreographer.getInstance().postFrameCallback(this); }
        public boolean isPlaying() { return playing; }
        public boolean isGameOver() { return gameOver; }

        private void spawnPipe() {
            float minTop = screenH * 0.1f;
            float maxTop = screenH - pipeGap - minTop;
            float topHeight = minTop + random.nextFloat() * (maxTop - minTop);

            pipes.add(new Pipe(screenW, topHeight));
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

            birdVelocity += gravity;
            if (birdVelocity > terminalVelocity) {
                birdVelocity = terminalVelocity;
            }
            birdY += birdVelocity;

            for (int i = 0; i < pipes.size(); i++) {
                Pipe p = pipes.get(i);
                p.x -= pipeSpeed;

                if (!p.passed && p.x + pipeWidth < birdX) {
                    p.passed = true;
                    score++;

                    // Gradually increase the game speed based on score
                    pipeSpeed = basePipeSpeed + (score * (screenW * 0.0004f));
                    if (pipeSpeed > maxPipeSpeed) {
                        pipeSpeed = maxPipeSpeed; // Cap the speed at medium
                    }

                    if (listener != null) listener.onScoreUpdated(score);
                }
            }

            if (!pipes.isEmpty() && pipes.get(0).x + pipeWidth < 0) {
                pipes.remove(0);
            }
            if (pipes.isEmpty() || screenW - pipes.get(pipes.size() - 1).x > (screenW * 0.55f)) {
                spawnPipe();
            }

            checkCollisions();
            invalidate();

            if (!gameOver) {
                Choreographer.getInstance().postFrameCallback(this);
            }
        }

        private void checkCollisions() {
            if (birdY + birdRadius >= screenH || birdY - birdRadius <= 0) {
                triggerGameOver();
                return;
            }

            float hitRadius = birdRadius * 0.75f;
            RectF birdRect = new RectF(birdX - hitRadius, birdY - hitRadius, birdX + hitRadius, birdY + hitRadius);

            for (Pipe p : pipes) {
                RectF topPipe = new RectF(p.x, 0, p.x + pipeWidth, p.topHeight);
                RectF bottomPipe = new RectF(p.x, p.topHeight + pipeGap, p.x + pipeWidth, screenH);

                if (RectF.intersects(birdRect, topPipe) || RectF.intersects(birdRect, bottomPipe)) {
                    triggerGameOver();
                    return;
                }
            }
        }

        private void triggerGameOver() {
            gameOver = true;
            playing = false;
            if (listener != null) listener.onGameOver(score);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);

            canvas.drawRect(0, 0, screenW, screenH, bgPaint);

            for (Pipe p : pipes) {
                canvas.drawRoundRect(p.x, -50f, p.x + pipeWidth, p.topHeight, 20f, 20f, pipePaint);
                canvas.drawRoundRect(p.x, p.topHeight + pipeGap, p.x + pipeWidth, screenH + 50f, 20f, 20f, pipePaint);
            }

            // ===============================================
            // REAL BIRD SHAPE & TILT PHYSICS RENDERER
            // ===============================================
            canvas.save();

            // Calculate Tilt Physics: Tilt up when jumping, dive down when falling
            float tiltAngle;
            if (birdVelocity < 0) {
                tiltAngle = -25f; // Jump tilt
            } else {
                tiltAngle = (birdVelocity / terminalVelocity) * 90f; // Dive tilt mapped to velocity
                if (tiltAngle > 90f) tiltAngle = 90f; // Cap at nose-dive
            }
            canvas.rotate(tiltAngle, birdX, birdY);

            // 1. Draw Beak (Amber color, geometric triangle)
            Paint beakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            beakPaint.setColor(Color.parseColor("#F5B041"));
            Path beakPath = new Path();
            beakPath.moveTo(birdX + birdRadius * 0.4f, birdY - birdRadius * 0.1f);
            beakPath.lineTo(birdX + birdRadius * 1.6f, birdY + birdRadius * 0.2f);
            beakPath.lineTo(birdX + birdRadius * 0.4f, birdY + birdRadius * 0.5f);
            beakPath.close();
            canvas.drawPath(beakPath, beakPaint);

            // 2. Draw Main Body (Slightly elongated oval)
            RectF body = new RectF(birdX - birdRadius * 1.1f, birdY - birdRadius * 0.9f,
                    birdX + birdRadius * 0.9f, birdY + birdRadius * 0.9f);
            canvas.drawOval(body, birdPaint);

            // 3. Draw Wing (Flapping Animation based on Jump Velocity)
            Paint wingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            wingPaint.setColor(Color.WHITE);
            wingPaint.setAlpha(200);

            // Wing positions itself dynamically: down when pushing air, up when gliding
            float wingTop = (birdVelocity < 0) ? (birdY) : (birdY - birdRadius * 0.2f);
            float wingBottom = (birdVelocity < 0) ? (birdY + birdRadius * 0.7f) : (birdY + birdRadius * 0.5f);

            RectF wing = new RectF(birdX - birdRadius * 0.8f, wingTop,
                    birdX - birdRadius * 0.1f, wingBottom);
            canvas.drawOval(wing, wingPaint);

            // 4. Draw Eye (White Base)
            Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            eyePaint.setColor(Color.WHITE);
            canvas.drawCircle(birdX + birdRadius * 0.35f, birdY - birdRadius * 0.4f, birdRadius * 0.35f, eyePaint);

            // 5. Draw Pupil (Black Dot looking forward)
            Paint pupilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            pupilPaint.setColor(Color.BLACK);
            canvas.drawCircle(birdX + birdRadius * 0.5f, birdY - birdRadius * 0.4f, birdRadius * 0.15f, pupilPaint);

            canvas.restore();
        }

        private static class Pipe {
            float x, topHeight;
            boolean passed = false;

            Pipe(float x, float topHeight) {
                this.x = x;
                this.topHeight = topHeight;
            }
        }
    }
}