package com.mobixournal.render

/**
 * **When** a freehand stroke is recognised — one answer, not two flags.
 *
 * The two triggers are exclusive by design (`figure.recognizeShapes`, `figure.recognizeOnHold`): a stroke
 * snapped while the tip rests is already clean geometry, and recognising it a second time at the lift
 * would read that figure back as ink — a held square is four corners and a repeat, which the circle fit
 * finds perfect (see `CanvasHoldSnap`). Carrying the choice as one value makes the illegal pair
 * unrepresentable, and it is what the Settings row offers — one answer at a time, instead of a switch
 * per trigger.
 */
enum class ShapeRecognition(
    /** The word the settings row shows for this answer — the same word on the tablet and in the window. */
    val label: String,
) {
    /** Raw ink, whatever it looks like. */
    OFF("Off"),

    /** Recognise when the stylus is lifted — where both shells started. */
    ON_LIFT("Automatic"),

    /** Recognise while the tip **rests** on the stroke, before the lift, so the snap is watchable. */
    ON_HOLD("On hold"),
}
