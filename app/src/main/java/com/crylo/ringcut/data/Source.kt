package com.crylo.ringcut.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

/** A file the user picked or shared, to be cut. */
data class Source(val uri: Uri, val name: String, val isVideo: Boolean) {
    companion object {
        fun from(context: Context, uri: Uri, isVideo: Boolean): Source {
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.onFailure { Log.w("Ringcut", "No display name for $uri", it) }.getOrNull()
            return Source(uri, name ?: uri.lastPathSegment.orEmpty(), isVideo)
        }
    }
}
