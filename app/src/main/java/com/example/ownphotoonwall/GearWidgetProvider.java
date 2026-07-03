package com.example.ownphotoonwall;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class GearWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_gear);

            // Route the click to our invisible Foreground Activity
            Intent intent = new Intent(context, GearClickActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    id,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.gear_image, pendingIntent);

            // Ensure the gear stays at the correct angle when the phone restarts
            SharedPreferences prefs = context.getSharedPreferences("GearPrefs", Context.MODE_PRIVATE);
            int currentAngle = prefs.getInt("angle", 0);
            views.setFloat(R.id.gear_image, "setRotation", (float) currentAngle);

            appWidgetManager.updateAppWidget(id, views);
        }
    }
}