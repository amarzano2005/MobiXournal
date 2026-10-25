package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.LineWeight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.ToolGlyph
import com.mobixournal.render.SelectionOps
import com.mobixournal.ui.theme.FloatingSurface

/**
 * The Select tool's contextual action bar: cut / copy / paste / duplicate / delete, recolour and
 * re-width the selected strokes. Horizontally scrollable so it fits narrow screens. (Resize and
 * rotate are on-canvas handles, not buttons.)
 *
 * With a selection it carries the whole set; with **none** it carries Paste alone — the one action
 * that needs no selection — so the bar is the single home of paste in either state. That matters
 * twice over: paste used to sit in a bar of its own that hid the moment anything was selected, so
 * pasting a second copy meant deselecting first; and as one button floating by itself at the bottom
 * edge it read as unrelated to the actions it belongs with.
 *
 * It carries **no Done button**: the bar floats on the selection itself (see
 * [SelectionActionAnchor]), so tapping off the selection is the way out — and that tap already starts
 * the next stroke, which is what a Done button would have interrupted. Its controls are
 * [SelectionBarButton] wide rather than Material's 48dp, which is what keeps the bar small enough to
 * sit against the element it acts on instead of covering the page.
 */
@Composable
fun SelectionActionBar(
    hasSelection: Boolean,
    canPaste: Boolean,
    onPaste: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onRecolor: (Int) -> Unit,
    palette: ColorPaletteState,
    onReWidth: (Float) -> Unit,
    widthSlots: List<Float>,
    onAlign: (SelectionOps.SelectionAlign) -> Unit,
    onDistribute: (Boolean) -> Unit,
    canEditShape: Boolean = false,
    onEditShape: () -> Unit = {},
    canEditPlot: Boolean = false,
    onEditPlot: () -> Unit = {},
    canEditTable: Boolean = false,
    onEditTable: () -> Unit = {},
    canEditCircuit: Boolean = false,
    onEditCircuit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // The one floating surface: the menus' fill and the menus' *edge* ([FloatingSurface]), at the
    // floating corner (20dp) rather than the 50% pill this bar used to cut for itself — a 32dp bar
    // at 20dp is a pill anyway, and it is now the pill the rest of the chrome turns at.
    FloatingSurface(modifier = modifier) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasSelection) {
                if (canEditShape) {
                    BarIconButton("Edit shape", ChromeGlyph.EDIT.imageVector(), onEditShape)
                }
                if (canEditPlot) {
                    BarIconButton("Edit plot", ChromeGlyph.EDIT.imageVector(), onEditPlot)
                }
                if (canEditTable) {
                    BarIconButton("Edit table", ChromeGlyph.EDIT.imageVector(), onEditTable)
                }
                if (canEditCircuit) {
                    BarIconButton("Edit circuit", ChromeGlyph.EDIT.imageVector(), onEditCircuit)
                }
                BarIconButton("Cut", ChromeGlyph.CUT.imageVector(), onCut)
                BarIconButton("Copy", ChromeGlyph.COPY.imageVector(), onCopy)
            }
            // Paste rides with Copy: the same idea in the opposite direction, and putting it here is
            // what keeps it usable while something *is* selected.
            if (canPaste) {
                BarIconButton("Paste", ChromeGlyph.PASTE.imageVector(), onPaste)
            }
            if (hasSelection) {
                BarIconButton("Duplicate", ChromeGlyph.DUPLICATE.imageVector(), onDuplicate)
                RecolorMenu(onRecolor, palette)
                ReWidthMenu(widthSlots, onReWidth)
                AlignMenu(onAlign, onDistribute)
                BarIconButton("Delete", ChromeGlyph.DELETE.imageVector(), onDelete)
            }
        }
    }
}

/**
 * A drop-down with the alignment and distribution actions for a multi-element selection: six edges
 * (left/centre/right, top/middle/bottom) and even spacing along each axis. Text-only rows — the bar
 * is too small to carry eight more glyphs, and the labels read faster than abstract alignment icons.
 */
