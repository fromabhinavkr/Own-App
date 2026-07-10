package com.example.ownphotoonwall;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;
import com.tom_roush.pdfbox.multipdf.Splitter;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

@SuppressLint({"SetTextI18n", "HardcodedText", "ObsoleteSdkInt", "Typos"})
public class PdfStudioActivity extends AppCompatActivity {

    private int panelColor, textColor;
    private AlertDialog progressDialog;

    private int activeAction = 0; // 1=Merge, 2=Split, 3=Compress

    private final ActivityResultLauncher<Intent> pickMultiplePdfsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    List<Uri> uris = new ArrayList<>();
                    if (data.getClipData() != null) {
                        for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                            uris.add(data.getClipData().getItemAt(i).getUri());
                        }
                    } else if (data.getData() != null) {
                        uris.add(data.getData());
                    }

                    if (uris.size() < 2) {
                        Toast.makeText(this, "Please select at least 2 PDFs to merge.", Toast.LENGTH_LONG).show();
                    } else {
                        executePdfMerge(uris);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> pickSinglePdfLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (activeAction == 2) showSplitDialog(uri);
                    else if (activeAction == 3) showCompressDialog(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_studio);

        PDFBoxResourceLoader.init(getApplicationContext());

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
        panelColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");

        findViewById(R.id.pdfRoot).setBackgroundColor(bgColor);
        ((TextView) findViewById(R.id.tvPdfTitle)).setTextColor(textColor);
        ((TextView) findViewById(R.id.tvMergeLabel)).setTextColor(textColor);
        ((TextView) findViewById(R.id.tvSplitLabel)).setTextColor(textColor);
        ((TextView) findViewById(R.id.tvCompressLabel)).setTextColor(textColor);
        ((TextView) findViewById(R.id.tvGalleryLabel)).setTextColor(textColor);

        applyModernCardStyle(findViewById(R.id.btnMergePdf));
        applyModernCardStyle(findViewById(R.id.btnSplitPdf));
        applyModernCardStyle(findViewById(R.id.btnCompressPdf));
        applyModernCardStyle(findViewById(R.id.btnPdfGallery));

        findViewById(R.id.btnMergePdf).setOnClickListener(v -> {
            activeAction = 1;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            pickMultiplePdfsLauncher.launch(intent);
        });

        findViewById(R.id.btnSplitPdf).setOnClickListener(v -> {
            activeAction = 2;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/pdf");
            pickSinglePdfLauncher.launch(intent);
        });

        findViewById(R.id.btnCompressPdf).setOnClickListener(v -> {
            activeAction = 3;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/pdf");
            pickSinglePdfLauncher.launch(intent);
        });

        findViewById(R.id.btnPdfGallery).setOnClickListener(v -> startActivity(new Intent(this, PdfGalleryActivity.class)));
    }

    private void applyModernCardStyle(View view) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(panelColor);
        gd.setCornerRadius(60f);
        view.setBackground(gd);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setElevation(8f);
        }
    }

    private void showProgress(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(60, 60, 60, 60);
        layout.setGravity(Gravity.CENTER_VERTICAL);

        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(Color.parseColor("#4A90E2")));
        layout.addView(progressBar);

        TextView tvMessage = new TextView(this);
        tvMessage.setText(message);
        tvMessage.setTextColor(textColor);
        tvMessage.setTextSize(16f);
        tvMessage.setPadding(40, 0, 0, 0);
        layout.addView(tvMessage);

        builder.setView(layout);
        builder.setCancelable(false);
        progressDialog = builder.create();

        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        progressDialog.show();

        if (progressDialog.getWindow() != null) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(panelColor);
            gd.setCornerRadius(60f);
            progressDialog.getWindow().getDecorView().setBackground(gd);
        }
    }

    private void hideProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private Uri createOutputUri(String prefix) {
        String fileName = prefix + "_" + System.currentTimeMillis() + ".pdf";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/OWN's PDF Gallery");
        }
        return getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
    }

    // ================== PDF MERGE ==================
    private void executePdfMerge(List<Uri> uris) {
        showProgress("Merging PDFs smoothly... No quality loss!");
        new Thread(() -> {
            try {
                Uri outUri = createOutputUri("Merged");
                if (outUri == null) throw new Exception("Failed to create output file.");

                OutputStream os = getContentResolver().openOutputStream(outUri);
                PDFMergerUtility merger = new PDFMergerUtility();
                merger.setDestinationStream(os);

                for (Uri uri : uris) {
                    InputStream is = getContentResolver().openInputStream(uri);
                    merger.addSource(is);
                }

                merger.mergeDocuments(null);
                if (os != null) os.close();

                runOnUiThread(() -> {
                    hideProgress();
                    Toast.makeText(this, "Merged successfully to OWN's PDF Gallery", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideProgress();
                    Toast.makeText(this, "Merge Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ================== PDF SPLIT ==================
    private void showSplitDialog(Uri uri) {
        showProgress("Analyzing PDF pages...");
        new Thread(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                PDDocument doc = PDDocument.load(is);
                int totalPages = doc.getNumberOfPages();
                doc.close();

                runOnUiThread(() -> {
                    hideProgress();
                    buildSplitUi(uri, totalPages);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideProgress();
                    Toast.makeText(this, "Cannot read PDF properties.", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void buildSplitUi(Uri uri, int totalPages) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);

        TextView title = new TextView(this);
        title.setText("Split PDF (Total Pages: " + totalPages + ")");
        title.setTextSize(20f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(textColor);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 32);
        layout.addView(title);

        EditText etStart = new EditText(this);
        etStart.setHint("Start Page (1 to " + totalPages + ")");
        etStart.setInputType(InputType.TYPE_CLASS_NUMBER);
        etStart.setTextColor(textColor);
        etStart.setHintTextColor(Color.GRAY);
        layout.addView(etStart);

        EditText etEnd = new EditText(this);
        etEnd.setHint("End Page (1 to " + totalPages + ")");
        etEnd.setInputType(InputType.TYPE_CLASS_NUMBER);
        etEnd.setTextColor(textColor);
        etEnd.setHintTextColor(Color.GRAY);
        etEnd.setPadding(0, 32, 0, 48);
        layout.addView(etEnd);

        Button btnApply = new Button(this);
        btnApply.setText("Split & Save");
        btnApply.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4A90E2")));
        btnApply.setTextColor(Color.WHITE);
        layout.addView(btnApply);

        builder.setView(layout);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.setOnShowListener(d -> {
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(panelColor);
                gd.setCornerRadius(60f);
                dialog.getWindow().getDecorView().setBackground(gd);
            });
        }

        btnApply.setOnClickListener(v -> {
            try {
                int start = Integer.parseInt(etStart.getText().toString());
                int end = Integer.parseInt(etEnd.getText().toString());
                if (start < 1 || end > totalPages || start > end) {
                    Toast.makeText(this, "Invalid page range.", Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                executePdfSplit(uri, start, end);
            } catch (Exception e) {
                Toast.makeText(this, "Enter valid numbers.", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void executePdfSplit(Uri uri, int start, int end) {
        showProgress("Extracting Pages " + start + " to " + end + "...");
        new Thread(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                PDDocument doc = PDDocument.load(is);

                Splitter splitter = new Splitter();
                splitter.setStartPage(start);
                splitter.setEndPage(end);
                splitter.setSplitAtPage(end - start + 1);

                List<PDDocument> splitDocs = splitter.split(doc);
                if (!splitDocs.isEmpty()) {
                    Uri outUri = createOutputUri("Split_" + start + "-" + end);
                    OutputStream os = getContentResolver().openOutputStream(outUri);
                    splitDocs.get(0).save(os);
                    splitDocs.get(0).close();
                    if (os != null) os.close();
                }

                doc.close();
                runOnUiThread(() -> {
                    hideProgress();
                    Toast.makeText(this, "Saved Split PDF successfully!", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideProgress();
                    Toast.makeText(this, "Split Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ================== PDF COMPRESS ==================
    private void showCompressDialog(Uri uri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);

        TextView title = new TextView(this);
        title.setText("Compress PDF");
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(textColor);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 32);
        layout.addView(title);

        Button btnLow = createCompressButton("Low Compression\n(Best Quality, Larger Size)");
        Button btnMed = createCompressButton("Medium Compression\n(Balanced)");
        Button btnHigh = createCompressButton("High Compression\n(Smallest Viable Size)");

        layout.addView(btnLow);
        layout.addView(btnMed);
        layout.addView(btnHigh);

        builder.setView(layout);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.setOnShowListener(d -> {
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(panelColor);
                gd.setCornerRadius(60f);
                dialog.getWindow().getDecorView().setBackground(gd);
            });
        }

        btnLow.setOnClickListener(v -> { dialog.dismiss(); executePdfCompress(uri, 0.85f, 1.0f, "LOWCOMPRESSED"); });
        btnMed.setOnClickListener(v -> { dialog.dismiss(); executePdfCompress(uri, 0.6f, 0.75f, "MEDIUMCOMPRESSED"); });
        btnHigh.setOnClickListener(v -> { dialog.dismiss(); executePdfCompress(uri, 0.30f, 0.50f, "HIGHCOMPRESSED"); });

        dialog.show();
    }

    private Button createCompressButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setPadding(32, 32, 32, 32);
        b.setBackgroundTintList(ColorStateList.valueOf(panelColor == Color.WHITE ? Color.parseColor("#E5E5EA") : Color.parseColor("#3A3A3C")));
        b.setTextColor(textColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 24);
        b.setLayoutParams(lp);
        return b;
    }

    private void executePdfCompress(Uri uri, float jpegQuality, float scaleFactor, String prefix) {
        showProgress("Deep Compressing Images in PDF...");
        new Thread(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                PDDocument doc = PDDocument.load(is);

                for (PDPage page : doc.getPages()) {
                    PDResources resources = page.getResources();
                    for (COSName name : resources.getXObjectNames()) {
                        PDXObject xObject = resources.getXObject(name);
                        if (xObject instanceof PDImageXObject) {
                            PDImageXObject imgObj = (PDImageXObject) xObject;
                            Bitmap bmp = imgObj.getImage();

                            if (bmp != null) {
                                // Smart Signature Preservation:
                                // Do not compress extremely small images (like digital signatures or tiny logos)
                                if (bmp.getWidth() < 400 && bmp.getHeight() < 400) {
                                    continue;
                                }

                                int newW = Math.max(1, (int)(bmp.getWidth() * scaleFactor));
                                int newH = Math.max(1, (int)(bmp.getHeight() * scaleFactor));
                                Bitmap scaled = Bitmap.createScaledBitmap(bmp, newW, newH, true);

                                // Fix for transparent PNG signatures turning black
                                // Render them onto a solid white canvas before JPEG conversion
                                Bitmap opaqueBmp = Bitmap.createBitmap(scaled.getWidth(), scaled.getHeight(), Bitmap.Config.ARGB_8888);
                                Canvas canvas = new Canvas(opaqueBmp);
                                canvas.drawColor(Color.WHITE);
                                canvas.drawBitmap(scaled, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));

                                PDImageXObject newImgObj = JPEGFactory.createFromImage(doc, opaqueBmp, jpegQuality);
                                resources.put(name, newImgObj);

                                if (scaled != bmp) scaled.recycle();
                                opaqueBmp.recycle();
                            }
                        }
                    }
                }

                // Pass the requested prefix here
                Uri outUri = createOutputUri(prefix);
                OutputStream os = getContentResolver().openOutputStream(outUri);
                doc.save(os);
                doc.close();
                if (os != null) os.close();

                runOnUiThread(() -> {
                    hideProgress();
                    Toast.makeText(this, "Compressed successfully!", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideProgress();
                    Toast.makeText(this, "Compression Failed: File may be protected or text-only.", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}