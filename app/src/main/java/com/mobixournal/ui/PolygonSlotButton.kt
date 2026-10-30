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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobixournal.ui.theme.rememberChromeInk

/**
 * The **Polygons slot**, whose menu is the polygon's side count.
 *
 * The family is one tool with a number, not a button per polygon, so this slot follows the figures that
 * carry a variant ([CompactShapeKindButton] in `EditorRegions.kt`) rather than the group slots: tapping
 * it takes the tool, tapping it again — or a long press — opens the 3–12 picker, and the number that
 * will be drawn sits in the button's **top-left corner**, clear of the figure, so the count reads as a
 * label on the slot rather than as part of the drawing. Picking a count also takes hold of the tool,
 * because seven sides is always about drawing a heptagon next.
 *
 * The count is the one value Settings ▸ Figures writes too (`AppSettings.polygonSides`), so the two
 * surfaces cannot disagree; [POLYGON_SIDE_CHOICES] keeps both on the builder's own range.
 */
/**
 * The polygon glyph's size inside the slot. A little smaller than [ToolIconSize] so the button's
 * top-left corner is free for the side count and the menu caret, without shrinking the figure to a
 * mark that no longer says "polygon".
 */
private val PolygonSlotGlyphSize: Dp = ToolIconSize * 0.9f

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
            Icon(
                EditorTool.POLYGON.icon,
                contentDescription = "Tool: Polygon, ${sides} sides",
                tint = tint,
                // The number owns the button's top-left corner, so the figure keeps to the opposite one
                // instead of being drawn under the digits: the count is a label on the slot, not part of
                // the drawing. At this size it still reads as a polygon at a glance.
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 2.dp, bottom = 2.dp)
                    .size(PolygonSlotGlyphSize),
            )
            // The figure itself is drawn from this number, so the slot says which one it is.
            Text(
                text = "$sides",
                color = tint,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 1.dp, top = 0.dp),
            )
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = tint,
                // The menu's caret takes the corner the number left free, so nothing sits on the figure.
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 1.dp, top = 1.dp)
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
