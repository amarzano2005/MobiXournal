package com.mobixournal.render

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.SplineElement
import com.mobixournal.format.model.SplineNode
import com.mobixournal.render.CanvasChrome.Companion.HANDLE_DRAW_PX
import kotlin.math.hypot

/**
 * Live drafting state for the interactive **Spline** tool on [DrawingSurfaceView].
 *
 * Tap places a smooth Catmull-Rom knot (`inX..outY == null`); dragging during placement pulls out
 * symmetric cubic Bézier handles (`outX/outY` and mirrored `inX/inY`).
 */
internal class SplineDraft {
    var active: Boolean = false
    var pageIndex: Int = 0
    val nodes: MutableList<SplineNode> = mutableListOf()
    var closed: Boolean = false
    var draggingGesture: Boolean = false
    var draggingHandles: Boolean = false
    var downX: Float = 0f
    var downY: Float = 0f
    var onChanged: (() -> Unit)? = null

    fun reset() {
        active = false
        nodes.clear()
        closed = false
        draggingGesture = false
        draggingHandles = false
        onChanged?.invoke()
    }
}

private const val SPLINE_DRAG_SLOP_PX = 10f

internal fun DrawingSurfaceView.handleSplineDown(event: MotionEvent, pointerIndex: Int): Boolean {
    if (!splineMode) return false
    val x = event.getX(pointerIndex)
    val y = event.getY(pointerIndex)
    val box = gestureBoxAt(x, y) ?: return false
    val pageIdx = box.index
    val ptX = box.toPtX(x, viewport.scrollX)
    val ptY = box.toPtY(y, viewport.scrollY)

    // If starting a fresh draft, check if the user tapped an existing SplineElement to select it.
    if (!splineDraft.active) {
        val page = doc.pages.getOrNull(pageIdx)
        if (page != null) {
            val existing = SelectionTester.pickTopmost(page, ptX, ptY) { el -> el is SplineElement }
            if (existing != null) {
                gestures.selection = ActiveSelection(pageIdx, setOf(existing))
                reportSelectionRect()
                render()
                return true
            }
        }
        splineDraft.active = true
        splineDraft.pageIndex = pageIdx
        splineDraft.nodes.clear()
        splineDraft.closed = false
    } else if (splineDraft.pageIndex != pageIdx) {
        // Switched pages: commit current draft if >= 2 nodes, then start on new page.
        if (splineDraft.nodes.size >= 2) confirmSplineDraft() else splineDraft.reset()
        splineDraft.active = true
        splineDraft.pageIndex = pageIdx
        splineDraft.nodes.clear()
        splineDraft.closed = false
    }

    splineDraft.nodes.add(SplineNode(x = ptX, y = ptY))
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
    if (!splineDraft.draggingHandles) {
        if (hypot((x - splineDraft.downX).toDouble(), (y - splineDraft.downY).toDouble()) < SPLINE_DRAG_SLOP_PX) {
            return
        }
        splineDraft.draggingHandles = true
    }
    val box = layout.boxes.getOrNull(splineDraft.pageIndex) ?: return
    val hx = box.toPtX(x, viewport.scrollX)
    val hy = box.toPtY(y, viewport.scrollY)
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
    splineDraft.draggingGesture = false
    splineDraft.draggingHandles = false
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
    if (splineDraft.nodes.isEmpty()) {
        splineDraft.reset()
    } else {
        splineDraft.onChanged?.invoke()
    }
    render()
}

internal fun DrawingSurfaceView.toggleSplineDraftClosed() {
    if (!splineDraft.active) return
    splineDraft.closed = !splineDraft.closed
    splineDraft.onChanged?.invoke()
    render()
}

internal fun DrawingSurfaceView.cancelSplineDraft() {
    if (!splineDraft.active) return
    splineDraft.reset()
    render()
}

fun DrawingSurfaceView.toggleSplineClosed(editing: SplineElement): SplineElement? {
    val updated = editing.copy(closed = !editing.closed)
    val prev = doc
    val newDoc = ElementEdits.replaceElement(prev, editing, updated) ?: return null
    session.open(prev)
    doc = newDoc
    finishGesture()
    relayout()
    return updated
}

