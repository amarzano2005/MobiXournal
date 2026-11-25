package com.mobixournal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pentagon
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.design.PenPalette
import com.mobixournal.format.XoppColor.withAlpha
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.render.ParametricShapes
import com.mobixournal.render.replaceShapeElement
import com.mobixournal.ui.theme.rememberClosingArgument
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Translucent fill alpha applied when picking a fill swatch (keeps grid lines readable). */
private const val FILL_ALPHA = 0x40

private enum class ShapeInspectorTab(val label: String) {
    GEOMETRY("Geometry"),
    STYLE("Style"),
}

/**
 * Hosts the anchored shape inspector beside whichever figure [EditorUiState.editingShape] names,
 * applying edits live to the canvas.
 */
@Composable
fun ShapeEditorHost(
    ui: EditorUiState,
    pane: PaneState,
    canvasSizePx: IntSize,
    modifier: Modifier = Modifier,
) {
    val shape = rememberClosingArgument(ui.editingShape) ?: return
    val surface = pane.surface
    val session = ui.shapeInspectorSession
    val onDismiss = { ui.editingShape = null }
    val keyboard = remember(session) {
        LivingKeyboardController { _, next -> next.filter { it.isDigit() || it == '.' }.take(7) }
    }
    var keyboardHeight by remember { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalLivingKeyboard provides keyboard) {
            LivingInspectorAnchor(
                sessionKey = session,
                anchorRect = pane.selectionRect,
                canvasSizePx = canvasSizePx,
                minWidth = 280.dp,
                maxWidth = 320.dp,
                keyboardHeightPx = if (keyboard.isOpen) keyboardHeight else 0,
                animateSurface = true,
            ) { dragModifier ->
                ShapeInspectorPanel(
                    shape = shape,
                    session = session,
                    dragModifier = dragModifier,
                    onApply = { updated ->
                        surface?.replaceShapeElement(shape, updated)
                        ui.editingShape = updated
                    },
                    onDismiss = onDismiss,
                )
            }
        }
        LivingInspectorKeyboardBar(
            controller = keyboard,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { keyboardHeight = it.height },
        )
    }
}

@Composable
private fun ShapeInspectorPanel(
    shape: ParametricShapeElement,
    session: Int,
    dragModifier: Modifier,
    onApply: (ParametricShapeElement) -> Unit,
    onDismiss: () -> Unit,
) {
    val keyboard = LocalLivingKeyboard.current
    var tab by remember(session) { mutableStateOf(ShapeInspectorTab.GEOMETRY) }
    var selectedIndex by remember(session) { mutableIntStateOf(0) }
    var editVersion by remember(session) { mutableIntStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, (shape.vertices.size - 1).coerceAtLeast(0))

    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShapeInspectorHeader(
            shapeKind = shape.shape,
            onDismiss = onDismiss,
            modifier = dragModifier,
        )
        ShapeTabRow(selected = tab, onSelect = {
            tab = it
            keyboard?.close()
        })
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f))

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                ShapeInspectorTab.GEOMETRY -> ShapeGeometryTab(
                    shape = shape,
                    selectedIndex = safeIndex,
                    editVersion = editVersion,
                    activeField = keyboard?.activeFieldId,
                    onSelectIndex = {
                        selectedIndex = it
                        keyboard?.close()
                    },
                    onFocusField = { _ -> },
                    onUpdate = { next ->
                        editVersion++
                        onApply(next)
                    },
                )
                ShapeInspectorTab.STYLE -> ShapeStyleTab(
                    shape = shape,
                    editVersion = editVersion,
                    activeField = keyboard?.activeFieldId,
                    onFocusField = { _ -> },
                    onUpdate = { next ->
                        editVersion++
                        onApply(next)
                    },
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onDismiss) { Text("Done") }
        }
    }
}

