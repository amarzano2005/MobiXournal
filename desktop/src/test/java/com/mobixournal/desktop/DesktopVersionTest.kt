package com.mobixournal.desktop

import com.mobixournal.desktop.settings.DESKTOP_VERSION
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window's version number has two homes that must agree: `compose.desktop`'s `packageVersion`, which
 * names the MSI and is what Windows shows in the app's properties, and [DESKTOP_VERSION], which the About
 * row reads and the tablet names as the build it speaks to.
 *
 * Only the first is written by a release, so the second fell a version behind and 1.2.0's installer
 * reported 1.1.0 inside the app. The pair is asserted rather than remembered.
 */
class DesktopVersionTest {

    @Test
    fun theAboutRowNamesThePackagedVersion() {
        val declared = Regex("""packageVersion\s*=\s*"([^"]+)"""")
            .find(repoFile("desktop/build.gradle.kts").readText())
            ?.groupValues
            ?.get(1)
        assertEquals(
            "DESKTOP_VERSION must be desktop/build.gradle.kts's packageVersion",
            declared,
            DESKTOP_VERSION,
        )
    }

    @Test
    fun theVersionIsThreeNumericParts() {
        assertTrue(
            "the MSI version is read by Windows as a number, so no suffix: $DESKTOP_VERSION",
            Regex("""\d+\.\d+\.\d+""").matches(DESKTOP_VERSION),
        )
    }

    /** The module's working directory is `desktop/`, so walk up to the repository root. */
    private fun repoFile(name: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, name)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("$name not found above ${System.getProperty("user.dir")}")
    }
}
