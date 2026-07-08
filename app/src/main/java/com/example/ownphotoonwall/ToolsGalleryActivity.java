package com.example.ownphotoonwall;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ToolsGalleryActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tools_gallery);

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        int rootBgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
        int cardBgColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        int titleColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");
        int subtitleColor = isDarkTheme ? Color.parseColor("#8E8E93") : Color.parseColor("#888888");

        // 1. Root and Headers
        LinearLayout rootLayout = findViewById(R.id.toolsGalleryRoot);
        TextView tvTitle = findViewById(R.id.tvToolsTitle);
        TextView tvSubtitle = findViewById(R.id.tvToolsSubtitle);

        if (rootLayout != null) rootLayout.setBackgroundColor(rootBgColor);
        if (tvTitle != null) tvTitle.setTextColor(titleColor);
        if (tvSubtitle != null) tvSubtitle.setTextColor(subtitleColor);

        // 2. Setup Sticker Maker Card
        LinearLayout cardSticker = findViewById(R.id.cardStickerMaker);
        TextView textSticker = findViewById(R.id.textStickerMaker);

        if (cardSticker != null) {
            cardSticker.setBackgroundTintList(ColorStateList.valueOf(cardBgColor));
            if (textSticker != null) textSticker.setTextColor(titleColor);

            cardSticker.setOnClickListener(v -> startActivity(new Intent(this, StickerMakerActivity.class)));
        }

        // 3. Setup Image Editor Card
        LinearLayout cardEditor = findViewById(R.id.cardImageEditor);
        TextView textEditor = findViewById(R.id.textImageEditor);

        if (cardEditor != null) {
            cardEditor.setBackgroundTintList(ColorStateList.valueOf(cardBgColor));
            if (textEditor != null) textEditor.setTextColor(titleColor);

            cardEditor.setOnClickListener(v -> startActivity(new Intent(this, ImageEditorActivity.class)));
        }
    }
}