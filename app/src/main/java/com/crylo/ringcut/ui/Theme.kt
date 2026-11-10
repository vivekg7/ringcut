package com.crylo.ringcut.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.crylo.ringcut.R
import com.crylo.ringcut.core.ToneType

/** Material You colours from the wallpaper; minSdk 31 means they are always available. */
@Composable
fun RingcutTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme, content = content)
}

@StringRes
fun ToneType.label(): Int = when (this) {
    ToneType.RINGTONE -> R.string.type_ringtone
    ToneType.NOTIFICATION -> R.string.type_notification
    ToneType.ALARM -> R.string.type_alarm
    ToneType.MUSIC -> R.string.type_music
}

@StringRes
fun ToneType.setDefaultLabel(): Int = when (this) {
    ToneType.RINGTONE -> R.string.set_default_ringtone
    ToneType.NOTIFICATION -> R.string.set_default_notification
    ToneType.ALARM -> R.string.set_default_alarm
    ToneType.MUSIC -> error("Music has no default slot")
}

@StringRes
fun ToneType.defaultSetMessage(): Int = when (this) {
    ToneType.RINGTONE -> R.string.default_set_ringtone
    ToneType.NOTIFICATION -> R.string.default_set_notification
    ToneType.ALARM -> R.string.default_set_alarm
    ToneType.MUSIC -> error("Music has no default slot")
}
