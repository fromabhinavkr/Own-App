package com.example.ownphotoonwall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.widget.RemoteViews;

public class HourglassService extends Service implements SensorEventListener {

    private boolean isRunning = false;
    private Handler handler = new Handler();
    private SensorManager sensorManager;
    private Sensor accelerometer;

    private float tiltX = 0;
    private float tiltY = 9.8f;

    private final int GRID_SIZE = 21;
    private int[][] sandGrid = new int[GRID_SIZE][GRID_SIZE];

    private final int UPDATE_RATE = 100; // Fast refresh for smooth falling

    // --- EXACT 8-BIT REFERENCE SHAPE ---
    private final int[] SHAPE_WIDTHS = {
            9, // Row 0: Top Cap
            7, // Row 1: Straight wall
            7, // Row 2: Straight wall
            6, // Row 3: Diagonal starts
            5, // Row 4
            4, // Row 5
            3, // Row 6
            2, // Row 7
            1, // Row 8
            0, // Row 9: Neck
            0, // Row 10: Center Neck
            0, // Row 11: Neck
            1, // Row 12
            2, // Row 13
            3, // Row 14
            4, // Row 15
            5, // Row 16
            6, // Row 17
            7, // Row 18: Straight wall
            7, // Row 19: Straight wall
            9  // Row 20: Bottom Cap
    };

    private Runnable physicsLoop = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                updatePhysics();
                drawWidget();
                handler.postDelayed(this, UPDATE_RATE);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundProtection();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        initializeSand();
    }

    private void initializeSand() {
        for (int y = 1; y <= 8; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                if (Math.abs(x - (GRID_SIZE / 2)) <= SHAPE_WIDTHS[y]) {
                    sandGrid[x][y] = 1;
                }
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        if (sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        handler.removeCallbacks(physicsLoop);
        handler.post(physicsLoop);
        return START_STICKY;
    }

    // --- CELLULAR AUTOMATA PHYSICS ---
    private void updatePhysics() {
        int[][] newGrid = new int[GRID_SIZE][GRID_SIZE];

        int dropX = 0;
        int dropY = 1;

        // 1. Lock gravity to the strongest axis so it doesn't pull diagonally
        if (Math.abs(tiltX) > Math.abs(tiltY)) {
            dropX = (tiltX > 0) ? -1 : 1;
            dropY = 0;
        } else {
            dropX = 0;
            dropY = (tiltY > 0) ? 1 : -1;
        }

        // 2. Scan the grid from bottom to top based on current gravity direction
        int startX = (dropX > 0) ? GRID_SIZE - 1 : 0;
        int endX = (dropX > 0) ? -1 : GRID_SIZE;
        int stepX = (dropX > 0) ? -1 : 1;

        int startY = (dropY > 0) ? GRID_SIZE - 1 : 0;
        int endY = (dropY > 0) ? -1 : GRID_SIZE;
        int stepY = (dropY > 0) ? -1 : 1;

        for (int y = startY; y != endY; y += stepY) {
            for (int x = startX; x != endX; x += stepX) {
                if (sandGrid[x][y] == 1) {

                    int nextX = x + dropX;
                    int nextY = y + dropY;

                    // 3. Randomize the slide direction so sand organically spreads and forms pyramids
                    boolean coinFlip = Math.random() > 0.5;
                    int slideDir1 = coinFlip ? 1 : -1;
                    int slideDir2 = -slideDir1;

                    int slideX1 = x + ((dropX == 0) ? slideDir1 : dropX);
                    int slideY1 = y + ((dropY == 0) ? slideDir1 : dropY);

                    int slideX2 = x + ((dropX == 0) ? slideDir2 : dropX);
                    int slideY2 = y + ((dropY == 0) ? slideDir2 : dropY);

                    // A. Try straight down (relative to gravity)
                    if (isInsideHourglass(nextX, nextY) && sandGrid[nextX][nextY] == 0 && newGrid[nextX][nextY] == 0) {
                        newGrid[nextX][nextY] = 1;
                    }
                    // B. Try primary diagonal slide
                    else if (isInsideHourglass(slideX1, slideY1) && sandGrid[slideX1][slideY1] == 0 && newGrid[slideX1][slideY1] == 0) {
                        newGrid[slideX1][slideY1] = 1;
                    }
                    // C. Try secondary diagonal slide
                    else if (isInsideHourglass(slideX2, slideY2) && sandGrid[slideX2][slideY2] == 0 && newGrid[slideX2][slideY2] == 0) {
                        newGrid[slideX2][slideY2] = 1;
                    }
                    // D. Stay put (hit a wall or floor)
                    else {
                        newGrid[x][y] = 1;
                    }
                }
            }
        }
        sandGrid = newGrid;
    }

    private boolean isInsideHourglass(int x, int y) {
        if (x < 0 || x >= GRID_SIZE || y < 0 || y >= GRID_SIZE) {
            return false;
        }
        int center = GRID_SIZE / 2;
        return Math.abs(x - center) <= SHAPE_WIDTHS[y];
    }

    private void drawWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName thisWidget = new ComponentName(this, HourglassWidget.class);
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.hourglass_widget);

        Bitmap bitmap = Bitmap.createBitmap(420, 420, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        int cellSize = 420 / GRID_SIZE;
        float radius = (cellSize / 2f) - 2f;

        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                if (isInsideHourglass(x, y)) {
                    float cx = (x * cellSize) + (cellSize / 2f);
                    float cy = (y * cellSize) + (cellSize / 2f);

                    if (sandGrid[x][y] == 1) {
                        paint.setColor(Color.parseColor("#E0E0E0"));
                    } else {
                        paint.setColor(Color.parseColor("#333333"));
                    }
                    canvas.drawCircle(cx, cy, radius, paint);
                }
            }
        }

        views.setImageViewBitmap(R.id.hourglass_image_view, bitmap);
        views.setImageViewResource(R.id.btn_toggle_hourglass, isRunning ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        manager.updateAppWidget(thisWidget, views);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            tiltX = event.values[0];
            tiltY = event.values[1];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void startForegroundProtection() {
        String channelId = "hourglass_channel";
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Hourglass Widget", NotificationManager.IMPORTANCE_LOW);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("Own's Hourglass")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(3, notification);
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        handler.removeCallbacks(physicsLoop);
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}