/**
 * [DrawingSurfaceView]'s ink capture: the freehand stroke, the tap-placed spline, the eraser drag,
 * and the tap that places text/images — plus the sampling, pressure-to-width and commit helpers they
 * share. Extensions on the view, so they read its state directly.
 */
package com.mobixournal.render

import android.view.MotionEvent
import com.mobixournal.audio.audioRef
import com.mobixournal.audio.withAudio
import com.mobixournal.format.XoppColor
import com.mobixournal.format.XoppColor.withAlpha
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import kotlin.math.hypot

internal var strokeStartEventTimeMs: Long = 0L

internal fun DrawingSurfaceView.startStroke(event: MotionEvent, pointerIndex: Int) {
    strokeStartEventTimeMs = event.eventTime
    scrolling = false
    shaping = shapeKind != null
    cancelHoldSnap()
    holdSnapped = false
    gestureStartDoc = doc
    gesturePointerId = event.getPointerId(pointerIndex)
    val box = gestureBoxAt(event.getX(pointerIndex), event.getY(pointerIndex))
        ?: run { current = null; return }
    currentPage = box.index
    if (shaping) {
        // Grid snap first, then the guide — a placed guide is the stronger constraint and must
        // not be undone by pulling the point back onto the ruling. The grid snap is the *joint* one
        // so an isometric sheet's node (which rules no axis) can still capture the endpoint.
        val (gx, gy) = snappedPoint(
            box,
            box.toPtX(event.getX(pointerIndex), scrollX),
            box.toPtY(event.getY(pointerIndex), scrollY),
        )
        val (sx, sy) = guided(box, gx, gy)
        shapeStartX = sx
        shapeStartY = sy
        shapeWidthPt = nominalShapeWidthPt()
        currentStrokes = null
        current = ArrayList(listOf(StrokePoint(shapeStartX, shapeStartY, shapeWidthPt, dtMs = 0)))
    } else {
        // Decimate against this page's real px/pt, so a stroke drawn zoomed out or in a
        // multi-column view keeps the same document-space detail as one drawn at 100%.
        smoother.reset(strokePrecision.stepPxFor(box.scale))
        current = ArrayList<StrokePoint>().also { addSamples(event, pointerIndex, box, it) }
        // Hold-to-snap starts counting from where the tip first landed.
        holdAnchorX = current?.lastOrNull()?.x ?: 0.0
        holdAnchorY = current?.lastOrNull()?.y ?: 0.0
        armHoldSnap()
    }
}

internal fun DrawingSurfaceView.extendStroke(event: MotionEvent) {
    val pointerIndex = event.findPointerIndex(gesturePointerId)
    if (pointerIndex < 0) return
    val box = gestureBox() ?: return
    if (shaping) {
        val (gx, gy) = snappedPoint(
            box,
            box.toPtX(event.getX(pointerIndex), scrollX),
            box.toPtY(event.getY(pointerIndex), scrollY),
        )
        val (ex, ey) = guided(box, gx, gy)
        val multi = ShapeBuilder.buildMulti(
            shapeKind ?: return, shapeStartX, shapeStartY, ex, ey, shapeWidthPt,
            tableRows, tableCols, tableHeader,
        )
        if (multi != null) {
            currentStrokes = multi
            current = null
        } else {
            currentStrokes = null
            current = ArrayList(
                ShapeBuilder.build(
                    shapeKind ?: return, shapeStartX, shapeStartY, ex, ey, shapeWidthPt,
                    tableRows, tableCols, tableHeader,
                    triangleKind, scaleneAngleA, scaleneAngleB, scaleneAngleC,
                    trapezoidKind, trapezoidAngleA, trapezoidAngleB,
                ),
            )
        }
        render()
    } else {
        current?.let {
            addSamples(event, pointerIndex, box, it)
            // Rest the tip and the stroke snaps to geometry before lift-off.
            maybeArmHoldSnap(it)
            render()
        }
    }
}

// --- the spline tool: tap to add a control point, drag to curve it, double-tap to finish -------

/**
 * A touch while the spline tool is active. A tap that pairs with the previous one (double-tap)
 * closes the curve; otherwise it appends a control point, which the following drag can curve.
 */
