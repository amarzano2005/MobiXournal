package com.mobixournal.desktop.settings

import com.mobixournal.desktop.tools.GlassPresence
import com.mobixournal.desktop.tools.PanelButton
import com.mobixournal.desktop.tools.glassSpring
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.tools.hoverFill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * **The first run's one question**: the format a new document is written in.
 *
 * The window used to answer it silently (every new page a gzip `.xopp`) while the tablet asked in its
 * tour (`OnboardingStepDocumentFormat`), so the two shells disagreed about what New makes. It is asked
 * **once**, until [DesktopSettingsState.documentFormatChosen] says it was answered, and `.mobix` is both
 * the pre-picked row and the format this app is built around (`docs/mobix-format.md`).
 *
 * A floating surface like every question here (`docs/interface-contract.md`): the floating glass, the
 * accent on the picked row, English copy, and the answer under it. Opened notebooks keep their own
 * format regardless, which is what the body says so the question does not read as a conversion.
 */
@Composable
internal fun FirstRunFormatDialog(
    picked: DesktopNewDocumentFormat,
    onPick: (DesktopNewDocumentFormat) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(430.dp).glassSpring().glassSurface().padding(18.dp)) {
        Text("Which format do new documents use?", color = DesktopTheme.ink, style = DesktopTheme.title)
        Spacer(Modifier.height(6.dp))
        Text(
            "Opened notebooks always keep their own format, and Save As switches either way at any time.",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.body,
        )
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PanelButton(label = "Start writing", primary = true, enabled = true, onClick = onConfirm)
        }
    }
}

/** One answer of [FirstRunFormatDialog]: its name, its sentence, and the mark that says it is picked. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FormatRow(title: String, note: String, picked: Boolean, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .hoverFill(
                DesktopTheme.railSlotShape,
                active = picked,
                base = if (picked) DesktopTheme.activeSlot else Color.Transparent,
            )
            .pointerInput(title) { detectTapGestures { onPick() } }
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
 * The shell's overlay for the question: the invisible blocker — nothing on the page behind it may take a
 * stroke — and the card, on the family's spring. The card takes itself away when the answer lands, so the
 * window is a window with a document in it, not one that asks again on every start.
 */
@Composable
internal fun BoxScope.FirstRunFormatHost(policy: DesktopSettingsState) {
    GlassPresence(visible = !policy.documentFormatChosen) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            if (!policy.documentFormatChosen) {
                var picked by remember { mutableStateOf(policy.newDocumentFormat) }
                FirstRunFormatDialog(
                    picked = picked,
                    onPick = { picked = it },
                    onConfirm = { policy.chooseNewDocumentFormat(picked) },
                )
            }
        }
    }
}
