package com.mobixournal.render

import android.graphics.Canvas
import android.graphics.RectF
import com.mobixournal.format.model.PlotElement
import java.util.Locale
import kotlin.math.max

/**
 * One parameter slider track geometry in view px.
 */
internal data class PlotSliderTrack(
    val paramName: String,
    val value: Double,
    val minVal: Double,
    val maxVal: Double,
    val trackStart: Float,
    val trackEnd: Float,
    val centerY: Float,
    val thumbRadius: Float = 8f,
) {
    val thumbX: Float
        get() {
            val span = (maxVal - minVal).takeIf { it > 1e-6 } ?: 1.0
            val fraction = ((value - minVal) / span).coerceIn(0.0, 1.0).toFloat()
            return trackStart + fraction * (trackEnd - trackStart)
        }

    fun hitTest(x: Float, y: Float): Boolean {
        val padY = 22f
        val padX = 22f
        return y in (centerY - padY)..(centerY + padY) &&
            x in (trackStart - padX)..(trackEnd + padX)
    }

    fun valueForTouch(x: Float): Double {
        val span = trackEnd - trackStart
        if (span <= 1e-3f) return minVal
        val fraction = ((x - trackStart) / span).coerceIn(0f, 1f).toDouble()
        return minVal + fraction * (maxVal - minVal)
    }
}

/**
 * Layout and rendering for on-canvas interactive parameter sliders anchored to a [PlotElement].
 */
internal object PlotSliders {

    private const val ROW_HEIGHT_PX = 36f
    private const val SLIDER_PAD_TOP_PX = 14f
    private const val MIN_TRACK_WIDTH_PX = 160f
    private const val CARD_PAD_PX = 12f

    fun computeSliderTracks(
        plot: PlotElement,
        pageBox: PageBox,
        scrollX: Float,
        scrollY: Float,
    ): List<PlotSliderTrack> {
        if (plot.parameters.isEmpty()) return emptyList()

        val l = (plot.left * pageBox.scale + pageBox.leftPx - scrollX).toFloat()
        val r = (plot.right * pageBox.scale + pageBox.leftPx - scrollX).toFloat()
        val b = (plot.bottom * pageBox.scale + pageBox.topPx - scrollY).toFloat() +
            DrawingSurfaceDefaults.SELECT_PAD_PX

        val plotWidth = r - l
        val trackWidth = max(plotWidth - 32f, MIN_TRACK_WIDTH_PX)
        val midX = (l + r) / 2f
        val trackStart = midX - trackWidth / 2f
        val trackEnd = midX + trackWidth / 2f

        val sortedEntries = plot.parameters.entries.sortedBy { it.key }
        val startY = b + SLIDER_PAD_TOP_PX

        return sortedEntries.mapIndexed { index, (name, value) ->
            val centerY = startY + index * ROW_HEIGHT_PX + ROW_HEIGHT_PX / 2f
            val minVal = if (value < -10.0) value - 5.0 else -10.0
            val maxVal = if (value > 10.0) value + 5.0 else 10.0
            PlotSliderTrack(
                paramName = name,
                value = value,
                minVal = minVal,
                maxVal = maxVal,
                trackStart = trackStart,
                trackEnd = trackEnd,
                centerY = centerY,
            )
        }
    }

    fun hitTestSlider(tracks: List<PlotSliderTrack>, touchX: Float, touchY: Float): PlotSliderTrack? =
        tracks.firstOrNull { it.hitTest(touchX, touchY) }

    fun draw(canvas: Canvas, tracks: List<PlotSliderTrack>, chrome: CanvasChrome) {
        if (tracks.isEmpty()) return

        val minX = tracks.minOf { it.trackStart } - CARD_PAD_PX
        val maxX = tracks.maxOf { it.trackEnd } + CARD_PAD_PX
        val minY = tracks.minOf { it.centerY - ROW_HEIGHT_PX / 2f } - CARD_PAD_PX / 2f
        val maxY = tracks.maxOf { it.centerY + ROW_HEIGHT_PX / 2f } + CARD_PAD_PX / 2f
        val cardRect = RectF(minX, minY, maxX, maxY)
        canvas.drawRoundRect(cardRect, 10f, 10f, chrome.sliderBackground)
        canvas.drawRoundRect(cardRect, 10f, 10f, chrome.sliderCardBorder)

        for (track in tracks) {
            val label = String.format(Locale.US, "%s = %.2f", track.paramName, track.value)
            val labelY = track.centerY - 4f
            canvas.drawText(label, track.trackStart, labelY, chrome.sliderText)

            val lineY = track.centerY + 8f
            canvas.drawLine(track.trackStart, lineY, track.trackEnd, lineY, chrome.sliderTrack)

            val thumbX = track.thumbX
            canvas.drawLine(track.trackStart, lineY, thumbX, lineY, chrome.sliderTrackActive)

            canvas.drawCircle(thumbX, lineY, track.thumbRadius, chrome.sliderThumb)
            canvas.drawCircle(thumbX, lineY, track.thumbRadius, chrome.sliderThumbBorder)
        }
    }
}
