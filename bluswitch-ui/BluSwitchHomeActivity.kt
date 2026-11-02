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
import org.yuzu.yuzu_emu.utils.PathUtil
import org.yuzu.yuzu_emu.utils.ThemeHelper

/** The new single-APK BluSwitch 2 launcher, using the actual native emulator core. */
class BluSwitchHomeActivity : AppCompatActivity() {
    private lateinit var browser: WebView
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var currentGames: List<Game> = emptyList()
    @Volatile private var closing = false
    private val settingsActions = BluSettingsActions(this) { showUserFolder() }

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

    // Launch loose Switch 2 ExeFS game directories using the native supported path.
    // This is a real game launch, not an NSP/XCI compatibility claim.
    private val exeFSFolderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) launchExeFSFolder(uri)
        }

    private fun launchExeFSFolder(uri: android.net.Uri) {
        if (android.os.Build.VERSION.SDK_INT >= 30 &&
            !android.os.Environment.isExternalStorageManager()) {
            AlertDialog.Builder(this).setTitle("Storage access needed")
                .setMessage("Switch 2 loose game folders need Android All files access. Enable access for BluSwitch 2, then choose your game folder again.")
                .setPositiveButton("Open access settings") { _, _ ->
                    try {
                        startActivity(Intent(
                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            android.net.Uri.parse("package:" + packageName)
                        ))
                    } catch (_: Exception) {
                        startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                }
                .setNegativeButton("Cancel", null).show()
            return
        }
        val path = PathUtil.getPathFromUri(uri)
        if (path == null) {
            AlertDialog.Builder(this).setTitle("Game folder not found")
                .setMessage("Choose a folder stored locally on your handheld containing exefs/main and romfs. Cloud-only folders are not supported.")
                .setPositiveButton("OK", null).show()
            return
        }
        io.execute {
            try {
                val games = GameHelper.findExeFSGames(File(path))
                runOnUiThread {
                    if (closing) return@runOnUiThread
                    when (games.size) {
                        0 -> AlertDialog.Builder(this).setTitle("No launchable game")
                            .setMessage("No exefs/main file was found in this folder. The native Switch 2 loader needs an extracted game folder containing exefs and romfs.")
                            .setPositiveButton("OK", null).show()
                        1 -> startNativeGame(games.first())
                        else -> AlertDialog.Builder(this)
                            .setTitle("Choose Switch 2 game")
                            .setItems(games.map { it.title }.toTypedArray()) { _, index ->
                                startNativeGame(games[index])
                            }.setNegativeButton("Cancel", null).show()
                    }
                }
            } catch (ex: Exception) {
                notifyUi("onAction", "Cannot read this game folder: " + (ex.message ?: "Invalid folder"))
            }
        }
    }

    private fun startNativeGame(game: Game) {
        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        settings.edit().putLong(game.keyLastPlayedTime, System.currentTimeMillis()).apply()
        // The original upstream Game.launchIntent handles loose ExeFS paths and ordinary files.
        startActivity(game.launchIntent)
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


    // Browse private emulator data inside BluSwitch. Never launch the upstream
    // Android DocumentsUI root, which is still labelled Eden on some builds.
    private fun showUserFolder() {
        if (closing) return
        browser.evaluateJavascript("window.BluSwitch && window.BluSwitch.open('user-files');", null)
        browseUserFolder("")
    }

    private fun resolveUserFile(relative: String): Pair<File, File> {
        val root = File(DirectoryInitialization.userDirectory, ".").canonicalFile
        val target = File(root, relative).canonicalFile
        require(target == root || target.path.startsWith(root.path + File.separator)) {
            "File is outside BluSwitch data"
        }
        return root to target
    }

    private fun browseUserFolder(relative: String) {
        if (!DirectoryInitialization.areDirectoriesReady) {
            notifyUi("setUserFiles", JSONObject().put("error", "Emulator storage is still starting.").toString())
            return
        }
        io.execute {
            val result = JSONObject()
            try {
                val (root, directory) = resolveUserFile(relative)
                require(directory.isDirectory) { "This folder is unavailable" }
                val relativePath = directory.path.removePrefix(root.path)
                    .trimStart(File.separatorChar).replace(File.separatorChar, '/')
                result.put("path", relativePath)
                val entries = JSONArray()
                directory.listFiles()
                    ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                    ?.take(400)
                    ?.forEach { child ->
                        try {
                            val (_, safeChild) = resolveUserFile(
                                child.canonicalPath.removePrefix(root.path)
                                    .trimStart(File.separatorChar)
                            )
                            if (safeChild.isFile || safeChild.isDirectory) {
                                val item = JSONObject()
                                item.put("name", child.name)
                                item.put("path", safeChild.path.removePrefix(root.path)
                                    .trimStart(File.separatorChar).replace(File.separatorChar, '/'))
                                item.put("folder", safeChild.isDirectory)
                                item.put("size", if (safeChild.isFile) safeChild.length() else 0)
                                entries.put(item)
                            }
                        } catch (_: Exception) { /* Skip inaccessible entries */ }
                    }
                result.put("entries", entries)
            } catch (ex: Exception) {
                result.put("error", ex.message ?: "Unable to read BluSwitch data")
            }
            notifyUi("setUserFiles", result.toString())
        }
    }

    private fun previewUserFile(relative: String) {
        if (!DirectoryInitialization.areDirectoriesReady) return
        io.execute {
            val result = JSONObject()
            try {
                val (_, file) = resolveUserFile(relative)
                require(file.isFile) { "This file is unavailable" }
                val safeExtensions = setOf("txt", "log", "ini", "json", "xml", "cfg", "md")
                require(file.extension.lowercase() in safeExtensions) {
                    "Preview is available for text files only"
                }
                require(file.length() <= 131072L) { "File is too large to preview" }
                val content = file.readText(Charsets.UTF_8)
                require(!content.contains('\\u0000')) { "Binary files cannot be previewed" }
                result.put("name", file.name)
                result.put("content", content)
            } catch (ex: Exception) {
                result.put("error", ex.message ?: "Unable to preview this file")
            }
            notifyUi("setUserPreview", result.toString())
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
                    val available = try { file?.exists() == true } catch (_: Exception) { false }
                    item.put("available", available)
                    if (available) {
                        val details = SwitchContainerInspector.inspect(this, fileUri)
                        item.put("format", details.optString("format", "Unknown"))
                        item.put("structure", details.optString("structure", "Not checked"))
                        item.put("contents", details.optString("contents", ""))
                        item.put("note", details.optString("note", ""))
                    }
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
            startNativeGame(game)
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


    // Read the official ForTheUsers Switch homebrew catalogue off the UI thread.
    // Homebrew archives are downloads only: the current NX2 core cannot execute NRO apps.
    @Volatile private var homebrewNames: Set<String> = emptySet()
    private fun fetchHomebrewCatalogue() {
        io.execute {
            val result = JSONObject()
            try {
                val conn = (java.net.URL("https://switch.cdn.fortheusers.org/repo.json")
                    .openConnection() as java.net.HttpURLConnection)
                conn.connectTimeout = 12000
                conn.readTimeout = 12000
                conn.instanceFollowRedirects = true
                try {
                    if (conn.responseCode != 200) throw IllegalStateException("Store HTTP " + conn.responseCode)
                    val json = conn.inputStream.bufferedReader().use { it.readText() }
                    if (json.length > 8_000_000) throw IllegalStateException("Store catalogue is too large")
                    val data = JSONObject(json)
                    val packages = data.getJSONArray("packages")
                    val names = mutableSetOf<String>()
                    for (i in 0 until packages.length()) {
                        val pkg = packages.optJSONObject(i) ?: continue
                        val name = pkg.optString("name")
                        if (name.matches(Regex("[A-Za-z0-9_.-]{1,120}"))) names.add(name)
                    }
                    homebrewNames = names
                    result.put("packages", packages)
                } finally {
                    conn.disconnect()
                }
            } catch (ex: Exception) {
                result.put("error", ex.message ?: "Connection failed")
            }
            notifyUi("setStoreCatalog", result.toString())
        }
    }

    private fun downloadHomebrew(name: String) {
        // Only packages found in the live official catalogue may be downloaded.
        if (name !in homebrewNames || !name.matches(Regex("[A-Za-z0-9_.-]{1,120}"))) {
            notifyUi("onAction", "Refresh the Homebrew Store before downloading.")
            return
        }
        try {
            val url = android.net.Uri.parse("https://switch.cdn.fortheusers.org/zips/" +
                android.net.Uri.encode(name) + ".zip")
            val request = android.app.DownloadManager.Request(url)
                .setTitle(name + " · BluSwitch 2 Homebrew")
                .setDescription("Homebrew package from ForTheUsers")
                .setMimeType("application/zip")
                .setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS, "BluSwitch2-" + name + ".zip")
            val manager = getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
            manager.enqueue(request)
            notifyUi("onAction", "Homebrew ZIP download added to Android Downloads. NRO execution is not yet supported.")
        } catch (ex: Exception) {
            notifyUi("onAction", "Download could not start: " + (ex.message ?: "Unknown error"))
        }
    }

    inner class Bridge {
        @JavascriptInterface fun requestStoreCatalog() { handler.post { fetchHomebrewCatalogue() } }
        @JavascriptInterface fun downloadHomebrew(name: String) { handler.post { downloadHomebrew(name) } }
        @JavascriptInterface fun browseUserFiles(path: String) {
            handler.post { browseUserFolder(path) }
        }
        @JavascriptInterface fun previewUserFile(path: String) {
            handler.post { previewUserFile(path) }
        }
        @JavascriptInterface fun requestGames() { handler.post { refreshGames() } }
        @JavascriptInterface fun launchGame(index: Int) { handler.post { launch(index) } }
        @JavascriptInterface fun favorite(index: Int) { handler.post { toggleFavorite(index) } }
        @JavascriptInterface fun pickGameFolder() { handler.post { gameFolderPicker.launch(null) } }
        @JavascriptInterface fun launchExeFSFolder() {
            handler.post { exeFSFolderPicker.launch(null) }
        }
        @JavascriptInterface fun pickGameFile() {
            handler.post { singleGamePicker.launch(arrayOf("*/*")) }
        }
        @JavascriptInterface fun removeGameFile(index: Int) {
            handler.post { removeImportedGame(index) }
        }
        @JavascriptInterface fun pickProdKeys() { handler.post { prodKeysPicker.launch(arrayOf("*/*")) } }
        @JavascriptInterface fun requestStatus() { handler.post { refreshStatus() } }
        @JavascriptInterface fun openSetting(id: String) {
            handler.post { if (!closing) settingsActions.open(id) }
        }
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
        settingsActions.close()
        io.shutdownNow()
        browser.removeJavascriptInterface("BluNative")
        browser.destroy()
        super.onDestroy()
    }
}
