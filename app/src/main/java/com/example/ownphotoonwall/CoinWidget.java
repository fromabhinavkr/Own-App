package com.example.ownphotoonwall;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.Drawable;
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

            // Draw initial coin (Heads/Lion) using the high-quality XML vector
            Bitmap initialCoin = getStaticCoinBitmap(context, true);
            if (initialCoin != null) {
                views.setImageViewBitmap(R.id.coin_image_view, initialCoin);
            }

            Intent intent = new Intent(context, CoinWidget.class);
            intent.setAction("TOSS_COIN");
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_MUTABLE;

            views.setOnClickPendingIntent(R.id.coin_image_view, PendingIntent.getBroadcast(context, id, intent, flags));
            appWidgetManager.updateAppWidget(id, views);
        }
    }

    // Helper method to cleanly convert your amazing XML vectors to High-Res Bitmaps
    private Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        Drawable drawable = context.getDrawable(drawableId);
        if (drawable == null) return null;

        Bitmap bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    // Helper method to draw the static state
    private Bitmap getStaticCoinBitmap(Context context, boolean showHeads) {
        return getBitmapFromVectorDrawable(context, showHeads ? R.drawable.ic_coin_heads : R.drawable.ic_coin_tails);
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

            // Pre-render the new metallic XML vectors into bitmaps to prevent memory stutters
            final Bitmap headsBitmap = getBitmapFromVectorDrawable(appContext, R.drawable.ic_coin_heads);
            final Bitmap tailsBitmap = getBitmapFromVectorDrawable(appContext, R.drawable.ic_coin_tails);

            final Paint p = new Paint();
            p.setAntiAlias(true);
            p.setFilterBitmap(true);
            final RectF destRect = new RectF();

            // Darkest metal shade for a realistic 3D spinning edge
            final int colorEdge = Color.parseColor("#1A1A20");

            handler.post(new Runnable() {
                int frames = 0;
                final int TOTAL_FRAMES = 45;
                final boolean isHeadsResult = new Random().nextBoolean();
                final int halfSpins = isHeadsResult ? 6 : 7;
                final float totalRotation = halfSpins * (float) Math.PI;

                @Override
                public void run() {
                    frames++;
                    boolean isFlipping = frames < TOTAL_FRAMES;
                    int animFrame = Math.min(frames, TOTAL_FRAMES);

                    // PHYSICS EASING: Cubic Ease-Out
                    float progress = animFrame / (float) TOTAL_FRAMES;
                    float ease = 1.0f - (float) Math.pow(1.0f - progress, 3.0);

                    Bitmap b = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(b);

                    float cx = 400, cy = 400;

                    // PERFECT ALIGNMENT: 350px exactly matches the inner diameter of the XML rim
                    float currentRadius = 350;
                    float angle = ease * totalRotation;
                    float absScaleX = Math.abs((float) Math.cos(angle));

                    // Force perfect, sharp alignment on the exact final frame
                    if (!isFlipping) {
                        absScaleX = 1.0f;
                        angle = totalRotation;
                    }

                    float thickness = 35;
                    float zOffset = (float) Math.sin(angle) * thickness;
                    float frontX = cx + zOffset;
                    float backX = cx - zOffset;
                    boolean frontIsVisible = Math.cos(angle) >= 0;
                    float visibleX = frontIsVisible ? frontX : backX;

                    // Draw the 3D metal thickness (Edge)
                    p.setColor(colorEdge);
                    for (int i = 0; i <= 25; i++) {
                        float t = i / 25.0f;
                        float sliceX = backX + (frontX - backX) * t;
                        c.drawOval(sliceX - (currentRadius * absScaleX), cy - currentRadius,
                                sliceX + (currentRadius * absScaleX), cy + currentRadius, p);
                    }

                    // Draw the beautiful XML Vector Face
                    c.save();
                    c.scale(absScaleX, 1.0f, visibleX, cy);
                    Bitmap currentFace = frontIsVisible ? headsBitmap : tailsBitmap;

                    // We stretch the 800x800 bitmap to cover the exact center coordinate
                    destRect.set(visibleX - 400, cy - 400, visibleX + 400, cy + 400);
                    if (currentFace != null) {
                        c.drawBitmap(currentFace, null, destRect, p);
                    }
                    c.restore();

                    RemoteViews v = new RemoteViews(appContext.getPackageName(), R.layout.coin_widget);
                    v.setImageViewBitmap(R.id.coin_image_view, b);
                    appWidgetManager.updateAppWidget(id, v);

                    if (isFlipping) {
                        handler.postDelayed(this, 25);
                    } else {
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