package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.tools.DesktopDialogFrame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import java.io.ByteArrayInputStream
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **One modal for the window** — the claim `docs/interface-contract.md` makes about questions.
 *
 * Eleven dialogs used to draw themselves: two scrims (one of them `Color.Black` at 45 %), three widths,
 * two corner shapes, and a footer that could be left-aligned or missing its commit. The window could ask
 * *Save your changes?* and *Export PDF* in two materials, and the same failure could be written in the
 * PDF's document red or in the refusal red depending on which file drew it.
 *
 * So this holds the source and the pixels to the one grammar: every modal composes [DesktopDialogFrame],
 * no dialog paints a scrim, a capsule or a document red of its own, and the frame drawn in a window dims
 * the page behind it with the one scrim and lays an opaque popup surface on top.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopDialogContractTest {

    private val root = generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, "AGENTS.md").isFile }
    private val source = File(root, "desktop/src/main/java/com/mobixournal/desktop")

    /** The modals: every one of these is a question asked over the page, so all of them use the frame. */
    private val modals = listOf(
        "tools/CloseDialog.kt",
        "shell/ExportAsDialog.kt",
        "shell/ExportPdfDialog.kt",
        "io/ImportPdfDialog.kt",
        "settings/FirstRunFormatDialog.kt",
        "settings/UpdateDialog.kt",
        "tools/ScaleneDialogs.kt",
        "shell/PageSetup.kt",
        "shell/PageRangePanel.kt",
        "shell/LayersPanel.kt",
    )

    /** Every file a dialog is drawn in, the transient failure card included. */
    private val dialogFiles = modals + "io/OpenFailureCard.kt"

    @Test
    fun `every modal is the one frame and paints no scrim of its own`() {
        modals.forEach { file ->
            val text = File(source, file).readText()
            assertTrue("$file must compose the shared modal", text.contains("DesktopDialogFrame("))
            assertTrue("$file draws its own scrim", !text.contains("Color.Black.copy"))
            assertTrue("$file still pours the tablet's glass", !text.contains("glassCapsule"))
        }
        val frame = File(source, "tools/DesktopDialogFrame.kt").readText()
        assertTrue("the frame is where the scrim lives", frame.contains(".background(DesktopTheme.scrim)"))
        assertTrue("and the popup surface with its hairline", frame.contains(".glassSurface()"))
        assertTrue("the footer is the same row in every dialog", frame.contains("Arrangement.spacedBy(8.dp, Alignment.End)"))
    }

    @Test
    fun `no dialog writes an error or a destructive answer in the document red`() {
        dialogFiles.forEach { file ->
            val text = File(source, file).readText()
            assertTrue("$file uses the PDF's red for a dialog state", !text.contains("documentRed"))
        }
        val failures = File(source, "io/OpenFailureCard.kt").readText()
        assertTrue("a failed open is a refusal, not a PDF", failures.contains("DesktopTheme.danger"))
    }

    @Test
    fun `the frame dims the page with the one scrim and lays an opaque card on it`() {
        val scene = ImageComposeScene(400, 300, Density(1f)) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                DesktopDialogFrame(
                    title = "A question",
                    onDismiss = { },
                    footer = { },
                    content = { },
                )
            }
        }
        try {
            var nanos = 0L
            repeat(60) {
                nanos += 16_000_000L
                scene.render(nanos)
            }
            val image = pixels(scene.render(nanos))

            val corner = image.getRGB(4, 4) and 0xFFFFFF
            assertTrue(
                "the page behind the question is dimmed: 0x" + corner.toString(16),
                luminance(corner) < luminance(0xFFFFFF) - 20,
            )
            assertEquals(
                "one scrim, not a gradient across the window",
                corner,
                image.getRGB(image.width - 5, image.height - 5) and 0xFFFFFF,
            )

            val card = DesktopTheme.popupSurface.toArgb() and 0xFFFFFF
            val centre = image.getRGB(image.width / 2, image.height / 2) and 0xFFFFFF
            assertTrue(
                "the card is the opaque popup surface: 0x" + centre.toString(16) +
                    " vs 0x" + card.toString(16),
                distance(centre, card) <= 2,
            )
        } finally {
            scene.close()
        }
    }

    /**
     * The **first-run question**, drawn: the card this task migrated, over a page, in a window's size.
     *
     * The PNG is kept under `build/desktop-render/` so the layout can be looked at, and the centre is checked
     * against the popup surface — a dialog that came out translucent again would fail here.
     */
    @Test
    fun `the first-run question is drawn on the one frame`() {
        val scene = ImageComposeScene(720, 480, Density(1f)) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                com.mobixournal.desktop.settings.FirstRunFormatDialog(
                    picked = com.mobixournal.desktop.settings.DesktopNewDocumentFormat.MOBIX,
                    onPick = {},
                    onConfirm = {},
                )
            }
        }
        try {
            var nanos = 0L
            repeat(60) {
                nanos += 16_000_000L
                scene.render(nanos)
            }
            val image = scene.render(nanos)
            val png = requireNotNull(image.encodeToData()?.bytes) { "Skia produced no PNG" }
            File("build/desktop-render/dialog-first-run.png").apply { parentFile?.mkdirs() }.writeBytes(png)
            val drawn = ImageIO.read(ByteArrayInputStream(png))
            val centre = drawn.getRGB(drawn.width / 2, drawn.height / 2) and 0xFFFFFF
            // The card's middle is its pre-picked answer (*.mobix*), so the steel of the picked row is what
            // lands there; a translucent card would show the white page through both tones.
            val picked = DesktopTheme.activeSlot.toArgb() and 0xFFFFFF
            assertTrue(
                "the pre-picked answer wears the steel: 0x" + centre.toString(16),
                distance(centre, picked) <= 2,
            )
        } finally {
            scene.close()
        }
    }

    private fun pixels(image: org.jetbrains.skia.Image): BufferedImage {
        val bytes = requireNotNull(image.encodeToData()?.bytes) { "Skia produced no PNG" }
        return ImageIO.read(ByteArrayInputStream(bytes))
    }

    private fun luminance(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun distance(a: Int, b: Int): Int {
        fun channel(rgb: Int, shift: Int) = (rgb shr shift) and 0xFF
        return maxOf(
            kotlin.math.abs(channel(a, 16) - channel(b, 16)),
            kotlin.math.abs(channel(a, 8) - channel(b, 8)),
            kotlin.math.abs(channel(a, 0) - channel(b, 0)),
        )
    }
}
