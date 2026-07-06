package com.example.ownphotoonwall;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
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

        // Apply colors to root and headers
        findViewById(R.id.galleryRoot).setBackgroundColor(rootBgColor);

        TextView tvTitle = findViewById(R.id.tvGalleryTitle);
        tvTitle.setTextColor(titleColor);

        TextView tvSubtitle = findViewById(R.id.tvGallerySubtitle);
        tvSubtitle.setTextColor(subtitleColor);

        // Link the layout cards
        LinearLayout cardDrawing = findViewById(R.id.cardDrawing);
        LinearLayout cardMaze = findViewById(R.id.cardMaze);
        LinearLayout cardSnake = findViewById(R.id.cardSnake);
        LinearLayout cardWater = findViewById(R.id.cardWater);
        LinearLayout cardGear = findViewById(R.id.cardGear);
        LinearLayout cardCoin = findViewById(R.id.cardCoin);
        LinearLayout cardHourglass = findViewById(R.id.cardHourglass);
        LinearLayout cardPhoto = findViewById(R.id.cardPhoto);

        // Link the text inside the cards
        TextView textDrawing = findViewById(R.id.textDrawing);
        TextView textMaze = findViewById(R.id.textMaze);
        TextView textSnake = findViewById(R.id.textSnake);
        TextView textWater = findViewById(R.id.textWater);
        TextView textGear = findViewById(R.id.textGear);
        TextView textCoin = findViewById(R.id.textCoin);
        TextView textHourglass = findViewById(R.id.textHourglass);
        TextView textPhoto = findViewById(R.id.textPhoto);

        // Arrays to loop through and apply the modern theme colors
        LinearLayout[] cards = {cardDrawing, cardMaze, cardSnake, cardWater, cardGear, cardCoin, cardHourglass, cardPhoto};
        TextView[] textViews = {textDrawing, textMaze, textSnake, textWater, textGear, textCoin, textHourglass, textPhoto};

        // Apply theme to every card and its text
        for (int i = 0; i < cards.length; i++) {
            cards[i].setBackgroundTintList(ColorStateList.valueOf(cardBgColor));
            textViews[i].setTextColor(titleColor);
        }

        // --- WIDGET PINNING LOGIC ---
        cardDrawing.setOnClickListener(v -> requestToPinWidget(DrawingWidgetProvider.class));
        cardMaze.setOnClickListener(v -> requestToPinWidget(MazeWidgetProvider.class));
        cardSnake.setOnClickListener(v -> requestToPinWidget(SnakeWidget.class));
        cardWater.setOnClickListener(v -> requestToPinWidget(WaterWidgetProvider.class));
        cardGear.setOnClickListener(v -> requestToPinWidget(GearWidgetProvider.class));
        cardCoin.setOnClickListener(v -> requestToPinWidget(CoinWidget.class));
        cardHourglass.setOnClickListener(v -> requestToPinWidget(HourglassWidget.class));
        cardPhoto.setOnClickListener(v -> requestToPinWidget(Photowidget.class));
    }

    private void requestToPinWidget(Class<?> widgetProviderClass) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                ComponentName myProvider = new ComponentName(this, widgetProviderClass);

                Intent pinnedWidgetCallbackIntent = new Intent();
                PendingIntent successCallback = PendingIntent.getBroadcast(
                        this,
                        0,
                        pinnedWidgetCallbackIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                appWidgetManager.requestPinAppWidget(myProvider, null, successCallback);
            } else {
                Toast.makeText(this, "Your phone's launcher doesn't support automatic widget pinning.", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "This feature requires Android 8.0 or higher.", Toast.LENGTH_SHORT).show();
        }
    }
}