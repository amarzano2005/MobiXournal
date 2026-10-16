package com.mobixournal.guards

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AGENTS.md` says this repo keeps documentation de-duplicated — each fact in one spoke, reachable
 * from the map — and that a stale doc should be caught mechanically rather than by discipline. The
 * architecture docs are exactly the place where that discipline had failed: a single
 * `docs/architecture.md` had grown to ~2500 lines, so the doc that "owns the internals" had to be
 * loaded whole and its topics were not addressable at all. It is now an index of one-topic spokes.
 *
 * This keeps that from happening again:
 *
 *  - **every spoke is reachable from the index** — a doc nobody links is a doc nobody reads;
 *  - **every relative link resolves**, `#anchor` included, so a renamed file or heading fails here
 *    instead of silently dead-ending a reader;
 *  - **every doc fits its line budget**, which is what makes "load one topic" true.
 *
 * Unit tests run with the module directory (`app/`) as their working directory, so the repo root is
 * found by walking up; [repoRoot] fails loudly rather than passing vacuously if it is not there.
 */
class DocsGuardTest {

    /** A doc past this is a mono-doc: the point of a spoke is that one topic loads on its own. */
    private val lineBudget = 400

    /**
     * The index that has to reach every spoke. `docs/releases/` is exempt: those are the historical
     * changelogs, frozen at the version they describe and never linked from the index.
     */
    private val index = "architecture.md"

    @Test
    fun everySpokeIsReachableFromTheIndex() {
        val indexText = docs.resolve(index).readText()
        val unreachable = spokes().filter { "(${it.name})" !in indexText }
        assertTrue(
            "these docs exist but the index never links them — add a row for each, or delete the " +
                "file: ${unreachable.map { it.name }}",
            unreachable.isEmpty(),
        )
    }

    @Test
    fun everyRelativeLinkResolvesToAFile() {
        val broken = mutableListOf<String>()
        for ((file, line, target) in links()) {
            val path = target.substringBefore('#')
            if (path.isEmpty() || path.startsWith("/") || "://" in path || path.startsWith("mailto:")) continue
            val resolved = File(file.parentFile, path).canonicalFile
            if (!resolved.exists()) broken += "${file.name}:$line -> $target"
        }
        assertTrue("these links point at nothing:\n  " + broken.joinToString("\n  "), broken.isEmpty())
    }

    @Test
    fun everyAnchorNamesAHeadingThatExists() {
        val broken = mutableListOf<String>()
        for ((file, line, target) in links()) {
            val anchor = target.substringAfter('#', "")
            if (anchor.isEmpty()) continue
            val path = target.substringBefore('#')
            val host = if (path.isEmpty()) file else File(file.parentFile, path)
            if (!host.isFile) continue
            val slugs = headings(host.readText()).map { slug(it) }
            if (anchor !in slugs) {
                broken += "${file.name}:$line -> $target (headings: ${slugs.joinToString(", ")})"
            }
        }
        assertTrue(
            "these anchors point at no heading — a heading was renamed and the link was not:\n  " +
                broken.joinToString("\n  "),
            broken.isEmpty(),
        )
    }

    @Test
    fun everyDocFitsTheLineBudget() {
        val fat = checkable().filter { it.readLines().size > lineBudget }
            .map { "${it.name} is ${it.readLines().size} lines" }
        assertTrue(
            "split these docs by topic (one spoke per topic, and add a row to the index):\n  " +
                fat.joinToString("\n  "),
            fat.isEmpty(),
        )
    }

    /**
     * The slug is the guard for anchors, so it is guarded: it has to match the strings the docs
     * actually write, including the em dash (dropped, like GitHub does) and an explicit `{#…}`.
     */
    @Test
    fun theSlugMatchesHowAnchorsAreWritten() {
        assertEquals("stylus--selection-roadmap", slug("Stylus & selection roadmap"))
        assertEquals("the-xopp-format-code-derived--this-is-its-authoritative-home", slug("The `.xopp` format (code-derived \u2014 this is its authoritative home)"))
        assertEquals("fidelity-notes--round-trip-hazards", slug("Fidelity notes / round-trip hazards"))
        assertEquals("selecting-objects-render", slug("Selecting objects (`render/`)"))
        assertEquals("relative-pdf-references", slug("Relative PDF references {#relative-pdf-references}"))
    }

