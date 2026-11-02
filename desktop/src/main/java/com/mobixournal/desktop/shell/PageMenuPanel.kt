package com.mobixournal.desktop.shell
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.format.model.Background
import com.mobixournal.render.PageSizeStep
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The **Pages** menu itself: the tablet's page navigator, its two quick sizes and the page's own two
 * questions, with the window's **Manage pages** section under them.
 *
 * Kept apart from `DesktopPages.kt` — which owns the commands — because a panel is drawing and the
 * commands are edits, and because the split is what keeps both files inside the module's structural
 * ratchet (`SourceStructureTest`). The order here is the one `docs/pages-and-paper.md` states.
 */
/** The page's size as the menu prints it: a preset's name, or the sheet's own two measures. */
internal fun pageFormatLabel(widthPt: Double, heightPt: Double): String {
    fun near(a: Double, b: Double) = abs(a - b) <= 1.0
    val preset = PageSetup.PRESETS.firstOrNull { candidate ->
        (near(widthPt, candidate.widthPt) && near(heightPt, candidate.heightPt)) ||
            (near(widthPt, candidate.heightPt) && near(heightPt, candidate.widthPt))
    }
    return preset?.label ?: "${widthPt.roundToInt()} × ${heightPt.roundToInt()} pt"
}

/** The paper the page in view is wearing, in the window's own word for it (`PageSetup.STYLES`). */
internal fun pageBackgroundLabel(background: Background): String? =
    (background as? Background.Solid)?.let { solid ->
        PageSetup.STYLES.firstOrNull { it.first == solid.style }?.second
    }

