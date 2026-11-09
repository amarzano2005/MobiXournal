package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import org.junit.Assert.*
import org.junit.Test

class IncrementalTaperTest {
    @Test fun everyPrefixMatchesTheFullContourIncludingRepeatedPointsAndProjectionChanges() {
        val random = java.util.Random(4)
        val points = ArrayList<StrokePoint>()
        val taper = IncrementalTaper()
        repeat(1000) { index ->
            val x = if (index % 5 == 0) points.lastOrNull()?.x ?: 0.0 else random.nextDouble() * 100
            val y = if (index % 5 == 0) points.lastOrNull()?.y ?: 0.0 else random.nextDouble() * 100
            points += StrokePoint(x, y, random.nextDouble() * 4)
            if (points.size >= 2) assertArrayEquals(InkGeometry.taperOutline(points, 2f, 3f, 4f),
                taper.outline(points, 2f, 3f, 4f, index), 0f)
        }
        assertTrue(taper.processedPoints < 3000)
        assertArrayEquals(InkGeometry.taperOutline(points, 1f, -2f, 7f), taper.outline(points, 1f, -2f, 7f, points.size), 0f)
    }
}
