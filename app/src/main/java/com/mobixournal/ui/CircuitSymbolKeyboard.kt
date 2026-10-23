package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The symbol keys a circuit value is written with — `10 kΩ`, `4.7 µF`, `±5 %` — grouped the way a
 * component is read: its multiplier, then its unit, then the few operators a tolerance or a range
 * needs.
 *
 * It is a **palette beside the field**, not a replacement for the system IME: a value is mostly
 * digits and letters, and the tablet's own keyboard already has those. This row is for the keys an
 * ordinary keyboard on a tablet either hides or makes awkward — Ω, µ, ⁻¹ — which is the same split
 * the LaTeX palette makes, and the same control (`LatexPalette`).
 */
val CIRCUIT_SYMBOL_ROWS: List<List<String>> = listOf(
    // Multipliers: `10 kΩ` and `4.7 µF` are written with these.
    listOf("k", "M", "G", "m", "µ", "n", "p"),
    // Units a component's value carries.
    listOf("Ω", "V", "A", "W", "F", "H", "Hz", "°C"),
    // Operators a tolerance, a range or a power needs.
    listOf("±", "≈", "×", "·", "/", "⁻¹", "√", "%"),
)

/**
 * The circuit value keyboard: one tappable key per entry of [CIRCUIT_SYMBOL_ROWS].
 *
 * Each key inserts its character at the field's own cursor ([insertSnippetAtCursor]), so a value is
 * corrected where the user is typing rather than only appended to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CircuitSymbolKeyboard(
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Symbols",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (row in CIRCUIT_SYMBOL_ROWS) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (symbol in row) {
                    Surface(
                        onClick = { onInsert(symbol) },
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = symbol,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
