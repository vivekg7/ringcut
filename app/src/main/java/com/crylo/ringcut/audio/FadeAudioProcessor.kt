package com.crylo.ringcut.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.crylo.ringcut.core.FadeEnvelope
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Applies a [FadeEnvelope] to decoded PCM inside Transformer's audio pipeline. Media3 has
 * no built-in fade, but a gain ramp is a few lines over its processor base class. The
 * envelope's frame counter starts at the first frame Transformer hands over, which is the
 * clip start because clipping happens upstream.
 */
@OptIn(UnstableApi::class)
class FadeAudioProcessor(
    private val lengthMs: Long,
    private val fadeInMs: Long,
    private val fadeOutMs: Long,
) : BaseAudioProcessor() {
    private var envelope = FadeEnvelope(0, 0, 0)
    private var frame = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        envelope = FadeEnvelope.of(lengthMs, fadeInMs, fadeOutMs, inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val format = inputAudioFormat
        val frames = remaining / format.bytesPerFrame
        val output = replaceOutputBuffer(frames * format.bytesPerFrame)
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        repeat(frames) {
            val gain = envelope.gainAt(frame++)
            repeat(format.channelCount) {
                if (isFloat) {
                    output.putFloat(inputBuffer.getFloat() * gain)
                } else {
                    val v = (inputBuffer.getShort() * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    output.putShort(v.toShort())
                }
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        frame = 0
    }

    override fun onReset() {
        frame = 0
    }
}
