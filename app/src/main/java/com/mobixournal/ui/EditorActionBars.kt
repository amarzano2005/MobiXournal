package com.mobixournal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The Select tool's contextual action bar, shown while a selection is active: cut / copy /
 * duplicate / delete, recolour and re-width the selected strokes. Horizontally scrollable so it fits
 * narrow screens. (Resize and rotate are on-canvas handles, not buttons.)
 *
 * It carries **no Done button**: the bar floats on the selection itself (see
 * [SelectionActionAnchor]), so tapping off the selection is the way out — and that tap already starts
 * the next stroke, which is what a Done button would have interrupted. Its controls are
 * [SelectionBarButton] wide rather than Material's 48dp, which is what keeps the bar small enough to
 * sit against the element it acts on instead of covering the page.
 */
@Composable
fun SelectionActionBar(
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onRecolor: (Int) -> Unit,
    palette: ColorPaletteState,
    onReWidth: (Float) -> Unit,
    widthSlots: List<Float>,
    modifier: Modifier = Modifier,
) {
    // 50% corners: a pill that follows whatever height the compact buttons give the bar, instead of a
    // fixed radius that a shorter bar would read as a lozenge.
    val shape = RoundedCornerShape(50)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    Surface(
        modifier = modifier,
        shape = shape,
        border = border,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIconButton("Cut", Icons.Filled.ContentCut, onCut)
            BarIconButton("Copy", Icons.Filled.ContentCopy, onCopy)
            BarIconButton("Duplicate", Icons.Filled.LibraryAdd, onDuplicate)
            RecolorMenu(onRecolor, palette)
            ReWidthMenu(widthSlots, onReWidth)
            BarIconButton("Delete", Icons.Filled.Delete, onDelete)
        }
    }
}

/**
 * One button of the compact action bars: a 32dp tap square around an 18dp glyph, over the 48dp
 * [IconButton] that would have set the bar's height.
 *
 * Built as a plain [Box] because [IconButton] enforces Material's 48dp minimum touch target, which no
 * `Modifier.size` can undo — using it is what previously made this bar as tall as a rail button and
 * twice as wide as it needed to be.
 */
@Composable
private fun BarIconButton(
    contentDescription: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SelectionBarButton)
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(SelectionBarIcon))
    }
}

/** The tap square one [BarIconButton] occupies; the bar's height is this plus its 2dp of padding. */
private val SelectionBarButton = 32.dp

/** The glyph inside a [BarIconButton] — 18dp, so a couple of pixels of the square stay as margin. */
private val SelectionBarIcon = 18.dp

/**
 * A drop-down that recolours the selection, offering the shared [ColorPaletteRows] — the same
 * swatches and the custom slot as the pen's palette. The colour picked is applied to the selection.
 */
@Composable
private fun RecolorMenu(onRecolor: (Int) -> Unit, palette: ColorPaletteState) {
    var open by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    Box {
        BarIconButton("Recolour", Icons.Filled.Palette) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ColorPaletteRows(
                selected = null,
                palette = palette,
                onPick = { c -> onRecolor(c); open = false },
                onEditCustom = { editing = true; open = false },
            )
        }
    }
    CustomColorEditor(visible = editing, palette = palette, onDismiss = { editing = false })
}

/** A width drop-down that re-widths the selected strokes, using the same configurable slots as the pen. */
@Composable
private fun ReWidthMenu(widthSlots: List<Float>, onReWidth: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarIconButton("Width", Icons.Filled.LineWeight) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            widthSlots.forEachIndexed { i, pt ->
                DropdownMenuItem(
                    text = { Text("${PEN_WIDTH_LABELS[i]}  (${ptLabel(pt)} pt)") },
                    onClick = { onReWidth(pt); open = false },
                )
            }
        }
    }
}