internal fun DrawingSurfaceView.splineDown(event: MotionEvent, pointerIndex: Int) {
    val x = event.getX(pointerIndex)
    val y = event.getY(pointerIndex)
    if (splineNodes.isNotEmpty() && pairsWithPreviousSplineTap(event.eventTime, x, y)) {
        finishSpline()
        return
    }
    scrolling = false
    // The first node fixes the page for the whole curve, so later taps stay in one stroke.
    val box = if (splineNodes.isEmpty()) gestureBoxAt(x, y) else gestureBox()
    if (box == null) return
    if (splineNodes.isEmpty()) {
        currentPage = box.index
        gestureStartDoc = doc
    }
    gesturePointerId = event.getPointerId(pointerIndex)
    if (splineNodes.isEmpty()) shapeWidthPt = nominalShapeWidthPt()
    splineAnchorX = box.toPtX(x, scrollX)
    splineAnchorY = box.toPtY(y, scrollY)
    splineNodes += SplineNode(splineAnchorX, splineAnchorY)
    splineDragging = true
    splineTapTime = event.eventTime
    splineTapX = x
    splineTapY = y
    renderSplinePreview()
}

/** Dragging away from the tap grows the newest node's tangent handle, curving the curve live. */
internal fun DrawingSurfaceView.splineMove(event: MotionEvent) {
    val pointerIndex = event.findPointerIndex(gesturePointerId)
    if (pointerIndex < 0) return
    val box = gestureBox() ?: return
    val tx = box.toPtX(event.getX(pointerIndex), scrollX) - splineAnchorX
    val ty = box.toPtY(event.getY(pointerIndex), scrollY) - splineAnchorY
    splineNodes[splineNodes.lastIndex] = SplineNode(splineAnchorX, splineAnchorY, tx, ty)
    renderSplinePreview()
}

/** The node is placed; the curve stays open, waiting for the next tap (or the finishing double-tap). */
internal fun DrawingSurfaceView.splineUp(event: MotionEvent) {
    splineDragging = false
    gesturePointerId = -1
    stylusOwner = false
    // A drag isn't a tap, so it can't be half of the double-tap that finishes the curve.
    if (hypot(event.x - splineTapX, event.y - splineTapY) > doubleTapSlopPx) splineTapTime = 0L
    renderSplinePreview()
}

internal fun DrawingSurfaceView.pairsWithPreviousSplineTap(time: Long, x: Float, y: Float): Boolean =
    splineTapTime != 0L && time - splineTapTime <= doubleTapTimeoutMs &&
        hypot(x - splineTapX, y - splineTapY) <= doubleTapSlopPx

/** Show the curve-so-far as the in-progress stroke, so it paints exactly as it will commit. */
internal fun DrawingSurfaceView.renderSplinePreview() {
    current = ArrayList(SplineBuilder.build(splineNodes, shapeWidthPt))
    onSplineChanged?.invoke(splineNodes.size)
    render()
}

/**
 * Drop the most recently placed control point. The escape hatch for a mis-tapped node — before this
 * the only recovery was cancelling the whole curve. Removing the last one leaves no spline open.
 */
fun DrawingSurfaceView.undoLastSplineNode() {
    if (splineNodes.isEmpty()) return
    splineNodes.removeAt(splineNodes.lastIndex)
    splineTapTime = 0L
    if (splineNodes.isEmpty()) {
        clearSpline()
        gestureStartDoc = null
        render()
    } else {
        renderSplinePreview()
    }
}

/** True while a spline is open — the editor uses this to decide whether Enter/Esc apply. */
fun DrawingSurfaceView.splineInProgress(): Boolean = splineNodes.isNotEmpty()

/**
 * Commit the open spline as one ordinary stroke (the same shape any other tool produces) and
 * clear the tool's state. Safe to call when nothing is open; a one-node spline is just dropped.
 */
fun DrawingSurfaceView.finishSpline() {
    if (splineNodes.isEmpty()) return
    val pts = SplineBuilder.build(splineNodes, shapeWidthPt)
    clearSpline()
    if (pts.size >= 2) {
        appendStroke(
            currentPage,
            Stroke(
                tool, strokeColor(), "round", pts, true,
                lineStyle = currentLineStyle,
            ),
        )
    }
    finishGesture()
    render()
}

