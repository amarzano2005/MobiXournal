package com.mobixournal.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.FloatingDialog
import com.mobixournal.ui.theme.rememberChromeInk

/** One-shot persistence tracking whether the user has seen the Focus mode guide dialog. */
internal object FocusModeGuidePrefs {
    private const val PREFS_NAME = "mobixournal_prefs"
    private const val KEY_GUIDE_SEEN = "focus_mode_guide_seen"

    fun isSeen(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_GUIDE_SEEN, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GUIDE_SEEN, true)
            .apply()
    }
}

/**
 * First-launch Liquid Glass guide dialog shown automatically on the user's first entry into
 * Focus mode (or reopened on demand from [FocusBarCustomizeDialog]).
 */
@Composable
internal fun FocusModeGuideDialog(
    onDismiss: () -> Unit,
    onCustomize: () -> Unit,
) {
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Focus mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FocusGuideRow(
                    icon = Icons.Outlined.DragIndicator,
                    title = "Move & dock the bar",
                    body = "Drag the handle anywhere on screen. Push it to the left or right edge " +
                        "to switch between horizontal and vertical layout.",
                )
                FocusGuideRow(
                    icon = Icons.Outlined.Tune,
                    title = "Customize tools",
                    body = "Press and hold the drag handle to swap, add, remove, or reorder tools " +
                        "(3–10 slots) without leaving Focus mode.",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
        dismissButton = {
            TextButton(onClick = onCustomize) { Text("Customize bar") }
        },
    )
}

@Composable
private fun FocusGuideRow(
    icon: ImageVector,
    title: String,
    body: String,
) {
    val badgeShape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(badgeShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = rememberChromeInk(),
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
