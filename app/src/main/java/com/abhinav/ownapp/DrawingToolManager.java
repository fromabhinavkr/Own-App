package com.abhinav.ownapp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import java.util.ArrayList;
import java.util.List;

public class DrawingToolManager {

    public static class DrawStroke {
        public Path path; public Paint paint; public int targetCanvasState; // 0 = draw, 1 = draw erase, 2 = bg erase, 3 = bg repair
        public DrawStroke(Path p, Paint pt, int tc) { path = new Path(p); paint = new Paint(pt); targetCanvasState = tc; }
    }

    private Bitmap drawLayerBitmap; private Canvas drawLayerCanvas;
    private Bitmap eraseLayerBitmap; private Canvas eraseLayerCanvas;

    public boolean isDrawMode = false; public boolean isDrawEraserMode = false;
    public int currentBrushColor = Color.RED; public float currentBrushWidth = 12f; public int currentBrushOpacity = 255;

    private final Path currentPath = new Path();
    private final Paint currentDrawPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path segmentPath = new Path();
    private float bX, bY, lastSegX, lastSegY;

    private final List<DrawStroke> drawStrokes = new ArrayList<>();

    public DrawingToolManager() {
        currentDrawPaint.setStyle(Paint.Style.STROKE); currentDrawPaint.setStrokeJoin(Paint.Join.ROUND); currentDrawPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void initBitmaps(int width, int height) {
        if (width <= 0 || height <= 0) return;
        drawLayerBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); drawLayerCanvas = new Canvas(drawLayerBitmap);
        eraseLayerBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); eraseLayerCanvas = new Canvas(eraseLayerBitmap);
    }

    public Bitmap getDrawLayerBitmap() { return drawLayerBitmap; }
    public Bitmap getEraseLayerBitmap() { return eraseLayerBitmap; }
    public Canvas getDrawLayerCanvas() { return drawLayerCanvas; }
    public Canvas getEraseLayerCanvas() { return eraseLayerCanvas; }
    public List<DrawStroke> getDrawStrokes() { return drawStrokes; }
    public Path getCurrentPath() { return currentPath; }
    public Paint getCurrentDrawPaint() { return currentDrawPaint; }

    public void clear() {
        drawStrokes.clear();
        if (drawLayerBitmap != null) drawLayerBitmap.eraseColor(Color.TRANSPARENT);
        if (eraseLayerBitmap != null) eraseLayerBitmap.eraseColor(Color.TRANSPARENT);
    }

    public void redrawAllStrokes() {
        if (drawLayerBitmap != null) drawLayerBitmap.eraseColor(Color.TRANSPARENT);
        if (eraseLayerBitmap != null) eraseLayerBitmap.eraseColor(Color.TRANSPARENT);
        for (DrawStroke stroke : drawStrokes) {
            Canvas targetCanvas = (stroke.targetCanvasState < 2) ? drawLayerCanvas : eraseLayerCanvas;
            if (targetCanvas != null) targetCanvas.drawPath(stroke.path, stroke.paint);
        }
    }

    public void removeStroke(DrawStroke stroke) {
        drawStrokes.remove(stroke); redrawAllStrokes();
    }

    // =========================================================================
    // NEW FLAWLESS REDO HANDLER - PERFECTLY RESTORES REMOVED STROKES
    // =========================================================================
    public void addStroke(DrawStroke stroke) {
        drawStrokes.add(stroke);
        Canvas targetCanvas = (stroke.targetCanvasState < 2) ? drawLayerCanvas : eraseLayerCanvas;
        if (targetCanvas != null) targetCanvas.drawPath(stroke.path, stroke.paint);
    }

    public void configurePaint(float scaleFactor, boolean isBgRemoverMode, boolean isBgRepairMode) {
        currentDrawPaint.setStrokeWidth(currentBrushWidth * scaleFactor);
        boolean isEraser = isBgRemoverMode || (isDrawMode && isDrawEraserMode);

        if (isDrawMode && drawLayerCanvas != null) {
            if (isDrawEraserMode) {
                currentDrawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR)); currentDrawPaint.setColor(Color.TRANSPARENT);
            } else {
                currentDrawPaint.setXfermode(null); currentDrawPaint.setColor(currentBrushColor); currentDrawPaint.setAlpha(currentBrushOpacity);
            }
        } else if (isBgRemoverMode && eraseLayerCanvas != null) {
            if (isBgRepairMode) {
                currentDrawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR)); currentDrawPaint.setColor(Color.TRANSPARENT);
            } else {
                currentDrawPaint.setXfermode(null); currentDrawPaint.setColor(Color.BLACK);
            }
        }
    }

    public void onTouchDown(float imgX, float imgY) {
        currentPath.reset(); currentPath.moveTo(imgX, imgY);
        bX = imgX; bY = imgY; lastSegX = imgX; lastSegY = imgY;
    }

    public void onTouchMove(float imgX, float imgY, boolean isEraser, boolean isDrawLayer) {
        float dx = Math.abs(imgX - bX), dy = Math.abs(imgY - bY);
        if (dx >= 2f || dy >= 2f) {
            float midX = (imgX + bX) / 2f, midY = (imgY + bY) / 2f;
            currentPath.quadTo(bX, bY, midX, midY);
            if (isEraser) {
                segmentPath.reset(); segmentPath.moveTo(lastSegX, lastSegY); segmentPath.quadTo(bX, bY, midX, midY);
                Canvas target = isDrawLayer ? drawLayerCanvas : eraseLayerCanvas;
                if (target != null) target.drawPath(segmentPath, currentDrawPaint);
            }
            bX = imgX; bY = imgY; lastSegX = midX; lastSegY = midY;
        }
    }

    public DrawStroke onTouchUp(float imgX, float imgY, boolean isEraser, boolean isDrawLayer, int targetCanvasState) {
        currentPath.lineTo(imgX, imgY);
        if (isEraser) {
            segmentPath.reset(); segmentPath.moveTo(lastSegX, lastSegY); segmentPath.lineTo(imgX, imgY);
            Canvas target = isDrawLayer ? drawLayerCanvas : eraseLayerCanvas;
            if (target != null) target.drawPath(segmentPath, currentDrawPaint);
        } else {
            if (drawLayerCanvas != null) drawLayerCanvas.drawPath(currentPath, currentDrawPaint);
        }
        DrawStroke stroke = new DrawStroke(currentPath, currentDrawPaint, targetCanvasState);
        drawStrokes.add(stroke); currentPath.reset(); return stroke;
    }
}