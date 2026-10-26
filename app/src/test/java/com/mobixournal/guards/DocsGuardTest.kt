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
 * This keeps that from happening again, in the two ways a doc set actually decays — it grows, and it
 * drifts. **Growth** (one file nobody can load, or two files claiming one topic):
 *
 *  - every spoke is reachable from the index, and every index row resolves;
 *  - every doc fits its **300-line** budget, which leaves headroom for the next sentence instead of a
 *    forced rewrite of the file before it;
 *  - every spoke opens the same way (`# H1`, then a `> Read when …` line) so a new one is written the
 *    way the rest are;
 *  - no two spokes share a title or a section heading — that is one topic with two homes, one of which
 *    is already going stale;
 *  - no sentence is pasted into two spokes: a moved paragraph leaves a pointer, not a copy.
 *
 * **Drift** (prose that describes code which has since changed):
 *
 *  - a code file a doc names must exist, so a rename, a move or a deletion fails here instead of
 *    confusing a reader who trusts the doc;
 *  - a task id a doc names must be in `TODO.toml` or `FINISHED.toml`;
 *  - every relative link and `#anchor` must resolve, so a renamed heading is caught at once.
 *
 * Not here, and deliberately: prose that is *wrong* in a way no file can witness ("the window is
 * read-only"). Those live in `DocClaimsTest`, next to the statements they protect.
 *
 * Unit tests run with the module directory (`app/`) as their working directory, so the repo root is
 * found by walking up; [repoRoot] fails loudly rather than passing vacuously if it is not there.
 */
class DocsGuardTest {

    /**
     * A doc past this is a mono-doc: the point of a spoke is that one topic loads on its own, and the
     * number leaves a spoke room to grow into rather than starting its author with a rewrite.
     */
    private val lineBudget = 300

    /**
     * The index that has to reach every spoke. `docs/releases/` is exempt: those are the historical
     * changelogs, frozen at the version they describe and never linked from the index.
     */
    private val index = "architecture.md"

    /** A doc's opening contract: who should open it, in one line, so a new spoke is never a mystery. */
    private val readWhen = Regex("""^> Read (when|before)\b""")

    /**
     * Files a doc may name without one existing in this checkout: they live on a host (`/data/android`)
     * or are elided with `...`. Recorded here, not silently skipped.
     */
    private val hostOnlyScripts = setOf("emulator.sh", "adb-targets.sh")

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
     * A spoke opens with its title and says, in the next few lines, when to read it.
     *
     * This is the whole of the template — the rest of a spoke's shape is its own — and it is enforced
     * because a new doc written in a different shape is where a set of docs starts to come apart.
     */
    @Test
    fun everySpokeOpensWithItsTitleAndAReadWhenLine() {
        val wrong = mutableListOf<String>()
        for (file in spokes()) {
            val lines = file.readLines()
            val title = lines.firstOrNull { it.isNotBlank() } ?: ""
            if (!title.startsWith("# ")) wrong += "${file.name}: does not open with an `# ` title"
            val opening = lines.take(20)
            if (opening.none { readWhen.containsMatchIn(it) }) {
                wrong += "${file.name}: no `> Read when …` line in its first 20 lines"
            }
        }
        assertTrue(
            "every spoke opens the same way (`# Title`, then `> Read when …`):\n  " +
                wrong.joinToString("\n  "),
            wrong.isEmpty(),
        )
    }

    /**
     * One topic, one home: no two spokes may carry the same H1, and no section heading of four words or
     * more may appear in two of them. A short heading (`Decisions`, `How it is tested`) is a shape rather
     * than a topic and is allowed twice; a long one is a subject, and a subject with two homes drifts.
     */
    @Test
    fun noTwoSpokesShareATitleOrASection() {
        val titles = mutableMapOf<String, MutableSet<String>>()
        val sections = mutableMapOf<String, MutableSet<String>>()
        for (file in spokes()) {
            var fenced = false
            for (raw in file.readLines()) {
                if (raw.trimStart().startsWith("```")) fenced = !fenced
                if (fenced) continue
                val match = Regex("""^(#{1,6})\s+(.*)$""").find(raw) ?: continue
                val level = match.groupValues[1].length
                val text = match.groupValues[2].trim().lowercase()
                if (level == 1) titles.getOrPut(text) { mutableSetOf() } += file.name
                if (text.split(Regex("""\s+""")).size >= 4) {
                    sections.getOrPut(text) { mutableSetOf() } += file.name
                }
            }
        }
        val clashTitles = titles.filterValues { it.size > 1 }
        val clashSections = sections.filterValues { it.size > 1 }
        assertTrue(
            "two spokes claim one title — a topic has one home:\n  " +
                clashTitles.entries.joinToString("\n  ") { "${it.key} in ${it.value}" },
            clashTitles.isEmpty(),
        )
        assertTrue(
            "two spokes claim one section heading — keep it in one and link from the other:\n  " +
                clashSections.entries.joinToString("\n  ") { "${it.key} in ${it.value}" },
            clashSections.isEmpty(),
        )
    }

    /**
     * A paragraph that moved leaves a pointer, not a copy: pasting it into the new spoke too is how the
     * two copies start to disagree. Twelve words is long enough that a match is a moved sentence rather
     * than a phrase both docs legitimately use.
     */
    @Test
    fun noSentenceIsPastedIntoTwoDocs() {
        val seen = mutableMapOf<String, MutableSet<String>>()
        for (file in prose()) {
            var fenced = false
            for (raw in file.readLines()) {
                val trimmed = raw.trim()
                if (trimmed.startsWith("```")) fenced = !fenced
                if (fenced) continue
                if (trimmed.isEmpty() || trimmed.startsWith(">") || trimmed.startsWith("|")) continue
                if (trimmed.startsWith("#") || trimmed.startsWith("_One spoke")) continue
                for (sentence in trimmed.split(Regex("""(?<=[.!?])\s+"""))) {
                    val normal = sentence.lowercase()
                        .replace(Regex("""[^\p{L}\p{N} ]"""), "")
                        .replace(Regex("""\s+"""), " ")
                        .trim()
                    if (normal.split(' ').size < 12) continue
                    seen.getOrPut(normal) { mutableSetOf() } += file.name
                }
            }
        }
        val pasted = seen.filterValues { it.size > 1 }
        assertTrue(
            "the same sentence appears in more than one doc — move it to its owner and link:\n  " +
                pasted.entries.joinToString("\n  ") { "${it.value} :: ${it.key.take(90)}…" },
            pasted.isEmpty(),
        )
    }

    /**
     * A doc that names a code file names a file that **exists**.
     *
     * Prose cannot be compiled, so a renamed or deleted file leaves the doc describing a class nobody
     * can open — the most common way these docs go stale, and invisible to every other check. Only
     * tokens that are clearly a path with a code extension are read (`render/InkGeometry.kt`,
     * `scripts/build.sh`); host-only scripts and `...`-elided paths are listed above.
     */
    @Test
    fun everyCodeFileADocNamesExists() {
        val paths = repoFiles()
        val named = Regex("""`([A-Za-z0-9_./-]+\.(?:kt|kts|sh|py|toml))`""")
        val missing = mutableListOf<String>()
        for (file in prose()) {
            file.readLines().forEachIndexed { i, raw ->
                for (match in named.findAll(raw)) {
                    val token = match.groupValues[1]
                    if (token.startsWith("/") || "..." in token) continue
                    if (token in hostOnlyScripts) continue
                    val found = paths.any { it == token || it.endsWith("/$token") }
                    if (!found) missing += "${file.name}:${i + 1} -> `$token`"
                }
            }
        }
        assertTrue(
            "these docs name a code file that is not in the tree — it was renamed, moved or deleted:\n  " +
                missing.joinToString("\n  "),
            missing.isEmpty(),
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

    /** A backticked slug of three or more words — the shape of a task id, and of little else. */
    private val taskIdLike = Regex("""`([a-z0-9]+(?:-[a-z0-9]+){2,})`""")

    /**
     * Backticked prose that has a task id's shape without being one. Deliberate, the way the source-size
     * ceilings are: adding a line here is a decision to state, not a way round the check.
     */
    private val proseThatLooksLikeATaskId = emptySet<String>()

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

    /** The docs under the link and budget rules: everything in `docs/`, minus the release archive. */
    private fun checkable(): List<File> =
        (spokes() + listOf(docs.resolve("tools.md"), docs.resolve(index))).distinct().sorted()

    /** The docs under the duplication and code-path rules: the same set, plus the two front doors. */
    private fun prose(): List<File> = (
        checkable() + listOf(File(repoRoot(), "README.md"), File(repoRoot(), "AGENTS.md"))
        ).filter { it.isFile }

    /** Every architecture spoke: the markdown files under `docs/` that are not the index or tools. */
    private fun spokes(): List<File> =
        docs.listFiles { f: File -> f.isFile && f.extension == "md" }!!
            .filter { it.name != index && it.name != "tools.md" }
            .sortedBy { it.name }

    /** Every repository file, as a `/`-joined path from the root, for [everyCodeFileADocNamesExists]. */
    private fun repoFiles(): List<String> {
        val out = mutableListOf<String>()
        val root = repoRoot()
        val skipped = setOf(".git", "build", ".gradle", ".idea", "node_modules", ".kotlin")
        root.walkTopDown()
            .onEnter { it == root || it.name !in skipped }
            .filter { it.isFile }
            .forEach { out += it.relativeTo(root).invariantSeparatorsPath }
        return out
    }

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
