package com.abhinav.ownapp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;

public class StickerContentProvider extends ContentProvider {
    // UPDATED: Now matches your new package name exactly
    public static final String AUTHORITY = "com.abhinav.ownapp.stickercontentprovider";
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, "metadata", 1);
        MATCHER.addURI(AUTHORITY, "metadata/*", 2);
        MATCHER.addURI(AUTHORITY, "stickers/*", 3);
        MATCHER.addURI(AUTHORITY, "stickers_asset/*/*", 4);
    }

    @Override
    public boolean onCreate() { return true; }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        int match = MATCHER.match(uri);
        if (match == 1) { return getAllPacksMetadata(); }
        else if (match == 2) { String packId = uri.getPathSegments().get(1); return getSinglePackMetadata(packId); }
        else if (match == 3) { String packId = uri.getPathSegments().get(1); return getStickersForPack(packId); }
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    private Cursor getAllPacksMetadata() {
        MatrixCursor cursor = new MatrixCursor(new String[]{"sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher", "sticker_pack_icon", "android_play_store_link", "ios_app_store_link", "publisher_email", "publisher_website", "privacy_policy_website", "license_agreement_website", "image_data_version", "avoid_cache", "animated_sticker_pack"});
        Context ctx = getContext(); int staticCount = 3;
        if (ctx != null) {
            SharedPreferences prefs = ctx.getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
            staticCount = prefs.getInt("static_pack_count", 3);
        }
        for (int i = 1; i <= staticCount; i++) { addPackRowToCursor(cursor, "ownphoto_pack_" + i); }
        return cursor;
    }

    private Cursor getSinglePackMetadata(String packId) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher", "sticker_pack_icon", "android_play_store_link", "ios_app_store_link", "publisher_email", "publisher_website", "privacy_policy_website", "license_agreement_website", "image_data_version", "avoid_cache", "animated_sticker_pack"});
        addPackRowToCursor(cursor, packId); return cursor;
    }

    private void addPackRowToCursor(MatrixCursor cursor, String packId) {
        Context ctx = getContext(); int version = 3; String packName = "My Custom Pack";
        if (ctx != null) {
            SharedPreferences prefs = ctx.getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
            version = prefs.getInt("sticker_version_" + packId, 3);
            packName = prefs.getString("pack_name_" + packId, "My Custom Pack");
            if (packName == null || packName.isEmpty()) packName = "My Custom Pack";
        }
        cursor.addRow(new Object[]{packId, packName, "OWN app", "tray.png", "", "", "", "", "", "", String.valueOf(version), 0, 0});
    }

    private Cursor getStickersForPack(String packId) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"sticker_file_name", "sticker_emoji"});
        Context context = getContext();
        if (context != null) {
            File dir = new File(context.getFilesDir(), "stickers/" + packId);
            File[] files = dir.listFiles((d, name) -> name.endsWith(".webp") && !name.contains("_tmp") && !name.contains("_thumb") && new File(d, name).length() > 100);
            if (files != null) {
                Arrays.sort(files, (f1, f2) -> { try { return Integer.compare(Integer.parseInt(f1.getName().replace(".webp","")), Integer.parseInt(f2.getName().replace(".webp",""))); } catch(Exception e){ return 0; } });
                for (File file : files) { cursor.addRow(new Object[]{file.getName(), "😀"}); }
            }
        }
        return cursor;
    }

    @Nullable
    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        if (uri.getPathSegments().size() >= 3 && uri.getPathSegments().get(0).equals("stickers_asset")) {
            String packId = uri.getPathSegments().get(1); String fileName = uri.getPathSegments().get(2);
            Context context = getContext(); if (context == null) throw new FileNotFoundException("Context is null");
            File file = new File(context.getFilesDir(), "stickers/" + packId + "/" + fileName);
            if (!file.exists() || file.length() == 0) throw new FileNotFoundException(file.getAbsolutePath());
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            return new AssetFileDescriptor(pfd, 0, file.length());
        }
        return super.openAssetFile(uri, mode);
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) { return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".metadata"; }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
}