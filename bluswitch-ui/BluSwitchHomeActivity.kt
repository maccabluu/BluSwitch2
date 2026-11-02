// SPDX-License-Identifier: GPL-3.0-or-later
// BluSwitch 2 original home screen, integrated with native Eden NX2 emulation.
package org.yuzu.yuzu_emu.ui.main

import android.app.AlertDialog
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.FileOutputStream
import java.security.MessageDigest
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
import androidx.activity.OnBackPressedCallback
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


    // GameTDB (https://www.gametdb.com/Switch/Downloads) artwork is optional.
    // The site lists Switch artwork, but not every Switch 2 title is represented.
    // Users choose their downloaded cover from Android's picker. No copyrighted
    // cover images are bundled into the emulator.
    private var pendingCoverGame: String? = null
    private val coverPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val path = pendingCoverGame
            pendingCoverGame = null
            if (path != null && uri != null) {
                io.execute {
                    try {
                        saveCover(path, uri)
                        notifyUi("onAction", "Your game cover has been saved.")
                        refreshGames()
                    } catch (e: Exception) {
                        notifyUi("onAction", "Unable to import cover: " + (e.message ?: "Invalid image"))
                    }
                }
            }
        }

    private fun coverFile(gamePath: String): File {
        val key = MessageDigest.getInstance("SHA-256")
            .digest(gamePath.toByteArray(Charsets.UTF_8))
            .take(16).joinToString("") { "%02x".format(it.toInt() and 255) }
        val folder = File(filesDir, "bluswitch_game_covers")
        if (!folder.isDirectory && !folder.mkdirs()) {
            throw IllegalStateException("Unable to make the game cover folder")
        }
        return File(folder, "$key.jpg")
    }

    private fun saveCover(gamePath: String, uri: android.net.Uri) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open the selected image")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth in 1..40000 && bounds.outHeight in 1..40000) {
            "Select a valid game cover image"
        }
        var sample = 1
        while (bounds.outWidth / sample > 1800 || bounds.outHeight / sample > 1800) {
            sample *= 2
        }
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it, null, BitmapFactory.Options().apply { inSampleSize = sample }
            )
        } ?: throw IllegalArgumentException("Could not read this picture")
        var scaled: Bitmap = bitmap
        try {
            val maxEdge = maxOf(bitmap.width, bitmap.height)
            if (maxEdge > 900) {
                val factor = 900.0 / maxEdge
                scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * factor).toInt().coerceAtLeast(1),
                    (bitmap.height * factor).toInt().coerceAtLeast(1),
                    true
                )
            }
            val target = coverFile(gamePath)
            val temporary = File(target.parentFile, target.name + ".new")
            val previous = File(target.parentFile, target.name + ".old")
            try {
                FileOutputStream(temporary).use { stream ->
                    require(scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)) {
                        "Cannot compress selected cover"
                    }
                    stream.fd.sync()
                }
                require(temporary.length() in 1..1000000) { "Cover file is too large" }
                if (previous.exists()) previous.delete()
                if (target.exists() && !target.renameTo(previous)) {
                    throw IllegalStateException("Could not replace previous cover")
                }
                if (!temporary.renameTo(target)) {
                    if (previous.exists()) previous.renameTo(target)
                    throw IllegalStateException("Could not save game cover")
                }
                previous.delete()
            } finally {
                temporary.delete()
            }
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun openGameTdb(index: Int) {
        val game = currentGames.getOrNull(index) ?: return
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("BluSwitch game title", game.title))
            val page = android.net.Uri.parse("https://www.gametdb.com/Switch/Search")
            startActivity(Intent(Intent.ACTION_VIEW, page))
            android.widget.Toast.makeText(this,
                "Game title copied. Search on GameTDB, download a cover, then use Cover.",
                android.widget.Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            android.widget.Toast.makeText(this,
                "Could not open GameTDB. Check your browser.",
                android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun chooseCover(index: Int) {
        pendingCoverGame = currentGames.getOrNull(index)?.path ?: return
        coverPicker.launch("image/*")
    }

    private fun resetCover(index: Int) {
        val path = currentGames.getOrNull(index)?.path ?: return
        io.execute {
            try {
                val cover = coverFile(path)
                require(!cover.exists() || cover.delete()) { "Cannot delete cover image" }
                refreshGames()
                notifyUi("onAction", "Your original game artwork has been restored.")
            } catch (e: Exception) {
                notifyUi("onAction", "Could not reset game cover.")
            }
        }
    }

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
                                NativeConfig.saveGlobalConfig() // Persist across EmulationActivity.reloadGlobalConfig()
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

    // Persist single-file imports separately from upstream scanned game folders.
    // SharedPreferences and SAF grants survive closing a game and normal APK upgrades.
    private val singleGamePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    val files = getSharedPreferences("bluswitch_imported_games", MODE_PRIVATE)
                    val saved = (files.getStringSet("uris", emptySet()) ?: emptySet()).toMutableSet()
                    saved.add(uri.toString())
                    files.edit().putStringSet("uris", saved).apply()
                    notifyUi("onAction", "Game file saved in your BluSwitch 2 library.")
                } catch (ex: Exception) {
                    notifyUi("onAction", "Could not save game file access: " + (ex.message ?: "Permission denied"))
                }
            }
        }

    private fun importedGameUris(): List<String> =
        (getSharedPreferences("bluswitch_imported_games", MODE_PRIVATE)
            .getStringSet("uris", emptySet()) ?: emptySet()).sorted()

    private fun removeImportedGame(index: Int) {
        val files = importedGameUris()
        if (index !in files.indices) return
        val old = files[index]
        val remaining = files.toMutableSet()
        remaining.remove(old)
        getSharedPreferences("bluswitch_imported_games", MODE_PRIVATE)
            .edit().putStringSet("uris", remaining).apply()
        notifyUi("onAction", "Game file removed from the BluSwitch library, not from your device.")
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
            val incoming = File(folder, "bluswitch-keys-import.tmp")
            pending = incoming
            val input = contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Could not open the selected key file")
            input.use { src ->
                incoming.outputStream().use { dest -> src.copyTo(dest) }
            }
            if (incoming.length() < 64 || incoming.length() > 2_000_000) {
                throw IllegalArgumentException("The selected key file has an unexpected size")
            }
            val backup = File(folder, "bluswitch-keys-backup.tmp")
            if (target.exists()) target.copyTo(backup, overwrite = true)
            try {
                incoming.copyTo(target, overwrite = true)
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
                state.put("allFiles", android.os.Environment.isExternalStorageManager())
                val dirs = JSONArray()
                NativeConfig.getGameDirs().forEach { dirs.put(it.uriString) }
                state.put("folders", dirs)
                val imported = JSONArray()
                importedGameUris().forEach { path ->
                    val fileUri = android.net.Uri.parse(path)
                    val file = DocumentFile.fromSingleUri(this, fileUri)
                    val item = JSONObject()
                    item.put("name", try { file?.name ?: fileUri.lastPathSegment ?: "Game file" }
                        catch (_: Exception) { "Game file" })
                    item.put("available", try { file?.exists() == true } catch (_: Exception) { false })
                    imported.put(item)
                }
                state.put("files", imported)
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
                NativeConfig.saveGlobalConfig()
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
        // Always return to the BluSwitch 2 screen, never the upstream Eden home.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!closing) browser.evaluateJavascript(
                    "window.BluSwitch && window.BluSwitch.back();", null
                )
            }
        })

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
                val found = GameHelper.getGames().toMutableList()
                // SAF file imports are not part of GameHelper's folder scanner.
                // Re-add valid selections from persistent storage after every native scan.
                importedGameUris().forEach { path ->
                    try {
                        if (found.none { it.path == path }) {
                            val uri = android.net.Uri.parse(path)
                            if (DocumentFile.fromSingleUri(this, uri)?.exists() == true) {
                                val selected = GameHelper.getGame(uri, true)
                                if (selected != null) found.add(selected)
                            }
                        }
                    } catch (_: Exception) { /* Retain saved selection for later retry */ }
                }
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
                            val custom = coverFile(game.path)
                            if (custom.isFile && custom.length() in 1..1000000) {
                                item.put("art", "data:image/jpeg;base64," +
                                    Base64.encodeToString(custom.readBytes(), Base64.NO_WRAP))
                                item.put("customCover", true)
                            } else {
                                val icon = GameMetadata.getIcon(game.path)
                                if (icon.isNotEmpty() && icon.size < 350000) {
                                    item.put("art", "data:image/png;base64," +
                                        Base64.encodeToString(icon, Base64.NO_WRAP))
                                }
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
        val accessible = try {
            when (uri.scheme) {
                "content" -> DocumentFile.fromSingleUri(this, uri)?.exists() == true
                "file" -> uri.path?.let { File(it).exists() } ?: false
                null -> File(game.path).exists()
                else -> true // Native engine-specific game paths are checked by the engine.
            }
        } catch (_: Exception) { false }
        if (!accessible) {
            AlertDialog.Builder(this)
                .setMessage("This game file is no longer accessible. Check your game folder.")
                .setPositiveButton("OK", null).show()
            refreshGames()
            return
        }
        val run = {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            prefs.edit().putLong(game.keyLastPlayedTime, System.currentTimeMillis()).apply()
            // Native core expects EXTRA_SELECTED_GAME ("SelectedGame"), not "game".
            // Use the upstream launcher so the actual game reaches EmulationFragment.
            EmulationActivity.launch(this, game)
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
        @JavascriptInterface fun openGameTdb(index: Int) {
            handler.post { this@BluSwitchHomeActivity.openGameTdb(index) }
        }
        @JavascriptInterface fun pickCover(index: Int) {
            handler.post { this@BluSwitchHomeActivity.chooseCover(index) }
        }
        @JavascriptInterface fun resetCover(index: Int) {
            handler.post { this@BluSwitchHomeActivity.resetCover(index) }
        }
        @JavascriptInterface fun pickGameFolder() { handler.post { gameFolderPicker.launch(null) } }
        @JavascriptInterface fun pickGameFile() {
            handler.post { singleGamePicker.launch(arrayOf("*/*")) }
        }
        @JavascriptInterface fun removeGameFile(index: Int) {
            handler.post { removeImportedGame(index) }
        }
        @JavascriptInterface fun pickProdKeys() { handler.post { prodKeysPicker.launch(arrayOf("*/*")) } }
        @JavascriptInterface fun requestStatus() { handler.post { refreshStatus() } }
        @JavascriptInterface fun requestStorageAccess() {
            handler.post {
                if (android.os.Build.VERSION.SDK_INT >= 30 &&
                    !android.os.Environment.isExternalStorageManager()
                ) {
                    try {
                        startActivity(Intent(
                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            android.net.Uri.parse("package:" + packageName)
                        ))
                    } catch (_: Exception) {
                        startActivity(Intent(
                            android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                        ))
                    }
                } else {
                    refreshStatus()
                }
            }
        }
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
