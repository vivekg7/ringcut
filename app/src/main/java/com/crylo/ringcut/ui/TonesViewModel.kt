package com.crylo.ringcut.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.crylo.ringcut.R
import com.crylo.ringcut.data.Tone
import com.crylo.ringcut.data.ToneStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class TonesViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ToneStore(app)

    private val _tones = MutableStateFlow<List<Tone>>(emptyList())
    val tones: StateFlow<List<Tone>> = _tones.asStateFlow()

    private val _playingId = MutableStateFlow<Long?>(null)
    val playingId: StateFlow<Long?> = _playingId.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _messages.receiveAsFlow()

    private var player: ExoPlayer? = null

    fun refresh() {
        viewModelScope.launch { _tones.value = runCatching { store.list() }.getOrDefault(emptyList()) }
    }

    fun togglePlay(tone: Tone) {
        val p = player ?: createPlayer().also { player = it }
        if (_playingId.value == tone.id) {
            stop()
            return
        }
        p.setMediaItem(MediaItem.fromUri(tone.uri))
        p.prepare()
        p.play()
        _playingId.value = tone.id
    }

    fun stop() {
        player?.stop()
        _playingId.value = null
    }

    fun rename(tone: Tone, title: String) = mutate(R.string.rename_failed) { store.rename(tone, title) }

    fun delete(tone: Tone) {
        if (_playingId.value == tone.id) stop()
        mutate(R.string.delete_failed) { store.delete(tone) }
    }

    private fun mutate(@StringRes failure: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { _messages.send(failure) }
            refresh()
        }
    }

    private fun createPlayer(): ExoPlayer = ExoPlayer.Builder(getApplication())
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .build()
        .apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) _playingId.value = null
                }

                override fun onPlayerError(error: PlaybackException) {
                    _playingId.value = null
                    _messages.trySend(R.string.play_failed)
                }
            })
        }

    override fun onCleared() {
        player?.release()
        player = null
    }
}
