package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mobixournal.render.PageSizeUnit
import com.mobixournal.render.decimalText
import kotlin.math.abs
import kotlin.math.roundToInt

/** Format a unit value for a field — [decimalText]'s one-decimal rule, under this dialog's name. */
internal fun fmtDim(value: Double): String = decimalText(value)

/**
 * A short label for the current page size: the matching preset's name (either orientation), else the
 * dimensions in [unit] — the unit the page format was set to — used as the trailing hint on the
 * "Page format" menu row.
 */
internal fun pageSizeLabel(
    widthPt: Double,
    heightPt: Double,
    unit: PageSizeUnit = PageSizeUnit.MM,
): String {
    PAGE_PRESETS.firstOrNull { matchesPageSize(widthPt, heightPt, it.widthPt, it.heightPt) }
        ?.let { return it.name }
    return "${unit.print(widthPt)}×${unit.print(heightPt)} ${unit.label}"
}

/**
 * The paper half of the page format, as one piece of state: the unit the fields are written in, the
 * size the user has settled on, and the two field strings that carry what they typed (kept separate
 * from the parsed size so a half-typed "20" does not become 20 mm on its way to 200).
 */
internal class PaperState(widthPt: Double, heightPt: Double, unit: PageSizeUnit = PageSizeUnit.MM) {
    var unit by mutableStateOf(unit)
    var widthPt by mutableStateOf(widthPt)
    var heightPt by mutableStateOf(heightPt)
    var widthText by mutableStateOf(fmtDim(unit.fromPt(widthPt)))
    var heightText by mutableStateOf(fmtDim(unit.fromPt(heightPt)))

    fun resync() {
        widthText = fmtDim(unit.fromPt(widthPt))
        heightText = fmtDim(unit.fromPt(heightPt))
    }

    fun updateFromExternal(w: Double, h: Double) {
        if (!matchesPageSize(widthPt, heightPt, w, h)) {
            widthPt = w
            heightPt = h
            resync()
        }
    }
}

/**
 * The **page format** of the document: its paper (preset tabs A4/A5/Letter/Legal plus **Default**, a
 * unit toggle, width/height fields and a swap button).
 *
 * [onConfirm] reports the paper; the caller applies it to the page in view. [onSaveAsDefault] stores
 * the paper as the default new documents and pages are born with.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PageFormatDialog(
    initialWidthPt: Double,
    initialHeightPt: Double,
    defaultWidthPt: Double,
    defaultHeightPt: Double,
    /**
     * The unit the fields are written in — the app's own ([PageSizeUnit]), stored when it changes, so
     * the Pages menu's quick size reads the sheet in the same unit this dialog was left on.
     */
    unit: PageSizeUnit = PageSizeUnit.MM,
    onUnit: (PageSizeUnit) -> Unit = {},
    onConfirm: (Double, Double) -> Unit,
    onSaveAsDefault: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val paper = remember(initialWidthPt, initialHeightPt) { PaperState(initialWidthPt, initialHeightPt, unit) }
    val defaultPaper = remember { PaperState(defaultWidthPt, defaultHeightPt, unit) }
    LaunchedEffect(defaultWidthPt, defaultHeightPt) {
        defaultPaper.updateFromExternal(defaultWidthPt, defaultHeightPt)
    }
    var inDefaultSection by remember { mutableStateOf(false) }

    com.mobixournal.ui.theme.FloatingDialog(
        onDismissRequest = onDismiss,
        title = {
            if (inDefaultSection) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        onSaveAsDefault(defaultPaper.widthPt, defaultPaper.heightPt)
                        inDefaultSection = false
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Default settings")
                }
            } else {
                Text("Page format")
            }
        },
        text = {
            if (inDefaultSection) {
                DefaultFormatContent(
                    defaultPaper = defaultPaper,
                    onPaperChanged = { w, h -> onSaveAsDefault(w, h) },
                    onUnit = onUnit,
                )
            } else {
                ActiveFormatContent(paper, onUnit)
            }
        },
        confirmButton = {
            if (inDefaultSection) {
                DefaultFormatActionButtons(
                    onBack = {
                        onSaveAsDefault(defaultPaper.widthPt, defaultPaper.heightPt)
                        inDefaultSection = false
                    },
                    onCancel = onDismiss,
                    onSave = {
                        onSaveAsDefault(defaultPaper.widthPt, defaultPaper.heightPt)
                        inDefaultSection = false
                    },
                )
            } else {
                ActiveFormatActionButtons(
                    onOpenDefaults = { inDefaultSection = true },
                    onCancel = onDismiss,
                    onSaveAsDefault = {
                        defaultPaper.widthPt = paper.widthPt
                        defaultPaper.heightPt = paper.heightPt
                        defaultPaper.resync()
                        onSaveAsDefault(paper.widthPt, paper.heightPt)
                    },
                    onSet = { onConfirm(paper.widthPt, paper.heightPt) },
                )
            }
        },
    )
}

