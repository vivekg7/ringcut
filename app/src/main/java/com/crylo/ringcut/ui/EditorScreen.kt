package com.crylo.ringcut.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crylo.ringcut.R
import com.crylo.ringcut.core.FileNaming
import com.crylo.ringcut.core.Handle
import com.crylo.ringcut.core.ToneType
import com.crylo.ringcut.core.TrimMath
import com.crylo.ringcut.data.ToneStore
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
    onMessage: (Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val setDefault = rememberDefaultToneSetter(onMessage)
    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.source?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val load = state.load) {
                is LoadState.Loading -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.reading_audio, (load.progress * 100).toInt()),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                is LoadState.Failed -> Text(
                    stringResource(load.message),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                is LoadState.Ready -> EditorContent(state, viewModel, load)
            }
        }
    }

    when (val export = state.export) {
        is ExportState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.saving)) },
            text = { LinearProgressIndicator(progress = { export.progress }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = viewModel::cancelExport) { Text(stringResource(R.string.cancel)) } },
        )
        is ExportState.Done -> AlertDialog(
            onDismissRequest = viewModel::dismissExport,
            title = { Text(stringResource(R.string.saved_title)) },
            text = {
                Text(
                    stringResource(R.string.saved_message, export.tone.title, export.tone.folder) + "\n\n" +
                        stringResource(if (export.reEncoded) R.string.saved_reencoded else R.string.saved_copied),
                )
            },
            confirmButton = {
                if (export.tone.type.canBeDefault) {
                    TextButton(onClick = {
                        setDefault(export.tone.uri, export.tone.type)
                        viewModel.dismissExport()
                    }) { Text(stringResource(export.tone.type.setDefaultLabel())) }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissExport()
                    onClose()
                }) { Text(stringResource(R.string.done)) }
            },
        )
        ExportState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismissExport,
            text = { Text(stringResource(R.string.export_failed)) },
            confirmButton = { TextButton(onClick = viewModel::dismissExport) { Text(stringResource(R.string.ok)) } },
        )
        ExportState.Idle -> Unit
    }
}

@Composable
private fun EditorContent(state: EditorState, viewModel: EditorViewModel, load: LoadState.Ready) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WaveformView(
            waveform = load.waveform,
            range = state.range,
            viewport = state.viewport,
            playheadMs = state.playheadMs,
            onMoveHandle = viewModel::moveHandle,
            onPan = viewModel::pan,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.zoom(0.5) }) {
                Icon(painterResource(R.drawable.ic_zoom_out), contentDescription = stringResource(R.string.zoom_out))
            }
            IconButton(onClick = viewModel::fit) {
                Icon(painterResource(R.drawable.ic_fit), contentDescription = stringResource(R.string.zoom_fit))
            }
            IconButton(onClick = { viewModel.zoom(2.0) }) {
                Icon(painterResource(R.drawable.ic_zoom_in), contentDescription = stringResource(R.string.zoom_in))
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.length_label, TrimMath.formatTime(state.range.lengthMs)),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HandleControls(R.string.start_label, state.range.startMs, Modifier.weight(1f)) { viewModel.nudge(Handle.START, it) }
            HandleControls(R.string.end_label, state.range.endMs, Modifier.weight(1f)) { viewModel.nudge(Handle.END, it) }
        }

        FilledTonalButton(onClick = viewModel::togglePreview, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(if (state.playing) R.drawable.ic_stop else R.drawable.ic_play), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (state.playing) R.string.stop_preview else R.string.preview))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.fadeIn,
                onClick = { viewModel.setFadeIn(!state.fadeIn) },
                label = { Text(stringResource(R.string.fade_in)) },
            )
            FilterChip(
                selected = state.fadeOut,
                onClick = { viewModel.setFadeOut(!state.fadeOut) },
                label = { Text(stringResource(R.string.fade_out)) },
            )
        }
        if (state.fadeIn || state.fadeOut) {
            Column {
                Text(stringResource(R.string.fade_length, "%.1f".format(state.fadeMs / 1000f)))
                Slider(
                    value = state.fadeMs / 1000f,
                    onValueChange = { viewModel.setFadeMs((it * 2).roundToLong() * 500) },
                    valueRange = 0.5f..5f,
                    steps = 8,
                )
            }
        }

        Text(stringResource(R.string.save_as), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ToneType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = state.type == type,
                    onClick = { viewModel.setType(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, ToneType.entries.size),
                    icon = {},
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(
                        stringResource(type.label()),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::setTitle,
            label = { Text(stringResource(R.string.name_label)) },
            suffix = { Text(FileNaming.EXTENSION) },
            singleLine = true,
            supportingText = { Text(ToneStore.folderOf(state.type) + "/") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.format_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = viewModel::save,
            enabled = state.title.isNotBlank() && state.export !is ExportState.Running,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.save)) }
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun HandleControls(label: Int, ms: Long, modifier: Modifier, onNudge: (Long) -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(TrimMath.formatTime(ms), style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { onNudge(-TrimMath.NUDGE_MS) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text(stringResource(R.string.nudge_back))
            }
            OutlinedButton(onClick = { onNudge(TrimMath.NUDGE_MS) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text(stringResource(R.string.nudge_forward))
            }
        }
    }
}
