package com.mobixournal.desktop

import com.mobixournal.design.PenPalette
import com.mobixournal.render.PressureCurve
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's **settings backup**, end to end: export writes every choice, import reads it back into a
 * window that had different ones, and anything that is not a backup changes nothing.
 *
 * This is the tablet's Backup section on a desktop, and the test is the one its own backup has: the file is
 * the only thing that carries a configuration between machines, so a key that quietly does not travel is a
 * setting the reader only discovers is missing on the other machine.
 */
class DesktopSettingsBackupTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(name: String) = DesktopSettings(File(temp.root, name))

    /** A choice from each corner of the file: the pen, the strips, a document, the palette. */
    private fun configured(file: File): DesktopSettingsState {
        val policy = DesktopSettingsState(DesktopSettings(file))
        policy.chooseStripPlacement(StripPlacement.RIGHT)
        policy.choosePressureEnabled(false)
        policy.chooseMinimumPressure(0.3f)
        policy.choosePressureMultiplier(2.5f)
        policy.chooseFigureWidth(4f)
        policy.chooseNewDocumentFormat(DesktopNewDocumentFormat.MOBIX)
        policy.moveToolSlot(0, 1)
        return policy
    }

    @Test
    fun `an exported backup is read back into another window`() {
        val exported = configured(File(temp.root, "source.properties"))
        val backup = File(temp.root, "settings" + DesktopSettingsBackup.EXTENSION)
        exported.exportBackup(backup)
        assertTrue("the file exists", backup.isFile)
        assertEquals("the answer is a sentence", "Settings exported to ${backup.name}.", exported.backupNotice)

        val target = DesktopSettingsState(DesktopSettings(File(temp.root, "target.properties")))
        target.importBackup(backup)

        assertEquals(StripPlacement.RIGHT, target.stripPlacement)
        assertFalse("sensitivity travelled", target.pressureEnabled)
        assertEquals(0.3f, target.minimumPressure, 1e-6f)
        assertEquals(2.5f, target.pressureMultiplier, 1e-6f)
        assertEquals(4f, target.figureWidth, 1e-6f)
        assertEquals(DesktopNewDocumentFormat.MOBIX, target.newDocumentFormat)
        assertEquals(exported.toolGroups.map { it.face }, target.toolGroups.map { it.face })

        // The file itself holds them, so the next window opens on the backup rather than on the old ones.
        val reopened = DesktopSettingsState(DesktopSettings(File(temp.root, "target.properties")))
        assertEquals(StripPlacement.RIGHT, reopened.stripPlacement)
        assertEquals(2.5f, reopened.pressureMultiplier, 1e-6f)
        assertFalse(reopened.pressureEnabled)
    }

    /** A backup is a configuration, not a patch: a key it does not carry is a key that was not set. */
    @Test
    fun `an import replaces the file rather than merging into it`() {
        val policy = DesktopSettingsState(DesktopSettings(File(temp.root, "merge.properties")))
        policy.chooseStripPlacement(StripPlacement.TOP)
        policy.choosePenStream(PenStream.WINTAB)
        val backup = File(temp.root, "partial" + DesktopSettingsBackup.EXTENSION)
        // What an older build's export looks like: the stamp, and one setting.
        backup.writeText(
            DesktopSettingsBackup.MARKER_KEY + "=" + DesktopSettingsBackup.MARKER + "\n" +
                DesktopSettingsBackup.VERSION_KEY + "=1\n" +
                DesktopSettings.KEY_TOOL_PLACEMENT + "=RIGHT\n",
        )

        policy.importBackup(backup)

        assertEquals(StripPlacement.RIGHT, policy.stripPlacement)
        assertEquals("a key the backup did not carry is back to its default", PenStream.AUTOMATIC, policy.penStream)
        assertEquals("the pen's floor is the default again", PressureCurve.MINIMUM_PRESSURE_DEFAULT, policy.minimumPressure, 1e-6f)
        assertEquals(PenPalette.DEFAULT_WIDTHS[1], policy.figureWidth, 1e-6f)
    }

    /** A file that is not a backup, or one from a newer build, is refused with a reason and changes nothing. */
    @Test
    fun `a file that is not a backup is refused`() {
        val policy = DesktopSettingsState(DesktopSettings(File(temp.root, "refuse.properties")))
        policy.chooseStripPlacement(StripPlacement.RIGHT)

        val other = File(temp.root, "other.properties")
        other.writeText("some.other=setting\n")
        policy.importBackup(other)
        assertEquals(DesktopSettingsBackup.BackupCheck.NotABackup.message, policy.backupNotice)
        assertEquals("nothing changed", StripPlacement.RIGHT, policy.stripPlacement)

        val newer = File(temp.root, "newer" + DesktopSettingsBackup.EXTENSION)
        newer.writeText(
            DesktopSettingsBackup.MARKER_KEY + "=" + DesktopSettingsBackup.MARKER + "\n" +
                DesktopSettingsBackup.VERSION_KEY + "=" + (DesktopSettingsBackup.CURRENT_VERSION + 1) + "\n",
        )
        policy.importBackup(newer)
        assertTrue(
            "the refusal names the format it cannot read",
            policy.backupNotice!!.contains("newer MobiXournal"),
        )
        assertEquals(StripPlacement.RIGHT, policy.stripPlacement)

        val missing = File(temp.root, "missing" + DesktopSettingsBackup.EXTENSION)
        policy.importBackup(missing)
        assertTrue("a missing file is a refusal, not a crash", policy.backupNotice!!.startsWith("The backup could not be read"))
    }
}
