package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.render.BackgroundRuling
import com.mobixournal.render.BackgroundRulings
import com.mobixournal.render.PageTemplates
import com.mobixournal.render.Stationery
import com.mobixournal.ui.theme.FloatingDialog
import kotlin.math.abs

/**
 * Paper-style labels, paired with the `<background style=…>` value written to the `.xopp` file. Names
 * match desktop Xournal++ verbatim so files round-trip; the renderer draws each in
 * [com.mobixournal.render.BackgroundRenderer].
 */
internal val BACKGROUND_STYLES: List<Pair<String, String>> = listOf(
    "plain" to "Plain",
    "lined" to "Lined",
    "ruled" to "Ruled",
    "graph" to "Graph",
    "dotted" to "Dotted",
    // Desktop Xournal++'s isometric paper (`isograph`); its `r1` is the triangle's side.
    "isograph" to "Isometric",
)

/** Ruling-spacing chips, in millimetres — the sizes people actually ask for, 1 mm to 10 mm. */
private val RULE_SPACING_MM = listOf(2.0, 3.0, 5.0, 7.0, 10.0)

/**
 * The live paper style's own name, for the **Page background** row's trailing hint — so the page menu
 * says which sheet is behind it instead of leaving the user to open the dialog and look. Null when the
 * page has no solid sheet to name (a PDF or pixmap), and for a style this build does not know.
 */
internal fun paperStyleLabel(style: String?): String? =
    BACKGROUND_STYLES.firstOrNull { (value, _) -> value == style }?.second

/**
 * The **page-background** dialog: what the sheet *is* (paper style), how it is *ruled* (spacing) and a
 * finished template over the top (stationery).
 *
 * It is a dialog rather than a section inside the Pages menu because that menu is already a long list
 * of page commands; paper is a submenu of its own, exactly like **Page format**, which is the other
 * question about the page rather than its parts. The three groups still run in the order they are set
 * — style, then spacing, then a template — and each is one flowing row of chips rather than a stacked
 * list, so the dialog stays short.
 *
 * [style] and [config] are the current page's, or null when the page is a PDF/pixmap (no solid sheet
 * to re-rule), in which case the paper and spacing controls are disabled and a line says why. Spacing is
 * written as desktop's own `r1` parameter ([BackgroundRuling]), so a page ruled at 7 mm reopens at
 * 7 mm on the desktop rather than silently reverting to the default.
 *
 * Style and spacing are settings, so they leave the dialog open to be tuned together; a **stationery**
 * preset is a one-shot action (it lays ink down), so it applies and closes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PageBackgroundDialog(
    open: Boolean,
    style: String?,
    config: String?,
    onStyle: (String) -> Unit,
    onConfig: (String?) -> Unit,
    onStationery: (Stationery) -> Unit,
    onDone: () -> Unit,
) {
    if (!open) return
    val ruling = remember(config) { BackgroundRuling.parse(config) }
    FloatingDialog(
        onDismissRequest = onDone,
        title = { Text("Page background") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Paper")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((value, label) in BACKGROUND_STYLES) {
                        FilterChip(
                            selected = value == style,
                            enabled = style != null,
                            onClick = { onStyle(value) },
                            label = { Text(label) },
                        )
                    }
                }
                SectionLabel("Rule spacing")
                RuleSpacingChips(style, ruling, onConfig)
                HorizontalDivider()
                SectionLabel("Stationery")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (kind in Stationery.entries) {
                        FilterChip(
                            selected = false,
                            enabled = style != null || kind == Stationery.CORNELL,
                            onClick = { onStationery(kind) },
                            label = { Text(kind.label) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    )
}

/** A heading naming one group of the dialog's controls. */
@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelMedium)
}

/**
 * The spacing choices: a row of millimetre chips — the tapped one written straight to the page — with
 * a compact field for any other value at the end of the same row. The chips mark the spacing the page
 * currently rules at, so the dialog says which one is live instead of leaving the user to remember.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleSpacingChips(
    style: String?,
    ruling: BackgroundRuling,
    onConfig: (String?) -> Unit,
) {
    if (style == null) {
        Text(
            "No ruling to space on a PDF or image page",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val currentMm = BackgroundRulings.spacingPt(style, ruling) / PageTemplates.MM_PT
    var text by remember(currentMm) { mutableStateOf(String.format(java.util.Locale.US, "%.1f", currentMm)) }
    val write = { mm: Double ->
        onConfig(ruling.withPt(BackgroundRuling.KEY_SPACING, mm * PageTemplates.MM_PT).text())
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (mm in RULE_SPACING_MM) {
            FilterChip(
                selected = abs(currentMm - mm) < 0.05,
                onClick = { write(mm) },
                label = { Text("${mm.toInt()} mm") },
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                // Millimetres, one decimal: anything else is a typo, and a spacing of zero would
                // divide the ruling into an infinite number of lines.
                val clean = typed.filter { it.isDigit() || it == '.' }.take(5)
                text = clean
                clean.toDoubleOrNull()?.takeIf { it in 0.5..50.0 }?.let(write)
            },
            singleLine = true,
            // A suffix rather than a floating label keeps the field one line tall, so it reads as part
            // of the chip row it sits in.
            suffix = { Text("mm") },
            modifier = Modifier
                .width(104.dp)
                .padding(top = 2.dp),
        )
    }
}
