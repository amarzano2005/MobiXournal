package com.mobixournal.ui

import kotlin.math.roundToInt

/**
 * Pure model, slot-resolution, layout-morphing geometry, and persistence helpers for the
 * Focus mode floating Liquid Glass bar. Kept free of Android framework types so every rule
 * can be verified directly on the JVM (`FocusBarGeometryTest`).
 */
const val FOCUS_BAR_MIN_SLOTS = 3
const val FOCUS_BAR_MAX_SLOTS = 10

/** Tools and quick controls that can occupy a slot in the Focus mode floating bar. */
enum class FocusBarSlotKind(
    val id: String,
    val label: String,
    val category: String,
) {
    PEN("pen", "Pen", "Default tools"),
    HIGHLIGHTER("highlighter", "Highlighter", "Default tools"),
    COLORS("colors", "Quick colours", "Default tools"),
    ERASER("eraser", "Eraser", "Default tools"),
    HAND("hand", "Hand", "Default tools"),
    SELECT("select", "Selection", "Default tools"),
    UNDO("undo", "Undo", "Extra tools"),
    REDO("redo", "Redo", "Extra tools"),
    TEXT("text", "Text & LaTeX", "Extra tools"),
    IMAGE("image", "Insert image", "Extra tools"),
    VSPACE("vspace", "Vertical space", "Extra tools"),
    PAGES("pages", "Pages", "Panels"),
    LAYERS("layers", "Layers", "Panels"),
    LINE("line", "Line", "Shapes & STEM"),
    RECTANGLE("rectangle", "Rectangle", "Shapes & STEM"),
    ELLIPSE("ellipse", "Circle / Ellipse", "Shapes & STEM"),
    TRIANGLE("triangle", "Triangle", "Shapes & STEM"),
    TRAPEZOID("trapezoid", "Trapezoid", "Shapes & STEM"),
    POLYGON("polygons", "Polygons", "Shapes & STEM"),
    ARROW("arrow", "Arrows", "Shapes & STEM"),
    AXIS("axis", "Coordinate axis", "Shapes & STEM"),
    GRAPH("graph", "Function graph", "Shapes & STEM"),
    TABLE("table", "Table", "Shapes & STEM"),
    CIRCUIT("circuit", "Passive circuits", "Shapes & STEM"),
    CIRCUIT_ACTIVE("circuit_active", "Active circuits", "Shapes & STEM"),
    LOGIC("logic", "Logic gates", "Shapes & STEM"),
    GUIDES("guides", "Drawing guides", "Shapes & STEM"),
    ;

    companion object {
        private val byId = entries.associateBy { it.id }

        fun fromId(id: String): FocusBarSlotKind? = byId[id]
    }
}

/** Factory-default 6 slots in Focus mode. */
val FOCUS_BAR_DEFAULT_SLOTS: List<FocusBarSlotKind> = listOf(
    FocusBarSlotKind.PEN,
    FocusBarSlotKind.HIGHLIGHTER,
    FocusBarSlotKind.COLORS,
    FocusBarSlotKind.ERASER,
    FocusBarSlotKind.HAND,
    FocusBarSlotKind.SELECT,
)

/**
 * Resolve stored slot IDs into a deduplicated, bounded list. Empty or completely unrecognized
 * lists fall back to [FOCUS_BAR_DEFAULT_SLOTS].
 */
fun resolveFocusBarSlots(stored: List<String>): List<FocusBarSlotKind> {
    if (stored.isEmpty()) return FOCUS_BAR_DEFAULT_SLOTS
    val resolved = stored.mapNotNull { FocusBarSlotKind.fromId(it.trim()) }
        .distinct()
        .take(FOCUS_BAR_MAX_SLOTS)
    return if (resolved.isEmpty()) FOCUS_BAR_DEFAULT_SLOTS else resolved
}

