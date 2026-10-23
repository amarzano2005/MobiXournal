package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.FloatingSurface

/**
 * The keyboard a circuit value is written with — **a keyboard instead of the system IME**, not a strip
 * of extra symbols beside it.
 *
 * One row of the field used to carry the circuit symbols, and it made the anchored inspector grow by a
 * third while the system keyboard still covered the page for the digits and letters a value also needs.
 * So the value field no longer opens the IME at all: it opens this, docked at the foot of the canvas,
 * with the three things a component value is made of on three pages of one key grid — its **digits**
 * (`R1`), its **letters** (designators `R`, `C`, `Q`, words like `Vcc`), and its **symbols** (`Ω`, `µ`,
 * `k`, `M`, `±`, `°C`…). The pages share one layout, so the panel is a keyboard's height and the
 * inspector stays the size it was designed to be.
 *
 * The key grid is data ([CircuitKey]) and the editing is a pure function ([applyCircuitKey]), so what a
 * key does is testable on the JVM without a device — the same split the LaTeX palette and its
 * `insertSnippetAtCursor` use.
 */
enum class CircuitKeyboardPage(val label: String) {
    DIGITS("123"),
    LETTERS("ABC"),
    SYMBOLS("Ω"),
}

/** One key of the circuit keyboard: something to type, or the backspace. */
sealed interface CircuitKey {

    /** What the key shows when the page's case does not change it. */
    val label: String

    /** A character key, inserted at the field's cursor. */
    data class Ch(override val label: String) : CircuitKey

    /** Delete the selection, or the character before the cursor. */
    data object Backspace : CircuitKey {
        override val label: String = "⌫"
    }
}

/**
 * [field] with [key] applied — the whole editing model of the keyboard: a character goes in at the
 * cursor (replacing a selection, the way `insertSnippetAtCursor` does), the backspace deletes the
 * selection or the character behind the cursor, and neither ever moves the caret off the text.
 */
fun applyCircuitKey(field: TextFieldValue, key: CircuitKey): TextFieldValue = when (key) {
    is CircuitKey.Ch -> insertSnippetAtCursor(field, key.label)
    CircuitKey.Backspace -> {
        val start = field.selection.min.coerceIn(0, field.text.length)
        val end = field.selection.max.coerceIn(0, field.text.length)
        when {
            start != end -> TextFieldValue(field.text.removeRange(start, end), TextRange(start))
            start > 0 -> TextFieldValue(
                field.text.removeRange(start - 1, start),
                TextRange(start - 1),
            )
            else -> field
        }
    }
}

/** Move the caret one place left ([step] -1) or right (+1), clamped to the text. */
fun moveCircuitCaret(field: TextFieldValue, step: Int): TextFieldValue {
    val target = if (step < 0) field.selection.min - 1 else field.selection.max + 1
    return TextFieldValue(field.text, TextRange(target.coerceIn(0, field.text.length)))
}

/** Digits, the separators a tolerance or a range needs, and the backspace. */
val CIRCUIT_DIGIT_ROWS: List<List<CircuitKey?>> = listOf(
    listOf("1", "2", "3", "4", "5").map { CircuitKey.Ch(it) },
    listOf("6", "7", "8", "9", "0").map { CircuitKey.Ch(it) },
    listOf(CircuitKey.Ch("."), CircuitKey.Ch(","), CircuitKey.Ch("-"), CircuitKey.Ch("+"), CircuitKey.Backspace),
)

/**
 * The letters, lowercase here because [CircuitValueKeyboard] uppercases them for the default case.
 *
 * The grid is alphabetical rather than QWERTY: a value uses a handful of letters at a time
 * (`R1`, `Vcc`, `GND`), and hunting one down on a five-key-wide QWERTY costs more than reading it off
 * four tidy rows.
 */
val CIRCUIT_LETTER_ROWS: List<List<CircuitKey?>> = run {
    val rows = ('a'..'z').map { CircuitKey.Ch(it.toString()) }.chunked(7)
    rows.mapIndexed { index, row -> if (index == rows.lastIndex) row + CircuitKey.Backspace else row }
}

