@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page
import com.mobixournal.render.BackgroundRuling
import com.mobixournal.render.BackgroundRulings
import kotlin.math.abs

/** Which question the page-setup surface is answering. */
enum class PageSetupSection {
    /** What the sheet is: its paper style and ruling spacing. */
    PAPER,

    /** How big the sheet is: a preset, in either orientation. */
    FORMAT,
}

/** Points per millimetre — the unit the tablet's ruling chips use, written here because the app's own. */
internal object PageSetup {
    const val MM_PT: Double = 72.0 / 25.4

    /** The paper styles the window's painter draws, named as the `.xopp` values desktop understands. */
    val STYLES: List<Pair<String, String>> = listOf(
        "plain" to "Plain",
        "lined" to "Lined",
        "ruled" to "Ruled",
        "graph" to "Graph",
        "dotted" to "Dotted",
        "isograph" to "Isometric",
    )

    /** The ruling spacings the tablet offers, in millimetres. */
    val SPACING_MM: List<Double> = listOf(2.0, 3.0, 5.0, 7.0, 10.0)

    /** A common page size in points, portrait. */
    data class Preset(val label: String, val widthPt: Double, val heightPt: Double)

    /** The tablet's page-size presets (`PAGE_PRESETS`). */
    val PRESETS: List<Preset> = listOf(
        Preset("A4", 595.276, 841.89),
        Preset("A5", 419.528, 595.276),
        Preset("Letter", 612.0, 792.0),
        Preset("Legal", 612.0, 1008.0),
    )
}

/**
 * The shell's overlay for **page setup**, in the window's own glass: paper style, ruling spacing and
 * page format, applied to the page in view as one undo step each.
 *
 * It is the desktop half of the tablet's Pages ▸ Page background and Page format dialogs. The two
 * sections share one surface because they are the same question — *what is this sheet?* — and it floats
 * over the page (a transient surface, not a band) while the sheet behind it repaints with every tap.
 */
@Composable
internal fun BoxScope.PageSetupHost(state: DesktopShellState) {
    var lastSection by remember { mutableStateOf(PageSetupSection.PAPER) }
    val open = state.pageSetup
    SideEffect { if (open != null) lastSection = open }
    GlassPresence(visible = open != null) {
        val section = open ?: lastSection
        val tab = state.active ?: return@GlassPresence
        val page = remember(tab, tab.page, tab.document.edits) { state.activePage() } ?: return@GlassPresence
        Box(
            Modifier
                .fillMaxSize()
                // A question takes no stroke on the page behind it.
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            PageSetupPanel(
                section = section,
                page = page,
                onStyle = state::setPageStyle,
                onSpacing = state::setPageSpacingMm,
                onSize = state::setPageSize,
                onDone = { state.closePageSetup() },
            )
        }
    }
}

/** The panel itself: its title, the section's chips and the one button that closes it. */
@Composable
private fun PageSetupPanel(
    section: PageSetupSection,
    page: Page,
    onStyle: (String) -> Unit,
    onSpacing: (Double) -> Unit,
    onSize: (Double, Double) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.width(430.dp).glassSpring().glassSurface().padding(18.dp)) {
        Text(
            if (section == PageSetupSection.PAPER) "Page background" else "Page format",
            color = DesktopTheme.ink,
            style = DesktopTheme.title,
        )
        Spacer(Modifier.height(12.dp))
        if (section == PageSetupSection.PAPER) {
            PaperSection(page, onStyle, onSpacing)
        } else {
            FormatSection(page, onSize)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PanelButton(label = "Done", primary = true, enabled = true, onClick = onDone)
        }
    }
}

/** Paper style and the ruling's spacing, both read from the live page so the chips say what is on it. */
@Composable
private fun PaperSection(page: Page, onStyle: (String) -> Unit, onSpacing: (Double) -> Unit) {
    val solid = page.background as? Background.Solid
    if (solid == null) {
        Text(
            "This page's background is a picture or a PDF, so it has no paper to restyle.",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.body,
        )
        return
    }
    SectionLabel("Paper")
    ChipRow {
        for ((value, label) in PageSetup.STYLES) {
            SetupChip(label = label, picked = solid.style == value, onClick = { onStyle(value) })
        }
    }
    SectionLabel("Rule spacing")
    val ruling = BackgroundRuling.parse(solid.config)
    val currentMm = BackgroundRulings.spacingPt(solid.style, ruling) / PageSetup.MM_PT
    ChipRow {
        for (mm in PageSetup.SPACING_MM) {
            SetupChip(
                label = "${mm.toInt()} mm",
                picked = abs(currentMm - mm) < 0.05,
                onClick = { onSpacing(mm) },
            )
        }
        Text(
            String.format(java.util.Locale.US, "%.1f mm", currentMm),
            color = DesktopTheme.inkMuted,
            style = DesktopTheme.numeric,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
    if (solid.style == "plain") {
        Text(
            "Plain paper has no ruling to space.",
            color = DesktopTheme.inkMuted,
            style = DesktopTheme.label,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The page-size presets in either orientation, with the live size named under them. */
@Composable
private fun FormatSection(page: Page, onSize: (Double, Double) -> Unit) {
    val landscape = page.width > page.height
    SectionLabel("Paper size")
    ChipRow {
        for (preset in PageSetup.PRESETS) {
            val matches = matchesSize(page.width, page.height, preset)
            SetupChip(
                label = preset.label,
                picked = matches,
                onClick = {
                    if (landscape) onSize(preset.heightPt, preset.widthPt)
                    else onSize(preset.widthPt, preset.heightPt)
                },
            )
        }
    }
    SectionLabel("Orientation")
    ChipRow {
        SetupChip(label = "Portrait", picked = !landscape, onClick = {
            val w = minOf(page.width, page.height)
            val h = maxOf(page.width, page.height)
            onSize(w, h)
        })
        SetupChip(label = "Landscape", picked = landscape, onClick = {
            val w = maxOf(page.width, page.height)
            val h = minOf(page.width, page.height)
            onSize(w, h)
        })
    }
    Text(
        String.format(
            java.util.Locale.US,
            "%.0f × %.0f pt  ·  %.1f × %.1f mm",
            page.width,
            page.height,
            page.width / PageSetup.MM_PT,
            page.height / PageSetup.MM_PT,
        ),
        color = DesktopTheme.inkMuted,
        style = DesktopTheme.numeric,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** A page size against a preset, in either orientation, with one point of slack. */
private fun matchesSize(width: Double, height: Double, preset: PageSetup.Preset): Boolean {
    fun near(a: Double, b: Double) = abs(a - b) <= 1.0
    return (near(width, preset.widthPt) && near(height, preset.heightPt)) ||
        (near(width, preset.heightPt) && near(height, preset.widthPt))
}

/** One group's heading, in the muted chrome ink the rest of the window uses. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = DesktopTheme.inkMuted,
        style = DesktopTheme.label,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/** A wrapping row of chips: the six paper styles fit two rows in a 430 dp panel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

/** One choice: a capsule that wears the steel when it is the live one. */
@Composable
private fun SetupChip(label: String, picked: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(
                if (picked) DesktopTheme.activeSlot else Color.Transparent,
                DesktopTheme.capsuleShape,
            )
            .hoverFill(DesktopTheme.capsuleShape, active = picked)
            .pointerInput(label, picked) { detectTapGestures { onClick() } }
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (picked) DesktopTheme.ink else DesktopTheme.inkSecondary,
            style = DesktopTheme.label,
        )
    }
}
