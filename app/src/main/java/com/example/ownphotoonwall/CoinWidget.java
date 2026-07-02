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

            final PendingResult pendingResult = goAsync();
            final Handler handler = new Handler(Looper.getMainLooper());

            handler.post(new Runnable() {
                int frames = 0;
                final int TOTAL_FRAMES = 26; // Smooth, cinematic arc
                final boolean isHeadsResult = new Random().nextBoolean();

                // Front face is ALWAYS Heads, Back is ALWAYS Tails.
                // Math forces the correct face to land forward.
                final int halfSpins = isHeadsResult ? 6 : 7;
                final float totalRotation = halfSpins * (float) Math.PI;

                @Override
                public void run() {
                    frames++;
                    boolean isFlipping = frames < TOTAL_FRAMES;
                    int animFrame = Math.min(frames, TOTAL_FRAMES);

                    Bitmap b = Bitmap.createBitmap(750, 750, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(b);
                    Paint p = new Paint();
                    p.setAntiAlias(true);

                    float cx = 375, cy = 375;

                    // --- 1. 3D ELEVATION (Jumps toward you) ---
                    float elevationProgress = (float) Math.sin((animFrame / (float)TOTAL_FRAMES) * Math.PI);
                    float currentRadius = 310 + (60 * elevationProgress);

                    // --- 2. 3D MATH (Angle, Squish, and Depth) ---
                    float angle = (animFrame / (float)TOTAL_FRAMES) * totalRotation;
                    float absScaleY = Math.abs((float) Math.cos(angle));

                    // Z-Depth calculates how much the coin tilts up or down
                    float thickness = 40; // True physical thickness of the coin
                    float zOffset = (float) Math.sin(angle) * thickness;

                    // Front and Back face coordinates based on perspective
                    float frontY = cy + zOffset;
                    float backY = cy - zOffset;

                    // If cos(angle) > 0, the Front Face (Heads) is closest to the camera
                    boolean frontIsVisible = Math.cos(angle) >= 0;
                    float visibleY = frontIsVisible ? frontY : backY;

                    // --- 3. VOLUMETRIC STACKING (Drawing the heavy metal cylinder edge) ---
                    // By drawing slices between the back and front face, we create a perfect 3D edge
                    p.setColor(Color.parseColor("#5A5A66")); // Dark shaded metal for the edge
                    p.setStyle(Paint.Style.FILL);
                    int slices = 25;
                    for (int i = 0; i <= slices; i++) {
                        float t = i / (float) slices;
                        float sliceY = backY + (frontY - backY) * t;
                        c.drawOval(cx - currentRadius, sliceY - (currentRadius * absScaleY),
                                cx + currentRadius, sliceY + (currentRadius * absScaleY), p);
                    }

                    // --- 4. THE SHINY METALLIC FACE ---
                    // A SweepGradient simulates realistic light reflecting off turning metal
                    Shader silverSweep = new SweepGradient(cx, visibleY,
                            new int[]{
                                    Color.parseColor("#C0C0C8"), // Darker base
                                    Color.parseColor("#FFFFFF"), // Bright glint
                                    Color.parseColor("#808088"), // Deep shadow
                                    Color.parseColor("#E6E6EA"), // Light base
                                    Color.parseColor("#FFFFFF"), // Bright glint
                                    Color.parseColor("#808088"), // Deep shadow
                                    Color.parseColor("#C0C0C8")  // Darker base
                            }, null);

                    // Map the lighting to the squished 3D perspective and rotate it over time!
                    Matrix matrix = new Matrix();
                    matrix.setScale(1.0f, absScaleY, cx, visibleY);
                    matrix.postRotate(frames * 18, cx, visibleY); // Light spins as the coin flips
                    silverSweep.setLocalMatrix(matrix);

                    p.setShader(silverSweep);
                    c.drawOval(cx - currentRadius, visibleY - (currentRadius * absScaleY),
                            cx + currentRadius, visibleY + (currentRadius * absScaleY), p);
                    p.setShader(null);

                    // --- 5. RENDER 3D STAMPED LETTERS (H / T) ---
                    c.save();
                    c.scale(1.0f, absScaleY, cx, visibleY); // Squishes the text perfectly onto the face

                    int dotSize = 45;
                    int spacing = 100;
                    int startX = 275;
                    // Dynamically shift the Y start position so it tracks the moving 3D face
                    float startY = visibleY - 200;

                    int[][] pattern = frontIsVisible ? new int[][]{
                            {1,0,1}, {1,0,1}, {1,1,1}, {1,0,1}, {1,0,1} // H
                    } : new int[][]{
                            {1,1,1}, {0,1,0}, {0,1,0}, {0,1,0}, {0,1,0} // T
                    };

                    for (int row = 0; row < 5; row++) {
                        for (int col = 0; col < 3; col++) {
                            if (pattern[row][col] == 1) {
                                // Engraved Shadow
                                p.setColor(Color.parseColor("#808088"));
                                c.drawCircle(startX + (col * spacing) + 6, startY + (row * spacing) + 6, dotSize, p);

                                // Solid Black Dot
                                p.setColor(Color.parseColor("#151515"));
                                c.drawCircle(startX + (col * spacing), startY + (row * spacing), dotSize, p);
                            }
                        }
                    }
                    c.restore();

                    RemoteViews v = new RemoteViews(appContext.getPackageName(), R.layout.coin_widget);
                    v.setImageViewBitmap(R.id.coin_image_view, b);
                    v.setImageViewResource(R.id.btn_toss_coin, isFlipping ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                    appWidgetManager.updateAppWidget(id, v);

                    if (isFlipping) {
                        handler.postDelayed(this, 40); // 40ms = highly fluid 25fps animation
                    } else {
                        triggerVibration(appContext);
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