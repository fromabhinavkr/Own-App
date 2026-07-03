package com.example.ownphotoonwall;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.widget.RemoteViews;

public class SnakeWidget extends AppWidgetProvider {

    public static final String PREFS_NAME = "SnakeWidgetPrefs";
    public static final String PREF_IS_RUNNING = "is_running_";
    public static final String PREF_IS_DARK = "is_dark_theme";

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isRunning = prefs.getBoolean(PREF_IS_RUNNING + appWidgetId, false);
        boolean isDarkTheme = prefs.getBoolean(PREF_IS_DARK, true);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.snake_widget);

        // --- NEW: TINT THE DYNAMIC BACKGROUND LAYER ---
        int rootBgColor = isDarkTheme ? Color.parseColor("#151515") : Color.WHITE;
        views.setInt(R.id.widget_bg_layer, "setColorFilter", rootBgColor);

        // UPDATE PLAY/PAUSE ICON
        int iconRes = isRunning ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        views.setImageViewResource(R.id.btn_toggle, iconRes);

        // PLAY/PAUSE BUTTON INTENT
        Intent playIntent = new Intent(context, SnakeWidget.class);
        playIntent.setAction("TOGGLE_SNAKE");
        playIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent playPending = PendingIntent.getBroadcast(
                context, appWidgetId, playIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_toggle, playPending);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();

        if (action != null) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

                if ("TOGGLE_SNAKE".equals(action)) {
                    boolean isRunning = prefs.getBoolean(PREF_IS_RUNNING + appWidgetId, false);
                    boolean newState = !isRunning;
                    prefs.edit().putBoolean(PREF_IS_RUNNING + appWidgetId, newState).apply();

                    Intent serviceIntent = new Intent(context, SnakeService.class);
                    if (newState) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent);
                            } else {
                                context.startService(serviceIntent);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        context.stopService(serviceIntent);
                    }
                    updateAppWidget(context, AppWidgetManager.getInstance(context), appWidgetId);
                }
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