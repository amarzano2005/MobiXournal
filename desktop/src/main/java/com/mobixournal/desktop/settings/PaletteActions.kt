package com.mobixournal.desktop.settings
import com.mobixournal.desktop.ink.DesktopInkState

import com.mobixournal.design.PenPalette

/**
 * The **Colors** page's subject: the palette the strips draw, and the writes the page may make.
 *
 * Lambdas, not copies. The live palette is the strip's own Compose state in [DesktopInkState], so a
 * swatch redefined on the settings page *is* the swatch the stroke panel shows, the moment it is
 * committed — a `val palette` field here would be a second copy of the list, and a settings page that
 * offers colours the pen does not have is worse than no page at all.
 *
 * The defaults are the page rendered outside the window — a fixture, a headless test — where nothing is
 * writable and the factory eight is all there is to show.
 */
internal class PaletteActions(
    /** The palette as it is now: the same list the colour flyout and the pen-tip ring draw. */
    val palette: () -> List<Int> = { PenPalette.COLORS },
    val pick: (Int) -> Unit = {},
    val define: (index: Int, colour: Int) -> Unit = { _, _ -> },
    val add: (Int) -> Unit = {},
    val remove: (index: Int) -> Unit = {},
    val restore: () -> Unit = {},
) {

    companion object {

        /** The shell's own palette, read and written through [ink] — one palette, two surfaces. */
        fun of(ink: DesktopInkState): PaletteActions = PaletteActions(
            palette = { ink.palette },
            pick = ink::pickColour,
            define = ink::definePaletteColour,
            add = ink::addPaletteColour,
            remove = ink::removePaletteColour,
            restore = ink::resetPalette,
        )
    }
}
