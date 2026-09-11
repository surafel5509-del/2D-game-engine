package com.nova.engine

/** Lightweight scene model used by the editor/runtime boundary. */
data class Transform2D(var x: Float = 0f, var y: Float = 0f, var rotation: Float = 0f, var scaleX: Float = 1f, var scaleY: Float = 1f)
data class Sprite2D(var texture: String = "", var width: Float = 64f, var height: Float = 64f, var layer: Int = 0)
data class Body2D(var dynamic: Boolean = true, var gravityScale: Float = 1f, var mass: Float = 1f)
data class Entity2D(
    val id: Int,
    var name: String,
    val transform: Transform2D = Transform2D(),
    var sprite: Sprite2D? = Sprite2D(),
    var body: Body2D? = Body2D()
)

class Scene2D(val name: String = "MainScene") {
    private val entities = LinkedHashMap<Int, Entity2D>()
    private var nextId = 1

    fun create(name: String, x: Float = 0f, y: Float = 0f): Entity2D {
        val e = Entity2D(nextId++, name, Transform2D(x, y))
        entities[e.id] = e
        return e
    }
    fun remove(id: Int) { entities.remove(id) }
    fun find(id: Int): Entity2D? = entities[id]
    fun all(): List<Entity2D> = entities.values.toList()
    fun clear() { entities.clear(); nextId = 1 }

    /** Stable, dependency-free text serialization for project saves and Git diffs. */
    fun serialize(): String = buildString {
        appendLine("NOVA_SCENE 1")
        appendLine("name=${name.replace("\\", "\\\\").replace("\n", "\\n")}")
        for (e in entities.values) {
            val s = e.sprite
            val b = e.body
            append("entity|${e.id}|${e.name.replace("|", "\\|")}|${e.transform.x}|${e.transform.y}|")
            append("${e.transform.rotation}|${e.transform.scaleX}|${e.transform.scaleY}|")
            append("${s?.texture ?: "-"}|${s?.width ?: 0f}|${s?.height ?: 0f}|${s?.layer ?: 0}|")
            appendLine("${b?.dynamic ?: false}|${b?.gravityScale ?: 0f}|${b?.mass ?: 0f}")
        }
    }
}
