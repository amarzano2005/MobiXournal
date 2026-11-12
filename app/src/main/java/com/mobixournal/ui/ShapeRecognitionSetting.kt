package com.mobixournal.ui

import com.mobixournal.render.ShapeRecognition

/**
 * The **one** recognition answer the settings row shows and the canvas obeys.
 *
 * The two stored flags are exclusive — `recognizeShapes` for the lift, `recognizeOnHold` for a resting
 * tip — so they are read and written here, once. A page that offered them as two switches let a reader
 * turn both on, and the recogniser then read a stroke a hold had already snapped (`ShapeRecognition`'s
 * own note is why that turns a held square back into a circle).
 */
val AppSettings.shapeRecognition: ShapeRecognition
    get() = when {
        recognizeShapes -> ShapeRecognition.ON_LIFT
        recognizeOnHold -> ShapeRecognition.ON_HOLD
        else -> ShapeRecognition.OFF
    }

/** [this] with the reader's chosen trigger — and the other one off, because there is only ever one. */
fun AppSettings.withShapeRecognition(value: ShapeRecognition): AppSettings = copy(
    recognizeShapes = value == ShapeRecognition.ON_LIFT,
    recognizeOnHold = value == ShapeRecognition.ON_HOLD,
)
