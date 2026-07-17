package com.example.ownphotoonwall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@SuppressWarnings("all")
public class MazeGameService extends Service implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private final Handler gameLoopHandler = new Handler(Looper.getMainLooper());
    private boolean isPlaying = false;

    private static final int GRID_WIDTH = 27;
    private static final int GRID_HEIGHT = 17;

    // INCREASED SCALE FOR CRISP HIGH-DEF 3D WIDGET RENDERING
    private static final int PIXEL_SCALE = 32;
    private static final int BITMAP_WIDTH = GRID_WIDTH * PIXEL_SCALE;
    private static final int BITMAP_HEIGHT = GRID_HEIGHT * PIXEL_SCALE;

    private Paint wallPaint, ballPaint, goalPaint, spikePaint;
    private float ballX = 1.5f, ballY = 1.5f;
    private float velX = 0f, velY = 0f;
    private float accelX = 0f, accelY = 0f;
    private static final float FRICTION = 0.85f;
    private static final float BALL_RADIUS = 0.2f;

    // ANTI-TUNNELING SPEED LIMIT
    private static final float MAX_SPEED = 0.6f;

    // MULTIPLE FLYING SPIKES ARRAY ENGINE
    private static final int NUM_SPIKES = 3;
    private final float[] spikeX = new float[NUM_SPIKES];
    private final float[] spikeY = new float[NUM_SPIKES];
    private final float[] spikeVelX = new float[NUM_SPIKES];
    private final float[] spikeVelY = new float[NUM_SPIKES];

    private int[][] mazeGrid = new int[GRID_HEIGHT][GRID_WIDTH];
    private final Path mazeWallPath = new Path();

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        goalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        spikePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        setupNeumorphicTheme();
    }

    private void setupNeumorphicTheme() {
        // Enforcing Modern Neumorphic Dark Theme to match the widget background perfectly
        wallPaint.setColor(Color.parseColor("#2C2F36")); // Raised 3D wall color
        wallPaint.setPathEffect(new CornerPathEffect(25.0f)); // Super smooth rounded walls
        wallPaint.setShadowLayer(14f, 8f, 8f, Color.parseColor("#0A0B0E")); // Deep 3D Shadow

        ballPaint.setColor(Color.parseColor("#4A90E2")); // Modern Glowing Blue Ball
        ballPaint.setShadowLayer(10f, 0f, 6f, Color.BLACK);

        goalPaint.setColor(Color.parseColor("#34C759")); // Neon Green Goal
        goalPaint.setShadowLayer(10f, 0f, 6f, Color.BLACK);

        spikePaint.setColor(Color.parseColor("#FF3B30")); // Hazard Red Flying Spike
        spikePaint.setShadowLayer(10f, 0f, 6f, Color.BLACK);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isPlaying) {
            stopGame();
            return START_NOT_STICKY;
        }
        startForeground(999, createNotification());
        isPlaying = true;
        if (!loadState()) generatePerfectMaze();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        gameLoopHandler.post(gameRunnable);
        return START_STICKY;
    }

    private void stopGame() {
        isPlaying = false;
        sensorManager.unregisterListener(this);
        gameLoopHandler.removeCallbacks(gameRunnable);
        saveState();
        renderFrame(true);
        stopForeground(true);
        stopSelf();
    }

    private boolean isColliding(float x, float y) {
        float[] pts = {x - BALL_RADIUS, y - BALL_RADIUS, x + BALL_RADIUS, y - BALL_RADIUS,
                x - BALL_RADIUS, y + BALL_RADIUS, x + BALL_RADIUS, y + BALL_RADIUS};
        for (int i = 0; i < pts.length; i += 2) {
            int gx = (int) pts[i];
            int gy = (int) pts[i+1];
            if (gx < 0 || gx >= GRID_WIDTH || gy < 0 || gy >= GRID_HEIGHT) return true;
            if (mazeGrid[gy][gx] == 1) return true;
        }
        return false;
    }

    private final Runnable gameRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPlaying) return;

            // BALL PHYSICS
            velX += accelX; velY += accelY;
            velX *= FRICTION; velY *= FRICTION;

            // BUG FIX: VELOCITY CLAMPING
            // Mathematically prevents the ball from moving so fast that it skips over a wall (Tunneling effect)
            velX = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, velX));
            velY = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, velY));

            float nextX = ballX + velX;
            float nextY = ballY + velY;

            if (!isColliding(nextX, ballY)) ballX = nextX;
            else velX = 0;

            if (!isColliding(ballX, nextY)) ballY = nextY;
            else velY = 0;

            // FLYING SPIKES PHYSICS (Loops through all 3)
            for (int i = 0; i < NUM_SPIKES; i++) {
                spikeX[i] += spikeVelX[i];
                spikeY[i] += spikeVelY[i];

                // Bounce spikes off edges of the board
                if (spikeX[i] <= 1f || spikeX[i] >= GRID_WIDTH - 1f) spikeVelX[i] *= -1;
                if (spikeY[i] <= 1f || spikeY[i] >= GRID_HEIGHT - 1f) spikeVelY[i] *= -1;

                // SPIKE COLLISION DETECTION WITH BALL
                float dx = ballX - spikeX[i];
                float dy = ballY - spikeY[i];
                if (Math.hypot(dx, dy) < (BALL_RADIUS + 0.35f)) {
                    // A Spike caught the ball! Reset player.
                    ballX = 1.5f;
                    ballY = 1.5f;
                    velX = 0f;
                    velY = 0f;
                    break; // No need to check other spikes this frame if we already died
                }
            }

            // WIN CONDITION
            if (mazeGrid[(int) ballY][(int) ballX] == 2) {
                generatePerfectMaze();
                saveState();
            }

            renderFrame(false);
            gameLoopHandler.postDelayed(this, 80); // 80ms loop for slightly smoother spike flying
        }
    };

    private void renderFrame(boolean showIcon) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            // Transparent background ensures your XML Neumorphic background shows through perfectly!
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            // Draw Neumorphic Rounded Maze Walls
            canvas.drawPath(mazeWallPath, wallPaint);

            // Draw Goal
            for (int y = 0; y < GRID_HEIGHT; y++) {
                for (int x = 0; x < GRID_WIDTH; x++) {
                    if (mazeGrid[y][x] == 2) {
                        RectF goalRect = new RectF(x * PIXEL_SCALE, y * PIXEL_SCALE, (x + 1) * PIXEL_SCALE, (y + 1) * PIXEL_SCALE);
                        canvas.drawRoundRect(goalRect, 10f, 10f, goalPaint);
                    }
                }
            }

            // Draw The Player Ball
            canvas.drawCircle(ballX * PIXEL_SCALE, ballY * PIXEL_SCALE, PIXEL_SCALE * 0.35f, ballPaint);

            // Draw All 3 Flying Hazard Spikes
            Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            corePaint.setColor(Color.WHITE);
            for (int i = 0; i < NUM_SPIKES; i++) {
                canvas.drawCircle(spikeX[i] * PIXEL_SCALE, spikeY[i] * PIXEL_SCALE, PIXEL_SCALE * 0.3f, spikePaint);
                // Draw a small inner core to make the spike look cooler
                canvas.drawCircle(spikeX[i] * PIXEL_SCALE, spikeY[i] * PIXEL_SCALE, PIXEL_SCALE * 0.1f, corePaint);
            }

            updateWidget(bitmap, showIcon);
            bitmap.recycle();
        } catch (Exception ignored) {}
    }

    private void updateWidget(Bitmap bitmap, boolean showIcon) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget_maze);
            views.setImageViewBitmap(R.id.maze_canvas, bitmap);
            views.setViewVisibility(R.id.maze_status_icon, showIcon ? View.VISIBLE : View.GONE);
            manager.updateAppWidget(new ComponentName(this, MazeWidgetProvider.class), views);
        } catch (Exception ignored) {}
    }

    private void saveState() {
        SharedPreferences prefs = getSharedPreferences("MazeState", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("ballX", ballX);
        editor.putFloat("ballY", ballY);

        for (int i = 0; i < NUM_SPIKES; i++) {
            editor.putFloat("spikeX_" + i, spikeX[i]);
            editor.putFloat("spikeY_" + i, spikeY[i]);
            editor.putFloat("spikeVelX_" + i, spikeVelX[i]);
            editor.putFloat("spikeVelY_" + i, spikeVelY[i]);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < GRID_HEIGHT; i++) for (int j = 0; j < GRID_WIDTH; j++) sb.append(mazeGrid[i][j]).append(",");
        editor.putString("grid", sb.toString());
        editor.apply();
    }

    private boolean loadState() {
        SharedPreferences prefs = getSharedPreferences("MazeState", MODE_PRIVATE);
        String gridStr = prefs.getString("grid", null);
        if (gridStr == null) return false;

        ballX = prefs.getFloat("ballX", 1.5f);
        ballY = prefs.getFloat("ballY", 1.5f);

        // Load Spikes with distinct starting fallback positions if starting fresh
        spikeX[0] = prefs.getFloat("spikeX_0", 13.5f); spikeY[0] = prefs.getFloat("spikeY_0", 8.5f);
        spikeVelX[0] = prefs.getFloat("spikeVelX_0", 0.25f); spikeVelY[0] = prefs.getFloat("spikeVelY_0", 0.25f);

        spikeX[1] = prefs.getFloat("spikeX_1", 23.5f); spikeY[1] = prefs.getFloat("spikeY_1", 3.5f);
        spikeVelX[1] = prefs.getFloat("spikeVelX_1", -0.3f); spikeVelY[1] = prefs.getFloat("spikeVelY_1", 0.2f);

        spikeX[2] = prefs.getFloat("spikeX_2", 3.5f); spikeY[2] = prefs.getFloat("spikeY_2", 13.5f);
        spikeVelX[2] = prefs.getFloat("spikeVelX_2", 0.2f); spikeVelY[2] = prefs.getFloat("spikeVelY_2", -0.3f);

        String[] parts = gridStr.split(",");
        if (parts.length < GRID_HEIGHT * GRID_WIDTH) return false;
        int idx = 0;
        try {
            for (int i = 0; i < GRID_HEIGHT; i++) for (int j = 0; j < GRID_WIDTH; j++) mazeGrid[i][j] = Integer.parseInt(parts[idx++]);
            buildMazePath();
            return true;
        } catch (Exception e) { return false; }
    }

    private void generatePerfectMaze() {
        for (int i = 0; i < GRID_HEIGHT; i++) for (int j = 0; j < GRID_WIDTH; j++) mazeGrid[i][j] = 1;
        boolean[][] visited = new boolean[GRID_HEIGHT][GRID_WIDTH];
        carvePathsDFS(1, 1, visited);
        mazeGrid[1][1] = 0;
        mazeGrid[GRID_HEIGHT - 2][GRID_WIDTH - 2] = 2;

        ballX = 1.5f; ballY = 1.5f;

        // Reset 3 Spikes across the map
        spikeX[0] = 13.5f; spikeY[0] = 8.5f; spikeVelX[0] = 0.25f; spikeVelY[0] = 0.25f;
        spikeX[1] = 23.5f; spikeY[1] = 3.5f; spikeVelX[1] = -0.3f; spikeVelY[1] = 0.2f;
        spikeX[2] = 3.5f; spikeY[2] = 13.5f; spikeVelX[2] = 0.2f; spikeVelY[2] = -0.3f;

        buildMazePath();
    }

    private void carvePathsDFS(int cx, int cy, boolean[][] visited) {
        visited[cy][cx] = true; mazeGrid[cy][cx] = 0;
        int[] dx = {0, 0, -2, 2}; int[] dy = {-2, 2, 0, 0};
        List<Integer> directions = new ArrayList<>();
        for (int i = 0; i < 4; i++) directions.add(i);
        Collections.shuffle(directions, new Random());
        for (int dir : directions) {
            int nx = cx + dx[dir]; int ny = cy + dy[dir];
            if (nx > 0 && nx < GRID_WIDTH - 1 && ny > 0 && ny < GRID_HEIGHT - 1) {
                if (!visited[ny][nx]) {
                    mazeGrid[cy + dy[dir] / 2][cx + dx[dir] / 2] = 0;
                    carvePathsDFS(nx, ny, visited);
                }
            }
        }
    }

    private void buildMazePath() {
        mazeWallPath.reset();
        Path cellPath = new Path();
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) {
                if (mazeGrid[y][x] == 1) {
                    cellPath.reset();
                    cellPath.addRect(x * PIXEL_SCALE, y * PIXEL_SCALE, (x + 1) * PIXEL_SCALE, (y + 1) * PIXEL_SCALE, Path.Direction.CW);
                    mazeWallPath.op(cellPath, Path.Op.UNION);
                }
            }
        }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelX = -event.values[0] * 0.05f; accelY = event.values[1] * 0.05f;
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private Notification createNotification() {
        String channelId = "maze_service";
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Maze Game", NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
            return new Notification.Builder(this, channelId).setContentTitle("Maze Active").setSmallIcon(R.drawable.ic_launcher_foreground).build();
        }
        return new Notification.Builder(this).setContentTitle("Maze Active").setSmallIcon(R.drawable.ic_launcher_foreground).build();
    }

    @Override public void onDestroy() { super.onDestroy(); isPlaying = false; }
    @Override public IBinder onBind(Intent intent) { return null; }
}