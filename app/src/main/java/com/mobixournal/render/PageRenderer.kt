package com.mobixournal.render

import android.graphics.Canvas
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.isLivingEntity

/**
 * Draws every layer's elements of a [Page] in z-order at a given scale and canvas offset — strokes
 * via [StrokePainter], everything else via [ElementRenderer]. The page background is drawn
 * separately by [BackgroundRenderer]. Shared by the on-screen [DrawingSurfaceView] and [PdfExporter]
 * so the flattened output matches the editor.
 */
object PageRenderer {

    /**
     * Bounding boxes keyed by element *identity*, so culling doesn't rescan every stroke's points on
     * every frame. The model is immutable — an edited element is a new instance — so a stale entry
     * can't outlive the geometry it describes. Bounded so a long session can't grow it without end.
     */
    private const val BOUNDS_CACHE_MAX = 20_000
    private val boundsCache = java.util.IdentityHashMap<Element, Bounds>()

    private fun boundsOf(element: Element): Bounds =
        boundsCache[element] ?: ElementBounds.of(element).also {
            if (boundsCache.size >= BOUNDS_CACHE_MAX) boundsCache.clear()
            boundsCache[element] = it
        }

    fun drawElements(
        canvas: Canvas,
        page: Page,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        strokes: StrokePainter,
        elements: ElementRenderer,
        hiddenLayers: Set<Int> = emptySet(),
        visible: Bounds? = null,
        /**
         * How a **stroke** of this page is drawn, when the caller wants it drawn differently: the replay
         * path, which dims a stroke the recording has not reached yet and animates the one being written
         * ([com.mobixournal.render.KaraokeStrokeAnimator]). True means it drew the stroke itself, false
         * leaves it to the page, and null — every export, and the screen whenever nothing is playing —
         * draws every stroke the way the element itself says.
         */
        strokeOverride: ((Stroke) -> Boolean)? = null,
    ) {
        page.layers.forEachIndexed { li, layer ->
            if (li in hiddenLayers) return@forEachIndexed
            for (element in layer.elements) {
                // A living entity is a recipe, and what goes on the canvas is its compiled ink
                // ([LivingInk]) — the very ink the `.xopp`, PDF and SVG exports write, so the screen and
                // the file cannot disagree about what a table or a plot looks like. It draws as several
                // pieces, each culled on its own, so a table half off the viewport is not skipped.
                if (element.isLivingEntity) {
                    drawPieces(
                        canvas, LivingInk.of(element).elements, scale, offsetX, offsetY,
                        strokes, elements, visible, strokeOverride,
                    )
                    continue
                }
                // Only the drawing thread passes a viewport, so the identity cache stays single-threaded.
                if (visible != null && !boundsOf(element).intersects(visible)) continue
                draw(canvas, element, scale, offsetX, offsetY, strokes, elements, strokeOverride)
            }
        }
    }

    private fun drawPieces(
        canvas: Canvas,
        pieces: List<Element>,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        strokes: StrokePainter,
        elements: ElementRenderer,
        visible: Bounds?,
        strokeOverride: ((Stroke) -> Boolean)?,
    ) {
        for (piece in pieces) {
            if (visible != null && !boundsOf(piece).intersects(visible)) continue
            draw(canvas, piece, scale, offsetX, offsetY, strokes, elements, strokeOverride)
        }
    }

    /** One element, stroke or not: the split the canvas has always drawn by. */
    private fun draw(
        canvas: Canvas,
        element: Element,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        strokes: StrokePainter,
        elements: ElementRenderer,
        strokeOverride: ((Stroke) -> Boolean)?,
    ) {
        if (element is Stroke) {
            if (strokeOverride?.invoke(element) == true) return
            strokes.draw(
                canvas, element.points, element.tool, element.color, scale, offsetX, offsetY,
                element.lineStyle, element.fill,
            )
        } else {
            elements.draw(canvas, element, scale, offsetX, offsetY)
        }
    }
}
