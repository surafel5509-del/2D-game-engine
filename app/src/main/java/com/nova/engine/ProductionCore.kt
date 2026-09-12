package com.nova.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

data class NovaVec2(var x: Float = 0f, var y: Float = 0f)
data class NovaRect(var x: Float = 0f, var y: Float = 0f, var w: Float = 1f, var h: Float = 1f)

data class RuntimeNode(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Node2D",
    var position: NovaVec2 = NovaVec2(),
    var rotation: Float = 0f,
    var scale: NovaVec2 = NovaVec2(1f, 1f),
    var visible: Boolean = true,
    var zIndex: Int = 0,
    var parentId: String? = null,
    var texture: String = "",
    var script: String? = null,
    var bodyType: String = "NONE",
    var collider: String = "NONE",
    val children: MutableList<RuntimeNode> = mutableListOf()
)

class RuntimeScene(var name: String = "MainScene") {
    val roots = mutableListOf<RuntimeNode>()
    var modified = false

    fun all(): List<RuntimeNode> {
        val out = mutableListOf<RuntimeNode>()
        fun visit(node: RuntimeNode) {
            out += node
            node.children.forEach(::visit)
        }
        roots.forEach(::visit)
        return out
    }

    fun find(id: String): RuntimeNode? = all().firstOrNull { it.id == id }

    fun add(node: RuntimeNode, parent: String? = null) {
        val parentNode = parent?.let(::find)
        if (parentNode == null) roots += node else parentNode.children += node
        node.parentId = parent
        modified = true
    }

    fun remove(id: String): RuntimeNode? {
        fun removeFrom(list: MutableList<RuntimeNode>): RuntimeNode? {
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) return list.removeAt(index)
            for (child in list) {
                val removed = removeFrom(child.children)
                if (removed != null) return removed
            }
            return null
        }
        return removeFrom(roots)?.also { modified = true }
    }
}

object RuntimeSceneCodec {
    fun encode(scene: RuntimeScene): String {
        fun encodeNode(node: RuntimeNode): JSONObject = JSONObject().apply {
            put("id", node.id)
            put("name", node.name)
            put("x", node.position.x)
            put("y", node.position.y)
            put("rotation", node.rotation)
            put("sx", node.scale.x)
            put("sy", node.scale.y)
            put("visible", node.visible)
            put("z", node.zIndex)
            put("parent", node.parentId)
            put("texture", node.texture)
            put("script", node.script)
            put("body", node.bodyType)
            put("collider", node.collider)
            put("children", JSONArray(node.children.map(::encodeNode)))
        }

        return JSONObject()
            .put("format", 1)
            .put("name", scene.name)
            .put("roots", JSONArray(scene.roots.map(::encodeNode)))
            .toString(2)
    }

    fun decode(raw: String): RuntimeScene {
        val root = JSONObject(raw)
        val scene = RuntimeScene(root.optString("name", "MainScene"))

        fun decodeNode(value: JSONObject): RuntimeNode {
            val node = RuntimeNode(
                id = value.optString("id", UUID.randomUUID().toString()),
                name = value.optString("name", "Node2D"),
                position = NovaVec2(
                    value.optDouble("x", 0.0).toFloat(),
                    value.optDouble("y", 0.0).toFloat()
                ),
                rotation = value.optDouble("rotation", 0.0).toFloat(),
                scale = NovaVec2(
                    value.optDouble("sx", 1.0).toFloat(),
                    value.optDouble("sy", 1.0).toFloat()
                ),
                visible = value.optBoolean("visible", true),
                zIndex = value.optInt("z", 0),
                parentId = value.optString("parent", "").ifEmpty { null },
                texture = value.optString("texture", ""),
                script = value.optString("script", "").ifEmpty { null },
                bodyType = value.optString("body", "NONE"),
                collider = value.optString("collider", "NONE")
            )
            val children = value.optJSONArray("children") ?: JSONArray()
            for (i in 0 until children.length()) {
                node.children += decodeNode(children.getJSONObject(i))
            }
            return node
        }

        val roots = root.optJSONArray("roots") ?: JSONArray()
        for (i in 0 until roots.length()) {
            scene.roots += decodeNode(roots.getJSONObject(i))
        }
        scene.modified = false
        return scene
    }
}

interface NovaEditCommand {
    val label: String
    fun apply()
    fun undo()
}

class NovaUndoRedo(private val limit: Int = 200) {
    private val undoStack = ArrayDeque<NovaEditCommand>()
    private val redoStack = ArrayDeque<NovaEditCommand>()

