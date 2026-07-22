package com.abhinav.ownapp;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class WidgetGalleryActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_gallery);

        // --- THEME SYNC LOGIC ---
        // Read the global dark mode preference set in MainActivity
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        // Define colors based on the theme
        int rootBgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
        int cardBgColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.WHITE;
        int titleColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");
        int subtitleColor = isDarkTheme ? Color.parseColor("#8E8E93") : Color.parseColor("#888888");
        int dividerColor = isDarkTheme ? Color.parseColor("#33FFFFFF") : Color.parseColor("#1A000000");

        // Apply colors to root and headers
        findViewById(R.id.galleryRoot).setBackgroundColor(rootBgColor);

        TextView tvTitle = findViewById(R.id.tvGalleryTitle);
        tvTitle.setTextColor(titleColor);

        TextView tvSubtitle = findViewById(R.id.tvGallerySubtitle);
        tvSubtitle.setTextColor(subtitleColor);

        // Link the layout cards
        LinearLayout cardDrawing = findViewById(R.id.cardDrawing);
        LinearLayout cardPhoto = findViewById(R.id.cardPhoto);
        LinearLayout cardMaze = findViewById(R.id.cardMaze);
        LinearLayout cardSnake = findViewById(R.id.cardSnake);
        LinearLayout cardWater = findViewById(R.id.cardWater);
        LinearLayout cardGear = findViewById(R.id.cardGear);
        LinearLayout cardCoin = findViewById(R.id.cardCoin);
        LinearLayout cardHourglass = findViewById(R.id.cardHourglass);

        // Link the text inside the cards
        TextView textDrawing = findViewById(R.id.textDrawing);
        TextView textPhoto = findViewById(R.id.textPhoto);
        TextView textMaze = findViewById(R.id.textMaze);
        TextView textSnake = findViewById(R.id.textSnake);
        TextView textWater = findViewById(R.id.textWater);
        TextView textGear = findViewById(R.id.textGear);
        TextView textCoin = findViewById(R.id.textCoin);
        TextView textHourglass = findViewById(R.id.textHourglass);

        // Link the horizontal dividers inside the cards
        View divDrawing = findViewById(R.id.divDrawing);
        View divPhoto = findViewById(R.id.divPhoto);
        View divMaze = findViewById(R.id.divMaze);
        View divSnake = findViewById(R.id.divSnake);
        View divWater = findViewById(R.id.divWater);
        View divGear = findViewById(R.id.divGear);
        View divCoin = findViewById(R.id.divCoin);
        View divHourglass = findViewById(R.id.divHourglass);

        // Arrays to loop through and apply the modern theme colors
        LinearLayout[] cards = {cardDrawing, cardPhoto, cardMaze, cardSnake, cardWater, cardGear, cardCoin, cardHourglass};
        TextView[] textViews = {textDrawing, textPhoto, textMaze, textSnake, textWater, textGear, textCoin, textHourglass};
        View[] dividers = {divDrawing, divPhoto, divMaze, divSnake, divWater, divGear, divCoin, divHourglass};

        // Apply theme to every card, text, and divider
        for (int i = 0; i < cards.length; i++) {
            cards[i].setBackgroundTintList(ColorStateList.valueOf(cardBgColor));
            textViews[i].setTextColor(titleColor);
            dividers[i].setBackgroundColor(dividerColor);
        }

        // --- WIDGET PINNING LOGIC ---
        cardDrawing.setOnClickListener(v -> requestToPinWidget(DrawingWidgetProvider.class));
        cardPhoto.setOnClickListener(v -> requestToPinWidget(Photowidget.class));
        cardMaze.setOnClickListener(v -> requestToPinWidget(MazeWidgetProvider.class));
        cardSnake.setOnClickListener(v -> requestToPinWidget(SnakeWidget.class));
        cardWater.setOnClickListener(v -> requestToPinWidget(WaterWidgetProvider.class));
        cardGear.setOnClickListener(v -> requestToPinWidget(GearWidgetProvider.class));
        cardCoin.setOnClickListener(v -> requestToPinWidget(CoinWidget.class));
        cardHourglass.setOnClickListener(v -> requestToPinWidget(HourglassWidget.class));
    }

    private void requestToPinWidget(Class<?> widgetProviderClass) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                ComponentName myProvider = new ComponentName(this, widgetProviderClass);
                // System UI handles the confirmation dialog, no toast needed.
                appWidgetManager.requestPinAppWidget(myProvider, null, null);
            } else {
                Toast.makeText(this, "Your phone's launcher doesn't support automatic widget pinning. Please add it manually.", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "This feature requires Android 8.0 or higher. Please add manually.", Toast.LENGTH_SHORT).show();
        }
    }
}