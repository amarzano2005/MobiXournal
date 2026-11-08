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
 *  - **the writing standard** — `docs/writing-docs.md` keeps the vocabulary, the phases and the Definition
 *    of Done every doc task follows, and both front doors keep pointing at it.
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
        Claim(
            "still to come",
            "the window's selection, its action bar and the in-place editors for tables, graphs and " +
                "circuits all exist; the README called them this long after they landed, which reads as a " +
                "missing feature — describe what is there, and name what is not in TODO.toml",
        ),
        Claim(
            "feasibility proof",
            "the `desktop/` window is the second shell of the product, not a demonstration: it opens, " +
                "edits, saves and exports the same documents the tablet does (docs/codebase.md owns the " +
                "identity statement, docs/desktop-workstation.md the window)",
        ),
        Claim(
            "Android app is the product",
            "two shells share one core — naming the Android app *the* product writes the PC window out " +
                "of the product it belongs to",
        ),
        Claim(
            "companion for Android",
            "the app is not an Android companion to another editor: the document lives in its own native " +
                "format and two shells open it (docs/codebase.md)",
        ),
        Claim(
            "not checked in",
            "`udiff.xopp` and `bhm_prior.pdf` are committed at the repo root and the README hands the first " +
                "of them to a new user as a document to open; a doc calling a sample absent sends its " +
                "reader looking for something that is right there",
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
        val gradleTasks = setOf("androidTest", "connectedDebugAndroidTest", "testDebugUnitTest")
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

    /**
     * `docs/writing-docs.md` says a compatibility claim is evidenced, never asserted in adjectives.
     *
     * The evidential claim is what this project already makes (`xopp-bridge.md` states its contract and
     * names the tests), so the vague superlative is the drift: it reads as a stronger promise while
     * carrying no test behind it. `docs/releases/` is exempt — a changelog records what a release said,
     * and rewriting history is the opposite of an archive.
     */
    @Test
    fun compatibilityClaimsStayQualified() {
        val vague = listOf(
            "fully compatible",
            "100% compatible",
            "perfectly compatible",
            "completely lossless",
            "guaranteed lossless",
            "seamless conversion",
        )
        val found = mutableListOf<String>()
        for (file in prose().filter { it.parentFile?.name != "releases" }) {
            file.readLines().forEachIndexed { i, line ->
                val prose = line.replace(Regex("`[^`]*`"), " ").lowercase()
                vague.firstOrNull { it in prose }?.let {
                    found += "${file.name}:${i + 1} -> \"$it\" (name what was verified, or state the limitation)"
                }
            }
        }
        assertTrue(
            "these docs promise compatibility in adjectives instead of evidence — say which step was " +
                "verified and which was not (`docs/writing-docs.md`, *Two formats: state what survives*):\n  " +
                found.joinToString("\n  "),
            found.isEmpty(),
        )
    }

    /**
     * The writing standard is a document that must not quietly lose its own rules.
     *
     * `docs/writing-docs.md` is what every doc task follows, so its vocabulary, its phases and its
     * Definition of Done are read out of the file here: dropping a status word or a phase fails the
     * build, and so does a front door that stops pointing at it — a standard nobody links is a standard
     * nobody follows.
     */
    @Test
    fun theWritingStandardKeepsItsMandatoryParts() {
        val standard = File(repoRoot(), "docs/writing-docs.md")
        assertTrue("docs/writing-docs.md is missing — it is where a doc task's rules live", standard.isFile)
        val text = standard.readText()
        val vocabulary = listOf(
            "Supported", "Partially supported", "Experimental",
            "Planned", "Not supported", "Not verified",
        )
        val missingWords = vocabulary.filter { it !in text }
        assertTrue(
            "docs/writing-docs.md must keep the six status words every doc uses: $missingWords",
            missingWords.isEmpty(),
        )
        val missingPhases = (1..7).map { "Phase $it" }.filter { it !in text }
        assertTrue(
            "docs/writing-docs.md must keep its seven phases — a phase dropped is a step nobody runs: " +
                missingPhases,
            missingPhases.isEmpty(),
        )
        val audiences = listOf("New users", "Xournal++ users", "Regular users", "Developers", "Contributors")
        val missingAudiences = audiences.filter { it !in text }
        assertTrue(
            "docs/writing-docs.md must keep the audiences it writes for: $missingAudiences",
            missingAudiences.isEmpty(),
        )
        assertTrue(
            "docs/writing-docs.md must keep its Definition of Done",
            "Definition of Done" in text,
        )
        for (door in listOf("AGENTS.md", "README.md")) {
            assertTrue(
                "$door must point at docs/writing-docs.md: the standard is a step of a doc task only while " +
                    "both front doors send their reader to it",
                "docs/writing-docs.md" in File(repoRoot(), door).readText(),
            )
        }
    }

    /**
     * The merge engine runs on the `.mobix` open path, so no doc may call it dead code.
     *
     * `DocumentIo` merges a conflict sibling before the editor shows the document and records the
     * outcome as a notification; the surface for that notification does not exist yet, which is a gap
     * to state, not a reason to describe the engine as uncalled. The old sentences (`no caller`,
     * `nothing in the app calls it`) survived a rename precisely because they name no symbol.
     */
    @Test
    fun theMergeEngineIsNotDescribedAsDeadCode() {
        val bridge = File(repoRoot(), "docs/xopp-bridge.md").readText().replace(Regex("\\s+"), " ")
        assertTrue(
            "docs/xopp-bridge.md must state that the app calls `MobixMergeEngine` on the `.mobix` open path",
            "MobixMergeEngine" in bridge && "open path" in bridge,
        )
        val found = mutableListOf<String>()
        for (file in prose()) {
            file.readLines().forEachIndexed { i, line ->
                if ("merge" !in line.lowercase()) return@forEachIndexed
                val prose = line.replace(Regex("`[^`]*`"), " ").lowercase()
                if ("no caller" in prose || "nothing in the app calls" in prose) {
                    found += "${file.name}:${i + 1}"
                }
            }
        }
        assertTrue(
            "these sentences call the merge engine dead code, but the app calls it on the `.mobix` " +
                "open path (name the unsurfaced notification instead):\n  " + found.joinToString("\n  "),
            found.isEmpty(),
        )
    }

    /**
     * Data tables ship with their editor, so the `.mobix` capability table must not deny it.
     *
     * The row used to read *Model only — no editor exists* while `TableCellEditorDialog` was already
     * wired in the editor regions and another spoke described it; a reader scoping work from the
     * format's own honesty column would have rebuilt a feature that exists.
     */
    @Test
    fun theDataTableRowNamesTheEditorThatShips() {
        val doc = File(repoRoot(), "docs/mobix-format.md")
        val row = doc.readLines().firstOrNull { "| **Data tables**" in it }
        assertTrue("docs/mobix-format.md must keep its data-table capability row", row != null)
        assertTrue(
            "the data-table row must state the shipping status, not 'no editor exists': $row",
            row!!.contains("**Supported**") && "no editor exists" !in row,
        )
    }

    /**
     * The front door names the one conversion the window refuses.
     *
     * A packaged `.xopp` whose background lives inside the package cannot be written back faithfully
     * by the window, which refuses it and leaves the file byte-identical. `Save As converts cleanly in
     * either direction` promised otherwise at the exact moment a user hands the app their file.
     */
    @Test
    fun theFrontDoorNamesTheConversionItRefuses() {
        val readme = File(repoRoot(), "README.md").readText()
        assertTrue(
            "README.md must name the packaged `.xopp` the Windows window refuses to rewrite",
            "packaged" in readme && "refuses" in readme,
        )
        assertTrue(
            "README.md must not promise conversion 'cleanly in either direction' — the window refuses " +
                "one case (docs/desktop-documents.md owns the refusal)",
            "converts cleanly in either direction" !in readme,
        )
    }

    /**
     * Typst is a preserved source, not a typesetter.
     *
     * `MathFlavor.TYPST` keeps and marks the source; every rendering path typesets through the LaTeX
     * parser and layout engine. Advertising `rendered as crisp vectors` bought a feature the build
     * does not have, and the failure surfaced after a user had typed their first formula.
     */
    @Test
    fun theTypstClaimStaysQualified() {
        val found = mutableListOf<String>()
        for (file in prose()) {
            file.readLines().forEachIndexed { i, line ->
                val lower = line.lowercase()
                if ("typst" in lower && ("crisp vector" in lower || "rendered as" in lower)) {
                    found += "${file.name}:${i + 1}"
                }
            }
        }
        assertTrue(
            "a Typst source is preserved and marked, not typeset: state the limitation instead of " +
                "promising rendered vectors:\n  " + found.joinToString("\n  "),
            found.isEmpty(),
        )
    }

    /**
     * The engine's advanced curves have a placement boundary, and the doc says so.
     *
     * Parametric and polar curves are drawn, exported and round-tripped, but `MultiFunctionPlotter`
     * places `y = f(x)` only — the reader who plans a UI for polar plots from the engine section needs
     * that sentence in the same place as the feature.
     */
    @Test
    fun thePlotPlacementBoundaryIsStated() {
        val text = File(repoRoot(), "docs/math-engine.md").readText()
        assertTrue(
            "docs/math-engine.md must state that the plot tool places Cartesian curves only — a polar " +
                "curve draws and exports, but cannot be created from it",
            "places Cartesian curves only" in text,
        )
    }

    /** One forbidden sentence, and why it is forbidden. */
    private data class Claim(val text: String, val why: String, val onlyAbout: String? = null)

    /**
     * The titles `DesktopSettingSection` declares, in declaration order.
     *
     * Found by name under the window's source tree rather than by its old flat path: the window's code is
     * split into topic packages ([code-structure.md](code-structure.md)), and this guard is about what the doc
     * *says*, not about where the file sits.
     */
    private fun desktopSettingsEnum(): List<String> {
        val file = File(repoRoot(), "desktop/src/main/java/com/mobixournal/desktop")
            .walkTopDown()
            .firstOrNull { it.isFile && it.name == "SettingsSections.kt" }
            ?: return emptyList()
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
