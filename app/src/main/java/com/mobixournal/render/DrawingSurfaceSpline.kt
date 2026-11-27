package com.mobixournal.render

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.SplineElement
import com.mobixournal.format.model.SplineNode
import com.mobixournal.render.CanvasChrome.Companion.HANDLE_DRAW_PX
import kotlin.math.hypot

/**
 * Live drafting state for the interactive **Spline** tool on [DrawingSurfaceView].
 *
 * Tap places a smooth Catmull-Rom knot (`inX..outY == null`); dragging during placement pulls out
 * symmetric cubic Bézier handles (`outX/outY` and mirrored `inX/inY`). Touching an existing knot
 * or handle during drafting grabs and moves it directly; tapping the first knot with >= 3 points
 * closes the loop.
 */
internal class SplineDraft {
    var active: Boolean = false
    var pageIndex: Int = 0
    val nodes: MutableList<SplineNode> = mutableListOf()
    var closed: Boolean = false
    var draggingGesture: Boolean = false
    var draggingHandles: Boolean = false
    var draftGrab: SplineHandleGrab? = null
    var downX: Float = 0f
    var downY: Float = 0f
    var onChanged: (() -> Unit)? = null

    fun cancelDrag() {
        draggingGesture = false
        draggingHandles = false
        draftGrab = null
    }

    fun reset() {
        active = false
        nodes.clear()
        closed = false
        cancelDrag()
        onChanged?.invoke()
    }
}

private const val SPLINE_DRAG_SLOP_PX = 10f

fun DrawingSurfaceView.singleSelectedSpline(): SplineElement? {
    val sel = selection ?: return null
    if (sel.refs.size != 1) return null
    val ref = sel.refs.first()
    val page = pageAt(sel.pageIndex) ?: return null
    return page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex) as? SplineElement
}

internal fun DrawingSurfaceView.handleSplineDown(event: MotionEvent, pointerIndex: Int): Boolean {
    if (!splineMode) return false
    val x = event.getX(pointerIndex)
    val y = event.getY(pointerIndex)
    val box = gestureBoxAt(x, y) ?: return false
    val pageIdx = box.index
    val ptX = box.toPtX(x, viewport.scrollX)
    val ptY = box.toPtY(y, viewport.scrollY)

    if (!splineDraft.active) {
        // 1. If a spline (or other element) is already selected, let its knots/handles/rotate/move win.
        if (selection != null && gestures.pressSelection(event)) return true
        // 2. If the user tapped an existing SplineElement on the page, select it and immediately start drag.
        val page = doc.pages.getOrNull(pageIdx)
        if (page != null) {
            val existing = SelectionTester.pickTopmost(page, ptX, ptY) { el -> el is SplineElement }
            if (existing != null) {
                gestures.selection = ActiveSelection(pageIdx, setOf(existing))
                reportSelectionRect()
                gestures.pressSelection(event)
                render()
                return true
            }
        }
        // 3. Empty paper: if something is selected, deselect it first without starting a new spline.
        if (selection != null) {
            gestures.clearSelection()
            reportSelectionRect()
            render()
            return true
        }
        splineDraft.active = true
        splineDraft.pageIndex = pageIdx
        splineDraft.nodes.clear()
        splineDraft.closed = false
    } else if (splineDraft.pageIndex != pageIdx) {
        if (splineDraft.nodes.size >= 2) confirmSplineDraft() else splineDraft.reset()
        splineDraft.active = true
        splineDraft.pageIndex = pageIdx
        splineDraft.nodes.clear()
        splineDraft.closed = false
    } else {
        // Active draft on this page: allow grabbing an existing knot/handle, or closing on knot 0.
        val tempSpline = SplineElement(nodes = splineDraft.nodes.toList(), closed = splineDraft.closed)
        val grab = hitTestSplineHandle(tempSpline, box, viewport.scrollX, viewport.scrollY, x, y)
        if (grab != null) {
            if (grab.kind == SplineHandleKind.KNOT && grab.nodeIndex == 0 &&
                splineDraft.nodes.size >= 3 && !splineDraft.closed
            ) {
                splineDraft.closed = true
                splineDraft.onChanged?.invoke()
                render()
                return true
            }
            splineDraft.draftGrab = grab
            splineDraft.draggingGesture = true
            splineDraft.draggingHandles = true
            render()
            return true
        }
    }

    splineDraft.nodes.add(SplineNode(x = ptX, y = ptY))
    splineDraft.draftGrab = null
    splineDraft.draggingGesture = true
    splineDraft.draggingHandles = false
    splineDraft.downX = x
    splineDraft.downY = y
    splineDraft.onChanged?.invoke()
    render()
    return true
}

