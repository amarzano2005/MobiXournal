package com.mobixournal.desktop.tools
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.sheet.FigureShape

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.design.PenPalette

/**
 * The **figure decisions**: the width a figure is drawn at and the variant a triangle or a trapezoid takes.
 *
 * One class rather than fields spread through the settings state, because they are one subject
 * ([FigureShape]) and the page reads them together — the drag asks for the width and the variant at the
 * same instant, and neither is allowed to be the stale half of a pair. The store is written on every
 * change, so a reopened window draws the figure the last one drew.
 */
internal class FigureChoices(private val store: DesktopSettings) {

    /** The width a figure is drawn at, in points: a figure has no pressure, so it is its own answer. */
    var width by mutableStateOf(store.figureWidth)
        private set

    /** The triangle's and trapezoid's answers — the variant a drag draws, and its angles. */
    var shape by mutableStateOf(store.figureShape())
        private set

    /** The figure whose variant menu is up (`DesktopTool.TRIANGLE`, …), or null when none is. */
    var menu by mutableStateOf<DesktopTool?>(null)
        private set

    /** The figure whose angle customization dialog is active (`TRIANGLE`, `TRAPEZOID`), or null when none is. */
    var editingFigure by mutableStateOf<DesktopTool?>(null)
        private set

    /** A width the reader picked in Settings ▸ Figures, held to the palette's own range. */
    fun chooseWidth(value: Float) {
        val clamped = PenPalette.clampWidth(value)
        width = clamped
        store.figureWidth = clamped
    }

    /** One figure's answer changed — the variant, an angle a scalene one is built from, or the sides. */
    fun chooseShape(value: FigureShape) {
        shape = value
        store.writeFigureShape(value)
        // The question is answered, so its menu closes — the tablet's picker does the same.
        menu = null
    }

    /** Open [tool]'s variant menu, or close the open one with null. */
    fun showMenu(tool: DesktopTool?) {
        menu = tool
    }

    /** Open [tool]'s angle customization dialog. */
    fun openDialog(tool: DesktopTool) {
        editingFigure = tool
    }

    /** Close any active figure angle customization dialog. */
    fun closeDialog() {
        editingFigure = null
    }

    /**
     * These decisions as the strips read them: one value carrying the shape, the open menu and the two
     * commands, so a slot neither reaches into this class nor keeps a second copy of a figure's answer.
     */
    val strip: FigureStrip
        get() = FigureStrip(
            shape = shape,
            menu = menu,
            onMenu = ::showMenu,
            onShape = ::chooseShape,
            editingFigure = editingFigure,
            onOpenDialog = ::openDialog,
            onCloseDialog = ::closeDialog,
        )

    /** Read both back from the store — after a settings backup has replaced it. */
    fun reload() {
        width = store.figureWidth
        shape = store.figureShape()
        menu = null
        editingFigure = null
    }
}
