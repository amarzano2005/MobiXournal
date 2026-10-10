package com.mobixournal.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app has exactly **one** menu material: `ToolbarMenu` (see `ToolbarPopup.kt`), which pins a
 * pop-up's fill to the toolbars' own colour and its corner to the chrome's own. A raw
 * `DropdownMenu` anywhere else silently brings Material3's paler, squarer panel back — it compiles,
 * it looks nearly right, and the difference is only visible by eye on a device, which is exactly the
 * kind of regression that survives review. So the invariant is checked mechanically instead: only
 * the file that *defines* the wrapper may call `DropdownMenu`.
 *
 * The check reads the source tree because that is where the mistake happens: `DropdownMenu` is not
 * a parameter of anything we control, so no runtime assertion can see a second call site. Unit tests
 * run with the module directory as their working directory, hence the `src/main/java` path;
 * [mainSources] fails loudly rather than passing vacuously if it is run from somewhere else.
 */
class ToolbarMenuGuardTest {

    /**
     * A call of Material3's menu, not of its items: `\b` after `DropdownMenu` keeps
     * `DropdownMenuItem(` — which every pop-up legitimately uses — out of the match.
     */
    private val menuCall = Regex("""\bDropdownMenu\s*\(""")

    @Test
    fun onlyTheWrapperThatDefinesToolbarMenuCallsDropdownMenu() {
        val offenders = mainSources().filter { menuCall.containsMatchIn(it.readText()) }

        assertEquals(
            "a pop-up must be built with ToolbarMenu(...), not a raw DropdownMenu(...) — " +
                "callers found: ${offenders.map { it.path }}",
            1,
            offenders.size,
        )
        val owner = offenders.single()
        assertTrue(
            "the one file calling DropdownMenu must be the wrapper itself (${owner.path}), so it " +
                "must define `fun ToolbarMenu(`",
            owner.readText().contains("fun ToolbarMenu("),
        )
    }

    /**
     * The pattern is the guard, so it is guarded: `DropdownMenuItem(` must not match it, or every
     * pop-up in the app would be reported as an offender and the failure would be noise.
     */
    @Test
    fun thePatternDoesNotFireOnMenuItems() {
        assertTrue("a menu item is not a menu", !menuCall.containsMatchIn("DropdownMenuItem("))
        assertTrue("nor is an import", !menuCall.containsMatchIn("import androidx.compose.material3.DropdownMenu"))
        assertTrue("but a call is", menuCall.containsMatchIn("DropdownMenu("))
        assertTrue("however it is qualified", menuCall.containsMatchIn("androidx.compose.material3.DropdownMenu("))
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