/** Encode Focus bar slot IDs for SharedPreferences, preserving every [FocusBarSlotKind]. */
fun encodeFocusBarSlotIds(ids: List<String>): String =
    ids.map { it.trim() }.filter { FocusBarSlotKind.fromId(it) != null }.distinct().joinToString(",")

/** Decode Focus bar slot IDs from SharedPreferences without dropping non-rail entries like `colors` or `hand`. */
fun decodeFocusBarSlotIds(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(',').map { it.trim() }.filter { FocusBarSlotKind.fromId(it) != null }.distinct()
}

/** Replace or remove the slot at [index]; returns [slots] unchanged if removal violates [FOCUS_BAR_MIN_SLOTS]. */
fun updateFocusBarSlotList(
    slots: List<FocusBarSlotKind>,
    index: Int,
    replacement: FocusBarSlotKind?,
): List<FocusBarSlotKind> {
    if (index !in slots.indices) return slots
    if (replacement == null) {
        return if (slots.size > FOCUS_BAR_MIN_SLOTS) {
            slots.toMutableList().apply { removeAt(index) }
        } else {
            slots
        }
    }
    val existingIndex = slots.indexOf(replacement)
    val mutable = slots.toMutableList()
    if (existingIndex >= 0 && existingIndex != index) {
        // Swap so choosing an already-present tool never duplicates or drops a slot silently.
        mutable[existingIndex] = mutable[index]
    }
    mutable[index] = replacement
    return mutable
}

/** Append the first unused [FocusBarSlotKind] if under [FOCUS_BAR_MAX_SLOTS]. */
fun appendFocusBarSlot(slots: List<FocusBarSlotKind>): List<FocusBarSlotKind> {
    if (slots.size >= FOCUS_BAR_MAX_SLOTS) return slots
    val next = FocusBarSlotKind.entries.firstOrNull { it !in slots } ?: return slots
    return slots + next
}

/** Toggle [kind] in [slots]: remove if present (respecting [FOCUS_BAR_MIN_SLOTS]), or append if absent (respecting [FOCUS_BAR_MAX_SLOTS]). */
fun toggleFocusBarSlot(slots: List<FocusBarSlotKind>, kind: FocusBarSlotKind): List<FocusBarSlotKind> {
    val existing = slots.indexOf(kind)
    if (existing >= 0) {
        return if (slots.size > FOCUS_BAR_MIN_SLOTS) {
            slots.toMutableList().apply { removeAt(existing) }
        } else {
            slots
        }
    }
    return if (slots.size < FOCUS_BAR_MAX_SLOTS) slots + kind else slots
}

/** Move the slot at [index] by [delta] (-1 left/up, +1 right/down) within bounds. */
fun moveFocusBarSlot(slots: List<FocusBarSlotKind>, index: Int, delta: Int): List<FocusBarSlotKind> {
    val target = index + delta
    if (index !in slots.indices || target !in slots.indices) return slots
    return slots.toMutableList().apply {
        val item = this[index]
        this[index] = this[target]
        this[target] = item
    }
}

/** Append [kind] if it is not already present and under [FOCUS_BAR_MAX_SLOTS]. */
fun addFocusBarSlot(slots: List<FocusBarSlotKind>, kind: FocusBarSlotKind): List<FocusBarSlotKind> {
    if (kind in slots || slots.size >= FOCUS_BAR_MAX_SLOTS) return slots
    return slots + kind
}

/** Move the slot at [from] to index [to], shifting intermediate slots while preserving order. */
fun reorderFocusBarSlot(slots: List<FocusBarSlotKind>, from: Int, to: Int): List<FocusBarSlotKind> {
    if (from !in slots.indices || to !in slots.indices || from == to) return slots
    return slots.toMutableList().apply {
        val item = removeAt(from)
        add(to, item)
    }
}

/** Edge-dock state driving the floating bar's liquid horizontal ↔ vertical orientation. */
enum class FocusBarDock {
    FREE,
    LEFT_EDGE,
    RIGHT_EDGE,
    BOTTOM_EDGE,
    ;

