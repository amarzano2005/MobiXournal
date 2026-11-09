package com.mobixournal.desktop.settings

import java.util.Properties

/**
 * **What a new document is written as**, as its own store: the Documents page's answer, and the flag that says
 * the first run's single question about it has been answered.
 *
 * Both keys live here rather than as loose properties on `DesktopSettings`, because reading one without the
 * other is the confusion this pair exists to prevent: a stored format can come from a settings backup or from
 * the factory default, and neither of those is somebody deciding. `DesktopSettings.documentFormat` owns it.
 */
internal class DocumentFormatStore(
    private val properties: Properties,
    private val write: () -> Unit,
) {

    /** What a new tab is written as: `.mobix`, a gzip `.xopp` or a zipped `.xopp`. */
    var format: DesktopNewDocumentFormat
        get() = DesktopNewDocumentFormat.of(properties.getProperty(DesktopSettings.KEY_NEW_DOCUMENT_FORMAT))
        set(value) {
            properties.setProperty(DesktopSettings.KEY_NEW_DOCUMENT_FORMAT, value.name)
            write()
        }

    /** Whether the first run's card has been answered; it is shown until this is `true`. */
    var chosen: Boolean
        get() = properties.getProperty(DesktopSettings.KEY_DOCUMENT_FORMAT_CHOSEN)?.toBooleanStrictOrNull() ?: false
        set(value) {
            properties.setProperty(DesktopSettings.KEY_DOCUMENT_FORMAT_CHOSEN, value.toString())
            write()
        }

    /** Answer both at once — the Settings row and the first run's card are the same question. */
    fun choose(value: DesktopNewDocumentFormat) {
        properties.setProperty(DesktopSettings.KEY_NEW_DOCUMENT_FORMAT, value.name)
        properties.setProperty(DesktopSettings.KEY_DOCUMENT_FORMAT_CHOSEN, "true")
        write()
    }
}