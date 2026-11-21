package com.mobixournal.design

/**
 * Coarse horizontal size classes for adaptive chrome layout across phones, foldables, and tablets.
 */
enum class ScreenWidthClass {
    /** Narrow upright phones (`< 600 dp`). */
    COMPACT,
    /** Foldables, small tablets, and landscape phones (`600..839 dp`). */
    MEDIUM,
    /** Tablets and desktop windows (`>= 840 dp`). */
    EXPANDED,
}

/**
 * Structural layout policy derived from [ScreenWidthClass] so every chrome surface, top-bar action,
 * overflow sheet, and dialog adapts consistently from one shared contract.
 */
data class ChromeLayoutPolicy(
    val widthClass: ScreenWidthClass,
    val isCompact: Boolean,
    val showInlinePdfExport: Boolean,
    val showInlineSplitView: Boolean,
    val showInlineSearchWhenClosed: Boolean,
    val overflowSheetIsBottom: Boolean,
    val overflowSheetMaxWidthDp: Int,
    val dialogWidthFraction: Float,
    val dialogMaxWidthDp: Int,
)

/**
 * Single authoritative source for responsive chrome breakpoints and layout policy.
 */
object WindowChromePolicy {
    const val COMPACT_BREAKPOINT_DP: Int = 600
    const val EXPANDED_BREAKPOINT_DP: Int = 840

    fun widthClassFor(widthDp: Int): ScreenWidthClass = when {
        widthDp < COMPACT_BREAKPOINT_DP -> ScreenWidthClass.COMPACT
        widthDp < EXPANDED_BREAKPOINT_DP -> ScreenWidthClass.MEDIUM
        else -> ScreenWidthClass.EXPANDED
    }

    fun forWidthDp(widthDp: Int): ChromeLayoutPolicy {
        val widthClass = widthClassFor(widthDp)
        val isCompact = widthClass == ScreenWidthClass.COMPACT
        return ChromeLayoutPolicy(
            widthClass = widthClass,
            isCompact = isCompact,
            showInlinePdfExport = !isCompact,
            showInlineSplitView = !isCompact,
            showInlineSearchWhenClosed = !isCompact,
            overflowSheetIsBottom = isCompact,
            overflowSheetMaxWidthDp = if (isCompact) 420 else 328,
            dialogWidthFraction = when (widthClass) {
                ScreenWidthClass.COMPACT -> 0.94f
                ScreenWidthClass.MEDIUM -> 0.84f
                ScreenWidthClass.EXPANDED -> 0.68f
            },
            dialogMaxWidthDp = when (widthClass) {
                ScreenWidthClass.COMPACT -> 440
                ScreenWidthClass.MEDIUM -> 560
                ScreenWidthClass.EXPANDED -> 620
            },
        )
    }
}