    val isVertical: Boolean get() = this == LEFT_EDGE || this == RIGHT_EDGE
}

/** Persisted normalized placement of the Focus floating bar across orientation/size changes. */
data class FocusBarAnchor(
    val dock: FocusBarDock = FocusBarDock.FREE,
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.94f,
)

fun encodeFocusBarAnchor(anchor: FocusBarAnchor): String =
    "${anchor.dock.name}:${"%.4f".format(java.util.Locale.US, anchor.xFraction.coerceIn(0f, 1f))}:" +
        "%.4f".format(java.util.Locale.US, anchor.yFraction.coerceIn(0f, 1f))

fun decodeFocusBarAnchor(raw: String?): FocusBarAnchor {
    if (raw.isNullOrBlank()) return FocusBarAnchor()
    val parts = raw.split(':')
    if (parts.size != 3) return FocusBarAnchor()
    val dock = runCatching { FocusBarDock.valueOf(parts[0].trim()) }.getOrNull() ?: FocusBarDock.FREE
    val xf = parts[1].trim().toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f
    val yf = parts[2].trim().toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.94f
    return FocusBarAnchor(dock, xf, yf)
}

/** Per-gesture drag tracking state kept across pointer move events. */
data class FocusBarDragState(
    val dock: FocusBarDock,
    val grabFracX: Float,
    val grabFracY: Float,
    /** Extreme finger coordinate reached while docked (min X for LEFT, max X for RIGHT, max Y for BOTTOM). */
    val dockReferencePx: Float = Float.NaN,
    /** Side dock temporarily suppressed after corner transitions until the finger pulls clear of that edge. */
    val blockedSideDock: FocusBarDock? = null,
)

/** Result of one drag step: updated gesture state plus clamped target top-left `(x, y)` and transition flags. */
data class FocusBarDragStep(
    val state: FocusBarDragState,
    val x: Float,
    val y: Float,
    val dockChanged: Boolean,
    val axisChanged: Boolean,
)

/**
 * Compute clamped top-left `(x, y)` for the Focus bar at its active size `(bw, bh)` and [dock]
 * using the live finger coordinates and grab fractions.
 */
fun focusBarRenderOffsetPx(
    dock: FocusBarDock,
    fingerX: Float,
    fingerY: Float,
    grabFracX: Float,
    grabFracY: Float,
    bw: Float,
    bh: Float,
    containerW: Float,
    containerH: Float,
    marginPx: Float,
    topSafePx: Float,
    bottomSafePx: Float,
): Pair<Float, Float> {
    val rawX = when (dock) {
        FocusBarDock.LEFT_EDGE -> marginPx
        FocusBarDock.RIGHT_EDGE -> containerW - bw - marginPx
        FocusBarDock.FREE, FocusBarDock.BOTTOM_EDGE -> fingerX - bw * grabFracX
    }
    val rawY = when (dock) {
        FocusBarDock.BOTTOM_EDGE -> containerH - bh - bottomSafePx
        else -> fingerY - bh * grabFracY
    }
    return clampFocusBarPosition(rawX, rawY, bw, bh, containerW, containerH, marginPx, topSafePx, bottomSafePx)
}

/**
 * Pure, oscillation-free drag state machine for the Focus floating bar:
 * - No premature side-edge magnet: [FocusBarDock.FREE] follows the finger 1:1 until the finger enters
 *   [sideFingerZonePx] or pushes the horizontal bar [sideOverpushPx] past the outer container boundary,
 *   and never sticks to [FocusBarDock.BOTTOM_EDGE].
 * - No handle-push or corner oscillation: leaving a docked edge requires pulling inward/upward by
 *   [undockPullPx] from the extreme finger coordinate reached while docked; touching the bottom from
 *   a vertical side dock unfolds into [FocusBarDock.BOTTOM_EDGE] with corner re-dock suppression.
 */
