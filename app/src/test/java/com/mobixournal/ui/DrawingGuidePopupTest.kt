package com.mobixournal.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Straighten
import com.mobixournal.render.GuideKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests for the Drawing Guides pop-up glyphs, icon resolution, and menu structure.
 */
class DrawingGuidePopupTest {

    @Test
    fun `guideButtonIcon resolves specific icons for active guides and ruler for none`() {
        assertEquals(Icons.Outlined.Straighten, guideButtonIcon(GuideKind.NONE))
        assertEquals(SetsquareIcon, guideButtonIcon(GuideKind.SETSQUARE))
        assertEquals(CompassIcon, guideButtonIcon(GuideKind.COMPASS))
        assertEquals(ProtractorIcon, guideButtonIcon(GuideKind.PROTRACTOR))
        assertEquals(Icons.Outlined.GridOn, guideButtonIcon(GuideKind.RULING))
        assertEquals(BenzeneIcon, guideButtonIcon(GuideKind.CHEMISTRY))
    }

    @Test
    fun `guideOptionIcon resolves dedicated symbol for every option in the menu`() {
        assertEquals(Icons.Outlined.Close, guideOptionIcon(GuideKind.NONE))
        assertEquals(SetsquareIcon, guideOptionIcon(GuideKind.SETSQUARE))
        assertEquals(CompassIcon, guideOptionIcon(GuideKind.COMPASS))
        assertEquals(ProtractorIcon, guideOptionIcon(GuideKind.PROTRACTOR))
        assertEquals(Icons.Outlined.GridOn, guideOptionIcon(GuideKind.RULING))
        assertEquals(BenzeneIcon, guideOptionIcon(GuideKind.CHEMISTRY))
    }

    @Test
    fun `setsquare glyph is a valid 24dp vector icon`() {
        assertEquals("Setsquare", SetsquareIcon.name)
        assertEquals(24f, SetsquareIcon.viewportWidth)
        assertEquals(24f, SetsquareIcon.viewportHeight)
        assertNotNull(SetsquareIcon.defaultWidth)
    }

    @Test
    fun `compass glyph is a valid 24dp vector icon`() {
        assertEquals("Compass", CompassIcon.name)
        assertEquals(24f, CompassIcon.viewportWidth)
        assertEquals(24f, CompassIcon.viewportHeight)
        assertNotNull(CompassIcon.defaultWidth)
    }

    @Test
    fun `protractor glyph is a valid 24dp vector icon`() {
        assertEquals("Protractor", ProtractorIcon.name)
        assertEquals(24f, ProtractorIcon.viewportWidth)
        assertEquals(24f, ProtractorIcon.viewportHeight)
        assertNotNull(ProtractorIcon.defaultWidth)
    }
}
