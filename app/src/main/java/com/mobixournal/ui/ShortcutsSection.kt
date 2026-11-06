package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Keyboard
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor

/**
 * The keyboard shortcuts, apart from the input behaviours they used to sit among: the **toggles**,
 * then **one key per tool** and **one key per pen colour**. Every row takes a single key (letter,
 * digit or symbol); leaving it unset disables that shortcut.
 *
 * Keys pressed on a hardware keyboard reach the editor through `onKeyPressed` (see
 * `EditorRegions.kt`), which resolves a character against these fields and maps — toggles first, then
 * colour, then tool — so the whole table is data, not code.
 */
enum class ShortcutSubpage(val title: String, val summary: String) {
    TOGGLES("Toggles", "Flip between two tools, or the chrome, with one key."),
    TOOLS("Tool shortcuts", "One key for each drawing and editing tool."),
    COLOURS("Colour shortcuts", "One key for each pen colour in your palette."),
}

@Composable
fun ShortcutsSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var subpage by remember { mutableStateOf<ShortcutSubpage?>(null) }

    when (val active = subpage) {
        null -> {
            SettingsCard {
                val pages = ShortcutSubpage.values()
                pages.forEachIndexed { index, page ->
                    SectionRow(title = page.title, summary = page.summary) {
                        subpage = page
                    }
                    if (index < pages.size - 1) {
                        SettingsDivider()
                    }
                }
            }
        }
        // A subpage draws its **title once**: the header above is the name this list gave it, so the card
        // under it is the card and not a second heading saying the same word.
        ShortcutSubpage.TOGGLES -> {
            SubpageHeader(active.title) { subpage = null }
            SettingsCard {
                SettingsNote("Flip between two tools, or the chrome, with one key. Leave a row unset to disable it.")
                KeySettingField("Eraser / last tool", settings.penEraserToggleKey) {
                    onChange(settings.copy(penEraserToggleKey = it))
                }
                KeySettingField("Hand / pan", settings.handToggleKey) {
                    onChange(settings.copy(handToggleKey = it))
                }
                KeySettingField("Select", settings.selectToggleKey) {
                    onChange(settings.copy(selectToggleKey = it))
                }
                KeySettingField("Full page", settings.fullPageToggleKey) {
                    onChange(settings.copy(fullPageToggleKey = it))
                }
                // The ring is the input's other door: one key that calls it up at the pen, exactly as the
                // barrel button does, so a pen without a reachable button can still reach its faces.
                KeySettingField("Ring at the pen", settings.ringShortcutKey) {
                    onChange(settings.copy(ringShortcutKey = it))
                }
            }
        }
        ShortcutSubpage.TOOLS -> {
            SubpageHeader(active.title) { subpage = null }
            var query by remember { mutableStateOf("") }
            SettingsCard {
                SettingsNote("One key for every tool — jumping straight to it, exactly as its rail slot would.")
                ShortcutFilter(query, "Filter tools") { query = it }
                val tools = EditorTool.entries.filter { it.label.contains(query, ignoreCase = true) }
                if (tools.isEmpty()) {
                    SettingsNote("No tool matches “$query”.")
                } else {
                    for (tool in tools) {
                        KeySettingField(tool.label, settings.toolShortcutKeys[tool].orEmpty()) {
                            onChange(settings.copy(toolShortcutKeys = settings.toolShortcutKeys.with(tool, it)))
                        }
                    }
                }
            }
        }
        ShortcutSubpage.COLOURS -> {
            SubpageHeader(active.title) { subpage = null }
            var query by remember { mutableStateOf("") }
            SettingsCard {
                SettingsNote(
                    "One key for every pen colour in your palette (Settings → Colors) — selecting the pen in " +
                        "that swatch. The list is the palette itself, so a colour you add joins it here at once.",
                )
                ShortcutFilter(query, "Filter colours") { query = it }
                val rows = colourShortcutRows(settings.penColors, settings.colorShortcutKeys)
                    .filter { query.isBlank() || colorDisplayName(it.first).contains(query, ignoreCase = true) }
                if (rows.isEmpty()) {
                    SettingsNote("No colour matches “$query”.")
                } else {
                    for ((color, key) in rows) {
                        KeySettingField(colorDisplayName(color), key, swatch = color) {
                            onChange(settings.copy(colorShortcutKeys = settings.colorShortcutKeys.with(color, it)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubpageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBack)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * The filter over a long list, so a tool or a colour is found by typing a few letters rather than by
 * scrolling past fifty rows. One field per subpage, never one per row: this page's rows used to each
 * carry their own text field, and opening *Tool shortcuts* then cost a full list of text fields in one
 * frame — the stutter it was reported as.
 */
@Composable
private fun ShortcutFilter(value: String, hint: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(hint) },
        singleLine = true,
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear filter")
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

/** [map] with [tool] bound to [key], dropping the entry when the key is cleared. */
private fun Map<EditorTool, String>.with(tool: EditorTool, key: String): Map<EditorTool, String> =
    if (key.isEmpty()) this - tool else this + (tool to key)

/** [map] with [color] bound to [key], dropping the entry when the key is cleared. */
private fun Map<Int, String>.with(color: Int, key: String): Map<Int, String> =
    if (key.isEmpty()) this - color else this + (color to key)

/**
 * The colour-shortcut rows: **one per swatch in the palette, in palette order**, each carrying the key
 * it is bound to (empty while unassigned).
 *
 * Derived from [colors] rather than from [keys] on purpose: the palette is the user's list, so adding
 * a colour under Settings → Colors — or from the canvas's Colour & size pop-up — gives it a row here
 * automatically, with an empty field waiting for its key. Building the rows from the shortcut map
 * instead would make a new colour invisible until a key happened to exist for it, which is the one
 * order in which the two lists could disagree. Duplicates are dropped, matching the palette's own
 * de-duplicated append.
 */
internal fun colourShortcutRows(colors: List<Int>, keys: Map<Int, String>): List<Pair<Int, String>> =
    colors.distinct().map { color -> color to keys[color].orEmpty() }

/**
 * One shortcut row: the name, then the key it carries, as a compact chip.
 *
 * A row is **one tap target and two texts**, not a text field with a label, supporting text and two
 * trailing buttons: the tool and colour lists are dozens of rows long, and composing a form control
 * for each of them was what made opening a list stutter. Tapping the chip opens [KeyDetectionDialog],
 * which is also the door a tablet's express key comes through; the clear button beside it unsets the
 * shortcut. When [swatch] is set the row leads with that colour's circle, so a colour is recognisable
 * as a colour at a glance.
 */
@Composable
private fun KeySettingField(
    label: String,
    value: String,
    swatch: Int? = null,
    onValueChange: (String) -> Unit,
) {
    var detecting by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatch != null) {
            ShortcutColorDot(swatch)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            maxLines = 2,
        )
        if (value.isNotEmpty()) {
            IconButton(
                onClick = { onValueChange("") },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Clear the $label shortcut",
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { detecting = true }
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = value.ifEmpty { "Set" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (value.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                color = if (value.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }

    DialogPresence(detecting) {
        KeyDetectionDialog(
            label = label,
            onKeyDetected = { detected ->
                onValueChange(detected)
                detecting = false
            },
            onDismiss = { detecting = false },
        )
    }
}

/** A colour's own circle, drawn like the palette's swatches: filled, with a hairline that keeps white visible. */
@Composable
private fun ShortcutColorDot(color: Int) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(1.dp, ModernTheme.hairlineBorder(chromeIsDark()), CircleShape),
    )
}

/**
 * How many frames the key dialog waits for its own window to attach before it stops asking for focus.
 * Three is enough for the window to be added on every device seen so far, and bounded so a dialog that
 * somehow never attaches cannot spin.
 */
private const val FOCUS_FRAMES = 3

/**
 * The key dialog's focus, asked for across its first frames.
 *
 * The dialog's window is attached on the frame **after** its content first composes, so asking for
 * focus from the composition's first effect is a race — and the loser gets
 * "FocusRequester is not initialized", thrown inside an effect, which takes the app down. That is why
 * the shell's other two focus requests are wrapped in `runCatching`; this one asks again on the next
 * frame instead of failing the first time, and stops asking after [FOCUS_FRAMES] so a window that
 * never attaches cannot spin. Either way the dialog stays dismissible with Cancel or Back.
 */
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

/** Modal dialog that listens for the next key press (keyboard or tablet express key) to assign it. */
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
