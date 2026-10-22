package com.mobixournal.render

/**
 * A generic undo/redo history over immutable snapshots of type [T]. Pure and Android-free so it's
 * unit-testable, and now shared: the Android editor parameterises it with the whole `Document` and the
 * desktop editor will do the same, one history per tab. Each edit calls [record] with the pre-edit
 * snapshot; [undo]/[redo] move between branches. Recording a new edit discards the redo branch, as in
 * every editor.
 */
class EditHistory<T>(private val maxDepth: Int = DEFAULT_MAX_DEPTH) {
    companion object {
        /**
         * How many undo steps to keep. Snapshots are structurally shared immutable documents, so a
         * step costs little more than the elements the edit touched; this is deep enough to cover a
         * long drawing session while still bounding memory on a tablet.
         */
        const val DEFAULT_MAX_DEPTH = 200
    }

    init {
        require(maxDepth > 0) { "maxDepth must be positive" }
    }

    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    /**
     * The **identity of the state the caller is in now** — a name for "this version of the document",
     * not a count of steps.
     *
     * It exists so that "is the document dirtier than the file?" is a question about the document and
     * not a boolean somebody has to remember to clear: the editor records the id at a successful save
     * (the *save point*) and compares it afterwards. Undoing back to a saved state returns the same id,
     * so the document is clean again without anyone counting; a new edit, or a new branch after a redo
     * was dropped, gets a fresh one, so it cannot be mistaken for the saved state even when the bytes
     * happen to be identical.
     *
     * The ids travel on their own stacks, in step with the snapshots, and [clear] starts a new state.
     */
    var stateId: Long = 0L
        private set

    /** The state id each snapshot in [undoStack] / [redoStack] restores, index for index. */
    private val undoIds = ArrayDeque<Long>()
    private val redoIds = ArrayDeque<Long>()

    /** The id the next recorded state gets — never reused, so no two states of one history collide. */
    private var nextState = 1L

    /** True when there is at least one undo step available. */
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    /** True when there is at least one redo step available. */
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Record that the state changed away from [before]; clears any redo branch. Once the history is
     * [maxDepth] deep the oldest step is dropped, so the newest edits are always undoable.
     * @param before State before the edit (to restore on undo).
     */
    fun record(before: T) {
        undoStack.addLast(before)
        undoIds.addLast(stateId)
        while (undoStack.size > maxDepth) {
            undoStack.removeFirst()
            undoIds.removeFirst()
        }
        redoStack.clear()
        redoIds.clear()
        stateId = freshState()
    }

    /**
     * The snapshot to restore for an undo, pushing [current] onto the redo branch; null if empty.
     * @param current Current state before undo.
     * @return Previous state to restore, or null if no undo available.
     */
    fun undo(current: T): T? {
        // The ids are the stacks' own length, so an empty history is still what decides emptiness here.
        val previousId = undoIds.removeLastOrNull() ?: return null
        val prev = undoStack.removeLast()
        redoStack.addLast(current)
        redoIds.addLast(stateId)
        stateId = previousId
        return prev
    }

    /**
     * The snapshot to restore for a redo, pushing [current] onto the undo branch; null if empty.
     * @param current Current state before redo.
     * @return Next state to restore, or null if no redo available.
     */
    fun redo(current: T): T? {
        val nextId = redoIds.removeLastOrNull() ?: return null
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        undoIds.addLast(stateId)
        stateId = nextId
        return next
    }

    /** Clear both undo and redo stacks, and start a state that is nobody's earlier state. */
    fun clear() {
        undoStack.clear()
        undoIds.clear()
        redoStack.clear()
        redoIds.clear()
        stateId = freshState()
    }

    private fun freshState(): Long = nextState++
}
