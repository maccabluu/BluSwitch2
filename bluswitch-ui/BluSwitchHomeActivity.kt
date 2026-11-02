// SPDX-License-Identifier: GPL-3.0-or-later
// BluSwitch 2 original home screen, integrated with native Eden NX2 emulation.
package org.yuzu.yuzu_emu.ui.main

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.activities.EmulationActivity
import org.yuzu.yuzu_emu.features.settings.model.Settings
import org.yuzu.yuzu_emu.model.Game
import org.yuzu.yuzu_emu.utils.DirectoryInitialization
import org.yuzu.yuzu_emu.utils.GameHelper
import org.yuzu.yuzu_emu.utils.GameMetadata
import org.yuzu.yuzu_emu.utils.ThemeHelper

/** The new single-APK BluSwitch 2 launcher, using the actual native emulator core. */
class BluSwitchHomeActivity : AppCompatActivity() {
    private lateinit var browser: WebView
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var currentGames: List<Game> = emptyList()
    @Volatile private var closing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.setTheme(this)
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        browser = WebView(this)
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = false
        browser.settings.allowContentAccess = false
        browser.settings.allowFileAccessFromFileURLs = false
        browser.settings.allowUniversalAccessFromFileURLs = false
        browser.settings.mediaPlaybackRequiresUserGesture = true
        browser.webChromeClient = WebChromeClient()
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return request?.url?.scheme != "file"
            }
        }
        browser.addJavascriptInterface(Bridge(), "BluNative")
        setContentView(browser)
        browser.loadUrl("file:///android_asset/bluswitch/index.html")

        val firstLaunch = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(Settings.PREF_FIRST_APP_LAUNCH, true)
        if (firstLaunch) handler.postDelayed({ openAdvanced() }, 350)
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed({ refreshGames() }, 400)
    }

    private fun notifyUi(method: String, payload: String) {
        if (closing) return
        runOnUiThread {
            if (!closing) browser.evaluateJavascript(
                "window.BluSwitch && window.BluSwitch." + method + "(" +
                    JSONObject.quote(payload) + ");", null
            )
        }
    }

    private fun refreshGames() {
        if (closing) return
        if (!DirectoryInitialization.areDirectoriesReady) {
            handler.postDelayed({ refreshGames() }, 300)
            return
        }
        io.execute {
            try {
                val found = GameHelper.getGames()
                currentGames = found
                val favorites = getSharedPreferences("bluswitch_home", MODE_PRIVATE)
                    .getStringSet("favorites", emptySet()) ?: emptySet()
                val results = JSONArray()
                found.forEachIndexed { position, game ->
                    val item = JSONObject()
                    item.put("index", position)
                    item.put("title", game.title)
                    item.put("favorite", favorites.contains(game.path))
                    if (position < 32) {
                        try {
                            val icon = GameMetadata.getIcon(game.path)
                            if (icon.isNotEmpty() && icon.size < 350000) {
                                item.put("art", "data:image/png;base64," +
                                    Base64.encodeToString(icon, Base64.NO_WRAP))
                            }
                        } catch (_: Exception) { }
                    }
                    results.put(item)
                }
                notifyUi("setGames", results.toString())
            } catch (ex: Exception) {
                notifyUi("showError", ex.message ?: "Unable to scan games")
            }
        }
    }

    private fun openAdvanced() {
        if (!closing) startActivity(Intent(this, MainActivity::class.java))
    }

    private fun launch(index: Int) {
        val game = currentGames.getOrNull(index) ?: return
        val uri = android.net.Uri.parse(game.path)
        if (DocumentFile.fromSingleUri(this, uri)?.exists() != true) {
            AlertDialog.Builder(this)
                .setMessage("This game file is no longer accessible. Check your game folder.")
                .setPositiveButton("OK", null).show()
            refreshGames()
            return
        }
        val run = {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            prefs.edit().putLong(game.keyLastPlayedTime, System.currentTimeMillis()).apply()
            val intent = Intent(this, EmulationActivity::class.java)
            intent.putExtra("game", game)
            intent.putExtra("custom", true)
            startActivity(intent)
        }
        if (NativeLibrary.gameRequiresFirmware(game.programId) &&
            !NativeLibrary.isFirmwareAvailable()) {
            AlertDialog.Builder(this).setTitle("Firmware required")
                .setMessage("This title might require system firmware. Continue anyway?")
                .setPositiveButton("Continue") { _, _ -> run() }
                .setNegativeButton("Cancel", null).show()
        } else run()
    }

    private fun toggleFavorite(index: Int) {
        val game = currentGames.getOrNull(index) ?: return
        val prefs = getSharedPreferences("bluswitch_home", MODE_PRIVATE)
        val favorites = (prefs.getStringSet("favorites", emptySet()) ?: emptySet()).toMutableSet()
        if (!favorites.add(game.path)) favorites.remove(game.path)
        prefs.edit().putStringSet("favorites", favorites).apply()
        refreshGames()
    }

    inner class Bridge {
        @JavascriptInterface fun requestGames() { handler.post { refreshGames() } }
        @JavascriptInterface fun launchGame(index: Int) { handler.post { launch(index) } }
        @JavascriptInterface fun favorite(index: Int) { handler.post { toggleFavorite(index) } }
        @JavascriptInterface fun openAdvanced() { handler.post { this@BluSwitchHomeActivity.openAdvanced() } }
        @JavascriptInterface fun exit() { handler.post { finishAffinity() } }
        @JavascriptInterface fun showInfo(title: String, message: String) {
            handler.post {
                AlertDialog.Builder(this@BluSwitchHomeActivity)
                    .setTitle(title.take(80)).setMessage(message.take(500))
                    .setPositiveButton("OK", null).show()
            }
        }
    }

    override fun onDestroy() {
        closing = true
        io.shutdownNow()
        browser.removeJavascriptInterface("BluNative")
        browser.destroy()
        super.onDestroy()
    }
}
