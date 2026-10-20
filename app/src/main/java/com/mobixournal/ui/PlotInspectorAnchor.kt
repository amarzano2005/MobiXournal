package com.mobixournal.ui

import android.graphics.RectF
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
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
 * Where a **freely dragged** inspector goes: [start] moved by [delta], brought back inside the canvas.
 *
 * A drag works on the panel's **absolute** position, never on a delta re-measured from the anchor
 * every frame. That distinction is what made the old inspector feel broken: a delta added to an
 * anchor that was itself already clamped at an edge reproduced the same clamped position, so the
 * panel "wouldn't move"; and every re-anchor — an Apply replacing the element with its edited
 * copy, the panel growing a curve row, the viewport resizing — pulled it back out from under the
 * hand. Once a drag has taken the position, the position is the panel's own.
 */
internal fun draggedInspectorOffset(
    start: IntOffset,
    delta: Offset,
    inspectorSize: IntSize,
    canvasSize: IntSize,
): IntOffset {
    val maxX = (canvasSize.width - inspectorSize.width).coerceAtLeast(0)
    val maxY = (canvasSize.height - inspectorSize.height).coerceAtLeast(0)
    return IntOffset(
        (start.x + delta.x.roundToInt()).coerceIn(0, maxX),
        (start.y + delta.y.roundToInt()).coerceIn(0, maxY),
    )
}

/**
 * Mutable editing state backing the anchored plot inspector.
 */
internal class PlotInspectorState(
    initialModeTab: Int,
    val curves: MutableList<CurveEditorState>,
    initialParamX: String,
    initialParamY: String,
    initialXMinText: String,
    initialXMaxText: String,
    initialShowCriticalPoints: Boolean,
    initialIncludeDerivative: Boolean,
    initialIncludeSummary: Boolean,
    val shade: PlotShadeState,
) {
    var modeTab by mutableStateOf(initialModeTab)
    var paramX by mutableStateOf(initialParamX)
    var paramY by mutableStateOf(initialParamY)
    var xMinText by mutableStateOf(initialXMinText)
    var xMaxText by mutableStateOf(initialXMaxText)
    var showCriticalPoints by mutableStateOf(initialShowCriticalPoints)
    var includeDerivative by mutableStateOf(initialIncludeDerivative)
    var includeSummary by mutableStateOf(initialIncludeSummary)

    val xMin: Double? get() = xMinText.toDoubleOrNull()
    val xMax: Double? get() = xMaxText.toDoubleOrNull()
    val rangeOk: Boolean get() = xMin != null && xMax != null && xMax!! > xMin!!

    val activeCurves: List<PlotCurve>
        get() = resolveActiveCurves(modeTab, curves, paramX, paramY)

    val canApply: Boolean
        get() = rangeOk && shade.ok && activeCurves.any { it.isDrawable() }
}

@Composable
internal fun rememberPlotInspectorState(plot: PlotElement, session: Int = 0): PlotInspectorState {
    val isParametric = plot.curves.any { it.kind == PlotKind.PARAMETRIC }
    val initialParam = plot.curves.firstOrNull { it.kind == PlotKind.PARAMETRIC }
    val shade = remember(session) {
        PlotShadeState(plot.shadeIntegral, plot.xMin, plot.xMax)
    }

    return remember(session) {
        val curves = mutableStateListOf<CurveEditorState>().apply {
            addAll(createInitialCurveList(isParametric, plot.curves.map { c ->
                PlotCurve(c.source, c.color, c.lineStyle, "f", c.kind, c.sourceY)
            }))
        }
        PlotInspectorState(
            initialModeTab = if (isParametric) 1 else 0,
            curves = curves,
            initialParamX = initialParam?.source ?: "cos(x)",
            initialParamY = initialParam?.sourceY ?: "sin(x)",
            initialXMinText = formatRangeNumber(plot.xMin),
            initialXMaxText = formatRangeNumber(plot.xMax),
            initialShowCriticalPoints = plot.showCriticalPoints,
            initialIncludeDerivative = plot.includeDerivative,
            initialIncludeSummary = plot.summaryText != null,
            shade = shade,
        )
    }
}

