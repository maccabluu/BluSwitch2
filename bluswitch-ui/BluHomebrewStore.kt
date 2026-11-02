// SPDX-License-Identifier: GPL-3.0-or-later
// BluSwitch 2 Homebrew Store: browse ForTheUsers Switch homebrew and save ZIP packages.
// Running downloaded NROs is not implied: native core compatibility varies.
package org.yuzu.yuzu_emu.ui.main

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal class BluHomebrewStore(private val context: Context) {
    private val base = "https://switch.cdn.fortheusers.org"
    private val maxCatalogBytes = 12 * 1024 * 1024
    private val maxZipBytes = 128L * 1024 * 1024
    private val namePattern = Regex("[a-zA-Z0-9_.-]{1,100}")

    private val archiveDir: File by lazy {
        File(context.filesDir, "bluswitch_homebrew_packages").also {
            if (!it.isDirectory && !it.mkdirs()) error("Cannot create homebrew download folder")
        }
    }

    private fun validName(id: String): String {
        require(namePattern.matches(id) && id != "." && id != ".." &&
            !id.contains("..")) { "Invalid homebrew package ID" }
        return id
    }

    private fun archive(id: String) = File(archiveDir, "${validName(id)}.zip")
    private fun fetchedText(maxBytes: Int): String {
        val url = URL("$base/repo.json")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 12000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "BluSwitch2/1.0 (Homebrew Store)")
        try {
            require(conn.responseCode == 200) { "Homebrew catalogue unavailable (HTTP ${conn.responseCode})" }
            if (conn.contentLengthLong > maxBytes) error("Homebrew catalogue is too large")
            return conn.inputStream.use { input ->
                val buffer = ByteArray(16384)
                val bytes = java.io.ByteArrayOutputStream()
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    bytes.write(buffer, 0, count)
                    require(bytes.size() <= maxBytes) { "Homebrew catalogue exceeds size limit" }
                }
                bytes.toString(Charsets.UTF_8.name())
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Returns a filtered catalogue of packages, with trusted CDN URLs only. */
    fun catalogue(): JSONObject {
        val source = JSONObject(fetchedText(maxCatalogBytes))
        val packages = source.optJSONArray("packages")
            ?: throw IllegalStateException("Homebrew catalogue has no packages")
        val out = JSONArray()
        for (i in 0 until minOf(packages.length(), 2000)) {
            val pkg = packages.optJSONObject(i) ?: continue
            val name = pkg.optString("name", "")
            if (!namePattern.matches(name) || name.contains("..")) continue
            val obj = JSONObject()
            obj.put("id", name)
            obj.put("title", pkg.optString("title", name).take(100))
            obj.put("author", pkg.optString("author", "").take(80))
            obj.put("version", pkg.optString("version", "").take(40))
            obj.put("category", pkg.optString("category", "misc").lowercase(Locale.ROOT).take(24))
            obj.put("description", pkg.optString("description", "").take(260))
            obj.put("icon", "$base/packages/$name/icon.png")
            obj.put("sizeKb", pkg.optLong("filesize", -1))
            obj.put("downloaded", archive(name).isFile)
            out.put(obj)
        }
        return JSONObject().put("packages", out)
            .put("source", "ForTheUsers Switch Homebrew App Store")
            .put("count", out.length())
    }

    fun downloaded(): JSONObject {
        val ids = JSONArray()
        val files = archiveDir.listFiles() ?: emptyArray()
        for (file in files) {
            if (file.isFile && file.name.endsWith(".zip") && file.length() > 4) {
                ids.put(file.name.removeSuffix(".zip"))
            }
        }
        return JSONObject().put("ids", ids)
    }

    /** Saves the original ZIP untouched, without extracting or launching unknown code. */
    fun download(id: String, progress: (Int) -> Unit = {}): JSONObject {
        val safeId = validName(id)
        val conn = URL("$base/zips/$safeId.zip").openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 20000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", "BluSwitch2/1.0 (Homebrew Store)")
        val destination = archive(safeId)
        val temp = File(archiveDir, "$safeId.download")
        try {
            require(conn.responseCode == 200) { "Homebrew download failed (HTTP ${conn.responseCode})" }
            val declared = conn.contentLengthLong
            require(declared <= maxZipBytes) { "Package exceeds 128 MB limit" }
            var total = 0L
            var lastProgress = -1
            conn.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= maxZipBytes) { "Download exceeded 128 MB limit" }
                        output.write(buffer, 0, count)
                        if (declared > 0) {
                            val percent = ((total * 100L) / declared).toInt().coerceIn(0, 100)
                            if (percent >= lastProgress + 10) {
                                lastProgress = percent
                                progress(percent)
                            }
                        }
                    }
                    output.fd.sync()
                }
            }
            require(total >= 4) { "Empty download" }
            val signature = temp.inputStream().use { ByteArray(4).also { bytes ->
                require(it.read(bytes) == 4)
            } }
            require(signature[0] == 0x50.toByte() &&
                signature[1] == 0x4b.toByte() &&
                signature[2] == 0x03.toByte() &&
                signature[3] == 0x04.toByte()) { "Package is not a valid ZIP download" }
            if (destination.exists()) {
                // Original download remains intact if the replacement fails.
                val backup = File(archiveDir, "$safeId.previous")
                if (backup.exists()) backup.delete()
                require(destination.renameTo(backup)) { "Cannot replace existing download" }
                if (!temp.renameTo(destination)) {
                    backup.renameTo(destination)
                    error("Cannot save new homebrew archive")
                }
                backup.delete()
            } else {
                require(temp.renameTo(destination)) { "Cannot save homebrew package" }
            }
            return JSONObject().put("id", safeId).put("downloaded", true).put("size", total)
                .put("message", "Homebrew ZIP downloaded into BluSwitch 2 storage. Running the app requires native emulator support.")
        } finally {
            temp.delete()
            conn.disconnect()
        }
    }

    fun remove(id: String): JSONObject {
        val safeId = validName(id)
        val file = archive(safeId)
        require(!file.exists() || file.delete()) { "Could not delete homebrew download" }
        return JSONObject().put("id", safeId).put("downloaded", false)
            .put("message", "Downloaded package removed. Existing game files were not changed.")
    }
}
