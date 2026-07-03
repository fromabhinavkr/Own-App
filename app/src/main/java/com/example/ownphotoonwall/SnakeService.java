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
        startForegroundProtection();
        updateWidget();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        handler.removeCallbacks(gameLoop);
        handler.post(gameLoop);
        return START_STICKY;
    }

    private void startForegroundProtection() {
        String channelId = "snake_game_channel";
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Snake Widget", NotificationManager.IMPORTANCE_LOW);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("Own's Auto Snake")
                .setContentText("Game is running...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        startForeground(2, notification);
    }

    private void updateWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName thisWidget = new ComponentName(this, SnakeWidget.class);
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.snake_widget);

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        // --- NEW LOGIC FOR BACKGROUND ---
        int rootBgColor = isDarkTheme ? Color.parseColor("#151515") : Color.WHITE;
        int snakeColor = isDarkTheme ? Color.WHITE : Color.BLACK;
        int buttonTint = isDarkTheme ? Color.WHITE : Color.parseColor("#222222");

        // Tint the background layer dynamically
        views.setInt(R.id.widget_bg_layer, "setColorFilter", rootBgColor);

        Bitmap bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // The canvas is now ALWAYS transparent so the background layer shows through!
        canvas.drawColor(Color.TRANSPARENT);

        Paint paint = new Paint();
        int cellSize = 400 / engine.width;
        paint.setAntiAlias(true);

        paint.setColor(Color.RED);
        canvas.drawCircle((engine.food.x * cellSize) + (cellSize / 2f), (engine.food.y * cellSize) + (cellSize / 2f), (cellSize / 2f) - 2f, paint);

        paint.setColor(snakeColor);
        for (Point p : engine.snake) {
            canvas.drawCircle((p.x * cellSize) + (cellSize / 2f), (p.y * cellSize) + (cellSize / 2f), (cellSize / 2f) - 2f, paint);
        }

        views.setImageViewBitmap(R.id.widget_image_view, bitmap);

        int iconRes = isRunning ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        views.setImageViewResource(R.id.btn_toggle, iconRes);

        views.setInt(R.id.btn_toggle, "setColorFilter", buttonTint);

        views.setViewVisibility(R.id.widget_title_text, isRunning ? View.GONE : View.VISIBLE);
        views.setTextColor(R.id.widget_title_text, isDarkTheme ? Color.parseColor("#888888") : Color.parseColor("#666666"));

        manager.updateAppWidget(thisWidget, views);
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        handler.removeCallbacks(gameLoop);
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}