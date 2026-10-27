package com.mobixournal.desktop

/**
 * The **toggle** keys of the Shortcuts page: one press picks a tool, the next press puts back the tool
 * that was in hand — the tablet's stylus button, written as a keyboard command.
 *
 * The rule is here rather than in `DesktopShell` for the reason every other rule is: it is arithmetic
 * over the picked tool, so a JVM test can press it without a window. Two things make it a family rather
 * than a tool:
 *
 * - **The eraser is two members** (partial and whole-stroke), so a press when *either* is in hand is
 *   already "the eraser", and the toggle must go back rather than jump to the other eraser.
 * - **The selection is two members** too (rectangle and loop), with the same reading.
 *
 * Which member a press **picks** is the reader's answer, stored per family
 * (`DesktopSettings.toggleEraser` / `toggleSelection`) and chosen on the Shortcuts page.
 */
internal val TOGGLE_COMMANDS: Set<ShellCommand> = setOf(
    ShellCommand.TOGGLE_ERASER,
    ShellCommand.TOGGLE_HAND,
    ShellCommand.TOGGLE_SELECTION,
)

/** The tools a toggle counts as "already there" — one for the hand, a pair for the eraser and selection. */
internal fun toggleFamily(target: DesktopTool): List<DesktopTool> = when (target) {
    DesktopTool.ERASER, DesktopTool.ERASER_WHOLE ->
        listOf(DesktopTool.ERASER, DesktopTool.ERASER_WHOLE)
    DesktopTool.MARQUEE, DesktopTool.LASSO ->
        listOf(DesktopTool.MARQUEE, DesktopTool.LASSO)
    else -> listOf(target)
}

/**
 * What a toggle press picks, given the tool in hand and the one before it.
 *
 * Out of the family, the press picks [target] — the member the reader chose. Inside it, the press puts
 * back [last]: the tool the reader was writing with before the switch. A [last] that is itself in the
 * family (or one no canvas acts on) cannot be the answer, so the pen is, which is the one tool every
 * document is written with.
 */
internal fun toggledTool(current: DesktopTool, last: DesktopTool, target: DesktopTool): DesktopTool {
    val family = toggleFamily(target)
    if (current !in family) return target
    return last.takeIf { it !in family && it in DESKTOP_TOOLS_THAT_ACT } ?: DesktopTool.PEN
}
