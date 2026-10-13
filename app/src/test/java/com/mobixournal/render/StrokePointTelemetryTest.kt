package com.mobixournal.render

import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixAssetSource
import com.mobixournal.format.mobix.MobixElementCodec
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StrokePointTelemetryTest {

    @Test
    fun strokePointRecordsMillisecondDeltaRelativeToStart() {
        strokeStartEventTimeMs = 10_000L

        // Point at stroke start (t = 10_000ms -> dt = 0ms)
        val p0 = StrokePoint(10.0, 20.0, 1.5, pressure = 0.5f, tilt = 10f, azimuth = 20f, dtMs = 0)
        assertEquals(0, p0.dtMs)

        // Point 15ms later (t = 10_015ms -> dt = 15ms)
        val p1 = StrokePoint(12.0, 22.0, 1.6, pressure = 0.6f, tilt = 11f, azimuth = 21f, dtMs = 15)
        assertEquals(15, p1.dtMs)

        // Point 45ms later (t = 10_045ms -> dt = 45ms)
        val p2 = StrokePoint(15.0, 25.0, 1.8, pressure = 0.7f, tilt = 12f, azimuth = 22f, dtMs = 45)
        assertEquals(45, p2.dtMs)

        val stroke = Stroke(
            tool = Tool.PEN,
            color = 0xFF000000.toInt(),
            capStyle = "round",
            points = listOf(p0, p1, p2),
            uniformWidth = false,
            extraAttrs = mapOf("fn" to "assets/aud_test.opus", "ts" to "1500"),
        )

        val sink = MobixAssetSink()
        val json = MobixElementCodec.encode(stroke, sink)
        val decoded = MobixElementCodec.decode(json, MobixAssetSource { null }) as? Stroke

        assertNotNull(decoded)
        assertEquals(3, decoded!!.points.size)
        assertEquals(0, decoded.points[0].dtMs)
        assertEquals(15, decoded.points[1].dtMs)
        assertEquals(45, decoded.points[2].dtMs)
        assertEquals(10f, decoded.points[0].tilt)
        assertEquals(20f, decoded.points[0].azimuth)
    }
}
