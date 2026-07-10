package com.example.ownphotoonwall;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

@SuppressLint("SetTextI18n")
public class PdfGalleryActivity extends AppCompatActivity {

    private RecyclerView rv;
    private PdfAdapter adapter;

    private static class PdfFile {
        String name;
        String size;
        Uri uri;
        PdfFile(String n, String s, Uri u) { name = n; size = s; uri = u; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_gallery);

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
        int panelColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        int primaryTextColor = isDarkTheme ? Color.WHITE : Color.parseColor("#1C1C1E");
        int secondaryTextColor = isDarkTheme ? Color.parseColor("#BBBBBB") : Color.parseColor("#555555");

        findViewById(R.id.galleryRoot).setBackgroundColor(bgColor);
        ((TextView) findViewById(R.id.tvGalleryTitle)).setTextColor(primaryTextColor);

        rv = findViewById(R.id.rvPdfGallery);
        rv.setLayoutManager(new LinearLayoutManager(this));

        List<PdfFile> pdfList = loadPdfs();
        adapter = new PdfAdapter(pdfList, panelColor, primaryTextColor, secondaryTextColor);
        rv.setAdapter(adapter);
    }

    private LinearLayout createItemLayout(ViewGroup parent, int panelColor, int primaryTextColor, int secondaryTextColor) {
        LinearLayout layout = new LinearLayout(parent.getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);

        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 24);
        layout.setLayoutParams(lp);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(panelColor);
        gd.setCornerRadius(40f);
        layout.setBackground(gd);

        TextView tvName = new TextView(parent.getContext());
        tvName.setTextColor(primaryTextColor);
        tvName.setTextSize(16f);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvSize = new TextView(parent.getContext());
        tvSize.setTextColor(secondaryTextColor);
        tvSize.setTextSize(14f);
        tvSize.setPadding(0, 8, 0, 24);

        LinearLayout actionRow = new LinearLayout(parent.getContext());
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnView = new Button(parent.getContext());
        btnView.setText("View");
        btnView.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4A90E2")));
        btnView.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnView.setLayoutParams(btnLp);

        Button btnShare = new Button(parent.getContext());
        btnShare.setText("Share");
        btnShare.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#34C759")));
        btnShare.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams btnLp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnLp2.setMargins(16, 0, 0, 0);
        btnShare.setLayoutParams(btnLp2);

        Button btnDelete = new Button(parent.getContext());
        btnDelete.setText("Delete");
        btnDelete.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF3B30")));
        btnDelete.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams btnLp3 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnLp3.setMargins(16, 0, 0, 0);
        btnDelete.setLayoutParams(btnLp3);

        actionRow.addView(btnView);
        actionRow.addView(btnShare);
        actionRow.addView(btnDelete);

        layout.addView(tvName);
        layout.addView(tvSize);
        layout.addView(actionRow);

        return layout;
    }

    private List<PdfFile> loadPdfs() {
        List<PdfFile> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String[] projection = {MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.SIZE};
            String selection = MediaStore.Files.FileColumns.RELATIVE_PATH + " LIKE ? AND " + MediaStore.Files.FileColumns.MIME_TYPE + "=?";
            String[] selectionArgs = new String[]{"%OWN's PDF Gallery%", "application/pdf"};

            Cursor cursor = getContentResolver().query(MediaStore.Files.getContentUri("external"), projection, selection, selectionArgs, MediaStore.Files.FileColumns.DATE_ADDED + " DESC");
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
                int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
                int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String name = cursor.getString(nameCol);
                    long sizeBytes = cursor.getLong(sizeCol);
                    String sizeStr = String.format(Locale.US, "%.2f MB", sizeBytes / (1024f * 1024f));
                    Uri uri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), "" + id);
                    list.add(new PdfFile(name, sizeStr, uri));
                }
                cursor.close();
            }
        }
        return list;
    }

    private class PdfAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<PdfFile> pdfList;
        private final int panelColor, primaryTextColor, secondaryTextColor;

        PdfAdapter(List<PdfFile> list, int panel, int primary, int secondary) {
            this.pdfList = list;
            this.panelColor = panel;
            this.primaryTextColor = primary;
            this.secondaryTextColor = secondary;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(createItemLayout(parent, panelColor, primaryTextColor, secondaryTextColor)) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            PdfFile file = pdfList.get(position);
            LinearLayout root = (LinearLayout) holder.itemView;
            ((TextView) root.getChildAt(0)).setText(file.name);
            ((TextView) root.getChildAt(1)).setText(file.size);

            LinearLayout actions = (LinearLayout) root.getChildAt(2);
            Button btnView = (Button) actions.getChildAt(0);
            Button btnShare = (Button) actions.getChildAt(1);
            Button btnDelete = (Button) actions.getChildAt(2);

            btnView.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(file.uri, "application/pdf");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, "Open PDF"));
            });

            btnShare.setOnClickListener(v -> {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/pdf");
                shareIntent.putExtra(Intent.EXTRA_STREAM, file.uri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Share PDF"));
            });

            btnDelete.setOnClickListener(v -> {
                // Custom Layout for dialog to ensure visibility in both themes
                LinearLayout dialogLayout = new LinearLayout(PdfGalleryActivity.this);
                dialogLayout.setOrientation(LinearLayout.VERTICAL);
                dialogLayout.setPadding(60, 60, 60, 60);

                TextView message = new TextView(PdfGalleryActivity.this);
                message.setText("Are you sure you want to permanently delete this file?");
                message.setTextColor(primaryTextColor);
                message.setTextSize(16f);
                dialogLayout.addView(message);

                AlertDialog.Builder builder = new AlertDialog.Builder(PdfGalleryActivity.this, R.style.ModernDialogStyle);
                builder.setTitle("Delete PDF");
                builder.setView(dialogLayout);

                builder.setPositiveButton("Delete", (dialog, which) -> {
                    try {
                        int deleted = getContentResolver().delete(file.uri, null, null);
                        if (deleted > 0) {
                            int currentPos = holder.getAdapterPosition();
                            if (currentPos != RecyclerView.NO_POSITION) {
                                pdfList.remove(currentPos);
                                notifyItemRemoved(currentPos);
                                notifyItemRangeChanged(currentPos, pdfList.size());
                                Toast.makeText(PdfGalleryActivity.this, "File deleted", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } catch (SecurityException e) {
                        Toast.makeText(PdfGalleryActivity.this, "Permission denied.", Toast.LENGTH_SHORT).show();
                    }
                });

                builder.setNegativeButton("Cancel", null);

                AlertDialog dialog = builder.create();
                dialog.setOnShowListener(d -> {
                    if (dialog.getWindow() != null) {
                        GradientDrawable gd = new GradientDrawable();
                        gd.setColor(panelColor);
                        gd.setCornerRadius(60f);
                        dialog.getWindow().getDecorView().setBackground(gd);

                        // Force Title color as well
                        int titleId = PdfGalleryActivity.this.getResources().getIdentifier("alertTitle", "id", "android");
                        TextView titleView = dialog.findViewById(titleId);
                        if (titleView != null) titleView.setTextColor(primaryTextColor);

                        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null)
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#FF3B30"));
                        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null)
                            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(primaryTextColor);
                    }
                });
                dialog.show();
            });
        }

        @Override public int getItemCount() { return pdfList.size(); }
    }
}