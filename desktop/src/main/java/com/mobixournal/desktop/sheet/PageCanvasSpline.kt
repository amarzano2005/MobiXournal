package com.mobixournal.desktop.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.pagePointOf
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.tools.DesktopButton
import com.mobixournal.desktop.tools.DesktopButtonVariant
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.SplineElement
import com.mobixournal.format.model.SplineNode
import com.mobixournal.render.ElementBounds
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionRegion
import com.mobixournal.render.SelectionTester
import com.mobixournal.render.SplineInk
import java.util.WeakHashMap
import kotlin.math.hypot

internal class DesktopSplineDraft {
    var active: Boolean by mutableStateOf(false)
    var pageIndex: Int by mutableStateOf(0)
    val nodes = mutableStateListOf<SplineNode>()
    var closed: Boolean by mutableStateOf(false)

    fun reset() {
        active = false
        nodes.clear()
        closed = false
    }
}

private val splineDrafts = WeakHashMap<DesktopTab, DesktopSplineDraft>()

internal fun splineDraftFor(tab: DesktopTab): DesktopSplineDraft =
    splineDrafts.getOrPut(tab) { DesktopSplineDraft() }

internal suspend fun AwaitPointerEventScope.splinePress(
    down: PointerInputChange,
    tool: DesktopTool,
    tab: DesktopTab,
    shapeInk: DesktopInk,
    dragView: DragView,
    density: Float,
    onEdited: (Int) -> Unit,
): Boolean {
    if (tool != DesktopTool.SPLINE) return false
    val (ptX, ptY) = pagePointOf(down.position.x, down.position.y, density, dragView.current())
    val draft = splineDraftFor(tab)

    if (!draft.active) {
        val page = runCatching { tab.document.page(tab.page) }.getOrNull()
        val existing = page?.let { SelectionTester.pickTopmost(it, ptX, ptY) { el -> el is SplineElement } }
        if (existing != null) {
            val el = page.layers.getOrNull(existing.layerIndex)?.elements?.getOrNull(existing.elementIndex)
            if (el != null) {
                val b = ElementBounds.of(el)
                tab.selection = PageSelection(SelectionRegion.Rect(b), setOf(existing))
            }
            return true
        }
        draft.active = true
        draft.pageIndex = tab.page
        draft.nodes.clear()
        draft.closed = false
    } else if (draft.pageIndex != tab.page) {
        if (draft.nodes.size >= 2) confirmDesktopSplineDraft(tab, shapeInk, onEdited) else draft.reset()
        draft.active = true
        draft.pageIndex = tab.page
        draft.nodes.clear()
        draft.closed = false
    }

    draft.nodes.add(SplineNode(ptX, ptY))
    val lastIdx = draft.nodes.lastIndex
    val startPos = down.position
    var draggingHandles = false
    down.consume()

    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
        if (change.changedToUpIgnoreConsumed()) {
            change.consume()
            break
        }
        val dist = hypot((change.position.x - startPos.x).toDouble(), (change.position.y - startPos.y).toDouble())
        if (!draggingHandles && dist >= 8.0) draggingHandles = true
        if (draggingHandles) {
            val (hx, hy) = pagePointOf(change.position.x, change.position.y, density, dragView.current())
            val node = draft.nodes[lastIdx]
            draft.nodes[lastIdx] = node.copy(
                outX = hx,
                outY = hy,
                inX = 2.0 * node.x - hx,
                inY = 2.0 * node.y - hy,
            )
        }
        change.consume()
    }
    return true
}

