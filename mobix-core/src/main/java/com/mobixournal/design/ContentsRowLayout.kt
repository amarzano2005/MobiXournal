package com.mobixournal.design

/**
 * **How one PDF Contents row is laid out, and what it says** — the indent a nesting level buys, the column
 * the page number keeps, and the sentence a reader hears — one rule for both shells, so the tablet's card
 * and the window's sidebar read the same outline the same way.
 *
 * Three things went wrong with a row that had no rule of its own. The indent was `depth × 14 dp` with the
 * depth capped at 8, so a deeply nested bookmark pushed its title a sixth of the way across a split-screen
 * pane and, worse, a **four-digit** page number sat wherever the title happened to end: the numbers formed
 * a ragged edge, and a long title on one row moved the number on the next. And a chapter heading read
 * exactly like the section under it, the indent being the only difference between them.
 *
 * So the indent is capped by the room the row actually has (a title never drops under [MIN_TITLE_DP], and
 * the indent gives way before the title does), the number gets a column reserved from the widest number in
 * the list — an empty one on a row that leads nowhere, which is what keeps the titles' right edge straight —
 * and a depth of 0 is a [isHeading] row, which each shell sets in its own heading type.
 *
 * These are computed from the row's **own** width, not the screen's: the sidebar, the card, a split-screen
 * pane and a 150 % text scale all hand the list a different number, and that number is the honest measure.
 */
object ContentsRowLayout {

    /** What one nesting level indents a row by, before the cap. */
    const val STEP_DP: Float = 14f

    /** The narrowest a title may be made by indenting: past this, the indent stops growing. */
    const val MIN_TITLE_DP: Float = 96f

    /** One digit of a page number at the row's label size, tabular figures included. */
    const val DIGIT_DP: Float = 8f

    /**
     * The branch control's slot — the interface contract's own touch target, not a smaller arrow the thumb
     * has to find.
     */
    const val BRANCH_DP: Float = 40f

    /** The air between the title and the page number's column. */
    const val GAP_DP: Float = 10f

    /**
     * The gutter a row's own action control keeps — one slot, reserved on **every** row so the titles end on
     * one edge, and empty on a row the file owns (a source-PDF row offers no command it cannot run).
     */
    const val ACTION_DP: Float = 40f

    /** The page column never takes more than this share of a narrow row: the title matters more. */
    const val MAX_COLUMN_SHARE: Float = 0.3f

    /** A chapter heading (depth 0) rather than a section under it — the one typographic step. */
    fun isHeading(depth: Int): Boolean = depth <= 0

    /** How many digits the widest page number in a list prints. */
    fun digitsOf(largestPageNumber: Int): Int = largestPageNumber.coerceAtLeast(1).toString().length

    /**
     * The width the page-number column is reserved at, for a list whose widest number is
     * [largestPageNumber] in a row [rowWidthDp] wide — the same for every row, so the numbers line up
     * under each other whether a row has three digits, four, or none at all.
     *
     * [DIGIT_DP] is a digit at 100 % text, so the column follows [fontScale] the way the glyphs do: a
     * reader at 150 % has digits half again as wide, and a column fixed in dp would clip the fourth one.
     */
    fun pageColumnDp(largestPageNumber: Int, rowWidthDp: Float, fontScale: Float = 1f): Float {
        val scale = fontScale.coerceAtLeast(1f)
        val wanted = digitsOf(largestPageNumber) * DIGIT_DP * scale + GAP_DP
        return wanted.coerceAtMost((rowWidthDp * MAX_COLUMN_SHARE).coerceAtLeast(GAP_DP))
    }

    /** How far a row at [depth] is indented in [rowWidthDp] of room with a [pageColumnDp] column kept. */
    fun indentDp(depth: Int, rowWidthDp: Float, pageColumnDp: Float): Float {
        val room = (rowWidthDp - pageColumnDp - MIN_TITLE_DP).coerceAtLeast(0f)
        return (depth.coerceAtLeast(0) * STEP_DP).coerceAtMost(room)
    }

    /**
     * The indentation a row takes once its **whole** budget is written down: the branch gutter, the action
     * gutter and the page column are room the indent cannot spend, and the title keeps [MIN_TITLE_DP].
     */
    fun indentInRow(
        depth: Int,
        rowWidthDp: Float,
        pageColumnDp: Float,
        branchDp: Float = BRANCH_DP,
        actionDp: Float = ACTION_DP,
    ): Float {
        val room = (rowWidthDp - branchDp - actionDp - pageColumnDp - MIN_TITLE_DP).coerceAtLeast(0f)
        return (depth.coerceAtLeast(0) * STEP_DP).coerceAtMost(room)
    }

    /**
     * The width a title actually gets: what the controls, the indent and the page column leave of the row —
     * never a negative number, which is what tells a caller the row is too narrow to hold its row.
     */
    fun titleWidthDp(
        depth: Int,
        rowWidthDp: Float,
        pageColumnDp: Float,
        branchDp: Float = BRANCH_DP,
        actionDp: Float = ACTION_DP,
    ): Float = (
        rowWidthDp - branchDp - actionDp - pageColumnDp -
            indentInRow(depth, rowWidthDp, pageColumnDp, branchDp, actionDp)
        ).coerceAtLeast(0f)

    /**
     * What a screen reader hears for one row: the title, the page it leads to, and — when it leads nowhere
     * — that it leads nowhere, so a dead row is not announced as a button that will not open.
     */
    fun announcement(title: String, pageNumber: Int?): String =
        if (pageNumber == null) "$title, no page" else "$title, page $pageNumber"
}
