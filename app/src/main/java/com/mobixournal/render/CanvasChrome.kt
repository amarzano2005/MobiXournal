package com.mobixournal.render

import android.graphics.DashPathEffect
import com.mobixournal.format.XoppColor.withAlpha
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Every non-document brush the canvas paints with: the backdrop behind the page stack, the
 * selection outline and its handles, the drag-select band, the setsquare/compass overlay, the
 * page-overview reorder/selection feedback, the stylus hover dot and the PDF-text highlight.
 *
 * These are chrome, not content — nothing here is ever written to the `.xopp`. Keeping them in one
 * holder is what lets [DrawingSurfaceView] stay about input and document state: the view asks for a
 * brush, and [applyChromeColors] is the single place the app's Material 3 scheme lands (the
 * `SurfaceView` sits outside the Compose tree, so the colours are pushed in from the hosting
 * composable — see `com.mobixournal.ui.theme.CanvasChromeColors`).
 */
internal class CanvasChrome {

    /** Backdrop behind the pages — replaced by the app's colour scheme via [applyChromeColors]. */
    var backdropColor = BACKDROP
        private set

    /**
     * Hairline traced around each sheet. The backdrop is the same colour as the app's bars now, so on a
     * light chrome this outline is the only thing separating a near-white page from a near-white surround.
     */
    val pageOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = PAGE_OUTLINE
    }

    /** Scratch rect for the page outline — reused each frame so the paint loop allocates nothing. */
    val pageOutlineRect = RectF()

    /** Selection outline: 2 px dashed stroke (10 px dash, 8 px gap). */
    val selectionStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = SELECTION_COLOR
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    /** Selection fill: translucent wash under the dashed outline. */
    val selectionFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = SELECTION_FILL
    }
    /** Selection handle dots: solid fill, [HANDLE_DRAW_PX] radius. */
    val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = SELECTION_COLOR
    }
    /** Selection handle arms: 2 px solid stroke connecting handles. */
    val handleArm = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = SELECTION_COLOR
    }
    /** Drag-select band fill: translucent wash under the lasso. */
    val bandFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = BAND_FILL
    }


    /** Setsquare/compass guide outline: 2 px solid amber stroke (no dash — physical instrument feel). */
    val guideStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = GUIDE_COLOR
    }
    /** Setsquare/compass guide fill: faint amber wash ([GUIDE_FILL_ALPHA]). */
    val guideFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = GUIDE_COLOR.withAlpha(GUIDE_FILL_ALPHA)
    }
    /** Setsquare/compass guide handle dots: solid amber fill. */
    val guideHandle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = GUIDE_COLOR
    }
    /**
     * The chemistry guide's bond mesh: a 1 px stroke at [GUIDE_MESH_ALPHA], faint enough to read as a
     * guide laid over the ink rather than as a drawing of its own.
     */
    val guideMesh = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = GUIDE_COLOR.withAlpha(GUIDE_MESH_ALPHA)
    }
    /** Protractor angle text labels. */
    val guideText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = GUIDE_COLOR
        textSize = 10f
        textAlign = Paint.Align.CENTER
    }

    /** Interactive parameter slider background pill. */
    val sliderBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xDD242424.toInt()
    }
    /** Interactive parameter slider card border. */
    val sliderCardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x33FFFFFF
    }
    /** Interactive parameter slider inactive track line. */
    val sliderTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        color = 0x55FFFFFF
    }
    /** Interactive parameter slider active track line. */
    val sliderTrackActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        color = SELECTION_COLOR
    }
    /** Interactive parameter slider thumb knob. */
    val sliderThumb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = SELECTION_COLOR
    }
    /** Interactive parameter slider thumb white border. */
    val sliderThumbBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFFFFFFFF.toInt()
    }
    /** Interactive parameter slider name and value text. */
    val sliderText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFECEFF1.toInt()
        textSize = 20f
        isFakeBoldText = true
    }

    /** Hover dot; its colour tracks the pen, so it is set per frame rather than by the scheme. */
    val hover = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    /**
     * The eraser tip outline — a thin black ring at the rubber's true radius, so its boundary is
     * visible while hovering and while erasing. Fixed black: it marks a hole, not a colour.
     */
    val eraserOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0xFF000000.toInt()
    }
    /** PDF-text selection highlight: translucent blue wash. */
    val textSelect = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = TEXT_SELECT_FILL
    }
    /** Document/PDF search result highlight: translucent yellow wash. */
    val searchHit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = SEARCH_HIT_FILL
    }
    /** Current search result outline: 3 px amber stroke. */
    val searchCurrent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = SEARCH_CURRENT_STROKE
    }

    // --- the pen-tip radial HUD (docs/ui-modernization.md, *The pen-tip HUD*) -------------------

    /** The HUD's glass disc, at the floating surface's own fill so it is the same material as a menu. */
    val hudSurface = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = HUD_SURFACE
    }
    /** The HUD's hairline: the ring at the nib and each slot's own rim. */
    val hudRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = HUD_RIM
    }
    /** A swatch's fill — the one paint whose colour is the user's, not the theme's. */
    val hudSwatch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = HUD_RIM
    }
    /**
     * A swatch's own rim, a touch stronger than the ring's hairline: white is a palette colour, and
     * on the light theme's white glass an outlined-by-12 % swatch would be a hole in the palette. At
     * 35 % the white and the black both read as objects without shouting over their neighbours.
     */
    val hudSwatchRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = HUD_RIM
    }
    /** The ring's plain ink: a width dot, the nib marker. */
    val hudInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = HUD_INK
    }
    /** A tool slot's monoline glyph, drawn from the shared catalogue. */
    val hudGlyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = HUD_INK
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    /** The picked slot's plate — the contract's steel — and its accent ring. */
    val hudPlate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = HUD_PLATE
    }
    val hudActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = SELECTION_COLOR
    }
    /** Scratch path for a tool glyph; reused by every slot of every frame. */
    val hudGlyphPath = Path()

    /** Scratch path for lasso drawing — reused each frame to avoid allocation. */
    val lassoPath = Path()
    /** Scratch path for guide drawing — reused each frame to avoid allocation. */
    val guidePath = Path()

    /**
     * Repaint the chrome from the app's Material 3 colour scheme. The translucent fills keep their
     * original alphas so the washes stay faint enough to draw over.
     */
    fun applyColors(backdrop: Int, outline: Int, selection: Int, guide: Int, floating: Int, ink: Int) {
        backdropColor = backdrop
        // The HUD wears the chrome's floating material and ink; its swatches keep their own colours.
        hudSurface.color = floating
        hudGlyph.color = ink
        hudInk.color = ink
        hudRim.color = ink.withAlpha(HUD_RIM_ALPHA)
        hudSwatchRim.color = ink.withAlpha(HUD_SWATCH_RIM_ALPHA)
        pageOutline.color = outline
        selectionStroke.color = selection
        handle.color = selection
        handleArm.color = selection
        selectionFill.color = selection.withAlpha(SELECTION_FILL_ALPHA)
        bandFill.color = selection.withAlpha(BAND_FILL_ALPHA)
        guideStroke.color = guide
        guideHandle.color = guide
        guideFill.color = guide.withAlpha(GUIDE_FILL_ALPHA)
        guideMesh.color = guide.withAlpha(GUIDE_MESH_ALPHA)
        guideText.color = guide
        hudActive.color = selection
        sliderTrackActive.color = selection
        sliderThumb.color = selection
    }

    /** Tint the hover dot to match the pen colour, at the fixed hover alpha. */
    fun tintHover(penArgb: Int) {
        hover.color = penArgb.withAlpha(HOVER_ALPHA)
    }

    internal companion object {
        const val BACKDROP = 0xFF000000.toInt()
        /** Page hairline before the app's colour scheme arrives — faint white, since [BACKDROP] is dark. */
        const val PAGE_OUTLINE = 0x26FFFFFF
        const val SELECTION_COLOR = 0xFF2060E0.toInt()

        /** Outline colour of the setsquare/compass overlay — amber, so it reads as an instrument
         * laid on the page rather than as a selection. */
        const val GUIDE_COLOR = 0xFFE08A20.toInt()

        /** Alpha of the wash filling the setsquare's body — enough to see, faint enough to draw over. */
        const val GUIDE_FILL_ALPHA = 0x22

        /** Alpha of the chemistry guide's bond mesh — fainter still, so a dense mesh never shouts. */
        const val GUIDE_MESH_ALPHA = 0x30
        const val PAGE_SELECT_FILL_ALPHA = 0x33
        /** Alpha of the wash inside a selection outline. */
        const val SELECTION_FILL_ALPHA = 0x14
        /** Alpha of the wash inside the drag-select band. */
        const val BAND_FILL_ALPHA = InkTokens.BAND_FILL_ALPHA

        const val SELECTION_FILL = 0x142060E0
        const val BAND_FILL = 0x222060E0
        /** Translucent blue wash over selected PDF-text word boxes (like a text highlight). */
        const val TEXT_SELECT_FILL = 0x552196F3
        /** Translucent yellow wash over document/PDF search results. */
        const val SEARCH_HIT_FILL = 0x66FFD54F
        const val SEARCH_CURRENT_STROKE = 0xFFFF8F00.toInt()
        const val HOVER_ALPHA = 0xB0
        /** Drawn radius of a selection/guide handle dot. */
        const val HANDLE_DRAW_PX = 7f

        /** Fallbacks until the theme pushes its floating fill and ink in (see [applyColors]). */
        const val HUD_SURFACE = 0xE0161618.toInt()
        const val HUD_INK = 0xFFFFFFFF.toInt()
        const val HUD_RIM = 0x40FFFFFF
        const val HUD_PLATE = 0xFF38383E.toInt()
        /** The rim's alpha over the chrome ink: the hairline's own 12 %. */
        const val HUD_RIM_ALPHA = 0x1F
        /** A swatch's outline: 35 %, so white-on-white still reads as a palette entry. */
        const val HUD_SWATCH_RIM_ALPHA = 0x59
    }
}

/**
 * Repaint the canvas chrome from the app's Material 3 colour scheme. The `SurfaceView` sits outside
 * the Compose tree, so the colours are pushed in from the hosting composable; see
 * `com.mobixournal.ui.theme.CanvasChromeColors`.
 *
 * An extension rather than a member because this is the only thing the view still owes the chrome
 * holder, and `DrawingSurfaceView` is at its line ceiling (`SourceStructureTest`).
 */
internal fun DrawingSurfaceView.applyChromeColors(
    backdrop: Int,
    pageOutline: Int,
    selection: Int,
    guide: Int,
    floating: Int = CanvasChrome.HUD_SURFACE,
    ink: Int = CanvasChrome.HUD_INK,
) {
    chrome.applyColors(backdrop, pageOutline, selection, guide, floating, ink)
    requestRender()
}
