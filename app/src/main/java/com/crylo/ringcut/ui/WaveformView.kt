package com.crylo.ringcut.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.crylo.ringcut.R
import com.crylo.ringcut.core.Handle
import com.crylo.ringcut.core.TrimMath
import com.crylo.ringcut.core.TrimRange
import com.crylo.ringcut.core.Viewport
import com.crylo.ringcut.core.Waveform
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * The waveform with the selection on top. Dragging near a handle moves it; dragging
 * anywhere else scrolls a zoomed-in view; a tap moves the nearer handle to that spot.
 * Only horizontal drags are claimed, so the screen still scrolls vertically over it.
 */
@Composable
fun WaveformView(
    waveform: Waveform,
    range: TrimRange,
    viewport: Viewport,
    playheadMs: Long?,
    onMoveHandle: (Handle, Long) -> Unit,
    onPan: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentRange by rememberUpdatedState(range)
    val currentViewport by rememberUpdatedState(viewport)
    val moveHandle by rememberUpdatedState(onMoveHandle)
    val pan by rememberUpdatedState(onPan)
    val slopPx = with(LocalDensity.current) { 28.dp.toPx() }

    val colors = MaterialTheme.colorScheme
    val selectedBar = colors.primary
    val idleBar = colors.onSurfaceVariant.copy(alpha = 0.4f)
    val selection = colors.primary.copy(alpha = 0.14f)
    val handle = colors.tertiary
    val playhead = colors.error
    val description = stringResource(R.string.waveform_description)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerHigh)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                var target: Handle? = null
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val w = size.width.toFloat()
                        val vp = currentViewport
                        target = TrimMath.hitHandle(
                            offset.x,
                            vp.msToX(currentRange.startMs, w),
                            vp.msToX(currentRange.endMs, w),
                            slopPx,
                        )
                    },
                    onDragEnd = { target = null },
                    onDragCancel = { target = null },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val w = size.width.toFloat()
                        val vp = currentViewport
                        val t = target
                        if (t != null) {
                            moveHandle(t, vp.xToMs(change.position.x, w))
                        } else {
                            pan((-dragAmount / w * vp.spanMs).roundToLong())
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat()
                    val vp = currentViewport
                    val ms = vp.xToMs(offset.x, w)
                    val r = currentRange
                    val h = if (abs(ms - r.startMs) <= abs(ms - r.endMs)) Handle.START else Handle.END
                    moveHandle(h, ms)
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val step = 3.dp.toPx()
        val barWidth = 2.dp.toPx()
        val count = (w / step).toInt()
        val bars = waveform.bars(viewport.startMs.toDouble(), viewport.endMs.toDouble(), count)
        val scale = if (waveform.maxPeak > 0f) 1f / waveform.maxPeak else 1f
        val startX = viewport.msToX(range.startMs, w)
        val endX = viewport.msToX(range.endMs, w)
        val centre = h / 2
        val maxAmp = h * 0.42f
        val minAmp = 1.dp.toPx()

        drawRect(selection, Offset(startX, 0f), Size(endX - startX, h))
        for (i in 0 until count) {
            val x = i * step
            val amp = max(bars[i] * scale * maxAmp, minAmp)
            val inside = x + barWidth / 2 in startX..endX
            drawRoundRect(
                if (inside) selectedBar else idleBar,
                Offset(x, centre - amp),
                Size(barWidth, amp * 2),
                CornerRadius(barWidth / 2),
            )
        }

        val handleWidth = 3.dp.toPx()
        val knob = Size(10.dp.toPx(), 32.dp.toPx())
        for (x in listOf(startX, endX)) {
            if (x < -knob.width || x > w + knob.width) continue
            drawRect(handle, Offset(x - handleWidth / 2, 0f), Size(handleWidth, h))
            drawRoundRect(handle, Offset(x - knob.width / 2, centre - knob.height / 2), knob, CornerRadius(knob.width / 2))
        }

        playheadMs?.let {
            val x = viewport.msToX(it, w)
            if (x in 0f..w) drawRect(playhead, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), h))
        }
    }
}
