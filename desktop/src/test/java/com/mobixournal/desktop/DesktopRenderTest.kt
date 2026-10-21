package com.mobixournal.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.mobixournal.format.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * The desktop preview's proof, run headlessly on the JVM: read a real `.xopp` with the app's own
 * codec, paint its first page on Skia, and inspect the pixels that came out.
 *
 * A successful build only says the code compiles. This says the shell actually *shows* the
 * document — the page count came from the file, the ink landed on the canvas, and the image is not
 * blank — which is the claim the preview makes in `docs/desktop-workstation.md`.
 */
class DesktopRenderTest {

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `loads a desktop xopp and renders its first page to png`() {
        val document = DocumentLoader.load(repoFile("udiff.xopp"))
        assertEquals("pages in udiff.xopp", 6, document.pageCount)
        assertTrue("strokes parsed", (document.strokeCount ?: 0) > 1_000)

        val page = document.page(0)
        val strokesOnPage = page.layers.sumOf { layer -> layer.elements.count { it is Stroke } }
        assertTrue("first page carries ink", strokesOnPage > 0)

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
        assertTrue("Skia produced a PNG", png != null && png.size > 10_000)

        val out = File("build/desktop-render/page-1.png").apply { parentFile?.mkdirs() }
        out.writeBytes(png!!)

        // The render has to hold ink, not just a sheet: count dark pixels (strokes) and any
        // non-white ones (paper ruling included) on a grid sample.
        val image = ImageIO.read(out)
        assertEquals(width, image.width)
        assertEquals(height, image.height)
        var sampled = 0
        var ink = 0
        var nonWhite = 0
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val rgb = image.getRGB(x, y) and 0xFFFFFF
                sampled++
                if (rgb != 0xFFFFFF) nonWhite++
                if (maxOf(rgb shr 16, (rgb shr 8) and 0xFF, rgb and 0xFF) < 0x60) ink++
                x += 3
            }
            y += 3
        }
        println("render samples=$sampled nonWhite=$nonWhite ink=$ink")
        assertTrue("page ruling was painted ($nonWhite of $sampled samples)", nonWhite > sampled / 40)
        assertTrue("strokes were painted ($ink of $sampled samples)", ink > sampled / 500)
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
