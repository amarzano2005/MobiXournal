package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.mobixournal.design.ShortcutKeys
import com.mobixournal.render.PressureCurve

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

    init {
        // The reading shape is a module-level seam the two ink adapters read (`PressureFeel`): the state is
        // its only writer, so opening a window puts the stored curve in force before the first stroke.
        PressureFeel.exponent = store.pressureExponent
    }

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

    /**
     * Whether the window is on the whole screen (`F11`).
     *
     * Deliberately **not** stored: a window opens as a window, and a file that remembered "full screen"
     * would take the desk from a reader who double-clicked a notebook. The rate it asks for is stored.
     */
    var fullScreen by mutableStateOf(false)
        private set

    /** The rate the window asks the display for while it is full screen; a wish the panel may refuse. */
    var targetHz by mutableStateOf(store.targetHz)
        private set

    /** The pen stream the window prefers. */
    var penStream by mutableStateOf(store.penStream)
        private set

    /** How the pen's reading is shaped before the model's pressure filter; 1.0 is untouched. */
    var pressureExponent by mutableStateOf(store.pressureExponent)
        private set

    /** Which part of the tablet's surface is mapped. */
    var activeArea by mutableStateOf(store.activeArea)
        private set

    /** The mapped area's two spans, as percentages of the surface. */
    var areaWidthPercent by mutableStateOf(store.areaWidthPercent)
        private set
    var areaHeightPercent by mutableStateOf(store.areaHeightPercent)
        private set

    /** Whether the area keeps the tablet's own ratio. */
    var aspectLock by mutableStateOf(store.aspectLock)
        private set

    /** The rows of the key table this window changed; the rest keep their factory keys. */
    var shortcuts by mutableStateOf(store.shortcuts)
        private set

    /**
     * The command that lost its key at the last binding, as the surface names it — null once it is read.
     *
     * The conflict is not a failure: [ShortcutKeys.bind] moves the key, and this is the sentence that says
     * **which** command was displaced, so a row losing it is never a silent theft.
     */
    var shortcutConflict by mutableStateOf<ShortcutEntry?>(null)
        private set

    /** The table the window resolves keys through: the factory rows with [shortcuts] laid over them. */
    val shortcutTable: Map<String, String> get() = ShortcutTable.merged(shortcuts)

    /**
     * The rectangle this choice maps, or null for the whole surface.
     *
     * The window's reader is opened from it ([DesktopTablet.open]), so the value the rows change and the
     * value the pen is mapped through cannot disagree.
     */
    val windowArea: TabletWindowArea? get() = store.windowArea()

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

    /**
     * Shape the pen's reading by [value]; the next stroke drawn reads it.
     *
     * Live, not on restart: the exponent is read per sample through a lambda the canvas holds, so dragging
     * the slider and drawing again is the whole test of it.
     */
    fun choosePressureExponent(value: Float) {
        val shaped = value.coerceIn(PressureCurve.EXPONENT_MIN, PressureCurve.EXPONENT_MAX)
        pressureExponent = shaped
        PressureFeel.exponent = shaped
        store.pressureExponent = shaped
    }

    /** Map [value]'s part of the tablet's surface, and remember it; the window reopens its reader. */
    fun chooseActiveArea(value: TabletArea) {
        activeArea = value
        store.activeArea = value
    }

    /**
     * Widen or narrow the mapped area, and keep the height in step while the aspect lock is on.
     *
     * The lock is the same statement as the sliders': both spans are percentages **of the surface**, so equal
     * ones are the tablet's own shape. Writing both here is what makes the height row move with this one.
     */
    fun chooseAreaWidth(percent: Int) {
        val width = percent.coerceIn(DesktopSettings.AREA_PERCENT_RANGE)
        areaWidthPercent = width
        store.areaWidthPercent = width
        if (aspectLock) chooseAreaHeight(width)
    }

    /** Change the area's height; with the lock on, the width follows instead. */
    fun chooseAreaHeight(percent: Int) {
        val height = percent.coerceIn(DesktopSettings.AREA_PERCENT_RANGE)
        areaHeightPercent = height
        store.areaHeightPercent = height
        if (aspectLock) {
            areaWidthPercent = height
            store.areaWidthPercent = height
        }
    }

    /**
     * Bind [key] to [id], and remember the table.
     *
     * One key, one command: the key's previous holder loses it and is named in [shortcutConflict]. A tool
     * may not take a `Ctrl` key — that column is the shell's, and a tool bound there would be shadowed by the
     * command it competes with — so such a press is refused and the conflict line says why instead.
     */
    fun rebind(id: String, key: String) {
        val entry = ShortcutTable.entry(id) ?: return
        if (key.isBlank()) return
        if (entry.tool != null && key.contains("Ctrl+", ignoreCase = true)) {
            shortcutConflict = entry
            return
        }
        val binding = ShortcutKeys.bind(shortcutTable, id, key)
        val factory = ShortcutTable.defaults()
        // Rows that changed, and the row the new key was taken from — filed as "no key", which is what keeps
        // it from finding its factory key again at the next start.
        val changed = binding.table.entries
            .filter { it.value != factory[it.key] }
            .associate { it.key to it.value }
        val unbound = factory.keys.filter { it !in binding.table }.associateWith { ShortcutKeys.UNBOUND }
        shortcuts = changed + unbound
        shortcutConflict = binding.displaced?.let { id -> ShortcutTable.entry(id) }
        store.shortcuts = shortcuts
    }

    /** Give every row its factory key back, and say nothing lost one. */
    fun restoreShortcuts() {
        shortcuts = emptyMap()
        shortcutConflict = null
        store.shortcuts = emptyMap()
    }

    /** The conflict sentence has been read; the next binding may write another. */
    fun clearShortcutConflict() {
        shortcutConflict = null
    }

    /** Keep the area the tablet's own ratio, or let the two spans move apart, and remember the answer. */
    fun chooseAspectLock(value: Boolean) {
        aspectLock = value
        store.aspectLock = value
        if (value) chooseAreaWidth(areaWidthPercent)
    }

    /**
     * Ask the display for [value] Hz while the window is full screen, and remember the wish.
     *
     * A machine with no such mode says so in the status bar instead: the display's real rate is what
     * [refreshLabel] always reports, so the row can be set and the readout stay true.
     */
    fun chooseTargetHz(value: Int) {
        targetHz = value
        store.targetHz = value
    }

    /** Take the whole screen, or give it back — the `F11` answer and the Display row's switch. */
    fun toggleFullScreen() {
        fullScreen = !fullScreen
    }
}
