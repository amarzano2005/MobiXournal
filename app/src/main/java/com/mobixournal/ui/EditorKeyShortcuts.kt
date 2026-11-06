package com.mobixournal.ui

import android.content.SharedPreferences
import com.mobixournal.render.DrawingSurfaceView

/**
 * The **keyboard's own routing table**: which toggle, colour or tool a pressed character means.
 *
 * It lives beside the toggle keys it reads (and out of `EditorRegions.kt`, which the structure guard
 * holds at its ceiling) so the routing is one readable list instead of a lambda buried in the pane's
 * bindings. Every branch goes through the same entry a rail slot, the barrel button or a finger
 * gesture uses — `activateTool` for a tool, the caller's own pen switch for a colour,
 * `summonRingForShortcut` for the ring — so a key can never mean something the user could not also
 * do by hand. `false` means "not a shortcut", which lets the key event fall through to the rest of
 * the editor.
 *
 * Precedence is fixed and shared with the settings page (`docs/chrome-settings.md`): toggles first,
 * so a toggle key always wins over a tool that happens to share the character, then colour, then tool.
 */
internal fun DrawingSurfaceView.routeShortcutKey(
    char: String,
    ui: EditorUiState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    switchToPen: (Int) -> Unit,
): Boolean {
    val hit: (String) -> Boolean = { it.isNotEmpty() && char.equals(it, ignoreCase = true) }

    /** Into the eraser the rail's slot stands for, and back to the tool that was live before it. */
    if (hit(settings.penEraserToggleKey)) {
        val previous = ui.tool
        ui.toggleTool(ui.eraserSwitchTarget(settings))
        activateTool(ui.tool, ui, settings, onSettingsChange, previous)
        return true
    }
    if (hit(settings.handToggleKey)) {
        val target = if (ui.tool == EditorTool.HAND) EditorTool.PEN else EditorTool.HAND
        activateTool(target, ui, settings, onSettingsChange)
        return true
    }
    // The Select toggle goes through the same entry the barrel's double-click uses: back to the tool
    // that was live before, never to a hard-coded pen.
    if (hit(settings.selectToggleKey)) {
        val previous = ui.tool
        ui.toggleTool(EditorTool.SELECT)
        activateTool(ui.tool, ui, settings, onSettingsChange, previous)
        return true
    }
    if (hit(settings.fullPageToggleKey)) {
        ui.fullPage = !ui.fullPage
        // The chrome decides what the canvas is asked to draw, so the tool is re-applied after the flip
        // — exactly as the barrel double-click and the hand centre double-tap do.
        applyTool(ui.tool)
        return true
    }
    if (hit(settings.ringShortcutKey)) {
        summonRingForShortcut()
        return true
    }

    // Colour shortcut: one key per palette colour, each switching to the pen in that swatch.
    settings.colorShortcutKeys.entries.firstOrNull { hit(it.value) }?.let { entry ->
        switchToPen(entry.key)
        return true
    }

    // Tool shortcut: one key per tool, activated the same way as from the rail or the barrel button.
    settings.toolShortcutKeys.entries.firstOrNull { hit(it.value) }?.let { entry ->
        activateTool(entry.key, ui, settings, onSettingsChange)
        return true
    }
    return false
}

/**
 * The `SharedPreferences` keys of the five **toggle** shortcuts.
 *
 * They live here, beside the router that reads them and the settings rows that write them, rather
 * than in `AppSettings`'s own constant block: that file is held at its recorded line ceiling, and a
 * table of shortcut keys is a shortcut concern, not a model one. The strings are the ones earlier
 * builds wrote, so an existing install keeps every key it had set.
 */
internal object ToggleKeys {
    const val ERASER = "pen_eraser_toggle_key"
    const val HAND = "hand_toggle_key"
    const val SELECT = "select_toggle_key"
    const val PAGE = "full_page_toggle_key"
    const val RING = "ring_shortcut_key"
}

/**
 * [this] with the five toggle keys read from [prefs], each falling back to the value it already
 * carries — the default for a fresh install, and untouched by a store that predates a key.
 *
 * One function rather than five constructor arguments in `load()`: the keys are a small table that
 * belongs beside [ToggleKeys], and `AppSettings` is held at its line ceiling.
 */
internal fun AppSettings.withToggleKeys(prefs: SharedPreferences): AppSettings = copy(
    penEraserToggleKey = prefs.getString(ToggleKeys.ERASER, penEraserToggleKey) ?: penEraserToggleKey,
    handToggleKey = prefs.getString(ToggleKeys.HAND, handToggleKey) ?: handToggleKey,
    selectToggleKey = prefs.getString(ToggleKeys.SELECT, selectToggleKey) ?: selectToggleKey,
    fullPageToggleKey = prefs.getString(ToggleKeys.PAGE, fullPageToggleKey) ?: fullPageToggleKey,
    ringShortcutKey = prefs.getString(ToggleKeys.RING, ringShortcutKey) ?: ringShortcutKey,
)

/** Write the five toggle keys in one call, so `save` stays a list of topics rather than of fields. */
internal fun SharedPreferences.Editor.putToggleShortcuts(s: AppSettings): SharedPreferences.Editor =
    putString(ToggleKeys.ERASER, s.penEraserToggleKey)
        .putString(ToggleKeys.HAND, s.handToggleKey)
        .putString(ToggleKeys.SELECT, s.selectToggleKey)
        .putString(ToggleKeys.PAGE, s.fullPageToggleKey)
        .putString(ToggleKeys.RING, s.ringShortcutKey)
