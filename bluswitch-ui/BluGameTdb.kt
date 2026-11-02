// SPDX-License-Identifier: GPL-3.0-or-later
// GameTDB artwork support for BluSwitch 2. Nothing is bundled or hotlinked in WebView.
package org.yuzu.yuzu_emu.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * GameTDB's Switch database uses its own five-character IDs.
 * Native console program IDs are not interchangeable with those IDs.
 * Title matches must be exact after normalisation; ambiguous matches are rejected.
 */
internal class BluGameTdb(private val context: Context) {
    companion object {
        const val WEBSITE = "https://www.gametdb.com/Switch"
        private const val INDEX = "https://www.gametdb.com/switchtdb.txt?LANG=EN"
        private const val USER_AGENT = "BluSwitch2/1.0 (GameTDB cover support; github.com/maccabluu/BluSwitch2)"
        private const val INDEX_LIMIT = 8 * 1024 * 1024
        private const val IMAGE_LIMIT = 3 * 1024 * 1024
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }

    private val covers: File get() = File(context.filesDir, "bluswitch_gametdb_covers").also {
        if (!it.isDirectory && !it.mkdirs()) error("Unable to create cover folder")
    }

    private fun filename(path: String): String {
        val sha = MessageDigest.getInstance("SHA-256")
            .digest(path.toByteArray(StandardCharsets.UTF_8))
        val prefix = sha.take(20).joinToString("") { "%02x".format(it.toInt() and 255) }
        return "$prefix.jpg"
    }

    fun cachedCover(path: String): File? = File(covers, filename(path)).takeIf {
        it.isFile && it.length() in 1..1_000_000
    }

    fun removeCover(path: String): Boolean {
        val old = File(covers, filename(path))
        return !old.exists() || old.delete()
    }

    private fun fetch(url: String, maxBytes: Int): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 5500
        connection.readTimeout = 9000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "image/jpeg,image/png,text/plain;q=0.8")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP " + connection.responseCode)
            }
            val declared = connection.contentLengthLong
            if (declared > maxBytes) throw IllegalArgumentException("Download exceeds allowed size")
            return connection.inputStream.use { input ->
                val bytes = ByteArrayOutputStream()
                val buf = ByteArray(16384)
                while (true) {
                    val count = input.read(buf)
                    if (count < 0) break
                    bytes.write(buf, 0, count)
                    if (bytes.size() > maxBytes) {
                        throw IllegalArgumentException("Download exceeds allowed size")
                    }
                }
                bytes.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun titles(): String {
        val cache = File(context.cacheDir, "bluswitch_gametdb_switch_index.txt")
        if (cache.isFile && cache.length() in 1..INDEX_LIMIT &&
            System.currentTimeMillis() - cache.lastModified() < WEEK_MS
        ) return cache.readText(Charsets.UTF_8)
        return try {
            val text = String(fetch(INDEX, INDEX_LIMIT), Charsets.UTF_8)
            require(text.lines().any { Regex("^[A-Z0-9]{5}\\s*=").containsMatchIn(it) }) {
                "GameTDB returned an unexpected database format"
            }
            val tmp = File(context.cacheDir, cache.name + ".new")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(cache)) {
                cache.writeText(text, Charsets.UTF_8)
                tmp.delete()
            }
            text
        } catch (e: Exception) {
            if (cache.isFile && cache.length() in 1..INDEX_LIMIT) {
                cache.readText(Charsets.UTF_8)
            } else throw e
        }
    }

    private fun normalised(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[™®©]"), "")
        .replace(Regex("[^\\p{L}\\p{N}]"), "")

    /** Returns only one unambiguous GameTDB ID, never a guessed 16-digit title ID. */
    fun findIdByTitle(title: String): String? {
        val wanted = normalised(title)
        if (wanted.length < 4) return null
        val entry = Regex("^([A-Z0-9]{5})\\s*=\\s*(.+)$")
        val matches = mutableSetOf<String>()
        for (line in titles().lineSequence()) {
            val m = entry.matchEntire(line.trim()) ?: continue
            if (normalised(m.groupValues[2]) == wanted) {
                matches.add(m.groupValues[1])
            }
        }
        return matches.singleOrNull()
    }

    /**
     * Downloads a small front cover only after a button press.
     * EN first, then US, with PNG/JPG support. Never modifies user-chosen artwork.
     */
    fun downloadCover(gamePath: String, identifier: String): File {
        val id = identifier.uppercase(Locale.ROOT)
        require(id.matches(Regex("[A-Z0-9]{5}"))) {
            "GameTDB IDs are five letters or numbers"
        }
        if (cachedCover(gamePath) != null) {
            throw IllegalStateException("A GameTDB cover is already saved. Reset before replacing it.")
        }
        var picture: ByteArray? = null
        for (style in listOf("coverM", "coverHQ")) {
            if (picture != null) break
            for (region in listOf("EN", "US")) {
                if (picture != null) break
                for (extension in listOf("png", "jpg")) {
                    val url = "https://art.gametdb.com/switch/$style/$region/$id.$extension"
                    val bytes = try { fetch(url, IMAGE_LIMIT) } catch (_: Exception) { continue }
                    if (bytes.size >= 128) {
                        val head = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, head)
                        if (head.outWidth in 1..10000 && head.outHeight in 1..10000 &&
                            head.outWidth.toLong() * head.outHeight <= 60_000_000
                        ) {
                            picture = bytes
                            break
                        }
                    }
                }
            }
        }
        val source = picture ?: throw IllegalStateException("No matching front cover was found on GameTDB.")
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, dimensions)
        var sample = 1
        while (dimensions.outWidth / sample > 1500 || dimensions.outHeight / sample > 1500) {
            sample *= 2
        }
        val original = BitmapFactory.decodeByteArray(
            source, 0, source.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw IllegalArgumentException("GameTDB image cannot be decoded")
        try {
            val edge = maxOf(original.width, original.height)
            val resized = if (edge > 900) {
                val scale = 900f / edge
                Bitmap.createScaledBitmap(original,
                    (original.width * scale).toInt().coerceAtLeast(1),
                    (original.height * scale).toInt().coerceAtLeast(1), true)
            } else original
            try {
                val output = File(covers, filename(gamePath))
                val temp = File(covers, output.name + ".new")
                try {
                    temp.outputStream().use {
                        require(resized.compress(Bitmap.CompressFormat.JPEG, 78, it)) {
                            "Cover could not be saved"
                        }
                    }
                    require(temp.length() in 1..1_000_000) { "Cover is too large" }
                    if (!temp.renameTo(output)) {
                        throw IllegalStateException("Could not cache GameTDB cover")
                    }
                } finally {
                    temp.delete()
                }
                return output
            } finally {
                if (resized !== original) resized.recycle()
            }
        } finally {
            original.recycle()
        }
    }
}
