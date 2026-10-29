package com.mobixournal.desktop

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
    followMouse = { System.nanoTime() - pointer.atNanos.value > PenPacketStroke.HOVER_STALE_NANOS },
)
