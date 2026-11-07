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
     * The zero-latency first step of a notch: it moves a fraction of the way at once, so the wheel is
     * answered on its own event frame, and answers **true** when that step already arrived — nothing is
     * then left to animate.
     */
    private fun leadStep(
        vp: Viewport,
        diffX: Float,
        diffY: Float,
        decay: Float,
        onViewport: (Viewport) -> Unit,
    ): Boolean {
        val initialAlpha = (1f - exp(-decay * 0.016f)).coerceIn(0.15f, 0.45f)
        val stepX = vp.offsetX + diffX * initialAlpha
        val stepY = vp.offsetY + diffY * initialAlpha
        onViewport(vp.copy(offsetX = stepX, offsetY = stepY))
        writtenX = stepX
        writtenY = stepY
        if (hypot(targetX - stepX, targetY - stepY) >= 0.5f) return false
        cancel()
        onViewport(vp.copy(offsetX = targetX, offsetY = targetY))
        return true
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
        /** False for a **fling**: the hand already moved what it moved, so there is no lead step to make. */
        lead: Boolean = true,
        /** The dp the clamp kept back, per axis — the edge a notch just ran into (`EdgeBump`). */
        onRefused: (Float, Float) -> Unit = { _, _ -> },
    ) {
        val vp = currentViewport()
        val contentW = pageW * vp.zoom
        val contentH = pageH * vp.zoom

        val baseTargetX = if (isRunning) targetX else vp.offsetX
        val baseTargetY = if (isRunning) targetY else vp.offsetY

        val wantX = baseTargetX + dx
        val wantY = baseTargetY + dy
        targetX = PageIndexScroll.clamp(wantX, contentW, viewW)
        targetY = PageIndexScroll.clamp(wantY, contentH, viewH)
        onRefused(wantX - targetX, wantY - targetY)

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
        writtenX = curX
        writtenY = curY

        // Immediate responsive lead step: provides zero-latency visual feedback on the event frame.
        if (lead && leadStep(vp, diffX, diffY, decay, onViewport)) return

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

/**
 * The **hand's release**: [dx]/[dy] dp of projected travel, eased with the fluidity a notch uses.
 *
 * Same engine, one difference — no lead step. A notch jumps a little on the frame it arrives so the wheel
 * feels answered; a hand that has just let go has already answered itself, and a jump there would read as
 * the sheet running away from the pointer.
 */
internal fun SmoothScroller.flingBy(
    dx: Float,
    dy: Float,
    currentViewport: () -> Viewport,
    pageW: Float,
    pageH: Float,
    viewW: Float,
    viewH: Float,
    fluidity: Float,
    onViewport: (Viewport) -> Unit,
) = scrollBy(
    dx = dx, dy = dy, currentViewport = currentViewport, pageW = pageW, pageH = pageH,
    viewW = viewW, viewH = viewH, fluidity = fluidity, onViewport = onViewport, lead = false,
)

@Composable
internal fun rememberSmoothScroller(): SmoothScroller {
    val scope = rememberCoroutineScope()
    return remember(scope) { SmoothScroller(scope) }
}
