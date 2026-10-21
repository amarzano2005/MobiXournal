package com.mobixournal.render

import kotlin.math.abs

/**
 * Evaluates piecewise functions and parametric curve sampling.
 *
 * Piecewise syntax:
 * Branches separated by semicolons or commas.
 * Each branch: `condition: expression` or `expression if condition` or `default: expression`.
 * Example: `x < 0: -x; x >= 0: x^2`
 */
object AdvancedCurves {

    data class PiecewiseBranch(
        val condition: (Double) -> Boolean,
        val formula: Formula,
    )

    /**
     * Parses a piecewise expression like `x < 0: -x; x >= 0: x^2` or `x < 0: -x, x >= 0: x^2`.
     * Returns a [Formula], or null if the syntax cannot be parsed.
     */
    fun compilePiecewise(source: String): Formula? {
        val trimmed = source.trim()
        if (!trimmed.contains(":") && !trimmed.contains(" if ")) return null
        val parts = if (trimmed.contains(";")) trimmed.split(";") else trimmed.split(",")
        val branches = mutableListOf<PiecewiseBranch>()

        for (part in parts) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val branch = parseBranch(p) ?: return null
            branches.add(branch)
        }
        if (branches.isEmpty()) return null

        return Formula { x ->
            for (b in branches) {
                if (b.condition(x)) {
                    return@Formula b.formula.at(x)
                }
            }
            Double.NaN
        }
    }

    private fun parseBranch(text: String): PiecewiseBranch? {
        val (condStr, exprStr) = when {
            text.contains(":") -> {
                val idx = text.indexOf(":")
                text.substring(0, idx).trim() to text.substring(idx + 1).trim()
            }
            text.contains(" if ") -> {
                val idx = text.indexOf(" if ")
                text.substring(idx + 4).trim() to text.substring(0, idx).trim()
            }
            else -> return null
        }

        val formula = FunctionPlot.compile(exprStr) ?: return null
        val condition = parseCondition(condStr) ?: return null
        return PiecewiseBranch(condition, formula)
    }

    private fun parseCondition(cond: String): ((Double) -> Boolean)? {
        val c = cond.lowercase().replace(" ", "")
        if (c == "else" || c == "default" || c == "otherwise" || c.isEmpty()) {
            return { true }
        }

        // Support forms like x<0, x<=0, x>0, x>=0, x==0, 0<=x<5
        return when {
            c.startsWith("x<=") -> {
                val v = c.removePrefix("x<=").toDoubleOrNull() ?: return null
                { x -> x <= v }
            }
            c.startsWith("x>=") -> {
                val v = c.removePrefix("x>=").toDoubleOrNull() ?: return null
                { x -> x >= v }
            }
            c.startsWith("x<") -> {
                val v = c.removePrefix("x<").toDoubleOrNull() ?: return null
                { x -> x < v }
            }
            c.startsWith("x>") -> {
                val v = c.removePrefix("x>").toDoubleOrNull() ?: return null
                { x -> x > v }
            }
            c.startsWith("x==") || c.startsWith("x=") -> {
                val v = c.removePrefix("x==").removePrefix("x=").toDoubleOrNull() ?: return null
                { x -> abs(x - v) < 1e-6 }
            }
            else -> null
        }
    }

    /**
     * Samples a 2D parametric curve: x = x(t), y = y(t) for t in [tMin]..[tMax].
     */
    fun sampleParametric(
        xFormula: Formula,
        yFormula: Formula,
        tMin: Double,
        tMax: Double,
        samples: Int = 600,
    ): List<Pair<Double, Double>> {
        val n = samples.coerceIn(10, 4000)
        val out = ArrayList<Pair<Double, Double>>(n + 1)
        val step = (tMax - tMin) / n
        for (i in 0..n) {
            val t = tMin + i * step
            val x = xFormula.at(t)
            val y = yFormula.at(t)
            if (x.isFinite() && y.isFinite()) {
                out.add(x to y)
            } else {
                out.add(Double.NaN to Double.NaN)
            }
        }
        return out
    }
}
