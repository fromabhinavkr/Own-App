package com.example.ownphotoonwall;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.RemoteViews;
import java.io.File;

public class DrawingWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_drawing);

            File file = new File(context.getFilesDir(), "drawing_" + id + ".png");

            if (file.exists()) {
                views.setImageViewBitmap(R.id.widget_image, BitmapFactory.decodeFile(file.getAbsolutePath()));
            } else {
                // FIXED: Create a tall rectangle (600x1000) instead of a square
                Bitmap defaultBitmap = Bitmap.createBitmap(600, 1000, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(defaultBitmap);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(Color.WHITE);
                canvas.drawRoundRect(new RectF(0, 0, 600, 1000), 60f, 60f, paint);
                views.setImageViewBitmap(R.id.widget_image, defaultBitmap);
            }

            Intent intent = new Intent(context, DrawingActivity.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);

            PendingIntent pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_image, pi);

            manager.updateAppWidget(id, views);
        }
    }
}