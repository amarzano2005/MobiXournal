package com.mobixournal.desktop

import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.settings.skippedUpdateVersion
import com.mobixournal.desktop.settings.theme
import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **Skip this version** outlives the window: the build's string lands in `desktop.properties` and a
 * reopened store reads the same answer, which is what lets the launch check stay quiet for it.
 */
class UpdateSkipTest {

    @Test
    fun `a skipped build is written to the settings file and comes back`() {
        val file = File.createTempFile("mobixournal-skip", ".properties").apply { delete() }
        try {
            val fresh = DesktopSettings(file)
            assertNull("a window that skipped nothing has no entry", fresh.skippedUpdateVersion)

            fresh.skippedUpdateVersion = "1.4.1"

            assertEquals("a restart reads the skipped build", "1.4.1", DesktopSettings(file).skippedUpdateVersion)
            val stored = Properties().apply { file.inputStream().use(::load) }
            assertEquals(
                "the key is the one docs/updates.md names",
                "1.4.1",
                stored.getProperty("update.skippedVersion"),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `the rest of the settings file survives the write`() {
        val file = File.createTempFile("mobixournal-skip", ".properties").apply { delete() }
        try {
            val store = DesktopSettings(file)
            store.skippedUpdateVersion = "1.4.1"
            assertEquals("the skip did not rewrite the other choices", store.theme, DesktopSettings(file).theme)
        } finally {
            file.delete()
        }
    }
}
