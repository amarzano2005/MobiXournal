package com.mobixournal.desktop.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A section starts at its heading; a visible scrollbar makes longer settings discoverable. */
@Composable internal fun SettingsScrollPane(
    modifier: Modifier,
    resetKey: Any?,
    contentPadding: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = remember(resetKey) { ScrollState(0) }
    Box(modifier) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).then(contentPadding), content = content)
        VerticalScrollbar(rememberScrollbarAdapter(scroll),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 6.dp, horizontal = 2.dp),
            style = defaultScrollbarStyle().copy(unhoverColor = DesktopTheme.hairline, hoverColor = DesktopTheme.inkSecondary))
    }
}
