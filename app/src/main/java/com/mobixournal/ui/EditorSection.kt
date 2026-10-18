package com.mobixournal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
        OptionGroup(
            title = "Default canvas",
            subtitle = "The canvas geometry newly created documents open on: fixed pages or an infinite board.",
            options = NewDocumentCanvas.entries,
            selected = settings.newDocumentCanvas,
            label = { it.label },
            onSelect = { mode ->
                val fmt = if (mode != NewDocumentCanvas.PAGINATED) NewDocumentFormat.MOBIX else settings.newDocumentFormat
                onChange(settings.copy(newDocumentCanvas = mode, newDocumentFormat = fmt))
            },
        )
        SettingsDivider()
        DefaultDimensionsControl(settings, onChange)
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
            canvas = settings.newDocumentCanvas,
            onChanged = { w, h ->
                onChange(settings.copy(defaultPageWidthPt = w, defaultPageHeightPt = h))
            },
        )
    }
}
