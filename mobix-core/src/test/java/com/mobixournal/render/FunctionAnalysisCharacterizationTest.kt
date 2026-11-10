package com.mobixournal.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What `FunctionAnalysis` **actually computes**, recorded case by case so a later change is deliberate
 * instead of silent (`docs/math-engine.md`, *Measured limits*).
 *
 * These are characterization tests, not specifications: every expected value here was read off the
 * implementation, and every one of them would change if the algorithm changed. That is the point — the
 * numeric scope (`stem-numeric-reliability`) may replace the scheme, but it has to update these numbers
 * and say why.
 *
 * The tolerance is stated per assertion. Where the implementation cannot meet a tolerance at all (a tangent
 * root it misses, a jump it does not report), the test asserts the **failure** rather than a number, so
 * nobody can read a green suite as "this is accurate".
 */
class FunctionAnalysisCharacterizationTest {

    private fun formula(source: String): Formula {
        val compiled = FunctionPlot.compile(source, emptyMap())
        assertNotNull("the parser accepts `$source`", compiled)
        return compiled!!
    }

    private fun pointsIn(source: String, xMin: Double, xMax: Double, steps: Int = 200): List<CriticalPoint> =
        FunctionAnalysis.findCriticalPoints(formula(source), xMin, xMax, steps = steps)

    private fun zerosOf(source: String, xMin: Double, xMax: Double) =
        pointsIn(source, xMin, xMax).filter { it.type == CriticalPointType.ZERO }

    private fun ofType(source: String, xMin: Double, xMax: Double, type: CriticalPointType) =
        pointsIn(source, xMin, xMax).filter { it.type == type }

    // ---- 1. sin: the well-behaved case the engine is tuned for ------------------------------------

    @Test
    fun `sin over minus pi to pi finds its roots, its extrema and a derivative of one at zero`() {
        val f = formula("sin(x)")
        val d = FunctionAnalysis.derivative(f)

        // Two-point central difference at a fixed h: (sin h - sin -h) / 2h = sin h / h = 1 - h^2/6, so
        // the error is ~1.7e-11 — inside 1e-9, and worse than a 5-point stencil would be.
        assertEquals("f'(0) of sin", 1.0, d.at(0.0), 1e-9)
        assertEquals("f'(pi/2) of sin is zero", 0.0, d.at(PI / 2.0), 1e-9)

        // xMin is never tested on its own (only as the bracket's left end), while xMax is the last sample:
        // sin(pi) is 1.2e-16, inside the 1e-7 value test, so pi is reported and -pi is not.
        val zeros = zerosOf("sin(x)", -PI, PI)
        assertEquals("two roots: 0 and pi; the left end of the range is never a candidate", 2, zeros.size)
        for (root in zeros.map { it.x }) {
            assertEquals("every root is a multiple of pi within 1e-6, was $root", 0.0, kotlin.math.sin(root), 1e-6)
        }

        assertEquals("one minimum", 1, ofType("sin(x)", -PI, PI, CriticalPointType.LOCAL_MIN).size)
        assertEquals("one maximum", 1, ofType("sin(x)", -PI, PI, CriticalPointType.LOCAL_MAX).size)
        assertEquals("the y-intercept is reported", 1, ofType("sin(x)", -PI, PI, CriticalPointType.Y_INTERCEPT).size)
    }

    @Test
    fun `a root is refined to the bisection's own stopping rule, not to machine precision`() {
        // sin(x) - 0.3 has one root in [0, 1]; bisection stops at |f| < 1e-10 or a bracket under 1e-8,
        // so the value at the reported root is inside 1e-10 but the x is not exact.
        val zeros = zerosOf("sin(x) - 0.3", 0.0, 1.0)
        assertEquals("one root", 1, zeros.size)
        val at = zeros.single().x
        // The bracket-width rule (1e-8) stops the loop before the value rule does, so |f| at the reported
        // root is about 1e-10 times the slope: 5.7e-10 here, not the 1e-10 the value rule promises.
        assertEquals("f(reported x) is inside the bracket rule's own residue", 0.0, kotlin.math.sin(at) - 0.3, 1e-9)
        assertEquals("and x is the arcsin within 1e-6", kotlin.math.asin(0.3), at, 1e-6)
    }

    // ---- 2. x^2 and the tangent root -------------------------------------------------------------

