// SPDX-License-Identifier: GPL-3.0-or-later
package com.blustudio.bluswitch2.v2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A new BluSwitch 2.0 application. It does not load or modify the old BluSwitch2 app. */
public final class MainActivity extends Activity {
    private static final int PICK_FOLDER = 2020;
    private static final String PREFS = "bluswitch_v2_library";
    private static final String FOLDERS = "game_trees";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout gameRow;
    private TextView subtitle;
    private TextView status;
    private TextView libraryLabel;
    private int blue;
    private int dark;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        blue = Color.rgb(46, 138, 252);
        dark = Color.rgb(8, 15, 29);
        buildConsole();
        refreshLibrary();
    }

    private int dp(float size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(
                width < 0 ? width : dp(width), height < 0 ? height : dp(height));
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable background(int top, int bottom, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{top, bottom});
        d.setCornerRadius(dp(radius));
        return d;
    }

    private Button action(String label, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackground(background(blue, Color.rgb(22, 83, 170), 12));
        b.setOnClickListener(onClick);
        return b;
    }

    private void buildConsole() {
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(dark);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(28), dp(20), dp(28), dp(22));
        scroller.addView(page);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("BLUSWITCH 2.0", 25, true, Color.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        Button add = action("+ Add game folder", v -> openFolderPicker());
        header.addView(add, lp(210, 50));
        Button refresh = action("Refresh", v -> refreshLibrary());
        LinearLayout.LayoutParams rlp = lp(110, 50);
        rlp.leftMargin = dp(10);
        header.addView(refresh, rlp);
        page.addView(header, lp(-1, 58));

        subtitle = text("A separate Android console and emulator development project", 15,
                false, Color.rgb(171, 193, 218));
        LinearLayout.LayoutParams sub = lp(-1, -2);
        sub.topMargin = dp(5);
        page.addView(subtitle, sub);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(24), dp(22), dp(24), dp(24));
        hero.setBackground(background(Color.rgb(18, 57, 112),
                Color.rgb(10, 31, 65), 20));
        LinearLayout.LayoutParams h = lp(-1, -2);
        h.topMargin = dp(25);
        page.addView(hero, h);
        hero.addView(text("YOUR GAME LIBRARY", 12, true, Color.rgb(140, 199, 255)));
        hero.addView(text("All your games. One console.", 26, true, Color.WHITE));
        hero.addView(text("Add a folder containing extracted game directories. BluSwitch 2.0 will check them without moving or changing files.",
                15, false, Color.rgb(203, 224, 245)));

        Button engineTest = action("Test native CPU", v -> runEngineTest());
        LinearLayout.LayoutParams testParams = lp(210, 46);
        testParams.topMargin = dp(15);
        hero.addView(engineTest, testParams);

        LinearLayout libraryTitle = new LinearLayout(this);
        libraryTitle.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sectionParams = lp(-1, 50);
        sectionParams.topMargin = dp(18);
        page.addView(libraryTitle, sectionParams);
        libraryLabel = text("LIBRARY", 19, true, Color.WHITE);
        libraryTitle.addView(libraryLabel, new LinearLayout.LayoutParams(0, -2, 1));
        libraryTitle.addView(text("EXTRACTED FOLDERS", 12, false,
                Color.rgb(132, 163, 195)));

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        page.addView(horizontal, lp(-1, 245));
        gameRow = new LinearLayout(this);
        gameRow.setOrientation(LinearLayout.HORIZONTAL);
        horizontal.addView(gameRow);

        status = text("Checking your game folders...", 13, false,
                Color.rgb(176, 199, 221));
        LinearLayout.LayoutParams s = lp(-1, -2);
        s.topMargin = dp(10);
        page.addView(status, s);

        TextView info = text("ENGINE STATUS: Initial native file-probe only. Switch 2 game execution is not implemented or verified.",
                13, false, Color.rgb(255, 205, 133));
        LinearLayout.LayoutParams i = lp(-1, -2);
        i.topMargin = dp(20);
        page.addView(info, i);
        setContentView(scroller);
    }

    private List<Uri> registeredFolders() {
        List<Uri> result = new ArrayList<>();
        String stored = getSharedPreferences(PREFS, MODE_PRIVATE).getString(FOLDERS, "[]");
        try {
            JSONArray array = new JSONArray(stored);
            for (int i = 0; i < array.length(); i++) {
                result.add(Uri.parse(array.getString(i)));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private void storeFolder(Uri tree) {
        List<Uri> roots = registeredFolders();
        for (Uri existing : roots) {
            if (existing.equals(tree)) return;
        }
        roots.add(tree);
        JSONArray array = new JSONArray();
        for (Uri root : roots) array.put(root.toString());
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        preferences.edit().putString(FOLDERS, array.toString()).apply();
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_FOLDER);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_FOLDER || result != RESULT_OK || data == null) return;
        Uri tree = data.getData();
        if (tree == null) return;
        try {
            getContentResolver().takePersistableUriPermission(
                    tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            storeFolder(tree);
            refreshLibrary();
        } catch (SecurityException ex) {
            new AlertDialog.Builder(this)
                    .setTitle("Folder permission")
                    .setMessage("Android did not grant persistent read access. Select the folder again.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void refreshLibrary() {
        status.setText("Scanning selected folders...");
        final List<Uri> folders = registeredFolders();
        worker.execute(() -> {
            List<GameCatalog.Game> games = GameCatalog.scan(this, folders);
            runOnUiThread(() -> displayGames(games, folders.size()));
        });
    }

    private void displayGames(List<GameCatalog.Game> games, int folderCount) {
        gameRow.removeAllViews();
        libraryLabel.setText("LIBRARY  ·  " + games.size());
        if (games.isEmpty()) {
            TextView empty = text("No extracted games found. Select a game folder or a folder containing games with exefs/main and romfs.",
                    17, false, Color.rgb(180, 202, 227));
            empty.setPadding(dp(20), dp(35), dp(20), dp(20));
            gameRow.addView(empty, lp(-1, 200));
        }
        for (GameCatalog.Game game : games) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(13), dp(14), dp(13));
            card.setBackground(background(Color.rgb(24, 52, 89),
                    Color.rgb(14, 28, 51), 15));
            LinearLayout.LayoutParams p = lp(210, 220);
            p.rightMargin = dp(14);
            gameRow.addView(card, p);
            TextView icon = text("B2", 34, true, Color.rgb(132, 199, 255));
            icon.setGravity(Gravity.CENTER);
            card.addView(icon, new LinearLayout.LayoutParams(-1, 0, 1));
            TextView label = text(game.title, 16, true, Color.WHITE);
            label.setMaxLines(2);
            card.addView(label);
            TextView hint = text("Inspect executable", 12, false,
                    Color.rgb(137, 188, 241));
            card.addView(hint);
            card.setOnClickListener(v -> inspectGame(game));
        }
        status.setText(folderCount + " registered folder(s), " + games.size()
                + " extracted game(s) found. Original files are read-only.");
    }

    private void inspectGame(GameCatalog.Game game) {
        worker.execute(() -> {
            String verdict;
            try (ParcelFileDescriptor descriptor =
                         getContentResolver().openFileDescriptor(game.executable, "r")) {
                if (descriptor == null) {
                    verdict = "Android could not open this game executable.";
                } else {
                    int result = NativeCore.inspectExecutable(descriptor.getFd());
                    if (result == 1) {
                        verdict = "NSO0 executable header recognised. This confirms file structure only. Switch 2 execution is not implemented yet.";
                    } else if (result == 0) {
                        verdict = "The executable does not begin with NSO0.";
                    } else {
                        verdict = "Could not read the executable header.";
                    }
                }
            } catch (Exception | LinkageError e) {
                verdict = "Unable to inspect this file: " + e.getClass().getSimpleName();
            }
            final String message = verdict;
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle(game.title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show());
        });
    }

    private void runEngineTest() {
        worker.execute(() -> {
            String result;
            try {
                result = NativeCore.runEngineSelfTest();
            } catch (LinkageError | RuntimeException e) {
                result = "Unable to run the native test: " + e.getClass().getSimpleName();
            }
            final String message = result;
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("BluSwitch 2.0 Native CPU")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show());
        });
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
