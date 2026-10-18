package com.mobixournal.ui

import android.graphics.RectF
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.render.PlotCurve
import com.mobixournal.render.PlotElementFactory
import com.mobixournal.render.replacePlotElement
import com.mobixournal.ui.theme.FloatingSurface

/**
 * Pure positioning helper for the anchored function plot inspector.
 */
internal fun plotInspectorOffset(
    selectionLeft: Float?,
    selectionTop: Float?,
    selectionRight: Float?,
    selectionBottom: Float?,
    inspectorSize: IntSize,
    canvasSize: IntSize,
    gapPx: Int,
    marginPx: Int,
): IntOffset {
    if (selectionLeft == null || selectionRight == null || selectionTop == null || inspectorSize.width <= 0) {
        val cx = ((canvasSize.width - inspectorSize.width) / 2).coerceAtLeast(marginPx)
        val cy = ((canvasSize.height - inspectorSize.height) / 2).coerceAtLeast(marginPx)
        return IntOffset(cx, cy)
    }

    val preferredRightX = (selectionRight + gapPx).toInt()
    val fitsRight = preferredRightX + inspectorSize.width <= canvasSize.width - marginPx

    val preferredLeftX = (selectionLeft - gapPx - inspectorSize.width).toInt()
    val fitsLeft = preferredLeftX >= marginPx

    val x = when {
        fitsRight -> preferredRightX
        fitsLeft -> preferredLeftX
        else -> {
            val spaceRight = canvasSize.width - selectionRight
            val spaceLeft = selectionLeft
            if (spaceRight >= spaceLeft) {
                (canvasSize.width - inspectorSize.width - marginPx).coerceAtLeast(marginPx)
            } else {
                marginPx
            }
        }
    }

    val maxY = (canvasSize.height - inspectorSize.height - marginPx).coerceAtLeast(marginPx)
    val y = selectionTop.toInt().coerceIn(marginPx, maxY)

    return IntOffset(x, y)
}

/**
 * Mutable editing state backing the anchored plot inspector.
 */
internal class PlotInspectorState(
    var modeTab: Int,
    val curves: MutableList<CurveEditorState>,
    var paramX: String,
    var paramY: String,
    var xMinText: String,
    var xMaxText: String,
    var showCriticalPoints: Boolean,
    var includeDerivative: Boolean,
    var includeSummary: Boolean,
    val shade: PlotShadeState,
) {
    val xMin: Double? get() = xMinText.toDoubleOrNull()
    val xMax: Double? get() = xMaxText.toDoubleOrNull()
    val rangeOk: Boolean get() = xMin != null && xMax != null && xMax!! > xMin!!

    val activeCurves: List<PlotCurve>
        get() = resolveActiveCurves(modeTab, curves, paramX, paramY)

    val canApply: Boolean
        get() = rangeOk && shade.ok && activeCurves.any { it.isDrawable() }
}

@Composable
internal fun rememberPlotInspectorState(plot: PlotElement): PlotInspectorState {
    val isParametric = plot.curves.any { it.kind == PlotKind.PARAMETRIC }
    var modeTab by remember(plot) { mutableStateOf(if (isParametric) 1 else 0) }

    val curves = remember(plot) {
        mutableStateListOf<CurveEditorState>().apply {
            addAll(createInitialCurveList(isParametric, plot.curves.map { c ->
                PlotCurve(c.source, c.color, c.lineStyle, "f", c.kind, c.sourceY)
            }))
        }
    }

    val initialParam = plot.curves.firstOrNull { it.kind == PlotKind.PARAMETRIC }
    var paramX by remember(plot) { mutableStateOf(initialParam?.source ?: "cos(x)") }
    var paramY by remember(plot) { mutableStateOf(initialParam?.sourceY ?: "sin(x)") }

    var xMinText by remember(plot) { mutableStateOf(formatRangeNumber(plot.xMin)) }
    var xMaxText by remember(plot) { mutableStateOf(formatRangeNumber(plot.xMax)) }
    var showCriticalPoints by remember(plot) { mutableStateOf(plot.showCriticalPoints) }
    var includeDerivative by remember(plot) { mutableStateOf(plot.includeDerivative) }
    var includeSummary by remember(plot) { mutableStateOf(plot.summaryText != null) }

    val shade = remember(plot) {
        PlotShadeState(plot.shadeIntegral, plot.xMin, plot.xMax)
    }

    return remember(plot, modeTab, paramX, paramY, xMinText, xMaxText, showCriticalPoints, includeDerivative, includeSummary, shade) {
        PlotInspectorState(
            modeTab = modeTab,
            curves = curves,
            paramX = paramX,
            paramY = paramY,
            xMinText = xMinText,
            xMaxText = xMaxText,
            showCriticalPoints = showCriticalPoints,
            includeDerivative = includeDerivative,
            includeSummary = includeSummary,
            shade = shade,
        )
    }
}

