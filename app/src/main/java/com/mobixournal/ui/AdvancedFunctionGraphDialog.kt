package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.PlotKind
import com.mobixournal.render.CriticalPoint
import com.mobixournal.ui.theme.rememberToolbarColor
import com.mobixournal.render.FunctionAnalysis
import com.mobixournal.render.FunctionPlot
import com.mobixournal.render.PlotCurve

/**
 * Whether this curve is one the app could draw at all — the gate the Insert button uses.
 *
 * A parametric curve needs *both* of its expressions: `x(t)` alone is half a curve, and the plot the
 * element compiler would produce from it is nothing (it reports the pair as unparsed).
 */
private fun PlotCurve.isDrawable(): Boolean =
    FunctionPlot.compile(source) != null &&
        (kind != PlotKind.PARAMETRIC || sourceY?.let { FunctionPlot.compile(it) != null } == true)

private val PRESET_COLORS = listOf(
    XOPP_BLUE,
    XOPP_RED,
    XOPP_GREEN,
    XOPP_ORANGE,
    XOPP_MAGENTA,
    XOPP_BLACK,
)

/** State for a curve being edited in the dialog. */
class CurveEditorState(
    source: String = "sin(x)",
    color: Int = PRESET_COLORS[0],
    lineStyle: LineStyle = LineStyle.PLAIN,
) {
    var source by mutableStateOf(source)
    var color by mutableStateOf(color)
    var lineStyle by mutableStateOf(lineStyle)
}

/**
 * Format a number for range text fields, avoiding redundant trailing '.0'.
 */
internal fun formatRangeNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

/**
 * Advanced multi-function plotting & mathematical study dialog:
 * - Multi-function input with customizable color & line style per curve
 * - Cartesian and Parametric modes
 * - Automatic critical point detection (roots, extrema, intersections, asymptotes)
 * - Optional numerical derivative f'(x)
 * - Optional summary table text insertion
 * - Live real-time Canvas preview
 *
 * Can be invoked to create a new plot or to edit an existing one via the optional [initialCurves],
 * [initialXMin], [initialXMax], [initialShowCriticalPoints], [initialIncludeDerivative], and
 * [initialIncludeSummary] parameters.
 */