/** The circuit symbols: multipliers, units, and the operators a tolerance or a power needs. */
val CIRCUIT_SYMBOL_ROWS: List<List<CircuitKey?>> = listOf(
    listOf("Ω", "µ", "k", "M", "G", "m").map { CircuitKey.Ch(it) },
    listOf("n", "p", "V", "A", "W", "F").map { CircuitKey.Ch(it) },
    listOf("H", "Hz", "°C", "±", "≈", "×").map { CircuitKey.Ch(it) },
    listOf<CircuitKey?>(
        CircuitKey.Ch("·"),
        CircuitKey.Ch("/"),
        CircuitKey.Ch("⁻¹"),
        CircuitKey.Ch("√"),
        CircuitKey.Ch("%"),
        null,
    ),
)

/** The key rows of [page], with the letters in the case the keyboard is switched to. */
fun circuitKeyRows(page: CircuitKeyboardPage, lowercase: Boolean): List<List<CircuitKey?>> = when (page) {
    CircuitKeyboardPage.DIGITS -> CIRCUIT_DIGIT_ROWS
    CircuitKeyboardPage.SYMBOLS -> CIRCUIT_SYMBOL_ROWS
    CircuitKeyboardPage.LETTERS -> CIRCUIT_LETTER_ROWS.map { row ->
        row.map { key ->
            if (key is CircuitKey.Ch && !lowercase) key.copy(label = key.label.uppercase()) else key
        }
    }
}

/**
 * The circuit value keyboard, docked at the foot of the canvas.
 *
 * It is a **floating surface** like every other thing shown over the page for a moment
 * (`docs/interface-contract.md`), and its keys are a tool slot tall so nothing here is smaller than
 * the chrome's smallest control.
 */
@Composable
fun CircuitValueKeyboard(
    field: TextFieldValue,
    onFieldChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(CircuitKeyboardPage.DIGITS) }
    // Component designators and unit names are written in capitals (`R1`, `10 KΩ`, `GND`), the
    // multipliers in lowercase (`k`, `m`, `µ`) — which the symbol page already carries on its own.
    var lowercase by remember { mutableStateOf(false) }

    FloatingSurface(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (row in circuitKeyRows(page, lowercase)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (key in row) {
                        if (key == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            CircuitKeyCap(
                                text = key.label,
                                modifier = Modifier.weight(1f),
                                onClick = { onFieldChange(applyCircuitKey(field, key)) },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (target in CircuitKeyboardPage.entries) {
                    CircuitKeyCap(
                        text = target.label,
                        modifier = Modifier.weight(1f),
                        selected = target == page,
                        onClick = { page = target },
                    )
                }
                CircuitKeyCap(
                    text = if (lowercase) "ABC" else "abc",
                    modifier = Modifier.weight(1f),
                    onClick = { lowercase = !lowercase },
                )
                CircuitKeyCap(
                    icon = Icons.Outlined.KeyboardArrowLeft,
                    description = "Move the cursor left",
                    modifier = Modifier.weight(1f),
                    onClick = { onFieldChange(moveCircuitCaret(field, -1)) },
                )
                CircuitKeyCap(
                    icon = Icons.Outlined.KeyboardArrowRight,
                    description = "Move the cursor right",
                    modifier = Modifier.weight(1f),
                    onClick = { onFieldChange(moveCircuitCaret(field, 1)) },
                )
                CircuitKeyCap(
                    icon = Icons.Outlined.Check,
                    description = "Done",
                    modifier = Modifier.weight(1f),
                    emphasis = true,
                    onClick = onDone,
                )
            }
        }
    }
}

/** One key: a glyph or a label, a tool slot tall, with the picked page wearing the accent. */
@Composable
private fun CircuitKeyCap(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    description: String? = null,
    selected: Boolean = false,
    emphasis: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = when {
        selected -> scheme.primary.copy(alpha = 0.28f)
        emphasis -> scheme.primary.copy(alpha = 0.16f)
        else -> scheme.surfaceVariant.copy(alpha = 0.55f)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = fill,
        modifier = modifier.height(44.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = description, modifier = Modifier.height(20.dp))
            } else {
                Text(
                    text = text ?: "",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
