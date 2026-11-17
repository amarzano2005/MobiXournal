package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mobixournal.render.OutlineRow
import com.mobixournal.ui.theme.PanelEdgeAir
import kotlinx.coroutines.CancellationException

private sealed interface BookmarkRowsState {
    data object Loading : BookmarkRowsState
    data object Failed : BookmarkRowsState
    data class Ready(val rows: List<OutlineRow>) : BookmarkRowsState
}

/** Shell appears immediately; archive reads and projection never run in a composition or on Main. */
@Composable
internal fun BookmarkRowsHost(
    pane: PaneState,
    session: ContentsSession,
    modifier: Modifier,
    content: @Composable (List<OutlineRow>) -> Unit,
) {
    // The key itself has reference equality: Document/List equality could inflate a lazy archive.
    val request = remember(pane.surface, pane.documentVersion, pane.outline) { Any() }
    val document = pane.surface?.doc
    val pdf = pane.outline
    var retry by remember(request) { mutableIntStateOf(0) }
    val cached = document?.let { session.bookmarkRows.cached(it.pages, it.outline, pdf) }
    val state = key(request, retry) {
        produceState<BookmarkRowsState>(cached?.let(BookmarkRowsState::Ready) ?: BookmarkRowsState.Loading) {
            try {
                value = BookmarkRowsState.Ready(document?.let {
                    session.bookmarkRows.load(it.pages, it.outline, pdf)
                }.orEmpty())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.w("BookmarkNavigation", "Unable to read bookmark destinations", failure)
                value = BookmarkRowsState.Failed
            }
        }.value
    }
    Column(modifier.fillMaxSize()) {
        when (val result = state) {
            is BookmarkRowsState.Ready -> content(result.rows)
            else -> Column(Modifier.padding(PanelEdgeAir), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result == BookmarkRowsState.Loading) {
                    CircularProgressIndicator()
                    Text("Reading bookmarks…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Bookmarks could not be read. Try again.", color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick = { retry++ }) { Text("Try again") }
                }
            }
        }
    }
}
