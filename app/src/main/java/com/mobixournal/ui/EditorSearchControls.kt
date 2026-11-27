package com.mobixournal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobixournal.design.DesignTokens
import com.mobixournal.render.SearchStatus
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.TabChipBorderLight
import com.mobixournal.ui.theme.TabChipFillDark
import com.mobixournal.ui.theme.TabChipFillLight
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor

@Composable
internal fun SearchControls(pane: PaneState) {
    fun apply(status: SearchStatus) {
        pane.searchCurrent = status.current
        pane.searchTotal = status.total
    }
    val isDark = chromeIsDark()
    val chromeInk = rememberChromeInk()
    val spec = DesignTokens.GlassMotion.spec(DesignTokens.GlassMotion.SurfaceRole.POPOVER)
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchFocused by remember { mutableStateOf(false) }

    LaunchedEffect(pane, pane.surface, pane.searchOpen, pane.documentVersion) {
        if (!pane.searchOpen) return@LaunchedEffect
        val surface = pane.surface ?: return@LaunchedEffect
        if (!surface.isHandwritingIndexReady()) {
            surface.ensureHandwritingIndex { surface.setSearchQuery(pane.searchQuery).let(::apply) }
        } else {
            surface.setSearchQuery(pane.searchQuery).let(::apply)
        }
    }
    DialogPresence(pane.searchIndexing) {
        SearchIndexingDialog(pane.searchIndexingProgress) {
            pane.surface?.cancelIndexing()
            pane.searchIndexing = false
        }
    }
    LaunchedEffect(pane.searchOpen) {
        if (pane.searchOpen) {
            pane.surface?.clearFocus()
            runCatching { focusRequester.requestFocus(); keyboardController?.show() }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(
            visible = !pane.searchOpen,
            enter = fadeIn(spring(spec.openDamping, spec.openStiffness)),
            exit = fadeOut(spring(spec.closeDamping, spec.closeStiffness)),
        ) {
            IconButton(onClick = { pane.searchOpen = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Search, contentDescription = "Search", tint = chromeInk, modifier = Modifier.size(20.dp))
            }
        }
        AnimatedVisibility(
            visible = pane.searchOpen,
            enter = expandHorizontally(spring<IntSize>(spec.openDamping, spec.openStiffness), expandFrom = Alignment.End) +
                fadeIn(spring(spec.openDamping, spec.openStiffness)),
            exit = shrinkHorizontally(spring<IntSize>(spec.closeDamping, spec.closeStiffness), shrinkTowards = Alignment.End) +
                fadeOut(spring(spec.closeDamping, spec.closeStiffness)),
        ) {
            val fill = if (isDark) TabChipFillDark else TabChipFillLight
            val restBorder = if (isDark) ModernTheme.hairlineBorder(true) else TabChipBorderLight
            val borderColor = if (searchFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else restBorder
            Surface(
                shape = RoundedCornerShape(DesignTokens.CAPSULE_CORNER_RADIUS_DP.dp),
                color = fill,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.padding(end = 2.dp).height(32.dp),
            ) {
                SearchBarInnerRow(
                    pane = pane, chromeInk = chromeInk, restBorder = restBorder, focusRequester = focusRequester,
                    onFocusChanged = { searchFocused = it }, onApply = ::apply,
                    onHideKeyboard = { keyboardController?.hide() }, onShowKeyboard = { keyboardController?.show() },
                )
            }
        }
    }
}

@Composable
private fun SearchBarInnerRow(
    pane: PaneState,
    chromeInk: androidx.compose.ui.graphics.Color,
    restBorder: androidx.compose.ui.graphics.Color,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onApply: (SearchStatus) -> Unit,
    onHideKeyboard: () -> Unit,
    onShowKeyboard: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
        if (pane.searchIndexing) {
            CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        } else {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = chromeInk.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = pane.searchQuery,
            onValueChange = {
                pane.searchQuery = it
                pane.surface?.setSearchQuery(it)?.let(onApply) ?: onApply(SearchStatus())
            },
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = chromeInk),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onHideKeyboard(); pane.surface?.nextSearchHit()?.let(onApply) }),
            modifier = Modifier
                .widthIn(min = 96.dp, max = 164.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) },
            decorationBox = { inner ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.clickable { pane.surface?.clearFocus(); focusRequester.requestFocus(); onShowKeyboard() },
                ) {
                    if (pane.searchQuery.isEmpty()) {
                        Text(
                            if (pane.searchIndexing) "Loading text…" else "Search document…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        )
                    }
                    inner()
                }
            },
        )
        if (pane.searchQuery.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            SearchCounterBadge(pane.searchCurrent, pane.searchTotal)
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.width(1.dp).height(16.dp).background(restBorder))
        Spacer(Modifier.width(2.dp))
        SearchBarNavActions(pane = pane, chromeInk = chromeInk, onApply = onApply, onHideKeyboard = onHideKeyboard)
    }
}

@Composable
private fun SearchBarNavActions(
    pane: PaneState,
    chromeInk: androidx.compose.ui.graphics.Color,
    onApply: (SearchStatus) -> Unit,
    onHideKeyboard: () -> Unit,
) {
    CompactIconButton(
        contentDescription = "Previous match",
        enabled = pane.searchTotal > 0,
        onClick = { pane.surface?.previousSearchHit()?.let(onApply) },
        modifier = Modifier.size(26.dp),
    ) {
        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Previous match", tint = chromeInk, modifier = Modifier.size(17.dp))
    }
    CompactIconButton(
        contentDescription = "Next match",
        enabled = pane.searchTotal > 0,
        onClick = { pane.surface?.nextSearchHit()?.let(onApply) },
        modifier = Modifier.size(26.dp),
    ) {
        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Next match", tint = chromeInk, modifier = Modifier.size(17.dp))
    }
    CompactIconButton(
        contentDescription = "Close search",
        onClick = {
            pane.searchOpen = false
            pane.searchQuery = ""
            pane.surface?.cancelIndexing()
            pane.surface?.clearSearch()?.let(onApply) ?: onApply(SearchStatus())
            onHideKeyboard()
            pane.surface?.requestFocus()
        },
        modifier = Modifier.size(26.dp),
    ) {
        Icon(Icons.Outlined.Close, contentDescription = "Close search", tint = chromeInk, modifier = Modifier.size(17.dp))
    }
}

@Composable
internal fun SearchIndexingDialog(
    progress: String?,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = rememberFloatingSurfaceColor(),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Loading text…",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (progress != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = progress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                TextButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun SearchCounterBadge(current: Int, total: Int) {
    val hasHits = total > 0
    val container = if (hasHits) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
    }
    val ink = if (hasHits) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = container,
    ) {
        Text(
            text = if (hasHits) "$current/$total" else "0/0",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            style = TextStyle(fontFeatureSettings = "tnum"),
            color = ink,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CompactIconButton(
    contentDescription: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.size(36.dp),
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .clip(RoundedCornerShape(8.dp))
            .semantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        icon()
    }
}
