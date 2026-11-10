package com.crylo.ringcut.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.crylo.ringcut.core.TrimRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [reEncoded] is false when the original audio stream was copied through untouched. */
data class ExportOutcome(val file: File, val reEncoded: Boolean)

/**
 * Cuts the selected range out of the source and writes it as AAC in an MP4 (.m4a)
 * container, dropping any video track.
 *
 * The output MIME type is pinned to AAC: Android has encoders for AAC but not for MP3, so
 * AAC is the one format every device can write. When the source is already AAC and no
 * fade is applied, Transformer copies the compressed audio across instead of decoding
 * and re-encoding it, which is faster and loses nothing. Anything else is transcoded.
 */
@OptIn(UnstableApi::class)
class AudioExporter(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun export(
        source: Uri,
        range: TrimRange,
        fadeInMs: Long,
        fadeOutMs: Long,
        output: File,
        onProgress: (Float) -> Unit,
    ): ExportOutcome = withContext(Dispatchers.Main) {
        output.delete()
        val mediaItem = MediaItem.Builder()
            .setUri(source)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(range.startMs)
                    .setEndPositionMs(range.endMs)
                    .build(),
            )
            .build()
        val processors = if (fadeInMs > 0 || fadeOutMs > 0) {
            listOf(FadeAudioProcessor(range.lengthMs, fadeInMs, fadeOutMs))
        } else {
            emptyList()
        }
        val edited = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setEffects(Effects(processors, emptyList()))
            .build()

        var transformer: Transformer? = null
        val progressJob = launch {
            val holder = ProgressHolder()
            while (true) {
                delay(PROGRESS_INTERVAL_MS)
                if (transformer?.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress / 100f)
                }
            }
        }
        try {
            val result = suspendCancellableCoroutine { cont ->
                val t = Transformer.Builder(context)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            cont.resume(exportResult)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                transformer = t
                // Transformer is single-threaded; cancellation may arrive from elsewhere.
                cont.invokeOnCancellation { mainHandler.post { t.cancel() } }
                t.start(edited, output.absolutePath)
            }
            ExportOutcome(output, reEncoded = result.audioConversionProcess != ExportResult.CONVERSION_PROCESS_TRANSMUXED)
        } catch (e: Throwable) {
            output.delete()
            throw e
        } finally {
            progressJob.cancel()
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 150L
    }
}
