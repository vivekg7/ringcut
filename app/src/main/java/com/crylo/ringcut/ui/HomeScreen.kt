package com.crylo.ringcut.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crylo.ringcut.R
import com.crylo.ringcut.core.TrimMath
import com.crylo.ringcut.data.Source
import com.crylo.ringcut.data.Tone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TonesViewModel,
    snackbar: SnackbarHostState,
    onOpen: (Source) -> Unit,
    onMessage: (Int) -> Unit,
) {
    val context = LocalContext.current
    val tones by viewModel.tones.collectAsStateWithLifecycle()
    val playingId by viewModel.playingId.collectAsStateWithLifecycle()
    val setDefault = rememberDefaultToneSetter(onMessage)

    // The system document picker instead of READ_MEDIA_AUDIO: the user hands over one
    // file, and the app never gets to see the rest of their music library.
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onOpen(Source.from(context, it, isVideo = false)) }
    }
    // The Photo Picker likewise needs no permission for videos.
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onOpen(Source.from(context, it, isVideo = true)) }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { viewModel.stop() }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect(onMessage) }

    var renaming by remember { mutableStateOf<Tone?>(null) }
    var deleting by remember { mutableStateOf<Tone?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PickCard(R.drawable.ic_music, R.string.pick_audio, R.string.pick_audio_hint) {
                        audioPicker.launch(arrayOf("audio/*"))
                    }
                    PickCard(R.drawable.ic_video, R.string.pick_video, R.string.pick_video_hint) {
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.my_tones),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 4.dp),
                )
            }
            if (tones.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_tones),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(tones, key = { it.id }) { tone ->
                ToneRow(
                    tone = tone,
                    playing = tone.id == playingId,
                    onPlay = { viewModel.togglePlay(tone) },
                    onSetDefault = { setDefault(tone.uri, tone.type) },
                    onRename = { renaming = tone },
                    onDelete = { deleting = tone },
                )
            }
        }
    }

    renaming?.let { tone ->
        var name by remember(tone.id) { mutableStateOf(tone.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.rename(tone, name)
                        renaming = null
                    },
                ) { Text(stringResource(R.string.rename)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deleting?.let { tone ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_title, tone.title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(tone)
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PickCard(@DrawableRes icon: Int, title: Int, hint: Int, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Column(Modifier.padding(start = 16.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToneRow(
    tone: Tone,
    playing: Boolean,
    onPlay: () -> Unit,
    onSetDefault: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val type = stringResource(tone.type.label())
    ListItem(
        headlineContent = { Text(tone.title, maxLines = 1) },
        supportingContent = {
            Text(
                if (tone.durationMs > 0) {
                    stringResource(R.string.tone_details, type, TrimMath.formatTime(tone.durationMs))
                } else {
                    stringResource(R.string.tone_details_no_duration, type)
                },
            )
        },
        leadingContent = {
            IconButton(onClick = onPlay) {
                Icon(
                    painterResource(if (playing) R.drawable.ic_stop else R.drawable.ic_play),
                    contentDescription = stringResource(if (playing) R.string.stop else R.string.play),
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (tone.type.canBeDefault) {
                        DropdownMenuItem(
                            text = { Text(stringResource(tone.type.setDefaultLabel())) },
                            onClick = {
                                menu = false
                                onSetDefault()
                            },
                        )
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = {
                        menu = false
                        onRename()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
                        menu = false
                        onDelete()
                    })
                }
            }
        },
    )
}
