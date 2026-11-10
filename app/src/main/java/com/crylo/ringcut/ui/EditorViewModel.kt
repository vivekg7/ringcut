package com.crylo.ringcut.ui

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.crylo.ringcut.R
import com.crylo.ringcut.audio.AudioExporter
import com.crylo.ringcut.audio.NoAudioTrackException
import com.crylo.ringcut.audio.WaveformCache
import com.crylo.ringcut.audio.WaveformDecoder
import com.crylo.ringcut.core.FadeEnvelope
import com.crylo.ringcut.core.FileNaming
import com.crylo.ringcut.core.Handle
import com.crylo.ringcut.core.ToneType
import com.crylo.ringcut.core.TrimMath
import com.crylo.ringcut.core.TrimRange
import com.crylo.ringcut.core.Viewport
import com.crylo.ringcut.core.Waveform
import com.crylo.ringcut.data.Source
import com.crylo.ringcut.data.Tone
import com.crylo.ringcut.data.ToneStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface LoadState {
    data class Loading(val progress: Float) : LoadState
    data class Ready(val waveform: Waveform) : LoadState
    data class Failed(@StringRes val message: Int) : LoadState
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float) : ExportState
    data class Done(val tone: Tone, val reEncoded: Boolean) : ExportState
    data object Failed : ExportState
}

