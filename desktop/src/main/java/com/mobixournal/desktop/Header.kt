package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.mobixournal.design.PenPalette
import com.mobixournal.format.model.Element
import java.io.File

/**
 * The window's **header**: the sidebar toggle, the tab strip and the tool-placement chooser.
 *
 * Split out of the shell because it is the band's own layout and its two controls, and the shell is the
 * state and the commands (`SourceStructureTest` keeps both files inside the budget). The glyphs are two or
 * three monoline strokes on a capsule of the header's own glass — the tablet's icon buttons beside the dock,
 * at the smaller desktop size — and the placement control writes the same settings entry the window's own
 * Interface section will, so there is one setting with two doors.
 */
@Composable
internal fun Header(
    tabs: DesktopTabs,
    sidebarVisible: Boolean,
    placement: ToolPlacement,
    onActivate: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onPageBackground: () -> Unit,
    onPageFormat: () -> Unit,
    onToggleSidebar: () -> Unit,
    onPlacement: (ToolPlacement) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(DesktopTheme.headerHeight)
            .bandSurface()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderMenu(
            sidebarVisible = sidebarVisible,
            canSave = tabs.active?.isDirty == true,
            onNew = onNew,
            onOpen = onOpen,
            onSave = onSave,
            onSaveAs = onSaveAs,
            onPageBackground = onPageBackground,
            onPageFormat = onPageFormat,
            onToggleSidebar = onToggleSidebar,
        )
        Spacer(Modifier.width(10.dp))
        TabStrip(
            tabs = tabs,
            onActivate = onActivate,
            onClose = onClose,
            onMove = onMove,
            onNew = onNew,
            modifier = Modifier.weight(1f),
        )
        // Air between the last tab and the header's own control: the strip and the placement button were
        // almost touching, which read as one crowded row rather than two objects on the band. The band is
        // only 40 dp tall, so the separation has to be sideways — a wider gap here is the whole breathing
        // room this row can get without growing the header.
        Spacer(Modifier.width(28.dp))
        PlacementButton(placement = placement, onPlacement = onPlacement)
    }
}

/**
 * The placement chooser: which of the two tool strips the window shows (Rail, Ribbon, Both).
 *
 * It is the one control of the Interface section the future settings window will own
 * (`desktop-settings-and-status-ui`), so until that window exists it stands in the header — and it
 * writes the same [DesktopSettings] entry the window will edit, so there is one setting with two doors
 * rather than two settings that can disagree.
 */
@Composable
internal fun PlacementButton(placement: ToolPlacement, onPlacement: (ToolPlacement) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box {
        HeaderGlyph(active = open, onClick = { open = !open }) { color, stroke ->
            // Three rows of different lengths: a rail, a ribbon, and both at once.
            drawLine(color, Offset(0f, 3f), Offset(size.width, 3f), stroke)
            drawLine(color, Offset(size.width * 0.45f, size.height / 2f), Offset(size.width, size.height / 2f), stroke)
            drawLine(color, Offset(0f, size.height - 3f), Offset(size.width, size.height - 3f), stroke)
        }
        if (open) {
            Popup(
                offset = with(density) { IntOffset(0, (DesktopTheme.headerHeight + 6.dp).roundToPx()) },
                onDismissRequest = { open = false },
            ) {
                Column(
                    Modifier
                        .glassSurface()
                        .width(PLACEMENT_POPUP_WIDTH)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Tool placement", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
                    Spacer(Modifier.height(4.dp))
                    for (option in ToolPlacement.entries) {
                        // Rows wrap the popup, never the window: `fillMaxWidth` inside a Popup is measured
                        // against the window, which is what made this menu a full-width sheet.
                        val picked = option == placement
                        Row(
                            Modifier
                                .width(PLACEMENT_POPUP_WIDTH - 16.dp)
                                .heightIn(min = 32.dp)
                                .background(
                                    if (picked) DesktopTheme.activeSlot else Color.Transparent,
                                    DesktopTheme.capsuleShape,
                                )
                                .hoverFill(DesktopTheme.capsuleShape, active = picked)
                                .pointerInput(option) {
                                    detectTapGestures { onPlacement(option); open = false }
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                option.label,
                                color = if (picked) DesktopTheme.ink else DesktopTheme.inkSecondary,
                                style = DesktopTheme.body,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A header control: two or three monoline strokes on a **capsule of the header's own glass** — the
 * tablet's icon buttons beside the dock, at the smaller desktop size.
 *
 * It used to be a bare 26 dp box that only appeared when active: a glyph floating on the band with
 * nothing around it. The capsule is the family's smallest surface — the same fill as the band, the
 * family's rim and a 4 dp lift — so a control reads as an object lying on the header instead of as a
 * symbol printed on it (`docs/ui-modernization.md`).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HeaderGlyph(
    active: Boolean,
    onClick: () -> Unit,
    draw: DrawScope.(Color, Float) -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val lit = active || hovered
    Box(
        Modifier
            .size(DesktopTheme.headerControlSize)
            .glassCapsule(
                cornerRadius = DesktopTheme.headerControlSize / 2,
                fill = if (lit) DesktopTheme.activeSlot else DesktopTheme.glass,
                shadow = DesktopTheme.bandShadow,
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .pointerInput(active) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.width(14.dp).height(14.dp)) {
            // The chrome ink, lit or not: on the tablet a header glyph is white and only its capsule
            // changes tone when it is active.
            draw(DesktopTheme.chromeInk, DesktopTheme.glyphStroke.toPx())
        }
    }
}

/** The placement menu's own width: a small menu beside its button, not a sheet across the window. */
private val PLACEMENT_POPUP_WIDTH = 200.dp
