package com.mobixournal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.FloatingSurface

/** Which compact in-app keyboard profile an anchored inspector field asks for. */
enum class LivingKeyboardKind {
    NUMERIC,
    FORMULA,
}

/**
 * Shared state for an anchored inspector's docked in-app keyboard.
 *
 * Replaces system IME `OutlinedTextField`s inside [LivingInspectorAnchor] so the full-width
 * system keyboard never covers the canvas preview or duplicates input bars.
 */
internal class LivingKeyboardController(
    private val filterInput: ((fieldId: String, nextText: String) -> String)? = null,
) {
    var activeFieldId by mutableStateOf<String?>(null)
        private set
    var kind by mutableStateOf(LivingKeyboardKind.NUMERIC)
        private set
    var field by mutableStateOf(TextFieldValue(""))
        private set
    var allowNegative by mutableStateOf(true)
        private set
    private var onCommit: ((String) -> Unit)? = null

    val isOpen: Boolean get() = activeFieldId != null

    fun open(
        fieldId: String,
        kind: LivingKeyboardKind,
        initialText: String,
        allowNegative: Boolean = true,
        onTextChange: (String) -> Unit,
    ) {
        activeFieldId = fieldId
        this.kind = kind
        this.allowNegative = allowNegative
        field = TextFieldValue(initialText, TextRange(initialText.length))
        onCommit = onTextChange
    }

    fun applyKey(key: CircuitKey) {
        val candidate = applyCircuitKey(field, key)
        val id = activeFieldId ?: return
        val filtered = filterInput?.invoke(id, candidate.text) ?: candidate.text
        field = if (filtered == candidate.text) {
            candidate
        } else {
            TextFieldValue(filtered, TextRange(filtered.length))
        }
        onCommit?.invoke(field.text)
    }

    fun moveCaret(step: Int) {
        field = moveCircuitCaret(field, step)
    }

    fun close() {
        activeFieldId = null
        onCommit = null
    }
}

internal val LocalLivingKeyboard = compositionLocalOf<LivingKeyboardController?> { null }

/**
 * Text field for plot inputs: uses [LivingDisplayField] (with docked in-app keyboard) inside
 * [PlotInspectorAnchor] and falls back to [OutlinedTextField] inside the modal creation dialog.
 */
@Composable
internal fun PlotInspectorAwareField(
    fieldId: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    kind: LivingKeyboardKind,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val keyboard = LocalLivingKeyboard.current
    if (keyboard != null) {
        val active = keyboard.activeFieldId == fieldId
        LivingDisplayField(
            label = label,
            value = if (active) keyboard.field.text else value,
            active = active,
            caretAt = if (active) keyboard.field.selection.min else null,
            isError = isError,
            onClick = {
                keyboard.open(
                    fieldId = fieldId,
                    kind = kind,
                    initialText = value,
                    allowNegative = true,
                    onTextChange = onValueChange,
                )
            },
            modifier = modifier,
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = isError,
            modifier = modifier.plotInputEcho(label, value),
        )
    }
}

/**
 * Non-focusable tappable display field for anchored inspectors: shows [label], [value] (with a
 * live caret while active), optional [suffix], and opens the docked in-app keyboard on tap.
 */
@Composable
internal fun LivingDisplayField(
    label: String,
    value: String,
    active: Boolean,
    caretAt: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    isError: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val borderColor = when {
        isError -> scheme.error
        active -> scheme.primary
        else -> scheme.outlineVariant
    }
    val containerColor = if (active) {
        scheme.primaryContainer.copy(alpha = 0.18f)
    } else {
        scheme.surfaceVariant.copy(alpha = 0.28f)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(if (active || isError) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isError -> scheme.error
                    active -> scheme.primary
                    else -> scheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shown = if (active) valueWithCaret(value, caretAt) else value
                Text(
                    text = shown.ifEmpty { "0" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value.isEmpty() && !active) scheme.onSurfaceVariant else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (suffix != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = suffix,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Docked in-app keyboard rendered at the foot of an anchored inspector's overlay layer.
 */
@Composable
internal fun LivingInspectorKeyboardBar(
    controller: LivingKeyboardController,
    modifier: Modifier = Modifier,
) {
    if (!controller.isOpen) return
    val maxWidth = when (controller.kind) {
        LivingKeyboardKind.NUMERIC -> 300.dp
        LivingKeyboardKind.FORMULA -> 460.dp
    }
    FloatingSurface(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (controller.kind) {
                LivingKeyboardKind.NUMERIC -> NumericKeyGrid(
                    allowNegative = controller.allowNegative,
                    onKey = controller::applyKey,
                )
                LivingKeyboardKind.FORMULA -> FormulaKeyGrid(onKey = controller::applyKey)
            }
            KeyboardActionRow(
                onLeft = { controller.moveCaret(-1) },
                onRight = { controller.moveCaret(1) },
                onDone = controller::close,
            )
        }
    }
}

@Composable
private fun NumericKeyGrid(
    allowNegative: Boolean,
    onKey: (CircuitKey) -> Unit,
) {
    val lastRow = listOf(
        CircuitKey.Ch("."),
        CircuitKey.Ch("0"),
        if (allowNegative) CircuitKey.Ch("-") else null,
        CircuitKey.Backspace,
    )
    val rows = listOf(
        listOf("7", "8", "9").map { CircuitKey.Ch(it) },
        listOf("4", "5", "6").map { CircuitKey.Ch(it) },
        listOf("1", "2", "3").map { CircuitKey.Ch(it) },
        lastRow,
    )
    KeyRows(rows = rows, onKey = onKey)
}

@Composable
private fun FormulaKeyGrid(onKey: (CircuitKey) -> Unit) {
    val rows: List<List<CircuitKey?>> = listOf(
        listOf("sin(", "cos(", "tan(", "exp(", "ln(", "sqrt(", "abs(").map { CircuitKey.Ch(it) },
        listOf("x", "t", "pi", "e", "(", ")", "^").map { CircuitKey.Ch(it) },
        listOf("7", "8", "9", "/", "*", "-", "+").map { CircuitKey.Ch(it) },
        listOf("4", "5", "6", "1", "2", "3").map { CircuitKey.Ch(it) } + listOf(CircuitKey.Backspace),
        listOf("0", ".").map { CircuitKey.Ch(it) },
    )
    KeyRows(rows = rows, onKey = onKey)
}

@Composable
private fun KeyRows(
    rows: List<List<CircuitKey?>>,
    onKey: (CircuitKey) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                for (key in row) {
                    if (key == null) {
                        Spacer(Modifier.weight(1f).height(38.dp))
                    } else {
                        CompactKeyButton(
                            label = key.label,
                            isAction = key is CircuitKey.Backspace,
                            onClick = { onKey(key) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyboardActionRow(
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onDone: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = scheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clickable(onClick = onLeft),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "Cursor left",
                modifier = Modifier.padding(6.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = scheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clickable(onClick = onRight),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Cursor right",
                modifier = Modifier.padding(6.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = scheme.primary,
            modifier = Modifier
                .weight(2f)
                .height(36.dp)
                .clickable(onClick = onDone),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Done", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
            }
        }
    }
}

@Composable
private fun CompactKeyButton(
    label: String,
    isAction: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isAction) scheme.secondaryContainer.copy(alpha = 0.65f) else scheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isAction) scheme.onSecondaryContainer else scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
