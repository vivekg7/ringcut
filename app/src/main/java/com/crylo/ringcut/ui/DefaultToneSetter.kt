package com.crylo.ringcut.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.crylo.ringcut.R
import com.crylo.ringcut.core.ToneType
import com.crylo.ringcut.data.DefaultTones

/**
 * Returns a function that sets a tone as the system default. If the app lacks "Modify
 * system settings" it first shows why it needs it, then opens the system screen, and
 * finishes the job when the user comes back with the switch on. The pending request is
 * saveable because Android may recreate the activity while the user is in Settings.
 */
@Composable
fun rememberDefaultToneSetter(onMessage: (Int) -> Unit): (Uri, ToneType) -> Unit {
    val context = LocalContext.current
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingType by rememberSaveable { mutableStateOf<String?>(null) }
    var explaining by rememberSaveable { mutableStateOf(false) }

    fun apply(uri: Uri, type: ToneType) {
        onMessage(if (DefaultTones.set(context, uri, type)) type.defaultSetMessage() else R.string.default_failed)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = pendingUri
        val type = pendingType?.let(ToneType::valueOf)
        pendingUri = null
        pendingType = null
        if (uri == null || type == null) return@rememberLauncherForActivityResult
        if (DefaultTones.canWrite(context)) apply(uri, type) else onMessage(R.string.permission_denied)
    }

    if (explaining) {
        AlertDialog(
            onDismissRequest = {
                explaining = false
                pendingUri = null
                pendingType = null
            },
            title = { Text(stringResource(R.string.write_settings_title)) },
            text = { Text(stringResource(R.string.write_settings_message)) },
            confirmButton = {
                TextButton(onClick = {
                    explaining = false
                    launcher.launch(DefaultTones.permissionIntent(context))
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    explaining = false
                    pendingUri = null
                    pendingType = null
                }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    return { uri, type ->
        if (DefaultTones.canWrite(context)) {
            apply(uri, type)
        } else {
            pendingUri = uri
            pendingType = type.name
            explaining = true
        }
    }
}
