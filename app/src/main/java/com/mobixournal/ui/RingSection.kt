package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.design.PenPalette
import com.mobixournal.render.HudSlot
import com.mobixournal.render.RadialHud
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.rememberClosingArgument
import java.util.Locale

/**
 * **Ring**: the ring itself, drawn between its settings, and what each of its faces holds.
 *
 * The page shows the ring **as it is drawn at the pen** — one disc, the same faces, the same order — because a
 * list of rows cannot answer the question this page exists for: *what does my ring look like?* A tap on a face
 * opens its chooser, and the chooser is the three families the ring can hold, each with every answer the tablet
 * has: the palette's colours (the editable custom one included), the pen's three widths, and **any tool the
 * rail puts in hand** — so a face is never limited to the six the factory ring starts with.
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
    var picked by remember { mutableStateOf<Int?>(null) }

    SettingsGroup("The ring") {
        SettingsNote(
            "The ring as it appears at the pen tip. Tap a face to give it a colour, a tool or a width — or " +
                "to take it off. The barrel button opens it (Settings → Stylus).",
        )
        RingDrawing(faces, picked) { index -> picked = if (picked == index) null else index }
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

    // The **face** is what is held, not just its index: removing a position shortens the list, and an index
    // kept past the end would read one place beyond it.
    val shown = rememberClosingArgument(picked?.let { index -> faces.getOrNull(index)?.let { index to it } })
    DialogPresence(picked != null) {
        val (index, face) = shown ?: return@DialogPresence
        RingFaceDialog(
            face = face,
            settings = settings,
            onPick = { choice ->
                onChange(ringWithFace(settings, index, choice))
                picked = null
            },
            onDismiss = { picked = null },
        )
    }
}

/**
 * The ring at its own size: one face per slot, on the same angles the pen commits with (`RadialHud.slotCenter`),
 * which is what makes this a faithful drawing rather than an illustration of one.
 *
 * A face with no mark is a tool this build has not got, and it is drawn as an empty position — tapping it still
 * opens the chooser, which is how such a face is replaced.
 */
@Composable
private fun RingDrawing(faces: List<HudSlot>, picked: Int?, onPick: (Int) -> Unit) {
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

/**
 * **One face's chooser**: the three families the ring can hold, and *Remove* at the end.
 *
 * The families are headed rather than merged, because a face can be one of three kinds of thing and a flat list
 * of forty answers would make the reader hunt for where the tools start — the same shape the window's chooser has.
 */
@Composable
private fun RingFaceDialog(
    face: HudSlot,
    settings: AppSettings,
    onPick: (HudSlot?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("This face") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                RingHead("Colours")
                (settings.penColors + listOfNotNull(settings.customColor)).distinct().forEach { argb ->
                    RingChoice(faceLabel(argb), chosen = face == HudSlot.Colour(argb)) {
                        onPick(HudSlot.Colour(argb))
                    }
                }
                RingHead("Widths")
                PenPalette.DEFAULT_WIDTHS.forEach { pt ->
                    RingChoice(widthLabel(pt), chosen = face == HudSlot.Width(pt)) {
                        onPick(HudSlot.Width(pt))
                    }
                }
                RingHead("Tools")
                RING_TOOL_CHOICES.forEach { tool ->
                    RingChoice(tool.label, chosen = face == HudSlot.ToolFace(tool.name)) {
                        onPick(HudSlot.ToolFace(tool.name))
                    }
                }
                RingHead("Leave it out")
                RingChoice("Remove this position", chosen = false) { onPick(null) }
            }
        },
    )
}

/** A heading inside the chooser: what family the answers under it belong to. */
@Composable
private fun RingHead(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** One answer: its name, and the accent when it is the face the ring already carries. */
@Composable
private fun RingChoice(label: String, chosen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
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