/**
 * Where the inspector sits and how it is moved: the panel's own offset (null until it has been
 * measured, which is also when it starts being drawn) plus the two callbacks that own it.
 */
private class InspectorPlacement(
    val offset: IntOffset?,
    /** Hand to the surface: the first size it reports becomes the placement's basis. */
    val measured: (IntSize) -> Unit,
    /** Hand to the drag handle: an absolute move of the panel, bounded by the canvas. */
    val dragBy: (Offset) -> Unit,
)

/**
 * The inspector's placement, taken **once per open** ([session]) and then owned by the panel.
 *
 * The position is settled on the first measured frame — beside [anchorRect], or centred when the
 * editor was opened without a selection — and after that only a drag changes it. Re-deriving it from
 * the anchor every frame is exactly what made the panel unusable before: an Apply replaces the
 * element with its edited copy, the surface grows a curve row, the viewport resizes, and each of
 * those recomputed the anchor and teleported the panel out from under the finger.
 */
@Composable
private fun rememberInspectorPlacement(
    session: Int,
    anchorRect: RectF?,
    canvasSizePx: IntSize,
    gapPx: Int,
    marginPx: Int,
): InspectorPlacement {
    var inspectorSize by remember(session) { mutableStateOf(IntSize.Zero) }
    var placement by remember(session) { mutableStateOf<IntOffset?>(null) }

    return InspectorPlacement(
        offset = placement,
        measured = { size ->
            inspectorSize = size
            if (placement == null) {
                placement = plotInspectorOffset(
                    selectionLeft = anchorRect?.left,
                    selectionTop = anchorRect?.top,
                    selectionRight = anchorRect?.right,
                    selectionBottom = anchorRect?.bottom,
                    inspectorSize = size,
                    canvasSize = canvasSizePx,
                    gapPx = gapPx,
                    marginPx = marginPx,
                )
            }
        },
        dragBy = { delta ->
            placement = draggedInspectorOffset(
                start = placement ?: IntOffset.Zero,
                delta = delta,
                inspectorSize = inspectorSize,
                canvasSize = canvasSizePx,
            )
        },
    )
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

    val gapPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    val marginPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    // The anchor is read **once per open**: the panel belongs beside the plot the editor was opened on,
    // not wherever the selection happens to have wandered since.
    val anchorRect = remember(ui.plotInspectorSession) { rect }
    val placement = rememberInspectorPlacement(
        session = ui.plotInspectorSession,
        anchorRect = anchorRect,
        canvasSizePx = canvasSizePx,
        gapPx = gapPx,
        marginPx = marginPx,
    )

    val state = rememberPlotInspectorState(plot, ui.plotInspectorSession)
    val echo = remember(ui.plotInspectorSession) { PlotInputEchoState() }

    // The inspector keeps its own offset inside a full-size layer so the echo cell can sit at the
    // canvas' bottom edge while the inspector stays anchored beside the plot.
    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalPlotInputEcho provides echo) {
            FloatingSurface(
                modifier = Modifier
                    // Drawn only once the panel knows where it goes, so it never flashes in the
                    // middle of the canvas and then hops to its anchor.
                    .alpha(if (placement.offset == null) 0f else 1f)
                    .offset { placement.offset ?: IntOffset.Zero }
                    .onSizeChanged(placement.measured)
                    .widthIn(min = 320.dp, max = 360.dp)
                    .padding(4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlotInspectorHeader(
                        onDismiss = onDismiss,
                        modifier = Modifier.pointerInput(canvasSizePx) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                placement.dragBy(dragAmount)
                            }
                        },
                    )

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

        PlotInputEchoBar(
            state = echo,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PlotInspectorHeader(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = "Drag to move",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
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
