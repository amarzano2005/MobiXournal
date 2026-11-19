package com.mobixournal.desktop.shell
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.tools.DesktopIconButton
import com.mobixournal.desktop.tools.DesktopTextField
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.settings.glyph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import kotlin.math.roundToInt

/**
 * The open documents, as a strip inside the header band.
 *
 * The strip is the flat band's, not a row of glass chips (`docs/interface-contract.md` §1): a tab is 28 dp
 * tall on the 4 dp control corner, the document in front wears the panel tone with one hairline and a
 * **2 dp accent bar** under it, and a tab behind it is bare — it only answers the pointer with the band's
 * own hover step. A tab whose edits are not in the file yet carries a small accent dot beside its label,
 * and its close cross is always on screen; a resting tab reveals the cross under the pointer.
 *
 * **The tabs scroll, the `+` does not.** Long labels and many documents used to push the strip past the
 * band's edge, where the last tabs and the new-document button were clipped and unreachable: the list now
 * lives in a horizontal scroll that shares the band with the window, and the `+` stands outside it.
 *
 * A **double-click edits the label in place** through the window's own field, so the caret is the accent
 * one in both themes, and **dragging sideways reorders** — the dragged tab follows the pointer through a
 * live translation until it is dropped on its new place. Widths are measured per tab because that drag
 * needs to know how far one tab is.
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
    Row(modifier.height(TAB_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        // `fill = false`, so a strip of one tab keeps the `+` beside it instead of at the band's far end.
        Row(
            Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.all.forEachIndexed { index, tab ->
                TabItem(
                    tab = tab,
                    active = index == tabs.activeIndex,
                    dragOffset = if (dragFrom == index) dragX else 0f,
                    renaming = renamingKey == tab.key,
                    onMeasure = { widths[tab.key] = it },
                    onActivate = { onActivate(index) },
                    onRename = { renamingKey = tab.key },
                    onRenameDone = { renamingKey = null },
                    onClose = { onClose(index) },
                    onDragStart = { dragFrom = index; dragX = 0f },
                    onDrag = { dragX += it },
                    onDragEnd = {
                        if (dragFrom >= 0) {
                            val width = (widths[tab.key] ?: TAB_FALLBACK_PX).coerceAtLeast(1f)
                            onMove(dragFrom, dragFrom + (dragX / width).roundToInt())
                        }
                        dragFrom = -1
                        dragX = 0f
                    },
                    onDragCancel = { dragFrom = -1; dragX = 0f },
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        AddTabButton(onNew)
    }
}

/**
 * One tab: its label — or the field that renames it — the dirty dot and the close cross.
 *
 * The tab answers a **press** rather than a tap: it also offers a double-click (the rename), and
 * `detectTapGestures` would hold every single click for the double-tap timeout, which read as a ~300 ms
 * slow switch. The drag is a second pointer loop on the same box, so a sideways gesture reorders while a
 * click still opens the document.
 */
