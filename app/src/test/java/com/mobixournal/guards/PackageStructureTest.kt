package com.mobixournal.guards

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tree's **shape**, as a rule the build enforces.
 *
 * `SourceStructureTest` owns how big a file and a function may be; this owns **where code lives**, which is the
 * half a size budget cannot see: a project can pass every line limit and still be a pile if every file sits in
 * one package. The window was exactly that — 200 files in `com.mobixournal.desktop` — and the guard exists so
 * it cannot happen again, and so the seven packages it was split into keep meaning something.
 *
 * What is checked, and why each one is here rather than in a review:
 *
 *  - **package ↔ directory**, in all three modules: a scripted move (this repo has had them) can leave a file
 *    whose `package` line no longer matches its folder, and from then on the tree lies about itself;
 *  - **the window's packages are a closed list** ([docs/code-structure.md]): a new package is a deliberate
 *    line in this file and in that doc, not an accident of import autocomplete;
 *  - **the window's root package is closed**: only the entry points, a shrink-only list. Every other file
 *    states its subject by choosing a package — the flat package grew one convenience file at a time;
 *  - **no package becomes the next dumping ground**: a subject package holds at most [filesPerPackage] files,
 *    and past that it is two subjects;
 *  - **the tablet's canvas pile stops growing**: `DrawingSurface*.kt` is one god object spread over 20 files
 *    (~5 000 lines). Splitting it is the real fix; until then the count is a ceiling and a new file fails;
 *  - **no bucket names**: `Utils`, `Helpers`, `Common`, `Misc`, `Stuff`, `Manager` are refused, because a
 *    bucket is where a topic goes to be forgotten.
 *
 * Everything here is a ratchet in the repo's sense: the recorded numbers may fall, and raising one is a
 * deliberate line in a diff that a reader can find.
 */
class PackageStructureTest {

    private val modules = listOf("app", "mobix-core", "desktop")

    /** The window's packages, exactly as [docs/code-structure.md] states them. */
    private val windowPackages = setOf("desktop", "desktop.sheet", "desktop.ink", "desktop.io", "desktop.settings", "desktop.tools", "desktop.shell")

    /** The files the window's root package may hold: the entry points, and nothing else. */
    private val windowRootFiles = setOf("DesktopApp.kt", "Main.kt")

    /** A subject package past this is two subjects. */
    private val filesPerPackage = 50

    /** The tablet's canvas cluster: one object spread over this many files, and no more. */
    private val canvasFiles = 20

    /** Prefixes that name no subject. */
    private val bucketNames = listOf("Utils", "Util", "Helpers", "Helper", "Common", "Misc", "Stuff", "Manager", "Managers")

    @Test
    fun `a file's package matches its directory in every module`() {
        val offenders = mutableListOf<String>()
        for (module in modules) {
            for (file in sources(module)) {
                val declared = packageOf(file) ?: continue
                val relative = file.relativeTo(File(repoRoot(), "$module/src/main/java")).invariantSeparatorsPath
                val folder = relative.substringBeforeLast('/', "")
                val expected = folder.replace('/', '.')
                if (declared != expected) offenders += "$module/$relative declares $declared, sits in $expected"
            }
        }
        assertTrue("package and directory must agree:\n  " + offenders.joinToString("\n  "), offenders.isEmpty())
    }

    @Test
    fun `the window's packages are the documented ones`() {
        val found = sources("desktop")
            .mapNotNull { packageOf(it) }
            .map { it.removePrefix("com.mobixournal.") }
            .toSet()
        val unknown = (found - windowPackages).sorted()
        assertTrue(
            "the window has packages docs/code-structure.md does not name: $unknown — add the row to the doc " +
                "and to this test in the same commit, with a subject name",
            unknown.isEmpty(),
        )
        val empty = (windowPackages - found).sorted()
        assertTrue("these documented packages have no files any more: $empty — drop them from the doc", empty.isEmpty())
    }

    @Test
    fun `the window's root package holds the entry points and nothing else`() {
        val root = File(repoRoot(), "desktop/src/main/java/com/mobixournal/desktop")
        val loose = (root.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension == "kt" }
            .map { it.name }
            .toSet()
        assertEquals(
            "the window's root package is for the entry points only: a new file here is how the flat " +
                "200-file package grew — give it a subject package instead (docs/code-structure.md)",
            windowRootFiles,
            loose,
        )
    }

    @Test
    fun `no subject package grows past the cap`() {
        val folder = File(repoRoot(), "desktop/src/main/java/com/mobixournal/desktop")
        val counts = (folder.walkTopDown().filter { it.isDirectory && it != folder }.map { it } + folder)
            .associate { dir ->
                val name = if (dir == folder) "desktop" else "desktop." + dir.name
                name to (dir.listFiles() ?: emptyArray()).count { it.isFile && it.extension == "kt" }
            }
        val over = counts.filterValues { it > filesPerPackage }
        assertTrue(
            "these packages are past $filesPerPackage files, so they are two subjects: " +
                "" + over.entries.joinToString { "${it.key}=${it.value}" } +
                " — split it by topic and state the new packages in docs/code-structure.md",
            over.isEmpty(),
        )
    }

    @Test
    fun `the tablet's canvas pile does not grow`() {
        val canvas = File(repoRoot(), "app/src/main/java/com/mobixournal/render")
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith("DrawingSurface") && it.extension == "kt" }
        assertTrue(
            "the canvas is already ${canvas.size} files of one object ($canvasFiles allowed): a new " +
                "DrawingSurface*.kt must be a split of an existing one, or an object that owns its state",
            canvas.size <= canvasFiles,
        )
    }

    @Test
    fun `no file is named after a bucket`() {
        val offenders = mutableListOf<String>()
        for (module in modules) {
            for (file in sources(module)) {
                val stem = file.nameWithoutExtension
                val bucket = bucketNames.firstOrNull { stem.endsWith(it) || stem == it }
                if (bucket != null) offenders += "$module: ${file.name}"
            }
        }
        assertTrue(
            "a bucket name says nothing about the subject, and is where a topic goes to be forgotten: " +
                offenders.joinToString(", "),
            offenders.isEmpty(),
        )
    }

    /** Every Kotlin source of [module]'s main source set. */
    private fun sources(module: String): List<File> =
        File(repoRoot(), "$module/src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** The package a file declares, or null for a file that declares none (a script, not a class). */
    private fun packageOf(file: File): String? =
        file.useLines { lines -> lines.firstOrNull { it.startsWith("package ") } }
            ?.removePrefix("package ")
            ?.trim()

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "AGENTS.md").isFile && File(dir, "docs").isDirectory) return dir
            dir = dir.parentFile
        }
        error("no repository root above ${File(".").absolutePath}: this test must run inside the repo")
    }
}
