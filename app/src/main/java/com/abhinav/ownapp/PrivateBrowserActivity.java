package com.abhinav.ownapp;

import android.animation.ValueAnimator;
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
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.os.SystemClock;
import android.text.TextUtils;
import android.transition.AutoTransition;
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
    private LinearLayout urlInputContainer; // Inner Pill Container
    private EditText etSearchUrl;
    private ProgressBar progressBar;
    private ProgressBar pageLoadIndicator; // Green loading ring

    // --- 3-STATE THEME VARIABLES ---
    private boolean isDarkTheme;
    private int themeState;

    private ImageView btnBack, btnForward, btnGo, btnMenu, ivAutoScrollIcon, btnDismissSearch;
    private FrameLayout btnAutoScroll;
    private ProgressBar autoActionIndicator;
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

    // --- AUTO SCROLL & SWIPE VARIABLES ---
    private final Handler autoScrollHandler = new Handler(Looper.getMainLooper());
    private int currentAutoScrollSpeed = 0;
    private final Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            WebView current = getCurrentWeb();
            if (current != null && currentAutoScrollSpeed > 0) {
                current.scrollBy(0, currentAutoScrollSpeed);
                autoScrollHandler.postDelayed(this, 16); // ~60 FPS smooth scrolling
            }
        }
    };

    private boolean isAutoSwiping = false;
    private final Handler autoSwipeHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoSwipeRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAutoSwiping) {
                simulateSwipeUp();
                autoSwipeHandler.postDelayed(this, 4000); // 4 seconds interval for Shorts/Reels
            }
        }
    };

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
        urlInputContainer = findViewById(R.id.urlInputContainer);
        etSearchUrl = findViewById(R.id.etSearchUrl);
        progressBar = findViewById(R.id.browserProgressBar);
        pageLoadIndicator = findViewById(R.id.pageLoadIndicator);
        btnBack = findViewById(R.id.btnBrowserBack);
        btnForward = findViewById(R.id.btnBrowserForward);
        btnGo = findViewById(R.id.btnBrowserGo);
        btnMenu = findViewById(R.id.btnBrowserMenu);
        btnAutoScroll = findViewById(R.id.btnAutoScroll);
        ivAutoScrollIcon = findViewById(R.id.ivAutoScrollIcon);
        autoActionIndicator = findViewById(R.id.autoActionIndicator);
        btnFullscreenToggle = findViewById(R.id.btnFullscreenToggle);
        btnDismissSearch = findViewById(R.id.btnDismissSearch);
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

        // --- BUTTER SMOOTH EXPANSION LOGIC ---
        etSearchUrl.setOnFocusChangeListener((v, hasFocus) -> {
            android.transition.TransitionSet transition = new android.transition.TransitionSet();
            transition.addTransition(new android.transition.ChangeBounds());
            transition.addTransition(new android.transition.Fade());
            transition.setDuration(300); // 300ms gives a buttery glide
            transition.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            TransitionManager.beginDelayedTransition((ViewGroup) rootLayout, transition);

            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) searchCapsule.getLayoutParams();
            int margin24dp = dp(24);
            GradientDrawable gd = (GradientDrawable) searchCapsule.getBackground();
            GradientDrawable urlGd = (GradientDrawable) urlInputContainer.getBackground();

            if (hasFocus) {
                params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                params.topMargin = margin24dp;
                params.bottomMargin = 0;

                etSearchUrl.setMaxLines(6);

                // Hide sibling tools
                btnBack.setVisibility(View.GONE);
                btnForward.setVisibility(View.GONE);
                btnMenu.setVisibility(View.GONE);
                btnAutoScroll.setVisibility(View.GONE);
                btnFullscreenToggle.setVisibility(View.GONE);

                btnDismissSearch.setVisibility(View.VISIBLE);

                animateCornerRadius(gd, dp(100), dp(24));
                animateCornerRadius(urlGd, dp(100), dp(16)); // Inner pill softens

                etSearchUrl.postDelayed(() -> {
                    etSearchUrl.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(etSearchUrl, InputMethodManager.SHOW_IMPLICIT);
                }, 300);
            } else {
                params.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                params.topMargin = 0;
                params.bottomMargin = margin24dp;

                etSearchUrl.setMaxLines(1);

                int vis = isFullscreen ? View.GONE : View.VISIBLE;
                btnBack.setVisibility(vis);
                btnForward.setVisibility(vis);

                // PERFECT FIX: Properly restore the container visibility!
                urlInputContainer.setVisibility(vis);

                btnMenu.setVisibility(vis);
                btnAutoScroll.setVisibility(vis);
                btnFullscreenToggle.setVisibility(View.VISIBLE);

                btnDismissSearch.setVisibility(View.GONE);

                animateCornerRadius(gd, dp(24), dp(100));
                animateCornerRadius(urlGd, dp(16), dp(100)); // Inner pill rounds perfectly back
            }
            searchCapsule.setLayoutParams(params);
        });

        btnDismissSearch.setOnClickListener(v -> {
            etSearchUrl.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etSearchUrl.getWindowToken(), 0);
        });

        btnBack.setOnClickListener(v -> {
            if (getCurrentWeb() != null && getCurrentWeb().canGoBack()) getCurrentWeb().goBack();
        });

        btnForward.setOnClickListener(v -> {
            if (getCurrentWeb() != null && getCurrentWeb().canGoForward()) getCurrentWeb().goForward();
        });

        btnGo.setOnClickListener(v -> loadUrlOrSearch());

        btnMenu.setOnClickListener(this::showRoundedMenu);

        btnAutoScroll.setOnClickListener(this::showAutoScrollMenu);

        btnFullscreenToggle.setOnClickListener(v -> toggleFullscreenCapsule());

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
                        int margin = dp(16);
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

    private void animateCornerRadius(GradientDrawable drawable, float startRadius, float endRadius) {
        ValueAnimator animator = ValueAnimator.ofFloat(startRadius, endRadius);
        animator.setDuration(300);
        animator.addUpdateListener(animation -> drawable.setCornerRadius((float) animation.getAnimatedValue()));
        animator.start();
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
        } else { // Star Mode
            cardBg = Color.parseColor("#141414");
            textColor = Color.WHITE;
            secondaryColor = Color.parseColor("#888888");
            iconBg = Color.parseColor("#242424");
        }

        addHomeShortcut("Google", "Secure Search", "https://www.google.com/", "G", cardBg, textColor, secondaryColor, iconBg, false, -1);
        addHomeShortcut("DuckDuckGo", "Secure Search", "https://duckduckgo.com/", "D", cardBg, textColor, secondaryColor, iconBg, false, -1);
        addHomeShortcut("Yahoo", "Secure Search", "https://search.yahoo.com/", "Y", cardBg, textColor, secondaryColor, iconBg, false, -1);
        addHomeShortcut("Instagram", "Social Media", "https://www.instagram.com/", "IG", cardBg, textColor, secondaryColor, iconBg, false, -1);
        addHomeShortcut("LinkedIn", "Professional", "https://www.linkedin.com/", "in", cardBg, textColor, secondaryColor, iconBg, false, -1);
        addHomeShortcut("GitHub", "Development", "https://github.com/", "GH", cardBg, textColor, secondaryColor, iconBg, false, -1);
        addHomeShortcut("YouTube", "Video", "https://www.youtube.com/", "▶", cardBg, textColor, secondaryColor, iconBg, false, -1);
        addHomeShortcut("Bing", "Secure Search", "https://search.bing.com/", "B", cardBg, textColor, secondaryColor, iconBg, false, -1);

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
        cardDrawable.setCornerRadius(dp(100));
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
        cardDrawable.setCornerRadius(dp(100));
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

    // --- FLAWLESS CAPSULE SHRINK LOGIC ---
    private void toggleFullscreenCapsule() {
        etSearchUrl.clearFocus();
        isFullscreen = !isFullscreen;
        int visibility = isFullscreen ? View.GONE : View.VISIBLE;

        btnBack.setVisibility(visibility);
        btnForward.setVisibility(visibility);

        // PERFECT FIX: We hide the entire nested URL container!
        // This flawlessly removes the green loading indicator when the capsule shrinks.
        urlInputContainer.setVisibility(visibility);

        btnMenu.setVisibility(visibility);
        btnAutoScroll.setVisibility(visibility);

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
            info.webView.loadUrl("");
            showHomePage();
        }
    }

    private void switchTab(int index) {
        if (index < 0 || index >= tabs.size()) return;

        stopAutoActions(); // Pause any automated scrolling/swiping on tab switch

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

        int outerUnselectedBg, outerSelectedBg, innerBg, textColor, separatorColor;

        if (themeState == 0) { // Light Mode
            outerUnselectedBg = Color.parseColor("#E5E5EA");
            outerSelectedBg = Color.parseColor("#FFB59F");
            innerBg = Color.WHITE;
            textColor = Color.BLACK;
            separatorColor = Color.BLACK;
        } else if (themeState == 1) { // Standard Dark Mode
            outerUnselectedBg = Color.parseColor("#332D2B");
            outerSelectedBg = Color.parseColor("#FFB59F");
            innerBg = Color.parseColor("#1C1C1E");
            textColor = Color.WHITE;
            separatorColor = Color.parseColor("#555555");
        } else { // Star Mode
            outerUnselectedBg = Color.parseColor("#2C2C2E");
            outerSelectedBg = Color.parseColor("#FFB59F");
            innerBg = Color.parseColor("#000000");
            textColor = Color.WHITE;
            separatorColor = Color.parseColor("#333333");
        }

        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            TabInfo info = tabs.get(i);

            LinearLayout outerCard = new LinearLayout(this);
            outerCard.setOrientation(LinearLayout.VERTICAL);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(8), dp(8), dp(8), dp(8));
            outerCard.setLayoutParams(params);

            GradientDrawable outerGd = new GradientDrawable();
            outerGd.setColor(index == currentTabIndex ? outerSelectedBg : outerUnselectedBg);
            outerGd.setCornerRadius(dp(16));
            outerCard.setBackground(outerGd);
            outerCard.setPadding(dp(6), dp(10), dp(6), dp(6));

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(4), 0, dp(4), dp(6));

            TextView title = new TextView(this);
            title.setText(info.title);
            title.setTextColor(textColor);
            title.setTextSize(13f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView close = new TextView(this);
            close.setText("X");
            close.setTextColor(textColor);
            close.setTextSize(15f);
            close.setTypeface(null, android.graphics.Typeface.BOLD);
            close.setPadding(dp(10), 0, 0, 0);
            close.setOnClickListener(v -> closeTab(index));

            header.addView(title);
            header.addView(close);

            View separator = new View(this);
            LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            sepParams.setMargins(dp(2), 0, dp(2), dp(6));
            separator.setLayoutParams(sepParams);
            separator.setBackgroundColor(separatorColor);

            ImageView preview = new ImageView(this);
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160));
            preview.setLayoutParams(previewParams);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);

            GradientDrawable innerGd = new GradientDrawable();
            innerGd.setColor(innerBg);
            innerGd.setCornerRadius(dp(10));
            preview.setBackground(innerGd);
            preview.setClipToOutline(true);

            if (info.preview != null) {
                preview.setImageBitmap(info.preview);
            }

            outerCard.addView(header);
            outerCard.addView(separator);
            outerCard.addView(preview);

            outerCard.setOnClickListener(v -> switchTab(index));
            tabsGrid.addView(outerCard);
        }
    }

    private void simulateSwipeUp() {
        WebView web = getCurrentWeb();
        if (web == null) return;

        long downTime = SystemClock.uptimeMillis();
        long eventTime = downTime;

        float x = web.getWidth() / 2.0f;
        float yStart = web.getHeight() * 0.8f;
        float yEnd = web.getHeight() * 0.2f;

        MotionEvent downEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, x, yStart, 0);
        web.dispatchTouchEvent(downEvent);
        downEvent.recycle();

        int steps = 15;
        for (int i = 1; i <= steps; i++) {
            eventTime += 10;
            float y = yStart - ((yStart - yEnd) * (i / (float) steps));
            MotionEvent moveEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, x, y, 0);
            web.dispatchTouchEvent(moveEvent);
            moveEvent.recycle();
        }

        eventTime += 10;
        MotionEvent upEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, x, yEnd, 0);
        web.dispatchTouchEvent(upEvent);
        upEvent.recycle();
    }

    private void stopAutoActions() {
        currentAutoScrollSpeed = 0;
        isAutoSwiping = false;
        autoScrollHandler.removeCallbacks(autoScrollRunnable);
        autoSwipeHandler.removeCallbacks(autoSwipeRunnable);
        autoActionIndicator.setVisibility(View.GONE);
    }

    private void startAutoScroll(int speedMultiplier) {
        stopAutoActions();
        currentAutoScrollSpeed = speedMultiplier;
        if (speedMultiplier > 0) {
            autoActionIndicator.setVisibility(View.VISIBLE);
            autoScrollHandler.post(autoScrollRunnable);
        }
    }

    private void startAutoSwipe() {
        stopAutoActions();
        isAutoSwiping = true;
        autoActionIndicator.setVisibility(View.VISIBLE);
        autoSwipeHandler.postDelayed(autoSwipeRunnable, 4000);
        Toast.makeText(this, "Auto Swipe Activated (4s)", Toast.LENGTH_SHORT).show();
    }

    private void showAutoScrollMenu(View anchor) {
        etSearchUrl.clearFocus();
        LinearLayout menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);

        menuLayout.setMinimumWidth(dp(220));

        int bgColor, textColor;
        if (themeState == 0) { // Light
            bgColor = Color.parseColor("#99FFFFFF");
            textColor = Color.BLACK;
        } else if (themeState == 1) { // Standard Dark
            bgColor = Color.parseColor("#992C2C2E");
            textColor = Color.WHITE;
        } else { // Star Mode
            bgColor = Color.parseColor("#E61C1C1E");
            textColor = Color.WHITE;
        }

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(dp(40));
        menuLayout.setBackground(gd);
        menuLayout.setPadding(dp(16), dp(16), dp(16), dp(16));

        final PopupWindow[] popupWindow = new PopupWindow[1];

        TextView tvStop = createMenuItem("Stop All", android.R.drawable.ic_media_pause, textColor);
        tvStop.setOnClickListener(v -> {
            popupWindow[0].dismiss();
            stopAutoActions();
        });

        TextView tvSwipe = createMenuItem("Auto Swipe (4s)", android.R.drawable.ic_menu_upload, textColor);
        tvSwipe.setOnClickListener(v -> {
            popupWindow[0].dismiss();
            startAutoSwipe();
        });

        TextView tv1x = createMenuItem("1x Speed", android.R.drawable.ic_media_play, textColor);
        tv1x.setOnClickListener(v -> {
            popupWindow[0].dismiss();
            startAutoScroll(1);
        });

        TextView tv2x = createMenuItem("2x Speed", android.R.drawable.ic_media_ff, textColor);
        tv2x.setOnClickListener(v -> {
            popupWindow[0].dismiss();
            startAutoScroll(3);
        });

        TextView tv3x = createMenuItem("3x Speed", android.R.drawable.ic_media_ff, textColor);
        tv3x.setOnClickListener(v -> {
            popupWindow[0].dismiss();
            startAutoScroll(6);
        });

        TextView tv4x = createMenuItem("4x Speed", android.R.drawable.ic_media_ff, textColor);
        tv4x.setOnClickListener(v -> {
            popupWindow[0].dismiss();
            startAutoScroll(12);
        });

        menuLayout.addView(tvStop);
        menuLayout.addView(tvSwipe);
        menuLayout.addView(tv1x);
        menuLayout.addView(tv2x);
        menuLayout.addView(tv3x);
        menuLayout.addView(tv4x);

        isMenuOpen = true;
        updateBackgroundBlur();
        popupWindow[0] = new PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow[0].setElevation(dp(30));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.transition.Transition enterTrans = new android.transition.Slide(Gravity.BOTTOM);
            enterTrans.setDuration(200);
            popupWindow[0].setEnterTransition(enterTrans);

            android.transition.Transition exitTrans = new android.transition.Fade();
            exitTrans.setDuration(150);
            popupWindow[0].setExitTransition(exitTrans);
        }

        popupWindow[0].setOnDismissListener(() -> {
            isMenuOpen = false;
            updateBackgroundBlur();
        });

        popupWindow[0].showAtLocation(anchor, Gravity.BOTTOM | Gravity.END, dp(20), dp(90));
    }

    private void showRoundedMenu(View anchor) {
        etSearchUrl.clearFocus();
        LinearLayout menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);

        menuLayout.setMinimumWidth(dp(220));

        int bgColor, textColor;
        if (themeState == 0) { // Light
            bgColor = Color.parseColor("#99FFFFFF");
            textColor = Color.BLACK;
        } else if (themeState == 1) { // Standard Dark
            bgColor = Color.parseColor("#992C2C2E");
            textColor = Color.WHITE;
        } else { // Star Mode
            bgColor = Color.parseColor("#E61C1C1E");
            textColor = Color.WHITE;
        }

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(dp(40));
        menuLayout.setBackground(gd);
        menuLayout.setPadding(dp(16), dp(16), dp(16), dp(16));

        final PopupWindow[] popupWindow = new PopupWindow[1];
        WebView current = getCurrentWeb();
        if (current == null) return;

        TextView tvHome = createMenuItem("Home", android.R.drawable.ic_menu_compass, textColor);
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
        popupWindow[0].setElevation(dp(30));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.transition.Transition enterTrans = new android.transition.Slide(Gravity.BOTTOM);
            enterTrans.setDuration(200);
            popupWindow[0].setEnterTransition(enterTrans);

            android.transition.Transition exitTrans = new android.transition.Fade();
            exitTrans.setDuration(150);
            popupWindow[0].setExitTransition(exitTrans);
        }

        popupWindow[0].setOnDismissListener(() -> {
            isMenuOpen = false;
            updateBackgroundBlur();
        });

        popupWindow[0].showAtLocation(anchor, Gravity.BOTTOM | Gravity.END, dp(20), dp(90));
    }

    private TextView createMenuItem(String text, int iconResId, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(16f);
        tv.setPadding(dp(16), dp(12), dp(16), dp(12));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setCompoundDrawablePadding(dp(16));

        if (iconResId != 0) {
            Drawable icon = androidx.core.content.ContextCompat.getDrawable(this, iconResId);
            if (icon != null) {
                icon = icon.mutate();
                int iconSize = dp(24);
                icon.setBounds(0, 0, iconSize, iconSize);

                icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                icon.setAlpha(200);

                tv.setCompoundDrawables(icon, null, null, null);
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
            dateHeader.setPadding(0, dp(12), 0, dp(4));
            downloadsList.addView(dateHeader);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, dp(8));
            row.setGravity(Gravity.CENTER_VERTICAL);

            ImageView thumb = new ImageView(this);
            thumb.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
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
            details.setPadding(dp(12), 0, 0, 0);
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
            menu.setPadding(dp(12), 0, dp(12), 0);
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
                    if (newProgress == 100) {
                        progressBar.setVisibility(View.GONE);
                        pageLoadIndicator.setVisibility(View.GONE); // Stop inner ring when done
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(newProgress);
                        pageLoadIndicator.setVisibility(View.VISIBLE); // Show inner ring loading
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
                if (view == getCurrentWeb()) {
                    pageLoadIndicator.setVisibility(View.VISIBLE); // Start ring when page starts
                }
                if (view == getCurrentWeb() && !isFullscreen) {
                    if (url == null || url.equals("about:blank") || url.startsWith("http://startpage") || url.isEmpty()) {
                        etSearchUrl.setText("");
                        showHomePage();
                    } else {
                        hideHomePage();
                        etSearchUrl.setText(url);
                    }
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (view == getCurrentWeb()) {
                    pageLoadIndicator.setVisibility(View.GONE); // Ensure ring stops if stuck
                }
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
        int bgColor, textColor, hintColor, buttonBgColor, accentBgColor, accentTextColor, capsuleGlassColor, overlayGlassColor, urlInnerColor;

        if (themeState == 0) { // Light Mode
            bgColor = Color.parseColor("#FFFFFF");
            textColor = Color.parseColor("#333333");
            hintColor = Color.parseColor("#A0A0A0");
            buttonBgColor = Color.WHITE;
            accentBgColor = Color.parseColor("#6750A4");
            accentTextColor = Color.WHITE;
            capsuleGlassColor = Color.parseColor("#D9FFFFFF");
            overlayGlassColor = Color.parseColor("#80FFFFFF");
            urlInnerColor = Color.parseColor("#E5E5EA"); // Distinct subtle grey pill
        } else if (themeState == 1) { // Standard Dark Mode
            bgColor = Color.parseColor("#1C1C1E");
            textColor = Color.WHITE;
            hintColor = Color.parseColor("#888888");
            buttonBgColor = Color.parseColor("#332D2B");
            accentBgColor = Color.parseColor("#FFB59F");
            accentTextColor = Color.parseColor("#000000");
            capsuleGlassColor = Color.parseColor("#D92C2C2E");
            overlayGlassColor = Color.parseColor("#801C1C1E");
            urlInnerColor = Color.parseColor("#141415"); // Deep dark pill
        } else { // Star Mode (AMOLED Pure Black)
            bgColor = Color.parseColor("#000000");
            textColor = Color.WHITE;
            hintColor = Color.parseColor("#888888");
            buttonBgColor = Color.parseColor("#1C1C1E");
            accentBgColor = Color.parseColor("#FFB59F");
            accentTextColor = Color.parseColor("#000000");
            capsuleGlassColor = Color.parseColor("#D91C1C1E");
            overlayGlassColor = Color.parseColor("#B3000000");
            urlInnerColor = Color.parseColor("#0A0A0A"); // Pure dark pill
        }

        getWindow().setStatusBarColor(bgColor);
        findViewById(R.id.browserRoot).setBackgroundColor(bgColor);

        // Theme Main Capsule
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(capsuleGlassColor);
        gd.setCornerRadius(dp(100)); // Default pill state
        searchCapsule.setBackground(gd);
        searchCapsule.setClipToOutline(true);

        // Theme Inner URL Pill
        if (urlInputContainer != null) {
            GradientDrawable urlGd = new GradientDrawable();
            urlGd.setColor(urlInnerColor);
            urlGd.setCornerRadius(dp(100));
            urlInputContainer.setBackground(urlGd);
            urlInputContainer.setClipToOutline(true);
        }

        tabsOverlay.setBackgroundColor(overlayGlassColor);
        downloadsOverlay.setBackgroundColor(overlayGlassColor);
        homeOverlay.setBackgroundColor(bgColor);

        etSearchUrl.setTextColor(textColor);
        etSearchUrl.setHintTextColor(hintColor);
        btnBack.setColorFilter(textColor);
        btnForward.setColorFilter(textColor);
        btnGo.setColorFilter(textColor);

        btnMenu.setColorFilter(textColor);
        ivAutoScrollIcon.setColorFilter(textColor);
        btnDismissSearch.setColorFilter(textColor); // Apply to Down Arrow

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
        stopAutoActions(); // Safely clear all callbacks
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