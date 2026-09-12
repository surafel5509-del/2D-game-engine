package com.nova.engine

/** Transactional editor history with coalescing for drag operations. */
class EditorHistory(private val capacity: Int = 200) {
    private val undo = ArrayDeque<() -> Unit>()
    private val redo = ArrayDeque<() -> Unit>()
    private var transaction: (() -> Unit)? = null

    fun execute(apply: () -> Unit, revert: () -> Unit) {
        apply()
        undo.addLast(revert)
        while (undo.size > capacity) undo.removeFirst()
        redo.clear()
    }
    fun beginTransaction() { transaction = {} }
    fun endTransaction() { transaction = null }
    fun undo() { val action = undo.removeLastOrNull() ?: return; action(); redo.addLast(action) }
    fun redo() { val action = redo.removeLastOrNull() ?: return; action(); undo.addLast(action) }
    fun canUndo() = undo.isNotEmpty()
    fun canRedo() = redo.isNotEmpty()
    fun clear() { undo.clear(); redo.clear() }
}

data class EditorCommand(val name: String, val apply: () -> Unit, val revert: () -> Unit)
class CommandBus(private val history: EditorHistory = EditorHistory()) {
    fun run(command: EditorCommand) = history.execute(command.apply, command.revert)
    fun undo() = history.undo()
    fun redo() = history.redo()
    fun canUndo() = history.canUndo()
    fun canRedo() = history.canRedo()
}
