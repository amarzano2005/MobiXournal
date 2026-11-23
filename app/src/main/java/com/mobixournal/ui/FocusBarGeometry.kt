package com.mobixournal.ui

import kotlin.math.roundToInt

/**
 * Pure model, slot-resolution, layout-morphing geometry, and persistence helpers for the
 * Focus mode floating Liquid Glass bar. Kept free of Android framework types so every rule
 * can be verified directly on the JVM (`FocusBarGeometryTest`).
 */
const val FOCUS_BAR_MIN_SLOTS = 3
const val FOCUS_BAR_MAX_SLOTS = 6

/** Tools and quick controls that can occupy a slot in the Focus mode floating bar. */
enum class FocusBarSlotKind(
    val id: String,
    val label: String,
    val category: String,
) {
    PEN("pen", "Pen (tap again for stroke)", "Default tools"),
    HIGHLIGHTER("highlighter", "Highlighter (tap again for stroke)", "Default tools"),
    COLORS("colors", "Quick colours", "Default tools"),
    ERASER("eraser", "Eraser (whole / standard)", "Default tools"),
    HAND("hand", "Hand (pan)", "Default tools"),
    SELECT("select", "Selection (box / lasso)", "Default tools"),
    UNDO("undo", "Undo", "Extra tools"),
    REDO("redo", "Redo", "Extra tools"),
    TEXT("text", "Text & LaTeX", "Extra tools"),
    IMAGE("image", "Insert image", "Extra tools"),
    VSPACE("vspace", "Vertical space", "Extra tools"),
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

/**
 * Effective `(enterPx, exitPx)` edge thresholds for finger-based docking: narrow enough to keep
 * most of the screen completely free for horizontal placement, with a clean hysteresis band so
 * leaving a side edge never re-triggers docking.
 */
fun effectiveEdgeThresholds(
    containerWidth: Float,
    baseEnterPx: Float,
    baseExitPx: Float,
): Pair<Float, Float> {
    if (containerWidth <= 0f) return 0f to 1f
    val enter = minOf(baseEnterPx, containerWidth * 0.16f).coerceAtLeast(1f)
    val exit = minOf(baseExitPx, containerWidth * 0.28f).coerceAtLeast(enter + 1f)
    return enter to exit
}

/**
 * Determine the bar's dock state from the finger's horizontal position ([centerX]) and optional
 * horizontal over-push ([rawBarLeft], [rawBarRight]) past the screen edges, so dragging from the
 * left handle to the right edge morphs just as effortlessly as dragging to the left edge.
 */
fun nextFocusBarDock(
    current: FocusBarDock,
    centerX: Float,
    containerWidth: Float,
    enterEdgePx: Float,
    exitEdgePx: Float,
    rawBarLeft: Float = Float.NaN,
    rawBarRight: Float = Float.NaN,
    overPushPx: Float = 0f,
    fingerY: Float = Float.NaN,
    rawBarBottom: Float = Float.NaN,
    containerHeight: Float = 0f,
    bottomSafePx: Float = 0f,
): FocusBarDock {
    if (containerWidth <= 0f) return FocusBarDock.FREE
    val touchBottom = containerHeight > 0f && (
        (rawBarBottom.isFinite() && rawBarBottom >= containerHeight - bottomSafePx - overPushPx) ||
            (fingerY.isFinite() && fingerY >= containerHeight - bottomSafePx - enterEdgePx)
        )
    val leftBottom = containerHeight > 0f && fingerY.isFinite() && fingerY <= containerHeight - bottomSafePx - exitEdgePx
    val pushedLeft = rawBarLeft.isFinite() && rawBarLeft <= -overPushPx
    val pushedRight = rawBarRight.isFinite() && rawBarRight >= containerWidth + overPushPx
    return when (current) {
        FocusBarDock.LEFT_EDGE -> when {
            touchBottom -> FocusBarDock.BOTTOM_EDGE
            centerX >= containerWidth - enterEdgePx -> FocusBarDock.RIGHT_EDGE
            centerX >= exitEdgePx -> FocusBarDock.FREE
            else -> FocusBarDock.LEFT_EDGE
        }
        FocusBarDock.RIGHT_EDGE -> when {
            touchBottom -> FocusBarDock.BOTTOM_EDGE
            centerX <= enterEdgePx -> FocusBarDock.LEFT_EDGE
            centerX <= containerWidth - exitEdgePx -> FocusBarDock.FREE
            else -> FocusBarDock.RIGHT_EDGE
        }
        FocusBarDock.BOTTOM_EDGE -> when {
            centerX <= enterEdgePx || pushedLeft -> FocusBarDock.LEFT_EDGE
            centerX >= containerWidth - enterEdgePx || pushedRight -> FocusBarDock.RIGHT_EDGE
            leftBottom -> FocusBarDock.FREE
            else -> FocusBarDock.BOTTOM_EDGE
        }
        FocusBarDock.FREE -> when {
            centerX <= enterEdgePx || pushedLeft -> FocusBarDock.LEFT_EDGE
            centerX >= containerWidth - enterEdgePx || pushedRight -> FocusBarDock.RIGHT_EDGE
            touchBottom -> FocusBarDock.BOTTOM_EDGE
            else -> FocusBarDock.FREE
        }
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