fun stepFocusBarDrag(
    state: FocusBarDragState,
    fingerX: Float,
    fingerY: Float,
    horizW: Float,
    horizH: Float,
    currentMorph: Float,
    containerW: Float,
    containerH: Float,
    marginPx: Float,
    topSafePx: Float,
    bottomSafePx: Float,
    undockPullPx: Float,
    sideFingerZonePx: Float,
    sideOverpushPx: Float,
): FocusBarDragStep {
    if (containerW <= 0f) {
        return FocusBarDragStep(state.copy(dock = FocusBarDock.FREE), marginPx, topSafePx, false, false)
    }
    val rawLeft = fingerX - horizW * state.grabFracX
    val rawRight = rawLeft + horizW
    val activeH = lerpF(horizH, horizW, currentMorph.coerceIn(0f, 1f))
    val rawBottom = fingerY + (1f - state.grabFracY) * activeH
    val hitBottom = containerH > 0f &&
        (rawBottom >= containerH - bottomSafePx || fingerY >= containerH - bottomSafePx - sideFingerZonePx)
    val hitLeft = rawLeft <= -sideOverpushPx || fingerX <= sideFingerZonePx
    val hitRight = rawRight >= containerW + sideOverpushPx || fingerX >= containerW - sideFingerZonePx
    val activeBlockedSide = when (state.blockedSideDock) {
        FocusBarDock.LEFT_EDGE ->
            if (fingerX > sideFingerZonePx + undockPullPx && rawLeft > -sideOverpushPx) null else state.blockedSideDock
        FocusBarDock.RIGHT_EDGE ->
            if (fingerX < containerW - sideFingerZonePx - undockPullPx && rawRight < containerW + sideOverpushPx) {
                null
            } else {
                state.blockedSideDock
            }
        else -> null
    }

    val (nextDock, trackedRef, nextBlockedSide) = resolveNextDockAndRef(
        state = state,
        fingerX = fingerX,
        fingerY = fingerY,
        hitLeft = hitLeft,
        hitRight = hitRight,
        hitBottom = hitBottom,
        activeBlockedSide = activeBlockedSide,
        containerW = containerW,
        containerH = containerH,
        undockPullPx = undockPullPx,
        sideFingerZonePx = sideFingerZonePx,
    )
    val dockChanged = nextDock != state.dock
    val axisChanged = state.dock.isVertical != nextDock.isVertical
    val (nextFracX, nextFracY, nextRef) = if (!dockChanged) {
        Triple(state.grabFracX, state.grabFracY, trackedRef)
    } else {
        transitionGrabAndRef(state, nextDock, fingerX, fingerY, horizW, containerW, marginPx)
    }
    val targetW = if (nextDock.isVertical) horizH else horizW
    val targetH = if (nextDock.isVertical) horizW else horizH
    val (cx, cy) = focusBarRenderOffsetPx(
        dock = nextDock,
        fingerX = fingerX,
        fingerY = fingerY,
        grabFracX = nextFracX,
        grabFracY = nextFracY,
        bw = targetW,
        bh = targetH,
        containerW = containerW,
        containerH = containerH,
        marginPx = marginPx,
        topSafePx = topSafePx,
        bottomSafePx = bottomSafePx,
    )
    return FocusBarDragStep(
        state = FocusBarDragState(nextDock, nextFracX, nextFracY, nextRef, nextBlockedSide),
        x = cx,
        y = cy,
        dockChanged = dockChanged,
        axisChanged = axisChanged,
    )
}

