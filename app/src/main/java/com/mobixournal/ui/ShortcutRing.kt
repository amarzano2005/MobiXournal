package com.mobixournal.ui

import com.mobixournal.render.HudTrigger
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.summonRadialHud

/**
 * The **ring's own shortcut**: one key that calls the pen-tip ring up, the same gesture the barrel button and
 * a double-click already have (`DrawingSurfaceHud`).
 *
 * It is a named door rather than an inline call so the key router stays one line per shortcut
 * (`EditorRegions.kt`, which `SourceStructureTest` holds to its ceiling) and so the trigger the keyboard opens
 * is a decision made in one place: a ring summoned from a key is a **tap** ring, because there is no held
 * button to release — the same reason the window's `Alt + B` ring is a picker.
 */
internal fun DrawingSurfaceView.summonRingForShortcut() = summonRadialHud(HudTrigger.TAP)
