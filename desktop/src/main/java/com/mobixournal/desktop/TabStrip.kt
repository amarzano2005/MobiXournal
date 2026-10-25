package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * The open documents, as a strip under the header.
 *
 * Each tab shows its document's label and a monoline close glyph; the active one is the only one drawn
 * as a raised surface, so "which document am I in" is answered by one thing instead of by a border, a
 * colour and a shadow at once. A tab whose edits are not in the file yet carries a small accent dot after
 * the label — the strip's only status readout, and the reason closing it asks before it goes. A **double-click edits the label in place** — a tab label is a label,
 * not the file's name — and **dragging sideways reorders** the strip. The `+` at the end is the same
 * open action the header button and `Ctrl+T` invoke.
 *
 * Tab widths are measured per tab, because drag-to-reorder needs to know how far one tab is: the
 * distance dragged divided by that width is how many places to move.
 */
@Composable
fun TabStrip(
    tabs: DesktopTabs,
    onActivate: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val widths = remember { mutableMapOf<String, Float>() }
    var renamingKey by remember { mutableStateOf<String?>(null) }
    var dragFrom by remember { mutableStateOf(-1) }
    var dragX by remember { mutableStateOf(0f) }

    // A little air at the strip's own end, so the last tab does not sit flush against the band's edge.
    Row(
        modifier.height(36.dp).padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.all.forEachIndexed { index, tab ->
            val active = index == tabs.activeIndex
            val width = widths[tab.key] ?: TAB_FALLBACK_PX

            Row(
                Modifier
                    .onGloballyPositioned { widths[tab.key] = it.size.width.toFloat() }
                    .padding(end = 6.dp)
                    .height(30.dp)
                    .widthIn(min = 120.dp, max = 240.dp)
                    .background(if (active) DesktopTheme.activeSlot else Color.Transparent, DesktopTheme.capsuleShape)
                    .hoverFill(DesktopTheme.capsuleShape, active = active)
                    // A tab is an object lying in the band's space, so the active one wears the family's
                    // rim (a band takes none): that is what turns the flat steel strip into a capsule.
                    .then(if (active) Modifier.glassRim(DesktopTheme.capsuleRadius) else Modifier)
                    .pointerInput(tab.key) {
                        detectTapGestures(
                            onTap = { onActivate(index) },
                            onDoubleTap = { renamingKey = tab.key },
                        )
                    }
                    .pointerInput(tab.key, tabs.size) {
                        // A drag that lands past the end is clamped by `DesktopTabs.move`, so letting
                        // go slightly outside the strip must not drop the tab.
                        detectDragGestures(
                            onDragStart = { dragFrom = index; dragX = 0f },
                            onDragEnd = {
                                if (dragFrom >= 0) {
                                    val steps = (dragX / width.coerceAtLeast(1f)).roundToInt()
                                    onMove(dragFrom, dragFrom + steps)
                                }
                                dragFrom = -1
                                dragX = 0f
                            },
                            onDragCancel = { dragFrom = -1; dragX = 0f },
                        ) { _, drag -> dragX += drag.x }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(12.dp))
                if (renamingKey == tab.key) {
                    RenamingField(tab, onDone = { renamingKey = null })
                } else {
                    androidx.compose.material3.Text(
                        text = tab.title,
                        color = if (active) DesktopTheme.ink else DesktopTheme.inkSecondary,
                        fontFamily = DesktopTheme.inter,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 12.sp,
                        letterSpacing = if (active) 0.2.sp else 0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                }
                if (tab.isDirty) {
                    // An **unsaved edit**, as the family's one status readout on the strip: a single
                    // accent dot beside the label, absent when the file holds everything on the page.
                    // It is not a control — the clickable thing beside it is the close glyph — so it
                    // carries no hover, focus or disabled state (`docs/interface-contract.md` §6).
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(DIRTY_DOT_DP.dp).background(DesktopTheme.accent, CircleShape))
                }
                Spacer(Modifier.weight(1f))
                CloseGlyph(onClose = { onClose(index) })
                Spacer(Modifier.width(6.dp))
            }
        }

        AddTabButton(onNew)
        Spacer(Modifier.width(4.dp))
    }
}

/** The label, edited in place: Enter commits, Escape abandons, an empty label falls back to the file. */
@Composable
private fun RenamingField(tab: DesktopTab, onDone: () -> Unit) {
    var text by remember(tab) { mutableStateOf(tab.title) }
    val focus = remember { FocusRequester() }

    fun commit() {
        tab.title = text.trim().ifEmpty { tab.file.name }
        onDone()
    }

    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = TextStyle(color = DesktopTheme.ink, fontSize = 12.sp, fontFamily = DesktopTheme.inter),
        modifier = Modifier
            .width(140.dp)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> {
                        commit()
                        true
                    }
                    Key.Escape -> {
                        onDone()
                        true
                    }
                    else -> false
                }
            },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** A close glyph drawn as two monoline strokes — no filled Material icon in this chrome. */
@Composable
private fun CloseGlyph(onClose: () -> Unit) {
    Box(
        Modifier
            .width(22.dp)
            .height(22.dp)
            .hoverFill(DesktopTheme.capsuleShape)
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.width(9.dp).height(9.dp)) {
            val stroke = DesktopTheme.glyphStroke.toPx()
            drawLine(DesktopTheme.inkSecondary, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), stroke)
            drawLine(DesktopTheme.inkSecondary, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), stroke)
        }
    }
}

/** A monoline `+`: a new page at the end of the strip, the tablet's New command. */
@Composable
private fun AddTabButton(onNew: () -> Unit) {
    Box(
        Modifier
            .width(28.dp)
            .height(28.dp)
            .background(Color.Transparent, DesktopTheme.capsuleShape)
            .hoverFill(DesktopTheme.capsuleShape)
            .border(1.dp, DesktopTheme.hairline, DesktopTheme.capsuleShape)
            .pointerInput(Unit) { detectTapGestures { onNew() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.width(9.dp).height(9.dp)) {
            val stroke = DesktopTheme.glyphStroke.toPx()
            drawLine(DesktopTheme.inkSecondary, androidx.compose.ui.geometry.Offset(0f, size.height / 2f), androidx.compose.ui.geometry.Offset(size.width, size.height / 2f), stroke)
            drawLine(DesktopTheme.inkSecondary, androidx.compose.ui.geometry.Offset(size.width / 2f, 0f), androidx.compose.ui.geometry.Offset(size.width / 2f, size.height), stroke)
        }
    }
}

/** Fallback width of a tab before its first layout pass has been measured. */
private const val TAB_FALLBACK_PX = 140f

/** The dirty dot's diameter: the smallest mark the strip can show and still be seen at arm's length. */
private const val DIRTY_DOT_DP = 6
