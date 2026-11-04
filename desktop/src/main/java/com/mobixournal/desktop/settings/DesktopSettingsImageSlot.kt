package com.mobixournal.desktop.settings
import com.mobixournal.desktop.tools.DesktopTool

/**
 * The strip's **one movable command**, as a stored answer: the slot **Insert image** stands under.
 *
 * It sits beside [DesktopSettings] rather than inside it because it is neither a tool nor a slot: a tool has
 * a place in `toolbar.order`, a command has an *anchor*, and one line of its own is what keeps the store from
 * growing a second arrangement beside the first (`SourceStructureTest` holds the store to its budget).
 *
 * The value is a [DesktopTool] name; a name this build's strips no longer hold reads back as Text, which is
 * where a fresh install keeps it (`stripItems`).
 */
var DesktopSettings.imageAfter: String
    get() = properties.getProperty(KEY_IMAGE_AFTER) ?: DesktopTool.TEXT.name
    set(value) {
        properties.setProperty(KEY_IMAGE_AFTER, value)
        save()
    }

/** The property the image slot's anchor lives under. */
const val KEY_IMAGE_AFTER = "interface.imageAfter"
