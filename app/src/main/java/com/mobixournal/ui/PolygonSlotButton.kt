package com.mobixournal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobixournal.ui.theme.rememberChromeInk

/**
 * The **Polygons slot**, whose menu is the polygon's side count.
 *
 * The family is one tool with a number, not a button per polygon, so this slot follows the figures that
 * carry a variant ([CompactShapeKindButton] in `EditorRegions.kt`) rather than the group slots: tapping
 * it takes the tool, tapping it again — or a long press — opens the 3–12 picker. The figure sits
 * **centred** at the rail's own glyph size, like every other slot's, and the two marks that describe it
 * keep to the corners: the count that will be drawn on the **top-left edge** of the button, the menu's
 * caret in the **bottom-right** one. Nothing is printed on the drawing, so the count reads as a label
 * on the slot instead of a marking on the pentagon. Picking a count also takes hold of the tool,
 * because seven sides is always about drawing a heptagon next.
 *
 * The count is the one value Settings ▸ Figures writes too (`AppSettings.polygonSides`), so the two
 * surfaces cannot disagree; [POLYGON_SIDE_CHOICES] keeps both on the builder's own range.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PolygonSlotButton(
    active: Boolean,
    sides: Int,
    onPick: (Int) -> Unit,
    onClick: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val tint = if (active) MaterialTheme.colorScheme.primary else rememberChromeInk()
    val shape = RoundedCornerShape(8.dp)
    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(shape)
                .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                .combinedClickable(
                    onClick = { if (active) open = true else onClick() },
                    onLongClick = { open = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            // The glyph is the slot's face, drawn centred like every other rail glyph and at nine tenths
            // of [ToolIconSize] — the hair it gives back is what the count in one corner and the caret in
            // the other need to stay off the drawing at this 32dp button.
            Icon(
                EditorTool.POLYGON.icon,
                contentDescription = "Tool: Polygon, ${sides} sides",
                tint = tint,
                modifier = Modifier.size(ToolIconSize * 0.9f),
            )
            // The figure itself is drawn from this number, so the slot says which one it is. It rides
            // the button's top-left edge, a hair inside the rim so the slot's rounded corner cannot
            // crop the digits, and clear of the drawing in the middle.
            Text(
                text = "$sides",
                color = tint,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 3.dp, top = 1.dp),
            )
            // The menu's caret takes the corner opposite the count, small and tight to the rim, so the
            // two marks frame the figure instead of competing with it for the middle.
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 1.dp, bottom = 0.dp)
                    .size(10.dp),
            )
        }
        ToolbarMenu(expanded = open, onDismissRequest = { open = false }) {
            ToolVariantPicker(
                heading = "Sides",
                labels = POLYGON_SIDE_CHOICES.map { "$it" },
                selectedIndex = polygonSideIndex(sides),
                onPick = { index ->
                    open = false
                    onPick(polygonSidesAt(index))
                },
            )
        }
    }
}