/** Throw the open spline away without committing it (Escape, or switching tools mid-curve). */
fun DrawingSurfaceView.cancelSpline() {
    if (splineNodes.isEmpty()) return
    clearSpline()
    gestureStartDoc = null
    render()
}

/**
 * Insert a grid table with [rows] and [cols] centered in the currently visible page viewport.
 * Uses the nominal figure stroke width ([nominalShapeWidthPt]), stroke colour and current line style.
 */
fun DrawingSurfaceView.insertTable(
    rows: Int = tableRows,
    cols: Int = tableCols,
    widthPt: Double = 240.0,
    heightPt: Double = 160.0,
    hasHeader: Boolean = tableHeader,
) {
    val target = visiblePageIndex()
    val box = layout.boxes.getOrNull(target)
    val (cx, cy) = if (box != null && width > 0 && height > 0) {
        box.toPtX(width / 2f, scrollX) to box.toPtY(height / 2f, scrollY)
    } else {
        val page = pageAt(target)
        ((page?.width ?: 612.0) / 2.0) to ((page?.height ?: 792.0) / 2.0)
    }
    val halfW = widthPt / 2.0
    val halfH = heightPt / 2.0
    val pts = ShapeBuilder.build(
        ShapeKind.TABLE,
        cx - halfW, cy - halfH,
        cx + halfW, cy + halfH,
        nominalShapeWidthPt(),
        rows, cols,
        hasHeader,
    )
    if (pts.size >= 2) {
        gestureStartDoc = doc
        appendStroke(
            target,
            Stroke(
                Tool.PEN,
                strokeColor(),
                "round",
                pts,
                true,
                lineStyle = currentLineStyle,
            ),
        )
        finishGesture()
        render()
    }
}

internal fun DrawingSurfaceView.clearSpline() {
    val wasOpen = splineNodes.isNotEmpty()
    splineNodes.clear()
    splineDragging = false
    splineTapTime = 0L
    current = null
    gesturePointerId = -1
    stylusOwner = false
    if (wasOpen) onSplineChanged?.invoke(0)
}

/** The eraser: touch/drag deletes any stroke it passes over on the page under the pointer. */
internal fun DrawingSurfaceView.startErase(event: MotionEvent, pointerIndex: Int) {
    scrolling = false
    erasing = true
    gestureStartDoc = doc
    gesturePointerId = event.getPointerId(pointerIndex)
    val box = gestureBoxAt(event.getX(pointerIndex), event.getY(pointerIndex)) ?: return
    currentPage = box.index
    // Eraser tip and barrel button both use WHOLE_STROKE mode to delete entire strokes.
    val isEraserTip = event.getToolType(pointerIndex) == android.view.MotionEvent.TOOL_TYPE_ERASER
    val mode = if (isEraserTip || barrelButton.held) EraserMode.WHOLE_STROKE else eraserMode
    eraseAt(box, event.getX(pointerIndex), event.getY(pointerIndex), mode)
}

/**
 * Pressing the barrel button **mid-stroke** turns the rest of that gesture into an erase: the ink
 * drawn so far is committed and the same gesture continues as the eraser, so holding the button
 * erases whether it was pressed before the tip landed or while writing. A release does not resurrect
 * the stroke — the gesture erases until the tip lifts. No-op unless the button really is held and the
 * settings map it to [BarrelAction.ERASE].
 */
internal fun DrawingSurfaceView.switchStrokeToErase(event: MotionEvent) {
    if (!barrelButton.held || inputSettings.barrelAction != BarrelAction.ERASE) return
    val pointerIndex = event.findPointerIndex(gesturePointerId)
    if (pointerIndex < 0) return
    val start = gestureStartDoc
    commitCurrent()
    startErase(event, pointerIndex)
    // The ink drawn and the erase it turned into stay one undo step.
    gestureStartDoc = start
}

