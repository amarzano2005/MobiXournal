package com.mobixournal.desktop.shell
import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.inksByPackets
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.tools.DesktopTool

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mobixournal.desktop.settings.drawToolGlyph
import com.mobixournal.design.ToolGlyph
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
        // The guides too: the slot poses an instrument, it lays no band, so a pen-sized ring would promise
        // ink it never draws.
        DesktopTool.HAND, DesktopTool.TEXT, DesktopTool.AUDIO, DesktopTool.PDF_TEXT_SELECT,
        DesktopTool.SETSQUARE, DesktopTool.COMPASS -> null
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
 *
 * The **pen-tip ring** does not change this answer: the ring's faces are aimed at with the same tool ring the
 * rest of the sheet wears, so the pointer keeps its picture-free cursor and `DesktopHudLayer` paints the ring
 * **above** the ring's glass — the surface must never hide the mark the reader points with.
 */
internal fun toolPointerIcon(
    tool: DesktopTool,
    ringVisible: Boolean,
    panMode: Boolean = false,
): PointerIcon = when {
    panMode || tool == DesktopTool.HAND -> PointerIcon.Hand
    // The PDF text-select slot reads words too: an I-beam says "drag across this line", where a ring the
    // size of the nib would promise ink no selection lays.
    tool == DesktopTool.TEXT || tool == DesktopTool.PDF_TEXT_SELECT -> PointerIcon(textCursor)
    ringVisible -> PointerIcon(blankCursor)
    else -> PointerIcon.Default
}

/**
 * The colour the ring is drawn in: the ink the tool would lay, so the pointer already shows the stroke.
 *
 * A figure or a circuit slot drawn while the highlighter is in hand writes with the pen's ink
 * ([inksByPackets]), which is why the same rule that picks the ring's **width** picks its colour. Both
 * erasers are the exception: they lay no ink, so a ring in the pen's colour would promise a stroke that
 * never arrives — they wear the ring in the chrome's ink and their own mark inside it.
 */
internal fun ringInk(tool: DesktopTool, ink: DesktopInk, shapeInk: DesktopInk): Color =
    if (isEraserSlot(tool)) DesktopTheme.ink
    else Color(if (tool.inksByPackets()) ink.colour else shapeInk.colour)

/** Whether [tool] rubs ink out rather than laying it — the slots whose ring carries the eraser's mark. */
internal fun isEraserSlot(tool: DesktopTool): Boolean =
    tool == DesktopTool.ERASER || tool == DesktopTool.ERASER_WHOLE

/**
 * Draw the ring: a dark halo under a rim in the ink in hand, so it stays visible on paper, on the black desk
 * and over ink of either colour — and says, before the stroke lands, which colour that stroke will be. Both
 * are the window's own tokens (`DesktopTheme`, one material class, `docs/interface-contract.md`).
 *
 * An **eraser** has no colour to promise, so it wears the catalogue's own eraser mark inside the disc:
 * the ring's size already says how much it cuts, and the mark says what it is.
 */
internal fun DrawScope.drawToolCursor(
    at: androidx.compose.ui.geometry.Offset,
    diameter: Float,
    colour: Color,
    eraser: Boolean,
) {
    val halo = Stroke(width = 1.5.dp.toPx())
    val rim = Stroke(width = 1.0.dp.toPx())
    drawCircle(DesktopTheme.desk, radius = diameter / 2f, center = at, style = halo)
    drawCircle(colour, radius = diameter / 2f - halo.width, center = at, style = rim)
    if (eraser) {
        val side = diameter * ERASER_MARK_FRACTION
        drawToolGlyph(
            glyph = ToolGlyph.ERASER,
            color = colour,
            side = side,
            at = at - androidx.compose.ui.geometry.Offset(side / 2f, side / 2f),
        )
    }
}

/** How much of the ring's diameter the eraser's mark takes — small enough to stay inside the disc. */
private const val ERASER_MARK_FRACTION = 0.6f
