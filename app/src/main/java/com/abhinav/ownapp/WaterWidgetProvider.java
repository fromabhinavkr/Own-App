package com.abhinav.ownapp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.widget.RemoteViews;

public class WaterWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_TOGGLE = "TOGGLE_WATER";
    private static final String PREFS_NAME = "WaterWidgetPrefs";
    private static final String PREF_IS_RUNNING = "is_running_";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidgetUI(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_TOGGLE.equals(intent.getAction())) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                return;
            }

            // Read the running state
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isRunning = prefs.getBoolean(PREF_IS_RUNNING + appWidgetId, false);

            boolean newState = !isRunning;
            prefs.edit().putBoolean(PREF_IS_RUNNING + appWidgetId, newState).apply();

            Intent serviceIntent = new Intent(context, WaterSensorService.class);
            if (newState) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                context.stopService(serviceIntent);
            }

            updateWidgetUI(context, AppWidgetManager.getInstance(context), appWidgetId);
        }
    }

    private void updateWidgetUI(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // 1. Get the running state from the Water prefs
        SharedPreferences waterPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isRunning = waterPrefs.getBoolean(PREF_IS_RUNNING + appWidgetId, false);

        // 2. Get the global Theme state from the Snake prefs!
        SharedPreferences themePrefs = context.getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
        boolean isDarkTheme = themePrefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.water_widget);

        // 3. Tint the new background layer based on the theme
        int rootBgColor = isDarkTheme ? Color.parseColor("#151515") : Color.WHITE;
        views.setInt(R.id.water_bg_layer, "setColorFilter", rootBgColor);

        // 4. Update Play/Pause icon
        int iconRes = isRunning ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        views.setImageViewResource(R.id.btn_toggle_water, iconRes);

        Intent intent = new Intent(context, WaterWidgetProvider.class);
        intent.setAction(ACTION_TOGGLE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.btn_toggle_water, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}