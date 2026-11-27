package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.SplineElement
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.addSplineMidpointNode
import com.mobixournal.render.cancelSplineDraft
import com.mobixournal.render.confirmSplineDraft
import com.mobixournal.render.removeSplineNode
import com.mobixournal.render.toggleSplineClosed
import com.mobixournal.render.toggleSplineDraftClosed
import com.mobixournal.render.toggleSplineNodeSmooth
import com.mobixournal.render.undoSplineDraftNode
import com.mobixournal.ui.theme.FloatingSurface

/**
 * Compact glass pill matching [SelectionActionBar] for spline drafting and live spline node editing.
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
        SplineDraftPill(
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
        SplineEditPill(
            spline = editing,
            onAddNode = { surface.addSplineMidpointNode(editing)?.let { ui.editingSpline = it } },
            onToggleSmooth = { surface.toggleSplineNodeSmooth(editing)?.let { ui.editingSpline = it } },
            onRemoveNode = { surface.removeSplineNode(editing)?.let { ui.editingSpline = it } },
            onToggleClosed = { surface.toggleSplineClosed(editing)?.let { ui.editingSpline = it } },
            onDone = { ui.editingSpline = null },
            modifier = modifier,
        )
    }
}

@Composable
private fun SplineDraftPill(
    nodeCount: Int,
    closed: Boolean,
    onUndo: () -> Unit,
    onToggleClosed: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingSurface(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SplinePillIcon(
                description = "Undo last knot",
                icon = Icons.AutoMirrored.Outlined.Undo,
                enabled = nodeCount > 0,
                onClick = onUndo,
            )
            SplinePillIcon(
                description = if (closed) "Open curve" else "Close loop",
                icon = Icons.Outlined.Loop,
                enabled = nodeCount >= 3,
                accented = closed,
                onClick = onToggleClosed,
            )
            SplinePillIcon(
                description = "Cancel spline",
                icon = Icons.Outlined.Close,
                onClick = onCancel,
            )
            SplinePillIcon(
                description = "Confirm spline",
                icon = Icons.Outlined.Check,
                enabled = nodeCount >= 2,
                accented = nodeCount >= 2,
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun SplineEditPill(
    spline: SplineElement,
    onAddNode: () -> Unit,
    onToggleSmooth: () -> Unit,
    onRemoveNode: () -> Unit,
    onToggleClosed: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingSurface(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SplinePillIcon(
                description = "Add knot",
                icon = Icons.Outlined.Add,
                onClick = onAddNode,
            )
            SplinePillIcon(
                description = "Toggle smooth or sharp corner",
                icon = Icons.Outlined.Timeline,
                onClick = onToggleSmooth,
            )
            SplinePillIcon(
                description = "Remove selected knot",
                icon = Icons.Outlined.DeleteOutline,
                enabled = spline.nodes.size > 2,
                onClick = onRemoveNode,
            )
            SplinePillIcon(
                description = if (spline.closed) "Open curve" else "Close loop",
                icon = Icons.Outlined.Loop,
                enabled = spline.nodes.size >= 3 || spline.closed,
                accented = spline.closed,
                onClick = onToggleClosed,
            )
            SplinePillIcon(
                description = "Done editing spline",
                icon = Icons.Outlined.Check,
                accented = true,
                onClick = onDone,
            )
        }
    }
}

@Composable
private fun SplinePillIcon(
    description: String,
    icon: ImageVector,
    enabled: Boolean = true,
    accented: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        accented -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}
