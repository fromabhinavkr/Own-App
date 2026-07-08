package com.example.ownphotoonwall;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("FieldCanBeLocal")
@SuppressLint({"SetTextI18n", "ClickableViewAccessibility", "DefaultLocale", "InflateParams"})
public class ImageEditorActivity extends AppCompatActivity {

    private FrameLayout canvasContainer;
    private PhotoEditorView editorView;
    private View tapToStartView;
    private View rightToolsPanel;
    private LinearLayout leftLayersPanel, cropToolsBar;

    private RecyclerView layersRecyclerView;
    private LayerAdapter layerAdapter;

    private Button btnZoom, btnGrid;

    private boolean isDarkTheme;
    private int panelColor;

    private Typeface currentCustomFont = null;
    private TextView activeFontLabel = null;

    private int textMainColor = Color.WHITE;
    private int textStrokeColor = Color.BLACK;
    private int textShadowColor = Color.BLACK;

    private Runnable dialogUpdateRunnable = null;

    public interface OnColorChangeListener { void onColorChanged(int color); }

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null)
                    loadImage(result.getData().getData(), false);
            }
    );

    private final ActivityResultLauncher<Intent> pickOverlayLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null)
                    loadImage(result.getData().getData(), true);
            }
    );

    private final ActivityResultLauncher<Intent> pickFontLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    loadCustomFont(result.getData().getData());
                    if (dialogUpdateRunnable != null) dialogUpdateRunnable.run();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_editor);

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        View root = findViewById(R.id.editorRoot);
        TextView tvTitle = findViewById(R.id.tvEditorTitle);
        canvasContainer = findViewById(R.id.canvasContainer);

        tapToStartView = findViewById(R.id.btnTapToStart);
        if (tapToStartView == null) tapToStartView = findViewById(R.id.tvTapToStart);

        leftLayersPanel = findViewById(R.id.leftLayersPanel);
        rightToolsPanel = findViewById(R.id.rightToolsPanel);
        cropToolsBar = findViewById(R.id.cropToolsBar);
        Button btnToggleLayers = findViewById(R.id.btnToggleLayers);
        Button btnToggleTools = findViewById(R.id.btnToggleTools);

        layersRecyclerView = findViewById(R.id.layersRecyclerView);
        layersRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (layerAdapter != null) {
                    layerAdapter.moveItem(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                }
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(layersRecyclerView);

        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
        panelColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        int textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");

        root.setBackgroundColor(bgColor);
        tvTitle.setTextColor(textColor);

        setModernMenuBackground(leftLayersPanel, panelColor, true);
        setModernMenuBackground(rightToolsPanel, panelColor, false);
        setModernBottomMenuBackground(findViewById(R.id.bottomMenuBar), panelColor);

        editorView = new PhotoEditorView(this);
        canvasContainer.addView(editorView);
        editorView.setOnLayerChangeListener(this::refreshLayersPanel);
        editorView.setOnModeChangeListener(this::updateToolButtons);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (editorView.isImageMissing()) {
                    finish();
                } else {
                    showExitDialog();
                }
            }
        });

        Button btnLoad = findViewById(R.id.btnLoad);
        btnZoom = findViewById(R.id.btnZoom);
        btnGrid = findViewById(R.id.btnGrid);
        Button btnCrop = findViewById(R.id.btnCrop);
        Button btnAdjust = findViewById(R.id.btnAdjust);
        Button btnBgRemover = findViewById(R.id.btnBgRemover);
        Button btnText = findViewById(R.id.btnText);
        Button btnDraw = findViewById(R.id.btnDraw);
        Button btnCopy = findViewById(R.id.btnCopy);
        Button btnUndo = findViewById(R.id.btnUndo);
        Button btnClear = findViewById(R.id.btnClear);
        Button btnExport = findViewById(R.id.btnExport);
        Button btnAddOverlay = findViewById(R.id.btnAddOverlay);

        Button btnCropCancel = findViewById(R.id.btnCropCancel);
        Button btnCrop1to1 = findViewById(R.id.btnCrop1to1);
        Button btnCropFree = findViewById(R.id.btnCropFree);
        Button btnCropCircle = findViewById(R.id.btnCropCircle);
        Button btnCropRotate = findViewById(R.id.btnCropRotate);
        Button btnCropMirror = findViewById(R.id.btnCropMirror);
        Button btnCropApply = findViewById(R.id.btnCropApply);

        ColorStateList btnColor = ColorStateList.valueOf(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA"));
        Button[] tools = {btnLoad, btnCrop, btnText, btnDraw, btnClear, btnToggleLayers, btnToggleTools, btnCopy, btnUndo, btnBgRemover, btnAdjust, btnZoom, btnGrid};
        for (Button b : tools) {
            if (b != null) { b.setBackgroundTintList(btnColor); b.setTextColor(textColor); }
        }

        if (btnUndo != null) { btnUndo.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9F0A"))); btnUndo.setTextColor(Color.WHITE); }
        if (btnExport != null) { btnExport.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CD964"))); btnExport.setTextColor(Color.WHITE); }
        if (btnBgRemover != null) { btnBgRemover.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF3B30"))); btnBgRemover.setTextColor(Color.WHITE); }
        if (btnAdjust != null) { btnAdjust.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#9C27B0"))); btnAdjust.setTextColor(Color.WHITE); }

        if (tapToStartView != null) tapToStartView.setOnClickListener(v -> launchPicker(false));
        if (btnLoad != null) btnLoad.setOnClickListener(v -> launchPicker(false));

        if (btnUndo != null) {
            btnUndo.setOnClickListener(v -> {
                if (!editorView.isImageMissing()) editorView.undoLastAction();
            });
        }

        if (btnGrid != null) {
            btnGrid.setOnClickListener(v -> editorView.toggleGridMode());
        }

        if (btnZoom != null) {
            btnZoom.setOnClickListener(v -> {
                if (editorView.isImageMissing()) return;
                editorView.enableZoomMode();
                Toast.makeText(this, "Zoom / Pan Active. Pinch to Zoom, drag to Pan.", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnAddOverlay != null) {
            btnAddOverlay.setOnClickListener(v -> {
                if (editorView.isImageMissing()) { Toast.makeText(this, "Load Base Image first!", Toast.LENGTH_SHORT).show(); return; }
                launchPicker(true);
            });
        }

        if (btnToggleTools != null) {
            btnToggleTools.setOnClickListener(v -> {
                if (rightToolsPanel != null) rightToolsPanel.setVisibility(rightToolsPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                if (leftLayersPanel != null) leftLayersPanel.setVisibility(View.GONE);
            });
        }

        if (btnToggleLayers != null) {
            btnToggleLayers.setOnClickListener(v -> {
                if (leftLayersPanel != null) leftLayersPanel.setVisibility(leftLayersPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                if (rightToolsPanel != null) rightToolsPanel.setVisibility(View.GONE);
            });
        }

        if (btnAdjust != null) {
            btnAdjust.setOnClickListener(v -> {
                if (editorView.isImageMissing() || editorView.isCropping) return;
                editorView.deselectLayer();
                if (rightToolsPanel != null) rightToolsPanel.setVisibility(View.GONE);

                LayoutInflater inflater = LayoutInflater.from(this);
                View dialogView = inflater.inflate(R.layout.dialog_adjust, null);
                forceDialogBackground(dialogView);
                setDialogTextColor(dialogView, isDarkTheme ? Color.WHITE : Color.BLACK);

                Slider slBrightness = dialogView.findViewById(R.id.slBrightness);
                Slider slContrast = dialogView.findViewById(R.id.slContrast);
                Slider slSaturation = dialogView.findViewById(R.id.slSaturation);
                Slider slHue = dialogView.findViewById(R.id.slHue);
                Button btnApplyAdjust = dialogView.findViewById(R.id.btnApplyAdjust);

                slBrightness.setValue(editorView.imgBrightness);
                slContrast.setValue(editorView.imgContrast);
                slSaturation.setValue(editorView.imgSaturation);
                slHue.setValue(editorView.imgHue);

                Slider.OnChangeListener listener = (slider, value, fromUser) -> {
                    if (fromUser) {
                        editorView.setAdjustments(slBrightness.getValue(), slContrast.getValue(), slSaturation.getValue(), slHue.getValue());
                    }
                };

                slBrightness.addOnChangeListener(listener);
                slContrast.addOnChangeListener(listener);
                slSaturation.addOnChangeListener(listener);
                slHue.addOnChangeListener(listener);

                final AlertDialog dialog = createModernRoundedDialog(dialogView);
                btnApplyAdjust.setOnClickListener(view -> dialog.dismiss());
                dialog.show();
            });
        }

        if (btnCrop != null) {
            btnCrop.setOnClickListener(v -> {
                if (editorView.isImageMissing()) return;
                editorView.startInteractiveCrop();
                if (rightToolsPanel != null) rightToolsPanel.setVisibility(View.GONE);
                if (cropToolsBar != null) cropToolsBar.setVisibility(View.VISIBLE);
            });
        }

        if (btnCropCancel != null) btnCropCancel.setOnClickListener(v -> { editorView.cancelCrop(); if(cropToolsBar!=null) cropToolsBar.setVisibility(View.GONE); if(rightToolsPanel!=null) rightToolsPanel.setVisibility(View.VISIBLE); });
        if (btnCrop1to1 != null) btnCrop1to1.setOnClickListener(v -> { editorView.setCropCircle(false); editorView.setCropRatio(1f); });
        if (btnCropFree != null) btnCropFree.setOnClickListener(v -> { editorView.setCropCircle(false); editorView.setCropRatio(0f); });
        if (btnCropCircle != null) btnCropCircle.setOnClickListener(v -> editorView.setCropCircle(true));
        if (btnCropRotate != null) btnCropRotate.setOnClickListener(v -> editorView.rotateImage());
        if (btnCropMirror != null) btnCropMirror.setOnClickListener(v -> editorView.mirrorImage());
        if (btnCropApply != null) btnCropApply.setOnClickListener(v -> { editorView.applyCrop(); if(cropToolsBar!=null) cropToolsBar.setVisibility(View.GONE); if(rightToolsPanel!=null) rightToolsPanel.setVisibility(View.VISIBLE); });

        if (btnBgRemover != null) {
            btnBgRemover.setOnClickListener(v -> {
                if (editorView.isImageMissing() || editorView.isCropping) return;
                editorView.deselectLayer();
                if (rightToolsPanel != null) rightToolsPanel.setVisibility(View.GONE);

                LayoutInflater inflater = LayoutInflater.from(this);
                View dialogView = inflater.inflate(R.layout.dialog_bg_remover, null);
                forceDialogBackground(dialogView);
                setDialogTextColor(dialogView, isDarkTheme ? Color.WHITE : Color.BLACK);

                MaterialButtonToggleGroup toggleGroup = dialogView.findViewById(R.id.toggleGroup);
                LinearLayout brushSizeContainer = dialogView.findViewById(R.id.brushSizeContainer);
                Slider sliderBrushSize = dialogView.findViewById(R.id.sliderBrushSize);
                TextView tvAutoColorInstructions = dialogView.findViewById(R.id.tvAutoColorInstructions);
                Button btnApplyBgRemove = dialogView.findViewById(R.id.btnApplyBgRemove);

                toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                    if (!isChecked) return;
                    if (checkedId == R.id.btnAutoColor) {
                        brushSizeContainer.setVisibility(View.GONE);
                        tvAutoColorInstructions.setVisibility(View.VISIBLE);
                    } else {
                        brushSizeContainer.setVisibility(View.VISIBLE);
                        tvAutoColorInstructions.setVisibility(View.GONE);
                    }
                });

                final AlertDialog dialog = createModernRoundedDialog(dialogView);

                btnApplyBgRemove.setOnClickListener(view -> {
                    editorView.isDrawMode = false;
                    int checkedId = toggleGroup.getCheckedButtonId();
                    if (checkedId == R.id.btnAutoColor) {
                        editorView.enterAutoColorRemovalMode();
                        Toast.makeText(this, "Auto Color: Tap a color on the canvas to remove it.", Toast.LENGTH_LONG).show();
                    } else if (checkedId == R.id.btnManualEraser) {
                        editorView.startBgEraser((int) sliderBrushSize.getValue(), false);
                    } else if (checkedId == R.id.btnRepair) {
                        editorView.startBgEraser((int) sliderBrushSize.getValue(), true);
                    }
                    dialog.dismiss();
                });

                dialog.show();
            });
        }

        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                if (editorView.isImageMissing()) return;
                editorView.copyActiveLayer();
            });
        }

        if (btnText != null) {
            btnText.setOnClickListener(v -> {
                if (editorView.isImageMissing() || editorView.isCropping) return;
                if (rightToolsPanel != null) rightToolsPanel.setVisibility(View.GONE);

                LayoutInflater inflater = LayoutInflater.from(this);
                View dialogView = inflater.inflate(R.layout.dialog_text_input, null);
                forceDialogBackground(dialogView.findViewById(R.id.dialogTextRoot));
                setDialogTextColor(dialogView, isDarkTheme ? Color.WHITE : Color.BLACK);

                FrameLayout previewContainer = dialogView.findViewById(R.id.textPreviewContainer);
                TextPreviewView previewView = new TextPreviewView(this);
                previewContainer.addView(previewView);

                EditText etInput = dialogView.findViewById(R.id.etInputText);
                EditText etTextHexCode = dialogView.findViewById(R.id.etTextHexCode);
                Button btnPickFont = dialogView.findViewById(R.id.btnPickFont);
                activeFontLabel = dialogView.findViewById(R.id.tvFontName);
                Button btnApply = dialogView.findViewById(R.id.btnApplyText);
                FrameLayout colorWheelContainer = dialogView.findViewById(R.id.colorWheelContainerText);

                MaterialButtonToggleGroup alignToggle = dialogView.findViewById(R.id.alignToggleGroup);
                Slider slLetterSpace = dialogView.findViewById(R.id.slLetterSpacing);
                Slider slLineSpace = dialogView.findViewById(R.id.slLineSpacing);
                Slider slStroke = dialogView.findViewById(R.id.slStrokeWidth);
                Slider slShadow = dialogView.findViewById(R.id.slShadowRadius);

                if (isDarkTheme) {
                    etInput.setTextColor(Color.WHITE); etInput.setHintTextColor(Color.GRAY);
                    etTextHexCode.setTextColor(Color.WHITE);
                } else {
                    etInput.setTextColor(Color.BLACK); etInput.setHintTextColor(Color.GRAY);
                    etTextHexCode.setTextColor(Color.BLACK);
                }

                if (currentCustomFont != null) activeFontLabel.setText("Custom Font Loaded");

                textMainColor = Color.WHITE;
                textStrokeColor = Color.BLACK;
                textShadowColor = Color.BLACK;
                etTextHexCode.setText("#FFFFFF");

                ColorWheelView colorWheel = new ColorWheelView(this);
                colorWheelContainer.addView(colorWheel);

                dialogUpdateRunnable = () -> {
                    if (activeFontLabel != null && currentCustomFont != null) {
                        activeFontLabel.setText("Custom Font Loaded");
                    }
                    String input = etInput.getText().toString();
                    if (input.isEmpty()) input = "Preview Text";
                    Layout.Alignment align = Layout.Alignment.ALIGN_CENTER;
                    int aId = alignToggle.getCheckedButtonId();
                    if (aId == R.id.btnAlignLeft) align = Layout.Alignment.ALIGN_NORMAL;
                    else if (aId == R.id.btnAlignRight) align = Layout.Alignment.ALIGN_OPPOSITE;

                    previewView.updateParams(input, currentCustomFont, textMainColor, align,
                            slLetterSpace.getValue(), slLineSpace.getValue(),
                            slStroke.getValue(), textStrokeColor, slShadow.getValue(), textShadowColor);
                };

                etInput.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) { dialogUpdateRunnable.run(); }
                    @Override public void afterTextChanged(Editable s) {}
                });

                alignToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> { if(isChecked) dialogUpdateRunnable.run(); });
                slLetterSpace.addOnChangeListener((slider, value, fromUser) -> dialogUpdateRunnable.run());
                slLineSpace.addOnChangeListener((slider, value, fromUser) -> dialogUpdateRunnable.run());
                slStroke.addOnChangeListener((slider, value, fromUser) -> dialogUpdateRunnable.run());
                slShadow.addOnChangeListener((slider, value, fromUser) -> dialogUpdateRunnable.run());

                boolean[] isUpdating = {false};
                colorWheel.setOnColorChangeListener(color -> {
                    textMainColor = color;
                    if (!isUpdating[0]) {
                        isUpdating[0] = true;
                        etTextHexCode.setText(String.format("#%06X", (0xFFFFFF & color)));
                        isUpdating[0] = false;
                    }
                    dialogUpdateRunnable.run();
                });

                etTextHexCode.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (isUpdating[0]) return;
                        if (s.length() == 7 && s.toString().startsWith("#")) {
                            try {
                                int newC = Color.parseColor(s.toString());
                                textMainColor = newC;
                                colorWheel.setColor(newC);
                                dialogUpdateRunnable.run();
                            } catch (Exception ignored) {}
                        }
                    }
                    @Override public void afterTextChanged(Editable s) {}
                });

                final AlertDialog dialog = createModernRoundedDialog(dialogView);

                btnPickFont.setOnClickListener(view -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf"});
                    pickFontLauncher.launch(intent);
                });

                btnApply.setOnClickListener(view -> {
                    String input = etInput.getText().toString().trim();
                    if (!input.isEmpty()) {
                        Layout.Alignment align = Layout.Alignment.ALIGN_CENTER;
                        int aId = alignToggle.getCheckedButtonId();
                        if (aId == R.id.btnAlignLeft) align = Layout.Alignment.ALIGN_NORMAL;
                        else if (aId == R.id.btnAlignRight) align = Layout.Alignment.ALIGN_OPPOSITE;

                        editorView.addAdvancedTextLayer(
                                input, currentCustomFont, textMainColor, align,
                                slLetterSpace.getValue(), slLineSpace.getValue(),
                                slStroke.getValue(), textStrokeColor,
                                slShadow.getValue(), textShadowColor
                        );
                    }
                    dialog.dismiss();
                });

                dialogUpdateRunnable.run();
                dialog.show();
            });
        }

        if (btnDraw != null) {
            btnDraw.setOnClickListener(v -> {
                if (editorView.isImageMissing() || editorView.isCropping) return;
                editorView.deselectLayer();
                if (rightToolsPanel != null) rightToolsPanel.setVisibility(View.GONE);

                LayoutInflater inflater = LayoutInflater.from(this);
                View dialogView = inflater.inflate(R.layout.dialog_brush_settings, null);
                forceDialogBackground(dialogView);
                setDialogTextColor(dialogView, isDarkTheme ? Color.WHITE : Color.BLACK);

                EditText etHexCode = dialogView.findViewById(R.id.etBrushHexCode);
                SeekBar sbSize = dialogView.findViewById(R.id.sbBrushSize);
                SeekBar sbOpacity = dialogView.findViewById(R.id.sbBrushOpacity);
                Button btnEraser = dialogView.findViewById(R.id.btnEraserToggle);
                Button btnApply = dialogView.findViewById(R.id.btnBrushApply);
                FrameLayout colorWheelContainer = dialogView.findViewById(R.id.colorWheelContainer);

                if (isDarkTheme) {
                    etHexCode.setTextColor(Color.WHITE);
                } else {
                    etHexCode.setTextColor(Color.BLACK);
                }

                ColorWheelView colorWheel = new ColorWheelView(this);
                colorWheelContainer.addView(colorWheel);

                sbSize.setProgress((int) editorView.currentBrushWidth);
                sbOpacity.setProgress((int) ((editorView.currentBrushOpacity / 255f) * 100f));
                etHexCode.setText(String.format("#%06X", (0xFFFFFF & editorView.currentBrushColor)));
                colorWheel.setColor(editorView.currentBrushColor);

                boolean isEraser = editorView.isDrawEraserMode;
                btnEraser.setBackgroundTintList(ColorStateList.valueOf(isEraser ? Color.parseColor("#FF3B30") : Color.parseColor("#E5E5EA")));
                btnEraser.setTextColor(isEraser ? Color.WHITE : Color.parseColor("#333333"));

                boolean[] isUpdating = {false};
                colorWheel.setOnColorChangeListener(color -> {
                    if (!editorView.isDrawEraserMode) {
                        editorView.currentBrushColor = color;
                        if (!isUpdating[0]) {
                            isUpdating[0] = true;
                            etHexCode.setText(String.format("#%06X", (0xFFFFFF & color)));
                            isUpdating[0] = false;
                        }
                    }
                });

                etHexCode.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (isUpdating[0] || editorView.isDrawEraserMode) return;
                        if (s.length() == 7 && s.toString().startsWith("#")) {
                            try {
                                int newC = Color.parseColor(s.toString());
                                editorView.currentBrushColor = newC;
                                colorWheel.setColor(newC);
                            } catch (Exception ignored) {}
                        }
                    }
                    @Override public void afterTextChanged(Editable s) {}
                });

                btnEraser.setOnClickListener(view -> {
                    editorView.isDrawEraserMode = !editorView.isDrawEraserMode;
                    btnEraser.setBackgroundTintList(ColorStateList.valueOf(editorView.isDrawEraserMode ? Color.parseColor("#FF3B30") : Color.parseColor("#E5E5EA")));
                    btnEraser.setTextColor(editorView.isDrawEraserMode ? Color.WHITE : Color.parseColor("#333333"));
                });

                final AlertDialog dialog = createModernRoundedDialog(dialogView);

                btnApply.setOnClickListener(view -> {
                    editorView.currentBrushOpacity = (int) ((sbOpacity.getProgress() / 100f) * 255f);
                    editorView.startDrawing(sbSize.getProgress());
                    dialog.dismiss();
                });
                dialog.show();
            });
        }

        if (btnClear != null) btnClear.setOnClickListener(v -> { if (editorView.isImageMissing()) return; editorView.clearModifications(); if (rightToolsPanel != null) rightToolsPanel.setVisibility(View.GONE); });

        if (btnExport != null) {
            btnExport.setOnClickListener(v -> {
                if (editorView.isImageMissing() || editorView.isCropping) return;
                editorView.deselectLayer();
                String[] formats = {"PNG (Preserves Transparency)", "JPG (Standard)", "WEBP (Compressed)"};

                AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
                builder.setTitle("Select Export Format").setItems(formats, (d, which) -> exportImage(which));
                AlertDialog dialog = builder.create();
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                    dialog.setOnShowListener(di -> {
                        forceDialogBackground(dialog.getWindow().getDecorView());
                        setDialogTextColor(dialog.getWindow().getDecorView(), isDarkTheme ? Color.WHITE : Color.BLACK);
                    });
                }
                dialog.show();
            });
        }
    }

    private void updateToolButtons() {
        if (btnZoom == null || btnGrid == null) return;
        int activeColor = Color.parseColor("#4A90E2");
        int defaultBg = isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA");
        int defaultText = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");

        btnZoom.setBackgroundTintList(ColorStateList.valueOf(editorView.isZoomMode ? activeColor : defaultBg));
        btnZoom.setTextColor(editorView.isZoomMode ? Color.WHITE : defaultText);

        btnGrid.setBackgroundTintList(ColorStateList.valueOf(editorView.isGridMode ? activeColor : defaultBg));
        btnGrid.setTextColor(editorView.isGridMode ? Color.WHITE : defaultText);
        btnGrid.setText(editorView.isGridMode ? "Grid: ON" : "Grid: OFF");
    }

    private void setDialogTextColor(View view, int color) {
        if (view instanceof TextView && !(view instanceof Button) && !(view instanceof EditText)) {
            ((TextView) view).setTextColor(color);
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                setDialogTextColor(vg.getChildAt(i), color);
            }
        }
    }

    private void showExitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        builder.setTitle("Unsaved Changes")
                .setMessage("Do you want to export your current edited image before exiting?")
                .setPositiveButton("Export", (dialog, which) -> findViewById(R.id.btnExport).performClick())
                .setNegativeButton("Exit Without Saving", (dialog, which) -> finish())
                .setNeutralButton("Cancel", null);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.setOnShowListener(di -> {
                forceDialogBackground(dialog.getWindow().getDecorView());
                setDialogTextColor(dialog.getWindow().getDecorView(), isDarkTheme ? Color.WHITE : Color.BLACK);
            });
        }
        dialog.show();
    }

    private void refreshLayersPanel() {
        if (layersRecyclerView == null) return;
        if (layersRecyclerView.getAdapter() == null) {
            layerAdapter = new LayerAdapter();
            layersRecyclerView.setAdapter(layerAdapter);
        } else {
            layersRecyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    private void forceDialogBackground(View view) {
        if (view == null) return;
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(panelColor);
        gd.setCornerRadius(60f);
        view.setBackground(gd);
    }

    private AlertDialog createModernRoundedDialog(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        return dialog;
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

    private void launchPicker(boolean isOverlay) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        if (isOverlay) pickOverlayLauncher.launch(intent);
        else pickImageLauncher.launch(intent);
    }

    private void loadImage(Uri uri, boolean isOverlay) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                is.close();
                if (bitmap != null) {
                    if (isOverlay) {
                        editorView.addImageLayer(bitmap);
                        if (leftLayersPanel != null) leftLayersPanel.setVisibility(View.VISIBLE);
                    } else {
                        if (tapToStartView != null) tapToStartView.setVisibility(View.GONE);
                        editorView.setImage(bitmap);
                    }
                }
            }
        } catch (Exception e) { Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show(); }
    }

    private void loadCustomFont(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is != null) {
                File tempFontFile = new File(getCacheDir(), "temp_font_" + System.currentTimeMillis() + ".ttf");
                FileOutputStream fos = new FileOutputStream(tempFontFile);
                byte[] buffer = new byte[1024]; int length;
                while ((length = is.read(buffer)) > 0) fos.write(buffer, 0, length);
                fos.close(); is.close();
                currentCustomFont = Typeface.createFromFile(tempFontFile);
                if (activeFontLabel != null) activeFontLabel.setText("Custom Font Loaded!");
            }
        } catch (Exception e) { Toast.makeText(this, "Error loading font", Toast.LENGTH_SHORT).show(); }
    }

    private void exportImage(int formatIndex) {
        Toast.makeText(this, "Processing and Saving...", Toast.LENGTH_SHORT).show();
        Bitmap finalImage = editorView.getRenderedBitmap(true);

        new Thread(() -> {
            try {
                String extension, mimeType; Bitmap.CompressFormat compressFormat; int quality = 100;
                if (formatIndex == 0) { extension = ".png"; mimeType = "image/png"; compressFormat = Bitmap.CompressFormat.PNG; }
                else if (formatIndex == 1) { extension = ".jpg"; mimeType = "image/jpeg"; compressFormat = Bitmap.CompressFormat.JPEG;quality=85; }
                else { extension = ".webp"; mimeType = "image/webp"; compressFormat = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP; quality = 80; }

                String fileName = "Edited_" + System.currentTimeMillis() + extension;
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OwnPhoto");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                }

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os != null) { finalImage.compress(compressFormat, quality, os); os.close(); }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0);
                        getContentResolver().update(uri, values, null, null);
                    }
                    runOnUiThread(() -> Toast.makeText(this, "Saved to Pictures/OwnPhoto", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "Export Failed", Toast.LENGTH_SHORT).show()); }
        }).start();
    }

    private class LayerAdapter extends RecyclerView.Adapter<LayerAdapter.LayerViewHolder> {

        class LayerViewHolder extends RecyclerView.ViewHolder {
            LayerViewHolder(View itemView) { super(itemView); }
        }

        private List<GraphicLayer> getReversedLayers() {
            List<GraphicLayer> reversed = new ArrayList<>(editorView.getLayers());
            Collections.reverse(reversed);
            return reversed;
        }

        @NonNull @Override
        public LayerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(24, 32, 24, 32);
            tv.setTextSize(14f);

            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 16);
            tv.setLayoutParams(params);

            return new LayerViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull LayerViewHolder holder, int position) {
            List<GraphicLayer> rev = getReversedLayers();
            if (position >= rev.size()) return;

            GraphicLayer layer = rev.get(position);
            TextView tv = (TextView) holder.itemView;

            tv.setText(layer.type == 0 ? "≡  Text: " + layer.text : "≡  Overlay Image");

            if (layer == editorView.getActiveLayer()) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.parseColor("#4A90E2"));
                bg.setCornerRadius(24f);
                tv.setBackground(bg);
                tv.setTextColor(Color.WHITE);
            } else {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA"));
                bg.setCornerRadius(24f);
                tv.setBackground(bg);
                tv.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
            }

            tv.setOnClickListener(v -> editorView.setActiveLayer(layer));
        }

        @Override
        public int getItemCount() {
            return editorView.getLayers().size();
        }

        public void moveItem(int fromPosition, int toPosition) {
            List<GraphicLayer> original = editorView.getLayers();
            List<GraphicLayer> reversed = new ArrayList<>(original);
            Collections.reverse(reversed);

            GraphicLayer moved = reversed.remove(fromPosition);
            reversed.add(toPosition, moved);

            Collections.reverse(reversed);
            original.clear();
            original.addAll(reversed);

            notifyItemMoved(fromPosition, toPosition);
            editorView.invalidate();
        }
    }

    public static class TextPreviewView extends View {
        private GraphicLayer previewLayer;
        public TextPreviewView(Context context) { super(context); }
        public void updateParams(String text, Typeface typeface, int color, Layout.Alignment align, float letterSpacing, float lineSpacing, float strokeWidth, int strokeColor, float shadowRadius, int shadowColor) {
            previewLayer = new GraphicLayer(text, typeface, color, align, letterSpacing, lineSpacing, strokeWidth, strokeColor, shadowRadius, shadowColor, 0, 0);
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (previewLayer != null) {
                canvas.save();
                canvas.translate(getWidth()/2f, getHeight()/2f);
                previewLayer.drawLayer(canvas);
                canvas.restore();
            }
        }
    }

    private static class ColorWheelView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private SweepGradient sweepGradient;
        private OnColorChangeListener listener;
        private final int[] colors = {Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED};

        public ColorWheelView(Context context) { super(context); paint.setStyle(Paint.Style.FILL); }
        public void setOnColorChangeListener(OnColorChangeListener listener) { this.listener = listener; }

        @Override protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            sweepGradient = new SweepGradient(w / 2f, h / 2f, colors, null);
        }

        public void setColor(int color) { if (listener != null) listener.onColorChanged(color); }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f; float cy = getHeight() / 2f;
            float radius = Math.min(cx, cy);
            paint.setShader(sweepGradient); canvas.drawCircle(cx, cy, radius, paint);
            paint.setShader(null); paint.setColor(Color.BLACK); canvas.drawCircle(cx, cy, radius * 0.3f, paint);
        }

        @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - getWidth() / 2f; float dy = event.getY() - getHeight() / 2f;
                double angle = Math.atan2(dy, dx);
                if (angle < 0) angle += 2 * Math.PI;
                if (listener != null) listener.onColorChanged(interpolateColor(colors, (float) (angle / (2 * Math.PI))));
                return true;
            }
            return super.onTouchEvent(event);
        }

        private int interpolateColor(int[] arr, float unit) {
            if (unit <= 0) return arr[0]; if (unit >= 1) return arr[arr.length - 1];
            float p = unit * (arr.length - 1); int i = (int) p; p -= i;
            int c0 = arr[i]; int c1 = arr[i + 1];
            return Color.argb(ave(Color.alpha(c0), Color.alpha(c1), p), ave(Color.red(c0), Color.red(c1), p), ave(Color.green(c0), Color.green(c1), p), ave(Color.blue(c0), Color.blue(c1), p));
        }
        private int ave(int s, int d, float p) { return s + Math.round(p * (d - s)); }
    }

    private static class GraphicLayer {
        int type;
        float x, y, scale = 1f, rotation = 0f;
        RectF bounds = new RectF();
        String text; Bitmap bitmap;

        Typeface typeface; int color, strokeColor, shadowColor;
        Layout.Alignment align; float letterSpacing, lineSpacing, strokeWidth, shadowRadius;
        TextPaint textPaint;
        StaticLayout staticLayout;

        GraphicLayer(GraphicLayer src) {
            this.type = src.type; this.x = src.x + 50f; this.y = src.y + 50f;
            this.scale = src.scale; this.rotation = src.rotation; this.bounds = new RectF(src.bounds);
            this.text = src.text; this.bitmap = src.bitmap;
            this.typeface = src.typeface; this.color = src.color; this.strokeColor = src.strokeColor; this.shadowColor = src.shadowColor;
            this.align = src.align; this.letterSpacing = src.letterSpacing; this.lineSpacing = src.lineSpacing;
            this.strokeWidth = src.strokeWidth; this.shadowRadius = src.shadowRadius;

            if (src.textPaint != null) this.textPaint = new TextPaint(src.textPaint);
            if (type == 0) buildStaticLayout();
        }

        GraphicLayer(String text, Typeface typeface, int color, Layout.Alignment align, float letterSpacing, float lineSpacing, float strokeWidth, int strokeColor, float shadowRadius, int shadowColor, float x, float y) {
            this.type = 0; this.text = text; this.typeface = typeface; this.color = color;
            this.align = align; this.letterSpacing = letterSpacing; this.lineSpacing = lineSpacing;
            this.strokeWidth = strokeWidth; this.strokeColor = strokeColor;
            this.shadowRadius = shadowRadius; this.shadowColor = shadowColor;
            this.x = x; this.y = y;

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(100f);
            if (typeface != null) textPaint.setTypeface(typeface);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textPaint.setLetterSpacing(letterSpacing);
            }
            buildStaticLayout();
        }

        GraphicLayer(Bitmap bitmap, float x, float y) {
            this.type = 1; this.bitmap = bitmap; this.x = x; this.y = y; updateBounds();
        }

        private void buildStaticLayout() {
            int width = (int) Layout.getDesiredWidth(text, textPaint) + 20;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                staticLayout = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, Math.max(10, width))
                        .setAlignment(align).setLineSpacing(0f, lineSpacing).build();
            } else {
                staticLayout = new StaticLayout(text, textPaint, Math.max(10, width), align, lineSpacing, 0f, false);
            }
            bounds.set(-staticLayout.getWidth() / 2f, -staticLayout.getHeight() / 2f, staticLayout.getWidth() / 2f, staticLayout.getHeight() / 2f);
        }

        void updateBounds() {
            if (type == 1 && bitmap != null) bounds.set(-bitmap.getWidth()/2f, -bitmap.getHeight()/2f, bitmap.getWidth()/2f, bitmap.getHeight()/2f);
        }

        void drawLayer(Canvas canvas) {
            if (type == 0 && staticLayout != null) {
                canvas.save();
                canvas.translate(-staticLayout.getWidth() / 2f, -staticLayout.getHeight() / 2f);

                if (strokeWidth > 0) {
                    textPaint.setStyle(Paint.Style.STROKE);
                    textPaint.setStrokeWidth(strokeWidth);
                    textPaint.setColor(strokeColor);
                    textPaint.clearShadowLayer();
                    staticLayout.draw(canvas);
                }

                textPaint.setStyle(Paint.Style.FILL);
                textPaint.setColor(color);
                if (shadowRadius > 0) textPaint.setShadowLayer(shadowRadius, 0, 0, shadowColor);
                else textPaint.clearShadowLayer();
                staticLayout.draw(canvas);

                canvas.restore();
            }
            else if (type == 1 && bitmap != null) canvas.drawBitmap(bitmap, -bitmap.getWidth()/2f, -bitmap.getHeight()/2f, null);
        }
    }

    private static class ActionRecord {
        int type;
        GraphicLayer layer;
        DrawStroke stroke;
        ActionRecord(GraphicLayer l) { type = 0; layer = l; }
        ActionRecord(DrawStroke s) { type = 1; stroke = s; }
    }

    private static class DrawStroke {
        Path path; Paint paint; int targetCanvas;
        DrawStroke(Path p, Paint pt, int tc) {
            path = new Path(p); paint = new Paint(pt); targetCanvas = tc;
        }
    }

    private static class PhotoEditorView extends View {
        private Bitmap baseImage;
        private final RectF destRect = new RectF();
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private Bitmap drawLayerBitmap; private Canvas drawLayerCanvas;
        private Bitmap eraseLayerBitmap; private Canvas eraseLayerCanvas;

        private final Paint checkerPaint = new Paint();
        private final Paint autoPunchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public boolean isDrawMode = false;
        public boolean isDrawEraserMode = false;

        public boolean isBgRemoverMode = false;
        private boolean isAutoColorRemovalMode = false;
        private boolean isBgRepairMode = false;

        public boolean isZoomMode = false;
        public boolean isGridMode = false;
        private float viewZoom = 1f, viewPanX = 0f, viewPanY = 0f;
        private final ScaleGestureDetector scaleDetector;

        public float imgBrightness = 0f, imgContrast = 1f, imgSaturation = 1f, imgHue = 0f;

        public int currentBrushColor = Color.RED;
        public float currentBrushWidth = 12f;
        public int currentBrushOpacity = 255;

        private final Path currentPath = new Path();
        private final Paint currentDrawPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final List<GraphicLayer> graphicLayers = new ArrayList<>();
        private final List<DrawStroke> drawStrokes = new ArrayList<>();
        private final List<ActionRecord> undoStack = new ArrayList<>();

        private GraphicLayer activeLayer = null;
        private Runnable layerListener;

        public interface OnModeChangeListener { void onModeChanged(); }
        private OnModeChangeListener modeListener;
        public void setOnModeChangeListener(OnModeChangeListener listener) { this.modeListener = listener; }

        public GraphicLayer getActiveLayer() { return activeLayer; }

        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint deletePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint deleteXPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Matrix mapMatrix = new Matrix();
        private final Matrix mapInverse = new Matrix();
        private final float[] mapPoint = new float[2];
        private final Path cropMaskPath = new Path();

        private int touchMode = 0;
        private float initialDist = 0f, initialScale = 1f, initialAngle = 0f, initialRotation = 0f;
        private final PointF lastTouch = new PointF();
        private float bX, bY, lastSegX, lastSegY;
        private final Path segmentPath = new Path();

        private int activePointerId = MotionEvent.INVALID_POINTER_ID;

        public boolean isCropping = false, isCircleCrop = false;
        private final RectF cropRect = new RectF();
        private int activeCropHandle = -1;
        private float lockedRatio = 0f;

        public PhotoEditorView(Context context) {
            super(context);
            setupPaints();
            setupCheckerboard();
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(@NonNull ScaleGestureDetector detector) {
                    viewZoom = Math.max(1f, Math.min(viewZoom * detector.getScaleFactor(), 10f));
                    invalidate(); return true;
                }
            });
        }

        private void setupPaints() {
            currentDrawPaint.setStyle(Paint.Style.STROKE); currentDrawPaint.setStrokeJoin(Paint.Join.ROUND); currentDrawPaint.setStrokeCap(Paint.Cap.ROUND);
            borderPaint.setColor(Color.WHITE); borderPaint.setStyle(Paint.Style.STROKE); borderPaint.setStrokeWidth(4f);
            handlePaint.setColor(Color.WHITE); handlePaint.setStyle(Paint.Style.FILL);
            deletePaint.setColor(Color.parseColor("#FF3B30")); deletePaint.setStyle(Paint.Style.FILL);
            deleteXPaint.setColor(Color.WHITE); deleteXPaint.setStyle(Paint.Style.STROKE); deleteXPaint.setStrokeWidth(4f);
            maskPaint.setColor(Color.argb(180, 0, 0, 0)); maskPaint.setStyle(Paint.Style.FILL);
            gridPaint.setColor(Color.argb(150, 255, 255, 255)); gridPaint.setStyle(Paint.Style.STROKE); gridPaint.setStrokeWidth(2f);
            autoPunchPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        }

        private void setupCheckerboard() {
            Bitmap cb = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888); Canvas cc = new Canvas(cb); cc.drawColor(Color.WHITE);
            Paint p = new Paint(); p.setColor(Color.LTGRAY); cc.drawRect(0, 0, 20, 20, p); cc.drawRect(20, 20, 40, 40, p);
            checkerPaint.setShader(new BitmapShader(cb, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }

        public void setOnLayerChangeListener(Runnable listener) { this.layerListener = listener; }

        public void setImage(Bitmap bitmap) {
            this.baseImage = bitmap;
            drawLayerBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            drawLayerCanvas = new Canvas(drawLayerBitmap);
            eraseLayerBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            eraseLayerCanvas = new Canvas(eraseLayerBitmap);
            clearModifications(); invalidate();
        }

        public boolean isImageMissing() { return baseImage == null; }

        public void enableZoomMode() { disableSpecialModes(); isZoomMode = true; if (modeListener != null) modeListener.onModeChanged(); }
        public void toggleGridMode() { isGridMode = !isGridMode; invalidate(); if (modeListener != null) modeListener.onModeChanged(); }
        public void startDrawing(int width) { disableSpecialModes(); isDrawMode = true; currentBrushWidth = width; }
        public void startBgEraser(int width, boolean isRepair) { disableSpecialModes(); isBgRemoverMode = true; isBgRepairMode = isRepair; currentBrushWidth = width; }
        public void enterAutoColorRemovalMode() { disableSpecialModes(); isAutoColorRemovalMode = true; }

        private void disableSpecialModes() {
            isDrawMode = false; isBgRemoverMode = false; isAutoColorRemovalMode = false; isZoomMode = false; activeLayer = null;
            if (modeListener != null) modeListener.onModeChanged();
        }

        public void setAdjustments(float brightness, float contrast, float saturation, float hue) {
            this.imgBrightness = brightness; this.imgContrast = contrast; this.imgSaturation = saturation; this.imgHue = hue;
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(saturation);
            cm.postConcat(new ColorMatrix(new float[] { contrast, 0, 0, 0, brightness, 0, contrast, 0, 0, brightness, 0, 0, contrast, 0, brightness, 0, 0, 0, 1, 0 }));
            if (hue != 0) {
                float h = hue / 180f * (float) Math.PI;
                float cos = (float) Math.cos(h), sin = (float) Math.sin(h);
                float lumR = 0.213f, lumG = 0.715f, lumB = 0.072f;
                cm.postConcat(new ColorMatrix(new float[] {
                        lumR + cos * (1 - lumR) + sin * (-lumR), lumG + cos * (-lumG) + sin * (-lumG), lumB + cos * (-lumB) + sin * (1 - lumB), 0, 0,
                        lumR + cos * (-lumR) + sin * (0.143f), lumG + cos * (1 - lumG) + sin * (0.140f), lumB + cos * (-lumB) + sin * (-0.283f), 0, 0,
                        lumR + cos * (-lumR) + sin * (-(1 - lumR)), lumG + cos * (-lumG) + sin * (lumG), lumB + cos * (1 - lumB) + sin * (lumB), 0, 0,
                        0f, 0f, 0f, 1f, 0f
                }));
            }
            bitmapPaint.setColorFilter(new ColorMatrixColorFilter(cm));
            invalidate();
        }

        public void addAdvancedTextLayer(String text, Typeface typeface, int color, Layout.Alignment align, float letterSpacing, float lineSpacing, float strokeWidth, int strokeColor, float shadowRadius, int shadowColor) {
            disableSpecialModes();
            GraphicLayer layer = new GraphicLayer(text, typeface, color, align, letterSpacing, lineSpacing, strokeWidth, strokeColor, shadowRadius, shadowColor, 0, 0);
            graphicLayers.add(layer); activeLayer = layer;
            undoStack.add(new ActionRecord(layer));
            if (layerListener != null) layerListener.run(); invalidate();
        }

        public void addImageLayer(Bitmap bmp) {
            disableSpecialModes(); GraphicLayer layer = new GraphicLayer(bmp, 0, 0);
            graphicLayers.add(layer); activeLayer = layer;
            undoStack.add(new ActionRecord(layer));
            if (layerListener != null) layerListener.run(); invalidate();
        }

        public void copyActiveLayer() {
            if (activeLayer == null) return;
            GraphicLayer toCopy = activeLayer; disableSpecialModes();
            GraphicLayer newLayer = new GraphicLayer(toCopy);
            graphicLayers.add(newLayer); activeLayer = newLayer;
            undoStack.add(new ActionRecord(newLayer));
            if (layerListener != null) layerListener.run(); invalidate();
        }

        public void undoLastAction() {
            if (undoStack.isEmpty()) return;
            ActionRecord lastAction = undoStack.remove(undoStack.size() - 1);

            if (lastAction.type == 0) {
                graphicLayers.remove(lastAction.layer);
                if (activeLayer == lastAction.layer) activeLayer = null;
                if (layerListener != null) layerListener.run();
            } else if (lastAction.type == 1) {
                drawStrokes.remove(lastAction.stroke);
                redrawAllStrokes();
            }
            invalidate();
        }

        private void redrawAllStrokes() {
            drawLayerBitmap.eraseColor(Color.TRANSPARENT);
            eraseLayerBitmap.eraseColor(Color.TRANSPARENT);
            for (DrawStroke stroke : drawStrokes) {
                Canvas targetCanvas = (stroke.targetCanvas < 2) ? drawLayerCanvas : eraseLayerCanvas;
                targetCanvas.drawPath(stroke.path, stroke.paint);
            }
        }

        public void deselectLayer() { activeLayer = null; invalidate(); }
        public void setActiveLayer(GraphicLayer layer) { activeLayer = layer; invalidate(); }
        public List<GraphicLayer> getLayers() { return graphicLayers; }

        public void clearModifications() {
            disableSpecialModes(); graphicLayers.clear(); undoStack.clear(); drawStrokes.clear();
            viewZoom = 1f; viewPanX = 0f; viewPanY = 0f; isCircleCrop = false; isGridMode = false;
            setAdjustments(0, 1, 1, 0);
            if (drawLayerBitmap != null) drawLayerBitmap.eraseColor(Color.TRANSPARENT);
            if (eraseLayerBitmap != null) eraseLayerBitmap.eraseColor(Color.TRANSPARENT);
            if (layerListener != null) layerListener.run();
            if (modeListener != null) modeListener.onModeChanged();
            invalidate();
        }

        public void startInteractiveCrop() { disableSpecialModes(); viewZoom=1f; viewPanX=0f; viewPanY=0f; isCropping = true; cropRect.set(destRect); invalidate(); }
        public void cancelCrop() { isCropping = false; isCircleCrop = false; invalidate(); }
        public void setCropRatio(float r) { lockedRatio = r; if(r==1f){ float s=Math.min(cropRect.width(),cropRect.height()); cropRect.set(cropRect.centerX()-s/2,cropRect.centerY()-s/2,cropRect.centerX()+s/2,cropRect.centerY()+s/2); } invalidate(); }
        public void setCropCircle(boolean isCircle) { this.isCircleCrop = isCircle; if (isCircle) setCropRatio(1f); invalidate(); }

        public void rotateImage() {
            if (baseImage == null) return;
            Bitmap flattened = getRenderedBitmap(true);
            Matrix matrix = new Matrix(); matrix.postRotate(90);
            setImage(Bitmap.createBitmap(flattened, 0, 0, flattened.getWidth(), flattened.getHeight(), matrix, true));
            if (isCropping) startInteractiveCrop();
        }

        public void mirrorImage() {
            if (baseImage == null) return;
            Bitmap flattened = getRenderedBitmap(true);
            Matrix matrix = new Matrix(); matrix.preScale(-1.0f, 1.0f);
            setImage(Bitmap.createBitmap(flattened, 0, 0, flattened.getWidth(), flattened.getHeight(), matrix, true));
            if (isCropping) startInteractiveCrop();
        }

        public void applyCrop() {
            if (!isCropping || baseImage == null) return;
            activeLayer = null; Bitmap flattened = getRenderedBitmap(true);
            float scaleX = flattened.getWidth() / destRect.width(); float scaleY = flattened.getHeight() / destRect.height();
            int bx = (int) ((cropRect.left - destRect.left) * scaleX); int by = (int) ((cropRect.top - destRect.top) * scaleY);
            int bw = (int) (cropRect.width() * scaleX); int bh = (int) (cropRect.height() * scaleY);
            bx = Math.max(0, bx); by = Math.max(0, by); bw = Math.min(flattened.getWidth() - bx, bw); bh = Math.min(flattened.getHeight() - by, bh);

            if (bw > 0 && bh > 0) {
                Bitmap cropped = Bitmap.createBitmap(flattened, bx, by, bw, bh);
                if (isCircleCrop) {
                    Bitmap circleBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(circleBitmap); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    c.drawCircle(bw / 2f, bh / 2f, Math.min(bw, bh) / 2f, p);
                    p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN)); c.drawBitmap(cropped, 0, 0, p); setImage(circleBitmap);
                } else setImage(cropped);
            }
            isCropping = false; isCircleCrop = false;
        }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (baseImage == null) return;

            float scale = Math.min((float) getWidth() / baseImage.getWidth(), (float) getHeight() / baseImage.getHeight()) * viewZoom;
            float dx = (getWidth() - baseImage.getWidth() * scale) / 2f + viewPanX;
            float dy = (getHeight() - baseImage.getHeight() * scale) / 2f + viewPanY;
            destRect.set(dx, dy, dx + baseImage.getWidth() * scale, dy + baseImage.getHeight() * scale);

            canvas.drawRect(destRect, checkerPaint);

            int sc = canvas.saveLayer(destRect, null);
            canvas.drawBitmap(baseImage, null, destRect, bitmapPaint);
            if (eraseLayerBitmap != null) canvas.drawBitmap(eraseLayerBitmap, null, destRect, autoPunchPaint);
            canvas.restoreToCount(sc);

            if (drawLayerBitmap != null) canvas.drawBitmap(drawLayerBitmap, null, destRect, null);

            if (isDrawMode && !isDrawEraserMode && !currentPath.isEmpty()) {
                canvas.save();
                canvas.translate(destRect.left, destRect.top);
                canvas.scale(destRect.width() / baseImage.getWidth(), destRect.height() / baseImage.getHeight());
                canvas.drawPath(currentPath, currentDrawPaint);
                canvas.restore();
            }

            for (GraphicLayer layer : graphicLayers) {
                canvas.save();
                canvas.translate(destRect.centerX(), destRect.centerY());
                canvas.scale(destRect.width() / baseImage.getWidth(), destRect.height() / baseImage.getHeight());
                canvas.translate(layer.x, layer.y); canvas.rotate(layer.rotation); canvas.scale(layer.scale, layer.scale);
                layer.drawLayer(canvas);

                if (layer == activeLayer && !isCropping) {
                    float l = layer.bounds.left, t = layer.bounds.top, r = layer.bounds.right, b = layer.bounds.bottom;
                    canvas.drawRect(l, t, r, b, borderPaint); canvas.drawCircle(l, t, 30f, deletePaint);
                    canvas.drawLine(l-12f, t-12f, l+12f, t+12f, deleteXPaint); canvas.drawLine(l+12f, t-12f, l-12f, t+12f, deleteXPaint);
                    canvas.drawCircle(r, b, 30f, handlePaint); canvas.drawCircle(r, b, 30f, borderPaint);
                    canvas.drawLine(0, b, 0, b+60f, borderPaint); canvas.drawCircle(0, b+60f, 30f, handlePaint); canvas.drawCircle(0, b+60f, 30f, borderPaint);
                }
                canvas.restore();
            }

            if (isCropping) {
                cropMaskPath.reset(); cropMaskPath.addRect(destRect, Path.Direction.CW);
                if (isCircleCrop) cropMaskPath.addCircle(cropRect.centerX(), cropRect.centerY(), cropRect.width()/2f, Path.Direction.CCW);
                else cropMaskPath.addRect(cropRect, Path.Direction.CCW);
                cropMaskPath.setFillType(Path.FillType.EVEN_ODD); canvas.drawPath(cropMaskPath, maskPaint);

                if (isCircleCrop) canvas.drawCircle(cropRect.centerX(), cropRect.centerY(), cropRect.width()/2f, borderPaint);
                else {
                    canvas.drawRect(cropRect, borderPaint);
                    float w3 = cropRect.width() / 3f, h3 = cropRect.height() / 3f;
                    canvas.drawLine(cropRect.left + w3, cropRect.top, cropRect.left + w3, cropRect.bottom, gridPaint); canvas.drawLine(cropRect.left + w3*2, cropRect.top, cropRect.left + w3*2, cropRect.bottom, gridPaint);
                    canvas.drawLine(cropRect.left, cropRect.top + h3, cropRect.right, cropRect.top + h3, gridPaint); canvas.drawLine(cropRect.left, cropRect.top + h3*2, cropRect.right, cropRect.top + h3*2, gridPaint);
                }
                canvas.drawCircle(cropRect.left, cropRect.top, 24f, handlePaint); canvas.drawCircle(cropRect.right, cropRect.top, 24f, handlePaint);
                canvas.drawCircle(cropRect.left, cropRect.bottom, 24f, handlePaint); canvas.drawCircle(cropRect.right, cropRect.bottom, 24f, handlePaint);
            }

            if (isGridMode) {
                float cellWidth = destRect.width() / 9f;
                float cellHeight = destRect.height() / 9f;
                for (int i = 1; i < 9; i++) {
                    canvas.drawLine(destRect.left + (cellWidth * i), destRect.top, destRect.left + (cellWidth * i), destRect.bottom, gridPaint);
                    canvas.drawLine(destRect.left, destRect.top + (cellHeight * i), destRect.right, destRect.top + (cellHeight * i), gridPaint);
                }
                canvas.drawRect(destRect, gridPaint);
            }
        }

        private float[] mapTouch(GraphicLayer l, float x, float y) {
            mapMatrix.reset(); mapMatrix.postTranslate(destRect.centerX(), destRect.centerY());
            mapMatrix.preScale(destRect.width() / baseImage.getWidth(), destRect.height() / baseImage.getHeight());
            mapMatrix.preTranslate(l.x, l.y); mapMatrix.preRotate(l.rotation); mapMatrix.preScale(l.scale, l.scale);
            mapMatrix.invert(mapInverse); mapPoint[0] = x; mapPoint[1] = y; mapInverse.mapPoints(mapPoint); return mapPoint;
        }

        @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
            float x = event.getX(), y = event.getY();

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
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: activePointerId = MotionEvent.INVALID_POINTER_ID; break;
                } return true;
            }

            if (isCropping) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        float hit = 80f;
                        if (Math.abs(x-cropRect.left)<hit && Math.abs(y-cropRect.top)<hit) activeCropHandle=0;
                        else if (Math.abs(x-cropRect.right)<hit && Math.abs(y-cropRect.top)<hit) activeCropHandle=1;
                        else if (Math.abs(x-cropRect.left)<hit && Math.abs(y-cropRect.bottom)<hit) activeCropHandle=2;
                        else if (Math.abs(x-cropRect.right)<hit && Math.abs(y-cropRect.bottom)<hit) activeCropHandle=3;
                        else if (cropRect.contains(x,y)) activeCropHandle=4; else activeCropHandle=-1;
                        lastTouch.set(x,y); return true;
                    case MotionEvent.ACTION_MOVE:
                        if (activeCropHandle != -1) {
                            float dx = x - lastTouch.x, dy = y - lastTouch.y;
                            if (activeCropHandle == 4) cropRect.offset(dx, dy);
                            else {
                                if (activeCropHandle==0){cropRect.left+=dx; cropRect.top+=dy;} else if(activeCropHandle==1){cropRect.right+=dx; cropRect.top+=dy;}
                                else if (activeCropHandle==2){cropRect.left+=dx; cropRect.bottom+=dy;} else if(activeCropHandle==3){cropRect.right+=dx; cropRect.bottom+=dy;}
                                if (lockedRatio==1f) {
                                    float s=Math.max(cropRect.width(),cropRect.height());
                                    if(activeCropHandle==0){cropRect.left=cropRect.right-s; cropRect.top=cropRect.bottom-s;} else if(activeCropHandle==1){cropRect.right=cropRect.left+s; cropRect.top=cropRect.bottom-s;}
                                    else if(activeCropHandle==2){cropRect.left=cropRect.right-s; cropRect.bottom=cropRect.top+s;} else if(activeCropHandle==3){cropRect.right=cropRect.left+s; cropRect.bottom=cropRect.top+s;}
                                }
                            }
                            if(cropRect.width()<100f) cropRect.right=cropRect.left+100f; if(cropRect.height()<100f) cropRect.bottom=cropRect.top+100f;
                            if(cropRect.left<destRect.left) cropRect.offset(destRect.left-cropRect.left,0); if(cropRect.top<destRect.top) cropRect.offset(0,destRect.top-cropRect.top);
                            if(cropRect.right>destRect.right) cropRect.offset(destRect.right-cropRect.right,0); if(cropRect.bottom>destRect.bottom) cropRect.offset(0,destRect.bottom-cropRect.bottom);
                            lastTouch.set(x,y); invalidate();
                        } return true;
                    case MotionEvent.ACTION_UP: activeCropHandle = -1; return true;
                }
            }
            else if (isAutoColorRemovalMode) {
                if (event.getAction() == MotionEvent.ACTION_DOWN && destRect.contains(x, y)) {
                    int bmpX = (int) ((x - destRect.left) * (baseImage.getWidth() / destRect.width()));
                    int bmpY = (int) ((y - destRect.top) * (baseImage.getHeight() / destRect.height()));
                    applyAutoColorRemoval(baseImage.getPixel(bmpX, bmpY)); isAutoColorRemovalMode = false;
                    Toast.makeText(getContext(), "Color removed!", Toast.LENGTH_SHORT).show(); return true;
                }
            }
            else if ((isDrawMode || isBgRemoverMode) && baseImage != null) {
                float imgX = (x - destRect.left) * (baseImage.getWidth() / destRect.width());
                float imgY = (y - destRect.top) * (baseImage.getHeight() / destRect.height());
                currentDrawPaint.setStrokeWidth(currentBrushWidth * (baseImage.getWidth() / destRect.width()));

                int targetCanvasState;
                boolean isEraser = isBgRemoverMode || (isDrawMode && isDrawEraserMode);

                if (isDrawMode && drawLayerCanvas != null) {
                    if (isDrawEraserMode) { currentDrawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR)); currentDrawPaint.setColor(Color.TRANSPARENT); targetCanvasState = 1; }
                    else { currentDrawPaint.setXfermode(null); currentDrawPaint.setColor(currentBrushColor); currentDrawPaint.setAlpha(currentBrushOpacity); targetCanvasState = 0; }
                } else if (isBgRemoverMode && eraseLayerCanvas != null) {
                    if (isBgRepairMode) { currentDrawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR)); currentDrawPaint.setColor(Color.TRANSPARENT); targetCanvasState = 3; }
                    else { currentDrawPaint.setXfermode(null); currentDrawPaint.setColor(Color.BLACK); targetCanvasState = 2; }
                } else {
                    return true;
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        currentPath.reset();
                        currentPath.moveTo(imgX, imgY);
                        bX = imgX; bY = imgY;
                        lastSegX = imgX; lastSegY = imgY;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(imgX - bX), dy = Math.abs(imgY - bY);
                        if (dx >= 2f || dy >= 2f) {
                            float midX = (imgX + bX)/2, midY = (imgY + bY)/2;
                            currentPath.quadTo(bX, bY, midX, midY);

                            if (isEraser) {
                                segmentPath.reset();
                                segmentPath.moveTo(lastSegX, lastSegY);
                                segmentPath.quadTo(bX, bY, midX, midY);
                                if (isDrawMode) drawLayerCanvas.drawPath(segmentPath, currentDrawPaint);
                                else eraseLayerCanvas.drawPath(segmentPath, currentDrawPaint);
                            }
                            bX = imgX; bY = imgY;
                            lastSegX = midX; lastSegY = midY;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        currentPath.lineTo(imgX, imgY);
                        if (isEraser) {
                            segmentPath.reset();
                            segmentPath.moveTo(lastSegX, lastSegY);
                            segmentPath.lineTo(imgX, imgY);
                            if (isDrawMode) drawLayerCanvas.drawPath(segmentPath, currentDrawPaint);
                            else eraseLayerCanvas.drawPath(segmentPath, currentDrawPaint);
                        } else {
                            drawLayerCanvas.drawPath(currentPath, currentDrawPaint);
                        }

                        DrawStroke stroke = new DrawStroke(currentPath, currentDrawPaint, targetCanvasState);
                        drawStrokes.add(stroke);
                        undoStack.add(new ActionRecord(stroke));
                        currentPath.reset();
                        break;
                }
                invalidate(); return true;
            }
            else {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchMode = 0;
                        if (activeLayer != null) {
                            float[] loc = mapTouch(activeLayer, x, y);
                            if (Math.hypot(loc[0]-activeLayer.bounds.left, loc[1]-activeLayer.bounds.top) < 80f) {
                                graphicLayers.remove(activeLayer); activeLayer = null;
                                if (layerListener != null) layerListener.run(); invalidate(); return true;
                            } else if (Math.hypot(loc[0]-activeLayer.bounds.right, loc[1]-activeLayer.bounds.bottom) < 80f) {
                                touchMode = 2; initialDist = (float)Math.hypot(x-destRect.centerX()-activeLayer.x*viewZoom, y-destRect.centerY()-activeLayer.y*viewZoom); initialScale = activeLayer.scale; return true;
                            } else if (Math.hypot(loc[0], loc[1]-(activeLayer.bounds.bottom+60f)) < 80f) {
                                touchMode = 3; initialAngle = (float)Math.toDegrees(Math.atan2(y-destRect.centerY()-activeLayer.y*viewZoom, x-destRect.centerX()-activeLayer.x*viewZoom)); initialRotation = activeLayer.rotation; return true;
                            }
                        }
                        boolean hit = false;
                        for (int i=graphicLayers.size()-1; i>=0; i--) {
                            GraphicLayer l = graphicLayers.get(i); float[] loc = mapTouch(l, x, y);
                            if (loc[0]>=l.bounds.left && loc[0]<=l.bounds.right && loc[1]>=l.bounds.top && loc[1]<=l.bounds.bottom) {
                                activeLayer = l; touchMode = 1; lastTouch.set(x, y); hit = true;
                                if (layerListener != null) layerListener.run();
                                invalidate(); return true;
                            }
                        }
                        if (!hit) activeLayer = null;
                        if (layerListener != null) layerListener.run();
                        invalidate(); return true;
                    case MotionEvent.ACTION_MOVE:
                        if (activeLayer != null) {
                            if (touchMode == 1) { activeLayer.x += (x - lastTouch.x) * (baseImage.getWidth() / destRect.width()); activeLayer.y += (y - lastTouch.y) * (baseImage.getHeight() / destRect.height()); lastTouch.set(x,y); }
                            else if (touchMode == 2) { float d = (float)Math.hypot(x-destRect.centerX()-activeLayer.x*viewZoom, y-destRect.centerY()-activeLayer.y*viewZoom); activeLayer.scale = Math.max(0.1f, initialScale*(d/initialDist)); }
                            else if (touchMode == 3) { float a = (float)Math.toDegrees(Math.atan2(y-destRect.centerY()-activeLayer.y*viewZoom, x-destRect.centerX()-activeLayer.x*viewZoom)); activeLayer.rotation = initialRotation+(a-initialAngle); }
                            invalidate(); return true;
                        } break;
                    case MotionEvent.ACTION_UP: touchMode = 0; return true;
                }
            } return super.onTouchEvent(event);
        }

        private void applyAutoColorRemoval(int colorToMatch) {
            if (baseImage == null || eraseLayerCanvas == null) return;
            int width = eraseLayerBitmap.getWidth(), height = eraseLayerBitmap.getHeight();
            int[] maskPixels = new int[width * height];
            int rT = Color.red(colorToMatch), gT = Color.green(colorToMatch), bT = Color.blue(colorToMatch);
            eraseLayerBitmap.getPixels(maskPixels, 0, width, 0, 0, width, height);

            for (int i=0; i < maskPixels.length; i++) {
                int bmpC = baseImage.getPixel(i % width, i / width);
                if (Color.alpha(bmpC) == 0) continue;
                if (Math.abs(Color.red(bmpC) - rT) <= 25 && Math.abs(Color.green(bmpC) - gT) <= 25 && Math.abs(Color.blue(bmpC) - bT) <= 25) { maskPixels[i] = Color.BLACK; }
            }
            eraseLayerBitmap.setPixels(maskPixels, 0, width, 0, 0, width, height); invalidate();
        }

        public Bitmap getRenderedBitmap(boolean isForExport) {
            Bitmap result = Bitmap.createBitmap(baseImage.getWidth(), baseImage.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(result);

            int sc = c.saveLayer(0, 0, result.getWidth(), result.getHeight(), null);
            c.drawBitmap(baseImage, 0, 0, bitmapPaint);
            if (eraseLayerBitmap != null) c.drawBitmap(eraseLayerBitmap, 0, 0, autoPunchPaint);
            c.restoreToCount(sc);

            if (drawLayerBitmap != null) c.drawBitmap(drawLayerBitmap, 0, 0, null);
            for (GraphicLayer layer : graphicLayers) {
                c.save(); c.translate(result.getWidth()/2f, result.getHeight()/2f); c.translate(layer.x, layer.y);
                c.rotate(layer.rotation); c.scale(layer.scale, layer.scale); layer.drawLayer(c); c.restore();
            }

            boolean tc = isCropping; GraphicLayer tl = activeLayer;
            if (isForExport) { isCropping = false; activeLayer = null; }
            isCropping = tc; activeLayer = tl; return result;
        }
    }
}