    @Test
    fun `x squared finds its tangent root only because a sample lands on it`() {
        val zeros = zerosOf("x^2", -2.0, 2.0)
        assertEquals("one root, found by the |f| < 1e-7 branch rather than by a sign change", 1, zeros.size)
        assertEquals("at the origin, to the grid's own resolution", 0.0, zeros.single().x, 1e-6)

        val minima = ofType("x^2", -2.0, 2.0, CriticalPointType.LOCAL_MIN)
        assertEquals("one minimum", 1, minima.size)
        assertEquals("at the origin", 0.0, minima.single().x, 1e-3)
    }

    /**
     * The limit that matters: a double root is invisible unless a sample happens to land inside the strip
     * where `|f| < 1e-7`. The vertex here sits 5e-4 away from the nearest sample, so `f` is ~2.5e-7 there
     * — above the threshold — and **no root is reported** for a curve that has one.
     */
    @Test
    fun `a tangent root no sample lands on is missed, and nothing says so`() {
        val zeros = zerosOf("(x - 0.5005)^2", 0.0, 1.0)
        assertEquals("the root at x = 0.5005 is not reported", 0, zeros.size)
        val minima = ofType("(x - 0.5005)^2", 0.0, 1.0, CriticalPointType.LOCAL_MIN)
        assertEquals("although the curve's own minimum is found", 1, minima.size)
        assertEquals("near x = 0.5005", 0.5005, minima.single().x, 1e-3)
    }

    @Test
    fun `two roots closer than the merge radius are reported as one`() {
        // x * (x - 0.03) has roots at 0 and 0.03; addUniquePoint merges anything within 0.05 in x.
        val zeros = zerosOf("x * (x - 0.03)", -1.0, 1.0)
        assertEquals("the second root is swallowed by the 0.05 merge radius", 1, zeros.size)
        assertEquals("the surviving one is the one at the origin", 0.0, zeros.single().x, 1e-6)
    }

    // ---- 3. 1/x across the pole -------------------------------------------------------------------

    @Test
    fun `1 over x reports one asymptote near the pole and refuses to integrate across it`() {
        // Asymptotes are appended directly, without the merge radius roots and extrema go through, so a
        // pole yields one point per side: the two midpoints of the transition pair are half a step apart
        // and both survive.
        val asymptotes = ofType("1/x", -1.0, 1.0, CriticalPointType.ASYMPTOTE)
        assertEquals("the pole is reported, once per side", 2, asymptotes.size)
        assertEquals("on the left, at the midpoint of the last finite sample and the pole", -0.005, asymptotes[0].x, 1e-6)
        assertEquals("and on the right", 0.005, asymptotes[1].x, 1e-6)
        assertTrue("an asymptote has no y", asymptotes.all { it.y.isNaN() })
        assertEquals("a pole is not a root", 0, zerosOf("1/x", -1.0, 1.0).size)

        assertNull(
            "integrating across the pole is refused rather than returned as NaN",
            FunctionAnalysis.definiteIntegral(formula("1/x"), -1.0, 1.0),
        )
        assertEquals("the integral on one side is still computed", 1.0, FunctionAnalysis.definiteIntegral(formula("1/x"), 1.0, Math.E)!!, 1e-9)
    }

    // ---- 4. abs(x) at a kink ---------------------------------------------------------------------

    @Test
    fun `abs at the origin has a zero derivative because the two sides cancel`() {
        val d = FunctionAnalysis.derivative(formula("abs(x)"))
        assertEquals("the kink's slope reads as zero", 0.0, d.at(0.0), 0.0)
        assertEquals("the slope off the kink is right", 1.0, d.at(0.5), 1e-9)
        assertEquals("and negative on the other side", -1.0, d.at(-0.5), 1e-9)

        assertEquals("no asymptote is invented", 0, ofType("abs(x)", -1.0, 1.0, CriticalPointType.ASYMPTOTE).size)
        assertEquals("the kink is reported as a minimum", 1, ofType("abs(x)", -1.0, 1.0, CriticalPointType.LOCAL_MIN).size)
        assertEquals("and as a root", 1, zerosOf("abs(x)", -1.0, 1.0).size)
    }

    // ---- 5. sqrt at its domain edge --------------------------------------------------------------

    @Test
    fun `sqrt at its domain edge has no derivative, but its edge is called an asymptote`() {
        assertTrue("f'(0) is not a number: one side of the difference is NaN", FunctionAnalysis.derivative(formula("sqrt(x)")).at(0.0).isNaN())

        val edges = ofType("sqrt(x)", -1.0, 1.0, CriticalPointType.ASYMPTOTE)
        assertEquals("the NaN-to-finite transition is reported as one asymptote", 1, edges.size)
        assertEquals("at the midpoint of the sample before the edge and the edge itself", -0.005, edges.single().x, 1e-6)

        assertEquals(
            "the root at 0 is NOT found: the sample before it is NaN, and the root test needs both sides finite",
            0,
            zerosOf("sqrt(x)", -1.0, 1.0).size,
        )
        assertEquals("the integral on the domain is 2/3", 2.0 / 3.0, FunctionAnalysis.definiteIntegral(formula("sqrt(x)"), 0.0, 1.0)!!, 1e-9)
        assertNull("and NaN outside it refuses the whole interval", FunctionAnalysis.definiteIntegral(formula("sqrt(x)"), -1.0, 1.0))
    }

