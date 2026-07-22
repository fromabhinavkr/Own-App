package com.abhinav.ownapp;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class ImagePickerActivity extends AppCompatActivity {

    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && mAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    try {
                        // 1. Grant persistent access
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        // 2. Save URI
                        getSharedPreferences("WidgetPrefs", MODE_PRIVATE)
                                .edit()
                                .putString("image_path_" + mAppWidgetId, uri.toString())
                                .apply();

                        // 3. Trigger an explicit update for this widget
                        Intent updateIntent = new Intent(this, Photowidget.class);
                        updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{mAppWidgetId});
                        sendBroadcast(updateIntent);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                finish();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAppWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        pickImageLauncher.launch("image/*");
    }
}