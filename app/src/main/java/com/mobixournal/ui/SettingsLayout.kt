package com.mobixournal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor

@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
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
 */
@Composable
internal fun SettingsCard(
    verticalInset: Dp = 8.dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = SettingsCardShape,
        color = rememberFloatingSurfaceColor(),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth().floatingRim(20.dp),
    ) {
        CompositionLocalProvider(LocalSettingsCardInset provides SettingsRowInset) {
            Column(
                Modifier.padding(horizontal = SettingsRowInset, vertical = verticalInset),
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

/** The hairline between two controls inside one [SettingsGroup] card — inset with the content it separates. */
@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

/** A muted paragraph explaining a group or a control — secondary to the controls around it. */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 4.dp),
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

/** The corner radius of every settings card (groups and the index lists). */
internal val SettingsCardShape = RoundedCornerShape(20.dp)
