package com.mobixournal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.render.PageSizeStep

/**
 * Editor preferences, in the order a user meets them: what a new document starts as (tool, page
 * size), then the aids that tidy what they draw (snapping, shape recognition).
 */
@Composable
fun EditorSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("New documents") {
        OptionGroup(
            title = "Default tool",
            subtitle = "Which tool is active when a document opens.",
            options = DEFAULT_TOOL_CHOICES,
            selected = settings.defaultTool,
            label = { it.label },
            onSelect = { onChange(settings.copy(defaultTool = it)) },
        )
        SettingsDivider()
        DefaultDimensionsControl(settings, onChange)
    }
    SettingsGroup("Quick page size") {
        PageSizeStepControl(settings, onChange)
    }
    SettingsGroup("Drawing aids") {
        SwitchRow(
            title = "Snap to grid",
            subtitle = "Shape endpoints land on the page background's ruling.",
            checked = settings.snapToGrid,
            onCheckedChange = { onChange(settings.copy(snapToGrid = it)) },
        )
        SettingsDivider()
        SwitchRow(
            title = "Snap rotation",
            subtitle = "Rotating a selection steps in 15° increments.",
            checked = settings.snapRotation,
            onCheckedChange = { onChange(settings.copy(snapRotation = it)) },
        )
        SettingsDivider()
        SwitchRow(
            title = "Shape recognition",
            subtitle = "Snap a finished freehand stroke to the shape it resembles — line, triangle, " +
                "rectangle or circle — using desktop Xournal++'s own recognizer. Anything " +
                "unrecognised stays as drawn.",
            checked = settings.recognizeShapes,
            onCheckedChange = { onChange(settings.copy(recognizeShapes = it)) },
        )
    }
}

/**
 * The pixels one tap of the Pages menu's **quick size** buttons adds to, or takes from, a sheet
 * ([PageSizeStep]). Written in pixels — the unit a reader thinks in — and converted to the page's own
 * points when the button is pressed; 96 px is one inch whatever the screen's density.
 */
@Composable
private fun PageSizeStepControl(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var text by remember { mutableStateOf(settings.pageSizeStepPx.toString()) }
    // A step changed elsewhere (a restored backup) is what the field shows, not the last thing typed.
    LaunchedEffect(settings.pageSizeStepPx) { text = settings.pageSizeStepPx.toString() }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed.filter { it.isDigit() }.take(4)
            text.toIntOrNull()?.let { px ->
                onChange(settings.copy(pageSizeStepPx = PageSizeStep.clampPx(px)))
            }
        },
        label = { Text("Step (px)") },
        supportingText = {
            Text(
                "The − and + beside Width and Height in the Pages menu change the sheet by this much. " +
                    "96 px is one inch; the factory step is ${PageSizeStep.DEFAULT_PX} px.",
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

/** The default page width and height controls for new documents, synchronized with the Page Format popup. */
@Composable
private fun DefaultDimensionsControl(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val paper = remember { PaperState(settings.defaultPageWidthPt, settings.defaultPageHeightPt) }
    LaunchedEffect(settings.defaultPageWidthPt, settings.defaultPageHeightPt) {
        paper.updateFromExternal(settings.defaultPageWidthPt, settings.defaultPageHeightPt)
    }

    ControlHeader(
        "Default page dimensions",
        "Dimensions for new documents and newly created pages.",
        Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        PaperSection(
            paper = paper,
            onChanged = { w, h ->
                onChange(settings.copy(defaultPageWidthPt = w, defaultPageHeightPt = h))
            },
        )
    }
}
