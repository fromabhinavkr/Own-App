package com.abhinav.ownapp;

import android.annotation.SuppressLint; import android.content.Context; import android.content.SharedPreferences;
import android.content.res.Configuration; import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint; import android.graphics.Path; import android.graphics.RectF; import android.graphics.Typeface; import android.graphics.drawable.GradientDrawable; import android.os.Bundle; import android.view.Choreographer; import android.view.MotionEvent; import android.view.View; import android.widget.Button; import android.widget.FrameLayout; import android.widget.LinearLayout; import android.widget.RelativeLayout; import android.widget.TextView;
import androidx.annotation.NonNull; import androidx.activity.EdgeToEdge; import androidx.appcompat.app.AppCompatActivity; import androidx.core.graphics.Insets; import androidx.core.view.ViewCompat; import androidx.core.view.WindowInsetsCompat;
import java.util.ArrayList; import java.util.List; import java.util.Locale; import java.util.Random;

@SuppressWarnings("all") @SuppressLint("SetTextI18n")
public class FlappyBirdActivity extends AppCompatActivity {

    private boolean isDarkTheme; private int highScore = 0; private SharedPreferences prefs;
    private TextView tvCurrentScore, tvHighScore, tvFinalScore, tvTapToStart, tvGameOverTitle, tvNewHighScoreBanner;
    private RelativeLayout pauseOverlay, gameOverOverlay; private FlappyGameEngine gameEngine;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_flappy_bird);
        View root = findViewById(R.id.flappyRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE); isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true); highScore = prefs.getInt("flappy_high_score", 0);
        FrameLayout gameContainer = findViewById(R.id.gameContainer);
        tvCurrentScore = findViewById(R.id.tvCurrentScore); tvHighScore = findViewById(R.id.tvHighScore); tvFinalScore = findViewById(R.id.tvFinalScore); tvTapToStart = findViewById(R.id.tvTapToStart); tvGameOverTitle = findViewById(R.id.tvGameOverTitle); tvNewHighScoreBanner = findViewById(R.id.tvNewHighScoreBanner);
        pauseOverlay = findViewById(R.id.pauseOverlay); gameOverOverlay = findViewById(R.id.gameOverOverlay);
        LinearLayout pauseCard = findViewById(R.id.pauseCard); LinearLayout gameOverCard = findViewById(R.id.gameOverCard);
        Button btnPause = findViewById(R.id.btnPause); Button btnResume = findViewById(R.id.btnResume); Button btnRestart = findViewById(R.id.btnRestart); Button btnQuit = findViewById(R.id.btnQuit); Button btnQuitFromPause = findViewById(R.id.btnQuitFromPause);

        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7"); int cardColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE; int textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");
        root.setBackgroundColor(bgColor); tvCurrentScore.setTextColor(textColor); tvHighScore.setTextColor(textColor); tvTapToStart.setTextColor(textColor); tvHighScore.setText("Best: " + highScore);
        ((TextView) findViewById(R.id.tvPauseTitle)).setTextColor(textColor); tvGameOverTitle.setTextColor(textColor); tvFinalScore.setTextColor(textColor);
        btnQuit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA"))); btnQuit.setTextColor(textColor);
        GradientDrawable gdCard = new GradientDrawable(); gdCard.setColor(cardColor); gdCard.setCornerRadius(60f); pauseCard.setBackground(gdCard); gameOverCard.setBackground(gdCard);

        gameEngine = new FlappyGameEngine(this, isDarkTheme); gameContainer.addView(gameEngine);
        gameEngine.setGameListener(new FlappyGameEngine.GameListener() {
            @Override public void onScoreUpdated(int score) { tvCurrentScore.setText(String.valueOf(score)); }
            @Override public void onGameOver(int finalScore) {
                tvGameOverTitle.setText("GAME OVER"); tvGameOverTitle.setTextColor(isDarkTheme ? Color.WHITE : Color.parseColor("#333333"));
                if (finalScore > highScore && finalScore > 0) { highScore = finalScore; prefs.edit().putInt("flappy_high_score", highScore).apply(); tvHighScore.setText("Best: " + highScore); tvNewHighScoreBanner.setVisibility(View.VISIBLE); } else { tvNewHighScoreBanner.setVisibility(View.GONE); }
                tvFinalScore.setText("Score: " + finalScore); gameOverOverlay.setVisibility(View.VISIBLE); btnPause.setVisibility(View.GONE);
            }
            @Override public void onGameStarted() { tvTapToStart.setVisibility(View.GONE); btnPause.setVisibility(View.VISIBLE); }
        });

        btnPause.setOnClickListener(v -> { gameEngine.pauseGame(); pauseOverlay.setVisibility(View.VISIBLE); btnPause.setVisibility(View.GONE); });
        btnResume.setOnClickListener(v -> { pauseOverlay.setVisibility(View.GONE); btnPause.setVisibility(View.VISIBLE); gameEngine.resumeGame(); });
        btnRestart.setOnClickListener(v -> { gameOverOverlay.setVisibility(View.GONE); tvNewHighScoreBanner.setVisibility(View.GONE); tvCurrentScore.setText("0"); tvTapToStart.setVisibility(View.VISIBLE); btnPause.setVisibility(View.VISIBLE); gameEngine.resetGame(); });
        btnQuit.setOnClickListener(v -> finish()); btnQuitFromPause.setOnClickListener(v -> finish());
    }

    @Override public void onConfigurationChanged(@NonNull Configuration newConfig) { super.onConfigurationChanged(newConfig); /* Engine naturally recomputes rotation in onSizeChanged */ }

    @Override protected void onPause() { super.onPause(); if (gameEngine != null && gameEngine.isPlaying() && !gameEngine.isGameOver()) { findViewById(R.id.btnPause).performClick(); } }

    private static class FlappyGameEngine extends View implements Choreographer.FrameCallback {
        private final Paint birdPaint = new Paint(Paint.ANTI_ALIAS_FLAG); private final Paint pipePaint = new Paint(Paint.ANTI_ALIAS_FLAG); private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float screenW, screenH, refW, refH; private float birdX, birdY, birdVelocity; private float birdRadius, wormRadius;
        private float gravity, jumpStrength, pipeWidth, pipeGap, terminalVelocity;
        private float pipeSpeed; private float basePipeSpeed; private float maxPipeSpeed;
        private final List<Pipe> pipes = new ArrayList<>(); private final Random random = new Random();
        private boolean playing = false; private boolean paused = false; private boolean gameOver = false; private int score = 0;

        // --- GOLDEN WORM & IMMUNITY VARIABLES ---
        private int pointsSinceLastWorm = 0;
        private boolean isImmune = false;
        private long immunityEndTime = 0;
        private float sparkleAngle = 0f;

        private GameListener listener;

        public interface GameListener { void onScoreUpdated(int score); void onGameOver(int finalScore); void onGameStarted(); }

        public FlappyGameEngine(Context context, boolean isDarkTheme) {
            super(context);
            birdPaint.setColor(isDarkTheme ? Color.parseColor("#FF9F0A") : Color.parseColor("#FF3B30"));
            pipePaint.setColor(isDarkTheme ? Color.parseColor("#32D74B") : Color.parseColor("#34C759"));
            bgPaint.setColor(isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7"));
        }

        public void setGameListener(GameListener listener) { this.listener = listener; }

        @Override protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            float oldBirdYRatio = (oldHeight > 0) ? (birdY / (float) oldHeight) : 0.5f;
            screenW = w; screenH = h;

            // Invariant Scaling: refH and refW guarantee identical bird size & speeds in both orientations!
            refH = Math.max(screenW, screenH);
            refW = Math.min(screenW, screenH);

            birdRadius = refH * 0.025f; wormRadius = refH * 0.022f; birdX = screenW * 0.3f;
            gravity = refH * 0.0008f; jumpStrength = refH * -0.015f; terminalVelocity = refH * 0.018f;
            basePipeSpeed = refW * 0.006f; maxPipeSpeed = refW * 0.015f;
            pipeWidth = refW * 0.18f;
            pipeGap = (screenH > screenW) ? (screenH * 0.28f) : (screenH * 0.48f); // Adaptive gap for landscape room

            if (oldWidth == 0 || oldHeight == 0) {
                resetGame();
            } else {
                // Seamlessly preserve mid-flight position across rotation without resetting score!
                birdY = oldBirdYRatio * screenH;
                if (birdY < birdRadius) birdY = birdRadius + 10;
                if (birdY > screenH - birdRadius) birdY = screenH - birdRadius - 10;
                pipeSpeed = basePipeSpeed + (score * (refW * 0.0004f)); if (pipeSpeed > maxPipeSpeed) pipeSpeed = maxPipeSpeed;
                for (Pipe p : pipes) {
                    float maxTop = screenH - pipeGap - (screenH * 0.1f);
                    if (p.topHeight > maxTop) p.topHeight = Math.max(screenH * 0.1f, maxTop);
                }
            }
        }

        public void resetGame() {
            birdY = screenH / 2f; birdVelocity = 0; pipes.clear(); score = 0; pipeSpeed = basePipeSpeed;
            pointsSinceLastWorm = 0; isImmune = false; immunityEndTime = 0;
            playing = false; paused = false; gameOver = false; invalidate();
        }

        public void pauseGame() { paused = true; }
        public void resumeGame() { paused = false; Choreographer.getInstance().postFrameCallback(this); }
        public boolean isPlaying() { return playing; }
        public boolean isGameOver() { return gameOver; }

        private void spawnPipe() {
            float minTop = screenH * 0.1f; float maxTop = screenH - pipeGap - minTop;
            float topHeight = minTop + random.nextFloat() * (maxTop - minTop);
            Pipe newPipe = new Pipe(screenW, topHeight);
            if (!isImmune && pointsSinceLastWorm >= 5) {
                newPipe.hasWorm = true;
                pointsSinceLastWorm = 0;
            }
            pipes.add(newPipe);
        }

        @SuppressLint("ClickableViewAccessibility") @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (gameOver || paused) return true;
                if (!playing) { playing = true; if (listener != null) listener.onGameStarted(); Choreographer.getInstance().postFrameCallback(this); }
                birdVelocity = jumpStrength; return true;
            }
            return super.onTouchEvent(event);
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!playing || paused || gameOver) return;

            if (isImmune && System.currentTimeMillis() >= immunityEndTime) {
                isImmune = false;
            }

            sparkleAngle += 12f; if (sparkleAngle > 360f) sparkleAngle -= 360f;

            birdVelocity += gravity; if (birdVelocity > terminalVelocity) birdVelocity = terminalVelocity; birdY += birdVelocity;

            for (int i = 0; i < pipes.size(); i++) {
                Pipe p = pipes.get(i); p.x -= pipeSpeed;

                if (p.hasWorm && !p.wormEaten) {
                    float wormX = p.x + (pipeWidth / 2f); float wormY = p.topHeight + (pipeGap / 2f);
                    float dx = birdX - wormX; float dy = birdY - wormY;
                    if ((dx * dx) + (dy * dy) <= ((birdRadius + wormRadius) * (birdRadius + wormRadius))) {
                        p.wormEaten = true;
                        isImmune = true;
                        immunityEndTime = System.currentTimeMillis() + 4000;
                    }
                }

                if (!p.passed && p.x + pipeWidth < birdX) {
                    p.passed = true; score++;
                    if (!isImmune) {
                        pointsSinceLastWorm++;
                    }
                    pipeSpeed = basePipeSpeed + (score * (refW * 0.0004f)); if (pipeSpeed > maxPipeSpeed) pipeSpeed = maxPipeSpeed;
                    if (listener != null) listener.onScoreUpdated(score);
                }
            }
            if (!pipes.isEmpty() && pipes.get(0).x + pipeWidth < 0) pipes.remove(0);
            if (pipes.isEmpty() || screenW - pipes.get(pipes.size() - 1).x > (screenW * 0.55f)) spawnPipe();
            checkCollisions(); invalidate();
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

            float hitRadius = birdRadius * 0.75f; RectF birdRect = new RectF(birdX - hitRadius, birdY - hitRadius, birdX + hitRadius, birdY + hitRadius);
            for (Pipe p : pipes) {
                RectF topPipe = new RectF(p.x, 0, p.x + pipeWidth, p.topHeight); RectF bottomPipe = new RectF(p.x, p.topHeight + pipeGap, p.x + pipeWidth, screenH);
                if (RectF.intersects(birdRect, topPipe) || RectF.intersects(birdRect, bottomPipe)) { triggerGameOver(); return; }
            }
        }

        private void triggerGameOver() { gameOver = true; playing = false; if (listener != null) listener.onGameOver(score); }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas); canvas.drawRect(0, 0, screenW, screenH, bgPaint);
            for (Pipe p : pipes) {
                canvas.drawRoundRect(p.x, -50f, p.x + pipeWidth, p.topHeight, 20f, 20f, pipePaint);
                canvas.drawRoundRect(p.x, p.topHeight + pipeGap, p.x + pipeWidth, screenH + 50f, 20f, 20f, pipePaint);

                if (p.hasWorm && !p.wormEaten) {
                    drawGoldenWorm(canvas, p.x + (pipeWidth / 2f), p.topHeight + (pipeGap / 2f), wormRadius);
                }
            }

            canvas.save();
            float tiltAngle; if (birdVelocity < 0) tiltAngle = -25f; else { tiltAngle = (birdVelocity / terminalVelocity) * 90f; if (tiltAngle > 90f) tiltAngle = 90f; }
            canvas.rotate(tiltAngle, birdX, birdY);

            if (isImmune) {
                Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                auraPaint.setColor(Color.parseColor("#FFD700")); auraPaint.setAlpha(80);
                canvas.drawCircle(birdX, birdY, birdRadius * 1.8f, auraPaint);
                auraPaint.setStyle(Paint.Style.STROKE); auraPaint.setStrokeWidth(5f); auraPaint.setAlpha(220);
                canvas.drawCircle(birdX, birdY, birdRadius * 1.8f, auraPaint);

                canvas.save();
                canvas.rotate(sparkleAngle, birdX, birdY);
                Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG); starPaint.setColor(Color.WHITE);
                for (int s = 0; s < 4; s++) { canvas.rotate(90, birdX, birdY); canvas.drawCircle(birdX, birdY - birdRadius * 1.8f, 6f, starPaint); }
                canvas.restore();
            }

            Paint beakPaint = new Paint(Paint.ANTI_ALIAS_FLAG); beakPaint.setColor(Color.parseColor("#F5B041"));
            Path beakPath = new Path(); beakPath.moveTo(birdX + birdRadius * 0.4f, birdY - birdRadius * 0.1f); beakPath.lineTo(birdX + birdRadius * 1.6f, birdY + birdRadius * 0.2f); beakPath.lineTo(birdX + birdRadius * 0.4f, birdY + birdRadius * 0.5f); beakPath.close();
            canvas.drawPath(beakPath, beakPaint);
            RectF body = new RectF(birdX - birdRadius * 1.1f, birdY - birdRadius * 0.9f, birdX + birdRadius * 0.9f, birdY + birdRadius * 0.9f); canvas.drawOval(body, birdPaint);
            Paint wingPaint = new Paint(Paint.ANTI_ALIAS_FLAG); wingPaint.setColor(Color.WHITE); wingPaint.setAlpha(200);
            float wingTop = (birdVelocity < 0) ? (birdY) : (birdY - birdRadius * 0.2f); float wingBottom = (birdVelocity < 0) ? (birdY + birdRadius * 0.7f) : (birdY + birdRadius * 0.5f);
            RectF wing = new RectF(birdX - birdRadius * 0.8f, wingTop, birdX - birdRadius * 0.1f, wingBottom); canvas.drawOval(wing, wingPaint);
            Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG); eyePaint.setColor(Color.WHITE); canvas.drawCircle(birdX + birdRadius * 0.35f, birdY - birdRadius * 0.4f, birdRadius * 0.35f, eyePaint);
            Paint pupilPaint = new Paint(Paint.ANTI_ALIAS_FLAG); pupilPaint.setColor(Color.BLACK); canvas.drawCircle(birdX + birdRadius * 0.5f, birdY - birdRadius * 0.4f, birdRadius * 0.15f, pupilPaint);
            canvas.restore();

            // ========================================================
            // LIVE IMMUNITY TIMER HUD RENDERER (TOP-CENTER OF SCREEN)
            // ========================================================
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
                barFill.setColor(Color.parseColor("#FFD700"));
                canvas.drawRoundRect(barX, barY, barX + (barW * progress), barY + barH, 11f, 11f, barFill);

                Paint timeText = new Paint(Paint.ANTI_ALIAS_FLAG);
                timeText.setColor(Color.WHITE);
                timeText.setTextSize(34f);
                timeText.setTypeface(Typeface.DEFAULT_BOLD);
                timeText.setTextAlign(Paint.Align.CENTER);
                String timeStr = String.format(Locale.US, "⭐ IMMUNITY: %.1fs ⭐", timeLeft / 1000f);
                canvas.drawText(timeStr, screenW / 2f, barY - 14f, timeText);
            }
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

        private static class Pipe {
            float x, topHeight; boolean passed = false; boolean hasWorm = false; boolean wormEaten = false;
            Pipe(float x, float topHeight) { this.x = x; this.topHeight = topHeight; }
        }
    }
}