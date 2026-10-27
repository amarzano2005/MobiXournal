package com.mobixournal.guards

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The suites CI actually runs, as a ratchet: **a module that carries tests must be named by a
 * workflow line**, or its tests are a claim nobody checks.
 *
 * This exists because the build can look green while a whole module is unexecuted. The suites of the
 * three modules do not share a task name — the Android app has `testDebugUnitTest`, the plain JVM
 * modules have `test` — so a workflow that names one of them (`./gradlew testDebugUnitTest`) runs
 * that one module and silently reports success for the rest. That is exactly how `mobix-core`, which
 * owns the `.xopp`/`.mobix` codecs, the document model and `FormatDriftTest`, came to have no CI run
 * at all: every step was correct, and the module was simply never mentioned.
 *
 * Nothing else can catch this. `DocsGuardTest` reads docs, `SourceStructureTest` reads sizes, and a
 * green check says only "what ran, passed". So the set of modules with a test source set is read from
 * `settings.gradle.kts` and compared against the Gradle lines of `.github/workflows/`; a new module
 * joins the build and the next CI run refuses to start until it is named too. The local front doors
 * (`scripts/build.sh`, `scripts/host-build.sh`) default to the same two suites so the gap is not
 * merely moved to the pre-push loop.
 */
class CiCoverageTest {

    /** Modules whose suites CI must run: those that actually carry a test source set. */
    @Test
    fun `a module with tests is run by CI`() {
        val modules = testedModules()

        // A settings parser that silently found nothing would make the check below vacuous, so the
        // list is pinned: both shells carry tests, and so does the core every format claim rests on.
        assertTrue("settings.gradle.kts named no tested modules at all", modules.size >= 3)
        assertTrue("mobix-core is not among the tested modules: $modules", "mobix-core" in modules)

        val lines = gradleLines()
        val missing = modules.filterNot { module -> runsModule(lines, module) }
        assertTrue(
            "these modules carry tests that no CI run executes: $missing\n" +
                "name them in .github/workflows/build.yml, e.g. " +
                "`./gradlew :app:testDebugUnitTest :mobix-core:test`",
            missing.isEmpty(),
        )
    }

    /**
     * The other direction, so the check above cannot be blinded by a rewrite: if the workflow stops
     * being readable as Gradle invocations it would report "nothing missing" for a file that no longer
     * builds anything.
     */
    @Test
    fun `the workflow lines this guard reads are real gradle invocations`() {
        val lines = gradleLines()
        assertTrue("no gradle invocation found under .github/workflows", lines.isNotEmpty())
        assertTrue(
            "the desktop suite is not among the workflow's gradle lines: $lines",
            lines.any { it.contains(":desktop:test") },
        )
    }

    /**
     * Whether some line runs [module]'s suite. `:${module}:test` also matches `:${module}:testDebugUnitTest`,
     * which is the Android module's name for the same thing; a line that *excludes* the task (`-x`)
     * is not one that runs it.
     */
    private fun runsModule(lines: List<String>, module: String): Boolean = lines.any { line ->
        line.contains(":$module:test") && !line.contains("-x :$module:test")
    }

    /** Every module declared by `settings.gradle.kts` that holds a test source set (`src/test*`). */
    private fun testedModules(): List<String> {
        val root = repoRoot()
        val settings = File(root, "settings.gradle.kts").readText()
        return Regex("""include\(":([^"]+)"\)""")
            .findAll(settings)
            .map { it.groupValues[1] }
            .filter { module -> hasTests(File(root, "$module/src")) }
            .toList()
    }

    private fun hasTests(src: File): Boolean =
        src.listFiles().orEmpty().any { it.isDirectory && it.name.startsWith("test") }

    /**
     * The `gradlew` invocations of every workflow file, comment lines dropped — a commented-out command
     * must not count as running anything.
     */
    private fun gradleLines(): List<String> =
        File(repoRoot(), ".github/workflows").listFiles().orEmpty()
            .filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
            .flatMap { it.readLines() }
            .map { it.trim() }
            .filter { line ->
                !line.startsWith("#") && (line.contains("gradlew") || line.startsWith("./gradle "))
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
