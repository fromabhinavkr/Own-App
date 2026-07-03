package com.example.ownphotoonwall;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

public class MazeWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_TOGGLE_MAZE = "com.example.ownphotoonwall.TOGGLE_MAZE";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_maze);

            Intent intent = new Intent(context, MazeWidgetProvider.class);
            intent.setAction(ACTION_TOGGLE_MAZE);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id, intent, flags);
            views.setOnClickPendingIntent(R.id.maze_canvas, pendingIntent);
            appWidgetManager.updateAppWidget(id, views);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        // Handle Theme Changes (Light/Dark mode switch)
        if (action != null && action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, MazeWidgetProvider.class);
            int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            onUpdate(context, appWidgetManager, allWidgetIds);
        }
        // Handle Game Start/Stop Tap
        else if (ACTION_TOGGLE_MAZE.equals(action)) {
            Intent serviceIntent = new Intent(context, MazeGameService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
        super.onReceive(context, intent);
    }
}