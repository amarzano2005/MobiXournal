package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The pen's **saved presets**, as state: the list, the live one, and the name being typed for a new one.
 *
 * It is its own object for the reason [DesktopInkState] is: the settings store is where a preset *lives*,
 * this is what the chrome **reads**, and the two doors that show it — the menu's Pen block and the Stylus
 * page — read one list rather than each holding a copy. Every mutation writes the file in the same call, so
 * a preset survives a crash exactly as a setting does.
 *
 * A preset is only the two pressure numbers; **who else hears about a change is not this file's job**. The
 * live filter, the shared [PressureFeel] seam and the sliders' own state belong to [DesktopSettingsState],
 * which calls in here and then writes those — which is what keeps the numbers and the chip naming them from
 * ever describing two different pens.
 */
class PenPresetState(private val store: DesktopSettings) {

    /** The saved presets, in the menu's order; the factory one on a fresh install. */
    var presets by mutableStateOf(store.penPresets)
        private set

    /** Which of them is live — the chip the menu writes in the accent. */
    var selectedId by mutableStateOf(store.selectedPenPresetId)
        private set

    /** The name being typed for a new preset, or null when nothing is being saved. */
    var draft by mutableStateOf<String?>(null)
        private set

    /**
     * Make [id] the live preset and return it, or null when nothing holds that handle.
     *
     * The selection is written **before** the caller writes the two numbers: those setters rewrite the live
     * entry, and a preset that was still the previous one would take the new preset's numbers into its own
     * stored line.
     */
    fun select(id: String): DesktopPenPreset? {
        val preset = presets.firstOrNull { it.id == id } ?: return null
        selectedId = preset.id
        store.selectedPenPresetId = preset.id
        return preset
    }

    /** Rewrite the live preset's entry with the numbers a slider just moved. */
    fun rewriteLive(change: (DesktopPenPreset) -> DesktopPenPreset) {
        val at = presets.indexOfFirst { it.id == selectedId }
        if (at < 0) return
        val updated = presets.toMutableList().also { it[at] = change(it[at]) }
        if (updated == presets) return
        presets = updated
        store.penPresets = updated
    }

    /** Open the name field. */
    fun beginSave() {
        draft = ""
    }

    /** Take what has been typed into the name field. */
    fun type(text: String) {
        draft = text.take(NAME_LIMIT)
    }

    /** Put the name field away without saving. */
    fun cancel() {
        draft = null
    }

    /**
     * Save the live numbers under the typed name and make the new preset the live one.
     *
     * A name that already exists is **rewritten in place** (its id kept, so the selection and any future
     * handle survive), which is what "save these numbers as *Light hand*" means when *Light hand* is already
     * in the list. A blank name saves nothing: a preset with no name is a chip nobody can pick twice.
     */
    fun commit(minimumPressure: Float, pressureMultiplier: Float) {
        val name = draft?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val existing = presets.firstOrNull { it.name.equals(name, ignoreCase = true) }
        val preset = existing?.copy(
            minimumPressure = minimumPressure,
            pressureMultiplier = pressureMultiplier,
        ) ?: DesktopPenPreset(
            id = uniquePenPresetId(presets, name),
            name = name,
            minimumPressure = minimumPressure,
            pressureMultiplier = pressureMultiplier,
        )
        val updated = addOrUpdatePenPreset(presets, preset)
        presets = updated
        store.penPresets = updated
        selectedId = preset.id
        store.selectedPenPresetId = preset.id
        draft = null
    }

    /**
     * Delete [id], moving the selection to the first preset it leaves when the deleted one was live.
     *
     * Answers whether anything was removed: the list always keeps one entry, so deleting the last preset is
     * a no-op rather than a list with no name for the pen's numbers.
     */
    fun remove(id: String): Boolean {
        val updated = removePenPreset(presets, id)
        if (updated == presets) return false
        presets = updated
        store.penPresets = updated
        if (selectedId == id) {
            selectedId = updated.first().id
            store.selectedPenPresetId = selectedId
        }
        return true
    }

    /** Re-read the list after an imported settings backup replaced the file. */
    fun reload() {
        presets = store.penPresets
        selectedId = store.selectedPenPresetId
        draft = null
    }

    private companion object {
        /** Long enough for a sentence, short enough that the chip stays a chip. */
        const val NAME_LIMIT = 32
    }
}
