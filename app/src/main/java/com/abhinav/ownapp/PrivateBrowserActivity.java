package com.abhinav.ownapp;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.text.TextUtils;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("all")
public class PrivateBrowserActivity extends AppCompatActivity {

    private FrameLayout webViewContainer;
    private LinearLayout searchCapsule;
    private EditText etSearchUrl;
    private ProgressBar progressBar;

    // --- 3-STATE THEME VARIABLES ---
    private boolean isDarkTheme;
    private int themeState;

    private ImageView btnBack, btnForward, btnGo, btnMenu;
    private TextView btnFullscreenToggle;
    private boolean isFullscreen = false;
    private LinearLayout tabsOverlay;
    private GridLayout tabsGrid;
    private LinearLayout downloadsOverlay, downloadsList;

    private SharedPreferences prefs;
    private SharedPreferences browserPrefs;

    private static class TabInfo {
        WebView webView;
        Bitmap preview;
        String title = "New Tab";
    }

    private final List<TabInfo> tabs = new ArrayList<>();
    private int currentTabIndex = 0;
    private String defaultUserAgent = null;
    private final String[] blockedDomains = {"google-analytics.com", "doubleclick.net", "facebook.net", "facebook.com/tr/", "scorecardresearch.com", "googlesyndication.com"};

    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    private FrameLayout mFullscreenContainer;
    private int mOriginalSystemUiVisibility;
    private int mOriginalOrientation;

    private boolean isMenuOpen = false;

    // --- PRIVATE BROWSER HOME / SEARCH SHORTCUTS ---
    private LinearLayout homeOverlay;
    private GridLayout homeShortcutList;
    private static final String BROWSER_PREFS = "private_browser_shortcuts";
    private static final String PREF_CUSTOM_LINKS = "custom_links_json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_browser);

