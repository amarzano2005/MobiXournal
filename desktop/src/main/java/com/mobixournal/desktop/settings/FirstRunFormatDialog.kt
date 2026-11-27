package com.mobixournal.desktop.settings

import com.mobixournal.desktop.shell.WindowKeys
import com.mobixournal.desktop.tools.DesktopDialogFrame
import com.mobixournal.desktop.tools.DialogWidth
import com.mobixournal.desktop.tools.GlassPresence
import com.mobixournal.desktop.tools.PanelButton
import com.mobixournal.desktop.tools.hoverFill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * **The first run's one question**: the format a new document is written in.
 *
 * The window used to answer it silently (every new page a gzip `.xopp`) while the tablet asked in its
 * tour (`OnboardingStepDocumentFormat`), so the two shells disagreed about what New makes. It is asked
 * **once**, until [DesktopSettingsState.documentFormatChosen] says it was answered, and `.mobix` is both
 * the pre-picked row and the format this app is built around (`docs/mobix-format.md`).
 *
 * A modal like every other question here ([DesktopDialogFrame]): the window's scrim, the opaque popup
 * surface, the accent on the picked row, English copy, and the answer under it. Opened notebooks keep
 * their own format regardless, which is what the subtitle says so the question does not read as a
 * conversion.
 */
@Composable
internal fun FirstRunFormatDialog(
    picked: DesktopNewDocumentFormat,
    onPick: (DesktopNewDocumentFormat) -> Unit,
    embedPdf: Boolean = true,
    onEmbedPdf: (Boolean) -> Unit = {},
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DesktopDialogFrame(
        title = "Which format do new documents use?",
        width = DialogWidth.Standard,
        subtitle = "Opened notebooks always keep their own format, and Save As switches either way at any time.",
        onDismiss = onConfirm,
        modifier = modifier,
        footer = {
            PanelButton(label = "Start writing", primary = true, enabled = true, onClick = onConfirm)
        },
    ) {
        Column(
            Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FormatRow(
                title = "MobiXournal (.mobix)",
                note = "One file for the ink, the figures and the audio. Recommended.",
                picked = picked == DesktopNewDocumentFormat.MOBIX,
                onPick = { onPick(DesktopNewDocumentFormat.MOBIX) },
            )
            FormatRow(
                title = "Xournal++ (.xopp)",
                note = "What desktop Xournal++ reads directly; living figures become ink.",
                picked = picked == DesktopNewDocumentFormat.XOPP,
                onPick = { onPick(DesktopNewDocumentFormat.XOPP) },
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = embedPdf, onClick = { onEmbedPdf(!embedPdf) }, role = Role.Checkbox)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Embed PDF backgrounds (keep PDFs inside .mobix and .xopp)",
                    color = DesktopTheme.ink,
                    style = DesktopTheme.label,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                SettingsSwitch(embedPdf)
            }
        }
    }
}

/**
 * One answer of [FirstRunFormatDialog]: its name, its sentence, and the mark that says it is picked.
 *
 * It is a **radio** — `selectable` gives it the role, the selected state in the semantics tree, a place in
 * the Tab order and Space/Enter — on the control's 4 dp corner with the accent ring while it has the
 * keyboard, the same row the import and export cards draw. A row that only watched for taps left the
 * keyboard with no way to answer the first question the window ever asks.
 */
@Composable
private fun FormatRow(title: String, note: String, picked: Boolean, onPick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { WindowKeys.focusControl(owner, false) } }
    Row(
        Modifier
            .fillMaxWidth()
            .hoverFill(
                DesktopTheme.controlShape,
                active = picked,
                base = if (picked) DesktopTheme.activeSlot else Color.Transparent,
                focused = focused,
            )
            .onFocusChanged {
                focused = it.isFocused
                WindowKeys.focusControl(owner, it.isFocused)
            }
            .selectable(
                selected = picked,
                interactionSource = interactions,
                indication = null,
                role = Role.RadioButton,
                onClick = onPick,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$title. $note" }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (picked) DesktopTheme.accent else DesktopTheme.ink,
                style = DesktopTheme.body,
            )
            Spacer(Modifier.height(2.dp))
            Text(note, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(12.dp)
                .background(
                    if (picked) DesktopTheme.accent else Color.Transparent,
                    DesktopTheme.capsuleShape,
                )
                .border(
                    1.dp,
                    if (picked) DesktopTheme.accent else DesktopTheme.hairline,
                    DesktopTheme.capsuleShape,
                ),
        )
    }
}

/**
 * The shell's overlay for the question: the card, on the family's spring. The frame supplies the blocker —
 * nothing on the page behind it may take a stroke. The card takes itself away when the answer lands, so the
 * window is a window with a document in it, not one that asks again on every start.
 */
@Composable
internal fun BoxScope.FirstRunFormatHost(policy: DesktopSettingsState) {
    GlassPresence(visible = !policy.documentFormatChosen) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!policy.documentFormatChosen) {
                var picked by remember { mutableStateOf(policy.newDocumentFormat) }
                var embedPdf by remember { mutableStateOf(policy.embedPdfBackgrounds) }
                FirstRunFormatDialog(
                    picked = picked,
                    onPick = { picked = it },
                    embedPdf = embedPdf,
                    onEmbedPdf = { embedPdf = it },
                    onConfirm = {
                        policy.chooseEmbedPdfBackgrounds(embedPdf)
                        policy.chooseNewDocumentFormat(picked)
                    },
                )
            }
        }
    }
}
