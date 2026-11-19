package com.mobixournal.desktop.tools

import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.shell.WindowKeys

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph

/**
 * The field's **editable line**: the value, its placeholder and its spoken name, in one `BasicTextField`.
 *
 * The placeholder is drawn **inside** the field, not beside it: the field's own semantics are then the node
 * that carries the words, so a reader (or a test) that looks the field up by its placeholder finds the
 * editable node rather than a label nothing can be typed into.
 */
@Composable
private fun FieldInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    enabled: Boolean,
    onSubmit: (() -> Unit)?,
    description: String?,
    focusRequester: FocusRequester?,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        enabled = enabled,
        textStyle = DesktopTheme.field.copy(
            color = if (enabled) DesktopTheme.ink else DesktopTheme.inkMuted,
        ),
        cursorBrush = SolidColor(DesktopTheme.accent),
        keyboardOptions = KeyboardOptions(
            imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (description == null) Modifier
                else Modifier.semantics { contentDescription = description },
            ),
        decorationBox = { field ->
            Box {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        placeholder,
                        color = DesktopTheme.inkMuted,
                        style = DesktopTheme.field,
                        maxLines = 1,
                    )
                }
                field()
            }
        },
    )
}

/**
 * The window's **one field**: a name box, a page range, a search, a preset's value.
 *
 * A field is a control, not a panel: the same 4 dp corner as a button, the `panelField` tone one step away
 * from the surface it sits on, and **one** hairline that says what the field is doing — `borderStrong` at
 * rest, the accent while it is being typed in, `danger` when its value is refused (§6). Nothing about it
 * lifts, glows or fills with glass, and the value and the placeholder are set in the same 12 sp tabular
 * face, so a number does not move when the first digit arrives.
 *
 * [onClear] adds the small `×` the contract asks for on a field the reader fills by hand; [onSubmit] makes
 * `Enter` the field's own commit rather than the shell's.
 */
@Composable
internal fun DesktopTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    error: Boolean = false,
    height: Dp = 30.dp,
    singleLine: Boolean = true,
    /** A field that is shown but not editable — the value it holds is still read, never typed over. */
    enabled: Boolean = true,
    /**
     * A field whose keys are **text**. The shell's shortcut table runs before the composition
     * ([WindowKeys]), so without this a letter typed into a bookmark title or a lane value was that
     * tool's accelerator — and picking a tool drops the selection the field belongs to.
     */
    typing: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    /** A field that owns the keyboard when something else asks for it (`Ctrl+F`'s search field). */
    focusRequester: FocusRequester? = null,
    /** The field's spoken name, for a field whose value alone does not say what it edits. */
    description: String? = null,
    /** What the owner must do when the field takes or leaves the keyboard — a search that arms a filter. */
    onFocusChange: ((Boolean) -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    /** The clear cross's spoken name, for a field whose clear says more than "Clear" (`"Clear search"`). */
    clearDescription: String = "Clear",
    /** What the field keeps right of its value and left of its clear cross — a count, two steps. */
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onSubmit: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val owner = remember { Any() }
    DisposableEffect(owner) {
        onDispose {
            WindowKeys.focusControl(owner, false)
            WindowKeys.focusField(owner, false)
        }
    }
    Row(
        modifier
            .height(height)
            .background(DesktopTheme.panelField, DesktopTheme.controlShape)
            .border(
                FIELD_BORDER,
                when {
                    error -> DesktopTheme.danger
                    focused -> DesktopTheme.accent
                    else -> DesktopTheme.borderStrong
                },
                DesktopTheme.controlShape,
            )
            .onFocusChanged {
                focused = it.isFocused
                WindowKeys.focusControl(owner, it.isFocused)
                if (typing) WindowKeys.focusField(owner, it.isFocused)
                onFocusChange?.invoke(it.isFocused)
            }
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.invoke()
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            FieldInput(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                singleLine = singleLine,
                enabled = enabled,
                onSubmit = onSubmit,
                description = description,
                focusRequester = focusRequester,
            )
        }
        trailing?.invoke(this)
        if (onClear != null && value.isNotEmpty()) {
            DesktopIconButton(description = clearDescription, onClick = onClear, size = 16.dp, tooltip = null) {
                Canvas(Modifier.size(10.dp)) { drawChromeGlyph(ChromeGlyph.CLOSE, DesktopTheme.inkSecondary) }
            }
        }
    }
}

/** One hairline, 1 dp, on every border of a field. */
private val FIELD_BORDER = 1.dp
