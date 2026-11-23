package com.mobixournal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobixournal.design.SettingsGlyph
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.liquidGlassContentSize
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor

private data class ToggleBindingItem(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val apply: (AppSettings, String) -> AppSettings,
)

private data class FilteredShortcuts(
    val toggles: List<ToggleBindingItem>,
    val toolGroups: List<Pair<ShortcutToolFamily, List<EditorTool>>>,
    val legacyTools: List<EditorTool>,
    val colorRows: List<Pair<Int, String>>,
) {
    val isEmpty: Boolean
        get() = toggles.isEmpty() && toolGroups.isEmpty() && legacyTools.isEmpty() && colorRows.isEmpty()
}

/**
 * Searchable, categorized keyboard and tablet ExpressKey shortcut manager:
 * filter pills (`All`, `Bound`, `Toggles`, `Tools`, `Colors`), duplicate-key detection,
 * grouped tool families with collapsible `Circuits & logic`, palette color shortcuts, and reset.
 */
@Composable
fun ShortcutsSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf(ShortcutFilterTab.ALL) }
    var circuitsExpanded by remember { mutableStateOf(false) }
    var confirmingReset by remember { mutableStateOf(false) }

    val onlyBound = filterTab == ShortcutFilterTab.BOUND
    val boundCount = remember(settings) { totalBoundShortcuts(settings) }
    val duplicateKeys = remember(settings) { duplicateShortcutKeys(settings) }
    val filtered = remember(settings, query, filterTab) {
        computeFilteredShortcuts(settings, query, filterTab)
    }

    SettingsSearchField(
        query = query,
        onQuery = { query = it },
        placeholder = "Search shortcuts, tools, or keys…",
        modifier = Modifier.padding(bottom = 12.dp),
    )
    ShortcutFilterBar(selected = filterTab, boundCount = boundCount, onSelect = { filterTab = it })
    if (duplicateKeys.isNotEmpty()) {
        DuplicateKeyBanner(duplicateKeys)
    }
    if (filtered.toggles.isNotEmpty()) {
        ToggleShortcutsGroup(filtered.toggles, duplicateKeys, settings, onChange)
    }
    ToolFamilyGroups(
        groups = filtered.toolGroups,
        query = query,
        onlyBound = onlyBound,
        circuitsExpanded = circuitsExpanded,
        onToggleCircuits = { circuitsExpanded = !circuitsExpanded },
        duplicateKeys = duplicateKeys,
        settings = settings,
        onChange = onChange,
    )
    if (filtered.legacyTools.isNotEmpty()) {
        ToolShortcutGroup("Legacy", filtered.legacyTools, duplicateKeys, settings, onChange)
    }
    if (filtered.colorRows.isNotEmpty()) {
        ColorShortcutsGroup(filtered.colorRows, duplicateKeys, settings, onChange)
    }
    if (filtered.isEmpty) {
        ShortcutEmptyState(query = query, onlyBound = onlyBound)
    }
    if (query.isBlank() && filterTab == ShortcutFilterTab.ALL) {
        ResetShortcutsGroup(
            confirming = confirmingReset,
            onAskReset = { confirmingReset = true },
            onConfirm = {
                confirmingReset = false
                onChange(resetShortcutsToDefaults(settings))
            },
            onCancel = { confirmingReset = false },
        )
    }
}

private fun computeFilteredShortcuts(
    settings: AppSettings,
    query: String,
    tab: ShortcutFilterTab,
): FilteredShortcuts {
    val onlyBound = tab == ShortcutFilterTab.BOUND
    val toggles = if (tab !in setOf(ShortcutFilterTab.ALL, ShortcutFilterTab.BOUND, ShortcutFilterTab.TOGGLES)) {
        emptyList()
    } else {
        toggleItemsFor(settings).filter {
            shortcutMatches(it.label, it.value, query) && (!onlyBound || it.value.isNotBlank())
        }
    }
    val toolGroups = filteredToolGroups(settings, query, tab)
    val legacyTools = if (tab !in setOf(ShortcutFilterTab.ALL, ShortcutFilterTab.BOUND, ShortcutFilterTab.TOOLS)) {
        emptyList()
    } else {
        boundRetiredTools(settings.toolShortcutKeys).filter { tool ->
            val key = settings.toolShortcutKeys[tool].orEmpty()
            shortcutMatches(tool.label, key, query) && (!onlyBound || key.isNotBlank())
        }
    }
    val colorRows = if (tab !in setOf(ShortcutFilterTab.ALL, ShortcutFilterTab.BOUND, ShortcutFilterTab.COLORS)) {
        emptyList()
    } else {
        colourShortcutRows(settings.penColors, settings.colorShortcutKeys).filter { (color, key) ->
            shortcutMatches(colorDisplayName(color), key, query) && (!onlyBound || key.isNotBlank())
        }
    }
    return FilteredShortcuts(toggles, toolGroups, legacyTools, colorRows)
}

