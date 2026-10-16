package com.mobixournal.render

import com.mobixournal.format.mobix.MobixMetaBlock
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.mobix.MobixMetaClaim
import com.mobixournal.format.mobix.MobixMetaDigest
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.isLivingEntity

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
 *
 * **The recipe is not thrown away, though.** Alongside the ink the export writes a *bridge block*
 * (`Document.bridge`, read back by the `.mobix` layer): one claim per entity — where its ink landed,
 * and the digest of that ink — so an `.xopp` this app produced can be re-imported as the document it
 * was, and an `.xopp` that was edited meanwhile is read as the edit and the claim dropped
 * ([MobixMetaBlock], `docs/mobix-format.md`).
 */
object XoppSynthesis {

    /**
     * The document [document] as `.xopp` ink, plus whatever could not be compiled.
     *
     * @param documentId the `.mobix` document's identity, when the caller knows it — recorded in the
     *   block for the sake of a later "which export was this?", and nothing the import decides on.
     */
    fun synthesize(document: Document, documentId: String? = null): SynthesisResult {
        val gaps = mutableListOf<Uncompilable>()
        val claims = mutableListOf<MobixMetaClaim>()
        // A page at a time, through the document's **page surface**: a document opened page by page
        // (`.mobix`) is read one page per pass and the reader's window releases it again, so the
        // export's working set is the output it is building rather than the source plus a copy.
        val source = PageSource.of(document.pages)
        val pages = ArrayList<Page>(source.pageCount)
        for (pageIndex in 0 until source.pageCount) {
            val page = source.page(pageIndex)
            pages += page.copy(
                layers = page.layers.mapIndexed { layerIndex, layer ->
                    layer.copy(elements = compileLayer(layer.elements, pageIndex, layerIndex, claims, gaps))
                },
            )
        }
        return SynthesisResult(
            document = document.copy(pages = pages, bridge = block(document, claims, documentId)),
            uncompiled = gaps,
        )
    }

    /**
     * One layer's elements flattened, remembering where each living entity's ink ended up: the block's
     * claim is the *pair* of the index range and the digest of what is in it, which is what makes the
     * claim checkable on the way back in.
     */
    private fun compileLayer(
        elements: List<Element>,
        pageIndex: Int,
        layerIndex: Int,
        claims: MutableList<MobixMetaClaim>,
        gaps: MutableList<Uncompilable>,
    ): List<Element> {
        val out = mutableListOf<Element>()
        for (element in elements) {
            val at = out.size
            val ink = inkOf(element, gaps)
            out += ink
            if (element.isLivingEntity && ink.isNotEmpty()) {
                claims += MobixMetaClaim(
                    page = pageIndex,
                    layer = layerIndex,
                    at = at,
                    count = ink.size,
                    sha256 = MobixMetaDigest.of(ink),
                    element = element,
                )
            }
        }
        return out
    }

    /**
     * The block that goes into the file.
     *
     * A document that produced no claims — no living entities in it — had already been flattened, so the
     * synthesis is the identity on it: whatever block it carried is left exactly as it was rather than
     * replaced by an empty one, because the entities it names are still there, still as that ink, and
     * their claims still verify (they are checked on the way back in, [MobixMetaBlock]). A document that
     * *did* produce claims gets a block describing this synthesis: the ink it was built from is the ink
     * being written, so any earlier claims are about a drawing that no longer exists.
     */
    private fun block(document: Document, claims: List<MobixMetaClaim>, documentId: String?): String? =
        if (claims.isEmpty()) document.bridge else MobixMetaBlock(doc = documentId, claims = claims).encode()

    /**
     * The ink [element] becomes: itself when it is already ink (or markup the `.xopp` writer can carry),
     * the compiled figure when it is a living entity ([LivingInk], the *same* dispatch the canvas and the
     * other exports draw through, so what is written is what was on screen), and nothing — with a [gaps]
     * entry — when this build cannot draw it.
     */
    private fun inkOf(element: Element, gaps: MutableList<Uncompilable>): List<Element> = when (element) {
        is Stroke, is TextElement, is ImageElement, is TexImageElement -> listOf(element)
        is PlotElement, is TableElement, is ParametricShapeElement, is CircuitElement -> {
            val ink = LivingInk.of(element)
            gaps += ink.uncompiled
            // A plot with no curve at all is a different loss from a curve that would not parse: there is
            // nothing to draw and nothing named, which is worth saying rather than writing an empty plot.
            if (ink.elements.isEmpty() && ink.uncompiled.isEmpty()) {
                gaps += Uncompilable(element.plotKind(), "this ${element.plotKind()} has no curve to draw")
            }
            ink.elements
        }
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

    /**
     * What to call [element]'s family in a report — "plot" for the empty-plot case, which is the only
     * place the export needs a name for a whole element rather than for one of its parts.
     */
    private fun Element.plotKind(): String = when (this) {
        is PlotElement -> "plot"
        is TableElement -> "table"
        is ParametricShapeElement -> "parametricShape"
        is CircuitElement -> "circuit"
        else -> "element"
    }
}