        View rootLayout = findViewById(R.id.browserRoot);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        try {
            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());
        } catch (Exception ignored) {
        }

        // --- PREFERENCES LOGIC INJECTION ---
        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        browserPrefs = getSharedPreferences(BROWSER_PREFS, MODE_PRIVATE);

        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = oldDark ? 1 : 0;
        }
        isDarkTheme = (themeState != 0);

        webViewContainer = findViewById(R.id.webViewContainer);
        searchCapsule = findViewById(R.id.searchCapsule);
        etSearchUrl = findViewById(R.id.etSearchUrl);
        progressBar = findViewById(R.id.browserProgressBar);
        btnBack = findViewById(R.id.btnBrowserBack);
        btnForward = findViewById(R.id.btnBrowserForward);
        btnGo = findViewById(R.id.btnBrowserGo);
        btnMenu = findViewById(R.id.btnBrowserMenu);
        btnFullscreenToggle = findViewById(R.id.btnFullscreenToggle);
        tabsOverlay = findViewById(R.id.tabsOverlay);
        tabsGrid = findViewById(R.id.tabsGrid);

        homeOverlay = findViewById(R.id.homeOverlay);
        homeShortcutList = findViewById(R.id.homeShortcutList);

        findViewById(R.id.btnCloseTabsOverlay).setOnClickListener(v -> {
            tabsOverlay.setVisibility(View.GONE);
            updateBackgroundBlur();
        });

        findViewById(R.id.btnAddNewTab).setOnClickListener(v -> {
            tabsOverlay.setVisibility(View.GONE);
            updateBackgroundBlur();
            createNewTab(null);
        });

        downloadsOverlay = findViewById(R.id.downloadsOverlay);
        downloadsList = findViewById(R.id.downloadsList);
        findViewById(R.id.btnCloseDownloadsOverlay).setOnClickListener(v -> {
            downloadsOverlay.setVisibility(View.GONE);
            updateBackgroundBlur();
        });

        applyTheme();
        renderHomeShortcuts();
        setupModernBackGesture();

        etSearchUrl.setOnFocusChangeListener((v, hasFocus) -> {
            TransitionManager.beginDelayedTransition((ViewGroup) rootLayout);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) searchCapsule.getLayoutParams();
            int margin24dp = (int) (24 * getResources().getDisplayMetrics().density);
            if (hasFocus) {
                params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                params.topMargin = margin24dp;
                params.bottomMargin = 0;
            } else {
                params.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                params.topMargin = 0;
                params.bottomMargin = margin24dp;
            }
            searchCapsule.setLayoutParams(params);
        });

        btnBack.setOnClickListener(v -> {
            if (getCurrentWeb() != null && getCurrentWeb().canGoBack()) getCurrentWeb().goBack();
        });

        btnForward.setOnClickListener(v -> {
            if (getCurrentWeb() != null && getCurrentWeb().canGoForward()) getCurrentWeb().goForward();
        });

        btnGo.setOnClickListener(v -> loadUrlOrSearch());
        btnMenu.setOnClickListener(this::showRoundedMenu);
        btnFullscreenToggle.setOnClickListener(v -> toggleFullscreenCapsule());

        // --- Butter-Smooth Dragging Logic for the Shrunk Capsule ---
        btnFullscreenToggle.setOnTouchListener(new View.OnTouchListener() {
            private float dX;
            private float startX;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (!isFullscreen) return false;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = searchCapsule.getX() - event.getRawX();
                        startX = event.getRawX();
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float newX = event.getRawX() + dX;
                        int screenWidth = getResources().getDisplayMetrics().widthPixels;
                        int margin = (int) (16 * getResources().getDisplayMetrics().density);
                        if (newX < margin) newX = margin;
                        if (newX > screenWidth - searchCapsule.getWidth() - margin) {
                            newX = screenWidth - searchCapsule.getWidth() - margin;
                        }
                        searchCapsule.setX(newX);
                        if (Math.abs(event.getRawX() - startX) > 10) {
                            isDragging = true;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            toggleFullscreenCapsule();
                        }
                        return true;
                }
                return false;
            }
        });

        etSearchUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadUrlOrSearch();
                return true;
            }
            return false;
        });

        createNewTab(null);
    }

    private void loadUrlOrSearch() {
        String query = etSearchUrl.getText().toString().trim();
        etSearchUrl.clearFocus();
        if (query.isEmpty() || getCurrentWeb() == null) return;
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(etSearchUrl.getWindowToken(), 0);

        hideHomePage();
        if (!query.contains(" ") && (query.contains(".") || query.startsWith("http"))) {
            if (!query.startsWith("http://") && !query.startsWith("https://")) query = "https://" + query;
            getCurrentWeb().loadUrl(query);
        } else {
            getCurrentWeb().loadUrl("https://www.google.com/search?q=" + query);
        }
    }

    private TabInfo getTabForWeb(WebView web) {
        for (TabInfo t : tabs) if (t.webView == web) return t;
        return tabs.isEmpty() ? null : tabs.get(0);
    }

    private void showHomePage() {
        etSearchUrl.clearFocus();
        etSearchUrl.setText("");
        if (homeOverlay != null) {
            renderHomeShortcuts();
            homeOverlay.setVisibility(View.VISIBLE);
        }
        updateBackgroundBlur();
    }

    private void hideHomePage() {
        if (homeOverlay != null) homeOverlay.setVisibility(View.GONE);
    }

    private void openShortcut(String url) {
        if (url == null || url.trim().isEmpty() || getCurrentWeb() == null) return;
        hideHomePage();
        getCurrentWeb().loadUrl(url);
    }

    private void renderHomeShortcuts() {
        if (homeShortcutList == null) return;
        homeShortcutList.removeAllViews();

        int cardBg, textColor, secondaryColor, iconBg;
        if (themeState == 0) { // Light
            cardBg = Color.parseColor("#F5F5F7");
            textColor = Color.parseColor("#222222");
            secondaryColor = Color.parseColor("#777777");
            iconBg = Color.parseColor("#E5E5EA");
        } else if (themeState == 1) { // Standard Dark
            cardBg = Color.parseColor("#1C1C1E");
            textColor = Color.WHITE;
            secondaryColor = Color.parseColor("#AAAAAA");
            iconBg = Color.parseColor("#2C2C2E");
        } else { // Star Mode (Pure Black Canvas, Dark Grey Pills)
            cardBg = Color.parseColor("#141414");
            textColor = Color.WHITE;
            secondaryColor = Color.parseColor("#888888");
            iconBg = Color.parseColor("#242424");
        }

        addHomeShortcut("Google", "Secure Search", "https://www.google.com/", "G", cardBg, textColor, secondaryColor, iconBg, false, -1);
        addHomeShortcut("DuckDuckGo", "Secure Search", "https://duckduckgo.com/", "D", cardBg, textColor, secondaryColor, iconBg, false, -1);
        addHomeShortcut("Yahoo", "Secure Search", "https://search.yahoo.com/", "Y", cardBg, textColor, secondaryColor, iconBg, false, -1);
        addHomeShortcut("Instagram", "Social Media", "https://www.instagram.com/", "◎", cardBg, textColor, secondaryColor, iconBg, false, -1);
        addHomeShortcut("LinkedIn", "Professional", "https://www.linkedin.com/", "in", cardBg, textColor, secondaryColor, iconBg, false, -1);
        addHomeShortcut("GitHub", "Development", "https://github.com/", "GH", cardBg, textColor, secondaryColor, iconBg, false, -1);
        addHomeShortcut("YouTube", "Video", "https://www.youtube.com/", "▶", cardBg, textColor, secondaryColor, iconBg, false, -1);
        addHomeShortcut("Gmail", "Email", "https://mail.google.com/", "✉", cardBg, textColor, secondaryColor, iconBg, false, -1);

        // Load multiple dynamic custom shortcuts
        try {
            JSONArray arr = new JSONArray(browserPrefs.getString(PREF_CUSTOM_LINKS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                addHomeShortcut(obj.getString("name"), "Custom Link", obj.getString("url"), "★", cardBg, textColor, secondaryColor, iconBg, true, i);
            }
        } catch (Exception e) {}

        addCustomShortcutCard(cardBg, textColor, secondaryColor, iconBg);
    }

    private void addHomeShortcut(String name, String subtitle, String url, String iconText, int cardBg, int textColor, int secondaryColor, int iconBg, boolean isCustom, int customIndex) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
        cardParams.width = 0;
        cardParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cardParams.setMargins(dp(6), dp(6), dp(6), dp(6));
        card.setLayoutParams(cardParams);

        GradientDrawable cardDrawable = new GradientDrawable();
        cardDrawable.setColor(cardBg);
        cardDrawable.setCornerRadius(dp(100)); // Beautiful Pill Shape
        card.setBackground(cardDrawable);

        TextView icon = new TextView(this);
        icon.setText(iconText);
        icon.setTextColor(textColor);
        icon.setTextSize(iconText.length() > 1 ? 12f : 16f);
        icon.setGravity(Gravity.CENTER);
        icon.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable iconDrawable = new GradientDrawable();
        iconDrawable.setColor(iconBg);
        iconDrawable.setShape(GradientDrawable.OVAL);
        icon.setBackground(iconDrawable);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        iconParams.setMargins(0, 0, dp(10), 0);
        icon.setLayoutParams(iconParams);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setGravity(Gravity.CENTER_VERTICAL);
        textBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(textColor);
        title.setTextSize(14f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);

        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextColor(secondaryColor);
        sub.setTextSize(11f);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);

        textBox.addView(title);
        textBox.addView(sub);

        card.addView(icon);
        card.addView(textBox);

        card.setOnClickListener(v -> openShortcut(url));

        if (isCustom) {
            card.setOnLongClickListener(v -> {
                showDeleteCustomShortcutDialog(customIndex, name);
                return true;
            });
        }

        homeShortcutList.addView(card);
    }

    private void addCustomShortcutCard(int cardBg, int textColor, int secondaryColor, int iconBg) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
        cardParams.width = 0;
        cardParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cardParams.setMargins(dp(6), dp(6), dp(6), dp(6));
        card.setLayoutParams(cardParams);

        GradientDrawable cardDrawable = new GradientDrawable();
        cardDrawable.setColor(cardBg);
        cardDrawable.setCornerRadius(dp(100)); // Beautiful Pill Shape
        card.setBackground(cardDrawable);

        TextView icon = new TextView(this);
        icon.setText("+");
        icon.setTextColor(textColor);
        icon.setTextSize(18f);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable iconDrawable = new GradientDrawable();
        iconDrawable.setColor(iconBg);
        iconDrawable.setShape(GradientDrawable.OVAL);
        icon.setBackground(iconDrawable);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        iconParams.setMargins(0, 0, dp(10), 0);
        icon.setLayoutParams(iconParams);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setGravity(Gravity.CENTER_VERTICAL);
        textBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("Add Shortcut");
        title.setTextColor(textColor);
        title.setTextSize(14f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView sub = new TextView(this);
        sub.setText("Custom URL");
        sub.setTextColor(secondaryColor);
        sub.setTextSize(11f);

        textBox.addView(title);
        textBox.addView(sub);
        card.addView(icon);
        card.addView(textBox);
        card.setOnClickListener(v -> showCustomShortcutDialog());
        homeShortcutList.addView(card);
    }

    private void showCustomShortcutDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(4);

        EditText nameInput = new EditText(this);
        nameInput.setHint("Name (e.g. Reddit)");
        nameInput.setSingleLine(true);
        nameInput.setPadding(pad, pad, pad, pad);

        EditText urlInput = new EditText(this);
        urlInput.setHint("Website URL");
        urlInput.setSingleLine(true);
        urlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setPadding(pad, pad, pad, pad);

        form.addView(nameInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(55)));
        form.addView(urlInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(55)));

        int dialogStyle = isDarkTheme ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
        AlertDialog.Builder builder = new AlertDialog.Builder(this, dialogStyle);
        builder.setTitle("Add Custom Shortcut");
        builder.setView(form);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String url = urlInput.getText().toString().trim();
            if (name.isEmpty() || url.isEmpty()) {
                Toast.makeText(this, "Enter both a name and URL.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
            saveCustomShortcut(name, url);
        });
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> styleDialogButtons(dialog));
        dialog.show();
    }

    private void saveCustomShortcut(String name, String url) {
        try {
            JSONArray arr = new JSONArray(browserPrefs.getString(PREF_CUSTOM_LINKS, "[]"));
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("url", url);
            arr.put(obj);
            browserPrefs.edit().putString(PREF_CUSTOM_LINKS, arr.toString()).apply();
            renderHomeShortcuts();
        } catch (Exception e) {}
    }

    private void showDeleteCustomShortcutDialog(int index, String name) {
        int dialogStyle = isDarkTheme ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
        AlertDialog.Builder builder = new AlertDialog.Builder(this, dialogStyle);
        builder.setTitle("Remove Shortcut");
        builder.setMessage("Remove '" + name + "' from shortcuts?");
        builder.setPositiveButton("Remove", (dialog, which) -> {
            try {
                JSONArray arr = new JSONArray(browserPrefs.getString(PREF_CUSTOM_LINKS, "[]"));
                if (index >= 0 && index < arr.length()) {
                    arr.remove(index);
                    browserPrefs.edit().putString(PREF_CUSTOM_LINKS, arr.toString()).apply();
                    renderHomeShortcuts();
                }
            } catch (Exception e) {}
        });
        builder.setNegativeButton("Cancel", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> styleDialogButtons(dialog));
        dialog.show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateBackgroundBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean shouldBlur = tabsOverlay.getVisibility() == View.VISIBLE || downloadsOverlay.getVisibility() == View.VISIBLE || isMenuOpen;
            if (shouldBlur)
                webViewContainer.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(35f, 35f, android.graphics.Shader.TileMode.CLAMP));
            else webViewContainer.setRenderEffect(null);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText && searchCapsule != null) {
                Rect outRect = new Rect();
                searchCapsule.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void styleDialogButtons(AlertDialog dialog) {
        int btnColor = isDarkTheme ? Color.parseColor("#FFB59F") : Color.parseColor("#6750A4");
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(btnColor);
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(btnColor);
        if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(btnColor);
    }

    private void setupModernBackGesture() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mCustomView != null) {
                    exitFullscreenVideo();
                } else if (tabsOverlay.getVisibility() == View.VISIBLE) {
                    tabsOverlay.setVisibility(View.GONE);
                    updateBackgroundBlur();
                } else if (downloadsOverlay.getVisibility() == View.VISIBLE) {
                    downloadsOverlay.setVisibility(View.GONE);
                    updateBackgroundBlur();
                } else if (!tabs.isEmpty() && getCurrentWeb() != null && getCurrentWeb().canGoBack()) {
                    getCurrentWeb().goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void enterFullscreenVideo(View view, WebChromeClient.CustomViewCallback callback) {
        if (mCustomView != null) {
            callback.onCustomViewHidden();
            return;
        }
        mOriginalOrientation = getRequestedOrientation();
        mOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
        mCustomView = view;
        mCustomViewCallback = callback;
        mFullscreenContainer = new FrameLayout(this);
        mFullscreenContainer.setBackgroundColor(Color.BLACK);
        mFullscreenContainer.addView(mCustomView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout decor = (FrameLayout) getWindow().getDecorView();
        decor.addView(mFullscreenContainer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void exitFullscreenVideo() {
        if (mCustomView == null) return;
        FrameLayout decor = (FrameLayout) getWindow().getDecorView();
        decor.removeView(mFullscreenContainer);
        mFullscreenContainer = null;
        mCustomView = null;
        if (mCustomViewCallback != null) mCustomViewCallback.onCustomViewHidden();
        mCustomViewCallback = null;
        getWindow().getDecorView().setSystemUiVisibility(mOriginalSystemUiVisibility);
        setRequestedOrientation(mOriginalOrientation);
    }

    private void toggleFullscreenCapsule() {
        etSearchUrl.clearFocus();
        isFullscreen = !isFullscreen;
        int visibility = isFullscreen ? View.GONE : View.VISIBLE;

        btnBack.setVisibility(visibility);
        btnForward.setVisibility(visibility);
        etSearchUrl.setVisibility(visibility);
        btnGo.setVisibility(visibility);
        btnMenu.setVisibility(visibility);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) searchCapsule.getLayoutParams();
        if (isFullscreen) {
            btnFullscreenToggle.setText("<>");
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.addRule(RelativeLayout.ALIGN_PARENT_END);
        } else {
            btnFullscreenToggle.setText("><");
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.removeRule(RelativeLayout.ALIGN_PARENT_END);
            searchCapsule.setTranslationX(0);
        }
        searchCapsule.setLayoutParams(params);
    }

    private WebView getCurrentWeb() {
        if (tabs.isEmpty() || currentTabIndex < 0 || currentTabIndex >= tabs.size()) return null;
        return tabs.get(currentTabIndex).webView;
    }

    private void createNewTab(String url) {
        captureCurrentTabPreview();
        TabInfo info = new TabInfo();
        info.webView = new WebView(this);
        info.webView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        info.webView.setTag(false);
        setupSuperSecureWebView(info.webView);
        tabs.add(info);
        webViewContainer.addView(info.webView);
        switchTab(tabs.size() - 1);

        if (url != null) {
            info.webView.loadUrl(url);
            hideHomePage();
        } else {
            info.webView.loadUrl("about:blank");
            showHomePage();
        }
    }

    private void switchTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        currentTabIndex = index;
        etSearchUrl.clearFocus();
        for (int i = 0; i < tabs.size(); i++)
            tabs.get(i).webView.setVisibility(i == currentTabIndex ? View.VISIBLE : View.GONE);
        WebView current = getCurrentWeb();
        if (current != null) {
            String currentUrl = current.getUrl();
            if (currentUrl == null || currentUrl.isEmpty() || "about:blank".equals(currentUrl)) {
                showHomePage();
            } else {
                etSearchUrl.setText(currentUrl);
                hideHomePage();
            }
        }
        tabsOverlay.setVisibility(View.GONE);
        updateBackgroundBlur();
    }

    private void closeTab(int index) {
        TabInfo closing = tabs.get(index);
        webViewContainer.removeView(closing.webView);
        closing.webView.clearHistory();
        closing.webView.clearCache(true);
        closing.webView.clearFormData();
        closing.webView.loadUrl("about:blank");
        closing.webView.destroy();
        if (closing.preview != null) closing.preview.recycle();
        tabs.remove(index);
        if (tabs.isEmpty()) {
            finish();
        } else {
            if (currentTabIndex >= tabs.size()) currentTabIndex = tabs.size() - 1;
            renderVisualTabsGrid();
            switchTab(currentTabIndex);
        }
    }

    private void captureCurrentTabPreview() {
        if (tabs.isEmpty() || currentTabIndex >= tabs.size()) return;
        TabInfo current = tabs.get(currentTabIndex);
        if (current.webView.getWidth() > 0 && current.webView.getHeight() > 0) {
            try {
                Bitmap bmp = Bitmap.createBitmap(current.webView.getWidth(), current.webView.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                current.webView.draw(c);
                if (current.preview != null) current.preview.recycle();
                current.preview = Bitmap.createScaledBitmap(bmp, 300, 500, true);
                bmp.recycle();
            } catch (OutOfMemoryError ignored) {
            }
        }
    }

    private void openVisualTabSwitcher() {
        etSearchUrl.clearFocus();
        captureCurrentTabPreview();
        renderVisualTabsGrid();
        tabsOverlay.setVisibility(View.VISIBLE);
        updateBackgroundBlur();
    }

    private void renderVisualTabsGrid() {
        tabsGrid.removeAllViews();

        int unselectedCardBg, selectedCardBg, unselectedText, selectedText, emptyPreviewBg;

        if (themeState == 0) { // Light Mode
            unselectedCardBg = Color.WHITE;
            selectedCardBg = Color.parseColor("#D0BCFF");
            unselectedText = Color.BLACK;
            selectedText = Color.parseColor("#381E72");
            emptyPreviewBg = Color.parseColor("#F2F2F7");
        } else if (themeState == 1) { // Standard Dark Mode
            unselectedCardBg = Color.parseColor("#332D2B");
            selectedCardBg = Color.parseColor("#FFB59F");
            unselectedText = Color.WHITE;
            selectedText = Color.parseColor("#3E211A");
            emptyPreviewBg = Color.parseColor("#1C1C1E");
        } else { // Star Mode (AMOLED Black)
            unselectedCardBg = Color.parseColor("#1C1C1E");
            selectedCardBg = Color.parseColor("#FFB59F");
            unselectedText = Color.WHITE;
            selectedText = Color.parseColor("#3E211A");
            emptyPreviewBg = Color.parseColor("#000000");
        }

        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            TabInfo info = tabs.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(12, 12, 12, 12);
            card.setLayoutParams(params);

            GradientDrawable gd = new GradientDrawable();
            gd.setColor(index == currentTabIndex ? selectedCardBg : unselectedCardBg);
            gd.setCornerRadius(30f);
            card.setBackground(gd);
            card.setPadding(20, 20, 20, 20);

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            TextView title = new TextView(this);
            title.setText(info.title);
            title.setTextColor(index == currentTabIndex ? selectedText : unselectedText);
            title.setTextSize(14f);
            title.setSingleLine(true);
            title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView close = new TextView(this);
            close.setText("X");
            close.setTextColor(index == currentTabIndex ? selectedText : unselectedText);
            close.setTextSize(16f);
            close.setPadding(10, 0, 0, 0);
            close.setOnClickListener(v -> closeTab(index));
            header.addView(title);
            header.addView(close);

            ImageView preview = new ImageView(this);
            preview.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400));
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setPadding(0, 20, 0, 0);
            if (info.preview != null) {
                preview.setImageBitmap(info.preview);
                preview.setClipToOutline(true);
            } else preview.setBackgroundColor(emptyPreviewBg);

            card.addView(header);
            card.addView(preview);
            preview.setOnClickListener(v -> switchTab(index));
            tabsGrid.addView(card);
        }
    }

    private void showRoundedMenu(View anchor) {
        etSearchUrl.clearFocus();
        LinearLayout menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);

        int bgColor, textColor;
        if (themeState == 0) { // Light
            bgColor = Color.parseColor("#99FFFFFF");
            textColor = Color.BLACK;
        } else if (themeState == 1) { // Standard Dark
            bgColor = Color.parseColor("#992C2C2E");
            textColor = Color.WHITE;
        } else { // Star Mode
            bgColor = Color.parseColor("#E61C1C1E"); // Smooth translucent black/gray
            textColor = Color.WHITE;
        }

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(40f);
        menuLayout.setBackground(gd);
        menuLayout.setPadding(20, 20, 20, 20);

        final PopupWindow[] popupWindow = new PopupWindow[1];
        WebView current = getCurrentWeb();
        if (current == null) return;

        TextView tvHome = createMenuItem("Home / Start Page", android.R.drawable.ic_menu_compass, textColor);
        tvHome.setOnClickListener(v -> {
            popupWindow[0].dismiss();
            current.loadUrl("about:blank");
            showHomePage();
        });

        TextView tvReload = createMenuItem("Reload Page", android.R.drawable.ic_popup_sync, textColor);
        tvReload.setOnClickListener(v -> {
            popupWindow[0].dismiss();
            current.reload();
        });

        boolean isDesktop = (boolean) current.getTag();
        TextView tvDesktop = createMenuItem(isDesktop ? "Switch to Mobile" : "Request Desktop Site", android.R.drawable.ic_menu_view, textColor);
        tvDesktop.setOnClickListener(v -> {
            popupWindow[0].dismiss();
            toggleDesktopMode(current);
        });

        TextView tvDownloads = createMenuItem("Downloads", android.R.drawable.stat_sys_download, textColor);
        tvDownloads.setOnClickListener(v -> {
            popupWindow[0].dismiss();
            openVisualDownloadsManager();
        });

        TextView tvNewTab = createMenuItem("New Tab", android.R.drawable.ic_menu_add, textColor);
        tvNewTab.setOnClickListener(v -> {
            popupWindow[0].dismiss();
            createNewTab(null);
        });

        TextView tvSwitch = createMenuItem("Manage Tabs (" + tabs.size() + ")", android.R.drawable.ic_menu_manage, textColor);
        tvSwitch.setOnClickListener(v -> {
            popupWindow[0].dismiss();
            openVisualTabSwitcher();
        });

        menuLayout.addView(tvHome);
        menuLayout.addView(tvReload);
        menuLayout.addView(tvDesktop);
        menuLayout.addView(tvDownloads);
        menuLayout.addView(tvNewTab);
        menuLayout.addView(tvSwitch);

        isMenuOpen = true;
        updateBackgroundBlur();
        popupWindow[0] = new PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow[0].setElevation(30f);
        popupWindow[0].setOnDismissListener(() -> {
            isMenuOpen = false;
            updateBackgroundBlur();
        });
        popupWindow[0].showAtLocation(anchor, Gravity.BOTTOM | Gravity.END, 40, 200);
    }

    private TextView createMenuItem(String text, int iconResId, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(16f);
        tv.setPadding(40, 30, 40, 30);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setCompoundDrawablePadding(40);

        if (iconResId != 0) {
            tv.setCompoundDrawablesWithIntrinsicBounds(iconResId, 0, 0, 0);
            Drawable[] drawables = tv.getCompoundDrawables();
            if (drawables[0] != null) {
                drawables[0].setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                drawables[0].setAlpha(200);
            }
        }
        return tv;
    }

    private void openVisualDownloadsManager() {
        etSearchUrl.clearFocus();
        downloadsList.removeAllViews();
        downloadsOverlay.setVisibility(View.VISIBLE);
        updateBackgroundBlur();

        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OWN's Browser downloads");
        File[] files = dir.listFiles();
        if (!dir.exists() || files == null || files.length == 0) return;
        Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.US);

        int primaryText, secondaryText, iconBg;
        if (themeState == 0) { // Light
            primaryText = Color.BLACK;
            secondaryText = Color.parseColor("#555555");
            iconBg = Color.parseColor("#E0E0E0");
        } else if (themeState == 1) { // Dark
            primaryText = Color.WHITE;
            secondaryText = Color.parseColor("#AAAAAA");
            iconBg = Color.parseColor("#3D322F");
        } else { // Star
            primaryText = Color.WHITE;
            secondaryText = Color.parseColor("#AAAAAA");
            iconBg = Color.parseColor("#1C1C1E");
        }

        for (File file : files) {
            TextView dateHeader = new TextView(this);
            dateHeader.setText(sdf.format(new Date(file.lastModified())));
            dateHeader.setTextColor(primaryText);
            dateHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            dateHeader.setPadding(0, 30, 0, 10);
            downloadsList.addView(dateHeader);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 16, 0, 16);
            row.setGravity(Gravity.CENTER_VERTICAL);

            ImageView thumb = new ImageView(this);
            thumb.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            String name = file.getName().toLowerCase();

            if (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".jpeg")) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 4;
                thumb.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath(), opts));
            } else {
                thumb.setImageResource(android.R.drawable.ic_menu_crop);
                thumb.setColorFilter(primaryText);
                thumb.setBackgroundColor(iconBg);
            }
            row.addView(thumb);

            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(24, 0, 0, 0);
            details.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView title = new TextView(this);
            title.setText(file.getName());
            title.setTextColor(primaryText);
            title.setTextSize(16f);
            title.setSingleLine(true);

            TextView sub = new TextView(this);
            sub.setText(String.format(Locale.US, "%.2f MB • Phone Storage", (file.length() / (1024f * 1024f))));
            sub.setTextColor(secondaryText);
            sub.setTextSize(12f);

            details.addView(title);
            details.addView(sub);
            row.addView(details);

            TextView menu = new TextView(this);
            menu.setText("⋮");
            menu.setTextColor(primaryText);
            menu.setTextSize(24f);
            menu.setPadding(20, 0, 20, 0);
            menu.setOnClickListener(v -> showFileActionDialog(file));

            row.setOnClickListener(v -> openFileDirectly(file));
            row.addView(menu);
            downloadsList.addView(row);
        }
    }

    private void openFileDirectly(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(file), "*/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(intent, "Open File..."));
        } catch (Exception e) {
            Toast.makeText(this, "No app found to open this file.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFileActionDialog(File file) {
        int dialogStyle = isDarkTheme ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
        AlertDialog.Builder b = new AlertDialog.Builder(this, dialogStyle);
        b.setTitle(file.getName());
        b.setPositiveButton("🗑️ Delete", (dialog, which) -> {
            if (file.delete()) openVisualDownloadsManager();
        });
        b.setNeutralButton("📂 Open / Share", (dialog, which) -> openFileDirectly(file));
        b.setNegativeButton("Cancel", null);
        AlertDialog dialog = b.show();
        styleDialogButtons(dialog);
    }

    private void triggerAskBeforeDownload(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
        String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
        if (fileName.endsWith(".bin")) {
            if (mimeType != null && mimeType.startsWith("image/"))
                fileName = fileName.replace(".bin", ".jpg");
            else if (mimeType != null && mimeType.startsWith("video/"))
                fileName = fileName.replace(".bin", ".mp4");
            else if (url.toLowerCase().contains(".jpg") || url.toLowerCase().contains(".jpeg"))
                fileName = fileName.replace(".bin", ".jpg");
            else if (url.toLowerCase().contains(".png"))
                fileName = fileName.replace(".bin", ".png");
            else if (url.toLowerCase().contains(".webp"))
                fileName = fileName.replace(".bin", ".webp");
            else if (url.toLowerCase().contains(".mp4"))
                fileName = fileName.replace(".bin", ".mp4");
        }

        if (mimeType != null) {
            if (mimeType.startsWith("image/") && !fileName.matches(".*\\.(jpg|jpeg|png|webp|gif)$"))
                fileName += ".jpg";
            else if (mimeType.startsWith("video/") && !fileName.matches(".*\\.(mp4|mkv|webm)$"))
                fileName += ".mp4";
        }

        int dialogStyle = isDarkTheme ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
        AlertDialog.Builder b = new AlertDialog.Builder(this, dialogStyle);
        b.setTitle("Download File?");
        b.setMessage("Folder: OWN's Browser downloads\nFile: " + fileName);
        final String finalFileName = fileName;

        b.setPositiveButton("Yes, Download", (dialog, which) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "OWN's Browser downloads/" + finalFileName);
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) dm.enqueue(request);
                Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Download failed.", Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("Cancel", null);
        AlertDialog dialog = b.show();
        styleDialogButtons(dialog);
    }

    private void toggleDesktopMode(WebView webView) {
        boolean isDesktop = (boolean) webView.getTag();
        WebSettings settings = webView.getSettings();
        if (defaultUserAgent == null) defaultUserAgent = settings.getUserAgentString();

        if (isDesktop) {
            settings.setUserAgentString(defaultUserAgent);
            settings.setUseWideViewPort(false);
            settings.setLoadWithOverviewMode(false);
            webView.setInitialScale(0);
            webView.setTag(false);
            Toast.makeText(this, "Mobile View...", Toast.LENGTH_SHORT).show();
        } else {
            settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            webView.setInitialScale(1);
            webView.setTag(true);
            Toast.makeText(this, "Desktop View...", Toast.LENGTH_SHORT).show();
        }
        webView.clearCache(true);
        webView.reload();
    }

    private class JavascriptBridge {
        @JavascriptInterface
        @SuppressWarnings("unused")
        public void processLinkText(String text) {
            runOnUiThread(() -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Link Text", text));
                    Toast.makeText(PrivateBrowserActivity.this, "Copied Link Text!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        @SuppressWarnings("unused")
        public void handleVideoLongPress(String videoUrl) {
            if (videoUrl == null || videoUrl.isEmpty()) return;
            runOnUiThread(() -> {
                int dialogStyle = isDarkTheme ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
                AlertDialog.Builder b = new AlertDialog.Builder(PrivateBrowserActivity.this, dialogStyle);
                b.setTitle("Video Options");
                String[] options = {"Copy Video Link", "Download Video (MP4)"};
                b.setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        ClipboardManager clip = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clip != null) {
                            clip.setPrimaryClip(ClipData.newPlainText("Video URL", videoUrl));
                            Toast.makeText(PrivateBrowserActivity.this, "Video Link Copied!", Toast.LENGTH_SHORT).show();
                        }
                    } else if (which == 1) {
                        WebView current = getCurrentWeb();
                        String ua = current != null ? current.getSettings().getUserAgentString() : "";
                        triggerAskBeforeDownload(videoUrl, ua, null, "video/mp4", 0);
                    }
                });
                b.show();
            });
        }
    }

    private void handleLongPress(WebView.HitTestResult result) {
        if (result.getExtra() == null) return;
        String url = result.getExtra();
        int type = result.getType();
        int dialogStyle = isDarkTheme ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
        AlertDialog.Builder b = new AlertDialog.Builder(this, dialogStyle);

        if (type == WebView.HitTestResult.IMAGE_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            b.setTitle("Image Options");
            String[] options = {"Copy Image Link", "Download Image", "Open Full View"};
            b.setItems(options, (dialog, which) -> {
                if (which == 0) {
                    ClipboardManager clip = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clip != null) {
                        clip.setPrimaryClip(ClipData.newPlainText("Image URL", url));
                        Toast.makeText(this, "Image Link Copied!", Toast.LENGTH_SHORT).show();
                    }
                } else if (which == 1) {
                    WebView current = getCurrentWeb();
                    String ua = current != null ? current.getSettings().getUserAgentString() : "";
                    triggerAskBeforeDownload(url, ua, null, "image/*", 0);
                } else if (which == 2) {
                    createNewTab(url);
                }
            });
            b.show();
        } else if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
            b.setTitle("Link Options");
            String[] options = {"Copy Link Address", "Go To Link (New Tab)", "Copy Link Text"};
            b.setItems(options, (dialog, which) -> {
                if (which == 0) {
                    ClipboardManager clip = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clip != null) {
                        clip.setPrimaryClip(ClipData.newPlainText("Link URL", url));
                        Toast.makeText(this, "Link Copied!", Toast.LENGTH_SHORT).show();
                    }
                } else if (which == 1) {
                    createNewTab(url);
                } else if (which == 2) {
                    WebView current = getCurrentWeb();
                    if (current != null) {
                        String js = "(function(){ var links = document.getElementsByTagName('a'); for(var i=0; i<links.length; i++){ if(links[i].href === '" + url + "'){ OwnBrowser.processLinkText(links[i].innerText); return; } } })();";
                        current.evaluateJavascript(js, null);
                    }
                }
            });
            b.show();
        }
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void setupSuperSecureWebView(WebView web) {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setSaveFormData(false);
        settings.setAllowFileAccess(false);
        settings.setDatabaseEnabled(false);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        if (defaultUserAgent == null) defaultUserAgent = settings.getUserAgentString();
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.addJavascriptInterface(new JavascriptBridge(), "OwnBrowser");
        web.addJavascriptInterface(new JavascriptBridge(), "control");
        web.setOnLongClickListener(v -> {
            WebView.HitTestResult result = ((WebView) v).getHitTestResult();
            if (result.getType() == WebView.HitTestResult.IMAGE_TYPE || result.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE || result.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                handleLongPress(result);
                return true;
            }
            return false;
        });
        web.setDownloadListener(this::triggerAskBeforeDownload);

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                enterFullscreenVideo(view, callback);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreenVideo();
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (view == getCurrentWeb()) {
                    if (newProgress == 100) progressBar.setVisibility(View.GONE);
                    else {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(newProgress);
                    }
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                tabs.get(tabs.indexOf(getTabForWeb(view))).title = title != null ? title : "New Tab";
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (view == getCurrentWeb() && !isFullscreen) {
                    if (url != null && url.startsWith("http://startpage")) {
                        etSearchUrl.setText("");
                    } else {
                        etSearchUrl.setText(url);
                    }
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Boolean isDesktop = (Boolean) view.getTag();
                if (isDesktop != null && isDesktop) {
                    view.evaluateJavascript("try { var meta = document.querySelector('meta[name=\"viewport\"]'); if (meta) { meta.setAttribute('content', 'width=1024'); } else { var m = document.createElement('meta'); m.name = 'viewport'; m.content = 'width=1024'; document.head.appendChild(m); } } catch(e) {}", null);
                }
                view.evaluateJavascript("document.addEventListener('contextmenu', function(e) { if(e.target.tagName === 'VIDEO') { OwnBrowser.handleVideoLongPress(e.target.src || e.target.currentSrc); } });", null);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                for (String domain : blockedDomains) {
                    if (url.contains(domain)) {
                        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
    }

    private void applyTheme() {
        int bgColor, textColor, hintColor, buttonBgColor, accentBgColor, accentTextColor, capsuleGlassColor, overlayGlassColor;

        if (themeState == 0) { // Light Mode
            bgColor = Color.parseColor("#F2F2F7");
            textColor = Color.parseColor("#333333");
            hintColor = Color.parseColor("#A0A0A0");
            buttonBgColor = Color.WHITE;
            accentBgColor = Color.parseColor("#6750A4");
            accentTextColor = Color.WHITE;
            capsuleGlassColor = Color.parseColor("#D9FFFFFF");
            overlayGlassColor = Color.parseColor("#80F2F2F7");
        } else if (themeState == 1) { // Standard Dark Mode
            bgColor = Color.parseColor("#1C1C1E");
            textColor = Color.WHITE;
            hintColor = Color.parseColor("#888888");
            buttonBgColor = Color.parseColor("#332D2B");
            accentBgColor = Color.parseColor("#FFB59F");
            accentTextColor = Color.parseColor("#000000");
            capsuleGlassColor = Color.parseColor("#D92C2C2E");
            overlayGlassColor = Color.parseColor("#801C1C1E");
        } else { // Star Mode (AMOLED Pure Black)
            bgColor = Color.parseColor("#000000"); // Infinite Pure Black Canvas
            textColor = Color.WHITE;
            hintColor = Color.parseColor("#888888");
            buttonBgColor = Color.parseColor("#1C1C1E"); // Dark grey floating cards
            accentBgColor = Color.parseColor("#FFB59F");
            accentTextColor = Color.parseColor("#000000");
            capsuleGlassColor = Color.parseColor("#D91C1C1E"); // Translucent Dark Grey Capsule
            overlayGlassColor = Color.parseColor("#B3000000"); // Very dark overlay for pure black
        }

        getWindow().setStatusBarColor(bgColor);
        findViewById(R.id.browserRoot).setBackgroundColor(bgColor);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(capsuleGlassColor);
        gd.setCornerRadius(100f);
        searchCapsule.setBackground(gd);
        searchCapsule.setClipToOutline(true);
        tabsOverlay.setBackgroundColor(overlayGlassColor);
        downloadsOverlay.setBackgroundColor(overlayGlassColor);
        homeOverlay.setBackgroundColor(bgColor);

        etSearchUrl.setTextColor(textColor);
        etSearchUrl.setHintTextColor(hintColor);
        btnBack.setColorFilter(textColor);
        btnForward.setColorFilter(textColor);
        btnGo.setColorFilter(textColor);
        btnMenu.setColorFilter(textColor);
        btnFullscreenToggle.setTextColor(textColor);

        TextView tvHomeTitle = findViewById(R.id.tvHomeTitle);
        if (tvHomeTitle != null) tvHomeTitle.setTextColor(textColor);

        TextView btnAddNewTab = findViewById(R.id.btnAddNewTab);
        ImageView btnCloseTabsOverlay = findViewById(R.id.btnCloseTabsOverlay);
        TextView tvDownloadsTitle = findViewById(R.id.tvDownloadsTitle);
        ImageView btnCloseDownloadsOverlay = findViewById(R.id.btnCloseDownloadsOverlay);
        TextView btnAllDownloads = findViewById(R.id.btnAllDownloads);

        btnAddNewTab.setTextColor(accentTextColor);
        btnAddNewTab.setBackgroundTintList(ColorStateList.valueOf(accentBgColor));
        btnCloseTabsOverlay.setColorFilter(textColor);
        tvDownloadsTitle.setTextColor(textColor);
        btnCloseDownloadsOverlay.setColorFilter(textColor);
        btnAllDownloads.setTextColor(textColor);
        btnAllDownloads.setBackgroundTintList(ColorStateList.valueOf(buttonBgColor));
    }

    @Override
    protected void onDestroy() {
        for (TabInfo t : tabs) {
            if (t.webView != null) {
                t.webView.clearHistory();
                t.webView.clearCache(true);
                t.webView.clearFormData();
                t.webView.loadUrl("about:blank");
                t.webView.destroy();
            }
            if (t.preview != null) t.preview.recycle();
        }
        tabs.clear();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();
        super.onDestroy();
    }
}