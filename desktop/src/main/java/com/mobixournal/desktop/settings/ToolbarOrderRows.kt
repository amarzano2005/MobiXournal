package com.mobixournal.desktop.settings
import com.mobixournal.desktop.tools.DEFAULT_IMAGE_ANCHOR
import com.mobixournal.desktop.tools.DesktopButton
import com.mobixournal.desktop.tools.DesktopButtonVariant
import com.mobixournal.desktop.tools.isDesktopDividerToken

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

/**
 * The **Buttons** group's rows, each tool slot and divider carrying a drag handle.
 */
@Composable
internal fun ToolbarOrderRows(policy: DesktopSettingsState) {
    val groups = policy.toolGroups
    val tokens = policy.strips.editableTokens
    val byName = groups.associateBy { it.face.name }
    val anchor = policy.imageAfter.takeIf { name -> name in byName } ?: DEFAULT_IMAGE_ANCHOR.name
    val drag = remember { ReorderState() }

    tokens.forEachIndexed { index, token ->
        if (isDesktopDividerToken(token)) {
            ReorderRow(drag, index, tokens.size) {
                SettingsRow(
                    label = "Divider",
                    note = "Visual separator in the tool strip.",
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DesktopButton(
                            label = "Remove",
                            variant = DesktopButtonVariant.Ghost,
                            onClick = { policy.strips.removeDivider(token) },
                        )
                        DragGrip(
                            label = "Divider",
                            onDragStart = { drag.start(index) },
                            onDrag = drag::by,
                            onDragEnd = { drag.drop(tokens.size)?.let { to -> policy.strips.moveEditableRow(index, to) } },
                            onDragCancel = drag::clear,
                            onStep = { direction -> policy.strips.moveEditableRow(index, index + direction) },
                        )
                    }
                }
            }
        } else {
            val group = byName[token] ?: return@forEachIndexed
            ReorderRow(drag, index, tokens.size) {
                SettingsRow(
                    label = group.face.label,
                    note = group.members.takeIf { it.size > 1 }?.joinToString(" · ") { it.label }.orEmpty(),
                ) {
                    DragGrip(
                        label = group.face.label,
                        onDragStart = { drag.start(index) },
                        onDrag = drag::by,
                        onDragEnd = { drag.drop(tokens.size)?.let { to -> policy.strips.moveEditableRow(index, to) } },
                        onDragCancel = drag::clear,
                        onStep = { direction -> policy.strips.moveEditableRow(index, index + direction) },
                    )
                }
            }
            if (token == anchor) {
                SettingsRow(
                    label = "Insert image",
                    note = "Follows the tool slot directly above it.",
                ) {
                    val toolPos = groups.indexOfFirst { it.face.name == anchor }
                    SettingsStepButtons(
                        upEnabled = toolPos > 0,
                        downEnabled = toolPos in 0 until groups.lastIndex,
                        onUp = { policy.moveImageSlot(-1) },
                        onDown = { policy.moveImageSlot(1) },
                    )
                }
            }
        }
    }
}
