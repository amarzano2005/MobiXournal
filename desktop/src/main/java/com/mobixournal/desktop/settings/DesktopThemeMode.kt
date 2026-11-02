package com.mobixournal.desktop.settings

/**
 * The window's two themes, and the answer that defers to Windows.
 *
 * [SYSTEM] is the default: the window opens on whatever the desktop is set to, and a reader who wants one
 * of the other two says so and stays there. [light] is the only place the deferral is resolved, so the
 * tokens, the title bar and the Appearance row cannot disagree about which theme is live.
 */
enum class DesktopThemeMode(val label: String, val keywords: String) {
    SYSTEM("System", "follow windows desktop light dark automatic"),
    DARK("Dark", "night black oled dark"),
    LIGHT("Light", "day paper white bright light"),
    ;

    /** Whether this answer means the light palette. */
    val light: Boolean
        get() = when (this) {
            SYSTEM -> DesktopSystemTheme.isLight()
            DARK -> false
            LIGHT -> true
        }
}
