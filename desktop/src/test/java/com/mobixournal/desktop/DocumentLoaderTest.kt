package com.mobixournal.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.mobixournal.format.Xopp
import com.mobixournal.format.XoppZip
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Stroke
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window opens what the file **is**, not what it is called.
 *
 * Every case here is a container the app writes, and the interesting ones are deliberately misnamed:
 * a `.mobix` saved as `.xopp` and a ZIP `.xopp` saved as `.mobix` both have to open through the right
 * path, because the picker and the shell cannot rely on the suffix (SAF hands the Android app name-less
 * URIs, and a desktop user renames files). The last test proves the paged path actually paints: a page
 * read through `PageSource` from a `.mobix` renders ink on Skia, not an empty sheet.
 */
class DocumentLoaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val pages = 40

    /**
     * A native `.mobix` with [pages] pages, one stroke each, written by the real container writer.
     * A copy is left in `build/desktop-smoke/` so the manual check the task asks for has an actual
     * notebook to open in the window — the suites' own files live in a temporary folder that is gone
     * by the time anyone runs the app.
     */
    private fun mobix(name: String = "notebook.mobix"): File =
        temp.newFile(name).also { file ->
            SyntheticNotebook.write(file, SyntheticNotebook.pages(pages))
            val smoke = File("build/desktop-smoke/$name").apply { parentFile?.mkdirs() }
            file.copyTo(smoke, overwrite = true)
        }

    /** The same `.xopp` content in a self-contained ZIP package, through the app's own writer. */
    private fun zippedXopp(name: String = "packaged.xopp"): File {
        val file = temp.newFile(name)
        XoppZip.save(Xopp.open(repoFile("udiff.xopp").inputStream().buffered()), pdf = null, output = file.outputStream())
        return file
    }

    @Test
    fun opensANativeMobixPageByPage() {
        val document = DocumentLoader.load(mobix())
        try {
            assertEquals(DesktopContainer.MOBIX, document.container)
            assertEquals(pages, document.pageCount)
            assertNull("a paged document does not claim an ink count it has not read", document.strokeCount)

            val page = document.page(3)
            assertEquals(SyntheticNotebook.idOf(3), page.id)
            assertEquals(
                3.0,
                (page.layers.single().elements.single() as Stroke).points.first().x,
                0.0,
            )
        } finally {
            document.close()
        }
    }

    @Test
    fun opensAZippedXoppWithItsWholeDocument() {
        val document = DocumentLoader.load(zippedXopp())
        try {
            assertEquals(DesktopContainer.XOPP_ZIP, document.container)
            assertEquals("pages in the packaged udiff.xopp", 6, document.pageCount)
            assertTrue("strokes parsed", (document.strokeCount ?: 0) > 1_000)
        } finally {
            document.close()
        }
    }

    @Test
    fun stillOpensAGzipXopp() {
        val document = DocumentLoader.load(repoFile("udiff.xopp"))
        try {
            assertEquals(DesktopContainer.XOPP_GZIP, document.container)
            assertEquals(6, document.pageCount)
        } finally {
            document.close()
        }
    }

    @Test
    fun decidesByContentAndNotByExtension() {
        // A notebook named `.xopp`: the leading bytes are a ZIP, its mimetype says `.mobix`.
        val misnamedMobix = mobix("looks-like-a-xopp.xopp")
        val asNotebook = DocumentLoader.load(misnamedMobix)
        try {
            assertEquals(DesktopContainer.MOBIX, asNotebook.container)
            assertEquals(pages, asNotebook.pageCount)
        } finally {
            asNotebook.close()
        }

        // A ZIP `.xopp` named `.mobix`: same ZIP signature, `content.xml` and no `.mobix` mimetype.
        val misnamedXopp = zippedXopp("looks-like-a-mobix.mobix")
        val asXopp = DocumentLoader.load(misnamedXopp)
        try {
            assertEquals(DesktopContainer.XOPP_ZIP, asXopp.container)
            assertEquals(6, asXopp.pageCount)
        } finally {
            asXopp.close()
        }
    }

    @Test
    fun reportsWhatItCannotOpenInsteadOfFailingInsideAReader() {
        val text = temp.newFile("notes.txt").apply { writeText("non è un documento\n") }
        val failure = runCatching { DocumentLoader.load(text) }.exceptionOrNull()
        assertTrue("a text file is refused by name", failure is UnsupportedOperationException)
        assertTrue(
            "and the message says what was expected: ${failure?.message}",
            failure?.message?.contains(".xopp") == true && failure.message?.contains(".mobix") == true,
        )

        val notADocument = temp.newFile("archive.zip").apply {
            java.util.zip.ZipOutputStream(outputStream()).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("readme.txt"))
                zip.write("hello".toByteArray())
                zip.closeEntry()
            }
        }
        val zipFailure = runCatching { DocumentLoader.load(notADocument) }.exceptionOrNull()
        assertTrue("a ZIP with no document inside is refused", zipFailure is UnsupportedOperationException)
    }

    /**
     * The paged path paints. A `.mobix` page is read through the reader and handed to the same
     * [PageView] the `.xopp` path uses, so the ink on screen is the document's own — this is the claim
     * "the desktop opens native documents" made visible rather than asserted.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun rendersAPageReadFromAMobixNotebook() {
        val document = DocumentLoader.load(mobix())
        try {
            val page = document.page(0)
            val width = page.width.toInt()
            val height = page.height.toInt()
            val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
                PageView(page = page, scale = 1f)
            }
            val png = try {
                scene.render().encodeToData()?.bytes
            } finally {
                scene.close()
            }
            assertTrue("Skia produced a PNG", png != null && png.size > 1_000)

            val out = File("build/desktop-render/mobix-page-1.png").apply { parentFile?.mkdirs() }
            out.writeBytes(png!!)

            val image = ImageIO.read(out)
            var ink = 0
            var y = 0
            while (y < image.height) {
                var x = 0
                while (x < image.width) {
                    val rgb = image.getRGB(x, y) and 0xFFFFFF
                    if (maxOf(rgb shr 16, (rgb shr 8) and 0xFF, rgb and 0xFF) < 0x60) ink++
                    x += 2
                }
                y += 2
            }
            assertTrue("the page's stroke was painted (ink=$ink)", ink > 0)
        } finally {
            document.close()
        }
    }

    /** The module's working directory is `desktop/`, so walk up to the repository root. */
    private fun repoFile(name: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, name)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("$name not found above ${System.getProperty("user.dir")}")
    }
}
