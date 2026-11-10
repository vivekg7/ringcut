package com.crylo.ringcut.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.crylo.ringcut.core.FileNaming
import com.crylo.ringcut.core.ToneType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Tone(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val type: ToneType,
    val folder: String,
)

/**
 * Saved tones live in the shared MediaStore, not in app storage: that is where the system
 * ringtone, notification and alarm pickers look, and the files survive uninstalling the
 * app. Scoped storage lets an app create, read, rename and delete its *own* MediaStore
 * entries without any storage permission, so the "My tones" list is simply a query for
 * entries this package owns.
 */
class ToneStore(private val context: Context) {
    private val resolver = context.contentResolver
    private val collection: Uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    suspend fun save(file: File, title: String, type: ToneType): Tone = withContext(Dispatchers.IO) {
        val name = FileNaming.fileName(title)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.TITLE, name.removeSuffix(FileNaming.EXTENSION))
            put(MediaStore.Audio.Media.MIME_TYPE, FileNaming.MIME_TYPE)
            put(MediaStore.Audio.Media.RELATIVE_PATH, folderOf(type) + "/")
            put(flagColumn(type), 1)
            // Hidden from other apps until the bytes are fully written.
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore refused the insert")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("Could not open $uri")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        Tone(ContentUris.parseId(uri), uri, name.removeSuffix(FileNaming.EXTENSION), 0, type, folderOf(type))
    }

    suspend fun list(): List<Tone> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_NOTIFICATION,
            MediaStore.Audio.Media.IS_ALARM,
        )
        val tones = mutableListOf<Tone>()
        resolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.OWNER_PACKAGE_NAME} = ?",
            arrayOf(context.packageName),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val type = when {
                    c.getInt(3) == 1 -> ToneType.RINGTONE
                    c.getInt(4) == 1 -> ToneType.NOTIFICATION
                    c.getInt(5) == 1 -> ToneType.ALARM
                    else -> ToneType.MUSIC
                }
                tones += Tone(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = c.getString(1).orEmpty().removeSuffix(FileNaming.EXTENSION),
                    durationMs = c.getLong(2),
                    type = type,
                    folder = folderOf(type),
                )
            }
        }
        tones
    }

    /** Renames the file itself, not just its title, so file managers show the new name too. */
    suspend fun rename(tone: Tone, newTitle: String) = withContext(Dispatchers.IO) {
        val name = FileNaming.fileName(newTitle)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.TITLE, name.removeSuffix(FileNaming.EXTENSION))
        }
        resolver.update(tone.uri, values, null, null)
    }

    suspend fun delete(tone: Tone) = withContext(Dispatchers.IO) {
        resolver.delete(tone.uri, null, null)
    }

    companion object {
        fun folderOf(type: ToneType): String = when (type) {
            ToneType.RINGTONE -> Environment.DIRECTORY_RINGTONES
            ToneType.NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
            ToneType.ALARM -> Environment.DIRECTORY_ALARMS
            ToneType.MUSIC -> Environment.DIRECTORY_MUSIC
        }

        private fun flagColumn(type: ToneType): String = when (type) {
            ToneType.RINGTONE -> MediaStore.Audio.Media.IS_RINGTONE
            ToneType.NOTIFICATION -> MediaStore.Audio.Media.IS_NOTIFICATION
            ToneType.ALARM -> MediaStore.Audio.Media.IS_ALARM
            ToneType.MUSIC -> MediaStore.Audio.Media.IS_MUSIC
        }
    }
}
