package com.abhinav.ownapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import java.util.LinkedList;

public class RamGraphView extends View {

    private Paint linePaint, fillPaint, trackPaint, progressPaint, textPaint, percentSignPaint;
    private Path graphPath, fillPath;
    private LinkedList<Float> history = new LinkedList<>();
    private final int MAX_DATA_POINTS = 40;
    private float currentPercent = 0f;
    private boolean isDarkTheme = true;

    private boolean isFirstData = true;

    public RamGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
        for (int i = 0; i < MAX_DATA_POINTS; i++) history.add(0f);
    }

    public void setTheme(boolean isDark) {
        this.isDarkTheme = isDark;
        initPaints();
        invalidate();
    }

    private void initPaints() {
        int accentBlue = Color.parseColor("#4A90E2");
        int trackColor = isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA");
        int textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#1C1C1E");

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(accentBlue);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(6f);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        // INCREASED THICKNESS: Bumped stroke width from 12f to 24f for a thicker ring
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(trackColor);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(24f);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        // INCREASED THICKNESS: Bumped stroke width from 12f to 24f
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setColor(accentBlue);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(24f);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(textColor);
        textPaint.setTextSize(90f);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        percentSignPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        percentSignPaint.setColor(textColor);
        percentSignPaint.setTextSize(40f);
        percentSignPaint.setTextAlign(Paint.Align.LEFT);

        graphPath = new Path();
        fillPath = new Path();
    }

    public void addRamData(float percentUsed) {
        if (isFirstData) {
            for (int i = 0; i < MAX_DATA_POINTS; i++) {
                history.set(i, percentUsed);
            }
            isFirstData = false;
        }

        currentPercent = percentUsed;
        history.removeFirst();
        history.add(percentUsed);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        int gradientStart = Color.parseColor("#504A90E2");
        int gradientEnd = Color.TRANSPARENT;
        fillPaint.setShader(new LinearGradient(0, 0, 0, h, gradientStart, gradientEnd, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // --- 1. DRAW CIRCULAR PROGRESS (Left Side) ---
        float circleCenterX = w * 0.22f;
        float circleCenterY = h * 0.55f;
        float radius = Math.min(w, h) * 0.28f;

        // FULL CIRCLE: Starts at -90 (top center) and draws a full 360 degrees
        canvas.drawArc(circleCenterX - radius, circleCenterY - radius, circleCenterX + radius, circleCenterY + radius,
                -90, 360, false, trackPaint);

        // FULL CIRCLE PROGRESS: Multiplies by 360 instead of 270
        float sweepAngle = 360f * (currentPercent / 100f);
        canvas.drawArc(circleCenterX - radius, circleCenterY - radius, circleCenterX + radius, circleCenterY + radius,
                -90, sweepAngle, false, progressPaint);

        String pctString = String.valueOf((int) currentPercent);
        float textY = circleCenterY - ((textPaint.descent() + textPaint.ascent()) / 2);
        canvas.drawText(pctString, circleCenterX, textY, textPaint);

        float textWidth = textPaint.measureText(pctString);
        canvas.drawText("%", circleCenterX + (textWidth / 2f) + 5, textY, percentSignPaint);

        // --- 2. DRAW SMOOTH LINE GRAPH (Right Side) ---
        float graphStartX = w * 0.45f;

        float lineBottom = h * 0.70f;
        float graphTop = h * 0.25f;
        float graphHeight = lineBottom - graphTop;

        float fillBottom = h + 20f;

        float stepX = (w - graphStartX + 20f) / (MAX_DATA_POINTS - 1);

        graphPath.reset();
        fillPath.reset();

        float prevX = graphStartX;
        float prevY = lineBottom - (history.get(0) / 100f * graphHeight);

        graphPath.moveTo(prevX, prevY);
        fillPath.moveTo(prevX, fillBottom);
        fillPath.lineTo(prevX, prevY);

        for (int i = 1; i < MAX_DATA_POINTS; i++) {
            float curX = graphStartX + (i * stepX);
            float curY = lineBottom - (history.get(i) / 100f * graphHeight);

            float cx1 = prevX + (curX - prevX) / 2f;
            float cy1 = prevY;
            float cx2 = prevX + (curX - prevX) / 2f;
            float cy2 = curY;

            graphPath.cubicTo(cx1, cy1, cx2, cy2, curX, curY);
            fillPath.cubicTo(cx1, cy1, cx2, cy2, curX, curY);

            prevX = curX;
            prevY = curY;
        }

        fillPath.lineTo(prevX, fillBottom);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(graphPath, linePaint);
    }
}