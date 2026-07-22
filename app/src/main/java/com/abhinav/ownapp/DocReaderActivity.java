package com.abhinav.ownapp;

import android.annotation.SuppressLint; import android.content.Context; import android.content.Intent; import android.content.SharedPreferences; import android.database.Cursor; import android.graphics.Bitmap; import android.graphics.BitmapFactory; import android.graphics.Color; import android.graphics.Typeface; import android.graphics.drawable.GradientDrawable; import android.net.Uri; import android.os.Build; import android.os.Bundle; import android.os.Handler; import android.os.Looper; import android.provider.OpenableColumns; import android.text.Editable; import android.text.Spanned;
import android.text.SpannableStringBuilder; import android.text.TextWatcher; import android.text.method.LinkMovementMethod; import android.text.style.AlignmentSpan; import android.text.style.BackgroundColorSpan; import android.text.style.BulletSpan; import android.text.style.ForegroundColorSpan; import android.text.style.ImageSpan; import android.text.style.RelativeSizeSpan; import android.text.style.StyleSpan; import android.text.style.URLSpan; import android.text.style.UnderlineSpan;
import android.view.View; import android.view.ViewGroup; import android.view.Window; import android.view.WindowManager; import android.view.inputmethod.InputMethodManager; import android.webkit.WebView; import android.widget.Button; import android.widget.EditText; import android.widget.FrameLayout; import android.widget.LinearLayout; import android.widget.ScrollView; import android.widget.TextView; import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher; import androidx.activity.result.contract.ActivityResultContracts; import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader; import java.io.InputStream; import java.io.InputStreamReader; import java.nio.charset.StandardCharsets; import java.util.ArrayList; import java.util.List; import java.util.zip.ZipEntry; import java.util.zip.ZipInputStream;

