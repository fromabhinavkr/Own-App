package com.example.ownphotoonwall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.RemoteViews;

public class SnakeService extends Service {

    private static SnakeEngine engine = new SnakeEngine();
    private static boolean isRunning = false;

    private Handler handler = new Handler();
    private final int UPDATE_RATE = 500;

    private Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                engine.move();
                updateWidget();
                handler.postDelayed(this, UPDATE_RATE);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        updateWidget();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals("com.example.ownphotoonwall.ACTION_TOGGLE")) {
                isRunning = !isRunning;

                handler.removeCallbacks(gameLoop);

                if (isRunning) {
                    startForegroundProtection(); // Protect the service from being killed!
                    handler.post(gameLoop);
                } else {
                    stopForeground(true); // Remove the notification when paused
                    updateWidget();
                }
            }
        }
        return START_STICKY;
    }

    // ==========================================
    // FOREGROUND NOTIFICATION LOGIC
    // ==========================================
    private void startForegroundProtection() {
        String channelId = "snake_game_channel";
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Android 8.0+ requires a Notification Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Snake Widget",
                    NotificationManager.IMPORTANCE_LOW // Low importance = completely silent
            );
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("Own's Auto Snake")
                .setContentText("The snake is active...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        // Promotes the service to Foreground status
        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(gameLoop);
    }

    private void updateWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName thisWidget = new ComponentName(this, SnakeWidget.class);
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.snake_widget);

        Bitmap bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        int cellSize = 400 / engine.width;

        // Anti-aliasing makes the circular dots smooth
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        // ==========================================
        // NOKIA DOT MATRIX DESIGN FIX
        // ==========================================

        // 1. Draw the Food (Red Dot)
        paint.setColor(Color.RED);
        float foodCenterX = (engine.food.x * cellSize) + (cellSize / 2f);
        float foodCenterY = (engine.food.y * cellSize) + (cellSize / 2f);
        float foodRadius = (cellSize / 2f) - 2f; // -2f creates the spacing gap
        canvas.drawCircle(foodCenterX, foodCenterY, foodRadius, paint);

        // 2. Draw the Snake (White Dots)
        paint.setColor(Color.WHITE);
        for (Point p : engine.snake) {
            float snakeCenterX = (p.x * cellSize) + (cellSize / 2f);
            float snakeCenterY = (p.y * cellSize) + (cellSize / 2f);
            float snakeRadius = (cellSize / 2f) - 2f;
            canvas.drawCircle(snakeCenterX, snakeCenterY, snakeRadius, paint);
        }
        // ==========================================

        views.setImageViewBitmap(R.id.widget_image_view, bitmap);
        views.setTextViewText(R.id.btn_toggle, isRunning ? "Stop" : "Start");

        int titleVisibility = isRunning ? View.GONE : View.VISIBLE;
        views.setViewVisibility(R.id.widget_title_text, titleVisibility);

        Intent toggleIntent = new Intent(this, SnakeService.class);
        toggleIntent.setAction("com.example.ownphotoonwall.ACTION_TOGGLE");

        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pendingIntent = PendingIntent.getForegroundService(this, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getService(this, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        views.setOnClickPendingIntent(R.id.btn_toggle, pendingIntent);
        views.setOnClickPendingIntent(R.id.widget_image_view, pendingIntent);

        manager.updateAppWidget(thisWidget, views);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}