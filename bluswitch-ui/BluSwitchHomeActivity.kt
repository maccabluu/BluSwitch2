// SPDX-License-Identifier: GPL-3.0-or-later
// BluSwitch 2 original home screen, integrated with native Eden NX2 emulation.
package org.yuzu.yuzu_emu.ui.main

import android.app.AlertDialog
import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
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
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.activities.EmulationActivity
import org.yuzu.yuzu_emu.features.settings.model.Settings
import org.yuzu.yuzu_emu.model.Game
import org.yuzu.yuzu_emu.model.GameDir
import org.yuzu.yuzu_emu.utils.DirectoryInitialization
import org.yuzu.yuzu_emu.utils.GameHelper
import org.yuzu.yuzu_emu.utils.GameMetadata
import org.yuzu.yuzu_emu.utils.NativeConfig
import org.yuzu.yuzu_emu.utils.ThemeHelper

/** The new single-APK BluSwitch 2 launcher, using the actual native emulator core. */
class BluSwitchHomeActivity : AppCompatActivity() {
    private lateinit var browser: WebView
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var currentGames: List<Game> = emptyList()
    @Volatile private var closing = false

    // Native Android pickers keep file permissions and remain inside BluSwitch 2.
    private val gameFolderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    io.execute {
                        try {
                            val folders = NativeConfig.getGameDirs()
                            if (folders.any { it.uriString == uri.toString() }) {
                                notifyUi("onAction", "This game folder is already in your library.")
                            } else {
                                NativeConfig.addGameDir(GameDir(uri.toString(), true))
                                notifyUi("onAction", "Game folder added to BluSwitch 2.")
                            }
                            refreshStatus()
                            refreshGames()
                        } catch (ex: Exception) {
                            notifyUi("onAction", "Could not add folder: " + (ex.message ?: "Unknown error"))
                        }
                    }
                } catch (ex: Exception) {
                    notifyUi("onAction", "Permission was not granted for this folder.")
                }
            }
        }

    private val prodKeysPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) io.execute { importProdKeys(uri) }
        }

    private fun importProdKeys(uri: android.net.Uri) {
        var pending: File? = null
        try {
            if (!DirectoryInitialization.areDirectoriesReady) {
                notifyUi("onAction", "BluSwitch 2 storage is not ready. Try again.")
                return
            }
            val fileName = contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else ""
            } ?: ""
            if (fileName != "prod.keys") {
                notifyUi("onAction", "Choose the prod.keys file dumped from your own console.")
                return
            }
            val folder = File(DirectoryInitialization.userDirectory, "keys")
            if (!folder.isDirectory && !folder.mkdirs()) {
                notifyUi("onAction", "Could not create the private keys folder.")
                return
            }
            val target = File(folder, "prod.keys")
            pending = File(folder, "bluswitch-keys-import.tmp")
            val input = contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Could not open the selected key file")
            input.use { src ->
                pending.outputStream().use { dest -> src.copyTo(dest) }
            }
            if (pending.length() < 64 || pending.length() > 2_000_000) {
                throw IllegalArgumentException("The selected key file has an unexpected size")
            }
            val backup = File(folder, "bluswitch-keys-backup.tmp")
            if (target.exists()) target.copyTo(backup, overwrite = true)
            try {
                pending.copyTo(target, overwrite = true)
                if (!NativeLibrary.reloadKeys()) {
                    throw IllegalArgumentException("The emulator could not load these keys")
                }
                backup.delete()
                notifyUi("onAction", "Your console keys were imported successfully.")
            } catch (ex: Exception) {
                if (backup.exists()) {
                    backup.copyTo(target, overwrite = true)
                    backup.delete()
                } else {
                    target.delete()
                }
                NativeLibrary.reloadKeys()
                throw ex
            }
        } catch (ex: Exception) {
            notifyUi("onAction", "Key import failed: " + (ex.message ?: "Unknown error"))
        } finally {
            pending?.delete()
            refreshStatus()
            refreshGames()
        }
    }

    private fun refreshStatus() {
        if (closing) return
        if (!DirectoryInitialization.areDirectoriesReady) {
            handler.postDelayed({ refreshStatus() }, 400)
            return
        }
        io.execute {
            try {
                val state = JSONObject()
                state.put("ready", true)
                state.put("keys", NativeLibrary.areKeysPresent())
                state.put("firmware", NativeLibrary.isFirmwareAvailable())
                val dirs = JSONArray()
                NativeConfig.getGameDirs().forEach { dirs.put(it.uriString) }
                state.put("folders", dirs)
                notifyUi("setStatus", state.toString())
            } catch (ex: Exception) {
                notifyUi("onAction", "Could not check setup status.")
            }
        }
    }

    private fun removeGameFolder(index: Int) {
        io.execute {
            try {
                val dirs = NativeConfig.getGameDirs().toMutableList()
                if (index !in dirs.indices) return@execute
                dirs.removeAt(index)
                NativeConfig.setGameDirs(dirs.toTypedArray())
                notifyUi("onAction", "Game folder removed from library. Files were not deleted.")
                refreshStatus()
                refreshGames()
            } catch (ex: Exception) {
                notifyUi("onAction", "Could not remove this folder.")
            }
        }
    }

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

        // BluSwitch 2 now owns first-run setup instead of opening the Eden interface.
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putBoolean(Settings.PREF_FIRST_APP_LAUNCH, false).apply()
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed({ refreshGames() }, 400)
        handler.postDelayed({ refreshStatus() }, 500)
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
        @JavascriptInterface fun pickGameFolder() { handler.post { gameFolderPicker.launch(null) } }
        @JavascriptInterface fun pickProdKeys() { handler.post { prodKeysPicker.launch(arrayOf("*/*")) } }
        @JavascriptInterface fun requestStatus() { handler.post { refreshStatus() } }
        @JavascriptInterface fun removeGameFolder(index: Int) { handler.post { this@BluSwitchHomeActivity.removeGameFolder(index) } }
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
