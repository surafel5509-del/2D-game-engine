package com.nova.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Nova V2 document model: one serializable source of truth for editor and runtime. */
data class V2Node(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Node2D",
    var type: String = "Node2D",
    var x: Float = 0f,
    var y: Float = 0f,
    var rotation: Float = 0f,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f,
    var width: Float = 64f,
    var height: Float = 64f,
    var zIndex: Int = 0,
    var visible: Boolean = true,
    var locked: Boolean = false,
    var texture: String = "",
    var script: String = "",
    var body: String = "NONE",
    var collider: String = "NONE",
    var animation: String = "Idle",
    var parentId: String? = null
)

data class V2Scene(
    var name: String = "MainScene",
    var modified: Boolean = false,
    val nodes: MutableList<V2Node> = mutableListOf()
) {
    fun find(id: String): V2Node? = nodes.firstOrNull { it.id == id }
    fun add(node: V2Node) { nodes += node; modified = true }
    fun remove(id: String): V2Node? = nodes.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { nodes.removeAt(it).also { modified = true } }
    fun duplicate(id: String): V2Node? = find(id)?.let { source ->
        V2Node(name = "${source.name}_copy", type = source.type, x = source.x + 32f, y = source.y + 32f,
            rotation = source.rotation, scaleX = source.scaleX, scaleY = source.scaleY, width = source.width,
            height = source.height, zIndex = source.zIndex, visible = source.visible, texture = source.texture,
            script = source.script, body = source.body, collider = source.collider, animation = source.animation).also { add(it) }
    }
}

class V2DocumentStore(private val context: Context) {
    private val sceneFile get() = File(context.filesDir, "v2-main-scene.json")
    private val scriptDir get() = File(context.filesDir, "scripts").also { it.mkdirs() }

    fun save(scene: V2Scene) {
        val root = JSONObject().put("format", 2).put("name", scene.name)
        val array = JSONArray()
        scene.nodes.forEach { n ->
            array.put(JSONObject().apply {
                put("id", n.id); put("name", n.name); put("type", n.type)
                put("x", n.x); put("y", n.y); put("rotation", n.rotation)
                put("scaleX", n.scaleX); put("scaleY", n.scaleY); put("width", n.width); put("height", n.height)
                put("z", n.zIndex); put("visible", n.visible); put("locked", n.locked)
                put("texture", n.texture); put("script", n.script); put("body", n.body)
                put("collider", n.collider); put("animation", n.animation); put("parent", n.parentId)
            })
        }
        sceneFile.writeText(root.put("nodes", array).toString(2))
        scene.modified = false
    }

    fun load(): V2Scene {
        if (!sceneFile.exists()) return V2Scene()
        return runCatching {
            val root = JSONObject(sceneFile.readText())
            val scene = V2Scene(root.optString("name", "MainScene"))
            val array = root.optJSONArray("nodes") ?: JSONArray()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                scene.nodes += V2Node(
                    id = o.optString("id", UUID.randomUUID().toString()), name = o.optString("name", "Node2D"),
                    type = o.optString("type", "Node2D"), x = o.optDouble("x").toFloat(), y = o.optDouble("y").toFloat(),
                    rotation = o.optDouble("rotation").toFloat(), scaleX = o.optDouble("scaleX", 1.0).toFloat(),
                    scaleY = o.optDouble("scaleY", 1.0).toFloat(), width = o.optDouble("width", 64.0).toFloat(),
                    height = o.optDouble("height", 64.0).toFloat(), zIndex = o.optInt("z"), visible = o.optBoolean("visible", true),
                    locked = o.optBoolean("locked"), texture = o.optString("texture"), script = o.optString("script"),
                    body = o.optString("body", "NONE"), collider = o.optString("collider", "NONE"),
                    animation = o.optString("animation", "Idle"), parentId = o.optString("parent").ifEmpty { null }
                )
            }
            scene.modified = false
            scene
        }.getOrElse { V2Scene() }
    }

    fun saveScript(name: String, source: String): File {
        val safe = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val file = File(scriptDir, if (safe.endsWith(".nova")) safe else "$safe.nova")
        file.writeText(source)
        return file
    }

    fun readScript(name: String): String = runCatching { File(scriptDir, name).readText() }.getOrDefault("")
}

/** Versioned undo/redo with full state snapshots; reliable for multi-property editor operations. */
class V2History(private val limit: Int = 256) {
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()
    fun checkpoint(snapshot: String) { undo.addLast(snapshot); if (undo.size > limit) undo.removeFirst(); redo.clear() }
    fun undo(current: String): String? = undo.removeLastOrNull()?.also { redo.addLast(current) }
    fun redo(current: String): String? = redo.removeLastOrNull()?.also { undo.addLast(current) }
    fun canUndo() = undo.isNotEmpty()
    fun canRedo() = redo.isNotEmpty()
}

/** Mobile-friendly asset import/copy service. Original files remain untouched; project owns a stable copy. */
class V2AssetManager(private val context: Context) {
    private val root get() = File(context.filesDir, "project/assets").also { it.mkdirs() }
    fun import(uri: android.net.Uri, name: String): File? = runCatching {
        val safe = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val out = File(root, safe)
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
        out
    }.getOrNull()
    fun list(): List<File> = root.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()
    fun delete(name: String): Boolean = File(root, name).delete()
    fun sizeBytes(): Long = list().sumOf { it.length() }
}
