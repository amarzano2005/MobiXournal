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
 *  - no sentence is pasted into two spokes: a moved paragraph leaves a pointer, not a copy;
 *  - **the front doors are documents too** (`README.md`, `AGENTS.md`). They sat outside `docs/`, so no
 *    rule looked at them, and the README grew three paragraphs of 12 000–23 000 characters, a desktop
 *    section retelling the spokes feature by feature, and a shortcut table `desktop-ui-layout.md`
 *    already owns. They now carry a recorded line budget, a cap on how long one paragraph of running
 *    prose may be ([noFrontDoorParagraphIsAWallOfText]), and the fact that a front door *points at*
 *    its spokes instead of restating them;
 *  - nothing at the repository root is an orphan: a file a reader meets first is named by a doc, a
 *    test or a script, or it does not belong there ([everyFileAtTheRootIsReferenced]).
 *
 * **Drift** (prose that describes code which has since changed):
 *
 *  - a code file a doc names must exist, so a rename, a move or a deletion fails here instead of
 *    confusing a reader who trusts the doc;
 *  - a doc named anywhere (`docs/…md`) must exist: `TODO.toml` used to send a reader to two spokes
 *    that had been split away, and only the files inside `docs/` were ever checked;
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

    /**
     * The two files a reader meets before any spoke. They are not spokes: they point at the spokes,
     * which is why they get a budget of their own rather than an entry in the index.
     */
    private val frontDoors = listOf("README.md", "AGENTS.md")

    /**
     * How long a front door may be. Recorded rather than derived, like the source-size ceilings: the
     * numbers are what these two files cost after being cut back to their job, and a change that pushes
     * past one is a decision to state — usually by moving the paragraph to the spoke that owns it.
     *
     * `README.md` is the human's front door (what it is, how to run it, where to read more) and
     * `AGENTS.md` the agent's map; neither is a place to accumulate detail, because detail has an owner
     * in `docs/` and a second copy of it drifts.
     */
    private val frontDoorBudget = mapOf("README.md" to 360, "AGENTS.md" to 430)

    /**
     * The longest a single paragraph of **running prose** may get in a front door, in characters.
     *
     * A wall of text is not a style opinion here: the README's desktop section was one paragraph of
     * 13 369 characters describing every command the window has, and a paragraph that long has no
     * sentence a reader can find again. Lists, tables, quotes, headings, HTML and code are exempt — they
     * are scanned by their shape, and a feature catalogue is legitimately long.
     */
    private val proseParagraphWall = 1_200

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
     * A doc that names another doc names one that **exists**.
     *
     * The architecture spokes are joined by name — `TODO.toml`'s scopes tell a task to read and update
     * `docs/<topic>.md`, and the map in `AGENTS.md` is a list of them — so a spoke that was split, renamed
     * or dropped leaves a task that sends its reader to a file nobody can open. That is invisible to the
     * link check, which only looked at the files inside `docs/`.
     *
     * `FINISHED.toml` is deliberately **not** read: it is the finished-work archive, and a description
     * there records what a task said when it was picked up. A doc it named may legitimately have been
     * split since; rewriting history to match is the opposite of an archive.
     */
    @Test
    fun everyDocADocNamesExists() {
        val missing = mutableListOf<String>()
        for (file in prose() + listOf(File(repoRoot(), "TODO.toml"))) {
            file.readLines().forEachIndexed { i, line ->
                for (match in docReference.findAll(line)) {
                    val target = "docs/" + match.groupValues[1]
                    // A *template* is not a path: `docs/releases/vX.Y.Z.md` names whichever version is
                    // being cut, and `<topic>`/`...` are the shapes an instruction uses.
                    if (elided.containsMatchIn(target)) continue
                    if (!File(repoRoot(), target).isFile) missing += "${file.name}:${i + 1} -> $target"
                }
            }
        }
        assertTrue(
            "these references name a doc the tree does not have — fix the reference, or add the spoke " +
                "(see 'Adding a spoke' in docs/architecture.md):\n  " + missing.joinToString("\n  "),
            missing.isEmpty(),
        )
    }

    /**
     * The front doors keep their **size**, in lines and in how long one paragraph may run.
     *
     * `everyDocFitsTheLineBudget` never saw them, and a front door with no ceiling is where detail
     * accumulates: the README reached 539 lines and one 23 000-character block of nested bullets. The
     * budget is a ratchet — the numbers are what the files cost once cut back to their job, so the next
     * paragraph has to be argued for, and the argument is usually "this belongs to a spoke".
     */
    @Test
    fun eachFrontDoorFitsItsBudget() {
        val fat = frontDoorBudget.mapNotNull { (name, budget) ->
            val file = File(repoRoot(), name)
            if (!file.isFile) return@mapNotNull null
            val lines = file.readLines().size
            if (lines > budget) "$name is $lines lines (budget $budget)" else null
        }
        assertTrue(
            "these front doors grew past the size they are meant to hold — move the detail to the spoke " +
                "that owns it and link, or raise the number in `frontDoorBudget` as a decision:\n  " +
                fat.joinToString("\n  "),
            fat.isEmpty(),
        )
    }

    /**
     * A paragraph of running prose in a front door stays short enough to read.
     *
     * Long is not the problem — *unbroken* is: a paragraph of 13 000 characters has no sentence a reader
     * can come back to, and the README's desktop section was exactly one of those. Splitting it costs a
     * blank line and a full stop, which is the whole of the remedy this check asks for. Lists, tables,
     * quotes, headings, HTML and fenced code are exempt ([proseParagraphs] is what decides).
     */
    @Test
    fun noFrontDoorParagraphIsAWallOfText() {
        val walls = mutableListOf<String>()
        for (name in frontDoors) {
            val file = File(repoRoot(), name)
            if (!file.isFile) continue
            for ((start, end, chars) in proseParagraphs(file)) {
                if (chars > proseParagraphWall) {
                    walls += "$name:$start-$end is $chars characters (max $proseParagraphWall) — break it " +
                        "into paragraphs, or move the detail to its spoke"
                }
            }
        }
        assertTrue("these paragraphs are walls of text:\n  " + walls.joinToString("\n  "), walls.isEmpty())
    }

    /**
     * Nothing at the repository root is an **orphan**.
     *
     * The root is the first thing a reader sees, so what sits there reads as deliberate: a screenshot
     * nobody links, a sample file no test opens, a leftover export. Every root file is therefore either
     * infrastructure (the build, the licence, this doc set's two front doors) or named somewhere in the
     * repo — by a doc, a test or a script, which is also what makes a fixture such as a sample `.xopp`
     * legitimate. Dot-files and anything `.gitignore` covers are exempt: they are the toolchain's, not
     * the reader's.
     */
    @Test
    fun everyFileAtTheRootIsReferenced() {
        val root = repoRoot()
        val ignored = gitignoreMatcher(root)
        val corpus = StringBuilder()
        val skipped = setOf(".git", "build", ".gradle", ".idea", "node_modules", ".kotlin")
        root.walkTopDown()
            .onEnter { it == root || it.name !in skipped }
            .filter { it.isFile && it.extension in referenceTextExtensions }
            .forEach { corpus.append(it.readText()).append('\n') }
        val text = corpus.toString()
        val orphans = root.listFiles().orEmpty()
            .filter { it.isFile && !it.name.startsWith(".") }
            .filter { it.name !in rootInfrastructure && !ignored(it.name) && it.name !in text }
            .map { it.name }
            .sorted()
        assertEquals(
            "these files sit at the repository root and nothing names them — link one from the doc or " +
                "test that uses it, add it to `rootInfrastructure` if it belongs there, or put it in " +
                ".gitignore if it is a local artifact rather than part of the tree",
            emptyList<String>(),
            orphans,
        )
    }

    /** A reference to a spoke, as `docs/<name>.md`; the `docs/[name].md` template in the map is not one. */
    private val docReference = Regex("""docs/([A-Za-z0-9/._-]+\.md)""")

    /** A doc path with a placeholder in it — a release's version, a `<topic>`, an elided `...` segment. */
    private val elided = Regex("""(\.\.\.|X\.Y\.Z|\[|<)""")

    /**
     * The root files that are their own justification: the build, the licence, the doc set's front doors.
     * A file added here is a decision to state, the same rule as the source-size ceilings.
     */
    private val rootInfrastructure = setOf(
        "README.md", "AGENTS.md", "LICENSE", "NOTICE", "TODO.toml", "FINISHED.toml",
        "gradle.properties", "gradlew", "gradlew.bat", "settings.gradle.kts", "build.gradle.kts",
    )

    /** The extensions that count as "named somewhere": prose, code, scripts and CI. */
    private val referenceTextExtensions = setOf("md", "toml", "kt", "kts", "py", "sh", "yml", "yaml")

    /** The `.gitignore` patterns, as a matcher for one bare file name at the root. */
    private fun gitignoreMatcher(root: File): (String) -> Boolean {
        val file = File(root, ".gitignore")
        if (!file.isFile) return { false }
        val patterns = file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("!") }
            .map { it.removePrefix("/").removeSuffix("/") }
            .filter { '/' !in it }
            .map { glob(it) }
        return { name -> patterns.any { it.matches(name) } }
    }

    /** One `.gitignore` glob (`*`, `?`) as a regex over a single file name. */
    private fun glob(pattern: String): Regex =
        Regex("^" + pattern.map { c ->
            when (c) {
                '*' -> ".*"
                '?' -> "."
                else -> Regex.escape(c.toString())
            }
        }.joinToString("") + "$")

    /**
     * The paragraphs of **running prose** in [file], as (first line, last line, characters).
     *
     * A block that begins, anywhere in it, with a structural marker — a heading, a list item, a table
     * row, a quote, an HTML tag, an indented line or a fence — is not prose and is not measured: those
     * are scanned by their shape, and their length is a catalogue rather than a paragraph.
     */
    private fun proseParagraphs(file: File): List<Triple<Int, Int, Int>> {
        val out = mutableListOf<Triple<Int, Int, Int>>()
        val orderedItem = Regex("""^\d+\.\s""")
        val lines = file.readLines()
        var fenced = false
        var start = -1
        var chars = 0
        fun close(end: Int) {
            if (start >= 0) out += Triple(start + 1, end, chars)
            start = -1
            chars = 0
        }
        lines.forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.startsWith("```")) {
                close(i)
                fenced = !fenced
                return@forEachIndexed
            }
            val structural = fenced || line.isEmpty() || raw.startsWith("  ") || raw.startsWith("\t") ||
                line.startsWith("#") || line.startsWith("|") || line.startsWith(">") ||
                line.startsWith("<") || line.startsWith("- ") || line.startsWith("* ") ||
                line.startsWith("---") || line.startsWith("***") || orderedItem.containsMatchIn(line)
            if (structural) {
                close(i)
                return@forEachIndexed
            }
            if (start < 0) start = i
            chars += line.length
        }
        close(lines.size)
        return out
    }

    /**
     * Every link in the checked docs — `](target)` and the front doors' HTML `src`/`href` — as
     * (file, line, target). Code is not prose: fenced blocks
     * and inline spans are skipped, so a dialect example such as `` `[label](url)` `` is not read as a
     * link to a file called `url`.
     */
    private fun links(): List<Triple<File, Int, String>> {
        val out = mutableListOf<Triple<File, Int, String>>()
        for (file in prose()) {
            var fenced = false
            file.readLines().forEachIndexed { i, raw ->
                if (raw.trimStart().startsWith("```")) fenced = !fenced
                if (fenced) return@forEachIndexed
                val line = raw.replace(Regex("`[^`]*`"), " ")
                for (m in Regex("""]\(([^)\s]+)\)""").findAll(line)) {
                    out += Triple(file, i + 1, m.groupValues[1])
                }
                // The README's header is HTML (the logo and the preview image), and a broken image is
                // the same broken link as a broken one written in markdown.
                for (m in Regex("(?:src|href)=\"([^\"]+)\"").findAll(line)) {
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

    /** The docs under the budget and heading rules: everything in `docs/`, minus the release archive. */
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
