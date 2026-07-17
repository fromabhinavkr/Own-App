package com.example.ownphotoonwall;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

@SuppressWarnings("all")
@SuppressLint("SetTextI18n")
public class CollageStudioActivity extends AppCompatActivity {

    private CollageCanvasView canvasView;
    private EditText etBgHexCode, etStrokeHexCode, etShadowHexCode;
    private Button btnRearrange, btnToggleAdjust, btnToggleZoom, btnToggleDelete;
    private Button btnToggleStroke, btnToggleShadow;

    // NEW: Transformation Mode Buttons
    private Button btnToggleRotate, btnToggleMirrorH, btnToggleMirrorV;

    private Button btnCenterAddPhotos;
    private RelativeLayout loadingOverlay;

    // State Variables
    private final List<Uri> currentUris = new ArrayList<>();
    private float currentRatio = 1.0f;
    private float currentBorderWidth = 10f;
    private float currentCornerRadius = 0f;
    private float currentBlender = 0f;
    private int currentBgColor = Color.WHITE;
    private Uri currentBgTexture = null;
    private int currentLayoutMode = 0;

    // Interaction Modes
    private boolean isRearrangeMode = false;
    private boolean isAdjustMode = false;
    private boolean isZoomMode = false;
    private boolean isDeleteMode = false;
    private boolean isRotateMode = false;
    private boolean isMirrorHMode = false;
    private boolean isMirrorVMode = false;

    // Zoom, Pan, & Transform Memory
    private final List<Float> imageScales = new ArrayList<>();
    private final List<Float> imagePanX = new ArrayList<>();
    private final List<Float> imagePanY = new ArrayList<>();
    private final List<Float> imageRotations = new ArrayList<>();
    private final List<Boolean> imageFlipX = new ArrayList<>();
    private final List<Boolean> imageFlipY = new ArrayList<>();

    // Stroke Variables
    private boolean isStrokeEnabled = true;
    private float currentStrokeWidth = 10f;
    private int currentStrokeColor = Color.WHITE;

    // Shadow Variables
    private boolean isShadowEnabled = false;
    private float currentShadowOffsetX = 15f;
    private float currentShadowOffsetY = 15f;
    private int currentShadowColor = Color.BLACK;

    // Undo/Redo Engine
    private final Stack<CollageState> undoStack = new Stack<>();
    private final Stack<CollageState> redoStack = new Stack<>();