internal fun DrawingSurfaceView.eraseMove(event: MotionEvent) {
    val pointerIndex = event.findPointerIndex(gesturePointerId)
    if (pointerIndex < 0) return
    val box = gestureBox() ?: return
    // Eraser tip and barrel button both use WHOLE_STROKE mode to delete entire strokes.
    val isEraserTip = event.getToolType(pointerIndex) == android.view.MotionEvent.TOOL_TYPE_ERASER
    val mode = if (isEraserTip || barrelButton.held) EraserMode.WHOLE_STROKE else eraserMode
    eraseAt(box, event.getX(pointerIndex), event.getY(pointerIndex), mode)
}

internal fun DrawingSurfaceView.eraseAt(box: PageBox, vx: Float, vy: Float, mode: EraserMode = eraserMode) {
    val px = box.toPtX(vx, scrollX)
    val py = box.toPtY(vy, scrollY)
    eraseX = vx; eraseY = vy
    eraseOnPage(currentPage, px, py, eraserRadiusPt, mode)
    render()
}

/** A tap in a placement tool: remember where it went down; a small drag cancels it. */
internal fun DrawingSurfaceView.beginPlace(event: MotionEvent, pointerIndex: Int) {
    scrolling = false
    erasing = false
    current = null
    placing = true
    placeDownX = event.getX(pointerIndex)
    placeDownY = event.getY(pointerIndex)
}

/** Moving past the tap slop turns a placement into a no-op (the user is scrubbing, not tapping). */
internal fun DrawingSurfaceView.placeMove(event: MotionEvent) {
    if (hypot(event.x - placeDownX, event.y - placeDownY) > DrawingSurfaceDefaults.TAP_SLOP_PX) placing = false
}

/**
 * Fire [onPlace] for the page/point the tap landed on, hitting an existing text box if any.
 *
 * On a board a tap in a cell the document has no chunk for **creates** that chunk: a placement is a
 * deliberate tap, and the text box, image or formula it is about to insert needs a page to live on. The
 * cell is the one the tap landed in, so the element arrives where it was placed.
 */
internal fun DrawingSurfaceView.commitPlace() {
    val kind = placeKind ?: return
    // A board places into the cell under the tap, creating it when the board has no chunk there
    // (DrawingSurfaceBoardInk).
    if (board != null) {
        commitChunkPlace(kind)
        return
    }
    val box = layout.pageAt(placeDownX + scrollX, placeDownY + scrollY) ?: return
    val xPt = box.toPtX(placeDownX, scrollX)
    val yPt = box.toPtY(placeDownY, scrollY)
    val existing = if (kind == PlaceKind.TEXT) textEdits.pickForEditing(box.index, xPt, yPt) else null
    onPlace?.invoke(kind, Placement(box.index, xPt, yPt, existing))
}
/**
 * Append every sample for the gesture's pointer — historical first — as pressure-scaled
 * page-local points. Sampling only [pointerIndex] (not pointer 0) is what lets a resting palm
 * coexist with the pen: the palm is a different pointer and is never read here.
 */
internal fun DrawingSurfaceView.addSamples(event: MotionEvent, pointerIndex: Int, box: PageBox, into: MutableList<StrokePoint>) {
    // Raw telemetry, stored and never drawn with (`docs/stylus.md`). Zero is a *reading* - a pen held
    // upright - so it is kept when the tablet declares the axis; only a tablet without it stores null.
    val tilt = event.telemetryAxis(MotionEvent.AXIS_TILT, pointerIndex)
    val azimuth = event.telemetryAxis(MotionEvent.AXIS_ORIENTATION, pointerIndex)

    for (h in 0 until event.historySize) {
        smoother.accept(
            event.getHistoricalX(pointerIndex, h),
            event.getHistoricalY(pointerIndex, h),
            event.getHistoricalPressure(pointerIndex, h),
        )?.let {
            val hTime = event.getHistoricalEventTime(h)
            into += point(box, it.x, it.y, it.pressure, hTime, tilt, azimuth)
        }
    }
    // The batch's newest sample is always kept, so the drawn line reaches the pen.
    smoother.accept(
        event.getX(pointerIndex), event.getY(pointerIndex), event.getPressure(pointerIndex), force = true,
    )?.let {
        into += point(box, it.x, it.y, it.pressure, event.eventTime, tilt, azimuth)
    }
}

