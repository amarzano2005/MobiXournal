package com.mobixournal.render

/**
 * A living entity this build could not turn into ink — a formula it cannot evaluate, a symbol it has
 * no drawing for. Reported rather than dropped: an entity that vanishes silently is indistinguishable
 * from one that was never there.
 */
data class Uncompilable(val type: String, val reason: String)
