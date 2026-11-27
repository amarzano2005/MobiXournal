package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.SplineElement
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.ElementEdits
import com.mobixournal.render.cancelSplineDraft
import com.mobixournal.render.confirmSplineDraft
import com.mobixournal.render.toggleSplineClosed
import com.mobixournal.render.toggleSplineDraftClosed
import com.mobixournal.render.undoSplineDraftNode

/**
 * Contextual mini action bar shown while drafting a new [SplineElement] or editing an existing one.
 */
@Composable
fun SplineDraftBarHost(
    surface: DrawingSurfaceView?,
    ui: EditorUiState,
    modifier: Modifier = Modifier,
) {
    var version by remember(surface) { mutableIntStateOf(0) }
    DisposableEffect(surface) {
        val draft = surface?.splineDraft
        draft?.onChanged = { version++ }
        onDispose {
            if (draft?.onChanged != null) draft.onChanged = null
        }
    }
    @Suppress("UNUSED_EXPRESSION")
    version
    val draft = surface?.splineDraft
    if (draft != null && draft.active) {
        SplineDraftCapsule(
            nodeCount = draft.nodes.size,
            closed = draft.closed,
            onUndo = { surface.undoSplineDraftNode() },
            onToggleClosed = { surface.toggleSplineDraftClosed() },
            onConfirm = { surface.confirmSplineDraft() },
            onCancel = { surface.cancelSplineDraft() },
            modifier = modifier,
        )
        return
    }
    val editing = ui.editingSpline
    if (editing != null && surface != null) {
        SplineEditCapsule(
            spline = editing,
            onToggleClosed = {
                surface.toggleSplineClosed(editing)?.let { ui.editingSpline = it }
            },
            onDone = { ui.editingSpline = null },
            modifier = modifier,
        )
    }
}

@Composable
private fun SplineDraftCapsule(
    nodeCount: Int,
    closed: Boolean,
    onUndo: () -> Unit,
    onToggleClosed: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "$nodeCount pts",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onUndo, enabled = nodeCount > 0) {
                Text("Undo")
            }
            TextButton(onClick = onToggleClosed, enabled = nodeCount >= 3) {
                Text(if (closed) "Open" else "Close")
            }
            FilledTonalButton(
                onClick = onConfirm,
                enabled = nodeCount >= 2,
                colors = ButtonDefaults.filledTonalButtonColors(),
            ) {
                Text("Confirm")
            }
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun SplineEditCapsule(
    spline: SplineElement,
    onToggleClosed: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Spline (${spline.nodes.size} pts)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onToggleClosed, enabled = spline.nodes.size >= 3) {
                Text(if (spline.closed) "Open curve" else "Close loop")
            }
            FilledTonalButton(onClick = onDone) {
                Text("Done")
            }
        }
    }
}
