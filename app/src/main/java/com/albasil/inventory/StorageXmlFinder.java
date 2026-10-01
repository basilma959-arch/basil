package com.albasil.bm;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        Set<String> seen = new HashSet<>();

        // 1) Android's shared-storage index. This is the most reliable source on
        // modern Android and can see XML files regardless of their MIME label.
        queryMediaStore(context, MediaStore.Files.getContentUri("external"), result, seen);

        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                queryMediaStore(context, MediaStore.Downloads.getContentUri("external"), result, seen);
            }
        } catch (Exception ignored) {
        }

        // 2) Direct filesystem scan for every shared folder Android allows the app
        // to read. This improves coverage on older devices and removable storage.
        Set<String> roots = new HashSet<>();
        try {
            File primary = Environment.getExternalStorageDirectory();
            if (primary != null) roots.add(primary.getAbsolutePath());
        } catch (Exception ignored) {
        }
        try {
            File[] appRoots = context.getExternalFilesDirs(null);
            if (appRoots != null) {
                for (File f : appRoots) {
                    if (f == null) continue;
                    String path = f.getAbsolutePath();
                    int ix = path.indexOf(File.separator + "Android" + File.separator);
                    if (ix > 0) roots.add(path.substring(0, ix));
                }
            }
        } catch (Exception ignored) {
        }

        for (String rootPath : roots) {
            scanReadableTree(new File(rootPath), result, seen);
        }

        Collections.sort(result, Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT)));
        return result;
    }

    private static void queryMediaStore(Context context, Uri base, List<Entry> result, Set<String> seen) {
        Cursor c = null;
        try {
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
                    if (id < 0 || name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xml")) continue;
                    Uri uri = ContentUris.withAppendedId(base, id);
                    String key = uri.toString();
                    if (seen.add(key)) result.add(new Entry(name, uri));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
    }

    private static void scanReadableTree(File root, List<Entry> result, Set<String> seen) {
        if (root == null || !root.exists() || !root.canRead()) return;
        ArrayDeque<File> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        final int MAX_DIRECTORIES = 50000;

        while (!queue.isEmpty() && visited < MAX_DIRECTORIES) {
            File dir = queue.removeFirst();
            visited++;
            if (dir == null || !dir.isDirectory() || !dir.canRead()) continue;

            String abs = dir.getAbsolutePath();
            // App-private Android folders are normally unreadable and expensive to walk.
            if (abs.contains(File.separator + "Android" + File.separator + "data") ||
                    abs.contains(File.separator + "Android" + File.separator + "obb")) {
                continue;
            }

            File[] children;
            try {
                children = dir.listFiles();
            } catch (Exception e) {
                continue;
            }
            if (children == null) continue;

            for (File f : children) {
                if (f == null) continue;
                if (f.isDirectory()) {
                    if (f.canRead()) queue.addLast(f);
                } else {
                    String name = f.getName();
                    if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".xml") && f.canRead()) {
                        String key = "file:" + f.getAbsolutePath();
                        if (seen.add(key)) result.add(new Entry(name, Uri.fromFile(f)));
                    }
                }
            }
        }
    }

    private StorageXmlFinder() {}
}
