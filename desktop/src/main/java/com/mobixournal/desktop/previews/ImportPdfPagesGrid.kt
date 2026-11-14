package com.mobixournal.desktop.previews

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.io.ImportPdfRequest
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.shell.WindowKeys
import com.mobixournal.desktop.tools.PanelButton
import com.mobixournal.render.ImportPageSelection

/** The height the grid is given: four rows of previews, then it scrolls on its own. */
private val GRID_HEIGHT = 232.dp

/**
 * **The pages of the file, as previews you can pick** — the window's half of the tablet's grid
 * ([com.mobixournal.ui.ImportPagesGrid]).
 *
 * The typed selection and this grid are two views of one answer, so a tap rewrites the field
 * (`2,4-6,9`) and a typed run lights up the previews. Only the cells that exist are asked for
 * ([DesktopPdfThumbnails] holds sixty), so a long scan is a scroll and a handful of rasters; the cache is
 * released with the card.
 *
 * **A preview is a checkbox.** `toggleable` gives it the role, the checked state and Space/Enter, and a
 * focused cell draws the accent on its own rim — the window's ring, the same one the two answers draw — so a
 * keyboard can pick pages 2, 4 and 7 without touching the pointer.
 */
@Composable
internal fun ImportPdfPagesGrid(
    request: ImportPdfRequest,
    onEdit: ((ImportPdfRequest) -> ImportPdfRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cache = remember(request.file) { DesktopPdfThumbnails(request.file) }
    DisposableEffect(cache) { onDispose { cache.close() } }
    val chosen = request.chosen.orEmpty().toSet()
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(countLabel(chosen.size), color = DesktopTheme.inkMuted, style = DesktopTheme.label)
            Spacer(Modifier.weight(1f))
            PanelButton(
                label = "Select all",
                primary = false,
                enabled = true,
                onClick = { onEdit { it.copy(pages = ImportPageSelection.text(ImportPageSelection.all(request.pageCount))) } },
            )
            Spacer(Modifier.width(6.dp))
            PanelButton(
                label = "Clear",
                primary = false,
                enabled = true,
                onClick = { onEdit { it.copy(pages = "") } },
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 60.dp),
            modifier = Modifier.fillMaxWidth().height(GRID_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(request.pageCount) { index ->
                PageCell(
                    page = index,
                    selected = index in chosen,
                    image = cache[index],
                    onToggle = { onEdit { it.copy(pages = ImportPageSelection.text(ImportPageSelection.toggle(request.chosen.orEmpty(), index))) } },
                    onShown = { cache.request(index) },
                )
            }
        }
    }
}

/** One page: its preview once the raster lands, its number always, and the ring while it holds focus. */
@Composable
private fun PageCell(
    page: Int,
    selected: Boolean,
    image: ImageBitmap?,
    onToggle: () -> Unit,
    onShown: () -> Unit,
) {
    LaunchedEffect(page) { onShown() }
    var focused by remember { mutableStateOf(false) }
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { WindowKeys.focusControl(owner, false) } }
    val shape = DesktopTheme.railSlotShape
    Box(
        Modifier
            .aspectRatio(0.72f)
            .clip(shape)
            .background(if (selected) DesktopTheme.activeSlot else DesktopTheme.panelField)
            .border(
                width = if (selected || focused) 2.dp else 1.dp,
                color = when {
                    focused -> DesktopTheme.accent
                    selected -> DesktopTheme.accent
                    else -> DesktopTheme.hairline
                },
                shape = shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                WindowKeys.focusControl(owner, it.isFocused)
            }
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = pageLabel(page, selected) },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(3.dp),
            )
        }
        Text(
            "${page + 1}",
            color = if (selected) DesktopTheme.accent else DesktopTheme.inkMuted,
            style = DesktopTheme.label,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
        )
    }
}

/** "Page 3" / "Page 3, selected" — the one thing a preview says out loud. */
private fun pageLabel(page: Int, selected: Boolean): String =
    if (selected) "Page ${page + 1}, selected" else "Page ${page + 1}"

/** "3 pages picked" — the grid's own count, above it. */
private fun countLabel(count: Int): String = if (count == 1) "1 page picked" else "$count pages picked"
