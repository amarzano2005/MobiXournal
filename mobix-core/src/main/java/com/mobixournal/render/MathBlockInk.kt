package com.mobixournal.render

import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.MathFlavor
import com.mobixournal.format.model.TexImageElement

/**
 * How a living math block becomes `.xopp`'s only math element: a `<teximage>`.
 *
 * Desktop Xournal++ stores a formula as the pixels it rendered plus, in newer files, the LaTeX source
 * in the element's `text` attribute. A math block has no pixels of its own — its source is the
 * authority — so the export writes the source (the desktop application typesets it on open) and marks
 * a non-LaTeX source in a preserved attribute, which is the only channel a `<teximage>` offers for
 * "this is Typst, not LaTeX".
 *
 * One definition, three callers: the `.xopp` synthesis bridge, the direct writer, and the PDF plan's
 * fallback for a source this build cannot lay out. Screen and export therefore cannot disagree about
 * which box a formula occupies or which colour it is.
 */
object MathBlockInk {

    /** The attribute carrying a Typst marker, so a re-import does not read Typst as LaTeX. */
    const val FLAVOR_ATTR: String = "mobixMathFlavor"

    /** [block] as the `<teximage>` a `.xopp` carries. */
    fun texImage(block: MathBlockElement): TexImageElement = TexImageElement(
        left = block.left,
        top = block.top,
        right = block.right,
        bottom = block.bottom,
        latex = block.source,
        color = block.color,
        data = null,
        latexInAttribute = true,
        extraAttrs = buildMap {
            putAll(block.extraAttrs)
            if (block.flavor != MathFlavor.LATEX) put(FLAVOR_ATTR, block.flavor.name)
        },
        // A TexImage is a `.xopp` element whose identity is minted on import; the block's id stays on
        // the `.mobix` side, and the synthesis bridge's metadata claim is what restores it.
        id = block.id,
    )
}
