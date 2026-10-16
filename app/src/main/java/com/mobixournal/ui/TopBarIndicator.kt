package com.mobixournal.ui

/*
 * The **active tool indicator** — the chrome's report of the live stroke: the selected tool, its
 * colour and its size letter.
 *
 * It lives on its own because it is a small, self-contained piece of chrome with two styles and a
 * geometric contract the tests pin: which end of the figures' dock it stands at
 * ([standaloneIndicatorSlot]) and the dock-cap shape it has to match exactly ([topBarCapShape]).
 * Nothing here draws a document.
 *
 * Both styles are live code, chosen by [INDICATOR_AS_DOCK_CAP]: the **dock cap** the indicator shipped
 * with ([ActiveToolIndicator], filling the figures' dock's own end) and the current floating button of
 * its own ([StandaloneToolIndicator]) that stands in the corner the rail's column and the dock leave
 * between them. They share one face ([ToolIndicatorFace]), so they can only differ in the surface the
 * face is drawn on. See `docs/chrome.md`.
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.rememberGlassSurfaceColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Secondary Toolbar dock's height and corner: what its end cap has to match to be one shape, and
 * what the standalone indicator borrows for a shape of its own ([StandaloneToolIndicator]).
 */
internal val TOP_BAR_DOCK_HEIGHT = 40.dp
// The dock's corner *is* the floating-surfaces corner (ModernTheme.panelCornerRadiusDp), so a menu, an
// action bar and a dialog turn at the radius the dock does — see AppShapes.
internal val TOP_BAR_DOCK_CORNER = ModernTheme.panelCornerRadiusDp

/** How much smaller the cap's inner corners are than the dock's: it caps one end, it doesn't echo it. */
internal val TOP_BAR_CAP_INNER_CORNER = 8.dp

/**
 * The end cap's shape: the dock's own corner ([TOP_BAR_DOCK_CORNER]) on the end the cap closes, and the
 * smaller [TOP_BAR_CAP_INNER_CORNER] on the side facing the figures. The cap's outer corner has to
 * match the dock's exactly, or the dock's own tone shows through as a crescent between the two curves
 * at the very corner the grey is meant to reach.
 *
 * It is a function rather than two dp values at the call site because the *mirror* is the fiddly part —
 * the rail can be docked to the right, and the cap then closes the dock's right end — so the rule is
 * pinned by a unit test instead of by eye.
 */
internal fun topBarCapShape(capOnLeft: Boolean): RoundedCornerShape {
    val outer = TOP_BAR_DOCK_CORNER
    val inner = TOP_BAR_CAP_INNER_CORNER
    return RoundedCornerShape(
        topStart = if (capOnLeft) outer else inner,
        bottomStart = if (capOnLeft) outer else inner,
        topEnd = if (capOnLeft) inner else outer,
        bottomEnd = if (capOnLeft) inner else outer,
    )
}

/**
 * Which of the indicator's two styles the chrome draws — the one line that makes the move reversible.
 *
 * `true` — the style in force — is the **dock cap**: the Secondary Toolbar dock's own end cap, flush with
 * the dock's edge and wearing the dock's corner ([ActiveToolIndicator]). `false` lifts the indicator out
 * of the dock into a floating button of its own ([StandaloneToolIndicator]) that stands *between the two
 * toolbars*: in the corner the rail's column and the dock leave between them, sized
 * ([TOP_BAR_INDICATOR_WIDTH]) so the dock starts on the document tabs' line — which from there on holds
 * the figures alone. That pill's width is deliberately the rail's own column, though, so it also sits
 * flush against the **screen's** left edge; as the cap the indicator starts clear of both the edge and
 * the rail, and the run still reads rail → indicator → figures.
 *
 * Both styles stay live code — the branch is compiled either way, so the backup cannot rot — and the
 * choice is a constant rather than a user setting, as asked: flip it and rebuild for the old look.
 */
internal val INDICATOR_AS_DOCK_CAP = true

/**
 * The air between the standalone indicator and the dock it was cut out of — read here (where the pill
 * trails a right-docked rail) and in `EditorRegions.kt`, where the top bar's leading slot leaves it
 * before the dock.
 */
internal val TOP_BAR_INDICATOR_GAP = 6.dp

/**
 * The floating top bar's **tools row**, **above** the status-bar inset it pads itself by.
 *
 * It is the bar's row, not the whole header: the document tabs are the row *inside* the same slab, one
 * row down (see [EditorTopBar]), and they are `TAB_STRIP_HEIGHT` tall. What has to clear the whole header
 * reads the header rather than repeating its height as a literal — the rail's column, whose lane starts
 * below the header so that neither the bar's glass nor the tabs' row can swallow its first slot. The
 * status-bar inset is deliberately *not* part of this: that is a device value, taken with
 * `statusBarsPadding()` at each call site.
 */
internal val TOP_BAR_ROW_HEIGHT = 52.dp

/** Air on each side of the indicator's face while it fills the dock's own end as the cap. */
private val TOP_BAR_FACE_PADDING = 10.dp

