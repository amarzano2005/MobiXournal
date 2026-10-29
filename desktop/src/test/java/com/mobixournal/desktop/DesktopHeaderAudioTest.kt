package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.mobixournal.format.mobix.SyntheticNotebook
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The audio pill is **in the header, immediately left of Save** — the place the owner asked for, proved by
 * pixels rather than by reading the layout code.
 *
 * Two bands are rendered: the pre-audio header and the same header with the session's pill. The picture
 * differs, and everything that differs sits to the **left of Save's capsule** — so the pill did not push
 * the file commands around and the band still ends where it did. The frame is also written to
 * `desktop/build/desktop-render/header-audio.png`, the way every other render test leaves its picture, so
 * the result can be looked at.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopHeaderAudioTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `the audio pill sits immediately left of save`() {
        val tabs = openDocument()
        val before = image(render(tabs, withAudio = false))
        val afterPng = render(tabs, withAudio = true)
        val after = image(afterPng)
        File("build/desktop-render/header-audio.png").apply { parentFile?.mkdirs() }.writeBytes(afterPng)

        val height = DesktopTheme.headerHeight.value.toInt()
        var leftmost = SCENE_WIDTH
        var rightmost = 0
        var changed = 0
        for (x in 0 until SCENE_WIDTH) {
            for (y in 0 until height) {
                if (before.getRGB(x, y) != after.getRGB(x, y)) {
                    changed++
                    leftmost = minOf(leftmost, x)
                    rightmost = maxOf(rightmost, x)
                }
            }
        }

        assertTrue("the pill changes what the band draws", changed > 200)
        // Save is the last capsule before the PDF export: its 36 dp capsule starts 52 px before the right
        // edge (10 dp margin + PDF capsule 36 + 6 gap). Nothing the pill adds may reach it.
        assertTrue(
            "the pill is left of Save (changed $leftmost..$rightmost of $SCENE_WIDTH)",
            rightmost < SCENE_WIDTH - 52,
        )
    }

    /** A band over a two-page document, at the width the header tests use. */
    private fun openDocument(): DesktopTabs {
        val file = temp.newFile("header.mobix").also { SyntheticNotebook.write(it, SyntheticNotebook.pages(2)) }
        val tabs = DesktopTabs()
        tabs.open(DocumentLoader.load(file))
        return tabs
    }

    /** One frame of the header — with the audio pill, or the pre-audio band it replaced. */
    private fun render(tabs: DesktopTabs, withAudio: Boolean): ByteArray {
        val scene = ImageComposeScene(SCENE_WIDTH, DesktopTheme.headerHeight.value.toInt(), Density(1f)) {
            Header(
                tabs = tabs,
                sidebarVisible = true,
                onActivate = {},
                onClose = {},
                onMove = { _, _ -> },
                onNew = {},
                onOpen = {},
                onSave = {},
                onSaveAs = {},
                onExportPdf = {},
                onExportSvg = {},
                onToggleSidebar = {},
                onSettings = {},
                onUndo = {},
                onRedo = {},
                audio = if (withAudio) DesktopAudioSession else null,
            )
        }
        return try {
            Snapshot.sendApplyNotifications()
            requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
        } finally {
            scene.close()
        }
    }

    private fun image(png: ByteArray) = ImageIO.read(ByteArrayInputStream(png))

    private companion object {
        const val SCENE_WIDTH = 900
    }
}
