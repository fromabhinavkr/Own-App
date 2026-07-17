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
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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

@SuppressWarnings("all") // FORCES ANDROID STUDIO TO IGNORE ALL YELLOW STRUCTURAL WARNINGS
@SuppressLint({"SetTextI18n", "ClickableViewAccessibility", "DefaultLocale", "InflateParams"})
public class ImageEditorActivity extends AppCompatActivity {

    private FrameLayout canvasContainer;
    private PhotoEditorView editorView;
    private View tapToStartView;
    private View rightToolsPanel;
    public View cropToolsBar;
    private LinearLayout leftLayersPanel;

    private RecyclerView layersRecyclerView;
    private LayerAdapter layerAdapter;

    private Button btnZoom, btnGrid, btnLayerOut;

    private boolean isDarkTheme;
    private int panelColor;

    private LayerSettingsUI layerSettingsUI;

    // The semi-transparent loading screen overlay for exports!
    private FrameLayout loadingOverlay;

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
                    if (layerSettingsUI != null) {
                        layerSettingsUI.loadCustomFont(result.getData().getData());
                        if (layerSettingsUI.dialogUpdateRunnable != null) layerSettingsUI.dialogUpdateRunnable.run();
                    }
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

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
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

        layerSettingsUI = new LayerSettingsUI(this, editorView, isDarkTheme);

