package com.abhinav.ownapp;

import android.annotation.SuppressLint; import android.content.Context; import android.content.SharedPreferences;
import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint; import android.graphics.Path;
import android.graphics.PixelFormat; import android.graphics.PointF; import android.graphics.drawable.GradientDrawable;
import android.os.Bundle; import android.view.MotionEvent; import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder; import android.view.SurfaceView; import android.view.View;
import android.widget.Button; import android.widget.EditText; import android.widget.FrameLayout;
import android.widget.LinearLayout; import android.widget.ScrollView; import android.widget.TextView; import android.widget.Toast;
import androidx.annotation.NonNull; import androidx.activity.EdgeToEdge; import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets; import androidx.core.view.ViewCompat; import androidx.core.view.WindowInsetsCompat;
import java.util.ArrayList; import java.util.HashSet; import java.util.List; import java.util.Set;

@SuppressWarnings("all")
public class SlateActivity extends AppCompatActivity {
    private boolean isDarkTheme, isMenuOpen = false, isZoomEnabled = true, isFieldZoomEnabled = false;
    private SharedPreferences prefs; private SlateCanvas slateCanvas; private LinearLayout flowerMenuLayout;
    private final String indicatorOn = " <font color='#34C759'>●</font>"; private final String indicatorOff = " <font color='#FF3B30'>●</font>";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- NEW: Lock the orientation to whatever state the app was launched in ---
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        EdgeToEdge.enable(this); setContentView(R.layout.activity_slate);
        View root = findViewById(R.id.slateRoot);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> { Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars()); v.setPadding(sys.left, sys.top, sys.right, sys.bottom); return WindowInsetsCompat.CONSUMED; });
        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE); isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
        slateCanvas = new SlateCanvas(this, isDarkTheme); ((FrameLayout) findViewById(R.id.slateContainer)).addView(slateCanvas);
        flowerMenuLayout = findViewById(R.id.flowerMenuLayout); TextView btnFlowerMenu = findViewById(R.id.btnFlowerMenu);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) { @Override public void handleOnBackPressed() { showExitConfirmationDialog(); } });
        btnFlowerMenu.setOnClickListener(v -> toggleMenu(!isMenuOpen, btnFlowerMenu));

        Button menuPen = findViewById(R.id.menuPen); Button menuEraser = findViewById(R.id.menuEraser);
        Button menuZoom = findViewById(R.id.menuZoom); Button menuFieldZoom = findViewById(R.id.menuFieldZoom);

        menuPen.setOnClickListener(v -> { slateCanvas.setToolMode(SlateCanvas.MODE_PEN); updateToolIndicators(menuPen, menuEraser); toggleMenu(false, btnFlowerMenu); });
        menuEraser.setOnClickListener(v -> { slateCanvas.setToolMode(SlateCanvas.MODE_ERASER); updateToolIndicators(menuPen, menuEraser); toggleMenu(false, btnFlowerMenu); });
        findViewById(R.id.menuClear).setOnClickListener(v -> { slateCanvas.clearCanvas(); toggleMenu(false, btnFlowerMenu); });
        findViewById(R.id.menuSave).setOnClickListener(v -> { toggleMenu(false, btnFlowerMenu); showSaveDraftDialog(); });
        findViewById(R.id.menuDrafts).setOnClickListener(v -> { toggleMenu(false, btnFlowerMenu); showDraftsGalleryDialog(); });
        findViewById(R.id.menuTheme).setOnClickListener(v -> { isDarkTheme = !isDarkTheme; slateCanvas.setDarkTheme(isDarkTheme); updateMenuTheme(); toggleMenu(false, btnFlowerMenu); });

        menuZoom.setOnClickListener(v -> {
            isZoomEnabled = !isZoomEnabled; slateCanvas.setZoomEnabled(isZoomEnabled);
            menuZoom.setText(isZoomEnabled ? "Zoom: ON" : "Zoom: OFF");
            menuZoom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(isZoomEnabled ? "#34C759" : "#FF3B30")));
            toggleMenu(false, btnFlowerMenu);
        });

        menuFieldZoom.setOnClickListener(v -> {
            isFieldZoomEnabled = !isFieldZoomEnabled; slateCanvas.setFieldZoomEnabled(isFieldZoomEnabled);
            menuFieldZoom.setText(isFieldZoomEnabled ? "Field Zoom: ON" : "Field Zoom: OFF");
            menuFieldZoom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(isFieldZoomEnabled ? "#34C759" : "#FF3B30")));
            toggleMenu(false, btnFlowerMenu);
        });

        findViewById(R.id.menuExit).setOnClickListener(v -> { toggleMenu(false, btnFlowerMenu); showExitConfirmationDialog(); });

        updateMenuTheme(); updateToolIndicators(menuPen, menuEraser);
        menuZoom.setText(isZoomEnabled ? "Zoom: ON" : "Zoom: OFF");
        menuZoom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(isZoomEnabled ? "#34C759" : "#FF3B30")));
        menuFieldZoom.setText(isFieldZoomEnabled ? "Field Zoom: ON" : "Field Zoom: OFF");
        menuFieldZoom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(isFieldZoomEnabled ? "#34C759" : "#FF3B30")));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("rot_draft", slateCanvas.serializeStrokes());
        outState.putFloat("rot_scale", slateCanvas.getScaleFactor());
        outState.putFloat("rot_tx", slateCanvas.getTranslateX());
        outState.putFloat("rot_ty", slateCanvas.getTranslateY());
        outState.putInt("rot_mode", slateCanvas.getCurrentMode());
        outState.putBoolean("rot_zoom", isZoomEnabled);
        outState.putBoolean("rot_inf", isFieldZoomEnabled);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        slateCanvas.loadStrokes(savedInstanceState.getString("rot_draft", ""));
        slateCanvas.setCameraState(savedInstanceState.getFloat("rot_scale", 1f), savedInstanceState.getFloat("rot_tx", 0f), savedInstanceState.getFloat("rot_ty", 0f));

        slateCanvas.setToolMode(savedInstanceState.getInt("rot_mode", SlateCanvas.MODE_PEN));
        isZoomEnabled = savedInstanceState.getBoolean("rot_zoom", true);
        isFieldZoomEnabled = savedInstanceState.getBoolean("rot_inf", false);
        slateCanvas.setZoomEnabled(isZoomEnabled); slateCanvas.setFieldZoomEnabled(isFieldZoomEnabled);

        updateToolIndicators(findViewById(R.id.menuPen), findViewById(R.id.menuEraser));
        Button mz = findViewById(R.id.menuZoom); mz.setText(isZoomEnabled ? "Zoom: ON" : "Zoom: OFF"); mz.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(isZoomEnabled ? "#34C759" : "#FF3B30")));
        Button mfz = findViewById(R.id.menuFieldZoom); mfz.setText(isFieldZoomEnabled ? "Field Zoom: ON" : "Field Zoom: OFF"); mfz.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(isFieldZoomEnabled ? "#34C759" : "#FF3B30")));
    }

    private void updateToolIndicators(Button penBtn, Button eraserBtn) {
        int mode = slateCanvas.getCurrentMode();
        penBtn.setText(android.text.Html.fromHtml("Pen tool" + ((mode == SlateCanvas.MODE_PEN) ? indicatorOn : indicatorOff), android.text.Html.FROM_HTML_MODE_LEGACY));
        eraserBtn.setText(android.text.Html.fromHtml("Eraser tool" + ((mode == SlateCanvas.MODE_ERASER) ? indicatorOn : indicatorOff), android.text.Html.FROM_HTML_MODE_LEGACY));
    }

    private void toggleMenu(boolean open, TextView btn) {
        if (isMenuOpen == open) return; isMenuOpen = open;

        btn.animate().scaleX(0.85f).scaleY(0.85f).setDuration(120).setInterpolator(new android.view.animation.DecelerateInterpolator()).withEndAction(() -> {
            btn.setText(open ? "Close" : "Menu");
            btn.animate().scaleX(1.10f).scaleY(1.10f).setDuration(150).setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).withEndAction(() -> {
                btn.animate().scaleX(1f).scaleY(1f).setDuration(350).setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
            }).start();
        }).start();

        if (open) { flowerMenuLayout.setVisibility(View.VISIBLE);
            for (int i = 0; i < flowerMenuLayout.getChildCount(); i++) { View c = flowerMenuLayout.getChildAt(i); c.setTranslationY(100f); c.setTranslationX(40f); c.setAlpha(0f); c.setScaleX(0.5f); c.setScaleY(0.5f);
                c.animate().translationY(0f).translationX(0f).alpha(1f).scaleX(1f).scaleY(1f).setDuration(350).setStartDelay((flowerMenuLayout.getChildCount() - i - 1) * 35L).setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start(); }
        } else {
            for (int i = 0; i < flowerMenuLayout.getChildCount(); i++) { View c = flowerMenuLayout.getChildAt(i);
                c.animate().translationY(100f).translationX(40f).alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(250).setStartDelay(i * 25L).setInterpolator(new android.view.animation.AnticipateInterpolator(1.2f)).withEndAction(() -> { if (c == flowerMenuLayout.getChildAt(flowerMenuLayout.getChildCount() - 1)) flowerMenuLayout.setVisibility(View.GONE); }).start(); }
        }
    }

    private void updateMenuTheme() {
        int btnColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.parseColor("#4A90E2");
        int textColor = isDarkTheme ? Color.WHITE : Color.BLACK;
        int[] btns = {R.id.menuPen, R.id.menuEraser, R.id.menuClear, R.id.menuSave, R.id.menuDrafts, R.id.menuTheme, R.id.menuExit};
        for(int id : btns) { Button b = findViewById(id); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(btnColor)); b.setTextColor(textColor); }
        ((Button)findViewById(R.id.menuZoom)).setTextColor(textColor); ((Button)findViewById(R.id.menuFieldZoom)).setTextColor(textColor);
    }

    private void saveImageToFolder(String name) {
        try { android.graphics.Bitmap bmp = slateCanvas.getBitmap(); android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name + ".png"); values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png"); values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/OWN's Slate Drafts");
            android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if(uri != null) { java.io.OutputStream out = getContentResolver().openOutputStream(uri); bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); out.close(); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void deleteImageFromFolder(String name) {
        try { android.content.ContentResolver r = getContentResolver(); android.net.Uri uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            android.database.Cursor c = r.query(uri, new String[]{android.provider.MediaStore.Images.Media._ID}, android.provider.MediaStore.Images.Media.DISPLAY_NAME + "=?", new String[]{name + ".png"}, null);
            if (c != null) { while (c.moveToNext()) { r.delete(android.content.ContentUris.withAppendedId(uri, c.getLong(c.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID))), null, null); } c.close(); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showExitConfirmationDialog() {
        android.app.Dialog d = new android.app.Dialog(this); d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE); d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(60,60,60,60); GradientDrawable gd = new GradientDrawable(); gd.setColor(isDarkTheme ? Color.parseColor("#E61C1C1E") : Color.parseColor("#E6F2F2F7")); gd.setCornerRadius(60f); root.setBackground(gd);
        TextView title = new TextView(this); title.setText("Exit Slate?"); title.setTextSize(20f); title.setTextColor(isDarkTheme?Color.WHITE:Color.BLACK); title.setTypeface(null, android.graphics.Typeface.BOLD); root.addView(title);
        TextView msg = new TextView(this); msg.setText("Are you sure you want to leave? Unsaved progress will be lost."); msg.setTextColor(isDarkTheme ? Color.parseColor("#CCCCCC") : Color.parseColor("#555555")); msg.setPadding(0,20,0,40); root.addView(msg);
        LinearLayout btnLayout = new LinearLayout(this); btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button btnNo = new Button(this); btnNo.setText("No"); GradientDrawable noBg = new GradientDrawable(); noBg.setColor(Color.GRAY); noBg.setCornerRadius(30f); btnNo.setBackground(noBg); btnNo.setTextColor(Color.WHITE); btnNo.setOnClickListener(v->d.dismiss());
        Button btnYes = new Button(this); btnYes.setText("Yes"); GradientDrawable yesBg = new GradientDrawable(); yesBg.setColor(Color.parseColor("#FF3B30")); yesBg.setCornerRadius(30f); btnYes.setBackground(yesBg); btnYes.setTextColor(Color.WHITE); btnYes.setOnClickListener(v->{ d.dismiss(); finish(); });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); p.setMargins(10,0,10,0); btnLayout.addView(btnNo, p); btnLayout.addView(btnYes, p); root.addView(btnLayout); d.setContentView(root); d.show();
    }

    private void showSaveDraftDialog() {
        android.app.Dialog d = new android.app.Dialog(this); d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE); d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(60,60,60,60); GradientDrawable gd = new GradientDrawable(); gd.setColor(isDarkTheme ? Color.parseColor("#E61C1C1E") : Color.parseColor("#E6F2F2F7")); gd.setCornerRadius(60f); root.setBackground(gd);
        TextView title = new TextView(this); title.setText("Save Vector Draft"); title.setTextSize(20f); title.setTextColor(isDarkTheme?Color.WHITE:Color.BLACK); title.setTypeface(null, android.graphics.Typeface.BOLD); root.addView(title);
        EditText input = new EditText(this); input.setHint("Enter draft name..."); input.setHintTextColor(Color.GRAY); input.setTextColor(isDarkTheme?Color.WHITE:Color.BLACK);
        GradientDrawable inputBg = new GradientDrawable(); inputBg.setColor(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#FFFFFF")); inputBg.setCornerRadius(20f); input.setBackground(inputBg); input.setPadding(30,30,30,30);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); ip.setMargins(0,30,0,30); root.addView(input, ip);
        LinearLayout btnLayout = new LinearLayout(this); btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button btnCancel = new Button(this); btnCancel.setText("Cancel"); GradientDrawable cBg = new GradientDrawable(); cBg.setColor(Color.GRAY); cBg.setCornerRadius(30f); btnCancel.setBackground(cBg); btnCancel.setTextColor(Color.WHITE); btnCancel.setOnClickListener(v->d.dismiss());
        Button btnSave = new Button(this); btnSave.setText("Save"); GradientDrawable sBg = new GradientDrawable(); sBg.setColor(Color.parseColor("#4A90E2")); sBg.setCornerRadius(30f); btnSave.setBackground(sBg); btnSave.setTextColor(Color.WHITE);
        btnSave.setOnClickListener(v->{ String n = input.getText().toString().trim(); if(n.isEmpty()) n = "Draft " + System.currentTimeMillis(); prefs.edit().putString("slate_draft_"+n, slateCanvas.serializeStrokes()).apply(); Set<String> s = new HashSet<>(prefs.getStringSet("slate_draft_names", new HashSet<>())); s.add(n); prefs.edit().putStringSet("slate_draft_names", s).apply(); saveImageToFolder(n); Toast.makeText(this,"Saved to App & Gallery!",Toast.LENGTH_SHORT).show(); d.dismiss(); });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); p.setMargins(10,0,10,0); btnLayout.addView(btnCancel, p); btnLayout.addView(btnSave, p); root.addView(btnLayout); d.setContentView(root); d.show();
    }

    private void showDraftsGalleryDialog() {
        android.app.Dialog d = new android.app.Dialog(this); d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE); d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(60,60,60,60); GradientDrawable gd = new GradientDrawable(); gd.setColor(isDarkTheme ? Color.parseColor("#E61C1C1E") : Color.parseColor("#E6F2F2F7")); gd.setCornerRadius(60f); root.setBackground(gd);
        TextView title = new TextView(this); title.setText("Drafts Gallery"); title.setTextSize(20f); title.setTextColor(isDarkTheme?Color.WHITE:Color.BLACK); title.setTypeface(null, android.graphics.Typeface.BOLD); title.setPadding(0,0,0,40); root.addView(title);
        ScrollView sv = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); Set<String> names = new HashSet<>(prefs.getStringSet("slate_draft_names", new HashSet<>()));
        if(names.isEmpty()){ TextView empty = new TextView(this); empty.setText("No drafts saved."); empty.setTextColor(Color.GRAY); list.addView(empty); }
        else { for(String n : names){ LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); rowLp.setMargins(0,0,0,20);
            Button b = new Button(this); b.setText(n); GradientDrawable bBg = new GradientDrawable(); bBg.setColor(isDarkTheme?Color.parseColor("#3A3A3C"):Color.parseColor("#FFFFFF")); bBg.setCornerRadius(30f); b.setBackground(bBg); b.setTextColor(isDarkTheme?Color.WHITE:Color.BLACK);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); b.setOnClickListener(v->{ slateCanvas.loadStrokes(prefs.getString("slate_draft_"+n,"")); Toast.makeText(this,"Loaded "+n,Toast.LENGTH_SHORT).show(); d.dismiss(); });
            Button del = new Button(this); del.setText("X"); GradientDrawable dBg = new GradientDrawable(); dBg.setColor(Color.parseColor("#FF3B30")); dBg.setCornerRadius(30f); del.setBackground(dBg); del.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT); dLp.setMargins(20,0,0,0);
            del.setOnClickListener(v->{ prefs.edit().remove("slate_draft_"+n).apply(); Set<String> updated = new HashSet<>(prefs.getStringSet("slate_draft_names", new HashSet<>())); updated.remove(n); prefs.edit().putStringSet("slate_draft_names", updated).apply(); list.removeView(row); deleteImageFromFolder(n); if(updated.isEmpty()){ TextView empty = new TextView(this); empty.setText("No drafts saved."); empty.setTextColor(Color.GRAY); list.addView(empty); } Toast.makeText(this,"Draft Deleted",Toast.LENGTH_SHORT).show(); });
            row.addView(b, lp); row.addView(del, dLp); list.addView(row, rowLp); } }
        sv.addView(list); root.addView(sv); Button btnClose = new Button(this); btnClose.setText("Close"); GradientDrawable cBg = new GradientDrawable(); cBg.setColor(Color.parseColor("#FF3B30")); cBg.setCornerRadius(30f); btnClose.setBackground(cBg); btnClose.setTextColor(Color.WHITE); btnClose.setOnClickListener(v->d.dismiss());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); clp.setMargins(0,40,0,0); root.addView(btnClose, clp); d.setContentView(root); d.show();
    }

    @Override protected void onPause() { super.onPause(); if (slateCanvas != null) slateCanvas.pauseRendering(); }
    @Override protected void onResume() { super.onResume(); if (slateCanvas != null) slateCanvas.resumeRendering(); }

    private static class SlateCanvas extends SurfaceView implements SurfaceHolder.Callback, Runnable {
        public static final int MODE_PEN = 0; public static final int MODE_ERASER = 1; private int currentMode = MODE_PEN;
        private Thread renderThread; private volatile boolean isRendering = false; private final SurfaceHolder holder;
        private boolean darkTheme, isZoomEnabled = true, isFieldZoomEnabled = false; private int bgColor, gridColor, penColor;
        private final android.graphics.Matrix tMatrix = new android.graphics.Matrix(), iMatrix = new android.graphics.Matrix();
        private float scaleFactor = 1.0f, translateX = 0f, translateY = 0f, lastTouchX, lastTouchY;
        private final ScaleGestureDetector scaleDetector;

        private final List<Stroke> strokes = new ArrayList<>(); private Stroke currentStroke; private Paint currentPaint;
        private final Object paintLock = new Object();
        private float previousX, previousY; private static final float TOUCH_TOLERANCE = 1f;

        public SlateCanvas(Context context, boolean isDarkTheme) { super(context); holder = getHolder(); holder.addCallback(this); setZOrderOnTop(false); holder.setFormat(PixelFormat.OPAQUE); setDarkTheme(isDarkTheme); scaleDetector = new ScaleGestureDetector(context, new ScaleListener()); setupNewPaint(); }
        public void setZoomEnabled(boolean e) { this.isZoomEnabled = e; }

        public float getScaleFactor() { return scaleFactor; }
        public float getTranslateX() { return translateX; }
        public float getTranslateY() { return translateY; }
        public void setCameraState(float s, float tx, float ty) { this.scaleFactor = s; this.translateX = tx; this.translateY = ty; updateMatrix(); }

        public void setFieldZoomEnabled(boolean e) {
            this.isFieldZoomEnabled = e;
            if (!e) {
                boolean changed = false;
                if (scaleFactor < 0.5f) { scaleFactor = 0.5f; changed = true; }
                if (scaleFactor > 5.0f) { scaleFactor = 5.0f; changed = true; }
                if (changed) updateMatrix();
            }
        }

        public void setDarkTheme(boolean dark) {
            this.darkTheme = dark; bgColor = dark ? Color.parseColor("#1C1C1E") : Color.parseColor("#FFFFFF");
            gridColor = dark ? Color.parseColor("#333333") : Color.parseColor("#E0E0E0"); penColor = dark ? Color.WHITE : Color.BLACK; setupNewPaint();
            synchronized(paintLock) { for(Stroke s : strokes) { if(!s.isEraser) { s.color = penColor; s.paint.setColor(penColor); } } }
        }

        public void setToolMode(int mode) { this.currentMode = mode; setupNewPaint(); }
        public int getCurrentMode() { return this.currentMode; }
        public void clearCanvas() { synchronized(paintLock) { strokes.clear(); } }

        private void setupNewPaint() {
            currentPaint = new Paint(); currentPaint.setAntiAlias(true); currentPaint.setDither(true); currentPaint.setStyle(Paint.Style.STROKE); currentPaint.setStrokeJoin(Paint.Join.ROUND); currentPaint.setStrokeCap(Paint.Cap.ROUND);
            if (currentMode == MODE_PEN) {
                currentPaint.setColor(penColor); currentPaint.setStrokeWidth(6f); currentPaint.setXfermode(null);
            } else {
                currentPaint.setColor(Color.TRANSPARENT); currentPaint.setStrokeWidth(40f); currentPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
            }
        }

        public String serializeStrokes() {
            StringBuilder sb = new StringBuilder();
            synchronized(paintLock) {
                for(Stroke s : strokes) { sb.append(s.isEraser?"1":"0").append(":").append(s.color).append(":").append(s.strokeWidth).append(":"); for(int i=0; i<s.points.size(); i++){ sb.append(s.points.get(i).x).append(",").append(s.points.get(i).y); if(i<s.points.size()-1) sb.append(";"); } sb.append("|"); }
            } return sb.toString();
        }

        public void loadStrokes(String data) {
            synchronized(paintLock) {
                strokes.clear(); if(data==null||data.isEmpty()) return; String[] raw = data.split("\\|");
                for(String r : raw) {
                    if(r.isEmpty()) continue; String[] p = r.split(":"); if(p.length<4) continue;
                    boolean isEraser = p[0].equals("1");
                    Stroke s = new Stroke(isEraser, isEraser ? Color.TRANSPARENT : penColor, Float.parseFloat(p[2]));
                    for(String pt : p[3].split(";")){ String[] c = pt.split(","); if(c.length==2) s.points.add(new PointF(Float.parseFloat(c[0]), Float.parseFloat(c[1]))); }
                    s.rebuildPath(); strokes.add(s);
                }
            }
        }

        public android.graphics.Bitmap getBitmap() {
            if (getWidth() <= 0 || getHeight() <= 0) return null;
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(getWidth(), getHeight(), android.graphics.Bitmap.Config.ARGB_8888); Canvas c = new Canvas(bmp); c.drawColor(bgColor);
            c.save(); c.concat(tMatrix); Paint gp = new Paint(); gp.setColor(gridColor);

            float currentScale = isFieldZoomEnabled ? scaleFactor : 1.0f;
            float sp = isFieldZoomEnabled ? (50f / scaleFactor) : 50f;
            while (sp * scaleFactor < 25f) sp *= 2f;
            float dotRadius = isFieldZoomEnabled ? (2f / scaleFactor) : 2f;
            if (dotRadius * scaleFactor < 0.5f) dotRadius = 0.5f / scaleFactor;

            float[] tl = {0, 0}, br = {getWidth(), getHeight()}; iMatrix.mapPoints(tl); iMatrix.mapPoints(br);
            int sx = (int)(tl[0] - (tl[0]%sp)), sy = (int)(tl[1] - (tl[1]%sp));
            for (float x=sx; x<br[0]; x+=sp) { for (float y=sy; y<br[1]; y+=sp) { c.drawCircle(x, y, dotRadius, gp); } }

            float left = Math.min(tl[0], br[0]), top = Math.min(tl[1], br[1]), right = Math.max(tl[0], br[0]), bottom = Math.max(tl[1], br[1]);
            int layer = c.saveLayer(left, top, right, bottom, null);
            synchronized(paintLock) {
                for (Stroke s : strokes) { s.paint.setStrokeWidth(s.strokeWidth / currentScale); c.drawPath(s.path, s.paint); }
                if (currentStroke != null) { currentPaint.setStrokeWidth(currentStroke.strokeWidth / currentScale); c.drawPath(currentStroke.path, currentPaint); }
            }
            c.restoreToCount(layer);
            c.restore(); return bmp;
        }

        public void pauseRendering() { isRendering = false; try { if (renderThread != null) { renderThread.join(); renderThread = null; } } catch (Exception ignored) {} }
        public void resumeRendering() { isRendering = true; renderThread = new Thread(this); renderThread.start(); }
        @Override public void surfaceCreated(@NonNull SurfaceHolder h) { resumeRendering(); } @Override public void surfaceChanged(@NonNull SurfaceHolder h, int f, int w, int ht) {} @Override public void surfaceDestroyed(@NonNull SurfaceHolder h) { pauseRendering(); }

        @Override public void run() {
            while (isRendering) {
                if (!holder.getSurface().isValid()) continue;
                Canvas c = holder.lockCanvas();
                if (c != null) {
                    synchronized (holder) { drawCanvas(c); }
                    holder.unlockCanvasAndPost(c);
                }
            }
        }

        private void drawCanvas(Canvas c) {
            c.drawColor(bgColor); c.save(); c.concat(tMatrix); Paint gp = new Paint(); gp.setColor(gridColor);

            float currentScale = isFieldZoomEnabled ? scaleFactor : 1.0f;
            float sp = isFieldZoomEnabled ? (50f / scaleFactor) : 50f;
            while (sp * scaleFactor < 25f) sp *= 2f;
            float dotRadius = isFieldZoomEnabled ? (2f / scaleFactor) : 2f;
            if (dotRadius * scaleFactor < 0.5f) dotRadius = 0.5f / scaleFactor;

            float[] tl = {0, 0}, br = {getWidth(), getHeight()}; iMatrix.mapPoints(tl); iMatrix.mapPoints(br);
            int sx = (int)(tl[0] - (tl[0]%sp)), sy = (int)(tl[1] - (tl[1]%sp));
            for (float x=sx; x<br[0]; x+=sp) { for (float y=sy; y<br[1]; y+=sp) { c.drawCircle(x, y, dotRadius, gp); } }

            float left = Math.min(tl[0], br[0]), top = Math.min(tl[1], br[1]), right = Math.max(tl[0], br[0]), bottom = Math.max(tl[1], br[1]);
            int layer = c.saveLayer(left, top, right, bottom, null);
            synchronized(paintLock) {
                for (Stroke s : strokes) { s.paint.setStrokeWidth(s.strokeWidth / currentScale); c.drawPath(s.path, s.paint); }
                if (currentStroke != null) { currentPaint.setStrokeWidth(currentStroke.strokeWidth / currentScale); c.drawPath(currentStroke.path, currentPaint); }
            }
            c.restoreToCount(layer);
            c.restore();
        }

        @SuppressLint("ClickableViewAccessibility") @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getPointerCount() > 1) {
                if (!isZoomEnabled) return true;
                scaleDetector.onTouchEvent(e);
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float fx = scaleDetector.getFocusX(), fy = scaleDetector.getFocusY();
                    translateX += (fx - lastTouchX); translateY += (fy - lastTouchY);
                    updateMatrix(); lastTouchX = fx; lastTouchY = fy;
                } else if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                    lastTouchX = scaleDetector.getFocusX(); lastTouchY = scaleDetector.getFocusY();
                    synchronized(paintLock) { currentStroke = null; }
                } return true;
            }

            float[] pts = {e.getX(), e.getY()}; iMatrix.mapPoints(pts); float cx = pts[0], cy = pts[1];
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    synchronized(paintLock) {
                        float baseWidth = currentMode == MODE_PEN ? 6f : 40f;
                        currentStroke = new Stroke(currentMode==MODE_ERASER, currentPaint.getColor(), baseWidth);
                        currentStroke.points.add(new PointF(cx, cy)); currentStroke.path.moveTo(cx, cy);
                        previousX = cx; previousY = cy;
                    } break;
                case MotionEvent.ACTION_MOVE:
                    if (currentStroke == null) break;
                    synchronized(paintLock) {
                        for (int i=0; i<e.getHistorySize(); i++) { float[] hPts = {e.getHistoricalX(i), e.getHistoricalY(i)}; iMatrix.mapPoints(hPts); processTouch(hPts[0], hPts[1]); }
                        processTouch(cx, cy);
                    } break;
                case MotionEvent.ACTION_UP:
                    synchronized(paintLock) {
                        if (currentStroke != null) {
                            currentStroke.path.lineTo(cx, cy); currentStroke.points.add(new PointF(cx, cy));
                            strokes.add(currentStroke); currentStroke = null;
                        }
                    } break;
            } return true;
        }

        private void processTouch(float x, float y) {
            if (Math.abs(x - previousX) >= TOUCH_TOLERANCE || Math.abs(y - previousY) >= TOUCH_TOLERANCE) {
                currentStroke.points.add(new PointF(x, y)); currentStroke.path.quadTo(previousX, previousY, (x + previousX)/2, (y + previousY)/2);
                previousX = x; previousY = y;
            }
        }

        private void updateMatrix() { tMatrix.reset(); tMatrix.postScale(scaleFactor, scaleFactor); tMatrix.postTranslate(translateX, translateY); tMatrix.invert(iMatrix); }

        private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float prevScale = scaleFactor;
                float minZoom = isFieldZoomEnabled ? 0.001f : 0.5f;
                float maxZoom = 5.0f;
                scaleFactor = Math.max(minZoom, Math.min(scaleFactor * d.getScaleFactor(), maxZoom));
                float adjusted = scaleFactor / prevScale; float fx = d.getFocusX(), fy = d.getFocusY();
                translateX = fx - (fx - translateX) * adjusted; translateY = fy - (fy - translateY) * adjusted; updateMatrix(); return true;
            }
        }

        private static class Stroke { List<PointF> points = new ArrayList<>(); boolean isEraser; int color; float strokeWidth; Path path = new Path(); Paint paint = new Paint(); Stroke(boolean e, int c, float w) { isEraser=e; color=c; strokeWidth=w; paint.setAntiAlias(true); paint.setStyle(Paint.Style.STROKE); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStrokeCap(Paint.Cap.ROUND); paint.setColor(c); paint.setStrokeWidth(w); if(isEraser) paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)); } void rebuildPath() { path.reset(); if(points.isEmpty()) return; float px=points.get(0).x, py=points.get(0).y; path.moveTo(px, py); for(int i=1; i<points.size(); i++) { PointF p=points.get(i); path.quadTo(px, py, (p.x+px)/2, (p.y+py)/2); px=p.x; py=p.y; } path.lineTo(px, py); } }
    }
}