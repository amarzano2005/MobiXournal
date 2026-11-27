package com.mobixournal.format.mobix

import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.SplineElement
import com.mobixournal.format.model.SplineNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobixSplineCodecTest {

    @Test
    fun roundTripsSplineWithExplicitAndAutoHandles() {
        val spline = SplineElement(
            nodes = listOf(
                SplineNode(10.0, 20.0, outX = 25.0, outY = 30.0),
                SplineNode(60.0, 80.0, inX = 45.0, inY = 70.0, smooth = false),
                SplineNode(110.0, 20.0),
            ),
            closed = true,
            color = 0xFFD32F2F.toInt(),
            width = 2.5,
            lineStyle = LineStyle.DOTTED,
            id = "spl_test1",
        )
        val encoded = MobixElementCodec.encode(spline, MobixAssetSink())
        val decoded = MobixElementCodec.decode(encoded, MobixAssetSource { null }) as SplineElement
        assertEquals(spline, decoded)
    }

    @Test
    fun malformedSplineFallsBackToRawElement() {
        val bad = MobixJson.obj {
            it["type"] = com.mobixournal.format.json.JsonNode.of("spline")
            it["nodes"] = com.mobixournal.format.json.JsonNode.arr(emptyList())
        }
        val decoded = MobixElementCodec.decode(bad, MobixAssetSource { null })
        assertTrue(decoded is RawElement)
    }
}
