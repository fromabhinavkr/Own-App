package com.example.ownphotoonwall;

import android.app.AlertDialog; import android.content.Context; import android.content.SharedPreferences; import android.content.res.ColorStateList; import android.content.res.Configuration; import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint; import android.graphics.Point; import android.graphics.RectF; import android.graphics.drawable.ColorDrawable; import android.os.Bundle; import android.os.Handler; import android.os.Looper; import android.view.LayoutInflater; import android.view.MotionEvent; import android.view.View; import android.widget.Button; import android.widget.FrameLayout; import android.widget.LinearLayout; import android.widget.TextView; import androidx.annotation.NonNull; import androidx.activity.EdgeToEdge; import androidx.appcompat.app.AppCompatActivity; import androidx.core.graphics.Insets; import androidx.core.view.ViewCompat; import androidx.core.view.WindowInsetsCompat; import java.util.ArrayList; import java.util.Random;

@SuppressWarnings("all")
public class SnakeGameActivity extends AppCompatActivity {

    private SnakeGameEngine gameEngine; private Button btnDifficulty; private Button btnPause; private TextView tvScore; private boolean isDarkTheme;
    private static final int SPEED_EASY = 220; private static final int SPEED_MEDIUM = 130; private static final int SPEED_HARD = 70;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_snake_game);
        View rootLayout = findViewById(R.id.snake_root);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE); isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
        btnDifficulty = findViewById(R.id.btnDifficulty); btnPause = findViewById(R.id.btnPause); tvScore = findViewById(R.id.tvScore);
        if (isDarkTheme) {
            ColorStateList darkBg = ColorStateList.valueOf(Color.parseColor("#3A3A3C")); int darkText = Color.WHITE;
            btnDifficulty.setBackgroundTintList(darkBg); btnDifficulty.setTextColor(darkText); btnPause.setBackgroundTintList(darkBg); btnPause.setTextColor(darkText); if (tvScore != null) tvScore.setTextColor(darkText);
        } else {
            ColorStateList lightBg = ColorStateList.valueOf(Color.parseColor("#E5E5EA")); int lightText = Color.parseColor("#333333");
            btnDifficulty.setBackgroundTintList(lightBg); btnDifficulty.setTextColor(lightText); btnPause.setBackgroundTintList(lightBg); btnPause.setTextColor(lightText); if (tvScore != null) tvScore.setTextColor(lightText);
        }
        FrameLayout container = findViewById(R.id.game_container); gameEngine = new SnakeGameEngine(this, isDarkTheme, prefs); container.addView(gameEngine);
        btnDifficulty.setOnClickListener(v -> showDifficultyDialog());
        btnPause.setOnClickListener(v -> { boolean isNowPaused = gameEngine.togglePause(); btnPause.setText(isNowPaused ? R.string.game_resume : R.string.game_pause); });

        // --- LAUNCH THE MODE SELECTION POPUP IMMEDIATELY ---
        showGameModeDialog();
    }

    @Override public void onConfigurationChanged(@NonNull Configuration newConfig) { super.onConfigurationChanged(newConfig); /* Engine naturally recomputes grid in onSizeChanged */ }

    // --- NEW: GAME MODE SELECTION DIALOG ENGINE ---
    private void showGameModeDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_snake_mode, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout dialogRoot = dialogView.findViewById(R.id.dialogModeRoot); TextView tvTitle = dialogView.findViewById(R.id.tvModeTitle); Button btnClosed = dialogView.findViewById(R.id.btnClosedGround); Button btnOpen = dialogView.findViewById(R.id.btnOpenGround);
        int btnBgColor = isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA"); int textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");
        if (isDarkTheme) dialogRoot.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2C2C2E"))); else dialogRoot.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        tvTitle.setTextColor(textColor); btnClosed.setBackgroundTintList(ColorStateList.valueOf(btnBgColor)); btnClosed.setTextColor(textColor); btnOpen.setBackgroundTintList(ColorStateList.valueOf(btnBgColor)); btnOpen.setTextColor(textColor);
        btnClosed.setOnClickListener(v -> { gameEngine.setGameMode(false); dialog.dismiss(); });
        btnOpen.setOnClickListener(v -> { gameEngine.setGameMode(true); dialog.dismiss(); });
        dialog.setOnCancelListener(d -> finish());
        dialog.show();
    }

    private void showDifficultyDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_difficulty, null); AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout dialogRoot = dialogView.findViewById(R.id.dialogRoot); TextView tvTitle = dialogView.findViewById(R.id.tvTitle); Button btnEasy = dialogView.findViewById(R.id.btnEasy); Button btnMedium = dialogView.findViewById(R.id.btnMedium); Button btnHard = dialogView.findViewById(R.id.btnHard);
        int btnBgColor = isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA"); int textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");
        if (isDarkTheme) dialogRoot.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2C2C2E"))); else dialogRoot.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        tvTitle.setTextColor(textColor); Button[] buttons = {btnEasy, btnMedium, btnHard};
        for (Button b : buttons) { b.setBackgroundTintList(ColorStateList.valueOf(btnBgColor)); b.setTextColor(textColor); }
        btnEasy.setOnClickListener(v -> { btnDifficulty.setText(R.string.diff_easy); gameEngine.setSpeed(SPEED_EASY); dialog.dismiss(); });
        btnMedium.setOnClickListener(v -> { btnDifficulty.setText(R.string.diff_medium); gameEngine.setSpeed(SPEED_MEDIUM); dialog.dismiss(); });
        btnHard.setOnClickListener(v -> { btnDifficulty.setText(R.string.diff_hard); gameEngine.setSpeed(SPEED_HARD); dialog.dismiss(); });
        dialog.show();
    }

    private class SnakeGameEngine extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); private final ArrayList<Point> snake = new ArrayList<>(); private Point apple; private final Random random = new Random(); private final Handler handler = new Handler(Looper.getMainLooper()); private final SharedPreferences prefs;
        private int direction = 0; private int nextDirection = 0; private boolean isGameOver = false; private boolean isPaused = false;
        private boolean isOpenGroundMode = false; private boolean isWaitingForMode = true;
        private int score = 0; private int highScore = 0; private boolean isNewHighScore = false;
        private int currentSpeed = SPEED_MEDIUM; private int gridCols = 20; private int gridRows = 20; private int blockSize = 30;
        private final int bgColor; private final int snakeColor; private final int appleColor; private final int textColor;
        private float startX, startY; private final RectF appleRect = new RectF(); private final RectF bodyRect = new RectF();

        public SnakeGameEngine(Context context, boolean isDarkTheme, SharedPreferences preferences) {
            super(context); this.prefs = preferences; this.highScore = prefs.getInt("snake_high_score", 0);
            if (isDarkTheme) { bgColor = Color.parseColor("#000000"); snakeColor = Color.parseColor("#FFFFFF"); appleColor = Color.parseColor("#FF3B30"); textColor = Color.WHITE; }
            else { bgColor = Color.parseColor("#FFFFFF"); snakeColor = Color.parseColor("#000000"); appleColor = Color.parseColor("#FF3B30"); textColor = Color.BLACK; }
            this.isWaitingForMode = true; // Wait for popup selection before starting!
        }

        public void setSpeed(int speed) { this.currentSpeed = speed; }

        public void setGameMode(boolean openGround) {
            this.isOpenGroundMode = openGround;
            this.isWaitingForMode = false;
            initGame();
        }

        public boolean togglePause() {
            if (isWaitingForMode || isGameOver) return false; isPaused = !isPaused;
            if (!isPaused) { handler.removeCallbacksAndMessages(null); handler.postDelayed(gameLoop, currentSpeed); }
            invalidate(); return isPaused;
        }

        private void initGame() {
            snake.clear(); snake.add(new Point(5, 5)); snake.add(new Point(4, 5)); snake.add(new Point(3, 5));
            direction = 0; nextDirection = 0; score = 0; isGameOver = false; isPaused = false; isNewHighScore = false;
            if (btnPause != null) btnPause.setText(R.string.game_pause);
            updateScoreUI(); spawnApple();
            handler.removeCallbacksAndMessages(null); handler.postDelayed(gameLoop, currentSpeed);
        }

        private void updateScoreUI() { if (tvScore != null) tvScore.setText("Score: " + score); }

        private void spawnApple() {
            int maxCol = Math.max(1, gridCols); int maxRow = Math.max(1, gridRows);
            int x = random.nextInt(maxCol);
            int topOffset = Math.min(3, maxRow / 4);
            int y = random.nextInt(Math.max(1, maxRow - topOffset)) + topOffset;
            apple = new Point(x, y);
        }

        private final Runnable gameLoop = new Runnable() { @Override public void run() { if (!isWaitingForMode && !isGameOver) { if (!isPaused) { moveSnake(); checkCollisions(); } invalidate(); handler.postDelayed(this, currentSpeed); } } };

        private void moveSnake() {
            direction = nextDirection; Point head = snake.get(0); Point newHead = new Point(head.x, head.y);
            switch (direction) { case 0: newHead.x++; break; case 1: newHead.y++; break; case 2: newHead.x--; break; case 3: newHead.y--; break; }

            // --- OPEN GROUND INFINITE LOOP ENGINE ---
            if (isOpenGroundMode) {
                if (newHead.x < 0) newHead.x = gridCols - 1;
                else if (newHead.x >= gridCols) newHead.x = 0;
                if (newHead.y < 0) newHead.y = gridRows - 1;
                else if (newHead.y >= gridRows) newHead.y = 0;
            }

            snake.add(0, newHead);
            if (apple != null && newHead.x == apple.x && newHead.y == apple.y) { score += 10; updateScoreUI(); spawnApple(); } else { snake.remove(snake.size() - 1); }
        }

        private void checkCollisions() {
            Point head = snake.get(0);
            // In Closed Ground mode, hitting a wall is Game Over!
            if (!isOpenGroundMode) {
                if (head.x < 0 || head.x >= gridCols || head.y < 0 || head.y >= gridRows) {
                    triggerGameOver(); return;
                }
            }
            // In both modes, biting yourself is always Game Over!
            for (int i = 1; i < snake.size(); i++) { if (head.x == snake.get(i).x && head.y == snake.get(i).y) { triggerGameOver(); return; } }
        }

        private void triggerGameOver() {
            isGameOver = true;
            if (score > highScore) { highScore = score; isNewHighScore = true; prefs.edit().putInt("snake_high_score", highScore).apply(); }
        }

        @Override protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            blockSize = Math.max(1, Math.min(w, h) / 20);
            gridCols = Math.max(10, w / blockSize);
            gridRows = Math.max(10, h / blockSize);
            if (!isWaitingForMode && apple != null && (apple.x >= gridCols || apple.y >= gridRows)) spawnApple();
            for (Point p : snake) { if (p.x >= gridCols) p.x = gridCols - 1; if (p.y >= gridRows) p.y = gridRows - 1; }
        }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas); canvas.drawColor(bgColor);
            if (isWaitingForMode) {
                paint.setColor(textColor); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(65f);
                canvas.drawText("Select Game Mode...", getWidth() / 2f, getHeight() / 2f, paint); paint.setTextAlign(Paint.Align.LEFT);
                return;
            }
            if (apple != null) {
                paint.setColor(appleColor); appleRect.set(apple.x * blockSize + 2, apple.y * blockSize + 2, (apple.x + 1) * blockSize - 2, (apple.y + 1) * blockSize - 2);
                canvas.drawRoundRect(appleRect, 16f, 16f, paint);
            }
            paint.setColor(snakeColor);
            for (Point p : snake) {
                bodyRect.set(p.x * blockSize + 2, p.y * blockSize + 2, (p.x + 1) * blockSize - 2, (p.y + 1) * blockSize - 2);
                canvas.drawRoundRect(bodyRect, 16f, 16f, paint);
            }
            if (isPaused && !isGameOver) {
                paint.setColor(textColor); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(100f);
                canvas.drawText("PAUSED", getWidth() / 2f, getHeight() / 2f, paint); paint.setTextAlign(Paint.Align.LEFT);
            }
            if (isGameOver) {
                paint.setColor(textColor); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(100f);
                canvas.drawText("GAME OVER", getWidth() / 2f, getHeight() / 2f - 120f, paint); paint.setTextSize(60f);
                canvas.drawText("Score: " + score, getWidth() / 2f, getHeight() / 2f - 20f, paint); canvas.drawText("High Score: " + highScore, getWidth() / 2f, getHeight() / 2f + 60f, paint);
                if (isNewHighScore) { paint.setColor(Color.parseColor("#4CD964")); paint.setTextSize(65f); canvas.drawText("🏆 New High Score! 🏆", getWidth() / 2f, getHeight() / 2f + 160f, paint); paint.setColor(textColor); }
                paint.setTextSize(45f); paint.setFakeBoldText(false); canvas.drawText("Tap anywhere to Restart", getWidth() / 2f, getHeight() / 2f + 280f, paint); paint.setTextAlign(Paint.Align.LEFT);
            }
        }

        @Override public boolean performClick() { return super.performClick(); }

        @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
            if (isWaitingForMode) return true; // Ignore touch while mode selection dialog is open
            if (isGameOver && event.getAction() == MotionEvent.ACTION_DOWN) { initGame(); return true; }
            if (isPaused) return true;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: startX = event.getX(); startY = event.getY(); return true;
                case MotionEvent.ACTION_UP:
                    performClick(); float endX = event.getX(); float endY = event.getY(); float dx = endX - startX; float dy = endY - startY;
                    if (Math.abs(dx) > 50 || Math.abs(dy) > 50) {
                        if (Math.abs(dx) > Math.abs(dy)) { if (dx > 0 && direction != 2) nextDirection = 0; else if (dx < 0 && direction != 0) nextDirection = 2; }
                        else { if (dy > 0 && direction != 3) nextDirection = 1; else if (dy < 0 && direction != 1) nextDirection = 3; }
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }
}