/**
 * Shown in a marquee mode when nothing is selected: paste the clipboard onto the visible page, and —
 * once a background-select marquee has been dragged — Copy or Cut the region it left behind. Copy
 * re-captures the region (so it can be re-copied after the clipboard has moved on) and Cut also
 * erases the ink it covers. The marquee shape isn't picked here — rectangle and lasso are separate
 * rail tools (see [EditorTool]) — so the bar composes to nothing when there is nothing to act on.
 */
@Composable
fun SelectModeBar(
    canPaste: Boolean,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
    hasRegion: Boolean = false,
    onCopyRegion: () -> Unit = {},
    onCutRegion: () -> Unit = {},
    onClearRegion: () -> Unit = {},
) {
    if (!canPaste && !hasRegion) return
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasRegion) {
                TextButton(onClick = onCopyRegion) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
                TextButton(onClick = onCutRegion) {
                    Icon(Icons.Filled.ContentCut, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Cut")
                }
            }
            if (canPaste) {
                TextButton(onClick = onPaste) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Paste")
                }
            }
            if (hasRegion) {
                IconButton(onClick = onClearRegion) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear region")
                }
            }
        }
    }
}

/**
 * Shown while a spline is open: finish the curve, drop the last control point, or throw it away.
 * The keyboard bindings (Enter/Escape) and the finishing double-tap still work — this is the
 * on-screen equivalent, since a tablet with no keyboard otherwise has only the double-tap, which is
 * easy to miss and awkward when two control points genuinely belong close together.
 *
 * Finish is disabled below two points, matching the commit rule: a one-node spline draws nothing.
 */
@Composable
fun SplineModeBar(
    nodeCount: Int,
    onFinish: () -> Unit,
    onUndoPoint: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nodeCount <= 0) return
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("$nodeCount ${if (nodeCount == 1) "point" else "points"}")
            IconButton(onClick = onUndoPoint) {
                Icon(Icons.Filled.Undo, contentDescription = "Undo last point")
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Discard curve")
            }
            TextButton(onClick = onFinish, enabled = nodeCount >= 2) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Finish")
            }
        }
    }
}

/** Shown while PDF text is selected: copy the selection to the system clipboard, or deselect. */
@Composable
fun TextSelectionBar(
    onCopy: () -> Unit,
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
            TextButton(onClick = onDeselect) { Text("Deselect") }
        }
    }
}

/**
 * The Table tool's contextual action bar: adjust rows & columns, and insert a table at current viewport centre.
 */
@Composable
fun TableModeBar(
    rows: Int,
    cols: Int,
    hasHeader: Boolean = false,
    onRowsChange: (Int) -> Unit,
    onColsChange: (Int) -> Unit,
    onHasHeaderChange: (Boolean) -> Unit = {},
    onInsert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                TableIcon,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Rows:", style = MaterialTheme.typography.labelMedium)
            IconButton(
                onClick = { onRowsChange((rows - 1).coerceAtLeast(1)) },
                enabled = rows > 1,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Fewer rows", modifier = Modifier.size(16.dp))
            }
            Text("$rows", style = MaterialTheme.typography.bodyMedium)
            IconButton(
                onClick = { onRowsChange((rows + 1).coerceAtMost(50)) },
                enabled = rows < 50,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "More rows", modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.width(4.dp))

            Text("Cols:", style = MaterialTheme.typography.labelMedium)
            IconButton(
                onClick = { onColsChange((cols - 1).coerceAtLeast(1)) },
                enabled = cols > 1,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Fewer columns", modifier = Modifier.size(16.dp))
            }
            Text("$cols", style = MaterialTheme.typography.bodyMedium)
            IconButton(
                onClick = { onColsChange((cols + 1).coerceAtMost(50)) },
                enabled = cols < 50,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "More columns", modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.width(4.dp))

            FilterChip(
                selected = hasHeader,
                onClick = { onHasHeaderChange(!hasHeader) },
                label = { Text("Header", style = MaterialTheme.typography.labelMedium) },
            )

            Spacer(Modifier.width(4.dp))

            TextButton(onClick = onInsert) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Insert")
            }
        }
    }
}
