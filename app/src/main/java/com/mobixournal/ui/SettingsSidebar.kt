package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import java.util.Locale

/**
 * The settings' permanent wide-screen side menu: a live search field, then the seven consolidated
 * [SettingsDestination]s grouped under their uppercase [SettingsGroupCategory] eyebrows.
 */
@Composable
internal fun SettingsSidebar(
    query: String,
    onQuery: (String) -> Unit,
    selected: SettingsDestination?,
    visibleDestinations: List<SettingsDestination>,
    onSelect: (SettingsDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleSet = remember(visibleDestinations) { visibleDestinations.toSet() }
    val groups = remember(visibleSet) {
        SettingsGroupCategory.entries.mapNotNull { category ->
            val rows = SettingsDestination.entries.filter { it.category == category && it in visibleSet }
            if (rows.isEmpty()) null else category to rows
        }
    }

    Column(modifier = modifier.padding(vertical = 12.dp)) {
        SettingsSearchField(query, onQuery, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(SettingsSidebarGapAfterField))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (groups.isEmpty()) {
                Text(
                    text = "No setting matches “$query”.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 20.dp),
                )
            } else {
                groups.forEachIndexed { idx, (category, rows) ->
                    Text(
                        text = category.title.uppercase(Locale.US),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.padding(
                            start = 10.dp,
                            top = if (idx == 0) 2.dp else 14.dp,
                            bottom = 4.dp,
                        ),
                    )
                    rows.forEach { dest ->
                        SidebarDestinationRow(
                            destination = dest,
                            selected = dest == selected,
                            onClick = { onSelect(dest) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarDestinationRow(
    destination: SettingsDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val rowBg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
    val iconTint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .height(SettingsSidebarRowHeight)
            .clip(shape)
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = destination.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Compact rounded pill search field shared by the tablet sidebar, phone index, and shortcuts list.
 */
@Composable
internal fun SettingsSearchField(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search settings…",
) {
    val dark = chromeIsDark()
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) MaterialTheme.colorScheme.primary else ModernTheme.hairlineBorder(dark)

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = { onQuery("") },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Clear search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        }
        HorizontalDivider(thickness = 1.dp, color = borderColor)
    }
}

/** One pill's height and the air around it: the list's step is the two added together. */
internal val SettingsSidebarRowHeight: Dp = 48.dp
internal val SettingsSidebarRowGap: Dp = 4.dp

/** The air under the search field — larger than the pills' step, so the field is not part of their list. */
internal val SettingsSidebarGapAfterField: Dp = 16.dp

/** Below this width the side menu is dropped for the pushed index (a phone, or a tablet in portrait). */
internal val SETTINGS_TWO_PANE_MIN_WIDTH = 600.dp
