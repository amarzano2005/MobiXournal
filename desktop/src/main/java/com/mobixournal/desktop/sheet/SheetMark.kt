package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.shell.isEraserSlot
import com.mobixournal.desktop.shell.ringInk
import com.mobixournal.desktop.shell.ringWidthPt
import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.PenPacketStroke
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.shell.toolCursorDiameterPx
import com.mobixournal.desktop.tools.DesktopTool

import androidx.compose.ui.graphics.Color

/**
 * What the tool's pointer is on the sheet: the ring it promises, and whether a mouse move may follow it.
 *
 * The ring is the size of the tool ([toolCursorDiameterPx]) — a pen, a highlighter, an eraser, a figure,
 * vertical space, the selection — and null for the slots that do not mark the page at a width. The
 * `followMouse` flag is the other half: a mouse move that merely **copies** the pen's contact must not
 * drag the ring back behind the packets still arriving.
 *
 * Lifted out of `SheetCanvas` so that composable stays the surface — the canvas is already the page's
 * layout and wiring (`SourceStructureTest` counts both).
 */
internal class SheetMark(
    val ringPx: Float?,
    /** The ink the ring wears — the colour the tool in hand will write (`ringInk`). */
    val ringColour: Color,
    /** True for the two erasers, whose ring carries the eraser's own mark instead of promising an ink. */
    val eraser: Boolean,
    val followMouse: () -> Boolean,
)

/** The mark [tool] leaves under the pointer on [sheet]. */
internal fun sheetMark(
    tool: DesktopTool,
    ink: DesktopInk,
    shapeInk: DesktopInk,
    sheet: SheetLayout,
    density: Float,
    pointer: PenPointer,
): SheetMark = SheetMark(
    ringPx = toolCursorDiameterPx(tool, tool.ringWidthPt(ink, shapeInk), sheet.zoom, density),
    ringColour = ringInk(tool, ink, shapeInk),
    eraser = isEraserSlot(tool),
    followMouse = { System.nanoTime() - pointer.atNanos.value > PenPacketStroke.HOVER_STALE_NANOS },
)
