package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import java.util.Locale

@Composable
internal fun ShortcutFilterBar(
    selected: ShortcutFilterTab,
    boundCount: Int,
    onSelect: (ShortcutFilterTab) -> Unit,
) {
    val dark = chromeIsDark()
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShortcutFilterTab.entries.forEach { tab ->
            val active = tab == selected
            val label = if (tab == ShortcutFilterTab.BOUND) "${tab.label} ($boundCount)" else tab.label
            val fill = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface
            val border = if (active) MaterialTheme.colorScheme.primary else ModernTheme.hairlineBorder(dark)
            val ink = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(fill)
                    .border(1.dp, border, shape)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = ink,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun DuplicateKeyBanner(duplicateKeys: Set<String>) {
    val joined = duplicateKeys.sorted().joinToString(", ")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = "Duplicate keys ($joined): toggles take precedence over colors and tools.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
internal fun ShortcutEmptyState(query: String, onlyBound: Boolean) {
    val message = when {
        query.isNotBlank() -> "No shortcuts match “$query”."
        onlyBound -> "No shortcuts bound yet. Switch to All to assign keys."
        else -> "No shortcuts available."
    }
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun ResetShortcutsGroup(
    confirming: Boolean,
    onAskReset: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    SettingsGroup("Reset") {
        if (!confirming) {
            OutlinedButton(
                onClick = onAskReset,
                modifier = Modifier.settingsActionPill(top = 12.dp, bottom = 12.dp),
            ) {
                Text("Reset shortcuts to defaults")
            }
        } else {
            SettingsNote("Restores toggle keys (B, H, S, F) to defaults and clears all tool and color keys.")
            Row(
                modifier = Modifier.settingsActionPill(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Confirm reset")
                }
            }
        }
    }
}

@Composable
internal fun ShortcutKeyRow(
    label: String,
    value: String,
    duplicateKeys: Set<String>,
    icon: ImageVector? = null,
    swatch: Int? = null,
    onValueChange: (String) -> Unit,
) {
    var detecting by remember { mutableStateOf(false) }
    val normalized = value.trim().uppercase(Locale.US)
    val isDuplicate = normalized.isNotEmpty() && normalized in duplicateKeys
    val reach = LocalSettingsCardInset.current

    Row(
        modifier = Modifier
            .fullBleedRow(reach)
            .fillMaxWidth()
            .clickable { detecting = true }
            .padding(horizontal = reach, vertical = settingsCellInset(reach)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatch != null) {
            ShortcutColorDot(swatch)
            Spacer(Modifier.width(12.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = { onValueChange("") },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear the $label shortcut",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        ShortcutKeyCap(value = value, conflict = isDuplicate)
    }

    DialogPresence(detecting) {
        KeyDetectionDialog(
            label = label,
            onKeyDetected = { detected ->
                onValueChange(detected)
                detecting = false
            },
            onDismiss = { detecting = false },
        )
    }
}

@Composable
private fun ShortcutKeyCap(value: String, conflict: Boolean) {
    val empty = value.isEmpty()
    val shape = RoundedCornerShape(8.dp)
    val dark = chromeIsDark()
    val fill = when {
        conflict -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        !empty -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    }
    val border = when {
        conflict -> MaterialTheme.colorScheme.error
        !empty -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> ModernTheme.hairlineBorder(dark)
    }
    val ink = when {
        conflict -> MaterialTheme.colorScheme.error
        !empty -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 38.dp, minHeight = 30.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value.ifEmpty { "Set" },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (empty) FontWeight.Normal else FontWeight.Bold,
            color = ink,
        )
    }
}

@Composable
private fun ShortcutColorDot(color: Int) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(1.dp, ModernTheme.hairlineBorder(chromeIsDark()), CircleShape),
    )
}
