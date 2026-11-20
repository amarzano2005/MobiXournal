package com.mobixournal.desktop.settings
import com.mobixournal.design.DesignTokens
import com.mobixournal.desktop.tools.BandEdge
import com.mobixournal.desktop.tools.LocalGlassClosing
import com.mobixournal.desktop.tools.PanelEdgeAir
import com.mobixournal.desktop.tools.PointerOwnership
import com.mobixournal.desktop.tools.bandDivider
import com.mobixournal.desktop.tools.glassSpring
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.tools.LaneClose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * **The Settings surface**: the window's own panel for every choice it keeps.
 *
 * It **floats over the page** — the glass, the family's rim, the 20 dp panel corner — and the document stays
 * drawn around it, because a setting is changed while looking at the thing it changes. The first version
 * took the whole body between the header and the status bar and painted a blank page behind it: on a desktop
 * that reads as a second window, and it threw away the comparison the reader was making
 * (`docs/interface-contract.md` §1 — a surface that comes and goes may lie over the page; a band may not).
 *
 * Its fill is [DesktopTheme.settingsPanel], the glass almost solid: a settings page is read, and at the
 * floating alpha the page's own ink came up through its labels.
 *
 * The panel is **draggable by its title bar**. It opens in the middle, which is over the sheet the reader
 * came to compare against; dragging it is how the thing being changed is kept in view. The offset is clamped
 * so the panel cannot be lost off an edge, and the window's own placement is not stored — a file that
 * remembered "moved left" would open a fresh window with a panel half off the screen.
 *
 * Its proportions are a panel's, not a screen's: the index is a narrow list of four areas
 * ([SettingsIndex]), the page is a column, a setting is one row ([SettingsRow]), the panel is capped at
 * [MAX_WIDTH] × [MAX_HEIGHT] so a 21:9 monitor does not stretch a label across the desk, and the footer is
 * the **help line** ([SettingsHelpLine]) that says what the row under the pointer does.
 *
 * Nothing here decides anything: every row reports a value from [DesktopSettingsState] and writes the next
 * one back, which is what makes a change visible on the page behind it the moment it is clicked.
 */
@Composable
internal fun SettingsSurface(
    policy: DesktopSettingsState,
    palette: PaletteActions = PaletteActions(),
    modifier: Modifier = Modifier,
) {
    // The panel is over the page, so the page is not live under it: Compose hands a press to the sheet as
    // well as to the row it landed on, and the sheet reads presses it did not consume. The pointer is the
    // panel's until it closes — a drag on a slider no longer draws the line behind the setting.
    DisposableEffect(Unit) {
        PointerOwnership.take()
        onDispose { PointerOwnership.release() }
    }
    // What the help line is saying: the sentence of the row under the pointer, written by the row itself.
    val help = remember { mutableStateOf("") }
    BoxWithConstraints(modifier.fillMaxSize().pointerHoverIcon(PointerIcon.Default), contentAlignment = Alignment.Center) {
        val panelWidth = minOf(maxWidth * 0.9f, MAX_WIDTH)
        val panelHeight = minOf(maxHeight * 0.88f, MAX_HEIGHT)
        val density = LocalDensity.current
        // How far the panel may travel before its edge leaves the window: half the room it has around it.
        val roomX = with(density) { ((maxWidth - panelWidth) / 2f).toPx() }
        val roomY = with(density) { ((maxHeight - panelHeight) / 2f).toPx() }
        val dx = policy.panelDx.coerceIn(-roomX, roomX)
        val dy = policy.panelDy.coerceIn(-roomY, roomY)
        // A modal, drawn honestly: the page stays where it was (it is what the setting changes) and goes
        // under the scrim, so nothing behind the panel reads as a control the pointer could still reach.
        val closing = LocalGlassClosing.current
        val scrimAlpha by animateFloatAsState(
            targetValue = if (closing) 0f else 1f,
            animationSpec = tween(
                if (closing) DesignTokens.GlassMotion.FADE_OUT_MS else DesignTokens.GlassMotion.FADE_IN_MS,
            ),
        )
        Box(Modifier.fillMaxSize().background(DesktopTheme.scrim.copy(alpha = DesktopTheme.scrim.alpha * scrimAlpha)))
        Column(
            Modifier
                .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                .width(panelWidth)
                .height(panelHeight)
                .glassSpring()
                .glassSurface(fill = DesktopTheme.settingsPanel)
                .pointerHoverIcon(PointerIcon.Default),
        ) {
            SettingsPanelHeader(policy)
            CompositionLocalProvider(
                LocalSettingsHelp provides help,
                LocalSettingsSearchQuery provides policy.query,
            ) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    SettingsIndex(policy)
                    Box(Modifier.width(1.dp).fillMaxHeight().background(DesktopTheme.hairline))
                    SettingsScrollPane(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        resetKey = policy.shownSection,
                        contentPadding = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    ) {
                        // The page is a column with a readable line length, not a surface that grows with
                        // the monitor: past 720 dp a label and its control stop belonging to each other.
                        Column(Modifier.widthIn(max = SettingsMetrics.PAGE_MAX_WIDTH)) {
                            AnimatedContent(
                                targetState = policy.shownSection,
                                transitionSpec = {
                                    (fadeIn(tween(120)) + slideInVertically(tween(120)) { 8 }) togetherWith
                                        fadeOut(tween(80)) using SizeTransform(clip = false)
                                },
                            ) { section ->
                                DesktopSettingPage(policy, palette, section)
                            }
                        }
                    }
                }
            }
            // Hover expands the inline help; the resting footer explains the immediate-apply model.
            SettingsHelpLine(help.value.ifEmpty { "Changes apply immediately." })
        }
    }
}