/** The same, for the standalone pill: its width is spoken for ([TOP_BAR_INDICATOR_WIDTH]), so this is what
 *  is left of that width once the face has its icon, swatch and letter. */
private val TOP_BAR_FACE_PADDING_TIGHT = 5.dp

/**
 * The standalone indicator's own width — and, because the figures' dock starts right after it, the line
 * that dock starts on.
 *
 * Two insets sit between the window's edge and the dock: the 4dp the top bar keeps in front of its leading
 * slot and the 4dp between that slot and the dock (Material 3's own, one on each side of the slot). The
 * document tab strip starts 72dp in — the rail's column ([SideToolbarModernTotalWidth]) plus the strip's own
 * 8dp lead-in (`TAB_STRIP_LEAD_IN`, in `EditorRegions.kt`) — so a 64dp slot is exactly what lands the dock
 * on the tabs' line: the bar's content and the tab row under it begin on one vertical line.
 *
 * The width is *fixed* rather than left to the face, so the dock cannot drift with the tool in play (the
 * size letter "S"/"M"/"L" is not the same width in every glyph) — the face is padded to fit inside it
 * instead. It also keeps the pill inside the rail's column, which is what leaves the pill standing in the
 * corner between the two toolbars rather than pushing the dock right.
 */
internal val TOP_BAR_INDICATOR_WIDTH = 64.dp

/** Which end of the figures' dock the standalone indicator stands at. */
internal enum class IndicatorSlot {
    /** The top bar's **leading** slot: the corner between the rail's column and the dock. */
    LEADING,
    /** **Trailing** the dock — for a right-docked rail, its end nearest the Main Toolbar. */
    TRAILING,
}

/**
 * Which side of the figures' dock the standalone indicator belongs on: always the side the Main Toolbar
 * is docked to, so the run always reads rail → indicator → figures rather than the indicator drifting to
 * the far edge from the hand's toolbar. On a left-docked rail that is the corner the rail and the dock
 * leave between them ([TOP_BAR_INDICATOR_WIDTH]), which is also the line the tabs start on.
 *
 * A rule read by both places that can draw it — [EditorTopBar]'s leading slot and [TopBarToolsRow]'s
 * trailing one — so the two cannot disagree about the mirror, and pinned by a unit test instead of by eye
 * (the same reason [topBarCapShape] is a function).
 */
internal fun standaloneIndicatorSlot(railOnLeft: Boolean): IndicatorSlot =
    if (railOnLeft) IndicatorSlot.LEADING else IndicatorSlot.TRAILING

/**
 * The indicator's **face**, shared by its two styles ([ActiveToolIndicator] and
 * [StandaloneToolIndicator]) so they can only differ in *which surface the face is drawn on* — and in how
 * much air that surface gives it ([horizontalPadding]) — never in what the indicator says:
 * - Selected tool (icon)
 * - Selected colour (swatch circle)
 * - Selected stroke size ("S", "M", or "L")
 *
 * The grey behind it is the scheme's `surfaceContainerHighest` role, a surface step and never the
 * accent: the indicator *reports* the live stroke, it is not a tool that can be picked, so the blue a
 * lit-up tool button wears made it read as a second active button beside the figures. Nothing draws the
 * stroke's own thickness either — the size letter reports it through [sizeLetterFor], with room to read
 * it.
 *
 * Tapping it opens the full Colour & Size pop-up ([ColorSizePopup]). The caller supplies the chrome —
 * the modifier chain that paints the surface under the face — because that chain is exactly what
 * differs between a cap flush inside the dock and a button floating on its own, and it supplies the air
 * around the face for the same reason: a cap has the whole dock's width to breathe in, while the
 * standalone pill is held to [TOP_BAR_INDICATOR_WIDTH] so it cannot push the dock off the tabs' line.
 */
