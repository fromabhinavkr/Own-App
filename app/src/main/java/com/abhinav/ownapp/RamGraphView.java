package com.abhinav.ownapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import java.util.LinkedList;

public class RamGraphView extends View {

    private Paint linePaint, fillPaint, trackPaint, progressPaint, textPaint, percentSignPaint;
    private Paint gridLinePaint, gridTextPaint;
    private Path graphPath, fillPath, gridPath;
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

        // Semi-transparent colors for the background grid
        int gridColor = isDarkTheme ? Color.parseColor("#26FFFFFF") : Color.parseColor("#1A000000");
        int gridTextColor = isDarkTheme ? Color.parseColor("#99FFFFFF") : Color.parseColor("#80000000");

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(accentBlue);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(6f);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(trackColor);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(24f);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setColor(accentBlue);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(24f);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(textColor);
        textPaint.setTextSize(80f);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        percentSignPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        percentSignPaint.setColor(textColor);
        percentSignPaint.setTextSize(35f);
        percentSignPaint.setTextAlign(Paint.Align.LEFT);

        // --- NEW: Paints for the Dotted Grid and Labels ---
        gridLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridLinePaint.setColor(gridColor);
        gridLinePaint.setStyle(Paint.Style.STROKE);
        gridLinePaint.setStrokeWidth(2.5f);
        // Creates the dotted/dashed effect
        gridLinePaint.setPathEffect(new DashPathEffect(new float[]{10f, 10f}, 0));

        gridTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridTextPaint.setColor(gridTextColor);
        gridTextPaint.setTextSize(26f);
        gridTextPaint.setTextAlign(Paint.Align.LEFT);

        graphPath = new Path();
        fillPath = new Path();
        gridPath = new Path();
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

        canvas.drawArc(circleCenterX - radius, circleCenterY - radius, circleCenterX + radius, circleCenterY + radius,
                -90, 360, false, trackPaint);

        float sweepAngle = 360f * (currentPercent / 100f);
        canvas.drawArc(circleCenterX - radius, circleCenterY - radius, circleCenterX + radius, circleCenterY + radius,
                -90, sweepAngle, false, progressPaint);

        String pctString = String.valueOf((int) currentPercent);
        float textY = circleCenterY - ((textPaint.descent() + textPaint.ascent()) / 2);

        float numWidth = textPaint.measureText(pctString);
        float pctWidth = percentSignPaint.measureText("%");
        float gap = 5f;

        float shiftLeft = (pctWidth + gap) / 2f;
        float numberX = circleCenterX - shiftLeft;

        canvas.drawText(pctString, numberX, textY, textPaint);

        float percentX = numberX + (numWidth / 2f) + gap;
        canvas.drawText("%", percentX, textY, percentSignPaint);

        // --- 2. DRAW THE DOTTED GRID & WAVE GRAPH (Right Side) ---

        // Strictly bound the graph to a safe box on the right side
        float graphStartX = w * 0.45f;
        float graphEndX = w * 0.88f; // Leave 12% space on the far right for text labels
        float graphTop = h * 0.15f;
        float graphBottom = h * 0.85f;
        float graphHeight = graphBottom - graphTop;

        // Draw Grid Lines and Percent Labels
        float[] gridPercentages = {100f, 75f, 50f, 25f, 0f};
        for (float p : gridPercentages) {
            float yPos = graphBottom - (p / 100f * graphHeight);

            // Draw Dotted Line
            gridPath.reset();
            gridPath.moveTo(graphStartX, yPos);
            gridPath.lineTo(graphEndX, yPos);
            canvas.drawPath(gridPath, gridLinePaint);

            // Draw "100%", "75%", etc. labels dynamically aligned with the lines
            float textOffset = (gridTextPaint.descent() + gridTextPaint.ascent()) / 2f;
            canvas.drawText((int)p + "%", graphEndX + 16f, yPos - textOffset, gridTextPaint);
        }

        // Draw the Smooth Live Wave
        float stepX = (graphEndX - graphStartX) / (MAX_DATA_POINTS - 1);

        graphPath.reset();
        fillPath.reset();

        float prevX = graphStartX;
        float prevY = graphBottom - (history.get(0) / 100f * graphHeight);

        graphPath.moveTo(prevX, prevY);
        fillPath.moveTo(prevX, graphBottom);
        fillPath.lineTo(prevX, prevY);

        for (int i = 1; i < MAX_DATA_POINTS; i++) {
            float curX = graphStartX + (i * stepX);
            float curY = graphBottom - (history.get(i) / 100f * graphHeight);

            // Cubic Bezier calculation for a flowing wave instead of sharp jagged lines
            float cx1 = prevX + (curX - prevX) / 2f;
            float cy1 = prevY;
            float cx2 = prevX + (curX - prevX) / 2f;
            float cy2 = curY;

            graphPath.cubicTo(cx1, cy1, cx2, cy2, curX, curY);
            fillPath.cubicTo(cx1, cy1, cx2, cy2, curX, curY);

            prevX = curX;
            prevY = curY;
        }

        fillPath.lineTo(prevX, graphBottom);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(graphPath, linePaint);
    }
}