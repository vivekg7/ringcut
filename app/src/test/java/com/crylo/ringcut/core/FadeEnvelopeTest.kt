package com.crylo.ringcut.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FadeEnvelopeTest {
    @Test fun noFadeIsUnityGain() {
        val env = FadeEnvelope(100, 0, 0)
        assertEquals(1f, env.gainAt(0), 0f)
        assertEquals(1f, env.gainAt(99), 0f)
    }

    @Test fun rampsInAndOutLinearly() {
        val env = FadeEnvelope(100, 10, 20)
        assertEquals(0f, env.gainAt(0), 1e-6f)
        assertEquals(0.5f, env.gainAt(5), 1e-6f)
        assertEquals(1f, env.gainAt(50), 1e-6f)
        assertEquals(0.5f, env.gainAt(90), 1e-6f)
        assertEquals(0f, env.gainAt(100), 1e-6f)
    }

    @Test fun overlongFadesShrinkInProportion() {
        val env = FadeEnvelope(100, 100, 100)
        assertEquals(50, env.fadeInFrames)
        assertEquals(50, env.fadeOutFrames)
        assertEquals(1f, env.gainAt(50), 1e-6f)
    }

    @Test fun builtFromMilliseconds() {
        val env = FadeEnvelope.of(lengthMs = 2_000, fadeInMs = 500, fadeOutMs = 0, sampleRate = 48_000)
        assertEquals(96_000, env.totalFrames)
        assertEquals(24_000, env.fadeInFrames)
    }
}
