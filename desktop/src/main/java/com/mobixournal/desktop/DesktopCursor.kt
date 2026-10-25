package com.mobixournal.desktop

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.dp
import com.mobixournal.render.eraserRadiusPt
import java.awt.Cursor
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import kotlin.math.max

/**
 * The window's **pen pointer**: the ring the tablet draws while a stylus hovers, added beside the
 * desktop's arrow.
 *
 * On a workstation the arrow is the wrong instrument over paper — it hides the point you are about to
 * draw at and says nothing about how wide the line will be. The tablet already answered this with a ring
 * the size of the tool, and the window adds it: [drawToolCursor] paints the ring **and the arrow goes
 * away** while a pen, a highlighter or an eraser is in hand, so the ring *is* the pointer rather than a
 * second mark stuck to the mouse's own (an arrow beside a ring points at nothing). Every other slot
 * keeps a pointer that means something — the hand for the Hand, the arrow elsewhere ([toolPointerIcon]).
 *
 * It reports the tool, not the device: an inverted pen rubs out because the **tool** is the eraser, and
 * the packet-driven stroke reads the eraser end from the tablet (`docs/desktop-workstation.md`).
 */

/** The narrowest the ring may draw — a 1 pt pen still has to be findable. */
internal const val CURSOR_MIN_PX = 7f

/**
 * The ring's diameter in the canvas's own pixels, for a tool of [widthPt] at [zoom].
 *
 * It is the stroke's width as the page will store it, so what the ring encloses is what the lift writes,
 * with a floor so the ring never vanishes. The eraser is wider than the pen at the same setting
 * (`eraserRadiusPt`), which is why it is passed in already resolved.
 */
internal fun cursorDiameterPx(radiusPt: Float, zoom: Float, density: Float): Float =
    max(CURSOR_MIN_PX, radiusPt * 2f * zoom * density)

/**
 * The ring for the tool in hand, or null when the tool draws nothing (every other slot keeps the arrow).
 *
 * A pen and a highlighter are as wide as their line; the **eraser is a radius** in the shared geometry
 * (`eraserRadiusPt`), which is why it goes through the same function with its own answer rather than the
 * tool's width doubled.
 */
internal fun toolCursorDiameterPx(tool: DesktopTool, widthPt: Float, zoom: Float, density: Float): Float? =
    when (tool) {
        DesktopTool.PEN, DesktopTool.HIGHLIGHTER -> cursorDiameterPx(widthPt / 2f, zoom, density)
        // Both erasers show the tip they cut with: the whole-stroke eraser is a disc like the partial one,
        // it simply takes everything the disc touches.
        DesktopTool.ERASER, DesktopTool.ERASER_WHOLE -> cursorDiameterPx(eraserRadiusPt(widthPt).toFloat(), zoom, density)
        else -> null
    }

/**
 * A cursor with **no picture**, so the ring the canvas paints is the only pointer over the sheet.
 *
 * AWT's own cursors are all pictures; the transparent one is a 1 × 1 image, which the platform accepts
 * as a cursor and draws as nothing. It is created once and reused — a fresh `Cursor` per frame would be
 * a native handle per frame.
 */
private val blankCursor: Cursor by lazy {
    val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    runCatching { Toolkit.getDefaultToolkit().createCustomCursor(image, Point(0, 0), "mobix-blank") }
        // A headless toolkit cannot make one; the crosshair at least keeps the point findable.
        .getOrElse { Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) }
}

/**
 * The pointer the sheet wears for [tool].
 *
 * Three answers, one per meaning: a tool that paints the page gets **no arrow at all** (its ring is the
 * pointer, [drawToolCursor]), the hand gets the hand (it moves the page, and the arrow said the page
 * would be drawn on), and every other slot — a figure, a selection, a living placement — keeps the
 * desktop's arrow, which is what those tools act with. `panMode` is Space held, which is the hand's pan
 * with any slot picked.
 */
internal fun toolPointerIcon(tool: DesktopTool, ringVisible: Boolean, panMode: Boolean = false): PointerIcon = when {
    ringVisible -> PointerIcon(blankCursor)
    panMode || tool == DesktopTool.HAND -> PointerIcon.Hand
    else -> PointerIcon.Default
}

/**
 * Draw the ring: a dark outline under a light one, so it stays visible on paper, on the black desk and
 * over ink of either colour — the same reason the tablet's ring is drawn twice. Both are the window's own
 * tokens (`DesktopTheme`, one material class, `docs/interface-contract.md`), not a colour of its own.
 */
internal fun DrawScope.drawToolCursor(at: androidx.compose.ui.geometry.Offset, diameter: Float) {
    val dark = Stroke(width = 1.5.dp.toPx())
    val light = Stroke(width = 1.0.dp.toPx())
    drawCircle(DesktopTheme.desk, radius = diameter / 2f, center = at, style = dark)
    drawCircle(DesktopTheme.ink, radius = diameter / 2f - dark.width, center = at, style = light)
}
