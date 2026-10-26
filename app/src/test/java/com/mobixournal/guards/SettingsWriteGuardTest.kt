package com.mobixournal.guards

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Settings have one writer** (`MainActivity.commitSettings`, reached through
 * `MainActivity.updateSettings`).
 *
 * The bug this guards is the one that lost a recording preference: a control saved
 * `settingsStore.load().copy(…)` on its own, so the window's live `AppSettings` never learned about
 * the choice — and the next, unrelated change wrote the old value straight back over it. A second
 * call site is not a style question, it is the bug returning, and it is invisible in a build or a
 * unit test because the store really did change.
 *
 * So the invariant is checked mechanically: no source file may call `settingsStore.save(` except the
 * activity that owns the writer. Reading the store (`settingsStore.load()`) stays legal — the writer
 * itself loads it once at launch, and `SettingsStore` is the file it persists to.
 */
class SettingsWriteGuardTest {

    private val storeWrite = Regex("""\bsettingsStore\s*\.\s*save\s*\(""")

    @Test
    fun onlyTheOwnersSingleWriterSavesSettings() {
        val offenders = mainSources().filter { storeWrite.containsMatchIn(it.readText()) }
        assertEquals(
            "settings must be saved through MainActivity.commitSettings/updateSettings, not by a " +
                "control writing the store itself — callers found: ${offenders.map { it.path }}",
            1,
            offenders.size,
        )
        assertTrue(
            "the one writer lives in MainActivity.kt",
            offenders.single().name == "MainActivity.kt",
        )
    }

    @Test
    fun theWriterIsTheOneEveryControlCalls() {
        val activity = mainSources().first { it.name == "MainActivity.kt" }.readText()
        assertTrue("the single writer is named", activity.contains("fun commitSettings(next: AppSettings)"))
        assertTrue("and a change reads the live object", activity.contains("fun updateSettings(change: (AppSettings) -> AppSettings)"))
    }

    /** Every Kotlin source file of the app's main source set. */
    private fun mainSources(): List<File> {
        val root = listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.isDirectory }
            ?: error(
                "no main source set from ${File(".").absolutePath}: this test must run with the " +
                    "module directory (app/) as its working directory",
            )
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }
}