@Composable
private fun TabItem(
    tab: DesktopTab,
    active: Boolean,
    dragOffset: Float,
    renaming: Boolean,
    onMeasure: (Float) -> Unit,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onRenameDone: () -> Unit,
    onClose: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val pointer = remember { MutableInteractionSource() }
    val hovered by pointer.collectIsHoveredAsState()
    val shape = DesktopTheme.controlShape
    Box(
        Modifier
            // The live drag: the tab follows the pointer instead of standing still until it is dropped.
            .graphicsLayer { translationX = dragOffset }
            .onGloballyPositioned { onMeasure(it.size.width.toFloat()) }
            .padding(end = TAB_GAP)
            .height(TAB_HEIGHT)
            .widthIn(min = TAB_MIN_WIDTH, max = TAB_MAX_WIDTH)
            .clip(shape)
            .background(if (active) DesktopTheme.popupSurface else Color.Transparent, shape)
            .then(if (active) Modifier.border(1.dp, DesktopTheme.hairline, shape) else Modifier)
            .then(if (active) Modifier else Modifier.hoverFill(shape))
            .hoverable(pointer)
            .pointerInput(tab.key) {
                detectTapGestures(onPress = { onActivate() }, onDoubleTap = { onRename() })
            }
            .pointerInput(tab.key) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                ) { _, drag -> onDrag(drag.x) }
            },
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (renaming) {
                TabRenameField(tab, onDone = onRenameDone)
            } else {
                Text(
                    text = tab.title,
                    color = if (active) DesktopTheme.ink else DesktopTheme.inkSecondary,
                    style = DesktopTheme.tab,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (tab.isDirty) {
                // An **unsaved edit**: one accent dot beside the label, absent when the file holds
                // everything on the page. It is not a control, so it carries no state of its own.
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(DIRTY_DOT_DP.dp).background(DesktopTheme.accent, CircleShape))
            }
            Spacer(Modifier.weight(1f))
            // Always there on the document in front and on a tab with unsaved work; a resting tab offers
            // it only while the pointer is on it, so a row of tabs is not a row of crosses.
            if (active || tab.isDirty || hovered) CloseTabButton(onClose)
        }
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(ACTIVE_BAR_HEIGHT)
                    .background(DesktopTheme.accent),
            )
        }
    }
}

/** The label, edited in place: Enter commits, Escape abandons, an empty label falls back to the file. */
@Composable
private fun TabRenameField(tab: DesktopTab, onDone: () -> Unit) {
    var text by remember(tab) { mutableStateOf(tab.title) }
    val focus = remember { FocusRequester() }

    fun commit() {
        tab.title = text.trim().ifEmpty { tab.file.name }
        onDone()
    }

    DesktopTextField(
        value = text,
        onValueChange = { text = it },
        height = TAB_HEIGHT,
        modifier = Modifier
            .width(TAB_RENAME_WIDTH)
            // The field's own keys, ahead of the text box: Enter is the commit, never a newline.
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
        focusRequester = focus,
        onSubmit = { commit() },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** The tab's close cross: the catalogue's mark with its own tooltip and accelerator. */
@Composable
private fun CloseTabButton(onClose: () -> Unit) {
    DesktopIconButton(
        description = "Close tab",
        onClick = onClose,
        size = CLOSE_BUTTON,
        shortcut = "Ctrl+W",
    ) {
        Canvas(Modifier.size(12.dp)) {
            drawChromeGlyph(ChromeGlyph.CLOSE, DesktopTheme.inkSecondary)
        }
    }
}

/** The `+`: another document, the same command the header's menu and `Ctrl+N` invoke. */
@Composable
private fun AddTabButton(onNew: () -> Unit) {
    DesktopIconButton(
        description = "New notebook",
        onClick = onNew,
        size = 28.dp,
        shortcut = "Ctrl+N",
    ) {
        Canvas(Modifier.size(16.dp)) {
            drawChromeGlyph(ChromeGlyph.ADD, DesktopTheme.chromeInk)
        }
    }
}

/** A tab's own height: the flat band's control, not the capsule chip it used to wear. */
private val TAB_HEIGHT = 28.dp

/** How much a tab may shrink to, and how much of the band one long label may take. */
private val TAB_MIN_WIDTH = 100.dp
private val TAB_MAX_WIDTH = 180.dp

/** The field a rename happens in: one tab's width, minus the air its own controls need. */
private val TAB_RENAME_WIDTH = 120.dp

/** The air after a tab, so two tabs read as two objects and not as one split label. */
private val TAB_GAP = 4.dp

/** The close cross's own box: one step inside the tab it closes. */
private val CLOSE_BUTTON = 20.dp

/** The bar under the document in front: the strip's one mark of place. */
private val ACTIVE_BAR_HEIGHT = 2.dp

/** Fallback width of a tab before its first layout pass has been measured. */
private const val TAB_FALLBACK_PX = 140f

/** The dirty dot's diameter: the smallest mark the strip can show and still be seen at arm's length. */
private const val DIRTY_DOT_DP = 6
