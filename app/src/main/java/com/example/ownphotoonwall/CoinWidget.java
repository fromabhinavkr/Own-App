package com.example.ownphotoonwall;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.VibrationEffect;
import android.widget.RemoteViews;
import java.util.Random;

public class CoinWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.coin_widget);

            Intent intent = new Intent(context, CoinWidget.class);
            intent.setAction("TOSS_COIN");
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }

            PendingIntent pending = PendingIntent.getBroadcast(context, id, intent, flags);
            views.setOnClickPendingIntent(R.id.btn_toss_coin, pending);

            appWidgetManager.updateAppWidget(id, views);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if ("TOSS_COIN".equals(intent.getAction())) {
            final int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
            final Context appContext = context.getApplicationContext();
            final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(appContext);

            // Bypasses Android 14 restrictions by running a secure async window
            final PendingResult pendingResult = goAsync();
            final Handler handler = new Handler(Looper.getMainLooper());

            handler.post(new Runnable() {
                int frames = 0;
                final boolean isHeadsResult = new Random().nextBoolean();

                @Override
                public void run() {
                    frames++;
                    boolean isFlipping = frames < 12;

                    // Create massive canvas
                    Bitmap b = Bitmap.createBitmap(750, 750, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(b);
                    Paint p = new Paint();
                    p.setAntiAlias(true);

                    float cx = 375, cy = 375, radius = 350;
                    float scale = isFlipping ? (float)Math.abs(Math.sin(frames * 0.8)) : 1.0f;

                    // Draw 3D Rims
                    p.setColor(Color.parseColor("#707070"));
                    c.drawCircle(cx, cy, radius + 15, p);
                    p.setColor(Color.parseColor("#B0B0B0"));
                    c.drawCircle(cx, cy, radius + 8, p);

                    // Silver Gradient Face
                    Shader silverGradient = new LinearGradient(
                            cx - radius, cy - radius,
                            cx + radius, cy + radius,
                            new int[]{
                                    Color.parseColor("#E8E8E8"),
                                    Color.parseColor("#FFFFFF"),
                                    Color.parseColor("#B8B8B8"),
                                    Color.parseColor("#888888")
                            },
                            new float[]{0.0f, 0.3f, 0.7f, 1.0f},
                            Shader.TileMode.CLAMP);

                    p.setShader(silverGradient);
                    c.drawOval(cx - (radius * scale), cy - radius, cx + (radius * scale), cy + radius, p);
                    p.setShader(null);

                    // Render Stamped 8-Bit Letters
                    if (!isFlipping) {
                        int dotSize = 45;
                        int spacing = 100;
                        int startX = 275;
                        int startY = 175;

                        int[][] pattern = isHeadsResult ? new int[][]{
                                {1,0,1}, {1,0,1}, {1,1,1}, {1,0,1}, {1,0,1} // H
                        } : new int[][]{
                                {1,1,1}, {0,1,0}, {0,1,0}, {0,1,0}, {0,1,0} // T
                        };

                        for (int row = 0; row < 5; row++) {
                            for (int col = 0; col < 3; col++) {
                                if (pattern[row][col] == 1) {
                                    p.setColor(Color.parseColor("#909090"));
                                    c.drawCircle(startX + (col * spacing) + 6, startY + (row * spacing) + 6, dotSize, p);

                                    p.setColor(Color.parseColor("#151515"));
                                    c.drawCircle(startX + (col * spacing), startY + (row * spacing), dotSize, p);
                                }
                            }
                        }
                    }

                    RemoteViews v = new RemoteViews(appContext.getPackageName(), R.layout.coin_widget);
                    v.setImageViewBitmap(R.id.coin_image_view, b);
                    v.setImageViewResource(R.id.btn_toss_coin, isFlipping ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                    appWidgetManager.updateAppWidget(id, v);

                    if (isFlipping) {
                        handler.postDelayed(this, 80);
                    } else {
                        triggerVibration(appContext);
                        // Safely close the system broadcast token
                        pendingResult.finish();
                    }
                }
            });
        }
    }

    private void triggerVibration(Context context) {
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK);
                android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build();
                vibrator.vibrate(effect, audioAttributes);
            } else {
                vibrator.vibrate(150);
            }
        }
    }
}