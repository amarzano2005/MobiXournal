package com.mobixournal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mobixournal.ui.theme.FloatingDialog

/**
 * The first-launch tour: the **two questions** a new user is the only one who can answer.
 *
 * 1. Which hand writes — it is what decides the edge the Main Toolbar docks to.
 * 2. What a new document is written in — `.mobix` (recommended) or the interchange `.xopp`.
 *
 * Both steps write their answer through as it is tapped ([onToolbarPosition], [onDocumentFormat]), so
 * the editor composed behind the panel shows the rail moving and the tour has nothing left to apply
 * when it closes. It is asked exactly once: [EditorScreen] shows it until `hasSeenOnboarding` is set,
 * and resetting the settings keeps that flag ([AppSettings.factoryDefaults]).
 *
 * The panel is the family's floating surface ([FloatingDialog]) and nothing else — no wordmark badge,
 * no progress dots, no title bar over a title. It used to be five pages: stylus pressure, figure
 * widths, ExpressKeys, and then the two questions above. The three pages taught settings from inside a
 * dialog that cannot change them, and they stood between a person and the two answers they could give
 * by tapping; everything else a new user needs is the canvas behind this panel, or the Settings screen
 * that owns those controls.
 */
@Composable
fun OnboardingDialog(
    onDismiss: () -> Unit,
    toolbarPosition: ToolbarPosition = ToolbarPosition.LEFT,
    onToolbarPosition: (ToolbarPosition) -> Unit = {},
    documentFormat: NewDocumentFormat = NewDocumentFormat.MOBIX,
    onDocumentFormat: (NewDocumentFormat) -> Unit = {},
) {
    var step by remember { mutableIntStateOf(1) }
    val totalSteps = 2

    BackHandler(enabled = true) {
        if (step > 1) step-- else onDismiss()
    }

    FloatingDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (step == 1) {
                    OnboardingStepHandedness(toolbarPosition, onToolbarPosition)
                } else {
                    OnboardingStepDocumentFormat(documentFormat, onDocumentFormat)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (step < totalSteps) step++ else onDismiss() }) {
                Text(if (step < totalSteps) "Next" else "Start writing")
            }
        },
        dismissButton = {
            // The first step has nothing behind it, so its dismissal is the tour's own Skip.
            TextButton(onClick = { if (step > 1) step-- else onDismiss() }) {
                Text(if (step > 1) "Back" else "Skip")
            }
        },
    )
}
