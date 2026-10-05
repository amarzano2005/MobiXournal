package com.mobixournal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import com.mobixournal.format.FontDescription
import com.mobixournal.format.SaveFormat
import com.mobixournal.render.ImportPdfMode
import kotlin.math.roundToInt

/** The families offered in the text dialog — names desktop Xournal++ and Android both resolve. */
private val TEXT_FAMILIES = listOf("Sans", "Serif", "Monospace")

/**
 * "Save As" chooser: name the file and pick the on-disk format. [SaveFormat.ORIGINAL] writes the
 * standard gzip `.xopp` (a PDF background stays linked by location); [SaveFormat.ZIPPED] writes a
 * single self-contained file with the PDF embedded inside (see `docs/architecture.md`). The choice
 * becomes sticky — later plain Saves reuse it — so the picker pre-selects the current format.
 *
 * [initialName] is the open document's own name (already carrying the `.xopp` suffix), not a generic
 * placeholder: saving a copy of `notes.xopp` should start at `notes.xopp`, not at `document.xopp`.
 * Whatever is typed here is forced through the same suffix rule before it reaches the picker.
 */
@Composable
fun SaveAsDialog(
    initialName: String,
    initialFormat: SaveFormat,
    onConfirm: (filename: String, format: SaveFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var format by remember { mutableStateOf(initialFormat) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save As") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("File name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Format", style = MaterialTheme.typography.labelMedium)
                FormatOption(
                    selected = format == SaveFormat.ORIGINAL,
                    title = "Original (gzip)",
                    subtitle = "Standard Xournal++ file; any PDF background stays linked by location.",
                    onClick = { format = SaveFormat.ORIGINAL },
                )
                FormatOption(
                    selected = format == SaveFormat.ZIPPED,
                    title = "Zipped (single file)",
                    subtitle = "One portable file with the PDF embedded inside.",
                    onClick = { format = SaveFormat.ZIPPED },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, format) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * "Import PDF" chooser: does the picked PDF become the whole document ([ImportPdfMode.REPLACE], which
 * discards the current pages) or land after the pages already open ([ImportPdfMode.APPEND])? A `.xopp`
 * can reference just one background PDF, so when the document already has one ([merging]) the append
 * merges the two into a single joined PDF — the subtitle says so, since the joined file is what later
 * saves link to (see [ImportPdfMode]).
 */
@Composable
fun ImportPdfDialog(
    merging: Boolean,
    onConfirm: (ImportPdfMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(ImportPdfMode.APPEND) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormatOption(
                    selected = mode == ImportPdfMode.REPLACE,
                    title = "Replace",
                    subtitle = "The PDF's pages become this document, replacing the current pages.",
                    onClick = { mode = ImportPdfMode.REPLACE },
                )
                FormatOption(
                    selected = mode == ImportPdfMode.APPEND,
                    title = "Append",
                    subtitle = if (merging)
                        "Add the PDF's pages after the pages already open, merging it into this document's background PDF."
                    else "Add the PDF's pages after the pages already open.",
                    onClick = { mode = ImportPdfMode.APPEND },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(mode) }) { Text("Choose PDF…") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One selectable option row in [SaveAsDialog]/[ImportPdfDialog]: a radio plus a title and explanation. */
@Composable
fun FormatOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        // A disabled option stays visible (so the choice is explained) but reads as unavailable.
        val alpha = if (enabled) 1f else 0.38f
        Column(modifier = Modifier.alpha(alpha)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * The styled text-box editor: content plus the styling the `.xopp` `<text>` element can hold —
 * font family, bold/italic, point size, and colour. Confirms with all five so the caller can
 * compose the font description and place/replace the box. (Underline is intentionally absent —
 * the format can't store it; see the scope rule in `AGENTS.md`.)
 */
@Composable
fun TextBoxDialog(
    title: String,
    initialContent: String,
    initialFamily: String,
    initialBold: Boolean,
    initialItalic: Boolean,
    initialSize: Double,
    initialColor: Int,
    palette: ColorPaletteState,
    onConfirm: (content: String, family: String, bold: Boolean, italic: Boolean, sizePt: Double, colorArgb: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var content by remember { mutableStateOf(initialContent) }
    var family by remember { mutableStateOf(initialFamily) }
    var bold by remember { mutableStateOf(initialBold) }
    var italic by remember { mutableStateOf(initialItalic) }
    var size by remember { mutableStateOf(initialSize.toFloat().coerceIn(TEXT_SIZE_MIN, TEXT_SIZE_MAX)) }
    var colorArgb by remember { mutableStateOf(initialColor) }
    var editingColor by remember { mutableStateOf(false) }

    // Outside the AlertDialog below: the HSV editor must outlive the row that opened it.
    CustomColorEditor(visible = editingColor, palette = palette, onDismiss = { editingColor = false })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    singleLine = false,
                    label = { Text("Text") },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FontFamilyPicker(family = family, onFamily = { family = it })
                    FilterChip(selected = bold, onClick = { bold = !bold }, label = { Text("Bold") })
                    FilterChip(selected = italic, onClick = { italic = !italic }, label = { Text("Italic") })
                }
                Text("Size: ${size.roundToInt()} pt")
                Slider(
                    value = size,
                    onValueChange = { size = it },
                    valueRange = TEXT_SIZE_MIN..TEXT_SIZE_MAX,
                )
                ColorPaletteRows(
                    selected = colorArgb,
                    palette = palette,
                    onPick = { c -> colorArgb = c },
                    onEditCustom = { editingColor = true },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(content, family, bold, italic, size.toDouble(), colorArgb) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Dropdown to pick a text font family from [TEXT_FAMILIES]. */
@Composable
fun FontFamilyPicker(family: String, onFamily: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(family) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (f in TEXT_FAMILIES) {
                DropdownMenuItem(text = { Text(f) }, onClick = { onFamily(f); open = false })
            }
        }
    }
}

/**
 * Dialog to customize the 3 interior angles of a scalene triangle (sum = 180°).
 */
@Composable
fun ScaleneAnglesDialog(
    angleA: Float,
    angleB: Float,
    angleC: Float,
    onConfirm: (angleA: Float, angleB: Float, angleC: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var a by remember { mutableStateOf(angleA) }
    var b by remember { mutableStateOf(angleB) }
    var c by remember { mutableStateOf(angleC) }

    val sum = a.roundToInt() + b.roundToInt() + c.roundToInt()
    val isValid = sum == 180 && a >= 5f && b >= 5f && c >= 5f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scalene triangle angles") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Set the three interior angles (sum must equal 180°).",
                    style = MaterialTheme.typography.bodySmall,
                )

                // Visual preview canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val strokeColor = MaterialTheme.colorScheme.primary
                    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val total = a + b + c
                        if (total <= 0f) return@Canvas
                        val radA = Math.toRadians((a * 180.0 / total))
                        val radB = Math.toRadians((b * 180.0 / total))
                        val radC = Math.PI - radA - radB
                        val sideC = sin(radC)
                        val sideB = sin(radB)
                        val vx0 = 0.0
                        val vy0 = 0.0
                        val vx1 = sideC
                        val vy1 = 0.0
                        val vx2 = sideB * cos(radA)
                        val vy2 = sideB * sin(radA)

                        val minX = minOf(vx0, vx1, vx2)
                        val maxX = maxOf(vx0, vx1, vx2)
                        val minY = minOf(vy0, vy1, vy2)
                        val maxY = maxOf(vy0, vy1, vy2)
                        val triW = (maxX - minX).coerceAtLeast(0.001)
                        val triH = (maxY - minY).coerceAtLeast(0.001)

                        val scale = minOf((size.width * 0.8) / triW, (size.height * 0.8) / triH)
                        val offsetX = (size.width - triW * scale) / 2.0 - minX * scale
                        val offsetY = (size.height - triH * scale) / 2.0 + maxY * scale

                        fun px(x: Double) = (x * scale + offsetX).toFloat()
                        fun py(y: Double) = (offsetY - y * scale).toFloat()

                        val p = Path().apply {
                            moveTo(px(vx0), py(vy0))
                            lineTo(px(vx1), py(vy1))
                            lineTo(px(vx2), py(vy2))
                            close()
                        }
                        drawPath(p, color = fillColor)
                        drawPath(p, color = strokeColor, style = Stroke(width = 2.dp.toPx()))
                    }
                }

                AngleRow(label = "Angle A (α)", value = a, onValueChange = { a = it })
                AngleRow(label = "Angle B (β)", value = b, onValueChange = { b = it })
                AngleRow(label = "Angle C (γ)", value = c, onValueChange = { c = it })

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (isValid) "Sum: $sum° ✓" else "Sum: $sum° (must be 180°)",
                        color = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!isValid) {
                        val neededC = (180 - a.roundToInt() - b.roundToInt()).toFloat()
                        if (neededC in 5f..170f) {
                            TextButton(onClick = { c = neededC }) {
                                Text("Auto-balance C (${neededC.roundToInt()}°)")
                            }
                        }
                    }
                }

                Text("Presets:", style = MaterialTheme.typography.labelSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    val presets = listOf(
                        Triple(40f, 60f, 80f),
                        Triple(30f, 60f, 90f),
                        Triple(45f, 60f, 75f),
                        Triple(35f, 55f, 90f),
                        Triple(25f, 45f, 110f),
                    )
                    for ((pa, pb, pc) in presets) {
                        OutlinedButton(
                            onClick = { a = pa; b = pb; c = pc },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("${pa.roundToInt()}°-${pb.roundToInt()}°-${pc.roundToInt()}°", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(a, b, c) }, enabled = isValid) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AngleRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
        IconButton(
            onClick = { onValueChange((value - 1f).coerceIn(5f, 170f)) },
            enabled = value > 5f,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $label", modifier = Modifier.size(16.dp))
        }
        Slider(
            value = value,
            onValueChange = { onValueChange(it.roundToInt().toFloat()) },
            valueRange = 5f..170f,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onValueChange((value + 1f).coerceIn(5f, 170f)) },
            enabled = value < 170f,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $label", modifier = Modifier.size(16.dp))
        }
        Text(
            "${value.roundToInt()}°",
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

