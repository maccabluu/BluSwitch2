// SPDX-License-Identifier: GPL-3.0-or-later
package com.blustudio.bluswitch2.v2;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SAF-only, read-only scan. An extracted title is not necessarily playable. */
final class GameCatalog {
    static final class Game {
        final String title;
        final Uri executable;
        final Uri folder;
        Game(String title, Uri executable, Uri folder) {
            this.title = title;
            this.executable = executable;
            this.folder = folder;
        }
    }

    private static final class Item {
        final String id;
        final String name;
        final String mime;
        Item(String id, String name, String mime) {
            this.id = id;
            this.name = name;
            this.mime = mime;
        }
        boolean isDirectory() {
            return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
        }
    }

    private GameCatalog() {}

    static List<Game> scan(Context context, List<Uri> roots) {
        Map<String, Game> found = new LinkedHashMap<>();
        for (Uri tree : roots) {
            try {
                String rootId = DocumentsContract.getTreeDocumentId(tree);
                String rootName = nameOf(context, tree, rootId);
                addIfGame(context, tree, rootId, rootName, found);
                // Scan selected game's direct children, not entire device storage.
                for (Item item : children(context, tree, rootId)) {
                    if (item.isDirectory()) {
                        addIfGame(context, tree, item.id, item.name, found);
                    }
                }
            } catch (RuntimeException ignored) {
                // A revoked SAF permission must not crash the app.
            }
        }
        return new ArrayList<>(found.values());
    }

    private static void addIfGame(Context context, Uri tree, String directoryId,
                                  String title, Map<String, Game> found) {
        Item exefs = null;
        Item romfs = null;
        for (Item item : children(context, tree, directoryId)) {
            if (item.isDirectory() && item.name.equalsIgnoreCase("exefs")) exefs = item;
            if (item.isDirectory() && item.name.equalsIgnoreCase("romfs")) romfs = item;
        }
        if (exefs == null || romfs == null) return;
        for (Item file : children(context, tree, exefs.id)) {
            if (!file.isDirectory() && file.name.equalsIgnoreCase("main")) {
                Uri exe = DocumentsContract.buildDocumentUriUsingTree(tree, file.id);
                Uri folder = DocumentsContract.buildDocumentUriUsingTree(tree, directoryId);
                found.put(exe.toString(), new Game(title, exe, folder));
                return;
            }
        }
    }

    private static String nameOf(Context context, Uri tree, String id) {
        Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, id);
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException ignored) {}
        return "Extracted game";
    }

    private static List<Item> children(Context context, Uri tree, String parentId) {
        List<Item> items = new ArrayList<>();
        Uri childUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] columns = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = context.getContentResolver().query(
                childUri, columns, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    items.add(new Item(cursor.getString(0), cursor.getString(1), cursor.getString(2)));
                }
            }
        } catch (RuntimeException ignored) {
            // Revoked/unavailable provider: treat as an empty folder.
        }
        return items;
    }
}
