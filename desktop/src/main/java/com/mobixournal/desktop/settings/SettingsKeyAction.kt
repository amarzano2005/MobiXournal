package com.mobixournal.desktop.settings

/**
 * What one key means to the Settings surface, when it is up.
 *
 * A policy value rather than a branch inside the surface: the surface owns the keyboard while it is open, and
 * *which* of its own controls `Esc` reaches is the whole of the question — the page's commands must hear
 * nothing behind it, whether or not a field is typing.
 */
enum class SettingsKeyAction {
    /** The key is not the surface's: nothing is up, or the field is typing. */
    NONE,

    /** `Esc` with a search word in the field: clear it, and keep the page. */
    CLEAR_SEARCH,

    /** `Esc` while a preset is being named: put the field away, and keep the page. */
    CANCEL_PRESET,

    /** `Esc` on a plain surface: put it away. */
    CLOSE,

    /** Any other key while the surface is up: the surface takes it, so the page hears nothing. */
    CAPTURE,
}
