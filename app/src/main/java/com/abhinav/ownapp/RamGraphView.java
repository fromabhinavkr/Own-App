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
import androidx.annotation.NonNull;
import java.util.LinkedList;

public class RamGraphView extends View {

    private Paint linePaint, fillPaint, gridLinePaint, gridTextPaint, boxBgPaint, boxStrokePaint;
    private Path graphPath, fillPath, gridPath;
    private final LinkedList<Float> history = new LinkedList<>();
    private static final int MAX_DATA_POINTS = 40;
    private int themeState = 1; // 0 = Light, 1 = Dark, 2 = Star
    private boolean isFirstData = true;

    // CRASH FIX: All 3 mandatory Android constructors added to prevent XML Inflation crashes
    public RamGraphView(Context context) {
        super(context);
        init();
    }

    public RamGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RamGraphView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        initPaints();
        for (int i = 0; i < MAX_DATA_POINTS; i++) history.add(0f);
    }

    public void setTheme(boolean isDark) {
        this.themeState = isDark ? 1 : 0;
        initPaints();
        invalidate();
    }

    public void setThemeState(int themeState) {
        this.themeState = themeState;
        initPaints();
        invalidate();
    }

    private void initPaints() {
        int accentBlue = Color.parseColor("#4A90E2");
        int gridColor, gridTextColor, boxBgColor, boxStrokeColor;

        if (themeState == 0) { // Light Theme
            gridColor = Color.parseColor("#1A000000");
            gridTextColor = Color.parseColor("#80000000");
            boxBgColor = Color.parseColor("#FFFFFF");
            boxStrokeColor = Color.parseColor("#E5E5EA");
        } else if (themeState == 1) { // Dark Theme
            gridColor = Color.parseColor("#26FFFFFF");
            gridTextColor = Color.parseColor("#99FFFFFF");
            boxBgColor = Color.parseColor("#000000");
            boxStrokeColor = Color.parseColor("#333333");
        } else { // Star / Other
            gridColor = Color.parseColor("#15FFFFFF");
            gridTextColor = Color.parseColor("#80FFFFFF");
            boxBgColor = Color.parseColor("#000000");
            boxStrokeColor = Color.parseColor("#333333");
        }

        // Paints for the surrounding Box
        boxBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxBgPaint.setColor(boxBgColor);
        boxBgPaint.setStyle(Paint.Style.FILL);

        boxStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxStrokePaint.setColor(boxStrokeColor);
        boxStrokePaint.setStyle(Paint.Style.STROKE);
        boxStrokePaint.setStrokeWidth(2f); // Matched to DeviceStatsHelper stroke width

        // Existing Graph Paints
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(accentBlue);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(6f);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        gridLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridLinePaint.setColor(gridColor);
        gridLinePaint.setStyle(Paint.Style.STROKE);
        gridLinePaint.setStrokeWidth(2.5f);
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
        history.removeFirst();
        history.add(percentUsed);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return; // CRASH FIX: Prevents zero-height shader exception

        int gradientStart = Color.parseColor("#504A90E2");
        int gradientEnd = Color.TRANSPARENT;
        fillPaint.setShader(new LinearGradient(0, 0, 0, h, gradientStart, gradientEnd, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;

        // --- 1. DRAW THE BACKGROUND BOX ---
        float cornerRadius = 10f * density; // Exact match to RAM details box
        float strokeInset = 1f; // Inset slightly so the border lines don't get cropped
        canvas.drawRoundRect(strokeInset, strokeInset, w - strokeInset, h - strokeInset, cornerRadius, cornerRadius, boxBgPaint);
        canvas.drawRoundRect(strokeInset, strokeInset, w - strokeInset, h - strokeInset, cornerRadius, cornerRadius, boxStrokePaint);

        // --- 2. CALCULATE PADDING SO GRAPH FITS INSIDE THE BOX ---
        float paddingStart = 12f * density;
        float textSpacing = 16f;
        float maxTextWidth = gridTextPaint.measureText("100%");

        float graphStartX = paddingStart;
        float graphEndX = w - paddingStart - maxTextWidth - textSpacing; // Responsive right margin based on text length
        float graphTop = h * 0.15f;
        float graphBottom = h * 0.85f;
        float graphHeight = graphBottom - graphTop;

        // --- 3. DRAW GRID & TEXT ---
        float[] gridPercentages = {100f, 75f, 50f, 25f, 0f};
        for (float p : gridPercentages) {
            float yPos = graphBottom - (p / 100f * graphHeight);

            gridPath.reset();
            gridPath.moveTo(graphStartX, yPos);
            gridPath.lineTo(graphEndX, yPos);
            canvas.drawPath(gridPath, gridLinePaint);

            float textOffset = (gridTextPaint.descent() + gridTextPaint.ascent()) / 2f;
            canvas.drawText((int)p + "%", graphEndX + textSpacing, yPos - textOffset, gridTextPaint);
        }

        // --- 4. DRAW GRAPH LINE & GRADIENT ---
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

            float cx1 = prevX + (curX - prevX) / 2f;
            float cy1 = prevY;
            float cx2 = prevX + (curX - prevX) / 2f;

            graphPath.cubicTo(cx1, cy1, cx2, curY, curX, curY);
            fillPath.cubicTo(cx1, cy1, cx2, curY, curX, curY);

            prevX = curX;
            prevY = curY;
        }

        fillPath.lineTo(prevX, graphBottom);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(graphPath, linePaint);
    }
}