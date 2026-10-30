package com.mobixournal.desktop

import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind
import java.util.Properties

/**
 * The triangle's and the trapezoid's **own** answers, as the settings file keeps them (`figure.*`).
 *
 * One value for seven keys, because they are one subject: the Figures page changes them together and the
 * drag reads them together ([FigureShape]). An angle outside [MIN_ANGLE]–[MAX_ANGLE] is clamped on the way
 * in, so a hand-edited file cannot ask for a triangle with a zero-degree corner; a name this build does
 * not have falls back to the tablet's own default rather than to no figure at all.
 */
internal object FigureSettings {

    /** The range a configured angle may take, in degrees — bounded away from a degenerate figure. */
    const val MIN_ANGLE = 5.0
    const val MAX_ANGLE = 175.0

    private const val TRIANGLE_KIND = "figure.triangleKind"
    private const val SCALENE_A = "figure.scaleneAngleA"
    private const val SCALENE_B = "figure.scaleneAngleB"
    private const val SCALENE_C = "figure.scaleneAngleC"
    private const val TRAPEZOID_KIND = "figure.trapezoidKind"
    private const val TRAPEZOID_A = "figure.trapezoidAngleA"
    private const val TRAPEZOID_B = "figure.trapezoidAngleB"

    /** Every answer the file holds, with the tablet's own defaults for the keys it has never written. */
    fun read(properties: Properties): FigureShape = FigureShape(
        triangleKind = enumOr(properties.getProperty(TRIANGLE_KIND), TriangleKind.EQUILATERAL),
        scaleneAngleA = angle(properties, SCALENE_A, 40.0),
        scaleneAngleB = angle(properties, SCALENE_B, 60.0),
        scaleneAngleC = angle(properties, SCALENE_C, 80.0),
        trapezoidKind = enumOr(properties.getProperty(TRAPEZOID_KIND), TrapezoidKind.ISOSCELES),
        trapezoidAngleA = angle(properties, TRAPEZOID_A, 75.0),
        trapezoidAngleB = angle(properties, TRAPEZOID_B, 60.0),
    )

    /** Write every answer; none is rounded away, so a reopened window draws what the last one drew. */
    fun write(properties: Properties, shape: FigureShape) {
        properties.setProperty(TRIANGLE_KIND, shape.triangleKind.name)
        properties.setProperty(SCALENE_A, shape.scaleneAngleA.toString())
        properties.setProperty(SCALENE_B, shape.scaleneAngleB.toString())
        properties.setProperty(SCALENE_C, shape.scaleneAngleC.toString())
        properties.setProperty(TRAPEZOID_KIND, shape.trapezoidKind.name)
        properties.setProperty(TRAPEZOID_A, shape.trapezoidAngleA.toString())
        properties.setProperty(TRAPEZOID_B, shape.trapezoidAngleB.toString())
    }

    /** An enum read from its stored name, or [fallback] for a name this build does not have. */
    private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw?.trim() } ?: fallback

    /** One stored angle in degrees, held to the range a figure can be built from. */
    private fun angle(properties: Properties, key: String, fallback: Double): Double =
        properties.getProperty(key)?.toDoubleOrNull()
            ?.takeIf { it.isFinite() }?.coerceIn(MIN_ANGLE, MAX_ANGLE) ?: fallback
}
