package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.isLivingEntity
import java.util.Collections
import java.util.IdentityHashMap

/**
 * **How a living entity is drawn** — the one place that decides it, for the canvas and for every export
 * alike.
 *
 * A living entity is a *recipe* (a table's grid, a plot's formula, a shape's vertices, a component's
 * symbol) and the only drawing of it this app has is the ink its family's compiler produces
 * ([SynthesisTable], [SynthesisPlot], [SynthesisShape], [SynthesisCircuit]). Those compilers are the
 * definition; this object is the *dispatch* around them, so that the editor, the PDF painter and the
 * SVG/PNG writers cannot each invent their own answer — screen and export showing different geometry
 * for one file is the failure this exists to prevent.
 *
 * [XoppSynthesis] uses the same dispatch, which is what makes an export look like what was on screen: a
 * table on a canvas and a table exported to `.xopp` or PDF are compiled by the same code from the same
 * element.
 *
 * Compiled ink is memoised by element *identity* (the model is immutable, so an edited element is a new
 * instance and a stale entry cannot outlive its geometry) and the map is synchronized because the
 * canvas draws on its own thread while an export may run on a worker.
 */
object LivingInk {

    /** What a living entity draws as, and whatever of it could not be drawn. */
    data class Ink(val elements: List<Element>, val uncompiled: List<Uncompilable>)

    /** Bounded like the other identity caches, so a long session cannot grow it without end. */
    private const val CACHE_MAX = 4_000
    private val cache = Collections.synchronizedMap(IdentityHashMap<Element, Ink>())

    /**
     * The ink [element] is drawn as. Only meaningful for a living entity: everything else is its own
     * drawing, so callers test [isLivingEntity] first rather than pay for a lookup per element per frame.
     */
    fun of(element: Element): Ink = cache[element] ?: compile(element).also {
        if (cache.size >= CACHE_MAX) cache.clear()
        cache[element] = it
    }

    private fun compile(element: Element): Ink = when (element) {
        is PlotElement -> {
            val plot = SynthesisPlot.ink(element)
            val losses = plot.unparsed.map { Uncompilable("plotCurve", "\"$it\" is not a formula this build can evaluate") }
            Ink(plot.elements, losses)
        }
        is ParametricShapeElement -> SynthesisShape.ink(element)
            ?.let { Ink(it, emptyList()) }
            ?: Ink(emptyList(), listOf(Uncompilable("parametricShape", "the vertices of a ${element.shape} figure this build cannot draw")))
        is CircuitElement -> SynthesisCircuit.ink(element)
            ?.let { Ink(it, emptyList()) }
            ?: Ink(emptyList(), listOf(Uncompilable("circuit", "\"${element.symbol}\" is not a symbol this build has a drawing for")))
        is TableElement -> Ink(SynthesisTable.ink(element), emptyList())
        // Not a living entity (or one this build does not model): its own drawing, nothing to report.
        else -> Ink(listOf(element), emptyList())
    }
}
