package com.example.ownphotoonwall;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RemoteViews;

public class SnakeWidget extends AppWidgetProvider {

    private static final String PREFS_NAME = "SnakeWidgetPrefs";
    private static final String PREF_IS_RUNNING = "is_running_";

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isRunning = prefs.getBoolean(PREF_IS_RUNNING + appWidgetId, false);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.snake_widget);

        // UPDATE ICON
        int iconRes = isRunning ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        views.setImageViewResource(R.id.btn_toggle, iconRes);

        // EXPLICIT INTENT: Use ComponentName to ensure the broadcast reaches this provider
        Intent intent = new Intent(context, SnakeWidget.class);
        intent.setAction("TOGGLE_SNAKE");
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(R.id.btn_toggle, pendingIntent);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // Must call super first!
        super.onReceive(context, intent);

        if ("TOGGLE_SNAKE".equals(intent.getAction())) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                boolean isRunning = prefs.getBoolean(PREF_IS_RUNNING + appWidgetId, false);
                boolean newState = !isRunning;

                prefs.edit().putBoolean(PREF_IS_RUNNING + appWidgetId, newState).apply();

                // Explicit service intent
                Intent serviceIntent = new Intent(context, SnakeService.class);

                if (newState) {
                    // Try-catch block prevents crash if service type is missing or invalid
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

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
}