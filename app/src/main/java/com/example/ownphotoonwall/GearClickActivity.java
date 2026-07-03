package com.example.ownphotoonwall;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.RemoteViews;

public class GearClickActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Vibrate immediately (We are now in the Foreground, so Android cannot block this!)
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, 255)); // Max strength
            } else {
                vibrator.vibrate(30);
            }
        }

        // 2. Rotate the gear visually
        SharedPreferences prefs = getSharedPreferences("GearPrefs", Context.MODE_PRIVATE);
        int currentAngle = prefs.getInt("angle", 0);
        int newAngle = (currentAngle + 45) % 360;
        prefs.edit().putInt("angle", newAngle).apply();

        RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget_gear);
        views.setFloat(R.id.gear_image, "setRotation", (float) newAngle);

        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName provider = new ComponentName(this, GearWidgetProvider.class);
        manager.updateAppWidget(provider, views);

        // 3. Close the invisible activity instantly and hide animations
        finish();
        overridePendingTransition(0, 0);
    }
}