@Composable
fun AdvancedFunctionGraphDialog(
    initialCurves: List<PlotCurve>? = null,
    initialXMin: Double? = null,
    initialXMax: Double? = null,
    initialShowCriticalPoints: Boolean? = null,
    initialIncludeDerivative: Boolean? = null,
    initialIncludeSummary: Boolean? = null,
    onConfirm: (
        curves: List<PlotCurve>,
        xMin: Double,
        xMax: Double,
        showCriticalPoints: Boolean,
        includeDerivative: Boolean,
        includeSummary: Boolean,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val isParametricInitial = initialCurves?.any { it.kind == PlotKind.PARAMETRIC } == true
    var modeTab by remember { mutableStateOf(if (isParametricInitial) 1 else 0) } // 0: Cartesian f(x), 1: Parametric {x(t), y(t)}
    val curves = remember {
        mutableStateListOf<CurveEditorState>().apply {
            addAll(createInitialCurveList(isParametricInitial, initialCurves))
        }
    }

    // Parametric inputs
    val initialParam = initialCurves?.firstOrNull { it.kind == PlotKind.PARAMETRIC }
    var paramX by remember { mutableStateOf(initialParam?.source ?: "cos(x)") }
    var paramY by remember { mutableStateOf(initialParam?.sourceY ?: "sin(x)") }

    var xMinText by remember { mutableStateOf(initialXMin?.let { formatRangeNumber(it) } ?: "-10") }
    var xMaxText by remember { mutableStateOf(initialXMax?.let { formatRangeNumber(it) } ?: "10") }
    var showCriticalPoints by remember { mutableStateOf(initialShowCriticalPoints ?: true) }
    var includeDerivative by remember { mutableStateOf(initialIncludeDerivative ?: false) }
    var includeSummary by remember { mutableStateOf(initialIncludeSummary ?: false) }

    val xMin = xMinText.toDoubleOrNull()
    val xMax = xMaxText.toDoubleOrNull()
    val rangeOk = xMin != null && xMax != null && xMax > xMin

    val activeCurves = remember(modeTab, curves.map { Triple(it.source, it.color, it.lineStyle) }, paramX, paramY) {
        resolveActiveCurves(modeTab, curves, paramX, paramY)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .widthIn(max = 1000.dp)
            .padding(horizontal = 16.dp),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        title = { Text("Graph & Function Study", style = MaterialTheme.typography.titleMedium) },
        text = {
            DialogBodyContent(
                modeTab = modeTab,
                onModeTabChange = { modeTab = it },
                curves = curves,
                paramX = paramX,
                paramY = paramY,
                onParamXChange = { paramX = it },
                onParamYChange = { paramY = it },
                xMinText = xMinText,
                onXMinChange = { xMinText = it },
                xMaxText = xMaxText,
                onXMaxChange = { xMaxText = it },
                rangeOk = rangeOk,
                showCriticalPoints = showCriticalPoints,
                onShowCriticalPointsChange = { showCriticalPoints = it },
                includeDerivative = includeDerivative,
                onIncludeDerivativeChange = { includeDerivative = it },
                includeSummary = includeSummary,
                onIncludeSummaryChange = { includeSummary = it },
                activeCurves = activeCurves,
                xMin = xMin,
                xMax = xMax,
            )
        },
        confirmButton = {
            TextButton(
                enabled = rangeOk && activeCurves.any { it.isDrawable() },
                onClick = {
                    onConfirm(activeCurves, xMin!!, xMax!!, showCriticalPoints, includeDerivative, includeSummary)
                },
            ) { Text(if (initialCurves != null) "Apply" else "Insert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun createInitialCurveList(
    isParametricInitial: Boolean,
    initialCurves: List<PlotCurve>?,
): List<CurveEditorState> =
    if (!isParametricInitial && !initialCurves.isNullOrEmpty()) {
        initialCurves.map { CurveEditorState(it.source, it.color, it.lineStyle) }
    } else {
        listOf(CurveEditorState("sin(x)", PRESET_COLORS[0]))
    }

private fun resolveActiveCurves(
    modeTab: Int,
    curves: List<CurveEditorState>,
    paramX: String,
    paramY: String,
): List<PlotCurve> =
    if (modeTab == 0) {
        curves.mapIndexed { i, c -> PlotCurve(c.source, c.color, c.lineStyle, "f${i + 1}") }
    } else {
        listOf(
            PlotCurve(
                source = paramX,
                color = PRESET_COLORS[0],
                lineStyle = LineStyle.PLAIN,
                label = "x(t)",
                kind = PlotKind.PARAMETRIC,
                sourceY = paramY,
            ),
        )
    }

@Composable
private fun DialogBodyContent(
    modeTab: Int,
    onModeTabChange: (Int) -> Unit,
    curves: MutableList<CurveEditorState>,
    paramX: String,
    paramY: String,
    onParamXChange: (String) -> Unit,
    onParamYChange: (String) -> Unit,
    xMinText: String,
    onXMinChange: (String) -> Unit,
    xMaxText: String,
    onXMaxChange: (String) -> Unit,
    rangeOk: Boolean,
    showCriticalPoints: Boolean,
    onShowCriticalPointsChange: (Boolean) -> Unit,
    includeDerivative: Boolean,
    onIncludeDerivativeChange: (Boolean) -> Unit,
    includeSummary: Boolean,
    onIncludeSummaryChange: (Boolean) -> Unit,
    activeCurves: List<PlotCurve>,
    xMin: Double?,
    xMax: Double?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 370.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DialogInputsColumn(
            modifier = Modifier.weight(1f),
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
        )

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )

        DialogPreviewColumn(
            modifier = Modifier.weight(1f),
            rangeOk = rangeOk,
            xMin = xMin,
            xMax = xMax,
            activeCurves = activeCurves,
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
private fun DialogInputsColumn(
    modifier: Modifier = Modifier,
    modeTab: Int,
    onModeTabChange: (Int) -> Unit,
    curves: MutableList<CurveEditorState>,
    paramX: String,
    paramY: String,
    onParamXChange: (String) -> Unit,
    onParamYChange: (String) -> Unit,
    xMinText: String,
    onXMinChange: (String) -> Unit,
    xMaxText: String,
    onXMaxChange: (String) -> Unit,
    rangeOk: Boolean,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ModeSelectorPill(modeTab = modeTab, onModeTabChange = onModeTabChange)

        if (modeTab == 0) {
            CartesianCurveList(curves = curves)
        } else {
            ParametricInputs(
                paramX = paramX,
                paramY = paramY,
                onXChange = onParamXChange,
                onYChange = onParamYChange,
            )
        }

        RangeInputs(
            isCartesian = modeTab == 0,
            xMinText = xMinText,
            onXMinChange = onXMinChange,
            xMaxText = xMaxText,
            onXMaxChange = onXMaxChange,
            rangeOk = rangeOk,
        )
    }
}

@Composable
private fun ModeSelectorPill(modeTab: Int, onModeTabChange: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = rememberToolbarColor(),
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val cartesianSelected = modeTab == 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (cartesianSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                    )
                    .clickable { onModeTabChange(0) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Cartesian y=f(x)",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (cartesianSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }

            val parametricSelected = modeTab == 1
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (parametricSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                    )
                    .clickable { onModeTabChange(1) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Parametric {x, y}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (parametricSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun RangeInputs(
    isCartesian: Boolean,
    xMinText: String,
    onXMinChange: (String) -> Unit,
    xMaxText: String,
    onXMaxChange: (String) -> Unit,
    rangeOk: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = xMinText,
            onValueChange = onXMinChange,
            label = { Text(if (isCartesian) "x min" else "t min") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = xMaxText,
            onValueChange = onXMaxChange,
            label = { Text(if (isCartesian) "x max" else "t max") },
            singleLine = true,
            isError = !rangeOk,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CartesianCurveList(curves: MutableList<CurveEditorState>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        curves.forEachIndexed { index, curveState ->
            CurveRow(
                index = index,
                curve = curveState,
                canDelete = curves.size > 1,
                onDelete = { curves.removeAt(index) },
            )
        }
        if (curves.size < 5) {
            TextButton(
                onClick = {
                    val nextColor = PRESET_COLORS[curves.size % PRESET_COLORS.size]
                    curves.add(CurveEditorState("x", nextColor))
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(" Add function")
            }
        }
    }
}

@Composable
private fun CurveRow(
    index: Int,
    curve: CurveEditorState,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    val compiled = remember(curve.source) { FunctionPlot.compile(curve.source) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorCircle(color = curve.color, onSelect = { curve.color = it })
            OutlinedTextField(
                value = curve.source,
                onValueChange = { curve.source = it },
                label = { Text("f${index + 1}(x)") },
                singleLine = true,
                isError = compiled == null,
                modifier = Modifier.weight(1f),
            )
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove")
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = curve.lineStyle == LineStyle.PLAIN,
                onClick = { curve.lineStyle = LineStyle.PLAIN },
                label = { Text("Solid", style = MaterialTheme.typography.bodySmall) },
            )
            FilterChip(
                selected = curve.lineStyle == LineStyle.DASHED,
                onClick = { curve.lineStyle = LineStyle.DASHED },
                label = { Text("Dashed", style = MaterialTheme.typography.bodySmall) },
            )
            FilterChip(
                selected = curve.lineStyle == LineStyle.DOTTED,
                onClick = { curve.lineStyle = LineStyle.DOTTED },
                label = { Text("Dotted", style = MaterialTheme.typography.bodySmall) },
            )
        }
    }
}

@Composable
private fun ColorCircle(color: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable { expanded = !expanded },
        )
        if (expanded) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 2.dp)
                    .background(
                        rememberToolbarColor(),
                        RoundedCornerShape(14.dp),
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PRESET_COLORS.forEach { c ->
                    val isSelected = c == color
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .then(
                                if (isSelected) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                },
                            )
                            .clickable {
                                onSelect(c)
                                expanded = false
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun ParametricInputs(
    paramX: String,
    paramY: String,
    onXChange: (String) -> Unit,
    onYChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = paramX,
            onValueChange = onXChange,
            label = { Text("x(t)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = paramY,
            onValueChange = onYChange,
            label = { Text("y(t)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

