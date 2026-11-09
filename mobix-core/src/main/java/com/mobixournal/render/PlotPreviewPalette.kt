package com.mobixournal.render

import com.mobixournal.format.model.PlotElement
import kotlin.math.pow

/** Preview-only contrast adaptation; never mutate the saved element or its synthesis/export colors. */
object PlotPreviewPalette {
    fun adapt(plot: PlotElement, background: Int, axis: Int, accent: Int): PlotElement = plot.copy(
        axisColor = axis,
        curves = plot.curves.map { curve ->
            if (contrast(curve.color, background) >= 3.0) curve else curve.copy(color = accent)
        },
    )

    fun contrast(first: Int, second: Int): Double {
        val a = luminance(first); val b = luminance(second)
        return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color ushr shift) and 255) / 255.0
            return if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
        }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }
}
