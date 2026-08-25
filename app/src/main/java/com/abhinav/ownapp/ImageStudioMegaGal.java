package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ImageStudioMegaGal extends AppCompatActivity {
    private boolean isDarkTheme;
    private int themeState; // --- 3-STATE THEME VARIABLE ---
    private RecyclerView rvMegaGallery;
    private MegaGalAdapter adapter;
    private Button btnTabGallery;
    private Button btnTabDrafts;
    private boolean isDraftMode = false;
    private final List<File> currentFiles = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_studio_mega_gal);

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);

        // --- 3-STATE THEME SYNC LOGIC ---
        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = oldDark ? 1 : 0;
        }
        isDarkTheme = (themeState != 0);

        View root = findViewById(R.id.megaGalRoot);
        TextView tvTitle = findViewById(R.id.tvMegaGalTitle);
        Button btnBack = findViewById(R.id.btnBack);
        btnTabGallery = findViewById(R.id.btnTabGallery);
        btnTabDrafts = findViewById(R.id.btnTabDrafts);
        rvMegaGallery = findViewById(R.id.rvMegaGallery);

        int bgColor, textColor;
        if (themeState == 0) { // Light Mode
            bgColor = Color.parseColor("#FFFFFF");
            textColor = Color.parseColor("#333333");
        } else if (themeState == 1) { // Standard Dark Mode
            bgColor = Color.parseColor("#0A0A0C");
            textColor = Color.WHITE;
        } else { // Star Mode (AMOLED Pure Black)
            bgColor = Color.parseColor("#000000"); // Pure AMOLED Black Canvas
            textColor = Color.WHITE;
        }

        root.setBackgroundColor(bgColor);
        tvTitle.setTextColor(textColor);

        GradientDrawable backBg = new GradientDrawable();
        backBg.setCornerRadius(100f);
        backBg.setColor(Color.parseColor("#FF3B30"));
        btnBack.setBackground(backBg);
        btnBack.setBackgroundTintList(null);
        btnBack.setOnClickListener(v -> finish());

        rvMegaGallery.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new MegaGalAdapter();
        rvMegaGallery.setAdapter(adapter);

        btnTabGallery.setOnClickListener(v -> switchTab(false));
        btnTabDrafts.setOnClickListener(v -> switchTab(true));
        switchTab(false);
    }

    private void switchTab(boolean toDrafts) {
        isDraftMode = toDrafts;
        int activeBg = Color.parseColor("#4A90E2");
        int inactiveBg = (themeState == 0) ? Color.parseColor("#D1D1D6") : Color.parseColor("#2C2C2E");
        int activeText = Color.WHITE;
        int inactiveText = (themeState == 0) ? Color.parseColor("#333333") : Color.WHITE;

        GradientDrawable galBg = new GradientDrawable();
        galBg.setCornerRadius(100f);
        galBg.setColor(!isDraftMode ? activeBg : inactiveBg);
        btnTabGallery.setBackgroundTintList(null);
        btnTabGallery.setBackground(galBg);
        btnTabGallery.setTextColor(!isDraftMode ? activeText : inactiveText);

        GradientDrawable draftBg = new GradientDrawable();
        draftBg.setCornerRadius(100f);
        draftBg.setColor(isDraftMode ? activeBg : inactiveBg);
        btnTabDrafts.setBackgroundTintList(null);
        btnTabDrafts.setBackground(draftBg);
        btnTabDrafts.setTextColor(isDraftMode ? activeText : inactiveText);

        loadFiles();
    }

    @SuppressLint("NotifyDataSetChanged") private void loadFiles() {
        currentFiles.clear();
        if (!isDraftMode) {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "OWN's Image studio");
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".webp"));
                if (files != null) {
                    Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                    Collections.addAll(currentFiles, files);
                }
            }
        } else {
            File draftDir = new File(getExternalFilesDir(null), "OwnDrafts");
            if (draftDir.exists() && draftDir.isDirectory()) {
                File[] folders = draftDir.listFiles(File::isDirectory);
                if (folders != null) {
                    Arrays.sort(folders, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                    Collections.addAll(currentFiles, folders);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }

    private void showDeleteDialog(File file, boolean isDraft) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ImageStudioMegaGal.this, R.style.ModernDialogStyle);
        LinearLayout mainLayout = new LinearLayout(ImageStudioMegaGal.this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(60, 60, 60, 60);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(themeState == 0 ? Color.parseColor("#FFFFFF") : Color.parseColor("#1C1C1E"));
        bg.setCornerRadius(60f);
        mainLayout.setBackground(bg);

        TextView title = new TextView(ImageStudioMegaGal.this);
        title.setText(isDraft ? "Delete Draft?" : "Delete Image?");
        title.setTextSize(20f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        int txtColor = (themeState == 0) ? Color.parseColor("#333333") : Color.WHITE;
        title.setTextColor(txtColor);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 40);
        mainLayout.addView(title);

        Button btnDelete = new Button(ImageStudioMegaGal.this);
        btnDelete.setText("Delete");
        btnDelete.setAllCaps(false);
        btnDelete.setTextColor(Color.WHITE);
        GradientDrawable delBg = new GradientDrawable();
        delBg.setColor(Color.parseColor("#FF3B30"));
        delBg.setCornerRadius(30f);
        btnDelete.setBackground(delBg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 130);
        lp.setMargins(0, 0, 0, 20);
        btnDelete.setLayoutParams(lp);
        mainLayout.addView(btnDelete);

        Button btnCancel = new Button(ImageStudioMegaGal.this);
        btnCancel.setText("Cancel");
        btnCancel.setAllCaps(false);
        btnCancel.setTextColor(txtColor);
        GradientDrawable canBg = new GradientDrawable();
        canBg.setColor((themeState == 0) ? Color.parseColor("#E5E5EA") : Color.parseColor("#2C2C2E"));
        canBg.setCornerRadius(30f);
        btnCancel.setBackground(canBg);
        LinearLayout.LayoutParams canLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 130);
        btnCancel.setLayoutParams(canLp);
        mainLayout.addView(btnCancel);

        builder.setView(mainLayout);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnDelete.setOnClickListener(delView -> {
            if (isDraft) {
                deleteRecursive(file);
                Toast.makeText(ImageStudioMegaGal.this, "Draft Deleted", Toast.LENGTH_SHORT).show();
            } else {
                file.delete();
                Toast.makeText(ImageStudioMegaGal.this, "Image Deleted", Toast.LENGTH_SHORT).show();
            }
            loadFiles();
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(canView -> dialog.dismiss());
        dialog.show();
    }

    private class MegaGalAdapter extends RecyclerView.Adapter<MegaGalAdapter.ViewHolder> {
        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            TextView draftLabel;
            ViewHolder(View v) {
                super(v);
                imageView = new ImageView(v.getContext());
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                draftLabel = new TextView(v.getContext());
                draftLabel.setBackgroundColor(Color.parseColor("#99000000"));
                draftLabel.setTextColor(Color.WHITE);
                draftLabel.setTextSize(12f);
                draftLabel.setGravity(Gravity.CENTER);
                draftLabel.setPadding(0, 10, 0, 10);

                FrameLayout container = new FrameLayout(v.getContext());
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 350);
                params.setMargins(8, 8, 8, 8);
                container.setLayoutParams(params);

                GradientDrawable border = new GradientDrawable();
                border.setColor((themeState == 0) ? Color.parseColor("#F2F2F7") : Color.parseColor("#1C1C1E"));
                border.setCornerRadius(24f);
                container.setBackground(border);
                container.setClipToOutline(true);

                container.addView(imageView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
                container.addView(draftLabel, labelParams);
                ((FrameLayout) v).addView(container);
            }
        }

        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout frame = new FrameLayout(parent.getContext());
            frame.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ViewHolder(frame);
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File f = currentFiles.get(position);
            if (!isDraftMode) {
                holder.draftLabel.setVisibility(View.GONE);
                try {
                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    opt.inSampleSize = 4;
                    Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opt);
                    holder.imageView.setImageBitmap(bmp);
                } catch(Exception e){}

                holder.itemView.setOnClickListener(v -> {
                    StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
                    StrictMode.setVmPolicy(builder.build());
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse("file://" + f.getAbsolutePath()), "image/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Intent chooser = Intent.createChooser(intent, "Open image with...");
                    try {
                        startActivity(chooser);
                    } catch(Exception e) {
                        Toast.makeText(ImageStudioMegaGal.this, "Cannot open image directly", Toast.LENGTH_SHORT).show();
                    }
                });

                holder.itemView.setOnLongClickListener(v -> {
                    showDeleteDialog(f, false);
                    return true;
                });
            } else {
                holder.draftLabel.setVisibility(View.VISIBLE);
                holder.draftLabel.setText(f.getName());
                File preview = new File(f, "base.png");
                if (preview.exists()) {
                    try {
                        BitmapFactory.Options opt = new BitmapFactory.Options();
                        opt.inSampleSize = 4;
                        Bitmap bmp = BitmapFactory.decodeFile(preview.getAbsolutePath(), opt);
                        holder.imageView.setImageBitmap(bmp);
                    } catch(Exception e){}
                } else {
                    holder.imageView.setBackgroundColor(Color.DKGRAY);
                }

                holder.itemView.setOnClickListener(v -> {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("draft_path", f.getAbsolutePath());
                    setResult(RESULT_OK, resultIntent);
                    finish();
                });

                holder.itemView.setOnLongClickListener(v -> {
                    showDeleteDialog(f, true);
                    return true;
                });
            }
        }

        @Override public int getItemCount() {
            return currentFiles.size();
        }
    }
}