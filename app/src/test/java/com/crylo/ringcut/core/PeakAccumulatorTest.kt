package com.crylo.ringcut.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PeakAccumulatorTest {
    @Test fun takesLoudestFrameAndChannelPerBucket() {
        val acc = PeakAccumulator(framesPerBucket = 2)
        // Stereo frames: (100,-200) (50,0) | (0,0) (-32768,10)
        val samples = shortArrayOf(100, -200, 50, 0, 0, 0, -32768, 10)
        acc.addPcm16(samples, samples.size, channels = 2)
        assertArrayEquals(floatArrayOf(200 / 32768f, 1f), acc.finish(), 1e-6f)
        assertEquals(4, acc.totalFrames)
    }

    @Test fun bucketsSpanBufferBoundariesAndPartialBucketIsKept() {
        val acc = PeakAccumulator(framesPerBucket = 3)
        acc.addPcm16(shortArrayOf(1000, 2000), 2, 1)
        acc.addPcm16(shortArrayOf(3000, 0, 9999), 3, 1)
        // Buckets: (1000,2000,3000) (0,9999)
        assertArrayEquals(floatArrayOf(3000 / 32768f, 9999 / 32768f), acc.finish(), 1e-6f)
    }

    @Test fun ignoresEntriesPastCountAndTrailingPartialFrame() {
        val acc = PeakAccumulator(framesPerBucket = 10)
        // count = 3 with stereo: only one whole frame is read.
        acc.addPcm16(shortArrayOf(10, 20, 30000, 30000), 3, 2)
        assertEquals(1, acc.totalFrames)
        assertArrayEquals(floatArrayOf(20 / 32768f), acc.finish(), 1e-6f)
    }

    @Test fun floatSamplesAreClampedToOne() {
        val acc = PeakAccumulator(framesPerBucket = 1)
        acc.addPcmFloat(floatArrayOf(-0.5f, 1.5f), 2, 1)
        assertArrayEquals(floatArrayOf(0.5f, 1f), acc.finish(), 1e-6f)
    }

    @Test fun growsPastInitialCapacity() {
        val acc = PeakAccumulator(framesPerBucket = 1)
        val samples = ShortArray(5000) { 16384 }
        acc.addPcm16(samples, samples.size, 1)
        assertEquals(5000, acc.finish().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyBuckets() {
        PeakAccumulator(0)
    }
}
