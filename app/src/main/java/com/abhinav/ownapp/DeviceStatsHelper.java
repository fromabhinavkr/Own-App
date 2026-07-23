package com.abhinav.ownapp;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.util.Locale;

public class DeviceStatsHelper {

    public static void setupDashboard(Activity activity, boolean isDarkTheme) {
        // --- 1. LINK VIEWS ---
        View ramCardBg = activity.findViewById(R.id.ramCardBg);
        View storageBatteryCardBg = activity.findViewById(R.id.storageBatteryCardBg);

        RamGraphView ramGraphView = activity.findViewById(R.id.ramGraphView);
        TextView tvRamTotal = activity.findViewById(R.id.tvRamTotal);
        TextView tvRamUsed = activity.findViewById(R.id.tvRamUsed);
        TextView tvRamFree = activity.findViewById(R.id.tvRamFree);

        TextView tvStorageTitle = activity.findViewById(R.id.tvStorageTitle);
        TextView tvStorageVal = activity.findViewById(R.id.tvStorageValue);
        ProgressBar pbStorage = activity.findViewById(R.id.pbStorage);

        TextView tvBatteryTitle = activity.findViewById(R.id.tvBatteryTitle);
        TextView tvBatteryVal = activity.findViewById(R.id.tvBatteryValue);
        ProgressBar pbBattery = activity.findViewById(R.id.pbBattery);

        if (tvStorageTitle == null) return; // Failsafe

        // --- 2. APPLY DARK/LIGHT MODE COLORS ---
        int textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#1C1C1E");
        int subTextColor = isDarkTheme ? Color.parseColor("#B0B0B8") : Color.parseColor("#666666");

        int cardBgColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.parseColor("#F0F0F5");

        ColorStateList trackColor = ColorStateList.valueOf(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA"));
        ColorStateList progressColor = ColorStateList.valueOf(Color.parseColor("#4A90E2"));

        // Safely tint the XML backgrounds to preserve clipToOutline
        if (ramCardBg != null) {
            ramCardBg.setBackgroundTintList(ColorStateList.valueOf(cardBgColor));
        }
        if (storageBatteryCardBg != null) {
            storageBatteryCardBg.setBackgroundTintList(ColorStateList.valueOf(cardBgColor));
        }

        // Apply Text Colors
        tvRamTotal.setTextColor(textColor);
        tvRamUsed.setTextColor(textColor);
        tvRamFree.setTextColor(subTextColor);

        TextView[] titles = {tvStorageTitle, tvBatteryTitle};
        TextView[] values = {tvStorageVal, tvBatteryVal};
        ProgressBar[] bars = {pbStorage, pbBattery};

        for (TextView t : titles) t.setTextColor(textColor);
        for (TextView v : values) v.setTextColor(subTextColor);
        for (ProgressBar b : bars) {
            b.setProgressBackgroundTintList(trackColor);
            b.setProgressTintList(progressColor);
        }

        if (ramGraphView != null) ramGraphView.setTheme(isDarkTheme);

        // --- 3. REAL-TIME RAM LOGIC ---
        ActivityManager actManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();

        Handler handler = new Handler(Looper.getMainLooper());
        Runnable ramUpdater = new Runnable() {
            @Override
            public void run() {
                if (ramGraphView == null || !ramGraphView.isAttachedToWindow()) return;

                actManager.getMemoryInfo(memInfo);
                long totalRamMB = memInfo.totalMem / 1048576L;
                long availRamMB = memInfo.availMem / 1048576L;
                long usedRamMB = totalRamMB - availRamMB;
                float percentUsed = ((float) usedRamMB / totalRamMB) * 100f;

                tvRamTotal.setText(String.format(Locale.US, "RAM - %d MB Total", totalRamMB));
                tvRamUsed.setText(String.format(Locale.US, "%d MB Used", usedRamMB));
                tvRamFree.setText(String.format(Locale.US, "%d MB Free", availRamMB));

                ramGraphView.addRamData(percentUsed);
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(ramUpdater);

        // --- 4. STORAGE LOGIC ---
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        long totalBlocks = stat.getBlockCountLong();

        double rawAvailStorageGb = (availableBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0);
        double rawTotalStorageGb = (totalBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0);

        int advertisedStorageGb = 8;
        int[] storageTiers = {8, 16, 32, 64, 128, 256, 512, 1024};
        for (int tier : storageTiers) {
            if (rawTotalStorageGb <= tier) {
                advertisedStorageGb = tier;
                break;
            }
        }
        double usedStorageGb = advertisedStorageGb - rawAvailStorageGb;
        tvStorageVal.setText(String.format(Locale.US, "%.1fGB Used", usedStorageGb));
        pbStorage.setMax(advertisedStorageGb * 10);
        pbStorage.setProgress((int) (usedStorageGb * 10));

        // --- 5. BATTERY LOGIC ---
        BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                float batteryPct = level * 100 / (float) scale;
                tvBatteryVal.setText((int) batteryPct + "%");
                pbBattery.setProgress((int) batteryPct);
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        activity.registerReceiver(batteryReceiver, filter);
    }
}