package com.example.ownphotoonwall;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

public class GlobeView extends View {
    private Bitmap originalMap;
    private BitmapShader shader;
    private Paint paint;
    private Matrix matrix;

    private float mapXOffset = 0;
    private float baseSpinSpeed = 1.5f;
    private float currentSpinSpeed = baseSpinSpeed;

    private GestureDetector gestureDetector;
    private Scroller scroller;

    public GlobeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // 1. Force the view's background to be completely transparent
        setBackgroundColor(0x00000000);

        originalMap = BitmapFactory.decodeResource(getResources(), R.drawable.earth_map);

        // 2. Set up the paint with Anti-Aliasing for perfectly smooth, non-pixelated edges
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        matrix = new Matrix();
        scroller = new Scroller(context, new DecelerateInterpolator());

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                scroller.forceFinished(true);
                currentSpinSpeed = 0;
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                mapXOffset += distanceX * 0.8f;
                invalidate();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                currentSpinSpeed = velocityX * 0.05f;
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                currentSpinSpeed = 40f;
                return true;
            }
        });
    }

    // 3. Scale the image and create the infinite looping shader only when the view size is determined
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float radius = Math.min(w, h) / 2.1f;
        int diameter = (int) (radius * 2);

        if (originalMap != null && diameter > 0) {
            float scale = (float) diameter / originalMap.getHeight();
            int newWidth = Math.round(originalMap.getWidth() * scale);

            Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalMap, newWidth, diameter, true);

            // TileMode.REPEAT automatically handles the infinite wrapping!
            shader = new BitmapShader(scaledBitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);
            paint.setShader(shader);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (shader == null) return;

        float width = getWidth();
        float height = getHeight();
        float radius = Math.min(width, height) / 2.1f;
        float cx = width / 2;
        float cy = height / 2;

        if (!scroller.isFinished()) {
            invalidate();
        } else {
            if (Math.abs(currentSpinSpeed) > baseSpinSpeed) {
                currentSpinSpeed *= 0.96f;
            } else {
                currentSpinSpeed = baseSpinSpeed;
            }
            mapXOffset += currentSpinSpeed;
        }

        // 4. Shift the shader matrix left/right to simulate spinning
        matrix.setTranslate(-mapXOffset, cy - radius);
        shader.setLocalMatrix(matrix);

        // 5. Draw a perfect circle. No clipping, no black boxes!
        canvas.drawCircle(cx, cy, radius, paint);

        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = gestureDetector.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP && currentSpinSpeed == 0) {
            currentSpinSpeed = baseSpinSpeed;
        }
        return handled || super.onTouchEvent(event);
    }
}