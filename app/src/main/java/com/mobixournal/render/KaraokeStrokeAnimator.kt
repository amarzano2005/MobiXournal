package com.mobixournal.render

import android.graphics.Canvas
import android.graphics.Paint
import com.mobixournal.audio.audioRef
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import kotlin.math.hypot

/**
 * Coordinates inking karaoke animation and sub-stroke scrubbing during audio replay
 * (`docs/audio-inking-sync.md`).
 *
 * During playback:
 * - Strokes written after the audio playhead are dimmed to alpha = 0.25.
 * - Active strokes in progress animate smoothly up to the current timestamp.
 * - The active nib is marked with a subtle glowing indicator.
 */
object KaraokeStrokeAnimator {

    const val DIMMED_ALPHA_FACTOR: Float = 0.25f

    /** The live audio playback state driving the karaoke canvas. */
    data class State(
        val audioFile: String,
        val playheadMs: Int,
        val isPlaying: Boolean = true,
    )

    /** The resolved rendering directive for a single stroke. */
    sealed interface RenderDirective {
        /** The stroke is rendered fully and normally. */
        data object Normal : RenderDirective

        /** The stroke is in the future relative to the playhead and is dimmed. */
        data class Dimmed(val colorArgb: Int) : RenderDirective

        /** The stroke is actively in progress: [visiblePoints] are drawn, tip is at [activeTip]. */
        data class Animating(
            val visiblePoints: List<StrokePoint>,
            val activeTip: StrokePoint?,
            val futurePoints: List<StrokePoint>?,
            val dimmedColorArgb: Int,
        ) : RenderDirective
    }

    /**
     * Determine how [stroke] should be rendered given the current karaoke playback [state].
     *
     * A **held** playhead — a pause, or a position dragged out with the scrubber — resolves exactly as a
     * sounding one does, because the writing at a position is the same writing whether or not sound is
     * coming out of the speaker; that is what lets a scrub walk the page back and forth. "Nothing is on"
     * is said by [state] being null, not by [State.isPlaying], which is informational (the chrome reads
     * it to label its own button).
     */
    fun resolve(stroke: Stroke, state: State?): RenderDirective {
        if (state == null) return RenderDirective.Normal
        val ref = stroke.audioRef() ?: return RenderDirective.Normal
        if (!isMatchingRecording(ref.filename, state.audioFile)) return RenderDirective.Normal

        val strokeStart = ref.startMs
        if (state.playheadMs < strokeStart) {
            return RenderDirective.Dimmed(dimmedColor(stroke.color, DIMMED_ALPHA_FACTOR))
        }

        val points = stroke.points
        if (points.isEmpty()) return RenderDirective.Normal

        val maxPointTime = strokeStart + (points.maxOfOrNull { it.dtMs ?: 0 } ?: 0)
        if (state.playheadMs >= maxPointTime) {
            return RenderDirective.Normal
        }

        val visible = points.filter { (strokeStart + (it.dtMs ?: 0)) <= state.playheadMs }
        val future = points.filter { (strokeStart + (it.dtMs ?: 0)) > state.playheadMs }
        val dimmedColor = dimmedColor(stroke.color, DIMMED_ALPHA_FACTOR)

        return RenderDirective.Animating(
            visiblePoints = visible,
            activeTip = visible.lastOrNull(),
            futurePoints = future,
            dimmedColorArgb = dimmedColor,
        )
    }

    /**
     * The **absolute time** point [index] of [points] names, or null when the stroke has no timeline up
     * to it: `T_start` plus the deltas of every point before it, which is the formula
     * `docs/audio-inking-sync.md` writes (`t_abs(P_i) = T_stroke_start + Σ dt_k`).
     *
     * A missing delta is **not** zero: it is a point whose time was never reported (a `.xopp` import, a
     * `.mobix` written before the telemetry landed), and summing it as nothing would put every later
     * point too early. So a hole anywhere up to [index] makes the time unknown — null — and a hole
     * *after* it does not, because the points before the hole are as well timed as they ever were.
     */
    fun pointTimeMs(strokeStartMs: Int, points: List<StrokePoint>, index: Int): Int? {
        if (index !in points.indices) return null
        var elapsed = 0
        for (i in 0..index) {
            val dt = points[i].dtMs ?: return null
            elapsed += dt.coerceAtLeast(0)
        }
        return strokeStartMs + elapsed
    }

    /**
     * Where a tap at the document point ([x], [y]) lands in the recording: the time of the point of
     * [points] nearest it, or null when that point has no time of its own — in which case the caller
     * falls back to the stroke's own start offset, which is what a stroke with no telemetry can promise.
     */
    fun seekTimeMs(strokeStartMs: Int, points: List<StrokePoint>, x: Double, y: Double): Int? {
        val nearest = points.indices.minByOrNull { hypot(points[it].x - x, points[it].y - y) }
            ?: return null
        return pointTimeMs(strokeStartMs, points, nearest)
    }

    /**
     * Compute the ARGB color with its alpha multiplied by [alphaFactor].
     */
    fun dimmedColor(colorArgb: Int, alphaFactor: Float = DIMMED_ALPHA_FACTOR): Int {
        val originalAlpha = (colorArgb ushr 24) and 0xFF
        val newAlpha = (originalAlpha * alphaFactor).toInt().coerceIn(0, 255)
        return (colorArgb and 0x00FFFFFF) or (newAlpha shl 24)
    }

    /**
     * Compare audio file references by basename to tolerate path/prefix differences.
     */
    fun isMatchingRecording(refFilename: String, stateAudioFile: String): Boolean {
        val a = refFilename.substringAfterLast('/').substringAfterLast('\\')
        val b = stateAudioFile.substringAfterLast('/').substringAfterLast('\\')
        return a.equals(b, ignoreCase = true)
    }

    /**
     * Draw the glowing tip indicator for an actively animating stroke tip.
     */
    fun drawTipIndicator(
        canvas: Canvas,
        tip: StrokePoint,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        colorArgb: Int,
        paint: Paint = defaultTipPaint,
    ) {
        val cx = (tip.x * scale + offsetX).toFloat()
        val cy = (tip.y * scale + offsetY).toFloat()
        val radius = (tip.width * scale * 1.5).toFloat().coerceAtLeast(4f)
        paint.color = colorArgb
        paint.alpha = 180
        canvas.drawCircle(cx, cy, radius, paint)
    }

    private val defaultTipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
}

private var surfaceKaraokeState: KaraokeStrokeAnimator.State? = null

/** The current live audio karaoke playback state on this surface. */
var DrawingSurfaceView.karaokeState: KaraokeStrokeAnimator.State?
    get() = surfaceKaraokeState
    set(value) {
        surfaceKaraokeState = value
        render()
    }
