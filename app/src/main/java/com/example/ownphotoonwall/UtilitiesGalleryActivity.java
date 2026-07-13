package com.example.ownphotoonwall;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class UtilitiesGalleryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_utilities_gallery);

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        LinearLayout root = findViewById(R.id.utilitiesGalleryRoot);
        TextView title = findViewById(R.id.tvUtilitiesTitle);
        TextView subtitle = findViewById(R.id.tvUtilitiesSubtitle);

        LinearLayout cardBrowser = findViewById(R.id.cardPrivateBrowser);
        TextView textBrowser = findViewById(R.id.textPrivateBrowser);
        ImageView ivBrowserPreview = findViewById(R.id.ivBrowserPreview);

        if (isDarkTheme) {
            root.setBackgroundColor(Color.parseColor("#1C1C1E"));
            title.setTextColor(Color.WHITE);
            subtitle.setTextColor(Color.parseColor("#AAAAAA"));

            cardBrowser.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2C2C2E")));
            textBrowser.setTextColor(Color.WHITE);
            // REMOVED ivBrowserPreview.setColorFilter() SO LOGO KEEPS ITS COLORS!
        } else {
            root.setBackgroundColor(Color.parseColor("#F2F2F7"));
            title.setTextColor(Color.BLACK);
            subtitle.setTextColor(Color.parseColor("#555555"));

            cardBrowser.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            textBrowser.setTextColor(Color.BLACK);
            // REMOVED ivBrowserPreview.setColorFilter() SO LOGO KEEPS ITS COLORS!
        }

        cardBrowser.setOnClickListener(v -> startActivity(new Intent(UtilitiesGalleryActivity.this, PrivateBrowserActivity.class)));
    }
}