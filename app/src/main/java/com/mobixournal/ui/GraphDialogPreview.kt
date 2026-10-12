package com.mobixournal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Stroke as InkStroke
import com.mobixournal.render.LivingInk
import com.mobixournal.render.PlotBox
import com.mobixournal.render.PlotCurve
import com.mobixournal.render.PlotElementFactory

/** The box the preview draws a plot in, in its own canvas's points. */
private val PREVIEW_BOX = PlotBox(left = 10.0, top = 10.0, width = 360.0, height = 160.0)

/** The point size the preview canvas is laid out for, margins included. */
private const val PREVIEW_WIDTH_PT = 380.0
private const val PREVIEW_HEIGHT_PT = 180.0

/**
 * The graph dialog's right-hand column: the **live preview** of the plot being asked for, and the three
 * switches that decide what is drawn beside the curves.
 *
 * It is a file of its own because the preview is its own subject — how a plot is shown *before* it is
 * placed — and because it is the one place in the dialog that talks to the drawing code rather than to
 * the inputs.
 */
@Composable
internal fun DialogPreviewColumn(
    modifier: Modifier = Modifier,
    rangeOk: Boolean,
    xMin: Double?,
    xMax: Double?,
    activeCurves: List<PlotCurve>,
    showCriticalPoints: Boolean,
    onShowCriticalPointsChange: (Boolean) -> Unit,
    includeDerivative: Boolean,
    onIncludeDerivativeChange: (Boolean) -> Unit,
    includeSummary: Boolean,
    onIncludeSummaryChange: (Boolean) -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (rangeOk && xMin != null && xMax != null) {
            FunctionPlotPreviewBox(
                curves = activeCurves,
                xMin = xMin,
                xMax = xMax,
                showCriticalPoints = showCriticalPoints,
                includeDerivative = includeDerivative,
            )
        }

        OptionsRow(
            showCriticalPoints = showCriticalPoints,
            onShowCriticalPointsChange = onShowCriticalPointsChange,
            includeDerivative = includeDerivative,
            onIncludeDerivativeChange = onIncludeDerivativeChange,
            includeSummary = includeSummary,
            onIncludeSummaryChange = onIncludeSummaryChange,
        )
    }
}

@Composable
private fun OptionsRow(
    showCriticalPoints: Boolean,
    onShowCriticalPointsChange: (Boolean) -> Unit,
    includeDerivative: Boolean,
    onIncludeDerivativeChange: (Boolean) -> Unit,
    includeSummary: Boolean,
    onIncludeSummaryChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showCriticalPoints, onCheckedChange = onShowCriticalPointsChange)
            Text("Detect critical points (roots, extrema, poles)", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includeDerivative, onCheckedChange = onIncludeDerivativeChange)
            Text("Show derivative curve f'(x)", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includeSummary, onCheckedChange = onIncludeSummaryChange)
            Text("Insert analysis report table next to plot", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * The plot as it will be placed, drawn from the drawing itself.
 *
 * The preview **is** the plot: the very element Insert would place, compiled by the very compiler the
 * page uses (`LivingInk`), so the graph promised here is the graph drawn on the sheet. That is also the
 * only way the parametric tab can preview anything at all: the multi-function plotter places `y = f(x)`
 * curves and refuses the rest, while the compiler samples a parametric pair as the loop it is.
 */
@Composable
private fun FunctionPlotPreviewBox(
    curves: List<PlotCurve>,
    xMin: Double,
    xMax: Double,
    showCriticalPoints: Boolean,
    includeDerivative: Boolean,
) {
    val axisColor = PREVIEW_AXIS_COLOR
    val previewInk = remember(curves, xMin, xMax, showCriticalPoints, includeDerivative, axisColor) {
        val element = PlotElementFactory.plotElementIn(
            box = PREVIEW_BOX,
            curves = curves,
            xMin = xMin,
            xMax = xMax,
            showCriticalPoints = showCriticalPoints,
            includeDerivative = includeDerivative,
            strokeWidthPt = 1.0,
            axisColor = axisColor,
        )
        LivingInk.of(element).elements.filterIsInstance<InkStroke>()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val scaleX = size.width / PREVIEW_WIDTH_PT.toFloat()
                val scaleY = size.height / PREVIEW_HEIGHT_PT.toFloat()
                for (ink in previewInk) {
                    val points = ink.points
                    if (points.size < 2) continue
                    val pathEffect = when (ink.lineStyle) {
                        LineStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        LineStyle.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(2f, 6f), 0f)
                        LineStyle.DASH_DOT -> PathEffect.dashPathEffect(floatArrayOf(12f, 6f, 2f, 6f), 0f)
                        LineStyle.PLAIN -> null
                    }
                    val path = Path()
                    path.moveTo(points.first().x.toFloat() * scaleX, points.first().y.toFloat() * scaleY)
                    for (i in 1 until points.size) {
                        path.lineTo(points[i].x.toFloat() * scaleX, points[i].y.toFloat() * scaleY)
                    }
                    val width = points.first().width.toFloat().coerceAtLeast(0.5f)
                    drawPath(
                        path = path,
                        color = Color(ink.color),
                        style = Stroke(width = width * scaleX, pathEffect = pathEffect),
                    )
                }
            }
        }
    }
}

/** The ink the preview's frame and axes are drawn in: the near-black the dialog has always shown. */
private val PREVIEW_AXIS_COLOR = 0xFF2A2A2A.toInt()
