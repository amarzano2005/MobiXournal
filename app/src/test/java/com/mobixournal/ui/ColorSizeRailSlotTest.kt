package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The rail's **Colour & size** slot carries three favourite colours down its left half, drawn for
 * whichever tool is in play. Both ternas travel through the toolbar together, so what is worth pinning
 * is that the slot never shows both at once (drawing two ternas is what would push the drawing tools
 * out of view) and that a picked colour lands in the slot belonging to the tool that owns it.
 */
class ColorSizeRailSlotTest {

    private val pen = AppSettings.DEFAULT_PEN_FAVORITES

    private fun callbacks(
        onPick: (EditorTool, Int) -> Unit = { _, _ -> },
        onAssign: (EditorTool, Int, Int) -> Unit = { _, _, _ -> },
    ) = ToolbarFavoritesCallbacks(
        penFavorites = pen,
        highlighterFavorites = AppSettings.DEFAULT_HIGHLIGHTER_FAVORITES,
        onPick = onPick,
        onAssign = onAssign,
    )

    @Test
    fun `the slot draws the terna of the tool in play, never both`() {
        // The pen's three while the pen is live…
        assertEquals(pen, favoritesFor(favoriteToolFor(EditorTool.PEN), callbacks()))
        // …the highlighter's own three once the highlighter is, which is the whole point of keeping
        // the sets apart: both ternas exist, only one reaches the rail.
        assertEquals(
            AppSettings.DEFAULT_HIGHLIGHTER_FAVORITES,
            favoritesFor(favoriteToolFor(EditorTool.HIGHLIGHTER), callbacks()),
        )
        assertNotEquals(pen, AppSettings.DEFAULT_HIGHLIGHTER_FAVORITES)
    }

    @Test
    fun `a non-inking tool still offers the pen's colours`() {
        // An eraser or a selection has no colour of its own; an empty slot would be worse than the
        // pen's, since the next pen stroke is what the colour is for.
        for (tool in listOf(EditorTool.ERASER, EditorTool.SELECT, EditorTool.HAND)) {
            assertEquals(pen, favoritesFor(favoriteToolFor(tool), callbacks()))
        }
    }

    @Test
    fun `the row is a fixed three however many the callback carries`() {
        val overlong = ToolbarFavoritesCallbacks(
            penFavorites = listOf(1, 2, 3, 4, 5),
            highlighterFavorites = listOf(6, 7, 8, 9, 10),
            onPick = { _, _ -> },
            onAssign = { _, _, _ -> },
        )
        assertEquals(3, favoritesFor(EditorTool.PEN, overlong).size)
        assertEquals(3, favoritesFor(EditorTool.HIGHLIGHTER, overlong).size)
    }

    @Test
    fun `reassigning rewrites only the owning tool's terna`() {
        val before = AppSettings()
        val after = assignFavorite(before, EditorTool.HIGHLIGHTER, 1, XOPP_MAGENTA)
        assertEquals(
            listOf(XOPP_YELLOW, XOPP_MAGENTA, XOPP_BLUE),
            after.highlighterFavorites,
        )
        assertEquals(before.penFavorites, after.penFavorites)
        assertEquals(
            listOf(XOPP_MAGENTA, XOPP_RED, XOPP_GREEN),
            assignFavorite(before, EditorTool.PEN, 0, XOPP_MAGENTA).penFavorites,
        )
    }

    @Test
    fun `reassigning normalises to opaque and cannot resize the row`() {
        // A transparent pick would be an invisible dot on the rail.
        assertEquals(
            XOPP_BLACK,
            assignFavorite(AppSettings(), EditorTool.PEN, 0, 0x00000000).penFavorites[0],
        )
        val before = AppSettings()
        assertEquals(
            before.penFavorites,
            assignFavorite(before, EditorTool.PEN, 7, XOPP_RED).penFavorites,
        )
        assertEquals(
            before.penFavorites,
            assignFavorite(before, EditorTool.PEN, -1, XOPP_RED).penFavorites,
        )
    }

    @Test
    fun `a favourite is written into its own tool's slot`() {
        // The pen's colour lives in lastColor…
        assertEquals(
            XOPP_RED,
            settingsWithFavoriteColor(AppSettings(), EditorTool.PEN, XOPP_RED).lastColor,
        )
        // …the highlighter's in its own field, leaving the pen's untouched.
        val hl = settingsWithFavoriteColor(AppSettings(), EditorTool.HIGHLIGHTER, XOPP_ORANGE)
        assertEquals(XOPP_ORANGE, hl.highlighterColor)
        assertEquals(DEFAULT_LAST_COLOR_UNTOUCHED, hl.lastColor)
    }

    @Test
    fun `tapping a favourite already in play takes the colour without a tool switch`() {
        val ui = EditorUiState(EditorTool.PEN, XOPP_BLACK, AppSettings().lastWidth, XOPP_YELLOW, 1f)
        var written: AppSettings? = null
        // A null surface stands in for "the view is not attached": the live field and the settings
        // are what a tap on the live tool has to move, and switching to the tool it is already on
        // would re-read the live field and undo the pick.
        pickFavoriteColor(null, ui, AppSettings(), { written = it }, EditorTool.PEN, XOPP_RED)
        assertEquals(XOPP_RED, ui.color)
        assertEquals(EditorTool.PEN, ui.tool)
        assertEquals(XOPP_RED, written?.lastColor)
    }

    @Test
    fun `tapping the highlighter's favourite seeds the highlighter colour`() {
        val ui = EditorUiState(EditorTool.PEN, XOPP_BLACK, AppSettings().lastWidth, XOPP_YELLOW, 1f)
        // No surface attached: the *switch* is the view's job (it re-reads the settings it is handed),
        // so what this pins is the seeding a tap does first — the colour is put where the incoming
        // tool reads it, and the pen's own colour is left alone.
        pickFavoriteColor(null, ui, AppSettings(), {}, EditorTool.HIGHLIGHTER, XOPP_MAGENTA)
        assertEquals(XOPP_MAGENTA, ui.highlighterColor)
        assertEquals(XOPP_BLACK, ui.color)
        // And the write the switch then carries is the same one the Colour & size pop-up makes.
        assertEquals(
            XOPP_MAGENTA,
            settingsWithFavoriteColor(AppSettings(), EditorTool.HIGHLIGHTER, XOPP_MAGENTA)
                .highlighterColor,
        )
        assertEquals(
            XOPP_BLACK,
            settingsWithFavoriteColor(AppSettings(), EditorTool.HIGHLIGHTER, XOPP_MAGENTA).lastColor,
        )
    }
}

/** The unchanged pen colour in the highlighter test above, read from the factory defaults. */
private val DEFAULT_LAST_COLOR_UNTOUCHED: Int = AppSettings().lastColor
