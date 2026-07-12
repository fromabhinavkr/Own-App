package com.example.ownphotoonwall;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.slider.Slider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class CloneActivity extends AppCompatActivity {

    public static final int MODE_ADJUST = 0;
    public static final int MODE_CLONE = 1;
    public static final int MODE_RESTORE = 2;

    private boolean isDarkTheme;
    private int currentMode = MODE_CLONE;

    private Bitmap targetBitmap;
    private Bitmap sourceBitmap;

    private SourceCloneView sourceView;
    private TargetCloneView targetView;

    private View btnClone, btnRestore, btnAdjust;
    private View optionsPanel;

    // Brush Settings
    public float cloneBrushSize = 80f;
    public int cloneOpacity = 255;
    public float cloneHardness = 50f;

    public PointF activeTouchPoint = null;

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    loadSourceImage(imageUri);
                } else if (sourceBitmap == null) {
                    Toast.makeText(this, "Source image required for cloning.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clone);

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
        applyTheme();

        String inputImagePath = getIntent().getStringExtra("image_path");
        if (inputImagePath != null && new File(inputImagePath).exists()) {
            targetBitmap = BitmapFactory.decodeFile(inputImagePath);
        } else {
            Toast.makeText(this, "Error loading target image", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FrameLayout targetContainer = findViewById(R.id.targetViewContainer);
        targetView = new TargetCloneView(this);
        targetContainer.addView(targetView);

        FrameLayout sourceContainer = findViewById(R.id.sourceViewContainer);
        sourceView = new SourceCloneView(this);
        sourceContainer.addView(sourceView);

        setupToolbar();
        setupOptionsPanel();

        findViewById(R.id.btnCancelClone).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        findViewById(R.id.btnSaveClone).setOnClickListener(v -> saveAndReturn());

        findViewById(R.id.btnFlipSource).setOnClickListener(v -> {
            if (sourceView != null) sourceView.flipHorizontally();
        });

        launchGallery();
    }

    private void applyCapsuleBackground(View view, int color) {
        if (view == null) return;

        // Save current padding to prevent background application from resetting it!
        int pL = view.getPaddingLeft();
        int pT = view.getPaddingTop();
        int pR = view.getPaddingRight();
        int pB = view.getPaddingBottom();

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(100f); // Perfect pill shape

        view.setBackground(gd);

        // Restore padding
        view.setPadding(pL, pT, pR, pB);

        // Force alignment inside the capsule
        if (view instanceof TextView) {
            ((TextView) view).setGravity(Gravity.CENTER);
            ((TextView) view).setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        } else if (view instanceof LinearLayout) {
            ((LinearLayout) view).setGravity(Gravity.CENTER);
        }
    }

    private void applyTheme() {
        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
        int panelColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        int textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");
        int labelColor = isDarkTheme ? Color.parseColor("#B3000000") : Color.parseColor("#B3FFFFFF");

        findViewById(R.id.cloneRoot).setBackgroundColor(bgColor);

        GradientDrawable toolbarBg = new GradientDrawable();
        toolbarBg.setColor(panelColor);
        toolbarBg.setCornerRadius(100f);
        findViewById(R.id.cloneToolbar).setBackground(toolbarBg);

        // This enforces the background AND keeps the text perfectly centered
        applyCapsuleBackground(findViewById(R.id.tvBaseLabel), labelColor);
        applyCapsuleBackground(findViewById(R.id.btnFlipSource), labelColor);

        TextView tvBaseLabel = findViewById(R.id.tvBaseLabel);
        TextView tvFlipIcon = findViewById(R.id.tvFlipIcon);
        TextView tvFlipText = findViewById(R.id.tvFlipText);

        tvBaseLabel.setTextColor(textColor);
        tvFlipIcon.setTextColor(textColor);
        tvFlipText.setTextColor(textColor);

        View options = findViewById(R.id.optionsPanel);
        options.setBackgroundColor(isDarkTheme ? Color.parseColor("#E61C1C1E") : Color.parseColor("#E6F2F2F7"));

        applyTextColor(findViewById(R.id.cloneToolbar), textColor);
        applyTextColor(options, textColor);
    }

    private void applyTextColor(View view, int color) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) applyTextColor(vg.getChildAt(i), color);
        }
    }

    private void launchGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        galleryLauncher.launch(intent);
    }

    // Prevents cloning from infinitely streaking at the edges!
    private Bitmap addTransparentPadding(Bitmap original) {
        Bitmap padded = Bitmap.createBitmap(original.getWidth() + 2, original.getHeight() + 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(padded);
        canvas.drawColor(Color.TRANSPARENT);
        canvas.drawBitmap(original, 1, 1, null);
        return padded;
    }

    private void loadSourceImage(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            is.close();

            int maxDim = 2048;
            int scale = 1;
            while (options.outWidth / scale / 2 >= maxDim && options.outHeight / scale / 2 >= maxDim) {
                scale *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = scale;

            InputStream is2 = getContentResolver().openInputStream(uri);
            if (is2 != null) {
                Bitmap decoded = BitmapFactory.decodeStream(is2, null, options);
                if (decoded != null) {
                    sourceBitmap = addTransparentPadding(decoded);
                    if (decoded != sourceBitmap) decoded.recycle();
                }
                is2.close();
            }

            sourceView.setBitmap(sourceBitmap);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load source image", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupToolbar() {
        findViewById(R.id.btnSetTarget).setOnClickListener(v -> launchGallery());

        btnClone = findViewById(R.id.btnClone);
        btnRestore = findViewById(R.id.btnRestore);
        btnAdjust = findViewById(R.id.btnAdjust);
        optionsPanel = findViewById(R.id.optionsPanel);

        btnClone.setOnClickListener(v -> setMode(MODE_CLONE));
        btnRestore.setOnClickListener(v -> setMode(MODE_RESTORE));
        btnAdjust.setOnClickListener(v -> setMode(MODE_ADJUST));

        findViewById(R.id.btnOptions).setOnClickListener(v -> {
            if (optionsPanel.getVisibility() == View.VISIBLE) {
                optionsPanel.setVisibility(View.GONE);
                findViewById(R.id.btnOptions).setBackgroundColor(Color.TRANSPARENT);
            } else {
                optionsPanel.setVisibility(View.VISIBLE);
                findViewById(R.id.btnOptions).setBackgroundColor(Color.parseColor("#4A90E2"));
            }
        });
    }

    private void setMode(int mode) {
        this.currentMode = mode;
        btnClone.setBackgroundColor(Color.TRANSPARENT);
        btnRestore.setBackgroundColor(Color.TRANSPARENT);
        btnAdjust.setBackgroundColor(Color.TRANSPARENT);

        if (mode == MODE_CLONE) btnClone.setBackgroundColor(Color.parseColor("#E53935"));
        else if (mode == MODE_RESTORE) btnRestore.setBackgroundColor(Color.parseColor("#4A90E2"));
        else if (mode == MODE_ADJUST) btnAdjust.setBackgroundColor(Color.parseColor("#4A90E2"));
    }

    private void setupOptionsPanel() {
        Slider slSize = findViewById(R.id.slCloneSize);
        Slider slOpacity = findViewById(R.id.slCloneOpacity);
        Slider slHardness = findViewById(R.id.slCloneHardness);

        slSize.addOnChangeListener((slider, value, fromUser) -> cloneBrushSize = value);
        slOpacity.addOnChangeListener((slider, value, fromUser) -> cloneOpacity = (int)((value/100f)*255));
        slHardness.addOnChangeListener((slider, value, fromUser) -> cloneHardness = value);
    }

    private void saveAndReturn() {
        Bitmap result = targetView.getFinalBitmap();
        try {
            File outFile = new File(getCacheDir(), "clone_out.png");
            FileOutputStream fos = new FileOutputStream(outFile);
            result.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            Intent data = new Intent();
            data.putExtra("out_path", outFile.getAbsolutePath());
            data.putExtra("save_as_base", true);
            setResult(RESULT_OK, data);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show();
        }
    }

    private void drawHUDOverlay(Canvas canvas, float viewZoom, float viewRotation, float width, float height) {
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        textPaint.setTextSize(45f);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(isDarkTheme ? Color.argb(200, 0, 0, 0) : Color.argb(200, 255, 255, 255));

        String txt = String.format("%.1fx  |  %.1f°", viewZoom, viewRotation % 360f);
        float w = textPaint.measureText(txt);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float h = fm.bottom - fm.top;

        float cx = width / 2f;
        float cy = height / 2f;

        canvas.drawRoundRect(cx - w/2f - 40f, cy - h/2f - 20f, cx + w/2f + 40f, cy + h/2f + 20f, 50f, 50f, bgPaint);
        canvas.drawText(txt, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
    }

    private class SourceCloneView extends View {
        private Bitmap bitmap;
        public final Matrix matrix = new Matrix();
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public PointF activeTouchPoint = null;

        private float viewZoom = 1f;
        private float viewPanX = 0f;
        private float viewPanY = 0f;
        private float viewRotation = 0f;

        public boolean isAdjusting = false;
        private float lastFocusX, lastFocusY;
        private float lastDist, lastAngle;
        private int activeCount = 0;

        public SourceCloneView(Context context) {
            super(context);
            indicatorPaint.setStyle(Paint.Style.STROKE);
            indicatorPaint.setColor(Color.WHITE);
            indicatorPaint.setStrokeWidth(4f);
            indicatorPaint.setShadowLayer(4f, 0, 2f, Color.BLACK);
        }

        public void setBitmap(Bitmap bmp) {
            this.bitmap = bmp;
            viewZoom = 1f; viewPanX = 0f; viewPanY = 0f; viewRotation = 0f;
            updateMatrix();
            invalidate();
        }

        public void flipHorizontally() {
            if (bitmap != null) {
                Matrix flipMatrix = new Matrix();
                flipMatrix.preScale(-1.0f, 1.0f);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), flipMatrix, true);
                CloneActivity.this.sourceBitmap = bitmap;
                updateMatrix();
                invalidate();
                if (targetView != null) targetView.invalidate();
            }
        }

        private void updateMatrix() {
            if (bitmap == null || getWidth() == 0) return;
            matrix.reset();
            float baseScale = Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());

            matrix.postTranslate(-bitmap.getWidth() / 2f, -bitmap.getHeight() / 2f);
            matrix.postScale(baseScale * viewZoom, baseScale * viewZoom);
            matrix.postRotate(viewRotation);
            matrix.postTranslate(getWidth() / 2f + viewPanX, getHeight() / 2f + viewPanY);
        }

        @Override protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            updateMatrix();
        }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, matrix, paint);
            }
            if (activeTouchPoint != null) {
                canvas.drawCircle(activeTouchPoint.x, activeTouchPoint.y, cloneBrushSize/2f, indicatorPaint);
            }
            if (isAdjusting && activeCount >= 2) {
                drawHUDOverlay(canvas, viewZoom, viewRotation, getWidth(), getHeight());
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (currentMode != MODE_ADJUST && event.getPointerCount() == 1) {
                isAdjusting = false;
                if (targetView != null) targetView.invalidate();
                return false;
            }

            isAdjusting = true;
            if (targetView != null) targetView.invalidate();

            int action = event.getActionMasked();
            boolean pointerUp = action == MotionEvent.ACTION_POINTER_UP;
            int skipIndex = pointerUp ? event.getActionIndex() : -1;

            float sumX = 0, sumY = 0;
            activeCount = 0;
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (i == skipIndex) continue;
                sumX += event.getX(i);
                sumY += event.getY(i);
                activeCount++;
            }
            float focusX = activeCount > 0 ? sumX / activeCount : 0;
            float focusY = activeCount > 0 ? sumY / activeCount : 0;

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    lastFocusX = focusX;
                    lastFocusY = focusY;
                    if (activeCount >= 2) {
                        int p1 = 0, p2 = 1;
                        if (skipIndex == 0) p1 = 2; else if (skipIndex == 1) p2 = 2;
                        float dx = event.getX(p2) - event.getX(p1);
                        float dy = event.getY(p2) - event.getY(p1);
                        lastDist = (float) Math.hypot(dx, dy);
                        lastAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    viewPanX += focusX - lastFocusX;
                    viewPanY += focusY - lastFocusY;
                    lastFocusX = focusX;
                    lastFocusY = focusY;

                    if (activeCount >= 2) {
                        float dx = event.getX(1) - event.getX(0);
                        float dy = event.getY(1) - event.getY(0);
                        float dist = (float) Math.hypot(dx, dy);
                        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

                        if (lastDist > 0) {
                            viewZoom *= (dist / lastDist);
                            viewZoom = Math.max(0.1f, Math.min(viewZoom, 50f));
                        }
                        viewRotation += (angle - lastAngle);

                        lastDist = dist;
                        lastAngle = angle;
                    }
                    updateMatrix();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isAdjusting = false;
                    activeCount = 0;
                    if (targetView != null) targetView.invalidate();
                    break;
            }
            invalidate();
            return true;
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }

    private class TargetCloneView extends View {
        private Bitmap paintBitmap;
        private Canvas paintCanvas;

        public final Matrix matrix = new Matrix();
        private final Paint renderPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint currentPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Path currentPath = new Path();
        private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float viewZoom = 1f;
        private float viewPanX = 0f;
        private float viewPanY = 0f;
        private float viewRotation = 0f;

        private float lastX, lastY;
        private boolean isDrawing = false;

        private boolean isAdjusting = false;
        private float lastFocusX, lastFocusY;
        private float lastDist, lastAngle;
        private int activeCount = 0;

        public TargetCloneView(Context context) {
            super(context);
            setLayerType(LAYER_TYPE_SOFTWARE, null);

            indicatorPaint.setStyle(Paint.Style.STROKE);
            indicatorPaint.setColor(Color.WHITE);
            indicatorPaint.setStrokeWidth(4f);
            indicatorPaint.setShadowLayer(4f, 0, 2f, Color.BLACK);
        }

        private void updateMatrix() {
            if (targetBitmap == null || getWidth() == 0) return;
            matrix.reset();
            float baseScale = Math.min((float) getWidth() / targetBitmap.getWidth(), (float) getHeight() / targetBitmap.getHeight());

            matrix.postTranslate(-targetBitmap.getWidth() / 2f, -targetBitmap.getHeight() / 2f);
            matrix.postScale(baseScale * viewZoom, baseScale * viewZoom);
            matrix.postRotate(viewRotation);
            matrix.postTranslate(getWidth() / 2f + viewPanX, getHeight() / 2f + viewPanY);
        }

        @Override protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            if (targetBitmap != null && paintBitmap == null) {
                paintBitmap = Bitmap.createBitmap(targetBitmap.getWidth(), targetBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                paintCanvas = new Canvas(paintBitmap);
                updateMatrix();
            }
        }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            if (targetBitmap != null) canvas.drawBitmap(targetBitmap, matrix, renderPaint);
            if (paintBitmap != null) canvas.drawBitmap(paintBitmap, matrix, renderPaint);

            if (isDrawing && currentMode != MODE_ADJUST) {
                canvas.save();
                canvas.concat(matrix);
                canvas.drawPath(currentPath, currentPaint);
                canvas.restore();
            }

            if (sourceView != null && sourceView.isAdjusting && sourceBitmap != null) {
                Paint ghostPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
                ghostPaint.setAlpha(128);
                canvas.drawBitmap(sourceBitmap, sourceView.matrix, ghostPaint);
            }

            if (CloneActivity.this.activeTouchPoint != null && currentMode != MODE_ADJUST && !sourceView.isAdjusting) {
                canvas.drawCircle(CloneActivity.this.activeTouchPoint.x, CloneActivity.this.activeTouchPoint.y, cloneBrushSize/2f, indicatorPaint);
            }

            if (isAdjusting && activeCount >= 2) {
                drawHUDOverlay(canvas, viewZoom, viewRotation, getWidth(), getHeight());
            }
        }

        private void prepBrush() {
            currentPaint.reset();
            currentPaint.setAntiAlias(true);
            currentPaint.setDither(true);
            currentPaint.setStyle(Paint.Style.STROKE);
            currentPaint.setStrokeJoin(Paint.Join.ROUND);
            currentPaint.setStrokeCap(Paint.Cap.ROUND);

            float scaleFactor = getScaleFactor();
            currentPaint.setStrokeWidth(cloneBrushSize / scaleFactor);

            float blur = (cloneBrushSize / scaleFactor) * ((100f - cloneHardness) / 100f);
            if (blur > 0) currentPaint.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));

            if (currentMode == MODE_RESTORE) {
                currentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                currentPaint.setColor(Color.TRANSPARENT);
                currentPaint.setAlpha(255);
            } else if (currentMode == MODE_CLONE) {
                currentPaint.setAlpha(cloneOpacity);
                if (sourceBitmap != null) {
                    BitmapShader shader = new BitmapShader(sourceBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

                    Matrix shaderMatrix = new Matrix();
                    Matrix inverseTarget = new Matrix();
                    matrix.invert(inverseTarget);

                    shaderMatrix.set(sourceView.matrix);
                    shaderMatrix.postConcat(inverseTarget);

                    shader.setLocalMatrix(shaderMatrix);
                    currentPaint.setShader(shader);
                }
            }
        }

        private float getScaleFactor() {
            float[] v = new float[9];
            matrix.getValues(v);
            return (float) Math.hypot(v[Matrix.MSCALE_X], v[Matrix.MSKEW_Y]);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float screenX = event.getX();
            float screenY = event.getY();

            CloneActivity.this.activeTouchPoint = new PointF(screenX, screenY);
            if (sourceView != null) {
                sourceView.activeTouchPoint = new PointF(screenX, screenY);
                sourceView.invalidate();
            }

            if (currentMode == MODE_ADJUST || event.getPointerCount() > 1) {
                isDrawing = false;
                isAdjusting = true;

                int action = event.getActionMasked();
                boolean pointerUp = action == MotionEvent.ACTION_POINTER_UP;
                int skipIndex = pointerUp ? event.getActionIndex() : -1;

                float sumX = 0, sumY = 0;
                activeCount = 0;
                for (int i = 0; i < event.getPointerCount(); i++) {
                    if (i == skipIndex) continue;
                    sumX += event.getX(i);
                    sumY += event.getY(i);
                    activeCount++;
                }
                float focusX = activeCount > 0 ? sumX / activeCount : 0;
                float focusY = activeCount > 0 ? sumY / activeCount : 0;

                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_POINTER_DOWN:
                        lastFocusX = focusX;
                        lastFocusY = focusY;
                        if (activeCount >= 2) {
                            int p1 = 0, p2 = 1;
                            if (skipIndex == 0) p1 = 2; else if (skipIndex == 1) p2 = 2;
                            float dx = event.getX(p2) - event.getX(p1);
                            float dy = event.getY(p2) - event.getY(p1);
                            lastDist = (float) Math.hypot(dx, dy);
                            lastAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        viewPanX += focusX - lastFocusX;
                        viewPanY += focusY - lastFocusY;
                        lastFocusX = focusX;
                        lastFocusY = focusY;

                        if (activeCount >= 2) {
                            float dx = event.getX(1) - event.getX(0);
                            float dy = event.getY(1) - event.getY(0);
                            float dist = (float) Math.hypot(dx, dy);
                            float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

                            if (lastDist > 0) {
                                viewZoom *= (dist / lastDist);
                                viewZoom = Math.max(0.1f, Math.min(viewZoom, 50f));
                            }
                            viewRotation += (angle - lastAngle);

                            lastDist = dist;
                            lastAngle = angle;
                        }
                        updateMatrix();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isAdjusting = false;
                        activeCount = 0;
                        CloneActivity.this.activeTouchPoint = null;
                        if (sourceView != null) { sourceView.activeTouchPoint = null; sourceView.invalidate(); }
                        performClick();
                        break;
                }
                invalidate();
                return true;
            }

            Matrix inverse = new Matrix();
            matrix.invert(inverse);
            float[] pts = {screenX, screenY};
            inverse.mapPoints(pts);
            float imgX = pts[0];
            float imgY = pts[1];

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isDrawing = true;
                    prepBrush();
                    currentPath.reset();
                    currentPath.moveTo(imgX, imgY);
                    lastX = imgX; lastY = imgY;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (isDrawing) {
                        currentPath.quadTo(lastX, lastY, (imgX + lastX) / 2, (imgY + lastY) / 2);
                        lastX = imgX; lastY = imgY;
                    }
                    break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    if (isDrawing) {
                        currentPath.lineTo(imgX, imgY);
                        paintCanvas.drawPath(currentPath, currentPaint);
                    }
                    currentPath.reset();
                    isDrawing = false;
                    CloneActivity.this.activeTouchPoint = null;
                    if (sourceView != null) { sourceView.activeTouchPoint = null; sourceView.invalidate(); }
                    performClick();
                    break;
            }
            invalidate();
            return true;
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        public Bitmap getFinalBitmap() {
            Bitmap result = Bitmap.createBitmap(targetBitmap.getWidth(), targetBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(result);
            c.drawBitmap(targetBitmap, 0, 0, null);
            if (paintBitmap != null) c.drawBitmap(paintBitmap, 0, 0, null);
            return result;
        }
    }
}