@SuppressWarnings("all") @SuppressLint("ClickableViewAccessibility")
public class DocReaderActivity extends AppCompatActivity {
    private TextView tvDocContent, tvDocTitle, tvSearchCount; private ScrollView scrollDocContent; private LinearLayout docPanel, searchBarContainer; private EditText etSearch;
    private ActivityResultLauncher<String[]> filePickerLauncher; private String rawDocText = "", lowerDocText = ""; private final List<Integer> matchOffsets = new ArrayList<>(); private int currentMatchIdx = -1;
    private static final int MAX_VISUAL_SPANS = 400; private static final int MAX_TOTAL_MATCHES = 2500;
    private final Handler searchHandler = new Handler(Looper.getMainLooper()); private Runnable searchRunnable;
    private SpannableStringBuilder finalSpannableDoc = new SpannableStringBuilder();
    private WebView wvDocContent; private String finalHtmlDoc = ""; private boolean isPdfModeActive = false;
    private FrameLayout fastScrollTrack;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_doc_reader);
        applyStatusBarTheme();

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE); boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7"); int panelColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE; int textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#1C1C1E");

        LinearLayout root = findViewById(R.id.docReaderRoot); docPanel = findViewById(R.id.docPanel); scrollDocContent = findViewById(R.id.scrollDocContent);
        tvDocTitle = findViewById(R.id.tvDocTitle); tvDocContent = findViewById(R.id.tvDocContent); searchBarContainer = findViewById(R.id.searchBarContainer); etSearch = findViewById(R.id.etSearch);
        tvSearchCount = findViewById(R.id.tvSearchCount); Button btnNextMatch = findViewById(R.id.btnNextMatch); Button btnCloseSearch = findViewById(R.id.btnCloseSearch);
        View fastScrollThumb = findViewById(R.id.fastScrollThumb); fastScrollTrack = findViewById(R.id.fastScrollTrack);
        wvDocContent = findViewById(R.id.wvDocContent); TextView btnMenu = findViewById(R.id.btnMenu);

        root.setBackgroundColor(bgColor); tvDocTitle.setTextColor(textColor); tvDocContent.setTextColor(textColor); etSearch.setTextColor(textColor); btnMenu.setTextColor(textColor);
        tvDocContent.setMovementMethod(LinkMovementMethod.getInstance());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { tvDocContent.setJustificationMode(android.text.Layout.JUSTIFICATION_MODE_INTER_WORD); }

        GradientDrawable gd = new GradientDrawable(); gd.setColor(panelColor); gd.setCornerRadius(30f); docPanel.setBackground(gd);
        GradientDrawable searchGd = new GradientDrawable(); searchGd.setColor(panelColor); searchGd.setCornerRadius(50f); searchGd.setStroke(2, Color.parseColor("#4A90E2")); searchBarContainer.setBackground(searchGd);
        GradientDrawable thumbGd = new GradientDrawable(); thumbGd.setColor(isDarkTheme ? Color.parseColor("#4A90E2") : Color.parseColor("#007AFF")); thumbGd.setCornerRadius(20f); fastScrollThumb.setBackground(thumbGd);
        GradientDrawable menuBtnGd = new GradientDrawable(); menuBtnGd.setColor(panelColor); menuBtnGd.setShape(GradientDrawable.OVAL); btnMenu.setBackground(menuBtnGd);

        wvDocContent.getSettings().setJavaScriptEnabled(true); wvDocContent.getSettings().setBuiltInZoomControls(true); wvDocContent.getSettings().setDisplayZoomControls(false); wvDocContent.getSettings().setSupportZoom(true); wvDocContent.getSettings().setUseWideViewPort(true); wvDocContent.getSettings().setLoadWithOverviewMode(true); wvDocContent.setBackgroundColor(Color.parseColor("#525659"));

        btnMenu.setOnClickListener(v -> showGlassmorphicMenu(btnMenu));

        btnCloseSearch.setOnClickListener(v -> { if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable); searchBarContainer.setVisibility(View.GONE); etSearch.setText(""); tvDocContent.setText(finalSpannableDoc); if(isPdfModeActive) wvDocContent.clearMatches(); InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0); });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable); if (s.toString().isEmpty()) performSearch(""); else { searchRunnable = () -> performSearch(s.toString()); searchHandler.postDelayed(searchRunnable, 350); } }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnNextMatch.setOnClickListener(v -> { if (isPdfModeActive) { wvDocContent.findNext(true); return; } if (!matchOffsets.isEmpty()) { currentMatchIdx = (currentMatchIdx + 1) % matchOffsets.size(); jumpToMatch(currentMatchIdx, etSearch.getText().toString().length()); } });

        fastScrollTrack.setOnTouchListener((v, event) -> { int trackH = fastScrollTrack.getHeight() - fastScrollThumb.getHeight(); if (trackH <= 0) return false; float y = Math.max(0, Math.min(trackH, event.getY() - (fastScrollThumb.getHeight() / 2f))); int maxScroll = docPanel.getHeight() - scrollDocContent.getHeight(); if (maxScroll > 0) scrollDocContent.scrollTo(0, (int) ((y / (float) trackH) * maxScroll)); return true; });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { scrollDocContent.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> { int maxScroll = docPanel.getHeight() - scrollDocContent.getHeight(); int trackH = fastScrollTrack.getHeight() - fastScrollThumb.getHeight(); if (maxScroll > 0 && trackH > 0) fastScrollThumb.setTranslationY(Math.max(0f, Math.min(1f, (float) scrollY / maxScroll)) * trackH); }); }

        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) readDocumentUri(uri); });
        Intent intent = getIntent(); if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) readDocumentUri(intent.getData());
    }

    @Override protected void onResume() { super.onResume(); applyStatusBarTheme(); }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) applyStatusBarTheme(); }

    /* FIX: Removed Export/Print PDF option from the menu entirely! */
    private void showGlassmorphicMenu(View anchor) {
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE); boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
        int popupBgColor = isDarkTheme ? Color.parseColor("#E61C1C1E") : Color.parseColor("#F2FFFFFF"); int popupTextColor = isDarkTheme ? Color.WHITE : Color.parseColor("#1C1C1E"); int divColor = isDarkTheme ? Color.parseColor("#33FFFFFF") : Color.parseColor("#1A000000");
        LinearLayout menuLayout = new LinearLayout(this); menuLayout.setOrientation(LinearLayout.VERTICAL); menuLayout.setPadding(16, 16, 16, 16);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(popupBgColor); bg.setCornerRadius(50f); if (!isDarkTheme) bg.setStroke(2, Color.parseColor("#D1D1D6")); menuLayout.setBackground(bg);
        android.widget.PopupWindow popup = new android.widget.PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true); popup.setElevation(40f); popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        String[] titles = {"📂 Open File", "🔍 Search Text", isPdfModeActive ? "📄 Switch to Text View" : "🖨️ Switch to PDF View"};
        for (int i = 0; i < titles.length; i++) {
            TextView tv = new TextView(this); tv.setText(titles[i]); tv.setTextColor(popupTextColor); tv.setTextSize(15f); tv.setPadding(40, 35, 40, 35); tv.setTypeface(null, Typeface.BOLD);
            final int idx = i; tv.setOnClickListener(v -> {
                popup.dismiss();
                if (idx == 0) filePickerLauncher.launch(new String[]{"*/*"});
                else if (idx == 1) { searchBarContainer.setVisibility(View.VISIBLE); etSearch.requestFocus(); InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); if (imm != null) imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT); }
                else if (idx == 2) { isPdfModeActive = !isPdfModeActive; if (isPdfModeActive) { scrollDocContent.setVisibility(View.GONE); wvDocContent.setVisibility(View.VISIBLE); fastScrollTrack.setVisibility(View.GONE); if (!finalHtmlDoc.isEmpty()) wvDocContent.loadDataWithBaseURL(null, finalHtmlDoc, "text/html", "UTF-8", null); Toast.makeText(getApplicationContext(), "PDF VIEW ENABLED", Toast.LENGTH_SHORT).show(); } else { wvDocContent.setVisibility(View.GONE); scrollDocContent.setVisibility(View.VISIBLE); fastScrollTrack.setVisibility(View.VISIBLE); Toast.makeText(getApplicationContext(), "TEXT VIEW ENABLED", Toast.LENGTH_SHORT).show(); } }
            });
            menuLayout.addView(tv); if (i < titles.length - 1) { View div = new View(this); div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2)); div.setBackgroundColor(divColor); menuLayout.addView(div); }
        }
        popup.showAsDropDown(anchor, 0, 20, android.view.Gravity.END);
    }

    private void applyStatusBarTheme() {
        try {
            SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE); boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7"); Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS); window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(bgColor); window.setNavigationBarColor(bgColor); window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bgColor));
            View decor = window.getDecorView(); int flags = decor.getSystemUiVisibility();
            if (!isDarkTheme) { flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; }
            else { flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; }
            decor.setSystemUiVisibility(flags);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { android.view.WindowInsetsController wic = window.getInsetsController(); if (wic != null) { int lightAppearance = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS; wic.setSystemBarsAppearance(!isDarkTheme ? lightAppearance : 0, lightAppearance); } }
        } catch (Throwable ignored) {}
    }

    private void performSearch(String query) {
        try { if (isPdfModeActive) { wvDocContent.findAllAsync(query); return; } matchOffsets.clear(); currentMatchIdx = -1; if (query.isEmpty() || rawDocText.isEmpty()) { tvDocContent.setText(finalSpannableDoc); tvSearchCount.setText("0/0"); return; } String lowerQ = query.toLowerCase(); int index = lowerDocText.indexOf(lowerQ); while (index >= 0 && matchOffsets.size() < MAX_TOTAL_MATCHES) { matchOffsets.add(index); index = lowerDocText.indexOf(lowerQ, index + query.length()); } if (matchOffsets.isEmpty()) { tvDocContent.setText(finalSpannableDoc); tvSearchCount.setText("0/0"); return; } SpannableStringBuilder spannable = new SpannableStringBuilder(finalSpannableDoc); int spanLimit = Math.min(matchOffsets.size(), MAX_VISUAL_SPANS); for (int i = 0; i < spanLimit; i++) { int offset = matchOffsets.get(i); spannable.setSpan(new BackgroundColorSpan(Color.parseColor("#FF9500")), offset, offset + query.length(), 0); } tvDocContent.setText(spannable); currentMatchIdx = 0; jumpToMatch(0, query.length()); } catch (Throwable t) { Toast.makeText(getApplicationContext(), "Search limited to prevent memory crash!", Toast.LENGTH_SHORT).show(); tvDocContent.setText(finalSpannableDoc); tvSearchCount.setText("0/0"); }
    }

    private void jumpToMatch(int idx, int len) {
        try { if (matchOffsets.isEmpty() || idx >= matchOffsets.size()) return; tvSearchCount.setText((idx + 1) + "/" + matchOffsets.size() + (matchOffsets.size() == MAX_TOTAL_MATCHES ? "+" : "")); tvDocContent.post(() -> { try { if (tvDocContent.getLayout() != null) { int line = tvDocContent.getLayout().getLineForOffset(matchOffsets.get(idx)); scrollDocContent.scrollTo(0, Math.max(0, tvDocContent.getLayout().getLineTop(line) - 150)); } } catch (Throwable ignored) {} }); } catch (Throwable ignored) {}
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqW, int reqH) { final int height = options.outHeight; final int width = options.outWidth; int inSampleSize = 1; if (height > reqH || width > reqW) { final int halfH = height / 2; final int halfW = width / 2; while ((halfH / inSampleSize) >= reqH || (halfW / inSampleSize) >= reqW) inSampleSize *= 2; } return inSampleSize; }

    private void readDocumentUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri); if (is == null) return;
            String fileName = "Document Loaded";
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) { if (cursor != null && cursor.moveToFirst()) { int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (idx != -1) fileName = cursor.getString(idx); } } catch (Exception ignored) {}
            if ("Document Loaded".equals(fileName)) { try { String path = uri.getPath(); if (path != null && path.lastIndexOf('/') != -1) fileName = path.substring(path.lastIndexOf('/') + 1); } catch (Exception ignored) {} }
            tvDocTitle.setText(fileName);

            String lowerName = fileName.toLowerCase();
            if (lowerName.endsWith(".pdf") || lowerName.endsWith(".ppt") || lowerName.endsWith(".pptx") || lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") || lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif") || lowerName.endsWith(".mp3") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") || lowerName.endsWith(".apk") || lowerName.endsWith(".exe") || lowerName.endsWith(".bin")) {
                rawDocText = "File Not Supported.\n\nOWN Doc Reader is designed for Word documents (.doc, .docx) and plain text files.\n\nThe file you selected (" + fileName + ") is not a supported document format.";
                lowerDocText = rawDocText.toLowerCase(); finalSpannableDoc = new SpannableStringBuilder(rawDocText); tvDocContent.setText(finalSpannableDoc); finalHtmlDoc = ""; wvDocContent.loadDataWithBaseURL(null, "", "text/html", "UTF-8", null); is.close(); return;
            }

            StringBuilder extractedText = new StringBuilder(); boolean isDocx = false;
            StringBuilder docXmlBuilder = new StringBuilder(); StringBuilder relsXmlBuilder = new StringBuilder();
            java.util.Map<String, String> base64MediaMap = new java.util.HashMap<>();
            java.util.Map<String, Bitmap> mediaMap = new java.util.HashMap<>();

            try (ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if ("word/document.xml".equals(name)) { isDocx = true; BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8)); String line; while ((line = reader.readLine()) != null) docXmlBuilder.append(line);
                    } else if ("word/_rels/document.xml.rels".equals(name)) { BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8)); String line; while ((line = reader.readLine()) != null) relsXmlBuilder.append(line);
                    } else if (name.startsWith("word/media/")) {
                        try {
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count; while ((count = zis.read(buffer)) != -1) baos.write(buffer, 0, count); byte[] bytes = baos.toByteArray();
                            base64MediaMap.put(name, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP));
                            BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inJustDecodeBounds = true; BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                            opts.inSampleSize = calculateInSampleSize(opts, 1200, 1200); opts.inJustDecodeBounds = false; Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts); if (bmp != null) mediaMap.put(name, bmp);
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            String docXml = docXmlBuilder.toString();
            if (isDocx && !docXml.isEmpty()) {
                java.util.Map<String, String> relMap = new java.util.HashMap<>();
                java.util.regex.Matcher mRel = java.util.regex.Pattern.compile("Id=\"(rId\\d+)\"[^>]*Target=\"([^\"]+)\"").matcher(relsXmlBuilder.toString());
                while (mRel.find()) { String rId = mRel.group(1); String target = mRel.group(2); if (target.startsWith("/")) target = target.substring(1); if (!target.startsWith("word/")) target = "word/" + target; relMap.put(rId, target); }

                StringBuilder html = new StringBuilder();
                html.append("<html><head><meta name=\"viewport\" content=\"width=900, user-scalable=yes\">");
                html.append("<style>body { background: #525659; margin: 0; padding: 24px; font-family: 'Times New Roman', serif; text-align: center; display: block; } ");
                html.append(".a4-page { background: #FFFFFF; width: 794px; min-height: 1123px; padding: 72px; box-shadow: 0 8px 16px rgba(0,0,0,0.6); box-sizing: border-box; color: #000000; line-height: 1.6; word-wrap: break-word; overflow: visible; margin: 0 auto; text-align: left; } ");
                html.append("p { margin: 6px 0; } img { max-width: 100%; height: auto; display: block; margin: 10px 0; } a { color: #4A90E2; text-decoration: none; } ");
                html.append("@media print { body { background: none; padding: 0; } .a4-page { box-shadow: none; margin: 0; padding: 0; width: 100%; min-height: auto; } } </style></head><body><div class=\"a4-page\">");

                String[] htmlParagraphs = docXml.split("</w:p>");
                for (String p : htmlParagraphs) {
                    if (!p.contains("<w:p ") && !p.contains("<w:p>")) continue;
                    String align = "left"; if (p.contains("w:val=\"center\"")) align = "center"; else if (p.contains("w:val=\"right\"") || p.contains("w:val=\"end\"")) align = "right"; else if (p.contains("w:val=\"both\"")) align = "justify";
                    int ilvl = 0; java.util.regex.Matcher mIlvl = java.util.regex.Pattern.compile("w:ilvl w:val=\"(\\d+)\"").matcher(p); if (mIlvl.find()) ilvl = Integer.parseInt(mIlvl.group(1));
                    boolean isList = p.contains("<w:numPr>"); boolean isHeading = p.contains("w:val=\"Heading"); boolean isBorder = p.contains("<w:pBdr>") && p.contains("<w:bottom ");
                    String pStyle = "text-align:" + align + "; "; if (ilvl > 0) pStyle += "margin-left:" + (ilvl * 30) + "px; "; else if (isList) pStyle += "margin-left:20px; ";
                    if (isBorder) pStyle += "border-bottom:1px solid #CCCCCC; padding-bottom:8px; margin-bottom:8px; ";
                    html.append("<p style=\"").append(pStyle).append("\">");
                    if (isList) { String bullet = "&#8226;"; if (ilvl == 1) bullet = "&#9675;"; else if (ilvl == 2) bullet = "&#9642;"; html.append("<span style=\"margin-left:-20px; display:inline-block; width:20px;\">").append(bullet).append(" </span>"); }
                    String[] htmlRuns = p.split("</w:r>");
                    for (String r : htmlRuns) {
                        if (!r.contains("<w:r ") && !r.contains("<w:r>")) continue;
                        boolean b = r.contains("<w:b/>") || r.contains("<w:b "); boolean i = r.contains("<w:i/>") || r.contains("<w:i "); boolean u = r.contains("<w:u ");
                        String color = ""; java.util.regex.Matcher mC = java.util.regex.Pattern.compile("w:color w:val=\"([0-9a-fA-F]{6})\"").matcher(r); if (mC.find()) color = "#" + mC.group(1);
                        String sz = ""; java.util.regex.Matcher mSz = java.util.regex.Pattern.compile("w:sz w:val=\"(\\d+)\"").matcher(r); if (mSz.find()) { int pt = Integer.parseInt(mSz.group(1)) / 2; sz = pt + "pt"; }
                        if (isHeading) { b = true; sz = "16pt"; }
                        java.util.regex.Matcher mImg = java.util.regex.Pattern.compile("r:(?:embed|id)=\"(rId\\d+)\"").matcher(r);
                        if (mImg.find()) { String target = relMap.get(mImg.group(1)); if (target != null && base64MediaMap.containsKey(target)) html.append("<img src=\"data:image/png;base64,").append(base64MediaMap.get(target)).append("\"/>"); }
                        java.util.regex.Matcher mT = java.util.regex.Pattern.compile("<w:t(?: [^>]+)?>(.*?)</w:t>").matcher(r); StringBuilder text = new StringBuilder();
                        while (mT.find()) text.append(mT.group(1).replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace(" ", "&nbsp;"));
                        if (text.length() > 0) {
                            String rStyle = ""; if (!color.isEmpty()) rStyle += "color:" + color + "; "; if (!sz.isEmpty()) rStyle += "font-size:" + sz + "; ";
                            if (b) html.append("<b>"); if (i) html.append("<i>"); if (u) html.append("<u>"); if (!rStyle.isEmpty()) html.append("<span style=\"").append(rStyle).append("\">");
                            html.append(text.toString());
                            if (!rStyle.isEmpty()) html.append("</span>"); if (u) html.append("</u>"); if (i) html.append("</i>"); if (b) html.append("</b>");
                        }
                    }
                    html.append("</p>");
                }
                html.append("</div></body></html>");
                finalHtmlDoc = html.toString();

                SpannableStringBuilder ssb = new SpannableStringBuilder(); java.util.regex.Matcher m = java.util.regex.Pattern.compile("<[^>]+>|[^<]+").matcher(docXml);
                boolean b=false, i=false, u=false, h=false, bul=false, inT=false; int rStart = -1, pStart = 0, linkStart = -1; String linkUrl = null; android.text.Layout.Alignment pAlign = null;
                int screenW = getResources().getDisplayMetrics().widthPixels - 100; if (screenW < 100) screenW = 800; int curTextColor = tvDocContent.getCurrentTextColor();
                while (m.find()) {
                    String t = m.group();
                    if (t.startsWith("<")) {
                        if (t.startsWith("<w:p ") || t.equals("<w:p>")) { pStart = ssb.length(); h = false; bul = false; pAlign = null; }
                        else if (t.equals("</w:p>")) { if (ssb.length() > 0 && ssb.charAt(ssb.length()-1) != '\n') ssb.append("\n"); if (pStart < ssb.length()) { if (h) { ssb.setSpan(new RelativeSizeSpan(1.4f), pStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); ssb.setSpan(new StyleSpan(Typeface.BOLD), pStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); } if (bul) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ssb.setSpan(new BulletSpan(40, curTextColor), pStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); else ssb.setSpan(new BulletSpan(40), pStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); } if (pAlign != null) ssb.setSpan(new AlignmentSpan.Standard(pAlign), pStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); } }
                        else if (t.startsWith("<w:jc ")) { if (t.contains("val=\"center\"")) pAlign = android.text.Layout.Alignment.ALIGN_CENTER; else if (t.contains("val=\"right\"") || t.contains("val=\"end\"")) pAlign = android.text.Layout.Alignment.ALIGN_OPPOSITE; else if (t.contains("val=\"both\"")) pAlign = android.text.Layout.Alignment.ALIGN_NORMAL; }
                        else if (t.startsWith("<w:r ") || t.equals("<w:r>")) { b = false; i = false; u = false; rStart = ssb.length(); }
                        else if (t.equals("</w:r>")) { if (rStart >= 0 && rStart < ssb.length()) { if (b) ssb.setSpan(new StyleSpan(Typeface.BOLD), rStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); if (i) ssb.setSpan(new StyleSpan(Typeface.ITALIC), rStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); if (u) ssb.setSpan(new UnderlineSpan(), rStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); } rStart = -1; }
                        else if (t.startsWith("<w:b/>") || (t.startsWith("<w:b ") && !t.contains("val=\"0\"") && !t.contains("val=\"false\""))) b = true;
                        else if (t.startsWith("<w:i/>") || (t.startsWith("<w:i ") && !t.contains("val=\"0\"") && !t.contains("val=\"false\""))) i = true;
                        else if (t.startsWith("<w:u ") || t.startsWith("<w:u/>")) { if (!t.contains("val=\"none\"")) u = true; }
                        else if (t.contains("w:val=\"Heading")) h = true; else if (t.startsWith("<w:numPr")) bul = true;
                        else if (t.startsWith("<w:hyperlink ") && t.contains("r:id=")) { java.util.regex.Matcher hm = java.util.regex.Pattern.compile("r:id=\"(rId\\d+)\"").matcher(t); if (hm.find()) { linkUrl = relMap.get(hm.group(1)); linkStart = ssb.length(); } }
                        else if (t.equals("</w:hyperlink>")) { if (linkUrl != null && linkStart >= 0 && linkStart < ssb.length()) { ssb.setSpan(new URLSpan(linkUrl), linkStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); ssb.setSpan(new ForegroundColorSpan(Color.parseColor("#4A90E2")), linkStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); } linkUrl = null; linkStart = -1; }
                        else if (t.startsWith("<a:blip") || t.startsWith("<v:imagedata")) {
                            java.util.regex.Matcher im = java.util.regex.Pattern.compile("r:(?:embed|id)=\"(rId\\d+)\"").matcher(t);
                            if (im.find()) {
                                String target = relMap.get(im.group(1));
                                if (target != null && mediaMap.containsKey(target)) {
                                    Bitmap bmp = mediaMap.get(target);
                                    if (bmp != null) {
                                        try {
                                            float ratio = (float) bmp.getHeight() / bmp.getWidth(); int hh = (int) (screenW * ratio);
                                            if (hh > 4000) { hh = 4000; screenW = (int) (hh / ratio); }
                                            Bitmap scaled = Bitmap.createScaledBitmap(bmp, screenW, hh, true); ssb.append("\n\n\uFFFC\n\n");
                                            ssb.setSpan(new ImageSpan(DocReaderActivity.this, scaled, android.text.style.DynamicDrawableSpan.ALIGN_BOTTOM), ssb.length() - 3, ssb.length() - 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            }
                        }
                        else if (t.startsWith("<w:t>") || t.startsWith("<w:t ")) inT = true; else if (t.equals("</w:t>")) inT = false; else if (t.startsWith("<w:br") || t.startsWith("<w:cr")) ssb.append("\n"); else if (t.startsWith("<w:tab")) ssb.append("\t\t\t\t");
                    } else if (inT) { ssb.append(t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")); }
                }
                while(ssb.length() > 0 && ssb.charAt(ssb.length()-1) == '\n') ssb.delete(ssb.length()-1, ssb.length());
                finalSpannableDoc = ssb; rawDocText = finalSpannableDoc.toString(); lowerDocText = rawDocText.toLowerCase();

            } else {
                extractedText.setLength(0); InputStream fallbackIs = getContentResolver().openInputStream(uri);
                if (fallbackIs != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(fallbackIs, StandardCharsets.UTF_8)); String line; boolean isBinaryGarbage = false;
                    while ((line = reader.readLine()) != null) { if (line.indexOf(0) != -1 || line.length() > 50000) { isBinaryGarbage = true; break; } String cleanLine = line.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\r]", ""); extractedText.append(cleanLine).append("\n"); if (extractedText.length() > 2000000) break; }
                    fallbackIs.close(); if (isBinaryGarbage) { extractedText.setLength(0); extractedText.append("File Not Supported (Binary/Media Content Detected).\n\nThis file appears to contain unsupported binary data rather than readable document text."); }
                }
                rawDocText = extractedText.length() > 0 ? extractedText.toString().trim() : "Unable to parse file text. The file may be empty or unsupported."; lowerDocText = rawDocText.toLowerCase(); finalSpannableDoc = new SpannableStringBuilder(rawDocText);
                finalHtmlDoc = ""; wvDocContent.loadDataWithBaseURL(null, "", "text/html", "UTF-8", null);
            }

            tvDocContent.setText(finalSpannableDoc); if (searchBarContainer.getVisibility() == View.VISIBLE) performSearch(etSearch.getText().toString());
            if (isPdfModeActive) { wvDocContent.loadDataWithBaseURL(null, finalHtmlDoc, "text/html", "UTF-8", null); }
        } catch (Throwable t) { Toast.makeText(getApplicationContext(), "Error reading file", Toast.LENGTH_SHORT).show(); rawDocText = "File Not Supported or Damaged.\n\nUnable to open this file format inside OWN Doc Reader."; lowerDocText = rawDocText.toLowerCase(); finalSpannableDoc = new SpannableStringBuilder(rawDocText); tvDocContent.setText(finalSpannableDoc); }
    }
}