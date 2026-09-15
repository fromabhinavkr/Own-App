package com.abhinav.ownapp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

public class FidgetSpinnerWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_fidget_spinner);

            // Open the new Interactive Window when the widget is tapped
            Intent intent = new Intent(context, SpinnerClickActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, flags);

            views.setOnClickPendingIntent(R.id.imgFidgetSpinner, pendingIntent);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}