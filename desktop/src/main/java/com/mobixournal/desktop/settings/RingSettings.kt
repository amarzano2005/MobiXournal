package com.mobixournal.desktop.settings
import com.mobixournal.desktop.tools.GlassPopup
import com.mobixournal.desktop.tools.GlassPresence
import com.mobixournal.desktop.tools.rememberClosingValue

import com.mobixournal.desktop.shell.ShellCommand
import com.mobixournal.desktop.sheet.RingFace
import com.mobixournal.desktop.sheet.drawRing
import com.mobixournal.desktop.sheet.ringWidthDotDiameter
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.FloatingSurface
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.design.PenPalette
import com.mobixournal.render.RadialHud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * **Ring**: the ring itself, drawn between its settings, and the key that opens it.
 *
 * The page shows the ring **as it will be drawn at the pen** — one glass disc, the same faces, the same
 * order — because a list of rows cannot answer the question this page exists for: *what does my ring look
 * like?* A click on a face opens its chooser, and the chooser is the three families the ring can hold, each
 * with every answer it has:
 *
 * - **Colours** — the palette the strips carry, the custom colour included: "the colours I want" is a
 *   palette, not a second list of eight.
 * - **Tools** — *every* tool the window puts in hand, from the pen to a logic gate, so a ring slot can be
 *   anything a strip has.
 * - **Widths** — the pen's three slots, S / M / L.
 *
 * A face answered with **Remove** leaves the ring. The first pick **pins** the ring: an untouched ring is
 * automatic (the palette's first five, the three widths and the six tools, following the palette) and a ring
 * the reader arranged is theirs until **Reset to defaults**.
 *
 * Nothing here is a side-button setting: on a PC a pen's buttons arrive as keyboard keys, so the key row is
 * the whole door (`docs/desktop-settings.md`).
 */
@Composable
internal fun RingSettings(policy: DesktopSettingsState) {
    val ring = policy.ring
    SettingsGroup("The ring") {
        RingDrawing(policy)
        SettingsCommand(
            label = "Add a position",
            note = "One more face, at the end of the ring — up to ${RadialHud.MAX_SLOTS}.",
            onClick = ring::addPosition,
        )
        SettingsCommand(
            label = "Reset to defaults",
            note = "The automatic ring: the palette's first five, the three widths and the six tools.",
            onClick = ring::reset,
        )
    }
    RingSummonGroup(policy)
    SettingsNote(
        "Click a face to give it a colour, a tool or a width — or to take it off. " +
            "A ring with no faces does not open.",
    )
}

/** The ring drawn at its own size, with the chooser of whichever face was clicked. */
@Composable
private fun RingDrawing(policy: DesktopSettingsState) {
    val faces = policy.ring.slots
    var picked by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    // One place decides the ring's size: the painter and the hit test read the same radii.
    val boardPx = with(density) { BOARD.toPx() }
    val ringRadiusPx = with(density) { RadialHud.RING_RADIUS_DP.dp.toPx() }
    val slotRadiusPx = with(density) { RadialHud.SLOT_DIAMETER_DP.dp.toPx() / 2f }
    Box(Modifier.fillMaxWidth().height(BOARD + 16.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(BOARD)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(faces, boardPx) {
                        detectTapGestures { at ->
                            picked = faceAt(
                                at = at,
                                count = faces.size,
                                cx = boardPx / 2f,
                                cy = boardPx / 2f,
                                ringRadius = ringRadiusPx,
                                slotRadius = slotRadiusPx,
                            )
                        }
                    },
            ) {
                drawRing(
                    centre = Offset(size.width / 2f, size.height / 2f),
                    faces = faces,
                    active = picked,
                    ringRadius = ringRadiusPx,
                    slotRadius = slotRadiusPx,
                    hairline = 1.dp.toPx(),
                    air = 10.dp.toPx(),
                )
            }
            if (faces.isEmpty()) {
                Text(
                    "Nothing on the ring",
                    color = DesktopTheme.inkMuted,
                    style = DesktopTheme.label,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            // The **face** is what is held, not just its index: removing a position shortens the list, and
            // an index kept past the end read one place beyond it — the chooser threw `Index 13 out of
            // bounds for length 13` while it was drawing back. A face is a value; it cannot go stale.
            val shown = rememberClosingValue(picked?.let { at -> faces.getOrNull(at)?.let { at to it } })
            // The **presence is the pick**, never the held face: [rememberClosingValue] keeps the last
            // picked face for the exit spring and never clears it, so gating on it left the chooser up
            // after an answer had already dismissed it — every later pick then landed on the face that
            // had opened the menu, which read as "it always replaces the same position".
            GlassPresence(picked != null) {
                val (faceIndex, face) = shown ?: return@GlassPresence
                GlassPopup(
                    offset = IntOffset(
                        with(density) { (BOARD + 12.dp).roundToPx() },
                        with(density) { 8.dp.roundToPx() },
                    ),
                    onDismissRequest = { picked = null },
                    properties = FloatingSurface,
                ) {
                    RingFaceMenu(
                        face = face,
                        colours = policy.ring.colourChoices(),
                        tools = policy.ring.toolChoices(),
                        widths = policy.ring.widthChoices(),
                    ) { choice ->
                        policy.ring.chooseFace(faceIndex, choice)
                        picked = null
                    }
                }
            }
        }
    }
}

/**
 * The face a tap at ([at]) landed on, or null when it landed on the ring's dead zone or off it.
 *
 * The arithmetic is `RadialHud`'s — the same centres the painter used — so a click and a pick at the pen
 * cannot disagree about which face is under a point.
 */
private fun faceAt(at: Offset, count: Int, cx: Float, cy: Float, ringRadius: Float, slotRadius: Float): Int? {
    if (count <= 0) return null
    for (index in 0 until count) {
        val (x, y) = RadialHud.slotCenter(cx, cy, index, count, ringRadius)
        if (hypot(at.x - x, at.y - y) <= slotRadius) return index
    }
    return null
}

/** The ring's own size: the disc (116 dp radius, a 40 dp face, 10 dp of air) and a little margin. */
private val BOARD = 292.dp

/** The chooser's width — a list, not a page: its longest answer is a tool's name. */
private val MENU_WIDTH = 248.dp

/** The chooser's height cap: the answer lists are long, and a menu taller than the panel cannot be read. */
private val MENU_HEIGHT = 320.dp

/**
 * **One face's chooser**: the three families, each with every answer it has, and *Remove* at the end.
 *
 * It is the same glass and the same inset rows as a select's list (`SettingsSelect`), because a menu is a
 * menu in this window; what it adds is the **heads** — a face can be one of three kinds of thing, and a
 * flat list of forty answers would make the reader hunt for where the tools start.
 */
@Composable
private fun RingFaceMenu(
    face: RingFace,
    colours: List<RingFace.Colour>,
    tools: List<RingFace.Tool>,
    widths: List<RingFace.Width>,
    onPick: (RingFace?) -> Unit,
) {
    Column(
        Modifier
            .width(MENU_WIDTH)
            .heightIn(max = MENU_HEIGHT)
            .verticalScroll(rememberScrollState())
            .glassSurface(fill = DesktopTheme.settingsPanel)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        if (colours.isNotEmpty()) {
            FaceHead("Colours")
            for (choice in colours) {
                FaceRow(label = colourLabel(choice.argb), chosen = choice == face, onClick = { onPick(choice) }) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(choice.argb)))
                }
            }
        }
        if (tools.isNotEmpty()) {
            FaceHead("Tools")
            for (choice in tools) {
                FaceRow(label = choice.tool.label, chosen = choice == face, onClick = { onPick(choice) }) {
                    Canvas(Modifier.size(14.dp)) { drawToolGlyph(choice.tool, DesktopTheme.ink) }
                }
            }
        }
        if (widths.isNotEmpty()) {
            FaceHead("Widths")
            for (choice in widths) {
                FaceRow(label = widthLabel(choice.pt), chosen = choice == face, onClick = { onPick(choice) }) {
                    Box(Modifier.size(WIDTH_DOT_BOX), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(widthDot(choice.pt)).clip(CircleShape).background(DesktopTheme.chromeInk))
                    }
                }
            }
        }
        FaceHead("Leave it out")
        FaceRow(label = "Remove this position", chosen = false, onClick = { onPick(null) }) {
            Box(Modifier.size(14.dp))
        }
    }
}