    fun execute(command: NovaEditCommand) {
        command.apply()
        undoStack.addLast(command)
        if (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val command = undoStack.removeLast()
            command.undo()
            redoStack.addLast(command)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val command = redoStack.removeLast()
            command.apply()
            undoStack.addLast(command)
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
}

class FloatPropertyCommand(
    override val label: String,
    private val get: () -> Float,
    private val set: (Float) -> Unit,
    private val value: Float
) : NovaEditCommand {
    private var old = 0f

    override fun apply() {
        old = get()
        set(value)
    }

    override fun undo() = set(old)
}

/** Deterministic animation clock for the unified frame-name clip model. */
class NovaAnimationRuntime(private val clip: AnimationClip) {
    var timeSeconds = 0f
    var playing = false

    fun play(reset: Boolean = false) {
        if (reset) timeSeconds = 0f
        playing = true
    }

    fun stop() {
        playing = false
        timeSeconds = 0f
    }

    fun update(dtSeconds: Float) {
        if (!playing || clip.frames.isEmpty()) return
        timeSeconds += dtSeconds.coerceAtLeast(0f)
        if (clip.mode == PlaybackMode.LOOP) {
            val duration = clip.frames.size / clip.fps
            if (duration > 0f) timeSeconds %= duration
        }
    }

    fun frameIndex(): Int {
        if (clip.frames.isEmpty()) return 0
        val raw = (timeSeconds * clip.fps).toInt()
        return when (clip.mode) {
            PlaybackMode.LOOP -> raw % clip.frames.size
            PlaybackMode.ONCE -> raw.coerceAtMost(clip.frames.lastIndex)
            PlaybackMode.PING_PONG -> {
                if (clip.frames.size == 1) {
                    0
                } else {
                    val period = (clip.frames.size - 1) * 2
                    val position = raw % period
                    if (position <= clip.frames.lastIndex) position else period - position
                }
            }
        }
    }
}

class NovaTileMap(val width: Int, val height: Int, val layers: Int = 1) {
    private val cells = IntArray(width * height * layers)

    private fun index(layer: Int, x: Int, y: Int): Int =
        layer * width * height + y * width + x

    fun get(layer: Int, x: Int, y: Int): Int =
        if (layer in 0 until layers && x in 0 until width && y in 0 until height) {
            cells[index(layer, x, y)]
        } else 0

    fun set(layer: Int, x: Int, y: Int, tile: Int) {
        if (layer in 0 until layers && x in 0 until width && y in 0 until height) {
            cells[index(layer, x, y)] = tile
        }
    }

    fun fill(layer: Int, tile: Int) {
        if (layer in 0 until layers) {
            for (y in 0 until height) {
                for (x in 0 until width) set(layer, x, y, tile)
            }
        }
    }
}

data class NovaMaterial(
    var shader: String = "sprite",
    var texture: String = "",
    var normalMap: String = "",
    var opacity: Float = 1f,
    var blend: String = "alpha"
)

data class NovaParticleSettings(
    var amount: Int = 200,
    var lifetime: Float = 1f,
    var speed: Float = 80f,
    var spread: Float = 360f,
    var gravity: NovaVec2 = NovaVec2(0f, 60f),
    var looping: Boolean = true
)

data class NovaProjectSettings(
    var name: String = "Nova Game",
    var mainScene: String = "MainScene.nova",
    var width: Int = 1280,
    var height: Int = 720,
    var targetFps: Int = 60,
    var pixelArt: Boolean = false
)

class NovaProjectStore(private val context: Context) {
    private val file get() = File(context.filesDir, "nova-project.json")

    fun save(settings: NovaProjectSettings) {
        file.writeText(
            JSONObject()
                .put("name", settings.name)
                .put("mainScene", settings.mainScene)
                .put("width", settings.width)
                .put("height", settings.height)
                .put("targetFps", settings.targetFps)
                .put("pixelArt", settings.pixelArt)
                .toString(2)
        )
    }

    fun load(): NovaProjectSettings {
        if (!file.exists()) return NovaProjectSettings()
        val value = JSONObject(file.readText())
        return NovaProjectSettings(
            name = value.optString("name", "Nova Game"),
            mainScene = value.optString("mainScene", "MainScene.nova"),
            width = value.optInt("width", 1280),
            height = value.optInt("height", 720),
            targetFps = value.optInt("targetFps", 60),
            pixelArt = value.optBoolean("pixelArt", false)
        )
    }
}

class NovaAssetIndex(private val context: Context) {
    private val file get() = File(context.filesDir, "nova-asset-index.json")
    private val records = linkedMapOf<String, Asset2D>()

    fun all(): List<Asset2D> = records.values.toList()

    fun put(asset: Asset2D) {
        records[asset.id] = asset
        save()
    }

    fun remove(id: String) {
        records.remove(id)
        save()
    }

    fun load() {
        if (!file.exists()) return
        val array = JSONArray(file.readText())
        for (i in 0 until array.length()) {
            val value = array.getJSONObject(i)
            val type = runCatching {
                AssetType.valueOf(value.optString("type", "OTHER"))
            }.getOrDefault(AssetType.OTHER)
            val asset = Asset2D(
                value.getString("id"),
                value.getString("path"),
                type,
                value.optLong("size")
            )
            records[asset.id] = asset
        }
    }

    private fun save() {
        val array = JSONArray()
        records.values.forEach { asset ->
            array.put(
                JSONObject()
                    .put("id", asset.id)
                    .put("path", asset.path)
                    .put("type", asset.type.name)
                    .put("size", asset.sizeBytes)
            )
        }
        file.writeText(array.toString())
    }
}

class NovaInputMap {
    private val bindings = mutableMapOf<String, MutableSet<Int>>()
    private val down = mutableSetOf<Int>()

    fun bind(action: String, key: Int) {
        bindings.getOrPut(action) { mutableSetOf() }.add(key)
    }

    fun press(key: Int) { down += key }
    fun release(key: Int) { down -= key }
    fun pressed(action: String): Boolean = bindings[action]?.any { it in down } == true
}

data class NovaAudioBus(val name: String, var volume: Float = 1f, var muted: Boolean = false)

class NovaAudioMixer {
    private val buses = mutableMapOf("Master" to NovaAudioBus("Master"))

    fun bus(name: String): NovaAudioBus = buses.getOrPut(name) { NovaAudioBus(name) }

    fun volume(name: String, value: Float) {
        bus(name).volume = value.coerceIn(0f, 1f)
    }
}
