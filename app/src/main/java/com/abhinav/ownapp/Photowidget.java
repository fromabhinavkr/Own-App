package com.abhinav.ownapp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;
import android.util.Size;

public class Photowidget extends AppWidgetProvider {

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.photowidget);

        // Fetch the specific image path
        SharedPreferences prefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE);
        String imageUriString = prefs.getString("image_path_" + appWidgetId, null);

        if (imageUriString != null) {
            Uri imageUri = Uri.parse(imageUriString);
            try {
                // Modern Android: Use loadThumbnail for better performance and memory management
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Bitmap thumbnail = context.getContentResolver().loadThumbnail(
                            imageUri, new Size(400, 400), null);
                    views.setImageViewBitmap(R.id.widget_image_view, thumbnail);
                } else {
                    // Fallback for older versions
                    views.setImageViewUri(R.id.widget_image_view, imageUri);
                }
            } catch (Exception e) {
                // If loading fails, fallback to standard URI or show a placeholder
                views.setImageViewUri(R.id.widget_image_view, imageUri);
                e.printStackTrace();
            }
        }

        // Configure click intent
        Intent intent = new Intent(context, ImagePickerActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        views.setOnClickPendingIntent(R.id.widget_image_view, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
}