package com.mobixournal.render

import android.graphics.Canvas

/**
 * **A page painted at the audio playhead**: the writing appearing as the recording that was made over
 * it reaches each stroke (`docs/audio-inking-sync.md`, mode 1).
 *
 * The directive is not computed here — [KaraokeStrokeAnimator.resolve] decides, per stroke, whether it
 * is *dimmed* (the recording has not reached it), *animating* (it is being written at this playhead) or
 * *normal* — and this is only what the canvas does with that answer:
 *
 * - a **dimmed** stroke draws in its dimmed colour,
 * - an **animating** stroke draws its visible prefix and a glowing tip at the nib,
 * - a **normal** stroke is left to the page's own painter, exactly as it is drawn with nothing playing.
 *
 * A page painted this way **cannot come out of the ink cache**: that bitmap holds the recording's
 * strokes as *finished* ([InkCache]), and a blit cannot un-draw a stroke that has not been written yet.
 * So one page is drawn directly for as long as a clip sounds — the same work a cache-miss frame already
 * does, paid only while a recording is playing back, and the reason the replay state is checked here
 * rather than inside the cache.
 */
internal fun DrawingSurfaceView.drawKaraokePage(
    canvas: Canvas,
    box: PageBox,
    state: KaraokeStrokeAnimator.State,
    hiddenLayers: Set<Int>,
) {
    PageRenderer.drawElements(
        canvas, box.page, box.scale, box.leftPx - scrollX, box.topPx - scrollY,
        strokePainter, elementRenderer, hiddenLayers, visibleBounds(box),
    ) { stroke -> drawKaraokeStroke(canvas, box, state, stroke) }
}

/**
 * Draw [stroke] the way the replay state says, answering whether it did: **false** for a stroke the
 * directive leaves alone, so the page's own painter draws it and the ordinary path stays the ordinary
 * path, and **true** for the two cases this file exists for.
 */
private fun DrawingSurfaceView.drawKaraokeStroke(
    canvas: Canvas,
    box: PageBox,
    state: KaraokeStrokeAnimator.State,
    stroke: com.mobixournal.format.model.Stroke,
): Boolean {
    val offsetX = box.leftPx - scrollX
    val offsetY = box.topPx - scrollY
    return when (val directive = KaraokeStrokeAnimator.resolve(stroke, state)) {
        KaraokeStrokeAnimator.RenderDirective.Normal -> false

        is KaraokeStrokeAnimator.RenderDirective.Dimmed -> {
            strokePainter.draw(
                canvas, stroke.points, stroke.tool, directive.colorArgb, box.scale,
                offsetX, offsetY, stroke.lineStyle, stroke.fill,
            )
            true
        }

        is KaraokeStrokeAnimator.RenderDirective.Animating -> {
            // Only what the recording has reached: the rest of the stroke is not on the page yet, and
            // showing it dimmed would be drawing writing that has not happened.
            strokePainter.draw(
                canvas, directive.visiblePoints, stroke.tool, stroke.color, box.scale,
                offsetX, offsetY, stroke.lineStyle, stroke.fill,
            )
            directive.activeTip?.let { tip ->
                drawKaraokeTipIndicator(canvas, tip, box.scale, offsetX, offsetY, stroke.color)
            }
            true
        }
    }
}

/**
 * Center the canvas viewport on [bounds] on page or tile [pageIndex].
 */
fun DrawingSurfaceView.scrollToContentBounds(pageIndex: Int, bounds: Bounds) {
    val box = chromeBoxFor(pageIndex) ?: return
    val centerX = box.toViewX((bounds.left + bounds.right) / 2.0, 0f)
    val centerY = box.toViewY((bounds.top + bounds.bottom) / 2.0, 0f)
    viewport.scrollToX(centerX - width / 2f)
    viewport.scrollToY(centerY - height / 2f)
    render()
}
