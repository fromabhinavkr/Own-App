package com.abhinav.ownapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

public class StarfieldView extends View {
    private Paint paint;
    private Star[] stars;
    private final int STAR_COUNT = 250; // Number of stars

    // A simple class to hold the properties of each star
    private class Star {
        float x, y, radius, speed;
        int alpha;
        boolean movingRight; // NEW: Tells the star which way to travel
    }

    public StarfieldView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stars = new Star[STAR_COUNT];
        Random random = new Random();

        // Generate random positions, sizes, speeds, and directions
        for (int i = 0; i < STAR_COUNT; i++) {
            stars[i] = new Star();
            stars[i].x = random.nextFloat(); // 0.0 to 1.0 multiplier
            stars[i].y = random.nextFloat();
            stars[i].radius = random.nextFloat() * 3.0f + 0.5f;
            stars[i].speed = random.nextFloat() * 0.0015f + 0.0002f;
            stars[i].alpha = random.nextInt(156) + 100;

            // 50% chance to move right, 50% chance to move left
            stars[i].movingRight = random.nextBoolean();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        // Draw the deep space void background
        canvas.drawColor(Color.parseColor("#030308"));

        for (Star star : stars) {
            paint.setColor(Color.WHITE);
            paint.setAlpha(star.alpha);

            // Draw the star at its current position
            canvas.drawCircle(star.x * width, star.y * height, star.radius, paint);

            // NEW: Move the star based on its assigned direction
            if (star.movingRight) {
                star.x += star.speed;
                // If it goes off the right edge, wrap it back to the left
                if (star.x > 1.0f) {
                    star.x = 0f;
                    star.y = (float) Math.random();
                }
            } else {
                star.x -= star.speed;
                // If it goes off the left edge, wrap it back to the right
                if (star.x < 0f) {
                    star.x = 1.0f;
                    star.y = (float) Math.random();
                }
            }

            // Random twinkle effect
            if (Math.random() > 0.96) {
                star.alpha = (int) (Math.random() * 155) + 100;
            }
        }

        // Loop the animation continuously
        postInvalidateOnAnimation();
    }
}