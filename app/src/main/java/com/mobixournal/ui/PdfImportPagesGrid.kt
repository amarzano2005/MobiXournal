package com.mobixournal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mobixournal.render.ImportPageSelection

/** How wide a preview is rasterised, in px — a cell's own width at 2× on the tablet's density. */
private const val THUMBNAIL_WIDTH_PX = 240

/** The height the screened grid is given: four rows of previews, then it scrolls on its own. */
private val GRID_HEIGHT = 232.dp

/**
 * **The pages of the file, as previews you can pick.** The typed selection and this grid are two views of
 * one answer: tapping a preview rewrites the field (`2,4-6,9`), and typing in the field lights up the
 * previews — the request is the only place the answer lives.
 *
 * Only the cells that exist are asked for, so a 500-page scan is a scroll and a handful of rasters rather
 * than 500 renders ([PdfThumbnailCache] holds at most [PDF_THUMBNAILS_KEPT]); the cache is the card's and is
 * released with it, which is what makes cancelling an import leave nothing behind.
 *
 * **A preview is a checkbox, not a picture with a border.** `toggleable` gives the cell the checkbox's role,
 * its checked state and its Space/Enter answers, so a screen reader says *Page 3, selected* and a keyboard
 * takes a page exactly as a finger does. The card's *Select all* and *Clear* are the same answer written the
 * short way, and they never lie: both go through [ImportPageSelection], the rule the window's grid shares.
 */
@Composable
internal fun ImportPagesGrid(
    prompt: PdfImportPrompt,
    onPages: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cache = remember(prompt.file) { PdfThumbnailCache(prompt.file, THUMBNAIL_WIDTH_PX) }
    DisposableEffect(cache) { onDispose { cache.close() } }
    val chosen = prompt.chosen.orEmpty().toSet()
    Column(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                countLabel(chosen.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onPages(ImportPageSelection.text(ImportPageSelection.all(prompt.pageCount))) }) {
                Text("Select all")
            }
            TextButton(onClick = { onPages("") }) { Text("Clear") }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 68.dp),
            modifier = Modifier.fillMaxWidth().height(GRID_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(prompt.pageCount) { index ->
                PageThumbnail(
                    page = index,
                    selected = index in chosen,
                    image = cache[index],
                    onToggle = {
                        onPages(ImportPageSelection.text(ImportPageSelection.toggle(prompt.chosen.orEmpty(), index)))
                    },
                    onShown = { cache.request(index) },
                )
            }
        }
    }
}

/**
 * One page: its preview once the raster lands, its number always. The number is what makes the grid
 * readable *while* the previews are still coming, which on a long file is the difference between "something
 * is happening" and a wall of grey.
 */
@Composable
private fun PageThumbnail(
    page: Int,
    selected: Boolean,
    image: ImageBitmap?,
    onToggle: () -> Unit,
    onShown: () -> Unit,
) {
    LaunchedEffect(page) { onShown() }
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .aspectRatio(0.72f)
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else scheme.surfaceVariant)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant,
                shape = shape,
            )
            // The preview is decoration; the cell's name is the sentence, and its checked state is the
            // selection — a screen reader reads "Page 3, selected" instead of a number and a picture.
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
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
        )
    }
}

/** "Page 3" / "Page 3, selected" — the one thing a preview says out loud. */
private fun pageLabel(page: Int, selected: Boolean): String =
    if (selected) "Page ${page + 1}, selected" else "Page ${page + 1}"

/** "3 of 108 pages picked" — the grid's own count, above it. */
private fun countLabel(count: Int): String {
    val pages = if (count == 1) "1 page" else "$count pages"
    return "$pages picked"
}