/**
 * The stroke width one sample of the current tool draws at, in pt. A highlighter lays down a
 * broad, constant-width band and ignores pressure; every other tool tapers with pressure. Shapes
 * and splines call this once per gesture so they match a pen stroke drawn at the same size.
 *
 * The pressure arithmetic itself lives in [PressureCurve.widthPt], so what a setting change does to a
 * stroke is pinned by unit tests rather than by reading this call site.
 */
internal fun DrawingSurfaceView.widthForPressure(pressure: Float): Double = if (tool == Tool.HIGHLIGHTER) {
    DrawingSurfaceDefaults.highlighterWidthFor(baseWidthPt)
} else {
    // Desktop Xournal++'s model, exactly: a point's width is the raw pressure — scaled by the
    // multiplier and floored at the minimum — times the tool's width. A zero reading is not treated
    // specially; it simply takes the floor, where the desktop's own filter puts it.
    PressureCurve.widthPt(baseWidthPt, pressure, pressureEnabled, pressureMultiplier, minimumPressure)
}

/**
 * The width a **shape or spline** stroke draws at, in pt: the tool's width setting, verbatim.
 *
 * Desktop Xournal++ draws a shape at the nominal width (it has no pressure stream), so sharing the
 * setting unchanged — rather than an app-invented scale — is what keeps a shape drawn here and one
 * drawn on the desktop the same thickness, and an already-authored file looking the same in both. A
 * shape is therefore as thick as the pen at full pressure and a little thicker than a light pen
 * stroke, which is exactly how the desktop behaves. Switch **Pressure sensitivity** off and the pen
 * draws at the setting too, so the two match. See "one width source" in docs/stack.md.
 */
internal fun DrawingSurfaceView.nominalShapeWidthPt(): Double = baseWidthPt.toDouble()

internal fun DrawingSurfaceView.point(
    box: PageBox,
    vx: Float,
    vy: Float,
    pressure: Float,
    eventTime: Long = strokeStartEventTimeMs,
    tilt: Float? = null,
    azimuth: Float? = null,
): StrokePoint {
    val width = widthForPressure(pressure)
    val (gx, gy) = guided(box, box.toPtX(vx, scrollX), box.toPtY(vy, scrollY))
    val dt = if (strokeStartEventTimeMs > 0L) (eventTime - strokeStartEventTimeMs).coerceAtLeast(0).toInt() else 0
    return StrokePoint(
        x = gx,
        y = gy,
        width = width,
        pressure = pressure,
        tilt = tilt,
        azimuth = azimuth,
        dtMs = dt,
    )
}