internal fun DrawingSurfaceView.handleSplineMove(event: MotionEvent) {
    if (!splineDraft.draggingGesture || splineDraft.nodes.isEmpty()) return
    val x = event.x
    val y = event.y
    val box = layout.boxes.getOrNull(splineDraft.pageIndex) ?: return
    val hx = box.toPtX(x, viewport.scrollX)
    val hy = box.toPtY(y, viewport.scrollY)

    splineDraft.draftGrab?.let { grab ->
        val updated = moveSplineHandle(grab, hx, hy)
        splineDraft.nodes.clear()
        splineDraft.nodes.addAll(updated.nodes)
        splineDraft.draftGrab = grab.copy(spline = updated)
        render()
        return
    }

    if (!splineDraft.draggingHandles) {
        if (hypot((x - splineDraft.downX).toDouble(), (y - splineDraft.downY).toDouble()) < SPLINE_DRAG_SLOP_PX) {
            return
        }
        splineDraft.draggingHandles = true
    }
    val lastIdx = splineDraft.nodes.lastIndex
    val node = splineDraft.nodes[lastIdx]
    splineDraft.nodes[lastIdx] = node.copy(
        outX = hx,
        outY = hy,
        inX = 2.0 * node.x - hx,
        inY = 2.0 * node.y - hy,
    )
    render()
}

internal fun DrawingSurfaceView.handleSplineUp() {
    splineDraft.cancelDrag()
    render()
}

internal fun DrawingSurfaceView.confirmSplineDraft() {
    if (!splineDraft.active) return
    val nodes = splineDraft.nodes.toList()
    val closed = splineDraft.closed
    val pageIdx = splineDraft.pageIndex
    splineDraft.reset()
    if (nodes.size < 2) {
        render()
        return
    }
    val widthPt = nominalShapeWidthPt()
    val spline = SplineElement(
        nodes = nodes,
        closed = closed,
        color = strokeColor(),
        width = widthPt.toDouble(),
        lineStyle = currentLineStyle,
        id = ElementId.mint(ElementId.SPLINE),
    )
    session.open(doc)
    ElementEdits.addElement(doc, pageIdx, spline) { resolvedActiveLayer(it) }?.let { edited ->
        doc = edited
        selectInsertedElement(pageIdx)
        relayout()
    }
    finishGesture()
}

internal fun DrawingSurfaceView.undoSplineDraftNode() {
    if (!splineDraft.active) return
    if (splineDraft.nodes.isNotEmpty()) {
        splineDraft.nodes.removeAt(splineDraft.nodes.lastIndex)
    }
    if (splineDraft.nodes.size < 3) splineDraft.closed = false
    if (splineDraft.nodes.isEmpty()) {
        splineDraft.reset()
    } else {
        splineDraft.onChanged?.invoke()
    }
    render()
}

internal fun DrawingSurfaceView.toggleSplineDraftClosed() {
    if (!splineDraft.active || splineDraft.nodes.size < 3) return
    splineDraft.closed = !splineDraft.closed
    splineDraft.onChanged?.invoke()
    render()
}

internal fun DrawingSurfaceView.cancelSplineDraft() {
    if (!splineDraft.active) return
    splineDraft.reset()
    render()
}

private fun DrawingSurfaceView.replaceSplineElement(
    current: SplineElement,
    updated: SplineElement,
): SplineElement? {
    val prev = doc
    val newDoc = ElementEdits.replaceElement(prev, current, updated) ?: return null
    session.open(prev)
    doc = newDoc
    finishGesture()
    relayout()
    reportSelectionRect()
    render()
    return updated
}

fun DrawingSurfaceView.toggleSplineClosed(editing: SplineElement): SplineElement? {
    if (!editing.closed && editing.nodes.size < 3) return null
    return replaceSplineElement(editing, editing.copy(closed = !editing.closed))
}

fun DrawingSurfaceView.toggleSplineNodeSmooth(editing: SplineElement): SplineElement? {
    val idx = gestures.selectedSplineNodeIndex.coerceIn(0, editing.nodes.lastIndex)
    return replaceSplineElement(editing, SplineInk.toggleNodeSmooth(editing, idx))
}

