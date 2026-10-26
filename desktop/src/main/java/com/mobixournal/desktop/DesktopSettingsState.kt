package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * The window's **Settings**, as state: whether the surface is up, which section it shows, what the search
 * field holds, and every choice the sections change.
 *
 * It is a piece of the shell's state, not a second copy of it ([DesktopShellState] owns one at
 * `state.prefs`), for the reason [DesktopInkState] is: the settings file is where a choice *lives*, this is
 * what the chrome **reads**, and every setter writes both — so the window obeys a change the moment the row
 * is clicked, and a restart finds the same answer. A row that only wrote the file would be a setting the
 * window forgot until it was reopened.
 *
 * The surface is a **section of the window**, not a dialog over the page: it takes the whole body between
 * the header and the status bar, so no glass is ever drawn on a sheet (`docs/interface-contract.md`).
 */
/** What one key means to the Settings surface. */
enum class SettingsKeyAction {
    /** The key is not the surface's: nothing is up, or the field is typing. */
    NONE,

    /** `Esc` with a search word in the field: clear it, and keep the page. */
    CLEAR_SEARCH,

    /** `Esc` on a plain surface: put it away. */
    CLOSE,

    /** Any other key while the surface is up: the surface takes it, so the page hears nothing. */
    CAPTURE,
}

class DesktopSettingsState(private val store: DesktopSettings) {

    /** Whether the surface is up. */
    var open by mutableStateOf(false)
        private set

    /** The section the surface shows; the list on its left picks another. */
    var section by mutableStateOf(DesktopSettingSection.DEFAULT)
        private set

    /** What has been typed into the search field; empty lists every section. */
    var query by mutableStateOf("")
        private set

    /**
     * True while the search field holds the keyboard.
     *
     * The page's own shortcuts are the window's, not the settings': with the field focused every letter is
     * an answer to the search, and with nothing focused in the surface no page command may fire behind it
     * either. [onKey] is what reads this.
     */
    var typing by mutableStateOf(false)

    /** Where the tool strips stand — read by `PageWorkspace`, so a change re-lays them at once. */
    var stripPlacement by mutableStateOf(store.stripPlacement)
        private set

    /** Which edge the rail takes. */
    var railSide by mutableStateOf(store.railSide)
        private set

    /** The zoom a document opens at. */
    var fitOnOpen by mutableStateOf(store.fitOnOpen)
        private set

    /** When the page list shows itself. */
    var sidebarPolicy by mutableStateOf(store.sidebarPolicy)
        private set

    /** Whether the inspector lane may open. */
    var inspectorPolicy by mutableStateOf(store.inspectorPolicy)
        private set

    /** Whether the status bar reports the paint time per frame. */
    var frameTime by mutableStateOf(store.frameTime)
        private set

    /** The pen stream the window prefers. */
    var penStream by mutableStateOf(store.penStream)
        private set

    /** The default sheet a new page is born on — the format section's stored pair, read here for a row. */
    val pageWidthPt: Double get() = store.pageWidthPt
    val pageHeightPt: Double get() = store.pageHeightPt

    /** The sections the search field leaves, in the list's own order. */
    val sections: List<DesktopSettingSection>
        get() = DesktopSettingSection.search(query)

    /** The section a click on the list opens, or the first one the search left. */
    val shownSection: DesktopSettingSection?
        get() {
            val left = sections
            return left.firstOrNull { it == section } ?: left.firstOrNull()
        }

    /**
     * Open the surface, on [section] or on the one it was last showing.
     *
     * The search is cleared: a field that reopened holding the last word would hide the sections the
     * reader came back for.
     */
    fun openAt(section: DesktopSettingSection = this.section) {
        this.section = section
        query = ""
        open = true
    }

    /** Close it — the close glyph's answer and `Esc`'s. */
    fun close() {
        open = false
        typing = false
        query = ""
    }

    /** Show [section] — a row of the index. */
    fun show(section: DesktopSettingSection) {
        this.section = section
    }

    /** Take the search field's text. */
    fun search(text: String) {
        query = text
        // A section the filter hides cannot stay the answer: the page follows the list.
        shownSection?.let { section = it }
    }

    /**
     * What one key means while the surface is up.
     *
     * The decision is a value so it can be tested: Compose's `KeyEvent` cannot be built outside a real
     * window, and "`Esc` clears the search before it closes the surface" is exactly the kind of rule worth
     * pinning (`DesktopSettingsTest`).
     */
    fun keyAction(key: Key): SettingsKeyAction = when {
        !open -> SettingsKeyAction.NONE
        key == Key.Escape && query.isNotEmpty() -> SettingsKeyAction.CLEAR_SEARCH
        key == Key.Escape -> SettingsKeyAction.CLOSE
        // A focused field's letters are the search; anything else is the surface's, so the page below
        // never hears a shortcut for a page it cannot see.
        typing -> SettingsKeyAction.NONE
        else -> SettingsKeyAction.CAPTURE
    }

    /** Answer [key] — the surface's keys, as [keyAction] reads them. */
    fun press(key: Key): Boolean = when (keyAction(key)) {
        SettingsKeyAction.NONE -> false
        SettingsKeyAction.CLEAR_SEARCH -> {
            query = ""
            shownSection?.let { section = it }
            true
        }
        SettingsKeyAction.CLOSE -> {
            close()
            true
        }
        SettingsKeyAction.CAPTURE -> true
    }

    /**
     * The window's own door: one key event from the root preview handler.
     *
     * It is called **before** the shortcut table, so its answer is final for as long as the surface is up.
     */
    fun onKey(event: KeyEvent): Boolean = event.type == KeyEventType.KeyDown && press(event.key)

    // --- the choices, each one a hook as well as a key -------------------------------------------

    /** Stand the strips at [value] (top, side or both) and remember it. */
    fun chooseStripPlacement(value: StripPlacement) {
        stripPlacement = value
        store.stripPlacement = value
    }

    /** Put the rail on [value] and remember it. */
    fun chooseRailSide(value: RailSide) {
        railSide = value
        store.railSide = value
    }

    /** Open documents at [value] from now on and remember it — the tab already open keeps its zoom. */
    fun chooseFitOnOpen(value: FitOnOpen) {
        fitOnOpen = value
        store.fitOnOpen = value
    }

    /** Show the page list by [value] and remember it. */
    fun chooseSidebarPolicy(value: SidebarPolicy) {
        sidebarPolicy = value
        store.sidebarPolicy = value
    }

    /** Let the inspector lane open by [value] and remember it. */
    fun chooseInspectorPolicy(value: InspectorPolicy) {
        inspectorPolicy = value
        store.inspectorPolicy = value
    }

    /** Report the paint time per frame, or stop reporting it, and remember the answer. */
    fun chooseFrameTime(value: Boolean) {
        frameTime = value
        store.frameTime = value
    }

    /** Prefer [value]'s pen stream and remember it; the window reopens its reader. */
    fun choosePenStream(value: PenStream) {
        penStream = value
        store.penStream = value
    }
}
