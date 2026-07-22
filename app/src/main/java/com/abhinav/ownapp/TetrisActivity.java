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
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

@SuppressLint("SetTextI18n")
public class TetrisActivity extends AppCompatActivity {

    private boolean isDarkTheme;
    private int highScore = 0;
    private SharedPreferences prefs;

    private TextView tvCurrentScore, tvHighScore, tvFinalScore, tvTapToStart, tvGameOverTitle, tvNewHighScoreBanner, tvNextLabel;
    private RelativeLayout pauseOverlay, gameOverOverlay;
    private TetrisEngine gameEngine;
    private NextShapeView nextShapeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tetris);

        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
        highScore = prefs.getInt("tetris_high_score", 0);

        View root = findViewById(R.id.tetrisRoot);
        FrameLayout gameContainer = findViewById(R.id.gameContainer);
        FrameLayout nextShapeContainer = findViewById(R.id.nextShapeContainer);

        tvCurrentScore = findViewById(R.id.tvCurrentScore);
        tvHighScore = findViewById(R.id.tvHighScore);
        tvFinalScore = findViewById(R.id.tvFinalScore);
        tvTapToStart = findViewById(R.id.tvTapToStart);
        tvGameOverTitle = findViewById(R.id.tvGameOverTitle);
        tvNewHighScoreBanner = findViewById(R.id.tvNewHighScoreBanner);
        tvNextLabel = findViewById(R.id.tvNextLabel);

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
        tvNextLabel.setTextColor(textColor);
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

        // Initialize Engines
        nextShapeView = new NextShapeView(this);
        nextShapeContainer.addView(nextShapeView);

        gameEngine = new TetrisEngine(this, isDarkTheme, nextShapeView);
        gameContainer.addView(gameEngine);

        // Callbacks
        gameEngine.setGameListener(new TetrisEngine.GameListener() {
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
                    prefs.edit().putInt("tetris_high_score", highScore).apply();
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
    // TETRIS DATA STRUCTURES
    // ==========================================
    private static final int[][][] TETROMINOES = {
            {{1, 1, 1, 1}}, // I (Cyan)
            {{1, 1}, {1, 1}}, // O (Yellow)
            {{0, 1, 0}, {1, 1, 1}}, // T (Purple)
            {{1, 0, 0}, {1, 1, 1}}, // L (Orange)
            {{0, 0, 1}, {1, 1, 1}}, // J (Blue)
            {{0, 1, 1}, {1, 1, 0}}, // S (Green)
            {{1, 1, 0}, {0, 1, 1}}  // Z (Red)
    };

    private static final int[] COLORS = {
            Color.TRANSPARENT,
            Color.parseColor("#00BCD4"), // Cyan
            Color.parseColor("#FFEB3B"), // Yellow
            Color.parseColor("#9C27B0"), // Purple
            Color.parseColor("#FF9800"), // Orange
            Color.parseColor("#2196F3"), // Blue
            Color.parseColor("#4CAF50"), // Green
            Color.parseColor("#F44336")  // Red
    };

    // ==========================================
    // NEXT SHAPE PREVIEW CANVAS
    // ==========================================
    private static class NextShapeView extends View {
        private int[][] shape;
        private int color;
        private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public NextShapeView(Context context) {
            super(context);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Color.argb(100, 0, 0, 0));
            strokePaint.setStrokeWidth(3f);
        }

        public void updateShape(int typeId) {
            this.shape = TETROMINOES[typeId];
            this.color = COLORS[typeId + 1];
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (shape == null) return;

            int rows = shape.length;
            int cols = shape[0].length;
            float cellSize = Math.min(getWidth() / 4f, getHeight() / 4f);

            float startX = (getWidth() - (cols * cellSize)) / 2f;
            float startY = (getHeight() - (rows * cellSize)) / 2f;

            blockPaint.setColor(color);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (shape[r][c] == 1) {
                        RectF rect = new RectF(startX + c * cellSize, startY + r * cellSize,
                                startX + (c + 1) * cellSize, startY + (r + 1) * cellSize);
                        canvas.drawRect(rect, blockPaint);
                        canvas.drawRect(rect, strokePaint);
                    }
                }
            }
        }
    }

    // ==========================================
    // MAIN GAME ENGINE & RENDERER
    // ==========================================
    private static class TetrisEngine extends View {

        private final int ROWS = 20;
        private final int COLS = 10;
        private int[][] board = new int[ROWS][COLS];

        private int[][] currentPiece;
        private int currentType, currentX, currentY;
        private int nextType;

        private float screenW, screenH, cellSize;
        private float boardTop, boardLeft, boardBottom;
        private float topHUDHeight;

        private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint();
        private final Paint brickFill = new Paint();
        private final Paint brickStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Random random = new Random();
        private GameListener listener;
        private NextShapeView nextShapeView;

        private boolean playing = false, paused = false, gameOver = false;
        private int score = 0;

        // Touch handling
        private float startX, startY;
        private boolean movedDuringTouch = false;
        private long touchStartTime;

        public interface GameListener {
            void onScoreUpdated(int score);
            void onGameOver(int finalScore);
            void onGameStarted();
        }

        public TetrisEngine(Context context, boolean isDarkTheme, NextShapeView nextShapeView) {
            super(context);
            this.nextShapeView = nextShapeView;

            gridPaint.setStyle(Paint.Style.STROKE);
            gridPaint.setColor(isDarkTheme ? Color.argb(30, 255, 255, 255) : Color.argb(30, 0, 0, 0));
            gridPaint.setStrokeWidth(2f);

            // Black & White Bricks
            brickFill.setColor(Color.BLACK);
            brickStroke.setStyle(Paint.Style.STROKE);
            brickStroke.setColor(Color.WHITE);
            brickStroke.setStrokeWidth(5f);
        }

        public void setGameListener(GameListener listener) { this.listener = listener; }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            screenW = w;
            screenH = h;

            // HUD is 100dp tall, add some padding
            topHUDHeight = getResources().getDisplayMetrics().density * 110;

            // Calculate cell size to perfectly fit 20 rows and 10 cols between bounds
            float availableHeight = screenH - topHUDHeight - 80f; // 80f reserved for floor/ceiling bricks
            cellSize = Math.min(screenW / COLS, availableHeight / ROWS);

            boardLeft = (screenW - (COLS * cellSize)) / 2f;
            boardTop = topHUDHeight + 40f; // Below the top brick line
            boardBottom = boardTop + (ROWS * cellSize);

            resetGame();
        }

        public void resetGame() {
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    board[r][c] = 0;
                }
            }
            score = 0;
            playing = false;
            paused = false;
            gameOver = false;

            nextType = random.nextInt(TETROMINOES.length);
            spawnPiece();
            invalidate();
        }

        private void spawnPiece() {
            currentType = nextType;
            currentPiece = TETROMINOES[currentType];
            currentX = COLS / 2 - currentPiece[0].length / 2;
            currentY = 0;

            nextType = random.nextInt(TETROMINOES.length);
            if (nextShapeView != null) nextShapeView.updateShape(nextType);

            if (!isValidMove(currentPiece, currentX, currentY)) {
                gameOver = true;
                playing = false;
                handler.removeCallbacks(gameLoop);
                if (listener != null) listener.onGameOver(score);
            }
        }

        public void pauseGame() { paused = true; handler.removeCallbacks(gameLoop); }
        public void resumeGame() { paused = false; handler.postDelayed(gameLoop, getDelay()); }
        public boolean isPlaying() { return playing; }
        public boolean isGameOver() { return gameOver; }

        private long getDelay() {
            return 500L; // Constant speed locked to 500ms
        }

        private final Runnable gameLoop = new Runnable() {
            @Override
            public void run() {
                if (playing && !paused && !gameOver) {
                    if (isValidMove(currentPiece, currentX, currentY + 1)) {
                        currentY++;
                    } else {
                        lockPiece();
                    }
                    invalidate();
                    handler.postDelayed(this, getDelay());
                }
            }
        };

        private boolean isValidMove(int[][] shape, int x, int y) {
            for (int r = 0; r < shape.length; r++) {
                for (int c = 0; c < shape[0].length; c++) {
                    if (shape[r][c] != 0) {
                        int boardX = x + c;
                        int boardY = y + r;
                        if (boardX < 0 || boardX >= COLS || boardY >= ROWS || (boardY >= 0 && board[boardY][boardX] != 0)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private void lockPiece() {
            for (int r = 0; r < currentPiece.length; r++) {
                for (int c = 0; c < currentPiece[0].length; c++) {
                    if (currentPiece[r][c] != 0 && currentY + r >= 0) {
                        board[currentY + r][currentX + c] = currentType + 1;
                    }
                }
            }
            clearLines();
            spawnPiece();
        }

        private void clearLines() {
            int linesCleared = 0;
            for (int r = ROWS - 1; r >= 0; r--) {
                boolean full = true;
                for (int c = 0; c < COLS; c++) {
                    if (board[r][c] == 0) { full = false; break; }
                }
                if (full) {
                    linesCleared++;
                    for (int moveR = r; moveR > 0; moveR--) {
                        System.arraycopy(board[moveR - 1], 0, board[moveR], 0, COLS);
                    }
                    for (int c = 0; c < COLS; c++) { board[0][c] = 0; }
                    r++; // Re-check the shifted row
                }
            }
            if (linesCleared > 0) {
                score += (linesCleared * linesCleared) * 10;
                if (listener != null) listener.onScoreUpdated(score);
            }
        }

        private void rotatePiece() {
            int rows = currentPiece.length;
            int cols = currentPiece[0].length;
            int[][] rotated = new int[cols][rows];
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    rotated[c][rows - 1 - r] = currentPiece[r][c];
                }
            }
            if (isValidMove(rotated, currentX, currentY)) {
                currentPiece = rotated;
                invalidate();
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (gameOver || paused) return true;

            if (!playing) {
                playing = true;
                if (listener != null) listener.onGameStarted();
                handler.postDelayed(gameLoop, getDelay());
                return true;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    movedDuringTouch = false;
                    touchStartTime = System.currentTimeMillis();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - startX;
                    float dy = event.getY() - startY;

                    if (Math.abs(dx) > cellSize) {
                        int steps = (int) (dx / cellSize);
                        if (steps > 0 && isValidMove(currentPiece, currentX + 1, currentY)) {
                            currentX++; startX += cellSize; movedDuringTouch = true;
                        } else if (steps < 0 && isValidMove(currentPiece, currentX - 1, currentY)) {
                            currentX--; startX -= cellSize; movedDuringTouch = true;
                        }
                        invalidate();
                    }

                    // Soft Drop
                    if (dy > cellSize) {
                        if (isValidMove(currentPiece, currentX, currentY + 1)) {
                            currentY++; startY += cellSize; movedDuringTouch = true;
                        }
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    long duration = System.currentTimeMillis() - touchStartTime;
                    if (!movedDuringTouch && duration < 250) {
                        rotatePiece(); // Fast tap to rotate
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }

        private void drawBricks(Canvas canvas, float yPos) {
            float brickW = screenW / 10f; // 10 bricks across
            float brickH = 40f;
            for (int i = 0; i < 10; i++) {
                RectF brick = new RectF(i * brickW, yPos, (i + 1) * brickW, yPos + brickH);
                canvas.drawRect(brick, brickFill);
                canvas.drawRect(brick, brickStroke);
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);

            // 1. Draw Black & White Brick Separators
            drawBricks(canvas, boardTop - 40f); // Top Line
            drawBricks(canvas, boardBottom);    // Bottom Floor Line

            // 2. Draw Board Background and Grid
            canvas.drawRect(boardLeft, boardTop, boardLeft + (COLS * cellSize), boardBottom, gridPaint);
            for (int i = 1; i < COLS; i++) canvas.drawLine(boardLeft + i * cellSize, boardTop, boardLeft + i * cellSize, boardBottom, gridPaint);
            for (int i = 1; i < ROWS; i++) canvas.drawLine(boardLeft, boardTop + i * cellSize, boardLeft + (COLS * cellSize), boardTop + i * cellSize, gridPaint);

            // 3. Draw Settled Blocks
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    if (board[r][c] != 0) {
                        drawBlock(canvas, c, r, COLORS[board[r][c]]);
                    }
                }
            }

            // 4. Draw Current Falling Piece
            if (currentPiece != null) {
                for (int r = 0; r < currentPiece.length; r++) {
                    for (int c = 0; c < currentPiece[0].length; c++) {
                        if (currentPiece[r][c] != 0 && currentY + r >= 0) {
                            drawBlock(canvas, currentX + c, currentY + r, COLORS[currentType + 1]);
                        }
                    }
                }
            }
        }

        private void drawBlock(Canvas canvas, int c, int r, int color) {
            RectF rect = new RectF(boardLeft + c * cellSize, boardTop + r * cellSize,
                    boardLeft + (c + 1) * cellSize, boardTop + (r + 1) * cellSize);
            blockPaint.setColor(color);
            blockPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(rect, blockPaint);

            blockPaint.setColor(Color.argb(100, 0,0,0));
            blockPaint.setStyle(Paint.Style.STROKE);
            blockPaint.setStrokeWidth(4f);
            canvas.drawRect(rect, blockPaint);
        }
    }
}