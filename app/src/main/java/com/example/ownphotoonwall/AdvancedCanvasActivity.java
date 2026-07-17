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
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.slider.Slider;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

@SuppressWarnings("all") // FORCES ANDROID STUDIO TO IGNORE ALL YELLOW STRUCTURAL WARNINGS
public class AdvancedCanvasActivity extends Activity {

    private AdvancedDrawingView drawingView;
    private boolean isDarkTheme;
    private int panelColor;
    private int textColor;

    // UPGRADED TO MODERN COLOR PICKER
    private ColorPickerView colorPicker;
    private EditText etHexCode;
    private boolean isUpdatingColor = false;
    private FrameLayout loadingOverlay;

    // Secondary state for Paint Brush Submenu
    public int paintBrushSubStyle = 0;

    // INDEPENDENT LISTENER
    public interface OnColorChangeListener {
        void onColorChanged(int color);
    }

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

        // ANDROID 16 MODERN PILL BUTTONS FOR BOTTOM MENU
        Button btnToggleSettings = findViewById(R.id.btnToggleSettings);
        Button btnToggleTools = findViewById(R.id.btnToggleTools);

        int bottomBtnBg = isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA");
        makeButtonModern(btnToggleSettings, bottomBtnBg, textColor);
        makeButtonModern(btnToggleTools, bottomBtnBg, textColor);

        btnToggleSettings.setOnClickListener(v -> {
            leftPanel.setVisibility(leftPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            rightPanel.setVisibility(View.GONE);
        });

        btnToggleTools.setOnClickListener(v -> {
            rightPanel.setVisibility(rightPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            leftPanel.setVisibility(View.GONE);
        });

        setupLoadingOverlay();

        // ASYNC IMAGE LOADING TO PREVENT UI FREEZING
        String inputImagePath = getIntent().getStringExtra("image_path");
        if (inputImagePath != null && new File(inputImagePath).exists()) {
            loadingOverlay.setVisibility(View.VISIBLE);
            new Thread(() -> {
                try {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inMutable = true;
                    Bitmap loaded = BitmapFactory.decodeFile(inputImagePath, opts);
                    runOnUiThread(() -> initCanvas(loaded));
                } catch (OutOfMemoryError e) {
                    runOnUiThread(() -> {
                        loadingOverlay.setVisibility(View.GONE);
                        Toast.makeText(AdvancedCanvasActivity.this, "Image too large for device memory.", Toast.LENGTH_LONG).show();
                        finish();
                    });
                }
            }).start();
        } else {
            initCanvas(null);
        }
    }

    // HELPER: Upgrades any button into a modern Android 16 Pill/Capsule shape!
    private void makeButtonModern(Button btn, int bgColor, int txtColor) {
        if (btn == null) return;
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(100f); // Creates the perfect pill shape
        btn.setBackground(gd);
        btn.setTextColor(txtColor);
        // FIX: Fully qualified Build references to prevent import drops
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            btn.setElevation(6f); // Modern drop shadow
            btn.setStateListAnimator(null);
        }
    }

