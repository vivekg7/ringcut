package com.crylo.ringcut.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Peak amplitudes for a whole track, one per [bucketMs] of audio, plus the track's
 * duration. Everything the waveform view needs; no PCM is kept.
 */
class Waveform(val peaks: FloatArray, val bucketMs: Double, val durationMs: Long) {
    init {
        require(bucketMs > 0) { "bucketMs must be positive" }
    }

    /** Loudest peak in the track; the view scales by it so quiet recordings still show. */
    val maxPeak: Float = peaks.maxOrNull() ?: 0f

    /**
     * Shrinks the time range [fromMs, toMs) into [count] bars. Each bar is the loudest
     * bucket it covers, and always covers at least one bucket so that zooming in past
     * the stored resolution repeats values instead of leaving gaps. Bars past either end
     * of the track are zero.
     */
    fun bars(fromMs: Double, toMs: Double, count: Int): FloatArray {
        val out = FloatArray(max(count, 0))
        if (count <= 0 || toMs <= fromMs || peaks.isEmpty()) return out
        val step = (toMs - fromMs) / count
        for (i in 0 until count) {
            val a = fromMs + i * step
            val b = a + step
            if (b <= 0 || a >= peaks.size * bucketMs) continue
            val first = floor(a / bucketMs).toInt().coerceIn(0, peaks.size - 1)
            val last = max(first + 1, ceil(b / bucketMs).toInt()).coerceAtMost(peaks.size)
            var m = 0f
            for (j in first until last) if (peaks[j] > m) m = peaks[j]
            out[i] = m
        }
        return out
    }

    companion object {
        /** Finest resolution worth storing: 10 ms is below what a finger can place. */
        const val MIN_BUCKET_MS = 10L

        /** Upper bound on stored buckets, so a two-hour video stays a few hundred KB. */
        const val MAX_BUCKETS = 20_000

        /** Bucket length for a track of [durationMs], or [MIN_BUCKET_MS] if unknown. */
        fun bucketMsFor(durationMs: Long): Long {
            if (durationMs <= 0) return MIN_BUCKET_MS
            return max(MIN_BUCKET_MS, ceil(durationMs.toDouble() / MAX_BUCKETS).toLong())
        }

        /** Frames per bucket at [sampleRate]; never zero. */
        fun framesPerBucket(bucketMs: Long, sampleRate: Int): Int =
            max(1L, bucketMs * sampleRate / 1000).toInt()

        /** How many stored peaks [downsample] merges into one; 1 when nothing is merged. */
        fun downsampleFactor(size: Int, maxCount: Int): Int {
            require(maxCount > 0)
            return if (size <= maxCount) 1 else ceil(size.toDouble() / maxCount).toInt()
        }

        /**
         * Merges neighbouring peaks (keeping the max of each group) until at most
         * [maxCount] remain. Used when the duration was unknown up front and the
         * decode produced more buckets than are worth keeping.
         */
        fun downsample(peaks: FloatArray, maxCount: Int): FloatArray {
            val factor = downsampleFactor(peaks.size, maxCount)
            if (factor == 1) return peaks
            val out = FloatArray(ceil(peaks.size.toDouble() / factor).toInt())
            for (i in out.indices) {
                var m = 0f
                for (j in i * factor until min((i + 1) * factor, peaks.size)) if (peaks[j] > m) m = peaks[j]
                out[i] = m
            }
            return out
        }
    }
}
