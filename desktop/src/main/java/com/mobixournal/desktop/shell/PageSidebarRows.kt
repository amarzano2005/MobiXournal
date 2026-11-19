package com.mobixournal.desktop.shell

import com.mobixournal.desktop.navigation.PageDragHandle
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.sheet.PageSelection
import com.mobixournal.desktop.sheet.PageSelectionRules
import com.mobixournal.desktop.sheet.rememberSelectionTap
import com.mobixournal.desktop.shell.WindowKeys
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.ink.of

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.format.model.Layer
import kotlinx.coroutines.withContext

/**
 * The **rows of the page sidebar** — the page thumbnails, the row that adds one, and the current page's
 * layers — split out of `PageSidebar.kt` so that file stays the lane's arrangement rather than its row
 * internals (`SourceStructureTest` keeps both under the ceiling).
 *
 * Every row here is a **control** in the window's grammar: the 4 dp [DesktopTheme.controlShape] corner and
 * [com.mobixournal.desktop.tools.hoverFill]'s single hover background, so a page, the `+` under it and a
 * layer answer the pointer in one language. A row that lights nothing is a row the reader has to guess is
 * clickable; a row that lights a capsule is a second material beside the flat band it stands on.
 */

/**
 * The page thumbnails and the row that adds one: the Pages view's own list, pulled out so [PageSidebar] reads
 * as the view it chooses and the sections under it, not as eight parameters of a row.
 */
@Composable
internal fun ColumnScope.PageThumbnailList(
    tab: DesktopTab,
    listState: LazyListState,
    current: Int,
    selection: PageSelection,
    revision: Int,
    cache: MutableMap<Int, ImageBitmap>,
    drag: PageDragHandle,
    onSelectPage: (Int) -> Unit,
    onRemovePage: (Int) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onMovePages: (Set<Int>, Int) -> Unit,
    onInsertPage: (Int) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = tab.pageCount) { index ->
            PageRow(
                tab = tab,
                index = index,
                current = current,
                picked = index in selection.pages,
                revision = revision,
                cache = cache,
                drag = drag,
                onPick = { ctrl, shift ->
                    tab.pickedPages = PageSelectionRules.click(selection, index, ctrl, shift, tab.pageCount)
                    onSelectPage(index)
                },
                onRemovePage = onRemovePage,
                onMovePage = { from, to ->
                    val carried = PageSelectionRules.dragging(selection, from)
                    if (carried.size <= 1) onMovePage(from, to) else onMovePages(carried, to)
                },
            )
        }
        // The list's own `+`: a blank sheet where the pages end (`docs/desktop-workstation.md`).
        item { AddPageRow(onAdd = { onInsertPage(tab.pageCount) }) }
    }
}

/**
 * One row of the page list: its number, the page itself, and its own delete.
 *
 * The thumbnail is asked for with the row's hidden layers as part of its key: switching a layer off
 * repaints this page's thumbnail rather than leaving a preview that disagrees with the sheet beside it.
 * So is the shell's **deferred** revision, and deliberately not the document's edit count — an edit drops
 * the page's cached bitmap, and the next quiet moment re-renders that one page here (the other rows hit
 * the cache) instead of re-rendering it in the middle of a stroke.
 */
@Composable
private fun PageRow(
    tab: DesktopTab,
    index: Int,
    current: Int,
    picked: Boolean,
    revision: Int,
    cache: MutableMap<Int, ImageBitmap>,
    drag: PageDragHandle,
    onPick: (Boolean, Boolean) -> Unit,
    onRemovePage: (Int) -> Unit,
    onMovePage: (Int, Int) -> Unit,
) {
    val preview = rememberThumbnail(tab, index, tab.hiddenOn(index), revision, cache)
    Row(
        Modifier
            .then(pageRowSurface(tab, index, current, picked, drag, onMovePage))
            // The row is the target, padding included: a click on its air still picks the page, and the
            // keyboard decides whether it also extends the selection.
            .then(rememberSelectionTap(onPick))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageThumbnail(preview = preview, index = index, current = current, picked = picked)
        Spacer(Modifier.weight(1f))
        // The row's own delete, where the page is: the last page of a document is never removed
        // (`PageOps.removeAt`), so the glyph wears the muted ink and does nothing.
        PageRemoveGlyph(
            enabled = tab.pageCount > 1,
            label = "Remove page ${index + 1}",
            onRemove = { onRemovePage(index) },
        )
    }
}

