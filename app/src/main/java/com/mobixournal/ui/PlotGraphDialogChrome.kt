package com.mobixournal.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mobixournal.format.model.PlotShading
import com.mobixournal.render.PlotCurve
import com.mobixournal.ui.theme.FloatingDialog

/**
 * The graph dialog's **chrome**: the `AlertDialog`, the body it shows and the two buttons that close it.
 *
 * It is split out of `AdvancedFunctionGraphDialog` so that composable is a *state-and-call* function —
 * the remember blocks, the derived answers, and one call — which is what keeps the dialog readable as it
 * grows controls (the shaded integral area was the last one in). Everything here is presentation: the
 * answers it hands back are the ones it was given.
 */
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
        shadeIntegral: PlotShading?,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    // The floating material, not the opaque `surfaceContainer` plate this dialog used to be cut from:
    // the function editor is the one pop-up big enough to *see* the material in, and it was the last
    // opaque panel the STEM tools opened. [FloatingDialog] also gives it the family's rim and corner.
    FloatingDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .widthIn(max = 1000.dp)
            .padding(horizontal = 16.dp),
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
                activeCurves = activeCurves,
                xMin = xMin,
                xMax = xMax,
            )
        },
        confirmButton = {
            TextButton(
                enabled = rangeOk && shade.ok && activeCurves.any { it.isDrawable() },
                onClick = {
                    if (xMin == null || xMax == null) return@TextButton
                    onConfirm(
                        activeCurves,
                        xMin,
                        xMax,
                        showCriticalPoints,
                        includeDerivative,
                        includeSummary,
                        shade.value,
                    )
                },
            ) { Text(if (isEditing) "Apply" else "Insert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