internal fun DrawingSurfaceView.commitCurrent() {
    val multi = currentStrokes
    if (multi != null) {
        currentStrokes = null
        current = null
        shaping = false
        for (mPts in multi) {
            if (mPts.size >= 2) {
                appendStroke(
                    currentPage,
                    Stroke(tool, strokeColor(), "round", mPts, true, lineStyle = currentLineStyle),
                )
            }
        }
        render()
        return
    }
    // Lift-off ends any pending hold, and a stroke the hold already snapped stays snapped.
    cancelHoldSnap()
    val raw = current ?: return
    current = null
    val wasShaping = shaping
    shaping = false
    val holdWasSnapped = holdSnapped
    holdSnapped = false

    // A tap/click with the table tool places a default-sized table centered at the tapped point.
    if (wasShaping && shapeKind == ShapeKind.TABLE && raw.size <= 2) {
        val ex = raw.lastOrNull()?.x ?: shapeStartX
        val ey = raw.lastOrNull()?.y ?: shapeStartY
        if (hypot(ex - shapeStartX, ey - shapeStartY) < 6.0) {
            val halfW = 100.0
            val halfH = 60.0
            val tablePts = ShapeBuilder.build(
                ShapeKind.TABLE,
                shapeStartX - halfW, shapeStartY - halfH,
                shapeStartX + halfW, shapeStartY + halfH,
                shapeWidthPt,
                tableRows, tableCols, tableHeader,
            )
            appendStroke(
                currentPage,
                Stroke(tool, strokeColor(), "round", tablePts, true, lineStyle = currentLineStyle),
            )
            render()
            return
        }
    }

    // Shape tools emit exact geometry — only freehand samples get thinned.
    // Thin against the page's real px/pt (fit-to-width × zoom), not the zoom alone — on a large
    // screen those differ by 2–4×, and using the zoom leaves visible facets at 100% and below.
    val pxPerPt = gestureBox()?.scale ?: zoom
    var snapped = false
    val pts = if (wasShaping) {
        raw
    } else {
        // With the recogniser on, a freehand stroke that clearly means a primitive is replaced by
        // clean geometry; anything it doesn't recognise is thinned and kept as drawn.
        //
        // The recogniser gets the *raw* samples, not the thinned ones: desktop Xournal++ feeds it
        // the whole recorded stroke, and thinning first would hide exactly the dense detail its
        // piecewise line fit reads to find corners (and could round a circle's samples off into
        // facets). Thinning is therefore done lazily, only on the strokes that weren't recognised.
        // Desktop Xournal++ gives the highlighter TOOL_CAP_RECOGNIZER too (ToolEnums.cpp), so a
        // straight highlighter line snaps to a line exactly as a pen stroke does.
        val recognisable = tool == Tool.PEN || tool == Tool.HIGHLIGHTER
        val shape = if (recognizeShapes && recognisable && raw.isNotEmpty()) {
            // Keep the thickness the stroke was actually drawn at, not the un-scaled base width,
            // so snapping to a primitive doesn't fatten the line under the user's hand.
            ShapeRecognizer.recognize(raw, raw.map { it.width }.average())
        } else {
            null
        }
        snapped = shape != null || holdWasSnapped
        shape ?: StrokeSimplifier.simplify(raw, StrokeSimplifier.toleranceFor(pxPerPt, strokePrecision))
    }
    if (pts.size >= 2) {
        // Highlighter and geometric shapes are constant-width → store a single width; the freehand
        // pen keeps its per-vertex pressure. The live line-style is baked in so it round-trips.
        val uniform = tool == Tool.HIGHLIGHTER || wasShaping || snapped
        val stroke = Stroke(
            tool, strokeColor(), "round", pts, uniform,
            lineStyle = currentLineStyle,
        )
        appendStroke(currentPage, stroke)
        // A dimension line carries its value: the measurement goes in as a text box of its own.
        if (wasShaping && shapeKind == ShapeKind.DIMENSION) appendDimensionLabel(raw, strokeColor())
    }
    render()
}

/** Highlighter is stored semi-transparent so it round-trips (and renders) translucent. */
internal fun DrawingSurfaceView.strokeColor(): Int =
    if (tool == Tool.HIGHLIGHTER && (colorArgb ushr 24) == 0xFF) {
        colorArgb.withAlpha(XoppColor.HIGHLIGHTER_ALPHA)
    } else {
        colorArgb
    }

// --- audio: stamp strokes while recording, replay them on a play-object tap ------------------

/**
 * Report the recording behind the topmost stroke under a play-object tap. Reports null (rather
 * than staying silent) when the tap misses or lands on a stroke that was drawn without audio, so
 * the editor can say so instead of leaving the tap looking broken.
 */
internal fun DrawingSurfaceView.audioTap(event: MotionEvent, pointerIndex: Int) {
    val x = event.getX(pointerIndex)
    val y = event.getY(pointerIndex)
    val box = layout.pageAt(x + scrollX, y + scrollY) ?: return
    val xPt = box.toPtX(x, scrollX)
    val yPt = box.toPtY(y, scrollY)
    val page = pageAt(box.index) ?: return
    val stroke = SelectionTester.pickTopmost(page, xPt, yPt)
        ?.let { page.layers.getOrNull(it.layerIndex)?.elements?.getOrNull(it.elementIndex) }
        ?.let { it as? Stroke }
    val baseRef = stroke?.audioRef()
    // Point-precise where the stroke carries the telemetry a `.mobix` stores: the tap starts the
    // recording at the **point** under it, so a tap on the fifth word starts there rather than at the
    // stroke's head. A stroke with no deltas (a `.xopp` import, an older `.mobix`) has no timeline to
    // be precise with, and the stroke's own offset is the honest answer.
    val at = baseRef?.let { ref -> KaraokeStrokeAnimator.seekTimeMs(ref.startMs, stroke.points, xPt, yPt) }
    onAudioTap?.invoke(baseRef?.copy(startMs = at ?: baseRef.startMs))
}

