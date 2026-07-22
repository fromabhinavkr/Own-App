package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

@SuppressWarnings("all")
@SuppressLint("SetTextI18n")
public class PdfStudioActivity extends AppCompatActivity {

    private ScanToPdfHelper scanToPdfHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_studio);

        // 1. Initialize Theme Matching
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
        int cardColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        int textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#1C1C1E");

        findViewById(R.id.pdfRoot).setBackgroundColor(bgColor);
        ((TextView) findViewById(R.id.tvPdfTitle)).setTextColor(textColor);

        // 2. Style all 5 Grid Cards with Rounded Backgrounds & Correct Text Colors
        int[] cardIds = {R.id.btnScanToPdf, R.id.btnMergePdf, R.id.btnSplitPdf, R.id.btnCompressPdf, R.id.btnPdfGallery};
        int[] labelIds = {R.id.tvScanLabel, R.id.tvMergeLabel, R.id.tvSplitLabel, R.id.tvCompressLabel, R.id.tvGalleryLabel};

        for (int i = 0; i < cardIds.length; i++) {
            LinearLayout card = findViewById(cardIds[i]);
            TextView label = findViewById(labelIds[i]);

            if (card != null) {
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(cardColor);
                gd.setCornerRadius(40f);
                card.setBackground(gd);
                card.setElevation(8f);
            }
            if (label != null) {
                label.setTextColor(textColor);
            }
        }

        // 3. Style and Wire Up the Circular "?" Help Button
        TextView btnScanHelp = findViewById(R.id.btnScanHelp);
        if (btnScanHelp != null) {
            GradientDrawable helpGd = new GradientDrawable();
            helpGd.setShape(GradientDrawable.OVAL);
            // Uses a clean professional accent blue for the circle
            helpGd.setColor(isDarkTheme ? Color.parseColor("#4A90E2") : Color.parseColor("#007AFF"));
            btnScanHelp.setBackground(helpGd);
            btnScanHelp.setTextColor(Color.WHITE);

            btnScanHelp.setOnClickListener(v -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(PdfStudioActivity.this, R.style.ModernDialogStyle);
                builder.setTitle("System Requirement");

                LinearLayout dialogLayout = new LinearLayout(PdfStudioActivity.this);
                dialogLayout.setOrientation(LinearLayout.VERTICAL);
                dialogLayout.setPadding(60, 40, 60, 40);

                TextView message = new TextView(PdfStudioActivity.this);
                message.setText("This scanning tool relies on Google Play Services. If the scanner fails to launch or operate correctly, please ensure that Google Play Services is installed and updated to the latest version on your device.");
                message.setTextColor(textColor);
                message.setTextSize(16f);
                message.setLineSpacing(0, 1.2f);
                dialogLayout.addView(message);

                builder.setView(dialogLayout);
                builder.setPositiveButton("Understood", null);

                AlertDialog dialog = builder.create();
                dialog.setOnShowListener(d -> {
                    if (dialog.getWindow() != null) {
                        GradientDrawable gd = new GradientDrawable();
                        gd.setColor(cardColor);
                        gd.setCornerRadius(60f);
                        dialog.getWindow().getDecorView().setBackground(gd);

                        int titleId = PdfStudioActivity.this.getResources().getIdentifier("alertTitle", "id", "android");
                        TextView titleView = dialog.findViewById(titleId);
                        if (titleView != null) titleView.setTextColor(textColor);

                        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null)
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#4A90E2"));
                    }
                });
                dialog.show();
            });
        }

        // 4. Initialize the Scan to PDF Engine
        scanToPdfHelper = new ScanToPdfHelper(this);
        scanToPdfHelper.setOnScanCompletedListener(new ScanToPdfHelper.OnScanCompletedListener() {
            @Override
            public void onPdfCreated(Uri pdfUri, int pageCount) {
                Toast.makeText(PdfStudioActivity.this, "Successfully Scanned " + pageCount + " Pages to Gallery!", Toast.LENGTH_LONG).show();

                // Automatically open PDF Gallery so user can view or share their new scan!
                Intent intent = new Intent(PdfStudioActivity.this, PdfGalleryActivity.class);
                startActivity(intent);
            }

            @Override
            public void onScanCancelled() {
                Toast.makeText(PdfStudioActivity.this, "Scanning cancelled", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onScanError(Exception e) {
                Toast.makeText(PdfStudioActivity.this, "Scanner Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // 5. Wire up Button Click Handlers
        findViewById(R.id.btnScanToPdf).setOnClickListener(v -> {
            // Launch scanner allowing up to 50 pages!
            scanToPdfHelper.startScanToPdf(50);
        });

        findViewById(R.id.btnPdfGallery).setOnClickListener(v -> {
            startActivity(new Intent(this, PdfGalleryActivity.class));
        });

        findViewById(R.id.btnMergePdf).setOnClickListener(v -> {
            Toast.makeText(this, "Select PDFs from Gallery to Merge", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, PdfGalleryActivity.class));
        });

        findViewById(R.id.btnSplitPdf).setOnClickListener(v -> {
            Toast.makeText(this, "Select a PDF from Gallery to Split", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, PdfGalleryActivity.class));
        });

        findViewById(R.id.btnCompressPdf).setOnClickListener(v -> {
            Toast.makeText(this, "Select a PDF from Gallery to Compress", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, PdfGalleryActivity.class));
        });
    }
}