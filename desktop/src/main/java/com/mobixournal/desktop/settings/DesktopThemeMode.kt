package com.mobixournal.desktop.settings

import com.mobixournal.design.ThemeMode

/**
 * The window's theme answers — **the tablet's enum, not a second copy of it** (`mobix-core`'s
 * [ThemeMode]).
 *
 * The two shells used to keep an enum each: the same three names, a different member order, and the
 * system answer resolved in a different place. What is genuinely the window's is here as extensions:
 * its search aliases, and the fact that "follow the system" means asking **Windows**.
 */
typealias DesktopThemeMode = ThemeMode

/**
 * Search aliases for the Appearance row, in the shape the settings index reads (`keywords`).
 *
 * They are the window's words for its own answers — a reader who types "night" or "bright" reaches the
 * row — so they live beside the window's UI rather than in the shared enum, where a shell that does not
 * search with them would carry them for nothing.
 */
val ThemeMode.keywords: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "follow windows desktop light dark automatic"
        ThemeMode.DARK -> "night black oled dark"
        ThemeMode.LIGHT -> "day paper white bright light"
    }

/** Whether this answer paints the light palette right now — `SYSTEM` asks Windows. */
val ThemeMode.light: Boolean
    get() = when (this) {
        ThemeMode.SYSTEM -> DesktopSystemTheme.isLight()
        ThemeMode.DARK -> false
        ThemeMode.LIGHT -> true
    }
