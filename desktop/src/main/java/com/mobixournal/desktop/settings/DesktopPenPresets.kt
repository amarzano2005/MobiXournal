package com.mobixournal.desktop.settings

import com.mobixournal.desktop.ink.DesktopPenPreset
import com.mobixournal.desktop.ink.decodePenPresets
import com.mobixournal.desktop.ink.encodePenPresets

/**
 * The pen's **named presets**, on the settings file: the two keys the Stylus carousel and the menu's Pen
 * block both read.
 *
 * Extensions rather than members because [DesktopSettings] is at its structural budget
 * (`SourceStructureTest`) and these two keys are one subject of their own — the pairs a name stands for, and
 * the id that lights one of the chips. The store's file and save are `internal` for exactly this.
 */

/**
 * The pen's saved pressure presets (`tablet.penPresets`) — the tablet's named pairs, in menu order.
 *
 * One line of `id;name;minimum;multiplier` entries, the same plain-text style as the strip's order. A
 * stored line this build cannot read falls back to the factory preset rather than emptying the menu, and
 * the list can never be stored empty: the last preset is the one the reader is drawing with.
 */
var DesktopSettings.penPresets: List<DesktopPenPreset>
    get() = decodePenPresets(properties.getProperty(DesktopSettings.KEY_PEN_PRESETS))
    set(value) {
        val kept = value.ifEmpty { DesktopPenPreset.DEFAULT_PRESETS }
        properties.setProperty(DesktopSettings.KEY_PEN_PRESETS, encodePenPresets(kept))
        save()
    }

/**
 * Which preset is live (`tablet.selectedPenPreset`), or the first saved one when the stored id is gone.
 *
 * Falling back rather than reporting an id nothing matches is what keeps the menu from having no chip
 * highlighted on a file that was hand-edited or written by a build with a preset this one lacks.
 */
var DesktopSettings.selectedPenPresetId: String
    get() {
        val presets = penPresets
        val stored = properties.getProperty(DesktopSettings.KEY_SELECTED_PEN_PRESET)?.trim()
        return presets.firstOrNull { it.id == stored }?.id ?: presets.first().id
    }
    set(value) {
        properties.setProperty(DesktopSettings.KEY_SELECTED_PEN_PRESET, value)
        save()
    }
