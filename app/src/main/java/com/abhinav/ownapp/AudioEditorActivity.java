package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@SuppressWarnings({"SpellCheckingInspection", "FieldCanBeLocal", "ResultOfMethodCallIgnored"})
@SuppressLint({"SetTextI18n", "HardCodedText", "ClickableViewAccessibility", "WrongConstant"})
public class AudioEditorActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout; private LinearLayout mainContent, leftDrawer, rightDrawer, tracksContainer, drawerTracksContainer, timelineControls, bottomBar, trimControlsBar, toolUndo, toolRedo;
    private RelativeLayout playhead, splitPlayhead; private View timelineLayout, splitPlayheadDot;
    private TextView btnGallery, tvTitle, tvTotalDuration, btnExport, btnLoadAudio, btnAddTrack, btnTracks, btnTools, toolTrim, toolMerge, toolVolume, toolSpeed, toolBass, toolTreble, toolSplit, btnCancelTrim, btnApplyTrim, tvLoading;
    private ImageView btnSeekStart, btnSeekEnd, btnPlayPause, icUndo, icRedo; private TextView tvUndo, tvRedo;
    private HorizontalScrollView timelineScroller;
    private boolean isDarkTheme, isPlaying = false, isScrubbingPlayhead = false; boolean isSplitMode = false, isSplitRemoveMode = false; int targetTrimIndex = 0;
    private final List<AudioTrackItem> projectTracks = new ArrayList<>(); private final List<MediaPlayer> activePlayers = new ArrayList<>();
    private final List<android.media.audiofx.AudioEffect> activeEffects = new ArrayList<>(); private final Handler handler = new Handler(Looper.getMainLooper()); private WaveformView activeWaveformView;
    private float msPerPx = 0f; private long maxTimelineMs = 1, playheadMs = 0;
    private final List<List<AudioTrackItem>> undoStack = new ArrayList<>(); private final List<List<AudioTrackItem>> redoStack = new ArrayList<>();

    public static class AudioTrackItem { public String name, path; public int offsetPx = 0; public boolean isMuted = false, isBase = false; public long durMs = 0, originalDurMs = 0; public float volume = 1.0f, speed = 1.0f; public int bassType = 0, trebleType = 0; public float[] waveCache = null; public AudioTrackItem(String name, String path) { this.name = name; this.path = path; } }
    public static class PcmAudio { public short[] pcm; public int sampleRate, channels; public PcmAudio(short[] p, int sr, int c) { pcm = p; sampleRate = sr; channels = c; } }

    private List<AudioTrackItem> cloneTracks(List<AudioTrackItem> list) { List<AudioTrackItem> c = new ArrayList<>(); for (AudioTrackItem t : list) { AudioTrackItem n = new AudioTrackItem(t.name, t.path); n.offsetPx = t.offsetPx; n.isMuted = t.isMuted; n.isBase = t.isBase; n.durMs = t.durMs; n.originalDurMs = t.originalDurMs; n.volume = t.volume; n.speed = t.speed; n.bassType = t.bassType; n.trebleType = t.trebleType; if (t.waveCache != null) n.waveCache = t.waveCache.clone(); c.add(n); } return c; }
    private void saveState() { undoStack.add(cloneTracks(projectTracks)); redoStack.clear(); }
    private void performUndo() { if (!undoStack.isEmpty()) { drawerLayout.closeDrawers(); stopPlayback(); redoStack.add(cloneTracks(projectTracks)); projectTracks.clear(); projectTracks.addAll(undoStack.remove(undoStack.size() - 1)); recalculateTimeline(); prepareMultiTrackPlayers(); Toast.makeText(this, "Undo Successful", Toast.LENGTH_SHORT).show(); } else Toast.makeText(this, "Nothing to Undo", Toast.LENGTH_SHORT).show(); }
    private void performRedo() { if (!redoStack.isEmpty()) { drawerLayout.closeDrawers(); stopPlayback(); undoStack.add(cloneTracks(projectTracks)); projectTracks.clear(); projectTracks.addAll(redoStack.remove(redoStack.size() - 1)); recalculateTimeline(); prepareMultiTrackPlayers(); Toast.makeText(this, "Redo Successful", Toast.LENGTH_SHORT).show(); } else Toast.makeText(this, "Nothing to Redo", Toast.LENGTH_SHORT).show(); }

    private final Runnable updatePlayhead = new Runnable() {
        @Override public void run() {
            if (isPlaying && !isScrubbingPlayhead) {
                playheadMs += 30; if (playheadMs >= maxTimelineMs) { stopPlayback(); playheadMs = 0; playhead.setTranslationX(0f); timelineScroller.smoothScrollTo(0, 0); return; }
                float targetX = playheadMs / msPerPx; playhead.setTranslationX(targetX);
                if (targetX > timelineScroller.getScrollX() + (timelineScroller.getWidth() * 0.8f)) timelineScroller.smoothScrollTo((int)(targetX - (timelineScroller.getWidth() * 0.5f)), 0);
                for (int i = 0; i < activePlayers.size(); i++) { MediaPlayer mp = activePlayers.get(i); long delay = (long) (projectTracks.get(i).offsetPx * msPerPx); if (playheadMs >= delay && playheadMs < delay + projectTracks.get(i).durMs) { if (!mp.isPlaying()) mp.start(); } else { if (mp.isPlaying()) { mp.pause(); mp.seekTo(0); } } }
                handler.postDelayed(this, 30);
            }
        }
    };

    private final ActivityResultLauncher<Intent> audioPickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Uri audioUri = result.getData().getData();
            if (audioUri != null) {
                String fileName = getFileName(audioUri); drawerLayout.closeDrawers(); btnLoadAudio.setVisibility(View.GONE); timelineLayout.setVisibility(View.GONE); timelineControls.setVisibility(View.GONE); tvLoading.setVisibility(View.VISIBLE); tvLoading.setText("Loading Audio...");
                new Thread(() -> {
                    String newPath = copyUriToCache(audioUri, "track_" + System.currentTimeMillis() + ".mp3");
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return; tvLoading.setVisibility(View.GONE);
                        if (newPath != null) {
                            saveState(); AudioTrackItem item = new AudioTrackItem(fileName, newPath);
                            try { MediaPlayer mp = new MediaPlayer(); mp.setDataSource(newPath); mp.prepare(); item.originalDurMs = mp.getDuration(); item.durMs = item.originalDurMs; mp.release(); } catch(Exception ignored){}
                            if (projectTracks.isEmpty()) { item.isBase = true; msPerPx = item.durMs / (1500f * getResources().getDisplayMetrics().density); } else { item.offsetPx = msPerPx > 0 ? (int)(playheadMs / msPerPx) : 0; }
                            projectTracks.add(item); recalculateTimeline(); prepareMultiTrackPlayers();
                        } else { Toast.makeText(this, "Failed to load audio.", Toast.LENGTH_SHORT).show(); if (projectTracks.isEmpty()) btnLoadAudio.setVisibility(View.VISIBLE); }
                    });
                }).start();
            }
        }
    });

    private final ActivityResultLauncher<Intent> mergeAudioPickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Uri audioUri = result.getData().getData();
            if (audioUri != null) {
                String fileName = getFileName(audioUri); drawerLayout.closeDrawers(); tvLoading.setVisibility(View.VISIBLE); tvLoading.setText("Loading Merge..."); timelineLayout.setVisibility(View.GONE); timelineControls.setVisibility(View.GONE);
                new Thread(() -> {
                    String newPath = copyUriToCache(audioUri, "merge_" + System.currentTimeMillis() + ".mp3");
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return; tvLoading.setVisibility(View.GONE);
                        if (newPath != null) {
                            List<String> opts = new ArrayList<>(); opts.add("Add to Front"); opts.add("Add to Back");
                            showCustomDialog("Merge Position", opts, idx -> {
                                saveState(); AudioTrackItem item = new AudioTrackItem(fileName, newPath); item.isBase = true;
                                try { MediaPlayer mp = new MediaPlayer(); mp.setDataSource(newPath); mp.prepare(); item.originalDurMs = mp.getDuration(); item.durMs = item.originalDurMs; mp.release(); } catch(Exception ignored){}
                                if (idx == 0) { int shiftPx = (int)(item.durMs / msPerPx); for(AudioTrackItem t : projectTracks) t.offsetPx += shiftPx; item.offsetPx = 0; projectTracks.add(0, item); } else { item.offsetPx = (int)(maxTimelineMs / msPerPx); projectTracks.add(item); }
                                recalculateTimeline(); prepareMultiTrackPlayers(); float exactMaxPx = maxTimelineMs / msPerPx; seekToTimelineX(exactMaxPx); timelineScroller.postDelayed(() -> timelineScroller.smoothScrollTo((int)exactMaxPx, 0), 100);
                            });
                        } else Toast.makeText(this, "Failed to load audio.", Toast.LENGTH_SHORT).show();
                    });
                }).start();
            }
        }
    });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_audio_editor); initViews(); setupEdgeToEdge();
        isDarkTheme = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE).getBoolean(SnakeWidget.PREF_IS_DARK, true); applyTheme(); setupListeners(); setupPlayheadScrubbing(); setupSplitPlayhead(); handleSharedIntent(getIntent());
    }

    private void handleSharedIntent(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null && (intent.getType().startsWith("audio/") || intent.getType().equals("application/ogg"))) {
            Uri audioUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (audioUri != null) {
                String fileName = getFileName(audioUri); btnLoadAudio.setVisibility(View.GONE); timelineLayout.setVisibility(View.GONE); timelineControls.setVisibility(View.GONE); tvLoading.setVisibility(View.VISIBLE); tvLoading.setText("Loading Shared Audio...");
                new Thread(() -> {
                    String newPath = copyUriToCache(audioUri, "track_" + System.currentTimeMillis() + ".mp3");
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return; tvLoading.setVisibility(View.GONE);
                        if (newPath != null) {
                            saveState(); AudioTrackItem item = new AudioTrackItem(fileName, newPath);
                            try { MediaPlayer mp = new MediaPlayer(); mp.setDataSource(newPath); mp.prepare(); item.originalDurMs = mp.getDuration(); item.durMs = item.originalDurMs; mp.release(); } catch(Exception ignored){}
                            if (projectTracks.isEmpty()) { item.isBase = true; msPerPx = item.durMs / (1500f * getResources().getDisplayMetrics().density); } else { item.offsetPx = msPerPx > 0 ? (int)(playheadMs / msPerPx) : 0; }
                            projectTracks.add(item); recalculateTimeline(); prepareMultiTrackPlayers();
                        } else { Toast.makeText(this, "Failed to load shared audio.", Toast.LENGTH_SHORT).show(); if (projectTracks.isEmpty()) btnLoadAudio.setVisibility(View.VISIBLE); }
                    });
                }).start();
            }
        }
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout); mainContent = findViewById(R.id.mainContent); leftDrawer = findViewById(R.id.leftDrawer); rightDrawer = findViewById(R.id.rightDrawer);
        btnGallery = findViewById(R.id.btnGallery); tvTitle = findViewById(R.id.tvTitle); tvTotalDuration = findViewById(R.id.tvTotalDuration); btnExport = findViewById(R.id.btnExport); btnLoadAudio = findViewById(R.id.btnLoadAudio);
        bottomBar = findViewById(R.id.bottomBar); btnTracks = findViewById(R.id.btnTracks); btnTools = findViewById(R.id.btnTools); trimControlsBar = findViewById(R.id.trimControlsBar);
        btnCancelTrim = findViewById(R.id.btnCancelTrim); btnApplyTrim = findViewById(R.id.btnApplyTrim); btnAddTrack = findViewById(R.id.btnAddTrack); toolUndo = findViewById(R.id.toolUndo); toolRedo = findViewById(R.id.toolRedo); toolTrim = findViewById(R.id.toolTrim); toolMerge = findViewById(R.id.toolMerge); toolVolume = findViewById(R.id.toolVolume); toolSpeed = findViewById(R.id.toolSpeed); toolBass = findViewById(R.id.toolBass); toolTreble = findViewById(R.id.toolTreble); toolSplit = findViewById(R.id.toolSplit);
        timelineLayout = findViewById(R.id.timelineLayout); timelineControls = findViewById(R.id.timelineControls); tracksContainer = findViewById(R.id.tracksContainer); drawerTracksContainer = findViewById(R.id.drawerTracksContainer);
        timelineScroller = findViewById(R.id.timelineScroller); playhead = findViewById(R.id.playhead); splitPlayhead = findViewById(R.id.splitPlayhead); splitPlayheadDot = findViewById(R.id.splitPlayheadDot); btnPlayPause = findViewById(R.id.btnPlayPause); tvLoading = findViewById(R.id.tvLoading);
        btnSeekStart = findViewById(R.id.btnSeekStart); btnSeekEnd = findViewById(R.id.btnSeekEnd); icUndo = findViewById(R.id.icUndo); icRedo = findViewById(R.id.icRedo); tvUndo = findViewById(R.id.tvUndo); tvRedo = findViewById(R.id.tvRedo);
    }

    private void setupEdgeToEdge() { ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, windowInsets) -> { Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()); mainContent.setPadding(insets.left, insets.top, insets.right, insets.bottom); leftDrawer.setPadding(insets.left, insets.top, insets.right, insets.bottom); rightDrawer.setPadding(insets.left, insets.top, insets.right, insets.bottom); return WindowInsetsCompat.CONSUMED; }); }

    private void setupPlayheadScrubbing() {
        playhead.setOnTouchListener(new View.OnTouchListener() {
            private float startTouchX = 0f, startTransX = 0f;
            @Override public boolean onTouch(@NonNull View v, @NonNull MotionEvent event) {
                float maxW = maxTimelineMs / msPerPx;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: isScrubbingPlayhead = true; startTouchX = event.getRawX(); startTransX = playhead.getTranslationX(); for (MediaPlayer mp : activePlayers) { try { if (mp.isPlaying()) mp.pause(); } catch (Exception ignored) {} } return true;
                    case MotionEvent.ACTION_MOVE: float newX = Math.max(0f, Math.min(startTransX + (event.getRawX() - startTouchX), maxW)); playhead.setTranslationX(newX); playheadMs = (long) (newX * msPerPx); for (int i = 0; i < activePlayers.size(); i++) { MediaPlayer mp = activePlayers.get(i); long delay = (long) (projectTracks.get(i).offsetPx * msPerPx); if (playheadMs >= delay) mp.seekTo((int) (playheadMs - delay)); else mp.seekTo(0); } return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: isScrubbingPlayhead = false; if (isPlaying && !activePlayers.isEmpty()) { for (int i = 0; i < activePlayers.size(); i++) { long delay = (long) (projectTracks.get(i).offsetPx * msPerPx); if (playheadMs >= delay && playheadMs < delay + projectTracks.get(i).durMs) activePlayers.get(i).start(); } } return true;
                } return false;
            }
        });
    }

    private void setupSplitPlayhead() {
        splitPlayhead.setOnTouchListener(new View.OnTouchListener() {
            private float startTouchX = 0f, startTransX = 0f;
            @Override public boolean onTouch(@NonNull View v, @NonNull MotionEvent event) {
                if (targetTrimIndex >= projectTracks.size()) return false;
                AudioTrackItem item = projectTracks.get(targetTrimIndex); float minX = item.offsetPx; float maxX = item.offsetPx + (item.durMs / msPerPx);
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: startTouchX = event.getRawX(); startTransX = splitPlayhead.getTranslationX(); return true;
                    case MotionEvent.ACTION_MOVE: splitPlayhead.setTranslationX(Math.max(minX, Math.min(startTransX + (event.getRawX() - startTouchX), maxX))); return true;
                } return false;
            }
        });
    }

    public void seekToTimelineX(float targetX) {
        float exactMaxPx = maxTimelineMs / msPerPx; float newX = Math.max(0f, Math.min(targetX, exactMaxPx)); playhead.setTranslationX(newX); playheadMs = (long) (newX * msPerPx);
        if (!activePlayers.isEmpty()) { try { for (int i = 0; i < activePlayers.size(); i++) { MediaPlayer mp = activePlayers.get(i); long delay = (long) (projectTracks.get(i).offsetPx * msPerPx); if (playheadMs >= delay) mp.seekTo((int) (playheadMs - delay)); else mp.seekTo(0); } } catch (Exception ignored) {} }
    }

    void moveSplitPlayhead(AudioTrackItem item, float targetX) {
        if (targetTrimIndex < projectTracks.size() && projectTracks.get(targetTrimIndex) == item) { float minX = item.offsetPx; float maxX = item.offsetPx + (item.durMs / msPerPx); splitPlayhead.setTranslationX(Math.max(minX, Math.min(targetX, maxX))); }
    }

    private void recalculateTimeline() {
        maxTimelineMs = 1; for (AudioTrackItem t : projectTracks) { if (t.isBase) { long endMs = (long)(t.offsetPx * msPerPx) + t.durMs; if (endMs > maxTimelineMs) maxTimelineMs = endMs; } }
        if (maxTimelineMs == 1 && !projectTracks.isEmpty()) { for (AudioTrackItem t : projectTracks) { long endMs = (long)(t.offsetPx * msPerPx) + t.durMs; if (endMs > maxTimelineMs) maxTimelineMs = endMs; } }
        long tSec = maxTimelineMs / 1000; tvTotalDuration.setText("Total: " + String.format(Locale.US, "%02d:%02d", tSec / 60, tSec % 60)); rebuildTimelineUI();
    }

    private void setupListeners() {
        btnGallery.setOnClickListener(v -> { stopPlayback(); startActivity(new Intent(this, AudioGalleryActivity.class)); });
        btnLoadAudio.setOnClickListener(v -> audioPickerLauncher.launch(new Intent(Intent.ACTION_GET_CONTENT).setType("audio/*")));
        btnAddTrack.setOnClickListener(v -> { if (projectTracks.isEmpty()) { Toast.makeText(this, "Load Base Track first!", Toast.LENGTH_SHORT).show(); return; } audioPickerLauncher.launch(new Intent(Intent.ACTION_GET_CONTENT).setType("audio/*")); });
        btnTracks.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START)); btnTools.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.END)); btnPlayPause.setOnClickListener(v -> togglePlayback());
        btnSeekStart.setOnClickListener(v -> { stopPlayback(); seekToTimelineX(0f); timelineScroller.smoothScrollTo(0, 0); });
        btnSeekEnd.setOnClickListener(v -> { stopPlayback(); float exactMaxPx = maxTimelineMs / msPerPx; seekToTimelineX(exactMaxPx); timelineScroller.post(() -> timelineScroller.smoothScrollTo((int)exactMaxPx, 0)); });
        toolMerge.setOnClickListener(v -> { if (projectTracks.isEmpty()) { Toast.makeText(this, "Load Base Track first!", Toast.LENGTH_SHORT).show(); return; } mergeAudioPickerLauncher.launch(new Intent(Intent.ACTION_GET_CONTENT).setType("audio/*")); });
        toolUndo.setOnClickListener(v -> performUndo()); toolRedo.setOnClickListener(v -> performRedo()); toolTrim.setOnClickListener(v -> promptTrackSelectionAndTrim()); toolVolume.setOnClickListener(v -> promptTrackSelectionAndVolume()); toolSpeed.setOnClickListener(v -> promptTrackSelectionAndSpeed()); toolBass.setOnClickListener(v -> promptTrackSelectionAndBass()); toolTreble.setOnClickListener(v -> promptTrackSelectionAndTreble()); toolSplit.setOnClickListener(v -> promptTrackSelectionAndSplit());
        btnCancelTrim.setOnClickListener(v -> exitTrimMode()); btnApplyTrim.setOnClickListener(v -> { if (isSplitMode) applySplitToTrack(isSplitRemoveMode); else { if (activeWaveformView == null || projectTracks.isEmpty()) return; applyTrimToTrack(activeWaveformView.getTrimStartRatio(), activeWaveformView.getTrimEndRatio()); exitTrimMode(); } });
        btnExport.setOnClickListener(v -> showExportDialog());
    }

    private void showCustomDialog(String titleText, List<String> items, DialogCallback callback) {
        Dialog d = new Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE); if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(80, 80, 80, 80);
        GradientDrawable rootBg = new GradientDrawable(); rootBg.setColor(Color.parseColor(isDarkTheme ? "#CC1C1C1E" : "#CCF2F2F7")); rootBg.setCornerRadius(100f); root.setBackground(rootBg);
        TextView title = new TextView(this); title.setText(titleText); title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); title.setTextSize(20f); title.setTypeface(null, Typeface.BOLD); title.setPadding(0, 0, 0, 60); title.setGravity(android.view.Gravity.CENTER); root.addView(title);
        ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        int pillBg = isDarkTheme ? Color.parseColor("#332D2B") : Color.WHITE, txtCol = isDarkTheme ? Color.WHITE : Color.BLACK;
        for (int i = 0; i < items.size(); i++) {
            TextView item = new TextView(this); item.setText(items.get(i)); item.setTextColor(txtCol); item.setTextSize(16f); item.setTypeface(null, Typeface.BOLD); item.setPadding(40, 40, 40, 40); item.setGravity(android.view.Gravity.CENTER);
            item.setBackground(createPill(pillBg)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, 24); item.setLayoutParams(lp);
            final int idx = i; item.setOnClickListener(v -> { d.dismiss(); callback.onSelect(idx); }); list.addView(item);
        }
        scroll.addView(list); root.addView(scroll); d.setContentView(root, new ViewGroup.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels * 0.85), -2)); d.show();
    }
    private interface DialogCallback { void onSelect(int index); }

    private void promptTrackSelectionAndTrim() {
        if (projectTracks.isEmpty()) { Toast.makeText(this, "Load audio first!", Toast.LENGTH_SHORT).show(); return; } drawerLayout.closeDrawers();
        if (projectTracks.size() == 1) { targetTrimIndex = 0; startTrimModeForTarget(); } else { List<String> names = new ArrayList<>(); for (AudioTrackItem t : projectTracks) names.add((t.isBase ? "Base: " : "Layer: ") + t.name); showCustomDialog("Select Layer to Trim", names, idx -> { targetTrimIndex = idx; startTrimModeForTarget(); }); }
    }

    private android.widget.SeekBar createSeekBar(int progress, android.widget.SeekBar.OnSeekBarChangeListener listener) { android.widget.SeekBar sb = new android.widget.SeekBar(this); sb.setMax(100); sb.setProgress(progress); sb.setOnSeekBarChangeListener(listener); return sb; }

    private void promptTrackSelectionAndVolume() {
        if (projectTracks.isEmpty()) { Toast.makeText(this, "Load audio first!", Toast.LENGTH_SHORT).show(); return; } drawerLayout.closeDrawers();
        if (projectTracks.size() == 1) { showVolumeDialog(0); } else { List<String> names = new ArrayList<>(); for (AudioTrackItem t : projectTracks) names.add((t.isBase ? "Base: " : "Layer: ") + t.name); showCustomDialog("Select Track for Volume", names, this::showVolumeDialog); }
    }

    private void showVolumeDialog(int trackIndex) {
        saveState(); AudioTrackItem item = projectTracks.get(trackIndex); Dialog d = new Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE); if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(80, 80, 80, 80);
        GradientDrawable rootBg = new GradientDrawable(); rootBg.setColor(Color.parseColor(isDarkTheme ? "#CC1C1C1E" : "#CCF2F2F7")); rootBg.setCornerRadius(100f); root.setBackground(rootBg);
        TextView title = new TextView(this); title.setText("Volume: " + (int)(item.volume * 100) + "%"); title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); title.setTextSize(20f); title.setTypeface(null, Typeface.BOLD); title.setPadding(0, 0, 0, 60); title.setGravity(android.view.Gravity.CENTER); root.addView(title);
        android.widget.SeekBar sb = createSeekBar((int)(item.volume * 100), new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) { title.setText("Volume: " + progress + "%"); item.volume = progress / 100f; if (trackIndex < activePlayers.size()) activePlayers.get(trackIndex).setVolume(item.isMuted ? 0f : item.volume, item.isMuted ? 0f : item.volume); }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {} @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        }); root.addView(sb);
        TextView btnOk = new TextView(this); btnOk.setText("Done"); btnOk.setTextColor(Color.WHITE); btnOk.setTextSize(16f); btnOk.setTypeface(null, Typeface.BOLD); btnOk.setPadding(40, 40, 40, 40); btnOk.setGravity(android.view.Gravity.CENTER); btnOk.setBackground(createPill(Color.parseColor("#5A9AF4"))); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 60, 0, 0); btnOk.setLayoutParams(lp); btnOk.setOnClickListener(v -> d.dismiss()); root.addView(btnOk); d.setContentView(root, new ViewGroup.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels * 0.85), -2)); d.show();
    }

    private void promptTrackSelectionAndSpeed() {
        if (projectTracks.isEmpty()) { Toast.makeText(this, "Load audio first!", Toast.LENGTH_SHORT).show(); return; } drawerLayout.closeDrawers();
        if (projectTracks.size() == 1) { showSpeedDialog(0); } else { List<String> names = new ArrayList<>(); for (AudioTrackItem t : projectTracks) names.add((t.isBase ? "Base: " : "Layer: ") + t.name); showCustomDialog("Select Track for Speed", names, this::showSpeedDialog); }
    }

    private void showSpeedDialog(int trackIndex) {
        saveState(); AudioTrackItem item = projectTracks.get(trackIndex); List<String> speeds = new ArrayList<>(); speeds.add("0.25x (Slowest)"); speeds.add("0.5x (Slow)"); speeds.add("1.0x (Normal)"); speeds.add("1.5x (Fast)"); speeds.add("2.0x (Faster)"); speeds.add("4.0x (Fastest)");
        showCustomDialog("Set Speed: " + item.speed + "x", speeds, idx -> { float[] sVals = {0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 4.0f}; item.speed = sVals[idx]; item.durMs = (long)(item.originalDurMs / item.speed); item.waveCache = null; recalculateTimeline(); prepareMultiTrackPlayers(); });
    }

    private void promptTrackSelectionAndBass() {
        if (projectTracks.isEmpty()) { Toast.makeText(this, "Load audio first!", Toast.LENGTH_SHORT).show(); return; } drawerLayout.closeDrawers();
        if (projectTracks.size() == 1) { showBassDialog(0); } else { List<String> names = new ArrayList<>(); for (AudioTrackItem t : projectTracks) names.add((t.isBase ? "Base: " : "Layer: ") + t.name); showCustomDialog("Select Track for Bass", names, this::showBassDialog); }
    }

    private void showBassDialog(int trackIndex) {
        saveState(); AudioTrackItem item = projectTracks.get(trackIndex); List<String> opts = new ArrayList<>(); opts.add("Low Bass"); opts.add("Normal"); opts.add("High Bass");
        showCustomDialog("Set Bass: " + (item.bassType == 1 ? "High" : item.bassType == -1 ? "Low" : "Normal"), opts, idx -> { item.bassType = (idx == 0) ? -1 : (idx == 2) ? 1 : 0; item.waveCache = null; recalculateTimeline(); prepareMultiTrackPlayers(); });
    }

    private void promptTrackSelectionAndTreble() {
        if (projectTracks.isEmpty()) { Toast.makeText(this, "Load audio first!", Toast.LENGTH_SHORT).show(); return; } drawerLayout.closeDrawers();
        if (projectTracks.size() == 1) { showTrebleDialog(0); } else { List<String> names = new ArrayList<>(); for (AudioTrackItem t : projectTracks) names.add((t.isBase ? "Base: " : "Layer: ") + t.name); showCustomDialog("Select Track for Treble", names, this::showTrebleDialog); }
    }

    private void showTrebleDialog(int trackIndex) {
        saveState(); AudioTrackItem item = projectTracks.get(trackIndex); List<String> opts = new ArrayList<>(); opts.add("Low Treble"); opts.add("Normal"); opts.add("High Treble");
        showCustomDialog("Set Treble: " + (item.trebleType == 1 ? "High" : item.trebleType == -1 ? "Low" : "Normal"), opts, idx -> { item.trebleType = (idx == 0) ? -1 : (idx == 2) ? 1 : 0; item.waveCache = null; recalculateTimeline(); prepareMultiTrackPlayers(); });
    }

    private void promptTrackSelectionAndSplit() {
        if (projectTracks.isEmpty()) { Toast.makeText(this, "Load audio first!", Toast.LENGTH_SHORT).show(); return; } drawerLayout.closeDrawers();
        if (projectTracks.size() == 1) { showSplitTypeDialog(0); } else { List<String> names = new ArrayList<>(); for (AudioTrackItem t : projectTracks) names.add((t.isBase ? "Base: " : "Layer: ") + t.name); showCustomDialog("Select Track to Split", names, this::showSplitTypeDialog); }
    }

    private void showSplitTypeDialog(int trackIndex) {
        List<String> opts = new ArrayList<>(); opts.add("Remove (Trim out middle)"); opts.add("Cutting (Slice at point)");
        showCustomDialog("Select Split Type", opts, idx -> { targetTrimIndex = trackIndex; startSplitModeForTarget(idx == 0); });
    }

    private void startSplitModeForTarget(boolean isRemove) {
        bottomBar.setVisibility(View.GONE); trimControlsBar.setVisibility(View.VISIBLE); isSplitMode = true; isSplitRemoveMode = isRemove; btnApplyTrim.setText("Apply Split");
        if (targetTrimIndex < projectTracks.size()) {
            AudioTrackItem item = projectTracks.get(targetTrimIndex);
            if (isRemove) { if (targetTrimIndex < tracksContainer.getChildCount() / 2) { View wv = tracksContainer.getChildAt((targetTrimIndex * 2) + 1); if (wv instanceof WaveformView) { activeWaveformView = (WaveformView) wv; activeWaveformView.setTrimMode(true); } } }
            else { splitPlayhead.setVisibility(View.VISIBLE); float pw = (item.durMs / msPerPx); splitPlayhead.setTranslationX(item.offsetPx + pw / 2f); }
        }
    }

    private void startTrimModeForTarget() {
        bottomBar.setVisibility(View.GONE); trimControlsBar.setVisibility(View.VISIBLE); isSplitMode = false; btnApplyTrim.setText("Apply Trim");
        if (targetTrimIndex < tracksContainer.getChildCount() / 2) { View wv = tracksContainer.getChildAt((targetTrimIndex * 2) + 1); if (wv instanceof WaveformView) { activeWaveformView = (WaveformView) wv; activeWaveformView.setTrimMode(true); } }
    }

    private void exitTrimMode() {
        if (activeWaveformView != null) activeWaveformView.setTrimMode(false);
        trimControlsBar.setVisibility(View.GONE); bottomBar.setVisibility(View.VISIBLE); splitPlayhead.setVisibility(View.GONE); isSplitMode = false; btnApplyTrim.setText("Apply Trim");
    }

    private void applySplitToTrack(boolean isRemove) {
        if (projectTracks.isEmpty() || targetTrimIndex >= projectTracks.size()) return;
        drawerLayout.closeDrawers(); stopPlayback(); tvLoading.setVisibility(View.VISIBLE); tvLoading.setText("Splitting Audio..."); timelineLayout.setVisibility(View.GONE); timelineControls.setVisibility(View.GONE);
        AudioTrackItem item = projectTracks.get(targetTrimIndex); String outPath1 = new java.io.File(getCacheDir(), "split1_" + System.currentTimeMillis() + ".m4a").getAbsolutePath(); String outPath2 = new java.io.File(getCacheDir(), "split2_" + System.currentTimeMillis() + ".m4a").getAbsolutePath();
        new Thread(() -> {
            double r1 = 0, r2 = 0; float trackW = item.durMs / msPerPx;
            if (isRemove && activeWaveformView != null) { r1 = activeWaveformView.getTrimStartRatio(); r2 = activeWaveformView.getTrimEndRatio(); } else if (!isRemove) { r1 = (splitPlayhead.getTranslationX() - item.offsetPx) / trackW; r2 = r1; }
            final double finalR1 = r1, finalR2 = r2;
            PcmAudio pcm1 = decodeToPcm(item.path, 0.0, finalR1, item.speed, item.bassType, item.trebleType);
            if (pcm1 != null && pcm1.pcm != null && pcm1.pcm.length > 0) { for (int i=0; i<pcm1.pcm.length; i++) pcm1.pcm[i] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, pcm1.pcm[i] * item.volume)); encodePcmToFile(pcm1, new java.io.File(outPath1)); }
            PcmAudio pcm2 = decodeToPcm(item.path, finalR2, 1.0, item.speed, item.bassType, item.trebleType);
            if (pcm2 != null && pcm2.pcm != null && pcm2.pcm.length > 0) { for (int i=0; i<pcm2.pcm.length; i++) pcm2.pcm[i] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, pcm2.pcm[i] * item.volume)); encodePcmToFile(pcm2, new java.io.File(outPath2)); }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return; tvLoading.setVisibility(View.GONE); timelineLayout.setVisibility(View.VISIBLE); timelineControls.setVisibility(View.VISIBLE);
                saveState(); AudioTrackItem p1 = new AudioTrackItem(item.name + (isRemove ? " R1" : " C1"), outPath1); p1.isBase = item.isBase; p1.offsetPx = item.offsetPx;
                try { MediaPlayer mp = new MediaPlayer(); mp.setDataSource(outPath1); mp.prepare(); p1.originalDurMs = mp.getDuration(); p1.durMs = p1.originalDurMs; mp.release(); } catch(Exception ignored){}
                AudioTrackItem p2 = new AudioTrackItem(item.name + (isRemove ? " R2" : " C2"), outPath2); p2.isBase = item.isBase; p2.offsetPx = item.offsetPx + (int)((isRemove ? finalR2 : finalR1) * trackW);
                try { MediaPlayer mp = new MediaPlayer(); mp.setDataSource(outPath2); mp.prepare(); p2.originalDurMs = mp.getDuration(); p2.durMs = p2.originalDurMs; mp.release(); } catch(Exception ignored){}
                projectTracks.remove(targetTrimIndex); projectTracks.add(targetTrimIndex, p2); projectTracks.add(targetTrimIndex, p1);
                recalculateTimeline(); prepareMultiTrackPlayers(); Toast.makeText(this, "Audio Split Successfully!", Toast.LENGTH_SHORT).show(); exitTrimMode();
            });
        }).start();
    }

    private void applyTrimToTrack(double startRatio, double endRatio) {
        if (projectTracks.isEmpty() || targetTrimIndex >= projectTracks.size()) return;
        drawerLayout.closeDrawers(); stopPlayback(); tvLoading.setVisibility(View.VISIBLE); tvLoading.setText("Preparing Trim..."); timelineLayout.setVisibility(View.GONE); timelineControls.setVisibility(View.GONE);
        AudioTrackItem targetItem = projectTracks.get(targetTrimIndex); String outPath = new java.io.File(getCacheDir(), "trimmed_" + System.currentTimeMillis() + ".m4a").getAbsolutePath();
        new Thread(() -> {
            PcmAudio trimmedPcm = decodeToPcm(targetItem.path, startRatio, endRatio, targetItem.speed, targetItem.bassType, targetItem.trebleType); boolean success = false;
            if (trimmedPcm != null && trimmedPcm.pcm != null && trimmedPcm.pcm.length > 0) { for (int i = 0; i < trimmedPcm.pcm.length; i++) trimmedPcm.pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, trimmedPcm.pcm[i] * targetItem.volume)); success = encodePcmToFile(trimmedPcm, new java.io.File(outPath)); }
            boolean finalSuccess = success;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return; tvLoading.setVisibility(View.GONE); timelineLayout.setVisibility(View.VISIBLE); timelineControls.setVisibility(View.VISIBLE);
                if (finalSuccess) {
                    saveState(); targetItem.path = outPath; targetItem.waveCache = null; targetItem.speed = 1.0f; targetItem.bassType = 0; targetItem.trebleType = 0;
                    try { MediaPlayer tempMp = new MediaPlayer(); tempMp.setDataSource(outPath); tempMp.prepare(); targetItem.originalDurMs = tempMp.getDuration(); targetItem.durMs = targetItem.originalDurMs; tempMp.release(); } catch(Exception ignored){}
                    recalculateTimeline(); prepareMultiTrackPlayers(); Toast.makeText(this, "Audio Trimmed Successfully!", Toast.LENGTH_SHORT).show();
                } else { Toast.makeText(this, "Trimming Failed", Toast.LENGTH_SHORT).show(); }
            });
        }).start();
    }

    private void rebuildDrawerLayersUI() {
        drawerTracksContainer.removeAllViews(); float density = getResources().getDisplayMetrics().density;
        drawerTracksContainer.setOnDragListener((v, e) -> {
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED: return true;
                case DragEvent.ACTION_DROP:
                    View draggedView = (View) e.getLocalState(); int oldIdx = drawerTracksContainer.indexOfChild(draggedView), newIdx = -1;
                    for (int j = 0; j < drawerTracksContainer.getChildCount(); j++) { View c = drawerTracksContainer.getChildAt(j); if (e.getY() < c.getY() + c.getHeight() / 2f) { newIdx = j; break; } }
                    if (oldIdx != -1) { saveState(); AudioTrackItem item = projectTracks.remove(oldIdx); if (newIdx == -1) projectTracks.add(item); else { if (newIdx > oldIdx) newIdx--; projectTracks.add(newIdx, item); } recalculateTimeline(); prepareMultiTrackPlayers(); }
                    draggedView.setVisibility(View.VISIBLE); return true;
                case DragEvent.ACTION_DRAG_ENDED: ((View) e.getLocalState()).setVisibility(View.VISIBLE); return true;
            } return false;
        });
        for (int i = 0; i < projectTracks.size(); i++) {
            AudioTrackItem item = projectTracks.get(i); final int oIdx = i;
            RelativeLayout row = new RelativeLayout(this); row.setBackground(createPill(Color.parseColor("#5A9AF4")));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, (int)(12*density)); row.setLayoutParams(lp); row.setPadding((int)(20*density), (int)(16*density), (int)(20*density), (int)(16*density));
            TextView tv = new TextView(this); tv.setText("≡  " + (item.isBase ? "Base:\n" : "Layer:\n") + item.name); tv.setTextColor(Color.WHITE); tv.setTextSize(14f); tv.setTypeface(null, Typeface.BOLD);
            RelativeLayout.LayoutParams tvLp = new RelativeLayout.LayoutParams(-2, -2); tvLp.addRule(RelativeLayout.ALIGN_PARENT_START); tvLp.addRule(RelativeLayout.CENTER_VERTICAL); tvLp.setMargins(0, 0, (int)(50*density), 0); tv.setLayoutParams(tvLp);
            TextView btnDel = new TextView(this); btnDel.setText("✖"); btnDel.setTextColor(Color.RED); btnDel.setTextSize(24f); btnDel.setTypeface(null, Typeface.BOLD); btnDel.setPadding(20, 20, 20, 20);
            RelativeLayout.LayoutParams delLp = new RelativeLayout.LayoutParams(-2, -2); delLp.addRule(RelativeLayout.ALIGN_PARENT_END); delLp.addRule(RelativeLayout.CENTER_VERTICAL); btnDel.setLayoutParams(delLp);
            btnDel.setOnClickListener(v -> { saveState(); projectTracks.remove(oIdx); if (projectTracks.isEmpty()) { stopPlayback(); timelineLayout.setVisibility(View.GONE); timelineControls.setVisibility(View.GONE); btnLoadAudio.setVisibility(View.VISIBLE); drawerLayout.closeDrawers(); } else { boolean hasBase = false; for(AudioTrackItem t : projectTracks) { if(t.isBase) { hasBase = true; break; } } if(!hasBase) projectTracks.get(0).isBase = true; recalculateTimeline(); prepareMultiTrackPlayers(); } });
            row.addView(tv); row.addView(btnDel); row.setOnLongClickListener(v -> { v.startDragAndDrop(ClipData.newPlainText("", ""), new View.DragShadowBuilder(v), v, 0); v.setVisibility(View.INVISIBLE); return true; });
            drawerTracksContainer.addView(row);
        }
    }

    private void rebuildTimelineUI() {
        tracksContainer.removeAllViews(); rebuildDrawerLayersUI(); float density = getResources().getDisplayMetrics().density;
        int exactWidth = (int) Math.ceil(maxTimelineMs / msPerPx); tracksContainer.setLayoutParams(new RelativeLayout.LayoutParams(exactWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        Runnable onLayerDragEnd = () -> {
            long tempMax = 1; for (AudioTrackItem t : projectTracks) { if (t.isBase) { long endMs = (long)(t.offsetPx * msPerPx) + t.durMs; if (endMs > tempMax) tempMax = endMs; } }
            if (tempMax == 1 && !projectTracks.isEmpty()) { for (AudioTrackItem t : projectTracks) { long endMs = (long)(t.offsetPx * msPerPx) + t.durMs; if (endMs > tempMax) tempMax = endMs; } }
            maxTimelineMs = tempMax; int eW = (int) Math.ceil(maxTimelineMs / msPerPx);
            long tSec = maxTimelineMs / 1000; tvTotalDuration.setText("Total: " + String.format(Locale.US, "%02d:%02d", tSec / 60, tSec % 60));
            tracksContainer.setLayoutParams(new RelativeLayout.LayoutParams(eW, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (!isPlaying && !activePlayers.isEmpty()) { for (int i = 0; i < activePlayers.size(); i++) { long delay = (long) (projectTracks.get(i).offsetPx * msPerPx); activePlayers.get(i).seekTo(playheadMs >= delay ? (int)(playheadMs - delay) : 0); } }
        };
        for (int i = 0; i < projectTracks.size(); i++) {
            AudioTrackItem item = projectTracks.get(i); TextView tv = new TextView(this);
            tv.setText(" " + (item.isBase ? "Base: " : "Layer: ") + item.name + (item.speed != 1.0f ? " (" + item.speed + "x)" : "") + (item.bassType == 1 ? " [High Bass]" : item.bassType == -1 ? " [Low Bass]" : "") + (item.trebleType == 1 ? " [High Treble]" : item.trebleType == -1 ? " [Low Treble]" : "")); tv.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); tv.setTextSize(14f);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2); tp.setMargins(item.offsetPx, (int)(12 * density), 0, (int)(4 * density)); tv.setLayoutParams(tp);
            int pixelWidth = Math.max(10, (int)(item.durMs / msPerPx)); int buckets = Math.max(10, pixelWidth / 6);
            WaveformView wv = new WaveformView(this, item, true, onLayerDragEnd, tv, item.isBase, isDarkTheme, buckets, timelineScroller); if (i == 0) activeWaveformView = wv;
            int curIcon = item.isMuted ? android.R.drawable.ic_lock_silent_mode_off : android.R.drawable.ic_media_play; tv.setCompoundDrawablesWithIntrinsicBounds(curIcon, 0, 0, 0); if (tv.getCompoundDrawables()[0] != null) tv.getCompoundDrawables()[0].setColorFilter(new PorterDuffColorFilter(item.isMuted ? Color.GRAY : Color.parseColor("#5185FF"), PorterDuff.Mode.SRC_IN)); tv.setAlpha(item.isMuted ? 0.5f : 1.0f); wv.setAlpha(item.isMuted ? 0.3f : 1.0f);
            tv.setOnClickListener(v -> { saveState(); item.isMuted = !item.isMuted; tv.setAlpha(item.isMuted ? 0.5f : 1.0f); wv.setAlpha(item.isMuted ? 0.3f : 1.0f); int ic = item.isMuted ? android.R.drawable.ic_lock_silent_mode_off : android.R.drawable.ic_media_play; tv.setCompoundDrawablesWithIntrinsicBounds(ic, 0, 0, 0); if (tv.getCompoundDrawables()[0] != null) tv.getCompoundDrawables()[0].setColorFilter(new PorterDuffColorFilter(item.isMuted ? Color.GRAY : Color.parseColor("#5185FF"), PorterDuff.Mode.SRC_IN)); int idx = projectTracks.indexOf(item); if (idx != -1 && !activePlayers.isEmpty() && activePlayers.size() > idx) activePlayers.get(idx).setVolume(item.isMuted ? 0f : item.volume, item.isMuted ? 0f : item.volume); });
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(pixelWidth, (int)(60 * density)); wp.leftMargin = item.offsetPx; wv.setLayoutParams(wp); tracksContainer.addView(tv); tracksContainer.addView(wv);
        }
        playhead.bringToFront(); playhead.setElevation(50f); splitPlayhead.bringToFront(); splitPlayhead.setElevation(51f);
    }

    private static class WaveformView extends View {
        private final Paint bgPaint, wavePaint, dimPaint, handlePaint, linePaint, textPaint; private final float[] waveData;
        private boolean isTrimMode = false, isLoading = true; private float trimStartRatio = 0.0f, trimEndRatio = 1.0f; private int draggingHandle = 0, loadProgress = 0;
        private final AudioTrackItem trackItem; private final boolean isDraggableLayer, isBaseLayer; private float startTouchX = 0f; private int startOffsetPx = 0;
        private final Runnable onDragEnd; private final TextView pairedTitle; private long lastClickTime = 0; private int cachedW = 0; private LinearGradient shader;
        private final HorizontalScrollView scroller;
        private final int[] baseColors = new int[]{Color.parseColor("#4FC3F7"), Color.parseColor("#2196F3"), Color.parseColor("#1976D2")};
        private final int[] layerColors = new int[]{Color.parseColor("#FF5722"), Color.parseColor("#FFEB3B"), Color.parseColor("#E91E63"), Color.parseColor("#9C27B0"), Color.parseColor("#2196F3")};

        public WaveformView(Context context, AudioTrackItem item, boolean draggable, Runnable onDragEnd, TextView title, boolean isBase, boolean isDark, int buckets, HorizontalScrollView hsv) {
            super(context); this.trackItem = item; this.isDraggableLayer = draggable; this.onDragEnd = onDragEnd; this.pairedTitle = title; this.isBaseLayer = isBase; this.scroller = hsv;
            bgPaint = new Paint(); bgPaint.setColor(Color.TRANSPARENT); wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG); wavePaint.setStrokeWidth(3f); wavePaint.setStrokeCap(Paint.Cap.ROUND);
            dimPaint = new Paint(); dimPaint.setColor(Color.parseColor(isDark ? "#99000000" : "#99FFFFFF")); handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG); handlePaint.setColor(Color.parseColor(isDark ? "#FFFFFF" : "#000000"));
            linePaint = new Paint(); linePaint.setColor(Color.parseColor(isDark ? "#FFFFFF" : "#000000")); linePaint.setStrokeWidth(4f);
            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG); textPaint.setColor(linePaint.getColor()); textPaint.setTextSize(36f); textPaint.setTypeface(Typeface.DEFAULT_BOLD); textPaint.setTextAlign(Paint.Align.LEFT);
            if (item.waveCache != null && item.waveCache.length == buckets) { waveData = new float[buckets]; System.arraycopy(item.waveCache, 0, waveData, 0, buckets); isLoading = false; }
            else { waveData = new float[buckets]; java.util.Arrays.fill(waveData, 0.01f); loadTrueWaveform(); }
        }

        private void loadTrueWaveform() {
            new Thread(() -> {
                MediaExtractor ex = new MediaExtractor(); android.media.MediaCodec cd = null;
                try {
                    ex.setDataSource(trackItem.path); int trk = -1; long dur = 0;
                    for (int i = 0; i < ex.getTrackCount(); i++) { MediaFormat fmt = ex.getTrackFormat(i); String mime = fmt.getString(MediaFormat.KEY_MIME); if (mime != null && mime.startsWith("audio/")) { trk = i; dur = fmt.getLong(MediaFormat.KEY_DURATION); break; } }
                    if (trk == -1) { ex.release(); return; }
                    ex.selectTrack(trk); MediaFormat fmt = ex.getTrackFormat(trk); String codecMime = fmt.getString(MediaFormat.KEY_MIME); if (codecMime == null) { ex.release(); return; }
                    cd = MediaCodec.createDecoderByType(codecMime); cd.configure(fmt, null, null, 0); cd.start();
                    MediaCodec.BufferInfo inf = new MediaCodec.BufferInfo(); boolean isEOS = false; int lastProgress = -1; List<Float> amps = new ArrayList<>();
                    while (!isEOS) {
                        int inId = cd.dequeueInputBuffer(5000);
                        if (inId >= 0) { ByteBuffer buf = cd.getInputBuffer(inId); if (buf != null) { int sz = ex.readSampleData(buf, 0); if (sz >= 0) { cd.queueInputBuffer(inId, 0, sz, ex.getSampleTime(), 0); ex.advance(); } else { cd.queueInputBuffer(inId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); } } }
                        int outId = cd.dequeueOutputBuffer(inf, 5000);
                        if (outId >= 0) {
                            ByteBuffer buf = cd.getOutputBuffer(outId);
                            if (buf != null && inf.size > 0) { ShortBuffer sBuf = buf.asShortBuffer(); int sz = sBuf.remaining(); float sum = 0; for (int j = 0; j < sz; j++) sum += Math.abs(sBuf.get(j)); amps.add((sz > 0) ? (sum / sz) : 0f); loadProgress = (int) ((inf.presentationTimeUs * 100f) / dur); loadProgress = Math.max(0, Math.min(100, loadProgress)); }
                            cd.releaseOutputBuffer(outId, false); if ((inf.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) isEOS = true;
                            if (loadProgress != lastProgress || isEOS) { lastProgress = loadProgress; postInvalidate(); }
                        }
                    }
                    cd.stop(); cd.release(); ex.release(); float max = 0.01f; int total = amps.size(); int buckets = waveData.length;
                    if (total > 0) { float step = (float) total / buckets; for (int i = 0; i < buckets; i++) { int start = (int) (i * step), end = (int) ((i + 1) * step); end = Math.max(start + 1, Math.min(total, end)); float peak = 0; for (int j = start; j < end; j++) { if (amps.get(j) > peak) peak = amps.get(j); } waveData[i] = peak; if (peak > max) max = peak; } }
                    for (int i = 0; i < buckets; i++) waveData[i] = Math.max(0.01f, waveData[i] / max);
                    trackItem.waveCache = waveData.clone(); isLoading = false; postInvalidate();
                } catch (Exception ignored) { isLoading = false; java.util.Arrays.fill(waveData, 0.05f); postInvalidate();
                } finally { try { if (cd != null) { cd.stop(); cd.release(); } } catch (Exception ignored) {} try { ex.release(); } catch (Exception ignored) {} }
            }).start();
        }

        public void setTrimMode(boolean active) { this.isTrimMode = active; if (active) { trimStartRatio = 0f; trimEndRatio = 1f; } invalidate(); }
        public float getTrimStartRatio() { return trimStartRatio; } public float getTrimEndRatio() { return trimEndRatio; }

        @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
            boolean handled = false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                long ct = System.currentTimeMillis();
                if (ct - lastClickTime < 300) {
                    AudioEditorActivity act = (AudioEditorActivity) getContext();
                    if (act.isSplitMode && !act.isSplitRemoveMode) act.moveSplitPlayhead(trackItem, trackItem.offsetPx + event.getX()); else act.seekToTimelineX(trackItem.offsetPx + event.getX());
                    lastClickTime = 0; return true;
                }
                lastClickTime = ct; handled = true;
            }
            if (isTrimMode) {
                float x = event.getX(), w = getWidth(), sx = trimStartRatio * w, ex = trimEndRatio * w;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: if (Math.abs(x - sx) < 80f) draggingHandle = 1; else if (Math.abs(x - ex) < 80f) draggingHandle = 2; if (draggingHandle != 0) { getParent().requestDisallowInterceptTouchEvent(true); return true; } break;
                    case MotionEvent.ACTION_MOVE: if (draggingHandle == 1) { trimStartRatio = Math.max(0f, Math.min(x / w, trimEndRatio - 0.05f)); invalidate(); } else if (draggingHandle == 2) { trimEndRatio = Math.max(trimStartRatio + 0.05f, Math.min(x / w, 1f)); invalidate(); } break;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: draggingHandle = 0; getParent().requestDisallowInterceptTouchEvent(false); break;
                } return true;
            } else if (isDraggableLayer) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: startTouchX = event.getRawX(); startOffsetPx = trackItem.offsetPx; getParent().requestDisallowInterceptTouchEvent(true); return true;
                    case MotionEvent.ACTION_MOVE: trackItem.offsetPx = Math.max(0, startOffsetPx + (int)(event.getRawX() - startTouchX)); ViewGroup.MarginLayoutParams wp = (ViewGroup.MarginLayoutParams) getLayoutParams(); wp.leftMargin = trackItem.offsetPx; setLayoutParams(wp); ViewGroup.MarginLayoutParams tp = (ViewGroup.MarginLayoutParams) pairedTitle.getLayoutParams(); tp.leftMargin = trackItem.offsetPx; pairedTitle.setLayoutParams(tp); return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: getParent().requestDisallowInterceptTouchEvent(false); if (onDragEnd != null) onDragEnd.run(); return true;
                }
            } return handled || super.onTouchEvent(event);
        }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas); int w = getWidth(), h = getHeight(); canvas.drawRect(0, 0, w, h, bgPaint); float centerY = h / 2f, stepX = (float) w / waveData.length;
            if (w > 0 && w != cachedW) { cachedW = w; shader = new LinearGradient(0, 0, w, 0, isBaseLayer ? baseColors : layerColors, null, Shader.TileMode.CLAMP); wavePaint.setShader(shader); }
            for (int i = 0; i < waveData.length; i++) { float cx = i * stepX, ah = (waveData[i] * h) / 2f; canvas.drawLine(cx, centerY - ah, cx, centerY + ah, wavePaint); }
            if (isLoading) { float scrollX = scroller != null ? scroller.getScrollX() : 0; float textX = Math.max(30f, scrollX - trackItem.offsetPx + 30f); float tw = textPaint.measureText("Scanning Audio... 100%"); textX = Math.min(textX, w - tw - 10f); canvas.drawText("Scanning Audio... " + loadProgress + "%", textX, h / 2f + 12f, textPaint); }
            if (isTrimMode) { float sx = trimStartRatio * w, ex = trimEndRatio * w; canvas.drawRect(0, 0, sx, h, dimPaint); canvas.drawRect(ex, 0, w, h, dimPaint); canvas.drawLine(sx, 0, sx, h, linePaint); canvas.drawLine(ex, 0, ex, h, linePaint); canvas.drawCircle(sx, centerY, 20f, handlePaint); canvas.drawCircle(ex, centerY, 20f, handlePaint); }
        }
    }

    private void prepareMultiTrackPlayers() { stopPlayback(); timelineLayout.setVisibility(View.VISIBLE); timelineControls.setVisibility(View.VISIBLE); btnPlayPause.setImageResource(android.R.drawable.ic_media_play); }

    private void togglePlayback() {
        if (projectTracks.isEmpty()) return;
        if (isPlaying) { stopPlayback(); btnPlayPause.setImageResource(android.R.drawable.ic_media_play); } else { startPlayback(); btnPlayPause.setImageResource(android.R.drawable.ic_media_pause); }
    }

    private void startPlayback() {
        stopPlayback();
        try {
            for (AudioTrackItem item : projectTracks) {
                MediaPlayer mp = new MediaPlayer(); mp.setDataSource(item.path); mp.prepare(); mp.setPlaybackParams(new android.media.PlaybackParams().setSpeed(item.speed)); if(item.isMuted) mp.setVolume(0f,0f); else mp.setVolume(item.volume,item.volume);
                try { if (item.bassType == 1) { android.media.audiofx.BassBoost bb = new android.media.audiofx.BassBoost(0, mp.getAudioSessionId()); bb.setEnabled(true); bb.setStrength((short) 1000); activeEffects.add(bb); } if (item.bassType == -1 || item.trebleType != 0) { android.media.audiofx.Equalizer eq = new android.media.audiofx.Equalizer(0, mp.getAudioSessionId()); eq.setEnabled(true); short bands = eq.getNumberOfBands(); for (short b = 0; b < bands; b++) { int cFreq = eq.getCenterFreq(b); if (item.bassType == -1 && cFreq < 300000) eq.setBandLevel(b, (short) -1500); if (item.trebleType != 0 && cFreq >= 3000000) eq.setBandLevel(b, (short) (item.trebleType == 1 ? 1500 : -1500)); } activeEffects.add(eq); } } catch (Exception ignored) {}
                activePlayers.add(mp);
            }
            for (int i = 0; i < activePlayers.size(); i++) { long delay = (long) (projectTracks.get(i).offsetPx * msPerPx); if (playheadMs >= delay && playheadMs < delay + projectTracks.get(i).durMs) { activePlayers.get(i).seekTo((int)(playheadMs - delay)); activePlayers.get(i).start(); } }
            isPlaying = true; handler.removeCallbacks(updatePlayhead); handler.postDelayed(updatePlayhead, 30);
        } catch (Exception e) { Toast.makeText(this, "Playback Error", Toast.LENGTH_SHORT).show(); }
    }

    private void stopPlayback() {
        isPlaying = false; handler.removeCallbacks(updatePlayhead);
        for (android.media.audiofx.AudioEffect ef : activeEffects) { try { ef.release(); } catch (Exception ignored) {} } activeEffects.clear();
        for (MediaPlayer mp : activePlayers) { try { if (mp.isPlaying()) mp.stop(); mp.release(); } catch (Exception ignored) {} } activePlayers.clear();
    }

    @SuppressLint("Range") private String getFileName(Uri uri) { String res = "Track"; if (uri.getScheme() != null && uri.getScheme().equals("content")) { try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) { if (c != null && c.moveToFirst()) res = c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME)); } catch (Exception ignored) {} } return res; }

    private GradientDrawable createPill(int col) { GradientDrawable gd = new GradientDrawable(); gd.setColor(col); gd.setCornerRadius(100f); return gd; }

    private void styleToolButton(TextView tv, int iconRes, int bgColor, int textColor) { tv.setTextColor(textColor); tv.setBackground(createPill(bgColor)); tv.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0); tv.setCompoundDrawablePadding(32); if (tv.getCompoundDrawables()[0] != null) tv.getCompoundDrawables()[0].setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN)); }
    private void styleToolLayout(LinearLayout layout, ImageView ic, TextView tv, int bgColor, int textColor) { layout.setBackground(createPill(bgColor)); tv.setTextColor(textColor); ic.setColorFilter(textColor, PorterDuff.Mode.SRC_IN); }

    private void applyTheme() {
        int bgColor = isDarkTheme ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7"), drawerBg = isDarkTheme ? Color.parseColor("#CC1C1C1E") : Color.parseColor("#CCF2F2F7");
        int textColor = isDarkTheme ? Color.WHITE : Color.BLACK, pillBg = isDarkTheme ? Color.parseColor("#332D2B") : Color.WHITE, toolBg = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.parseColor("#E5E5EA"), blueAccent = Color.parseColor("#5A9AF4");
        getWindow().setStatusBarColor(bgColor); mainContent.setBackgroundColor(bgColor); leftDrawer.setBackgroundColor(drawerBg); rightDrawer.setBackgroundColor(drawerBg);
        tvTitle.setTextColor(textColor); tvTotalDuration.setTextColor(isDarkTheme ? Color.LTGRAY : Color.DKGRAY); ((TextView) findViewById(R.id.tvLeftTitle)).setTextColor(textColor); ((TextView) findViewById(R.id.tvTracksEmpty)).setTextColor(textColor); ((TextView) findViewById(R.id.tvRightTitle)).setTextColor(textColor);
        btnGallery.setBackground(createPill(pillBg)); btnGallery.setTextColor(textColor); btnExport.setBackground(createPill(pillBg)); btnExport.setTextColor(textColor);
        btnTracks.setBackground(createPill(pillBg)); btnTracks.setTextColor(textColor); btnTools.setBackground(createPill(pillBg)); btnTools.setTextColor(textColor);
        btnCancelTrim.setBackground(createPill(pillBg)); btnCancelTrim.setTextColor(textColor); btnLoadAudio.setBackground(createPill(blueAccent)); btnApplyTrim.setBackground(createPill(Color.parseColor("#34C759")));
        styleToolButton(btnAddTrack, android.R.drawable.ic_input_add, toolBg, textColor); styleToolButton(toolTrim, android.R.drawable.ic_menu_edit, toolBg, textColor); styleToolButton(toolMerge, android.R.drawable.ic_menu_add, toolBg, textColor); styleToolButton(toolVolume, android.R.drawable.ic_lock_silent_mode_off, toolBg, textColor); styleToolButton(toolSpeed, android.R.drawable.ic_media_ff, toolBg, textColor); styleToolButton(toolBass, android.R.drawable.ic_menu_sort_by_size, toolBg, textColor); styleToolButton(toolTreble, android.R.drawable.ic_menu_sort_alphabetically, toolBg, textColor); styleToolButton(toolSplit, android.R.drawable.ic_menu_crop, toolBg, textColor);
        styleToolLayout(toolUndo, icUndo, tvUndo, toolBg, textColor); styleToolLayout(toolRedo, icRedo, tvRedo, toolBg, textColor);
        timelineControls.setBackground(null);
        GradientDrawable playBg = new GradientDrawable(); playBg.setColor(textColor); playBg.setShape(GradientDrawable.OVAL); btnPlayPause.setBackground(playBg); btnPlayPause.setColorFilter(bgColor, PorterDuff.Mode.SRC_IN);
        btnSeekStart.setBackground(null); btnSeekStart.setColorFilter(textColor, PorterDuff.Mode.SRC_IN); btnSeekStart.setAlpha(0.7f); btnSeekEnd.setBackground(null); btnSeekEnd.setColorFilter(textColor, PorterDuff.Mode.SRC_IN); btnSeekEnd.setAlpha(0.7f);
        GradientDrawable splitDot = new GradientDrawable(); splitDot.setColor(Color.parseColor("#4CAF50")); splitDot.setShape(GradientDrawable.OVAL); splitPlayheadDot.setBackground(splitDot);
    }

    private String copyUriToCache(Uri uri, String name) {
        try { java.io.InputStream in = getContentResolver().openInputStream(uri); if (in == null) return null; java.io.File f = new java.io.File(getCacheDir(), name); java.io.OutputStream out = new java.io.FileOutputStream(f); byte[] buf = new byte[1024]; int len; while ((len = in.read(buf)) > 0) out.write(buf, 0, len); in.close(); out.close(); return f.getAbsolutePath(); } catch (Exception e) { return null; }
    }

    private void showExportDialog() {
        boolean allMuted = true; for(AudioTrackItem t: projectTracks) if(!t.isMuted) { allMuted = false; break; }
        if (projectTracks.isEmpty() || allMuted) { Toast.makeText(this, "No active tracks to export!", Toast.LENGTH_SHORT).show(); return; }
        List<String> formats = new ArrayList<>(); formats.add("Audio Format (.m4a)"); formats.add("MP3 Format (.mp3)"); formats.add("Opus Format (.opus)");
        showCustomDialog("Export Format", formats, idx -> { String ext = ".m4a"; if (idx == 1) ext = ".mp3"; else if (idx == 2) ext = ".opus"; exportProjectToGallery(ext); });
    }

    private void exportProjectToGallery(String ext) {
        stopPlayback(); tvLoading.setVisibility(View.VISIBLE); tvLoading.setText("Preparing Export..."); timelineLayout.setVisibility(View.GONE); timelineControls.setVisibility(View.GONE);
        java.io.File dir = new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "OWN's Audio Gallery"); if (!dir.exists()) { dir.mkdirs(); }
        java.io.File outFile = new java.io.File(dir, "Studio_Track_" + System.currentTimeMillis() + ext);
        new Thread(() -> {
            boolean success = nativeExportMultiTrack(outFile);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return; tvLoading.setVisibility(View.GONE); timelineLayout.setVisibility(View.VISIBLE); timelineControls.setVisibility(View.VISIBLE);
                if (success) { MediaScannerConnection.scanFile(this, new String[]{outFile.getAbsolutePath()}, null, null); Toast.makeText(this, "Saved to Music/OWN's Audio Gallery!", Toast.LENGTH_LONG).show(); } else Toast.makeText(this, "Export Failed", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private boolean nativeExportMultiTrack(java.io.File outFile) {
        try {
            List<AudioTrackItem> active = new ArrayList<>(); for(AudioTrackItem t : projectTracks) if(!t.isMuted) active.add(t);
            if (active.isEmpty()) return false;
            int totalSamples = (int)((maxTimelineMs / 1000.0) * 44100); short[] masterPcm = new short[totalSamples * 2]; int tCount = 1;
            for (AudioTrackItem item : active) {
                final int curT = tCount++; runOnUiThread(() -> tvLoading.setText("Decoding Track " + curT + "..."));
                PcmAudio a = decodeToPcm(item.path, 0.0, 1.0, item.speed, item.bassType, item.trebleType); if (a == null || a.pcm == null) continue;
                int startSample = (int)(((item.offsetPx * msPerPx) / 1000.0) * 44100) * 2; if (startSample % 2 != 0) startSample--;
                for (int i = 0; i < a.pcm.length && (startSample + i) < masterPcm.length; i++) { int mix = masterPcm[startSample + i] + (int)(a.pcm[i] * item.volume); masterPcm[startSample + i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mix)); }
            }
            runOnUiThread(() -> tvLoading.setText("Encoding Audio..."));
            return encodePcmToFile(new PcmAudio(masterPcm, 44100, 2), outFile);
        } catch (Exception e) { return false; }
    }

    public PcmAudio decodeToPcm(String path, double startRatio, double endRatio, float speed, int bassType, int trebleType) {
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(path); int track = -1; long durUs = 0;
            for (int i = 0; i < ex.getTrackCount(); i++) { MediaFormat fmt = ex.getTrackFormat(i); String mime = fmt.getString(MediaFormat.KEY_MIME); if (mime != null && mime.startsWith("audio/")) { track = i; durUs = fmt.getLong(MediaFormat.KEY_DURATION); break; } }
            if (track == -1) return null; ex.selectTrack(track); MediaFormat format = ex.getTrackFormat(track); String mime = format.getString(MediaFormat.KEY_MIME); if (mime == null) return null;
            int sr = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100; int ch = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            MediaCodec cd = MediaCodec.createDecoderByType(mime); cd.configure(format, null, null, 0); cd.start();
            MediaCodec.BufferInfo inf = new MediaCodec.BufferInfo(); java.io.ByteArrayOutputStream pcmStream = new java.io.ByteArrayOutputStream(); boolean isEOS = false;
            long startUs = (long)(durUs * startRatio), endUs = (long)(durUs * endRatio); long totalUs = endUs - startUs; if (totalUs <= 0) totalUs = 1; int lastProgress = -1;
            if (startUs > 0) ex.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            while (!isEOS) {
                int inId = cd.dequeueInputBuffer(5000);
                if (inId >= 0) {
                    ByteBuffer buf = cd.getInputBuffer(inId);
                    if (buf != null) { int sz = ex.readSampleData(buf, 0); long pts = ex.getSampleTime(); if (sz >= 0 && pts <= endUs) { cd.queueInputBuffer(inId, 0, sz, pts, 0); ex.advance(); } else { cd.queueInputBuffer(inId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); } }
                }
                int outId = cd.dequeueOutputBuffer(inf, 5000);
                if (outId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { MediaFormat outFmt = cd.getOutputFormat(); if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sr = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT); }
                else if (outId >= 0) {
                    ByteBuffer buf = cd.getOutputBuffer(outId);
                    if (buf != null && inf.size > 0 && inf.presentationTimeUs >= startUs) { byte[] chunk = new byte[inf.size]; buf.get(chunk); pcmStream.write(chunk); }
                    cd.releaseOutputBuffer(outId, false); if ((inf.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) isEOS = true;
                    if (inf.presentationTimeUs >= startUs) { int p = (int)(((inf.presentationTimeUs - startUs) * 100L) / totalUs); p = Math.max(0, Math.min(100, p)); if (p != lastProgress) { lastProgress = p; final int fp = p; runOnUiThread(() -> tvLoading.setText("Decoding... " + fp + "%")); } }
                }
            }
            cd.stop(); cd.release(); ex.release(); byte[] b = pcmStream.toByteArray(); short[] s = new short[b.length / 2];
            ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s); int targetSr = (int)(sr * speed); short[] resampled = resamplePcm(s, targetSr, ch);
            if (bassType != 0 || trebleType != 0) {
                float bL = 0f, bR = 0f, tL = 0f, tR = 0f; float aBass = 0.02f, aTreble = 0.3f;
                for (int i = 0; i < resampled.length; i+=2) {
                    float sL = resampled[i]; bL += aBass * (sL - bL); tL += aTreble * (sL - tL); float hL = sL - tL;
                    int mL = (int)(sL + (bassType == 1 ? bL * 2.0f : bassType == -1 ? bL * -0.8f : 0) + (trebleType == 1 ? hL * 1.5f : trebleType == -1 ? hL * -0.8f : 0)); resampled[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mL));
                    if (i+1 < resampled.length) { float sR = resampled[i+1]; bR += aBass * (sR - bR); tR += aTreble * (sR - tR); float hR = sR - tR; int mR = (int)(sR + (bassType == 1 ? bR * 2.0f : bassType == -1 ? bR * -0.8f : 0) + (trebleType == 1 ? hR * 1.5f : trebleType == -1 ? hR * -0.8f : 0)); resampled[i+1] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mR)); }
                }
            }
            return new PcmAudio(resampled, 44100, 2);
        } catch (Exception e) { return null; }
    }

    private short[] resamplePcm(short[] in, int inSr, int inCh) {
        if (inSr == 44100 && inCh == 2) return in;
        int inFrames = in.length / inCh, outFrames = (int) ((inFrames * 44100L) / inSr); short[] out = new short[outFrames * 2];
        for (int i = 0; i < outFrames; i++) {
            float inIdx = i * (float) inSr / 44100f; int idx1 = (int) inIdx, idx2 = Math.min(idx1 + 1, inFrames - 1); float frac = inIdx - idx1;
            for (int c = 0; c < 2; c++) { int srcC = c < inCh ? c : 0; short val1 = in[idx1 * inCh + srcC], val2 = in[idx2 * inCh + srcC]; out[i * 2 + c] = (short) (val1 + frac * (val2 - val1)); }
        } return out;
    }

    private boolean encodePcmToFile(PcmAudio audio, java.io.File outFile) {
        try {
            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2); format.setInteger(MediaFormat.KEY_BIT_RATE, 128000); format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC); encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); encoder.start();
            MediaMuxer muxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4); int trackIndex = -1; boolean muxerStarted = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo(); ByteBuffer pcmBuffer = ByteBuffer.allocateDirect(audio.pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN); pcmBuffer.asShortBuffer().put(audio.pcm); int offset = 0; boolean inputEOS = false, outputEOS = false;
            long totalBytes = audio.pcm.length * 2L; if(totalBytes <= 0) totalBytes = 1; int lastProgress = -1;
            while (!outputEOS) {
                if (!inputEOS) {
                    int inId = encoder.dequeueInputBuffer(5000);
                    if (inId >= 0) {
                        ByteBuffer buf = encoder.getInputBuffer(inId);
                        if (buf != null) {
                            buf.clear(); int chunk = Math.min(buf.remaining(), (audio.pcm.length * 2) - offset);
                            if (chunk > 0) {
                                pcmBuffer.position(offset); pcmBuffer.limit(offset + chunk); buf.put(pcmBuffer); long timeUs = (long) offset * 1000000L / (44100L * 4L); encoder.queueInputBuffer(inId, 0, chunk, timeUs, 0); offset += chunk;
                                int p = (int)((offset * 100L) / totalBytes); p = Math.max(0, Math.min(100, p)); if (p != lastProgress) { lastProgress = p; final int fp = p; runOnUiThread(() -> tvLoading.setText("Saving... " + fp + "%")); }
                            } else { encoder.queueInputBuffer(inId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEOS = true; }
                        }
                    }
                }
                int outId = encoder.dequeueOutputBuffer(info, 5000);
                if (outId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { trackIndex = muxer.addTrack(encoder.getOutputFormat()); muxer.start(); muxerStarted = true; }
                else if (outId >= 0) {
                    ByteBuffer outBuf = encoder.getOutputBuffer(outId); if (outBuf != null && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0 && muxerStarted) { muxer.writeSampleData(trackIndex, outBuf, info); }
                    encoder.releaseOutputBuffer(outId, false); if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEOS = true;
                }
            }
            encoder.stop(); encoder.release(); if (muxerStarted) { muxer.stop(); muxer.release(); } return true;
        } catch (Exception e) { return false; }
    }

    @Override protected void onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null); stopPlayback(); }
}