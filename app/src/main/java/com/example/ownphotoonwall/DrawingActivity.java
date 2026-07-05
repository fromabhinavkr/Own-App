package com.example.ownphotoonwall;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;

public class DrawingActivity extends Activity {
    private DrawingView drawingView;
    private FrameLayout canvasContainer;
    private LinearLayout rootLayout;
    private ImageButton btnThemeToggle;
    private SharedPreferences prefs;

    private int currentBgMode = 1; // 0=Trans, 1=White, 2=Black, 3=WhiteLined, 4=BlackLined
    private int currentTempColor;
    private boolean isUpdatingFromWheel = false;
    private boolean isDarkMode = false;

    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawing);

        prefs = getSharedPreferences("DrawingAppPrefs", MODE_PRIVATE);
        isDarkMode = prefs.getBoolean("isDarkMode", false);
        currentBgMode = prefs.getInt("bgMode", 1); // Remembers your paper type

        Intent intent = getIntent();
        if (intent.getExtras() != null) {
            mAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        drawingView = findViewById(R.id.drawingView);
        canvasContainer = findViewById(R.id.canvasContainer);
        rootLayout = findViewById(R.id.rootLayout);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);

        updateCanvasBackground();
        applyTheme();

        btnThemeToggle.setOnClickListener(v -> {
            isDarkMode = !isDarkMode;
            prefs.edit().putBoolean("isDarkMode", isDarkMode).apply();
            applyTheme();
        });

        findViewById(R.id.btnWidgetColor).setOnClickListener(v -> showWidgetBackgroundPicker());
        findViewById(R.id.btnBrushColor).setOnClickListener(v -> showModernColorPicker());
        findViewById(R.id.btnUndo).setOnClickListener(v -> drawingView.undo());
        findViewById(R.id.btnClear).setOnClickListener(v -> drawingView.clear());

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            saveDrawingAndPushToWidget();
            finish();
        });
    }

    private void applyTheme() {
        if (isDarkMode) {
            rootLayout.setBackgroundColor(Color.parseColor("#1C1C1E"));
            btnThemeToggle.setImageResource(R.drawable.ic_sun);
            btnThemeToggle.setColorFilter(Color.WHITE);
        } else {
            rootLayout.setBackgroundColor(Color.parseColor("#F2F2F7"));
            btnThemeToggle.setImageResource(R.drawable.ic_moon);
            btnThemeToggle.setColorFilter(Color.parseColor("#333333"));
        }
    }

    private void updateCanvasBackground() {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(60f);

        // Base solid color behind the lines
        if (currentBgMode == 0) gd.setColor(Color.parseColor("#E0E0E0"));
        else if (currentBgMode == 1 || currentBgMode == 3) gd.setColor(Color.WHITE);
        else if (currentBgMode == 2 || currentBgMode == 4) gd.setColor(Color.parseColor("#121212"));

        canvasContainer.setBackground(gd);
        canvasContainer.setClipToOutline(true);

        // Tell the view to draw the lines if needed
        drawingView.setBgMode(currentBgMode);
    }

    private void showWidgetBackgroundPicker() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bg_picker, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Apply dynamic Modern Theme to the Dialog
        LinearLayout dialogRoot = dialogView.findViewById(R.id.dialogRoot);
        TextView tvTitle = dialogView.findViewById(R.id.tvTitle);

        if (isDarkMode) {
            dialogRoot.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2C2C2E")));
            tvTitle.setTextColor(Color.WHITE);
        } else {
            dialogRoot.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            tvTitle.setTextColor(Color.parseColor("#333333"));
        }

        setupBgButton(dialogView.findViewById(R.id.btnTrans), dialog, 0);
        setupBgButton(dialogView.findViewById(R.id.btnWhite), dialog, 1);
        setupBgButton(dialogView.findViewById(R.id.btnBlack), dialog, 2);
        setupBgButton(dialogView.findViewById(R.id.btnWhiteLined), dialog, 3);
        setupBgButton(dialogView.findViewById(R.id.btnBlackLined), dialog, 4);

        dialog.show();
    }

    // Styles the buttons inside the dialog based on theme
    private void setupBgButton(Button btn, AlertDialog dialog, int mode) {
        if (isDarkMode) {
            btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3A3A3C")));
            btn.setTextColor(Color.WHITE);
        } else {
            btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E5E5EA")));
            btn.setTextColor(Color.parseColor("#333333"));
        }

        btn.setOnClickListener(v -> {
            currentBgMode = mode;
            prefs.edit().putInt("bgMode", currentBgMode).apply();
            updateCanvasBackground();
            dialog.dismiss();
        });
    }

    private void showModernColorPicker() {
        currentTempColor = drawingView.getCurrentColor();
        final float[] currentTempStrokeWidth = { drawingView.getStrokeWidth() };

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_color_picker, null);
        FrameLayout container = dialogView.findViewById(R.id.colorWheelContainer);
        EditText hexInput = dialogView.findViewById(R.id.hexInput);
        SeekBar brushSizeSlider = dialogView.findViewById(R.id.brushSizeSlider);
        Button btnApply = dialogView.findViewById(R.id.btnApplyColor);
        Button btnEraser = dialogView.findViewById(R.id.btnEraser);

        hexInput.setText(String.format("%06X", (0xFFFFFF & currentTempColor)));

        brushSizeSlider.setProgress((int) currentTempStrokeWidth[0]);
        brushSizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentTempStrokeWidth[0] = Math.max(2f, (float) progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        View colorWheel = new View(this) {
            final Paint huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint satPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
                float cx = w / 2f;
                float cy = h / 2f;
                float radius = Math.min(cx, cy) - 20f;

                int[] colors = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED};
                huePaint.setShader(new SweepGradient(cx, cy, colors, null));
                satPaint.setShader(new RadialGradient(cx, cy, radius, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP));
            }

            @Override
            protected void onDraw(Canvas canvas) {
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float radius = Math.min(cx, cy) - 20f;

                canvas.drawCircle(cx, cy, radius, huePaint);
                canvas.drawCircle(cx, cy, radius, satPaint);

                centerPaint.setColor(currentTempColor);
                centerPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, radius * 0.3f, centerPaint);

                centerPaint.setColor(Color.LTGRAY);
                centerPaint.setStyle(Paint.Style.STROKE);
                centerPaint.setStrokeWidth(3f);
                canvas.drawCircle(cx, cy, radius * 0.3f, centerPaint);
            }
            @Override public boolean performClick() { return super.performClick(); }
        };

        colorWheel.setOnTouchListener((v, event) -> {
            float cx = v.getWidth() / 2f;
            float cy = v.getHeight() / 2f;
            float radius = Math.min(cx, cy) - 20f;
            float dx = event.getX() - cx;
            float dy = event.getY() - cy;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance <= radius) {
                float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                if (angle < 0) angle += 360;
                float saturation = distance / radius;

                currentTempColor = Color.HSVToColor(new float[]{angle, saturation, 1f});
                v.invalidate();

                isUpdatingFromWheel = true;
                hexInput.setText(String.format("%06X", (0xFFFFFF & currentTempColor)));
                isUpdatingFromWheel = false;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) v.performClick();
            return true;
        });

        container.addView(colorWheel);

        hexInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (!isUpdatingFromWheel && s != null && s.length() == 6) {
                    try {
                        currentTempColor = Color.parseColor("#" + s);
                        colorWheel.invalidate();
                    } catch (Exception ignored) {}
                }
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnEraser.setOnClickListener(v -> {
            drawingView.setStrokeWidth(currentTempStrokeWidth[0]);
            drawingView.setEraser(true);
            dialog.dismiss();
        });

        btnApply.setOnClickListener(v -> {
            drawingView.setStrokeWidth(currentTempStrokeWidth[0]);
            drawingView.setEraser(false);
            drawingView.setColor(currentTempColor);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveDrawingAndPushToWidget() {
        int width = drawingView.getWidth();
        int height = drawingView.getHeight();
        if (width <= 0 || height <= 0) return;

        Bitmap baseBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas baseCanvas = new Canvas(baseBitmap);

        // Export base solid color
        if (currentBgMode == 0) baseCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.SRC);
        else if (currentBgMode == 1 || currentBgMode == 3) baseCanvas.drawColor(Color.WHITE);
        else if (currentBgMode == 2 || currentBgMode == 4) baseCanvas.drawColor(Color.parseColor("#121212"));

        // Let DrawingView draw the lines and strokes over the base color
        drawingView.drawStrokes(baseCanvas);

        Bitmap roundedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas roundedCanvas = new Canvas(roundedBitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        float radius = 60f;
        RectF rect = new RectF(0, 0, width, height);
        roundedCanvas.drawRoundRect(rect, radius, radius, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        roundedCanvas.drawBitmap(baseBitmap, 0, 0, paint);

        String filename = (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) ? "drawing.png" : "drawing_" + mAppWidgetId + ".png";

        try {
            FileOutputStream fos = openFileOutput(filename, Context.MODE_PRIVATE);
            roundedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);

            if (mAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget_drawing);
                File file = new File(getFilesDir(), filename);
                if (file.exists()) {
                    views.setImageViewBitmap(R.id.widget_image, BitmapFactory.decodeFile(file.getAbsolutePath()));
                }

                Intent intent = new Intent(this, DrawingActivity.class);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
                PendingIntent pi = PendingIntent.getActivity(this, mAppWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.widget_image, pi);

                appWidgetManager.updateAppWidget(mAppWidgetId, views);
            }
        } catch (Exception e) {
            Log.e("DrawingActivity", "Error saving drawing to widget", e);
        }
    }
}