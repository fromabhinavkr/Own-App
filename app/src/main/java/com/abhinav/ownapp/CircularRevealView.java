package com.abhinav.ownapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class CircularRevealView extends View {
    private Path clipPath = new Path();
    private float cx, cy, radius;

    public CircularRevealView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setRevealInfo(float cx, float cy, float radius) {
        this.cx = cx;
        this.cy = cy;
        this.radius = radius;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        clipPath.reset();
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        super.onDraw(canvas);
    }
}