private fun toggleItemsFor(settings: AppSettings): List<ToggleBindingItem> = listOf(
    ToggleBindingItem("Eraser / last tool", settings.penEraserToggleKey, EditorTool.ERASER.icon) { s, k ->
        s.copy(penEraserToggleKey = k)
    },
    ToggleBindingItem("Hand / pan", settings.handToggleKey, EditorTool.HAND.icon) { s, k ->
        s.copy(handToggleKey = k)
    },
    ToggleBindingItem("Select", settings.selectToggleKey, EditorTool.SELECT.icon) { s, k ->
        s.copy(selectToggleKey = k)
    },
    ToggleBindingItem("Focus mode", settings.fullPageToggleKey, SettingsGlyph.NAVIGATION.imageVector()) { s, k ->
        s.copy(fullPageToggleKey = k)
    },
    ToggleBindingItem("Ring at the pen", settings.ringShortcutKey, SettingsGlyph.RING.imageVector()) { s, k ->
        s.copy(ringShortcutKey = k)
    },
)

private fun filteredToolGroups(
    settings: AppSettings,
    query: String,
    tab: ShortcutFilterTab,
): List<Pair<ShortcutToolFamily, List<EditorTool>>> {
    if (tab !in setOf(ShortcutFilterTab.ALL, ShortcutFilterTab.BOUND, ShortcutFilterTab.TOOLS)) {
        return emptyList()
    }
    val onlyBound = tab == ShortcutFilterTab.BOUND
    return shortcutToolGroups().mapNotNull { (family, tools) ->
        val filtered = tools.filter { tool ->
            val key = settings.toolShortcutKeys[tool].orEmpty()
            shortcutMatches(tool.label, key, query) && (!onlyBound || key.isNotBlank())
        }
        val keepCollapsedHeader = family.collapsible && query.isBlank() && !onlyBound
        if (filtered.isEmpty() && !keepCollapsedHeader) null else family to filtered
    }
}

@Composable
private fun ToggleShortcutsGroup(
    items: List<ToggleBindingItem>,
    duplicateKeys: Set<String>,
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
) {
    SettingsGroup("Toggles") {
        items.forEachIndexed { index, item ->
            ShortcutKeyRow(
                label = item.label,
                value = item.value,
                duplicateKeys = duplicateKeys,
                icon = item.icon,
                onValueChange = { nextKey -> onChange(item.apply(settings, nextKey)) },
            )
            if (index < items.size - 1) SettingsDivider()
        }
    }
}

@Composable
private fun ToolFamilyGroups(
    groups: List<Pair<ShortcutToolFamily, List<EditorTool>>>,
    query: String,
    onlyBound: Boolean,
    circuitsExpanded: Boolean,
    onToggleCircuits: () -> Unit,
    duplicateKeys: Set<String>,
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
) {
    groups.forEach { (family, tools) ->
        if (family.collapsible && query.isBlank() && !onlyBound) {
            CollapsibleCircuitsGroup(
                tools = tools,
                expanded = circuitsExpanded,
                onToggle = onToggleCircuits,
                duplicateKeys = duplicateKeys,
                settings = settings,
                onChange = onChange,
            )
        } else {
            ToolShortcutGroup(family.title, tools, duplicateKeys, settings, onChange)
        }
    }
}