/**
 * The anchored micro-inspector for an existing living plot on the canvas.
 */
@Composable
fun PlotInspectorAnchor(
    pane: PaneState,
    ui: EditorUiState,
    canvasSizePx: IntSize,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = { ui.editingPlot = null },
) {
    val plot = ui.editingPlot ?: return
    val surface = pane.surface
    val rect = pane.selectionRect

    var inspectorSize by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    val marginPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    val offset = plotInspectorOffset(
        selectionLeft = rect?.left,
        selectionTop = rect?.top,
        selectionRight = rect?.right,
        selectionBottom = rect?.bottom,
        inspectorSize = inspectorSize,
        canvasSize = canvasSizePx,
        gapPx = gapPx,
        marginPx = marginPx,
    )

    val state = rememberPlotInspectorState(plot)

    FloatingSurface(
        modifier = modifier
            .offset { offset }
            .onSizeChanged { inspectorSize = it }
            .widthIn(min = 320.dp, max = 360.dp)
            .padding(4.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlotInspectorHeader(onDismiss = onDismiss)

            PlotInspectorBody(state = state)

            PlotInspectorActions(
                canApply = state.canApply,
                onDismiss = onDismiss,
                onApply = {
                    val xMin = state.xMin ?: return@PlotInspectorActions
                    val xMax = state.xMax ?: return@PlotInspectorActions
                    val updated = PlotElementFactory.applyPlotEdit(
                        original = plot,
                        editedCurves = state.activeCurves,
                        xMin = xMin,
                        xMax = xMax,
                        showCriticalPoints = state.showCriticalPoints,
                        includeDerivative = state.includeDerivative,
                        includeSummary = state.includeSummary,
                        shadeIntegral = state.shade.value,
                    )
                    surface?.replacePlotElement(plot, updated)
                    ui.editingPlot = updated
                },
            )
        }
    }
}

@Composable
private fun PlotInspectorHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ShowChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text("Plot Inspector", style = MaterialTheme.typography.titleSmall)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PlotInspectorBody(state: PlotInspectorState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DialogInputsColumn(
            modifier = Modifier.weight(1f, fill = false),
            modeTab = state.modeTab,
            onModeTabChange = { state.modeTab = it },
            curves = state.curves,
            paramX = state.paramX,
            paramY = state.paramY,
            onParamXChange = { state.paramX = it },
            onParamYChange = { state.paramY = it },
            xMinText = state.xMinText,
            onXMinChange = { state.xMinText = it },
            xMaxText = state.xMaxText,
            onXMaxChange = { state.xMaxText = it },
            rangeOk = state.rangeOk,
            shade = state.shade,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        OptionsRow(
            showCriticalPoints = state.showCriticalPoints,
            onShowCriticalPointsChange = { state.showCriticalPoints = it },
            includeDerivative = state.includeDerivative,
            onIncludeDerivativeChange = { state.includeDerivative = it },
            includeSummary = state.includeSummary,
            onIncludeSummaryChange = { state.includeSummary = it },
        )
    }
}

@Composable
private fun PlotInspectorActions(
    canApply: Boolean,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismiss) {
            Text("Close")
        }
        Button(
            enabled = canApply,
            onClick = onApply,
        ) {
            Text("Apply")
        }
    }
}
