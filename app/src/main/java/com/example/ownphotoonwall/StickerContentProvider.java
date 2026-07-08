package com.example.ownphotoonwall;

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

public class StickerContentProvider extends ContentProvider {

    public static final String AUTHORITY = "com.example.ownphotoonwall.stickercontentprovider";
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, "metadata", 1);
        MATCHER.addURI(AUTHORITY, "metadata/*", 2);
        MATCHER.addURI(AUTHORITY, "stickers/*", 3);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (MATCHER.match(uri) == 1 || MATCHER.match(uri) == 2) {
            return getPackMetadata();
        } else if (MATCHER.match(uri) == 3) {
            return getStickers();
        }
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    private Cursor getPackMetadata() {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher",
                "sticker_pack_icon", "android_play_store_link", "ios_app_store_link",
                "publisher_email", "publisher_website", "privacy_policy_website",
                "license_agreement_website", "image_data_version", "avoid_cache", "animated_sticker_pack"
        });

        int version = 3;
        String packName = "My Custom Pack";
        Context ctx = getContext();

        if (ctx != null) {
            SharedPreferences prefs = ctx.getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
            version = prefs.getInt("sticker_version", 3);
            packName = prefs.getString("pack_name", "My Custom Pack");
            if (packName == null || packName.isEmpty()) packName = "My Custom Pack";
        }

        cursor.addRow(new Object[]{
                "ownphoto_pack_1", packName, "OWN app",
                "tray.png", "", "", "", "", "", "", String.valueOf(version), 0, 0
        });
        return cursor;
    }

    private Cursor getStickers() {
        MatrixCursor cursor = new MatrixCursor(new String[]{"sticker_file_name", "sticker_emoji"});

        Context context = getContext();
        if (context != null) {
            File dir = new File(context.getFilesDir(), "stickers");
            File[] files = dir.listFiles((d, name) -> name.endsWith(".webp"));
            if (files != null) {
                for (File file : files) {
                    cursor.addRow(new Object[]{file.getName(), "😀"});
                }
            }
        }
        return cursor;
    }

    @Nullable
    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        if (uri.getPathSegments().size() == 3 && uri.getPathSegments().get(0).equals("stickers_asset")) {
            String fileName = uri.getPathSegments().get(2);
            Context context = getContext();
            if (context == null) throw new FileNotFoundException("Context is null");

            File file = new File(context.getFilesDir(), "stickers/" + fileName);
            if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());

            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            return new AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        }
        return super.openAssetFile(uri, mode);
    }

    @Nullable @Override public String getType(@NonNull Uri uri) { return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".metadata"; }
    @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }
    @Override public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
    @Override public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
}