/** A heading inside the chooser: what family the answers under it belong to. */
@Composable
private fun FaceHead(title: String) {
    Text(
        title,
        color = DesktopTheme.inkMuted,
        style = DesktopTheme.label,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** One answer: its mark, its name, and the accent when it is the face the ring already carries. */
@Composable
private fun FaceRow(
    label: String,
    chosen: Boolean,
    onClick: () -> Unit,
    mark: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 30.dp)
            .hoverFill(
                DesktopTheme.railSlotShape,
                base = if (chosen) DesktopTheme.activeSlot.copy(alpha = 0.35f) else Color.Transparent,
            )
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mark()
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (chosen) DesktopTheme.accent else DesktopTheme.ink,
            style = DesktopTheme.body,
            maxLines = 1,
        )
    }
}



/** The factory palette's own names, so a list of colours reads as colours and not as eight hex codes. */
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
private fun colourLabel(argb: Int): String {
    val hex = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)
    val name = SWATCH_NAMES[argb]
    return if (name == null) hex else "$name · $hex"
}

/** A width face, in the points the pen draws with. */
private fun widthLabel(pt: Float): String = String.format(Locale.ROOT, "%.2f pt", pt)

/** The square a width's dot lives in, so the three rows' marks line up whatever their size. */
private val WIDTH_DOT_BOX = 16.dp

/**
 * **A width's dot**: its diameter is the stroke's own, spread across the pen's three slots.
 *
 * The ramp itself lives in `DesktopHudPaint.ringWidthDotDiameter`, so this preview and the ring at the
 * pen cannot disagree about what S, M and L look like. It is the *widths the pen ships* rather than the
 * pointer's whole 0.5 – 15 pt range, where 0.85 and 2.6 pt came out 4.4 dp and 5.4 dp — dots the eye read
 * as one size. Across 0.85 – 2.6 pt the three land six, nine and fourteen dp apart.
 */
private fun widthDot(pt: Float): Dp = ringWidthDotDiameter(pt).dp

/**
 * **Opening it**: the key that summons the ring, armed on the spot.
 *
 * The row is the Shortcuts page's own (`ShellCommand.RADIAL_HUD`, one table, one binding), reached from here
 * because this is the page a reader comes to when the ring will not open. A pen button that a driver turns
 * into a keyboard key presses this key, which is how a PC pen reaches the ring at all.
 */
@Composable
private fun RingSummonGroup(policy: DesktopSettingsState) {
    val id = ShellCommand.RADIAL_HUD.name
    val armed = policy.armedShortcut == id
    val key = ShortcutTable.key(policy.shortcutTable, id)
    SettingsGroup("Opening it") {
        SettingsRow(
            label = "Key",
            note = "Click, then press a key — a pen button a driver turns into one works here too.",
            onClick = { policy.armShortcut(id) },
        ) {
            when {
                armed -> Text("Press a key…", color = DesktopTheme.accent, style = DesktopTheme.label)
                key.isEmpty() -> Text("None", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
                else -> ShortcutKeyChip(key)
            }
        }
    }
}
