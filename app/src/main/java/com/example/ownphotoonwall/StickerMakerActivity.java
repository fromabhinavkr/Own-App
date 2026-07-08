package com.example.ownphotoonwall;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;

@SuppressWarnings("FieldCanBeLocal")
@SuppressLint({"SetTextI18n", "SpellCheckingInspection"})
public class StickerMakerActivity extends AppCompatActivity {

    private Button btnAddWA;
    private TextView tvSubtitle;
    private GridLayout stickerGrid;
    private int stickerCount = 0;

    private boolean isDarkTheme;
    private int textColor;
    private int cardColor;
    private SharedPreferences prefs;

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    processImageForWhatsApp(result.getData().getData());
                }
            }
    );

    private final ActivityResultLauncher<Intent> addStickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_CANCELED && result.getData() != null) {
                    String validationError = result.getData().getStringExtra("validation_error");
                    if (validationError != null) Toast.makeText(this, "WA Rejected: " + validationError, Toast.LENGTH_LONG).show();
                } else if (result.getResultCode() == Activity.RESULT_OK) {
                    Toast.makeText(this, "Successfully added to WhatsApp!", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_maker);

        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        View root = findViewById(R.id.stickerMakerRoot);
        EditText etTitle = findViewById(R.id.etStickerTitle);
        tvSubtitle = findViewById(R.id.tvStickerSubtitle);
        Button btnPick = findViewById(R.id.btnPickImage);
        btnAddWA = findViewById(R.id.btnAddWhatsApp);
        stickerGrid = findViewById(R.id.stickerGrid);

        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
        cardColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");
        ColorStateList btnColor = ColorStateList.valueOf(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA"));

        root.setBackgroundColor(bgColor);

        // Setup Pack Name Field
        String savedName = prefs.getString("pack_name", "My Custom Pack");
        etTitle.setText(savedName);
        etTitle.setTextColor(textColor);
        etTitle.setHintTextColor(isDarkTheme ? Color.parseColor("#666666") : Color.parseColor("#AAAAAA"));

        etTitle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                prefs.edit().putString("pack_name", s.toString().trim()).apply();
            }
        });

        tvSubtitle.setTextColor(isDarkTheme ? Color.parseColor("#8E8E93") : Color.parseColor("#888888"));
        btnPick.setBackgroundTintList(btnColor);
        btnPick.setTextColor(textColor);

        btnPick.setOnClickListener(v -> {
            if (stickerCount >= 30) {
                Toast.makeText(this, "Pack is full (30 max)!", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            pickImageLauncher.launch(intent);
        });

        btnAddWA.setOnClickListener(v -> {
            if (stickerCount >= 3) {
                String currentName = prefs.getString("pack_name", "My Custom Pack");
                if (currentName.isEmpty()) currentName = "My Custom Pack";

                int currentVersion = prefs.getInt("sticker_version", 3);
                prefs.edit().putInt("sticker_version", currentVersion + 1).apply();

                Intent intent = new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
                intent.putExtra("sticker_pack_id", "ownphoto_pack_1");
                intent.putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY);
                intent.putExtra("sticker_pack_name", currentName);
                try {
                    intent.setPackage("com.whatsapp");
                    addStickerLauncher.launch(intent);
                } catch (ActivityNotFoundException e) {
                    try {
                        intent.setPackage("com.whatsapp.w4b");
                        addStickerLauncher.launch(intent);
                    } catch (ActivityNotFoundException e2) {
                        Toast.makeText(this, "WhatsApp is not installed.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        loadExistingStickers();
    }

    private void loadExistingStickers() {
        File dir = new File(getFilesDir(), "stickers");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "Storage access error", Toast.LENGTH_SHORT).show();
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".webp"));
        stickerCount = (files != null) ? files.length : 0;

        updateUI(files);
    }

    private void updateUI(File[] files) {
        stickerGrid.removeAllViews();

        if (files != null) {
            Arrays.sort(files, (f1, f2) -> {
                try {
                    int n1 = Integer.parseInt(f1.getName().replace(".webp", ""));
                    int n2 = Integer.parseInt(f2.getName().replace(".webp", ""));
                    return Integer.compare(n1, n2);
                } catch (NumberFormatException e) {
                    return 0;
                }
            });

            for (File file : files) {
                addStickerToGrid(file, BitmapFactory.decodeFile(file.getAbsolutePath()));
            }
        }

        if (stickerCount < 3) {
            tvSubtitle.setText("Add " + (3 - stickerCount) + " more stickers.\nLong-press a sticker to delete.");
            tvSubtitle.setTextColor(isDarkTheme ? Color.parseColor("#FF9F0A") : Color.parseColor("#FF3B30"));
            btnAddWA.setBackgroundTintList(ColorStateList.valueOf(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#D1D1D6")));
            btnAddWA.setTextColor(isDarkTheme ? Color.parseColor("#8E8E93") : Color.parseColor("#888888"));
            btnAddWA.setEnabled(false);
        } else {
            tvSubtitle.setText(stickerCount + " Stickers Ready!\nLong-press a sticker to delete.");
            tvSubtitle.setTextColor(isDarkTheme ? Color.parseColor("#32D74B") : Color.parseColor("#34C759"));
            btnAddWA.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CD964")));
            btnAddWA.setTextColor(Color.WHITE);
            btnAddWA.setEnabled(true);
        }
    }

    private void addStickerToGrid(File file, Bitmap bitmap) {
        LinearLayout frame = new LinearLayout(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 250;
        params.height = 250;
        params.setMargins(16, 16, 16, 16);
        frame.setLayoutParams(params);
        frame.setBackgroundTintList(ColorStateList.valueOf(cardColor));
        frame.setBackgroundResource(R.drawable.bg_gallery_card);
        frame.setGravity(Gravity.CENTER);
        frame.setPadding(16, 16, 16, 16);

        // Modern Rounded Delete Dialog
        frame.setOnLongClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
            builder.setTitle("Delete Sticker")
                    .setMessage("Remove this sticker from the pack?")
                    .setPositiveButton("DELETE", (dialog, which) -> {
                        if (file.delete()) {
                            Toast.makeText(this, "Sticker deleted", Toast.LENGTH_SHORT).show();
                            loadExistingStickers(); // Refresh Grid instantly
                        }
                    })
                    .setNegativeButton("CANCEL", null);

            AlertDialog dialog = builder.create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

                GradientDrawable gd = new GradientDrawable();
                gd.setColor(cardColor);
                gd.setCornerRadius(60f);

                dialog.setOnShowListener(di -> {
                    Window window = dialog.getWindow();
                    if (window != null && window.getDecorView() != null) {
                        window.getDecorView().setBackground(gd);
                        int targetTextColor = isDarkTheme ? Color.WHITE : Color.BLACK;
                        setDialogTextColor(window.getDecorView(), targetTextColor);
                    }
                });
            }
            dialog.show();
            return true;
        });

        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bitmap);
        iv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);

        frame.addView(iv);
        stickerGrid.addView(frame);
    }

    private void processImageForWhatsApp(Uri uri) {
        Toast.makeText(this, "Processing...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                Bitmap original = BitmapFactory.decodeStream(is);
                if (original == null) return;

                Bitmap stickerBitmap = Bitmap.createScaledBitmap(original, 512, 512, true);
                File dir = new File(getFilesDir(), "stickers");

                if (!dir.exists() && !dir.mkdirs()) {
                    runOnUiThread(() -> Toast.makeText(StickerMakerActivity.this, "Storage access error", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Get exactly how many files currently exist to determine if tray is needed
                File[] existingFiles = dir.listFiles((d, name) -> name.endsWith(".webp"));
                int currentFileCount = (existingFiles != null) ? existingFiles.length : 0;

                if (currentFileCount == 0) {
                    Bitmap trayBitmap = Bitmap.createScaledBitmap(original, 96, 96, true);
                    FileOutputStream outTray = new FileOutputStream(new File(dir, "tray.png"));
                    trayBitmap.compress(Bitmap.CompressFormat.PNG, 100, outTray);
                    outTray.close();
                }

                // Determine next safe ID to prevent overwriting
                int nextId = 1;
                if (existingFiles != null) {
                    for (File f : existingFiles) {
                        try {
                            int id = Integer.parseInt(f.getName().replace(".webp", ""));
                            if (id >= nextId) nextId = id + 1;
                        } catch (NumberFormatException ignored) {}
                    }
                }

                Bitmap.CompressFormat webpFormat = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ?
                        Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;

                File stickerFile = new File(dir, nextId + ".webp");
                FileOutputStream outSticker = new FileOutputStream(stickerFile);
                stickerBitmap.compress(webpFormat, 80, outSticker);
                outSticker.close();

                runOnUiThread(this::loadExistingStickers);

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(StickerMakerActivity.this, "Error processing", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // Helper method to recursively ensure text color contrasts with the background theme
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
}