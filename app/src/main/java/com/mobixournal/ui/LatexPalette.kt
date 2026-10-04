package com.mobixournal.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/** A quick-insert scientific symbol for LaTeX formulas. */
data class LatexSymbol(val label: String, val snippet: String)

/** Categories of STEM symbols offered by the LaTeX palette. */
enum class LatexCategory(val label: String) {
    CALCULUS("Calculus"),
    GREEK("Greek"),
    OPERATORS("Operators"),
    PHYSICS("Physics"),
}

/** The curated dictionary of STEM symbols available in each category. */
val LATEX_PALETTE_SYMBOLS: Map<LatexCategory, List<LatexSymbol>> = mapOf(
    LatexCategory.CALCULUS to listOf(
        LatexSymbol("x/y", "\\frac{a}{b}"),
        LatexSymbol("√x", "\\sqrt{x}"),
        LatexSymbol("x²", "^{2}"),
        LatexSymbol("xₙ", "_{n}"),
        LatexSymbol("∫", "\\int_{a}^{b} "),
        LatexSymbol("∑", "\\sum_{i=1}^{n} "),
        LatexSymbol("lim", "\\lim_{x \\to 0} "),
        LatexSymbol("∂/∂x", "\\frac{\\partial f}{\\partial x}"),
        LatexSymbol("∞", "\\infty"),
        LatexSymbol("( )", "\\left(  \\right)"),
        LatexSymbol("[ ]", "\\left[  \\right]"),
        LatexSymbol("|x|", "\\left| x \\right|"),
    ),
    LatexCategory.GREEK to listOf(
        LatexSymbol("α", "\\alpha"),
        LatexSymbol("β", "\\beta"),
        LatexSymbol("γ", "\\gamma"),
        LatexSymbol("θ", "\\theta"),
        LatexSymbol("λ", "\\lambda"),
        LatexSymbol("μ", "\\mu"),
        LatexSymbol("π", "\\pi"),
        LatexSymbol("ρ", "\\rho"),
        LatexSymbol("σ", "\\sigma"),
        LatexSymbol("ω", "\\omega"),
        LatexSymbol("Δ", "\\Delta"),
        LatexSymbol("Ω", "\\Omega"),
    ),
    LatexCategory.OPERATORS to listOf(
        LatexSymbol("±", "\\pm"),
        LatexSymbol("×", "\\times"),
        LatexSymbol("÷", "\\div"),
        LatexSymbol("·", "\\cdot"),
        LatexSymbol("≤", "\\le"),
        LatexSymbol("≥", "\\ge"),
        LatexSymbol("≠", "\\neq"),
        LatexSymbol("≈", "\\approx"),
        LatexSymbol("∈", "\\in"),
        LatexSymbol("∉", "\\notin"),
        LatexSymbol("⊂", "\\subset"),
        LatexSymbol("∪", "\\cup"),
    ),
    LatexCategory.PHYSICS to listOf(
        LatexSymbol("v⃗", "\\vec{v}"),
        LatexSymbol("a⃗", "\\vec{a}"),
        LatexSymbol("F⃗", "\\vec{F}"),
        LatexSymbol("î", "\\hat{i}"),
        LatexSymbol("ĵ", "\\hat{j}"),
        LatexSymbol("k̂", "\\hat{k}"),
        LatexSymbol("→", "\\rightarrow"),
        LatexSymbol("⇒", "\\Rightarrow"),
        LatexSymbol("↔", "\\leftrightarrow"),
        LatexSymbol("Δt", "\\Delta t"),
        LatexSymbol("°C", "^{\\circ}\\text{C}"),
        LatexSymbol("ℏ", "\\hbar"),
    ),
)

/** Pure helper: inserts [snippet] into [current] at the active selection or cursor position. */
fun insertSnippetAtCursor(current: TextFieldValue, snippet: String): TextFieldValue {
    val text = current.text
    val start = current.selection.min.coerceIn(0, text.length)
    val end = current.selection.max.coerceIn(0, text.length)
    val newText = text.substring(0, start) + snippet + text.substring(end)
    val newCursor = start + snippet.length
    return TextFieldValue(text = newText, selection = TextRange(newCursor))
}

/**
 * Quick STEM symbol palette component with category chips and symbol insertion buttons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LatexPalette(
    selectedCategory: LatexCategory,
    onSelectCategory: (LatexCategory) -> Unit,
    onInsertSnippet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (cat in LatexCategory.entries) {
                FilterChip(
                    selected = cat == selectedCategory,
                    onClick = { onSelectCategory(cat) },
                    label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val symbols = LATEX_PALETTE_SYMBOLS[selectedCategory].orEmpty()
            for (sym in symbols) {
                Surface(
                    onClick = { onInsertSnippet(sym.snippet) },
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = sym.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Dedicated LaTeX formula dialog featuring a live text field and the quick-insert STEM symbol palette.
 */
@Composable
fun LatexDialog(
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    var activeCategory by remember { mutableStateOf(LatexCategory.CALCULUS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("LaTeX Formula") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LatexPalette(
                    selectedCategory = activeCategory,
                    onSelectCategory = { activeCategory = it },
                    onInsertSnippet = { snippet ->
                        textFieldValue = insertSnippetAtCursor(textFieldValue, snippet)
                    },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    label = { Text("LaTeX code") },
                    placeholder = { Text("\\int e^{-x^2} dx") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(textFieldValue.text) }) {
                Text("Place")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