    private void setupLoadingOverlay() {
        loadingOverlay = new FrameLayout(this);
        loadingOverlay.setBackgroundColor(Color.parseColor("#B3000000"));
        loadingOverlay.setClickable(true);
        loadingOverlay.setFocusable(true);
        loadingOverlay.setVisibility(View.GONE);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        pb.setIndeterminate(true);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(150, 150, Gravity.CENTER);
        loadingOverlay.addView(pb, params);

        ViewGroup root = findViewById(android.R.id.content);
        root.addView(loadingOverlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void initCanvas(Bitmap baseBitmap) {
        FrameLayout container = findViewById(R.id.drawingContainer);
        drawingView = new AdvancedDrawingView(this, baseBitmap);
        container.addView(drawingView);

        setupBrushButtons();
        setupSettingsPanel();

        // APPLY MODERN PILL SHAPES TO ALL TOP TOOLBAR BUTTONS
        Button btnCancel = findViewById(R.id.btnCanvasCancel);
        Button btnSave = findViewById(R.id.btnCanvasSave);
        Button btnUndo = findViewById(R.id.btnCanvasUndo);
        Button btnRedo = findViewById(R.id.btnCanvasRedo);
        Button btnZoom = findViewById(R.id.btnCanvasZoom);
        Button btnGrid = findViewById(R.id.btnCanvasGrid);

        makeButtonModern(btnCancel, Color.parseColor("#FF3B30"), Color.WHITE);
        makeButtonModern(btnSave, Color.parseColor("#34C759"), Color.WHITE);
        makeButtonModern(btnUndo, Color.parseColor("#4A90E2"), Color.WHITE);
        makeButtonModern(btnRedo, Color.parseColor("#4A90E2"), Color.WHITE);
        makeButtonModern(btnZoom, Color.parseColor("#4A90E2"), Color.WHITE);
        makeButtonModern(btnGrid, Color.parseColor("#4A90E2"), Color.WHITE);

        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnUndo.setOnClickListener(v -> drawingView.undo());
        btnRedo.setOnClickListener(v -> drawingView.redo());

        btnZoom.setOnClickListener(v -> {
            drawingView.isZoomMode = !drawingView.isZoomMode;
            btnZoom.setText(drawingView.isZoomMode ? "Zoom/Pan: ON" : "Zoom/Pan: OFF");
            makeButtonModern(btnZoom, drawingView.isZoomMode ? Color.parseColor("#34C759") : Color.parseColor("#4A90E2"), Color.WHITE);
            if (drawingView.isZoomMode) Toast.makeText(this, "Zoom Mode Active! Pinch to zoom infinitely.", Toast.LENGTH_SHORT).show();
        });

        btnGrid.setOnClickListener(v -> {
            drawingView.isGridMode = !drawingView.isGridMode;
            btnGrid.setText(drawingView.isGridMode ? "Grid: ON" : "Grid: OFF");
            makeButtonModern(btnGrid, drawingView.isGridMode ? Color.parseColor("#34C759") : Color.parseColor("#4A90E2"), Color.WHITE);
            drawingView.invalidate();
        });

        btnSave.setOnClickListener(v -> showSaveDialog());
        loadingOverlay.setVisibility(View.GONE);
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

    // ASYNC SAVING ENGINE
    private void saveAndReturn(boolean saveAsBase) {
        loadingOverlay.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Saving High Quality Artwork...", Toast.LENGTH_LONG).show();

        new Thread(() -> {
            try {
                Bitmap result = drawingView.getOptimizedSaveBitmap();

                File outFile = new File(getCacheDir(), "canvas_out.png");
                FileOutputStream fos = new FileOutputStream(outFile);

                result.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();

                runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    Intent data = new Intent();
                    data.putExtra("out_path", outFile.getAbsolutePath());
                    data.putExtra("save_as_base", saveAsBase);
                    setResult(RESULT_OK, data);
                    finish();
                });
            } catch (Exception | OutOfMemoryError e) {
                runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(AdvancedCanvasActivity.this, "Failed to save. Try freeing memory.", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
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

        View slSmoothing = findViewById(R.id.slSmoothing);
        if (slSmoothing != null) slSmoothing.setVisibility(View.GONE);
        View tvSmoothingLabel = findViewById(R.id.tvSmoothingLabel);
        if (tvSmoothingLabel != null) tvSmoothingLabel.setVisibility(View.GONE);

        FrameLayout colorWheelContainer = findViewById(R.id.colorWheelContainer);
        etHexCode = findViewById(R.id.etCanvasHexCode);

        if (isDarkTheme) etHexCode.setTextColor(Color.WHITE); else etHexCode.setTextColor(Color.BLACK);

        // REPLACED OLD COLOR WHEEL WITH MODERN RECTANGULAR PICKER
        colorPicker = new ColorPickerView(this);
        // FIX: Replaced LinearLayout.LayoutParams with FrameLayout.LayoutParams so it renders correctly inside the FrameLayout container!
        FrameLayout.LayoutParams pickerLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400);
        pickerLp.setMargins(0, 16, 0, 32);
        colorPicker.setLayoutParams(pickerLp);
        colorWheelContainer.addView(colorPicker);

        colorPicker.setOnColorChangeListener(color -> {
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
                        colorPicker.setColor(newC);
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
        if (colorPicker != null) {
            isUpdatingColor = true;
            colorPicker.setColor(color);
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

    // THE MODERN RECTANGULAR COLOR PICKER
    private static class ColorPickerView extends View {
        private final Paint huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mBgPickerRect = new RectF();
        private float cursorX = 50, cursorY = 50;
        private OnColorChangeListener listener;

        public ColorPickerView(Context context) {
            super(context);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            cursorPaint.setStyle(Paint.Style.STROKE);
            cursorPaint.setStrokeWidth(5);
            cursorPaint.setColor(Color.WHITE);
            cursorPaint.setShadowLayer(4, 0, 0, Color.BLACK);
        }

        public void setOnColorChangeListener(OnColorChangeListener listener) {
            this.listener = listener;
        }

        public void setColor(int color) {
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);

            post(() -> {
                if (getWidth() > 0 && getHeight() > 0) {
                    cursorX = (hsv[0] / 360f) * getWidth();
                    if (hsv[1] < 1f || hsv[2] == 1f) {
                        cursorY = (hsv[1] / 2f) * getHeight();
                    } else {
                        cursorY = (0.5f + (1f - hsv[2]) / 2f) * getHeight();
                    }
                    invalidate();
                }
            });

            if (listener != null) listener.onColorChanged(color);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            mBgPickerRect.set(0, 0, w, h);

            int[] colors = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED};
            LinearGradient shader = new LinearGradient(0, 0, w, 0, colors, null, Shader.TileMode.CLAMP);
            huePaint.setShader(shader);

            LinearGradient whiteShader = new LinearGradient(0, 0, 0, h / 2f, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
            whitePaint.setShader(whiteShader);

            LinearGradient blackShader = new LinearGradient(0, h / 2f, 0, h, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP);
            blackPaint.setShader(blackShader);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawRoundRect(mBgPickerRect, 24f, 24f, huePaint);
            canvas.drawRoundRect(mBgPickerRect, 24f, 24f, whitePaint);
            canvas.drawRoundRect(mBgPickerRect, 24f, 24f, blackPaint);
            canvas.drawCircle(cursorX, cursorY, 20f, cursorPaint);
        }

        // FIX: Fully qualified SuppressLint annotation to prevent missing import error
        @android.annotation.SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(@NonNull MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }

            cursorX = Math.max(0, Math.min(event.getX(), getWidth()));
            cursorY = Math.max(0, Math.min(event.getY(), getHeight()));

            float hue = (cursorX / getWidth()) * 360f;
            float yNorm = cursorY / getHeight();

            float saturation;
            float value;

            if (yNorm <= 0.5f) {
                saturation = yNorm * 2f;
                value = 1f;
            } else {
                saturation = 1f;
                value = 1f - ((yNorm - 0.5f) * 2f);
            }

            int color = Color.HSVToColor(new float[]{hue, saturation, value});

            if (listener != null) {
                listener.onColorChanged(color);
            }
            invalidate();
            return true;
        }
    }

    private static class AdvancedDrawingView extends View {

        private final Bitmap baseBitmap;
        private Bitmap paintBitmap;
        private Canvas paintCanvas;

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
                try {
                    paintBitmap = Bitmap.createBitmap(baseBitmap.getWidth(), baseBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                    paintCanvas = new Canvas(paintBitmap);
                    saveUndoState();
                } catch (OutOfMemoryError e) {
                    Toast.makeText(getContext(), "Canvas initialized with limited memory", Toast.LENGTH_SHORT).show();
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();

            if (paintBitmap == null) return;
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
                case 0:
                    p.setStrokeWidth(size * 0.4f);
                    p.setAlpha((int)(alpha * 0.8f));
                    p.setPathEffect(new DiscretePathEffect(3f, 2f));
                    break;
                case 1:
                    p.setStrokeWidth(size);
                    p.setAlpha(alpha);
                    break;
                case 2:
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
                case 3:
                    p.setStrokeWidth(size * 1.5f);
                    p.setStrokeCap(Paint.Cap.SQUARE);
                    p.setAlpha((int)(alpha * 0.6f));
                    if (!isPreview) p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN));
                    break;
                case 4:
                    p.setStrokeWidth(size * 1.2f);
                    p.setAlpha(alpha);
                    p.setPathEffect(new CornerPathEffect(size));
                    break;
                case 5:
                    p.setStrokeWidth(size * 2f);
                    p.setAlpha((int)(alpha * 0.25f));
                    finalBlur = size * 0.8f;
                    break;
                case 6:
                    p.setStrokeWidth(size * 1.5f);
                    p.setAlpha((int)(alpha * 0.4f));
                    if (!isPreview) p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN));
                    finalBlur = size * 0.5f;
                    break;
                case 7:
                    p.setStrokeWidth(size);
                    p.setAlpha(alpha);
                    p.setPathEffect(new DiscretePathEffect(10f, 5f));
                    break;
                case 8:
                    p.setStrokeWidth(size);
                    p.setAlpha((int)(alpha * 0.8f));
                    finalBlur = size * 0.4f;
                    break;
                case 9:
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

        private void executeFloodFill(int imgX, int imgY) {
            Toast.makeText(getContext(), "Filling Area...", Toast.LENGTH_SHORT).show();

            new Thread(() -> {
                try {
                    Bitmap merged = getFinalBitmap();
                    int width = merged.getWidth();
                    int height = merged.getHeight();

                    int px = Math.max(0, Math.min(imgX, width - 1));
                    int py = Math.max(0, Math.min(imgY, height - 1));

                    int[] mergedPixels = new int[width * height];
                    merged.getPixels(mergedPixels, 0, width, 0, 0, width, height);
                    int targetColor = mergedPixels[py * width + px];

                    if (colorMatch(targetColor, brushColor)) return;

                    int[] paintPixels = new int[width * height];
                    paintBitmap.getPixels(paintPixels, 0, width, 0, 0, width, height);

                    int[] stack = new int[width * height];
                    int stackPointer = 0;
                    stack[stackPointer++] = py * width + px;

                    while (stackPointer > 0) {
                        int pos = stack[--stackPointer];
                        int cx = pos % width;
                        int cy = pos / width;

                        if (!colorMatch(mergedPixels[cy * width + cx], targetColor)) continue;

                        int w = cx;
                        while (w > 0 && colorMatch(mergedPixels[cy * width + (w - 1)], targetColor)) w--;
                        int e = cx;
                        while (e < width - 1 && colorMatch(mergedPixels[cy * width + (e + 1)], targetColor)) e++;

                        boolean scanAbove = false;
                        boolean scanBelow = false;

                        for (int xIdx = w; xIdx <= e; xIdx++) {
                            int currentIdx = cy * width + xIdx;
                            paintPixels[currentIdx] = brushColor;
                            mergedPixels[currentIdx] = brushColor;

                            if (cy > 0) {
                                int upIdx = (cy - 1) * width + xIdx;
                                boolean matchAbove = colorMatch(mergedPixels[upIdx], targetColor);
                                if (matchAbove && !scanAbove) {
                                    stack[stackPointer++] = upIdx;
                                    scanAbove = true;
                                } else if (!matchAbove) scanAbove = false;
                            }

                            if (cy < height - 1) {
                                int downIdx = (cy + 1) * width + xIdx;
                                boolean matchBelow = colorMatch(mergedPixels[downIdx], targetColor);
                                if (matchBelow && !scanBelow) {
                                    stack[stackPointer++] = downIdx;
                                    scanBelow = true;
                                } else if (!matchBelow) scanBelow = false;
                            }
                        }
                    }

                    ((Activity)getContext()).runOnUiThread(() -> {
                        paintBitmap.setPixels(paintPixels, 0, width, 0, 0, width, height);
                        saveUndoState();
                        invalidate();
                        Toast.makeText(getContext(), "Fill Complete!", Toast.LENGTH_SHORT).show();
                    });
                } catch (OutOfMemoryError e) {
                    ((Activity)getContext()).runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Fill failed: Too much memory needed.", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        }

        private boolean colorMatch(int c1, int c2) {
            if (c1 == c2) return true;
            return Math.abs(Color.red(c1) - Color.red(c2)) <= 25 &&
                    Math.abs(Color.green(c1) - Color.green(c2)) <= 25 &&
                    Math.abs(Color.blue(c1) - Color.blue(c2)) <= 25;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (paintBitmap == null) return false;

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

                    if (brushType == 10) return true;

                    isLongPressFired = false;
                    showPickerIndicator = false;
                    downImgX = imgX; downImgY = imgY;
                    postDelayed(longPressRunnable, 500);

                    if (brushType == 8) {
                        if (smudgeSampleBitmap == null) {
                            try {
                                smudgeSampleBitmap = Bitmap.createBitmap(baseBitmap.getWidth(), baseBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                                smudgeSampleCanvas = new Canvas(smudgeSampleBitmap);
                            } catch (OutOfMemoryError e) {
                                Toast.makeText(getContext(), "Not enough memory for Smudge tool", Toast.LENGTH_SHORT).show();
                                return true;
                            }
                        }

                        smudgeSampleBitmap.eraseColor(Color.TRANSPARENT);
                        if (baseBitmap != null) smudgeSampleCanvas.drawBitmap(baseBitmap, 0, 0, null);
                        smudgeSampleCanvas.drawBitmap(paintBitmap, 0, 0, null);

                        smudgeShader = new BitmapShader(smudgeSampleBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

                        smudgeStampPaint.reset();
                        smudgeStampPaint.setAntiAlias(true);
                        smudgeStampPaint.setShader(smudgeShader);
                        smudgeStampPaint.setAlpha((int)(brushOpacity * 0.9f));
                        smudgeStampPaint.setMaskFilter(new BlurMaskFilter(Math.max(0.1f, brushSize * 0.3f), BlurMaskFilter.Blur.NORMAL));
                    }

                    applyBrushProfileToPaint(currentPaint, brushType, paintBrushSubStyle, brushSize, brushOpacity, brushColor, brushHardness, false);
                    currentPath.reset();

                    lastX = imgX; lastY = imgY;
                    currentPath.moveTo(imgX, imgY);
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (brushType == 10) return true;

                    if (!isLongPressFired && Math.hypot(imgX - downImgX, imgY - downImgY) > (15f / scale)) {
                        removeCallbacks(longPressRunnable);
                    }

                    if (isLongPressFired) {
                        pickColorAtTouch(imgX, imgY);
                        return true;
                    }

                    float tolerance = 2f / scale;
                    float dX = Math.abs(imgX - lastX);
                    float dY = Math.abs(imgY - lastY);

                    if (dX >= tolerance || dY >= tolerance) {
                        float midX = (lastX + imgX) / 2f;
                        float midY = (lastY + imgY) / 2f;

                        if (brushType == 8 && smudgeShader != null) {
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
                        } else if (brushType == 9) {
                            currentPath.quadTo(lastX, lastY, midX, midY);
                            paintCanvas.drawPath(currentPath, currentPaint);
                            lastX = imgX;
                            lastY = imgY;
                        } else {
                            currentPath.quadTo(lastX, lastY, midX, midY);
                            lastX = imgX;
                            lastY = imgY;
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    performClick();

                    if (brushType == 10) {
                        float moved = (float) Math.hypot(x - downScreenX, event.getY() - downScreenY);
                        if (moved < 25f) {
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

        public void setBrushSize(float size) { this.brushSize = size; }
        public void setBrushOpacity(int alpha) { this.brushOpacity = alpha; }
        public void setBrushColor(int color) { this.brushColor = color; }
        public void setBrushHardness(float hardness) { this.brushHardness = hardness; }
        public void setBrushType(int type) {
            this.brushType = type;
            if (getContext() instanceof AdvancedCanvasActivity) {
                this.paintBrushSubStyle = ((AdvancedCanvasActivity)getContext()).paintBrushSubStyle;
            }
        }

        private void saveUndoState() {
            try {
                if (rasterUndoStack.size() >= 5) rasterUndoStack.remove(0);
                rasterUndoStack.add(Bitmap.createBitmap(paintBitmap));
                rasterRedoStack.clear();
            } catch (OutOfMemoryError e) {
                if (!rasterUndoStack.isEmpty()) rasterUndoStack.remove(0);
            }
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

        public Bitmap getOptimizedSaveBitmap() {
            if (baseBitmap != null && paintBitmap != null) {
                Canvas c = new Canvas(baseBitmap);
                c.drawBitmap(paintBitmap, 0, 0, null);
                return baseBitmap;
            }
            return paintBitmap != null ? paintBitmap : baseBitmap;
        }
    }
}