@Composable
private fun AlignMenu(
    onAlign: (SelectionOps.SelectionAlign) -> Unit,
    onDistribute: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarIconButton("Align & distribute", ChromeGlyph.ALIGN_LEFT.imageVector()) { open = true }
        ToolbarMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun row(label: String, action: () -> Unit) {
                DropdownMenuItem(
                    text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { open = false; action() },
                )
            }
            row("Align left") { onAlign(SelectionOps.SelectionAlign.LEFT) }
            row("Align horizontal centre") { onAlign(SelectionOps.SelectionAlign.H_CENTRE) }
            row("Align right") { onAlign(SelectionOps.SelectionAlign.RIGHT) }
            row("Align top") { onAlign(SelectionOps.SelectionAlign.TOP) }
            row("Align vertical middle") { onAlign(SelectionOps.SelectionAlign.V_MIDDLE) }
            row("Align bottom") { onAlign(SelectionOps.SelectionAlign.BOTTOM) }
            row("Distribute horizontally") { onDistribute(true) }
            row("Distribute vertically") { onDistribute(false) }
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
        BarIconButton("Recolour", ChromeGlyph.RECOLOUR.imageVector()) { open = true }
        ToolbarMenu(expanded = open, onDismissRequest = { open = false }) {
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
        BarIconButton("Width", ChromeGlyph.WIDTH.imageVector()) { open = true }
        ToolbarMenu(expanded = open, onDismissRequest = { open = false }) {
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
 * The background-select **region bar**: once a marquee has been dragged, Copy or Cut the flattened
 * region it left behind, or drop the region. Copy re-captures the region (so it can be re-copied
 * after the clipboard has moved on) and Cut also erases the ink it covers. The marquee shape isn't
 * picked here — rectangle and lasso are separate rail tools (see [EditorTool]) — so the bar composes
 * to nothing until a region actually exists.
 *
 * Paste deliberately does **not** live here. It is an action on the document, not on a region, so it
 * belongs to the action bar that also carries Copy and Cut — [SelectionActionBar] — which is where it
 * stays reachable while something is selected.
 */
@Composable
fun SelectModeBar(
    modifier: Modifier = Modifier,
    hasRegion: Boolean = false,
    onCopyRegion: () -> Unit = {},
    onCutRegion: () -> Unit = {},
    onClearRegion: () -> Unit = {},
) {
    if (!hasRegion) return
    FloatingSurface(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasRegion) {
                TextButton(onClick = onCopyRegion) {
                    Icon(ChromeGlyph.COPY.imageVector(), contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
                TextButton(onClick = onCutRegion) {
                    Icon(ChromeGlyph.CUT.imageVector(), contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Cut")
                }
            }
            if (hasRegion) {
                IconButton(onClick = onClearRegion) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear region")
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
    FloatingSurface(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("$nodeCount ${if (nodeCount == 1) "point" else "points"}")
            IconButton(onClick = onUndoPoint) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo last point")
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Outlined.Close, contentDescription = "Discard curve")
            }
            TextButton(onClick = onFinish, enabled = nodeCount >= 2) {
                Icon(Icons.Outlined.Check, contentDescription = null)
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
    FloatingSurface(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onCopy) {
                Icon(ChromeGlyph.COPY.imageVector(), contentDescription = null)
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
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingSurface(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                ToolGlyph.TABLE.imageVector(),
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
                Icon(Icons.Outlined.Remove, contentDescription = "Fewer rows", modifier = Modifier.size(16.dp))
            }
            Text("$rows", style = MaterialTheme.typography.bodyMedium)
            IconButton(
                onClick = { onRowsChange((rows + 1).coerceAtMost(50)) },
                enabled = rows < 50,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "More rows", modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.width(4.dp))

            Text("Cols:", style = MaterialTheme.typography.labelMedium)
            IconButton(
                onClick = { onColsChange((cols - 1).coerceAtLeast(1)) },
                enabled = cols > 1,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Outlined.Remove, contentDescription = "Fewer columns", modifier = Modifier.size(16.dp))
            }
            Text("$cols", style = MaterialTheme.typography.bodyMedium)
            IconButton(
                onClick = { onColsChange((cols + 1).coerceAtMost(50)) },
                enabled = cols < 50,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "More columns", modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.width(4.dp))

            GlassToggleChip(
                selected = hasHeader,
                label = "Header",
                onClick = { onHasHeaderChange(!hasHeader) },
            )

            Spacer(Modifier.width(4.dp))

            TextButton(onClick = onDone) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Done")
            }
        }
    }
}

/**
 * A one-option toggle that sits **on** the glass instead of on a plate of its own: a pill with the
 * family's hairline, tinted with the primary container only while it is on.
 *
 * It replaces a Material [androidx.compose.material3.FilterChip] here. A chip is drawn as its own
 * *container* — a tonal fill, its own elevation and M3's own border — so inside this bar it read as
 * an opaque little card lying on a pane of glass, which is exactly what made the table bar look
 * wrong however transparent the bar itself was. This keeps the bar one material edge to edge and
 * states "on" with the same steel grey the rail's live slot uses.
 */
@Composable
private fun GlassToggleChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.6f),
                shape = CircleShape,
            )
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        )
    }
}
