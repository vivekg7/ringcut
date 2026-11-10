package com.crylo.ringcut.core

/**
 * Linear gain ramps at the start and end of a clip of [totalFrames] frames. If the two
 * fades together are longer than the clip they are shrunk in proportion, so a short
 * clip fades in and straight back out instead of never reaching full volume twice.
 */
class FadeEnvelope(val totalFrames: Long, fadeInFrames: Long, fadeOutFrames: Long) {
    val fadeInFrames: Long
    val fadeOutFrames: Long

    init {
        val fin = fadeInFrames.coerceAtLeast(0)
        val fout = fadeOutFrames.coerceAtLeast(0)
        val total = totalFrames.coerceAtLeast(0)
        if (fin + fout > total && fin + fout > 0) {
            this.fadeInFrames = total * fin / (fin + fout)
            this.fadeOutFrames = total - this.fadeInFrames
        } else {
            this.fadeInFrames = fin
            this.fadeOutFrames = fout
        }
    }

    fun gainAt(frame: Long): Float {
        var gain = 1f
        if (fadeInFrames > 0 && frame < fadeInFrames) gain = frame.coerceAtLeast(0).toFloat() / fadeInFrames
        val framesLeft = totalFrames - frame
        if (fadeOutFrames > 0 && framesLeft < fadeOutFrames) {
            gain = minOf(gain, framesLeft.coerceAtLeast(0).toFloat() / fadeOutFrames)
        }
        return gain
    }

    companion object {
        /** Builds an envelope from milliseconds, for both export (sample rate) and preview (rate 1000). */
        fun of(lengthMs: Long, fadeInMs: Long, fadeOutMs: Long, sampleRate: Int): FadeEnvelope =
            FadeEnvelope(
                lengthMs * sampleRate / 1000,
                fadeInMs * sampleRate / 1000,
                fadeOutMs * sampleRate / 1000,
            )
    }
}
