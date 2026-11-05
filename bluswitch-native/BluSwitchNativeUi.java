// SPDX-License-Identifier: GPL-3.0-or-later
// BluSwitch2 first-party WebView frontend for the Banana Native/nxnative Android host.
// The game engine, packer, settings and saves stay native. No second launcher app.
package dev.nxnative.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.view.ViewGroup;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class BluSwitchNativeUi {
    private static WebView browser;
    private static MainActivity owner;

    private BluSwitchNativeUi() {}

    static void show(MainActivity activity, FrameLayout frame) {
        if (browser != null) {
            browser.removeJavascriptInterface("BluNative");
            browser.destroy();
        }
        owner = activity;
        browser = new WebView(activity);
        browser.setBackgroundColor(android.graphics.Color.WHITE);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                return !("file".equals(url.getScheme()) && "android_asset".equals(url.getHost()));
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (view != browser) return;
                sendGames(activity);
                sendStatus(activity);
            }
        });
        browser.addJavascriptInterface(new Bridge(activity), "BluNative");
        frame.addView(browser, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        browser.loadUrl("file:///android_asset/bluswitch/index.html");
    }

    static boolean back(MainActivity activity) {
        if (activity != owner || browser == null) return false;
        browser.evaluateJavascript("window.BluSwitch && window.BluSwitch.back()", null);
        return true;
    }

    static void sendGames(MainActivity activity) {
        if (owner != activity || browser == null) return;
        new Thread(() -> {
            JSONArray list = new JSONArray();
            Set<String> favorites = activity.getSharedPreferences("bluswitch_favorites", Activity.MODE_PRIVATE)
                    .getStringSet("paths", java.util.Collections.emptySet());
            List<Game> games = Game.load(activity);
            for (int index = 0; index < games.size(); index++) {
                Game game = games.get(index);
                JSONObject item = new JSONObject();
                try {
                    item.put("index", index);
                    item.put("title", game.name);
                    item.put("titleId", game.titleId);
                    item.put("path", game.gameDir);
                    item.put("favorite", favorites != null && favorites.contains(game.gameDir));
                    item.put("supported", Profile.of(game.titleId) != null);
                    item.put("ready", Game.ready(game));
                } catch (org.json.JSONException ignored) {}
                list.put(item);
            }
            deliver(activity, "setGames", list.toString());
        }, "bluswitch-library").start();
    }

    static void sendStatus(MainActivity activity) {
        if (owner != activity || browser == null) return;
        JSONObject result = new JSONObject();
        JSONArray folders = new JSONArray();
        JSONArray files = new JSONArray();
        for (Game g : Game.load(activity)) {
            if (Nup.isNup(new File(g.gameDir))) {
                JSONObject f = new JSONObject();
                try {
                    f.put("name", new File(g.gameDir).getName());
                    f.put("available", new File(g.gameDir).isFile());
                    f.put("structure", "NUP package for " + g.name);
                    f.put("contents", g.titleId);
                } catch (org.json.JSONException ignored) {}
                files.put(f);
            } else folders.put(g.gameDir);
        }
        try {
            result.put("folders", folders);
            result.put("files", files);
            result.put("ready", true);
            result.put("allFiles", Environment.isExternalStorageManager());
        } catch (org.json.JSONException ignored) {}
        deliver(activity, "setStatus", result.toString());
    }

    private static void deliver(MainActivity activity, String method, String json) {
        activity.runOnUiThread(() -> {
            if (activity != owner || browser == null) return;
            browser.evaluateJavascript("window.BluSwitch && window.BluSwitch." + method +
                    "(" + JSONObject.quote(json) + ")", null);
        });
    }

    private static Game game(MainActivity activity, int index) {
        List<Game> all = Game.load(activity);
        return index >= 0 && index < all.size() ? all.get(index) : null;
    }

    private static Game selected(MainActivity activity, int index, boolean nup) {
        List<Game> matches = new ArrayList<>();
        for (Game g : Game.load(activity)) {
            if (Nup.isNup(new File(g.gameDir)) == nup) matches.add(g);
        }
        return index >= 0 && index < matches.size() ? matches.get(index) : null;
    }

    private static void toggleFavorite(MainActivity activity, int index) {
        Game g = game(activity, index);
        if (g == null) return;
        SharedPreferences p = activity.getSharedPreferences("bluswitch_favorites", Activity.MODE_PRIVATE);
        Set<String> entries = new HashSet<>(p.getStringSet("paths", java.util.Collections.emptySet()));
        if (!entries.add(g.gameDir)) entries.remove(g.gameDir);
        p.edit().putStringSet("paths", entries).apply();
        sendGames(activity);
    }

    private static void update(MainActivity activity) {
        sendGames(activity);
        sendStatus(activity);
    }

    static final class Bridge {
        private final MainActivity activity;
        Bridge(MainActivity activity) { this.activity = activity; }
        private void ui(Runnable action) { activity.runOnUiThread(action); }

        @JavascriptInterface public void requestGames() { update(activity); }
        @JavascriptInterface public void requestStatus() { sendStatus(activity); }
        @JavascriptInterface public void launchGame(int index) {
            ui(() -> { Game g = game(activity, index); if (g != null) activity.launch(g); });
        }
        @JavascriptInterface public void diagnoseGame(int index) {
            ui(() -> { Game g = game(activity, index); if (g != null) activity.bluManage(g); });
        }
        @JavascriptInterface public void favorite(int index) { ui(() -> toggleFavorite(activity, index)); }
        @JavascriptInterface public void pickGameFolder() { ui(activity::addGame); }
        @JavascriptInterface public void launchExeFSFolder() { ui(activity::addGame); }
        @JavascriptInterface public void pickGameFile() { ui(activity::bluPickNup); }
        @JavascriptInterface public void removeGameFile(int index) {
            ui(() -> { Game g = selected(activity, index, true); if (g != null) activity.bluRemove(g); });
        }
        @JavascriptInterface public void removeGameFolder(int index) {
            ui(() -> { Game g = selected(activity, index, false); if (g != null) activity.bluRemove(g); });
        }
        @JavascriptInterface public void openSetting(String id) { ui(() -> activity.bluOpenSetting(id)); }
        @JavascriptInterface public void requestStorageAccess() { ui(activity::bluStorageAccess); }
        @JavascriptInterface public void saveBackup(int index) {
            ui(() -> { Game g = game(activity, index); if (g != null) SaveUi.backup(activity, g); });
        }
        @JavascriptInterface public void saveRestore(int index) {
            ui(() -> { Game g = game(activity, index); if (g != null) SaveUi.restoreList(activity, g); });
        }
        @JavascriptInterface public void exit() { ui(activity::finish); }
    }
}
