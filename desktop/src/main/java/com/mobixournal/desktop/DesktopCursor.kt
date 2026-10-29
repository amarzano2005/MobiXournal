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
 * away** for every tool that marks the page, so the ring *is* the pointer rather than a second mark
 * stuck to the mouse's own (an arrow beside a ring points at nothing). The one exception is the **hand**,
 * which moves the page instead of marking it and keeps the hand ([toolPointerIcon]).
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
 * The ring for the tool in hand, or null for the slots that do not mark the sheet at a width.
 *
 * Every tool that lays ink **at the pen's width** wears it — the pen, the highlighter, both erasers, each
 * figure, the circuit slots, vertical space and the selection — because the ring is the only thing that
 * says where and how wide the mark will be.
 *
 * Two slots do not. The **hand** carries the page instead of marking it, so it wears the hand; and **Text**
 * writes words, not a band of ink, so a circle the size of the nib promised a width no character has. It
 * wears the text cursor instead ([toolPointerIcon]) and the field under it shows the caret.
 *
 * A pen and a highlighter are as wide as their line; the **eraser is a radius** in the shared geometry
 * (`eraserRadiusPt`), which is why it goes through the same function with its own answer rather than the
 * tool's width doubled.
 */
internal fun toolCursorDiameterPx(tool: DesktopTool, widthPt: Float, zoom: Float, density: Float): Float? =
    when (tool) {
        // A tap tool has no width to promise either: the audio tap skips to a stroke, it does not draw
        // a ring of the pen's size over the page.
        DesktopTool.HAND, DesktopTool.TEXT, DesktopTool.AUDIO -> null
        // Both erasers show the tip they cut with: the whole-stroke eraser is a disc like the partial one,
        // it simply takes everything the disc touches.
        DesktopTool.ERASER, DesktopTool.ERASER_WHOLE -> cursorDiameterPx(eraserRadiusPt(widthPt).toFloat(), zoom, density)
        // Everything else — a figure, a circuit, vertical space, the marquee and the lasso — writes at the
        // pen's width, so its ring is the pen's ring at that point.
        else -> cursorDiameterPx(widthPt / 2f, zoom, density)
    }

/**
 * The width the ring is drawn at for this slot: the tool's **own** ink, or the pen's when the slot writes
 * with the pen's ([DesktopTool.inksByPackets]).
 *
 * The distinction matters at exactly one point: a figure drawn while the highlighter is in hand lands at
 * the pen's width (`DesktopShellState.shapeInk`), so its ring has to be the pen's ring — a marker-wide ring
 * over a 2 pt line would promise ink five times too thick.
 */
internal fun DesktopTool.ringWidthPt(ink: DesktopInk, shapeInk: DesktopInk): Float =
    if (inksByPackets()) ink.width else shapeInk.width

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

/** The platform's own **text cursor** — the bar every text field on the desktop wears. */
private val textCursor: Cursor by lazy {
    runCatching { Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR) }
        .getOrElse { Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) }
}

/**
 * The pointer the sheet wears for [tool].
 *
 * Three answers, one per meaning: the **hand** gets the hand, because it is the only slot that moves the
 * page rather than marking it — and `panMode` is Space held, which is that same pan with any slot picked;
 * **Text** gets the text cursor, because what a press produces there is a caret and words, not a band of
 * ink, and an I-beam is the one pointer that says so; every other tool gets **no arrow at all**, because
 * its ring is the pointer ([drawToolCursor]). An arrow beside a ring points at nothing, and a ring alone
 * is the mark the tablet draws.
 */
internal fun toolPointerIcon(tool: DesktopTool, ringVisible: Boolean, panMode: Boolean = false): PointerIcon = when {
    panMode || tool == DesktopTool.HAND -> PointerIcon.Hand
    tool == DesktopTool.TEXT -> PointerIcon(textCursor)
    ringVisible -> PointerIcon(blankCursor)
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
