package com.abhinav.ownapp;

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
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.widget.RemoteViews;

public class WaterSensorService extends Service implements SensorEventListener {

    private SensorManager sensorManager;
    private float currentWaterAngle = 0;
    private float currentVelocity = 0;

    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel("water_channel", "Water Widget", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        Notification notification = new Notification.Builder(this, "water_channel")
                .setContentTitle("Water Widget Active")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(1, notification);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Apply deadzone to remove micro-jitter
            if (Math.abs(event.values[0]) > 0.05f) {
                updateWidget(event.values[0]);
            }
        }
    }

    private void updateWidget(float tilt) {
        // Physics
        float targetAngle = tilt * 0.15f;
        float springStrength = 0.05f;
        float damping = 0.92f;

        float acceleration = (targetAngle - currentWaterAngle) * springStrength;
        currentVelocity = (currentVelocity + acceleration) * damping;
        currentWaterAngle += currentVelocity;

        // CLAMPING: Restrict tilt to 1.0 (approx 57 degrees)
        float maxTilt = 1.0f;
        if (currentWaterAngle > maxTilt) currentWaterAngle = maxTilt;
        if (currentWaterAngle < -maxTilt) currentWaterAngle = maxTilt * -1;

        Bitmap bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Path containerPath = new Path();
        containerPath.addRoundRect(0, 0, 300, 300, 40, 40, Path.Direction.CW);
        canvas.clipPath(containerPath);

        // FIX 1: Change to TRANSPARENT so the XML background layer shows through!
        canvas.drawColor(Color.TRANSPARENT);

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        paint.setAlpha(220);

        Shader shader = new LinearGradient(0, 100, 0, 300,
                Color.parseColor("#72E7FF"), Color.parseColor("#005073"), Shader.TileMode.CLAMP);
        paint.setShader(shader);

        // FORCE FILL LOGIC: Calculate surface points with a wider base
        float centerX = 150;
        float centerY = 200;

        float x1 = centerX - (450 * (float)Math.cos(currentWaterAngle));
        float y1 = centerY - (450 * (float)Math.sin(currentWaterAngle));
        float x2 = centerX + (450 * (float)Math.cos(currentWaterAngle));
        float y2 = centerY + (float)Math.sin(currentWaterAngle) * 450;

        Path waterPath = new Path();
        waterPath.moveTo(x1, y1);
        waterPath.quadTo(centerX, centerY - 10, x2, y2);
        waterPath.lineTo(300, 300);
        waterPath.lineTo(0, 300);
        waterPath.close();

        canvas.drawPath(waterPath, paint);

        RemoteViews views = new RemoteViews(getPackageName(), R.layout.water_widget);

        // FIX 2: Re-apply the Theme colors on every frame update
        SharedPreferences themePrefs = getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
        boolean isDarkTheme = themePrefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        int rootBgColor = isDarkTheme ? Color.parseColor("#151515") : Color.WHITE;
        int buttonTint = isDarkTheme ? Color.WHITE : Color.parseColor("#222222");

        views.setInt(R.id.water_bg_layer, "setColorFilter", rootBgColor);
        views.setInt(R.id.btn_toggle_water, "setColorFilter", buttonTint);

        // Make sure the pause icon stays visible while the service is running
        views.setImageViewResource(R.id.btn_toggle_water, android.R.drawable.ic_media_pause);

        views.setImageViewBitmap(R.id.water_canvas, bitmap);
        AppWidgetManager.getInstance(getApplicationContext())
                .updateAppWidget(new ComponentName(this, WaterWidgetProvider.class), views);
    }

    @Override
    public void onDestroy() {
        sensorManager.unregisterListener(this);
        super.onDestroy();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override
    public IBinder onBind(Intent intent) { return null; }
}