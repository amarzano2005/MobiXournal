package com.mobixournal.desktop.tools
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.sheet.PageInk
import com.mobixournal.desktop.sheet.number
import com.mobixournal.desktop.shell.thumbnail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Stroke as InkStroke
import com.mobixournal.render.LivingInk
import com.mobixournal.render.PlotBox
import com.mobixournal.render.PlotPreviewPalette
import kotlin.math.min

/**
 * The **live preview** of the graph a placement is asking for.
 *
 * The tablet's graph dialog shows the curve while its equations are typed, and the window's question had no
 * such answer: the values were entered blind and the result only appeared after **Place**. This draws the
 * *element itself* — [PendingPlacement.plotIn], the very builder Place commits — through `LivingInk`, the
 * same compiler the page and every export run, so what the card promises is what lands on the sheet, down
 * to the axes, the critical points and the shaded area.
 *
 * It reads the pending answers as **snapshot state**, so it follows every keystroke: the equations, the two
 * domain numbers, the flags and the shading all redraw it. Only the preview's palette is adapted to the
 * panel; the placed element and its exported colors remain unchanged.
 */
@Composable
internal fun PlotQuestionPreview(pending: PendingPlacement) {
    // Reading the answers inside the `remember` keys is also what subscribes this composable to them.
    val source = remember(
        pending.sources,
        pending.xMin,
        pending.xMax,
        pending.showCriticalPoints,
        pending.includeDerivative,
        pending.includeSummary,
        pending.shade,
        pending.shadeFrom,
        pending.shadeTo,
    ) {
        pending.plotIn(PREVIEW_BOX, PREVIEW_STROKE_PT)
    }
    PlotInkPreview(source, "No graph yet — an equation and an open range draw it here")
}

/**
 * The **preview of a plot that already exists** — the same picture for an element the lane is editing.
 *
 * The editor had no graph at all: its equations were committed blind, one `Enter` at a time, and the only
 * way to see what they drew was to close the lane and look at the sheet. It draws the element itself
 * through `LivingInk`, so the card and the page cannot disagree, and it fits the element's own box into
 * the card (a plotted graph is held by `left`/`top`/`right`/`bottom`, so the normalization is a subtraction
 * whatever box the element was placed in).
 */
@Composable
internal fun PlotInkPreview(plot: PlotElement?, placeholder: String) {
    val background = DesktopTheme.settingsPanel.toArgb()
    val axis = DesktopTheme.inkSecondary.toArgb()
    val accent = DesktopTheme.accent.toArgb()
    val ink = remember(plot, background, axis, accent) {
        plot?.let { LivingInk.of(PlotPreviewPalette.adapt(it, background, axis, accent)).elements }.orEmpty()
    }
    val strokes = ink.filterIsInstance<InkStroke>()
    val numbers = ink.filterIsInstance<TextElement>()
    val textMeasurer = rememberTextMeasurer()
    Box(
        Modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT)
            .padding(6.dp),
    ) {
        if (strokes.isEmpty()) {
            Text(
                placeholder,
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.label,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Canvas(Modifier.fillMaxSize()) { drawPreviewInk(strokes, numbers, textMeasurer) }
        }
    }
}

/**
 * Draw [strokes] — page points — fitted into this canvas, keeping the preview box's own aspect, and the
 * [numbers] the plot writes ([TextElement]s of the same compiled ink) at the very coordinates the
 * compiler gave them: the measured advances the sheet and the exports place them on, so the card's ticks
 * are the page's ticks.
 *
 * One uniform scale (the smaller of the two) rather than one per axis: a graph stretched to fill the card
 * would show a slope that is not the slope on the page.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewInk(
    strokes: List<InkStroke>,
    numbers: List<TextElement>,
    textMeasurer: TextMeasurer,
) {
    // A plot's ink is in page points, wherever the graph sits: its strokes start at its own `left`/`top`,
    // so the preview shifts them to the card's corner before scaling. A pending graph is built in a box at
    // the origin, so this is the same number for both callers.
    val all = strokes.flatMap { it.points }
    val originX = all.minOfOrNull { it.x }?.toFloat() ?: 0f
    val originY = all.minOfOrNull { it.y }?.toFloat() ?: 0f
    val boxW = ((all.maxOfOrNull { it.x }?.toFloat() ?: 1f) - originX).coerceAtLeast(1f)
    val boxH = ((all.maxOfOrNull { it.y }?.toFloat() ?: 1f) - originY).coerceAtLeast(1f)
    val scale = min(size.width / boxW, size.height / boxH)
    val offsetX = (size.width - boxW * scale) / 2f
    val offsetY = (size.height - boxH * scale) / 2f
    for (step in 1 until 8) {
        val x = offsetX + boxW * scale * step / 8f
        drawLine(DesktopTheme.hairline, androidx.compose.ui.geometry.Offset(x, offsetY),
            androidx.compose.ui.geometry.Offset(x, offsetY + boxH * scale), 1f)
    }
    for (step in 1 until 4) {
        val y = offsetY + boxH * scale * step / 4f
        drawLine(DesktopTheme.hairline, androidx.compose.ui.geometry.Offset(offsetX, y),
            androidx.compose.ui.geometry.Offset(offsetX + boxW * scale, y), 1f)
    }
    for (stroke in strokes) {
        val points = stroke.points
        if (points.size < 2) continue
        val path = Path().apply {
            moveTo(
                (points.first().x.toFloat() - originX) * scale + offsetX,
                (points.first().y.toFloat() - originY) * scale + offsetY,
            )
            for (index in 1 until points.size) {
                lineTo(
                    (points[index].x.toFloat() - originX) * scale + offsetX,
                    (points[index].y.toFloat() - originY) * scale + offsetY,
                )
            }
        }
        // The width the stroke carries, but never a hairline the preview cannot show.
        val width = (points.first().width.toFloat() * scale).coerceAtLeast(1f)
        drawPath(
            path = path,
            color = Color(stroke.color),
            style = Stroke(
                width = width,
                pathEffect = PageInk.dashEffect(stroke.lineStyle, stroke.points.first().width, scale),
            ),
        )
    }
    // The tick numbers, in the preview's own transform. The analysis summary is prose below the frame,
    // not a number beside an axis, and would run out of the card.
    for (number in numbers) {
        if (number.y > PREVIEW_BOX.bottom + LABEL_SLACK_PT) continue
        val face = textFace(number.font)
        val layout = textMeasurer.measure(
            text = number.content,
            style = TextStyle(
                color = Color(number.color),
                fontSize = (number.size * scale).toFloat().toSp(),
                fontFamily = face.family,
                fontWeight = face.weight,
                fontStyle = face.style,
            ),
        )
        drawText(
            layout,
            topLeft = androidx.compose.ui.geometry.Offset(
                (number.x.toFloat() - originX) * scale + offsetX,
                (number.y.toFloat() - originY) * scale + offsetY,
            ),
        )
    }
}

/** The box the preview is compiled in, in page points — the same proportions as the placed graph. */
private val PREVIEW_BOX = PlotBox(left = 0.0, top = 0.0, width = 320.0, height = 140.0)

/** The preview's height in dp: a graph, not a thumbnail. */
private val PREVIEW_HEIGHT = 128.dp

/** One point, so the preview reads a thin curve without turning it into a blob. */
private const val PREVIEW_STROKE_PT = 1.0

/** How far below the frame's bottom a text element may start and still be a tick number. */
private const val LABEL_SLACK_PT = 12.0
