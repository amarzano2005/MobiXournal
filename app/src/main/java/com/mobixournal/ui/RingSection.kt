package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.design.PenPalette
import com.mobixournal.render.HudSlot
import com.mobixournal.render.RadialHud
import java.util.Locale

/**
 * **Ring**: the ring itself, drawn between its settings, and what each of its faces holds.
 *
 * The page shows the ring **as it is drawn at the pen** — one disc, the same faces, the same order — because a
 * list of rows cannot answer the question this page exists for: *what does my ring look like?* A tap picks a
 * face, and the row under the drawing says what that position holds.
 *
 * That row is a **select** ([OptionGroup]), the app's one grammar for a choice: the answer in hand at the end
 * of one line, the rest in a list under it. The face used to open a dialog of three headed columns, which was
 * the radio-column shape this screen has moved away from everywhere else — a page that answered its one
 * question in the old language read as an older screen.
 *
 * The first change **pins** the ring: an untouched ring is the factory one (`RadialHud.factorySlots`, the same
 * ring the window draws) and a ring arranged here is the reader's until *Reset to defaults*. The stored shape is
 * one line (`AppSettings.ringSlots`, `RadialHud.encodeSlots`), which is the line the window writes too.
 *
 * What opens it is not here: on a tablet the ring is the barrel button's (Settings → Stylus).
 */
@Composable
fun RingSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val faces = ringFaces(settings)
    // The face in hand: the first one until a tap says otherwise, so the select always has a subject and the
    // page has no state a reader can be stuck in.
    var picked by remember { mutableStateOf(0) }
    val index = picked.coerceIn(0, (faces.size - 1).coerceAtLeast(0))

    SettingsGroup("The ring") {
        SettingsNote(
            "The ring as it appears at the pen tip. Tap a face, then choose what it holds; the barrel " +
                "button opens it (Settings → Stylus).",
        )
        if (faces.isNotEmpty()) {
            RingDrawing(faces, index) { tapped -> picked = tapped }
            SettingsDivider()
            OptionGroup<HudSlot?>(
                title = "Face ${index + 1} of ${faces.size}",
                subtitle = "What this position holds.",
                options = ringAnswers(settings),
                selected = faces[index],
                label = ::ringAnswerLabel,
                onSelect = { answer -> onChange(ringWithFace(settings, index, answer)) },
            )
        }
        if (faces.size < RadialHud.MAX_SLOTS) {
            OutlinedButton(
                onClick = { onChange(ringWithPosition(settings)) },
                modifier = Modifier.settingsActionPill(top = 8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add a position")
            }
        }
        if (settings.ringSlots != null) {
            TextButton(
                onClick = { onChange(settings.copy(ringSlots = null)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset to defaults")
            }
        }
    }
}

/**
 * Every answer a face can hold, then the one that takes it off the ring.
 *
 * One list rather than three: a face *is* one of these things, so the select that answers it is one control —
 * the same "one line per choice" the rest of Settings keeps. The tools are the rail's own ([RING_TOOL_CHOICES]),
 * so a face can be anything the toolbar puts in hand, and `null` is the last entry.
 */
private fun ringAnswers(settings: AppSettings): List<HudSlot?> {
    val colours = (settings.penColors + listOfNotNull(settings.customColor)).distinct()
    return colours.map { HudSlot.Colour(it) } +
        PenPalette.DEFAULT_WIDTHS.map { HudSlot.Width(it) } +
        RING_TOOL_CHOICES.map { HudSlot.ToolFace(it.name) } +
        listOf(null)
}

/** An answer's name: a swatch is its name and hex, a width its points, a tool its own label. */
private fun ringAnswerLabel(answer: HudSlot?): String = when (answer) {
    null -> "Remove this position"
    is HudSlot.Colour -> faceLabel(answer.argb)
    is HudSlot.Width -> widthLabel(answer.pt)
    is HudSlot.ToolFace -> ringToolFor(answer.tool)?.label ?: answer.tool
}

/**
 * The ring at its own size: one face per slot, on the same angles the pen commits with (`RadialHud.slotCenter`),
 * which is what makes this a faithful drawing rather than an illustration of one.
 *
 * The face in hand is lit, and tapping another hands it the row below. A face with no mark is a tool this build
 * has not got: it is drawn as an empty position, and choosing for it is how it is replaced.
 */
@Composable
private fun RingDrawing(faces: List<HudSlot>, picked: Int, onPick: (Int) -> Unit) {
    val board = BOARD
    val slot = RadialHud.SLOT_DIAMETER_DP.dp
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(board)) {
            faces.forEachIndexed { index, face ->
                val (ux, uy) = RadialHud.slotCenter(0f, 0f, index, faces.size, 1f)
                val centreX = board / 2f + RadialHud.RING_RADIUS_DP.dp * ux
                val centreY = board / 2f + RadialHud.RING_RADIUS_DP.dp * uy
                Box(
                    Modifier
                        .offset(x = centreX - slot / 2f, y = centreY - slot / 2f)
                        .size(slot)
                        .clip(CircleShape)
                        .clickable { onPick(index) }
                        .background(
                            if (picked == index) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    RingFaceMark(face)
                }
            }
        }
    }
}

/** The ring's own size: the disc (116 dp radius, a 40 dp face) and a little margin, as the window draws it. */
private val BOARD = 292.dp

/** One face's mark, read without a label: a swatch is its colour, a width is a dot of that width, a tool is its glyph. */
@Composable
private fun RingFaceMark(face: HudSlot) {
    when (face) {
        is HudSlot.Colour -> Box(
            Modifier.size(32.dp).clip(CircleShape).background(Color(face.argb)),
        )
        is HudSlot.Width -> Box(
            Modifier.size(widthDot(face.pt)).clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface),
        )
        is HudSlot.ToolFace -> {
            val tool = ringToolFor(face.tool)
            if (tool != null) {
                Icon(tool.icon, contentDescription = tool.label, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A width's dot: 6 dp at the thinnest slot, 14 dp at the widest — S, M and L told apart before a label is read. */
private fun widthDot(pt: Float): Dp {
    val smallest = PenPalette.DEFAULT_WIDTHS.min()
    val largest = PenPalette.DEFAULT_WIDTHS.max()
    val t = ((pt - smallest) / (largest - smallest)).coerceIn(0f, 1f)
    return (6f + 8f * t).dp
}

/** The factory palette's own names, so a list of colours reads as colours and not as hex codes. */
private val SWATCH_NAMES: Map<Int, String> = mapOf(
    PenPalette.BLACK to "Black",
    PenPalette.RED to "Red",
    PenPalette.GREEN to "Green",
    PenPalette.BLUE to "Blue",
    PenPalette.ORANGE to "Orange",
    PenPalette.YELLOW to "Yellow",
    PenPalette.MAGENTA to "Magenta",
    PenPalette.WHITE to "White",
)

/** A swatch: its name when it is one of the factory eight, then its hex. */
private fun faceLabel(argb: Int): String {
    val hex = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)
    return SWATCH_NAMES[argb]?.let { name -> "$name · $hex" } ?: hex
}

/** A width face, in the points the pen draws with. */
private fun widthLabel(pt: Float): String = String.format(Locale.ROOT, "%.2f pt", pt)
