package com.mobixournal.ui

import com.mobixournal.format.model.ShapeVertex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the compact shape inspector's geometric labels and mini-diagram normalization.
 */
class ShapeInspectorHelpersTest {

    @Test
    fun vertexEdgeAndAngleLabelsFollowGeometricNotation() {
        assertEquals("A", vertexLabel(0))
        assertEquals("C", vertexLabel(2))
        assertEquals("A-B", edgeLabel(0, 3))
        assertEquals("B-C", edgeLabel(1, 3))
        assertEquals("C-A", edgeLabel(2, 3))
        assertEquals("∠A", angleLabel(0))
        assertEquals("∠B", angleLabel(1))
    }

    @Test
    fun normalizePolygonForPreviewFitsInsidePaddedBoxAndCenters() {
        val rect = listOf(
            ShapeVertex(100.0, 200.0),
            ShapeVertex(300.0, 200.0),
            ShapeVertex(300.0, 300.0),
            ShapeVertex(100.0, 300.0),
        )
        val pts = normalizePolygonForPreview(rect, width = 240f, height = 100f, padding = 20f)
        assertEquals(4, pts.size)
        assertTrue(pts.all { it.x in 19.9f..220.1f && it.y in 19.9f..80.1f })
        // Height (100pt) is the limiting dimension: 60px tall -> scale 0.6 -> width 120px, centered in 200px => x in [60, 180]
        assertEquals(60f, pts[0].x, 0.5f)
        assertEquals(180f, pts[1].x, 0.5f)
        assertEquals(20f, pts[0].y, 0.5f)
        assertEquals(80f, pts[2].y, 0.5f)
    }
}
