package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ToolGlyph
import com.mobixournal.design.ToolGlyphCatalogue
import com.mobixournal.render.GuideKind
import com.mobixournal.ui.theme.rememberChromeInk

/**
 * The guide chooser's faces, from the **shared** catalogue.
 *
 * They used to be two vectors built here, which meant the window's guides slot could only have drawn a
 * second setsquare of its own. [ToolGlyph.SETSQUARE] and [ToolGlyph.COMPASS] are the one art now, on the
 * 24-unit grid `glyphVector` builds from, so the tablet's menu and the PC's slot show one picture.
 */
val SetsquareIcon: ImageVector by lazy {
    glyphVector("Setsquare", ToolGlyphCatalogue.parts(ToolGlyph.SETSQUARE))
}

/** Outline glyph for the drafting compass, from the shared catalogue. */
val CompassIcon: ImageVector by lazy {
    glyphVector("Compass", ToolGlyphCatalogue.parts(ToolGlyph.COMPASS))
}

/** Outline glyph for the semicircular graduated protractor. */
val ProtractorIcon: ImageVector by lazy {
    ImageVector.Builder("Protractor", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3f, 17f)
            lineTo(21f, 17f)
            arcTo(9f, 9f, 0f, false, false, 3f, 17f)
            moveTo(12f, 17f)
            lineTo(12f, 13f)
            moveTo(12f, 17f)
            lineTo(16f, 13f)
        }
    }.build()
}

/** The icon shown on the toolbar button for [kind]. */
fun guideButtonIcon(kind: GuideKind): ImageVector = when (kind) {
    GuideKind.NONE -> Icons.Outlined.Straighten
    GuideKind.SETSQUARE -> SetsquareIcon
    GuideKind.COMPASS -> CompassIcon
    GuideKind.PROTRACTOR -> ProtractorIcon
    GuideKind.RULING -> Icons.Outlined.GridOn
    GuideKind.CHEMISTRY -> BenzeneIcon
}

/** The icon shown in the submenu list for [kind]. */
fun guideOptionIcon(kind: GuideKind): ImageVector = when (kind) {
    GuideKind.NONE -> Icons.Outlined.Close
    GuideKind.SETSQUARE -> SetsquareIcon
    GuideKind.COMPASS -> CompassIcon
    GuideKind.PROTRACTOR -> ProtractorIcon
    GuideKind.RULING -> Icons.Outlined.GridOn
    GuideKind.CHEMISTRY -> BenzeneIcon
}

/**
 * Single item inside the Drawing Guide popup menu.
 */
@Composable
private fun GuideOptionMenuItem(
    opt: GuideKind,
    label: String,
    desc: String,
    selectedKind: GuideKind,
    onKind: (GuideKind) -> Unit,
    dismiss: () -> Unit,
) {
    val isSelected = selectedKind == opt
    DropdownMenuItem(
        text = {
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingIcon = {
            Icon(
                guideOptionIcon(opt),
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        },
        trailingIcon = {
            if (isSelected) {
                Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        },
        onClick = {
            val next = if (opt == GuideKind.NONE || isSelected) GuideKind.NONE else opt
            onKind(next)
            dismiss()
        },
    )
}

/**
 * Menu content for the Drawing Guides pop-up, opened by [CompactGuidePopupButton] in the Secondary
 * Toolbar (the rail no longer offers a guides slot).
 * Categorized into Off, Geometric Instruments, and Page Snapping Lattices, with dedicated icons.
 */
@Composable
internal fun DrawingGuideMenuItems(
    selectedKind: GuideKind,
    onKind: (GuideKind) -> Unit,
    dismiss: () -> Unit,
) {
    MenuHeading("Drawing guide")

    GuideOptionMenuItem(
        opt = GuideKind.NONE,
        label = "Off",
        desc = "Disable active drawing guide",
        selectedKind = selectedKind,
        onKind = onKind,
        dismiss = dismiss,
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    MenuHeading("Instruments")

    val instruments = listOf(
        Triple(GuideKind.SETSQUARE, "Setsquare", "30° / 60° / 90° triangle with grab handles"),
        Triple(GuideKind.COMPASS, "Compass", "Circles and smooth circular arcs"),
        Triple(GuideKind.PROTRACTOR, "Protractor", "Graduated scale & 15° radial snapping"),
    )
    for ((opt, label, desc) in instruments) {
        GuideOptionMenuItem(opt, label, desc, selectedKind, onKind, dismiss)
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    MenuHeading("Lattices & Snapping")

    val lattices = listOf(
        Triple(GuideKind.RULING, "Page ruling", "Snap to the sheet's printed lines & grid"),
        Triple(GuideKind.CHEMISTRY, "Chemistry lattice", "60° / 120° bonds & hexagonal benzene rings"),
    )
    for ((opt, label, desc) in lattices) {
        GuideOptionMenuItem(opt, label, desc, selectedKind, onKind, dismiss)
    }
}

/**
 * Secondary top-bar compact button for drawing guides — the only entry point now that the rail's
 * writing-tools-only face dropped its guides slot.
 */
@Composable
internal fun CompactGuidePopupButton(
    kind: GuideKind,
    onKind: (GuideKind) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val active = kind != GuideKind.NONE
    val tint = if (active) MaterialTheme.colorScheme.primary else rememberChromeInk()
    val shape = RoundedCornerShape(8.dp)
    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(shape)
                .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                .clickable { open = !open },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                guideButtonIcon(kind),
                contentDescription = "Drawing guides",
                tint = tint,
                // The rail's glyph size, so the guides slot weighs the same as the figures beside it.
                modifier = Modifier.size(ToolIconSize),
            )
        }
        ToolbarMenu(expanded = open, onDismissRequest = { open = false }) {
            DrawingGuideMenuItems(selectedKind = kind, onKind = onKind, dismiss = { open = false })
        }
    }
}
