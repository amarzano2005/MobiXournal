package com.mobixournal.ui

import com.mobixournal.render.DrawingGuide
import com.mobixournal.render.GuideKind
import com.mobixournal.render.Snapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The remembered drawing-guide pose: what is worth keeping, and that it survives the codec. */
class GuidePoseTest {

    @Test
    fun `every instrument pose round-trips through the codec`() {
        val poses = listOf(
            GuidePose(0, DrawingGuide.Setsquare(x = 120.0, y = 400.0, size = 180.0, angle = 0.0)),
            GuidePose(2, DrawingGuide.Setsquare(x = 12.5, y = 40.25, size = 66.0, angle = 0.78539816)),
            GuidePose(1, DrawingGuide.Compass(x = 300.0, y = 250.0, radius = 100.0)),
            GuidePose(3, DrawingGuide.Protractor(x = 100.0, y = 200.0, radius = 80.0, angle = 1.2)),
        )
        for (pose in poses) {
            val restored = GuidePoseCodec.fromJson(GuidePoseCodec.toJson(pose))
            assertEquals("round trip of $pose", pose, restored)
        }
    }

    @Test
    fun `only the instruments are worth remembering`() {
        assertTrue(DrawingGuide.Setsquare(0.0, 0.0).isRememberable)
        assertTrue(DrawingGuide.Compass(0.0, 0.0).isRememberable)
        assertTrue(DrawingGuide.Protractor(0.0, 0.0).isRememberable)
        // The invisible rules re-derive their anchor from the page, so there is no pose to keep.
        assertFalse(DrawingGuide.Ruling(0.0, 0.0, Snapping.Lattice.NONE).isRememberable)
        assertFalse(DrawingGuide.Chemistry(0.0, 0.0).isRememberable)
        // And the codec writes nothing for them, even if one is handed to it.
        assertEquals("null", GuidePoseCodec.toJson(GuidePose(0, DrawingGuide.Ruling(0.0, 0.0))))
        assertEquals("null", GuidePoseCodec.toJson(GuidePose(0, DrawingGuide.Chemistry(0.0, 0.0))))
        assertNull(GuidePoseCodec.fromJson("null"))
        assertNull(GuidePoseCodec.fromJson(null))
        assertNull(GuidePoseCodec.fromJson(""))
    }

    @Test
    fun `the kind of a live guide is the setting it came from`() {
        assertEquals(GuideKind.SETSQUARE, DrawingGuide.Setsquare(0.0, 0.0).guideKind)
        assertEquals(GuideKind.COMPASS, DrawingGuide.Compass(0.0, 0.0).guideKind)
        assertEquals(GuideKind.PROTRACTOR, DrawingGuide.Protractor(0.0, 0.0).guideKind)
        assertEquals(GuideKind.RULING, DrawingGuide.Ruling(0.0, 0.0).guideKind)
        assertEquals(GuideKind.CHEMISTRY, DrawingGuide.Chemistry(0.0, 0.0).guideKind)
        // Which is what round-trips: place(kind) yields a guide of that kind again.
        for (kind in listOf(GuideKind.SETSQUARE, GuideKind.COMPASS, GuideKind.PROTRACTOR)) {
            assertEquals(kind, kind.place(10.0, 20.0)!!.guideKind)
        }
    }

    @Test
    fun `an unreadable pose reads as no pose rather than a wrong instrument`() {
        for (json in listOf(
            """{"kind": "NOSUCHGUIDE", "x": 1, "y": 2}""",
            """{"x": 1, "y": 2}""",
            """{"kind": "SETSQUARE"}""",
            """{"kind": "RULING", "x": 1, "y": 2}""",
            """{"kind": "CHEMISTRY", "x": 1, "y": 2}""",
            "not json at all",
            "[]",
        )) {
            assertNull("for <$json>", GuidePoseCodec.fromJson(json))
        }
    }

    @Test
    fun `a pose missing its size falls back to the freshly placed one`() {
        val restored = GuidePoseCodec.fromJson("""{"kind": "SETSQUARE", "x": 5, "y": 6}""")
        assertEquals(GuidePose(0, DrawingGuide.Setsquare(5.0, 6.0)), restored)
        // A degenerate size is floored, so a hand-edited pose can never collapse an instrument.
        val tiny = GuidePoseCodec.fromJson("""{"kind": "COMPASS", "x": 0, "y": 0, "radius": 0.0}""")
        assertEquals(DrawingGuide.MIN_SIZE_PT, (tiny!!.guide as DrawingGuide.Compass).radius, 1e-9)
    }
}