private fun resolveNextDockAndRef(
    state: FocusBarDragState,
    fingerX: Float,
    fingerY: Float,
    hitLeft: Boolean,
    hitRight: Boolean,
    hitBottom: Boolean,
    activeBlockedSide: FocusBarDock?,
    containerW: Float,
    containerH: Float,
    undockPullPx: Float,
    sideFingerZonePx: Float,
): Triple<FocusBarDock, Float, FocusBarDock?> = when (state.dock) {
    FocusBarDock.FREE -> when {
        activeBlockedSide != FocusBarDock.LEFT_EDGE && hitLeft -> Triple(FocusBarDock.LEFT_EDGE, fingerX, null)
        activeBlockedSide != FocusBarDock.RIGHT_EDGE && hitRight -> Triple(FocusBarDock.RIGHT_EDGE, fingerX, null)
        else -> Triple(FocusBarDock.FREE, Float.NaN, activeBlockedSide)
    }
    FocusBarDock.LEFT_EDGE -> {
        val bestX = if (state.dockReferencePx.isFinite()) minOf(state.dockReferencePx, fingerX) else fingerX
        when {
            hitBottom -> Triple(FocusBarDock.BOTTOM_EDGE, fingerY, FocusBarDock.LEFT_EDGE)
            fingerX > sideFingerZonePx && fingerX >= bestX + undockPullPx -> Triple(FocusBarDock.FREE, Float.NaN, null)
            else -> Triple(FocusBarDock.LEFT_EDGE, bestX, null)
        }
    }
    FocusBarDock.RIGHT_EDGE -> {
        val bestX = if (state.dockReferencePx.isFinite()) maxOf(state.dockReferencePx, fingerX) else fingerX
        when {
            hitBottom -> Triple(FocusBarDock.BOTTOM_EDGE, fingerY, FocusBarDock.RIGHT_EDGE)
            fingerX < containerW - sideFingerZonePx && fingerX <= bestX - undockPullPx ->
                Triple(FocusBarDock.FREE, Float.NaN, null)
            else -> Triple(FocusBarDock.RIGHT_EDGE, bestX, null)
        }
    }
    FocusBarDock.BOTTOM_EDGE -> {
        val bestY = if (state.dockReferencePx.isFinite()) maxOf(state.dockReferencePx, fingerY) else fingerY
        when {
            activeBlockedSide != FocusBarDock.LEFT_EDGE && hitLeft -> Triple(FocusBarDock.LEFT_EDGE, fingerX, null)
            activeBlockedSide != FocusBarDock.RIGHT_EDGE && hitRight -> Triple(FocusBarDock.RIGHT_EDGE, fingerX, null)
            containerH > 0f && fingerY <= bestY - undockPullPx ->
                Triple(FocusBarDock.FREE, Float.NaN, activeBlockedSide)
            else -> Triple(FocusBarDock.BOTTOM_EDGE, bestY, activeBlockedSide)
        }
    }
}

private fun transitionGrabAndRef(
    state: FocusBarDragState,
    nextDock: FocusBarDock,
    fingerX: Float,
    fingerY: Float,
    horizW: Float,
    containerW: Float,
    marginPx: Float,
): Triple<Float, Float, Float> = when (nextDock) {
    FocusBarDock.LEFT_EDGE, FocusBarDock.RIGHT_EDGE -> Triple(0.5f, state.grabFracY, fingerX)
    FocusBarDock.BOTTOM_EDGE -> {
        val fracX = if (state.dock.isVertical) {
            undockGrabFractionX(state.dock, fingerX, horizW, containerW, marginPx)
        } else {
            state.grabFracX
        }
        Triple(fracX, 0.5f, fingerY)
    }
    FocusBarDock.FREE -> {
        val fracX = if (state.dock.isVertical) {
            undockGrabFractionX(state.dock, fingerX, horizW, containerW, marginPx)
        } else {
            state.grabFracX
        }
        val fracY = if (state.dock.isVertical) state.grabFracY else 0.5f
        Triple(fracX, fracY, Float.NaN)
    }
}

/**
 * Compute the grab fraction `[0.05f, 0.95f]` when undocking from [fromDock] back to horizontal so
 * the unfurled horizontal bar starts cleanly inside [edgeMarginPx] without jumping or re-docking.
 */
