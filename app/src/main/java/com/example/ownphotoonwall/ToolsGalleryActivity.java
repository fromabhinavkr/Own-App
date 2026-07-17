package com.example.ownphotoonwall;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
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

        // The color for your new separator lines
        int dividerColor = isDarkTheme ? Color.parseColor("#33FFFFFF") : Color.parseColor("#1A000000");

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
        View divSticker = findViewById(R.id.divStickerMaker);

        if (cardSticker != null) {
            applyModernCardStyle(cardSticker, cardBgColor);
            if (textSticker != null) textSticker.setTextColor(titleColor);
            if (divSticker != null) divSticker.setBackgroundColor(dividerColor); // Paint the line

            cardSticker.setOnClickListener(v -> startActivity(new Intent(this, StickerMakerActivity.class)));
        }

        // 3. Setup Image Editor Card
        LinearLayout cardEditor = findViewById(R.id.cardImageEditor);
        TextView textEditor = findViewById(R.id.textImageEditor);
        View divEditor = findViewById(R.id.divImageEditor);

        if (cardEditor != null) {
            applyModernCardStyle(cardEditor, cardBgColor);
            if (textEditor != null) textEditor.setTextColor(titleColor);
            if (divEditor != null) divEditor.setBackgroundColor(dividerColor); // Paint the line

            cardEditor.setOnClickListener(v -> startActivity(new Intent(this, ImageEditorActivity.class)));
        }

        // 4. Setup PDF Studio Card
        LinearLayout cardPdf = findViewById(R.id.cardPdfStudio);
        TextView textPdf = findViewById(R.id.textPdfStudio);
        View divPdf = findViewById(R.id.divPdfStudio);

        if (cardPdf != null) {
            applyModernCardStyle(cardPdf, cardBgColor);
            if (textPdf != null) textPdf.setTextColor(titleColor);
            if (divPdf != null) divPdf.setBackgroundColor(dividerColor); // Paint the line

            cardPdf.setOnClickListener(v -> startActivity(new Intent(this, PdfStudioActivity.class)));
        }

        // 5. Setup Collage Studio Card (NEW)
        LinearLayout cardCollage = findViewById(R.id.cardCollageStudio);
        TextView textCollage = findViewById(R.id.textCollageStudio);
        View divCollage = findViewById(R.id.divCollageStudio);

        if (cardCollage != null) {
            applyModernCardStyle(cardCollage, cardBgColor);
            if (textCollage != null) textCollage.setTextColor(titleColor);
            if (divCollage != null) divCollage.setBackgroundColor(dividerColor); // Paint the line

            cardCollage.setOnClickListener(v -> startActivity(new Intent(this, CollageStudioActivity.class)));
        }
    }

    // Notice we removed the stroke (border) and the elevation (shadow) completely
    private void applyModernCardStyle(View card, int bgColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(50f); // Keeps the smooth rounded corners

        // No stroke and no elevation!
        card.setBackground(gd);
    }
}