package com.mobixournal.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mobixournal.format.model.PlotInfoSide
import com.mobixournal.format.model.PlotShading
import com.mobixournal.render.PlotCurve
import com.mobixournal.ui.theme.FloatingDialog

/** A screen narrower than this is a phone, where the dialog takes nearly the whole width. */
private const val NARROW_DIALOG_WIDTH_DP = 600

/**
 * How wide and how tall the dialog's body is on a screen [screenWidthDp] wide.
 *
 * A phone is narrow enough that two side-by-side panes are cramped and the body had to be scrolled
 * before the report-side chips appeared under the preview. There the dialog takes nearly the whole
 * width and a taller body, so the four sides are on screen at once; a window shorter than that
 * budget still clamps it, and the panes keep their own scroll.
 */
private fun plotDialogMetrics(screenWidthDp: Int): Pair<Float, Dp> =
    if (screenWidthDp < NARROW_DIALOG_WIDTH_DP) 0.96f to 560.dp else 0.82f to 420.dp

/** Whether the dialog has an answer it may hand back: a range, a shade and a curve that draws. */
internal fun canConfirmPlot(rangeOk: Boolean, shade: PlotShadeState, curves: List<PlotCurve>): Boolean =
    rangeOk && shade.ok && curves.any { it.isDrawable() }

/**
 * The graph dialog's **chrome**: the `AlertDialog`, the body it shows and the two buttons that close it.
 *
 * It is split out of `AdvancedFunctionGraphDialog` so that composable is a *state-and-call* function —
 * the remember blocks, the derived answers, and one call — which is what keeps the dialog readable as it
 * grows controls (the shaded integral area was the last one in). Everything here is presentation: the
 * answers it hands back are the ones it was given.
 */
// The floating material, not the opaque `surfaceContainer` plate this dialog used to be cut from: the
// function editor is the one pop-up big enough to *see* the material in, and it was the last opaque
// panel the STEM tools opened. [FloatingDialog] also gives it the family's rim and corner.
@Composable
internal fun PlotGraphDialog(
    isEditing: Boolean,
    modeTab: Int,
    onModeTabChange: (Int) -> Unit,
    curves: MutableList<CurveEditorState>,
    paramX: String,
    onParamXChange: (String) -> Unit,
    paramY: String,
    onParamYChange: (String) -> Unit,
    xMinText: String,
    onXMinChange: (String) -> Unit,
    xMaxText: String,
    onXMaxChange: (String) -> Unit,
    rangeOk: Boolean,
    shade: PlotShadeState,
    showCriticalPoints: Boolean,
    onShowCriticalPointsChange: (Boolean) -> Unit,
    includeDerivative: Boolean,
    onIncludeDerivativeChange: (Boolean) -> Unit,
    includeSummary: Boolean,
    onIncludeSummaryChange: (Boolean) -> Unit,
    infoSide: PlotInfoSide,
    onInfoSideChange: (PlotInfoSide) -> Unit,
    activeCurves: List<PlotCurve>,
    xMin: Double?,
    xMax: Double?,
    onConfirm: (
        curves: List<PlotCurve>,
        xMin: Double,
        xMax: Double,
        showCriticalPoints: Boolean,
        includeDerivative: Boolean,
        includeSummary: Boolean,
        infoSide: PlotInfoSide,
        shadeIntegral: PlotShading?,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    // A phone is narrow enough that two side-by-side panes are cramped and the body had to be
    // scrolled before the report-side chips appeared under the preview. There the dialog takes
    // nearly the whole width and a taller body, so the four sides are on screen at once; a window
    // shorter than that budget still clamps it, and the panes keep their own scroll.
    val (widthFraction, bodyHeight) = plotDialogMetrics(LocalConfiguration.current.screenWidthDp)
    FloatingDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(widthFraction).widthIn(max = 1000.dp).padding(horizontal = 16.dp),
        title = { Text("Graph & Function Study", style = MaterialTheme.typography.titleMedium) },
        text = {
            DialogBodyContent(
                modeTab = modeTab,
                onModeTabChange = onModeTabChange,
                curves = curves,
                paramX = paramX,
                paramY = paramY,
                onParamXChange = onParamXChange,
                onParamYChange = onParamYChange,
                xMinText = xMinText,
                onXMinChange = onXMinChange,
                xMaxText = xMaxText,
                onXMaxChange = onXMaxChange,
                rangeOk = rangeOk,
                shade = shade,
                showCriticalPoints = showCriticalPoints,
                onShowCriticalPointsChange = onShowCriticalPointsChange,
                includeDerivative = includeDerivative,
                onIncludeDerivativeChange = onIncludeDerivativeChange,
                includeSummary = includeSummary,
                onIncludeSummaryChange = onIncludeSummaryChange,
                infoSide = infoSide,
                onInfoSideChange = onInfoSideChange,
                activeCurves = activeCurves,
                xMin = xMin,
                xMax = xMax,
                maxBodyHeight = bodyHeight,
            )
        },
        confirmButton = {
            PlotConfirmButton(
                enabled = canConfirmPlot(rangeOk, shade, activeCurves),
                label = if (isEditing) "Apply" else "Insert",
                onClick = {
                    val minX = xMin
                    val maxX = xMax
                    if (minX != null && maxX != null) {
                        onConfirm(
                            activeCurves, minX, maxX, showCriticalPoints,
                            includeDerivative, includeSummary, infoSide, shade.value,
                        )
                    }
                },
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The dialog's one committing command; its label says whether it inserts a plot or rewrites one. */
@Composable
private fun PlotConfirmButton(enabled: Boolean, label: String, onClick: () -> Unit) {
    TextButton(enabled = enabled, onClick = onClick) { Text(label) }
}
