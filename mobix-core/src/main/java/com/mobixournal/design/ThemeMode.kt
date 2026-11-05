package com.mobixournal.design

/**
 * Which colour scheme the app wears — the three answers, in **one** home for both shells.
 *
 * It lives here because the tablet and the window were keeping an enum each: same three names, a
 * different member order (the tablet's `SYSTEM, LIGHT, DARK` against the window's `SYSTEM, DARK, LIGHT`)
 * and the system answer resolved in two places, one of which could be — and was — wrong. A list drawn
 * from [entries] and a file that records a name now read the same in both shells.
 *
 * @property label The answer's name, as a settings row shows it.
 */
enum class ThemeMode(val label: String) {
    /** Follow the operating system's light/dark setting. */
    SYSTEM("System"),
    /** Always use the light colour scheme. */
    LIGHT("Light"),
    /** Always use the dark colour scheme. */
    DARK("Dark"),
    ;

    /** The same answer, for a shell whose default is dark rather than the system's. */
    companion object {
        /** The answer a file names, or [fallback] for a name this build does not know. */
        fun named(name: String?, fallback: ThemeMode = SYSTEM): ThemeMode =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: fallback
    }
}