    // UI Theme
    private boolean isDarkTheme;
    private int textColor;
    private int themeButtonColor;

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> texturePickerLauncher;
    private boolean isUpdatingHexProgrammatically = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collage_studio);

        SharedPreferences appPrefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        isDarkTheme = appPrefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        applyModernTheme();

        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        FrameLayout canvasContainer = findViewById(R.id.canvasContainer);

        canvasView = new CollageCanvasView(this);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        canvasContainer.addView(canvasView, layoutParams);

        btnCenterAddPhotos = findViewById(R.id.btnCenterAddPhotos);

        setupLaunchers();
        setupButtonsAndSliders(drawerLayout);
        setupColorPickers();

        saveStateToHistory();
        updateUIVisibility();
    }

    private void applyModernTheme() {
        int mainBg = isDarkTheme ? Color.parseColor("#000000") : Color.parseColor("#F2F2F7");
        int topBarBg = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.WHITE;
        int drawerBg = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#FFFFFF");
        int loadingCardBg = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;

        textColor = isDarkTheme ? Color.WHITE : Color.BLACK;
        themeButtonColor = Color.parseColor("#4049EA");

        findViewById(R.id.mainContentRoot).setBackgroundColor(mainBg);
        findViewById(R.id.topBar).setBackgroundColor(topBarBg);

        View leftDrawer = findViewById(R.id.leftDrawer);
        if (leftDrawer != null) leftDrawer.setBackgroundColor(drawerBg);
        View rightDrawer = findViewById(R.id.rightDrawer);
        if (rightDrawer != null) rightDrawer.setBackgroundColor(drawerBg);

        loadingOverlay = findViewById(R.id.loadingOverlay);
        LinearLayout loadingCard = findViewById(R.id.loadingCard);
        if (loadingCard != null) {
            GradientDrawable loadingBgDrawable = new GradientDrawable();
            loadingBgDrawable.setColor(loadingCardBg);
            loadingBgDrawable.setCornerRadius(50f);
            loadingCard.setBackground(loadingBgDrawable);
        }

        TextView tvTopTitle = findViewById(R.id.tvTopTitle);
        if (tvTopTitle != null) tvTopTitle.setTextColor(textColor);
        TextView tvLoadingText = findViewById(R.id.tvLoadingText);
        if (tvLoadingText != null) tvLoadingText.setTextColor(textColor);

        View leftContent = findViewById(R.id.leftDrawerContent);
        if (leftContent != null) recolorText(leftContent, textColor);

        View rightContent = findViewById(R.id.rightDrawerContent);
        if (rightContent != null) recolorText(rightContent, textColor);

        setupRoundButton(findViewById(R.id.btnOpenSettings), themeButtonColor);
        setupRoundButton(findViewById(R.id.btnOpenTools), themeButtonColor);
        setupRoundButton(findViewById(R.id.btnCenterAddPhotos), themeButtonColor);
        setupRoundButton(findViewById(R.id.btnDrawerAddPhotos), themeButtonColor);
        setupRoundButton(findViewById(R.id.btnExport), themeButtonColor);

        btnRearrange = findViewById(R.id.btnRearrange);
        btnToggleAdjust = findViewById(R.id.btnToggleAdjust);
        btnToggleZoom = findViewById(R.id.btnToggleZoom);
        btnToggleDelete = findViewById(R.id.btnToggleDelete);

        btnToggleRotate = findViewById(R.id.btnToggleRotate);
        btnToggleMirrorH = findViewById(R.id.btnToggleMirrorH);
        btnToggleMirrorV = findViewById(R.id.btnToggleMirrorV);

        setupRoundButton(btnRearrange, themeButtonColor);
        setupRoundButton(btnToggleAdjust, themeButtonColor);
        setupRoundButton(btnToggleZoom, themeButtonColor);
        setupRoundButton(btnToggleDelete, themeButtonColor);

        setupRoundButton(btnToggleRotate, themeButtonColor);
        setupRoundButton(btnToggleMirrorH, themeButtonColor);
        setupRoundButton(btnToggleMirrorV, themeButtonColor);

        setupRoundButton(findViewById(R.id.btnBgTexture), themeButtonColor);
        setupRoundButton(findViewById(R.id.btnUndo), themeButtonColor);
        setupRoundButton(findViewById(R.id.btnRedo), themeButtonColor);

        btnToggleStroke = findViewById(R.id.btnToggleStroke);
        if (btnToggleStroke != null) setupRoundButton(btnToggleStroke, Color.parseColor("#4CAF50"));

        btnToggleShadow = findViewById(R.id.btnToggleShadow);
        if (btnToggleShadow != null) setupRoundButton(btnToggleShadow, themeButtonColor);

        int[] layoutIds = {R.id.btnLayoutGrid, R.id.btnLayoutRow, R.id.btnLayoutFree, R.id.btnLayoutFit};
        for (int id : layoutIds) setupRoundButton(findViewById(id), themeButtonColor);

        int[] ratioIds = {R.id.btnRatio11, R.id.btnRatio169, R.id.btnRatio43, R.id.btnRatio916, R.id.btnRatio32, R.id.btnRatio23};
        for (int id : ratioIds) setupRoundButton(findViewById(id), themeButtonColor);
    }

    private void recolorText(View view, int color) {
        if (view == null) return;
        if (view instanceof TextView && !(view instanceof Button) && !(view instanceof EditText)) {
            ((TextView) view).setTextColor(color);
        } else if (view instanceof android.view.ViewGroup) {
            for (int i = 0; i < ((android.view.ViewGroup) view).getChildCount(); i++) {
                recolorText(((android.view.ViewGroup) view).getChildAt(i), color);
            }
        }
    }

    private void setupRoundButton(View view, int bgColor) {
        if (!(view instanceof Button)) return;
        Button btn = (Button) view;
        btn.setBackgroundTintList(null);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(100f);
        shape.setColor(bgColor);
        btn.setBackground(shape);
        btn.setTextColor(Color.WHITE);
    }

    private void setInteractionMode(int mode) {
        isRearrangeMode = (mode == 1);
        isAdjustMode = (mode == 2);
        isZoomMode = (mode == 3);
        isDeleteMode = (mode == 4);
        isRotateMode = (mode == 5);
        isMirrorHMode = (mode == 6);
        isMirrorVMode = (mode == 7);

        if (btnRearrange != null) {
            btnRearrange.setText(isRearrangeMode ? "Rearrange: ON" : "Rearrange: OFF");
            setupRoundButton(btnRearrange, isRearrangeMode ? Color.parseColor("#4CAF50") : themeButtonColor);
        }
        if (btnToggleAdjust != null) {
            btnToggleAdjust.setText(isAdjustMode ? "Adjust: ON" : "Adjust: OFF");
            setupRoundButton(btnToggleAdjust, isAdjustMode ? Color.parseColor("#4CAF50") : themeButtonColor);
        }
        if (btnToggleZoom != null) {
            btnToggleZoom.setText(isZoomMode ? "Zoom: ON" : "Zoom: OFF");
            setupRoundButton(btnToggleZoom, isZoomMode ? Color.parseColor("#4CAF50") : themeButtonColor);
        }
        if (btnToggleDelete != null) {
            btnToggleDelete.setText(isDeleteMode ? "Delete: ON (Tap to Remove)" : "Delete: OFF");
            setupRoundButton(btnToggleDelete, isDeleteMode ? Color.parseColor("#F44336") : themeButtonColor);
        }
        if (btnToggleRotate != null) {
            btnToggleRotate.setText(isRotateMode ? "Rotate: ON" : "Rotate: OFF");
            setupRoundButton(btnToggleRotate, isRotateMode ? Color.parseColor("#4CAF50") : themeButtonColor);
        }
        if (btnToggleMirrorH != null) {
            btnToggleMirrorH.setText(isMirrorHMode ? "Mirror ↔: ON" : "Mirror ↔: OFF");
            setupRoundButton(btnToggleMirrorH, isMirrorHMode ? Color.parseColor("#4CAF50") : themeButtonColor);
        }
        if (btnToggleMirrorV != null) {
            btnToggleMirrorV.setText(isMirrorVMode ? "Mirror ↕: ON" : "Mirror ↕: OFF");
            setupRoundButton(btnToggleMirrorV, isMirrorVMode ? Color.parseColor("#4CAF50") : themeButtonColor);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void preventDrawerIntercept(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
    }

    @SuppressLint("SetTextI18n")
    private void setupButtonsAndSliders(DrawerLayout drawerLayout) {
        findViewById(R.id.btnOpenSettings).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        findViewById(R.id.btnOpenTools).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.END));

        View.OnClickListener addPhotosListener = v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                imagePickerLauncher.launch(intent);
            } catch (Exception e) {
                Toast.makeText(this, "No suitable app found to pick images.", Toast.LENGTH_SHORT).show();
            }
        };

        btnCenterAddPhotos.setOnClickListener(addPhotosListener);
        findViewById(R.id.btnDrawerAddPhotos).setOnClickListener(addPhotosListener);
        findViewById(R.id.btnExport).setOnClickListener(this::showExportMenu);

        findViewById(R.id.btnBgTexture).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                texturePickerLauncher.launch(intent);
            } catch (Exception e) {
                Toast.makeText(this, "No app found.", Toast.LENGTH_SHORT).show();
            }
        });

        if (btnRearrange != null) btnRearrange.setOnClickListener(v -> setInteractionMode(isRearrangeMode ? 0 : 1));
        if (btnToggleAdjust != null) btnToggleAdjust.setOnClickListener(v -> setInteractionMode(isAdjustMode ? 0 : 2));
        if (btnToggleZoom != null) btnToggleZoom.setOnClickListener(v -> setInteractionMode(isZoomMode ? 0 : 3));
        if (btnToggleDelete != null) btnToggleDelete.setOnClickListener(v -> setInteractionMode(isDeleteMode ? 0 : 4));

        if (btnToggleRotate != null) btnToggleRotate.setOnClickListener(v -> setInteractionMode(isRotateMode ? 0 : 5));
        if (btnToggleMirrorH != null) btnToggleMirrorH.setOnClickListener(v -> setInteractionMode(isMirrorHMode ? 0 : 6));
        if (btnToggleMirrorV != null) btnToggleMirrorV.setOnClickListener(v -> setInteractionMode(isMirrorVMode ? 0 : 7));

        if (btnToggleStroke != null) {
            btnToggleStroke.setOnClickListener(v -> {
                isStrokeEnabled = !isStrokeEnabled;
                btnToggleStroke.setText(isStrokeEnabled ? "Stroke: ON" : "Stroke: OFF");
                setupRoundButton(btnToggleStroke, isStrokeEnabled ? Color.parseColor("#4CAF50") : themeButtonColor);
                canvasView.invalidate();
                saveStateToHistory();
            });
        }

        if (btnToggleShadow != null) {
            btnToggleShadow.setOnClickListener(v -> {
                isShadowEnabled = !isShadowEnabled;
                btnToggleShadow.setText(isShadowEnabled ? "Shadow: ON" : "Shadow: OFF");
                setupRoundButton(btnToggleShadow, isShadowEnabled ? Color.parseColor("#4CAF50") : themeButtonColor);
                canvasView.invalidate();
                saveStateToHistory();
            });
        }

        findViewById(R.id.btnRatio11).setOnClickListener(v -> setRatio(1.0f));
        findViewById(R.id.btnRatio169).setOnClickListener(v -> setRatio(16f / 9f));
        findViewById(R.id.btnRatio43).setOnClickListener(v -> setRatio(4f / 3f));
        findViewById(R.id.btnRatio916).setOnClickListener(v -> setRatio(9f / 16f));
        findViewById(R.id.btnRatio32).setOnClickListener(v -> setRatio(3f / 2f));
        findViewById(R.id.btnRatio23).setOnClickListener(v -> setRatio(2f / 3f));

        findViewById(R.id.btnLayoutGrid).setOnClickListener(v -> { currentLayoutMode = 0; applyUpdateAsync(true, true); });
        findViewById(R.id.btnLayoutRow).setOnClickListener(v -> { currentLayoutMode = 1; applyUpdateAsync(true, true); });
        findViewById(R.id.btnLayoutFit).setOnClickListener(v -> { currentLayoutMode = 3; applyUpdateAsync(true, true); });

        findViewById(R.id.btnLayoutFree).setOnClickListener(v -> {
            Dialog dialog = new Dialog(this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(60, 60, 60, 60);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(isDarkTheme ? Color.parseColor("#2C2C2E") : Color.parseColor("#FFFFFF"));
            bg.setCornerRadius(50f);
            if (isDarkTheme) bg.setStroke(2, Color.parseColor("#444444"));
            else bg.setStroke(2, Color.parseColor("#DDDDDD"));
            layout.setBackground(bg);

            TextView title = new TextView(this);
            title.setText("Free Mode");
            title.setTextSize(20f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
            title.setPadding(0, 0, 0, 30);

            TextView msg = new TextView(this);
            msg.setText("YOU ARE GOING TO ENTER FREE MODE.\nTHE IMAGES MAY NOT BE IN ORDER AFTER THIS.");
            msg.setTextSize(14f);
            msg.setTextColor(isDarkTheme ? Color.parseColor("#DDDDDD") : Color.parseColor("#333333"));
            msg.setPadding(0, 0, 0, 50);

            LinearLayout buttonLayout = new LinearLayout(this);
            buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
            buttonLayout.setGravity(Gravity.END);

            Button btnCancel = new Button(this);
            btnCancel.setText("Cancel");
            btnCancel.setBackgroundColor(Color.TRANSPARENT);
            btnCancel.setTextColor(Color.GRAY);
            btnCancel.setOnClickListener(v2 -> dialog.dismiss());

            Button btnProceed = new Button(this);
            btnProceed.setText("Proceed");
            btnProceed.setAllCaps(false);
            setupRoundButton(btnProceed, themeButtonColor);
            btnProceed.setOnClickListener(v2 -> {
                dialog.dismiss();
                currentLayoutMode = 2;
                applyUpdateAsync(true, true);
            });

            buttonLayout.addView(btnCancel);
            LinearLayout.LayoutParams proceedParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            proceedParams.setMargins(20, 0, 0, 0);
            buttonLayout.addView(btnProceed, proceedParams);

            layout.addView(title);
            layout.addView(msg);
            layout.addView(buttonLayout);

            dialog.setContentView(layout);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                int width = (int)(getResources().getDisplayMetrics().widthPixels * 0.85);
                dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            dialog.show();
        });

        SeekBar seekBorderWidth = findViewById(R.id.seekBorderWidth);
        SeekBar seekStrokeWidth = findViewById(R.id.seekStrokeWidth);
        SeekBar seekCornerRadius = findViewById(R.id.seekCornerRadius);
        SeekBar seekBlender = findViewById(R.id.seekBlender);
        SeekBar seekShadowOffsetX = findViewById(R.id.seekShadowOffsetX);
        SeekBar seekShadowOffsetY = findViewById(R.id.seekShadowOffsetY);

        preventDrawerIntercept(seekBorderWidth);
        preventDrawerIntercept(seekStrokeWidth);
        preventDrawerIntercept(seekCornerRadius);
        preventDrawerIntercept(seekBlender);
        preventDrawerIntercept(seekShadowOffsetX);
        preventDrawerIntercept(seekShadowOffsetY);

        if (seekBorderWidth != null) {
            seekBorderWidth.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int p, boolean b) { currentBorderWidth = p; canvasView.invalidate(); }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { saveStateToHistory(); }
            });
        }
        if (seekStrokeWidth != null) {
            seekStrokeWidth.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int p, boolean b) { currentStrokeWidth = p; canvasView.invalidate(); }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { saveStateToHistory(); }
            });
        }
        if (seekCornerRadius != null) {
            seekCornerRadius.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int p, boolean b) { currentCornerRadius = p; canvasView.invalidate(); }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { saveStateToHistory(); }
            });
        }
        if (seekBlender != null) {
            seekBlender.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int p, boolean b) { currentBlender = p; canvasView.invalidate(); }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { saveStateToHistory(); }
            });
        }
        if (seekShadowOffsetX != null) {
            seekShadowOffsetX.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int p, boolean b) { currentShadowOffsetX = p - 100f; canvasView.invalidate(); }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { saveStateToHistory(); }
            });
        }
        if (seekShadowOffsetY != null) {
            seekShadowOffsetY.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int p, boolean b) { currentShadowOffsetY = p - 100f; canvasView.invalidate(); }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { saveStateToHistory(); }
            });
        }

        findViewById(R.id.btnUndo).setOnClickListener(v -> undo());
        findViewById(R.id.btnRedo).setOnClickListener(v -> redo());
    }

    private void showExportMenu(View anchor) {
        if (currentUris.isEmpty()) {
            Toast.makeText(this, "Add images first!", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout popupLayout = new LinearLayout(this);
        popupLayout.setOrientation(LinearLayout.VERTICAL);
        popupLayout.setPadding(40, 40, 40, 40);
        popupLayout.setMinimumWidth(450);

        GradientDrawable popupBg = new GradientDrawable();
        popupBg.setColor(isDarkTheme ? Color.parseColor("#2C2C2E") : Color.parseColor("#FFFFFF"));
        popupBg.setCornerRadius(50f);
        popupBg.setStroke(2, isDarkTheme ? Color.parseColor("#444444") : Color.parseColor("#DDDDDD"));
        popupLayout.setBackground(popupBg);

        Button btnPng = new Button(this);
        btnPng.setText("1. HIGH PNG");
        btnPng.setAllCaps(false);
        btnPng.setTextSize(16f);
        setupRoundButton(btnPng, themeButtonColor);
        LinearLayout.LayoutParams params1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130);
        params1.setMargins(0, 0, 0, 24);
        btnPng.setLayoutParams(params1);

        Button btnJpg = new Button(this);
        btnJpg.setText("2. HIGH JPG");
        btnJpg.setAllCaps(false);
        btnJpg.setTextSize(16f);
        setupRoundButton(btnJpg, themeButtonColor);
        LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130);
        btnJpg.setLayoutParams(params2);

        popupLayout.addView(btnPng);
        popupLayout.addView(btnJpg);

        PopupWindow popupWindow = new PopupWindow(popupLayout,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popupWindow.setElevation(24f);

        btnPng.setOnClickListener(v -> {
            popupWindow.dismiss();
            exportHighQualityCollage(Bitmap.CompressFormat.PNG, "png");
        });

        btnJpg.setOnClickListener(v -> {
            popupWindow.dismiss();
            exportHighQualityCollage(Bitmap.CompressFormat.JPEG, "jpg");
        });

        popupWindow.showAsDropDown(anchor, -200, 20);
    }

    private void updateUIVisibility() {
        btnCenterAddPhotos.setVisibility(currentUris.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setupLaunchers() {
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count && currentUris.size() < 50; i++) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored){}
                        currentUris.add(uri);
                        imageScales.add(1.0f);
                        imagePanX.add(0f);
                        imagePanY.add(0f);
                        imageRotations.add(0f);
                        imageFlipX.add(false);
                        imageFlipY.add(false);
                    }
                } else if (data.getData() != null && currentUris.size() < 50) {
                    Uri uri = data.getData();
                    try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored){}
                    currentUris.add(uri);
                    imageScales.add(1.0f);
                    imagePanX.add(0f);
                    imagePanY.add(0f);
                    imageRotations.add(0f);
                    imageFlipX.add(false);
                    imageFlipY.add(false);
                }
                applyUpdateAsync(true, true);
            }
        });

        texturePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                currentBgTexture = result.getData().getData();
                try { getContentResolver().takePersistableUriPermission(currentBgTexture, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored){}
                applyUpdateAsync(true, false);
            }
        });
    }

    private void setupColorPickers() {
        etBgHexCode = findViewById(R.id.etBgHexCode);
        etStrokeHexCode = findViewById(R.id.etStrokeHexCode);
        etShadowHexCode = findViewById(R.id.etShadowHexCode);

        FrameLayout bgContainer = findViewById(R.id.colorPickerContainer);
        ColorPickerView bgColorPicker = new ColorPickerView(this, new ColorPickerView.OnColorPickedListener() {
            @Override public void onColorPicked(int color) {
                currentBgColor = color;
                currentBgTexture = null;
                isUpdatingHexProgrammatically = true;
                if (etBgHexCode != null) etBgHexCode.setText(String.format("#%06X", (0xFFFFFF & color)));
                isUpdatingHexProgrammatically = false;
                canvasView.invalidate();
            }
            @Override public void onColorPickEnded() { saveStateToHistory(); }
        });
        bgContainer.addView(bgColorPicker);

        FrameLayout strokeContainer = findViewById(R.id.strokeColorContainer);
        if (strokeContainer != null) {
            ColorPickerView strokeColorPicker = new ColorPickerView(this, new ColorPickerView.OnColorPickedListener() {
                @Override public void onColorPicked(int color) {
                    currentStrokeColor = color;
                    isUpdatingHexProgrammatically = true;
                    if (etStrokeHexCode != null) etStrokeHexCode.setText(String.format("#%06X", (0xFFFFFF & color)));
                    isUpdatingHexProgrammatically = false;
                    canvasView.invalidate();
                }
                @Override public void onColorPickEnded() { saveStateToHistory(); }
            });
            strokeContainer.addView(strokeColorPicker);
        }

        FrameLayout shadowContainer = findViewById(R.id.shadowColorContainer);
        if (shadowContainer != null) {
            ColorPickerView shadowColorPicker = new ColorPickerView(this, new ColorPickerView.OnColorPickedListener() {
                @Override public void onColorPicked(int color) {
                    currentShadowColor = color;
                    isUpdatingHexProgrammatically = true;
                    if (etShadowHexCode != null) etShadowHexCode.setText(String.format("#%06X", (0xFFFFFF & color)));
                    isUpdatingHexProgrammatically = false;
                    canvasView.invalidate();
                }
                @Override public void onColorPickEnded() { saveStateToHistory(); }
            });
            shadowContainer.addView(shadowColorPicker);
        }

        setupHexInput(etBgHexCode, 0);
        setupHexInput(etStrokeHexCode, 1);
        setupHexInput(etShadowHexCode, 2);
    }

    private void setupHexInput(EditText et, int type) {
        if (et == null) return;
        et.setTextColor(textColor);
        et.setHintTextColor(isDarkTheme ? Color.GRAY : Color.LTGRAY);
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (isUpdatingHexProgrammatically) return;
                try {
                    if (s.length() == 7 && s.toString().startsWith("#")) {
                        int color = Color.parseColor(s.toString());
                        if (type == 0) { currentBgColor = color; currentBgTexture = null; }
                        else if (type == 1) { currentStrokeColor = color; }
                        else if (type == 2) { currentShadowColor = color; }
                        canvasView.invalidate();
                        saveStateToHistory();
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    private void setRatio(float ratio) {
        currentRatio = ratio;
        applyUpdateAsync(true, true);
    }

    private void applyUpdateAsync(boolean shouldSaveHistory, boolean forceRecalculateBounds) {
        if (shouldSaveHistory) {
            Toast.makeText(this, "Loading images...", Toast.LENGTH_SHORT).show();
        }
        canvasView.loadThumbnailsAsync(currentUris, () -> {
            if (forceRecalculateBounds) canvasView.forceRecalculateGrid();
            else canvasView.updateLayoutMath();

            canvasView.requestLayout();
            canvasView.invalidate();
            updateUIVisibility();
            if (shouldSaveHistory) saveStateToHistory();
        });
    }

    // ==========================================
    // HISTORY ENGINE (UNDO/REDO)
    // ==========================================
    private static class CollageState {
        List<Uri> uris; float ratio, borderWidth, strokeWidth, cornerRadius, blender;
        int bgColor, strokeColor, layoutMode; Uri bgTexture; boolean isStrokeEnabled;
        boolean isShadowEnabled; float shadowOffsetX, shadowOffsetY; int shadowColor;
        List<RectF> boundsPercentages;
        List<Float> scales, panXs, panYs, rotations;
        List<Boolean> flipXs, flipYs;

        CollageState(List<Uri> u, float r, float bw, float sw, float cr, float bl,
                     int bg, int sc, int lm, Uri tex, boolean se,
                     boolean she, float sox, float soy, int shc, List<RectF> bounds,
                     List<Float> s, List<Float> px, List<Float> py,
                     List<Float> rot, List<Boolean> fx, List<Boolean> fy) {
            uris = new ArrayList<>(u); ratio = r; borderWidth = bw; strokeWidth = sw; cornerRadius = cr; blender = bl;
            bgColor = bg; strokeColor = sc; layoutMode = lm; bgTexture = tex; isStrokeEnabled = se;
            isShadowEnabled = she; shadowOffsetX = sox; shadowOffsetY = soy; shadowColor = shc;

            boundsPercentages = new ArrayList<>();
            for (RectF b : bounds) boundsPercentages.add(new RectF(b));

            scales = new ArrayList<>(s);
            panXs = new ArrayList<>(px);
            panYs = new ArrayList<>(py);
            rotations = new ArrayList<>(rot);
            flipXs = new ArrayList<>(fx);
            flipYs = new ArrayList<>(fy);
        }
    }

    private void saveStateToHistory() {
        redoStack.clear();
        undoStack.push(new CollageState(currentUris, currentRatio, currentBorderWidth, currentStrokeWidth, currentCornerRadius,
                currentBlender, currentBgColor, currentStrokeColor, currentLayoutMode, currentBgTexture,
                isStrokeEnabled, isShadowEnabled, currentShadowOffsetX, currentShadowOffsetY, currentShadowColor,
                canvasView.getBoundsAsPercentages(), imageScales, imagePanX, imagePanY, imageRotations, imageFlipX, imageFlipY));
    }

    private void undo() {
        if (undoStack.size() > 1) {
            redoStack.push(undoStack.pop());
            restoreState(undoStack.peek());
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            CollageState state = redoStack.pop();
            undoStack.push(state);
            restoreState(state);
        }
    }

    private void restoreState(CollageState state) {
        currentUris.clear();
        currentUris.addAll(state.uris);
        currentRatio = state.ratio;
        currentBorderWidth = state.borderWidth;
        currentStrokeWidth = state.strokeWidth;
        currentCornerRadius = state.cornerRadius;
        currentBlender = state.blender;
        currentBgColor = state.bgColor;
        currentStrokeColor = state.strokeColor;
        currentLayoutMode = state.layoutMode;
        currentBgTexture = state.bgTexture;

        isStrokeEnabled = state.isStrokeEnabled;
        if (btnToggleStroke != null) {
            btnToggleStroke.setText(isStrokeEnabled ? "Stroke: ON" : "Stroke: OFF");
            setupRoundButton(btnToggleStroke, isStrokeEnabled ? Color.parseColor("#4CAF50") : themeButtonColor);
        }

        isShadowEnabled = state.isShadowEnabled;
        currentShadowOffsetX = state.shadowOffsetX;
        currentShadowOffsetY = state.shadowOffsetY;
        currentShadowColor = state.shadowColor;
        if (btnToggleShadow != null) {
            btnToggleShadow.setText(isShadowEnabled ? "Shadow: ON" : "Shadow: OFF");
            setupRoundButton(btnToggleShadow, isShadowEnabled ? Color.parseColor("#4CAF50") : themeButtonColor);
        }

        imageScales.clear(); imageScales.addAll(state.scales);
        imagePanX.clear(); imagePanX.addAll(state.panXs);
        imagePanY.clear(); imagePanY.addAll(state.panYs);
        imageRotations.clear(); imageRotations.addAll(state.rotations);
        imageFlipX.clear(); imageFlipX.addAll(state.flipXs);
        imageFlipY.clear(); imageFlipY.addAll(state.flipYs);

        ((SeekBar) findViewById(R.id.seekBorderWidth)).setProgress((int) currentBorderWidth);
        ((SeekBar) findViewById(R.id.seekStrokeWidth)).setProgress((int) currentStrokeWidth);
        ((SeekBar) findViewById(R.id.seekCornerRadius)).setProgress((int) currentCornerRadius);
        ((SeekBar) findViewById(R.id.seekBlender)).setProgress((int) currentBlender);
        ((SeekBar) findViewById(R.id.seekShadowOffsetX)).setProgress((int) currentShadowOffsetX + 100);
        ((SeekBar) findViewById(R.id.seekShadowOffsetY)).setProgress((int) currentShadowOffsetY + 100);

        isUpdatingHexProgrammatically = true;
        if (etBgHexCode != null) etBgHexCode.setText(String.format("#%06X", (0xFFFFFF & currentBgColor)));
        if (etStrokeHexCode != null) etStrokeHexCode.setText(String.format("#%06X", (0xFFFFFF & currentStrokeColor)));
        if (etShadowHexCode != null) etShadowHexCode.setText(String.format("#%06X", (0xFFFFFF & currentShadowColor)));
        isUpdatingHexProgrammatically = false;

        canvasView.restoreBoundsFromPercentages(state.boundsPercentages);
        applyUpdateAsync(false, false);
    }

    // ==========================================
    // HIGH-QUALITY EXPORT ENGINE
    // ==========================================
    private void exportHighQualityCollage(Bitmap.CompressFormat format, String extension) {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                int targetWidth = 4000;
                int targetHeight = (int) (targetWidth / currentRatio);

                Bitmap finalBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                Canvas exportCanvas = new Canvas(finalBitmap);

                List<RectF> exportBounds = new ArrayList<>();
                float scaleX = (float) targetWidth / canvasView.getWidth();
                float scaleY = (float) targetHeight / canvasView.getHeight();
                for (RectF b : canvasView.layoutBounds) {
                    exportBounds.add(new RectF(b.left * scaleX, b.top * scaleY, b.right * scaleX, b.bottom * scaleY));
                }

                if (currentBgTexture != null) {
                    Bitmap bgBmp = decodeSampledBitmapFromUri(currentBgTexture, targetWidth, targetHeight);
                    if (bgBmp != null) {
                        exportCanvas.drawBitmap(bgBmp, null, new RectF(0, 0, targetWidth, targetHeight), null);
                        bgBmp.recycle();
                    }
                } else {
                    exportCanvas.drawColor(currentBgColor);
                }

                Paint imgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                imgPaint.setFilterBitmap(true);
                Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                maskPaint.setColor(Color.WHITE);
                Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setColor(currentStrokeColor);
                Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                shadowPaint.setColor(currentShadowColor);

                Matrix m = new Matrix();
                float scaleFactor = Math.max(scaleX, scaleY);
                float scaledBorder = currentBorderWidth * scaleFactor;
                float scaledStroke = currentStrokeWidth * scaleFactor;
                float scaledCorner = currentCornerRadius * scaleFactor;
                float scaledBlender = currentBlender * scaleFactor;
                float scaledShadowX = currentShadowOffsetX * scaleFactor;
                float scaledShadowY = currentShadowOffsetY * scaleFactor;

                strokePaint.setStrokeWidth(scaledStroke);

                for (int i = 0; i < currentUris.size(); i++) {
                    RectF cell = exportBounds.get(i);
                    float safeBorder = Math.min(scaledBorder, Math.min(cell.width(), cell.height()) / 2.2f);
                    RectF imgRect = new RectF(cell.left + safeBorder, cell.top + safeBorder, cell.right - safeBorder, cell.bottom - safeBorder);

                    Bitmap hrBmp = decodeSampledBitmapFromUri(currentUris.get(i), (int) imgRect.width(), (int) imgRect.height());
                    if (hrBmp == null) continue;

                    if (currentLayoutMode == 3) {
                        float imgRatio = (float) hrBmp.getWidth() / hrBmp.getHeight();
                        float rectRatio = imgRect.width() / imgRect.height();
                        float newW = imgRect.width();
                        float newH = imgRect.height();
                        if (imgRatio > rectRatio) {
                            newH = newW / imgRatio;
                        } else {
                            newW = newH * imgRatio;
                        }
                        float dx = (imgRect.width() - newW) / 2f;
                        float dy = (imgRect.height() - newH) / 2f;
                        imgRect.set(imgRect.left + dx, imgRect.top + dy, imgRect.right - dx, imgRect.bottom - dy);
                    }

                    float userScale = i < imageScales.size() ? imageScales.get(i) : 1.0f;
                    float userPanX = (i < imagePanX.size() ? imagePanX.get(i) : 0f) * scaleX;
                    float userPanY = (i < imagePanY.size() ? imagePanY.get(i) : 0f) * scaleY;
                    float userRot = i < imageRotations.size() ? imageRotations.get(i) : 0f;
                    boolean userFlipX = i < imageFlipX.size() ? imageFlipX.get(i) : false;
                    boolean userFlipY = i < imageFlipY.size() ? imageFlipY.get(i) : false;

                    m.reset();
                    float baseScale = Math.max(imgRect.width() / hrBmp.getWidth(), imgRect.height() / hrBmp.getHeight());
                    float finalScale = baseScale * userScale;

                    m.postScale(finalScale, finalScale);
                    m.postTranslate(imgRect.left + (imgRect.width() - hrBmp.getWidth() * finalScale) / 2f + userPanX,
                            imgRect.top + (imgRect.height() - hrBmp.getHeight() * finalScale) / 2f + userPanY);

                    // Transform (Flip & Rotate) perfectly around its center
                    if (userFlipX || userFlipY) {
                        m.postScale(userFlipX ? -1 : 1, userFlipY ? -1 : 1, imgRect.centerX(), imgRect.centerY());
                    }
                    if (userRot != 0f) {
                        m.postRotate(userRot, imgRect.centerX(), imgRect.centerY());
                    }

                    // Draw Shadow Layer FIRST
                    if (isShadowEnabled && currentShadowColor != Color.TRANSPARENT) {
                        int shadowSave = exportCanvas.save();
                        exportCanvas.translate(scaledShadowX, scaledShadowY);
                        shadowPaint.setMaskFilter(new BlurMaskFilter(15f * scaleFactor, BlurMaskFilter.Blur.NORMAL));
                        exportCanvas.drawRoundRect(imgRect, scaledCorner, scaledCorner, shadowPaint);
                        exportCanvas.restoreToCount(shadowSave);
                    }

                    int saveCount = exportCanvas.saveLayer(imgRect, null);

                    if (scaledBlender > 0) {
                        maskPaint.setMaskFilter(new BlurMaskFilter(scaledBlender, BlurMaskFilter.Blur.NORMAL));
                    } else {
                        maskPaint.setMaskFilter(null);
                    }
                    exportCanvas.drawRoundRect(imgRect, scaledCorner, scaledCorner, maskPaint);

                    imgPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                    exportCanvas.drawBitmap(hrBmp, m, imgPaint);
                    imgPaint.setXfermode(null);

                    hrBmp.recycle();
                    exportCanvas.restoreToCount(saveCount);

                    // Draw Stroke ON TOP
                    if (isStrokeEnabled && currentStrokeColor != Color.TRANSPARENT && currentStrokeWidth > 0) {
                        exportCanvas.drawRoundRect(imgRect, scaledCorner, scaledCorner, strokePaint);
                    }
                }

                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "CollageStudio_" + System.currentTimeMillis() + "." + extension);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/" + (extension.equals("jpg") ? "jpeg" : "png"));
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OWN's Collage Studio");

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream out = null;
                    try {
                        out = getContentResolver().openOutputStream(uri);
                        if (out != null) {
                            finalBitmap.compress(format, 100, out);
                        }
                    } finally {
                        if (out != null) {
                            try { out.close(); } catch (Exception ignored) {}
                        }
                    }
                }
                finalBitmap.recycle();

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(CollageStudioActivity.this, "Saved Successfully to Pictures/OWN's Collage Studio!", Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(CollageStudioActivity.this, "Error Saving Collage", Toast.LENGTH_SHORT).show();
                });
                Log.e("CollageStudio", "Export Error", e);
            }
        }).start();
    }

    private Bitmap decodeSampledBitmapFromUri(Uri uri, int reqWidth, int reqHeight) {
        InputStream is = null;
        try {
            is = getContentResolver().openInputStream(uri);
            if (is == null) return null;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            is.close();

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;

            is = getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(is, null, options);
        } catch (Exception e) {
            Log.e("CollageStudio", "Decode Error", e);
            return null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ignored) {}
            }
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    // ==========================================
    // CUSTOM CANVAS ENGINE VIEW
    // ==========================================
    private class CollageCanvasView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Matrix mMatrix = new Matrix();
        private final RectF mImgRect = new RectF();
        private final RectF mFloatRect = new RectF();
        private final RectF mBgRect = new RectF();

        private final List<Bitmap> thumbnails = new ArrayList<>();
        private Bitmap bgTextureThumb = null;

        public final List<RectF> layoutBounds = new ArrayList<>();
        private final List<RectF> percentageBounds = new ArrayList<>();

        private int draggedIndex = -1;
        private float dragTouchX, dragTouchY;

        private Float draggedLineX = null;
        private Float draggedLineY = null;
        private final List<Integer> cellsUpdatingLeft = new ArrayList<>();
        private final List<Integer> cellsUpdatingRight = new ArrayList<>();
        private final List<Integer> cellsUpdatingTop = new ArrayList<>();
        private final List<Integer> cellsUpdatingBottom = new ArrayList<>();

        private float lastSpan = 1f;
        private float lastFocusX = 0f;
        private float lastFocusY = 0f;

        public CollageCanvasView(Context context) {
            super(context);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            mMaskPaint.setColor(Color.WHITE);
            mStrokePaint.setStyle(Paint.Style.STROKE);
            mPaint.setFilterBitmap(true);
        }

        public void loadThumbnailsAsync(List<Uri> uris, Runnable onComplete) {
            new Thread(() -> {
                List<Bitmap> tempThumbs = new ArrayList<>();
                for (Uri uri : uris) {
                    Bitmap bmp = decodeSampledBitmapFromUri(uri, 300, 300);
                    if (bmp != null) tempThumbs.add(bmp);
                }

                Bitmap tempBg = null;
                if (currentBgTexture != null) {
                    tempBg = decodeSampledBitmapFromUri(currentBgTexture, 800, 800);
                }

                final Bitmap finalBg = tempBg;
                new Handler(Looper.getMainLooper()).post(() -> {
                    for (Bitmap b : thumbnails) if (b != null && !b.isRecycled()) b.recycle();
                    thumbnails.clear();
                    thumbnails.addAll(tempThumbs);

                    if (bgTextureThumb != null && !bgTextureThumb.isRecycled()) bgTextureThumb.recycle();
                    bgTextureThumb = finalBg;

                    if (onComplete != null) onComplete.run();
                });
            }).start();
        }

        public List<RectF> getBoundsAsPercentages() {
            List<RectF> percs = new ArrayList<>();
            float w = getWidth() > 0 ? getWidth() : 1000;
            float h = getHeight() > 0 ? getHeight() : 1000;
            for (RectF r : layoutBounds) {
                percs.add(new RectF(r.left / w, r.top / h, r.right / w, r.bottom / h));
            }
            return percs;
        }

        public void restoreBoundsFromPercentages(List<RectF> percs) {
            percentageBounds.clear();
            percentageBounds.addAll(percs);
            layoutBounds.clear();
            float w = getWidth() > 0 ? getWidth() : 1000;
            float h = getHeight() > 0 ? getHeight() : 1000;
            for (RectF p : percentageBounds) {
                layoutBounds.add(new RectF(p.left * w, p.top * h, p.right * w, p.bottom * h));
            }
        }

        public void forceRecalculateGrid() {
            percentageBounds.clear();
            updateLayoutMath();
        }

        public void updateLayoutMath() {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0 || currentUris.isEmpty()) return;

            if (!percentageBounds.isEmpty() && percentageBounds.size() == currentUris.size()) {
                layoutBounds.clear();
                for (RectF p : percentageBounds) {
                    layoutBounds.add(new RectF(p.left * w, p.top * h, p.right * w, p.bottom * h));
                }
                return;
            }

            layoutBounds.clear();
            if (currentLayoutMode == 2) {
                for (int i = 0; i < currentUris.size(); i++) {
                    float rectW = w * 0.4f;
                    float rectH = h * 0.4f;
                    float l = (float) (Math.random() * (w - rectW));
                    float t = (float) (Math.random() * (h - rectH));
                    layoutBounds.add(new RectF(l, t, l + rectW, t + rectH));
                }
            } else {
                int count = currentUris.size();
                if (currentLayoutMode == 0 || currentLayoutMode == 3) {
                    int cols = (int) Math.ceil(Math.sqrt(count));
                    int rows = (int) Math.ceil((double) count / cols);
                    float cellW = w / cols;
                    float cellH = h / rows;

                    int index = 0;
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            if (index >= count) break;
                            layoutBounds.add(new RectF(c * cellW, r * cellH, (c + 1) * cellW, (r + 1) * cellH));
                            index++;
                        }
                    }
                } else {
                    int rows = (int) Math.ceil(Math.sqrt(count));
                    float rowH = h / rows;
                    int imgsPerRow = count / rows;
                    int extras = count % rows;

                    int index = 0;
                    for (int r = 0; r < rows; r++) {
                        int currentCols = imgsPerRow + (r < extras ? 1 : 0);
                        float cellW = w / currentCols;
                        for (int c = 0; c < currentCols; c++) {
                            if (index >= count) break;
                            layoutBounds.add(new RectF(c * cellW, r * rowH, (c + 1) * cellW, (r + 1) * rowH));
                            index++;
                        }
                    }
                }
            }
            percentageBounds.clear();
            percentageBounds.addAll(getBoundsAsPercentages());
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            int h = MeasureSpec.getSize(heightMeasureSpec);

            int calcH = (int) (w / currentRatio);
            if (calcH > h) {
                calcH = h;
                w = (int) (calcH * currentRatio);
            }
            setMeasuredDimension(w, calcH);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            updateLayoutMath();
        }

        private float getSpacing(MotionEvent event) {
            if (event.getPointerCount() < 2) return 0;
            float x = event.getX(0) - event.getX(1);
            float y = event.getY(0) - event.getY(1);
            return (float) Math.hypot(x, y);
        }

        private float getFocusX(MotionEvent event) {
            if (event.getPointerCount() < 2) return event.getX(0);
            return (event.getX(0) + event.getX(1)) / 2f;
        }

        private float getFocusY(MotionEvent event) {
            if (event.getPointerCount() < 2) return event.getY(0);
            return (event.getY(0) + event.getY(1)) / 2f;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (currentUris.isEmpty() || layoutBounds.isEmpty()) return false;

            float x = event.getX();
            float y = event.getY();

            // ================= ROTATE / MIRROR MODES =================
            if (isRotateMode || isMirrorHMode || isMirrorVMode) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    for (int i = layoutBounds.size() - 1; i >= 0; i--) {
                        if (layoutBounds.get(i).contains(x, y)) {
                            if (isRotateMode) {
                                imageRotations.set(i, (imageRotations.get(i) + 90f) % 360f);
                            } else if (isMirrorHMode) {
                                imageFlipX.set(i, !imageFlipX.get(i));
                            } else if (isMirrorVMode) {
                                imageFlipY.set(i, !imageFlipY.get(i));
                            }
                            saveStateToHistory();
                            invalidate();
                            return true;
                        }
                    }
                }
                return false;
            }

            // ================= DELETE MODE =================
            if (isDeleteMode) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    for (int i = layoutBounds.size() - 1; i >= 0; i--) {
                        if (layoutBounds.get(i).contains(x, y)) {
                            currentUris.remove(i);
                            Bitmap bmp = thumbnails.remove(i);
                            if (bmp != null && !bmp.isRecycled()) bmp.recycle();

                            if (i < imageScales.size()) imageScales.remove(i);
                            if (i < imagePanX.size()) imagePanX.remove(i);
                            if (i < imagePanY.size()) imagePanY.remove(i);
                            if (i < imageRotations.size()) imageRotations.remove(i);
                            if (i < imageFlipX.size()) imageFlipX.remove(i);
                            if (i < imageFlipY.size()) imageFlipY.remove(i);

                            if (currentLayoutMode == 2) {
                                if (i < percentageBounds.size()) percentageBounds.remove(i);
                            } else {
                                percentageBounds.clear();
                            }

                            updateLayoutMath();
                            saveStateToHistory();
                            invalidate();

                            if (currentUris.isEmpty()) {
                                setInteractionMode(0);
                            }
                            CollageStudioActivity.this.updateUIVisibility();
                            return true;
                        }
                    }
                }
                return false;
            }

            // ================= ZOOM/PAN MODE =================
            if (isZoomMode) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        for (int i = layoutBounds.size() - 1; i >= 0; i--) {
                            if (layoutBounds.get(i).contains(x, y)) {
                                draggedIndex = i;
                                lastFocusX = x;
                                lastFocusY = y;
                                return true;
                            }
                        }
                        break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        if (draggedIndex != -1) {
                            lastSpan = getSpacing(event);
                            lastFocusX = getFocusX(event);
                            lastFocusY = getFocusY(event);
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (draggedIndex != -1) {
                            float focusX = getFocusX(event);
                            float focusY = getFocusY(event);
                            float dx = focusX - lastFocusX;
                            float dy = focusY - lastFocusY;

                            imagePanX.set(draggedIndex, imagePanX.get(draggedIndex) + dx);
                            imagePanY.set(draggedIndex, imagePanY.get(draggedIndex) + dy);

                            if (event.getPointerCount() >= 2) {
                                float span = getSpacing(event);
                                if (lastSpan > 0) {
                                    float scaleFactor = span / lastSpan;
                                    float newScale = imageScales.get(draggedIndex) * scaleFactor;
                                    newScale = Math.max(0.1f, Math.min(newScale, 10.0f));
                                    imageScales.set(draggedIndex, newScale);
                                }
                                lastSpan = span;
                            }
                            lastFocusX = focusX;
                            lastFocusY = focusY;
                            invalidate();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        if (event.getPointerCount() <= 1 && draggedIndex != -1) {
                            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                                draggedIndex = -1;
                                saveStateToHistory();
                            }
                        }
                        break;
                }
                return true;
            }

            // ================= ADJUST MODE =================
            if (isAdjustMode) {
                if (currentLayoutMode == 2) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        for (int i = layoutBounds.size() - 1; i >= 0; i--) {
                            RectF r = layoutBounds.get(i);
                            if (Math.abs(r.right - x) < 80 && Math.abs(r.bottom - y) < 80) {
                                draggedIndex = i; return true;
                            }
                        }
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE && draggedIndex != -1) {
                        RectF r = layoutBounds.get(draggedIndex);
                        r.right = Math.max(r.left + 150, x);
                        r.bottom = Math.max(r.top + 150, y);
                        invalidate();
                    } else if (event.getAction() == MotionEvent.ACTION_UP && draggedIndex != -1) {
                        draggedIndex = -1;
                        percentageBounds.clear();
                        percentageBounds.addAll(getBoundsAsPercentages());
                        saveStateToHistory();
                    }
                } else {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        cellsUpdatingLeft.clear(); cellsUpdatingRight.clear();
                        cellsUpdatingTop.clear(); cellsUpdatingBottom.clear();
                        float nearestX = -1, nearestY = -1;
                        float minDistX = 60, minDistY = 60;

                        for (RectF r : layoutBounds) {
                            if (r.left > 1 && Math.abs(r.left - x) < minDistX) { minDistX = Math.abs(r.left - x); nearestX = r.left; }
                            if (r.right < getWidth() - 1 && Math.abs(r.right - x) < minDistX) { minDistX = Math.abs(r.right - x); nearestX = r.right; }
                            if (r.top > 1 && Math.abs(r.top - y) < minDistY) { minDistY = Math.abs(r.top - y); nearestY = r.top; }
                            if (r.bottom < getHeight() - 1 && Math.abs(r.bottom - y) < minDistY) { minDistY = Math.abs(r.bottom - y); nearestY = r.bottom; }
                        }

                        if (nearestX != -1 && minDistX <= minDistY) {
                            draggedLineX = nearestX;
                            for (int i = 0; i < layoutBounds.size(); i++) {
                                RectF r = layoutBounds.get(i);
                                if (Math.abs(r.left - nearestX) < 2) cellsUpdatingLeft.add(i);
                                if (Math.abs(r.right - nearestX) < 2) cellsUpdatingRight.add(i);
                            }
                            return true;
                        } else if (nearestY != -1) {
                            draggedLineY = nearestY;
                            for (int i = 0; i < layoutBounds.size(); i++) {
                                RectF r = layoutBounds.get(i);
                                if (Math.abs(r.top - nearestY) < 2) cellsUpdatingTop.add(i);
                                if (Math.abs(r.bottom - nearestY) < 2) cellsUpdatingBottom.add(i);
                            }
                            return true;
                        }
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        if (draggedLineX != null) {
                            float newX = Math.max(50, Math.min(getWidth() - 50, x));
                            for (int i : cellsUpdatingLeft) layoutBounds.get(i).left = newX;
                            for (int i : cellsUpdatingRight) layoutBounds.get(i).right = newX;
                            invalidate();
                        } else if (draggedLineY != null) {
                            float newY = Math.max(50, Math.min(getHeight() - 50, y));
                            for (int i : cellsUpdatingTop) layoutBounds.get(i).top = newY;
                            for (int i : cellsUpdatingBottom) layoutBounds.get(i).bottom = newY;
                            invalidate();
                        }
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        draggedLineX = null; draggedLineY = null;
                        percentageBounds.clear();
                        percentageBounds.addAll(getBoundsAsPercentages());
                        saveStateToHistory();
                    }
                }
                return true;
            }

            // ================= REARRANGE MODE =================
            if (isRearrangeMode) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    for (int i = layoutBounds.size() - 1; i >= 0; i--) {
                        if (layoutBounds.get(i).contains(x, y)) {
                            draggedIndex = i;
                            dragTouchX = x; dragTouchY = y;
                            return true;
                        }
                    }
                } else if (event.getAction() == MotionEvent.ACTION_MOVE && draggedIndex != -1) {
                    if (currentLayoutMode == 2) {
                        float dx = x - dragTouchX; float dy = y - dragTouchY;
                        layoutBounds.get(draggedIndex).offset(dx, dy);
                    }
                    dragTouchX = x; dragTouchY = y;
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_UP && draggedIndex != -1) {
                    if (currentLayoutMode != 2) {
                        for (int i = 0; i < layoutBounds.size(); i++) {
                            if (i != draggedIndex && layoutBounds.get(i).contains(x, y)) {
                                Collections.swap(currentUris, draggedIndex, i);
                                Collections.swap(thumbnails, draggedIndex, i);
                                Collections.swap(imageScales, draggedIndex, i);
                                Collections.swap(imagePanX, draggedIndex, i);
                                Collections.swap(imagePanY, draggedIndex, i);
                                Collections.swap(imageRotations, draggedIndex, i);
                                Collections.swap(imageFlipX, draggedIndex, i);
                                Collections.swap(imageFlipY, draggedIndex, i);
                                break;
                            }
                        }
                    }
                    draggedIndex = -1;
                    percentageBounds.clear();
                    percentageBounds.addAll(getBoundsAsPercentages());
                    saveStateToHistory();
                    invalidate();
                }
                return true;
            }

            return false;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);

            if ((layoutBounds.isEmpty() || layoutBounds.size() != currentUris.size()) && getWidth() > 0) {
                updateLayoutMath();
            }

            if (currentBgTexture != null && bgTextureThumb != null) {
                mBgRect.set(0, 0, getWidth(), getHeight());
                canvas.drawBitmap(bgTextureThumb, null, mBgRect, null);
            } else {
                canvas.drawColor(currentBgColor);
            }

            if (layoutBounds.isEmpty() || thumbnails.isEmpty()) return;

            for (int i = 0; i < thumbnails.size(); i++) {
                if (i >= layoutBounds.size()) break;
                if (isRearrangeMode && currentLayoutMode != 2 && i == draggedIndex) continue;
                drawCell(canvas, i, layoutBounds.get(i));
            }

            if (isRearrangeMode && currentLayoutMode != 2 && draggedIndex != -1 && draggedIndex < thumbnails.size() && draggedIndex < layoutBounds.size()) {
                RectF cell = layoutBounds.get(draggedIndex);
                float offsetX = dragTouchX - cell.centerX();
                float offsetY = dragTouchY - cell.centerY();
                mFloatRect.set(cell.left + offsetX, cell.top + offsetY, cell.right + offsetX, cell.bottom + offsetY);
                drawCell(canvas, draggedIndex, mFloatRect);
            }
        }

        private void drawCell(Canvas canvas, int index, RectF cell) {
            Bitmap bmp = thumbnails.get(index);
            if (bmp == null || bmp.isRecycled()) return;

            float safeBorder = Math.min(currentBorderWidth, Math.min(cell.width(), cell.height()) / 2.2f);
            mImgRect.set(cell.left + safeBorder, cell.top + safeBorder, cell.right - safeBorder, cell.bottom - safeBorder);
            if (mImgRect.width() <= 0 || mImgRect.height() <= 0) return;

            if (currentLayoutMode == 3) {
                float imgRatio = (float) bmp.getWidth() / bmp.getHeight();
                float rectRatio = mImgRect.width() / mImgRect.height();
                float newW = mImgRect.width();
                float newH = mImgRect.height();
                if (imgRatio > rectRatio) {
                    newH = newW / imgRatio;
                } else {
                    newW = newH * imgRatio;
                }
                float dx = (mImgRect.width() - newW) / 2f;
                float dy = (mImgRect.height() - newH) / 2f;
                mImgRect.set(mImgRect.left + dx, mImgRect.top + dy, mImgRect.right - dx, mImgRect.bottom - dy);
            }

            float userScale = index < imageScales.size() ? imageScales.get(index) : 1.0f;
            float userPanX = index < imagePanX.size() ? imagePanX.get(index) : 0f;
            float userPanY = index < imagePanY.size() ? imagePanY.get(index) : 0f;
            float userRot = index < imageRotations.size() ? imageRotations.get(index) : 0f;
            boolean userFlipX = index < imageFlipX.size() ? imageFlipX.get(index) : false;
            boolean userFlipY = index < imageFlipY.size() ? imageFlipY.get(index) : false;

            mMatrix.reset();
            float baseScale = Math.max(mImgRect.width() / bmp.getWidth(), mImgRect.height() / bmp.getHeight());
            float finalScale = baseScale * userScale;

            mMatrix.postScale(finalScale, finalScale);
            mMatrix.postTranslate(mImgRect.left + (mImgRect.width() - bmp.getWidth() * finalScale) / 2f + userPanX,
                    mImgRect.top + (mImgRect.height() - bmp.getHeight() * finalScale) / 2f + userPanY);

            // Transform (Flip & Rotate) perfectly around its center
            if (userFlipX || userFlipY) {
                mMatrix.postScale(userFlipX ? -1 : 1, userFlipY ? -1 : 1, mImgRect.centerX(), mImgRect.centerY());
            }
            if (userRot != 0f) {
                mMatrix.postRotate(userRot, mImgRect.centerX(), mImgRect.centerY());
            }

            if (isShadowEnabled && currentShadowColor != Color.TRANSPARENT) {
                int shadowSave = canvas.save();
                canvas.translate(currentShadowOffsetX, currentShadowOffsetY);
                mShadowPaint.setColor(currentShadowColor);
                mShadowPaint.setMaskFilter(new BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL));
                canvas.drawRoundRect(mImgRect, currentCornerRadius, currentCornerRadius, mShadowPaint);
                canvas.restoreToCount(shadowSave);
            }

            int saveCount = canvas.saveLayer(mImgRect, null);

            if (currentBlender > 0) {
                mMaskPaint.setMaskFilter(new BlurMaskFilter(currentBlender, BlurMaskFilter.Blur.NORMAL));
            } else {
                mMaskPaint.setMaskFilter(null);
            }
            canvas.drawRoundRect(mImgRect, currentCornerRadius, currentCornerRadius, mMaskPaint);

            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(bmp, mMatrix, mPaint);
            mPaint.setXfermode(null);

            canvas.restoreToCount(saveCount);

            if (isStrokeEnabled && currentStrokeColor != Color.TRANSPARENT && currentStrokeWidth > 0) {
                mStrokePaint.setColor(currentStrokeColor);
                mStrokePaint.setStrokeWidth(currentStrokeWidth);
                canvas.drawRoundRect(mImgRect, currentCornerRadius, currentCornerRadius, mStrokePaint);
            }

            if (isAdjustMode && currentLayoutMode == 2) {
                Paint highlightPaint = new Paint();
                highlightPaint.setColor(Color.RED);
                canvas.drawCircle(mImgRect.right, mImgRect.bottom, 25f, highlightPaint);
                Paint innerPaint = new Paint();
                innerPaint.setColor(Color.WHITE);
                canvas.drawCircle(mImgRect.right, mImgRect.bottom, 10f, innerPaint);
            }
        }
    }

    // ==========================================
    // CUSTOM COLOR PICKER COMPONENT
    // ==========================================
    private static class ColorPickerView extends View {
        private final Paint huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mBgPickerRect = new RectF();
        private float cursorX = 50, cursorY = 50;
        private final OnColorPickedListener listener;

        interface OnColorPickedListener {
            void onColorPicked(int color);
            void onColorPickEnded();
        }

        public ColorPickerView(Context context, OnColorPickedListener listener) {
            super(context);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            this.listener = listener;
            cursorPaint.setStyle(Paint.Style.STROKE);
            cursorPaint.setStrokeWidth(5);
            cursorPaint.setColor(Color.WHITE);
            cursorPaint.setShadowLayer(4, 0, 0, Color.BLACK);
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

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
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

            float saturation = 1f;
            float value = 1f;

            if (yNorm <= 0.5f) {
                saturation = yNorm * 2f;
                value = 1f;
            } else {
                saturation = 1f;
                value = 1f - ((yNorm - 0.5f) * 2f);
            }

            int color = Color.HSVToColor(new float[]{hue, saturation, value});

            if (listener != null) {
                listener.onColorPicked(color);
                if (action == MotionEvent.ACTION_UP) {
                    listener.onColorPickEnded();
                }
            }
            invalidate();
            return true;
        }
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
    }
}