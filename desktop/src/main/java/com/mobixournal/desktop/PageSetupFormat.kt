package com.mobixournal.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.Page
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The **format** half of the window's page setup: how big the sheet is.
 *
 * It is the tablet's page-format dialog, without the parts that are about the app rather than this page:
 * the paper's presets, a **Default** entry (the size new sheets are born with, stored beside the window's
 * other settings), the two orientations, a unit choice and — the part the window used to lack — the two
 * fields that take **any** size, in mm, inches or points. A drag can only choose between presets while a
 * notebook's real size is a note's own; the fields are what makes the window able to set one.
 *
 * Every write is [DesktopPageSetupState.setSize]'s: one edit on the page in view, one undo step, so a
 * format chosen here is a page the file will carry and `Ctrl+Z` takes back.
 */
internal enum class PageUnit(val label: String, val perPt: Double) {
    /** Millimetres — the unit the tablet's own dialog opens on. */
    MM("mm", 25.4 / 72.0),

    /** Inches. */
    IN("in", 1.0 / 72.0),

    /** Points, the document's own unit. */
    PT("pt", 1.0);

    /** A value in this unit as the page stores it, in points. */
    fun toPt(value: Double): Double = value / perPt

    /** A page's points as this unit prints them. */
    fun fromPt(pt: Double): Double = pt * perPt
}

/** A dimension in the dialog's notation: at most one decimal, a whole number printed without `.0`. */
internal fun formatDimension(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

/** The smallest and largest sheet the fields accept, in points — 1 in to 200 in. */
private const val MIN_PAGE_PT = 72.0
private const val MAX_PAGE_PT = 14_400.0

/** The format section: the presets, the custom fields and the size the sheet has now. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FormatSection(
    page: Page,
    /** The page's new size, in points. */
    onSize: (Double, Double) -> Unit,
    /** The size new sheets are born with — what the **Default** entry applies. */
    defaultSize: Pair<Double, Double>,
) {
    var unit by remember { mutableStateOf(PageUnit.MM) }
    val landscape = page.width > page.height

    SectionLabel("Paper size")
    FlowRowChips {
        for (preset in PageSetup.PRESETS) {
            SetupChip(
                label = preset.label,
                picked = matchesSize(page.width, page.height, preset.widthPt, preset.heightPt),
                onClick = {
                    if (landscape) onSize(preset.heightPt, preset.widthPt)
                    else onSize(preset.widthPt, preset.heightPt)
                },
            )
        }
        // The **Default** entry the tablet offers: one tap for the size this window starts new sheets
        // with, so a reader who set their paper once does not have to match a preset by eye.
        SetupChip(
            label = "Default",
            picked = matchesSize(page.width, page.height, defaultSize.first, defaultSize.second),
            onClick = { onSize(defaultSize.first, defaultSize.second) },
        )
    }

    SectionLabel("Orientation")
    FlowRowChips {
        SetupChip(label = "Portrait", picked = !landscape, onClick = {
            onSize(minOf(page.width, page.height), maxOf(page.width, page.height))
        })
        SetupChip(label = "Landscape", picked = landscape, onClick = {
            onSize(maxOf(page.width, page.height), minOf(page.width, page.height))
        })
    }

    SectionLabel("Custom size")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (candidate in PageUnit.entries) {
            SetupChip(
                label = candidate.label,
                picked = candidate == unit,
                onClick = { unit = candidate },
            )
        }
        Spacer(Modifier.weight(1f))
        SetupChip(label = "Swap", picked = false, onClick = { onSize(page.height, page.width) })
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Two halves of the panel, not two full-width fields: `Field` fills the width it is given, so
        // each dimension gets half the row and the pair cannot overflow the surface.
        Box(Modifier.weight(1f)) {
            DimensionField(
                label = "Width (${unit.label})",
                initial = formatDimension(unit.fromPt(page.width)),
                unit = unit,
                onCommit = { pt -> pt.takeIf { it in MIN_PAGE_PT..MAX_PAGE_PT }?.let { onSize(it, page.height) } },
            )
        }
        Box(Modifier.weight(1f)) {
            DimensionField(
                label = "Height (${unit.label})",
                initial = formatDimension(unit.fromPt(page.height)),
                unit = unit,
                onCommit = { pt -> pt.takeIf { it in MIN_PAGE_PT..MAX_PAGE_PT }?.let { onSize(page.width, it) } },
            )
        }
    }
    Text(
        String.format(
            Locale.US,
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
    Text(
        String.format(
            Locale.US,
            "Default sheet: %s",
            PageSetup.PRESETS.firstOrNull { matchesSize(defaultSize.first, defaultSize.second, it.widthPt, it.heightPt) }
                ?.label
                ?: "%.0f × %.0f pt".format(defaultSize.first, defaultSize.second),
        ),
        color = DesktopTheme.inkMuted,
        style = DesktopTheme.label,
    )
}

/**
 * One dimension of the sheet: a field that commits on `Enter` or when it loses focus, in **points**.
 *
 * The field prints and parses in the section's [PageUnit]; the answer is always the document's own unit,
 * so a size typed in millimetres and the same size typed in points land as the same page. A typo, a zero
 * or a value outside the printable range is simply not committed — the page keeps what it had, and the
 * field shows it again on the next frame.
 */
@Composable
private fun DimensionField(
    label: String,
    initial: String,
    unit: PageUnit,
    onCommit: (Double) -> Unit,
) {
    Field(label) {
        ValueField(
            initial = initial,
            numeric = true,
            onCommit = { entered ->
                entered.replace(',', '.').trim().toDoubleOrNull()
                    ?.takeIf { it > 0.0 && it.isFinite() }
                    ?.let(unit::toPt)
                    ?.let(onCommit)
            },
        )
    }
}

/** A wrapping row of chips, the paper styles' own layout (`ChipRow` in `PageSetup.kt`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

/** A page size against a preset, in either orientation, with one point of slack. */
internal fun matchesSize(width: Double, height: Double, presetW: Double, presetH: Double): Boolean {
    fun near(a: Double, b: Double) = abs(a - b) <= 1.0
    return (near(width, presetW) && near(height, presetH)) ||
        (near(width, presetH) && near(height, presetW))
}