fun DrawingSurfaceView.removeSplineNode(editing: SplineElement): SplineElement? {
    val idx = gestures.selectedSplineNodeIndex.coerceIn(0, editing.nodes.lastIndex)
    val updated = SplineInk.removeNode(editing, idx) ?: return null
    gestures.selectedSplineNodeIndex = (idx - 1).coerceAtLeast(0)
    return replaceSplineElement(editing, updated)
}

fun DrawingSurfaceView.addSplineMidpointNode(editing: SplineElement): SplineElement? {
    val idx = gestures.selectedSplineNodeIndex.coerceIn(0, editing.nodes.lastIndex)
    val n = editing.nodes.size
    val nextIdx = if (idx < n - 1) idx + 1 else if (editing.closed) 0 else (idx - 1).coerceAtLeast(0)
    val a = editing.nodes[idx]
    val b = editing.nodes[nextIdx]
    val insertAt = if (idx < n - 1) idx + 1 else if (editing.closed) n else idx
    val mid = SplineNode(x = (a.x + b.x) / 2.0, y = (a.y + b.y) / 2.0)
    val updated = editing.copy(nodes = editing.nodes.toMutableList().apply { add(insertAt, mid) })
    gestures.selectedSplineNodeIndex = insertAt
    return replaceSplineElement(editing, updated)
}

internal fun DrawingSurfaceView.drawSplineDraft(canvas: Canvas) {
    if (!splineDraft.active || splineDraft.nodes.isEmpty()) return
    val box = layout.boxes.getOrNull(splineDraft.pageIndex) ?: return
    val widthPt = nominalShapeWidthPt()
    val pts = SplineInk.samplePoints(splineDraft.nodes, splineDraft.closed, widthPt.toDouble())
    if (pts != null && pts.size >= 2) {
        val path = Path()
        path.moveTo(box.toViewX(pts[0].x, scrollX), box.toViewY(pts[0].y, scrollY))
        for (i in 1 until pts.size) {
            path.lineTo(box.toViewX(pts[i].x, scrollX), box.toViewY(pts[i].y, scrollY))
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = strokeColor()
            strokeWidth = (widthPt.toFloat() * box.scale).coerceAtLeast(1.5f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            val dash = InkGeometry.dashIntervalsPt(currentLineStyle, widthPt.toDouble())
            if (dash != null && dash.size >= 2) {
                pathEffect = DashPathEffect(dash.map { (it * box.scale).toFloat() }.toFloatArray(), 0f)
            }
        }
        canvas.drawPath(path, paint)
    }
    drawSplineNodesAndHandles(canvas, box, splineDraft.nodes, splineDraft.closed, selectedNode = splineDraft.nodes.lastIndex)
}

internal fun DrawingSurfaceView.drawSplineEditHandles(canvas: Canvas, box: PageBox) {
    val sel = selection ?: return
    if (sel.refs.size != 1 || sel.pageIndex != box.index) return
    val ref = sel.refs.first()
    val page = pageAt(sel.pageIndex) ?: return
    val spline = page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex) as? SplineElement ?: return
    drawSplineNodesAndHandles(canvas, box, spline.nodes, spline.closed, selectedNode = gestures.selectedSplineNodeIndex)
}

private fun DrawingSurfaceView.drawSplineNodesAndHandles(
    canvas: Canvas,
    box: PageBox,
    nodes: List<SplineNode>,
    closed: Boolean,
    selectedNode: Int,
) {
    for (i in nodes.indices) {
        val n = nodes[i]
        val nx = box.toViewX(n.x, scrollX)
        val ny = box.toViewY(n.y, scrollY)
        val (inH, outH) = SplineInk.handlesFor(nodes, closed, i)
        if (SplineInk.hasActiveInHandle(nodes, closed, i)) {
            val ix = box.toViewX(inH.first, scrollX)
            val iy = box.toViewY(inH.second, scrollY)
            if (hypot((ix - nx).toDouble(), (iy - ny).toDouble()) > 3.0) {
                canvas.drawLine(nx, ny, ix, iy, chrome.handleArm)
                canvas.drawCircle(ix, iy, HANDLE_DRAW_PX * 0.65f, chrome.handle)
            }
        }
        if (SplineInk.hasActiveOutHandle(nodes, closed, i)) {
            val ox = box.toViewX(outH.first, scrollX)
            val oy = box.toViewY(outH.second, scrollY)
            if (hypot((ox - nx).toDouble(), (oy - ny).toDouble()) > 3.0) {
                canvas.drawLine(nx, ny, ox, oy, chrome.handleArm)
                canvas.drawCircle(ox, oy, HANDLE_DRAW_PX * 0.65f, chrome.handle)
            }
        }
        val isSelected = i == selectedNode
        if (isSelected) {
            canvas.drawCircle(nx, ny, HANDLE_DRAW_PX + 5.5f, chrome.handleArm)
        }
        val knotR = if (isSelected) HANDLE_DRAW_PX + 1.5f else HANDLE_DRAW_PX
        val haloR = knotR + 2f
        if (n.smooth) {
            canvas.drawCircle(nx, ny, haloR, chrome.pinHalo)
            canvas.drawCircle(nx, ny, knotR, chrome.handle)
        } else {
            canvas.drawRect(nx - haloR, ny - haloR, nx + haloR, ny + haloR, chrome.pinHalo)
            canvas.drawRect(nx - knotR, ny - knotR, nx + knotR, ny + knotR, chrome.handle)
        }
    }
}

