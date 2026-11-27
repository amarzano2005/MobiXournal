package com.mobixournal.desktop.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.pagePointOf
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.SplineElement
import com.mobixournal.format.model.SplineNode
import com.mobixournal.render.ElementBounds
import com.mobixournal.render.ElementRef
import com.mobixournal.render.InkGeometry
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
    onLiveDrag: ((() -> Unit)?) -> Unit = {},
    onEditLiving: (() -> Unit)? = null,
    taps: TapTracker = TapTracker(),
): Boolean {
    if (tool != DesktopTool.SPLINE) return false
    val (ptX, ptY) = pagePointOf(down.position.x, down.position.y, density, dragView.current())
    if (ptX !in 0.0..dragView.pageW.toDouble() || ptY !in 0.0..dragView.pageH.toDouble()) return false
    val draft = splineDraftFor(tab)

    if (!draft.active) {
        if (tab.selection != null && hitsActiveSelection(tab, ptX, ptY, dragView, density)) {
            selectionDrag(
                start = down, tool = SelectionTool.MARQUEE, tab = tab, view = dragView, density = density,
                onLiveDrag = onLiveDrag, onEdited = onEdited, onEditLiving = onEditLiving, taps = taps,
            )
            return true
        }
        val page = runCatching { tab.document.page(tab.page) }.getOrNull()
        val existing = page?.let { SelectionTester.pickTopmost(it, ptX, ptY) { el -> el is SplineElement } }
        if (existing != null) {
            val el = page.layers.getOrNull(existing.layerIndex)?.elements?.getOrNull(existing.elementIndex)
            if (el != null) {
                val b = ElementBounds.of(el)
                tab.selection = PageSelection(SelectionRegion.Rect(b), setOf(existing))
                selectionDrag(
                    start = down, tool = SelectionTool.MARQUEE, tab = tab, view = dragView, density = density,
                    onLiveDrag = onLiveDrag, onEdited = onEdited, onEditLiving = onEditLiving, taps = taps,
                )
            }
            return true
        }
        if (tab.selection != null) {
            tab.selection = null
            down.consume()
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
    } else if (tryEditDraftGrip(down, draft, ptX, ptY, dragView, density)) {
        return true
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

private suspend fun AwaitPointerEventScope.tryEditDraftGrip(
    down: PointerInputChange,
    draft: DesktopSplineDraft,
    ptX: Double,
    ptY: Double,
    dragView: DragView,
    density: Float,
): Boolean {
    val tempSpline = SplineElement(nodes = draft.nodes.toList(), closed = draft.closed)
    val pxPerPt = (dragView.current().zoom * density).coerceAtLeast(0.1f)
    val tolPt = (HANDLE_PX / pxPerPt).toDouble()
    val grip = gripAt(tempSpline, ptX, ptY, tolPt) ?: return false
    if (grip is LivingGrip.SplineKnot && grip.index == 0 && draft.nodes.size >= 3 && !draft.closed) {
        draft.closed = true
        down.consume()
        return true
    }
    down.consume()
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
        if (change.changedToUpIgnoreConsumed()) { change.consume(); break }
        val (cx, cy) = pagePointOf(change.position.x, change.position.y, density, dragView.current())
        val updated = dragGrip(tempSpline, grip, cx - ptX, cy - ptY) as SplineElement
        draft.nodes.clear()
        draft.nodes.addAll(updated.nodes)
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
    val before = tab.document.snapshot()
    tab.record(before)
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
    val pts = SplineInk.samplePoints(draft.nodes, draft.closed, shapeInk.width.toDouble())
    val curveColor = Color(shapeInk.colour)
    val chrome = com.mobixournal.desktop.settings.DesktopTheme.accent
    if (pts != null && pts.size >= 2) {
        val path = Path().apply {
            val p0 = toScreen(pts[0].x, pts[0].y)
            moveTo(p0.x, p0.y)
            for (i in 1 until pts.size) {
                val pi = toScreen(pts[i].x, pts[i].y)
                lineTo(pi.x, pi.y)
            }
        }
        val dash = InkGeometry.dashIntervalsPt(shapeInk.lineStyle, shapeInk.width.toDouble())
        val pathEffect = if (dash != null && dash.size >= 2) {
            PathEffect.dashPathEffect(dash.map { (it * viewport.zoom * density).toFloat() }.toFloatArray(), 0f)
        } else null
        drawPath(
            path = path,
            color = curveColor,
            style = Stroke(
                width = (shapeInk.width * viewport.zoom * density).coerceAtLeast(1.5f),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = pathEffect,
            ),
        )
    }
    val nodes = draft.nodes.toList()
    for (i in nodes.indices) {
        val n = nodes[i]
        val k = toScreen(n.x, n.y)
        val (inH, outH) = SplineInk.handlesFor(nodes, draft.closed, i)
        if (SplineInk.hasActiveInHandle(nodes, draft.closed, i)) {
            val ih = toScreen(inH.first, inH.second)
            if (hypot((ih.x - k.x).toDouble(), (ih.y - k.y).toDouble()) > 3.0) {
                drawLine(chrome.copy(alpha = 0.55f), k, ih, strokeWidth = 1.2f * density)
                drawCircle(Color.White, radius = 4.2f * density, center = ih)
                drawCircle(chrome, radius = 3.0f * density, center = ih)
            }
        }
        if (SplineInk.hasActiveOutHandle(nodes, draft.closed, i)) {
            val oh = toScreen(outH.first, outH.second)
            if (hypot((oh.x - k.x).toDouble(), (oh.y - k.y).toDouble()) > 3.0) {
                drawLine(chrome.copy(alpha = 0.55f), k, oh, strokeWidth = 1.2f * density)
                drawCircle(Color.White, radius = 4.2f * density, center = oh)
                drawCircle(chrome, radius = 3.0f * density, center = oh)
            }
        }
        if (i == nodes.lastIndex) {
            drawCircle(chrome.copy(alpha = 0.45f), radius = 8.5f * density, center = k, style = Stroke(width = 1.5f * density))
        }
        val haloR = 5.5f * density
        val knotR = 4f * density
        if (n.smooth) {
            drawCircle(Color.White, radius = haloR, center = k)
            drawCircle(chrome, radius = knotR, center = k)
        } else {
            drawRect(Color.White, topLeft = Offset(k.x - haloR, k.y - haloR), size = Size(haloR * 2f, haloR * 2f))
            drawRect(chrome, topLeft = Offset(k.x - knotR, k.y - knotR), size = Size(knotR * 2f, knotR * 2f))
        }
    }
}

@Composable
internal fun DesktopSplineBar(
    tool: DesktopTool,
    tab: DesktopTab,
    shapeInk: DesktopInk,
    onEdited: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = splineDraftFor(tab)
    LaunchedEffect(tool, tab) {
        if (tool != DesktopTool.SPLINE && draft.active) {
            if (draft.nodes.size >= 2) confirmDesktopSplineDraft(tab, shapeInk, onEdited)
            else draft.reset()
        }
    }
    if (!draft.active) return
    Row(
        modifier = modifier
            .padding(bottom = 12.dp)
            .glassSurface(cornerRadius = DesktopTheme.surfaceRadius)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BarButton(
            glyph = ChromeGlyph.UNDO,
            enabled = draft.nodes.isNotEmpty(),
            tooltip = "Undo last knot",
            shortcut = "",
        ) {
            if (draft.nodes.isNotEmpty()) draft.nodes.removeAt(draft.nodes.lastIndex)
            if (draft.nodes.size < 3) draft.closed = false
            if (draft.nodes.isEmpty()) draft.reset()
        }
        BarButton(
            glyph = ChromeGlyph.LOOP,
            enabled = draft.nodes.size >= 3,
            tooltip = if (draft.closed) "Open curve" else "Close loop",
            shortcut = "",
            active = draft.closed,
            accented = draft.closed,
        ) {
            draft.closed = !draft.closed
        }
        BarButton(
            glyph = ChromeGlyph.CLOSE,
            enabled = true,
            tooltip = "Cancel spline",
            shortcut = "",
        ) {
            draft.reset()
        }
        BarButton(
            glyph = ChromeGlyph.CHECK,
            enabled = draft.nodes.size >= 2,
            tooltip = "Confirm spline",
            shortcut = "",
            accented = draft.nodes.size >= 2,
        ) {
            confirmDesktopSplineDraft(tab, shapeInk, onEdited)
        }
    }
}
