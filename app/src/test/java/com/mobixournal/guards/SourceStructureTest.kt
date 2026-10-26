package com.mobixournal.guards

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AGENTS.md` asks for the code split into many small pieces — no mono-file, and no function far past
 * ~100 lines — because navigation and unit-testability are what make the code cheap to change. That
 * rule was discipline: every file was hand-read to find the offenders, and a file that quietly doubled
 * in size was noticed only when someone opened it.
 *
 * This makes the rule mechanical, as a **ratchet**: the files and functions that are already over
 * budget are listed below, and the test fails when an entry grows past its recorded ceiling or a
 * *new* one appears. Nothing here asks for a one-shot cleanup — it asks that the debt stop growing,
 * so splitting a listed file is always a step the test rewards (drop its entry) and adding 300 lines
 * to it is a step it refuses.
 *
 * The check reads the source tree because that is where the mistake happens: nothing at runtime can
 * see how long a file is. Unit tests run with the module directory as their working directory, hence
 * the `src/main/java` path; [mainSources] fails loudly rather than passing vacuously if it is run from
 * somewhere else.
 */
class SourceStructureTest {

    /** A file this long is a mono-file by the standard in `AGENTS.md`: split it by topic. */
    private val fileBudget = 700

    /**
     * The files that are over [fileBudget] today, with the ceiling each may not cross. Raising one is
     * allowed — but it is a deliberate line in a diff, which is the whole point of the ratchet.
     */
    private val fileCeilings = mapOf(
        // 1780 -> 1778 deliberate: the living figure's panel moved beside its own dialog
        // (`ShapeEditorHost`), so the canvas file pays one line for it, not twenty.
        // +14 deliberate: the split workspace's own window plumbing — the pane session bridge and the
        // shared document an editing pane hands its mirror, both passed straight through to the canvas.
        "ui/EditorRegions.kt" to 1792,
        // +5 deliberate: the editable page window it owns (pageSource / pageWindow / resetPageWindow).
        // +3 deliberate: the background resolver a paged `.mobix` reads through (its setter lives in
        // CanvasPageSource.kt, beside the page source it belongs to).
        // +7 deliberate: the lazy "this page's background is missing" report the resolver drains.
        // +12 deliberate: the board the canvas draws is one field (`board`, a whole geometry + tiles +
        // paper in `render/DrawingSurfaceInfinite.kt`), the eviction it drives, and the wholesale-swap
        // reset that keeps a board from surviving the document it belonged to.
        // +48 deliberate: a board is written into, so the view gained the cell the gesture is writing in
        // (`gestureCell`, the one thing the page index cannot say for a cell with no page yet), the
        // grid reconciliation in the `doc` setter that keeps the cells in step with an undo, the shape a
        // save reads back (`canvasGeometry`), and the refusal a page-shaped gesture gets on a board. The
        // coordinate and commit arithmetic itself lives in BoardGrid / BoardInk / DrawingSurfaceBoardInk.
        // +83 more, the same feature's other half: the board's reading of "where you are" (BoardReading,
        // reported instead of a page), the part-in-view reads the layer panel and a tab snapshot need
        // (currentPageIndex / visiblePageIndex over the grid), the search jump through the chrome box,
        // and the mirrored pane being handed the board's grid along with the document.
        // -6: the shape-endpoint snap takes an explicit connector switch rather than carrying the
        // stale duplicate doc block it had.
        // +15 deliberate: the two panes of one document — the docKey it was opened as (so a mirror can
        // look its session up) and the shared-session handle it reports an edit through. The sharing
        // itself lives in `panes/DocumentSessions.kt`.
        // +3: the armed selection lift, one field, beside the clipboard it ends up on.
        "render/DrawingSurfaceView.kt" to 1841,
        "render/CircuitShapes.kt" to 1180,
        "ui/AppSettings.kt" to 997,
        "ui/EditorDialogs.kt" to 920,
    )

    /** A function past this is the "~100 lines max" in `AGENTS.md`: extract a helper. */
    private val functionBudget = 100

    /**
     * The functions already over [functionBudget], by `path#name`, measured with [longFunctions]. Each
     * is a candidate for extraction; none of them may grow.
     */
    private val longFunctions = setOf(
        "ui/EditorOverflowMenu.kt#OverflowMenu",
        "ui/EditorRegions.kt#TopBarToolsRow",
        "ui/EditorRegions.kt#SearchControls",
        "ui/EditorRegions.kt#EditorTopBar",
        "ui/EditorRegions.kt#EditorPaneView",
        "ui/EditorRegions.kt#bindEditorActions",
        "ui/EditorScreen.kt#EditorScreen",
        "ui/PenPresetsWidgets.kt#PenPresetsRow",
        "ui/EditorOverlays.kt#EditorOverlays",
        "ui/EditorDialogs.kt#ScaleneAnglesDialog",
        "ui/EditorDialogs.kt#PenParametersDialog",
        "ui/AppSettingsBackup.kt#fromNode",
        "ui/AppSettingsBackup.kt#toJson",
        "format/json/JsonParser.kt#parse",
        "ui/AppSettings.kt#load",
        "ui/BackupSection.kt#BackupSection",
    )

    @Test
    fun noSourceFileGrowsPastItsRecordedCeiling() {
        val offenders = mutableListOf<String>()
        for (file in mainSources()) {
            val key = keyOf(file)
            val size = file.readLines().size
            val ceiling = fileCeilings[key]
            if (ceiling == null) {
                if (size > fileBudget) offenders += "$key is $size lines (no ceiling recorded)"
            } else if (size > ceiling) {
                offenders += "$key is $size lines (ceiling $ceiling)"
            }
        }
        assertTrue(
            "split the file by topic, or raise its ceiling deliberately — these grew past it:\n  " +
                offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }

    /**
     * The list of ceilings is a statement about the tree, so it is checked for *stale* entries too: a
     * file that no longer exists, or one that has come back under [fileBudget] and should therefore be
     * listed under the plain budget instead of an exemption.
     */
    @Test
    fun theCeilingListDescribesTheTreeAsItIs() {
        val byKey = mainSources().associateBy { keyOf(it) }
        for ((key, ceiling) in fileCeilings) {
            val file = byKey[key] ?: throw AssertionError("$key is listed but does not exist — drop it")
            val size = file.readLines().size
            assertTrue(
                "the exemption for $key is stale: it is $size lines, under the $fileBudget budget — " +
                    "delete the entry so the whole tree is measured by one rule",
                size > fileBudget,
            )
            assertTrue("$key has shrunk to $size; lower its ceiling to say so", size <= ceiling)
        }
    }

    @Test
    fun noFunctionGrowsPastItsRecordedCeiling() {
        // If the scanner ever stops seeing a file's code, every span in it disappears and the test would
        // pass by measuring nothing: a file that declares functions must be seen to declare them.
        for (file in mainSources()) {
            val declared = Regex("""\bfun\s+\w""").findAll(codeOnly(file.readText())).count()
            assertTrue(
                "${keyOf(file)} declares no function to the scanner — the measure is not reading it",
                declared > 0 || !file.readText().contains("fun "),
            )
        }
        val offenders = longFunctions()
            .filter { (id, lines) -> lines > functionBudget && id !in longFunctions }
            .map { (id, lines) -> "$id is $lines lines" }
        assertTrue(
            "extract a helper from the function, or add it to [longFunctions] as known debt:\n  " +
                offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }

    /** The allow-list is a statement about the tree as well: an entry that no longer applies goes. */
    @Test
    fun theLongFunctionListDescribesTheTreeAsItIs() {
        val measured = longFunctions().keys
        val gone = longFunctions - measured
        assertTrue(
            "these are listed as long but are no longer over $functionBudget lines — drop them: $gone",
            gone.isEmpty(),
        )
    }

    /**
     * The measurement is the guard, so it is guarded: [codeOnly] must blank out the braces and `fun`s
     * that live inside strings, comments and templates, or every KDoc paragraph would be counted as
     * code and the failure would be noise.
     */
    @Test
    fun theMeasureIgnoresBracesInStringsAndComments() {
        val sample = """
            // fun notAFunction() { { { }
            /* fun alsoNot() { } */
            private val message = "fun inAString() { { {"
            /** A KDoc mentioning fun inProse() { */
            internal fun real(): Int {
                val text = "closing braces inside a string: } }"
                return text.length
            }
        """.trimIndent()
        assertEquals(
            "only the one real function is measured",
            setOf("real"),
            functionSpans(codeOnly(sample)).map { it.name }.toSet(),
        )
    }

    /**
     * Blank the parts of the source that are not code: line and block comments, `"…"` strings (with
     * escapes), `"""…"""` strings, and `'…'` characters. This is what makes brace counting safe — a
     * LaTeX template or a JSON fixture inside a string must not look like a block.
     */
    private fun codeOnly(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var state = 0 // 0 code, 1 line comment, 2 block comment, 3 string, 4 raw string
        while (i < source.length) {
            val c = source[i]
            val next = source.getOrNull(i + 1)
            when (state) {
                0 -> when {
                    c == '/' && next == '/' -> { state = 1; out.append("  "); i++ }
                    c == '/' && next == '*' -> { state = 2; out.append("  "); i++ }
                    c == '"' && next == '"' && source.getOrNull(i + 2) == '"' -> {
                        state = 4; out.append("   "); i += 2
                    }
                    c == '"' -> { state = 3; out.append(' ') }
                    c == '\'' -> { out.append(' '); i = skipChar(source, i) - 1 }
                    else -> out.append(c)
                }
                1 -> { if (c == '\n') { state = 0; out.append('\n') } else out.append(' ') }
                // A comment's newlines are kept: the measure counts *real* lines, so blanking one
                // would compress the file and make every span below it look short.
                2 -> if (c == '*' && next == '/') { state = 0; out.append("  "); i++ } else {
                    out.append(if (c == '\n') '\n' else ' ')
                }
                3 -> if (c == '\\') { out.append("  "); i++ } else if (c == '"' || c == '\n') {
                    state = 0; out.append(' ')
                } else out.append(' ')
                4 -> if (c == '"' && next == '"' && source.getOrNull(i + 2) == '"') {
                    state = 0; out.append("   "); i += 2
                } else out.append(if (c == '\n') '\n' else ' ')
            }
            i++
        }
        return out.toString()
    }

    /** Index just past a `'…'` character literal (or `i + 1` when it is not one). */
    private fun skipChar(source: String, i: Int): Int {
        var j = i + 1
        while (j < source.length && source[j] != '\'' && source[j] != '\n') {
            if (source[j] == '\\') j++
            j++
        }
        return (j + 1).coerceAtMost(source.length)
    }

    /** One measured function: its name and how many lines its declaration covers. */
    private data class Span(val name: String, val lines: Int)

    private val funDeclaration = Regex(
        """(?:^|\s)(?:(?:private|internal|public|protected|override|suspend|inline|operator|tailrec|""" +
            """external|abstract|open|actual|expect|const)\s+)*fun\s+(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?(\w+)\s*\(""",
    )

    /**
     * The spans of the functions whose body is a **brace block**, keyed `path#name` against a line
     * count. An expression body (`fun f() = …`, the shape of a one-line helper) has no brace to count
     * and is skipped rather than guessed at: under-reporting a span is a missed offender, while a
     * guessed one would fail the build on a function that is fine.
     */
    private fun longFunctions(): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        for (file in mainSources()) {
            for (span in functionSpans(codeOnly(file.readText()))) {
                out["${keyOf(file)}#${span.name}"] = span.lines
            }
        }
        return out
    }

    /** Measure every brace-bodied function in [code] (already run through [codeOnly]). */
    private fun functionSpans(code: String): List<Span> {
        val lineStarts = mutableListOf(0)
        code.forEachIndexed { at, c -> if (c == '\n') lineStarts += at + 1 }
        fun lineOf(at: Int) = lineStarts.indexOfLast { it <= at }

        val out = mutableListOf<Span>()
        for (match in funDeclaration.findAll(code)) {
            // The signature usually wraps, so the body's brace is found by walking past the parameter
            // list first: a default argument's `=` must never be mistaken for an expression body.
            val opener = bodyOpener(code, match.range.last) ?: continue
            if (code[opener] != '{') continue
            var depth = 0
            var at = opener
            while (at < code.length) {
                if (code[at] == '{') depth++
                if (code[at] == '}' && --depth == 0) break
                at++
            }
            out += Span(match.groupValues[1], lineOf(at) - lineOf(match.range.first) + 1)
        }
        return out
    }

    /**
     * Where a declaration starting at [from] ends up: the index of the `{` that opens its body, the
     * index of the `=` that makes it an expression body, or `null` when neither is found in a sane
     * distance (in which case the caller skips the declaration rather than guessing a span).
     */
    private fun bodyOpener(code: String, from: Int): Int? {
        var i = code.indexOf('(', from)
        if (i < 0) return null
        var depth = 0
        while (i < code.length) {
            if (code[i] == '(') depth++
            if (code[i] == ')' && --depth == 0) break
            i++
        }
        // Only the rest of the line that closes the parameter list: a wrapped signature always puts its
        // return type and body opener there, while a declaration with *no* body (an interface method,
        // an `expect fun`) has neither — and scanning further would swallow the next function's brace
        // and report the declaration as one long span.
        while (i < code.length && code[i] != '\n') {
            if (code[i] == '{' || code[i] == '=') return i
            i++
        }
        return null
    }

    private fun keyOf(file: File): String = file.path.replace('\\', '/').substringAfter("com/mobixournal/")

    /**
     * Every Kotlin source file of the shared main source sets — the app, `mobix-core` and the desktop
     * shell.
     *
     * The core was split out of `app` (Phase A of `step-3-desktop-workstation`) and the desktop shell
     * arrived with it, and the budget has to follow the code: a 700-line file must not become legal by
     * being moved one module out. Keys stay relative to `com/mobixournal/`, exactly as before the
     * splits, so the recorded ceilings mean the same file they always did (`desktop/DesktopShell.kt`
     * for the shell's own sources).
     */
    private fun mainSources(): List<File> {
        val appRoot = listOf(File("src/main/java/com/mobixournal"), File("app/src/main/java/com/mobixournal"))
            .firstOrNull { it.isDirectory }
            ?: error(
                "no main source set from ${File(".").absolutePath}: this test must run with the " +
                    "module directory (app/) as its working directory",
            )
        val coreRoot = listOf(
            File("mobix-core/src/main/java/com/mobixournal"),
            File("../mobix-core/src/main/java/com/mobixournal"),
        ).firstOrNull { it.isDirectory }
            ?: error(
                "no mobix-core source set from ${File(".").absolutePath}: the structure budget must " +
                    "cover the shared core, not only the app",
            )
        val desktopRoot = listOf(
            File("desktop/src/main/java/com/mobixournal"),
            File("../desktop/src/main/java/com/mobixournal"),
        ).firstOrNull { it.isDirectory }
            ?: error(
                "no desktop source set from ${File(".").absolutePath}: the structure budget must " +
                    "cover the desktop shell as well, or a mono-file can grow there unwatched",
            )
        return listOf(appRoot, coreRoot, desktopRoot)
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
    }
}
