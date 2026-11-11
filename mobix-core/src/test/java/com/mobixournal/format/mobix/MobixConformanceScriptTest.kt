package com.mobixournal.format.mobix

import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.RawElement
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cross-write, verified with the tool that is not this build.
 *
 * `MobixSchemaGuardTest` holds the corpus and the writer's field names to the schemas, but every one of
 * those checks lives inside `mobix-core`'s own classpath — so "a file written here validates in the
 * independent tool" was a claim about a script nothing ran. This test runs it: `scripts/verify-mobix.py`
 * reads the bytes this writer produced, with no dependency on this module, and its exit code is the
 * assertion.
 *
 * The third case is the one that keeps the first honest: a validator that accepted everything would pass
 * a file the app wrote, so two documents it must refuse — a manifest with no `documentId` and an archive
 * whose `mimetype` is not this format's — are asserted to fail with the row that covers them.
 */
class MobixConformanceScriptTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "spec/schema").isDirectory }
        ?: error("spec/schema not found above ${File("").absolutePath}")

    private val script = File(repoRoot, "scripts/verify-mobix.py")
    private val corpus = File(repoRoot, "mobix-core/src/test/resources/fixtures/mobix-v1")

    /** A fixed clock: the bytes are the writer's, the timestamp is the test's. */
    private val now = 1_790_000_000_000L

    private fun fixture(name: String): ByteArray = File(corpus, "$name.mobix").readBytes()

    private fun open(bytes: ByteArray): MobixDocument =
        MobixDocument.open(MobixContainer.open(ByteArrayInputStream(bytes)))

    private fun tempFile(name: String, bytes: ByteArray): File =
        File.createTempFile("mobix-verify-$name-", ".mobix").apply {
            writeBytes(bytes)
            deleteOnExit()
        }

    /**
     * The script's verdict — exit code and combined output — run with [arguments].
     *
     * A missing `python3` is a failure, not a skip: `.github/workflows/build.yml` runs python3 for the
     * todo scripts, so an environment that cannot run this is an environment where the check silently
     * disappears, which is the failure mode the check exists to prevent.
     */
    private fun runScript(vararg arguments: String): Pair<Int, String> {
        assertTrue("${script.path} is missing", script.isFile)
        val process = try {
            ProcessBuilder("python3", script.absolutePath, *arguments)
                .directory(repoRoot)
                .redirectErrorStream(true)
                .start()
        } catch (failure: java.io.IOException) {
            error("cannot run python3 for ${script.name}: ${failure.message}")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return process.waitFor() to output
    }

    private fun verify(file: File): Pair<Int, String> = runScript(file.absolutePath)

    private fun assertVerifies(file: File, what: String) {
        val (exit, output) = verify(file)
        assertEquals("$what must verify outside this module, the script said: $output", 0, exit)
    }

    // ---- the writer's own bytes -------------------------------------------------------------------

    @Test
    fun aDocumentThisWriterProducesVerifiesWithTheScript() {
        val written = open(fixture("18-every-family")).toBytes(now)
        assertVerifies(tempFile("app-written", written), "a save of the every-family document")

        val families = open(written).pages.single().layers.flatMap(Layer::elements)
        assertEquals("the save kept every modelled family", 9, families.size)
        assertTrue("and none of them fell back to retention", families.none { it is RawElement })
    }

    @Test
    fun aThirdPartyDocumentKeepsItsUnknownConstructAndStillVerifies() {
        val saved = open(fixture("17-third-party-writer")).toBytes(now)
        assertVerifies(tempFile("third-party-saved", saved), "a save of the third-party document")

        val preserved = open(saved).pages.single().layers.flatMap(Layer::elements)
            .filterIsInstance<RawElement>()
        assertTrue(
            "the construct this build has no model for is still in the file it wrote, got " +
                preserved.map { it.name },
            preserved.any { it.name == "3dPlotElement" },
        )
    }

    // ---- the checklist --------------------------------------------------------------------------

    /**
     * The rows the script decides and the rows the checklist says it decides are the same set, in both
     * directions. Without this the two drift the way prose does: the script grows a row nobody documented,
     * or the checklist claims a row no tool checks and a reader trusts it.
     */
    @Test
    fun theChecklistAndTheScriptClaimTheSameRows() {
        val marker = "**Script-decided rows:**"
        val declaredLine = File(repoRoot, "spec/CONFORMANCE.md").readLines()
            .firstOrNull { it.trimStart().startsWith(marker) }
            ?: error("spec/CONFORMANCE.md must declare the rows a script decides, with `$marker`")
        val declared = Regex("\\d+").findAll(declaredLine.substringAfter(marker))
            .map { it.value.toInt() }
            .toSet()

        val (exit, output) = runScript("--rows")
        assertEquals("--rows must succeed: $output", 0, exit)
        val reported = output.lineSequence().mapNotNull { it.trim().toIntOrNull() }.toSet()

        assertEquals(
            "the checklist and `--rows` must name the same rows; the script said $reported, the " +
                "checklist said $declared",
            declared,
            reported,
        )
    }

    // ---- what the script must refuse --------------------------------------------------------------

    @Test
    fun theScriptRefusesTheDocumentsItShouldByRow() {
        val (missingIdExit, missingIdOutput) = verify(
            archive("""{"formatVersion":1,"canvasMode":"PAGINATED"}"""),
        )
        assertEquals("a manifest with no documentId must fail: $missingIdOutput", 1, missingIdExit)
        assertTrue(
            "and fail for that rule, not for the shape of the file: $missingIdOutput",
            "row 21" in missingIdOutput,
        )

        val (wrongTypeExit, wrongTypeOutput) = verify(
            archive(
                """{"formatVersion":1,"documentId":"d","canvasMode":"PAGINATED"}""",
                mimetype = "application/zip",
            ),
        )
        assertEquals("an archive declaring another type must fail: $wrongTypeOutput", 1, wrongTypeExit)
        assertTrue(
            "and fail on the mimetype row: $wrongTypeOutput",
            "row 2" in wrongTypeOutput,
        )
    }

    /**
     * A minimal archive with a chosen manifest and `mimetype`: the two mutations above are documents the
     * writer would never produce, which is exactly why they are built here rather than saved.
     */
    private fun archive(manifestJson: String, mimetype: String = "application/vnd.mobixournal+zip"): File {
        val file = File.createTempFile("mobix-verify-mutated-", ".mobix").apply { deleteOnExit() }
        val type = mimetype.toByteArray(Charsets.UTF_8)
        ZipOutputStream(file.outputStream()).use { zip ->
            val stored = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = type.size.toLong()
                compressedSize = type.size.toLong()
                crc = CRC32().apply { update(type) }.value
            }
            zip.putNextEntry(stored)
            zip.write(type)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("pages/page-0001.json"))
            zip.write("""{"layers":[{"elements":[]}]}""".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return file
    }
}
