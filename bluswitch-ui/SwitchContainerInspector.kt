// SPDX-License-Identifier: GPL-3.0-or-later
// Preliminary structural probe for locally chosen NSP / XCI containers.
// This never reads encryption keys or claims Switch 2 NCA4 compatibility.
package org.yuzu.yuzu_emu.ui.main

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bounded, read-only inspection of a game container header.
 * NSP (PFS0) and XCI (HEAD) are also used by the earlier Switch.
 * Recognising a container does NOT mean its encrypted contents can be loaded.
 */
object SwitchContainerInspector {
    private const val MAX_ENTRIES = 4096
    private const val MAX_NAMES_BYTES = 2 * 1024 * 1024
    private const val ENTRY_SIZE = 0x18

    fun inspect(context: Context, uri: Uri): JSONObject {
        val result = JSONObject()
        result.put("format", "Unknown")
        result.put("structure", "Not checked")
        result.put("contents", "")
        result.put("playable", false)
        result.put("note", "Container compatibility is not verified.")
        try {
            val filename = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment.orEmpty()
            val extension = filename.substringAfterLast('.', "").lowercase()
            result.put("format", extension.uppercase().ifBlank { "Unknown" })

            if (extension != "nsp" && extension != "xci") {
                result.put("structure", "Other file type")
                result.put("note", "NSP/XCI inspection only. Other formats still use the existing native loader.")
                return result
            }
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { stream ->
                    val channel = stream.channel
                    val fileSize = channel.size()
                    result.put("bytes", fileSize)
                    if (fileSize < 0x200) {
                        result.put("structure", "Truncated or unsupported")
                        result.put("note", "The selected game file is too small to contain a complete NSP/XCI header.")
                        return result
                    }
                    val header = readAt(channel, 0, 0x200)
                    if (extension == "nsp") {
                        if (String(header, 0, 4, Charsets.US_ASCII) != "PFS0") {
                            result.put("structure", "Unrecognised NSP layout")
                            result.put("note", "This NSP does not have the PFS0 header understood by the existing loader.")
                            return result
                        }
                        val meta = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                        val entryCount = meta.getInt(4)
                        val tableSize = meta.getInt(8)
                        if (entryCount !in 0..MAX_ENTRIES || tableSize !in 0..MAX_NAMES_BYTES) {
                            result.put("structure", "Invalid PFS0 table sizes")
                            return result
                        }
                        val dataStart = 0x10L + ENTRY_SIZE * entryCount.toLong() + tableSize.toLong()
                        if (dataStart > fileSize) {
                            result.put("structure", "Incomplete PFS0 directory")
                            return result
                        }
                        val entries = readAt(channel, 0x10L, entryCount * ENTRY_SIZE)
                        val names = readAt(channel, 0x10L + entryCount * ENTRY_SIZE.toLong(), tableSize)
                        var ncas = 0
                        var tickets = 0
                        var badEntry = false
                        for (i in 0 until entryCount) {
                            val entry = ByteBuffer.wrap(entries, i * ENTRY_SIZE, ENTRY_SIZE)
                                .order(ByteOrder.LITTLE_ENDIAN)
                            val offset = entry.long
                            val size = entry.long
                            val nameOffset = entry.int
                            if (offset < 0 || size < 0 || nameOffset !in names.indices ||
                                offset > fileSize - dataStart ||
                                size > fileSize - dataStart - offset
                            ) {
                                badEntry = true
                                break
                            }
                            val end = names.indexOf(0, nameOffset)
                            if (end < nameOffset) {
                                badEntry = true
                                break
                            }
                            val name = String(names, nameOffset, end - nameOffset, Charsets.US_ASCII)
                                .lowercase()
                            if (name.endsWith(".nca")) ncas++
                            if (name.endsWith(".tik")) tickets++
                        }
                        if (badEntry) {
                            result.put("structure", "Invalid PFS0 file records")
                            result.put("note", "The archive has invalid or truncated file records.")
                            return result
                        }
                        result.put("structure", "Recognised PFS0 package")
                        result.put("contents", "$entryCount entries, $ncas NCA files, $tickets tickets")
                        result.put("note", "Package recognised, but Switch 2 NCA4 game loading is not implemented. Original Switch packages may work with your own keys.")
                    } else {
                        val magic = String(header, 0x100, 4, Charsets.US_ASCII)
                        if (magic == "HEAD") {
                            result.put("structure", "Recognised legacy XCI game-card header")
                            result.put("note", "Recognised game-card header. This does not establish Switch 2 game-card support.")
                        } else {
                            result.put("structure", "Unrecognised XCI layout")
                            result.put("note", "This game card does not use the HEAD header expected by the current XCI loader. Switch 2 XCI support needs a new verified parser.")
                        }
                    }
                }
            } ?: result.put("structure", "No file access")
        } catch (ex: Exception) {
            result.put("structure", "Unable to inspect")
            result.put("note", "Could not read this game file: " + (ex.message ?: "Provider error"))
        }
        return result
    }

    private fun readAt(channel: java.nio.channels.FileChannel, position: Long, amount: Int): ByteArray {
        require(amount in 0..(MAX_NAMES_BYTES + MAX_ENTRIES * ENTRY_SIZE))
        val buffer = ByteBuffer.allocate(amount)
        var current = position
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, current)
            if (n <= 0) throw IllegalArgumentException("Unexpected end of archive")
            current += n.toLong()
        }
        return buffer.array()
    }

    private fun ByteArray.indexOf(value: Byte, fromIndex: Int): Int {
        for (i in fromIndex until size) if (this[i] == value) return i
        return -1
    }
}