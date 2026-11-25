package com.mobixournal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.PlotShading
import com.mobixournal.ui.theme.liquidGlassContentSize

/**
 * The **shaded integral area** control of the plot editor: the switch that decides whether a plot stands
 * for an area at all, and the pair of bounds it stands for when it does.
 *
 * It is its own file because it is its own question — the rest of the dialog edits a *curve*, while this
 * edits the **region** the curve's integral is taken over, and the two are read in different places
 * (`PlotInk` draws the wash, `FunctionAnalysis` reports the integral, the `.xopp` synthesis compiles both
 * from the one element — `docs/math-engine.md`, section 3). Keeping the state here means the dialog holds
 * one value ([PlotShadeState.value]) instead of six, and the control is drawn from that value alone.
 *
 * @param initial the area the element already carries, or null for a plot with none.
 * @param initialFrom the element's own domain start, used when the switch is turned on with nothing typed.
 * @param initialTo the element's own domain end.
 */
internal class PlotShadeState(
    initial: PlotShading?,
    private val initialFrom: Double,
    private val initialTo: Double,
) {

    /** Whether the plot stands for an area at all. */
    var on by mutableStateOf(initial != null)
        private set

    /** The lower bound as typed; a value the plot's own domain supplies until it is edited. */
    var fromText by mutableStateOf(formatRangeNumber(initial?.from ?: initialFrom))
        private set

    /** The upper bound as typed. */
    var toText by mutableStateOf(formatRangeNumber(initial?.to ?: initialTo))
        private set

    /** The area the control currently describes, or null when it describes none — what is written back. */
    val value: PlotShading?
        get() {
            val from = fromText.toDoubleOrNull() ?: return null
            val to = toText.toDoubleOrNull() ?: return null
            return if (on && to > from) PlotShading(from, to) else null
        }

    /** False only while the switch is on and the pair cannot be read as an area — the confirm gate. */
    val ok: Boolean get() = !on || value != null

    /**
     * Turn the area on or off. Turning it **on** with an unreadable pair takes the plot's own domain, so
     * the control never comes up empty and an edit always has bounds to write.
     */
    fun toggle(next: Boolean) {
        if (next && fromText.toDoubleOrNull() == null) fromText = formatRangeNumber(initialFrom)
        if (next && toText.toDoubleOrNull() == null) toText = formatRangeNumber(initialTo)
        on = next
    }

    fun editFrom(text: String) {
        fromText = text
    }

    fun editTo(text: String) {
        toText = text
    }
}

/**
 * The switch and, once it is on, the bounds — drawn only when they mean something, since a wash no curve
 * is drawn under would be an area of nothing.
 */
@Composable
internal fun PlotShadeInputs(state: PlotShadeState) {
    Column(
        modifier = Modifier.liquidGlassContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = state.on,
            onClick = { state.toggle(!state.on) },
            label = { Text("Shade integral area") },
        )
        AnimatedVisibility(
            visible = state.on,
            enter = fadeIn(tween(180)) + expandVertically(),
            exit = fadeOut(tween(140)) + shrinkVertically(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlotInspectorAwareField(
                    fieldId = "plot_shade_from",
                    label = "shade from",
                    value = state.fromText,
                    onValueChange = state::editFrom,
                    kind = LivingKeyboardKind.NUMERIC,
                    modifier = Modifier.weight(1f),
                )
                PlotInspectorAwareField(
                    fieldId = "plot_shade_to",
                    label = "shade to",
                    value = state.toText,
                    onValueChange = state::editTo,
                    kind = LivingKeyboardKind.NUMERIC,
                    isError = !state.ok,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
