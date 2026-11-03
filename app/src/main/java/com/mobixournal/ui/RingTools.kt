package com.mobixournal.ui

import com.mobixournal.design.PenPalette
import com.mobixournal.render.HudSlot
import com.mobixournal.render.RadialHud

/**
 * **The tablet's ring, as values the app can act on** — the arms of `RadialHud`'s shared ring.
 *
 * The geometry and the stored line live in `mobix-core` (`RadialHud`), because both shells draw the same ring;
 * what is the app's is the *vocabulary*: a face names its tool, and this file is where that name becomes an
 * [EditorTool], and where the reader's own faces become the ring the surface draws. A name this build has no
 * tool for is dropped, so a file written by another version cannot leave a face that does nothing.
 *
 * Nothing here touches Compose, so the rules are JVM tests (`RingToolsTest`) and the page in `RingSection.kt`
 * is only their arrangement.
 */

/** The face's tool, or null when this build has no tool of that name. */
fun ringToolFor(name: String): EditorTool? {
    val wanted = name.trim()
    if (wanted.isEmpty()) return null
    EditorTool.entries.firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.let { return it }
    // The two selections are named differently on each shell: `MARQUEE`/`LASSO` are the ring's shared names,
    // `SELECT`/`LASSO_SELECT` the tablet's own slots.
    return when (wanted.uppercase()) {
        "MARQUEE" -> EditorTool.SELECT
        "LASSO" -> EditorTool.LASSO_SELECT
        else -> null
    }
}

/**
 * The entries no toolbar offers any more — kept so an old shortcut table or a saved tool selection still
 * decodes. They are not offered as a face: a slot that does nothing is worse than an absent one.
 */
private val LEGACY_ONLY_TOOLS: Set<EditorTool> = setOf(
    EditorTool.SQUARE,
    EditorTool.CHEM_BOND_SINGLE,
    EditorTool.CHEM_BOND_DOUBLE,
    EditorTool.CHEM_BOND_TRIPLE,
    EditorTool.CHEM_BENZENE,
)

/** The tools a face may hold: every tool the rail puts in hand, so the ring can be anything the toolbar is. */
val RING_TOOL_CHOICES: List<EditorTool> = EditorTool.entries.filter { it !in LEGACY_ONLY_TOOLS }

/**
 * The faces the ring draws right now: the reader's own line when they have arranged one, the **factory ring**
 * (`RadialHud.factorySlots`) when they never have — which is what a fresh install shows.
 */
fun ringFaces(settings: AppSettings): List<HudSlot> =
    RadialHud.decodeSlots(settings.ringSlots)
        ?: RadialHud.factorySlots(settings.penColors, settings.customColor, settings.penWidths)

/**
 * [settings] with position [index] holding [face], or without that position when [face] is null — and a ring
 * left with no faces is the factory one again rather than a stored emptiness.
 */
fun ringWithFace(settings: AppSettings, index: Int, face: HudSlot?): AppSettings {
    val faces = ringFaces(settings).toMutableList()
    if (index !in faces.indices) return settings
    if (face == null) faces.removeAt(index) else faces[index] = face
    return settings.copy(ringSlots = faces.takeIf { it.isNotEmpty() }?.let(RadialHud::encodeSlots))
}

/**
 * [settings] with one more position — the first width, so a new face is always something the ring can
 * commit the moment it is added.
 */
fun ringWithPosition(settings: AppSettings): AppSettings {
    val faces = ringFaces(settings)
    if (faces.size >= RadialHud.MAX_SLOTS) return settings
    return settings.copy(
        ringSlots = RadialHud.encodeSlots(faces + HudSlot.Width(PenPalette.DEFAULT_WIDTHS.first())),
    )
}
