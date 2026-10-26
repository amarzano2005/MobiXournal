package com.mobixournal.desktop

import java.io.File
import java.util.Properties

/**
 * The window's **settings backup**: every choice in `desktop.properties`, written to a file the reader
 * chooses and read back on another machine or another build.
 *
 * The tablet has had this since its Backup section (`AppSettingsBackup`): export the configuration, import
 * it later, and **refuse** anything that is not a backup this build understands rather than quietly loading
 * half a configuration. The window's file is a properties file, so the backup is the same file with a
 * stamp: [MARKER_KEY] says what it is and [VERSION_KEY] how it was written, which is what makes a stale or
 * unrelated file a refusal with a reason instead of a window that starts up with somebody else's settings.
 *
 * The import is **wholesale** ([DesktopSettings.restore]): a backup is a configuration, so a key it does not
 * carry is a key that was not set when it was written. Older backups import (missing keys fall back to the
 * defaults on the next read); a newer one is refused, because this build cannot promise to keep what it
 * does not know.
 */
object DesktopSettingsBackup {

    /** The property that identifies the file. */
    const val MARKER_KEY = "backup.format"

    /** What [MARKER_KEY] holds, in every backup this window writes. */
    const val MARKER = "MobiXournal desktop settings"

    /** The property carrying the format's version. */
    const val VERSION_KEY = "backup.version"

    /** The format this build writes; a file stamped higher is refused. */
    const val CURRENT_VERSION = 1

    /** The suffix a backup file is proposed with. */
    const val EXTENSION = ".properties"

    /** Write [store]'s every choice into [target], stamped as a backup of this build. */
    fun write(store: DesktopSettings, target: File) {
        val properties = Properties()
        properties.setProperty(MARKER_KEY, MARKER)
        properties.setProperty(VERSION_KEY, CURRENT_VERSION.toString())
        for ((key, value) in store.snapshot()) properties.setProperty(key, value)
        target.absoluteFile.parentFile?.mkdirs()
        target.outputStream().use { out ->
            properties.store(out, "MobiXournal desktop settings backup")
        }
    }

    /** Check a **picked file** before importing it: only a backup this build reads is accepted. */
    fun read(file: File): BackupCheck {
        val properties = Properties()
        try {
            file.inputStream().use(properties::load)
        } catch (failure: Exception) {
            return BackupCheck.Unreadable(failure.message ?: "the file could not be read")
        }
        if (properties.getProperty(MARKER_KEY)?.trim() != MARKER) return BackupCheck.NotABackup
        val version = properties.getProperty(VERSION_KEY)?.trim()?.toIntOrNull()
            ?: return BackupCheck.NotABackup
        if (version > CURRENT_VERSION) return BackupCheck.TooNew(version)
        if (version < 1) return BackupCheck.NotABackup
        val values = properties.stringPropertyNames()
            .filter { it != MARKER_KEY && it != VERSION_KEY }
            .associateWith { properties.getProperty(it) }
        return BackupCheck.Ok(values)
    }

    /** What a picked file turned out to be. [message] is the sentence the Backup section shows. */
    sealed interface BackupCheck {

        /** A backup this build understands, already read. */
        data class Ok(val values: Map<String, String>) : BackupCheck {
            override val message: String
                get() = "Read ${values.size} settings from the backup."
        }

        /** Not a settings backup: another properties file, or a file with no stamp. */
        data object NotABackup : BackupCheck {
            override val message: String get() = "That file is not a MobiXournal settings backup."
        }

        /** Written by a newer window, whose settings this build cannot keep faithfully. */
        data class TooNew(val version: Int) : BackupCheck {
            override val message: String
                get() = "That backup was written by a newer MobiXournal (format $version); this build " +
                    "reads format $CURRENT_VERSION."
        }

        /** A file that could not be read at all. */
        data class Unreadable(val reason: String) : BackupCheck {
            override val message: String get() = "The backup could not be read: $reason."
        }

        /** The one sentence every answer can be shown as. */
        val message: String
    }
}
