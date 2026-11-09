package com.mobixournal.desktop

import com.mobixournal.desktop.ink.*
import com.mobixournal.format.model.StrokePoint
import org.junit.Assert.*
import org.junit.Test

class IncrementalSmoothingTest {
    @Test fun everyPrefixMatchesFullSmoothingAndProcessesABoundedTail() {
        val random = java.util.Random(3)
        val points = ArrayList<StrokePoint>()
        val incremental = IncrementalSmoothing()
        repeat(1000) { index ->
            points += StrokePoint(index.toDouble(), if (index % 7 == 0) 8.0 else random.nextDouble(), 1.0 + index % 4)
            incremental.update(points)
            assertEquals("curves at prefix $index", smoothedInk(points), incremental.curves)
            assertEquals("taper samples at prefix $index", smoothPointsForTaper(points), incremental.points)
        }
        assertTrue(incremental.processedControls < 4000)
        incremental.update(points.take(30).map { it.copy(x = it.x + 3) })
        assertEquals(smoothPointsForTaper(points.take(30).map { it.copy(x = it.x + 3) }), incremental.points)
    }
}
