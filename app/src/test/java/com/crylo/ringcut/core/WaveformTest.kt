package com.crylo.ringcut.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformTest {
    private val wave = Waveform(floatArrayOf(0.1f, 0.5f, 0.2f, 0.9f, 0.3f, 0.4f), bucketMs = 10.0, durationMs = 60)

    @Test fun barsTakeMaxOfCoveredBuckets() {
        assertArrayEquals(floatArrayOf(0.5f, 0.9f, 0.4f), wave.bars(0.0, 60.0, 3), 0f)
    }

    @Test fun zoomingPastResolutionRepeatsInsteadOfGaps() {
        // 10 ms range into 4 bars: every bar still lands in bucket 1.
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f), wave.bars(10.0, 20.0, 4), 0f)
    }

    @Test fun barsOutsideTheTrackAreZero() {
        val bars = wave.bars(-30.0, 90.0, 4) // ranges: -30..0, 0..30, 30..60, 60..90
        assertArrayEquals(floatArrayOf(0f, 0.5f, 0.9f, 0f), bars, 0f)
    }

    @Test fun emptyOrDegenerateRequestsAreSafe() {
        assertEquals(0, wave.bars(0.0, 60.0, 0).size)
        assertArrayEquals(floatArrayOf(0f, 0f), wave.bars(30.0, 30.0, 2), 0f)
        assertArrayEquals(floatArrayOf(0f), Waveform(FloatArray(0), 10.0, 0).bars(0.0, 10.0, 1), 0f)
    }

    @Test fun maxPeak() {
        assertEquals(0.9f, wave.maxPeak)
    }

    @Test fun bucketSizeScalesWithDuration() {
        assertEquals(10, Waveform.bucketMsFor(-1))
        assertEquals(10, Waveform.bucketMsFor(180_000)) // 3 min → 18 000 buckets
        assertEquals(360, Waveform.bucketMsFor(2 * 3_600_000)) // 2 h → 20 000 buckets
        assertEquals(441, Waveform.framesPerBucket(10, 44_100))
        assertEquals(1, Waveform.framesPerBucket(0, 44_100))
    }

    @Test fun downsampleKeepsGroupMaxima() {
        val peaks = floatArrayOf(0.1f, 0.5f, 0.2f, 0.9f, 0.3f)
        assertArrayEquals(floatArrayOf(0.5f, 0.9f, 0.3f), Waveform.downsample(peaks, 3), 0f)
        assertArrayEquals(floatArrayOf(0.9f), Waveform.downsample(peaks, 1), 0f)
        assertArrayEquals(peaks, Waveform.downsample(peaks, 10), 0f)
        assertEquals(2, Waveform.downsampleFactor(5, 3))
        assertEquals(1, Waveform.downsampleFactor(5, 5))
    }
}