internal fun DrawingSurfaceView.drawSplineDraft(canvas: Canvas) {
    if (!splineDraft.active || splineDraft.nodes.isEmpty()) return
    val box = layout.boxes.getOrNull(splineDraft.pageIndex) ?: return
    val widthPt = nominalShapeWidthPt()
    val pts = SplineInk.samplePoints(splineDraft.nodes, splineDraft.closed)
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
    for (i in splineDraft.nodes.indices) {
        val n = splineDraft.nodes[i]
        val nx = box.toViewX(n.x, scrollX)
        val ny = box.toViewY(n.y, scrollY)
        val nInX = n.inX
        val nInY = n.inY
        if (nInX != null && nInY != null) {
            val ix = box.toViewX(nInX, scrollX)
            val iy = box.toViewY(nInY, scrollY)
            canvas.drawLine(nx, ny, ix, iy, chrome.handleArm)
            canvas.drawCircle(ix, iy, HANDLE_DRAW_PX * 0.72f, chrome.handle)
        }
        val nOutX = n.outX
        val nOutY = n.outY
        if (nOutX != null && nOutY != null) {
            val ox = box.toViewX(nOutX, scrollX)
            val oy = box.toViewY(nOutY, scrollY)
            canvas.drawLine(nx, ny, ox, oy, chrome.handleArm)
            canvas.drawCircle(ox, oy, HANDLE_DRAW_PX * 0.72f, chrome.handle)
        }
        canvas.drawCircle(nx, ny, HANDLE_DRAW_PX + 2f, chrome.pinHalo)
        canvas.drawCircle(nx, ny, HANDLE_DRAW_PX, chrome.handle)
    }
}

internal fun DrawingSurfaceView.drawSplineEditHandles(canvas: Canvas, box: PageBox) {
    val sel = selection ?: return
    if (sel.refs.size != 1 || sel.pageIndex != box.index) return
    val ref = sel.refs.first()
    val page = pageAt(sel.pageIndex) ?: return
    val spline = page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex) as? SplineElement ?: return
    for (i in spline.nodes.indices) {
        val n = spline.nodes[i]
        val nx = box.toViewX(n.x, scrollX)
        val ny = box.toViewY(n.y, scrollY)
        val (inH, outH) = SplineInk.handlesFor(spline.nodes, spline.closed, i)
        val ix = box.toViewX(inH.first, scrollX)
        val iy = box.toViewY(inH.second, scrollY)
        val ox = box.toViewX(outH.first, scrollX)
        val oy = box.toViewY(outH.second, scrollY)
        if (hypot((ix - nx).toDouble(), (iy - ny).toDouble()) > 3.0) {
            canvas.drawLine(nx, ny, ix, iy, chrome.handleArm)
            canvas.drawCircle(ix, iy, HANDLE_DRAW_PX * 0.68f, chrome.handle)
        }
        if (hypot((ox - nx).toDouble(), (oy - ny).toDouble()) > 3.0) {
            canvas.drawLine(nx, ny, ox, oy, chrome.handleArm)
            canvas.drawCircle(ox, oy, HANDLE_DRAW_PX * 0.68f, chrome.handle)
        }
        canvas.drawCircle(nx, ny, HANDLE_DRAW_PX + 2f, chrome.pinHalo)
        canvas.drawCircle(nx, ny, HANDLE_DRAW_PX, chrome.handle)
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
        val ix = box.toViewX(inH.first, scrollX)
        val iy = box.toViewY(inH.second, scrollY)
        if (hypot((ix - nx).toDouble(), (iy - ny).toDouble()) > 3.0 &&
            hypot((viewX - ix).toDouble(), (viewY - iy).toDouble()) <= hitR
        ) {
            return SplineHandleGrab(spline, i, SplineHandleKind.IN_HANDLE)
        }
        val ox = box.toViewX(outH.first, scrollX)
        val oy = box.toViewY(outH.second, scrollY)
        if (hypot((ox - nx).toDouble(), (oy - ny).toDouble()) > 3.0 &&
            hypot((viewX - ox).toDouble(), (viewY - oy).toDouble()) <= hitR
        ) {
            return SplineHandleGrab(spline, i, SplineHandleKind.OUT_HANDLE)
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

