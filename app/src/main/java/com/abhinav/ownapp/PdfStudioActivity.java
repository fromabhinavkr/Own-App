package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"all"})
@SuppressLint("SetTextI18n")
public class PdfStudioActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout; private RelativeLayout mainContent, cropUIContainer; private LinearLayout leftDrawer, rightDrawer, centerPrompt, drawerPagesContainer, pageNavContainer;
    private TextView btnGallery, tvTitle, btnExport, btnLoadPdf, btnScanPdf, btnPages, btnTools, toolCropPage, toolAddImage, toolAddPdf, toolAddScannedPdf, toolDeletePage, tvLoading, btnPrevPage, btnNextPage, tvPageIndicator;
    private TextView btnCropCancel, btnCropFree, btnCropFull, btnCrop11, btnCropA4, btnCropRotate, btnCropMirror, btnCropApply;
    private ImageView pdfRenderView; private View pdfViewerContainer; private SeekBar pageSeekBar; private FrameLayout cropViewFrame;
    private ScanToPdfHelper scanToPdfHelper; private boolean isDarkTheme, isAddingScan = false; private int currentPageIndex = 0; private Uri cropOutputUri; private CustomCropView customCropView;
    private Dialog customProgressDialog; private TextView customProgressText;

    // --- 3-State Theme Variables ---
    private int themeState;
    private int colorBg, colorDrawerBg, colorText, colorPillBg, colorToolBg, colorDialogBg;

    private static class PdfPageItem { int originalIndex; Uri sourceUri; Bitmap thumbnail; ImageView ivRef; boolean isImage; public PdfPageItem(int i, Uri u, boolean img) { originalIndex=i; sourceUri=u; isImage=img; } }
    private final List<PdfPageItem> pdfPages = new ArrayList<>();

    private void showCustomProgress(String message) {
        runOnUiThread(() -> {
            if (customProgressDialog == null) {
                customProgressDialog = new Dialog(this); customProgressDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                if (customProgressDialog.getWindow() != null) customProgressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); customProgressDialog.setCancelable(false);
                LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(80, 80, 80, 80); root.setGravity(android.view.Gravity.CENTER);
                GradientDrawable bg = new GradientDrawable(); bg.setColor(colorDialogBg); bg.setCornerRadius(100f); root.setBackground(bg);
                customProgressText = new TextView(this); customProgressText.setTextColor(colorText); customProgressText.setTextSize(18f); customProgressText.setTypeface(null, Typeface.BOLD); customProgressText.setGravity(android.view.Gravity.CENTER);
                root.addView(customProgressText); customProgressDialog.setContentView(root, new ViewGroup.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels * 0.85), -2));
            }
            customProgressText.setText(message); if (!customProgressDialog.isShowing()) customProgressDialog.show();
        });
    }
    private void updateCustomProgress(String message) { runOnUiThread(() -> { if (customProgressText != null) customProgressText.setText(message); }); }
    private void hideCustomProgress() { runOnUiThread(() -> { if (customProgressDialog != null && customProgressDialog.isShowing()) customProgressDialog.dismiss(); }); }

    private class CustomCropView extends View {
        Bitmap bitmap; RectF imageRect=new RectF(), cropRect=new RectF(); Paint dimPaint=new Paint(), linePaint=new Paint(Paint.ANTI_ALIAS_FLAG), handlePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        int draggingHandle=-1; float touchDx, touchDy, targetRatio=0f;
        public CustomCropView(android.content.Context c){ super(c); dimPaint.setColor(Color.parseColor("#99000000")); linePaint.setColor(Color.WHITE); linePaint.setStrokeWidth(5f); linePaint.setStyle(Paint.Style.STROKE); handlePaint.setColor(Color.WHITE); handlePaint.setStyle(Paint.Style.FILL); }
        public void setBitmap(Bitmap b){ this.bitmap=b; post(()->{ updateImageRect(); cropRect.set(imageRect); invalidate(); }); }
        public void setRatio(float r){ targetRatio=r; applyRatio(); invalidate(); }
        public void rotate(){ if(bitmap==null) return; android.graphics.Matrix m=new android.graphics.Matrix(); m.postRotate(90); Bitmap b=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true); Bitmap old=bitmap; setBitmap(b); if(old!=b) old.recycle(); }
        public void mirror(){ if(bitmap==null) return; android.graphics.Matrix m=new android.graphics.Matrix(); m.preScale(-1f,1f); Bitmap b=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true); Bitmap old=bitmap; setBitmap(b); if(old!=b) old.recycle(); }
        private void updateImageRect(){ if(bitmap==null || getWidth()==0) return; float scale = Math.min((float)getWidth()/bitmap.getWidth(), (float)getHeight()/bitmap.getHeight()); float w = bitmap.getWidth()*scale, h = bitmap.getHeight()*scale; float cx = getWidth()/2f, cy = getHeight()/2f; imageRect.set(cx-w/2f, cy-h/2f, cx+w/2f, cy+h/2f); }
        private void applyRatio(){ if(targetRatio==0) return; float cx = cropRect.centerX(), cy = cropRect.centerY(), w = cropRect.width(), h = cropRect.height(); if(w/h > targetRatio) w = h * targetRatio; else h = w / targetRatio; if(cx-w/2f < imageRect.left) w = (cx-imageRect.left)*2f; if(cy-h/2f < imageRect.top) h = (cy-imageRect.top)*2f; cropRect.set(cx-w/2f, cy-h/2f, cx+w/2f, cy+h/2f); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); if(bitmap==null) return; c.drawBitmap(bitmap, null, imageRect, new Paint(Paint.FILTER_BITMAP_FLAG));
            c.drawRect(imageRect.left, imageRect.top, imageRect.right, cropRect.top, dimPaint); c.drawRect(imageRect.left, cropRect.bottom, imageRect.right, imageRect.bottom, dimPaint); c.drawRect(imageRect.left, cropRect.top, cropRect.left, cropRect.bottom, dimPaint); c.drawRect(cropRect.right, cropRect.top, imageRect.right, cropRect.bottom, dimPaint); c.drawRect(cropRect, linePaint);
            float w=cropRect.width()/3f, h=cropRect.height()/3f; for(int i=1;i<3;i++){ c.drawLine(cropRect.left+w*i, cropRect.top, cropRect.left+w*i, cropRect.bottom, linePaint); c.drawLine(cropRect.left, cropRect.top+h*i, cropRect.right, cropRect.top+h*i, linePaint); }
            float r=25f; c.drawCircle(cropRect.left, cropRect.top, r, handlePaint); c.drawCircle(cropRect.right, cropRect.top, r, handlePaint); c.drawCircle(cropRect.left, cropRect.bottom, r, handlePaint); c.drawCircle(cropRect.right, cropRect.bottom, r, handlePaint);
            c.drawCircle(cropRect.centerX(), cropRect.top, r, handlePaint); c.drawCircle(cropRect.centerX(), cropRect.bottom, r, handlePaint); c.drawCircle(cropRect.left, cropRect.centerY(), r, handlePaint); c.drawCircle(cropRect.right, cropRect.centerY(), r, handlePaint);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX(), y=e.getY();
            if(e.getAction()==MotionEvent.ACTION_DOWN){ float th=80f; draggingHandle=-1;
                if(dist(x,y,cropRect.left,cropRect.top)<th) draggingHandle=0; else if(dist(x,y,cropRect.right,cropRect.top)<th) draggingHandle=1; else if(dist(x,y,cropRect.left,cropRect.bottom)<th) draggingHandle=2; else if(dist(x,y,cropRect.right,cropRect.bottom)<th) draggingHandle=3; else if(dist(x,y,cropRect.centerX(),cropRect.top)<th) draggingHandle=4; else if(dist(x,y,cropRect.centerX(),cropRect.bottom)<th) draggingHandle=5; else if(dist(x,y,cropRect.left,cropRect.centerY())<th) draggingHandle=6; else if(dist(x,y,cropRect.right,cropRect.centerY())<th) draggingHandle=7; else if(cropRect.contains(x,y)){ draggingHandle=8; touchDx=x-cropRect.left; touchDy=y-cropRect.top; } return true;
            } else if(e.getAction()==MotionEvent.ACTION_MOVE && draggingHandle!=-1){
                float l=cropRect.left, t=cropRect.top, r=cropRect.right, b=cropRect.bottom;
                if(draggingHandle==8){ float w=cropRect.width(), h=cropRect.height(); l=x-touchDx; t=y-touchDy; r=l+w; b=t+h; if(l<imageRect.left){l=imageRect.left; r=l+w;} if(t<imageRect.top){t=imageRect.top; b=t+h;} if(r>imageRect.right){r=imageRect.right; l=r-w;} if(b>imageRect.bottom){b=imageRect.bottom; t=b-h;} cropRect.set(l,t,r,b); invalidate(); return true; }
                if(draggingHandle==0||draggingHandle==4||draggingHandle==6){ l=Math.max(imageRect.left, Math.min(x, r-100)); t=Math.max(imageRect.top, Math.min(y, b-100)); if(draggingHandle==4) l=cropRect.left; if(draggingHandle==6) t=cropRect.top; }
                if(draggingHandle==1||draggingHandle==4||draggingHandle==7){ r=Math.min(imageRect.right, Math.max(x, l+100)); t=Math.max(imageRect.top, Math.min(y, b-100)); if(draggingHandle==4) r=cropRect.right; if(draggingHandle==7) t=cropRect.top; }
                if(draggingHandle==2||draggingHandle==6||draggingHandle==5){ l=Math.max(imageRect.left, Math.min(x, r-100)); b=Math.min(imageRect.bottom, Math.max(y, t+100)); if(draggingHandle==5) l=cropRect.left; if(draggingHandle==6) b=cropRect.bottom; }
                if(draggingHandle==3||draggingHandle==7||draggingHandle==5){ r=Math.min(imageRect.right, Math.max(x, l+100)); b=Math.min(imageRect.bottom, Math.max(y, t+100)); if(draggingHandle==5) r=cropRect.right; if(draggingHandle==7) b=cropRect.bottom; }
                cropRect.set(l,t,r,b); if(targetRatio!=0) applyRatio(); invalidate(); return true;
            } return super.onTouchEvent(e);
        }
        private float dist(float x1, float y1, float x2, float y2){ return (float)Math.hypot(x1-x2, y1-y2); }
        public Bitmap getCroppedImage(){ if(bitmap==null) return null; float scaleX = bitmap.getWidth()/imageRect.width(), scaleY = bitmap.getHeight()/imageRect.height(); int cx = (int)((cropRect.left-imageRect.left)*scaleX), cy = (int)((cropRect.top-imageRect.top)*scaleY), cw = (int)(cropRect.width()*scaleX), ch = (int)(cropRect.height()*scaleY); cx=Math.max(0,cx); cy=Math.max(0,cy); cw=Math.min(bitmap.getWidth()-cx,cw); ch=Math.min(bitmap.getHeight()-cy,ch); return Bitmap.createBitmap(bitmap, cx, cy, cw, ch); }
    }

    private void openPicker(ActivityResultLauncher<Intent> launcher, boolean allowMultiple, String title, String type) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT); intent.setType(type); intent.addCategory(Intent.CATEGORY_OPENABLE);
        if(allowMultiple) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); launcher.launch(Intent.createChooser(intent, title));
    }

    private final ActivityResultLauncher<Intent> loadPdfLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) { Uri u = result.getData().getData(); if (u != null) loadPdfIntoStudio(u); }
    });

    private final ActivityResultLauncher<Intent> addPdfLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Intent d = result.getData(); List<Uri> uris = new ArrayList<>();
            if (d.getClipData() != null) { for(int i=0; i<d.getClipData().getItemCount(); i++) uris.add(d.getClipData().getItemAt(i).getUri()); } else if (d.getData() != null) uris.add(d.getData());
            if(uris.isEmpty()) return; List<String> opts = new ArrayList<>(); opts.add("1. Front"); opts.add("2. Back"); showCustomDialog("Add Position", opts, w -> addPdfsToStudio(uris, w == 0));
        }
    });

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) { Uri imageUri = result.getData().getData(); if (imageUri != null) { List<String> opts = new ArrayList<>(); opts.add("1. Front"); opts.add("2. Back"); showCustomDialog("Add Position", opts, w -> convertImageToPdfAndAdd(imageUri, w == 0)); } }
    });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_pdf_studio);
        PDFBoxResourceLoader.init(getApplicationContext());

        // --- 3-STATE THEME LOGIC INJECTION ---
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = oldDark ? 1 : 0;
        }
        isDarkTheme = (themeState != 0); // Legacy check for boolean utilities

        if (themeState == 0) { // Light Mode
            colorBg = Color.parseColor("#F2F2F7");
            colorDrawerBg = Color.parseColor("#CCF2F2F7");
            colorText = Color.BLACK;
            colorPillBg = Color.WHITE;
            colorToolBg = Color.parseColor("#E5E5EA");
            colorDialogBg = Color.parseColor("#CCF2F2F7");
        } else if (themeState == 1) { // Standard Dark Mode
            colorBg = Color.parseColor("#1C1C1E");
            colorDrawerBg = Color.parseColor("#CC1C1C1E");
            colorText = Color.WHITE;
            colorPillBg = Color.parseColor("#332D2B");
            colorToolBg = Color.parseColor("#2C2C2E");
            colorDialogBg = Color.parseColor("#CC1C1C1E");
        } else { // Star Mode (AMOLED Pure Black)
            colorBg = Color.parseColor("#000000"); // Pure Black canvas
            colorDrawerBg = Color.parseColor("#E61C1C1E"); // Slightly translucent dark gray to hover above black
            colorText = Color.WHITE;
            colorPillBg = Color.parseColor("#1C1C1E");
            colorToolBg = Color.parseColor("#1C1C1E");
            colorDialogBg = Color.parseColor("#E61C1C1E");
        }

        initViews(); applyTheme(); setupListeners();
        scanToPdfHelper = new ScanToPdfHelper(this);
        scanToPdfHelper.setOnScanCompletedListener(new ScanToPdfHelper.OnScanCompletedListener() {
            @Override public void onPdfCreated(Uri pdfUri, int pageCount) {
                if(!isAddingScan) { Toast.makeText(PdfStudioActivity.this, "Scanned " + pageCount + " pages!", Toast.LENGTH_SHORT).show(); loadPdfIntoStudio(pdfUri); }
                else { List<String> opts = new ArrayList<>(); opts.add("1. Front"); opts.add("2. Back"); List<Uri> uris = new ArrayList<>(); uris.add(pdfUri); showCustomDialog("Add Position", opts, w -> addPdfsToStudio(uris, w == 0)); }
            }
            @Override public void onScanCancelled() { Toast.makeText(PdfStudioActivity.this, "Scan cancelled", Toast.LENGTH_SHORT).show(); }
            @Override public void onScanError(Exception e) { Toast.makeText(PdfStudioActivity.this, "Scan Error: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
        });
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout); mainContent = findViewById(R.id.mainContent); leftDrawer = findViewById(R.id.leftDrawer); rightDrawer = findViewById(R.id.rightDrawer); centerPrompt = findViewById(R.id.centerPrompt); drawerPagesContainer = findViewById(R.id.drawerPagesContainer);
        btnGallery = findViewById(R.id.btnGallery); tvTitle = findViewById(R.id.tvTitle); btnExport = findViewById(R.id.btnExport); btnLoadPdf = findViewById(R.id.btnLoadPdf); btnScanPdf = findViewById(R.id.btnScanPdf);
        btnPages = findViewById(R.id.btnPages); btnTools = findViewById(R.id.btnTools); toolCropPage = findViewById(R.id.toolCropPage); toolAddImage = findViewById(R.id.toolAddImage); toolAddPdf = findViewById(R.id.toolAddPdf); toolAddScannedPdf = findViewById(R.id.toolAddScannedPdf); toolDeletePage = findViewById(R.id.toolDeletePage);
        pdfViewerContainer = findViewById(R.id.pdfViewerContainer); pdfRenderView = findViewById(R.id.pdfRenderView); tvLoading = findViewById(R.id.tvLoading); pageNavContainer = findViewById(R.id.pageNavContainer); btnPrevPage = findViewById(R.id.btnPrevPage); btnNextPage = findViewById(R.id.btnNextPage); tvPageIndicator = findViewById(R.id.tvPageIndicator); pageSeekBar = findViewById(R.id.pageSeekBar);
        cropUIContainer = findViewById(R.id.cropUIContainer); cropViewFrame = findViewById(R.id.cropViewFrame); customCropView = new CustomCropView(this); cropViewFrame.addView(customCropView);
        btnCropCancel = findViewById(R.id.btnCropCancel); btnCropFree = findViewById(R.id.btnCropFree); btnCropFull = findViewById(R.id.btnCropFull); btnCrop11 = findViewById(R.id.btnCrop11); btnCropA4 = findViewById(R.id.btnCropA4); btnCropRotate = findViewById(R.id.btnCropRotate); btnCropMirror = findViewById(R.id.btnCropMirror); btnCropApply = findViewById(R.id.btnCropApply);
    }

    private GradientDrawable createPill(int color) { GradientDrawable gd = new GradientDrawable(); gd.setColor(color); gd.setCornerRadius(100f); return gd; }
    private void styleTool(TextView tv, int icon, int bg, int txt) { tv.setBackground(createPill(bg)); tv.setTextColor(txt); tv.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0); tv.setCompoundDrawablePadding(32); if(tv.getCompoundDrawables()[0]!=null) tv.getCompoundDrawables()[0].setColorFilter(new PorterDuffColorFilter(txt, PorterDuff.Mode.SRC_IN)); }

    private void applyTheme() {
        int accent = Color.parseColor("#5A9AF4");
        mainContent.setBackgroundColor(colorBg); leftDrawer.setBackgroundColor(colorDrawerBg); rightDrawer.setBackgroundColor(colorDrawerBg); getWindow().setStatusBarColor(colorBg);
        tvTitle.setTextColor(colorText); ((TextView)findViewById(R.id.tvLeftTitle)).setTextColor(colorText); ((TextView)findViewById(R.id.tvRightTitle)).setTextColor(colorText);
        btnGallery.setBackground(createPill(colorPillBg)); btnGallery.setTextColor(colorText); btnExport.setBackground(createPill(colorPillBg)); btnExport.setTextColor(colorText);
        btnPages.setBackground(createPill(colorPillBg)); btnPages.setTextColor(colorText); btnTools.setBackground(createPill(colorPillBg)); btnTools.setTextColor(colorText);
        btnLoadPdf.setBackground(createPill(accent)); btnScanPdf.setBackground(createPill(Color.parseColor("#34C759")));
        pageNavContainer.setBackground(createPill(colorPillBg)); tvPageIndicator.setTextColor(colorText); btnPrevPage.setTextColor(colorText); btnNextPage.setTextColor(colorText);
        float density = getResources().getDisplayMetrics().density; GradientDrawable thumb = new GradientDrawable(); thumb.setShape(GradientDrawable.RECTANGLE); thumb.setCornerRadius(100f); thumb.setColor(Color.parseColor("#665A9AF4")); thumb.setSize((int)(48*density), (int)(36*density)); pageSeekBar.setThumb(thumb);
        styleTool(toolCropPage, android.R.drawable.ic_menu_crop, colorToolBg, colorText); styleTool(toolAddImage, android.R.drawable.ic_menu_gallery, colorToolBg, colorText); styleTool(toolAddPdf, android.R.drawable.ic_menu_add, colorToolBg, colorText); styleTool(toolAddScannedPdf, android.R.drawable.ic_menu_camera, colorToolBg, colorText); styleTool(toolDeletePage, android.R.drawable.ic_menu_delete, colorToolBg, colorText);
        btnCropCancel.setBackground(createPill(Color.parseColor("#FF4444"))); btnCropFree.setBackground(createPill(accent)); btnCropFull.setBackground(createPill(accent)); btnCrop11.setBackground(createPill(accent)); btnCropA4.setBackground(createPill(accent)); btnCropRotate.setBackground(createPill(accent)); btnCropMirror.setBackground(createPill(accent)); btnCropApply.setBackground(createPill(Color.parseColor("#34C759")));
    }

    private void setupListeners() {
        btnGallery.setOnClickListener(v -> startActivity(new Intent(this, PdfGalleryActivity.class)));
        btnExport.setOnClickListener(v -> showExportDialog());
        btnLoadPdf.setOnClickListener(v -> openPicker(loadPdfLauncher, false, "Select PDF from Internal Storage", "application/pdf"));
        btnScanPdf.setOnClickListener(v -> { isAddingScan = false; scanToPdfHelper.startScanToPdf(50); });
        btnPages.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        btnTools.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.END));
        toolCropPage.setOnClickListener(v -> { drawerLayout.closeDrawers(); if(pdfPages.isEmpty()) return; launchInternalCrop(); });
        toolAddImage.setOnClickListener(v -> { drawerLayout.closeDrawers(); if(pdfPages.isEmpty()){ Toast.makeText(this, "Load a Base PDF first!", Toast.LENGTH_SHORT).show(); return; } openPicker(imagePickerLauncher, false, "Select Image", "image/*"); });
        toolAddPdf.setOnClickListener(v -> { drawerLayout.closeDrawers(); if(pdfPages.isEmpty()){ Toast.makeText(this, "Load a Base PDF first!", Toast.LENGTH_SHORT).show(); return; } openPicker(addPdfLauncher, true, "Select PDFs to Add", "application/pdf"); });
        toolAddScannedPdf.setOnClickListener(v -> { drawerLayout.closeDrawers(); if(pdfPages.isEmpty()){ Toast.makeText(this, "Load a Base PDF first!", Toast.LENGTH_SHORT).show(); return; } isAddingScan = true; scanToPdfHelper.startScanToPdf(50); });
        toolDeletePage.setOnClickListener(v -> { drawerLayout.closeDrawers(); if(pdfPages.isEmpty()){ Toast.makeText(this, "Load a PDF first!", Toast.LENGTH_SHORT).show(); return; } showDeletePagesDialog(); });
        btnPrevPage.setOnClickListener(v -> { if (currentPageIndex > 0) { currentPageIndex--; updatePageLabels(); renderMainPdfPage(); } });
        btnNextPage.setOnClickListener(v -> { if (currentPageIndex < pdfPages.size() - 1) { currentPageIndex++; updatePageLabels(); renderMainPdfPage(); } });
        pageSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { if(fromUser) { currentPageIndex = progress; updatePageLabels(); renderMainPdfPage(); } } @Override public void onStartTrackingTouch(SeekBar seekBar) {} @Override public void onStopTrackingTouch(SeekBar seekBar) {} });

        btnCropCancel.setOnClickListener(v -> cropUIContainer.setVisibility(View.GONE));
        btnCropFree.setOnClickListener(v -> customCropView.setRatio(0f)); btnCropFull.setOnClickListener(v -> { customCropView.setRatio(0f); customCropView.setBitmap(customCropView.bitmap); });
        btnCrop11.setOnClickListener(v -> customCropView.setRatio(1f)); btnCropA4.setOnClickListener(v -> customCropView.setRatio(1f/1.414f));
        btnCropRotate.setOnClickListener(v -> customCropView.rotate()); btnCropMirror.setOnClickListener(v -> customCropView.mirror());
        btnCropApply.setOnClickListener(v -> applyInternalCrop());
    }

    private void launchInternalCrop() {
        showCustomProgress("Loading Crop Engine...");
        new Thread(() -> {
            try {
                PdfPageItem item = pdfPages.get(currentPageIndex); Bitmap bmp;
                if (item.isImage) { bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), item.sourceUri); }
                else { ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(item.sourceUri, "r"); if(pfd == null) return; PdfRenderer renderer = new PdfRenderer(pfd); PdfRenderer.Page page = renderer.openPage(item.originalIndex); bmp = Bitmap.createBitmap((int)(page.getWidth()*3.0f), (int)(page.getHeight()*3.0f), Bitmap.Config.ARGB_8888); Canvas c = new Canvas(bmp); c.drawColor(Color.WHITE); c.scale(3.0f, 3.0f); page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); page.close(); renderer.close(); pfd.close(); }
                final Bitmap finalBmp = bmp; runOnUiThread(() -> { hideCustomProgress(); customCropView.setBitmap(finalBmp); cropUIContainer.setVisibility(View.VISIBLE); });
            } catch(Exception e) { runOnUiThread(() -> { hideCustomProgress(); Toast.makeText(this, "Failed to load crop", Toast.LENGTH_SHORT).show(); }); }
        }).start();
    }

    private void applyInternalCrop() {
        Bitmap cropped = customCropView.getCroppedImage(); if(cropped == null) return;
        showCustomProgress("Applying Crop...");
        new Thread(() -> {
            try {
                File tempImg = new File(getCacheDir(), "cropped_img_" + System.currentTimeMillis() + ".png"); FileOutputStream fos = new FileOutputStream(tempImg); cropped.compress(Bitmap.CompressFormat.PNG, 100, fos); fos.close();
                pdfPages.get(currentPageIndex).sourceUri = Uri.fromFile(tempImg); pdfPages.get(currentPageIndex).originalIndex = 0; pdfPages.get(currentPageIndex).thumbnail = null; pdfPages.get(currentPageIndex).isImage = true;
                runOnUiThread(() -> { hideCustomProgress(); cropUIContainer.setVisibility(View.GONE); startThumbnailLoader(); renderMainPdfPage(); Toast.makeText(this, "Page Cropped!", Toast.LENGTH_SHORT).show(); });
            } catch(Exception e) { runOnUiThread(() -> { hideCustomProgress(); Toast.makeText(this, "Crop failed", Toast.LENGTH_SHORT).show(); }); }
        }).start();
    }

    private void convertImageToPdfAndAdd(Uri imageUri, boolean toFront) {
        showCustomProgress("Adding Image...");
        new Thread(() -> {
            try {
                List<PdfPageItem> newPages = new ArrayList<>(); newPages.add(new PdfPageItem(0, imageUri, true));
                if(toFront) pdfPages.addAll(0, newPages); else pdfPages.addAll(newPages);
                runOnUiThread(() -> { hideCustomProgress(); rebuildPagesDrawerUI(); updatePageLabels(); renderMainPdfPage(); Toast.makeText(this, "Image Added!", Toast.LENGTH_SHORT).show(); startThumbnailLoader(); });
            } catch (Exception e) { runOnUiThread(() -> { hideCustomProgress(); Toast.makeText(this, "Failed to add image", Toast.LENGTH_SHORT).show(); }); }
        }).start();
    }

    private void showDeletePagesDialog() {
        Dialog d = new Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE); if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(80, 80, 80, 80);
        GradientDrawable rootBg = new GradientDrawable(); rootBg.setColor(colorDialogBg); rootBg.setCornerRadius(100f); root.setBackground(rootBg);
        TextView title = new TextView(this); title.setText("Delete Pages"); title.setTextColor(colorText); title.setTextSize(20f); title.setTypeface(null, Typeface.BOLD); title.setPadding(0, 0, 0, 60); title.setGravity(android.view.Gravity.CENTER); root.addView(title);
        LinearLayout inputs = new LinearLayout(this); inputs.setOrientation(LinearLayout.HORIZONTAL); inputs.setGravity(android.view.Gravity.CENTER); inputs.setPadding(0, 0, 0, 60);
        EditText etFrom = new EditText(this); etFrom.setHint("From"); etFrom.setInputType(InputType.TYPE_CLASS_NUMBER); etFrom.setTextColor(colorText); etFrom.setHintTextColor(Color.GRAY); etFrom.setBackground(createPill(colorPillBg)); etFrom.setPadding(40, 40, 40, 40); etFrom.setGravity(android.view.Gravity.CENTER); LinearLayout.LayoutParams pFrom = new LinearLayout.LayoutParams(0, -2, 1f); inputs.addView(etFrom, pFrom);
        TextView tvTo = new TextView(this); tvTo.setText(" to "); tvTo.setTextColor(colorText); tvTo.setTextSize(16f); tvTo.setTypeface(null, Typeface.BOLD); tvTo.setPadding(20, 0, 20, 0); inputs.addView(tvTo);
        EditText etTo = new EditText(this); etTo.setHint("To"); etTo.setInputType(InputType.TYPE_CLASS_NUMBER); etTo.setTextColor(colorText); etTo.setHintTextColor(Color.GRAY); etTo.setBackground(createPill(colorPillBg)); etTo.setPadding(40, 40, 40, 40); etTo.setGravity(android.view.Gravity.CENTER); LinearLayout.LayoutParams pTo = new LinearLayout.LayoutParams(0, -2, 1f); inputs.addView(etTo, pTo);
        root.addView(inputs);
        TextView btnDel = new TextView(this); btnDel.setText("Delete Range"); btnDel.setTextColor(Color.WHITE); btnDel.setTextSize(16f); btnDel.setTypeface(null, Typeface.BOLD); btnDel.setGravity(android.view.Gravity.CENTER); btnDel.setPadding(40, 40, 40, 40); btnDel.setBackground(createPill(Color.parseColor("#FF4444")));
        btnDel.setOnClickListener(v -> {
            try {
                int f = Integer.parseInt(etFrom.getText().toString()); int t = Integer.parseInt(etTo.getText().toString());
                if(f < 1 || t > pdfPages.size() || f > t) { Toast.makeText(this, "Invalid range", Toast.LENGTH_SHORT).show(); return; }
                for(int i = t - 1; i >= f - 1; i--) { pdfPages.get(i).ivRef = null; pdfPages.remove(i); }
                d.dismiss(); rebuildPagesDrawerUI(); if(currentPageIndex >= pdfPages.size()) currentPageIndex = Math.max(0, pdfPages.size() - 1); updatePageLabels(); renderMainPdfPage(); Toast.makeText(this, "Pages deleted!", Toast.LENGTH_SHORT).show();
            } catch(Exception e) { Toast.makeText(this, "Enter valid numbers", Toast.LENGTH_SHORT).show(); }
        }); root.addView(btnDel);
        d.setContentView(root, new ViewGroup.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels * 0.90), -2)); d.show();
    }

    private void showCustomDialog(String titleText, List<String> items, DialogCallback callback) {
        Dialog d = new Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE); if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(80, 80, 80, 80);
        GradientDrawable rootBg = new GradientDrawable(); rootBg.setColor(colorDialogBg); rootBg.setCornerRadius(100f); root.setBackground(rootBg);
        TextView title = new TextView(this); title.setText(titleText); title.setTextColor(colorText); title.setTextSize(20f); title.setTypeface(null, Typeface.BOLD); title.setPadding(0, 0, 0, 60); title.setGravity(android.view.Gravity.CENTER); root.addView(title);
        ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.size(); i++) {
            TextView item = new TextView(this); item.setText(items.get(i)); item.setTextColor(colorText); item.setTextSize(16f); item.setTypeface(null, Typeface.BOLD); item.setPadding(40, 40, 40, 40); item.setGravity(android.view.Gravity.CENTER);
            item.setBackground(createPill(colorPillBg)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, 24); item.setLayoutParams(lp);
            final int idx = i; item.setOnClickListener(v -> { d.dismiss(); callback.onSelect(idx); }); list.addView(item);
        }
        scroll.addView(list); root.addView(scroll); d.setContentView(root, new ViewGroup.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels * 0.85), -2)); d.show();
    }
    private interface DialogCallback { void onSelect(int index); }

    private void showExportDialog() {
        if(pdfPages.isEmpty()) { Toast.makeText(this, "No PDF loaded to export!", Toast.LENGTH_SHORT).show(); return; }
        Dialog d = new Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE); if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(80, 80, 80, 80);
        GradientDrawable rootBg = new GradientDrawable(); rootBg.setColor(colorDialogBg); rootBg.setCornerRadius(100f); root.setBackground(rootBg);
        TextView title = new TextView(this); title.setText("Export Options"); title.setTextColor(colorText); title.setTextSize(20f); title.setTypeface(null, Typeface.BOLD); title.setPadding(0, 0, 0, 60); title.setGravity(android.view.Gravity.CENTER); root.addView(title);
        TextView btn = new TextView(this); btn.setText("Original (Lossless)"); btn.setTextColor(colorText); btn.setTextSize(16f); btn.setTypeface(null, Typeface.BOLD); btn.setGravity(android.view.Gravity.CENTER_VERTICAL); btn.setPadding(40, 40, 40, 40);
        btn.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_save, 0, 0, 0); btn.setCompoundDrawablePadding(32); if(btn.getCompoundDrawables()[0]!=null) btn.getCompoundDrawables()[0].setColorFilter(new PorterDuffColorFilter(colorText, PorterDuff.Mode.SRC_IN));
        btn.setBackground(createPill(colorPillBg)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, 24); btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> { d.dismiss(); exportLosslessPdf("Original"); }); root.addView(btn);
        d.setContentView(root, new ViewGroup.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels * 0.90), -2)); d.show();
    }

    private void exportLosslessPdf(String prefix) {
        showCustomProgress("Exporting... 0%");
        new Thread(() -> {
            java.util.Map<Uri, PDDocument> openDocs = new java.util.HashMap<>(); java.util.Map<Uri, File> tempFiles = new java.util.HashMap<>();
            try {
                PDDocument finalDoc = new PDDocument();
                for (int i = 0; i < pdfPages.size(); i++) {
                    PdfPageItem item = pdfPages.get(i);
                    if (item.isImage) {
                        Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), item.sourceUri); float pdfWidth = bmp.getWidth() / 3f; float pdfHeight = bmp.getHeight() / 3f;
                        if(pdfWidth > 842f || pdfHeight > 842f) { float scale = Math.min(842f / pdfWidth, 842f / pdfHeight); pdfWidth *= scale; pdfHeight *= scale; }
                        PDPage page = new PDPage(new com.tom_roush.pdfbox.pdmodel.common.PDRectangle(pdfWidth, pdfHeight)); finalDoc.addPage(page);
                        com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(finalDoc, bmp);
                        com.tom_roush.pdfbox.pdmodel.PDPageContentStream contentStream = new com.tom_roush.pdfbox.pdmodel.PDPageContentStream(finalDoc, page); contentStream.drawImage(pdImage, 0, 0, pdfWidth, pdfHeight); contentStream.close();
                    } else {
                        PDDocument sourceDoc = openDocs.get(item.sourceUri);
                        if (sourceDoc == null) {
                            File tempFile = new File(getCacheDir(), "pdfbox_cache_" + System.currentTimeMillis() + "_" + i + ".pdf"); java.io.InputStream in = getContentResolver().openInputStream(item.sourceUri); java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile);
                            byte[] buffer = new byte[8192]; int bytesRead; while ((bytesRead = in.read(buffer)) != -1) out.write(buffer, 0, bytesRead); in.close(); out.close();
                            sourceDoc = PDDocument.load(tempFile); openDocs.put(item.sourceUri, sourceDoc); tempFiles.put(item.sourceUri, tempFile);
                        }
                        if (sourceDoc != null) { PDPage page = sourceDoc.getPage(item.originalIndex); finalDoc.importPage(page); }
                    }
                    int pct = (int)(((i + 1) / (float)pdfPages.size()) * 100); updateCustomProgress("Exporting... " + pct + "%");
                }
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "OWN's PDF Gallery"); if(!dir.exists()) dir.mkdirs(); File file = new File(dir, prefix + "_" + System.currentTimeMillis() + ".pdf"); finalDoc.save(file); finalDoc.close();
                MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, new String[]{"application/pdf"}, null);
                runOnUiThread(() -> { hideCustomProgress(); Toast.makeText(this, "Exported Successfully!", Toast.LENGTH_SHORT).show(); startActivity(new Intent(this, PdfGalleryActivity.class)); });
            } catch (Exception e) { runOnUiThread(() -> { hideCustomProgress(); Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); }); }
            finally { for(PDDocument d : openDocs.values()) { try { d.close(); } catch(Exception ignored){} } for(File f : tempFiles.values()) { try { f.delete(); } catch(Exception ignored){} } }
        }).start();
    }

    private void loadPdfIntoStudio(Uri uri) {
        showCustomProgress("Loading PDF...");
        new Thread(() -> {
            try {
                ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r"); if(pfd == null) return;
                PdfRenderer renderer = new PdfRenderer(pfd); pdfPages.clear(); int count = renderer.getPageCount(); renderer.close(); pfd.close(); currentPageIndex = 0;
                for(int i=0; i<count; i++) pdfPages.add(new PdfPageItem(i, uri, false));
                runOnUiThread(() -> { hideCustomProgress(); rebuildPagesDrawerUI(); updatePageLabels(); renderMainPdfPage(); pdfViewerContainer.setVisibility(View.VISIBLE); Toast.makeText(this, "PDF Loaded!", Toast.LENGTH_SHORT).show(); startThumbnailLoader(); });
            } catch(Exception e) { runOnUiThread(() -> { hideCustomProgress(); Toast.makeText(this, "Failed to load PDF", Toast.LENGTH_SHORT).show(); centerPrompt.setVisibility(View.VISIBLE); }); }
        }).start();
    }

    private void addPdfsToStudio(List<Uri> uris, boolean toFront) {
        showCustomProgress("Adding PDFs...");
        new Thread(() -> {
            try {
                List<PdfPageItem> newPages = new ArrayList<>();
                for(Uri uri : uris) {
                    ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r"); if(pfd == null) continue;
                    PdfRenderer renderer = new PdfRenderer(pfd); int count = renderer.getPageCount(); renderer.close(); pfd.close();
                    for(int i=0; i<count; i++) newPages.add(new PdfPageItem(i, uri, false));
                }
                if(toFront) pdfPages.addAll(0, newPages); else pdfPages.addAll(newPages);
                runOnUiThread(() -> { hideCustomProgress(); rebuildPagesDrawerUI(); updatePageLabels(); renderMainPdfPage(); Toast.makeText(this, "PDFs Added!", Toast.LENGTH_SHORT).show(); startThumbnailLoader(); });
            } catch(Exception e) { runOnUiThread(() -> { hideCustomProgress(); Toast.makeText(this, "Failed to add PDF", Toast.LENGTH_SHORT).show(); }); }
        }).start();
    }

    private void startThumbnailLoader() {
        new Thread(() -> {
            for(int i=0; i<pdfPages.size(); i++) {
                PdfPageItem item = pdfPages.get(i);
                if(item.thumbnail == null) {
                    try {
                        Bitmap bmp;
                        if(item.isImage) {
                            Bitmap raw = MediaStore.Images.Media.getBitmap(getContentResolver(), item.sourceUri); float ratio = (float)raw.getHeight() / raw.getWidth(); bmp = Bitmap.createScaledBitmap(raw, 200, (int)(200*ratio), true); if(raw != bmp) raw.recycle();
                        } else {
                            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(item.sourceUri, "r"); if(pfd == null) continue;
                            PdfRenderer renderer = new PdfRenderer(pfd); PdfRenderer.Page page = renderer.openPage(item.originalIndex); float ratio = (float)page.getHeight() / page.getWidth();
                            bmp = Bitmap.createBitmap(200, (int)(200*ratio), Bitmap.Config.ARGB_8888); Canvas c = new Canvas(bmp); c.drawColor(Color.WHITE); page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); page.close(); renderer.close(); pfd.close();
                        }
                        item.thumbnail = bmp; runOnUiThread(() -> { if (item.ivRef != null) item.ivRef.setImageBitmap(item.thumbnail); });
                    } catch(Exception ignored) {}
                }
            }
        }).start();
    }

    private void renderMainPdfPage() {
        if(pdfPages.isEmpty()) { pdfViewerContainer.setVisibility(View.GONE); centerPrompt.setVisibility(View.VISIBLE); return; }
        tvPageIndicator.setText((currentPageIndex + 1) + " / " + pdfPages.size()); pageSeekBar.setMax(Math.max(0, pdfPages.size() - 1)); pageSeekBar.setProgress(currentPageIndex);
        new Thread(() -> {
            try {
                PdfPageItem item = pdfPages.get(currentPageIndex); Bitmap bmp;
                if(item.isImage) {
                    Bitmap raw = MediaStore.Images.Media.getBitmap(getContentResolver(), item.sourceUri); float ratio = (float)raw.getHeight() / raw.getWidth(); int w = getResources().getDisplayMetrics().widthPixels * 2; bmp = Bitmap.createScaledBitmap(raw, w, (int)(w * ratio), true); if(raw != bmp) raw.recycle();
                } else {
                    ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(item.sourceUri, "r"); if(pfd == null) return;
                    PdfRenderer renderer = new PdfRenderer(pfd); PdfRenderer.Page page = renderer.openPage(item.originalIndex); bmp = Bitmap.createBitmap(page.getWidth()*2, page.getHeight()*2, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(bmp); c.drawColor(Color.WHITE); page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); page.close(); renderer.close(); pfd.close();
                }
                final Bitmap finalBmp = bmp; runOnUiThread(() -> pdfRenderView.setImageBitmap(finalBmp));
            } catch(Exception ignored) {}
        }).start();
    }

    private void rebuildPagesDrawerUI() {
        drawerPagesContainer.removeAllViews(); float density = getResources().getDisplayMetrics().density;
        drawerPagesContainer.setOnDragListener((v, e) -> {
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED: return true;
                case DragEvent.ACTION_DROP:
                    View draggedView = (View) e.getLocalState(); int oldIdx = drawerPagesContainer.indexOfChild(draggedView), newIdx = -1;
                    for (int j = 0; j < drawerPagesContainer.getChildCount(); j++) { View c = drawerPagesContainer.getChildAt(j); if (e.getY() < c.getY() + c.getHeight() / 2f) { newIdx = j; break; } }
                    if (oldIdx != -1) { PdfPageItem item = pdfPages.remove(oldIdx); drawerPagesContainer.removeView(draggedView); if (newIdx == -1) { pdfPages.add(item); drawerPagesContainer.addView(draggedView); } else { if (newIdx > oldIdx) newIdx--; pdfPages.add(newIdx, item); drawerPagesContainer.addView(draggedView, newIdx); } updatePageLabels(); renderMainPdfPage(); }
                    draggedView.setVisibility(View.VISIBLE); return true;
                case DragEvent.ACTION_DRAG_ENDED: ((View) e.getLocalState()).setVisibility(View.VISIBLE); return true;
            } return false;
        });
        for (int i = 0; i < pdfPages.size(); i++) {
            PdfPageItem item = pdfPages.get(i); LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, (int)(16*density)); row.setLayoutParams(lp); row.setPadding((int)(16*density), (int)(16*density), (int)(16*density), (int)(16*density));
            ImageView iv = new ImageView(this); iv.setId(View.generateViewId()); iv.setOutlineProvider(new ViewOutlineProvider() { @Override public void getOutline(View v, Outline outline) { outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 20f * density); } }); iv.setClipToOutline(true);
            if(item.thumbnail != null) iv.setImageBitmap(item.thumbnail); else iv.setBackgroundColor(Color.LTGRAY); item.ivRef = iv;
            LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams((int)(120*density), (int)(160*density)); iv.setLayoutParams(ivLp); iv.setScaleType(ImageView.ScaleType.CENTER_CROP);

            LinearLayout rightCol = new LinearLayout(this); rightCol.setOrientation(LinearLayout.VERTICAL); rightCol.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams rightColLp = new LinearLayout.LayoutParams(0, -1, 1f); rightColLp.setMargins((int)(16*density), 0, 0, 0); rightCol.setLayoutParams(rightColLp);

            TextView tv = new TextView(this); tv.setText("Page " + (i + 1)); tv.setTextColor(colorText); tv.setTextSize(18f); tv.setTypeface(null, Typeface.BOLD); tv.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(-1, -2); tvLp.setMargins(0, 0, 0, (int)(24*density)); tv.setLayoutParams(tvLp);

            TextView btnDel = new TextView(this); btnDel.setText("✖"); btnDel.setTextColor(Color.RED); btnDel.setTextSize(32f); btnDel.setTypeface(null, Typeface.BOLD); btnDel.setGravity(android.view.Gravity.CENTER);
            btnDel.setOnClickListener(v -> { int idx = pdfPages.indexOf(item); if(idx!=-1){ pdfPages.remove(idx); item.ivRef=null; rebuildPagesDrawerUI(); if (currentPageIndex >= pdfPages.size()) currentPageIndex = Math.max(0, pdfPages.size() - 1); updatePageLabels(); renderMainPdfPage(); }});

            rightCol.addView(tv); rightCol.addView(btnDel);
            final long[] lastClick = {0}; row.setOnClickListener(v -> { long now = System.currentTimeMillis(); if(now - lastClick[0] < 300) { int target = pdfPages.indexOf(item); if(target != -1) { currentPageIndex = target; updatePageLabels(); renderMainPdfPage(); drawerLayout.closeDrawers(); } } lastClick[0] = now; });
            row.addView(iv); row.addView(rightCol); row.setOnLongClickListener(v -> { v.startDragAndDrop(ClipData.newPlainText("", ""), new View.DragShadowBuilder(v), v, 0); v.setVisibility(View.INVISIBLE); return true; });
            drawerPagesContainer.addView(row);
        }
    }

    private void updatePageLabels() {
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < drawerPagesContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) drawerPagesContainer.getChildAt(i);
            LinearLayout rightCol = (LinearLayout) row.getChildAt(1);
            TextView tv = (TextView) rightCol.getChildAt(0); tv.setText("Page " + (i + 1));
            GradientDrawable bg = createPill(colorPillBg);
            if (i == currentPageIndex) bg.setStroke((int)(4 * density), Color.parseColor("#5A9AF4"));
            row.setBackground(bg);
        }
    }
}