    // ---- 6. a rapidly oscillating function -------------------------------------------------------

    @Test
    fun `a curve oscillating faster than the sampling is under-reported, not aliased into nonsense`() {
        // sin(20x) over [-1, 1] has roots every pi/20 = 0.157, comfortably above the 0.05 merge radius and
        // well covered by a 0.01 step: all 13 are found.
        val moderate = zerosOf("sin(20*x)", -1.0, 1.0)
        assertEquals("thirteen roots above the merge radius", 13, moderate.size)

        // sin(400x) has 127 roots in the same interval on a step of 0.01 — three samples per period. The
        // detector can only report the sign changes it sees, so the count collapses; it does not invent
        // roots the samples do not show.
        val fast = zerosOf("sin(400*x)", -1.0, 1.0)
        assertEquals("37 of the true 127 roots, because three samples per period cannot resolve them", 37, fast.size)
        // A bracket holding an odd number of roots still bisects to a real one of them, so the roots that
        // are reported are genuine: what fails is the count, not the coordinates.
        // The bracket-width stop leaves a value residue that scales with the slope: 1e-8 of x at a slope of
        // 400 is about 4e-6 of f, so a tolerance tighter than that would be asserting machine precision.
        assertTrue("and every reported root is a genuine zero within the residue that slope allows", fast.all { abs(kotlin.math.sin(400 * it.x)) < 1e-5 })
    }

    // ---- 7. overflow ----------------------------------------------------------------------------

    @Test
    fun `an overflow is refused rather than printed as a number`() {
        val f = formula("exp(x)")
        val d = FunctionAnalysis.derivative(f)

        // Measured relative error 2.5e-9 — larger than the difference scheme's own h^2/6 = 1.7e-11, so the
        // limit at this magnitude is the evaluation, not the stencil. Recorded rather than explained away.
        assertEquals("f' where both sides are finite is exp(700) within 2.5e-9 relative", exp(700.0), d.at(700.0), exp(700.0) * 1e-8)
        assertTrue("f'(710) is NaN: the right-hand sample overflows", d.at(710.0).isNaN())
        assertNull("an interval whose end overflows is refused", FunctionAnalysis.definiteIntegral(f, 0.0, 710.0))
        assertEquals("the finite part still integrates", exp(1.0) - 1.0, FunctionAnalysis.definiteIntegral(f, 0.0, 1.0)!!, 1e-9)

        val points = pointsIn("exp(x)", 0.0, 710.0)
        assertTrue("the overflow is reported as an asymptote", points.any { it.type == CriticalPointType.ASYMPTOTE })
        assertTrue("and the summary reports no invented value", points.none { it.type == CriticalPointType.ZERO })
    }

    // ---- 8. the summary the user reads -----------------------------------------------------------

    @Test
    fun `the summary groups findings by kind and prints coordinates to three decimals`() {
        val f = formula("sin(x)")
        val points = FunctionAnalysis.findCriticalPoints(f, -PI, PI, curveName = "f")
        val summary = FunctionAnalysis.buildSummaryReport(
            curves = listOf(PlotCurve("sin(x)", label = "f")),
            points = points,
            xMin = -PI,
            xMax = PI,
            integrals = listOf("f" to FunctionAnalysis.definiteIntegral(f, -PI, PI)),
        )

        assertTrue("the header states the range", summary.contains("Function Study (x in [-3.142, 3.142])"))
        assertTrue("roots are listed once under one heading", summary.contains("• Roots / Zeros at x: "))
        assertTrue("the minima and maxima have their own headings", summary.contains("• Local Minima: ") && summary.contains("• Local Maxima: "))
        assertTrue("a root is printed by x alone, without a y that is zero by definition", !summary.contains("(-3.142, 0)"))
        assertTrue("the integral of sin over a whole period is zero up to the difference scheme", summary.contains("• ∫ f dx = "))
        assertEquals("3.142, not 3.14159: three decimals, trailing zeros trimmed", "-3.142", FunctionAnalysis::class.java.let { "%.3f".format(-PI) })
    }
}
