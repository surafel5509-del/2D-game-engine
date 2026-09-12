package com.nova.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.ArrayDeque

/** Production editor services: commands, scene persistence, validation and runtime state. */
interface EditorCommand { fun execute(); fun undo(); val label: String }

class CommandHistory(private val capacity: Int = 200) {
    private val undoStack = ArrayDeque<EditorCommand>()
    private val redoStack = ArrayDeque<EditorCommand>()
    fun execute(command: EditorCommand) { command.execute(); undoStack.addLast(command); redoStack.clear(); trim() }
    fun undo(): Boolean = if (undoStack.isEmpty()) false else undoStack.removeLast().also { it.undo(); redoStack.addLast(it) }.let { true }
    fun redo(): Boolean = if (redoStack.isEmpty()) false else redoStack.removeLast().also { it.execute(); undoStack.addLast(it) }.let { true }
    fun clear() { undoStack.clear(); redoStack.clear() }
    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()
    fun undoCount() = undoStack.size
    fun redoCount() = redoStack.size
    private fun trim() { while (undoStack.size > capacity) undoStack.removeFirst() }
}

class MoveEntityCommand(private val entity: EditorEntity, private val nx: Float, private val ny: Float) : EditorCommand {
    private val ox = entity.x; private val oy = entity.y
    override val label = "Move ${entity.name}"
    override fun execute() { entity.x = nx; entity.y = ny }
    override fun undo() { entity.x = ox; entity.y = oy }
}

class EntityCollection(private val history: CommandHistory = CommandHistory()) {
    private val items = LinkedHashMap<Int, EditorEntity>()
    fun add(entity: EditorEntity) { items[entity.id] = entity }
    fun remove(id: Int): EditorEntity? = items.remove(id)
    fun get(id: Int) = items[id]
    fun all() = items.values.toList()
    fun move(id: Int, x: Float, y: Float) { items[id]?.let { history.execute(MoveEntityCommand(it, x, y)) } }
    fun undo() = history.undo()
    fun redo() = history.redo()
    fun history() = history
}

object ScenePersistence {
    const val SCHEMA = 5
    fun encode(name: String, entities: List<EditorEntity>): String {
        val root = JSONObject().put("schema", SCHEMA).put("scene", name)
        val nodes = JSONArray()
        entities.forEach { e ->
            nodes.put(JSONObject()
                .put("id", e.id).put("name", e.name)
                .put("x", e.x.toDouble()).put("y", e.y.toDouble())
                .put("width", e.width.toDouble()).put("height", e.height.toDouble())
                .put("visible", e.visible).put("locked", e.locked)
                .put("layer", e.layer).put("texture", e.textureId).put("tag", e.tag))
        }
        return root.put("entities", nodes).toString(2)
    }
    fun save(file: File, name: String, entities: List<EditorEntity>) { file.parentFile?.mkdirs(); file.writeText(encode(name, entities)) }
    fun decode(text: String): Pair<String, List<EditorEntity>> {
        val root = JSONObject(text)
        require(root.optInt("schema", 0) in 1..SCHEMA) { "Unsupported scene schema" }
        val name = root.optString("scene", "MainScene")
        val nodes = root.optJSONArray("entities") ?: JSONArray()
        val result = ArrayList<EditorEntity>(nodes.length())
        for (i in 0 until nodes.length()) {
            val n = nodes.getJSONObject(i)
            result += EditorEntity(n.getInt("id"), n.optString("name", "Node2D"), n.optDouble("x").toFloat(), n.optDouble("y").toFloat(), n.optDouble("width", 96.0).toFloat(), n.optDouble("height", 96.0).toFloat(), n.optBoolean("visible", true), n.optBoolean("locked", false), n.optInt("layer", 0), n.optString("texture", "player"), n.optString("tag", "Node2D"))
        }
        return name to result
    }
    fun load(file: File) = decode(file.readText())
}

data class RuntimeConfig(var fixedHz: Int = 60, var timeScale: Float = 1f, var paused: Boolean = false, var maxFrameDelta: Float = .25f)
class RuntimeSession {
    val config = RuntimeConfig()
    private var accumulator = 0f
    var simulationTime = 0.0
        private set
    fun advance(deltaSeconds: Float, fixedStep: (Float) -> Unit): Int {
        if (config.paused) return 0
        val dt = deltaSeconds.coerceIn(0f, config.maxFrameDelta) * config.timeScale.coerceIn(0f, 8f)
        accumulator += dt
        val step = 1f / config.fixedHz.coerceIn(1, 240)
        var count = 0
        while (accumulator >= step && count < 8) { fixedStep(step); accumulator -= step; simulationTime += step; count++ }
        return count
    }
    fun reset() { accumulator = 0f; simulationTime = 0.0 }
}

object ProjectValidator {
    data class Issue(val severity: Severity, val code: String, val message: String)
    enum class Severity { ERROR, WARNING, INFO }
    fun validate(project: EditorProject, entities: List<EditorEntity>): List<Issue> {
        val issues = mutableListOf<Issue>()
        if (project.projectName.isBlank()) issues += Issue(Severity.ERROR, "PROJECT_NAME", "Project name is empty")
        if (entities.isEmpty()) issues += Issue(Severity.WARNING, "EMPTY_SCENE", "Scene contains no entities")
        val ids = entities.map { it.id }
        if (ids.size != ids.toSet().size) issues += Issue(Severity.ERROR, "DUPLICATE_ID", "Scene contains duplicate entity IDs")
        entities.filter { it.width <= 0f || it.height <= 0f }.forEach { issues += Issue(Severity.ERROR, "INVALID_SIZE", "${it.name} has an invalid size") }
        project.assets.filter { it.path.isBlank() }.forEach { issues += Issue(Severity.WARNING, "ASSET_PATH", "Asset ${it.id} has no path") }
        if (issues.none { it.severity == Severity.ERROR }) issues += Issue(Severity.INFO, "VALID", "Project validation passed")
        return issues
    }
}

object BuildProfile {
    enum class Target { DEBUG, RELEASE, PLAY_AAB }
    data class Settings(val target: Target, val minify: Boolean, val shrinkResources: Boolean, val debuggable: Boolean)
    fun settings(target: Target) = when (target) {
        Target.DEBUG -> Settings(target, false, false, true)
        Target.RELEASE -> Settings(target, true, true, false)
        Target.PLAY_AAB -> Settings(target, true, true, false)
    }
}
