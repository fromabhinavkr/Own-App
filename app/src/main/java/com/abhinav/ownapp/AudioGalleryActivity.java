package com.abhinav.ownapp;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class AudioGalleryActivity extends AppCompatActivity {
    private View galleryRoot; private TextView btnBack, tvGalleryTitle; private LinearLayout listContainer; private boolean isDarkTheme;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_audio_gallery);
        galleryRoot = findViewById(R.id.galleryRoot); btnBack = findViewById(R.id.btnBack);
        tvGalleryTitle = findViewById(R.id.tvGalleryTitle); listContainer = findViewById(R.id.listContainer);
        ViewCompat.setOnApplyWindowInsetsListener(galleryRoot, (v, insets) -> { Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars()); v.setPadding(sys.left, sys.top, sys.right, sys.bottom); return WindowInsetsCompat.CONSUMED; });
        isDarkTheme = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE).getBoolean(SnakeWidget.PREF_IS_DARK, true);
        applyTheme(); loadExportedFiles();
        btnBack.setOnClickListener(v -> finish());
    }

    private GradientDrawable createPill(int color) { GradientDrawable gd = new GradientDrawable(); gd.setColor(color); gd.setCornerRadius(100f); return gd; }

    private void applyTheme() {
        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.WHITE; int textColor = isDarkTheme ? Color.WHITE : Color.BLACK;
        getWindow().setStatusBarColor(bgColor); galleryRoot.setBackgroundColor(bgColor); tvGalleryTitle.setTextColor(textColor);
        btnBack.setBackground(createPill(Color.parseColor("#FF4444"))); btnBack.setTextColor(Color.WHITE);
    }

    private void loadExportedFiles() {
        listContainer.removeAllViews(); File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "OWN's Audio Gallery");
        File[] files = dir.listFiles();
        if (files != null && files.length > 0) { for (File f : files) addFileToUI(f, "Exported Track"); } else showEmptyMessage("No exported audio files found.");
    }

    @SuppressWarnings("SameParameterValue")
    private void addFileToUI(File file, String subtitle) {
        int cardBg = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.parseColor("#F2F2F7"), textColor = isDarkTheme ? Color.WHITE : Color.BLACK;
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL); card.setPadding(40, 40, 40, 40); card.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable gd = new GradientDrawable(); gd.setColor(cardBg); gd.setCornerRadius(40f); card.setBackground(gd);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, 0, 0, 24); card.setLayoutParams(params);

        LinearLayout textLayout = new LinearLayout(this); textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f); textLayout.setLayoutParams(textParams);
        TextView title = new TextView(this); title.setText(file.getName()); title.setTextColor(textColor); title.setTextSize(16f); title.setTypeface(null, Typeface.BOLD);
        TextView sub = new TextView(this); sub.setText(subtitle); sub.setTextColor(Color.GRAY); sub.setTextSize(12f);
        textLayout.addView(title); textLayout.addView(sub);

        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(-2, -2); iconParams.setMarginStart(30);
        ImageView btnRename = new ImageView(this); btnRename.setImageResource(android.R.drawable.ic_menu_edit); btnRename.setColorFilter(Color.parseColor("#5A9AF4")); btnRename.setPadding(20, 20, 20, 20); btnRename.setLayoutParams(iconParams); btnRename.setOnClickListener(v -> showRenameDialog(file));
        ImageView btnShare = new ImageView(this); btnShare.setImageResource(android.R.drawable.ic_menu_share); btnShare.setColorFilter(textColor); btnShare.setPadding(20, 20, 20, 20); btnShare.setLayoutParams(iconParams); btnShare.setOnClickListener(v -> shareAudio(file));
        ImageView btnDelete = new ImageView(this); btnDelete.setImageResource(android.R.drawable.ic_menu_delete); btnDelete.setColorFilter(Color.parseColor("#FF4444")); btnDelete.setPadding(20, 20, 20, 20); btnDelete.setLayoutParams(iconParams); btnDelete.setOnClickListener(v -> { if (file.exists() && file.delete()) { Toast.makeText(this, "Track Deleted", Toast.LENGTH_SHORT).show(); loadExportedFiles(); } else Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show(); });

        card.addView(textLayout); card.addView(btnRename); card.addView(btnShare); card.addView(btnDelete); listContainer.addView(card);
    }

    private void showRenameDialog(File file) {
        Dialog d = new Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE); if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(80, 80, 80, 80);
        GradientDrawable rootBg = new GradientDrawable(); rootBg.setColor(Color.parseColor(isDarkTheme ? "#CC1C1C1E" : "#CCF2F2F7")); rootBg.setCornerRadius(100f); root.setBackground(rootBg);
        TextView title = new TextView(this); title.setText("Rename Track"); title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); title.setTextSize(20f); title.setTypeface(null, Typeface.BOLD); title.setPadding(0, 0, 0, 60); title.setGravity(Gravity.CENTER); root.addView(title);
        EditText input = new EditText(this); String oldName = file.getName(); int dotIdx = oldName.lastIndexOf('.'); String ext = dotIdx > 0 ? oldName.substring(dotIdx) : ""; input.setText(dotIdx > 0 ? oldName.substring(0, dotIdx) : oldName); input.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); input.setPadding(40, 40, 40, 40); input.setBackground(createPill(isDarkTheme ? Color.parseColor("#332D2B") : Color.WHITE)); root.addView(input);
        TextView btnOk = new TextView(this); btnOk.setText("Save"); btnOk.setTextColor(Color.WHITE); btnOk.setTextSize(16f); btnOk.setTypeface(null, Typeface.BOLD); btnOk.setPadding(40, 40, 40, 40); btnOk.setGravity(Gravity.CENTER); btnOk.setBackground(createPill(Color.parseColor("#5A9AF4"))); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 60, 0, 0); btnOk.setLayoutParams(lp);
        btnOk.setOnClickListener(v -> { String n = input.getText().toString().trim(); if (!n.isEmpty()) { File newFile = new File(file.getParent(), n + ext); if (file.renameTo(newFile)) { android.media.MediaScannerConnection.scanFile(this, new String[]{newFile.getAbsolutePath(), file.getAbsolutePath()}, null, null); Toast.makeText(this, "Renamed!", Toast.LENGTH_SHORT).show(); loadExportedFiles(); d.dismiss(); } else Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show(); } }); root.addView(btnOk);
        d.setContentView(root, new ViewGroup.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels * 0.85), -2)); d.show();
    }

    private void shareAudio(File file) {
        Toast.makeText(this, "Preparing to share...", Toast.LENGTH_SHORT).show();
        android.media.MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, new String[]{"audio/*"}, (path, uri) -> runOnUiThread(() -> {
            Intent intent = new Intent(Intent.ACTION_SEND); intent.setType("audio/*");
            if (uri != null) { intent.putExtra(Intent.EXTRA_STREAM, uri); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            else { StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder(); StrictMode.setVmPolicy(builder.build()); intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file)); }
            startActivity(Intent.createChooser(intent, "Share Audio"));
        }));
    }

    private void showEmptyMessage(String msg) {
        TextView empty = new TextView(this); empty.setText(msg); empty.setTextColor(Color.GRAY); empty.setTextSize(16f); empty.setGravity(Gravity.CENTER); empty.setPadding(0, 100, 0, 0); listContainer.addView(empty);
    }
}