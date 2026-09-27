package com.abhinav.ownapp;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        int alarmId = intent.getIntExtra("ALARM_ID", 1002);
        String customNote = intent.getStringExtra("ALARM_NOTE");

        if (customNote == null || customNote.trim().isEmpty()) {
            customNote = "Time's Up!";
        }

        // --- HANDLE STOPPING THE ALARM ---
        if ("com.abhinav.ownapp.ACTION_STOP".equals(action)) {
            nm.cancel(alarmId);
            removeAlarmFromStorage(context, alarmId);
            context.sendBroadcast(new Intent("com.abhinav.ownapp.ALARM_UPDATED"));
            return;
        }

        // --- HANDLE SNOOZING THE ALARM (5 MINUTES) ---
        if ("com.abhinav.ownapp.ACTION_SNOOZE".equals(action)) {
            nm.cancel(alarmId);
            removeAlarmFromStorage(context, alarmId);

            long snoozeTime = System.currentTimeMillis() + (5 * 60 * 1000);
            int newAlarmId = (int) (snoozeTime % Integer.MAX_VALUE);

            try {
                SharedPreferences prefs = context.getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
                JSONArray alarmsArray = new JSONArray(prefs.getString("alarms_list", "[]"));
                JSONObject newAlarm = new JSONObject();
                newAlarm.put("id", newAlarmId);
                newAlarm.put("time", snoozeTime);
                newAlarm.put("note", customNote);
                alarmsArray.put(newAlarm);
                prefs.edit().putString("alarms_list", alarmsArray.toString()).apply();
            } catch (Exception e) {}

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            // FIX: Explicitly target the receiver component to prevent intent hijacking or OS block
            Intent snoozeIntent = new Intent();
            snoozeIntent.setComponent(new ComponentName(context, AlarmReceiver.class));
            snoozeIntent.setAction("com.abhinav.ownapp.TRIGGER_ALARM");
            snoozeIntent.putExtra("ALARM_ID", newAlarmId);
            snoozeIntent.putExtra("ALARM_NOTE", customNote);

            PendingIntent pIntent = PendingIntent.getBroadcast(context, newAlarmId, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            AlarmManager.AlarmClockInfo snoozeClockInfo = new AlarmManager.AlarmClockInfo(snoozeTime, pIntent);
            alarmManager.setAlarmClock(snoozeClockInfo, pIntent);

            Toast.makeText(context, "Snoozed for 5 minutes.", Toast.LENGTH_SHORT).show();
            context.sendBroadcast(new Intent("com.abhinav.ownapp.ALARM_UPDATED"));
            return;
        }

        // --- NORMAL ALARM TRIGGER: FIRE SOUND AND NOTIFICATION ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "alarm_channel_insistent",
                    "Loud Alarms",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Critical scheduled alarms with sound.");

            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmSound == null) alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (alarmSound == null) alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build();

            channel.setSound(alarmSound, audioAttributes);
            channel.enableVibration(true);
            channel.setBypassDnd(true);
            nm.createNotificationChannel(channel);
        }

        Intent stopIntent = new Intent(context, AlarmReceiver.class);
        stopIntent.setAction("com.abhinav.ownapp.ACTION_STOP");
        stopIntent.putExtra("ALARM_ID", alarmId);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(context, alarmId + 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent snoozeIntent = new Intent(context, AlarmReceiver.class);
        snoozeIntent.setAction("com.abhinav.ownapp.ACTION_SNOOZE");
        snoozeIntent.putExtra("ALARM_ID", alarmId);
        snoozeIntent.putExtra("ALARM_NOTE", customNote);
        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(context, alarmId + 2, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "alarm_channel_insistent")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(customNote)
                .setContentText("Your alarm is ringing. Wake up!")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(false) // FIX: Cannot be dismissed by tapping
                .setOngoing(true)     // FIX: Prevents swiping away without action
                .addAction(android.R.drawable.ic_popup_sync, "Snooze (5m)", snoozePendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent);

        android.app.Notification notification = builder.build();

        // FIX: The holy grail for continuous looping sound until user hits Stop/Snooze
        notification.flags |= android.app.Notification.FLAG_INSISTENT;
        notification.flags |= android.app.Notification.FLAG_NO_CLEAR;

        nm.notify(alarmId, notification);
        context.sendBroadcast(new Intent("com.abhinav.ownapp.ALARM_UPDATED"));
    }

    private void removeAlarmFromStorage(Context context, int alarmId) {
        SharedPreferences prefs = context.getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray alarmsArray = new JSONArray(prefs.getString("alarms_list", "[]"));
            JSONArray updatedArray = new JSONArray();
            for (int i = 0; i < alarmsArray.length(); i++) {
                if (alarmsArray.getJSONObject(i).getInt("id") != alarmId) {
                    updatedArray.put(alarmsArray.getJSONObject(i));
                }
            }
            prefs.edit().putString("alarms_list", updatedArray.toString()).apply();
        } catch (Exception e) {}
    }
}