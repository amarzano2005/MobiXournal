package com.mobixournal.ui

import com.mobixournal.render.ShapeBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The polygon tool's factory count moved from six to four, and an install that never chose a count still
 * carries six because every save writes the whole settings object. The migration maps that stored default
 * once; everything else — a count the reader picked, and anything already migrated — is left alone.
 */
class AppSettingsPolygonMigrationTest {

    @Test
    fun `a stored old default becomes the new one`() {
        assertEquals(
            ShapeBuilder.DEFAULT_POLYGON_SIDES,
            migratedPolygonSides(LEGACY_POLYGON_SIDES, alreadyMigrated = false),
        )
        assertEquals("and the new default is four", 4, ShapeBuilder.DEFAULT_POLYGON_SIDES)
    }

    @Test
    fun `a count chosen afterwards is kept, six included`() {
        for (sides in listOf(3, 5, 6, 9, 12)) {
            assertEquals(
                "a migrated profile keeps $sides",
                sides,
                migratedPolygonSides(sides, alreadyMigrated = true),
            )
        }
        assertEquals("a stored count that was never six is untouched", 9, migratedPolygonSides(9, false))
    }
}
