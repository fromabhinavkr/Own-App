package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings({"all"})
@SuppressLint("SetTextI18n")
public class PdfGalleryActivity extends AppCompatActivity {

    private RecyclerView rv; private PdfAdapter adapter;

    private static class PdfFile {
        String name, size; Uri uri;
        PdfFile(String n, String s, Uri u) { name = n; size = s; uri = u; }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_pdf_gallery);

        // --- 3-STATE THEME SYNC LOGIC ---
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        int themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = oldDark ? 1 : 0;
        }

        int bgColor, panelColor, primaryTextColor, secondaryTextColor;

        if (themeState == 0) { // Light Mode
            bgColor = Color.parseColor("#F2F2F7");
            panelColor = Color.WHITE;
            primaryTextColor = Color.parseColor("#1C1C1E");
            secondaryTextColor = Color.parseColor("#555555");
        } else if (themeState == 1) { // Standard Dark Mode
            bgColor = Color.parseColor("#1C1C1E");
            panelColor = Color.parseColor("#2C2C2E");
            primaryTextColor = Color.WHITE;
            secondaryTextColor = Color.parseColor("#BBBBBB");
        } else { // Star Mode (AMOLED Pure Black)
            bgColor = Color.parseColor("#000000"); // Pure AMOLED Black
            panelColor = Color.parseColor("#1C1C1E"); // Elevated dark gray for cards
            primaryTextColor = Color.WHITE;
            secondaryTextColor = Color.parseColor("#BBBBBB");
        }

        findViewById(R.id.galleryRoot).setBackgroundColor(bgColor);
        ((TextView) findViewById(R.id.tvGalleryTitle)).setTextColor(primaryTextColor);
        getWindow().setStatusBarColor(bgColor);

        rv = findViewById(R.id.rvPdfGallery); rv.setLayoutManager(new LinearLayoutManager(this));
        List<PdfFile> pdfList = loadPdfs();
        adapter = new PdfAdapter(pdfList, panelColor, primaryTextColor, secondaryTextColor);
        rv.setAdapter(adapter);
    }

    private TextView createPillBtn(android.content.Context ctx, String text, String color, float density, int ml, int mr) {
        TextView tv = new TextView(ctx); tv.setText(text); tv.setTextColor(Color.WHITE); tv.setTextSize(13f); tv.setTypeface(null, Typeface.BOLD); tv.setGravity(android.view.Gravity.CENTER); tv.setPadding(0, (int)(12*density), 0, (int)(12*density));
        GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.parseColor(color)); gd.setCornerRadius(100f); tv.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins((int)(ml*density), 0, (int)(mr*density), 0); tv.setLayoutParams(lp); return tv;
    }

    private LinearLayout createItemLayout(ViewGroup parent, int panelColor, int primaryTextColor, int secondaryTextColor) {
        float density = parent.getContext().getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(parent.getContext()); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, (int)(12*density)); layout.setLayoutParams(lp);
        GradientDrawable gd = new GradientDrawable(); gd.setColor(panelColor); gd.setCornerRadius(20f * density); layout.setBackground(gd);
        TextView tvName = new TextView(parent.getContext()); tvName.setTextColor(primaryTextColor); tvName.setTextSize(16f); tvName.setTypeface(null, Typeface.BOLD);
        TextView tvSize = new TextView(parent.getContext()); tvSize.setTextColor(secondaryTextColor); tvSize.setTextSize(14f); tvSize.setPadding(0, (int)(4*density), 0, (int)(16*density));
        LinearLayout actionRow = new LinearLayout(parent.getContext()); actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.addView(createPillBtn(parent.getContext(), "VIEW", "#5A9AF4", density, 0, 4));
        actionRow.addView(createPillBtn(parent.getContext(), "SHARE", "#34C759", density, 4, 4));
        actionRow.addView(createPillBtn(parent.getContext(), "RENAME", "#FF9500", density, 4, 4));
        actionRow.addView(createPillBtn(parent.getContext(), "DELETE", "#FF4444", density, 4, 0));
        layout.addView(tvName); layout.addView(tvSize); layout.addView(actionRow); return layout;
    }

    private List<PdfFile> loadPdfs() {
        List<PdfFile> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String[] projection = {MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.SIZE};
            String selection = MediaStore.Files.FileColumns.RELATIVE_PATH + " LIKE ? AND " + MediaStore.Files.FileColumns.MIME_TYPE + "=?";
            String[] selectionArgs = new String[]{"%OWN's PDF Gallery%", "application/pdf"};
            Cursor cursor = getContentResolver().query(MediaStore.Files.getContentUri("external"), projection, selection, selectionArgs, MediaStore.Files.FileColumns.DATE_ADDED + " DESC");
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID), nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME), sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol), sizeBytes = cursor.getLong(sizeCol); String name = cursor.getString(nameCol), sizeStr = String.format(Locale.US, "%.2f MB", sizeBytes / (1024f * 1024f));
                    Uri uri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), "" + id); list.add(new PdfFile(name, sizeStr, uri));
                } cursor.close();
            }
        } return list;
    }

    private class PdfAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<PdfFile> pdfList; private final int panelColor, primaryTextColor, secondaryTextColor;
        PdfAdapter(List<PdfFile> list, int panel, int primary, int secondary) { this.pdfList = list; this.panelColor = panel; this.primaryTextColor = primary; this.secondaryTextColor = secondary; }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { return new RecyclerView.ViewHolder(createItemLayout(parent, panelColor, primaryTextColor, secondaryTextColor)) {}; }
        @Override public int getItemCount() { return pdfList.size(); }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            PdfFile file = pdfList.get(position); LinearLayout root = (LinearLayout) holder.itemView; ((TextView) root.getChildAt(0)).setText(file.name); ((TextView) root.getChildAt(1)).setText(file.size);
            LinearLayout actions = (LinearLayout) root.getChildAt(2); TextView btnView = (TextView) actions.getChildAt(0), btnShare = (TextView) actions.getChildAt(1), btnRename = (TextView) actions.getChildAt(2), btnDelete = (TextView) actions.getChildAt(3);

            btnView.setOnClickListener(v -> { Intent intent = new Intent(Intent.ACTION_VIEW); intent.setDataAndType(file.uri, "application/pdf"); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(intent, "Open PDF")); });
            btnShare.setOnClickListener(v -> { Intent shareIntent = new Intent(Intent.ACTION_SEND); shareIntent.setType("application/pdf"); shareIntent.putExtra(Intent.EXTRA_STREAM, file.uri); shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(shareIntent, "Share PDF")); });

            btnRename.setOnClickListener(v -> {
                LinearLayout dialogLayout = new LinearLayout(PdfGalleryActivity.this); dialogLayout.setOrientation(LinearLayout.VERTICAL); dialogLayout.setPadding(60, 60, 60, 60);
                EditText et = new EditText(PdfGalleryActivity.this); et.setText(file.name.replace(".pdf", "")); et.setTextColor(primaryTextColor); et.setHintTextColor(secondaryTextColor);

                // FIX: Adapt the EditText input box background dynamically for all 3 themes
                int etBgColor = (panelColor == Color.WHITE) ? Color.parseColor("#E5E5EA") :
                        ((panelColor == Color.parseColor("#1C1C1E")) ? Color.parseColor("#2C2C2E") : Color.parseColor("#1C1C1E"));

                GradientDrawable etBg = new GradientDrawable(); etBg.setColor(etBgColor); etBg.setCornerRadius(20f); et.setBackground(etBg); et.setPadding(40, 40, 40, 40); dialogLayout.addView(et);
                AlertDialog.Builder builder = new AlertDialog.Builder(PdfGalleryActivity.this, R.style.ModernDialogStyle); builder.setTitle("Rename PDF").setView(dialogLayout);
                builder.setPositiveButton("Rename", (dialog, which) -> {
                    String newName = et.getText().toString().trim(); if (newName.isEmpty()) return; if (!newName.toLowerCase().endsWith(".pdf")) newName += ".pdf";
                    try {
                        android.content.ContentValues values = new android.content.ContentValues(); values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, newName);
                        int updated = getContentResolver().update(file.uri, values, null, null);
                        if (updated > 0) { file.name = newName; notifyItemChanged(holder.getAdapterPosition()); Toast.makeText(PdfGalleryActivity.this, "Renamed successfully!", Toast.LENGTH_SHORT).show(); }
                        else { Toast.makeText(PdfGalleryActivity.this, "Failed to rename.", Toast.LENGTH_SHORT).show(); }
                    } catch (Exception e) { Toast.makeText(PdfGalleryActivity.this, "Rename failed: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
                }); builder.setNegativeButton("Cancel", null);
                AlertDialog dialog = builder.create(); dialog.setOnShowListener(d -> {
                    if (dialog.getWindow() != null) { GradientDrawable gd = new GradientDrawable(); gd.setColor(panelColor); gd.setCornerRadius(60f); dialog.getWindow().getDecorView().setBackground(gd);
                        int titleId = PdfGalleryActivity.this.getResources().getIdentifier("alertTitle", "id", "android"); TextView titleView = dialog.findViewById(titleId); if (titleView != null) titleView.setTextColor(primaryTextColor);
                        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#FF9500"));
                        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(primaryTextColor);
                    }
                }); dialog.show();
            });

            btnDelete.setOnClickListener(v -> {
                LinearLayout dialogLayout = new LinearLayout(PdfGalleryActivity.this); dialogLayout.setOrientation(LinearLayout.VERTICAL); dialogLayout.setPadding(60, 60, 60, 60);
                TextView message = new TextView(PdfGalleryActivity.this); message.setText("Are you sure you want to permanently delete this file?"); message.setTextColor(primaryTextColor); message.setTextSize(16f); dialogLayout.addView(message);
                AlertDialog.Builder builder = new AlertDialog.Builder(PdfGalleryActivity.this, R.style.ModernDialogStyle); builder.setTitle("Delete PDF").setView(dialogLayout);
                builder.setPositiveButton("Delete", (dialog, which) -> {
                    try { int deleted = getContentResolver().delete(file.uri, null, null);
                        if (deleted > 0) { int currentPos = holder.getAdapterPosition(); if (currentPos != RecyclerView.NO_POSITION) { pdfList.remove(currentPos); notifyItemRemoved(currentPos); notifyItemRangeChanged(currentPos, pdfList.size()); Toast.makeText(PdfGalleryActivity.this, "File deleted", Toast.LENGTH_SHORT).show(); } }
                    } catch (Exception e) { Toast.makeText(PdfGalleryActivity.this, "Permission denied.", Toast.LENGTH_SHORT).show(); }
                }); builder.setNegativeButton("Cancel", null);
                AlertDialog dialog = builder.create(); dialog.setOnShowListener(d -> {
                    if (dialog.getWindow() != null) { GradientDrawable gd = new GradientDrawable(); gd.setColor(panelColor); gd.setCornerRadius(60f); dialog.getWindow().getDecorView().setBackground(gd);
                        int titleId = PdfGalleryActivity.this.getResources().getIdentifier("alertTitle", "id", "android"); TextView titleView = dialog.findViewById(titleId); if (titleView != null) titleView.setTextColor(primaryTextColor);
                        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#FF3B30"));
                        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(primaryTextColor);
                    }
                }); dialog.show();
            });
        }
    }
}