/**
 * The panel behind that slot: **the tablet's own page menu**, in the window's glass.
 *
 * The tablet's order is the frame's, and it is kept: the **page navigator** (the sheet the reader is on,
 * between the two arrows that step it), the two **quick sizes** that nudge the sheet without a dialog,
 * the list's two commands, then the page's own two questions with their live answers printed at the end
 * of the row. The window answered the same questions with nine flat rows — *Previous page* and *Next page*
 * among them — which is a list to read rather than a menu to use, and nothing said which sheet you were on.
 *
 * The window's own half is **Manage pages**, under its own name at the foot: the two moves and the typed
 * range (`PageRangePanel`), which the tablet does not have and a desk with forty sheets needs. It is one
 * more section rather than five more rows at the top, so the rows the two shells share stay adjacent.
 *
 * Every row wears its **glyph** in the leading slot, as the phone's menu items do: the same pictures are
 * read from `ChromeGlyphCatalogue`, so *Add page* is the `+` the strip's own add is, *Page background* is
 * the paper the page-setup surface is, and a row on the PC is recognised before it is read.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PageMenuPanel(actions: PageMenuActions, onClose: () -> Unit) {
    Column(
        Modifier
            .glassSurface()
            .width(PageMenuMetrics.width)
            .padding(PageMenuMetrics.padding),
        verticalArrangement = Arrangement.spacedBy(PageMenuMetrics.gap),
    ) {
        PageNavRow(actions, onClose)
        PageMenuHeading("Quick size")
        QuickSizeRow(actions, axis = PageMenuMetrics.Item.WIDTH)
        QuickSizeRow(actions, axis = PageMenuMetrics.Item.HEIGHT)
        PageMenuRow(glyph = ChromeGlyph.ADD, label = "Add page", enabled = true) { onClose(); actions.addPage() }
        PageMenuRow(glyph = ChromeGlyph.MINUS, label = "Remove page", enabled = actions.canRemove) {
            onClose()
            actions.removePage()
        }
        PageMenuRule()
        PageMenuRow(glyph = ChromeGlyph.PAGE_FORMAT, label = "Page format", enabled = true, value = actions.formatLabel) {
            onClose()
            actions.format()
        }
        PageMenuRow(
            glyph = ChromeGlyph.PAGE_BACKGROUND,
            label = "Page background",
            enabled = true,
            value = actions.backgroundLabel,
        ) {
            onClose()
            actions.background()
        }
        PageMenuRule()
        PageMenuHeading("Manage pages")
        PageMenuRow(glyph = ChromeGlyph.CHEVRON_UP, label = "Move page up", enabled = actions.canMoveUp) {
            onClose()
            actions.movePageUp()
        }
        PageMenuRow(glyph = ChromeGlyph.CHEVRON_DOWN, label = "Move page down", enabled = actions.canMoveDown) {
            onClose()
            actions.movePageDown()
        }
        PageMenuRow(glyph = ChromeGlyph.DESCRIPTION, label = "Manage pages…", enabled = actions.canManage) {
            onClose()
            actions.managePages()
        }
    }
}

/** The **navigator**: the sheet in view between the two arrows that step it — the tablet's first row. */
@Composable
private fun PageNavRow(actions: PageMenuActions, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(PageMenuMetrics.rowHeight)
            .padding(horizontal = PageMenuMetrics.navInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageGlyphButton(
            glyph = ChromeGlyph.CHEVRON_UP,
            label = "Previous page",
            enabled = actions.canStep && actions.page > 0,
            rotation = -90f,
        ) { onClose(); actions.previousPage() }
        Text(
            text = "Page ${actions.page + 1} / ${actions.pageCount}",
            color = DesktopTheme.ink,
            style = DesktopTheme.numeric,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        PageGlyphButton(
            glyph = ChromeGlyph.CHEVRON_UP,
            label = "Next page",
            enabled = actions.canStep && actions.page < actions.pageCount - 1,
            rotation = 90f,
        ) { onClose(); actions.nextPage() }
    }
}

/**
 * One axis of **Quick size**: the sheet's measure, and the two buttons that nudge it.
 *
 * The step is [PageSizeStep.DEFAULT_MM] — the tablet's own factory step, a length of paper — turned into
 * points by the shared rule, because a page is kept in points while a step is read in millimetres. Each tap
 * applies at once and the menu stays open, so three taps are three steps rather than three visits to the
 * menu.
 */
@Composable
private fun QuickSizeRow(actions: PageMenuActions, axis: PageMenuMetrics.Item) {
    val size = actions.pageSize ?: return
    val isWidth = axis == PageMenuMetrics.Item.WIDTH
    val measure = if (isWidth) size.first else size.second
    val label = if (isWidth) "Width" else "Height"
    val stepPt = PageSizeStep.pt(PageSizeStep.DEFAULT_MM)
    val stepLabel = PageSizeStep.label(PageSizeStep.DEFAULT_MM)
    fun nudge(steps: Int) {
        val (width, height) = if (isWidth) {
            PageSizeStep.step(size.first, size.second, steps, 0, stepPt)
        } else {
            PageSizeStep.step(size.first, size.second, 0, steps, stepPt)
        }
        actions.onSize(width, height)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(PageMenuMetrics.rowHeight)
            .padding(horizontal = PageMenuMetrics.rowInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.body,
            modifier = Modifier.weight(1f),
        )
        Text("${measure.roundToInt()} pt", color = DesktopTheme.ink, style = DesktopTheme.numeric)
        Spacer(Modifier.width(6.dp))
        PageGlyphButton(
            glyph = ChromeGlyph.MINUS,
            label = "$label − $stepLabel",
            enabled = measure > PageSizeStep.MIN_PAGE_PT,
        ) { nudge(-1) }
        Spacer(Modifier.width(PageMenuMetrics.glyphGap))
        PageGlyphButton(
            glyph = ChromeGlyph.ADD,
            label = "$label + $stepLabel",
            enabled = true,
        ) { nudge(1) }
    }
}

/** A section's name in the panel, at the eyebrow role — the tablet's `MenuHeading`. */
@Composable
private fun PageMenuHeading(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(PageMenuMetrics.headingHeight)
            .padding(start = PageMenuMetrics.rowInset),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = DesktopTheme.inkMuted, style = DesktopTheme.eyebrow, maxLines = 1)
    }
}

/**
 * One of the panel's small glyph buttons: the navigator's two arrows and the four quick-size steps.
 *
 * [rotation] turns a catalogued chevron into the arrow a direction needs — the catalogue has up and down,
 * and a left arrow is the up one turned a quarter turn rather than a second picture of its own.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PageGlyphButton(
    glyph: ChromeGlyph,
    label: String,
    enabled: Boolean,
    rotation: Float = 0f,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val ink = if (enabled) DesktopTheme.inkSecondary else DesktopTheme.inkMuted
    Box(
        Modifier
            .size(PageMenuMetrics.glyphButton)
            .background(
                if (hovered && enabled) DesktopTheme.hoverSlot else Color.Transparent,
                DesktopTheme.capsuleShape,
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .semantics { contentDescription = label }
            .pointerInput(glyph, enabled) { if (enabled) detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            if (rotation == 0f) drawChromeGlyph(glyph, ink) else rotate(rotation) { drawChromeGlyph(glyph, ink) }
        }
    }
}

/**
 * The panel's numbers, in one place: the layout reads them and so does `DesktopPagesTest`, which clicks
 * the rows by the same arithmetic the panel was laid out with — a test that guesses a coordinate fails
 * the day a gap changes for a good reason.
 */
