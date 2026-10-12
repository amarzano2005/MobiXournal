package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement

/**
 * A loss the export had to accept: an element it could not draw, or a part of one. [type] names what
 * was lost (an element kind, or `plotCurve`) and [reason] says which one and why, in the terms the
 * user wrote it in. The export's honest half: a symbol or a formula this build has no drawing for is
 * **stated**, not silently left out of the file.
 */
data class Uncompilable(val type: String, val reason: String)

/**
 * What an export produced: a `.xopp` document, and whatever could not be drawn into it — whether that
 * was a whole element or one curve of a plot.
 */
data class SynthesisResult(val document: Document, val uncompiled: List<Uncompilable>)

/**
 * The **synthesis export**: the step that turns a document holding living entities into one the
 * `.xopp` writer can write in full (`docs/mobix-format.md`, the write half of the bridge).
 *
 * A living entity is a *recipe* — a table's grid, a plot's formula, a shape's vertices, a component's
 * symbol — and `.xopp` has no place to keep a recipe, so the export cooks it: each element is
 * replaced, **in its own position in its layer**, by the ink it stands for. Nothing else about the
 * document changes, so the `.xopp` model the other direction reads is untouched and the round trip a
 * `.xopp`-only user cares about keeps working.
 *
 * What this deliberately does **not** do is pretend. Three cases lose something, and all three are
 * reported rather than guessed at ([uncompiled]):
 *
 *  - A symbol or shape kind this build has no drawing for — there is no ink to invent.
 *  - A curve whose formula does not parse, whether that leaves the plot empty or one curve short.
 *  - An element preserved from a *newer* `.mobix` whose body is JSON: it has no `.xopp` spelling at
 *    all, which is why the writer skips it.
 *
 * What is lost silently, because the format has nowhere to put it, is the stylus telemetry of a
 * stroke (`tilt`, `azimuth`, `dtMs`) and the *editable* nature of every entity above: the exported
 * `.xopp` holds a drawing of a table, which is exactly what desktop Xournal++ can read.
 */
object XoppSynthesis {

    /** The document [document] as `.xopp` ink, plus whatever could not be compiled. */
    fun synthesize(document: Document): SynthesisResult {
        val gaps = mutableListOf<Uncompilable>()
        val pages = document.pages.map { page ->
            page.copy(
                layers = page.layers.map { layer ->
                    layer.copy(elements = layer.elements.flatMap { inkOf(it, gaps) })
                },
            )
        }
        return SynthesisResult(document.copy(pages = pages), gaps)
    }

    /**
     * The ink [element] becomes: itself when it is already ink (or markup the `.xopp` writer can
     * carry), the compiled figure when it is a living entity, and nothing — with a [gaps] entry —
     * when this build cannot draw it.
     */
    private fun inkOf(element: Element, gaps: MutableList<Uncompilable>): List<Element> = when (element) {
        is Stroke, is TextElement, is ImageElement, is TexImageElement -> listOf(element)
        is TableElement -> SynthesisTable.ink(element)
        is ParametricShapeElement -> SynthesisShape.ink(element) ?: gap(
            gaps, "parametricShape", "the vertices of a ${element.shape} figure this build cannot draw",
        )
        is PlotElement -> {
            val plot = SynthesisPlot.ink(element)
            plot.unparsed.forEach {
                gaps += Uncompilable("plotCurve", "\"$it\" is not a formula this build can evaluate")
            }
            if (plot.elements.isEmpty() && plot.unparsed.isEmpty()) {
                gaps += Uncompilable("plot", "this plot has no curve to draw")
            }
            plot.elements
        }
        is CircuitElement -> SynthesisCircuit.ink(element) ?: gap(
            gaps, "circuit", "\"${element.symbol}\" is not a symbol this build has a drawing for",
        )
        // Markup that came from a `.xopp` exports as itself; a body in this format's own JSON has no
        // `.xopp` spelling, so leaving it out is the only honest answer — and it is said out loud.
        is RawElement -> if (element.syntax == RawElement.Syntax.XML) {
            listOf(element)
        } else {
            gap(gaps, element.name, "an element from a newer build, which has no .xopp spelling")
        }
    }

    private fun gap(gaps: MutableList<Uncompilable>, type: String, reason: String): List<Element> {
        gaps += Uncompilable(type, reason)
        return emptyList()
    }
}
