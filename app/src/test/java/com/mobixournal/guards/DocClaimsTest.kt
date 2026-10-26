package com.mobixournal.guards

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The docs' **drift** guard: the statements no other check can witness.
 *
 * `DocsGuardTest` enforces the shape of the doc set — the budget, the index, the headings, the links,
 * the code files and task ids a doc names. What it cannot see is a sentence that is simply no longer
 * true: a doc calling the PC window *read-only* weeks after it learned to save, a Settings spoke listing
 * nine sections while the enum has ten, a test named in a doc that was renamed. Those have no broken
 * link and no missing file, so they survive review and mislead the next reader — they did exactly that
 * here twice, which is why they are now checked:
 *
 *  - **[FORBIDDEN]** — sentences that were false once and must not come back. Adding one is a decision
 *    to state (the same rule as `proseThatLooksLikeATaskId` and the source-size ceilings).
 *  - **the Settings parity test** — the doc's section list is derived from `DesktopSettingSection`, so
 *    a section added to the window fails the build until the doc names it. This is the model for the
 *    other enumerable facts: anything the code lists, the doc's list is derived from, not retyped.
 *  - **the test-class check** — a doc naming `DesktopSomethingTest` names a class that exists.
 */
class DocClaimsTest {

    /**
     * Sentences that were false in this repo, and the near-variants of them.
     *
     * Each entry is a literal fragment, matched case-insensitively on one line. They describe a
     * capability the code has (or no longer has) — a rename cannot catch them, so they live here.
     */
    private val forbidden = listOf(
        Claim(
            "read-only",
            "the desktop window opens, paints, edits, saves and exports; calling it read-only is the " +
                "sentence this guard exists for — describe what it does, or link " +
                "desktop-workstation.md",
            onlyAbout = "desktop",
        ),
        Claim(
            "does not edit",
            "the `desktop/` window edits ink, figures, living elements, text and the selection",
        ),
    )

    @Test
    fun forbiddenStatementsStayOut() {
        val found = mutableListOf<String>()
        for (file in prose()) {
            file.readLines().forEachIndexed { i, line ->
                // A claim is an assertion about what the app does; `read-only` in backticks is a mention
                // of the word (this guard's own documentation quotes it), so code spans do not count.
                val prose = line.replace(Regex("`[^`]*`"), " ")
                for (claim in forbidden) {
                    if (!prose.contains(claim.text, ignoreCase = true)) continue
                    val context = claim.onlyAbout
                    if (context != null && !prose.contains(context, ignoreCase = true)) continue
                    found += "${file.name}:${i + 1} -> \"${claim.text}\" (${claim.why})"
                }
            }
        }
        assertTrue(
            "these docs restate something that is no longer true:\n  " + found.joinToString("\n  "),
            found.isEmpty(),
        )
    }

    /**
     * The window's Settings doc lists the sections the window **has**.
     *
     * Both sides come from `SettingsSections.kt`: the titles are parsed from the enum and the count from
     * its size, so adding a section (or dropping one) fails here until `desktop-settings.md` says so.
     * This is the shape to copy for the next enumerable fact — a doc's list derived from the code's list,
     * never retyped beside it.
     */
    @Test
    fun theSettingsSpokeListsEverySectionTheWindowHas() {
        val enum = desktopSettingsEnum()
        assertTrue("the Settings enum could not be read — did `SettingsSections.kt` move?", enum.isNotEmpty())
        val doc = File(repoRoot(), "docs/desktop-settings.md").readText()
        val missing = enum.filter { it !in doc }
        assertTrue(
            "the window has these Settings sections and `desktop-settings.md` does not name them: " +
                missing + " — add them to the doc's list",
            missing.isEmpty(),
        )
        val countWords = listOf(
            "no", "one", "two", "three", "four", "five", "six",
            "seven", "eight", "nine", "ten", "eleven", "twelve",
        )
        val word = countWords.getOrNull(enum.size)
        assertTrue(
            "`desktop-settings.md` states the section count in words; extend the table in this test " +
                "for ${enum.size} sections",
            word != null,
        )
        assertTrue(
            "`desktop-settings.md` must say the window's index is **$word sections** (it has " +
                "${enum.size})",
            "**$word sections**" in doc,
        )
    }

    /**
     * A doc that names a test names a test that exists: renaming a suite is common, and the doc that
     * still cites the old name reads as coverage that is not there. Gradle task names (`androidTest`)
     * are not classes and are listed here deliberately.
     */
    @Test
    fun everyTestClassADocNamesExists() {
        val classes = kotlinTypes()
        val gradleTasks = setOf("androidTest", "connectedDebugAndroidTest")
        val missing = mutableListOf<String>()
        for (file in prose()) {
            file.readLines().forEachIndexed { i, line ->
                for (match in Regex("""`([A-Za-z0-9_]*Test[A-Za-z0-9_]*)`""").findAll(line)) {
                    val name = match.groupValues[1]
                    if (name in gradleTasks || name in classes) continue
                    missing += "${file.name}:${i + 1} -> `$name`"
                }
            }
        }
        assertTrue(
            "these docs name a test class the tree does not have — it was renamed or removed:\n  " +
                missing.joinToString("\n  "),
            missing.isEmpty(),
        )
    }

    /** One forbidden sentence, and why it is forbidden. */
    private data class Claim(val text: String, val why: String, val onlyAbout: String? = null)

    /** The titles `DesktopSettingSection` declares, in declaration order. */
    private fun desktopSettingsEnum(): List<String> {
        val file = File(
            repoRoot(),
            "desktop/src/main/java/com/mobixournal/desktop/SettingsSections.kt",
        )
        if (!file.isFile) return emptyList()
        val body = file.readText().substringAfter("enum class DesktopSettingSection")
        return Regex("""(?m)^\s{4}[A-Z_]+\(\s*\n\s*"([^"]+)"""")
            .findAll(body).map { it.groupValues[1] }.toList()
    }

    /** Every `class`/`object` name declared in a Kotlin source file of this checkout. */
    private fun kotlinTypes(): Set<String> {
        val names = mutableSetOf<String>()
        for (file in sources()) {
            for (line in file.readLines()) {
                Regex("""\b(?:class|object)\s+([A-Za-z0-9_]+)""").find(line)
                    ?.let { names += it.groupValues[1] }
            }
        }
        return names
    }

    /** The Kotlin sources under the repository root, build output excluded. */
    private fun sources(): List<File> {
        val root = repoRoot()
        val skipped = setOf(".git", "build", ".gradle", ".idea", "node_modules", ".kotlin")
        return root.walkTopDown()
            .onEnter { it == root || it.name !in skipped }
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    /** The docs under the claim rules: every `.md` in `docs/` (releases frozen), plus the front doors. */
    private fun prose(): List<File> {
        val docs = File(repoRoot(), "docs").listFiles { f: File -> f.isFile && f.extension == "md" }!!
            .sortedBy { it.name }
        val front = listOf("README.md", "AGENTS.md").map { File(repoRoot(), it) }
        return (docs + front).filter { it.isFile }
    }

    /** The repository root: the nearest ancestor that holds `AGENTS.md` and `docs/`. */
    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "AGENTS.md").isFile && File(dir, "docs").isDirectory) return dir
            dir = dir.parentFile
        }
        error("no repository root above ${File(".").absolutePath}: this test must run inside the repo")
    }
}
