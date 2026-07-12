package com.example.ownphotoonwall;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentValues;
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
import android.text.InputType;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private View cropToolsBar;
    private LinearLayout leftLayersPanel;

    private RecyclerView layersRecyclerView;
    private LayerAdapter layerAdapter;

    private Button btnZoom, btnGrid, btnLayerOut;

    private boolean isDarkTheme;
    private int panelColor;

    private Typeface currentCustomFont = null;
    private TextView activeFontLabel = null;

    private int activeColorTarget = 0;
    private Runnable dialogUpdateRunnable = null;

    public interface OnColorChangeListener { void onColorChanged(int color); }
    public interface EyedropperCallback { void onColorPicked(int color); }

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null)
                    loadImage(result.getData().getData(), false);
            }
    );

    private final ActivityResultLauncher<Intent> pickOverlayLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null)
                    loadImage(result.getData().getData(), true);
            }
    );

    private final ActivityResultLauncher<Intent> pickFontLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    loadCustomFont(result.getData().getData());
                    if (dialogUpdateRunnable != null) dialogUpdateRunnable.run();
                }
            }
    );

    private final ActivityResultLauncher<Intent> advancedCanvasLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String outPath = result.getData().getStringExtra("out_path");
                    boolean saveAsBase = result.getData().getBooleanExtra("save_as_base", false);

                    if (outPath != null && editorView != null) {
                        Bitmap finishedCanvas = BitmapFactory.decodeFile(outPath);
                        if (finishedCanvas != null) {
                            if (saveAsBase) {
                                editorView.setImage(finishedCanvas);
                                if (tapToStartView != null) tapToStartView.setVisibility(View.GONE);
                                Toast.makeText(this, "Canvas saved as Base Image!", Toast.LENGTH_SHORT).show();
                            } else {
                                editorView.addImageLayer(finishedCanvas);
                                Toast.makeText(this, "Canvas added as a new layer!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
            }
    );

    // --- NEW CLONE LAUNCHER ADDED HERE ---
    private final ActivityResultLauncher<Intent> cloneLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String outPath = result.getData().getStringExtra("out_path");
                    boolean saveAsBase = result.getData().getBooleanExtra("save_as_base", false);

                    if (outPath != null && editorView != null) {
                        Bitmap finishedClone = BitmapFactory.decodeFile(outPath);
                        if (finishedClone != null) {
                            if (saveAsBase) {
                                editorView.setImage(finishedClone);
                                if (tapToStartView != null) tapToStartView.setVisibility(View.GONE);
                                Toast.makeText(this, "Cloned image applied to Base!", Toast.LENGTH_SHORT).show();
                            } else {
                                editorView.addImageLayer(finishedClone);
                                Toast.makeText(this, "Cloned image added as a new layer!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
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
                if (layerAdapter != null) layerAdapter.moveItem(viewHolder.getAdapterPosition(), target.getAdapterPosition());
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

        editorView.setTextDoubleTapListener(this::showTextAddDialog);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (editorView.isImageMissing()) finish(); else showExitDialog();
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
        Button btnAdvancedCanvas = findViewById(R.id.btnAdvancedCanvas);
        Button btnCloneTool = findViewById(R.id.btnCloneTool); // --- ADDED CLONE BUTTON HERE ---
        Button btnCopy = findViewById(R.id.btnCopy);
        Button btnLayerEdit = findViewById(R.id.btnLayerEdit);
        Button btnUndo = findViewById(R.id.btnUndo);
        Button btnClear = findViewById(R.id.btnClear);
        Button btnExport = findViewById(R.id.btnExport);
        Button btnAddOverlay = findViewById(R.id.btnAddOverlay);
        btnLayerOut = findViewById(R.id.btnLayerOut);

        Button btnCropCancel = findViewById(R.id.btnCropCancel);
        Button btnCropFull = findViewById(R.id.btnCropFull);
        Button btnCropFree = findViewById(R.id.btnCropFree);
        Button btnCrop1to1 = findViewById(R.id.btnCrop1to1);
        Button btnCrop16to9 = findViewById(R.id.btnCrop16to9);
        Button btnCrop9to16 = findViewById(R.id.btnCrop9to16);
        Button btnCrop4to3 = findViewById(R.id.btnCrop4to3);
        Button btnCrop4to1 = findViewById(R.id.btnCrop4to1);
        Button btnCropPerspective = findViewById(R.id.btnCropPerspective);
        Button btnCropCircle = findViewById(R.id.btnCropCircle);
        Button btnCropRotate = findViewById(R.id.btnCropRotate);
        Button btnCropMirror = findViewById(R.id.btnCropMirror);
        Button btnCropApply = findViewById(R.id.btnCropApply);

        ColorStateList btnColorState = ColorStateList.valueOf(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA"));
        Button[] tools = {btnLoad, btnCrop, btnText, btnDraw, btnClear, btnToggleLayers, btnToggleTools, btnCopy, btnLayerEdit, btnUndo, btnBgRemover, btnAdjust, btnZoom, btnGrid, btnLayerOut};
        for (Button b : tools) { if (b != null) { b.setBackgroundTintList(btnColorState); b.setTextColor(textColor); } }

        if (btnUndo != null) { btnUndo.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9F0A"))); btnUndo.setTextColor(Color.WHITE); }
        if (btnExport != null) { btnExport.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CD964"))); btnExport.setTextColor(Color.WHITE); }
        if (btnBgRemover != null) { btnBgRemover.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF3B30"))); btnBgRemover.setTextColor(Color.WHITE); }
        if (btnAdjust != null) { btnAdjust.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#9C27B0"))); btnAdjust.setTextColor(Color.WHITE); }
        if (btnLayerEdit != null) { btnLayerEdit.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4A90E2"))); btnLayerEdit.setTextColor(Color.WHITE); }

        if (btnAdvancedCanvas != null) { btnAdvancedCanvas.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E91E63"))); btnAdvancedCanvas.setTextColor(Color.WHITE); }
        if (btnCloneTool != null) { btnCloneTool.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9500"))); btnCloneTool.setTextColor(Color.WHITE); }

        if (tapToStartView != null) tapToStartView.setOnClickListener(btnView -> launchPicker(false));
        if (btnLoad != null) btnLoad.setOnClickListener(btnView -> launchPicker(false));

        if (btnLayerOut != null) {
            btnLayerOut.setText("Layer Out: OFF");
            btnLayerOut.setOnClickListener(btnView -> {
                editorView.isLayerOutMode = !editorView.isLayerOutMode;
                if (editorView.isLayerOutMode) {
                    btnLayerOut.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#34C759")));
                    btnLayerOut.setTextColor(Color.WHITE); btnLayerOut.setText("Layer Out: ON");
                } else {
                    btnLayerOut.setBackgroundTintList(ColorStateList.valueOf(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA")));
                    btnLayerOut.setTextColor(isDarkTheme ? Color.WHITE : Color.parseColor("#333333"));
                    btnLayerOut.setText("Layer Out: OFF");
                }
                editorView.invalidate();
            });
        }

        if (btnUndo != null) btnUndo.setOnClickListener(btnView -> { if (!editorView.isImageMissing()) editorView.undoLastAction(); });
        if (btnGrid != null) btnGrid.setOnClickListener(btnView -> editorView.toggleGridMode());
        if (btnZoom != null) btnZoom.setOnClickListener(btnView -> {
            if (editorView.isImageMissing()) return;
            editorView.toggleZoomMode();
            if (editorView.isZoomMode) Toast.makeText(this, "Zoom / Pan Active. Pinch to Zoom.", Toast.LENGTH_SHORT).show();
        });

        if (btnAddOverlay != null) btnAddOverlay.setOnClickListener(btnView -> { if (editorView.isImageMissing()) return; launchPicker(true); });

        if (btnToggleTools != null) btnToggleTools.setOnClickListener(btnView -> {
            if (rightToolsPanel != null) rightToolsPanel.setVisibility(rightToolsPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            if (leftLayersPanel != null) leftLayersPanel.setVisibility(View.GONE);
        });

        if (btnToggleLayers != null) btnToggleLayers.setOnClickListener(btnView -> {
            if (leftLayersPanel != null) leftLayersPanel.setVisibility(leftLayersPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            if (rightToolsPanel != null) rightToolsPanel.setVisibility(View.GONE);
        });

        if (btnText != null) {
            btnText.setOnClickListener(btnView -> {
                if (editorView.isImageMissing() || editorView.isCropping) return;
                editorView.deselectLayer();
                if (rightToolsPanel != null) rightToolsPanel.setVisibility(View.GONE);
                showTextAddDialog(null);
            });
        }

        if (btnLayerEdit != null) {
            btnLayerEdit.setOnClickListener(btnView -> {
                GraphicLayer layer = editorView.getActiveLayer();
                if (layer == null) {
                    Toast.makeText(this, "Select a layer on the canvas first!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (rightToolsPanel != null) rightToolsPanel.setVisibility(View.GONE);
                showLayerEditDialog(layer);
            });
        }

        if (btnAdjust != null) {
            btnAdjust.setOnClickListener(btnView -> {
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

                slBrightness.setValue(editorView.imgBrightness); slContrast.setValue(editorView.imgContrast);
                slSaturation.setValue(editorView.imgSaturation); slHue.setValue(editorView.imgHue);

                Slider.OnChangeListener listener = (slider, value, fromUser) -> {
                    if (fromUser) editorView.setAdjustments(slBrightness.getValue(), slContrast.getValue(), slSaturation.getValue(), slHue.getValue());
                };
                slBrightness.addOnChangeListener(listener); slContrast.addOnChangeListener(listener);
                slSaturation.addOnChangeListener(listener); slHue.addOnChangeListener(listener);

                final AlertDialog dialog = createModernRoundedDialog(dialogView);
                btnApplyAdjust.setOnClickListener(applyBtn -> dialog.dismiss()); dialog.show();
            });
        }

        if (btnCrop != null) {
            btnCrop.setOnClickListener(btnView -> {
                if (editorView.isImageMissing()) return;
                editorView.startInteractiveCrop();
                if (rightToolsPanel != null) rightToolsPanel.setVisibility(View.GONE);
                if (cropToolsBar != null) cropToolsBar.setVisibility(View.VISIBLE);
            });
        }

        if (btnCropCancel != null) btnCropCancel.setOnClickListener(btnView -> { editorView.cancelCrop(); if(cropToolsBar!=null) cropToolsBar.setVisibility(View.GONE); if(rightToolsPanel!=null) rightToolsPanel.setVisibility(View.VISIBLE); });
        if (btnCropFree != null) btnCropFree.setOnClickListener(btnView -> { editorView.setCropCircle(false); editorView.setCropRatio(0f); });
        if (btnCropFull != null) btnCropFull.setOnClickListener(btnView -> editorView.setCropFull());
        if (btnCrop1to1 != null) btnCrop1to1.setOnClickListener(btnView -> { editorView.setCropCircle(false); editorView.setCropRatio(1f); });
        if (btnCrop16to9 != null) btnCrop16to9.setOnClickListener(btnView -> { editorView.setCropCircle(false); editorView.setCropRatio(16f / 9f); });
        if (btnCrop9to16 != null) btnCrop9to16.setOnClickListener(btnView -> { editorView.setCropCircle(false); editorView.setCropRatio(9f / 16f); });
        if (btnCrop4to3 != null) btnCrop4to3.setOnClickListener(btnView -> { editorView.setCropCircle(false); editorView.setCropRatio(4f / 3f); });
        if (btnCrop4to1 != null) btnCrop4to1.setOnClickListener(btnView -> { editorView.setCropCircle(false); editorView.setCropRatio(4f); });
        if (btnCropPerspective != null) btnCropPerspective.setOnClickListener(btnView -> editorView.startPerspectiveCrop());
        if (btnCropCircle != null) btnCropCircle.setOnClickListener(btnView -> editorView.setCropCircle(true));
        if (btnCropRotate != null) btnCropRotate.setOnClickListener(btnView -> editorView.rotateImage());
        if (btnCropMirror != null) btnCropMirror.setOnClickListener(btnView -> editorView.mirrorImage());
        if (btnCropApply != null) btnCropApply.setOnClickListener(btnView -> { editorView.applyCrop(); if(cropToolsBar!=null) cropToolsBar.setVisibility(View.GONE); if(rightToolsPanel!=null) rightToolsPanel.setVisibility(View.VISIBLE); });

        if (btnBgRemover != null) {
            btnBgRemover.setOnClickListener(btnView -> {
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

                btnApplyBgRemove.setOnClickListener(applyBtn -> {
                    editorView.drawingManager.isDrawMode = false;
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

        if (btnCopy != null) btnCopy.setOnClickListener(btnView -> { if (editorView.isImageMissing()) return; editorView.copyActiveLayer(); });

        if (btnDraw != null) {
            btnDraw.setOnClickListener(btnView -> {
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

                if (isDarkTheme) etHexCode.setTextColor(Color.WHITE); else etHexCode.setTextColor(Color.BLACK);

                ColorWheelView colorWheel = new ColorWheelView(this);
                colorWheelContainer.addView(colorWheel);

                sbSize.setProgress((int) editorView.drawingManager.currentBrushWidth);
                sbOpacity.setProgress((int) ((editorView.drawingManager.currentBrushOpacity / 255f) * 100f));
                etHexCode.setText(String.format("#%06X", (0xFFFFFF & editorView.drawingManager.currentBrushColor)));
                colorWheel.setColor(editorView.drawingManager.currentBrushColor);

                boolean isEraser = editorView.drawingManager.isDrawEraserMode;
                btnEraser.setBackgroundTintList(ColorStateList.valueOf(isEraser ? Color.parseColor("#FF3B30") : Color.parseColor("#E5E5EA")));
                btnEraser.setTextColor(isEraser ? Color.WHITE : Color.parseColor("#333333"));

                final AlertDialog dialog = createModernRoundedDialog(dialogView);

                ViewGroup row = (ViewGroup) btnApply.getParent();
                row.removeAllViews();

                LinearLayout newRow = new LinearLayout(this);
                newRow.setOrientation(LinearLayout.HORIZONTAL);
                newRow.setWeightSum(3f);
                newRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lpBtn.setMargins(8, 0, 8, 0);

                Button btnTurnOff = new Button(this);
                btnTurnOff.setText("Turn OFF");
                btnTurnOff.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF3B30")));
                btnTurnOff.setTextColor(Color.WHITE);
                btnTurnOff.setLayoutParams(lpBtn);

                btnEraser.setLayoutParams(lpBtn);
                btnApply.setLayoutParams(lpBtn);

                newRow.addView(btnTurnOff);
                newRow.addView(btnEraser);
                newRow.addView(btnApply);
                row.addView(newRow);

                btnTurnOff.setOnClickListener(offBtn -> {
                    editorView.drawingManager.isDrawMode = false;
                    editorView.drawingManager.isDrawEraserMode = false;
                    updateToolButtons();
                    dialog.dismiss();
                    Toast.makeText(this, "Draw Tool Disabled", Toast.LENGTH_SHORT).show();
                });

                boolean[] isUpdating = {false};
                colorWheel.setOnColorChangeListener(color -> {
                    if (!editorView.drawingManager.isDrawEraserMode) {
                        editorView.drawingManager.currentBrushColor = color;
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
                        if (isUpdating[0]) return;
                        if (s.length() == 7 && s.toString().startsWith("#")) {
                            try {
                                int newC = Color.parseColor(s.toString());
                                editorView.drawingManager.currentBrushColor = newC;
                                colorWheel.setColor(newC);
                            } catch (Exception ignored) {}
                        }
                    }
                    @Override public void afterTextChanged(Editable s) {}
                });

                btnEraser.setOnClickListener(eraserBtn -> {
                    editorView.drawingManager.isDrawEraserMode = !editorView.drawingManager.isDrawEraserMode;
                    btnEraser.setBackgroundTintList(ColorStateList.valueOf(editorView.drawingManager.isDrawEraserMode ? Color.parseColor("#FF3B30") : Color.parseColor("#E5E5EA")));
                    btnEraser.setTextColor(editorView.drawingManager.isDrawEraserMode ? Color.WHITE : Color.parseColor("#333333"));
                });

                btnApply.setOnClickListener(applyBtn -> {
                    editorView.drawingManager.currentBrushOpacity = (int) ((sbOpacity.getProgress() / 100f) * 255f);
                    editorView.startDrawing(sbSize.getProgress());
                    updateToolButtons();
                    dialog.dismiss();
                });
                dialog.show();
            });
        }

        // --- NEW CLONE TOOL CLICK LISTENER ---
        if (btnCloneTool != null) {
            btnCloneTool.setOnClickListener(v -> {
                if (editorView.isImageMissing()) {
                    Toast.makeText(this, "Load an image on the canvas first!", Toast.LENGTH_SHORT).show();
                    return;
                }

                Toast.makeText(this, "Preparing Clone Studio...", Toast.LENGTH_SHORT).show();
                btnCloneTool.setEnabled(false);
                btnCloneTool.setText("Loading...");

                new Thread(() -> {
                    Bitmap currentImage;
                    GraphicLayer active = editorView.getActiveLayer();

                    // IF A LAYER IS SELECTED, EXTRACT ONLY THAT LAYER
                    if (active != null) {
                        if (active.type == 1 && active.bitmap != null) {
                            currentImage = active.bitmap.copy(Bitmap.Config.ARGB_8888, true);
                        } else {
                            active.bake();
                            currentImage = active.bakedCache != null ? active.bakedCache.copy(Bitmap.Config.ARGB_8888, true) : editorView.getRenderedBitmap(true);
                        }
                    } else {
                        // NO LAYER SELECTED, BAKE ENTIRE SCREEN
                        currentImage = editorView.getRenderedBitmap(true);
                    }

                    try {
                        File tempIn = new File(getCacheDir(), "clone_in.png");
                        FileOutputStream fos = new FileOutputStream(tempIn);
                        currentImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.close();

                        runOnUiThread(() -> {
                            btnCloneTool.setEnabled(true);
                            btnCloneTool.setText("Clone Tool"); // Reset text
                            Intent intent = new Intent(ImageEditorActivity.this, CloneActivity.class);
                            intent.putExtra("image_path", tempIn.getAbsolutePath());
                            cloneLauncher.launch(intent);
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            btnCloneTool.setEnabled(true);
                            btnCloneTool.setText("Clone Tool");
                            Toast.makeText(ImageEditorActivity.this, "Failed to launch Clone Tool", Toast.LENGTH_SHORT).show();
                        });
                    }
                }).start();
            });
        }

        if (btnAdvancedCanvas != null) {
            btnAdvancedCanvas.setOnClickListener(v -> {
                if (editorView.isImageMissing()) {
                    showEmptyCanvasColorPicker();
                    return;
                }

                Toast.makeText(this, "Preparing Canvas...", Toast.LENGTH_SHORT).show();
                btnAdvancedCanvas.setEnabled(false);
                btnAdvancedCanvas.setText("Loading...");

                new Thread(() -> {
                    Bitmap currentImage;
                    GraphicLayer active = editorView.getActiveLayer();

                    // IF A LAYER IS SELECTED, EXTRACT ONLY THAT LAYER
                    if (active != null) {
                        if (active.type == 1 && active.bitmap != null) {
                            currentImage = active.bitmap.copy(Bitmap.Config.ARGB_8888, true);
                        } else {
                            active.bake();
                            currentImage = active.bakedCache != null ? active.bakedCache.copy(Bitmap.Config.ARGB_8888, true) : editorView.getRenderedBitmap(true);
                        }
                    } else {
                        // NO LAYER SELECTED, BAKE ENTIRE SCREEN
                        currentImage = editorView.getRenderedBitmap(true);
                    }

                    try {
                        File tempIn = new File(getCacheDir(), "canvas_in.png");
                        FileOutputStream fos = new FileOutputStream(tempIn);
                        currentImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.close();

                        runOnUiThread(() -> {
                            btnAdvancedCanvas.setEnabled(true);
                            btnAdvancedCanvas.setText("Pro Canvas");
                            Intent intent = new Intent(ImageEditorActivity.this, AdvancedCanvasActivity.class);
                            intent.putExtra("image_path", tempIn.getAbsolutePath());
                            advancedCanvasLauncher.launch(intent);
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            btnAdvancedCanvas.setEnabled(true);
                            btnAdvancedCanvas.setText("Pro Canvas");
                            Toast.makeText(ImageEditorActivity.this, "Failed to launch Canvas", Toast.LENGTH_SHORT).show();
                        });
                    }
                }).start();
            });
        }

        if (btnClear != null) btnClear.setOnClickListener(btnView -> { if (editorView.isImageMissing()) return; editorView.clearModifications(); if (rightToolsPanel != null) rightToolsPanel.setVisibility(View.GONE); });

        if (btnExport != null) {
            btnExport.setOnClickListener(btnView -> {
                if (editorView.isImageMissing() || editorView.isCropping) return;
                editorView.deselectLayer();
                String[] formats = {
                        "PNG (Full High Quality)",
                        "PNG (Compressed Version)",
                        "JPG (Full High Quality)",
                        "JPG (Compressed Version)",
                        "WEBP (Full High Quality)",
                        "WEBP (Compressed Version)"
                };
                AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
                builder.setTitle("Select Export Format").setItems(formats, (d, which) -> exportImage(which));
                AlertDialog dialog = builder.create();
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                    dialog.setOnShowListener(di -> {
                        if (dialog.getWindow() != null) {
                            forceDialogBackground(dialog.getWindow().getDecorView());
                            setDialogTextColor(dialog.getWindow().getDecorView(), isDarkTheme ? Color.WHITE : Color.BLACK);
                        }
                    });
                }
                dialog.show();
            });
        }
    }

    private void showEmptyCanvasColorPicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("Choose Canvas Color");
        title.setTextSize(20f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 32);
        mainLayout.addView(title);

        ColorWheelView colorWheel = new ColorWheelView(this);
        LinearLayout.LayoutParams wheelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 500);
        colorWheel.setLayoutParams(wheelLp);
        mainLayout.addView(colorWheel);

        LinearLayout hexRow = new LinearLayout(this);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER);
        hexRow.setPadding(0, 32, 0, 16);

        TextView hexLabel = new TextView(this);
        hexLabel.setText("HEX: ");
        hexLabel.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        hexLabel.setTypeface(null, Typeface.BOLD);

        EditText etHex = new EditText(this);
        etHex.setText("#FFFFFF");
        etHex.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        etHex.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        hexRow.addView(hexLabel);
        hexRow.addView(etHex);
        mainLayout.addView(hexRow);

        Button btnCreate = new Button(this);
        btnCreate.setText("Create Canvas");
        btnCreate.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E91E63")));
        btnCreate.setTextColor(Color.WHITE);

        final int[] selectedColor = {Color.WHITE};
        final boolean[] isUpdating = {false};

        colorWheel.setOnColorChangeListener(color -> {
            selectedColor[0] = color;
            if (!isUpdating[0]) {
                isUpdating[0] = true;
                etHex.setText(String.format("#%06X", (0xFFFFFF & color)));
                isUpdating[0] = false;
            }
        });

        etHex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUpdating[0]) return;
                if (s.length() == 7 && s.toString().startsWith("#")) {
                    try {
                        int newC = Color.parseColor(s.toString());
                        selectedColor[0] = newC;
                        isUpdating[0] = true;
                        colorWheel.setColor(newC);
                        isUpdating[0] = false;
                    } catch (Exception ignored) {}
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(0, 32, 0, 0);
        mainLayout.addView(btnCreate, btnLp);

        builder.setView(mainLayout);
        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnCreate.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(ImageEditorActivity.this, AdvancedCanvasActivity.class);
            intent.putExtra("bg_color", selectedColor[0]);
            advancedCanvasLauncher.launch(intent);
        });

        dialog.setOnShowListener(di -> {
            if (dialog.getWindow() != null) {
                View decorView = dialog.getWindow().getDecorView();
                forceDialogBackground(decorView);
            }
        });
        dialog.show();
    }

    private Button createToggleButton(String text, boolean active) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(10f);
        b.setPadding(8, 0, 8, 0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(active ? Color.parseColor("#4A90E2") : Color.parseColor("#3A3A3C")));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 100);
        lp.setMargins(4, 0, 4, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private View createSliderWithLabel(String label, float min, float max, float current, Slider.OnChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        tv.setTextSize(12f);
        tv.setTypeface(null, Typeface.BOLD);
        Slider slider = new Slider(this);
        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setValue(current);
        slider.addOnChangeListener(listener);
        row.addView(tv);
        row.addView(slider);
        return row;
    }

    private void showTextAddDialog(GraphicLayer layerToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText(layerToEdit == null ? "Add Text" : "Edit Text");
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 32);
        mainLayout.addView(title);

        FrameLayout previewContainer = new FrameLayout(this);
        previewContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 250));
        previewContainer.setBackgroundColor(Color.parseColor(isDarkTheme ? "#1C1C1E" : "#E5E5EA"));
        TextPreviewView previewView = new TextPreviewView(this);
        previewContainer.addView(previewView);
        mainLayout.addView(previewContainer);

        EditText etInput = new EditText(this);
        etInput.setHint("Type your text here...");
        etInput.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        etInput.setHintTextColor(Color.GRAY);
        etInput.setPadding(0, 32, 0, 32);
        etInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etInput.setSingleLine(false);
        etInput.setMaxLines(6);
        if (layerToEdit != null) {
            etInput.setText(layerToEdit.text);
            currentCustomFont = layerToEdit.typeface;
        } else {
            currentCustomFont = null;
        }
        mainLayout.addView(etInput);

        final Layout.Alignment[] currentAlign = {layerToEdit != null ? layerToEdit.align : Layout.Alignment.ALIGN_CENTER};

        LinearLayout alignGroup = new LinearLayout(this);
        alignGroup.setOrientation(LinearLayout.HORIZONTAL);
        alignGroup.setGravity(Gravity.CENTER);
        alignGroup.setPadding(0, 0, 0, 16);
        Button btnAlignLeft = createToggleButton("Left", currentAlign[0] == Layout.Alignment.ALIGN_NORMAL);
        Button btnAlignCenter = createToggleButton("Center", currentAlign[0] == Layout.Alignment.ALIGN_CENTER);
        Button btnAlignRight = createToggleButton("Right", currentAlign[0] == Layout.Alignment.ALIGN_OPPOSITE);
        alignGroup.addView(btnAlignLeft); alignGroup.addView(btnAlignCenter); alignGroup.addView(btnAlignRight);
        mainLayout.addView(alignGroup);

        LinearLayout targetGroup = new LinearLayout(this);
        targetGroup.setOrientation(LinearLayout.HORIZONTAL);
        targetGroup.setGravity(Gravity.CENTER);
        targetGroup.setPadding(0, 16, 0, 16);

        Button btnTargetMain = createToggleButton("Text", true);
        Button btnTargetStroke = createToggleButton("Stroke", false);
        Button btnTargetShadow = createToggleButton("Shadow", false);
        Button btnTargetInner = createToggleButton("Inner", false);

        targetGroup.addView(btnTargetMain); targetGroup.addView(btnTargetStroke);
        targetGroup.addView(btnTargetShadow); targetGroup.addView(btnTargetInner);
        mainLayout.addView(targetGroup);

        ColorWheelView colorWheel = new ColorWheelView(this);
        LinearLayout.LayoutParams wheelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400);
        colorWheel.setLayoutParams(wheelLp);
        mainLayout.addView(colorWheel);

        LinearLayout hexRow = new LinearLayout(this);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER);
        hexRow.setPadding(0, 16, 0, 16);

        EditText etHex = new EditText(this);
        etHex.setText("#FFFFFF");
        etHex.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);

        Button btnEyedropper = new Button(this);
        btnEyedropper.setText("🖌️ Pick");
        btnEyedropper.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4A90E2")));
        btnEyedropper.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pickLp.setMargins(16, 0, 0, 0);
        btnEyedropper.setLayoutParams(pickLp);

        hexRow.addView(etHex); hexRow.addView(btnEyedropper);
        mainLayout.addView(hexRow);

        LinearLayout pageTint = new LinearLayout(this); pageTint.setOrientation(LinearLayout.VERTICAL);
        LinearLayout pageStroke = new LinearLayout(this); pageStroke.setOrientation(LinearLayout.VERTICAL);
        LinearLayout pageShadow = new LinearLayout(this); pageShadow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout pageInner = new LinearLayout(this); pageInner.setOrientation(LinearLayout.VERTICAL);

        final float[] stateLetter = {layerToEdit != null ? layerToEdit.letterSpacing : 0f};
        final float[] stateLine = {layerToEdit != null ? layerToEdit.lineSpacing : 0f};
        final float[] stateStroke = {layerToEdit != null ? layerToEdit.strokeWidth : 0f};
        final float[] stateShadow = {layerToEdit != null ? layerToEdit.shadowRadius : 0f};
        final float[] stateInner = {layerToEdit != null ? layerToEdit.innerShadowRadius : 0f};
        final float[] stateShadX = {layerToEdit != null ? layerToEdit.shadowOffsetX : 0f};
        final float[] stateShadY = {layerToEdit != null ? layerToEdit.shadowOffsetY : 0f};
        final float[] stateInnerX = {layerToEdit != null ? layerToEdit.innerShadowOffsetX : 0f};
        final float[] stateInnerY = {layerToEdit != null ? layerToEdit.innerShadowOffsetY : 0f};

        final int[] textColors = {
                layerToEdit != null ? layerToEdit.color : Color.WHITE,
                layerToEdit != null ? layerToEdit.strokeColor : Color.BLACK,
                layerToEdit != null ? layerToEdit.shadowColor : Color.BLACK,
                layerToEdit != null ? layerToEdit.innerShadowColor : Color.BLACK
        };

        pageTint.addView(createSliderWithLabel("Letter Spacing", -0.5f, 1f, stateLetter[0], (Slider s, float v, boolean u) -> { stateLetter[0]=v; dialogUpdateRunnable.run(); }));
        pageTint.addView(createSliderWithLabel("Line Spacing", 0f, 100f, stateLine[0], (Slider s, float v, boolean u) -> { stateLine[0]=v; dialogUpdateRunnable.run(); }));

        Button btnPickFont = new Button(this);
        btnPickFont.setText("Choose Custom Font (.ttf / .otf)");
        btnPickFont.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4A90E2")));
        btnPickFont.setTextColor(Color.WHITE);
        pageTint.addView(btnPickFont);

        activeFontLabel = new TextView(this);
        activeFontLabel.setText(currentCustomFont != null ? "Custom Font Loaded" : "Default Font Selected");
        activeFontLabel.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        activeFontLabel.setGravity(Gravity.CENTER);
        activeFontLabel.setPadding(0, 16, 0, 16);
        pageTint.addView(activeFontLabel);

        pageStroke.addView(createSliderWithLabel("Stroke Thickness", 0f, 50f, stateStroke[0], (Slider s, float v, boolean u) -> { stateStroke[0]=v; dialogUpdateRunnable.run(); }));

        pageShadow.addView(createSliderWithLabel("Shadow Blur", 0f, 50f, stateShadow[0], (Slider s, float v, boolean u) -> { stateShadow[0]=v; dialogUpdateRunnable.run(); }));
        pageShadow.addView(createSliderWithLabel("Shadow Offset X", -100f, 100f, stateShadX[0], (Slider s, float v, boolean u) -> { stateShadX[0]=v; dialogUpdateRunnable.run(); }));
        pageShadow.addView(createSliderWithLabel("Shadow Offset Y", -100f, 100f, stateShadY[0], (Slider s, float v, boolean u) -> { stateShadY[0]=v; dialogUpdateRunnable.run(); }));

        pageInner.addView(createSliderWithLabel("Inner Shadow Blur", 0f, 50f, stateInner[0], (Slider s, float v, boolean u) -> { stateInner[0]=v; dialogUpdateRunnable.run(); }));
        pageInner.addView(createSliderWithLabel("Inner Offset X", -100f, 100f, stateInnerX[0], (Slider s, float v, boolean u) -> { stateInnerX[0]=v; dialogUpdateRunnable.run(); }));
        pageInner.addView(createSliderWithLabel("Inner Offset Y", -100f, 100f, stateInnerY[0], (Slider s, float v, boolean u) -> { stateInnerY[0]=v; dialogUpdateRunnable.run(); }));

        mainLayout.addView(pageTint);
        mainLayout.addView(pageStroke);
        mainLayout.addView(pageShadow);
        mainLayout.addView(pageInner);

        pageStroke.setVisibility(View.GONE);
        pageShadow.setVisibility(View.GONE);
        pageInner.setVisibility(View.GONE);

        Button btnApply = new Button(this);
        btnApply.setText(layerToEdit == null ? "Add to Image" : "Update Image");
        btnApply.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#34C759")));
        btnApply.setTextColor(Color.WHITE);
        mainLayout.addView(btnApply);

        ScrollView scrollWrapper = new ScrollView(this);
        scrollWrapper.addView(mainLayout);
        builder.setView(scrollWrapper);

        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        activeColorTarget = 0;
        colorWheel.setColor(textColors[0]);
        etHex.setText(String.format("#%06X", (0xFFFFFF & textColors[0])));

        View.OnClickListener alignListener = aBtn -> {
            btnAlignLeft.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3A3A3C")));
            btnAlignCenter.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3A3A3C")));
            btnAlignRight.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3A3A3C")));
            aBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4A90E2")));
            if (aBtn == btnAlignLeft) currentAlign[0] = Layout.Alignment.ALIGN_NORMAL;
            else if (aBtn == btnAlignCenter) currentAlign[0] = Layout.Alignment.ALIGN_CENTER;
            else if (aBtn == btnAlignRight) currentAlign[0] = Layout.Alignment.ALIGN_OPPOSITE;
            dialogUpdateRunnable.run();
        };
        btnAlignLeft.setOnClickListener(alignListener); btnAlignCenter.setOnClickListener(alignListener); btnAlignRight.setOnClickListener(alignListener);

        View.OnClickListener targetListener = tBtn -> {
            btnTargetMain.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3A3A3C")));
            btnTargetStroke.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3A3A3C")));
            btnTargetShadow.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3A3A3C")));
            btnTargetInner.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3A3A3C")));
            tBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4A90E2")));

            if (tBtn == btnTargetMain) activeColorTarget = 0;
            else if (tBtn == btnTargetStroke) activeColorTarget = 1;
            else if (tBtn == btnTargetShadow) activeColorTarget = 2;
            else if (tBtn == btnTargetInner) activeColorTarget = 3;

            colorWheel.setColor(textColors[activeColorTarget]);
            etHex.setText(String.format("#%06X", (0xFFFFFF & textColors[activeColorTarget])));

            pageTint.setVisibility(activeColorTarget == 0 ? View.VISIBLE : View.GONE);
            pageStroke.setVisibility(activeColorTarget == 1 ? View.VISIBLE : View.GONE);
            pageShadow.setVisibility(activeColorTarget == 2 ? View.VISIBLE : View.GONE);
            pageInner.setVisibility(activeColorTarget == 3 ? View.VISIBLE : View.GONE);
        };
        btnTargetMain.setOnClickListener(targetListener); btnTargetStroke.setOnClickListener(targetListener);
        btnTargetShadow.setOnClickListener(targetListener); btnTargetInner.setOnClickListener(targetListener);

        dialogUpdateRunnable = () -> {
            if (activeFontLabel != null) activeFontLabel.setText(currentCustomFont != null ? "Custom Font Loaded" : "Default Font Selected");
            String input = etInput.getText().toString(); if (input.isEmpty()) input = "Preview Text";

            GraphicLayer pLayer = new GraphicLayer(input, currentCustomFont, textColors[0], currentAlign[0],
                    stateLetter[0], stateLine[0], stateStroke[0], textColors[1],
                    stateShadow[0], textColors[2], stateInner[0], textColors[3], 0, 0);
            pLayer.shadowOffsetX = stateShadX[0]; pLayer.shadowOffsetY = stateShadY[0];
            pLayer.innerShadowOffsetX = stateInnerX[0]; pLayer.innerShadowOffsetY = stateInnerY[0];
            pLayer.isDirty = true;

            previewView.setPreviewLayer(pLayer);
        };

        boolean[] isUpdating = {false};
        colorWheel.setOnColorChangeListener(color -> {
            textColors[activeColorTarget] = color;
            if (!isUpdating[0]) {
                isUpdating[0] = true;
                etHex.setText(String.format("#%06X", (0xFFFFFF & color)));
                isUpdating[0] = false;
            }
            dialogUpdateRunnable.run();
        });

        etHex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUpdating[0]) return;
                if (s.length() == 7 && s.toString().startsWith("#")) {
                    try {
                        int newC = Color.parseColor(s.toString());
                        textColors[activeColorTarget] = newC;
                        colorWheel.setColor(newC); dialogUpdateRunnable.run();
                    } catch (Exception ignored) {}
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { dialogUpdateRunnable.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnEyedropper.setOnClickListener(eyeBtn -> {
            dialog.hide();
            editorView.startEyedropper(color -> {
                dialog.show();
                textColors[activeColorTarget] = color;
                colorWheel.setColor(color);
                etHex.setText(String.format("#%06X", (0xFFFFFF & color)));
                dialogUpdateRunnable.run();
            });
        });

        btnPickFont.setOnClickListener(fontBtn -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf"});
            pickFontLauncher.launch(intent);
        });

        btnApply.setOnClickListener(applyBtn -> {
            String input = etInput.getText().toString().trim();
            if (!input.isEmpty()) {
                if (layerToEdit != null) {
                    layerToEdit.text = input;
                    layerToEdit.typeface = currentCustomFont;
                    layerToEdit.color = textColors[0];
                    layerToEdit.align = currentAlign[0];
                    layerToEdit.letterSpacing = stateLetter[0];
                    layerToEdit.lineSpacing = stateLine[0];
                    layerToEdit.strokeWidth = stateStroke[0];
                    layerToEdit.strokeColor = textColors[1];
                    layerToEdit.shadowRadius = stateShadow[0];
                    layerToEdit.shadowColor = textColors[2];
                    layerToEdit.innerShadowRadius = stateInner[0];
                    layerToEdit.innerShadowColor = textColors[3];
                    layerToEdit.shadowOffsetX = stateShadX[0];
                    layerToEdit.shadowOffsetY = stateShadY[0];
                    layerToEdit.innerShadowOffsetX = stateInnerX[0];
                    layerToEdit.innerShadowOffsetY = stateInnerY[0];

                    if (layerToEdit.textPaint == null) layerToEdit.textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    layerToEdit.textPaint.setTypeface(layerToEdit.typeface);
                    layerToEdit.textPaint.setLetterSpacing(layerToEdit.letterSpacing);

                    layerToEdit.buildStaticLayout();
                    layerToEdit.isDirty = true;
                    editorView.invalidate();
                } else {
                    GraphicLayer newLayer = new GraphicLayer(input, currentCustomFont, textColors[0], currentAlign[0],
                            stateLetter[0], stateLine[0], stateStroke[0], textColors[1],
                            stateShadow[0], textColors[2], stateInner[0], textColors[3], 0, 0);
                    newLayer.shadowOffsetX = stateShadX[0]; newLayer.shadowOffsetY = stateShadY[0];
                    newLayer.innerShadowOffsetX = stateInnerX[0]; newLayer.innerShadowOffsetY = stateInnerY[0];
                    newLayer.isDirty = true;

                    editorView.deselectLayer();
                    editorView.getLayers().add(newLayer); editorView.setActiveLayer(newLayer);
                    editorView.getUndoStack().add(new ActionRecord(newLayer));
                    if (editorView.getLayerListener() != null) editorView.getLayerListener().run();
                    editorView.invalidate();
                }
            }
            dialog.dismiss();
        });

        dialog.setOnShowListener(di -> {
            if (dialog.getWindow() != null) forceDialogBackground(dialog.getWindow().getDecorView());
        });
        dialogUpdateRunnable.run();
        dialog.show();
    }

    private void showLayerEditDialog(GraphicLayer layer) {
        if (layer == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("Edit Layer");
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 32);
        mainLayout.addView(title);

        LinearLayout targetGroup = new LinearLayout(this);
        targetGroup.setOrientation(LinearLayout.HORIZONTAL);
        targetGroup.setGravity(Gravity.CENTER);
        targetGroup.setPadding(0, 0, 0, 16);

        Button btnTargetMain = createToggleButton("Tint", true);
        Button btnTargetStroke = createToggleButton("Stroke", false);
        Button btnTargetShadow = createToggleButton("Shadow", false);
        Button btnTargetInner = createToggleButton("Inner", false);

        targetGroup.addView(btnTargetMain); targetGroup.addView(btnTargetStroke);
        targetGroup.addView(btnTargetShadow); targetGroup.addView(btnTargetInner);
        mainLayout.addView(targetGroup);

        ColorWheelView colorWheel = new ColorWheelView(this);
        LinearLayout.LayoutParams wheelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400);
        colorWheel.setLayoutParams(wheelLp);
        mainLayout.addView(colorWheel);

        LinearLayout hexRow = new LinearLayout(this);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER);
        hexRow.setPadding(0, 16, 0, 16);

        EditText etHex = new EditText(this);
        etHex.setText(String.format("#%06X", (0xFFFFFF & layer.color)));
        etHex.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);

        Button btnEyedropper = new Button(this);
        btnEyedropper.setText("🖌️ Pick");
        btnEyedropper.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4A90E2")));
        btnEyedropper.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pickLp.setMargins(16, 0, 0, 0);
        btnEyedropper.setLayoutParams(pickLp);

        hexRow.addView(etHex); hexRow.addView(btnEyedropper);
        mainLayout.addView(hexRow);

        LinearLayout pageTint = new LinearLayout(this); pageTint.setOrientation(LinearLayout.VERTICAL);
        LinearLayout pageStroke = new LinearLayout(this); pageStroke.setOrientation(LinearLayout.VERTICAL);
        LinearLayout pageShadow = new LinearLayout(this); pageShadow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout pageInner = new LinearLayout(this); pageInner.setOrientation(LinearLayout.VERTICAL);

        Button btnCropLayer = null;
        if (layer.type == 1) {
            btnCropLayer = new Button(this);
            btnCropLayer.setText("Crop Image Layer");
            btnCropLayer.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9500"))); // Orange
            btnCropLayer.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams cropLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cropLp.setMargins(0, 16, 0, 16);
            btnCropLayer.setLayoutParams(cropLp);
            pageTint.addView(btnCropLayer, 0);
        }

        pageTint.addView(createSliderWithLabel("Transparency", 0, 255, layer.alpha, (Slider s, float v, boolean u) -> { layer.alpha = (int)v; layer.isDirty=true; editorView.invalidate(); }));
        pageTint.addView(createSliderWithLabel("Rotation", -180, 180, layer.rotation, (Slider s, float v, boolean u) -> { layer.rotation = v; editorView.invalidate(); }));

        LinearLayout mirrorRow = new LinearLayout(this);
        mirrorRow.setOrientation(LinearLayout.HORIZONTAL);
        mirrorRow.setGravity(Gravity.CENTER);
        mirrorRow.setPadding(0, 16, 0, 16);

        Button btnMirrorH = new Button(this); btnMirrorH.setText("Mirror ↔"); btnMirrorH.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9500"))); btnMirrorH.setTextColor(Color.WHITE);
        Button btnMirrorV = new Button(this); btnMirrorV.setText("Mirror ↕"); btnMirrorV.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9500"))); btnMirrorV.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(8, 0, 8, 0);
        btnMirrorH.setLayoutParams(btnLp); btnMirrorV.setLayoutParams(btnLp);

        btnMirrorH.setOnClickListener(btnH -> { layer.flipX = !layer.flipX; layer.isDirty=true; editorView.invalidate(); });
        btnMirrorV.setOnClickListener(btnV -> { layer.flipY = !layer.flipY; layer.isDirty=true; editorView.invalidate(); });

        mirrorRow.addView(btnMirrorH); mirrorRow.addView(btnMirrorV);
        pageTint.addView(mirrorRow);

        pageStroke.addView(createSliderWithLabel("Stroke Thickness", 0, 50, layer.strokeWidth, (Slider s, float v, boolean u) -> { layer.strokeWidth = v; layer.isDirty=true; editorView.invalidate(); }));

        pageShadow.addView(createSliderWithLabel("Shadow Blur", 0, 50, layer.shadowRadius, (Slider s, float v, boolean u) -> { layer.shadowRadius = v; layer.isDirty=true; editorView.invalidate(); }));
        pageShadow.addView(createSliderWithLabel("Shadow Offset X", -100f, 100f, layer.shadowOffsetX, (Slider s, float v, boolean u) -> { layer.shadowOffsetX = v; layer.isDirty=true; editorView.invalidate(); }));
        pageShadow.addView(createSliderWithLabel("Shadow Offset Y", -100f, 100f, layer.shadowOffsetY, (Slider s, float v, boolean u) -> { layer.shadowOffsetY = v; layer.isDirty=true; editorView.invalidate(); }));

        pageInner.addView(createSliderWithLabel("Inner Shadow Blur", 0, 50, layer.innerShadowRadius, (Slider s, float v, boolean u) -> { layer.innerShadowRadius = v; layer.isDirty=true; editorView.invalidate(); }));
        pageInner.addView(createSliderWithLabel("Inner Offset X", -100f, 100f, layer.innerShadowOffsetX, (Slider s, float v, boolean u) -> { layer.innerShadowOffsetX = v; layer.isDirty=true; editorView.invalidate(); }));
        pageInner.addView(createSliderWithLabel("Inner Offset Y", -100f, 100f, layer.innerShadowOffsetY, (Slider s, float v, boolean u) -> { layer.innerShadowOffsetY = v; layer.isDirty=true; editorView.invalidate(); }));

        mainLayout.addView(pageTint);
        mainLayout.addView(pageStroke);
        mainLayout.addView(pageShadow);
        mainLayout.addView(pageInner);

        pageStroke.setVisibility(View.GONE);
        pageShadow.setVisibility(View.GONE);
        pageInner.setVisibility(View.GONE);

        Button btnApply = new Button(this);
        btnApply.setText("Done");
        btnApply.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#34C759")));
        btnApply.setTextColor(Color.WHITE);
        mainLayout.addView(btnApply);

        ScrollView scrollWrapper = new ScrollView(this);
        scrollWrapper.addView(mainLayout);
        builder.setView(scrollWrapper);

        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        if (btnCropLayer != null) {
            btnCropLayer.setOnClickListener(v -> {
                dialog.dismiss();
                editorView.startLayerCrop();
                if (rightToolsPanel != null) rightToolsPanel.setVisibility(View.GONE);
                if (cropToolsBar != null) cropToolsBar.setVisibility(View.VISIBLE);
            });
        }

        activeColorTarget = 0;

        View.OnClickListener targetListener = tBtn -> {
            btnTargetMain.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3A3A3C")));
            btnTargetStroke.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3A3A3C")));
            btnTargetShadow.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3A3A3C")));
            btnTargetInner.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3A3A3C")));
            tBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4A90E2")));

            if (tBtn == btnTargetMain) activeColorTarget = 0;
            else if (tBtn == btnTargetStroke) activeColorTarget = 1;
            else if (tBtn == btnTargetShadow) activeColorTarget = 2;
            else if (tBtn == btnTargetInner) activeColorTarget = 3;

            int displayColor = Color.WHITE;
            if (activeColorTarget == 0) displayColor = layer.color;
            else if (activeColorTarget == 1) displayColor = layer.strokeColor;
            else if (activeColorTarget == 2) displayColor = layer.shadowColor;
            else if (activeColorTarget == 3) displayColor = layer.innerShadowColor;

            colorWheel.setColor(displayColor);
            etHex.setText(String.format("#%06X", (0xFFFFFF & displayColor)));

            pageTint.setVisibility(activeColorTarget == 0 ? View.VISIBLE : View.GONE);
            pageStroke.setVisibility(activeColorTarget == 1 ? View.VISIBLE : View.GONE);
            pageShadow.setVisibility(activeColorTarget == 2 ? View.VISIBLE : View.GONE);
            pageInner.setVisibility(activeColorTarget == 3 ? View.VISIBLE : View.GONE);
        };
        btnTargetMain.setOnClickListener(targetListener); btnTargetStroke.setOnClickListener(targetListener);
        btnTargetShadow.setOnClickListener(targetListener); btnTargetInner.setOnClickListener(targetListener);

        boolean[] isUpdating = {false};
        colorWheel.setOnColorChangeListener(color -> {
            if (activeColorTarget == 0) layer.color = color;
            else if (activeColorTarget == 1) layer.strokeColor = color;
            else if (activeColorTarget == 2) layer.shadowColor = color;
            else if (activeColorTarget == 3) layer.innerShadowColor = color;

            if (!isUpdating[0]) {
                isUpdating[0] = true;
                etHex.setText(String.format("#%06X", (0xFFFFFF & color)));
                isUpdating[0] = false;
            }
            layer.isDirty = true;
            editorView.invalidate();
        });

        etHex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUpdating[0]) return;
                if (s.length() == 7 && s.toString().startsWith("#")) {
                    try {
                        int newC = Color.parseColor(s.toString());
                        if (activeColorTarget == 0) layer.color = newC;
                        else if (activeColorTarget == 1) layer.strokeColor = newC;
                        else if (activeColorTarget == 2) layer.shadowColor = newC;
                        else if (activeColorTarget == 3) layer.innerShadowColor = newC;
                        colorWheel.setColor(newC);
                        layer.isDirty = true;
                        editorView.invalidate();
                    } catch (Exception ignored) {}
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnEyedropper.setOnClickListener(eyeBtn -> {
            dialog.hide();
            editorView.startEyedropper(color -> {
                dialog.show();
                if (activeColorTarget == 0) layer.color = color;
                else if (activeColorTarget == 1) layer.strokeColor = color;
                else if (activeColorTarget == 2) layer.shadowColor = color;
                else if (activeColorTarget == 3) layer.innerShadowColor = color;
                colorWheel.setColor(color);
                etHex.setText(String.format("#%06X", (0xFFFFFF & color)));
                layer.isDirty = true;
                editorView.invalidate();
            });
        });

        dialog.setOnShowListener(di -> {
            if (dialog.getWindow() != null) {
                View decorView = dialog.getWindow().getDecorView();
                forceDialogBackground(decorView);
                setDialogTextColor(decorView, isDarkTheme ? Color.WHITE : Color.BLACK);
            }
        });

        btnApply.setOnClickListener(doneBtn -> dialog.dismiss());
        dialog.show();
    }

    private void updateToolButtons() {
        if (btnZoom == null || btnGrid == null) return;
        int activeColor = Color.parseColor("#34C759"); // Green
        int defaultBg = isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA");
        int defaultText = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");

        btnZoom.setBackgroundTintList(ColorStateList.valueOf(editorView.isZoomMode ? activeColor : defaultBg));
        btnZoom.setTextColor(editorView.isZoomMode ? Color.WHITE : defaultText);
        btnZoom.setText(editorView.isZoomMode ? "Zoom/Pan: ON" : "Zoom/Pan: OFF");

        btnGrid.setBackgroundTintList(ColorStateList.valueOf(editorView.isGridMode ? activeColor : defaultBg));
        btnGrid.setTextColor(editorView.isGridMode ? Color.WHITE : defaultText);
        btnGrid.setText(editorView.isGridMode ? "Grid: ON" : "Grid: OFF");

        Button dBtn = findViewById(R.id.btnDraw);
        if (dBtn != null) {
            dBtn.setBackgroundTintList(ColorStateList.valueOf(editorView.drawingManager.isDrawMode ? activeColor : defaultBg));
            dBtn.setTextColor(editorView.drawingManager.isDrawMode ? Color.WHITE : defaultText);
            dBtn.setText(editorView.drawingManager.isDrawMode ? "Draw: ON" : "Draw Tool");
        }
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
                if (dialog.getWindow() != null) {
                    forceDialogBackground(dialog.getWindow().getDecorView());
                    setDialogTextColor(dialog.getWindow().getDecorView(), isDarkTheme ? Color.WHITE : Color.BLACK);
                }
                int buttonTextColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");
                if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonTextColor);
                if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonTextColor);
                if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(buttonTextColor);
            });
        }
        dialog.show();
    }

    @SuppressLint("NotifyDataSetChanged")
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

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight || (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private void loadImage(Uri uri, boolean isOverlay) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            InputStream is = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(is, null, options);
            if (is != null) is.close();

            int maxDimension = 4096;
            options.inSampleSize = calculateInSampleSize(options, maxDimension, maxDimension);
            options.inJustDecodeBounds = false;

            is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            if (is != null) is.close();

            if (bitmap != null) {
                if (isOverlay) {
                    editorView.addImageLayer(bitmap);
                    if (leftLayersPanel != null) leftLayersPanel.setVisibility(View.VISIBLE);
                } else {
                    if (tapToStartView != null) tapToStartView.setVisibility(View.GONE);
                    editorView.setImage(bitmap);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load image safely", Toast.LENGTH_SHORT).show();
        }
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
                String extension, mimeType;
                Bitmap.CompressFormat compressFormat;
                int quality = 100;
                Bitmap exportBmp = finalImage;

                boolean scaleDown = false;

                if (formatIndex == 0) {
                    extension = ".png"; mimeType = "image/png"; compressFormat = Bitmap.CompressFormat.PNG;
                } else if (formatIndex == 1) {
                    extension = ".png"; mimeType = "image/png"; compressFormat = Bitmap.CompressFormat.PNG;
                    scaleDown = true;
                } else if (formatIndex == 2) {
                    extension = ".jpg"; mimeType = "image/jpeg"; compressFormat = Bitmap.CompressFormat.JPEG;
                } else if (formatIndex == 3) {
                    extension = ".jpg"; mimeType = "image/jpeg"; compressFormat = Bitmap.CompressFormat.JPEG;
                    quality = 60;
                    scaleDown = true;
                } else if (formatIndex == 4) {
                    extension = ".webp"; mimeType = "image/webp";
                    compressFormat = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ? Bitmap.CompressFormat.WEBP_LOSSLESS : Bitmap.CompressFormat.WEBP;
                    quality = 100;
                } else {
                    extension = ".webp"; mimeType = "image/webp";
                    compressFormat = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
                    quality = 60;
                    scaleDown = true;
                }

                if (scaleDown) {
                    exportBmp = Bitmap.createScaledBitmap(finalImage, Math.max(1, finalImage.getWidth()/2), Math.max(1, finalImage.getHeight()/2), true);
                }

                String fileName = "Edited_" + System.currentTimeMillis() + extension;
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OWN's Image studio");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                }

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os != null) { exportBmp.compress(compressFormat, quality, os); os.close(); }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0);
                        getContentResolver().update(uri, values, null, null);
                    }
                    runOnUiThread(() -> Toast.makeText(this, "Saved to Pictures/OWN's Image studio", Toast.LENGTH_LONG).show());
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

        @SuppressLint("NotifyDataSetChanged")
        public void moveItem(int fromPosition, int toPosition) {
            List<GraphicLayer> original = editorView.getLayers();
            List<GraphicLayer> reversed = new ArrayList<>(original);
            Collections.reverse(reversed);

            GraphicLayer moved = reversed.remove(fromPosition);
            reversed.add(toPosition, moved);

            Collections.reverse(reversed);
            original.clear();
            original.addAll(reversed);

            notifyDataSetChanged();
            editorView.invalidate();
        }
    }

    public static class TextPreviewView extends View {
        public GraphicLayer previewLayer;
        public TextPreviewView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        public void setPreviewLayer(GraphicLayer layer) {
            this.previewLayer = layer;
            if (previewLayer != null) previewLayer.bake();
            invalidate();
        }
        @Override protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (previewLayer != null) {
                float viewW = getWidth();
                float viewH = getHeight();
                float layerW = previewLayer.bounds.width() + previewLayer.currentPad * 2;
                float layerH = previewLayer.bounds.height() + previewLayer.currentPad * 2;

                float scale = 1f;
                if (layerW > 0 && layerH > 0) {
                    float scaleX = (viewW - 80) / layerW;
                    float scaleY = (viewH - 80) / layerH;
                    scale = Math.min(scaleX, scaleY);
                    if (scale > 1f) scale = 1f;
                }

                canvas.save();
                canvas.translate(viewW / 2f, viewH / 2f);
                canvas.scale(scale, scale);
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

    public static class GraphicLayer {
        int type;
        float x, y, scaleX = 1f, scaleY = 1f, rotation = 0f;
        int alpha = 255;
        boolean flipX = false, flipY = false;
        RectF bounds = new RectF();
        String text; Bitmap bitmap;

        Typeface typeface; int color, strokeColor, shadowColor, innerShadowColor;
        Layout.Alignment align; float letterSpacing, lineSpacing, strokeWidth, shadowRadius, innerShadowRadius;

        float shadowOffsetX = 0f, shadowOffsetY = 0f;
        float innerShadowOffsetX = 0f, innerShadowOffsetY = 0f;

        TextPaint textPaint;
        StaticLayout staticLayout;

        Bitmap bakedCache;
        Bitmap alphaCache;
        float currentPad = 0f;
        boolean isDirty = true;

        Paint renderPaint;

        GraphicLayer(GraphicLayer src) {
            this.type = src.type; this.x = src.x + 50f; this.y = src.y + 50f;
            this.scaleX = src.scaleX; this.scaleY = src.scaleY; this.rotation = src.rotation; this.bounds = new RectF(src.bounds);
            this.text = src.text; this.bitmap = src.bitmap;
            this.typeface = src.typeface; this.color = src.color; this.strokeColor = src.strokeColor; this.shadowColor = src.shadowColor;
            this.innerShadowColor = src.innerShadowColor; this.innerShadowRadius = src.innerShadowRadius;
            this.align = src.align; this.letterSpacing = src.letterSpacing; this.lineSpacing = src.lineSpacing;
            this.strokeWidth = src.strokeWidth; this.shadowRadius = src.shadowRadius;
            this.alpha = src.alpha; this.flipX = src.flipX; this.flipY = src.flipY;
            this.shadowOffsetX = src.shadowOffsetX; this.shadowOffsetY = src.shadowOffsetY;
            this.innerShadowOffsetX = src.innerShadowOffsetX; this.innerShadowOffsetY = src.innerShadowOffsetY;

            renderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            renderPaint.setDither(true);

            if (src.textPaint != null) this.textPaint = new TextPaint(src.textPaint);
            if (type == 0) buildStaticLayout(); else updateBounds();

            this.currentPad = src.currentPad;
            this.isDirty = true;
            this.bakedCache = null;
            if (src.alphaCache != null && !src.alphaCache.isRecycled()) {
                Bitmap.Config conf = src.alphaCache.getConfig();
                if (conf == null) conf = Bitmap.Config.ARGB_8888;
                this.alphaCache = src.alphaCache.copy(conf, true);
            }
        }

        GraphicLayer(String text, Typeface typeface, int color, Layout.Alignment align, float letterSpacing, float lineSpacing, float strokeWidth, int strokeColor, float shadowRadius, int shadowColor, float innerShadowRadius, int innerShadowColor, float x, float y) {
            this.type = 0; this.text = text; this.typeface = typeface; this.color = color;
            this.align = align; this.letterSpacing = letterSpacing; this.lineSpacing = lineSpacing;
            this.strokeWidth = strokeWidth; this.strokeColor = strokeColor;
            this.shadowRadius = shadowRadius; this.shadowColor = shadowColor;
            this.innerShadowRadius = innerShadowRadius; this.innerShadowColor = innerShadowColor;
            this.x = x; this.y = y;

            renderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            renderPaint.setDither(true);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(250f);
            textPaint.setStrokeJoin(Paint.Join.ROUND);
            textPaint.setStrokeCap(Paint.Cap.ROUND);
            if (typeface != null) textPaint.setTypeface(typeface);
            textPaint.setLetterSpacing(letterSpacing);
            buildStaticLayout();
            this.isDirty = true;
        }

        GraphicLayer(Bitmap bitmap, float x, float y) {
            this.type = 1; this.bitmap = bitmap; this.x = x; this.y = y;
            this.color = Color.WHITE; this.strokeColor = Color.BLACK; this.shadowColor = Color.BLACK; this.innerShadowColor = Color.BLACK;
            renderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            renderPaint.setDither(true);
            updateBounds();
            this.isDirty = true;
        }

        void buildStaticLayout() {
            float maxWidth = 0;
            String[] lines = text.split("\n");
            for (String line : lines) {
                float w = textPaint.measureText(line);
                if (w > maxWidth) maxWidth = w;
            }
            int width = (int) maxWidth + 20;

            staticLayout = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, Math.max(10, width))
                    .setAlignment(align).setLineSpacing(lineSpacing, 1.0f).build();
            bounds.set(-staticLayout.getWidth() / 2f, -staticLayout.getHeight() / 2f, staticLayout.getWidth() / 2f, staticLayout.getHeight() / 2f);
        }

        void updateBounds() {
            if (type == 1 && bitmap != null) {
                bounds.set(-bitmap.getWidth()/2f, -bitmap.getHeight()/2f, bitmap.getWidth()/2f, bitmap.getHeight()/2f);
                if (alphaCache != null && !alphaCache.isRecycled()) alphaCache.recycle();
                alphaCache = bitmap.extractAlpha();
            }
        }

        float getPadding() {
            float maxOffset = Math.max(Math.max(Math.abs(shadowOffsetX), Math.abs(shadowOffsetY)), Math.max(Math.abs(innerShadowOffsetX), Math.abs(innerShadowOffsetY)));
            if (type == 0) {
                return Math.max(strokeWidth, Math.max(shadowRadius, innerShadowRadius)) * 2f + maxOffset + 20f;
            } else {
                float imgScaleFactor = Math.max(bitmap.getWidth(), bitmap.getHeight()) / 300f;
                if (imgScaleFactor < 1f) imgScaleFactor = 1f;
                return (Math.max(strokeWidth, Math.max(shadowRadius, innerShadowRadius)) * imgScaleFactor * 2f) + (maxOffset * imgScaleFactor) + 20f;
            }
        }

        void bake() {
            if (type == 0 && staticLayout == null) return;
            if (type == 1 && bitmap == null) return;

            currentPad = getPadding();
            int bw, bh;
            if (type == 0) {
                bw = staticLayout.getWidth() + (int)(currentPad * 2);
                bh = staticLayout.getHeight() + (int)(currentPad * 2);
            } else {
                bw = bitmap.getWidth() + (int)(currentPad * 2);
                bh = bitmap.getHeight() + (int)(currentPad * 2);
            }

            if (bw <= 0 || bh <= 0) return;

            if (bakedCache != null && bakedCache.getWidth() == bw && bakedCache.getHeight() == bh) {
                bakedCache.eraseColor(Color.TRANSPARENT);
            } else {
                if (bakedCache != null && !bakedCache.isRecycled()) bakedCache.recycle();
                try { bakedCache = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); }
                catch (OutOfMemoryError e) { return; }
            }

            Canvas c = new Canvas(bakedCache);
            c.translate(currentPad, currentPad);

            if (type == 0) {
                if (shadowRadius > 0.1f || shadowOffsetX != 0 || shadowOffsetY != 0) {
                    textPaint.setStyle(Paint.Style.FILL);
                    textPaint.setShadowLayer(shadowRadius > 0 ? shadowRadius : 1f, shadowOffsetX, shadowOffsetY, shadowColor);
                    textPaint.setColor(color);
                    textPaint.setAlpha(alpha);
                    staticLayout.draw(c);
                    textPaint.clearShadowLayer();
                }

                if (strokeWidth > 0.1f) {
                    textPaint.setStyle(Paint.Style.STROKE);
                    textPaint.setStrokeWidth(strokeWidth * 2f);
                    textPaint.setColor(strokeColor);
                    textPaint.setAlpha(alpha);
                    staticLayout.draw(c);
                }

                c.saveLayer(null, null);
                textPaint.setStyle(Paint.Style.FILL);
                textPaint.setColor(color);
                textPaint.setAlpha(alpha);
                staticLayout.draw(c);

                if (innerShadowRadius > 0.1f || innerShadowOffsetX != 0 || innerShadowOffsetY != 0) {
                    Paint atopPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    atopPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
                    c.saveLayer(null, atopPaint);

                    Paint shadowFill = new Paint(Paint.ANTI_ALIAS_FLAG);
                    shadowFill.setColor(innerShadowColor);
                    shadowFill.setAlpha(alpha);
                    c.drawRect(-currentPad * 2, -currentPad * 2, bw + currentPad * 2, bh + currentPad * 2, shadowFill);

                    int oldColor = textPaint.getColor();
                    int oldAlpha = textPaint.getAlpha();
                    textPaint.setColor(Color.BLACK);
                    textPaint.setAlpha(255);
                    textPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                    float blurAmt = innerShadowRadius > 0 ? innerShadowRadius : 0.1f;
                    textPaint.setMaskFilter(new BlurMaskFilter(blurAmt, BlurMaskFilter.Blur.NORMAL));

                    c.save();
                    c.translate(innerShadowOffsetX, innerShadowOffsetY);
                    staticLayout.draw(c);
                    c.restore();

                    textPaint.setColor(oldColor);
                    textPaint.setAlpha(oldAlpha);
                    textPaint.setXfermode(null);
                    textPaint.setMaskFilter(null);
                    c.restore();
                }
                c.restore();

            } else if (type == 1) {
                if (alphaCache == null || alphaCache.isRecycled()) alphaCache = bitmap.extractAlpha();

                float imgScaleFactor = Math.max(bitmap.getWidth(), bitmap.getHeight()) / 300f;
                if (imgScaleFactor < 1f) imgScaleFactor = 1f;

                float sShadow = shadowRadius * imgScaleFactor;
                float sStroke = strokeWidth * imgScaleFactor;
                float inShadow = innerShadowRadius * imgScaleFactor;

                float sOffsetX = shadowOffsetX * imgScaleFactor;
                float sOffsetY = shadowOffsetY * imgScaleFactor;
                float inOffsetX = innerShadowOffsetX * imgScaleFactor;
                float inOffsetY = innerShadowOffsetY * imgScaleFactor;

                if (sShadow > 0.1f || sOffsetX != 0 || sOffsetY != 0) {
                    Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    shadowPaint.setColor(shadowColor);
                    shadowPaint.setAlpha((int)(alpha * 0.8f));
                    if (sShadow > 0) shadowPaint.setMaskFilter(new BlurMaskFilter(sShadow, BlurMaskFilter.Blur.NORMAL));
                    c.drawBitmap(alphaCache, sOffsetX, sOffsetY, shadowPaint);
                }

                if (sStroke > 0.1f) {
                    Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    strokePaint.setColor(strokeColor);
                    strokePaint.setAlpha(alpha);
                    int steps = 16;
                    for (int i = 0; i < steps; i++) {
                        float angle = (float) (i * 2 * Math.PI / steps);
                        float sdx = (float) Math.cos(angle) * sStroke;
                        float sdy = (float) Math.sin(angle) * sStroke;
                        c.drawBitmap(alphaCache, sdx, sdy, strokePaint);
                    }
                    if (sStroke > 10) {
                        for (int i = 0; i < steps; i++) {
                            float angle = (float) (i * 2 * Math.PI / steps);
                            float sdx = (float) Math.cos(angle) * (sStroke / 2f);
                            float sdy = (float) Math.sin(angle) * (sStroke / 2f);
                            c.drawBitmap(alphaCache, sdx, sdy, strokePaint);
                        }
                    }
                }

                c.saveLayer(null, null);
                Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                fillPaint.setAlpha(alpha);
                c.drawBitmap(bitmap, 0, 0, fillPaint);

                if (inShadow > 0.1f || inOffsetX != 0 || inOffsetY != 0) {
                    Paint atopPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    atopPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
                    c.saveLayer(null, atopPaint);

                    Paint innerFill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    innerFill.setColor(innerShadowColor);
                    innerFill.setAlpha(alpha);
                    c.drawRect(-currentPad * 2, -currentPad * 2, bw + currentPad * 2, bh + currentPad * 2, innerFill);

                    Paint punchPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    punchPaint.setColor(Color.BLACK);
                    punchPaint.setAlpha(255);
                    punchPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                    float blurAmt = inShadow > 0 ? inShadow : 0.1f;
                    punchPaint.setMaskFilter(new BlurMaskFilter(blurAmt, BlurMaskFilter.Blur.NORMAL));
                    c.drawBitmap(alphaCache, inOffsetX, inOffsetY, punchPaint);

                    c.restore();
                }
                c.restore();
            }
            isDirty = false;
        }

        void drawLayer(Canvas canvas) {
            if (isDirty || bakedCache == null || bakedCache.isRecycled()) bake();

            canvas.save();
            canvas.scale(flipX ? -1 : 1, flipY ? -1 : 1);

            float cx = type == 0 ? staticLayout.getWidth() / 2f : bitmap.getWidth() / 2f;
            float cy = type == 0 ? staticLayout.getHeight() / 2f : bitmap.getHeight() / 2f;

            canvas.drawBitmap(bakedCache, -cx - currentPad, -cy - currentPad, renderPaint);

            canvas.restore();
        }
    }

    private static class ActionRecord {
        int type;
        GraphicLayer layer;
        DrawingToolManager.DrawStroke stroke;
        ActionRecord(GraphicLayer l) { type = 0; layer = l; }
        ActionRecord(DrawingToolManager.DrawStroke s) { type = 1; stroke = s; }
    }

    private static class PhotoEditorView extends View {
        private Bitmap baseImage;
        private final RectF destRect = new RectF();
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        public final DrawingToolManager drawingManager = new DrawingToolManager();

        private final Paint checkerPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint autoPunchPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint hqPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        public boolean isBgRemoverMode = false;
        private boolean isAutoColorRemovalMode = false;
        private boolean isBgRepairMode = false;

        public boolean isZoomMode = false;
        public boolean isGridMode = false;

        public boolean isLayerOutMode = false;

        private boolean isColorPickerMode = false;
        private EyedropperCallback eyedropperCallback;
        private float pickerX = 0, pickerY = 0;
        private int pickerColor = Color.BLACK;

        private float viewZoom = 1f, viewPanX = 0f, viewPanY = 0f;
        private final ScaleGestureDetector scaleDetector;

        public float imgBrightness = 0f, imgContrast = 1f, imgSaturation = 1f, imgHue = 0f;

        private final List<GraphicLayer> graphicLayers = new ArrayList<>();
        private final List<ActionRecord> undoStack = new ArrayList<>();

        private GraphicLayer activeLayer = null;
        private Runnable layerListener;

        public interface TextDoubleTapListener { void onDoubleTap(GraphicLayer layer); }
        private TextDoubleTapListener textDoubleTapListener;
        public void setTextDoubleTapListener(TextDoubleTapListener listener) { this.textDoubleTapListener = listener; }
        private long lastTapTime = 0;

        public interface OnModeChangeListener { void onModeChanged(); }
        private OnModeChangeListener modeListener;
        public void setOnModeChangeListener(OnModeChangeListener listener) { this.modeListener = listener; }

        public GraphicLayer getActiveLayer() { return activeLayer; }
        public List<GraphicLayer> getLayers() { return graphicLayers; }
        public Runnable getLayerListener() { return layerListener; }
        public List<ActionRecord> getUndoStack() { return undoStack; }

        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handleShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint deletePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint deleteXPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint pickerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pickerFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Matrix drawBoxMat = new Matrix();
        private final Path drawBoxPath = new Path();
        private final Path drawPerspectivePath = new Path();
        private final Path drawPerspectiveMask = new Path();
        private final Matrix touchFwdMat = new Matrix();
        private final Matrix touchInvMat = new Matrix();
        private final float[] touchPt = new float[2];
        private final Path cropMaskPath = new Path();
        private final float[] activeLayerPts = new float[16];
        private final float[] touchHitPts = new float[10];

        private int touchMode = 0;
        private float initialDistX = 0f, initialDistY = 0f, initialDist = 0f;
        private float initialScaleX = 1f, initialScaleY = 1f, initialAngle = 0f, initialRotation = 0f;
        private final PointF lastTouch = new PointF();

        private float activeLayerCenterX = 0f;
        private float activeLayerCenterY = 0f;

        private int activePointerId = MotionEvent.INVALID_POINTER_ID;

        public boolean isCropping = false, isCircleCrop = false, isPerspectiveMode = false;
        public boolean isLayerCropping = false;
        private final RectF cropRect = new RectF();
        private int activeCropHandle = -1;
        private float lockedRatio = 0f;

        private final float[] perspectiveCorners = new float[8];
        private int activePerspectiveHandle = -1;

        public PhotoEditorView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
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
            bitmapPaint.setDither(true);

            borderShadowPaint.setColor(Color.BLACK); borderShadowPaint.setStyle(Paint.Style.STROKE); borderShadowPaint.setStrokeWidth(8f);
            borderPaint.setColor(Color.WHITE); borderPaint.setStyle(Paint.Style.STROKE); borderPaint.setStrokeWidth(4f);

            handleShadowPaint.setColor(Color.BLACK); handleShadowPaint.setStyle(Paint.Style.FILL);
            handlePaint.setColor(Color.WHITE); handlePaint.setStyle(Paint.Style.FILL);

            deletePaint.setColor(Color.parseColor("#FF3B30")); deletePaint.setStyle(Paint.Style.FILL);
            deleteXPaint.setColor(Color.WHITE); deleteXPaint.setStyle(Paint.Style.STROKE); deleteXPaint.setStrokeWidth(4f);
            maskPaint.setColor(Color.argb(180, 0, 0, 0)); maskPaint.setStyle(Paint.Style.FILL);

            gridShadowPaint.setColor(Color.argb(120, 0, 0, 0)); gridShadowPaint.setStyle(Paint.Style.STROKE); gridShadowPaint.setStrokeWidth(4f);
            gridPaint.setColor(Color.argb(200, 255, 255, 255)); gridPaint.setStyle(Paint.Style.STROKE); gridPaint.setStrokeWidth(2f);

            autoPunchPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

            pickerBorderPaint.setColor(Color.WHITE); pickerBorderPaint.setStyle(Paint.Style.STROKE); pickerBorderPaint.setStrokeWidth(6f);
            pickerBorderPaint.setShadowLayer(4f, 0, 0, Color.BLACK);
            pickerFillPaint.setStyle(Paint.Style.FILL);
        }

        private void setupCheckerboard() {
            Bitmap cb = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888); Canvas cc = new Canvas(cb); cc.drawColor(Color.WHITE);
            Paint p = new Paint(); p.setColor(Color.LTGRAY); cc.drawRect(0, 0, 20, 20, p); cc.drawRect(20, 20, 40, 40, p);
            checkerPaint.setShader(new BitmapShader(cb, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }

        public void setOnLayerChangeListener(Runnable listener) { this.layerListener = listener; }

        public void setImage(Bitmap bitmap) {
            this.baseImage = bitmap;
            drawingManager.initBitmaps(bitmap.getWidth(), bitmap.getHeight());
            clearModifications(); invalidate();
        }

        public boolean isImageMissing() { return baseImage == null; }

        public void startEyedropper(EyedropperCallback callback) {
            disableSpecialModes();
            isColorPickerMode = true;
            eyedropperCallback = callback;
            Toast.makeText(getContext(), "Drag to pick a color, release to select.", Toast.LENGTH_LONG).show();
            invalidate();
        }

        public void toggleZoomMode() {
            if (isZoomMode) {
                isZoomMode = false;
            } else {
                disableSpecialModes();
                isZoomMode = true;
            }
            if (modeListener != null) modeListener.onModeChanged();
        }

        public void toggleGridMode() { isGridMode = !isGridMode; invalidate(); if (modeListener != null) modeListener.onModeChanged(); }
        public void startDrawing(int width) { disableSpecialModes(); drawingManager.isDrawMode = true; drawingManager.currentBrushWidth = width; }
        public void startBgEraser(int width, boolean isRepair) { disableSpecialModes(); isBgRemoverMode = true; isBgRepairMode = isRepair; drawingManager.currentBrushWidth = width; }
        public void enterAutoColorRemovalMode() { disableSpecialModes(); isAutoColorRemovalMode = true; }

        private void disableSpecialModes(boolean keepLayer) {
            drawingManager.isDrawMode = false; isBgRemoverMode = false; isAutoColorRemovalMode = false; isZoomMode = false; isColorPickerMode = false;
            if (!keepLayer) activeLayer = null;
            if (modeListener != null) modeListener.onModeChanged();
        }
        private void disableSpecialModes() { disableSpecialModes(false); }

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

        public void addAdvancedTextLayer(String text, Typeface typeface, int color, Layout.Alignment align, float letterSpacing, float lineSpacing, float strokeWidth, int strokeColor, float shadowRadius, int shadowColor, float innerShadowRadius, int innerShadowColor) {
            disableSpecialModes();
            GraphicLayer layer = new GraphicLayer(text, typeface, color, align, letterSpacing, lineSpacing, strokeWidth, strokeColor, shadowRadius, shadowColor, innerShadowRadius, innerShadowColor, 0, 0);
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
            GraphicLayer toCopy = new GraphicLayer(activeLayer); disableSpecialModes();
            graphicLayers.add(toCopy); activeLayer = toCopy;
            undoStack.add(new ActionRecord(toCopy));
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
                drawingManager.removeStroke(lastAction.stroke);
            }
            invalidate();
        }

        public void deselectLayer() { activeLayer = null; invalidate(); }
        public void setActiveLayer(GraphicLayer layer) { activeLayer = layer; invalidate(); }

        public void clearModifications() {
            disableSpecialModes(); graphicLayers.clear(); undoStack.clear();
            drawingManager.clear();
            viewZoom = 1f; viewPanX = 0f; viewPanY = 0f; isCircleCrop = false; isPerspectiveMode = false; isGridMode = false; isLayerCropping = false;
            setAdjustments(0, 1, 1, 0);
            if (layerListener != null) layerListener.run();
            if (modeListener != null) modeListener.onModeChanged();
            invalidate();
        }

        public void startInteractiveCrop() {
            disableSpecialModes(); viewZoom=1f; viewPanX=0f; viewPanY=0f;
            isCropping = true; isPerspectiveMode = false; isLayerCropping = false; cropRect.set(destRect); invalidate();
        }

        public void startLayerCrop() {
            if (activeLayer == null || activeLayer.type != 1) return;
            isLayerCropping = true;
            disableSpecialModes(true);
            viewZoom = 1f; viewPanX = 0f; viewPanY = 0f;
            isCropping = true; isPerspectiveMode = false;

            Bitmap targetBmp = activeLayer.bitmap;
            float scale = Math.min((float) getWidth() / targetBmp.getWidth(), (float) getHeight() / targetBmp.getHeight()) * viewZoom;
            float dx = (getWidth() - targetBmp.getWidth() * scale) / 2f + viewPanX;
            float dy = (getHeight() - targetBmp.getHeight() * scale) / 2f + viewPanY;
            destRect.set(dx, dy, dx + targetBmp.getWidth() * scale, dy + targetBmp.getHeight() * scale);

            cropRect.set(destRect);
            invalidate();
        }

        public void cancelCrop() { isCropping = false; isCircleCrop = false; isPerspectiveMode = false; isLayerCropping = false; invalidate(); }

        public void setCropFull() {
            isCircleCrop = false;
            lockedRatio = 0f;
            cropRect.set(destRect);
            invalidate();
        }

        public void setCropRatio(float r) {
            lockedRatio = r;
            if (r > 0) {
                float maxW = destRect.width();
                float maxH = destRect.height();
                float w = maxW;
                float h = w / r;
                if (h > maxH) { h = maxH; w = h * r; }
                float cx = destRect.centerX();
                float cy = destRect.centerY();
                cropRect.set(cx - w/2, cy - h/2, cx + w/2, cy + h/2);
            }
            invalidate();
        }

        public void setCropCircle(boolean isCircle) { this.isCircleCrop = isCircle; if (isCircle) setCropRatio(1f); invalidate(); }

        public void startPerspectiveCrop() {
            disableSpecialModes(isLayerCropping);
            viewZoom = 1f; viewPanX = 0f; viewPanY = 0f;
            isPerspectiveMode = true; isCropping = false;
            perspectiveCorners[0] = destRect.left; perspectiveCorners[1] = destRect.top;
            perspectiveCorners[2] = destRect.right; perspectiveCorners[3] = destRect.top;
            perspectiveCorners[4] = destRect.right; perspectiveCorners[5] = destRect.bottom;
            perspectiveCorners[6] = destRect.left; perspectiveCorners[7] = destRect.bottom;
            invalidate();
        }

        public void rotateImage() {
            if (isLayerCropping && activeLayer != null && activeLayer.type == 1) {
                Matrix matrix = new Matrix(); matrix.postRotate(90);
                activeLayer.bitmap = Bitmap.createBitmap(activeLayer.bitmap, 0, 0, activeLayer.bitmap.getWidth(), activeLayer.bitmap.getHeight(), matrix, true);
                activeLayer.updateBounds();
                activeLayer.isDirty = true;
                startLayerCrop();
                return;
            }
            if (baseImage == null) return;
            Bitmap flattened = getRenderedBitmap(true);
            Matrix matrix = new Matrix(); matrix.postRotate(90);
            setImage(Bitmap.createBitmap(flattened, 0, 0, flattened.getWidth(), flattened.getHeight(), matrix, true));
            if (isCropping) startInteractiveCrop();
        }

        public void mirrorImage() {
            if (isLayerCropping && activeLayer != null && activeLayer.type == 1) {
                Matrix matrix = new Matrix(); matrix.preScale(-1.0f, 1.0f);
                activeLayer.bitmap = Bitmap.createBitmap(activeLayer.bitmap, 0, 0, activeLayer.bitmap.getWidth(), activeLayer.bitmap.getHeight(), matrix, true);
                activeLayer.updateBounds();
                activeLayer.isDirty = true;
                startLayerCrop();
                return;
            }
            if (baseImage == null) return;
            Bitmap flattened = getRenderedBitmap(true);
            Matrix matrix = new Matrix(); matrix.preScale(-1.0f, 1.0f);
            setImage(Bitmap.createBitmap(flattened, 0, 0, flattened.getWidth(), flattened.getHeight(), matrix, true));
            if (isCropping) startInteractiveCrop();
        }

        public void applyCrop() {
            if (isLayerCropping) {
                if (activeLayer == null || activeLayer.type != 1) return;
                Bitmap srcBmp = activeLayer.bitmap;
                Bitmap targetBmp = srcBmp;

                if (isPerspectiveMode) {
                    float scaleX = srcBmp.getWidth() / destRect.width();
                    float scaleY = srcBmp.getHeight() / destRect.height();
                    float[] src = new float[8];
                    for (int i=0; i<8; i+=2) {
                        src[i] = (perspectiveCorners[i] - destRect.left) * scaleX;
                        src[i+1] = (perspectiveCorners[i+1] - destRect.top) * scaleY;
                    }

                    float wTop = (float) Math.hypot(src[2] - src[0], src[3] - src[1]);
                    float wBot = (float) Math.hypot(src[4] - src[6], src[5] - src[7]);
                    float hLeft = (float) Math.hypot(src[6] - src[0], src[7] - src[1]);
                    float hRight = (float) Math.hypot(src[4] - src[2], src[5] - src[3]);

                    int finalW = (int) Math.max(wTop, wBot);
                    int finalH = (int) Math.max(hLeft, hRight);

                    if (finalW > 0 && finalH > 0) {
                        float[] dst = new float[] { 0, 0, finalW, 0, finalW, finalH, 0, finalH };
                        Matrix matrix = new Matrix();
                        matrix.setPolyToPoly(src, 0, dst, 0, 4);

                        Bitmap result = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888);
                        Canvas c = new Canvas(result);
                        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                        c.drawBitmap(srcBmp, matrix, p);
                        targetBmp = result;
                    }
                } else {
                    float scaleX = srcBmp.getWidth() / destRect.width();
                    float scaleY = srcBmp.getHeight() / destRect.height();
                    int bx = (int) ((cropRect.left - destRect.left) * scaleX);
                    int by = (int) ((cropRect.top - destRect.top) * scaleY);
                    int bw = (int) (cropRect.width() * scaleX);
                    int bh = (int) (cropRect.height() * scaleY);

                    int finalX = Math.max(0, bx);
                    int finalY = Math.max(0, by);
                    int finalW = Math.min(srcBmp.getWidth() - finalX, bw);
                    int finalH = Math.min(srcBmp.getHeight() - finalY, bh);

                    if (finalW > 0 && finalH > 0) {
                        Bitmap cropped = Bitmap.createBitmap(srcBmp, finalX, finalY, finalW, finalH);
                        targetBmp = cropped;
                        if (isCircleCrop) {
                            Bitmap circleBitmap = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888);
                            Canvas c = new Canvas(circleBitmap);
                            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                            c.drawCircle(finalW / 2f, finalH / 2f, Math.min(finalW, finalH) / 2f, p);
                            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                            c.drawBitmap(cropped, 0, 0, p);
                            targetBmp = circleBitmap;
                        }
                    }
                }
                activeLayer.bitmap = targetBmp;
                activeLayer.updateBounds();
                activeLayer.isDirty = true;
                isLayerCropping = false;
                isCropping = false;
                isPerspectiveMode = false;
                isCircleCrop = false;
                invalidate();
                return;
            }

            if (baseImage == null) return;
            if (isPerspectiveMode) {
                float scaleX = baseImage.getWidth() / destRect.width();
                float scaleY = baseImage.getHeight() / destRect.height();
                float[] src = new float[8];
                for (int i=0; i<8; i+=2) {
                    src[i] = (perspectiveCorners[i] - destRect.left) * scaleX;
                    src[i+1] = (perspectiveCorners[i+1] - destRect.top) * scaleY;
                }

                float wTop = (float) Math.hypot(src[2] - src[0], src[3] - src[1]);
                float wBot = (float) Math.hypot(src[4] - src[6], src[5] - src[7]);
                float hLeft = (float) Math.hypot(src[6] - src[0], src[7] - src[1]);
                float hRight = (float) Math.hypot(src[4] - src[2], src[5] - src[3]);

                int finalW = (int) Math.max(wTop, wBot);
                int finalH = (int) Math.max(hLeft, hRight);

                if (finalW > 0 && finalH > 0) {
                    float[] dst = new float[] { 0, 0, finalW, 0, finalW, finalH, 0, finalH };
                    Matrix matrix = new Matrix();
                    matrix.setPolyToPoly(src, 0, dst, 0, 4);

                    Bitmap result = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(result);
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    Bitmap flattened = getRenderedBitmap(true);
                    c.drawBitmap(flattened, matrix, p);
                    activeLayer = null;
                    setImage(result);
                }
                isPerspectiveMode = false;
                return;
            }

            if (!isCropping) return;
            activeLayer = null; Bitmap flattened = getRenderedBitmap(true);
            float scaleX = flattened.getWidth() / destRect.width(); float scaleY = flattened.getHeight() / destRect.height();
            int bx = (int) ((cropRect.left - destRect.left) * scaleX); int by = (int) ((cropRect.top - destRect.top) * scaleY);
            int bw = (int) (cropRect.width() * scaleX); int bh = (int) (cropRect.height() * scaleY);

            int finalX = Math.max(0, bx);
            int finalY = Math.max(0, by);
            int finalW = Math.min(flattened.getWidth() - finalX, bw);
            int finalH = Math.min(flattened.getHeight() - finalY, bh);

            if (finalW > 0 && finalH > 0) {
                Bitmap cropped = Bitmap.createBitmap(flattened, finalX, finalY, finalW, finalH);
                Bitmap finalSetBitmap = cropped;
                if (isCircleCrop) {
                    Bitmap circleBitmap = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(circleBitmap); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    c.drawCircle(finalW / 2f, finalH / 2f, Math.min(finalW, finalH) / 2f, p);
                    p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN)); c.drawBitmap(cropped, 0, 0, p);
                    finalSetBitmap = circleBitmap;
                }
                setImage(finalSetBitmap);
            }
            isCropping = false; isCircleCrop = false;
        }

        private void drawShadowHandle(Canvas c, float x, float y, boolean isDelete, boolean isStretch) {
            c.drawCircle(x, y, 34f, handleShadowPaint);
            if (isStretch) {
                c.drawCircle(x, y, 30f, handlePaint);
                c.drawCircle(x, y, 12f, borderShadowPaint);
                c.drawCircle(x, y, 12f, borderPaint);
            } else {
                c.drawCircle(x, y, 30f, isDelete ? deletePaint : handlePaint);
                c.drawCircle(x, y, 30f, borderPaint);
                if (isDelete) {
                    c.drawLine(x-12f, y-12f, x+12f, y+12f, deleteXPaint);
                    c.drawLine(x+12f, y-12f, x-12f, y+12f, deleteXPaint);
                }
            }
        }

        private void drawCropUI(Canvas canvas) {
            if (isPerspectiveMode) {
                drawPerspectivePath.reset();
                drawPerspectivePath.moveTo(perspectiveCorners[0], perspectiveCorners[1]);
                drawPerspectivePath.lineTo(perspectiveCorners[2], perspectiveCorners[3]);
                drawPerspectivePath.lineTo(perspectiveCorners[4], perspectiveCorners[5]);
                drawPerspectivePath.lineTo(perspectiveCorners[6], perspectiveCorners[7]);
                drawPerspectivePath.close();

                drawPerspectiveMask.reset();
                drawPerspectiveMask.addRect(destRect, Path.Direction.CW);
                drawPerspectiveMask.addPath(drawPerspectivePath);
                drawPerspectiveMask.setFillType(Path.FillType.EVEN_ODD);
                canvas.drawPath(drawPerspectiveMask, maskPaint);

                canvas.drawPath(drawPerspectivePath, borderShadowPaint);
                canvas.drawPath(drawPerspectivePath, borderPaint);
                for (int i=0; i<8; i+=2) drawShadowHandle(canvas, perspectiveCorners[i], perspectiveCorners[i+1], false, false);
            }
            else if (isCropping) {
                cropMaskPath.reset();
                cropMaskPath.addRect(destRect, Path.Direction.CW);
                if (isCircleCrop) cropMaskPath.addCircle(cropRect.centerX(), cropRect.centerY(), cropRect.width()/2f, Path.Direction.CCW);
                else cropMaskPath.addRect(cropRect, Path.Direction.CCW);
                cropMaskPath.setFillType(Path.FillType.EVEN_ODD);
                canvas.drawPath(cropMaskPath, maskPaint);

                if (isCircleCrop) {
                    canvas.drawCircle(cropRect.centerX(), cropRect.centerY(), cropRect.width()/2f, borderShadowPaint);
                    canvas.drawCircle(cropRect.centerX(), cropRect.centerY(), cropRect.width()/2f, borderPaint);
                } else {
                    canvas.drawRect(cropRect, borderShadowPaint);
                    canvas.drawRect(cropRect, borderPaint);
                    float w3 = cropRect.width() / 3f, h3 = cropRect.height() / 3f;

                    canvas.drawLine(cropRect.left + w3, cropRect.top, cropRect.left + w3, cropRect.bottom, gridShadowPaint);
                    canvas.drawLine(cropRect.left + w3*2, cropRect.top, cropRect.left + w3*2, cropRect.bottom, gridShadowPaint);
                    canvas.drawLine(cropRect.left, cropRect.top + h3, cropRect.right, cropRect.top + h3, gridShadowPaint);
                    canvas.drawLine(cropRect.left, cropRect.top + h3*2, cropRect.right, cropRect.top + h3*2, gridShadowPaint);

                    canvas.drawLine(cropRect.left + w3, cropRect.top, cropRect.left + w3, cropRect.bottom, gridPaint);
                    canvas.drawLine(cropRect.left + w3*2, cropRect.top, cropRect.left + w3*2, cropRect.bottom, gridPaint);
                    canvas.drawLine(cropRect.left, cropRect.top + h3, cropRect.right, cropRect.top + h3, gridPaint);
                    canvas.drawLine(cropRect.left, cropRect.top + h3*2, cropRect.right, cropRect.top + h3*2, gridPaint);
                }
                drawShadowHandle(canvas, cropRect.left, cropRect.top, false, false);
                drawShadowHandle(canvas, cropRect.right, cropRect.top, false, false);
                drawShadowHandle(canvas, cropRect.left, cropRect.bottom, false, false);
                drawShadowHandle(canvas, cropRect.right, cropRect.bottom, false, false);
            }
        }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);

            if (isLayerCropping && activeLayer != null && activeLayer.type == 1) {
                Bitmap targetBmp = activeLayer.bitmap;
                float scale = Math.min((float) getWidth() / targetBmp.getWidth(), (float) getHeight() / targetBmp.getHeight()) * viewZoom;
                float dx = (getWidth() - targetBmp.getWidth() * scale) / 2f + viewPanX;
                float dy = (getHeight() - targetBmp.getHeight() * scale) / 2f + viewPanY;
                destRect.set(dx, dy, dx + targetBmp.getWidth() * scale, dy + targetBmp.getHeight() * scale);

                canvas.drawRect(destRect, checkerPaint);
                canvas.drawBitmap(targetBmp, null, destRect, bitmapPaint);
                drawCropUI(canvas);
                return;
            }

            if (baseImage == null) return;

            float scale = Math.min((float) getWidth() / baseImage.getWidth(), (float) getHeight() / baseImage.getHeight()) * viewZoom;
            float dx = (getWidth() - baseImage.getWidth() * scale) / 2f + viewPanX;
            float dy = (getHeight() - baseImage.getHeight() * scale) / 2f + viewPanY;
            destRect.set(dx, dy, dx + baseImage.getWidth() * scale, dy + baseImage.getHeight() * scale);

            canvas.drawRect(destRect, checkerPaint);

            int sc = canvas.saveLayer(destRect, null);
            canvas.drawBitmap(baseImage, null, destRect, bitmapPaint);
            if (drawingManager.getEraseLayerBitmap() != null) {
                canvas.drawBitmap(drawingManager.getEraseLayerBitmap(), null, destRect, autoPunchPaint);
            }
            canvas.restoreToCount(sc);

            if (drawingManager.getDrawLayerBitmap() != null) {
                canvas.drawBitmap(drawingManager.getDrawLayerBitmap(), null, destRect, hqPaint);
            }

            if (drawingManager.isDrawMode && !drawingManager.isDrawEraserMode && !drawingManager.getCurrentPath().isEmpty()) {
                canvas.save();
                canvas.translate(destRect.left, destRect.top);
                canvas.scale(destRect.width() / baseImage.getWidth(), destRect.height() / baseImage.getHeight());
                canvas.drawPath(drawingManager.getCurrentPath(), drawingManager.getCurrentDrawPaint());
                canvas.restore();
            }

            for (GraphicLayer layer : graphicLayers) {
                canvas.save();
                if (!isLayerOutMode) { canvas.clipRect(destRect); }

                canvas.translate(destRect.centerX(), destRect.centerY());
                canvas.scale(destRect.width() / baseImage.getWidth(), destRect.height() / baseImage.getHeight());
                canvas.translate(layer.x, layer.y);
                canvas.rotate(layer.rotation);
                canvas.scale(layer.scaleX, layer.scaleY);

                layer.drawLayer(canvas);
                canvas.restore();

                if (layer == activeLayer && !isCropping && !isPerspectiveMode && !isColorPickerMode) {

                    drawBoxMat.reset();
                    drawBoxMat.postTranslate(destRect.centerX(), destRect.centerY());
                    drawBoxMat.preScale(destRect.width() / baseImage.getWidth(), destRect.height() / baseImage.getHeight());
                    drawBoxMat.preTranslate(layer.x, layer.y);
                    drawBoxMat.preRotate(layer.rotation);

                    float pad = layer.getPadding();
                    float l = (layer.bounds.left - pad) * layer.scaleX;
                    float t = (layer.bounds.top - pad) * layer.scaleY;
                    float r = (layer.bounds.right + pad) * layer.scaleX;
                    float b = (layer.bounds.bottom + pad) * layer.scaleY;
                    float cx = (l + r) / 2f;
                    float cy = (t + b) / 2f;

                    activeLayerPts[0] = l; activeLayerPts[1] = t;
                    activeLayerPts[2] = r; activeLayerPts[3] = t;
                    activeLayerPts[4] = r; activeLayerPts[5] = b;
                    activeLayerPts[6] = l; activeLayerPts[7] = b;
                    activeLayerPts[8] = cx; activeLayerPts[9] = t - 80f;
                    activeLayerPts[10] = cx; activeLayerPts[11] = t;
                    activeLayerPts[12] = r; activeLayerPts[13] = cy;
                    activeLayerPts[14] = cx; activeLayerPts[15] = b;

                    drawBoxMat.mapPoints(activeLayerPts);

                    drawBoxPath.reset();
                    drawBoxPath.moveTo(activeLayerPts[0], activeLayerPts[1]);
                    drawBoxPath.lineTo(activeLayerPts[2], activeLayerPts[3]);
                    drawBoxPath.lineTo(activeLayerPts[4], activeLayerPts[5]);
                    drawBoxPath.lineTo(activeLayerPts[6], activeLayerPts[7]);
                    drawBoxPath.close();

                    canvas.drawPath(drawBoxPath, borderShadowPaint);
                    canvas.drawPath(drawBoxPath, borderPaint);

                    canvas.drawLine(activeLayerPts[10], activeLayerPts[11], activeLayerPts[8], activeLayerPts[9], borderShadowPaint);
                    canvas.drawLine(activeLayerPts[10], activeLayerPts[11], activeLayerPts[8], activeLayerPts[9], borderPaint);

                    drawShadowHandle(canvas, activeLayerPts[0], activeLayerPts[1], true, false);
                    drawShadowHandle(canvas, activeLayerPts[4], activeLayerPts[5], false, false);
                    drawShadowHandle(canvas, activeLayerPts[8], activeLayerPts[9], false, false);

                    if (layer.type == 1) {
                        drawShadowHandle(canvas, activeLayerPts[12], activeLayerPts[13], false, true);
                        drawShadowHandle(canvas, activeLayerPts[14], activeLayerPts[15], false, true);
                    }
                }
            }

            drawCropUI(canvas);

            if (isGridMode && !isCropping) {
                float cellWidth = destRect.width() / 9f;
                float cellHeight = destRect.height() / 9f;
                for (int i = 1; i < 9; i++) {
                    canvas.drawLine(destRect.left + (cellWidth * i), destRect.top, destRect.left + (cellWidth * i), destRect.bottom, gridShadowPaint);
                    canvas.drawLine(destRect.left, destRect.top + (cellHeight * i), destRect.right, destRect.top + (cellHeight * i), gridShadowPaint);

                    canvas.drawLine(destRect.left + (cellWidth * i), destRect.top, destRect.left + (cellWidth * i), destRect.bottom, gridPaint);
                    canvas.drawLine(destRect.left, destRect.top + (cellHeight * i), destRect.right, destRect.top + (cellHeight * i), gridPaint);
                }
                canvas.drawRect(destRect, gridShadowPaint);
                canvas.drawRect(destRect, gridPaint);
            }

            if (isColorPickerMode && pickerX > 0 && pickerY > 0) {
                pickerFillPaint.setColor(pickerColor);
                canvas.drawCircle(pickerX, pickerY - 100f, 60f, pickerFillPaint);
                canvas.drawCircle(pickerX, pickerY - 100f, 60f, pickerBorderPaint);
                canvas.drawLine(pickerX - 20f, pickerY, pickerX + 20f, pickerY, pickerBorderPaint);
                canvas.drawLine(pickerX, pickerY - 20f, pickerX, pickerY + 20f, pickerBorderPaint);
            }
        }

        private float[] mapTouch(GraphicLayer l, float x, float y) {
            touchFwdMat.reset();
            touchFwdMat.postTranslate(destRect.centerX(), destRect.centerY());
            if (isLayerCropping && activeLayer != null && activeLayer.type == 1) {
                touchFwdMat.preScale(destRect.width() / activeLayer.bitmap.getWidth(), destRect.height() / activeLayer.bitmap.getHeight());
            } else {
                touchFwdMat.preScale(destRect.width() / baseImage.getWidth(), destRect.height() / baseImage.getHeight());
            }
            touchFwdMat.preTranslate(l.x, l.y);
            touchFwdMat.preRotate(l.rotation);

            touchFwdMat.invert(touchInvMat);
            touchPt[0] = x; touchPt[1] = y;
            touchInvMat.mapPoints(touchPt);
            return touchPt;
        }

        private int pickColorFromImage(float x, float y) {
            if (baseImage == null) return Color.BLACK;
            int bmpX = (int) ((x - destRect.left) * (baseImage.getWidth() / destRect.width()));
            int bmpY = (int) ((y - destRect.top) * (baseImage.getHeight() / destRect.height()));
            bmpX = Math.max(0, Math.min(bmpX, baseImage.getWidth() - 1));
            bmpY = Math.max(0, Math.min(bmpY, baseImage.getHeight() - 1));
            return baseImage.getPixel(bmpX, bmpY);
        }

        @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
            float x = event.getX(), y = event.getY();

            if (isColorPickerMode) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        pickerX = x; pickerY = y;
                        pickerColor = pickColorFromImage(x, y);
                        invalidate();
                        return true;
                    case MotionEvent.ACTION_UP:
                        isColorPickerMode = false;
                        if (eyedropperCallback != null) eyedropperCallback.onColorPicked(pickerColor);
                        invalidate();
                        return true;
                }
            }

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

            if (isPerspectiveMode) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        activePerspectiveHandle = -1;
                        for (int i=0; i<8; i+=2) {
                            if (Math.hypot(x - perspectiveCorners[i], y - perspectiveCorners[i+1]) < 80f) { activePerspectiveHandle = i; break; }
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (activePerspectiveHandle != -1) {
                            perspectiveCorners[activePerspectiveHandle] = Math.max(destRect.left, Math.min(x, destRect.right));
                            perspectiveCorners[activePerspectiveHandle+1] = Math.max(destRect.top, Math.min(y, destRect.bottom));
                            invalidate();
                        }
                        return true;
                    case MotionEvent.ACTION_UP: activePerspectiveHandle = -1; return true;
                }
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
                            RectF oldRect = new RectF(cropRect);

                            if (activeCropHandle == 4) {
                                cropRect.offset(dx, dy);
                            } else {
                                if (activeCropHandle == 0) { cropRect.left += dx; cropRect.top += dy; }
                                else if (activeCropHandle == 1) { cropRect.right += dx; cropRect.top += dy; }
                                else if (activeCropHandle == 2) { cropRect.left += dx; cropRect.bottom += dy; }
                                else if (activeCropHandle == 3) { cropRect.right += dx; cropRect.bottom += dy; }

                                if (lockedRatio > 0f) {
                                    float w = cropRect.width();
                                    float h = w / lockedRatio;
                                    if (activeCropHandle == 0 || activeCropHandle == 1) cropRect.top = cropRect.bottom - h;
                                    else cropRect.bottom = cropRect.top + h;
                                }
                            }

                            if (cropRect.width() < 100f || cropRect.height() < 100f) {
                                cropRect.set(oldRect);
                            }

                            if (cropRect.left < destRect.left || cropRect.top < destRect.top || cropRect.right > destRect.right || cropRect.bottom > destRect.bottom) {
                                if (activeCropHandle == 4) {
                                    if (cropRect.left < destRect.left) cropRect.offset(destRect.left - cropRect.left, 0);
                                    if (cropRect.top < destRect.top) cropRect.offset(0, destRect.top - cropRect.top);
                                    if (cropRect.right > destRect.right) cropRect.offset(destRect.right - cropRect.right, 0);
                                    if (cropRect.bottom > destRect.bottom) cropRect.offset(0, destRect.bottom - cropRect.bottom);
                                } else {
                                    cropRect.set(oldRect);
                                }
                            }

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
            else if ((drawingManager.isDrawMode || isBgRemoverMode) && baseImage != null) {
                float imgX = (x - destRect.left) * (baseImage.getWidth() / destRect.width());
                float imgY = (y - destRect.top) * (baseImage.getHeight() / destRect.height());

                float scaleFactor = baseImage.getWidth() / destRect.width();
                drawingManager.configurePaint(scaleFactor, isBgRemoverMode, isBgRepairMode);

                int targetCanvasState;
                boolean isEraser = isBgRemoverMode || (drawingManager.isDrawMode && drawingManager.isDrawEraserMode);

                if (drawingManager.isDrawMode && drawingManager.getDrawLayerCanvas() != null) {
                    targetCanvasState = drawingManager.isDrawEraserMode ? 1 : 0;
                } else if (isBgRemoverMode && drawingManager.getEraseLayerCanvas() != null) {
                    targetCanvasState = isBgRepairMode ? 3 : 2;
                } else {
                    return true;
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        drawingManager.onTouchDown(imgX, imgY);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        drawingManager.onTouchMove(imgX, imgY, isEraser, drawingManager.isDrawMode);
                        break;
                    case MotionEvent.ACTION_UP:
                        DrawingToolManager.DrawStroke stroke = drawingManager.onTouchUp(imgX, imgY, isEraser, drawingManager.isDrawMode, targetCanvasState);
                        undoStack.add(new ActionRecord(stroke));
                        break;
                }
                invalidate(); return true;
            }
            else {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchMode = 0;
                        if (activeLayer != null) {
                            touchFwdMat.reset();
                            touchFwdMat.postTranslate(destRect.centerX(), destRect.centerY());
                            touchFwdMat.preScale(destRect.width() / baseImage.getWidth(), destRect.height() / baseImage.getHeight());
                            touchFwdMat.preTranslate(activeLayer.x, activeLayer.y);
                            touchFwdMat.preRotate(activeLayer.rotation);

                            float pad = activeLayer.getPadding();
                            float l = (activeLayer.bounds.left - pad) * activeLayer.scaleX;
                            float t = (activeLayer.bounds.top - pad) * activeLayer.scaleY;
                            float r = (activeLayer.bounds.right + pad) * activeLayer.scaleX;
                            float b = (activeLayer.bounds.bottom + pad) * activeLayer.scaleY;
                            float cx = (l + r) / 2f;
                            float cy = (t + b) / 2f;

                            touchHitPts[0] = l; touchHitPts[1] = t;
                            touchHitPts[2] = r; touchHitPts[3] = b;
                            touchHitPts[4] = cx; touchHitPts[5] = t - 80f;
                            touchHitPts[6] = r; touchHitPts[7] = cy;
                            touchHitPts[8] = cx; touchHitPts[9] = b;

                            touchFwdMat.mapPoints(touchHitPts);

                            float[] cPt = new float[]{0f, 0f};
                            touchFwdMat.mapPoints(cPt);
                            activeLayerCenterX = cPt[0];
                            activeLayerCenterY = cPt[1];

                            float touchRadius = 80f;

                            if (Math.hypot(x - touchHitPts[0], y - touchHitPts[1]) < touchRadius) {
                                graphicLayers.remove(activeLayer); activeLayer = null;
                                if (layerListener != null) layerListener.run(); invalidate(); return true;
                            } else if (Math.hypot(x - touchHitPts[2], y - touchHitPts[3]) < touchRadius) {
                                touchMode = 2;
                                float[] loc = mapTouch(activeLayer, x, y);
                                initialDist = (float)Math.hypot(loc[0], loc[1]);
                                initialScaleX = activeLayer.scaleX;
                                initialScaleY = activeLayer.scaleY;
                                return true;
                            } else if (Math.hypot(x - touchHitPts[4], y - touchHitPts[5]) < touchRadius) {
                                touchMode = 3;
                                initialAngle = (float)Math.toDegrees(Math.atan2(y - activeLayerCenterY, x - activeLayerCenterX));
                                initialRotation = activeLayer.rotation;
                                return true;
                            } else if (activeLayer.type == 1 && Math.hypot(x - touchHitPts[6], y - touchHitPts[7]) < touchRadius) {
                                touchMode = 4;
                                float[] loc = mapTouch(activeLayer, x, y);
                                initialDistX = Math.abs(loc[0]);
                                initialScaleX = activeLayer.scaleX;
                                return true;
                            } else if (activeLayer.type == 1 && Math.hypot(x - touchHitPts[8], y - touchHitPts[9]) < touchRadius) {
                                touchMode = 5;
                                float[] loc = mapTouch(activeLayer, x, y);
                                initialDistY = Math.abs(loc[1]);
                                initialScaleY = activeLayer.scaleY;
                                return true;
                            }
                        }

                        for (int i=graphicLayers.size()-1; i>=0; i--) {
                            GraphicLayer layer = graphicLayers.get(i);
                            float[] loc = mapTouch(layer, x, y);

                            float pad = layer.getPadding();
                            float bl = (layer.bounds.left - pad) * layer.scaleX;
                            float bt = (layer.bounds.top - pad) * layer.scaleY;
                            float br = (layer.bounds.right + pad) * layer.scaleX;
                            float bb = (layer.bounds.bottom + pad) * layer.scaleY;

                            if (loc[0]>=bl && loc[0]<=br && loc[1]>=bt && loc[1]<=bb) {
                                if (activeLayer == layer && layer.type == 0) {
                                    long currentTime = System.currentTimeMillis();
                                    if (currentTime - lastTapTime < 300) {
                                        if (textDoubleTapListener != null) textDoubleTapListener.onDoubleTap(layer);
                                        lastTapTime = 0;
                                        return true;
                                    }
                                    lastTapTime = currentTime;
                                } else {
                                    lastTapTime = System.currentTimeMillis();
                                }
                                activeLayer = layer; touchMode = 1; lastTouch.set(x, y);
                                if (layerListener != null) layerListener.run();
                                invalidate(); return true;
                            }
                        }
                        activeLayer = null;
                        if (layerListener != null) layerListener.run();
                        invalidate(); return true;

                    case MotionEvent.ACTION_MOVE:
                        if (activeLayer != null) {
                            if (touchMode == 1) {
                                activeLayer.x += (x - lastTouch.x) * (baseImage.getWidth() / destRect.width());
                                activeLayer.y += (y - lastTouch.y) * (baseImage.getHeight() / destRect.height());

                                if (!isLayerOutMode) {
                                    float halfW = baseImage.getWidth() / 2f;
                                    float halfH = baseImage.getHeight() / 2f;
                                    activeLayer.x = Math.max(-halfW, Math.min(activeLayer.x, halfW));
                                    activeLayer.y = Math.max(-halfH, Math.min(activeLayer.y, halfH));
                                }

                                lastTouch.set(x,y);
                            }
                            else if (touchMode == 2) {
                                float[] loc = mapTouch(activeLayer, x, y);
                                float d = (float)Math.hypot(loc[0], loc[1]);
                                float factor = d / initialDist;
                                activeLayer.scaleX = Math.max(0.1f, initialScaleX * factor);
                                activeLayer.scaleY = Math.max(0.1f, initialScaleY * factor);
                            }
                            else if (touchMode == 3) {
                                float a = (float)Math.toDegrees(Math.atan2(y - activeLayerCenterY, x - activeLayerCenterX));
                                activeLayer.rotation = initialRotation + (a - initialAngle);
                            }
                            else if (touchMode == 4) {
                                float[] loc = mapTouch(activeLayer, x, y);
                                float factor = Math.abs(loc[0]) / initialDistX;
                                activeLayer.scaleX = Math.max(0.1f, initialScaleX * factor);
                            }
                            else if (touchMode == 5) {
                                float[] loc = mapTouch(activeLayer, x, y);
                                float factor = Math.abs(loc[1]) / initialDistY;
                                activeLayer.scaleY = Math.max(0.1f, initialScaleY * factor);
                            }
                            invalidate(); return true;
                        } break;
                    case MotionEvent.ACTION_UP:
                        if ((touchMode == 2 || touchMode == 4 || touchMode == 5) && activeLayer != null && activeLayer.type == 0) {
                            if (activeLayer.scaleX != 1f || activeLayer.scaleY != 1f) {
                                float avgScale = (activeLayer.scaleX + activeLayer.scaleY) / 2f;
                                activeLayer.textPaint.setTextSize(activeLayer.textPaint.getTextSize() * avgScale);
                                activeLayer.strokeWidth *= avgScale;
                                activeLayer.shadowRadius *= avgScale;
                                activeLayer.innerShadowRadius *= avgScale;
                                activeLayer.shadowOffsetX *= avgScale;
                                activeLayer.shadowOffsetY *= avgScale;
                                activeLayer.innerShadowOffsetX *= avgScale;
                                activeLayer.innerShadowOffsetY *= avgScale;
                                activeLayer.lineSpacing *= avgScale;

                                activeLayer.scaleX = 1f;
                                activeLayer.scaleY = 1f;

                                activeLayer.buildStaticLayout();
                                activeLayer.isDirty = true;
                                invalidate();
                            }
                        }
                        touchMode = 0;
                        return true;
                }
            } return super.onTouchEvent(event);
        }

        private void applyAutoColorRemoval(int colorToMatch) {
            if (baseImage == null || drawingManager.getEraseLayerCanvas() == null) return;
            Bitmap eraseBitmap = drawingManager.getEraseLayerBitmap();
            int width = eraseBitmap.getWidth(), height = eraseBitmap.getHeight();
            int[] maskPixels = new int[width * height];
            int rT = Color.red(colorToMatch), gT = Color.green(colorToMatch), bT = Color.blue(colorToMatch);
            eraseBitmap.getPixels(maskPixels, 0, width, 0, 0, width, height);

            for (int i=0; i < maskPixels.length; i++) {
                int bmpC = baseImage.getPixel(i % width, i / width);
                if (Color.alpha(bmpC) == 0) continue;
                if (Math.abs(Color.red(bmpC) - rT) <= 25 && Math.abs(Color.green(bmpC) - gT) <= 25 && Math.abs(Color.blue(bmpC) - bT) <= 25) { maskPixels[i] = Color.BLACK; }
            }
            eraseBitmap.setPixels(maskPixels, 0, width, 0, 0, width, height); invalidate();
        }

        public Bitmap getRenderedBitmap(boolean isForExport) {
            Bitmap result = Bitmap.createBitmap(baseImage.getWidth(), baseImage.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(result);

            int sc = c.saveLayer(0, 0, result.getWidth(), result.getHeight(), null);
            c.drawBitmap(baseImage, 0, 0, bitmapPaint);
            if (drawingManager.getEraseLayerBitmap() != null) {
                c.drawBitmap(drawingManager.getEraseLayerBitmap(), 0, 0, autoPunchPaint);
            }
            c.restoreToCount(sc);

            if (drawingManager.getDrawLayerBitmap() != null) {
                c.drawBitmap(drawingManager.getDrawLayerBitmap(), 0, 0, hqPaint);
            }
            for (GraphicLayer layer : graphicLayers) {
                c.save();
                if (!isLayerOutMode && !isForExport) {
                    c.clipRect(0, 0, result.getWidth(), result.getHeight());
                }
                c.translate(result.getWidth()/2f, result.getHeight()/2f); c.translate(layer.x, layer.y);

                c.rotate(layer.rotation);
                c.scale(layer.scaleX, layer.scaleY);

                layer.drawLayer(c);
                c.restore();
            }

            boolean tc = isCropping; GraphicLayer tl = activeLayer; boolean tp = isPerspectiveMode;
            if (isForExport) { isCropping = false; isPerspectiveMode = false; activeLayer = null; }
            isCropping = tc; activeLayer = tl; isPerspectiveMode = tp; return result;
        }
    }
}