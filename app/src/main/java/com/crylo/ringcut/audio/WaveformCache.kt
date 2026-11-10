package com.crylo.ringcut.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import com.crylo.ringcut.core.Waveform
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Two-level cache of decoded waveforms: a few in memory (reopening the same file in one
 * session is instant) and the rest on disk in the cache directory (reopening it next week
 * skips the decode). Keyed by URI and file size, so an edited file is decoded afresh.
 * The OS may clear the disk half at any time; that only costs a re-decode.
 */
class WaveformCache(context: Context) {
    private val resolver = context.contentResolver
    private val dir = File(context.cacheDir, "waveforms")
    private val memory = LruCache<String, Waveform>(MEMORY_ENTRIES)

    fun key(uri: Uri): String {
        val size = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
            }
        }.getOrNull() ?: -1L
        val digest = MessageDigest.getInstance("SHA-256").digest("$uri|$size".toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    fun get(key: String): Waveform? {
        memory.get(key)?.let { return it }
        val file = File(dir, "$key.bin")
        if (!file.exists()) return null
        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                check(input.readInt() == VERSION)
                val durationMs = input.readLong()
                val bucketMs = input.readDouble()
                val peaks = FloatArray(input.readInt()) { input.readFloat() }
                Waveform(peaks, bucketMs, durationMs)
            }
        }.onSuccess {
            memory.put(key, it)
            file.setLastModified(System.currentTimeMillis())
        }.onFailure { file.delete() }.getOrNull()
    }

    fun put(key: String, waveform: Waveform) {
        memory.put(key, waveform)
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "$key.tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(VERSION)
                out.writeLong(waveform.durationMs)
                out.writeDouble(waveform.bucketMs)
                out.writeInt(waveform.peaks.size)
                waveform.peaks.forEach { out.writeFloat(it) }
            }
            tmp.renameTo(File(dir, "$key.bin"))
            trim()
        }
    }

    private fun trim() {
        val files = dir.listFiles { f -> f.name.endsWith(".bin") } ?: return
        if (files.size <= DISK_ENTRIES) return
        files.sortedBy { it.lastModified() }.take(files.size - DISK_ENTRIES).forEach { it.delete() }
    }

    private companion object {
        const val VERSION = 1
        const val MEMORY_ENTRIES = 6
        const val DISK_ENTRIES = 50
    }
}
