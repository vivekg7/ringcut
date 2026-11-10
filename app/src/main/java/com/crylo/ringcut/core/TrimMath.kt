package com.crylo.ringcut.core

import kotlin.math.abs
import kotlin.math.roundToLong

/** The part of the track to keep, in milliseconds from the start of the source. */
data class TrimRange(val startMs: Long, val endMs: Long) {
    val lengthMs: Long get() = endMs - startMs
}

enum class Handle { START, END }

/**
 * The slice of the track currently on screen. Pixel/time conversion lives here so the
 * view, the drag handling and the tests all agree on it.
 */
data class Viewport(val startMs: Long, val spanMs: Long) {
    val endMs: Long get() = startMs + spanMs

    fun msToX(ms: Long, widthPx: Float): Float =
        if (spanMs <= 0) 0f else ((ms - startMs).toFloat() / spanMs) * widthPx

    /** Clamped to the viewport, so dragging a handle past the edge parks it there. */
    fun xToMs(x: Float, widthPx: Float): Long {
        if (widthPx <= 0f) return startMs
        val fraction = (x / widthPx).coerceIn(0f, 1f)
        return startMs + (fraction * spanMs).roundToLong()
    }
}

object TrimMath {
    /** Shortest selection allowed; anything shorter can't be heard, let alone used. */
    const val MIN_LENGTH_MS = 500L

    /** Smallest zoomed-in span: two seconds across the screen is enough for 0.1 s work. */
    const val MIN_SPAN_MS = 2_000L

    /** Step of the fine-tune buttons. */
    const val NUDGE_MS = 100L

    /** Default length when a file is opened: a typical ringtone loop. */
    const val DEFAULT_LENGTH_MS = 30_000L

    fun initialRange(durationMs: Long): TrimRange =
        TrimRange(0, if (durationMs <= 0) 0 else minOf(durationMs, DEFAULT_LENGTH_MS))

    fun fullViewport(durationMs: Long): Viewport = Viewport(0, maxOf(durationMs, 1))

    /** Moves one edge, keeping it inside the track and at least [MIN_LENGTH_MS] from the other. */
    fun move(range: TrimRange, handle: Handle, toMs: Long, durationMs: Long): TrimRange {
        val minLength = minOf(MIN_LENGTH_MS, durationMs)
        return when (handle) {
            Handle.START -> range.copy(startMs = toMs.coerceIn(0, maxOf(0, range.endMs - minLength)))
            Handle.END -> range.copy(endMs = toMs.coerceIn(minOf(range.startMs + minLength, durationMs), durationMs))
        }
    }

    fun nudge(range: TrimRange, handle: Handle, deltaMs: Long, durationMs: Long): TrimRange {
        val current = if (handle == Handle.START) range.startMs else range.endMs
        return move(range, handle, current + deltaMs, durationMs)
    }

    /**
     * Which handle a touch at [x] grabs, or null if neither is within [slopPx]. When the
     * handles overlap, the side of the touch decides, so a collapsed selection can still
     * be pulled open in either direction.
     */
    fun hitHandle(x: Float, startX: Float, endX: Float, slopPx: Float): Handle? {
        val dStart = abs(x - startX)
        val dEnd = abs(x - endX)
        if (dStart > slopPx && dEnd > slopPx) return null
        return when {
            dStart < dEnd -> Handle.START
            dEnd < dStart -> Handle.END
            x < startX -> Handle.START
            else -> Handle.END
        }
    }

    /**
     * Zooms by [factor] (>1 zooms in) keeping [anchorMs] at the same place on screen,
     * then keeps the viewport inside the track.
     */
    fun zoom(viewport: Viewport, factor: Double, anchorMs: Long, durationMs: Long): Viewport {
        val total = maxOf(durationMs, 1)
        val span = (viewport.spanMs / factor).roundToLong().coerceIn(minOf(MIN_SPAN_MS, total), total)
        val anchorFraction =
            if (viewport.spanMs <= 0) 0.5 else ((anchorMs - viewport.startMs).toDouble() / viewport.spanMs).coerceIn(0.0, 1.0)
        val start = anchorMs - (anchorFraction * span).roundToLong()
        return Viewport(start.coerceIn(0, total - span), span)
    }

    fun pan(viewport: Viewport, deltaMs: Long, durationMs: Long): Viewport =
        viewport.copy(startMs = (viewport.startMs + deltaMs).coerceIn(0, maxOf(0, durationMs - viewport.spanMs)))

    /** Scrolls the least amount needed for [ms] to be on screen. */
    fun reveal(viewport: Viewport, ms: Long, durationMs: Long): Viewport = when {
        ms < viewport.startMs -> pan(viewport, ms - viewport.startMs, durationMs)
        ms > viewport.endMs -> pan(viewport, ms - viewport.endMs, durationMs)
        else -> viewport
    }

    /** "m:ss.t" (or "h:mm:ss.t"), tenths because that is the nudge resolution. */
    fun formatTime(ms: Long): String {
        val tenths = (maxOf(ms, 0) + 50) / 100
        val t = tenths % 10
        val totalSeconds = tenths / 10
        val s = totalSeconds % 60
        val m = (totalSeconds / 60) % 60
        val h = totalSeconds / 3600
        return if (h > 0) "%d:%02d:%02d.%d".format(h, m, s, t) else "%d:%02d.%d".format(m, s, t)
    }
}
