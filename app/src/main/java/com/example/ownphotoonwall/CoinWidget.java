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

            // Draw initial coin (Heads/Lion) so it's not blank
            Bitmap initialCoin = getStaticCoinBitmap(context, true);
            views.setImageViewBitmap(R.id.coin_image_view, initialCoin);

            Intent intent = new Intent(context, CoinWidget.class);
            intent.setAction("TOSS_COIN");
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_MUTABLE;

            views.setOnClickPendingIntent(R.id.coin_image_view, PendingIntent.getBroadcast(context, id, intent, flags));
            appWidgetManager.updateAppWidget(id, views);
        }
    }

    // Helper method to draw the initial state
    private Bitmap getStaticCoinBitmap(Context context, boolean showHeads) {
        Bitmap b = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setFilterBitmap(true);

        float cx = 400, cy = 400;
        float currentRadius = 385;

        // Draw Edge
        p.setColor(Color.parseColor("#4A4A55"));
        c.drawCircle(cx, cy, currentRadius, p);

        // Draw Face
        Shader silverSweep = new SweepGradient(cx, cy,
                new int[]{Color.parseColor("#E4E4E9"), Color.parseColor("#FFFFFF"), Color.parseColor("#B4B4B9"), Color.parseColor("#E4E4E9")}, null);
        p.setShader(silverSweep);
        c.drawCircle(cx, cy, currentRadius - 5, p);
        p.setShader(null);

        // Draw Icon
        Bitmap icon = BitmapFactory.decodeResource(context.getResources(), showHeads ? R.drawable.icon_lion : R.drawable.icon_torch);
        RectF iconRect = new RectF(cx - currentRadius + 5, cy - currentRadius + 5, cx + currentRadius - 5, cy + currentRadius - 5);
        c.drawBitmap(icon, null, iconRect, p);

        return b;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if ("TOSS_COIN".equals(intent.getAction())) {
            final int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
            final Context appContext = context.getApplicationContext();
            final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(appContext);
            final PendingResult pendingResult = goAsync();
            final Handler handler = new Handler(Looper.getMainLooper());

            final Bitmap iconLion = BitmapFactory.decodeResource(appContext.getResources(), R.drawable.icon_lion);
            final Bitmap iconTorch = BitmapFactory.decodeResource(appContext.getResources(), R.drawable.icon_torch);

            handler.post(new Runnable() {
                int frames = 0;
                final int TOTAL_FRAMES = 32;
                final boolean isHeadsResult = new Random().nextBoolean();
                final int halfSpins = isHeadsResult ? 6 : 7;
                final float totalRotation = halfSpins * (float) Math.PI;

                @Override
                public void run() {
                    frames++;
                    boolean isFlipping = frames < TOTAL_FRAMES;
                    int animFrame = Math.min(frames, TOTAL_FRAMES);

                    Bitmap b = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(b);
                    Paint p = new Paint();
                    p.setAntiAlias(true);
                    p.setFilterBitmap(true);

                    float cx = 400, cy = 400;
                    float currentRadius = 385;
                    float angle = (animFrame / (float)TOTAL_FRAMES) * totalRotation;
                    float absScaleX = Math.abs((float) Math.cos(angle));

                    float thickness = 35;
                    float zOffset = (float) Math.sin(angle) * thickness;
                    float frontX = cx + zOffset;
                    float backX = cx - zOffset;
                    boolean frontIsVisible = Math.cos(angle) >= 0;
                    float visibleX = frontIsVisible ? frontX : backX;

                    // Edge
                    for (int i = 0; i <= 25; i++) {
                        float t = i / 25.0f;
                        float sliceX = backX + (frontX - backX) * t;
                        p.setColor(Color.parseColor("#2D2D35"));
                        c.drawOval(sliceX - (currentRadius * absScaleX), cy - currentRadius,
                                sliceX + (currentRadius * absScaleX), cy + currentRadius, p);
                    }

                    // Face
                    Shader silverSweep = new SweepGradient(visibleX, cy,
                            new int[]{Color.parseColor("#E4E4E9"), Color.parseColor("#FFFFFF"), Color.parseColor("#9999A0"), Color.parseColor("#E4E4E9")}, null);
                    Matrix matrix = new Matrix();
                    matrix.setScale(absScaleX, 1.0f, visibleX, cy);
                    matrix.postRotate(frames * 20, visibleX, cy);
                    silverSweep.setLocalMatrix(matrix);
                    p.setShader(silverSweep);
                    c.drawOval(visibleX - (currentRadius * absScaleX), cy - (currentRadius),
                            visibleX + (currentRadius * absScaleX), cy + (currentRadius), p);
                    p.setShader(null);

                    // Icon
                    c.save();
                    c.scale(absScaleX, 1.0f, visibleX, cy);
                    Bitmap currentIcon = frontIsVisible ? iconLion : iconTorch;
                    RectF iconRect = new RectF(visibleX - currentRadius, cy - currentRadius, visibleX + currentRadius, cy + currentRadius);
                    c.drawBitmap(currentIcon, null, iconRect, p);
                    c.restore();

                    RemoteViews v = new RemoteViews(appContext.getPackageName(), R.layout.coin_widget);
                    v.setImageViewBitmap(R.id.coin_image_view, b);
                    appWidgetManager.updateAppWidget(id, v);

                    if (isFlipping) handler.postDelayed(this, 35);
                    else {
                        triggerVibration(appContext);
                        pendingResult.finish();
                    }
                }
            });
        }
    }

    private void triggerVibration(Context context) {
        Vibrator vibrator = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ?
                ((VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)).getDefaultVibrator() :
                (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK);
                vibrator.vibrate(effect, new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build());
            } else {
                vibrator.vibrate(150);
            }
        }
    }
}