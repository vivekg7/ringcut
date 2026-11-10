package com.crylo.ringcut.data

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import com.crylo.ringcut.core.ToneType

/**
 * Setting the phone's default ringtone, notification sound or alarm writes to system
 * settings, which Android gates behind the special "Modify system settings" access
 * (WRITE_SETTINGS). It can't be requested with a normal dialog: the user has to flip a
 * switch on a system screen, so the app explains why first and then sends them there.
 */
object DefaultTones {
    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())

    /** Returns false if the permission is missing or the system refused the write. */
    fun set(context: Context, uri: Uri, type: ToneType): Boolean {
        val ringtoneType = when (type) {
            ToneType.RINGTONE -> RingtoneManager.TYPE_RINGTONE
            ToneType.NOTIFICATION -> RingtoneManager.TYPE_NOTIFICATION
            ToneType.ALARM -> RingtoneManager.TYPE_ALARM
            ToneType.MUSIC -> return false
        }
        if (!canWrite(context)) return false
        return runCatching { RingtoneManager.setActualDefaultRingtoneUri(context, ringtoneType, uri) }.isSuccess
    }
}
