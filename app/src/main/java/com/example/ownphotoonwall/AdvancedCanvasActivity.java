package com.example.ownphotoonwall;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.DashPathEffect;
import android.graphics.DiscretePathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathDashPathEffect;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.slider.Slider;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class AdvancedCanvasActivity extends Activity {

    private AdvancedDrawingView drawingView;
    private boolean isDarkTheme;
    private int panelColor;
    private int textColor;

    private ColorWheelView colorWheel;
    private EditText etHexCode;
    private boolean isUpdatingColor = false;

    // Secondary state for Paint Brush Submenu
    public int paintBrushSubStyle = 0;

    public int getCanvasBgColor() {
        return getIntent().getIntExtra("bg_color", Color.WHITE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advanced_canvas);

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
        panelColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");

        findViewById(R.id.canvasRoot).setBackgroundColor(bgColor);

        View rightPanel = findViewById(R.id.rightBrushesPanel);
        View leftPanel = findViewById(R.id.leftSettingsPanel);
        View bottomMenu = findViewById(R.id.bottomMenuBar);

        setModernMenuBackground(leftPanel, panelColor, true);
        setModernMenuBackground(rightPanel, panelColor, false);
        setModernBottomMenuBackground(bottomMenu, panelColor);

        applyTextColor(findViewById(R.id.leftSettingsPanel), textColor);

        Button btnToggleSettings = findViewById(R.id.btnToggleSettings);
        Button btnToggleTools = findViewById(R.id.btnToggleTools);
        btnToggleSettings.setBackgroundTintList(ColorStateList.valueOf(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA")));
        btnToggleSettings.setTextColor(textColor);
        btnToggleTools.setBackgroundTintList(ColorStateList.valueOf(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA")));
        btnToggleTools.setTextColor(textColor);

        btnToggleSettings.setOnClickListener(v -> {
            leftPanel.setVisibility(leftPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            rightPanel.setVisibility(View.GONE);
        });

        btnToggleTools.setOnClickListener(v -> {
            rightPanel.setVisibility(rightPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            leftPanel.setVisibility(View.GONE);
        });

        String inputImagePath = getIntent().getStringExtra("image_path");
        Bitmap baseBitmap = null;

        if (inputImagePath != null && new File(inputImagePath).exists()) {
            baseBitmap = BitmapFactory.decodeFile(inputImagePath);
        }

        FrameLayout container = findViewById(R.id.drawingContainer);
        drawingView = new AdvancedDrawingView(this, baseBitmap);
        container.addView(drawingView);

        setupBrushButtons();
        setupSettingsPanel();

        findViewById(R.id.btnCanvasCancel).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        findViewById(R.id.btnCanvasUndo).setOnClickListener(v -> drawingView.undo());
        findViewById(R.id.btnCanvasRedo).setOnClickListener(v -> drawingView.redo());

        Button btnCanvasZoom = findViewById(R.id.btnCanvasZoom);
        btnCanvasZoom.setOnClickListener(v -> {
            drawingView.isZoomMode = !drawingView.isZoomMode;
            btnCanvasZoom.setText(drawingView.isZoomMode ? "Zoom/Pan: ON" : "Zoom/Pan: OFF");
            btnCanvasZoom.setBackgroundTintList(ColorStateList.valueOf(drawingView.isZoomMode ? Color.parseColor("#34C759") : Color.parseColor("#4A90E2")));
            if (drawingView.isZoomMode) Toast.makeText(this, "Zoom Mode Active! Pinch to zoom infinitely.", Toast.LENGTH_SHORT).show();
        });

        Button btnCanvasGrid = findViewById(R.id.btnCanvasGrid);
        btnCanvasGrid.setOnClickListener(v -> {
            drawingView.isGridMode = !drawingView.isGridMode;
            btnCanvasGrid.setText(drawingView.isGridMode ? "Grid: ON" : "Grid: OFF");
            btnCanvasGrid.setBackgroundTintList(ColorStateList.valueOf(drawingView.isGridMode ? Color.parseColor("#34C759") : Color.parseColor("#4A90E2")));
            drawingView.invalidate();
        });

        findViewById(R.id.btnCanvasSave).setOnClickListener(v -> showSaveDialog());
    }

    private void showSaveDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        b.setTitle("Save Canvas");
        b.setMessage("How would you like to save this artwork into the Studio?");
        b.setPositiveButton("As Base Image", (d, w) -> saveAndReturn(true));
        b.setNegativeButton("As New Layer", (d, w) -> saveAndReturn(false));
        b.setNeutralButton("Cancel", null);

        AlertDialog dialog = b.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        dialog.setOnShowListener(di -> {
            if (dialog.getWindow() != null) {
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(panelColor);
                gd.setCornerRadius(60f);
                dialog.getWindow().getDecorView().setBackground(gd);
                applyTextColor(dialog.getWindow().getDecorView(), textColor);
            }
        });
        dialog.show();
    }

    private void saveAndReturn(boolean saveAsBase) {
        Bitmap result = drawingView.getFinalBitmap();
        try {
            File outFile = new File(getCacheDir(), "canvas_out.png");
            FileOutputStream fos = new FileOutputStream(outFile);
            result.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            Intent data = new Intent();
            data.putExtra("out_path", outFile.getAbsolutePath());
            data.putExtra("save_as_base", saveAsBase);
            setResult(RESULT_OK, data);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save canvas", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupBrushButtons() {
        LinearLayout list = findViewById(R.id.brushListContainer);
        String[] brushes = {"Pencil", "Pen", "Paint Brush", "Marker", "Ink Brush", "Airbrush", "Watercolor", "Oil Brush", "Smudge", "Eraser", "Fill (Bucket)"};

        for (int i = 0; i < brushes.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(8, 16, 8, 16);

            BrushPreviewView preview = new BrushPreviewView(this, i);
            preview.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80));

            TextView tv = new TextView(this);
            tv.setText(brushes[i]);
            tv.setTextColor(textColor);
            tv.setTextSize(12f);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, 8, 0, 0);

            row.addView(preview);
            row.addView(tv);

            GradientDrawable normalBg = new GradientDrawable();
            normalBg.setColor(Color.TRANSPARENT);
            normalBg.setCornerRadius(20f);
            row.setBackground(normalBg);

            final int brushType = i;
            row.setOnClickListener(v -> {
                if (brushType == 2) {
                    showPaintBrushSubMenu(list, row, brushType);
                } else {
                    activateBrush(list, row, brushType, 0);
                }
            });

            list.addView(row);
        }
    }

    private void showPaintBrushSubMenu(LinearLayout list, LinearLayout row, int brushType) {
        String[] styles = {"Round Brush", "Flat Canvas Brush", "Wet Bristle"};
        AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        b.setTitle("Select Paint Style");
        b.setItems(styles, (dialog, which) -> activateBrush(list, row, brushType, which));

        AlertDialog dialog = b.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setOnShowListener(di -> {
            if (dialog.getWindow() != null) {
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(panelColor);
                gd.setCornerRadius(40f);
                dialog.getWindow().getDecorView().setBackground(gd);
                applyTextColor(dialog.getWindow().getDecorView(), textColor);
            }
        });
        dialog.show();
    }

    private void activateBrush(LinearLayout list, LinearLayout row, int brushType, int subStyle) {
        for (int j = 0; j < list.getChildCount(); j++) {
            list.getChildAt(j).setBackgroundColor(Color.TRANSPARENT);
        }
        GradientDrawable activeBg = new GradientDrawable();
        activeBg.setColor(isDarkTheme ? Color.parseColor("#4A4A4C") : Color.parseColor("#D1D1D6"));
        activeBg.setCornerRadius(20f);
        row.setBackground(activeBg);

        paintBrushSubStyle = subStyle;
        drawingView.setBrushType(brushType);

        findViewById(R.id.rightBrushesPanel).setVisibility(View.GONE);
        findViewById(R.id.leftSettingsPanel).setVisibility(View.VISIBLE);
    }

    private void setupSettingsPanel() {
        Slider slSize = findViewById(R.id.slSize);
        Slider slOpacity = findViewById(R.id.slOpacity);
        Slider slHardness = findViewById(R.id.slHardness);

        // Permanently hiding the Smoothing UI components completely!
        View slSmoothing = findViewById(R.id.slSmoothing);
        if (slSmoothing != null) slSmoothing.setVisibility(View.GONE);
        View tvSmoothingLabel = findViewById(R.id.tvSmoothingLabel);
        if (tvSmoothingLabel != null) tvSmoothingLabel.setVisibility(View.GONE);

        FrameLayout colorWheelContainer = findViewById(R.id.colorWheelContainer);
        etHexCode = findViewById(R.id.etCanvasHexCode);

        if (isDarkTheme) etHexCode.setTextColor(Color.WHITE); else etHexCode.setTextColor(Color.BLACK);

        colorWheel = new ColorWheelView(this);
        colorWheelContainer.addView(colorWheel);

        colorWheel.setOnColorChangeListener(color -> {
            drawingView.setBrushColor(color);
            if (!isUpdatingColor) {
                isUpdatingColor = true;
                etHexCode.setText(String.format("#%06X", (0xFFFFFF & color)));
                isUpdatingColor = false;
            }
        });

        etHexCode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUpdatingColor) return;
                if (s.length() == 7 && s.toString().startsWith("#")) {
                    try {
                        int newC = Color.parseColor(s.toString());
                        isUpdatingColor = true;
                        colorWheel.setColor(newC);
                        drawingView.setBrushColor(newC);
                        isUpdatingColor = false;
                    } catch (Exception ignored) {}
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        slSize.addOnChangeListener((slider, value, fromUser) -> drawingView.setBrushSize(value));
        slOpacity.addOnChangeListener((slider, value, fromUser) -> drawingView.setBrushOpacity((int) ((value/100f) * 255f)));
        slHardness.addOnChangeListener((slider, value, fromUser) -> drawingView.setBrushHardness(value));
    }

    public void updateColorUI(int color) {
        if (colorWheel != null) {
            isUpdatingColor = true;
            colorWheel.setColor(color);
            if (etHexCode != null) etHexCode.setText(String.format("#%06X", (0xFFFFFF & color)));
            isUpdatingColor = false;
        }
    }

    private void setModernMenuBackground(View view, int color, boolean isLeft) {
        if (view == null) return;
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadii(isLeft ? new float[]{0,0,60,60,60,60,0,0} : new float[]{60,60,0,0,0,0,60,60});
        view.setBackground(gd);
    }

    private void setModernBottomMenuBackground(View view, int color) {
        if (view == null) return;
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadii(new float[]{60,60,60,60,0,0,0,0});
        view.setBackground(gd);
    }

    private void applyTextColor(View view, int color) {
        if (view instanceof TextView && !(view instanceof Button) && !(view instanceof EditText)) {
            ((TextView) view).setTextColor(color);
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) applyTextColor(vg.getChildAt(i), color);
        }
    }

    private class BrushPreviewView extends View {
        private final Paint previewPaint;
        private final Path wavePath = new Path();
        private final Paint backgroundPaint = new Paint();
        private final RectF bgRect = new RectF();
        private final int bType;

        public BrushPreviewView(Context context, int brushType) {
            super(context);
            this.bType = brushType;
            setLayerType(LAYER_TYPE_SOFTWARE, null);

            backgroundPaint.setColor(Color.WHITE);
            backgroundPaint.setStyle(Paint.Style.FILL);

            previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

            if (brushType != 10) {
                AdvancedDrawingView.applyBrushProfileToPaint(previewPaint, brushType, paintBrushSubStyle, 16f, 255, Color.BLACK, 100f, true);
            } else {
                previewPaint.setColor(Color.parseColor("#4A90E2"));
                previewPaint.setStyle(Paint.Style.FILL);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();

            bgRect.set(10, 10, w-10, h-10);
            canvas.drawRoundRect(bgRect, 20f, 20f, backgroundPaint);

            if (bType == 10) {
                canvas.drawCircle(w/2f, h/2f, 15f, previewPaint);
            } else {
                wavePath.reset();
                wavePath.moveTo(30, h/2f);
                wavePath.cubicTo(w/3f, 0, w*2/3f, h, w-30, h/2f);
                canvas.drawPath(wavePath, previewPaint);
            }
        }
    }

    private static class ColorWheelView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private SweepGradient sweepGradient;
        private ImageEditorActivity.OnColorChangeListener listener;
        private final int[] colors = {Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED};
        private int currentColor = Color.RED;

        public ColorWheelView(Context context) { super(context); paint.setStyle(Paint.Style.FILL); }
        public void setOnColorChangeListener(ImageEditorActivity.OnColorChangeListener listener) { this.listener = listener; }

        @Override protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            sweepGradient = new SweepGradient(w / 2f, h / 2f, colors, null);
        }

        public void setColor(int color) {
            this.currentColor = color;
            invalidate();
            if (listener != null) listener.onColorChanged(color);
        }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f; float cy = getHeight() / 2f;
            float radius = Math.min(cx, cy);

            paint.setShader(sweepGradient); canvas.drawCircle(cx, cy, radius, paint);

            paint.setShader(null);
            paint.setColor(currentColor);
            canvas.drawCircle(cx, cy, radius * 0.4f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(6f);
            canvas.drawCircle(cx, cy, radius * 0.4f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - getWidth() / 2f; float dy = event.getY() - getHeight() / 2f;
                double angle = Math.atan2(dy, dx);
                if (angle < 0) angle += 2 * Math.PI;
                currentColor = interpolateColor(colors, (float) (angle / (2 * Math.PI)));
                invalidate();
                if (listener != null) listener.onColorChanged(currentColor);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                performClick();
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override public boolean performClick() { return super.performClick(); }

        private int interpolateColor(int[] arr, float unit) {
            if (unit <= 0) return arr[0]; if (unit >= 1) return arr[arr.length - 1];
            float p = unit * (arr.length - 1); int i = (int) p; p -= i;
            int c0 = arr[i]; int c1 = arr[i + 1];
            return Color.argb(ave(Color.alpha(c0), Color.alpha(c1), p), ave(Color.red(c0), Color.red(c1), p), ave(Color.green(c0), Color.green(c1), p), ave(Color.blue(c0), Color.blue(c1), p));
        }
        private int ave(int s, int d, float p) { return s + Math.round(p * (d - s)); }
    }

    private static class AdvancedDrawingView extends View {

        private final Bitmap baseBitmap;
        private Bitmap paintBitmap;
        private Canvas paintCanvas;

        // Smudge Optimization Buffers
        private Bitmap smudgeSampleBitmap;
        private Canvas smudgeSampleCanvas;
        private BitmapShader smudgeShader;
        private final Matrix smudgeMatrix = new Matrix();

        private final int canvasBgColor;

        private final ArrayList<Bitmap> rasterUndoStack = new ArrayList<>();
        private final ArrayList<Bitmap> rasterRedoStack = new ArrayList<>();

        private final Path currentPath;
        private final Paint currentPaint;
        private final Paint smudgeStampPaint;

        private final Paint renderPaint = new Paint();
        private final Path indicatorPath = new Path();
        private final Paint indShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint indStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint indFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint gridShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float brushSize = 10f;
        private int brushOpacity = 255;
        private int brushColor = Color.RED;
        private float brushHardness = 100f;
        private int brushType = 0;
        private int paintBrushSubStyle = 0;

        private float lastX, lastY;

        // Screen Touch Trackers for precise fill detection
        private float downScreenX, downScreenY;

        public boolean isZoomMode = false;
        public boolean isGridMode = false;
        private float viewZoom = 1f;
        private float viewPanX = 0f;
        private float viewPanY = 0f;
        private final ScaleGestureDetector scaleDetector;
        private final PointF lastTouch = new PointF();
        private int activePointerId = MotionEvent.INVALID_POINTER_ID;

        private boolean isLongPressFired = false;
        private boolean showPickerIndicator = false;
        private float screenTouchX, screenTouchY;
        private float downImgX, downImgY;

        private final Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isZoomMode && brushType != 10) {
                    isLongPressFired = true;
                    showPickerIndicator = true;
                    pickColorAtTouch(downImgX, downImgY);
                }
            }
        };

        public AdvancedDrawingView(Context context, Bitmap baseImage) {
            super(context);
            setLayerType(LAYER_TYPE_SOFTWARE, null);

            if (baseImage == null) {
                this.canvasBgColor = ((AdvancedCanvasActivity)context).getCanvasBgColor();
                DisplayMetrics m = context.getResources().getDisplayMetrics();
                int w = Math.min(3000, (m.widthPixels > 0 ? m.widthPixels : 1080) * 2);
                int h = Math.min(4000, (m.heightPixels > 0 ? m.heightPixels : 1920) * 2);

                this.baseBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                this.baseBitmap.eraseColor(canvasBgColor);
            } else {
                this.baseBitmap = baseImage;
                this.canvasBgColor = Color.WHITE;
            }

            currentPath = new Path();
            currentPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            smudgeStampPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

            indShadowPaint.setShadowLayer(8f, 0, 4f, Color.BLACK);
            indShadowPaint.setColor(Color.parseColor("#333333"));
            indStrokePaint.setStyle(Paint.Style.STROKE);
            indStrokePaint.setColor(Color.WHITE);
            indStrokePaint.setStrokeWidth(6f);
            indFillPaint.setStyle(Paint.Style.FILL);

            gridShadowPaint.setColor(Color.argb(120, 0, 0, 0));
            gridShadowPaint.setStyle(Paint.Style.STROKE);
            gridShadowPaint.setStrokeWidth(4f);
            gridPaint.setColor(Color.argb(200, 255, 255, 255));
            gridPaint.setStyle(Paint.Style.STROKE);
            gridPaint.setStrokeWidth(2f);

            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(@NonNull ScaleGestureDetector detector) {
                    viewZoom = Math.max(1f, Math.min(viewZoom * detector.getScaleFactor(), 50f));
                    invalidate();
                    return true;
                }
            });
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            if (paintBitmap == null && baseBitmap != null) {
                paintBitmap = Bitmap.createBitmap(baseBitmap.getWidth(), baseBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                paintCanvas = new Canvas(paintBitmap);

                smudgeSampleBitmap = Bitmap.createBitmap(baseBitmap.getWidth(), baseBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                smudgeSampleCanvas = new Canvas(smudgeSampleBitmap);

                saveUndoState();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();

            float wTarget = paintBitmap.getWidth();
            float hTarget = paintBitmap.getHeight();

            float scale = Math.min((float) getWidth() / wTarget, (float) getHeight() / hTarget) * viewZoom;
            float dx = (getWidth() - wTarget * scale) / 2f + viewPanX;
            float dy = (getHeight() - hTarget * scale) / 2f + viewPanY;

            canvas.translate(dx, dy);
            canvas.scale(scale, scale);

            renderPaint.setFilterBitmap(viewZoom <= 5f);
            renderPaint.setAntiAlias(viewZoom <= 5f);

            if (baseBitmap != null) canvas.drawBitmap(baseBitmap, 0, 0, renderPaint);
            if (paintBitmap != null) canvas.drawBitmap(paintBitmap, 0, 0, renderPaint);

            // LIVE DRAWING PATH - This makes the line appear instantly as you draw!
            if (!currentPath.isEmpty() && !isLongPressFired && brushType != 8 && brushType != 9 && brushType != 10) {
                canvas.drawPath(currentPath, currentPaint);
            }

            if (isGridMode) {
                float cellW = wTarget / 9f;
                float cellH = hTarget / 9f;
                for (int i = 1; i < 9; i++) {
                    canvas.drawLine((cellW * i), 0, (cellW * i), hTarget, gridShadowPaint);
                    canvas.drawLine(0, (cellH * i), wTarget, (cellH * i), gridShadowPaint);
                    canvas.drawLine((cellW * i), 0, (cellW * i), hTarget, gridPaint);
                    canvas.drawLine(0, (cellH * i), wTarget, (cellH * i), gridPaint);
                }
                canvas.drawRect(0, 0, wTarget, hTarget, gridShadowPaint);
                canvas.drawRect(0, 0, wTarget, hTarget, gridPaint);
            }

            canvas.restore();

            if (showPickerIndicator) {
                float cx = screenTouchX;
                float cy = screenTouchY;
                indicatorPath.reset();
                indicatorPath.moveTo(cx, cy);
                indicatorPath.cubicTo(cx - 70f, cy - 70f, cx - 70f, cy - 180f, cx, cy - 180f);
                indicatorPath.cubicTo(cx + 70f, cy - 180f, cx + 70f, cy - 70f, cx, cy);
                indicatorPath.close();

                canvas.drawPath(indicatorPath, indShadowPaint);
                canvas.drawPath(indicatorPath, indStrokePaint);

                indFillPaint.setColor(brushColor);
                canvas.drawCircle(cx, cy - 120f, 45f, indFillPaint);
            }
        }

        public static void applyBrushProfileToPaint(Paint p, int bType, int subStyle, float size, int alpha, int color, float hardness, boolean isPreview) {
            p.reset();
            p.setAntiAlias(true);
            p.setDither(true);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(color);

            float finalBlur = 0f;

            switch(bType) {
                case 0: // Pencil
                    p.setStrokeWidth(size * 0.4f);
                    p.setAlpha((int)(alpha * 0.8f));
                    p.setPathEffect(new DiscretePathEffect(3f, 2f));
                    break;
                case 1: // Pen
                    p.setStrokeWidth(size);
                    p.setAlpha(alpha);
                    break;
                case 2: // Paint Brush
                    p.setStrokeWidth(size);
                    p.setAlpha((int)(alpha * 0.9f));
                    if (subStyle == 1) {
                        p.setStrokeCap(Paint.Cap.SQUARE);
                    } else if (subStyle == 2) {
                        p.setAlpha((int)(alpha * 0.6f));
                        p.setPathEffect(new DiscretePathEffect(5f, 4f));
                        finalBlur = size * 0.1f;
                    } else {
                        finalBlur = size * 0.15f;
                    }
                    break;
                case 3: // Marker
                    p.setStrokeWidth(size * 1.5f);
                    p.setStrokeCap(Paint.Cap.SQUARE);
                    p.setAlpha((int)(alpha * 0.6f));
                    if (!isPreview) p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN));
                    break;
                case 4: // Ink Brush
                    p.setStrokeWidth(size * 1.2f);
                    p.setAlpha(alpha);
                    p.setPathEffect(new CornerPathEffect(size));
                    break;
                case 5: // Airbrush
                    p.setStrokeWidth(size * 2f);
                    p.setAlpha((int)(alpha * 0.25f));
                    finalBlur = size * 0.8f;
                    break;
                case 6: // Watercolor
                    p.setStrokeWidth(size * 1.5f);
                    p.setAlpha((int)(alpha * 0.4f));
                    if (!isPreview) p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN));
                    finalBlur = size * 0.5f;
                    break;
                case 7: // Oil Brush
                    p.setStrokeWidth(size);
                    p.setAlpha(alpha);
                    p.setPathEffect(new DiscretePathEffect(10f, 5f));
                    break;
                case 8: // Smudge Tool
                    p.setStrokeWidth(size);
                    p.setAlpha((int)(alpha * 0.8f));
                    finalBlur = size * 0.4f;
                    break;
                case 9: // Eraser
                    p.setStrokeWidth(size);
                    if (!isPreview) {
                        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                        p.setColor(Color.TRANSPARENT);
                    } else {
                        p.setColor(Color.GRAY);
                        p.setPathEffect(new DashPathEffect(new float[]{20f, 20f}, 0f));
                    }
                    p.setAlpha(255);
                    break;
            }

            if (hardness < 95f && bType != 9) {
                float hardnessBlur = size * ((100f - hardness) / 100f);
                finalBlur = Math.max(finalBlur, hardnessBlur);
            }
            if (finalBlur > 0) p.setMaskFilter(new BlurMaskFilter(finalBlur, BlurMaskFilter.Blur.NORMAL));
        }

        private int sampleColorFast(float x, float y) {
            int px = Math.max(0, Math.min((int)x, paintBitmap.getWidth() - 1));
            int py = Math.max(0, Math.min((int)y, paintBitmap.getHeight() - 1));

            int c = paintBitmap.getPixel(px, py);
            if (Color.alpha(c) == 0 && baseBitmap != null) {
                int bx = Math.max(0, Math.min((int)x, baseBitmap.getWidth() - 1));
                int by = Math.max(0, Math.min((int)y, baseBitmap.getHeight() - 1));
                c = baseBitmap.getPixel(bx, by);
            }
            if (Color.alpha(c) == 0) return canvasBgColor;
            return c;
        }

        private void pickColorAtTouch(float imgX, float imgY) {
            int picked = sampleColorFast(imgX, imgY);
            if (Color.alpha(picked) > 0) {
                brushColor = picked;
                ((AdvancedCanvasActivity)getContext()).updateColorUI(picked);
            }
            invalidate();
        }

        // --- HIGH-PERFORMANCE PRIMITIVE SCANLINE FLOOD FILL ---
        private void executeFloodFill(int imgX, int imgY) {
            Toast.makeText(getContext(), "Filling Area...", Toast.LENGTH_SHORT).show();

            new Thread(() -> {
                Bitmap merged = getFinalBitmap();
                int width = merged.getWidth();
                int height = merged.getHeight();

                int px = Math.max(0, Math.min(imgX, width - 1));
                int py = Math.max(0, Math.min(imgY, height - 1));

                int[] mergedPixels = new int[width * height];
                merged.getPixels(mergedPixels, 0, width, 0, 0, width, height);
                int targetColor = mergedPixels[py * width + px];

                // Stop if color is already the matching target color to prevent infinite loops
                if (colorMatch(targetColor, brushColor, 25)) return;

                int[] paintPixels = new int[width * height];
                paintBitmap.getPixels(paintPixels, 0, width, 0, 0, width, height);

                // Primitive Flat Coordinate Stack allocation to bypass LinkedList memory garbage allocations
                int[] stack = new int[width * height];
                int stackPointer = 0;

                stack[stackPointer++] = py * width + px;

                while (stackPointer > 0) {
                    int pos = stack[--stackPointer];
                    int cx = pos % width;
                    int cy = pos / width;

                    if (!colorMatch(mergedPixels[cy * width + cx], targetColor, 25)) continue;

                    int w = cx;
                    while (w > 0 && colorMatch(mergedPixels[cy * width + (w - 1)], targetColor, 25)) {
                        w--;
                    }

                    int e = cx;
                    while (e < width - 1 && colorMatch(mergedPixels[cy * width + (e + 1)], targetColor, 25)) {
                        e++;
                    }

                    boolean scanAbove = false;
                    boolean scanBelow = false;

                    for (int xIdx = w; xIdx <= e; xIdx++) {
                        int currentIdx = cy * width + xIdx;
                        paintPixels[currentIdx] = brushColor;
                        mergedPixels[currentIdx] = brushColor;

                        if (cy > 0) {
                            int upIdx = (cy - 1) * width + xIdx;
                            boolean matchAbove = colorMatch(mergedPixels[upIdx], targetColor, 25);
                            if (matchAbove && !scanAbove) {
                                stack[stackPointer++] = (cy - 1) * width + xIdx;
                                scanAbove = true;
                            } else if (!matchAbove) {
                                scanAbove = false;
                            }
                        }

                        if (cy < height - 1) {
                            int downIdx = (cy + 1) * width + xIdx;
                            boolean matchBelow = colorMatch(mergedPixels[downIdx], targetColor, 25);
                            if (matchBelow && !scanBelow) {
                                stack[stackPointer++] = (cy + 1) * width + xIdx;
                                scanBelow = true;
                            } else if (!matchBelow) {
                                scanBelow = false;
                            }
                        }
                    }
                }

                ((Activity)getContext()).runOnUiThread(() -> {
                    paintBitmap.setPixels(paintPixels, 0, width, 0, 0, width, height);
                    saveUndoState();
                    invalidate();
                    Toast.makeText(getContext(), "Fill Complete!", Toast.LENGTH_SHORT).show();
                });
            }).start();
        }

        private boolean colorMatch(int c1, int c2, int tol) {
            if (c1 == c2) return true;
            return Math.abs(Color.red(c1) - Color.red(c2)) <= tol &&
                    Math.abs(Color.green(c1) - Color.green(c2)) <= tol &&
                    Math.abs(Color.blue(c1) - Color.blue(c2)) <= tol;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            if (isZoomMode) {
                scaleDetector.onTouchEvent(event);
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: lastTouch.set(x, y); activePointerId = event.getPointerId(0); break;
                    case MotionEvent.ACTION_MOVE:
                        int pi = event.findPointerIndex(activePointerId);
                        if (pi != -1) {
                            float currX = event.getX(pi), currY = event.getY(pi);
                            if (!scaleDetector.isInProgress()) { viewPanX += currX - lastTouch.x; viewPanY += currY - lastTouch.y; }
                            lastTouch.set(currX, currY); invalidate();
                        } break;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                        activePointerId = MotionEvent.INVALID_POINTER_ID;
                        performClick();
                        break;
                } return true;
            }

            float scale = Math.min((float) getWidth() / paintBitmap.getWidth(), (float) getHeight() / paintBitmap.getHeight()) * viewZoom;
            float dx = (getWidth() - paintBitmap.getWidth() * scale) / 2f + viewPanX;
            float dy = (getHeight() - paintBitmap.getHeight() * scale) / 2f + viewPanY;

            float imgX = (x - dx) / scale;
            float imgY = (y - dy) / scale;

            screenTouchX = x;
            screenTouchY = y;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downScreenX = x;
                    downScreenY = y;

                    if (brushType == 10) return true; // Bucket Mode

                    isLongPressFired = false;
                    showPickerIndicator = false;
                    downImgX = imgX; downImgY = imgY;
                    postDelayed(longPressRunnable, 500);

                    // SMUDGE ENGINE - COMPLETELY UNTOUCHED AS REQUESTED
                    if (brushType == 8) {
                        smudgeSampleBitmap.eraseColor(Color.TRANSPARENT);
                        if (baseBitmap != null) smudgeSampleCanvas.drawBitmap(baseBitmap, 0, 0, null);
                        smudgeSampleCanvas.drawBitmap(paintBitmap, 0, 0, null);

                        smudgeShader = new BitmapShader(smudgeSampleBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

                        smudgeStampPaint.reset();
                        smudgeStampPaint.setAntiAlias(true);
                        smudgeStampPaint.setShader(smudgeShader);
                        smudgeStampPaint.setAlpha((int)(brushOpacity * 0.9f));
                        smudgeStampPaint.setMaskFilter(new BlurMaskFilter(brushSize * 0.3f, BlurMaskFilter.Blur.NORMAL));
                    }

                    applyBrushProfileToPaint(currentPaint, brushType, paintBrushSubStyle, brushSize, brushOpacity, brushColor, brushHardness, false);
                    currentPath.reset();

                    lastX = imgX; lastY = imgY;
                    currentPath.moveTo(imgX, imgY);
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (brushType == 10) return true; // Bucket Mode

                    if (!isLongPressFired && Math.hypot(imgX - downImgX, imgY - downImgY) > (15f / scale)) {
                        removeCallbacks(longPressRunnable);
                    }

                    if (isLongPressFired) {
                        pickColorAtTouch(imgX, imgY);
                        return true;
                    }

                    // ZERO-LAG INSTANT DRAWING LOGIC
                    float tolerance = 2f / scale;
                    float dX = Math.abs(imgX - lastX);
                    float dY = Math.abs(imgY - lastY);

                    if (dX >= tolerance || dY >= tolerance) {
                        float midX = (lastX + imgX) / 2f;
                        float midY = (lastY + imgY) / 2f;

                        if (brushType == 8) {
                            // SMUDGE ENGINE UNTOUCHED AS REQUESTED
                            float dist = (float) Math.hypot(imgX - lastX, imgY - lastY);
                            float spacing = Math.max(1f, brushSize * 0.05f);

                            if (dist >= spacing) {
                                int steps = (int)(dist / spacing);
                                float stepX = (imgX - lastX) / steps;
                                float stepY = (imgY - lastY) / steps;

                                for(int i=0; i<steps; i++) {
                                    float nextX = lastX + stepX;
                                    float nextY = lastY + stepY;

                                    smudgeMatrix.setTranslate(lastX - nextX, lastY - nextY);
                                    smudgeShader.setLocalMatrix(smudgeMatrix);

                                    paintCanvas.drawCircle(nextX, nextY, brushSize/2f, smudgeStampPaint);
                                    smudgeSampleCanvas.drawCircle(nextX, nextY, brushSize/2f, smudgeStampPaint);

                                    lastX = nextX;
                                    lastY = nextY;
                                }
                            }
                        } else if (brushType == 9) { // PERFECTLY SMOOTH CONTINUOUS ERASER
                            currentPath.quadTo(lastX, lastY, midX, midY);
                            paintCanvas.drawPath(currentPath, currentPaint); // Live clear execution
                            lastX = imgX;
                            lastY = imgY;
                        } else {
                            // Lag-free Path Append
                            currentPath.quadTo(lastX, lastY, midX, midY);
                            lastX = imgX;
                            lastY = imgY;
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    performClick();

                    if (brushType == 10) { // Bucket Mode Triggered
                        float moved = (float) Math.hypot(x - downScreenX, event.getY() - downScreenY);
                        if (moved < 25f) { // Precise Screen-Pixel Tap Detection!
                            executeFloodFill((int)imgX, (int)imgY);
                        }
                        return true;
                    }

                    removeCallbacks(longPressRunnable);
                    showPickerIndicator = false;
                    if (isLongPressFired) {
                        isLongPressFired = false;
                        invalidate();
                        return true;
                    }

                    // Stroke permanently baked instantly to canvas!
                    if (brushType != 8 && brushType != 10) {
                        currentPath.lineTo(imgX, imgY);
                        paintCanvas.drawPath(currentPath, currentPaint);
                    }
                    currentPath.reset();
                    saveUndoState();
                    break;
            }
            invalidate();
            return true;
        }

        @Override public boolean performClick() { return super.performClick(); }

        public void setBrushSize(float size) {
            this.brushSize = size;
        }

        public void setBrushOpacity(int alpha) {
            this.brushOpacity = alpha;
        }

        public void setBrushColor(int color) {
            this.brushColor = color;
        }

        public void setBrushHardness(float hardness) {
            this.brushHardness = hardness;
        }

        public void setBrushType(int type) {
            this.brushType = type;
            if (getContext() instanceof AdvancedCanvasActivity) {
                this.paintBrushSubStyle = ((AdvancedCanvasActivity)getContext()).paintBrushSubStyle;
            }
        }

        private void saveUndoState() {
            if (rasterUndoStack.size() >= 10) rasterUndoStack.remove(0);
            rasterUndoStack.add(Bitmap.createBitmap(paintBitmap));
            rasterRedoStack.clear();
        }

        public void undo() {
            if (rasterUndoStack.size() > 1) {
                rasterRedoStack.add(rasterUndoStack.remove(rasterUndoStack.size() - 1));
                Bitmap prev = rasterUndoStack.get(rasterUndoStack.size() - 1);
                paintBitmap = Bitmap.createBitmap(prev);
                paintCanvas = new Canvas(paintBitmap);
                invalidate();
            }
        }

        public void redo() {
            if (!rasterRedoStack.isEmpty()) {
                Bitmap next = rasterRedoStack.remove(rasterRedoStack.size() - 1);
                rasterUndoStack.add(next);
                paintBitmap = Bitmap.createBitmap(next);
                paintCanvas = new Canvas(paintBitmap);
                invalidate();
            }
        }

        public Bitmap getFinalBitmap() {
            Bitmap result = Bitmap.createBitmap(paintBitmap.getWidth(), paintBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(result);
            if (baseBitmap != null) c.drawBitmap(baseBitmap, 0, 0, null);
            c.drawBitmap(paintBitmap, 0, 0, null);
            return result;
        }
    }
}