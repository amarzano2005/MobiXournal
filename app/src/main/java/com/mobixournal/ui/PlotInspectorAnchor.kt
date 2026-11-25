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
import androidx.compose.runtime.mutableIntStateOf
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
import com.mobixournal.format.model.PlotInfoSide
import com.mobixournal.format.model.PlotKind
import com.mobixournal.render.PlotCurve
import com.mobixournal.render.PlotElementFactory
import com.mobixournal.render.replacePlotElement
import com.mobixournal.ui.theme.FloatingSurface

/**
 * Pure positioning helper for the anchored function plot inspector (delegates to
 * [anchoredInspectorOffset]).
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
): IntOffset = anchoredInspectorOffset(
    selectionLeft = selectionLeft,
    selectionTop = selectionTop,
    selectionRight = selectionRight,
    selectionBottom = selectionBottom,
    inspectorSize = inspectorSize,
    canvasSize = canvasSize,
    gapPx = gapPx,
    marginPx = marginPx,
)

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
    initialInfoSide: PlotInfoSide = PlotInfoSide.BELOW,
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
    var infoSide by mutableStateOf(initialInfoSide)

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
            initialInfoSide = plot.infoSide,
            shade = shade,
        )
    }
}

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

    val state = rememberPlotInspectorState(plot, ui.plotInspectorSession)
    val keyboard = remember(ui.plotInspectorSession) { LivingKeyboardController() }
    var keyboardHeight by remember { mutableIntStateOf(0) }

    // The inspector keeps its own offset inside a full-size layer so the docked in-app keyboard
    // can sit at the canvas' bottom edge while the inspector stays anchored above it.
    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalLivingKeyboard provides keyboard) {
            LivingInspectorAnchor(
                sessionKey = ui.plotInspectorSession,
                anchorRect = pane.selectionRect,
                canvasSizePx = canvasSizePx,
                minWidth = 320.dp,
                maxWidth = 360.dp,
                keyboardHeightPx = if (keyboard.isOpen) keyboardHeight else 0,
            ) { dragModifier ->
                PlotInspectorPanel(
                    state = state,
                    dragModifier = dragModifier,
                    onDismiss = onDismiss,
                    onApply = {
                        val xMin = state.xMin ?: return@PlotInspectorPanel
                        val xMax = state.xMax ?: return@PlotInspectorPanel
                        val updated = PlotElementFactory.applyPlotEdit(
                            original = plot,
                            editedCurves = state.activeCurves,
                            xMin = xMin,
                            xMax = xMax,
                            showCriticalPoints = state.showCriticalPoints,
                            includeDerivative = state.includeDerivative,
                            includeSummary = state.includeSummary,
                            infoSide = state.infoSide,
                            shadeIntegral = state.shade.value,
                        )
                        surface?.replacePlotElement(plot, updated)
                        ui.editingPlot = updated
                    },
                )
            }
        }

        if (keyboard.isOpen) {
            LivingInspectorKeyboardBar(
                controller = keyboard,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { keyboardHeight = it.height },
            )
        }
    }
}

/**
 * The panel's own content: its draggable header, the body, and the two commands that close it.
 *
 * The body takes the height the header and the buttons leave; inside it the report-side options are
 * measured before the inputs, so the chips are never the row that gets squeezed — the inputs scroll
 * instead, and the panel never grows past the canvas it floats in.
 */
@Composable
private fun PlotInspectorPanel(
    state: PlotInspectorState,
    dragModifier: Modifier,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlotInspectorHeader(
            onDismiss = onDismiss,
            modifier = dragModifier,
        )

        PlotInspectorBody(
            state = state,
            modifier = Modifier.weight(1f, fill = false),
        )

        PlotInspectorActions(canApply = state.canApply, onDismiss = onDismiss, onApply = onApply)
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
private fun PlotInspectorBody(state: PlotInspectorState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // A comfortable ceiling on a tall canvas; the weight above is what bounds it on a short
            // one, so this is never the constraint that clips the chips.
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
            infoSide = state.infoSide,
            onInfoSideChange = { state.infoSide = it },
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
