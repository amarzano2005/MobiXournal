package com.mobixournal.ui

import com.mobixournal.render.ShapeRecognition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The **one** recognition answer the Settings row shows: the two stored flags are exclusive, and the row
 * writes them together — a reader who could tick both would have a stroke a hold already snapped read
 * again at the lift.
 */
class ShapeRecognitionSettingTest {

    @Test
    fun theDefaultsReadAsAutomatic() {
        assertEquals(ShapeRecognition.ON_LIFT, AppSettings().shapeRecognition)
        assertEquals("both shells spell it the same way", "Automatic", ShapeRecognition.ON_LIFT.label)
    }

    @Test
    fun eachAnswerWritesItsOwnFlagAndClearsTheOther() {
        val base = AppSettings().withShapeRecognition(ShapeRecognition.ON_HOLD)
        assertEquals(ShapeRecognition.ON_HOLD, base.shapeRecognition)
        assertTrue(base.recognizeOnHold)
        assertFalse("the lift went off with it", base.recognizeShapes)

        val lift = base.withShapeRecognition(ShapeRecognition.ON_LIFT)
        assertEquals(ShapeRecognition.ON_LIFT, lift.shapeRecognition)
        assertFalse("the hold went off in turn", lift.recognizeOnHold)

        val off = lift.withShapeRecognition(ShapeRecognition.OFF)
        assertEquals(ShapeRecognition.OFF, off.shapeRecognition)
        assertFalse("neither trigger is left on", off.recognizeShapes || off.recognizeOnHold)
    }
}
