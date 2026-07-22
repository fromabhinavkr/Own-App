package com.abhinav.ownapp;

import android.app.AlertDialog; import android.content.Intent; import android.content.SharedPreferences; import android.content.res.ColorStateList; import android.graphics.Color; import android.graphics.drawable.GradientDrawable; import android.os.Bundle; import android.view.View;
import android.widget.LinearLayout; import android.widget.TextView; import androidx.appcompat.app.AppCompatActivity;

@SuppressWarnings("all")
public class UtilitiesGalleryActivity extends AppCompatActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_utilities_gallery);

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE); boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7"); int cardColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        int textColor = isDarkTheme ? Color.WHITE : Color.BLACK; int subtitleColor = isDarkTheme ? Color.parseColor("#AAAAAA") : Color.parseColor("#555555");
        int divColor = isDarkTheme ? Color.parseColor("#33FFFFFF") : Color.parseColor("#1A000000");

        LinearLayout root = findViewById(R.id.utilitiesGalleryRoot); TextView title = findViewById(R.id.tvUtilitiesTitle); TextView subtitle = findViewById(R.id.tvUtilitiesSubtitle);
        LinearLayout cardBrowser = findViewById(R.id.cardPrivateBrowser); TextView textBrowser = findViewById(R.id.textPrivateBrowser); View divBrowser = findViewById(R.id.divPrivateBrowser);
        LinearLayout cardDocReader = findViewById(R.id.cardDocReader); TextView textDocReader = findViewById(R.id.textDocReader); View divDocReader = findViewById(R.id.divDocReader);

        root.setBackgroundColor(bgColor); title.setTextColor(textColor); subtitle.setTextColor(subtitleColor);
        cardBrowser.setBackgroundTintList(ColorStateList.valueOf(cardColor)); textBrowser.setTextColor(textColor); divBrowser.setBackgroundColor(divColor);
        cardDocReader.setBackgroundTintList(ColorStateList.valueOf(cardColor)); textDocReader.setTextColor(textColor); divDocReader.setBackgroundColor(divColor);

        TextView btnBrowserHelp = findViewById(R.id.btnBrowserHelp);
        if (btnBrowserHelp != null) {
            GradientDrawable helpGd = new GradientDrawable(); helpGd.setShape(GradientDrawable.OVAL); helpGd.setColor(isDarkTheme ? Color.parseColor("#4A90E2") : Color.parseColor("#007AFF"));
            btnBrowserHelp.setBackground(helpGd); btnBrowserHelp.setTextColor(Color.WHITE);
            btnBrowserHelp.setOnClickListener(v -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(UtilitiesGalleryActivity.this, R.style.ModernDialogStyle); builder.setTitle("Privacy & Network Notice");
                LinearLayout dialogLayout = new LinearLayout(UtilitiesGalleryActivity.this); dialogLayout.setOrientation(LinearLayout.VERTICAL); dialogLayout.setPadding(60, 40, 60, 40);
                TextView message = new TextView(UtilitiesGalleryActivity.this); message.setText("This tool requires an active Internet connection to function. In accordance with strict privacy standards, Own does not collect, track, or store any of your browsing history, personal data, or usage metrics.");
                message.setTextColor(textColor); message.setTextSize(16f); message.setLineSpacing(0, 1.2f); dialogLayout.addView(message);
                builder.setView(dialogLayout); builder.setPositiveButton("Understood", null);
                AlertDialog dialog = builder.create();
                dialog.setOnShowListener(d -> {
                    if (dialog.getWindow() != null) {
                        GradientDrawable gd = new GradientDrawable(); gd.setColor(cardColor); gd.setCornerRadius(60f); dialog.getWindow().getDecorView().setBackground(gd);
                        int titleId = UtilitiesGalleryActivity.this.getResources().getIdentifier("alertTitle", "id", "android"); TextView titleView = dialog.findViewById(titleId); if (titleView != null) titleView.setTextColor(textColor);
                        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#4A90E2"));
                    }
                });
                dialog.show();
            });
        }

        cardBrowser.setOnClickListener(v -> startActivity(new Intent(UtilitiesGalleryActivity.this, PrivateBrowserActivity.class)));
        cardDocReader.setOnClickListener(v -> startActivity(new Intent(UtilitiesGalleryActivity.this, DocReaderActivity.class)));
    }
}