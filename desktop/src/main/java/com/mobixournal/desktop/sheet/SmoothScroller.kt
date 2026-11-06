package com.mobixournal.desktop.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import com.mobixournal.desktop.settings.DesktopNavigationStore
import com.mobixournal.desktop.shell.PageIndexScroll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.hypot

/**
 * Frame-rate independent smooth scrolling animation for desktop wheel input.
 *
 * Successive wheel notches accumulate into [targetX] and [targetY]. An immediate responsive
 * first step eliminates perceived input lag, while a coroutine loop using [withFrameNanos]
 * smoothly glides towards the target with exponential decay.
 */
internal class SmoothScroller(private val scope: CoroutineScope) {
    private var job: Job? = null
    var targetX: Float = 0f
        private set
    var targetY: Float = 0f
        private set

    val isRunning: Boolean get() = job?.isActive == true

    /** Cancels any in-flight glide immediately, keeping the current viewport position. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /**
     * Nudges the viewport by [dx] and [dy] dp, animating smoothly towards the target offset.
     */
    fun scrollBy(
        dx: Float,
        dy: Float,
        currentViewport: () -> Viewport,
        pageW: Float,
        pageH: Float,
        viewW: Float,
        viewH: Float,
        fluidity: Float,
        onViewport: (Viewport) -> Unit,
    ) {
        val vp = currentViewport()
        val contentW = pageW * vp.zoom
        val contentH = pageH * vp.zoom

        val baseTargetX = if (isRunning) targetX else vp.offsetX
        val baseTargetY = if (isRunning) targetY else vp.offsetY

        targetX = PageIndexScroll.clamp(baseTargetX + dx, contentW, viewW)
        targetY = PageIndexScroll.clamp(baseTargetY + dy, contentH, viewH)

        val curX = vp.offsetX
        val curY = vp.offsetY
        val diffX = targetX - curX
        val diffY = targetY - curY

        if (hypot(diffX, diffY) < 0.5f) {
            cancel()
            if (targetX != curX || targetY != curY) {
                onViewport(vp.copy(offsetX = targetX, offsetY = targetY))
            }
            return
        }

        val decay = DesktopNavigationStore.decayRate(fluidity)

        // Immediate responsive lead step: provides zero-latency visual feedback on the event frame
        val initialAlpha = (1f - exp(-decay * 0.016f)).coerceIn(0.15f, 0.45f)
        val stepX = curX + diffX * initialAlpha
        val stepY = curY + diffY * initialAlpha
        onViewport(vp.copy(offsetX = stepX, offsetY = stepY))

        if (hypot(targetX - stepX, targetY - stepY) < 0.5f) {
            cancel()
            onViewport(vp.copy(offsetX = targetX, offsetY = targetY))
            return
        }

        if (job?.isActive == true) return

        job = scope.launch {
            var lastNano = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { nowNano ->
                    val dt = ((nowNano - lastNano) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    lastNano = nowNano

                    val currentVp = currentViewport()
                    val frameCurX = currentVp.offsetX
                    val frameCurY = currentVp.offsetY
                    val frameDiffX = targetX - frameCurX
                    val frameDiffY = targetY - frameCurY

                    if (hypot(frameDiffX, frameDiffY) < 0.5f) {
                        onViewport(currentVp.copy(offsetX = targetX, offsetY = targetY))
                        job = null
                        return@withFrameNanos
                    }

                    val alpha = (1f - exp(-decay * dt)).coerceIn(0.01f, 1f)
                    val nextX = frameCurX + frameDiffX * alpha
                    val nextY = frameCurY + frameDiffY * alpha
                    onViewport(currentVp.copy(offsetX = nextX, offsetY = nextY))
                }
            }
        }
    }
}

@Composable
internal fun rememberSmoothScroller(): SmoothScroller {
    val scope = rememberCoroutineScope()
    return remember(scope) { SmoothScroller(scope) }
}
