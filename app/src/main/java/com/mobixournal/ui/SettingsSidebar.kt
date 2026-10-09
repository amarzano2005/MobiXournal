package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The settings' permanent side menu: the search field, then the four [SettingsArea]s.
 *
 * This is the shape a tablet's system settings use — the top-level list stays on screen and the pane
 * beside it shows what is selected — so moving between areas is one tap with no level to walk back
 * through, and the search is reachable from wherever the user is. It is drawn only on wide screens
 * ([SETTINGS_TWO_PANE_MIN_WIDTH]); on a phone the same areas are the pushed index page instead, since
 * a permanent menu there would leave the controls a strip to live in.
 *
 * The field is a *field*, not a button to a search screen: typing in it filters the pane live.
 */
@Composable
internal fun SettingsSidebar(
    query: String,
    onQuery: (String) -> Unit,
    selected: SettingsArea,
    onArea: (SettingsArea) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
        SettingsSearchField(query, onQuery)
        Spacer(Modifier.size(12.dp))
        for (area in SettingsArea.values()) {
            SidebarRow(
                area = area,
                selected = area == selected,
                onClick = { onArea(area) },
            )
        }
    }
}

/** One area in the side menu: its icon, its name, and a tonal pill while it is the selected one. */
@Composable
private fun SidebarRow(area: SettingsArea, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = area.icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = area.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The one settings search field, shared by the side menu (wide) and the index page (narrow) so both
 * shapes search the same way: typing filters live, and the clear button appears only once there is
 * something to clear.
 */
@Composable
internal fun SettingsSearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text("Search settings") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The icon of each area, so the side menu is scannable by shape as well as by word — the same trick a
 * tablet's settings list uses. Declared here, next to the list that draws it, because it is a property
 * of *this menu*; nothing else renders an area.
 */
private val SettingsArea.icon: ImageVector
    get() = when (this) {
        SettingsArea.INPUT -> Icons.Filled.TouchApp
        SettingsArea.DRAWING -> Icons.Filled.Brush
        SettingsArea.INTERFACE -> Icons.Filled.Tune
        SettingsArea.APP -> Icons.Filled.Info
    }

/** Below this width the side menu is dropped for the pushed index (a phone, or a tablet in portrait). */
internal val SETTINGS_TWO_PANE_MIN_WIDTH = 600.dp