@Composable
private fun ToolIndicatorFace(
    ui: EditorUiState,
    styleCallbacks: ToolbarStyleCallbacks,
    modifier: Modifier = Modifier,
    /** Air on each side of the face, inside whatever surface the caller paints. */
    horizontalPadding: Dp = TOP_BAR_FACE_PADDING,
) {
    val isHighlighter = ui.tool == EditorTool.HIGHLIGHTER
    val effectiveColor = if (isHighlighter) ui.highlighterColor else ui.color
    val hasColorAndSize = ui.tool !in NON_INKING_TOOLS
    val sizeLabel = sizeLetterFor(ui.width, styleCallbacks.widthSlots)

    ColorSizePopup(styleCallbacks) { open ->
        Box(
            modifier = modifier
                .clickable(onClick = open)
                .padding(horizontal = horizontalPadding)
                .semantics {
                    contentDescription = if (hasColorAndSize) {
                        "Active tool: ${ui.tool.label}, colour ${colorDisplayName(effectiveColor)}, size $sizeLabel"
                    } else {
                        "Active tool: ${ui.tool.label}"
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = ui.tool.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    // The dock's own glyph size, shared with the rail ([ToolIconSize]).
                    modifier = Modifier.size(ToolIconSize),
                )
                if (hasColorAndSize) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(effectiveColor))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                    Text(
                        text = sizeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The indicator's **dock cap** style — the Secondary Toolbar dock's own end, the end nearest the Main
 * Toolbar, rather than a chip floating inside it.
 *
 * It is as tall as the dock ([TOP_BAR_DOCK_HEIGHT]), flush with its edge, and wears the dock's corner
 * ([TOP_BAR_DOCK_CORNER]) on the end it caps, while the corners facing the figures stay smaller so the
 * cap reads as part of the bar rather than as a second bar. The flush, full-height grey is the point:
 * an inset chip leaves a crescent of the dock's own tone around itself, and the toolbar's rounded
 * border then encloses the dock instead of enclosing the indicator.
 *
 * Nothing divides the cap from the figures either: a vertical divider cut the dock in two for a
 * boundary the grey already draws, so the tools simply start a padding-width after the cap.
 *
 * It is kept as the restore path behind [INDICATOR_AS_DOCK_CAP] — the style the indicator wore before
 * it was moved out to [StandaloneToolIndicator] — and is `internal` rather than private because its
 * only way on screen is that constant's branch, which is compiled in `EditorRegions.kt`.
 */
@Composable
internal fun ActiveToolIndicator(
    ui: EditorUiState,
    styleCallbacks: ToolbarStyleCallbacks,
    /** True when the cap is the dock's left end, which is where the Main Toolbar is docked. */
    capOnLeft: Boolean = true,
    modifier: Modifier = Modifier,
) {
    ToolIndicatorFace(
        ui = ui,
        styleCallbacks = styleCallbacks,
        modifier = modifier
            .fillMaxHeight()
            .clip(topBarCapShape(capOnLeft))
            .background(MaterialTheme.colorScheme.primaryContainer),
    )
}

/**
 * The indicator as a **floating button of its own** — the current style ([INDICATOR_AS_DOCK_CAP] off).
 * It is cut out of the dock and stands between the two toolbars instead of being the first thing *inside*
 * the figures' dock: in the corner the rail's column and the dock leave between them, so the run reads
 * rail → indicator → figures. Its width is what leaves the dock on the document tabs' line below
 * ([TOP_BAR_INDICATOR_WIDTH]), so the toolbar of figures starts exactly where the `Untitled` chip does.
 *
 * Freed of the dock's edge it can be a whole shape: the dock's corner ([TOP_BAR_DOCK_CORNER]) on all
 * four sides — a capsule at [TOP_BAR_DOCK_HEIGHT] — and the dock's tonal and shadow elevation and
 * hairline border, so it reads as one of the floating docks rather than as a bare patch of grey. A
 * [TOP_BAR_INDICATOR_GAP] keeps it visibly its own surface wherever it trails the dock.
 *
 * Its width is [TOP_BAR_INDICATOR_WIDTH], which is what keeps the figures' dock on the document tabs'
 * line: the dock starts where this pill ends (plus Material 3's own gap), so the pill is sized to the
 * corner between the rail's column and that line rather than to its own face. The face is padded tighter
 * ([TOP_BAR_FACE_PADDING_TIGHT]) to fit, and because the width is fixed rather than measured, the dock
 * cannot shift sideways when the tool in play changes the size letter.
 *
 * The face is shared with the cap ([ToolIndicatorFace]): the same icon, live colour and size letter,
 * and the same tap into the Colour & Size pop-up. Only the surface it is drawn on moved.
 */
@Composable
internal fun StandaloneToolIndicator(
    ui: EditorUiState,
    styleCallbacks: ToolbarStyleCallbacks,
    modifier: Modifier = Modifier,
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val glassColor = rememberGlassSurfaceColor()
    val hairlineColor = com.mobixournal.ui.theme.ModernTheme.hairlineBorder(isDark)

    Surface(
        shape = ModernTheme.capsuleShape,
        color = glassColor,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(com.mobixournal.ui.theme.ModernTheme.hairlineStrokeWidthDp, hairlineColor),
        modifier = modifier
            .width(TOP_BAR_INDICATOR_WIDTH)
            .height(TOP_BAR_DOCK_HEIGHT),
    ) {
        ToolIndicatorFace(
            ui = ui,
            styleCallbacks = styleCallbacks,
            modifier = Modifier.fillMaxSize(),
            horizontalPadding = TOP_BAR_FACE_PADDING_TIGHT,
        )
    }
}

/**
 * Secondary Toolbar: a compact, horizontal scrollable row of geometric figures and tools that sits
 * inside the top bar, allowing quick access without shrinking the canvas. It renders as a floating dock
 * surface enclosing only the figures, adapting its width dynamically to the number of visible figures.
 */
private val NON_INKING_TOOLS: Set<EditorTool> = setOf(
    EditorTool.ERASER,
    EditorTool.ERASER_WHOLE,
    EditorTool.HAND,
    EditorTool.SELECT,
    EditorTool.LASSO_SELECT,
    EditorTool.TEXT_SELECT,
    EditorTool.BG_SELECT,
    EditorTool.VERTICAL_SPACE,
    EditorTool.PLAY_OBJECT,
    EditorTool.IMAGE,
)
