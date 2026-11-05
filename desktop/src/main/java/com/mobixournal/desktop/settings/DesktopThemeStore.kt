package com.mobixournal.desktop.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The window's theme, in both places it lives: the file's answer and the live one.
 *
 * Split out of [DesktopSettings] and [DesktopSettingsState] so neither file grows past the size
 * ceiling `SourceStructureTest` enforces, and so the whole topic is read in one place.
 */

/**
 * The window's theme — the Appearance row's three answers, with the system's own as the default.
 *
 * Stored by name rather than by a light flag, so the third answer (follow Windows) has somewhere to
 * live and a file this build writes stays readable in the next.
 */
var DesktopSettings.theme: DesktopThemeMode
    get() = DesktopThemeMode.named(properties.getProperty(DesktopSettings.KEY_THEME))
    set(value) {
        properties.setProperty(DesktopSettings.KEY_THEME, value.name)
        save()
    }

/**
 * The theme as the Appearance row shows it, and the one write path that moves it.
 *
 * Picking writes the file and the chrome together: every chrome colour is a getter over the theme's own
 * state (`DesktopTheme.use`), so the window changes in the same frame.
 */
internal class DesktopAppearanceState(private val store: DesktopSettings) {
    /** Whether it is the light palette, for a row that wants to say so. */
    var theme by mutableStateOf(store.theme)
        private set

    /** Pick the theme: the file keeps it and the window changes **now**. */
    fun chooseTheme(mode: DesktopThemeMode) {
        theme = mode
        store.theme = mode
        DesktopTheme.use(mode.light)
    }
}