    /**
     * Every `](target)` in the checked docs, as (file, line, target). Code is not prose: fenced blocks
     * and inline spans are skipped, so a dialect example such as `` `[label](url)` `` is not read as a
     * link to a file called `url`.
     */
    private fun links(): List<Triple<File, Int, String>> {
        val out = mutableListOf<Triple<File, Int, String>>()
        for (file in checkable()) {
            var fenced = false
            file.readLines().forEachIndexed { i, raw ->
                if (raw.trimStart().startsWith("```")) fenced = !fenced
                if (fenced) return@forEachIndexed
                val prose = raw.replace(Regex("`[^`]*`"), " ")
                for (m in Regex("""]\(([^)\s]+)\)""").findAll(prose)) {
                    out += Triple(file, i + 1, m.groupValues[1])
                }
            }
        }
        return out
    }

    /**
     * A doc that names a task names a task that **exists**.
     *
     * The spoke docs are where a fact's owner and its unfinished work are recorded together — the native
     * format's capability table marks a row "live only where the open/save path actually runs it, and
     * otherwise names the task that will make it live" — so an id that has since been archived, renamed
     * or never added reads as pending work that is not there. That is a drift no reader can see: the doc
     * is prose and the id is a string in another file. This is the something that catches it.
     */
    @Test
    fun everyTaskIdADocNamesExists() {
        val ids = taskIds()
        val unknown = mutableListOf<String>()
        for (file in checkable()) {
            file.readLines().forEachIndexed { i, raw ->
                for (match in taskIdLike.findAll(raw)) {
                    val slug = match.groupValues[1]
                    if (slug in ids || slug in proseThatLooksLikeATaskId) continue
                    unknown += "${file.name}:${i + 1} -> `$slug`"
                }
            }
        }
        assertTrue(
            "these read as task ids but no task has them — fix the reference, or add the phrase to " +
                "proseThatLooksLikeATaskId deliberately:\n  " + unknown.joinToString("\n  "),
            unknown.isEmpty(),
        )
    }

    /** A backticked slug of three or more words — the shape of a task id, and of little else. */
    private val taskIdLike = Regex("""`([a-z0-9]+(?:-[a-z0-9]+){2,})`""")

    /**
     * Backticked prose that has a task id's shape without being one. Deliberate, the way the source-size
     * ceilings are: adding a line here is a decision to state, not a way round the check.
     */
    private val proseThatLooksLikeATaskId = setOf(
        // The desktop companion plugin's own name (docs/mobix-format.md), not a task in either file.
        "xournalpp-mobix-plugin",
    )

    /** Every task id in `TODO.toml` and `FINISHED.toml`: the two files a doc may reference. */
    private fun taskIds(): Set<String> {
        val ids = mutableSetOf<String>()
        for (name in listOf("TODO.toml", "FINISHED.toml")) {
            val file = File(repoRoot(), name)
            if (!file.isFile) continue
            for (line in file.readLines()) {
                Regex("""^\s*id\s*=\s*"([^"]+)"""").find(line)?.let { ids += it.groupValues[1] }
            }
        }
        return ids
    }

    /** The docs under budget and link rules: everything in `docs/`, minus the release archive. */
    private fun checkable(): List<File> =
        (spokes() + listOf(docs.resolve("tools.md"), docs.resolve(index))).distinct().sorted()

    /** Every architecture spoke: the markdown files under `docs/` that are not the index or tools. */
    private fun spokes(): List<File> =
        docs.listFiles { f: File -> f.isFile && f.extension == "md" }!!
            .filter { it.name != index && it.name != "tools.md" }
            .sortedBy { it.name }

    /** Every heading of [text], code fences skipped. */
    private fun headings(text: String): List<String> {
        var fenced = false
        return text.split('\n').mapNotNull { raw ->
            if (raw.trimStart().startsWith("```")) fenced = !fenced
            if (fenced) null else Regex("""^#{1,6}\s+(.*)$""").find(raw)?.groupValues?.get(1)?.trim()
        }
    }

    /** The heading's anchor, the way GitHub derives it (which is how the docs write links). */
    private fun slug(heading: String): String {
        Regex("""\{#([^}]+)}""").find(heading)?.let { return it.groupValues[1] }
        return heading.lowercase()
            .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
            .replace(' ', '-')
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

    private val docs: File get() = File(repoRoot(), "docs")
}
