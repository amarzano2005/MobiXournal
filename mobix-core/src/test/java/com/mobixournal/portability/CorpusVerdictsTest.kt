package com.mobixournal.portability

import com.mobixournal.format.mobix.MobixContainer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The oracle the independent CLI is compared against: one verdict per committed corpus file, recorded
 * in [`spec/corpus-verdicts.csv`](../../../../../../spec/corpus-verdicts.csv) and recomputed here.
 *
 * A rule added to this side alone changes a verdict, and this fails until the table is updated — which
 * is what makes `.github/workflows/build.yml`'s `cli` job meaningful: it never runs Kotlin, so the table
 * is the only thing that can carry this side's answer to the Python reader. Update it deliberately when
 * the table's own line disagrees.
 */
class CorpusVerdictsTest {

    @Test
    fun everyCorpusVerdictIsTheOneTheSpecificationRecords() {
        val recorded = recordedVerdicts()
        val computed = corpusFiles().associate { it.name to verdictOf(it) }

        assertEquals(
            "a corpus file the verdict table does not name, or a name it invents",
            computed.keys.sorted(),
            recorded.keys.sorted(),
        )
        val disagreements = computed
            .filter { (name, verdict) -> recorded[name] != verdict }
            .map { (name, verdict) -> "$name: recorded ${recorded[name]}, mobix-core says $verdict" }
        assertTrue(
            "these verdicts disagree with spec/corpus-verdicts.csv:\n  " + disagreements.joinToString("\n  "),
            disagreements.isEmpty(),
        )
    }

    /**
     * What this build decides: the archive opens, every page decodes, and the `assets/` rules hold.
     * A refusal is a verdict, not a crash — the hostile corpus is meant to land here.
     */
    private fun verdictOf(file: File): String = try {
        file.inputStream().use { MobixContainer.open(it) }
        if (MobixAssetValidator.validate(file).clean) "clean" else "refused"
    } catch (failure: Throwable) {
        "refused"
    }

    /** Every committed corpus file, golden and hostile, by name. */
    private fun corpusFiles(): List<File> = listOf("mobix-v1", "mobix-hostile").flatMap { directory ->
        val dir = File(javaClass.getResource("/fixtures/$directory")!!.toURI())
        dir.listFiles().orEmpty().filter { it.extension == "mobix" }
    }

    /** The recorded table, `file,verdict` per line, comments and blanks dropped. */
    private fun recordedVerdicts(): Map<String, String> {
        val table = File(repoRoot(), "spec/corpus-verdicts.csv")
        assertTrue("spec/corpus-verdicts.csv is missing", table.isFile)
        return table.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line ->
                val (name, verdict) = line.split(",", limit = 2)
                name.trim() to verdict.trim()
            }
    }

    /** The repository root: the nearest ancestor that holds `AGENTS.md` and `docs/`. */
    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "AGENTS.md").isFile && File(dir, "docs").isDirectory) return dir
            dir = dir.parentFile
        }
        error("no repository root above ${File("").absolutePath}: this test must run inside the repo")
    }
}
