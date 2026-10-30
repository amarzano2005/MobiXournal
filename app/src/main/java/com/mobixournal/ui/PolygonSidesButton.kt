package com.mobixournal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.mobixournal.ui.theme.rememberChromeInk

/**
 * The Polygons slot's **side count**, drawn beside the slot: what the generic polygon will draw, and
 * one tap away from another answer. It is a control of its own rather than a second section of the
 * slot's menu, because the slot's menu already picks a *member* (polygon, pentagon, hexagon) and one
 * popup with two kinds of choice in it reads as neither.
 *
 * Picking a count also takes hold of the polygon: seven sides is always about drawing a heptagon next,
 * so the picker does not stop at storing the number. The pentagon and hexagon presets keep their own
 * fixed counts and ignore this one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PolygonSidesButton(
    active: Boolean,
    sides: Int,
    onPick: (Int) -> Unit,
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
                .clickable(onClickLabel = "Choose how many sides the polygon draws") { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$sides",
                color = tint,
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 1.dp, bottom = 1.dp)
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
