package com.example.ownphotoonwall;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Random;

public class SnakeGameActivity extends Activity {

    private SnakeGameEngine gameEngine;
    private Button btnDifficulty;
    private Button btnPause;
    private boolean isDarkTheme;

    // Difficulty Speeds (Milliseconds per frame)
    private static final int SPEED_EASY = 220;
    private static final int SPEED_MEDIUM = 130;
    private static final int SPEED_HARD = 70;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_snake_game);

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        // Setup the Native UI Overlay
        btnDifficulty = findViewById(R.id.btnDifficulty);
        btnPause = findViewById(R.id.btnPause);

        // Sync Button Colors to Theme
        if (isDarkTheme) {
            ColorStateList darkBg = ColorStateList.valueOf(Color.parseColor("#3A3A3C"));
            int darkText = Color.WHITE;
            btnDifficulty.setBackgroundTintList(darkBg);
            btnDifficulty.setTextColor(darkText);
            btnPause.setBackgroundTintList(darkBg);
            btnPause.setTextColor(darkText);
        } else {
            ColorStateList lightBg = ColorStateList.valueOf(Color.parseColor("#E5E5EA"));
            int lightText = Color.parseColor("#333333");
            btnDifficulty.setBackgroundTintList(lightBg);
            btnDifficulty.setTextColor(lightText);
            btnPause.setBackgroundTintList(lightBg);
            btnPause.setTextColor(lightText);
        }

        // Setup the Game Engine Canvas
        FrameLayout container = findViewById(R.id.game_container);
        gameEngine = new SnakeGameEngine(this, isDarkTheme, prefs);
        container.addView(gameEngine);

        btnDifficulty.setOnClickListener(v -> showDifficultyDialog());

        btnPause.setOnClickListener(v -> {
            boolean isNowPaused = gameEngine.togglePause();
            btnPause.setText(isNowPaused ? R.string.game_resume : R.string.game_pause);
        });
    }

    private void showDifficultyDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_difficulty, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout dialogRoot = dialogView.findViewById(R.id.dialogRoot);
        TextView tvTitle = dialogView.findViewById(R.id.tvTitle);
        Button btnEasy = dialogView.findViewById(R.id.btnEasy);
        Button btnMedium = dialogView.findViewById(R.id.btnMedium);
        Button btnHard = dialogView.findViewById(R.id.btnHard);

        int btnBgColor = isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA");
        int textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");

        if (isDarkTheme) {
            dialogRoot.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2C2C2E")));
        } else {
            dialogRoot.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        }

        tvTitle.setTextColor(textColor);
        Button[] buttons = {btnEasy, btnMedium, btnHard};
        for (Button b : buttons) {
            b.setBackgroundTintList(ColorStateList.valueOf(btnBgColor));
            b.setTextColor(textColor);
        }

        btnEasy.setOnClickListener(v -> {
            btnDifficulty.setText(R.string.diff_easy);
            gameEngine.setSpeed(SPEED_EASY);
            dialog.dismiss();
        });

        btnMedium.setOnClickListener(v -> {
            btnDifficulty.setText(R.string.diff_medium);
            gameEngine.setSpeed(SPEED_MEDIUM);
            dialog.dismiss();
        });

        btnHard.setOnClickListener(v -> {
            btnDifficulty.setText(R.string.diff_hard);
            gameEngine.setSpeed(SPEED_HARD);
            dialog.dismiss();
        });

        dialog.show();
    }

    // --- INNER CLASS: THE GAME ENGINE ---
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

        private int score = 0;
        private int highScore = 0;
        private boolean isNewHighScore = false;

        private int currentSpeed = SPEED_MEDIUM;
        private final int GRID_SIZE = 20;
        private int blockSize;

        private final int bgColor;
        private final int snakeColor;
        private final int appleColor;
        private final int textColor;

        private float startX, startY;
        private final RectF appleRect = new RectF();
        private final RectF bodyRect = new RectF();

        public SnakeGameEngine(Context context, boolean isDarkTheme, SharedPreferences preferences) {
            super(context);
            this.prefs = preferences;
            this.highScore = prefs.getInt("snake_high_score", 0);

            if (isDarkTheme) {
                bgColor = Color.parseColor("#000000");
                snakeColor = Color.parseColor("#FFFFFF");
                appleColor = Color.parseColor("#FF3B30");
                textColor = Color.WHITE;
            } else {
                bgColor = Color.parseColor("#FFFFFF");
                snakeColor = Color.parseColor("#000000");
                appleColor = Color.parseColor("#FF3B30");
                textColor = Color.BLACK;
            }

            initGame();
        }

        public void setSpeed(int speed) {
            this.currentSpeed = speed;
        }

        public boolean togglePause() {
            if (isGameOver) return false;
            isPaused = !isPaused;
            if (!isPaused) {
                // If unpausing, ensure the loop is running
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(gameLoop, currentSpeed);
            }
            invalidate(); // Force a redraw to show/hide "PAUSED" text
            return isPaused;
        }

        private void initGame() {
            snake.clear();
            snake.add(new Point(5, 5));
            snake.add(new Point(4, 5));
            snake.add(new Point(3, 5));
            direction = 0;
            nextDirection = 0;
            score = 0;
            isGameOver = false;
            isPaused = false;
            isNewHighScore = false;

            // Reset Pause button text visually if restarting
            if (btnPause != null) {
                btnPause.setText(R.string.game_pause);
            }

            spawnApple();

            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(gameLoop, currentSpeed);
        }

        private void spawnApple() {
            int x = random.nextInt(GRID_SIZE);
            int y = random.nextInt(GRID_SIZE + 10);
            apple = new Point(x, y);
        }

        private final Runnable gameLoop = new Runnable() {
            @Override
            public void run() {
                if (!isGameOver) {
                    if (!isPaused) {
                        moveSnake();
                        checkCollisions();
                    }
                    invalidate();
                    handler.postDelayed(this, currentSpeed);
                }
            }
        };

        private void moveSnake() {
            direction = nextDirection;
            Point head = snake.get(0);
            Point newHead = new Point(head.x, head.y);

            switch (direction) {
                case 0: newHead.x++; break;
                case 1: newHead.y++; break;
                case 2: newHead.x--; break;
                case 3: newHead.y--; break;
            }

            snake.add(0, newHead);

            if (newHead.x == apple.x && newHead.y == apple.y) {
                score += 10;
                spawnApple();
            } else {
                snake.remove(snake.size() - 1);
            }
        }

        private void checkCollisions() {
            Point head = snake.get(0);

            if (head.x < 0 || head.x >= GRID_SIZE || head.y < 0 || head.y >= (getHeight() / blockSize)) {
                triggerGameOver();
            }

            for (int i = 1; i < snake.size(); i++) {
                if (head.x == snake.get(i).x && head.y == snake.get(i).y) {
                    triggerGameOver();
                }
            }
        }

        private void triggerGameOver() {
            isGameOver = true;
            if (score > highScore) {
                highScore = score;
                isNewHighScore = true;
                prefs.edit().putInt("snake_high_score", highScore).apply();
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            blockSize = w / GRID_SIZE;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(bgColor);

            paint.setColor(appleColor);
            appleRect.set(apple.x * blockSize + 2, apple.y * blockSize + 2,
                    (apple.x + 1) * blockSize - 2, (apple.y + 1) * blockSize - 2);
            canvas.drawRoundRect(appleRect, 16f, 16f, paint);

            paint.setColor(snakeColor);
            for (Point p : snake) {
                bodyRect.set(p.x * blockSize + 2, p.y * blockSize + 2,
                        (p.x + 1) * blockSize - 2, (p.y + 1) * blockSize - 2);
                canvas.drawRoundRect(bodyRect, 16f, 16f, paint);
            }

            // Draw Live Score
            paint.setColor(textColor);
            paint.setTextSize(60f);
            paint.setFakeBoldText(true);
            canvas.drawText("Score: " + score, 40f, 100f, paint);

            // Draw Pause Overlay
            if (isPaused && !isGameOver) {
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(100f);
                canvas.drawText("PAUSED", getWidth() / 2f, getHeight() / 2f, paint);
                paint.setTextAlign(Paint.Align.LEFT);
            }

            // Draw Modern Game Over Screen
            if (isGameOver) {
                paint.setTextAlign(Paint.Align.CENTER);

                paint.setTextSize(100f);
                canvas.drawText("GAME OVER", getWidth() / 2f, getHeight() / 2f - 120f, paint);

                paint.setTextSize(60f);
                canvas.drawText("Score: " + score, getWidth() / 2f, getHeight() / 2f - 20f, paint);
                canvas.drawText("High Score: " + highScore, getWidth() / 2f, getHeight() / 2f + 60f, paint);

                if (isNewHighScore) {
                    paint.setColor(Color.parseColor("#4CD964")); // Success Green for new records
                    paint.setTextSize(65f);
                    canvas.drawText("🏆 New High Scorer! 🏆", getWidth() / 2f, getHeight() / 2f + 160f, paint);
                    paint.setColor(textColor); // Revert color for next line
                }

                paint.setTextSize(45f);
                paint.setFakeBoldText(false);
                canvas.drawText("Tap anywhere to Restart", getWidth() / 2f, getHeight() / 2f + 280f, paint);
                paint.setTextAlign(Paint.Align.LEFT);
            }
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        @Override
        public boolean onTouchEvent(@NonNull MotionEvent event) {
            if (isGameOver && event.getAction() == MotionEvent.ACTION_DOWN) {
                initGame();
                return true;
            }

            if (isPaused) {
                return true; // Ignore swipes while paused
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    return true;

                case MotionEvent.ACTION_UP:
                    performClick();
                    float endX = event.getX();
                    float endY = event.getY();
                    float dx = endX - startX;
                    float dy = endY - startY;

                    if (Math.abs(dx) > 50 || Math.abs(dy) > 50) {
                        if (Math.abs(dx) > Math.abs(dy)) {
                            if (dx > 0 && direction != 2) nextDirection = 0;
                            else if (dx < 0 && direction != 0) nextDirection = 2;
                        } else {
                            if (dy > 0 && direction != 3) nextDirection = 1;
                            else if (dy < 0 && direction != 1) nextDirection = 3;
                        }
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }
}