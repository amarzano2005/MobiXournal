package com.mobixournal.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The keyboard shortcuts, apart from the input behaviours they used to sit among: the two toggles,
 * then **one key per tool** and **one key per pen colour**. Every field takes a single character
 * (letter, digit or symbol); leaving it empty disables that shortcut.
 *
 * Keys pressed on a hardware keyboard reach the editor through `onKeyPressed` (see
 * `EditorRegions.kt`), which resolves a character against these maps — first the colour map, then
 * the tool map — so the whole table is data, not code.
 */
@Composable
fun ShortcutsSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    Text("Toggles", style = MaterialTheme.typography.titleSmall)
    Text(
        "Flip between two tools with one key.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(4.dp))
    KeySettingField("Pen ↔ Eraser", settings.penEraserToggleKey) {
        onChange(settings.copy(penEraserToggleKey = it))
    }
    KeySettingField("Hand/Pan", settings.handToggleKey) {
        onChange(settings.copy(handToggleKey = it))
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text("Tool shortcuts", style = MaterialTheme.typography.titleSmall)
    Text(
        "One key for every tool — jumping straight to it, exactly as its rail slot would.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(4.dp))
    for (tool in EditorTool.entries) {
        KeySettingField(tool.label, settings.toolShortcutKeys[tool].orEmpty()) {
            onChange(settings.copy(toolShortcutKeys = settings.toolShortcutKeys.with(tool, it)))
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text("Colour shortcuts", style = MaterialTheme.typography.titleSmall)
    Text(
        "One key for every pen colour in your palette (Settings → Colors) — selecting the pen in that swatch.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(4.dp))
    for (color in settings.penColors) {
        KeySettingField(colorDisplayName(color), settings.colorShortcutKeys[color].orEmpty()) {
            onChange(settings.copy(colorShortcutKeys = settings.colorShortcutKeys.with(color, it)))
        }
    }
}

/** [map] with [tool] bound to [key], dropping the entry when the key is cleared. */
private fun Map<EditorTool, String>.with(tool: EditorTool, key: String): Map<EditorTool, String> =
    if (key.isEmpty()) this - tool else this + (tool to key)

/** [map] with [color] bound to [key], dropping the entry when the key is cleared. */
private fun Map<Int, String>.with(color: Int, key: String): Map<Int, String> =
    if (key.isEmpty()) this - color else this + (color to key)

/** A single-character key field; clearing it disables the shortcut it belongs to. */
@Composable
private fun KeySettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    var detecting by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.take(1)
            onValueChange(text)
        },
        label = { Text(label) },
        supportingText = { Text("Any key: letter, number or symbol. Empty = disabled.") },
        singleLine = true,
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            text = ""
                            onValueChange("")
                        },
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear shortcut")
                    }
                }
                IconButton(
                    onClick = { detecting = true },
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Detect key directly",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )

    if (detecting) {
        KeyDetectionDialog(
            label = label,
            onKeyDetected = { detected ->
                text = detected
                onValueChange(detected)
                detecting = false
            },
            onDismiss = { detecting = false },
        )
    }
}

/** Modal dialog that listens for the next key press (keyboard or tablet express key) to assign it. */
@Composable
fun KeyDetectionDialog(
    label: String,
    onKeyDetected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.92f)
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
                        text = "Listening... (press a key or ExpressKey)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
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

