package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;

@SuppressWarnings("all") @SuppressLint({"SetTextI18n", "SpellCheckingInspection", "ClickableViewAccessibility", "DrawAllocation"})
public class StickerMakerActivity extends AppCompatActivity {
    private Button btnAddWA, btnPick; private TextView tvSubtitle; private EditText etTitle; private GridLayout stickerGrid;
    private LinearLayout packSelectorContainer; private HorizontalScrollView packScrollView;
    private int stickerCount = 0; private boolean isDarkTheme; private int textColor, cardColor, panelColor, glassBorderColor; private SharedPreferences prefs;
    private String currentPackId = "ownphoto_pack_1"; private int staticPackCount = 3;

    private final ActivityResultLauncher<Intent> pickMediaLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) { Uri uri = result.getData().getData(); if (uri != null) showVisualCropStudio(uri); }
    });

    private final ActivityResultLauncher<Intent> addStickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_CANCELED && result.getData() != null) { String validationError = result.getData().getStringExtra("validation_error"); if (validationError != null) Toast.makeText(this, "WA Rejected: " + validationError, Toast.LENGTH_LONG).show(); }
        else if (result.getResultCode() == Activity.RESULT_OK) { Toast.makeText(this, "Successfully added to WhatsApp!", Toast.LENGTH_SHORT).show(); }
    });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_sticker_maker);
        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE); isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
        View root = findViewById(R.id.stickerMakerRoot); etTitle = findViewById(R.id.etStickerTitle); tvSubtitle = findViewById(R.id.tvStickerSubtitle);
        btnPick = findViewById(R.id.btnPickImage); btnAddWA = findViewById(R.id.btnAddWhatsApp); stickerGrid = findViewById(R.id.stickerGrid);
        packSelectorContainer = findViewById(R.id.packSelectorContainer); packScrollView = findViewById(R.id.packScrollView);

        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7"); cardColor = isDarkTheme ? Color.parseColor("#E62C2C2E") : Color.parseColor("#F2FFFFFF"); panelColor = isDarkTheme ? Color.parseColor("#E63A3A3C") : Color.parseColor("#E6E5E5EA"); textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#1C1C1E"); glassBorderColor = isDarkTheme ? Color.parseColor("#33FFFFFF") : Color.parseColor("#D1D1D6");
        root.setBackgroundColor(bgColor); etTitle.setTextColor(textColor); etTitle.setHintTextColor(isDarkTheme ? Color.parseColor("#8E8E93") : Color.parseColor("#888888"));
        tvSubtitle.setTextColor(isDarkTheme ? Color.parseColor("#8E8E93") : Color.parseColor("#666666"));

        staticPackCount = prefs.getInt("static_pack_count", 3); refreshPackTabBar(); switchPack("ownphoto_pack_1");

        etTitle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {} @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { prefs.edit().putString("pack_name_" + currentPackId, s.toString().trim()).apply(); }
        });

        btnPick.setOnClickListener(v -> {
            if (stickerCount >= 30) { Toast.makeText(this, "Pack is full (30 max)!", Toast.LENGTH_SHORT).show(); return; }
            Intent intent = new Intent(Intent.ACTION_PICK); intent.setType("image/*"); pickMediaLauncher.launch(intent);
        });

        btnAddWA.setOnClickListener(v -> {
            if (stickerCount >= 3) {
                String currentName = prefs.getString("pack_name_" + currentPackId, "My Custom Pack"); if (currentName.isEmpty()) currentName = "My Custom Pack";
                int currentVersion = prefs.getInt("sticker_version_" + currentPackId, 3); prefs.edit().putInt("sticker_version_" + currentPackId, currentVersion + 1).apply();
                Intent intent = new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK"); intent.putExtra("sticker_pack_id", currentPackId); intent.putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY); intent.putExtra("sticker_pack_name", currentName);
                try { intent.setPackage("com.whatsapp"); addStickerLauncher.launch(intent); } catch (ActivityNotFoundException e) { try { intent.setPackage("com.whatsapp.w4b"); addStickerLauncher.launch(intent); } catch (ActivityNotFoundException e2) { Toast.makeText(this, "WhatsApp is not installed.", Toast.LENGTH_LONG).show(); } }
            }
        });
    }

    private void refreshPackTabBar() {
        packSelectorContainer.removeAllViews();
        for (int i = 1; i <= staticPackCount; i++) {
            String pId = "ownphoto_pack_" + i; String label = "📦 Pack " + i; Button btn = createPackButton(label, pId.equals(currentPackId), false); final int idx = i;
            btn.setOnClickListener(v -> switchPack(pId)); if (i > 1) { btn.setOnLongClickListener(v -> { showDeletePackDialog(pId, idx); return true; }); } packSelectorContainer.addView(btn);
        }
        Button btnAddStatic = createPackButton("+ Static", false, true);
        btnAddStatic.setOnClickListener(v -> { staticPackCount++; prefs.edit().putInt("static_pack_count", staticPackCount).apply(); String newId = "ownphoto_pack_" + staticPackCount; prefs.edit().putString("pack_name_" + newId, "Static Pack " + staticPackCount).apply(); refreshPackTabBar(); switchPack(newId); packScrollView.post(() -> packScrollView.fullScroll(View.FOCUS_RIGHT)); });
        packSelectorContainer.addView(btnAddStatic);
    }

    private Button createPackButton(String text, boolean isActive, boolean isAddBtn) {
        Button btn = new Button(this); btn.setText(text); btn.setTextSize(13f); btn.setTypeface(null, Typeface.BOLD); btn.setAllCaps(false); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 100); lp.setMargins(0, 0, 8, 0); btn.setLayoutParams(lp); btn.setPadding(36, 0, 36, 0);
        GradientDrawable gd = new GradientDrawable(); gd.setCornerRadius(100f); gd.setStroke(2, glassBorderColor);
        if (isAddBtn) { gd.setColor(panelColor); btn.setTextColor(Color.parseColor("#4A90E2")); }
        else if (isActive) { gd.setColor(Color.parseColor("#4A90E2")); btn.setTextColor(Color.WHITE); }
        else { gd.setColor(cardColor); btn.setTextColor(textColor); }
        btn.setBackgroundTintList(null); btn.setBackground(gd); return btn;
    }

    private void switchPack(String packId) {
        currentPackId = packId; refreshPackTabBar();
        String savedName = prefs.getString("pack_name_" + currentPackId, "My Custom Pack");
        etTitle.setText(savedName); btnPick.setText("Add New Sticker"); loadExistingStickers();
    }

    private void showDeletePackDialog(String packIdToDelete, int deleteIdx) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        builder.setTitle("Delete Pack").setMessage("Delete this entire pack and all stickers inside it?").setPositiveButton("DELETE", (dialog, which) -> {
            File dir = new File(getFilesDir(), "stickers/" + packIdToDelete); deleteDirectory(dir);
            for (int m = deleteIdx + 1; m <= staticPackCount; m++) { File oldDir = new File(getFilesDir(), "stickers/ownphoto_pack_" + m); File newDir = new File(getFilesDir(), "stickers/ownphoto_pack_" + (m - 1)); oldDir.renameTo(newDir); prefs.edit().putString("pack_name_ownphoto_pack_" + (m - 1), prefs.getString("pack_name_ownphoto_pack_" + m, "Static Pack " + (m - 1))).putInt("sticker_version_ownphoto_pack_" + (m - 1), prefs.getInt("sticker_version_ownphoto_pack_" + m, 3)).apply(); }
            staticPackCount--; prefs.edit().putInt("static_pack_count", staticPackCount).apply(); switchPack("ownphoto_pack_1");
            Toast.makeText(this, "Pack deleted", Toast.LENGTH_SHORT).show();
        }).setNegativeButton("CANCEL", null);
        AlertDialog dialog = builder.create(); applyGlassDialogStyle(dialog); dialog.show();
    }

    private void deleteDirectory(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) { File[] children = fileOrDirectory.listFiles(); if (children != null) { for (File child : children) deleteDirectory(child); } } fileOrDirectory.delete();
    }

    private void loadExistingStickers() {
        File dir = new File(getFilesDir(), "stickers/" + currentPackId); if (!dir.exists() && !dir.mkdirs()) { Toast.makeText(this, "Storage access error", Toast.LENGTH_SHORT).show(); return; }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".webp") && !name.contains("_tmp") && !name.contains("_thumb") && new File(d, name).length() > 100);
        stickerCount = (files != null) ? files.length : 0; updateUI(files);
    }

    private void updateUI(File[] files) {
        stickerGrid.removeAllViews();
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> { try { int n1 = Integer.parseInt(f1.getName().replace(".webp", "")); int n2 = Integer.parseInt(f2.getName().replace(".webp", "")); return Integer.compare(n1, n2); } catch (NumberFormatException e) { return 0; } });
            for (File file : files) {
                File thumbFile = new File(file.getParentFile(), file.getName().replace(".webp", "_thumb.png"));
                Bitmap bmp = thumbFile.exists() ? BitmapFactory.decodeFile(thumbFile.getAbsolutePath()) : BitmapFactory.decodeFile(file.getAbsolutePath());
                addStickerToGrid(file, bmp);
            }
        }
        if (stickerCount < 3) {
            tvSubtitle.setText("Add " + (3 - stickerCount) + " more stickers.\nLong-press a sticker to delete."); tvSubtitle.setTextColor(isDarkTheme ? Color.parseColor("#FF9F0A") : Color.parseColor("#FF3B30"));
            btnAddWA.setBackgroundTintList(ColorStateList.valueOf(panelColor)); btnAddWA.setTextColor(isDarkTheme ? Color.parseColor("#8E8E93") : Color.parseColor("#888888")); btnAddWA.setEnabled(false);
        } else {
            tvSubtitle.setText(stickerCount + " Stickers Ready!\nLong-press a sticker to delete."); tvSubtitle.setTextColor(isDarkTheme ? Color.parseColor("#32D74B") : Color.parseColor("#34C759"));
            btnAddWA.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#34C759"))); btnAddWA.setTextColor(Color.WHITE); btnAddWA.setEnabled(true);
        }
    }

    private void addStickerToGrid(File file, Bitmap bitmap) {
        LinearLayout frame = new LinearLayout(this); GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = 240; params.height = 240; params.setMargins(14, 14, 14, 14); frame.setLayoutParams(params);
        GradientDrawable gd = new GradientDrawable(); gd.setColor(cardColor); gd.setCornerRadius(40f); gd.setStroke(2, glassBorderColor); frame.setBackground(gd); frame.setGravity(Gravity.CENTER); frame.setPadding(16, 16, 16, 16);
        frame.setOnLongClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle); builder.setTitle("Delete Sticker").setMessage("Remove this sticker from the pack?").setPositiveButton("DELETE", (dialog, which) -> { if (file.delete()) { File thumbFile = new File(file.getParentFile(), file.getName().replace(".webp", "_thumb.png")); if (thumbFile.exists()) thumbFile.delete(); ensureValidTrayIcon(file.getParentFile()); Toast.makeText(this, "Sticker deleted", Toast.LENGTH_SHORT).show(); loadExistingStickers(); } }).setNegativeButton("CANCEL", null);
            AlertDialog dialog = builder.create(); applyGlassDialogStyle(dialog); dialog.show(); return true;
        });
        ImageView iv = new ImageView(this); iv.setImageBitmap(bitmap); iv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)); iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(iv); stickerGrid.addView(frame);
    }

    private void ensureValidTrayIcon(File dir) {
        try {
            File trayFile = new File(dir, "tray.png"); File[] files = dir.listFiles((d, name) -> name.endsWith(".webp") && !name.contains("_tmp") && !name.contains("_thumb"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, (f1, f2) -> { try { return Integer.compare(Integer.parseInt(f1.getName().replace(".webp","")), Integer.parseInt(f2.getName().replace(".webp",""))); } catch(Exception e){ return 0; } });
                File firstThumb = new File(dir, files[0].getName().replace(".webp", "_thumb.png")); Bitmap tb = firstThumb.exists() ? BitmapFactory.decodeFile(firstThumb.getAbsolutePath()) : BitmapFactory.decodeFile(files[0].getAbsolutePath());
                if (tb != null) { Bitmap tray = Bitmap.createScaledBitmap(tb, 96, 96, true); FileOutputStream out = new FileOutputStream(trayFile); tray.compress(Bitmap.CompressFormat.PNG, 75, out); out.close(); if (!tray.isRecycled()) tray.recycle(); if (!tb.isRecycled()) tb.recycle(); }
            }
        } catch (Exception ignored) {}
    }

    private Bitmap decodeSampledBitmapFromUri(Uri uri, int reqWidth, int reqHeight) {
        try {
            InputStream is = getContentResolver().openInputStream(uri); BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true; BitmapFactory.decodeStream(is, null, options); if (is != null) is.close();
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight); options.inJustDecodeBounds = false;
            is = getContentResolver().openInputStream(uri); Bitmap bmp = BitmapFactory.decodeStream(is, null, options); if (is != null) is.close(); return bmp;
        } catch (Exception e) { return null; }
    }
    private int calculateInSampleSize(BitmapFactory.Options options, int reqW, int reqH) {
        int h = options.outHeight, w = options.outWidth, inSampleSize = 1;
        if (h > reqH || w > reqW) { int halfH = h / 2, halfW = w / 2; while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) inSampleSize *= 2; } return inSampleSize;
    }

    private void showVisualCropStudio(Uri mediaUri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(35, 35, 35, 35);
        GradientDrawable gd = new GradientDrawable(); gd.setColor(cardColor); gd.setCornerRadius(60f); gd.setStroke(2, glassBorderColor); layout.setBackground(gd);

        TextView title = new TextView(this); title.setText("✂️ Grid Sticker Studio"); title.setTextSize(18f); title.setTypeface(null, Typeface.BOLD); title.setTextColor(textColor); title.setGravity(Gravity.CENTER); title.setPadding(0, 0, 0, 8); layout.addView(title);

        FrameLayout previewBox = new FrameLayout(this); LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(650, 650); boxLp.gravity = Gravity.CENTER; boxLp.setMargins(0, 4, 0, 14); previewBox.setLayoutParams(boxLp);
        GradientDrawable boxGd = new GradientDrawable(); boxGd.setColor(isDarkTheme ? Color.parseColor("#141414") : Color.parseColor("#F8F8F8")); boxGd.setStroke(4, Color.parseColor("#4A90E2")); boxGd.setCornerRadius(25f); previewBox.setBackground(boxGd);

        ImageView iv = new ImageView(this); iv.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); iv.setScaleType(ImageView.ScaleType.MATRIX); previewBox.addView(iv);

        View gridOverlay = new View(this) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG) {{ setColor(Color.WHITE); setStrokeWidth(2f); setAlpha(160); }};
            @Override protected void onDraw(Canvas c) {
                super.onDraw(c); int w = getWidth(), h = getHeight();
                c.drawLine(w/3f, 0, w/3f, h, p); c.drawLine(w*2/3f, 0, w*2/3f, h, p);
                c.drawLine(0, h/3f, w, h/3f, p); c.drawLine(0, h*2/3f, w, h*2/3f, p);
                c.drawRect(0, 0, w, h, new Paint(Paint.ANTI_ALIAS_FLAG) {{ setColor(Color.parseColor("#4A90E2")); setStyle(Paint.Style.STROKE); setStrokeWidth(6f); }});
            }
        };
        gridOverlay.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); previewBox.addView(gridOverlay); layout.addView(previewBox);

        final Bitmap[] previewBmp = {null};
        new Thread(() -> {
            try {
                previewBmp[0] = decodeSampledBitmapFromUri(mediaUri, 1024, 1024);
                if (previewBmp[0] != null) { runOnUiThread(() -> { iv.setImageBitmap(previewBmp[0]); setupTouchAndPresets(iv, previewBmp[0], previewBox, 650); }); }
            } catch (Exception ignored) {}
        }).start();

        LinearLayout chipRow = new LinearLayout(this); chipRow.setOrientation(LinearLayout.HORIZONTAL); chipRow.setGravity(Gravity.CENTER); chipRow.setPadding(0, 4, 0, 16);
        String[] cNames = {"Original", "1:1"};
        for (int i=0; i<2; i++) {
            Button b = new Button(this); b.setText(cNames[i]); b.setTextSize(13f); b.setAllCaps(false); b.setTypeface(null, Typeface.BOLD); b.setTextColor(textColor);
            GradientDrawable cGd = new GradientDrawable(); cGd.setColor(panelColor); cGd.setCornerRadius(50f); cGd.setStroke(2, glassBorderColor); b.setBackgroundTintList(null); b.setBackground(cGd);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, 95, 1f); clp.setMargins(10, 0, 10, 0); b.setLayoutParams(clp);
            int finalI = i; b.setOnClickListener(v -> { if (previewBmp[0] != null) applyRatioChipMatrix(iv, previewBmp[0], previewBox, 650, finalI); });
            chipRow.addView(b);
        }
        layout.addView(chipRow);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); pb.setIndeterminate(true); pb.setVisibility(View.GONE); layout.addView(pb);
        LinearLayout btnRow = new LinearLayout(this); btnRow.setOrientation(LinearLayout.HORIZONTAL); btnRow.setPadding(0, 10, 0, 0);

        Button btnCancel = new Button(this); btnCancel.setText("CANCEL"); btnCancel.setTextColor(Color.WHITE); btnCancel.setTypeface(null, Typeface.BOLD);
        GradientDrawable gdCancel = new GradientDrawable(); gdCancel.setColor(Color.parseColor("#FF3B30")); gdCancel.setCornerRadius(100f); btnCancel.setBackgroundTintList(null); btnCancel.setBackground(gdCancel);
        LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(0, 115, 1f); lpC.setMargins(0, 0, 10, 0); btnCancel.setLayoutParams(lpC);

        Button btnBuild = new Button(this); btnBuild.setText("COMPILE STICKER"); btnBuild.setTextColor(Color.WHITE); btnBuild.setTypeface(null, Typeface.BOLD);
        GradientDrawable gdBuild = new GradientDrawable(); gdBuild.setColor(Color.parseColor("#34C759")); gdBuild.setCornerRadius(100f); btnBuild.setBackgroundTintList(null); btnBuild.setBackground(gdBuild);
        LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(0, 115, 1f); lpB.setMargins(10, 0, 0, 0); btnBuild.setLayoutParams(lpB);

        btnRow.addView(btnCancel); btnRow.addView(btnBuild); layout.addView(btnRow);

        builder.setView(layout); AlertDialog dialog = builder.create(); if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnBuild.setOnClickListener(v -> {
            pb.setVisibility(View.VISIBLE); btnBuild.setEnabled(false); btnCancel.setEnabled(false);
            Matrix userMatrix = new Matrix(iv.getImageMatrix()); int boxW = previewBox.getWidth() > 0 ? previewBox.getWidth() : 650; int boxH = previewBox.getHeight() > 0 ? previewBox.getHeight() : 650;
            new Thread(() -> {
                try {
                    File dir = new File(getFilesDir(), "stickers/" + currentPackId); if (!dir.exists() && !dir.mkdirs()) return;
                    File[] existingFiles = dir.listFiles((d, name) -> name.endsWith(".webp") && !name.contains("_tmp") && !name.contains("_thumb"));
                    int nextId = 1; if (existingFiles != null) { for (File f : existingFiles) { try { int id = Integer.parseInt(f.getName().replace(".webp", "")); if (id >= nextId) nextId = id + 1; } catch (NumberFormatException ignored) {} } }
                    File tmpFile = new File(dir, nextId + "_tmp.webp"); File stickerFile = new File(dir, nextId + ".webp"); File thumbFile = new File(dir, nextId + "_thumb.png");
                    Bitmap.CompressFormat webpFormat = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;

                    Bitmap orig = decodeSampledBitmapFromUri(mediaUri, 1024, 1024); if (orig == null) throw new Exception();
                    Bitmap cropped = applyMatrixToSticker(orig, userMatrix, boxW, boxH); orig.recycle();
                    FileOutputStream outSticker = new FileOutputStream(tmpFile); cropped.compress(webpFormat, 75, outSticker); outSticker.close();
                    if (tmpFile.exists() && tmpFile.length() > 100) { tmpFile.renameTo(stickerFile); } else { throw new Exception("Write failed"); }
                    FileOutputStream outThumb = new FileOutputStream(thumbFile); Bitmap thumb = Bitmap.createScaledBitmap(cropped, 256, 256, true); thumb.compress(Bitmap.CompressFormat.PNG, 100, outThumb); outThumb.close(); thumb.recycle();
                    cropped.recycle();
                    ensureValidTrayIcon(dir);
                    runOnUiThread(() -> { dialog.dismiss(); loadExistingStickers(); Toast.makeText(this, "Sticker Added!", Toast.LENGTH_SHORT).show(); });
                } catch (Exception e) { runOnUiThread(() -> { dialog.dismiss(); Toast.makeText(this, "Failed to compile sticker", Toast.LENGTH_SHORT).show(); }); }
            }).start();
        });
        dialog.show();
    }

    private void setupTouchAndPresets(ImageView iv, Bitmap bmp, FrameLayout previewBox, int boxSize) {
        applyRatioChipMatrix(iv, bmp, previewBox, boxSize, 0);
        final Matrix matrix = new Matrix(iv.getImageMatrix()); final Matrix savedMatrix = new Matrix(); final PointF start = new PointF(); final PointF mid = new PointF(); final float[] oldDist = {1f}; final int[] mode = {0};
        iv.setOnTouchListener((v, event) -> {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN: savedMatrix.set(matrix); start.set(event.getX(), event.getY()); mode[0] = 1; break;
                case MotionEvent.ACTION_POINTER_DOWN: oldDist[0] = spacing(event); if (oldDist[0] > 10f) { savedMatrix.set(matrix); midPoint(mid, event); mode[0] = 2; } break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP: mode[0] = 0; break;
                case MotionEvent.ACTION_MOVE:
                    if (mode[0] == 1) { matrix.set(savedMatrix); matrix.postTranslate(event.getX() - start.x, event.getY() - start.y); }
                    else if (mode[0] == 2) { float newDist = spacing(event); if (newDist > 10f) { float scale = newDist / oldDist[0]; matrix.set(savedMatrix); matrix.postScale(scale, scale, mid.x, mid.y); } } break;
            }
            iv.setImageMatrix(matrix); return true;
        });
    }

    private void applyRatioChipMatrix(ImageView iv, Bitmap bmp, FrameLayout previewBox, int maxBoxSize, int chipIdx) {
        Matrix m = new Matrix(); int bw = bmp.getWidth(), bh = bmp.getHeight(); float ratio = (float) bw / bh;
        int targetW = maxBoxSize, targetH = maxBoxSize;
        if (chipIdx == 0) { if (ratio >= 1f) targetH = Math.max(200, (int) (maxBoxSize / ratio)); else targetW = Math.max(200, (int) (maxBoxSize * ratio)); }
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) previewBox.getLayoutParams(); lp.width = targetW; lp.height = targetH; previewBox.setLayoutParams(lp); previewBox.requestLayout();
        if (chipIdx == 0) { float scale = (float) targetW / bw; m.postScale(scale, scale); }
        else { float scale = Math.max((float) maxBoxSize / bw, (float) maxBoxSize / bh); m.postScale(scale, scale); m.postTranslate((maxBoxSize - bw * scale) / 2f, (maxBoxSize - bh * scale) / 2f); }
        iv.setImageMatrix(m);
    }

    private Bitmap applyMatrixToSticker(Bitmap orig, Matrix userMatrix, int boxW, int boxH) {
        Bitmap finalBmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(finalBmp);
        int maxDim = Math.max(boxW, boxH); float scale = 512f / maxDim; float dx = (512f - (boxW * scale)) / 2f; float dy = (512f - (boxH * scale)) / 2f;
        canvas.translate(dx, dy); canvas.scale(scale, scale); canvas.drawBitmap(orig, userMatrix, new Paint(Paint.FILTER_BITMAP_FLAG)); return finalBmp;
    }

    private float spacing(MotionEvent e) { float x = e.getX(0) - e.getX(1); float y = e.getY(0) - e.getY(1); return (float) Math.sqrt(x * x + y * y); }
    private void midPoint(PointF p, MotionEvent e) { p.set((e.getX(0) + e.getX(1)) / 2, (e.getY(0) + e.getY(1)) / 2); }

    private void applyGlassDialogStyle(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            GradientDrawable bgGd = new GradientDrawable(); bgGd.setColor(cardColor); bgGd.setCornerRadius(60f); bgGd.setStroke(2, glassBorderColor);
            dialog.setOnShowListener(di -> {
                Window window = dialog.getWindow();
                if (window != null && window.getDecorView() != null) { window.getDecorView().setBackground(bgGd); setDialogTextColor(window.getDecorView(), textColor); }
                Button btnPos = dialog.getButton(AlertDialog.BUTTON_POSITIVE); if (btnPos != null) btnPos.setTextColor(Color.parseColor("#FF3B30"));
                Button btnNeg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE); if (btnNeg != null) btnNeg.setTextColor(textColor);
            });
        }
    }

    private void setDialogTextColor(View view, int color) {
        if (view instanceof TextView && !(view instanceof Button) && !(view instanceof EditText)) ((TextView) view).setTextColor(color);
        else if (view instanceof ViewGroup) { ViewGroup vg = (ViewGroup) view; for (int i = 0; i < vg.getChildCount(); i++) setDialogTextColor(vg.getChildAt(i), color); }
    }
}