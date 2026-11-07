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

    /**
     * Where this glide last left the viewport.
     *
     * A glide owns the view only while nothing else writes it: the hand's pan, the pen's packets and a
     * search reveal all move the same offset, so a frame that finds it somewhere it did not leave it has
     * been overtaken and must let go. This is what ends a glide without anybody cancelling it — and
     * cancelling it on a pointer press was the bug: the press belonged to the hand, and the wheel took it.
     */
    private var writtenX = 0f
    private var writtenY = 0f

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
        writtenX = stepX
        writtenY = stepY

        if (hypot(targetX - stepX, targetY - stepY) < 0.5f) {
            cancel()
            onViewport(vp.copy(offsetX = targetX, offsetY = targetY))
            return
        }

        if (job?.isActive == true) return

        val run = scope.launch {
            var lastNano = withFrameNanos { it }
            while (isActive) {
                // `withFrameNanos` answers whether the glide is **over**, and the loop has to leave when
                // it is. Clearing [job] inside the frame and returning only from the lambda left the
                // `while` spinning with no handle on it: the window then wrote this notch's target over
                // the viewport on every frame, so the sheet stayed glued where the wheel left it and the
                // hand's pan moved nothing at all. One notch at the start of a session owned the view.
                val settled = withFrameNanos { nowNano ->
                    // A frame already queued when the glide was cancelled must write **nothing**: the hand
                    // cancelled it precisely to write its own travel, and this callback used to land after
                    // that event and put the wheel's target back.
                    if (!isActive) return@withFrameNanos true
                    val dt = ((nowNano - lastNano) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    lastNano = nowNano

                    val currentVp = currentViewport()
                    val frameCurX = currentVp.offsetX
                    val frameCurY = currentVp.offsetY
                    // Overtaken: someone else moved the sheet since this glide's last write, so the
                    // glide is over. Writing the target over it is what made the hand look dead after
                    // a scroll — the sheet was pulled back to the wheel's resting place every frame.
                    if (hypot(frameCurX - writtenX, frameCurY - writtenY) > 0.5f) return@withFrameNanos true
                    val frameDiffX = targetX - frameCurX
                    val frameDiffY = targetY - frameCurY

                    if (hypot(frameDiffX, frameDiffY) < 0.5f) {
                        onViewport(currentVp.copy(offsetX = targetX, offsetY = targetY))
                        writtenX = targetX
                        writtenY = targetY
                        true
                    } else {
                        val alpha = (1f - exp(-decay * dt)).coerceIn(0.01f, 1f)
                        val nextX = frameCurX + frameDiffX * alpha
                        val nextY = frameCurY + frameDiffY * alpha
                        onViewport(currentVp.copy(offsetX = nextX, offsetY = nextY))
                        writtenX = nextX
                        writtenY = nextY
                        false
                    }
                }
                if (settled) break
            }
        }
        job = run
        // Drop the handle only if this run is still the current one: a newer notch may own [job] already,
        // and clearing it would leave that glide uncancellable.
        run.invokeOnCompletion { if (job === run) job = null }
    }
}

@Composable
internal fun rememberSmoothScroller(): SmoothScroller {
    val scope = rememberCoroutineScope()
    return remember(scope) { SmoothScroller(scope) }
}
