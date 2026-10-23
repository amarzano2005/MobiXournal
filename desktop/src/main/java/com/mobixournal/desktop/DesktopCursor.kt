package com.mobixournal.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.dp
import com.mobixournal.render.eraserRadiusPt
import kotlin.math.max

/**
 * The window's **pen pointer**: the ring the tablet draws while a stylus hovers, standing in for the
 * desktop's arrow.
 *
 * On a workstation the arrow is the wrong instrument over paper — it hides the point you are about to
 * draw at and says nothing about how wide the line will be. The tablet already answered this with a ring
 * the size of the tool, and the window copies it: the system cursor is hidden over the page while a
 * drawing tool is active (a transparent AWT cursor, so the habit of "the cursor is where the pen is"
 * still holds if the ring is off screen), and [drawToolCursor] paints the ring at the pointer.
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
        DesktopTool.ERASER -> cursorDiameterPx(eraserRadiusPt(widthPt).toFloat(), zoom, density)
        else -> null
    }

/**
 * A cursor with nothing drawn in it, so the ring is the only pointer over the page.
 *
 * It is still a cursor (rather than no cursor at all) because a window with none anywhere is a window
 * that has lost the pointer: the hotspot keeps every AWT hit test and every hover event working, and the
 * shape is what is invisible, not the pointer.
 */
@Composable
internal fun rememberBlankPointerIcon(): PointerIcon = remember {
    val image = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val cursor = java.awt.Toolkit.getDefaultToolkit()
        .createCustomCursor(image, java.awt.Point(8, 8), "mobix-pen")
    PointerIcon(cursor)
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