@Composable
private fun CollapsibleCircuitsGroup(
    tools: List<EditorTool>,
    expanded: Boolean,
    onToggle: () -> Unit,
    duplicateKeys: Set<String>,
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
) {
    val boundInFamily = tools.count { !settings.toolShortcutKeys[it].isNullOrBlank() }
    val reach = LocalSettingsCardInset.current
    SettingsGroup("Circuits & logic") {
        Column(Modifier.liquidGlassContentSize()) {
            Row(
                modifier = Modifier
                    .fullBleedRow(reach)
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = reach, vertical = settingsCellInset(reach)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlHeader(
                    title = "${tools.size} circuit & gate tools",
                    subtitle = if (boundInFamily > 0) "$boundInFamily bound" else "Tap to expand or collapse",
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    tools.forEach { tool ->
                        SettingsDivider()
                        ShortcutKeyRow(
                            label = tool.label,
                            value = settings.toolShortcutKeys[tool].orEmpty(),
                            duplicateKeys = duplicateKeys,
                            icon = tool.icon,
                            onValueChange = { key ->
                                onChange(settings.copy(toolShortcutKeys = settings.toolShortcutKeys.with(tool, key)))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolShortcutGroup(
    title: String,
    tools: List<EditorTool>,
    duplicateKeys: Set<String>,
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
) {
    SettingsGroup(title) {
        tools.forEachIndexed { index, tool ->
            ShortcutKeyRow(
                label = tool.label,
                value = settings.toolShortcutKeys[tool].orEmpty(),
                duplicateKeys = duplicateKeys,
                icon = tool.icon,
                onValueChange = { key ->
                    onChange(settings.copy(toolShortcutKeys = settings.toolShortcutKeys.with(tool, key)))
                },
            )
            if (index < tools.size - 1) SettingsDivider()
        }
    }
}

@Composable
private fun ColorShortcutsGroup(
    rows: List<Pair<Int, String>>,
    duplicateKeys: Set<String>,
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
) {
    SettingsGroup("Pen palette") {
        rows.forEachIndexed { index, (color, key) ->
            ShortcutKeyRow(
                label = colorDisplayName(color),
                value = key,
                duplicateKeys = duplicateKeys,
                swatch = color,
                onValueChange = { nextKey ->
                    onChange(settings.copy(colorShortcutKeys = settings.colorShortcutKeys.with(color, nextKey)))
                },
            )
            if (index < rows.size - 1) SettingsDivider()
        }
    }
}

private fun Map<EditorTool, String>.with(tool: EditorTool, key: String): Map<EditorTool, String> =
    if (key.isEmpty()) this - tool else this + (tool to key)

private fun Map<Int, String>.with(color: Int, key: String): Map<Int, String> =
    if (key.isEmpty()) this - color else this + (color to key)

private const val FOCUS_FRAMES = 3

@Composable
private fun rememberKeyCaptureFocus(): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) {
        repeat(FOCUS_FRAMES) {
            withFrameNanos { }
            if (runCatching { focusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    return focusRequester
}

/** Modal dialog that listens for the next key press (keyboard or tablet ExpressKey) to assign it. */
@Composable
fun KeyDetectionDialog(
    label: String,
    onKeyDetected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = rememberKeyCaptureFocus()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = rememberFloatingSurfaceColor(),
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.92f)
                .floatingRim(20.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                        val native = event.nativeKeyEvent
                        if (native.keyCode == android.view.KeyEvent.KEYCODE_BACK ||
                            native.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE
                        ) {
                            onDismiss()
                            return@onPreviewKeyEvent true
                        }
                        val key = keyStringFromKeyEvent(native)
                        if (key != null) {
                            onKeyDetected(key)
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                },
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Detect key for $label",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Press a key on your keyboard or graphics tablet to assign it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Listening… (press a key or ExpressKey)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(18.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}

/** Pure resolution of key string from key event components, testable without Android mocks. */
internal fun resolveKeyString(unicodeChar: Int, displayLabel: Char, keyCode: Int): String? {
    if (unicodeChar != 0) {
        val c = unicodeChar.toChar()
        if (!c.isISOControl() && !c.isWhitespace()) {
            return c.toString().uppercase()
        }
    }
    if (displayLabel != '\u0000' && !displayLabel.isWhitespace()) {
        return displayLabel.toString().uppercase()
    }
    return when (keyCode) {
        android.view.KeyEvent.KEYCODE_SPACE -> " "
        in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 ->
            ('0' + (keyCode - android.view.KeyEvent.KEYCODE_0)).toString()
        in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 ->
            ('0' + (keyCode - android.view.KeyEvent.KEYCODE_NUMPAD_0)).toString()
        in android.view.KeyEvent.KEYCODE_A..android.view.KeyEvent.KEYCODE_Z ->
            ('A' + (keyCode - android.view.KeyEvent.KEYCODE_A)).toString()
        else -> null
    }
}

/** Extracts a clean single character or symbol from a key event, returning null for non-character keys. */
internal fun keyStringFromKeyEvent(event: android.view.KeyEvent): String? {
    val unicode = if (event.unicodeChar != 0) event.unicodeChar else event.getUnicodeChar(event.metaState)
    return resolveKeyString(unicode, event.displayLabel, event.keyCode)
}