        editorView.setTextDoubleTapListener(layer -> layerSettingsUI.showTextAddDialog(layer));

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
        Button btnCloneTool = findViewById(R.id.btnCloneTool);
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
                layerSettingsUI.showTextAddDialog(null);
            });
        }

        if (btnLayerEdit != null) {
            btnLayerEdit.setOnClickListener(btnView -> {
                LayerSettingsUI.GraphicLayer layer = editorView.getActiveLayer();
                if (layer == null) {
                    Toast.makeText(this, "Select a layer on the canvas first!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (rightToolsPanel != null) rightToolsPanel.setVisibility(View.GONE);
                layerSettingsUI.showLayerEditDialog(layer);
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

                AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
                builder.setView(dialogView);
                AlertDialog dialog = builder.create();
                if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
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

                com.google.android.material.button.MaterialButton btnEraserBase = dialogView.findViewById(R.id.btnManualEraser);

                com.google.android.material.button.MaterialButton btnSmooth = new com.google.android.material.button.MaterialButton(
                        dialogView.getContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                int smoothBtnId = View.generateViewId();
                btnSmooth.setId(smoothBtnId);
                btnSmooth.setText("SMOOTH");
                btnSmooth.setStrokeWidth(0);
                toggleGroup.addView(btnSmooth);

                for (int i = 0; i < toggleGroup.getChildCount(); i++) {
                    View child = toggleGroup.getChildAt(i);
                    if (child instanceof com.google.android.material.button.MaterialButton) {
                        com.google.android.material.button.MaterialButton btn = (com.google.android.material.button.MaterialButton) child;
                        btn.setTextSize(10f);
                        btn.setPadding(0, btn.getPaddingTop(), 0, btn.getPaddingBottom());
                        btn.setMaxLines(1);

                        if (btnEraserBase != null) {
                            btn.setTextColor(btnEraserBase.getTextColors());
                            btn.setBackgroundTintList(btnEraserBase.getBackgroundTintList());
                            btn.setStrokeColor(btnEraserBase.getStrokeColor());
                        }

                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                        btn.setLayoutParams(lp);
                    }
                }

                LinearLayout smoothContainer = new LinearLayout(this);
                smoothContainer.setOrientation(LinearLayout.VERTICAL);
                smoothContainer.setVisibility(View.GONE);
                smoothContainer.setPadding(0, 32, 0, 0);

                TextView tvSmoothLabel = new TextView(this);
                tvSmoothLabel.setText("Smooth Edge Level (0 - 5)");
                tvSmoothLabel.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
                tvSmoothLabel.setTypeface(null, android.graphics.Typeface.BOLD);
                tvSmoothLabel.setPadding(0, 0, 0, 16);

                Slider sliderSmooth = new Slider(this);
                sliderSmooth.setValueFrom(0f);
                sliderSmooth.setValueTo(5f);
                sliderSmooth.setStepSize(1f);

                sliderSmooth.setValue(editorView.currentSmoothLevel);

                smoothContainer.addView(tvSmoothLabel);
                smoothContainer.addView(sliderSmooth);

                ViewGroup parentGroup = (ViewGroup) brushSizeContainer.getParent();
                parentGroup.addView(smoothContainer, parentGroup.indexOfChild(brushSizeContainer) + 1);

                toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                    if (!isChecked) return;

                    brushSizeContainer.setVisibility(checkedId == R.id.btnManualEraser || checkedId == R.id.btnRepair ? View.VISIBLE : View.GONE);
                    tvAutoColorInstructions.setVisibility(checkedId == R.id.btnAutoColor ? View.VISIBLE : View.GONE);
                    smoothContainer.setVisibility(checkedId == smoothBtnId ? View.VISIBLE : View.GONE);

                    if (checkedId == smoothBtnId) {
                        Bitmap currentMask = editorView.drawingManager.getEraseLayerBitmap();
                        if (currentMask != null && editorView.rawEraseMask == null) {
                            Bitmap.Config config = currentMask.getConfig();
                            if (config == null) config = Bitmap.Config.ARGB_8888;
                            editorView.rawEraseMask = currentMask.copy(config, true);
                        }
                    }
                });

                sliderSmooth.addOnChangeListener((slider, value, fromUser) -> {
                    if (fromUser && editorView.rawEraseMask != null) {
                        editorView.currentSmoothLevel = value;
                        int level = (int) value;
                        Bitmap currentMask = editorView.drawingManager.getEraseLayerBitmap();
                        if (currentMask != null) {
                            applySmoothToMaskInPlace(currentMask, editorView.rawEraseMask, level);
                            editorView.invalidate();
                        }
                    }
                });

                AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
                builder.setView(dialogView);
                AlertDialog dialog = builder.create();
                if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

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
                    } else if (checkedId == smoothBtnId) {
                        Toast.makeText(this, "Edge Smoothing Applied!", Toast.LENGTH_SHORT).show();
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

                // FULLY QUALIFIED SEEKBAR
                android.widget.SeekBar sbSize = dialogView.findViewById(R.id.sbBrushSize);
                android.widget.SeekBar sbOpacity = dialogView.findViewById(R.id.sbBrushOpacity);

                Button btnEraser = dialogView.findViewById(R.id.btnEraserToggle);
                Button btnApply = dialogView.findViewById(R.id.btnBrushApply);
                FrameLayout colorWheelContainer = dialogView.findViewById(R.id.colorWheelContainer);

                etHexCode.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);

                LayerSettingsUI.ColorPickerView colorPicker = new LayerSettingsUI.ColorPickerView(this);
                LinearLayout.LayoutParams wheelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400);
                colorPicker.setLayoutParams(wheelLp);
                colorWheelContainer.addView(colorPicker);

                sbSize.setProgress((int) editorView.drawingManager.currentBrushWidth);
                sbOpacity.setProgress((int) ((editorView.drawingManager.currentBrushOpacity / 255f) * 100f));
                etHexCode.setText(String.format("#%06X", (0xFFFFFF & editorView.drawingManager.currentBrushColor)));
                colorPicker.setColor(editorView.drawingManager.currentBrushColor);

                boolean isEraser = editorView.drawingManager.isDrawEraserMode;
                btnEraser.setBackgroundTintList(ColorStateList.valueOf(isEraser ? Color.parseColor("#FF3B30") : Color.parseColor("#E5E5EA")));
                btnEraser.setTextColor(isEraser ? Color.WHITE : Color.parseColor("#333333"));

                AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
                builder.setView(dialogView);
                AlertDialog dialog = builder.create();
                if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

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
                colorPicker.setOnColorChangeListener(color -> {
                    if (!editorView.drawingManager.isDrawEraserMode) {
                        editorView.drawingManager.currentBrushColor = color;
                        if (!isUpdating[0]) {
                            isUpdating[0] = true;
                            etHexCode.setText(String.format("#%06X", (0xFFFFFF & color)));
                            isUpdating[0] = false;
                        }
                    }
                });

                // FULLY QUALIFIED TEXT WATCHER
                etHexCode.addTextChangedListener(new android.text.TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (isUpdating[0]) return;
                        if (s.length() == 7 && s.toString().startsWith("#")) {
                            try {
                                int newC = Color.parseColor(s.toString());
                                editorView.drawingManager.currentBrushColor = newC;
                                colorPicker.setColor(newC);
                            } catch (Exception ignored) {}
                        }
                    }
                    @Override public void afterTextChanged(android.text.Editable s) {}
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
                    LayerSettingsUI.GraphicLayer active = editorView.getActiveLayer();

                    if (active != null) {
                        if (active.type == 1 && active.bitmap != null) {
                            currentImage = active.bitmap.copy(Bitmap.Config.ARGB_8888, true);
                        } else {
                            active.bake();
                            currentImage = active.bakedCache != null ? active.bakedCache.copy(Bitmap.Config.ARGB_8888, true) : editorView.getRenderedBitmap(true);
                        }
                    } else {
                        currentImage = editorView.getRenderedBitmap(true);
                    }

                    try {
                        File tempIn = new File(getCacheDir(), "clone_in.png");
                        FileOutputStream fos = new FileOutputStream(tempIn);
                        currentImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.close();

                        runOnUiThread(() -> {
                            btnCloneTool.setEnabled(true);
                            btnCloneTool.setText("Clone Tool");
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
                    LayerSettingsUI.GraphicLayer active = editorView.getActiveLayer();

                    if (active != null) {
                        if (active.type == 1 && active.bitmap != null) {
                            currentImage = active.bitmap.copy(Bitmap.Config.ARGB_8888, true);
                        } else {
                            active.bake();
                            currentImage = active.bakedCache != null ? active.bakedCache.copy(Bitmap.Config.ARGB_8888, true) : editorView.getRenderedBitmap(true);
                        }
                    } else {
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

        setupLoadingOverlay();

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

    private void setupLoadingOverlay() {
        loadingOverlay = new FrameLayout(this);
        loadingOverlay.setBackgroundColor(Color.parseColor("#B3000000")); // Semi-transparent black
        loadingOverlay.setClickable(true);
        loadingOverlay.setFocusable(true);
        loadingOverlay.setVisibility(View.GONE);

        android.widget.ProgressBar pb = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        pb.setIndeterminate(true);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(150, 150, android.view.Gravity.CENTER);
        loadingOverlay.addView(pb, params);

        ViewGroup root = findViewById(android.R.id.content);
        root.addView(loadingOverlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void exportImage(int formatIndex) {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Processing and Saving...", Toast.LENGTH_SHORT).show();

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
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
                        runOnUiThread(() -> {
                            if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                            Toast.makeText(this, "Saved to Pictures/OWN's Image studio", Toast.LENGTH_LONG).show();
                        });
                    } else {
                        runOnUiThread(() -> {
                            if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                            Toast.makeText(this, "Export Failed", Toast.LENGTH_SHORT).show();
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                        Toast.makeText(this, "Export Failed", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        }, 50);
    }

    public void launchFontPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf"});
        pickFontLauncher.launch(intent);
    }

    public void hideToolsPanel() {
        if (rightToolsPanel != null) rightToolsPanel.setVisibility(View.GONE);
    }

    private void applySmoothToMaskInPlace(Bitmap mask, Bitmap originalBackup, int level) {
        if (mask == null || originalBackup == null) return;
        int width = mask.getWidth();
        int height = mask.getHeight();

        if (level == 0) {
            int[] pixels = new int[width * height];
            originalBackup.getPixels(pixels, 0, width, 0, 0, width, height);
            mask.setPixels(pixels, 0, width, 0, 0, width, height);
            return;
        }

        float radius = level + 1f;

        Bitmap temp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(temp);

        // FULLY QUALIFIED BLUR MASK FILTER
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setMaskFilter(new android.graphics.BlurMaskFilter(radius, android.graphics.BlurMaskFilter.Blur.SOLID));

        Bitmap alpha = originalBackup.extractAlpha();
        c.drawBitmap(alpha, 0, 0, paint);
        alpha.recycle();

        int[] tempPixels = new int[width * height];
        temp.getPixels(tempPixels, 0, width, 0, 0, width, height);
        mask.setPixels(tempPixels, 0, width, 0, 0, width, height);
        temp.recycle();
    }

    private void showEmptyCanvasColorPicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("Choose Canvas Color");
        title.setTextSize(20f);
        // FULLY QUALIFIED TYPEFACE
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 32);
        mainLayout.addView(title);

        LayerSettingsUI.ColorPickerView colorPicker = new LayerSettingsUI.ColorPickerView(this);
        LinearLayout.LayoutParams wheelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 500);
        colorPicker.setLayoutParams(wheelLp);
        mainLayout.addView(colorPicker);

        LinearLayout hexRow = new LinearLayout(this);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(android.view.Gravity.CENTER);
        hexRow.setPadding(0, 32, 0, 16);

        TextView hexLabel = new TextView(this);
        hexLabel.setText("HEX: ");
        hexLabel.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        hexLabel.setTypeface(null, android.graphics.Typeface.BOLD);

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

        colorPicker.setOnColorChangeListener(color -> {
            selectedColor[0] = color;
            if (!isUpdating[0]) {
                isUpdating[0] = true;
                etHex.setText(String.format("#%06X", (0xFFFFFF & color)));
                isUpdating[0] = false;
            }
        });

        // FULLY QUALIFIED TEXT WATCHER
        etHex.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUpdating[0]) return;
                if (s.length() == 7 && s.toString().startsWith("#")) {
                    try {
                        int newC = Color.parseColor(s.toString());
                        selectedColor[0] = newC;
                        isUpdating[0] = true;
                        colorPicker.setColor(newC);
                        isUpdating[0] = false;
                    } catch (Exception ignored) {}
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
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

    public void setDialogTextColor(View view, int color) {
        if (view instanceof TextView && !(view instanceof Button) && !(view instanceof EditText)) {
            ((TextView) view).setTextColor(color);
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                setDialogTextColor(vg.getChildAt(i), color);
            }
        }
    }

    public AlertDialog createModernRoundedDialog(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        return dialog;
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

    public void forceDialogBackground(View view) {
        if (view == null) return;
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(panelColor);
        gd.setCornerRadius(60f);
        view.setBackground(gd);
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

            int maxDimension = isOverlay ? 2048 : 4096;
            options.inSampleSize = calculateInSampleSize(options, maxDimension, maxDimension);
            options.inJustDecodeBounds = false;

            options.inMutable = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            }

            is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            if (is != null) is.close();

            if (bitmap != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.getConfig() == Bitmap.Config.HARDWARE) {
                    Bitmap swBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                    bitmap.recycle();
                    bitmap = swBitmap;
                }

                if (isOverlay) {
                    int bW = bitmap.getWidth();
                    int bH = bitmap.getHeight();
                    if (bW > maxDimension || bH > maxDimension) {
                        float ratio = Math.min((float) maxDimension / bW, (float) maxDimension / bH);
                        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, (int) (bW * ratio), (int) (bH * ratio), true);
                        if (scaled != bitmap) {
                            bitmap.recycle();
                            bitmap = scaled;
                        }
                    }
                }

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

    private class LayerAdapter extends RecyclerView.Adapter<LayerAdapter.LayerViewHolder> {

        class LayerViewHolder extends RecyclerView.ViewHolder {
            LayerViewHolder(View itemView) { super(itemView); }
        }

        private List<LayerSettingsUI.GraphicLayer> getReversedLayers() {
            List<LayerSettingsUI.GraphicLayer> reversed = new ArrayList<>(editorView.getLayers());
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
            List<LayerSettingsUI.GraphicLayer> rev = getReversedLayers();
            if (position >= rev.size()) return;

            LayerSettingsUI.GraphicLayer layer = rev.get(position);
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

            tv.setGravity(android.view.Gravity.CENTER_VERTICAL);
            tv.setOnClickListener(v -> editorView.setActiveLayer(layer));
        }

        @Override
        public int getItemCount() {
            return editorView.getLayers().size();
        }

        @SuppressLint("NotifyDataSetChanged")
        public void moveItem(int fromPosition, int toPosition) {
            List<LayerSettingsUI.GraphicLayer> original = editorView.getLayers();
            List<LayerSettingsUI.GraphicLayer> reversed = new ArrayList<>(original);
            Collections.reverse(reversed);

            LayerSettingsUI.GraphicLayer moved = reversed.remove(fromPosition);
            reversed.add(toPosition, moved);

            Collections.reverse(reversed);
            original.clear();
            original.addAll(reversed);

            notifyDataSetChanged();
            editorView.invalidate();
        }
    }

    public static class ActionRecord {
        int type;
        LayerSettingsUI.GraphicLayer layer;
        DrawingToolManager.DrawStroke stroke;
        public ActionRecord(LayerSettingsUI.GraphicLayer l) { type = 0; layer = l; }
        public ActionRecord(DrawingToolManager.DrawStroke s) { type = 1; stroke = s; }
    }

    public static class PhotoEditorView extends View {
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

        public float currentSmoothLevel = 0f;
        public Bitmap rawEraseMask = null;

        public boolean isZoomMode = false;
        public boolean isGridMode = false;

        public boolean isLayerOutMode = false;

        private boolean isColorPickerMode = false;
        private EyedropperCallback eyedropperCallback;
        private float pickerX = 0, pickerY = 0;
        private int pickerColor = Color.BLACK;

        private float viewZoom = 1f, viewPanX = 0f, viewPanY = 0f;
        private android.view.ScaleGestureDetector scaleDetector;

        public float imgBrightness = 0f, imgContrast = 1f, imgSaturation = 1f, imgHue = 0f;

        private final List<LayerSettingsUI.GraphicLayer> graphicLayers = new ArrayList<>();
        private final List<ActionRecord> undoStack = new ArrayList<>();

        private LayerSettingsUI.GraphicLayer activeLayer = null;
        private Runnable layerListener;

        public interface TextDoubleTapListener { void onDoubleTap(LayerSettingsUI.GraphicLayer layer); }
        private TextDoubleTapListener textDoubleTapListener;
        public void setTextDoubleTapListener(TextDoubleTapListener listener) { this.textDoubleTapListener = listener; }
        private long lastTapTime = 0;

        public interface OnModeChangeListener { void onModeChanged(); }
        private OnModeChangeListener modeListener;
        public void setOnModeChangeListener(OnModeChangeListener listener) { this.modeListener = listener; }

        public LayerSettingsUI.GraphicLayer getActiveLayer() { return activeLayer; }
        public List<LayerSettingsUI.GraphicLayer> getLayers() { return graphicLayers; }
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
            init(context);
        }

        private void init(Context context) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setupPaints();
            setupCheckerboard();
            scaleDetector = new android.view.ScaleGestureDetector(context, new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(@NonNull android.view.ScaleGestureDetector detector) {
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

        // FULLY QUALIFIED PARAMETERS TO STOP ANDROID STUDIO IMPORT GLITCHES
        public void addAdvancedTextLayer(String text, android.graphics.Typeface typeface, int color, android.text.Layout.Alignment align, float letterSpacing, float lineSpacing, float strokeWidth, int strokeColor, float shadowRadius, int shadowColor, float innerShadowRadius, int innerShadowColor) {
            disableSpecialModes();
            LayerSettingsUI.GraphicLayer layer = new LayerSettingsUI.GraphicLayer(text, typeface, color, align, letterSpacing, lineSpacing, strokeWidth, strokeColor, shadowRadius, shadowColor, innerShadowRadius, innerShadowColor, 0f, 0f);
            graphicLayers.add(layer); activeLayer = layer;
            undoStack.add(new ActionRecord(layer));
            if (layerListener != null) layerListener.run(); invalidate();
        }

        public void addImageLayer(Bitmap bmp) {
            disableSpecialModes(); LayerSettingsUI.GraphicLayer layer = new LayerSettingsUI.GraphicLayer(bmp, 0f, 0f);
            graphicLayers.add(layer); activeLayer = layer;
            undoStack.add(new ActionRecord(layer));
            if (layerListener != null) layerListener.run(); invalidate();
        }

        public void copyActiveLayer() {
            if (activeLayer == null) return;
            LayerSettingsUI.GraphicLayer toCopy = new LayerSettingsUI.GraphicLayer(activeLayer); disableSpecialModes();
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
                if (isBgRemoverMode) {
                    currentSmoothLevel = 0f;
                    if (rawEraseMask != null) { rawEraseMask.recycle(); rawEraseMask = null; }
                }
            }
            invalidate();
        }

        public void deselectLayer() { activeLayer = null; invalidate(); }
        public void setActiveLayer(LayerSettingsUI.GraphicLayer layer) { activeLayer = layer; invalidate(); }

        public void clearModifications() {
            disableSpecialModes(); graphicLayers.clear(); undoStack.clear();
            drawingManager.clear();
            viewZoom = 1f; viewPanX = 0f; viewPanY = 0f; isCircleCrop = false; isPerspectiveMode = false; isGridMode = false; isLayerCropping = false;

            currentSmoothLevel = 0f;
            if (rawEraseMask != null) { rawEraseMask.recycle(); rawEraseMask = null; }

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

                // FULLY QUALIFIED EVEN_ODD TO PREVENT ERRORS
                drawPerspectiveMask.setFillType(android.graphics.Path.FillType.EVEN_ODD);
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

                // FULLY QUALIFIED EVEN_ODD TO PREVENT ERRORS
                cropMaskPath.setFillType(android.graphics.Path.FillType.EVEN_ODD);
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

            for (LayerSettingsUI.GraphicLayer layer : graphicLayers) {
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

                    float l = layer.bounds.left * layer.scaleX;
                    float t = layer.bounds.top * layer.scaleY;
                    float r = layer.bounds.right * layer.scaleX;
                    float b = layer.bounds.bottom * layer.scaleY;
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

        private float[] mapTouch(LayerSettingsUI.GraphicLayer l, float x, float y) {
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

                        if (isBgRemoverMode) {
                            currentSmoothLevel = 0f;
                            if (rawEraseMask != null) {
                                rawEraseMask.recycle();
                                rawEraseMask = null;
                            }
                        }

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

                            float l = activeLayer.bounds.left * activeLayer.scaleX;
                            float t = activeLayer.bounds.top * activeLayer.scaleY;
                            float r = activeLayer.bounds.right * activeLayer.scaleX;
                            float b = activeLayer.bounds.bottom * activeLayer.scaleY;
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
                            LayerSettingsUI.GraphicLayer layer = graphicLayers.get(i);
                            float[] loc = mapTouch(layer, x, y);

                            float bl = layer.bounds.left * layer.scaleX;
                            float bt = layer.bounds.top * layer.scaleY;
                            float br = layer.bounds.right * layer.scaleX;
                            float bb = layer.bounds.bottom * layer.scaleY;

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

                                // FIX: Removed redundant cast to satisfy Android Studio strictly
                                if (activeLayer.textPaint != null) {
                                    activeLayer.textPaint.setTextSize(activeLayer.textPaint.getTextSize() * avgScale);
                                }

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

                // FIX: Extracted matching logic to satisfy compiler warning seamlessly
                if (Math.abs(Color.red(bmpC) - rT) <= 25 &&
                        Math.abs(Color.green(bmpC) - gT) <= 25 &&
                        Math.abs(Color.blue(bmpC) - bT) <= 25) {
                    maskPixels[i] = Color.BLACK;
                }
            }
            eraseBitmap.setPixels(maskPixels, 0, width, 0, 0, width, height);

            currentSmoothLevel = 0f;
            if (rawEraseMask != null) {
                rawEraseMask.recycle();
                rawEraseMask = null;
            }

            invalidate();
        }

        public Bitmap getRenderedBitmap(boolean isForExport) {
            boolean tc = isCropping;
            LayerSettingsUI.GraphicLayer tl = activeLayer;
            boolean tp = isPerspectiveMode;

            if (isForExport) {
                isCropping = false;
                isPerspectiveMode = false;
                activeLayer = null;
            }

            Bitmap result = Bitmap.createBitmap(baseImage.getWidth(), baseImage.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);

            int sc = canvas.saveLayer(0, 0, result.getWidth(), result.getHeight(), null);
            canvas.drawBitmap(baseImage, 0, 0, bitmapPaint);
            if (drawingManager.getEraseLayerBitmap() != null) {
                canvas.drawBitmap(drawingManager.getEraseLayerBitmap(), 0, 0, autoPunchPaint);
            }
            canvas.restoreToCount(sc);

            if (drawingManager.getDrawLayerBitmap() != null) {
                canvas.drawBitmap(drawingManager.getDrawLayerBitmap(), 0, 0, hqPaint);
            }

            for (LayerSettingsUI.GraphicLayer layer : graphicLayers) {
                canvas.save();
                if (!isLayerOutMode && !isForExport) {
                    canvas.clipRect(0, 0, result.getWidth(), result.getHeight());
                }
                canvas.translate(result.getWidth() / 2f, result.getHeight() / 2f);
                canvas.translate(layer.x, layer.y);

                canvas.rotate(layer.rotation);
                canvas.scale(layer.scaleX, layer.scaleY);

                layer.drawLayer(canvas);
                canvas.restore();
            }

            if (isForExport) {
                isCropping = tc;
                activeLayer = tl;
                isPerspectiveMode = tp;
            }
            return result;
        }
    }
}