@Composable
private fun ShapeInspectorHeader(
    shapeKind: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prettyKind = shapeKind.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = modifier.weight(1f),
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
                Icons.Outlined.Pentagon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(prettyKind, style = MaterialTheme.typography.titleSmall)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ShapeTabRow(
    selected: ShapeInspectorTab,
    onSelect: (ShapeInspectorTab) -> Unit,
) {
    InspectorTabBar(
        labels = ShapeInspectorTab.entries.map { it.label },
        selectedIndex = selected.ordinal,
        onSelect = { idx -> onSelect(ShapeInspectorTab.entries[idx]) },
    )
}

@Composable
private fun ShapeGeometryTab(
    shape: ParametricShapeElement,
    selectedIndex: Int,
    editVersion: Int,
    activeField: String?,
    onSelectIndex: (Int) -> Unit,
    onFocusField: (String?) -> Unit,
    onUpdate: (ParametricShapeElement) -> Unit,
) {
    if (shape.vertices.size >= 3) {
        ShapeMiniDiagram(
            vertices = shape.vertices,
            selectedIndex = selectedIndex,
            onSelectVertex = onSelectIndex,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (index in shape.vertices.indices) {
                InspectorChip(
                    label = vertexLabel(index),
                    selected = index == selectedIndex,
                    onClick = { onSelectIndex(index) },
                )
            }
        }

        val sidePt = ParametricShapes.sideLengthPt(shape, selectedIndex)
        val angleDeg = ParametricShapes.interiorAngleDeg(shape, selectedIndex)
        val edgeName = edgeLabel(selectedIndex, shape.vertices.size)
        val angleName = angleLabel(selectedIndex)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShapeNumberInput(
                fieldId = "edge_$selectedIndex",
                label = "Edge $edgeName",
                suffix = "pt",
                value = sidePt,
                syncKey = editVersion * 31 + selectedIndex,
                isEditing = activeField == "edge_$selectedIndex",
                onActivate = { onFocusField("edge_$selectedIndex") },
                onCommit = { length ->
                    if (length > 0.5) onUpdate(ParametricShapes.setSideLength(shape, selectedIndex, length))
                },
                modifier = Modifier.weight(1f),
            )
            ShapeNumberInput(
                fieldId = "angle_$selectedIndex",
                label = "Angle $angleName",
                suffix = "°",
                value = angleDeg,
                syncKey = editVersion * 31 + selectedIndex,
                isEditing = activeField == "angle_$selectedIndex",
                onActivate = { onFocusField("angle_$selectedIndex") },
                onCommit = { deg ->
                    if (deg in 1.0..179.0) onUpdate(ParametricShapes.setInteriorAngle(shape, selectedIndex, deg))
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Text("On-page labels", style = MaterialTheme.typography.labelMedium)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InspectorChip(
            label = "Lengths",
            selected = shape.showSideLengths,
            onClick = { onUpdate(shape.copy(showSideLengths = !shape.showSideLengths)) },
            modifier = Modifier.weight(1f),
        )
        InspectorChip(
            label = "Angles",
            selected = shape.showAngles,
            onClick = { onUpdate(shape.copy(showAngles = !shape.showAngles)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ShapeMiniDiagram(
    vertices: List<ShapeVertex>,
    selectedIndex: Int,
    onSelectVertex: (Int) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .pointerInput(vertices) {
                detectTapGestures { tap ->
                    val pts = normalizePolygonForPreview(
                        vertices = vertices,
                        width = size.width.toFloat(),
                        height = size.height.toFloat(),
                        padding = 22.dp.toPx(),
                    )
                    val nearest = pts.indices.minByOrNull { i ->
                        hypot((pts[i].x - tap.x).toDouble(), (pts[i].y - tap.y).toDouble())
                    }
                    if (nearest != null) onSelectVertex(nearest)
                }
            },
    ) {
        val pts = normalizePolygonForPreview(
            vertices = vertices,
            width = size.width,
            height = size.height,
            padding = 22.dp.toPx(),
        )
        if (pts.size < 3) return@Canvas

        val outlinePath = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            close()
        }
        drawPath(
            path = outlinePath,
            color = scheme.onSurface.copy(alpha = 0.55f),
            style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round),
        )

        // Highlight the active edge (from selectedIndex to next vertex).
        val nextIndex = (selectedIndex + 1) % pts.size
        drawLine(
            color = scheme.primary,
            start = pts[selectedIndex],
            end = pts[nextIndex],
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Draw vertex dots and A, B, C... labels.
        val cx = pts.map { it.x }.average().toFloat()
        val cy = pts.map { it.y }.average().toFloat()
        for (i in pts.indices) {
            val pt = pts[i]
            val isSelected = i == selectedIndex
            drawCircle(
                color = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                radius = if (isSelected) 4.5.dp.toPx() else 2.5.dp.toPx(),
                center = pt,
            )
            val dx = pt.x - cx
            val dy = pt.y - cy
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
            val layout = measurer.measure(vertexLabel(i), style = labelStyle)
            val push = 12.dp.toPx()
            val tx = (pt.x + (dx / dist) * push - layout.size.width / 2f)
                .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            val ty = (pt.y + (dy / dist) * push - layout.size.height / 2f)
                .coerceIn(0f, (size.height - layout.size.height).coerceAtLeast(0f))
            drawText(
                textLayoutResult = layout,
                color = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                topLeft = Offset(tx, ty),
            )
        }
    }
}

@Composable
private fun ShapeStyleTab(
    shape: ParametricShapeElement,
    editVersion: Int,
    activeField: String?,
    onFocusField: (String?) -> Unit,
    onUpdate: (ParametricShapeElement) -> Unit,
) {
    ShapeNumberInput(
        fieldId = "stroke_width",
        label = "Outline width",
        suffix = "pt",
        value = shape.strokeWidth,
        syncKey = editVersion,
        isEditing = activeField == "stroke_width",
        onActivate = { onFocusField("stroke_width") },
        onCommit = { w ->
            if (w in 0.5..30.0) onUpdate(shape.copy(strokeWidth = w))
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Outline colour", style = MaterialTheme.typography.labelMedium)
    ShapeColorStrip(
        selectedOpaqueRgb = shape.strokeColor and 0x00FFFFFF,
        hasSelection = true,
        onPick = { color -> onUpdate(shape.copy(strokeColor = color)) },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Fill colour", style = MaterialTheme.typography.labelMedium)
        InspectorChip(
            label = "None",
            selected = shape.fillColor == null,
            onClick = { onUpdate(shape.copy(fillColor = null)) },
        )
    }
    ShapeColorStrip(
        selectedOpaqueRgb = (shape.fillColor ?: 0) and 0x00FFFFFF,
        hasSelection = shape.fillColor != null,
        onPick = { color -> onUpdate(shape.copy(fillColor = color.withAlpha(FILL_ALPHA))) },
    )
}

@Composable
private fun ShapeColorStrip(
    selectedOpaqueRgb: Int,
    hasSelection: Boolean,
    onPick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (swatch in PenPalette.COLORS) {
            val selected = hasSelection && ((swatch and 0x00FFFFFF) == selectedOpaqueRgb)
            ColorSwatch(
                color = swatch,
                selected = selected,
                onClick = { onPick(swatch) },
                compact = true,
            )
        }
        Spacer(Modifier.width(2.dp))
    }
}

@Composable
private fun ShapeNumberInput(
    fieldId: String,
    label: String,
    suffix: String,
    value: Double,
    syncKey: Int,
    isEditing: Boolean,
    onActivate: () -> Unit,
    onCommit: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalLivingKeyboard.current
    val active = keyboard?.activeFieldId == fieldId
    val formatted = formatMeasure(value)

    LivingDisplayField(
        label = label,
        value = if (active) keyboard?.field?.text.orEmpty() else formatted,
        active = active,
        caretAt = if (active) keyboard?.field?.selection?.min else null,
        suffix = suffix,
        onClick = {
            onActivate()
            keyboard?.open(
                fieldId = fieldId,
                kind = LivingKeyboardKind.NUMERIC,
                initialText = formatted,
                allowNegative = false,
                label = label,
            ) { typed ->
                if (typed.isNotEmpty() && !typed.endsWith(".")) {
                    typed.toDoubleOrNull()?.let(onCommit)
                }
            }
        },
        modifier = modifier,
    )
}

/** Vertex label (`0 -> "A"`, `1 -> "B"`, ...). */
internal fun vertexLabel(index: Int): String = ('A' + index.coerceAtLeast(0)).toString()

/** Edge label between [index] and the next vertex (`0` of `3` -> `"A-B"`, `2` of `3` -> `"C-A"`). */
internal fun edgeLabel(index: Int, vertexCount: Int): String {
    if (vertexCount <= 0) return ""
    val i = index.mod(vertexCount)
    val next = (i + 1) % vertexCount
    return "${vertexLabel(i)}-${vertexLabel(next)}"
}

/** Interior angle label (`0 -> "∠A"`). */
internal fun angleLabel(index: Int): String = "∠${vertexLabel(index)}"

/**
 * Maps [vertices] into `[padding .. width - padding] x [padding .. height - padding]` while
 * preserving aspect ratio and centering the polygon.
 */
internal fun normalizePolygonForPreview(
    vertices: List<ShapeVertex>,
    width: Float,
    height: Float,
    padding: Float,
): List<Offset> {
    if (vertices.isEmpty()) return emptyList()
    val minX = vertices.minOf { it.x }
    val maxX = vertices.maxOf { it.x }
    val minY = vertices.minOf { it.y }
    val maxY = vertices.maxOf { it.y }
    val spanX = (maxX - minX).coerceAtLeast(1e-3)
    val spanY = (maxY - minY).coerceAtLeast(1e-3)
    val availW = (width - 2f * padding).coerceAtLeast(1f)
    val availH = (height - 2f * padding).coerceAtLeast(1f)
    val scale = minOf(availW / spanX.toFloat(), availH / spanY.toFloat())
    val offsetX = padding + (availW - spanX.toFloat() * scale) / 2f
    val offsetY = padding + (availH - spanY.toFloat() * scale) / 2f
    return vertices.map { v ->
        Offset(
            x = offsetX + (v.x - minX).toFloat() * scale,
            y = offsetY + (v.y - minY).toFloat() * scale,
        )
    }
}

private fun formatMeasure(value: Double): String {
    val tenth = (value * 10.0).roundToInt() / 10.0
    return if (tenth == tenth.toLong().toDouble()) tenth.toLong().toString() else tenth.toString()
}