/**
 * The row's own surface: its fill for each state, the landing line a drag draws, the pointer's step and the
 * rim of a picked page — kept out of [PageRow] so that function stays the row's content.
 *
 * Every corner here is the **control's** 4 dp ([DesktopTheme.controlShape]): a page row is a control in a
 * flat band, not a card of its own. The drag handler is remembered against the row's own callbacks through a
 * holder, because a pointer handler outlives the composition that built it.
 */
@Composable
private fun pageRowSurface(
    tab: DesktopTab,
    index: Int,
    current: Int,
    picked: Boolean,
    drag: PageDragHandle,
    onMovePage: (Int, Int) -> Unit,
): Modifier {
    val lifted = drag.from == index
    val move = rememberUpdatedState(onMovePage)
    // The row the lifted page would land on wears the accent line along its top edge: one mark, in the
    // chrome's own ink, so where the page will go is readable while the list scrolls under the pointer.
    val landing = drag.active && drag.target == index && drag.target != drag.from
    return Modifier
        .fillMaxWidth()
        // A drag on the thumbnail reorders; a wheel scroll still scrolls the list, which is how this
        // window is read on a PC.
        .pointerInput(index, tab.pageCount) {
            detectDragGestures(
                onDragStart = { offset -> drag.start(index, offset.y) },
                onDragEnd = { drag.drop()?.let { (from, to) -> move.value(from, to) } },
                onDragCancel = { drag.cancel() },
            ) { change, amount ->
                change.consume()
                drag.moveBy(amount.y)
            }
        }
        .background(
            when {
                lifted -> DesktopTheme.activeSlot
                picked -> DesktopTheme.accent.copy(alpha = 0.18f)
                index == current -> DesktopTheme.accent.copy(alpha = 0.10f)
                else -> Color.Transparent
            },
            DesktopTheme.controlShape,
        )
        .drawBehind {
            if (landing) {
                drawLine(
                    color = DesktopTheme.accent,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
        .hoverFill(DesktopTheme.controlShape, active = index == current || picked)
        .border(
            1.dp,
            when {
                picked -> DesktopTheme.accent
                index == current -> DesktopTheme.accent.copy(alpha = 0.55f)
                else -> Color.Transparent
            },
            DesktopTheme.controlShape,
        )
}

/** The row's number and its page: the picture, or the tone standing in for it until the lane paints one. */
@Composable
private fun PageThumbnail(preview: ImageBitmap?, index: Int, current: Int, picked: Boolean) {
    Text(
        text = "${index + 1}",
        color = if (picked || index == current) DesktopTheme.accent else DesktopTheme.inkMuted,
        style = DesktopTheme.numeric,
        modifier = Modifier.width(22.dp),
    )
    if (preview == null) {
        Box(Modifier.width(THUMB_WIDTH).height(72.dp).background(DesktopTheme.activeSlot))
    } else {
        val aspect = preview.height.toFloat() / preview.width.toFloat()
        Image(
            bitmap = preview,
            contentDescription = "Page ${index + 1}",
            modifier = Modifier.width(THUMB_WIDTH).height((THUMB_WIDTH.value * aspect).dp),
        )
    }
}

/**
 * A row's delete: the chrome's monoline close, on the row rather than in a menu.
 *
 * Muted until the row is hovered and inert on a one-page document (`enabled = false`), which is the
 * **disabled** state of `docs/interface-contract.md` §6 rather than a second, dimmer close glyph: an
 * unavailable action stays where the eye expects it and says so in tone.
 *
 * It is a real control ([clickable], role Button), not a tap gesture: the glyph is reachable with Tab, wears
 * the accent ring while it has the keyboard, and lights its own 4 dp square under the pointer instead of a
 * capsule the rest of the band does not have. Its box and mark are the inspector's close — the band's 28 dp
 * header command with the 14 dp cross — so the two lanes' × commands are one control, not two sizes.
 */
@Composable
private fun PageRemoveGlyph(enabled: Boolean, label: String, onRemove: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val owner = remember { Any() }
    val answer = rememberUpdatedState(onRemove)
    DisposableEffect(owner) { onDispose { WindowKeys.focusControl(owner, false) } }
    Box(
        Modifier
            .size(HEADER_CONTROL)
            .hoverFill(DesktopTheme.controlShape, focused = focused)
            .onFocusChanged {
                focused = it.isFocused
                WindowKeys.focusControl(owner, it.isFocused)
            }
            .clickable(enabled = enabled, role = Role.Button) { answer.value() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            drawChromeGlyph(ChromeGlyph.CLOSE, if (enabled) DesktopTheme.inkSecondary else DesktopTheme.inkMuted)
        }
    }
}

/**
 * The page list's **`+`**: one more blank sheet, after the last page.
 *
 * A row rather than a floating button: the list is the page's own surface, and a control that adds a page
 * belongs at the place the pages end — the same reason the tablet's page strip grows its last slot into a
 * `+` (`docs/desktop-workstation.md`). The glyph is the catalogue's `ADD`, the ink the chrome's.
 */
@Composable
private fun AddPageRow(onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .hoverFill(DesktopTheme.controlShape)
            .clickable { onAdd() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(14.dp)) {
            drawChromeGlyph(ChromeGlyph.ADD, DesktopTheme.inkSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Text("Add a page", color = DesktopTheme.inkSecondary, style = DesktopTheme.label)
    }
}

/** The current page's layers, topmost first — the order the eye reads a stack of ink in. */
@Composable
internal fun LayersSection(tab: DesktopTab) {
    val page = remember(tab, tab.page, tab.document.edits) {
        runCatching { tab.document.page(tab.page) }.getOrNull()
    }
    val layers = page?.layers.orEmpty()
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = "Layers",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.eyebrow,
        )
        Spacer(Modifier.height(4.dp))
        if (layers.isEmpty()) {
            Text("No layers", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        }
        for (index in layers.indices.reversed()) {
            val hidden = tab.isLayerHidden(tab.page, index)
            Row(
                Modifier
                    .fillMaxWidth()
                    .hoverFill(DesktopTheme.controlShape)
                    .clickable { tab.toggleLayer(tab.page, index) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LayerEye(hidden = hidden)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = layers[index].label(index),
                    // A hidden layer stays listed, in the muted ink: it is switched off, not gone.
                    color = if (hidden) DesktopTheme.inkMuted else DesktopTheme.ink,
                    style = DesktopTheme.label,
                )
            }
        }
    }
}

/**
 * The eye beside a layer's name: open when the layer is painted, struck through when it is not.
 *
 * Monoline like every other glyph in the window, and drawn rather than shipped as an icon: the chrome
 * has one stroke weight and one ink, and an image would be a second material in a 16 dp square.
 */
@Composable
private fun LayerEye(hidden: Boolean) {
    val ink = if (hidden) DesktopTheme.inkMuted else DesktopTheme.ink
    Canvas(Modifier.size(16.dp)) {
        // The phone's own eye, from the catalogue: the window used to draw a lid, a pupil and a slash,
        // and the two shells' layers list wore two different eyes for one state.
        drawChromeGlyph(if (hidden) ChromeGlyph.VISIBILITY_OFF else ChromeGlyph.VISIBILITY, ink)
    }
}

/**
 * One row's thumbnail, painted **off the UI thread** and handed over when it is ready.
 *
 * A thumbnail is a full software paint of the page — 29–39 ms on a dense sheet, measured — and it used to
 * run inside composition, so every quiet moment in a burst of writing or erasing paid for one on the frame
 * the window was drawing. The render now runs on [THUMBNAIL_PAINT], one at a time, and only the finished
 * bitmap reaches the UI thread; the row keeps the picture it already had until then, so a refresh never
 * blanks the sidebar.
 *
 * The slot is keyed on the **page**, not on [revision]: that is what keeps the old picture up across a
 * refresh. The effect re-runs on every key change (the hidden layers, the shell's deferred revision) and
 * only publishes a bitmap it actually got.
 */
@Composable
private fun rememberThumbnail(
    tab: DesktopTab,
    index: Int,
    hidden: Set<Int>,
    revision: Int,
    cache: MutableMap<Int, ImageBitmap>,
): ImageBitmap? {
    // A page already painted is **drawn in this frame**: the row used to start empty and wait for the
    // paint lane to hand back a bitmap it already had, so switching to a document whose thumbnails were
    // warm showed an empty list until the lane had gone round every row.
    val painted = if (hidden.isEmpty()) cache[index] else null
    val slot = remember(tab, index) { mutableStateOf(painted) }
    LaunchedEffect(tab, index, hidden, revision) {
        val painted = withContext(THUMBNAIL_PAINT) { thumbnail(tab, index, THUMB_WIDTH.value, cache) }
        if (painted != null) slot.value = painted
    }
    return slot.value
}
