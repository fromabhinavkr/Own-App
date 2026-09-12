package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import java.io.File;
import java.util.Locale;

@SuppressLint("SetTextI18n")
public class DeviceStatsHelper {

    private static Handler statsHandler;
    private static Runnable statsRunnable;

    public static void setupDashboard(Activity activity, boolean isDarkTheme) {
        if (activity == null || activity.isFinishing()) return;

        try {
            // --- 1. LINK VIEWS ---
            View ramCardBg = activity.findViewById(R.id.ramCardBg);
            View storageCardBg = activity.findViewById(R.id.storageCardBg);
            View batteryCardBg = activity.findViewById(R.id.batteryCardBg);
            View ramDetailsBox = activity.findViewById(R.id.ramDetailsBox);

            RamGraphView ramGraphView = activity.findViewById(R.id.ramGraphView);
            TextView tvRamTitle = activity.findViewById(R.id.tvRamTitle);
            TextView tvRamPercentLarge = activity.findViewById(R.id.tvRamPercentLarge);
            TextView tvRamPercentUnit = activity.findViewById(R.id.tvRamPercentUnit);

            TextView tvRamUsedLbl = activity.findViewById(R.id.tvRamUsedLbl);
            TextView tvRamUsed = activity.findViewById(R.id.tvRamUsed);
            TextView tvRamFreeLbl = activity.findViewById(R.id.tvRamFreeLbl);
            TextView tvRamFree = activity.findViewById(R.id.tvRamFree);
            TextView tvRamTotalLbl = activity.findViewById(R.id.tvRamTotalLbl);
            TextView tvRamTotal = activity.findViewById(R.id.tvRamTotal);

            TextView tvStorageValLarge = activity.findViewById(R.id.tvStorageValueLarge);
            TextView tvStorageUnit = activity.findViewById(R.id.tvStorageUnit);
            TextView tvStorageTitle = activity.findViewById(R.id.tvStorageTitle);
            TextView tvStorageStatus = activity.findViewById(R.id.tvStorageStatus);
            FrameLayout storageSegmentContainer = activity.findViewById(R.id.storageSegmentContainer);

            TextView tvBatteryValLarge = activity.findViewById(R.id.tvBatteryValueLarge);
            TextView tvBatteryUnit = activity.findViewById(R.id.tvBatteryUnit);
            TextView tvBatteryTitle = activity.findViewById(R.id.tvBatteryTitle);
            TextView tvBatteryStatus = activity.findViewById(R.id.tvBatteryStatus);
            FrameLayout batterySegmentContainer = activity.findViewById(R.id.batterySegmentContainer);

            if (ramCardBg == null || storageSegmentContainer == null || batterySegmentContainer == null) return;

            // --- 2. APPLY DARK/LIGHT MODE COLORS ---
            int textColor = isDarkTheme ? Color.WHITE : Color.parseColor("#1C1C1E");
            int subTextColor = isDarkTheme ? Color.parseColor("#B0B0B8") : Color.parseColor("#8E8E93");
            int cardBgColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.parseColor("#F2F2F7");

            int innerBoxBgColor = isDarkTheme ? Color.parseColor("#000000") : Color.parseColor("#FFFFFF");
            int innerBoxStrokeColor = isDarkTheme ? Color.parseColor("#333333") : Color.parseColor("#E5E5EA");

            int activeAccentColor = isDarkTheme ? Color.parseColor("#5AC8FA") : Color.parseColor("#007AFF");
            int storageCriticalColor = isDarkTheme ? Color.parseColor("#FF453A") : Color.parseColor("#FF3B30");
            int barInactiveColor = isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#D1D1D6");

            int batteryChargingColor = isDarkTheme ? Color.parseColor("#32D74B") : Color.parseColor("#34C759");
            int batteryDischargingColor = isDarkTheme ? Color.parseColor("#5AC8FA") : Color.parseColor("#007AFF");

            ViewCompat.setBackgroundTintList(ramCardBg, ColorStateList.valueOf(cardBgColor));
            ramCardBg.setClipToOutline(true);

            if (storageCardBg != null) {
                ViewCompat.setBackgroundTintList(storageCardBg, ColorStateList.valueOf(cardBgColor));
                storageCardBg.setClipToOutline(true);
            }
            if (batteryCardBg != null) {
                ViewCompat.setBackgroundTintList(batteryCardBg, ColorStateList.valueOf(cardBgColor));
                batteryCardBg.setClipToOutline(true);
            }

            if (ramDetailsBox != null) {
                GradientDrawable boxShape = new GradientDrawable();
                boxShape.setColor(innerBoxBgColor);
                boxShape.setCornerRadius(activity.getResources().getDisplayMetrics().density * 10f);
                boxShape.setStroke(2, innerBoxStrokeColor);
                ramDetailsBox.setBackground(boxShape);
            }

            if (tvRamPercentLarge != null) tvRamPercentLarge.setTextColor(textColor);
            if (tvRamPercentUnit != null) tvRamPercentUnit.setTextColor(textColor);
            if (tvRamTitle != null) tvRamTitle.setTextColor(subTextColor);

            if (tvRamUsedLbl != null) tvRamUsedLbl.setTextColor(subTextColor);
            if (tvRamUsed != null) tvRamUsed.setTextColor(subTextColor);
            if (tvRamFreeLbl != null) tvRamFreeLbl.setTextColor(subTextColor);
            if (tvRamFree != null) tvRamFree.setTextColor(subTextColor);
            if (tvRamTotalLbl != null) tvRamTotalLbl.setTextColor(subTextColor);
            if (tvRamTotal != null) tvRamTotal.setTextColor(subTextColor);

            if (tvStorageValLarge != null) tvStorageValLarge.setTextColor(textColor);
            if (tvStorageUnit != null) tvStorageUnit.setTextColor(textColor);
            if (tvStorageTitle != null) tvStorageTitle.setTextColor(subTextColor);

            if (tvBatteryValLarge != null) tvBatteryValLarge.setTextColor(textColor);
            if (tvBatteryUnit != null) tvBatteryUnit.setTextColor(textColor);
            if (tvBatteryTitle != null) tvBatteryTitle.setTextColor(subTextColor);

            if (ramGraphView != null) ramGraphView.setTheme(isDarkTheme);

            storageSegmentContainer.removeAllViews();
            SegmentedProgressView storageProgressView = new SegmentedProgressView(activity);
            storageSegmentContainer.addView(storageProgressView);

            batterySegmentContainer.removeAllViews();
            SegmentedProgressView batteryProgressView = new SegmentedProgressView(activity);
            batterySegmentContainer.addView(batteryProgressView);

            // --- 3. STORAGE LOGIC ---
            try {
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
                float storagePct = (float) (usedStorageGb / advertisedStorageGb) * 100f;

                if (tvStorageValLarge != null) tvStorageValLarge.setText(String.format(Locale.US, "%.1f", usedStorageGb));

                if (tvStorageStatus != null) {
                    if (storagePct >= 90f) {
                        tvStorageStatus.setText("Low Space");
                        tvStorageStatus.setTextColor(storageCriticalColor);
                        storageProgressView.setColors(storageCriticalColor, barInactiveColor);
                    } else {
                        tvStorageStatus.setText("Optimal Space");
                        tvStorageStatus.setTextColor(activeAccentColor);
                        storageProgressView.setColors(activeAccentColor, barInactiveColor);
                    }
                }
                storageProgressView.setProgress(storagePct);
            } catch (Exception e) {
                Log.e("DeviceStats", "Storage read failed", e);
            }

            // --- 4. REAL-TIME RAM & BATTERY LOGIC ---
            ActivityManager actManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();

            SegmentedProgressView finalBatteryProgressView = batteryProgressView;

            if (statsHandler != null && statsRunnable != null) {
                statsHandler.removeCallbacks(statsRunnable);
            }

            statsHandler = new Handler(Looper.getMainLooper());
            statsRunnable = new Runnable() {
                @Override
                public void run() {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return; // Safely exit if activity is dead
                    }

                    // BUG FIX: If the view isn't attached yet, wait and try again instead of killing the loop.
                    if (!ramCardBg.isAttachedToWindow()) {
                        statsHandler.postDelayed(this, 500);
                        return;
                    }

                    // RAM Updates
                    if (ramGraphView != null && actManager != null) {
                        actManager.getMemoryInfo(memInfo);
                        long totalRamMB = memInfo.totalMem / 1048576L;
                        long availRamMB = memInfo.availMem / 1048576L;
                        long usedRamMB = totalRamMB - availRamMB;

                        if (totalRamMB > 0) {
                            float percentUsed = ((float) usedRamMB / totalRamMB) * 100f;

                            if (tvRamPercentLarge != null) tvRamPercentLarge.setText(String.valueOf((int) percentUsed));
                            if (tvRamUsed != null) tvRamUsed.setText(String.format(Locale.US, "%d MB", usedRamMB));
                            if (tvRamFree != null) tvRamFree.setText(String.format(Locale.US, "%d MB", availRamMB));
                            if (tvRamTotal != null) tvRamTotal.setText(String.format(Locale.US, "%d MB", totalRamMB));

                            ramGraphView.addRamData(percentUsed);
                        }
                    }

                    // BUG FIX: Battery Updates via Sticky Intent (Works securely on MIUI/ColorOS/HyperOS)
                    try {
                        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                        Intent batteryStatusIntent = activity.registerReceiver(null, ifilter);

                        if (batteryStatusIntent != null) {
                            int level = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                            int scale = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                            int status = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

                            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                    status == BatteryManager.BATTERY_STATUS_FULL;

                            int batteryPct = (int) ((level / (float) scale) * 100);

                            if (batteryPct >= 0 && batteryPct <= 100) {
                                if (tvBatteryValLarge != null) tvBatteryValLarge.setText(String.valueOf(batteryPct));

                                if (tvBatteryStatus != null) {
                                    if (isCharging) {
                                        tvBatteryStatus.setText("Charging");
                                        tvBatteryStatus.setTextColor(batteryChargingColor);
                                        finalBatteryProgressView.setColors(batteryChargingColor, barInactiveColor);
                                    } else {
                                        tvBatteryStatus.setText("Discharging");
                                        tvBatteryStatus.setTextColor(batteryDischargingColor);
                                        finalBatteryProgressView.setColors(batteryDischargingColor, barInactiveColor);
                                    }
                                }
                                finalBatteryProgressView.setProgress(batteryPct);
                            }
                        }
                    } catch (Exception e) {
                        Log.e("DeviceStats", "Battery intent read error", e);
                    }

                    statsHandler.postDelayed(this, 1000);
                }
            };

            statsHandler.post(statsRunnable);

        } catch (Exception e) {
            Log.e("DeviceStats", "Dashboard setup fatal error", e);
        }
    }

    public static class SegmentedProgressView extends View {
        private static final int SEGMENTS = 10;
        private float progress = 0;
        private final Paint paintActive = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintInactive = new Paint(Paint.ANTI_ALIAS_FLAG);

        public SegmentedProgressView(Context context) {
            super(context);
        }

        public void setColors(int active, int inactive) {
            paintActive.setColor(active);
            paintInactive.setColor(inactive);
            invalidate();
        }

        public void setProgress(float progress) {
            this.progress = progress;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();

            if (w <= 0 || h <= 0) return;

            float gap = getContext().getResources().getDisplayMetrics().density * 4f;
            float segmentWidth = (w - (gap * (SEGMENTS - 1))) / SEGMENTS;

            if (segmentWidth <= 0) return;

            int activeSegments = Math.round((progress / 100f) * SEGMENTS);

            for (int i = 0; i < SEGMENTS; i++) {
                float left = i * (segmentWidth + gap);
                float right = left + segmentWidth;
                float corner = h / 3.5f;

                if (i < activeSegments) {
                    canvas.drawRoundRect(left, 0, right, h, corner, corner, paintActive);
                } else {
                    canvas.drawRoundRect(left, 0, right, h, corner, corner, paintInactive);
                }
            }
        }
    }
}