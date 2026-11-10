package com.crylo.ringcut.core

import kotlin.math.abs

/**
 * Turns a stream of decoded PCM into one peak amplitude (0..1) per bucket of
 * [framesPerBucket] frames. A frame's value is the loudest of its channels, and a
 * bucket's value is the loudest frame in it: peaks, not averages, so short transients
 * (a drum hit, the start of a word) stay visible once the waveform is shrunk to screen
 * width.
 *
 * Memory is one float per bucket, so a whole song never has to be held as PCM.
 */
class PeakAccumulator(val framesPerBucket: Int) {
    init {
        require(framesPerBucket > 0) { "framesPerBucket must be positive" }
    }

    private var peaks = FloatArray(1024)
    private var size = 0
    private var current = 0f
    private var framesInBucket = 0

    /** Total frames seen so far, for working out duration when the container lacks it. */
    var totalFrames = 0L
        private set

    /** Interleaved signed 16-bit samples; only the first [count] entries are read. */
    fun addPcm16(samples: ShortArray, count: Int, channels: Int) {
        require(channels > 0)
        var i = 0
        while (i + channels <= count) {
            var max = 0
            for (c in 0 until channels) {
                val v = abs(samples[i + c].toInt())
                if (v > max) max = v
            }
            addFrame(max / 32768f)
            i += channels
        }
    }

    /** Interleaved float samples in -1..1; only the first [count] entries are read. */
    fun addPcmFloat(samples: FloatArray, count: Int, channels: Int) {
        require(channels > 0)
        var i = 0
        while (i + channels <= count) {
            var max = 0f
            for (c in 0 until channels) {
                val v = abs(samples[i + c])
                if (v > max) max = v
            }
            addFrame(max.coerceAtMost(1f))
            i += channels
        }
    }

    private fun addFrame(value: Float) {
        if (value > current) current = value
        framesInBucket++
        totalFrames++
        if (framesInBucket == framesPerBucket) closeBucket()
    }

    private fun closeBucket() {
        if (size == peaks.size) peaks = peaks.copyOf(size * 2)
        peaks[size++] = current
        current = 0f
        framesInBucket = 0
    }

    /** The peaks so far, including a final partial bucket. */
    fun finish(): FloatArray {
        if (framesInBucket > 0) closeBucket()
        return peaks.copyOf(size)
    }
}
