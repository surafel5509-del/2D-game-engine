package com.nova.engine

/** Transactional command history for editor operations. */
class EditorHistory(private val capacity: Int = 200) {
    private data class Entry(val apply: () -> Unit, val revert: () -> Unit)
    private val undo = ArrayDeque<Entry>()
    private val redo = ArrayDeque<Entry>()

    fun execute(apply: () -> Unit, revert: () -> Unit) {
        apply(); undo.addLast(Entry(apply, revert)); while (undo.size > capacity) undo.removeFirst(); redo.clear()
    }
    fun undo() { val e = undo.removeLastOrNull() ?: return; e.revert(); redo.addLast(e) }
    fun redo() { val e = redo.removeLastOrNull() ?: return; e.apply(); undo.addLast(e) }
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
