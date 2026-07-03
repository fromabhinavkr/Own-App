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
import android.content.res.Configuration;
import android.graphics.Bitmap;
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

public class MazeGameService extends Service implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private final Handler gameLoopHandler = new Handler(Looper.getMainLooper());
    private boolean isPlaying = false;

    private static final int GRID_WIDTH = 27;
    private static final int GRID_HEIGHT = 17;
    private static final int PIXEL_SCALE = 16;
    private static final int BITMAP_WIDTH = GRID_WIDTH * PIXEL_SCALE;
    private static final int BITMAP_HEIGHT = GRID_HEIGHT * PIXEL_SCALE;

    private Paint wallPaint, ballPaint, goalPaint;
    private float ballX = 1.5f, ballY = 1.5f;
    private float velX = 0f, velY = 0f;
    private float accelX = 0f, accelY = 0f;
    private static final float FRICTION = 0.85f;
    private static final float BALL_RADIUS = 0.2f; // Smaller radius to stay strictly in paths

    private int[][] mazeGrid = new int[GRID_HEIGHT][GRID_WIDTH];
    private final Path mazeWallPath = new Path();

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wallPaint.setPathEffect(new CornerPathEffect(5.0f));
        ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        goalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        goalPaint.setColor(Color.RED);
        updateThemeColors();
    }

    private void updateThemeColors() {
        boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        ballPaint.setColor(isNight ? Color.WHITE : Color.BLACK);
        wallPaint.setColor(isNight ? Color.LTGRAY : Color.DKGRAY);
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

    // New stricter collision check
    private boolean isColliding(float x, float y) {
        // Check 4 corners of the ball
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
            velX += accelX; velY += accelY;
            velX *= FRICTION; velY *= FRICTION;

            float nextX = ballX + velX;
            float nextY = ballY + velY;

            // Only move if new position is NOT colliding
            if (!isColliding(nextX, ballY)) ballX = nextX;
            else velX = 0;

            if (!isColliding(ballX, nextY)) ballY = nextY;
            else velY = 0;

            if (mazeGrid[(int) ballY][(int) ballX] == 2) { generatePerfectMaze(); saveState(); }
            renderFrame(false);
            gameLoopHandler.postDelayed(this, 100); // 100ms is stable for widgets
        }
    };

    private void renderFrame(boolean showIcon) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            // Transparent background ensures your XML rounded background is visible
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            canvas.drawPath(mazeWallPath, wallPaint);
            for (int y = 0; y < GRID_HEIGHT; y++) {
                for (int x = 0; x < GRID_WIDTH; x++) {
                    if (mazeGrid[y][x] == 2) {
                        RectF goalRect = new RectF(x * PIXEL_SCALE, y * PIXEL_SCALE, (x + 1) * PIXEL_SCALE, (y + 1) * PIXEL_SCALE);
                        canvas.drawRoundRect(goalRect, 4f, 4f, goalPaint);
                    }
                }
            }
            canvas.save();
            canvas.scale(1.0f, ((float) BITMAP_HEIGHT / BITMAP_WIDTH) * 1.5f, ballX * PIXEL_SCALE, ballY * PIXEL_SCALE);
            canvas.drawCircle(ballX * PIXEL_SCALE, ballY * PIXEL_SCALE, (float) PIXEL_SCALE / 2.3f, ballPaint);
            canvas.restore();
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

    // Logic methods kept intact
    private void saveState() {
        SharedPreferences prefs = getSharedPreferences("MazeState", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("ballX", ballX); editor.putFloat("ballY", ballY);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < GRID_HEIGHT; i++) for (int j = 0; j < GRID_WIDTH; j++) sb.append(mazeGrid[i][j]).append(",");
        editor.putString("grid", sb.toString());
        editor.apply();
    }

    private boolean loadState() {
        SharedPreferences prefs = getSharedPreferences("MazeState", MODE_PRIVATE);
        String gridStr = prefs.getString("grid", null);
        if (gridStr == null) return false;
        ballX = prefs.getFloat("ballX", 1.5f); ballY = prefs.getFloat("ballY", 1.5f);
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
        ballX = 1.5f; ballY = 1.5f; buildMazePath();
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