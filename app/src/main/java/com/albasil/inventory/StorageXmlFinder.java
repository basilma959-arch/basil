package com.albasil.bm;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

final class StorageXmlFinder {
    static final class Entry {
        final String name;
        final Uri uri;
        Entry(String name, Uri uri) {
            this.name = name;
            this.uri = uri;
        }
    }

    static List<Entry> find(Context context) {
        List<Entry> result = new ArrayList<>();
        Cursor c = null;
        try {
            Uri base = MediaStore.Files.getContentUri("external");
            String[] projection = new String[]{
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME
            };
            c = context.getContentResolver().query(
                    base,
                    projection,
                    MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?",
                    new String[]{"%.xml"},
                    MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE ASC"
            );
            if (c != null) {
                int idIx = c.getColumnIndex(MediaStore.Files.FileColumns._ID);
                int nameIx = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME);
                while (c.moveToNext()) {
                    long id = idIx >= 0 ? c.getLong(idIx) : -1;
                    String name = nameIx >= 0 ? c.getString(nameIx) : "";
                    if (id >= 0 && name != null && name.toLowerCase().endsWith(".xml")) {
                        result.add(new Entry(name, ContentUris.withAppendedId(base, id)));
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    private StorageXmlFinder() {}
}