/** The panel's title row: the drag handle, the search field and the close glyph. */
@Composable
private fun SettingsPanelHeader(policy: DesktopSettingsState) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .background(DesktopTheme.glass)
            // The header's own hairline: it meets the index's separator, so the title row and the two
            // columns under it read as one panel instead of a row floating above a box.
            .bandDivider(BandEdge.BOTTOM)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The title area is the handle: a header is where a window is grabbed, and the field and the
        // close glyph keep their own pointer handling beside it. Centred by the box rather than nudged
        // with a top padding, so the title sits on the header's own middle at every font metric.
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(policy) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        policy.dragPanel(drag.x, drag.y)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", color = DesktopTheme.ink, style = DesktopTheme.title)
                policy.shownSection?.let { section ->
                    Text("  /  ${section.title}", color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
                }
            }
        }
        SettingsSearchField(
            query = policy.query,
            onQuery = policy::search,
            onFocus = { focused ->
                policy.typing = focused
                if (focused) policy.cancelArmed()
            },
            modifier = Modifier.width(260.dp),
        )
        Spacer(Modifier.width(8.dp))
        // The same cross the inspector lane closes with: one glyph for "put this away" everywhere.
        LaneClose { policy.close() }
    }
}

/**
 * The **index**: the five areas as eyebrows, and their sections under them.
 *
 * A dozen names in one flat column is a list read top to bottom; five short groups is a list scanned by
 * its shape, and desktop task areas put related controls together (`DesktopSettingArea`). A section's
 * sentence is not drawn here — the index is names only, and the help line says what a section covers when
 * the pointer is on it.
 */
@Composable
private fun SettingsIndex(policy: DesktopSettingsState) {
    SettingsScrollPane(
        modifier = Modifier.width(SettingsMetrics.INDEX_WIDTH).fillMaxHeight().background(DesktopTheme.glass),
        resetKey = "index",
        contentPadding = Modifier.padding(horizontal = 10.dp, vertical = PanelEdgeAir),
    ) {
        var firstArea = true
        for (area in DesktopSettingArea.entries) {
            val rows = policy.sections.filter { it.area == area }
            if (rows.isEmpty()) continue
            Text(
                area.title,
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.label,
                // The first area sits directly under the panel edge air; subsequent areas open with 16 dp
                // so the four task blocks read as distinct groups, with 4 dp before their first row.
                modifier = Modifier.padding(start = 10.dp, top = if (firstArea) 4.dp else 16.dp, bottom = 4.dp),
            )
            firstArea = false
            for (section in rows) {
                SettingsSectionRow(
                    section = section,
                    selected = section == policy.shownSection,
                    onClick = { policy.show(section) },
                )
            }
        }
    }
}

/** Full contextual help on hover, or the immediate-apply reminder at rest. */
@Composable
private fun SettingsHelpLine(sentence: String) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DesktopTheme.hairline))
    Row(
        Modifier
            .fillMaxWidth()
            .height(HELP_HEIGHT)
            .background(DesktopTheme.glass)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            sentence,
            color = DesktopTheme.inkMuted,
            style = DesktopTheme.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The panel's cap: wide enough for a row of four chips, and no wider than a settings page reads. */
private val MAX_WIDTH = 1050.dp
private val MAX_HEIGHT = 760.dp

/** The panel's own title row — shorter than a band's, because a panel is not the window's chrome. */
private val HEADER_HEIGHT = 46.dp

/** The help line under the body: one sentence, at the label role, and never more than one line. */
private val HELP_HEIGHT = 28.dp