data class EditorState(
    val source: Source? = null,
    val load: LoadState = LoadState.Loading(0f),
    val range: TrimRange = TrimRange(0, 0),
    val viewport: Viewport = Viewport(0, 1),
    val playing: Boolean = false,
    val playheadMs: Long? = null,
    val fadeIn: Boolean = false,
    val fadeOut: Boolean = false,
    val fadeMs: Long = 1_500,
    val type: ToneType = ToneType.RINGTONE,
    val title: String = "",
    val export: ExportState = ExportState.Idle,
) {
    val durationMs: Long get() = (load as? LoadState.Ready)?.waveform?.durationMs ?: 0
}

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val decoder = WaveformDecoder(app)
    private val cache = WaveformCache(app)
    private val exporter = AudioExporter(app)
    private val store = ToneStore(app)

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var exportJob: Job? = null
    private var playheadJob: Job? = null
    private var player: ExoPlayer? = null

    fun open(source: Source) {
        close()
        val fallback = if (source.isVideo) getApplication<Application>().getString(R.string.video_title_fallback) else FileNaming.FALLBACK
        _state.value = EditorState(source = source, title = FileNaming.titleFromSource(source.name, fallback))
        loadJob = viewModelScope.launch {
            try {
                val started = SystemClock.elapsedRealtime()
                val waveform = withContext(Dispatchers.IO) {
                    val key = cache.key(source.uri)
                    cache.get(key) ?: decoder.decode(source.uri) { p ->
                        _state.update { s -> if (s.load is LoadState.Loading) s.copy(load = LoadState.Loading(p)) else s }
                    }.also { cache.put(key, it) }
                }
                Log.i(TAG, "Waveform ready in ${SystemClock.elapsedRealtime() - started} ms")
                _state.update {
                    it.copy(
                        load = LoadState.Ready(waveform),
                        range = TrimMath.initialRange(waveform.durationMs),
                        viewport = TrimMath.fullViewport(waveform.durationMs),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoAudioTrackException) {
                _state.update { it.copy(load = LoadState.Failed(R.string.error_no_audio)) }
            } catch (e: Exception) {
                Log.w(TAG, "Could not decode ${source.uri}", e)
                _state.update { it.copy(load = LoadState.Failed(R.string.error_decode)) }
            }
        }
    }

    fun close() {
        stopPreview()
        loadJob?.cancel()
        exportJob?.cancel()
        _state.value = EditorState()
    }

    fun moveHandle(handle: Handle, toMs: Long) = editRange { TrimMath.move(it.range, handle, toMs, it.durationMs) }

    fun nudge(handle: Handle, deltaMs: Long) {
        editRange { TrimMath.nudge(it.range, handle, deltaMs, it.durationMs) }
        _state.update { s ->
            val ms = if (handle == Handle.START) s.range.startMs else s.range.endMs
            s.copy(viewport = TrimMath.reveal(s.viewport, ms, s.durationMs))
        }
    }

    private inline fun editRange(change: (EditorState) -> TrimRange) {
        val s = _state.value
        if (s.load !is LoadState.Ready) return
        val range = change(s)
        if (range == s.range) return
        if (s.playing) stopPreview()
        _state.update { it.copy(range = range) }
    }

    fun zoom(factor: Double) = _state.update { s ->
        val anchor = (s.range.startMs + s.range.endMs) / 2
        val visibleAnchor = if (anchor in s.viewport.startMs..s.viewport.endMs) anchor else (s.viewport.startMs + s.viewport.endMs) / 2
        s.copy(viewport = TrimMath.zoom(s.viewport, factor, visibleAnchor, s.durationMs))
    }

    fun fit() = _state.update { it.copy(viewport = TrimMath.fullViewport(it.durationMs)) }

    fun pan(deltaMs: Long) = _state.update { it.copy(viewport = TrimMath.pan(it.viewport, deltaMs, it.durationMs)) }

    fun setFadeIn(on: Boolean) = _state.update { it.copy(fadeIn = on) }
    fun setFadeOut(on: Boolean) = _state.update { it.copy(fadeOut = on) }
    fun setFadeMs(ms: Long) = _state.update { it.copy(fadeMs = ms) }
    fun setType(type: ToneType) = _state.update { it.copy(type = type) }
    fun setTitle(title: String) = _state.update { it.copy(title = title) }

    fun togglePreview() {
        if (_state.value.playing) stopPreview() else startPreview()
    }

    /**
     * Plays exactly the selection by giving ExoPlayer a clipped MediaItem, so the end is
     * enforced by the player itself rather than by polling. Fades are imitated with the
     * player volume; the export applies them to the samples.
     */
    private fun startPreview() {
        val s = _state.value
        val source = s.source ?: return
        if (s.load !is LoadState.Ready || s.range.lengthMs <= 0) return
        val p = player ?: createPlayer().also { player = it }
        p.setMediaItem(
            MediaItem.Builder()
                .setUri(source.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(s.range.startMs)
                        .setEndPositionMs(s.range.endMs)
                        .build(),
                )
                .build(),
        )
        p.prepare()
        p.play()
        _state.update { it.copy(playing = true, playheadMs = s.range.startMs) }
        val envelope = FadeEnvelope.of(
            s.range.lengthMs,
            if (s.fadeIn) s.fadeMs else 0,
            if (s.fadeOut) s.fadeMs else 0,
            sampleRate = 1000,
        )
        playheadJob?.cancel()
        playheadJob = viewModelScope.launch {
            while (isActive) {
                val position = p.currentPosition
                p.volume = envelope.gainAt(position)
                _state.update { it.copy(playheadMs = s.range.startMs + position) }
                delay(FRAME_MS)
            }
        }
    }

    private fun stopPreview() {
        playheadJob?.cancel()
        playheadJob = null
        player?.stop()
        _state.update { it.copy(playing = false, playheadMs = null) }
    }

    private fun createPlayer(): ExoPlayer = ExoPlayer.Builder(getApplication())
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .build()
        .apply {
            // A video's picture is never shown, so don't spend a decoder on it.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) stopPreview()
                }

                override fun onPlayerError(error: PlaybackException) = stopPreview()
            })
        }

    fun save() {
        val s = _state.value
        val source = s.source ?: return
        if (s.load !is LoadState.Ready || s.export is ExportState.Running) return
        stopPreview()
        _state.update { it.copy(export = ExportState.Running(0f)) }
        exportJob = viewModelScope.launch {
            val dir = File(getApplication<Application>().cacheDir, "export").apply { mkdirs() }
            val out = File(dir, "${UUID.randomUUID()}${FileNaming.EXTENSION}")
            try {
                val outcome = exporter.export(
                    source.uri,
                    s.range,
                    if (s.fadeIn) s.fadeMs else 0,
                    if (s.fadeOut) s.fadeMs else 0,
                    out,
                ) { p -> _state.update { it.copy(export = ExportState.Running(p)) } }
                val tone = store.save(outcome.file, s.title, s.type)
                _state.update { it.copy(export = ExportState.Done(tone, outcome.reEncoded)) }
            } catch (e: CancellationException) {
                _state.update { it.copy(export = ExportState.Idle) }
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Export failed", e)
                _state.update { it.copy(export = ExportState.Failed) }
            } finally {
                out.delete()
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
    }

    fun dismissExport() = _state.update { it.copy(export = ExportState.Idle) }

    override fun onCleared() {
        player?.release()
        player = null
    }

    private companion object {
        const val TAG = "Ringcut"
        const val FRAME_MS = 33L
    }
}
