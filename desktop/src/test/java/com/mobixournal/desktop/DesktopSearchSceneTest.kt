package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopContainer
import com.mobixournal.desktop.io.DesktopDocument
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.glyph
import com.mobixournal.desktop.sheet.PageBackdrop
import com.mobixournal.desktop.shell.DesktopSearch
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.DesktopTabs
import com.mobixournal.desktop.shell.HeaderSearchField
import com.mobixournal.desktop.shell.SearchResultsSection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.render.Bounds
import com.mobixournal.render.SearchHit
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search chrome **as pixels**: the field's counter and clear glyph, and the results list.
 *
 * A token test can say the capsule is the right glass; only a render can say the field draws anything at
 * all when a query is up, and that the results section appears with hits and disappears without them. The
 * PNGs land in `build/desktop-render/` like the window's other render tests.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopSearchSceneTest {

    private fun tab(): DesktopTab {
        val model = Document(
            pages = listOf(
                Page(
                    width = 595.0,
                    height = 842.0,
                    background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                    layers = listOf(Layer(emptyList())),
                ),
            ),
        )
        val document = DesktopDocument(
            file = File("search-scene.xopp"),
            source = PageSource.of(model),
            model = model,
            container = DesktopContainer.XOPP_GZIP,
            backdrop = PageBackdrop(bundled = { null }, documentDir = File(".")),
        )
        val tabs = DesktopTabs()
        tabs.open(document)
        return tabs.active!!
    }

    private fun answer(query: String, hits: List<SearchHit>) = DesktopSearch(query = query, hits = hits)

    private fun render(name: String, width: Int, height: Int, content: @androidx.compose.runtime.Composable () -> Unit): BufferedImage {
        val scene = ImageComposeScene(width, height, Density(1f)) {
            Box(Modifier.fillMaxSize().background(DesktopTheme.desk)) {
                content()
            }
        }
        val png = try {
            val bytes = requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
            File("build/desktop-render/$name.png").apply { parentFile?.mkdirs() }.writeBytes(bytes)
            bytes
        } finally {
            scene.close()
        }
        return ImageIO.read(ByteArrayInputStream(png))
    }

    /** How many pixels two renders of the same size disagree on. */
    private fun differing(a: BufferedImage, b: BufferedImage): Int {
        var count = 0
        for (y in 0 until minOf(a.height, b.height)) {
            for (x in 0 until minOf(a.width, b.width)) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) count++
            }
        }
        return count
    }

    @Test
    fun `the field draws its counter and its clear glyph only while a query is up`() {
        val field = tab()
        val empty = render("search-field-empty", 320, 60) {
            HeaderSearchField(field, onQuery = {}, onNext = {}, onPrevious = {}, onClose = {})
        }

        field.searchQuery = "alpha"
        field.search = answer(
            "alpha",
            listOf(SearchHit(0, listOf(Bounds(10.0, 10.0, 40.0, 20.0)), snippet = "alpha beta")),
        )
        val filled = render("search-field-query", 320, 60) {
            HeaderSearchField(field, onQuery = {}, onNext = {}, onPrevious = {}, onClose = {})
        }

        assertTrue("the counter and the clear glyph are pixels", differing(empty, filled) > 40)
    }

    @Test
    fun `the results section lists matches and is gone with no query`() {
        val results = tab()
        val withoutQuery = render("search-results-empty", 240, 200) {
            Column(Modifier.width(240.dp)) { SearchResultsSection(results, onSearchHit = {}) }
        }

        results.search = answer(
            "alpha",
            listOf(
                SearchHit(0, listOf(Bounds(10.0, 10.0, 40.0, 20.0)), snippet = "alpha beta gamma"),
                SearchHit(2, listOf(Bounds(10.0, 40.0, 40.0, 50.0)), snippet = "the alpha of the page"),
            ),
        )
        val withQuery = render("search-results-hits", 240, 200) {
            Column(Modifier.width(240.dp)) { SearchResultsSection(results, onSearchHit = {}) }
        }

        assertTrue("two rows and a heading are pixels", differing(withoutQuery, withQuery) > 200)
    }
}
