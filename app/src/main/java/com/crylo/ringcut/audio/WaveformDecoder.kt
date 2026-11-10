package com.crylo.ringcut.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.crylo.ringcut.core.PeakAccumulator
import com.crylo.ringcut.core.Waveform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

class NoAudioTrackException : Exception("No audio track")

/**
 * Decodes the first audio track of a [Uri] with the platform's own codecs and reduces it
 * to peak amplitudes as it goes. Uses MediaExtractor + MediaCodec rather than a player so
 * it runs as fast as the codec allows (a 4-minute MP3 typically takes a few seconds), and
 * works the same for audio files and for the audio track of a video.
 */
class WaveformDecoder(private val context: Context) {

    suspend fun decode(uri: Uri, onProgress: (Float) -> Unit): Waveform = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: run {
                val mimes = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
                Log.w("Ringcut", "No audio track among $mimes")
                throw NoAudioTrackException()
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            var durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else -1L
            if (durationUs <= 0) durationUs = retrieverDurationUs(uri)

            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            val decoded = try {
                codec.configure(format, null, null, 0)
                codec.start()
                drain(extractor, codec, format, Waveform.bucketMsFor(durationUs / 1000), durationUs, onProgress)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            val raw = decoded.peaks.finish()
            val factor = Waveform.downsampleFactor(raw.size, Waveform.MAX_BUCKETS)
            val bucketMs = decoded.peaks.framesPerBucket * factor * 1000.0 / decoded.sampleRate
            val durationMs = if (durationUs > 0) durationUs / 1000 else decoded.peaks.totalFrames * 1000 / decoded.sampleRate
            Waveform(Waveform.downsample(raw, Waveform.MAX_BUCKETS), bucketMs, durationMs)
        } finally {
            extractor.release()
        }
    }

    private class Decoded(val peaks: PeakAccumulator, val sampleRate: Int)

    private suspend fun drain(
        extractor: MediaExtractor,
        codec: MediaCodec,
        inputFormat: MediaFormat,
        bucketMs: Long,
        durationUs: Long,
        onProgress: (Float) -> Unit,
    ): Decoded {
        var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var isFloat = false
        var acc: Decoded? = null
        var shorts = ShortArray(0)
        var floats = FloatArray(0)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var lastProgress = -1

        while (true) {
            coroutineContext.ensureActive()
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // The real output shape: HE-AAC, for one, doubles the rate the container claims.
                val out = codec.outputFormat
                channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                isFloat = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                    out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
            } else if (outIndex >= 0) {
                val target = acc ?: Decoded(PeakAccumulator(Waveform.framesPerBucket(bucketMs, sampleRate)), sampleRate)
                acc = target
                if (info.size > 0) {
                    val buffer = codec.getOutputBuffer(outIndex)!!
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    buffer.order(ByteOrder.nativeOrder())
                    if (isFloat) {
                        val fb = buffer.asFloatBuffer()
                        val n = fb.remaining()
                        if (floats.size < n) floats = FloatArray(n)
                        fb.get(floats, 0, n)
                        target.peaks.addPcmFloat(floats, n, channels)
                    } else {
                        val sb = buffer.asShortBuffer()
                        val n = sb.remaining()
                        if (shorts.size < n) shorts = ShortArray(n)
                        sb.get(shorts, 0, n)
                        target.peaks.addPcm16(shorts, n, channels)
                    }
                    if (durationUs > 0) {
                        val pct = (info.presentationTimeUs * 100 / durationUs).toInt().coerceIn(0, 100)
                        if (pct != lastProgress) {
                            lastProgress = pct
                            onProgress(pct / 100f)
                        }
                    }
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        return acc
    }

    private fun retrieverDurationUs(uri: Uri): Long = runCatching {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(context, uri)
            (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L) * 1000
        }
    }.getOrDefault(-1L)

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}
