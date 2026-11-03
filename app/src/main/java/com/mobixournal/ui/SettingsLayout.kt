package com.mobixournal.ui

import com.mobixournal.ui.theme.PanelEdgeAir
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The building blocks every settings page is laid out with, so the pages share one look:
 *
 * - a page is a stack of [SettingsGroup]s — a small accent-coloured **header** over a rounded,
 *   tonal **card** holding that group's controls (the shape Android's own settings use);
 * - inside a card, controls are separated by a [SettingsDivider] hairline, never a full-bleed rule;
 * - explanatory prose that belongs to the whole group goes in a [SettingsNote].
 *
 * Grouping is the page's *order*: related controls sit in one card, and a reader can scan the
 * headers to find the one they want instead of reading every subtitle top to bottom.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.floatingRim

@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(bottom = SettingsGroupGap)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = SettingsHeaderGap),
        )
        SettingsCard(content = content)
    }
}

/**
 * The horizontal air a settings card gives its own content: the space between the card's rounded edge
 * and a row's text.
 *
 * It is one constant because two things must agree on it — the card pads its content by exactly this,
 * and [fullBleedRow] reaches back out by exactly this, which is what lets a row's highlight be the
 * card's own width instead of a rounded band floating inside it.
 */
internal val SettingsRowInset: Dp = 16.dp

/**
 * How far past its own content a control inside a settings card reaches, provided by [SettingsCard].
 *
 * Zero everywhere else. The same row composables (`SwitchRow`, `OptionGroup`, `SectionRow`) are drawn
 * inside dialogs too, where there is no card to reach across and a row must keep the bounds it always
 * had; a dialog simply never provides the inset.
 */
internal val LocalSettingsCardInset = compositionLocalOf { 0.dp }

/**
 * One settings card: the rounded, floating container that a [SettingsGroup] and the index pages put
 * their rows in.
 *
 * The content is padded by [SettingsRowInset] and the same number is published to the rows through
 * [LocalSettingsCardInset], so a row can lay itself out into that padding and light the whole card
 * (see [fullBleedRow]).
 *
 * It pads **horizontally only**: a row reaches the card's top and bottom edges too, so a press or a
 * selection is shaped by the card's rounded clip instead of lighting a rectangle that stops short of the
 * corner — a vertical inset is what made a selected first or last row read as a bar inside the card.
 */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A card is a **settings surface**, not a pop-up: the near-solid panel fill (the window's own
    // settings material), the 20 dp panel corner and the rim as its whole edge — no Material
    // elevation, whose blur would land inside the card rather than beside it.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(SettingsCardShape)
            .background(ModernTheme.settingsPanel(chromeIsDark()))
            .floatingRim(20.dp),
    ) {
        CompositionLocalProvider(LocalSettingsCardInset provides SettingsRowInset) {
            Column(
                // Air on the sides only. A row reaches the card's top and bottom edges as well as its
                // sides, so a press, a hover or a selection on the first (or last) row is cut by the
                // card's own 20 dp clip and wears the corner; any air the card kept vertically left
                // that row a square band floating inside the curve. Every block that is not a row
                // carries its own air (`settingsBlockInset`, `SettingsNote`).
                Modifier.padding(horizontal = SettingsRowInset),
                content = content,
            )
        }
    }
}

/**
 * Lays this control out into the card's own horizontal padding, so its **bounds** — and therefore its
 * press, selection or drag highlight — are the card's width, while its content keeps [SettingsRowInset]
 * of air and the card's rounded clip supplies the corners.
 *
 * A Compose ripple is drawn inside the node it belongs to, so a row sitting in the card's padded
 * content can only ever light the middle of the card: a smaller copy of the card's own shape, which
 * reads as a box inside a box. Reaching out by the inset is what makes the row *be* the box; the card
 * clips the overflow at its rounded edge, exactly as the desktop window's rows do.
 *
 * [reach] is [LocalSettingsCardInset], so outside a card this is a no-op.
 */
internal fun Modifier.fullBleedRow(reach: Dp): Modifier = layout { measurable, constraints ->
    val bounds = if (constraints.hasBoundedWidth) {
        fullBleedBounds(constraints.maxWidth, reach.roundToPx())
    } else {
        // A row in an unbounded parent has no card edge to reach for; keep the bounds it was given.
        null
    }
    val placeable = measurable.measure(
        bounds?.let { constraints.copy(minWidth = 0, maxWidth = it.contentWidth) } ?: constraints,
    )
    layout(bounds?.reportedWidth ?: placeable.width, placeable.height) {
        placeable.place(bounds?.contentX ?: 0, 0)
    }
}

