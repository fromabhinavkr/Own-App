package com.example.ownphotoonwall;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;

public class DrawingView extends View {
    private Path currentPath = new Path();
    private int currentColor = Color.BLACK;
    private float currentStrokeWidth = 12f;
    private boolean isEraser = false;
    private int bgMode = 1; // Used for drawing lines (0=Trans, 1=White, 2=Black, 3=WhiteLined, 4=BlackLined)
    private final List<Stroke> strokes = new ArrayList<>();

    private float mX, mY;
    private static final float TOUCH_TOLERANCE = 4;

    private static class Stroke {
        Path path;
        int color;
        float strokeWidth;
        boolean isEraser;
        Stroke(Path p, int c, float w, boolean e) {
            this.path = p; this.color = c; this.strokeWidth = w; this.isEraser = e;
        }
    }

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setColor(int color) { this.currentColor = color; this.isEraser = false; }
    public int getCurrentColor() { return currentColor; }
    public void setEraser(boolean eraser) { this.isEraser = eraser; }

    public void setStrokeWidth(float width) { this.currentStrokeWidth = width; }
    public float getStrokeWidth() { return currentStrokeWidth; }

    // NEW: Method to set the background pattern
    public void setBgMode(int mode) { this.bgMode = mode; invalidate(); }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        drawStrokes(canvas);
    }

    public void drawStrokes(Canvas canvas) {
        // --- DRAW BACKGROUND LINES IF APPLICABLE ---
        if (bgMode == 3) {
            drawLines(canvas, Color.parseColor("#B2CCDF")); // Classic light blue notebook line
        } else if (bgMode == 4) {
            drawLines(canvas, Color.parseColor("#333333")); // Dark mode notebook line
        }

        // --- DRAW STROKES ---
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);

        for (Stroke s : strokes) {
            paint.setStrokeWidth(s.strokeWidth);
            if (s.isEraser) {
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            } else {
                paint.setXfermode(null);
                paint.setColor(s.color);
            }
            canvas.drawPath(s.path, paint);
        }

        paint.setStrokeWidth(currentStrokeWidth);
        if (isEraser) {
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        } else {
            paint.setXfermode(null);
            paint.setColor(currentColor);
        }
        canvas.drawPath(currentPath, paint);
    }

    // Helper to draw repeating horizontal lines
    private void drawLines(Canvas canvas, int color) {
        Paint linePaint = new Paint();
        linePaint.setColor(color);
        linePaint.setStrokeWidth(3f);
        float spacing = 80f; // Distance between notebook lines
        for (float y = spacing; y < getHeight(); y += spacing) {
            canvas.drawLine(0, y, getWidth(), y, linePaint);
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        performClick();
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentPath = new Path();
                currentPath.moveTo(x, y);
                mX = x;
                mY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(x - mX);
                float dy = Math.abs(y - mY);
                if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                    currentPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
                    mX = x;
                    mY = y;
                }
                break;
            case MotionEvent.ACTION_UP:
                currentPath.lineTo(mX, mY);
                strokes.add(new Stroke(currentPath, currentColor, currentStrokeWidth, isEraser));
                currentPath = new Path();
                break;
        }
        invalidate();
        return true;
    }

    @Override public boolean performClick() { return super.performClick(); }
    public void undo() { if (!strokes.isEmpty()) { strokes.remove(strokes.size() - 1); invalidate(); } }
    public void clear() { strokes.clear(); currentPath.reset(); invalidate(); }
}