@Composable
private fun ActiveFormatContent(paper: PaperState, onUnit: (PageSizeUnit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Paper", style = MaterialTheme.typography.labelMedium)
        PaperSection(paper, onUnit = onUnit)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaultFormatContent(
    defaultPaper: PaperState,
    onPaperChanged: (Double, Double) -> Unit,
    onUnit: (PageSizeUnit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Default dimensions", style = MaterialTheme.typography.labelMedium)
        PaperSection(defaultPaper, onChanged = onPaperChanged, onUnit = onUnit)
    }
}

@Composable
private fun ActiveFormatActionButtons(
    onOpenDefaults: () -> Unit,
    onCancel: () -> Unit,
    onSaveAsDefault: () -> Unit,
    onSet: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = onOpenDefaults,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Default settings", fontWeight = FontWeight.Medium)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = onSaveAsDefault) { Text("Set as default") }
            TextButton(onClick = onSet) { Text("Set") }
        }
    }
}

@Composable
private fun DefaultFormatActionButtons(
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = onSave) { Text("Save default") }
        }
    }
}

/**
 * The paper dimensions and presets: preset tabs, unit selector, and the width/height fields.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaperSection(
    paper: PaperState,
    onChanged: ((Double, Double) -> Unit)? = null,
    /** The unit a chip picked here is stored as — the quick size reads the sheet in it afterwards. */
    onUnit: ((PageSizeUnit) -> Unit)? = null,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (preset in PAGE_PRESETS) {
            SizeTab(
                label = preset.name,
                selected = matchesPageSize(paper.widthPt, paper.heightPt, preset.widthPt, preset.heightPt),
                onClick = {
                    paper.widthPt = preset.widthPt
                    paper.heightPt = preset.heightPt
                    paper.resync()
                    onChanged?.invoke(paper.widthPt, paper.heightPt)
                },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Units:", style = MaterialTheme.typography.labelMedium)
        for (u in PageSizeUnit.entries) {
            TextButton(onClick = {
                if (u != paper.unit) {
                    paper.unit = u
                    paper.resync()
                    onUnit?.invoke(u)
                }
            }) {
                Text(
                    u.label,
                    fontWeight = if (u == paper.unit) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SizeField(
            label = "Width",
            text = paper.widthText,
            unit = paper.unit,
            onText = { entered ->
                paper.widthText = entered
                entered.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                    paper.widthPt = paper.unit.toPt(it).coerceIn(72.0, 14400.0)
                    onChanged?.invoke(paper.widthPt, paper.heightPt)
                }
            },
        )
        IconButton(onClick = {
            val w = paper.widthPt
            paper.widthPt = paper.heightPt
            paper.heightPt = w
            paper.resync()
            onChanged?.invoke(paper.widthPt, paper.heightPt)
        }) { Icon(Icons.Outlined.SwapHoriz, contentDescription = "Swap width and height") }
        SizeField(
            label = "Height",
            text = paper.heightText,
            unit = paper.unit,
            onText = { entered ->
                paper.heightText = entered
                entered.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                    paper.heightPt = paper.unit.toPt(it).coerceIn(72.0, 14400.0)
                    onChanged?.invoke(paper.widthPt, paper.heightPt)
                }
            },
        )
    }
}

/** One editable dimension of the paper, in the dialog's current [unit]. */
@Composable
private fun SizeField(label: String, text: String, unit: PageSizeUnit, onText: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onText,
        label = { Text(label) },
        suffix = { Text(unit.label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(140.dp),
    )
}

/** One entry in the page-size preset row: bold while the current size matches the entry. */
@Composable
private fun SizeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