/**
 * The bounds [fullBleedRow] gives a row, in pixels: what the row reports to its parent, the width its
 * content is measured at, and where that content is placed from the row's left edge.
 *
 * The arithmetic is its own function so it can be checked on the JVM ([SettingsRowFullBleedTest]): the
 * content must reach exactly one inset past each side, which is the card's own edge.
 */
internal data class FullBleedBounds(val reportedWidth: Int, val contentWidth: Int, val contentX: Int)

internal fun fullBleedBounds(maxWidth: Int, reach: Int): FullBleedBounds = FullBleedBounds(
    reportedWidth = maxWidth,
    contentWidth = maxWidth + reach * 2,
    contentX = -reach,
)

/**
 * The air a settings card gives **between two cells** — held by the rows, never by the divider.
 *
 * A row's press or selection is painted by the row's own node (a ripple cannot draw outside it), so
 * anything the divider keeps for itself stays unlit: a divider carrying its own margins left a strip of
 * card between the light and the hairline, which a reader sees as "the selection does not reach the
 * divider". The rows own the air; the divider is the boundary itself.
 */
/** The settings card's air is the app-wide one, so a card and a pop-up breathe alike. */
internal val SettingsCellAir: Dp = PanelEdgeAir

/**
 * A cell's own inset above and below its text, inside [SettingsCellAir]. One number for every kind of
 * row, so a switch row, a select row, an index row and a link all put their text on the same lines.
 */
internal val SettingsCellPad: Dp = 8.dp

/**
 * The air between two cards — the largest step in the settings screen, and deliberately so: a reader has
 * to be able to tell "another control in this group" from "another group" at a glance. It used to be
 * *smaller* than the air inside a card (20 dp against 36), which made groups run together while the
 * rows that belonged to them drifted apart.
 */
internal val SettingsGroupGap: Dp = 32.dp

/** The air between a group's header and its card — a header names the card, so it sits close to it. */
internal val SettingsHeaderGap: Dp = 10.dp

/** The air a row adds vertically inside a card — zero outside one, where there are no dividers. */
internal fun settingsCellAir(reach: Dp): Dp = if (reach > 0.dp) SettingsCellAir else 0.dp

/** The air a full-width pill keeps from the card's rounded edge — a shape needs more than a line of text. */
internal val SettingsActionPillAir: Dp = SettingsCellAir

/** What a cell pads by above and below its text: the shared inset plus the lit air that carries the ripple. */
internal fun settingsCellInset(reach: Dp): Dp = SettingsCellPad + settingsCellAir(reach)

/**
 * A block that is **not** a row — a slider's header, a note — takes the same inset as a cell, so its text
 * sits on the rows' column instead of a few dp above it.
 */
@Composable
internal fun Modifier.settingsBlockInset(): Modifier {
    val reach = LocalSettingsCardInset.current
    return padding(top = settingsCellInset(reach), bottom = 4.dp)
}

/**
 * The hairline between two controls inside one [SettingsGroup] card, **at the boundary** of the two
 * cells: its air belongs to the rows ([SettingsCellAir]), so a lit row reaches it.
 */
@Composable
fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/** A muted paragraph explaining a group or a control — secondary to the controls around it. */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // A note is a **block**, not a row ([settingsBlockInset]): the card keeps no air of its own, so a
        // note opening a card has to clear the rounded edge itself or its first line lands on the curve.
        modifier = modifier.settingsBlockInset(),
    )
}

/**
 * A control's own heading: its name and, under it, what it does. Shared by the switches, radio
 * groups and sliders so every control in every card reads with the same hierarchy.
 */
@Composable
fun ControlHeader(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A full-width action pill under a note — "Export to JSON", "Reset to Defaults", "Add swatch".
 *
 * A filled or outlined shape is read by its **border**, and a border sitting on the card's text column
 * looks like it was cut off by the card: it keeps [SettingsActionPillAir] on each side instead, so the
 * pill floats inside the card while the note above it stays on the text column. Outside a card the air
 * collapses, because there is no rounded edge to clear.
 */
@Composable
internal fun Modifier.settingsActionPill(top: Dp = 8.dp, bottom: Dp = 8.dp): Modifier {
    val reach = LocalSettingsCardInset.current
    val side = if (reach > 0.dp) SettingsActionPillAir else 0.dp
    return fillMaxWidth().padding(start = side, end = side, top = top, bottom = bottom)
}

/** The corner radius of every settings card (groups and the index lists). */
internal val SettingsCardShape = RoundedCornerShape(20.dp)