/** [stroke] with the live recording position stamped on, or unchanged when nothing is recording. */
internal fun DrawingSurfaceView.withAudioStamp(stroke: Stroke): Stroke =
    audioStamp?.invoke()?.let(stroke::withAudio) ?: stroke

/**
 * Append [stroke] to the active (or top) layer of page [pageIndex], rebuilding the model.
 *
 * On a board [pageIndex] is the document page a **chunk** stands for, and the stroke goes into the cell
 * the gesture began in — creating the board's chunk when the pen went down in a cell it had none for
 * ([BoardInk.writeAt]), which is what makes an infinite canvas writable rather than only drawable. The
 * cell's own page is the store, so nothing is staged in the tile cache: eviction cannot lose the ink.
 */
internal fun DrawingSurfaceView.appendStroke(pageIndex: Int, stroke: Stroke) {
    // On a board the stroke goes into the chunk under the pen rather than onto a page of the stack
    // (DrawingSurfaceBoardInk).
    if (board != null) {
        appendChunkStroke(pageIndex, stroke)
        return
    }
    val edited = windowEditPage(pageIndex) { page -> withStroke(page, stroke) } ?: return
    relayout() // rebuild boxes so they reference the updated pages, not stale ones
}

/**
 * [page] with [stroke] appended to its active (or top) layer — the one commit shape ink has, shared by
 * a page stack's ink and a board's (`DrawingSurfaceBoardInk`).
 */
internal fun DrawingSurfaceView.withStroke(page: Page, stroke: Stroke): Page {
    val layers = page.layers.ifEmpty { listOf(Layer(emptyList())) }.toMutableList()
    val target = resolvedActiveLayer(page).coerceIn(0, layers.lastIndex)
    // Stamping here rather than at each commit site covers freehand, shapes and splines alike.
    layers[target] = Layer(layers[target].elements + withAudioStamp(stroke), layers[target].name)
    return page.copy(layers = layers)
}

/**
 * The font a **figure's own labels** are drawn in (a plot's tick numbers, a dimension's measurement),
 * and its size in pt — the text tool's own default, at 8 pt.
 *
 * Shared beyond this file on purpose: the synthesis export labels a figure it compiles into ink
 * (`XoppSynthesis`), and a table's cells and a plot's ticks must come out in the same hand the app
 * would have drawn them in on screen.
 */
internal const val FIGURE_LABEL_FONT = "Sans"
internal const val FIGURE_LABEL_SIZE_PT = 8.0

/** The layer new ink lands on for [page]: [activeLayerIndex] when in range, else the top layer. */
internal fun DrawingSurfaceView.resolvedActiveLayer(page: Page): Int =
    if (activeLayerIndex in page.layers.indices) activeLayerIndex else page.layers.lastIndex

/**
 * Apply the eraser disc to page [pageIndex] in the current [eraserMode], on the selected layer
 * only and skipping hidden layers ([PageEraser]). Returns true if anything on the page changed.
 */
internal fun DrawingSurfaceView.eraseOnPage(pageIndex: Int, px: Double, py: Double, radius: Double, mode: EraserMode = eraserMode): Boolean {
    if (board != null) return eraseFromChunk(pageIndex, px, py, radius, mode)
    val page = pageAt(pageIndex) ?: return false
    val hidden = page.layers.indices.filter { isLayerHidden(pageIndex, it) }.toSet()
    val target = resolvedActiveLayer(page)
    // `erase` answers null when the disc touched nothing; handing the page straight back keeps this a
    // no-op for the window, so a wipe that erased nothing records no undo step.
    val erased = windowEditPage(pageIndex) { PageEraser.erase(it, px, py, radius, mode, hidden, target) ?: it }
    if (erased == null) return false
    relayout()
    return true
}
