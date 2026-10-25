package com.mobixournal.desktop

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.format.FontDescription
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.TextElement

/**
 * The **text box's section of the inspector lane**: its face, its size and its ink.
 *
 * These are the tablet's text dialog's own controls — family, bold, italic, point size, colour — offered
 * where this shell offers an element's values, one committed field at a time. The *words* are not here:
 * the Text tool writes them in place on the sheet, which is the one thing a field in a 300 dp column could
 * not do better (`docs/drawing-tools.md`).
 *
 * Every control writes a `TextElement` the file already understands — a Pango font description, a size in
 * pt, an ARGB colour — so a restyle survives a save, reopens on the tablet with the same face, and composes
 * to the same `<text>` element Xournal++ reads. Nothing is offered that the format would drop: underline is
 * not representable (`FontDescription`'s header says why), so there is no underline control.
 */
@Composable
internal fun TextBody(text: TextElement, commit: (Element) -> Unit) {
    val face = FontDescription.parse(text.font)
    SectionCard("Face") {
        // A file may name a family this picker does not offer (`Arial`, `Sans Mono`); it is still the box's
        // family, so it joins the row as the picked answer instead of leaving nothing picked at all.
        val families = TEXT_FAMILIES + if (face.family in TEXT_FAMILIES) emptyList() else listOf(face.family)
        ChoiceRow(options = families, picked = face.family) { family ->
            if (family != face.family) commit(text.withFamily(family))
        }
        Spacer(Modifier.height(6.dp))
        SwitchRow("Bold", face.bold) { on -> commit(text.withBold(on)) }
        SwitchRow("Italic", face.italic) { on -> commit(text.withItalic(on)) }
    }
    SectionCard("Size") {
        // The same range a new box is authored in (`TextAuthoring`), so the lane cannot make a box the
        // tool could not have written.
        NumberField("size pt", text.size) { value ->
            if (value != text.size) commit(text.copy(size = value.coerceIn(TEXT_MIN_SIZE_PT, TEXT_MAX_SIZE_PT)))
        }
        Readout("face", face.compose())
    }
    SectionCard("Ink") {
        ColourRow(current = text.color) { colour -> commit(text.copy(color = colour)) }
    }
}