internal fun confirmDesktopSplineDraft(
    tab: DesktopTab,
    shapeInk: DesktopInk,
    onEdited: (Int) -> Unit,
) {
    val draft = splineDraftFor(tab)
    if (!draft.active) return
    val nodes = draft.nodes.toList()
    val closed = draft.closed
    val pageIdx = draft.pageIndex
    draft.reset()
    if (nodes.size < 2) return
    val spline = SplineElement(
        nodes = nodes,
        closed = closed,
        color = shapeInk.colour,
        width = shapeInk.width.toDouble(),
        lineStyle = shapeInk.lineStyle,
        id = ElementId.mint(ElementId.SPLINE),
    )
    var addedRef: ElementRef? = null
    tab.document.editPage(pageIdx) { page ->
        val layerIdx = page.layers.lastIndex.coerceAtLeast(0)
        val layer = page.layers.getOrNull(layerIdx) ?: return@editPage page
        val newLayer = layer.copy(elements = layer.elements + spline)
        addedRef = ElementRef(layerIdx, newLayer.elements.lastIndex)
        page.copy(layers = page.layers.toMutableList().also { it[layerIdx] = newLayer })
    }
    addedRef?.let { ref ->
        val b = ElementBounds.of(spline)
        tab.selection = PageSelection(SelectionRegion.Rect(b), setOf(ref))
    }
    onEdited(pageIdx)
}

internal fun DrawScope.drawDesktopSplineDraft(
    tab: DesktopTab,
    viewport: Viewport,
    density: Float,
    shapeInk: DesktopInk,
) {
    val draft = splineDraftFor(tab)
    if (!draft.active || draft.pageIndex != tab.page || draft.nodes.isEmpty()) return
    fun toScreen(xPt: Double, yPt: Double): Offset {
        val (sx, sy) = viewport.toScreen(xPt.toFloat(), yPt.toFloat())
        return Offset(sx * density, sy * density)
    }
    val pts = SplineInk.samplePoints(draft.nodes, draft.closed)
    val accent = Color(shapeInk.colour)
    if (pts != null && pts.size >= 2) {
        val path = Path().apply {
            val p0 = toScreen(pts[0].x, pts[0].y)
            moveTo(p0.x, p0.y)
            for (i in 1 until pts.size) {
                val pi = toScreen(pts[i].x, pts[i].y)
                lineTo(pi.x, pi.y)
            }
        }
        drawPath(
            path = path,
            color = accent,
            style = Stroke(width = (shapeInk.width * viewport.zoom * density).coerceAtLeast(1.5f), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
    for (n in draft.nodes) {
        val k = toScreen(n.x, n.y)
        val nInX = n.inX
        val nInY = n.inY
        if (nInX != null && nInY != null) {
            val ih = toScreen(nInX, nInY)
            drawLine(accent.copy(alpha = 0.6f), k, ih, strokeWidth = 1.2f * density)
            drawCircle(accent, radius = 4f * density, center = ih)
        }
        val nOutX = n.outX
        val nOutY = n.outY
        if (nOutX != null && nOutY != null) {
            val oh = toScreen(nOutX, nOutY)
            drawLine(accent.copy(alpha = 0.6f), k, oh, strokeWidth = 1.2f * density)
            drawCircle(accent, radius = 4f * density, center = oh)
        }
        drawCircle(Color.White, radius = 5.5f * density, center = k)
        drawCircle(accent, radius = 4f * density, center = k)
    }
}

@Composable
internal fun DesktopSplineBar(
    tab: DesktopTab,
    shapeInk: DesktopInk,
    onEdited: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = splineDraftFor(tab)
    if (!draft.active) return
    Surface(
        modifier = modifier.padding(bottom = 12.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${draft.nodes.size} pts",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DesktopButton(
                label = "Undo",
                onClick = {
                    if (draft.nodes.isNotEmpty()) draft.nodes.removeAt(draft.nodes.lastIndex)
                    if (draft.nodes.isEmpty()) draft.reset()
                },
                variant = DesktopButtonVariant.Ghost,
                enabled = draft.nodes.isNotEmpty(),
            )
            DesktopButton(
                label = if (draft.closed) "Open" else "Close",
                onClick = { draft.closed = !draft.closed },
                variant = DesktopButtonVariant.Ghost,
                enabled = draft.nodes.size >= 3,
            )
            DesktopButton(
                label = "Confirm",
                onClick = { confirmDesktopSplineDraft(tab, shapeInk, onEdited) },
                variant = DesktopButtonVariant.Primary,
                enabled = draft.nodes.size >= 2,
            )
            DesktopButton(
                label = "Cancel",
                onClick = { draft.reset() },
                variant = DesktopButtonVariant.Ghost,
            )
        }
    }
}
