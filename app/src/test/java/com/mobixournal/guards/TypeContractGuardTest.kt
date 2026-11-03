package com.mobixournal.guards

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tablet asks only for weights **the family actually ships**.
 *
 * `Type.kt` builds Inter from three files — `inter_regular`, `inter_medium`, `inter_bold` — so the family
 * has 400, 500 and 700 and nothing else. A `FontWeight.SemiBold` (600) is therefore not a weight this
 * product owns: the renderer answers with the nearest face it has, so a label meant to be one step quieter
 * than a title comes out at 700, and the quiet/enumerated scale the theme promises is a lie at exactly the
 * places a reader looks first (a picked tab, a settings header, a dialog's own name).
 *
 * The window had already recorded this rule — `DesktopTheme.tab` says it, and `DesktopThemeTest` asserts it
 * ("a weight the family does not ship is not asked for"). The tablet asked for 600 in twelve files until
 * this sweep existed, which is how one product came to draw one label in two weights. A fourth face would
 * be a decision: add it to `Type.kt`, then to [SHIPPED] here, in one change.
 */
class TypeContractGuardTest {

    @Test
    fun `the tablet asks only for weights the family ships`() {
        val offenders = ArrayList<String>()
        for (file in sources()) {
            file.readLines().forEachIndexed { index, line ->
                Regex("""FontWeight\.([A-Za-z]+)""").findAll(line).forEach { found ->
                    val weight = found.groupValues[1]
                    if (weight !in SHIPPED) offenders += "${at(file)}:${index + 1} asks $weight"
                }
                // A numeric weight is the same request spelled another way (`FontWeight(600)`).
                if (NUMERIC.containsMatchIn(line)) offenders += "${at(file)}:${index + 1} asks a numeric weight"
            }
        }
        assertTrue(
            "the family ships 400, 500 and 700 (`Type.kt`) — anything else resolves to a neighbour, so the " +
                "role's own weight is not what is drawn: $offenders",
            offenders.isEmpty(),
        )
    }

    /** A guard that measures nothing passes vacuously: the sweep has to see the chrome it claims to read. */
    @Test
    fun `the sweep reads the chrome`() {
        val names = sources().map { it.name }
        assertTrue("expected the tablet's ui sources, found ${names.size} under ${ui()}", names.size > 50)
        assertTrue("a chrome file this rule exists for went unread under ${ui()}", "TabStrip.kt" in names)
    }

    /** The three faces `Type.kt` builds Inter from — `SHIPPED` is the whole list, by design. */
    private companion object {
        val SHIPPED = setOf("Normal", "Medium", "Bold")
        val NUMERIC = Regex("""FontWeight\(\s*\d+\s*\)""")
    }

    /** The tablet's `ui` package, found from wherever Gradle runs the test (`app/` or the repository root). */
    private fun ui(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            for (candidate in listOf("src/main/java/com/mobixournal/ui", "app/src/main/java/com/mobixournal/ui")) {
                val found = File(dir, candidate)
                if (File(found, "theme/Type.kt").isFile) return found
            }
            dir = dir.parentFile
        }
        throw AssertionError("no .../ui/theme/Type.kt above ${File("").absolutePath}")
    }

    private fun sources(): List<File> =
        ui().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** A source's path as a reader names it, with `/` on every host. */
    private fun at(file: File): String = "ui/" + file.relativeTo(ui()).invariantSeparatorsPath

}
