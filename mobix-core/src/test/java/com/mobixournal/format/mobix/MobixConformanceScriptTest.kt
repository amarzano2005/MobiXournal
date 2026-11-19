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
     * The script's verdict — exit code and combined output — run with [arguments] on [pythonInterpreter].
     *
     * A missing interpreter is a failure, not a skip: `.github/workflows/build.yml` runs python3 for the
     * todo scripts, so an environment that cannot run this is an environment where the check silently
     * disappears, which is the failure mode the check exists to prevent. Missing means *no candidate
     * answered*, which is why the name is resolved rather than assumed — see [pythonInterpreter].
     */
    private fun runScript(vararg arguments: String): Pair<Int, String> {
        assertTrue("${script.path} is missing", script.isFile)
        val interpreter = pythonInterpreter()
        val process = try {
            ProcessBuilder(interpreter, script.absolutePath, *arguments)
                .directory(repoRoot)
                .redirectErrorStream(true)
                .start()
        } catch (failure: java.io.IOException) {
            error("cannot run $interpreter for ${script.name}: ${failure.message}")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return process.waitFor() to output
    }

    /**
     * The interpreter that really runs on this host, resolved once.
     *
     * `python3` is the CI's name and the name this test used to call outright — but on Windows it is also
     * the **App Execution Alias** Microsoft ships: the alias is earlier on `PATH` than a real install,
     * starts a stub that prints "Python was not found" and exits non-zero, and the check then reported the
     * stub's refusal as the *script's* verdict. A candidate that cannot answer is worse than an absent
     * interpreter, because it looks like the file did not conform.
     *
     * So a candidate is used only when it answers `<candidate> --version` with exit 0, and the entries
     * after the two `PATH` names are where a Windows install sits behind that alias: the Python install
     * manager's `%LOCALAPPDATA%\Python\bin`, and the per-user and all-users `Python3xx` folders. Nothing
     * is skipped — no candidate answering is a failure that names every path tried.
     */
    private fun pythonInterpreter(): String = interpreter ?: error(
        "no working Python on this host; tried ${pythonCandidates().joinToString(", ")}. " +
            "The conformance check runs ${script.name} and is not skippable.",
    )

    private val interpreter: String? by lazy { pythonCandidates().firstOrNull(::answers) }

    /** Every place an interpreter is worth trying, the CI's own name first. */
    private fun pythonCandidates(): List<String> {
        val installs = mutableListOf<String>()
        val local = System.getenv("LOCALAPPDATA").orEmpty()
        val programFiles = System.getenv("ProgramFiles").orEmpty()
        if (local.isNotEmpty()) {
            installs += "$local\\Python\\bin\\python3.exe"
            installs += "$local\\Python\\bin\\python.exe"
            installs += installsUnder("$local\\Programs\\Python")
        }
        if (programFiles.isNotEmpty()) installs += installsUnder(programFiles)
        return listOf("python3", "python") + installs + "/usr/bin/python3"
    }

    /** `…\Python3xx\python.exe` under [root], newest first: the per-user and all-users install layouts. */
    private fun installsUnder(root: String): List<String> =
        File(root).listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("Python3") }
            ?.sortedByDescending { it.name }
            ?.map { File(it, "python.exe").path }
            ?.filter { File(it).isFile }
            .orEmpty()

    /** Whether [candidate] is a real interpreter: `--version` has to finish, and finish successfully. */
    private fun answers(candidate: String): Boolean = try {
        val probe = ProcessBuilder(candidate, "--version").redirectErrorStream(true).start()
        probe.inputStream.bufferedReader().use { it.readText() }
        probe.waitFor() == 0
    } catch (failure: java.io.IOException) {
        false
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
