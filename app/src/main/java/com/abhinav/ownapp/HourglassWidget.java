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

public class HourglassWidget extends AppWidgetProvider {

    private static final String PREFS_NAME = "HourglassPrefs";
    private static final String PREF_IS_RUNNING = "hg_running_";

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // 1. Get the running state from the Hourglass prefs
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isRunning = prefs.getBoolean(PREF_IS_RUNNING + appWidgetId, false);

        // 2. Get the global Theme state from the Snake prefs!
        SharedPreferences themePrefs = context.getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
        boolean isDarkTheme = themePrefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.hourglass_widget);

        // 3. Tint the new background layer based on the theme
        int rootBgColor = isDarkTheme ? Color.parseColor("#151515") : Color.WHITE;
        views.setInt(R.id.hourglass_bg_layer, "setColorFilter", rootBgColor);

        // 4. Update Play/Pause icon
        int iconRes = isRunning ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        views.setImageViewResource(R.id.btn_toggle_hourglass, iconRes);

        Intent intent = new Intent(context, HourglassWidget.class);
        intent.setAction("TOGGLE_HOURGLASS");
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(R.id.btn_toggle_hourglass, pendingIntent);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if ("TOGGLE_HOURGLASS".equals(intent.getAction())) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                boolean isRunning = prefs.getBoolean(PREF_IS_RUNNING + appWidgetId, false);
                boolean newState = !isRunning;

                prefs.edit().putBoolean(PREF_IS_RUNNING + appWidgetId, newState).apply();

                Intent serviceIntent = new Intent(context, HourglassService.class);

                if (newState) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent);
                        } else {
                            context.startService(serviceIntent);
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                } else {
                    context.stopService(serviceIntent);
                }

                updateAppWidget(context, AppWidgetManager.getInstance(context), appWidgetId);
            }
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
}