internal object PageMenuMetrics {
    val width = 236.dp
    val padding = 6.dp
    val rowHeight = 30.dp
    val headingHeight = 22.dp
    val ruleHeight = 7.dp
    val gap = 1.dp

    /** A small glyph button in a row: the navigator's arrows and the quick-size steps. */
    val glyphButton = 22.dp

    /** The air between those two buttons, so a mistimed click lands on neither. */
    val glyphGap = 2.dp

    /** A row's leading picture and the column it stands in: the phone's menu-item geometry. */
    val rowGlyph = 16.dp
    val glyphSlot = 20.dp

    /** A row's own horizontal inset, and the navigator's (it holds a capsule at each end). */
    val rowInset = 8.dp
    val navInset = 4.dp

    /**
     * The panel top to bottom, one entry per thing it draws.
     *
     * A named list rather than a count: the panel's rows are no longer interchangeable, and a test that
     * wants "the format row" should say so instead of counting past the quick sizes to index seven.
     */
    enum class Item {
        NAV, QUICK_SIZE, WIDTH, HEIGHT, ADD, REMOVE, RULE_ONE, FORMAT, BACKGROUND, RULE_TWO, MANAGE, MOVE_UP,
        MOVE_DOWN, MANAGE_PAGES,
    }

    /** How tall each of those is; a rule is its own band of air. */
    fun heightOf(item: Item): Dp = when (item) {
        Item.NAV, Item.WIDTH, Item.HEIGHT, Item.ADD, Item.REMOVE, Item.FORMAT, Item.BACKGROUND,
        Item.MOVE_UP, Item.MOVE_DOWN, Item.MANAGE_PAGES,
        -> rowHeight
        Item.QUICK_SIZE, Item.MANAGE -> headingHeight
        Item.RULE_ONE, Item.RULE_TWO -> ruleHeight
    }

    /** The top edge of [item], from the panel's own padding and the gaps between what stands above it. */
    fun top(item: Item): Dp {
        var y = padding
        for (other in Item.entries) {
            if (other == item) return y
            y += heightOf(other) + gap
        }
        return y
    }

    /** The middle of [item]: where a click on that row belongs. */
    fun centre(item: Item): Dp = top(item) + heightOf(item) / 2

    /** The middle of a quick-size step: the two glyph buttons at the row's right end. */
    fun stepCentre(plus: Boolean): Dp {
        val right = width - padding - rowInset - glyphButton / 2
        return if (plus) right else right - glyphButton - glyphGap
    }

    /** The middle of a navigator arrow: one at each end of its row. */
    fun navCentre(next: Boolean): Dp =
        if (next) width - padding - navInset - glyphButton / 2 else padding + navInset + glyphButton / 2

    /** The bottom of the panel's content, plus its padding — how tall the panel is. */
    val bottom: Dp get() = top(Item.MANAGE_PAGES) + rowHeight + padding
}

/**
 * One row: its **glyph**, its label, an optional answer at the end, and the muted tone when the document
 * cannot do it.
 *
 * The leading slot is the phone's own arrangement — a menu item is read by its picture first — and it is
 * [PageMenuMetrics.glyphSlot] wide for every row, so the labels of a section start on one line whatever
 * picture stands beside them.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PageMenuRow(
    glyph: ChromeGlyph,
    label: String,
    enabled: Boolean,
    value: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(PageMenuMetrics.rowHeight)
            .background(Color.Transparent, DesktopTheme.capsuleShape)
            .hoverFill(DesktopTheme.capsuleShape, active = !enabled)
            .pointerInput(label, enabled) { if (enabled) detectTapGestures { onClick() } }
            .padding(horizontal = PageMenuMetrics.rowInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(PageMenuMetrics.glyphSlot), contentAlignment = Alignment.CenterStart) {
            Canvas(Modifier.size(PageMenuMetrics.rowGlyph)) {
                drawChromeGlyph(glyph, if (enabled) DesktopTheme.inkSecondary else DesktopTheme.inkMuted)
            }
        }
        Spacer(Modifier.width(PageMenuMetrics.glyphGap))
        Text(
            label,
            color = if (enabled) DesktopTheme.ink else DesktopTheme.inkMuted,
            style = DesktopTheme.body,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, color = DesktopTheme.inkMuted, style = DesktopTheme.numeric, maxLines = 1)
        }
    }
}

/** A hairline between the page list's commands and the page's own two questions. */
@Composable
private fun PageMenuRule() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(PageMenuMetrics.ruleHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DesktopTheme.hairline))
    }
}
