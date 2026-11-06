// SPDX-License-Identifier: GPL-3.0-or-later
// Optional, user-requested GameTDB cover downloads. Never runs on the boot path.
package org.yuzu.yuzu_emu.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.yuzu.yuzu_emu.model.Game
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.Normalizer

internal class BluCoverArt(context: Context) {
    private val folder = File(context.filesDir, "bluswitch2_covers").apply { mkdirs() }
    private val database = File(folder, "gametdb-switch-en.txt")

    private fun key(game: Game): String {
        val raw = game.title.trim().lowercase()
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return hash.take(16).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun file(game: Game) = File(folder, key(game) + ".jpg")

    fun cached(game: Game): String? {
        val item = file(game)
        if (!item.isFile || item.length() !in 100L..300_000L) return null
        return try {
            "data:image/jpeg;base64," +
                Base64.encodeToString(item.readBytes(), Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }

    private fun normal(title: String): String {
        val simple = Normalizer.normalize(title, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
        return simple.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    private fun fetch(url: String, maxBytes: Int): ByteArray? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5500
            readTimeout = 7500
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "BluSwitch2/1.0 (game cover lookup)")
        }
        return try {
            if (connection.responseCode != 200) return null
            if (connection.contentLengthLong > maxBytes) return null
            connection.inputStream.use { stream ->
                ByteArrayOutputStream().use { output ->
                    val chunk = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = stream.read(chunk)
                        if (count <= 0) break
                        total += count
                        if (total > maxBytes) return null
                        output.write(chunk, 0, count)
                    }
                    output.toByteArray()
                }
            }
        } catch (_: Exception) { null }
        finally { connection.disconnect() }
    }

    private fun index(): Map<String, String> {
        // This is only reached from the user's Get covers action, never game boot.
        // Keep the title index for seven days to avoid unnecessary downloads.
        if (!database.isFile ||
            System.currentTimeMillis() - database.lastModified() > 7L * 24 * 60 * 60 * 1000
        ) {
            fetch("https://www.gametdb.com/switchtdb.txt?LANG=EN", 8_000_000)
                ?.let { bytes ->
                    if (bytes.isNotEmpty()) database.writeBytes(bytes)
                }
        }
        if (!database.isFile) return emptyMap()
        val result = HashMap<String, String>()
        database.forEachLine(Charsets.UTF_8) { line ->
            val split = line.indexOf(" = ")
            if (split < 4 || split > 8) return@forEachLine
            val id = line.substring(0, split).trim()
            val title = normal(line.substring(split + 3))
            if (id.matches(Regex("[A-Z0-9]{5,6}")) &&
                title.isNotEmpty() && title !in result
            ) result[title] = id
        }
        return result
    }

    private fun compressCover(bytes: ByteArray): ByteArray? {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            val scale = minOf(1f, 320f / source.width, 460f / source.height)
            val width = maxOf(1, (source.width * scale).toInt())
            val height = maxOf(1, (source.height * scale).toInt())
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(source, width, height, true)
                         else source
            try {
                ByteArrayOutputStream().use { out ->
                    if (!scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)) null
                    else out.toByteArray().takeIf { it.size in 100..300_000 }
                }
            } finally { if (scaled !== source) scaled.recycle() }
        } finally { source.recycle() }
    }

    // User-selected images work for Switch 2 titles not listed by GameTDB.
    // Imported art is stored in the same permanent app-private cover folder.
    fun import(game: Game, bytes: ByteArray): String? {
        if (bytes.size !in 100..6_000_000) return null
        val compressed = compressCover(bytes) ?: return null
        return try {
            file(game).writeBytes(compressed)
            cached(game)
        } catch (_: Exception) { null }
    }

    fun download(
        games: List<Game>,
        cancelled: () -> Boolean,
        onCover: (Game, String) -> Unit
    ): Int {
        val titles = index()
        if (titles.isEmpty() || cancelled()) return 0
        var downloaded = 0
        for (game in games.take(32)) {
            if (cancelled()) break
            if (cached(game) != null) continue
            val id = titles[normal(game.title)] ?: continue
            var stored = false
            for (region in arrayOf("EN", "US")) {
                if (cancelled() || stored) break
                for (extension in arrayOf("jpg", "png")) {
                    if (cancelled()) break
                    val data = fetch(
                        "https://art.gametdb.com/switch/coverM/$region/$id.$extension",
                        750_000
                    ) ?: continue
                    val cover = compressCover(data) ?: continue
                    try {
                        file(game).writeBytes(cover)
                        cached(game)?.let { onCover(game, it) }
                        downloaded++
                        stored = true
                        break
                    } catch (_: Exception) { /* Artwork is optional. */ }
                }
            }
        }
        return downloaded
    }
}
