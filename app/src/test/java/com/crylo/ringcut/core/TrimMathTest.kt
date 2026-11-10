package com.crylo.ringcut.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrimMathTest {
    private val duration = 60_000L

    @Test fun initialRangeIsThirtySecondsOrWholeTrack() {
        assertEquals(TrimRange(0, 30_000), TrimMath.initialRange(duration))
        assertEquals(TrimRange(0, 12_000), TrimMath.initialRange(12_000))
    }

    @Test fun handlesStayInsideTrackAndApart() {
        val r = TrimRange(10_000, 20_000)
        assertEquals(TrimRange(0, 20_000), TrimMath.move(r, Handle.START, -5_000, duration))
        assertEquals(TrimRange(19_500, 20_000), TrimMath.move(r, Handle.START, 25_000, duration))
        assertEquals(TrimRange(10_000, 60_000), TrimMath.move(r, Handle.END, 99_000, duration))
        assertEquals(TrimRange(10_000, 10_500), TrimMath.move(r, Handle.END, 0, duration))
    }

    @Test fun nudgeMovesByTenthsAndClamps() {
        val r = TrimRange(50, 1_000)
        assertEquals(TrimRange(150, 1_000), TrimMath.nudge(r, Handle.START, TrimMath.NUDGE_MS, duration))
        assertEquals(TrimRange(0, 1_000), TrimMath.nudge(r, Handle.START, -TrimMath.NUDGE_MS, duration))
        assertEquals(TrimRange(50, 900), TrimMath.nudge(r, Handle.END, -TrimMath.NUDGE_MS, duration))
    }

    @Test fun trackShorterThanMinimumLengthStillWorks() {
        val r = TrimRange(0, 300)
        assertEquals(TrimRange(0, 300), TrimMath.move(r, Handle.START, 200, 300))
        assertEquals(TrimRange(0, 300), TrimMath.move(r, Handle.END, 100, 300))
    }

    @Test fun viewportConversionsRoundTrip() {
        val vp = Viewport(10_000, 20_000)
        assertEquals(500f, vp.msToX(20_000, 1000f), 0.001f)
        assertEquals(20_000L, vp.xToMs(500f, 1000f))
        assertEquals(10_000L, vp.xToMs(-50f, 1000f))
        assertEquals(30_000L, vp.xToMs(5000f, 1000f))
    }

    @Test fun hitHandlePicksNearestWithinSlop() {
        assertEquals(Handle.START, TrimMath.hitHandle(105f, 100f, 300f, 20f))
        assertEquals(Handle.END, TrimMath.hitHandle(290f, 100f, 300f, 20f))
        assertNull(TrimMath.hitHandle(200f, 100f, 300f, 20f))
        // Overlapping handles: side of the touch decides.
        assertEquals(Handle.START, TrimMath.hitHandle(95f, 100f, 100f, 20f))
        assertEquals(Handle.END, TrimMath.hitHandle(105f, 100f, 100f, 20f))
    }

    @Test fun zoomKeepsAnchorInPlaceAndStaysInTrack() {
        val full = TrimMath.fullViewport(duration)
        val zoomed = TrimMath.zoom(full, 2.0, anchorMs = 30_000, durationMs = duration)
        assertEquals(Viewport(15_000, 30_000), zoomed)
        // Anchor near the start keeps its on-screen position (1/60 of the width).
        assertEquals(Viewport(500, 30_000), TrimMath.zoom(full, 2.0, 1_000, duration))
        // Anchor at the very end: the viewport can't run past the track.
        assertEquals(Viewport(30_000, 30_000), TrimMath.zoom(full, 2.0, 60_000, duration))
        // Never tighter than the minimum span, never wider than the track.
        assertEquals(TrimMath.MIN_SPAN_MS, TrimMath.zoom(full, 1000.0, 30_000, duration).spanMs)
        assertEquals(full, TrimMath.zoom(zoomed, 0.25, 30_000, duration))
    }

    @Test fun panAndRevealStayInTrack() {
        val vp = Viewport(10_000, 20_000)
        assertEquals(Viewport(0, 20_000), TrimMath.pan(vp, -50_000, duration))
        assertEquals(Viewport(40_000, 20_000), TrimMath.pan(vp, 50_000, duration))
        assertEquals(Viewport(5_000, 20_000), TrimMath.reveal(vp, 5_000, duration))
        assertEquals(Viewport(15_000, 20_000), TrimMath.reveal(vp, 35_000, duration))
        assertEquals(vp, TrimMath.reveal(vp, 20_000, duration))
    }

    @Test fun formatsTimeToTenths() {
        assertEquals("0:00.0", TrimMath.formatTime(0))
        assertEquals("0:05.3", TrimMath.formatTime(5_340))
        assertEquals("1:05.0", TrimMath.formatTime(64_960))
        assertEquals("1:02:03.4", TrimMath.formatTime(3_723_400))
        assertEquals("0:00.0", TrimMath.formatTime(-10))
    }
}