fun undockGrabFractionX(
    fromDock: FocusBarDock,
    fingerX: Float,
    horizBarW: Float,
    containerWidth: Float,
    edgeMarginPx: Float,
): Float {
    val w = horizBarW.coerceAtLeast(1f)
    return when (fromDock) {
        FocusBarDock.LEFT_EDGE -> ((fingerX - edgeMarginPx) / w).coerceIn(0.05f, 0.95f)
        FocusBarDock.RIGHT_EDGE -> (1f - (containerWidth - edgeMarginPx - fingerX) / w).coerceIn(0.05f, 0.95f)
        FocusBarDock.FREE, FocusBarDock.BOTTOM_EDGE -> 0.5f
    }
}

/**
 * Interpolated outer dimensions `(widthPx, heightPx)` of the floating capsule at [morphProgress]
 * (`0f` = horizontal, `1f` = vertical).
 */
fun focusBarSizePx(
    slotCount: Int,
    slotPx: Float,
    gapPx: Float,
    handlePx: Float,
    paddingPx: Float,
    morphProgress: Float,
): Pair<Float, Float> {
    val t = morphProgress.coerceIn(0f, 1f)
    val count = slotCount.coerceAtLeast(1)
    val main = paddingPx * 2f + handlePx + count * (gapPx + slotPx)
    val cross = paddingPx * 2f + slotPx
    return lerpF(main, cross, t) to lerpF(cross, main, t)
}

/**
 * Interpolated top-left `(xPx, yPx)` for child [childIndex] inside the floating bar where index `0`
 * is the drag handle and indices `1..slotCount` are the tool slots.
 */
fun focusBarChildOffsetPx(
    childIndex: Int,
    slotPx: Float,
    gapPx: Float,
    handlePx: Float,
    paddingPx: Float,
    morphProgress: Float,
): Pair<Float, Float> {
    val t = morphProgress.coerceIn(0f, 1f)
    val hMain = if (childIndex == 0) {
        paddingPx
    } else {
        paddingPx + handlePx + gapPx + (childIndex - 1) * (slotPx + gapPx)
    }
    val hCross = paddingPx
    // Slight tangential arc during mid-morph keeps slots from colliding while turning the corner.
    val arcOffset = kotlin.math.sin(t * kotlin.math.PI.toFloat()) * (gapPx * 0.6f) * (if (childIndex % 2 == 0) 1f else -1f)
    val x = lerpF(hMain, hCross, t) + arcOffset * (1f - kotlin.math.abs(t - 0.5f) * 2f).coerceAtLeast(0f)
    val y = lerpF(hCross, hMain, t)
    return x to y
}

/** Clamp a floating bar's top-left `(x, y)` inside the container's safe insets. */
fun clampFocusBarPosition(
    x: Float,
    y: Float,
    barW: Float,
    barH: Float,
    containerW: Float,
    containerH: Float,
    marginPx: Float,
    topSafePx: Float,
    bottomSafePx: Float,
): Pair<Float, Float> {
    val maxX = (containerW - barW - marginPx).coerceAtLeast(marginPx)
    val minY = topSafePx.coerceAtLeast(marginPx)
    val maxY = (containerH - barH - bottomSafePx).coerceAtLeast(minY)
    return x.coerceIn(marginPx, maxX) to y.coerceIn(minY, maxY)
}