internal enum class SplineHandleKind { KNOT, IN_HANDLE, OUT_HANDLE }

internal data class SplineHandleGrab(
    val spline: SplineElement,
    val nodeIndex: Int,
    val kind: SplineHandleKind,
)

internal fun hitTestSplineHandle(
    spline: SplineElement,
    box: PageBox,
    scrollX: Float,
    scrollY: Float,
    viewX: Float,
    viewY: Float,
): SplineHandleGrab? {
    val hitR = DrawingSurfaceDefaults.HANDLE_HIT_PX
    for (i in spline.nodes.indices) {
        val n = spline.nodes[i]
        val nx = box.toViewX(n.x, scrollX)
        val ny = box.toViewY(n.y, scrollY)
        if (hypot((viewX - nx).toDouble(), (viewY - ny).toDouble()) <= hitR) {
            return SplineHandleGrab(spline, i, SplineHandleKind.KNOT)
        }
        val (inH, outH) = SplineInk.handlesFor(spline.nodes, spline.closed, i)
        if (SplineInk.hasActiveInHandle(spline.nodes, spline.closed, i)) {
            val ix = box.toViewX(inH.first, scrollX)
            val iy = box.toViewY(inH.second, scrollY)
            if (hypot((ix - nx).toDouble(), (iy - ny).toDouble()) > 3.0 &&
                hypot((viewX - ix).toDouble(), (viewY - iy).toDouble()) <= hitR
            ) {
                return SplineHandleGrab(spline, i, SplineHandleKind.IN_HANDLE)
            }
        }
        if (SplineInk.hasActiveOutHandle(spline.nodes, spline.closed, i)) {
            val ox = box.toViewX(outH.first, scrollX)
            val oy = box.toViewY(outH.second, scrollY)
            if (hypot((ox - nx).toDouble(), (oy - ny).toDouble()) > 3.0 &&
                hypot((viewX - ox).toDouble(), (viewY - oy).toDouble()) <= hitR
            ) {
                return SplineHandleGrab(spline, i, SplineHandleKind.OUT_HANDLE)
            }
        }
    }
    return null
}

internal fun moveSplineHandle(grab: SplineHandleGrab, xPt: Double, yPt: Double): SplineElement {
    val idx = grab.nodeIndex
    val nodes = grab.spline.nodes.toMutableList()
    val node = nodes[idx]
    val updatedNode = when (grab.kind) {
        SplineHandleKind.KNOT -> {
            val dx = xPt - node.x
            val dy = yPt - node.y
            node.copy(
                x = xPt,
                y = yPt,
                inX = node.inX?.let { it + dx },
                inY = node.inY?.let { it + dy },
                outX = node.outX?.let { it + dx },
                outY = node.outY?.let { it + dy },
            )
        }
        SplineHandleKind.IN_HANDLE -> {
            val mirrorOut = if (node.smooth) (2.0 * node.x - xPt) to (2.0 * node.y - yPt) else null
            node.copy(
                inX = xPt,
                inY = yPt,
                outX = mirrorOut?.first ?: node.outX,
                outY = mirrorOut?.second ?: node.outY,
            )
        }
        SplineHandleKind.OUT_HANDLE -> {
            val mirrorIn = if (node.smooth) (2.0 * node.x - xPt) to (2.0 * node.y - yPt) else null
            node.copy(
                outX = xPt,
                outY = yPt,
                inX = mirrorIn?.first ?: node.inX,
                inY = mirrorIn?.second ?: node.inY,
            )
        }
    }
    nodes[idx] = updatedNode
    return grab.spline.copy(nodes = nodes)
}