/** Compute the settled snap target `(x, y)` when a drag ends in [dock]. */
fun snapFocusBarPosition(
    dock: FocusBarDock,
    x: Float,
    y: Float,
    barW: Float,
    barH: Float,
    containerW: Float,
    containerH: Float,
    edgeMarginPx: Float,
    topSafePx: Float,
    bottomSafePx: Float,
): Pair<Float, Float> {
    val targetX = when (dock) {
        FocusBarDock.LEFT_EDGE -> edgeMarginPx
        FocusBarDock.RIGHT_EDGE -> containerW - barW - edgeMarginPx
        FocusBarDock.FREE, FocusBarDock.BOTTOM_EDGE -> x
    }
    val targetY = when (dock) {
        FocusBarDock.BOTTOM_EDGE -> containerH - barH - bottomSafePx
        else -> y
    }
    return clampFocusBarPosition(
        x = targetX,
        y = targetY,
        barW = barW,
        barH = barH,
        containerW = containerW,
        containerH = containerH,
        marginPx = edgeMarginPx,
        topSafePx = topSafePx,
        bottomSafePx = bottomSafePx,
    )
}

/** Resolve pixel `(x, y)` from a persisted [FocusBarAnchor] when the viewport initializes or resizes. */
fun resolveFocusBarPixelPosition(
    anchor: FocusBarAnchor,
    barW: Float,
    barH: Float,
    containerW: Float,
    containerH: Float,
    edgeMarginPx: Float,
    topSafePx: Float,
    bottomSafePx: Float,
): Pair<Float, Float> {
    val minY = topSafePx.coerceAtLeast(edgeMarginPx)
    val maxY = (containerH - barH - bottomSafePx).coerceAtLeast(minY)
    val rawY = minY + (maxY - minY) * anchor.yFraction.coerceIn(0f, 1f)
    val maxX = (containerW - barW - edgeMarginPx).coerceAtLeast(edgeMarginPx)
    val rawX = edgeMarginPx + (maxX - edgeMarginPx) * anchor.xFraction.coerceIn(0f, 1f)
    return snapFocusBarPosition(
        dock = anchor.dock,
        x = rawX,
        y = rawY,
        barW = barW,
        barH = barH,
        containerW = containerW,
        containerH = containerH,
        edgeMarginPx = edgeMarginPx,
        topSafePx = topSafePx,
        bottomSafePx = bottomSafePx,
    )
}

/** Convert current pixel `(x, y)` into normalized fractions `[0f, 1f]` for persistence. */
fun focusBarAnchorFromPixels(
    dock: FocusBarDock,
    x: Float,
    y: Float,
    barW: Float,
    barH: Float,
    containerW: Float,
    containerH: Float,
    edgeMarginPx: Float,
    topSafePx: Float,
    bottomSafePx: Float,
): FocusBarAnchor {
    val maxX = (containerW - barW - edgeMarginPx).coerceAtLeast(edgeMarginPx)
    val minY = topSafePx.coerceAtLeast(edgeMarginPx)
    val maxY = (containerH - barH - bottomSafePx).coerceAtLeast(minY)
    val xf = if (maxX > edgeMarginPx) ((x - edgeMarginPx) / (maxX - edgeMarginPx)).coerceIn(0f, 1f) else 0.5f
    val yf = if (maxY > minY) ((y - minY) / (maxY - minY)).coerceIn(0f, 1f) else 0.94f
    return FocusBarAnchor(dock = dock, xFraction = xf, yFraction = yf)
}

/** Preferred direction a tool's popover opens relative to the floating bar so it stays inside the page. */
enum class FocusPopoverSide {
    ABOVE,
    BELOW,
    RIGHT,
    LEFT,
}

fun popoverSideFor(
    dock: FocusBarDock,
    barCenterY: Float,
    containerH: Float,
): FocusPopoverSide = when (dock) {
    FocusBarDock.LEFT_EDGE -> FocusPopoverSide.RIGHT
    FocusBarDock.RIGHT_EDGE -> FocusPopoverSide.LEFT
    FocusBarDock.BOTTOM_EDGE -> FocusPopoverSide.ABOVE
    FocusBarDock.FREE -> if (barCenterY < containerH * 0.35f) FocusPopoverSide.BELOW else FocusPopoverSide.ABOVE
}

private fun lerpF(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

internal fun Float.roundToIntPx(): Int = roundToInt()
