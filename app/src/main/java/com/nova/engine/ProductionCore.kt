package com.nova.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** Production-oriented editor/runtime data layer. No UI dependency; safe to use from the editor and play mode. */

data class Vec2(var x: Float = 0f, var y: Float = 0f)
data class Rect2(var x: Float = 0f, var y: Float = 0f, var w: Float = 1f, var h: Float = 1f)

data class Node2D(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Node2D",
    var position: Vec2 = Vec2(),
    var rotation: Float = 0f,
    var scale: Vec2 = Vec2(1f, 1f),
    var visible: Boolean = true,
    var zIndex: Int = 0,
    var parentId: String? = null,
    var tags: MutableSet<String> = mutableSetOf()
)

data class SpriteComponent(
    var texture: String = "",
    var region: Rect2 = Rect2(0f, 0f, 1f, 1f),
    var pivot: Vec2 = Vec2(.5f, .5f),
    var tint: Int = 0xffffffff.toInt(),
    var flipX: Boolean = false,
    var flipY: Boolean = false,
    var pixelsPerUnit: Float = 100f
)

data class Body2D(
    var bodyType: BodyType = BodyType.DYNAMIC,
    var velocity: Vec2 = Vec2(),
    var gravityScale: Float = 1f,
    var mass: Float = 1f,
    var friction: Float = .6f,
    var restitution: Float = .1f,
    var sensor: Boolean = false,
    var layer: Int = 1,
    var mask: Int = -1
)
enum class BodyType { STATIC, KINEMATIC, DYNAMIC }

data class Collider2D(var shape: Shape = Shape.BOX, var size: Vec2 = Vec2(32f, 32f), var radius: Float = 16f)
enum class Shape { BOX, CIRCLE, CAPSULE, POLYGON }

data class SceneNode(
    val node: Node2D,
    var sprite: SpriteComponent? = null,
    var body: Body2D? = null,
    var collider: Collider2D? = null,
    var script: String? = null,
    var children: MutableList<SceneNode> = mutableListOf()
)

class SceneDocument(var name: String = "MainScene") {
    val roots = mutableListOf<SceneNode>()
    var modified = false
    fun allNodes(): List<SceneNode> {
        val out = mutableListOf<SceneNode>()
        fun visit(n: SceneNode) { out += n; n.children.forEach(::visit) }
        roots.forEach(::visit); return out
    }
    fun find(id: String) = allNodes().firstOrNull { it.node.id == id }
    fun add(node: SceneNode, parentId: String? = null) {
        val p = parentId?.let(::find)
        if (p == null) roots += node else p.children += node
        node.node.parentId = parentId; modified = true
    }
    fun remove(id: String): SceneNode? {
        fun removeFrom(list: MutableList<SceneNode>): SceneNode? {
            val i = list.indexOfFirst { it.node.id == id }
            if (i >= 0) return list.removeAt(i)
            list.forEach { r -> removeFrom(r.children)?.let { return it } }
            return null
        }
        return removeFrom(roots)?.also { modified = true }
    }
}

object SceneSerializer {
    fun encode(scene: SceneDocument): String {
        val root = JSONObject().put("format", 2).put("name", scene.name)
        fun nodeJson(n: SceneNode): JSONObject = JSONObject().apply {
            put("id", n.node.id); put("name", n.node.name)
            put("x", n.node.position.x); put("y", n.node.position.y)
            put("rotation", n.node.rotation); put("sx", n.node.scale.x); put("sy", n.node.scale.y)
            put("visible", n.node.visible); put("z", n.node.zIndex); put("parent", n.node.parentId)
            put("tags", JSONArray(n.node.tags.toList()))
            n.sprite?.let { s -> put("sprite", JSONObject().put("texture", s.texture).put("u", s.region.x).put("v", s.region.y).put("uw", s.region.w).put("vh", s.region.h).put("tint", s.tint).put("flipX", s.flipX).put("flipY", s.flipY)) }
            n.body?.let { b -> put("body", JSONObject().put("type", b.bodyType.name).put("vx", b.velocity.x).put("vy", b.velocity.y).put("gravity", b.gravityScale).put("mass", b.mass).put("friction", b.friction).put("restitution", b.restitution).put("sensor", b.sensor).put("layer", b.layer).put("mask", b.mask)) }
            n.collider?.let { c -> put("collider", JSONObject().put("shape", c.shape.name).put("w", c.size.x).put("h", c.size.y).put("radius", c.radius)) }
            n.script?.let { put("script", it) }
            put("children", JSONArray(n.children.map(::nodeJson)))
        }
        root.put("roots", JSONArray(scene.roots.map(::nodeJson)))
        return root.toString(2)
    }
    fun decode(raw: String): SceneDocument {
        val o = JSONObject(raw); val scene = SceneDocument(o.optString("name", "MainScene"))
        fun read(v: JSONObject): SceneNode {
            val n = Node2D(v.optString("id", UUID.randomUUID().toString()), v.optString("name", "Node2D"), Vec2(v.optDouble("x", 0.0).toFloat(), v.optDouble("y", 0.0).toFloat()), v.optDouble("rotation", 0.0).toFloat(), Vec2(v.optDouble("sx", 1.0).toFloat(), v.optDouble("sy", 1.0).toFloat()), v.optBoolean("visible", true), v.optInt("z", 0), v.optString("parent", "").ifEmpty { null })
            val tags = v.optJSONArray("tags") ?: JSONArray(); for (i in 0 until tags.length()) n.tags += tags.getString(i)
            val s = v.optJSONObject("sprite")?.let { SpriteComponent(it.optString("texture"), Rect2(it.optDouble("u").toFloat(), it.optDouble("v").toFloat(), it.optDouble("uw", 1.0).toFloat(), it.optDouble("vh", 1.0).toFloat()), tint = it.optInt("tint", 0xffffffff.toInt()), flipX = it.optBoolean("flipX"), flipY = it.optBoolean("flipY")) }
            val b = v.optJSONObject("body")?.let { Body2D(BodyType.valueOf(it.optString("type", "DYNAMIC")), Vec2(it.optDouble("vx").toFloat(), it.optDouble("vy").toFloat()), it.optDouble("gravity", 1.0).toFloat(), it.optDouble("mass", 1.0).toFloat(), it.optDouble("friction", .6).toFloat(), it.optDouble("restitution", .1).toFloat(), it.optBoolean("sensor"), it.optInt("layer", 1), it.optInt("mask", -1)) }
            val c = v.optJSONObject("collider")?.let { Collider2D(Shape.valueOf(it.optString("shape", "BOX")), Vec2(it.optDouble("w", 32.0).toFloat(), it.optDouble("h", 32.0).toFloat()), it.optDouble("radius", 16.0).toFloat()) }
            val out = SceneNode(n, s, b, c, v.optString("script", "").ifEmpty { null })
            val kids = v.optJSONArray("children") ?: JSONArray(); for (i in 0 until kids.length()) out.children += read(kids.getJSONObject(i))
            return out
        }
        val roots = o.optJSONArray("roots") ?: JSONArray(); for (i in 0 until roots.length()) scene.roots += read(roots.getJSONObject(i))
        scene.modified = false; return scene
    }
}

data class AssetRecord(val guid: String, val path: String, val type: AssetType, var size: Long = 0, var hash: String = "", var imported: Boolean = false)
enum class AssetType { TEXTURE, ATLAS, AUDIO, FONT, SCENE, PREFAB, SCRIPT, TILEMAP, MATERIAL, UNKNOWN }

class AssetDatabase(private val context: Context) {
    private val file get() = File(context.filesDir, "nova-assets.json")
    private val records = linkedMapOf<String, AssetRecord>()
    fun scan() = records.values.toList()
    fun upsert(record: AssetRecord) { records[record.guid] = record; persist() }
    fun remove(guid: String) { records.remove(guid); persist() }
    fun findByPath(path: String) = records.values.firstOrNull { it.path == path }
    fun importPath(path: String, type: AssetType): AssetRecord = AssetRecord(UUID.randomUUID().toString(), path, type, imported = true).also(::upsert)
    fun load() { if (!file.exists()) return; val a = JSONArray(file.readText()); for (i in 0 until a.length()) { val o=a.getJSONObject(i); val r=AssetRecord(o.getString("guid"),o.getString("path"),AssetType.valueOf(o.optString("type","UNKNOWN")),o.optLong("size"),o.optString("hash"),o.optBoolean("imported")); records[r.guid]=r } }
    private fun persist() { val a=JSONArray(); records.values.forEach { r -> a.put(JSONObject().put("guid",r.guid).put("path",r.path).put("type",r.type.name).put("size",r.size).put("hash",r.hash).put("imported",r.imported)) }; file.writeText(a.toString()) }
}

interface EditCommand { fun apply(); fun undo(); val label: String }
class UndoRedoManager(private val limit: Int = 100) {
    private val undo = ArrayDeque<EditCommand>(); private val redo = ArrayDeque<EditCommand>()
    fun execute(c: EditCommand) { c.apply(); undo.addLast(c); if (undo.size > limit) undo.removeFirst(); redo.clear() }
    fun undo() { if (undo.isNotEmpty()) undo.removeLast().also { it.undo(); redo.addLast(it) } }
    fun redo() { if (redo.isNotEmpty()) redo.removeLast().also { it.apply(); undo.addLast(it) } }
    fun canUndo()=undo.isNotEmpty(); fun canRedo()=redo.isNotEmpty()
}

class PropertyCommand<T>(private val labelText: String, private val getter: () -> T, private val setter: (T) -> Unit, private val value: T): EditCommand {
    private lateinit var old: T
    override val label get() = labelText
    override fun apply() { old = getter(); setter(value) }
    override fun undo() { setter(old) }
}

data class AnimationFrame(val region: Rect2, val duration: Float)
data class AnimationClip(val name: String, val frames: List<AnimationFrame>, val loop: Boolean = true, val speed: Float = 1f)
class AnimationPlayer {
    private val clips = mutableMapOf<String, AnimationClip>(); var current: String? = null; var time = 0f; var playing = false
    fun add(clip: AnimationClip) { clips[clip.name] = clip }
    fun play(name: String, restart: Boolean = false) { if (current != name || restart) time=0f; current=name; playing=true }
    fun stop() { playing=false; time=0f }
    fun update(dt: Float): Rect2? { val c=clips[current] ?: return null; if (playing) time += dt*c.speed; val total=c.frames.sumOf { it.duration.toDouble() }.toFloat(); if (total<=0f) return null; var t=if(c.loop) time%total else min(time,total-.0001f); for(f in c.frames){ if(t<f.duration)return f.region; t-=f.duration }; return c.frames.lastOrNull()?.region }
}

data class TileCell(var id: Int = 0, var flags: Int = 0)
class TileMap2D(val width: Int, val height: Int, val layers: Int = 1) {
    private val cells = Array(layers) { Array(width * height) { TileCell() } }
    fun get(layer:Int,x:Int,y:Int)=if(layer in 0 until layers && x in 0 until width && y in 0 until height) cells[layer][y*width+x] else null
    fun set(layer:Int,x:Int,y:Int,id:Int){ if(layer in 0 until layers && x in 0 until width && y in 0 until height) cells[layer][y*width+x].id=id }
    fun fill(layer:Int,id:Int){ if(layer in 0 until layers) cells[layer].forEach{it.id=id} }
}

class InputMap {
    private val actions = mutableMapOf<String, MutableSet<Int>>()
    private val pressed = mutableSetOf<Int>()
    fun bind(action:String,key:Int){actions.getOrPut(action){mutableSetOf()} += key}
    fun keyDown(key:Int){pressed += key}; fun keyUp(key:Int){pressed -= key}
    fun isPressed(action:String)=actions[action]?.any{it in pressed}==true
    fun clear(){pressed.clear()}
}

data class Material2D(var shader:String="default", var texture:String="", var opacity:Float=1f, var blend:String="alpha", var normalMap:String="")
data class ParticleEmitter2D(var amount:Int=100, var lifetime:Float=1f, var speed:Float=80f, var spread:Float=360f, var gravity:Vec2=Vec2(0f,60f), var looping:Boolean=true)
data class AudioBus(val name:String, var volume:Float=1f, var muted:Boolean=false)
class AudioMixer { private val buses=mutableMapOf("Master" to AudioBus("Master")); fun bus(name:String)=buses.getOrPut(name){AudioBus(name)}; fun setVolume(name:String,v:Float){bus(name).volume=max(0f,min(1f,v))} }

data class ProjectSettings(var name:String="Nova Game", var mainScene:String="MainScene.scene", var width:Int=1280, var height:Int=720, var pixelsPerUnit:Float=100f, var vsync:Boolean=true, var targetFps:Int=60)
class ProjectStore(private val context:Context) {
    private val file get()=File(context.filesDir,"project.json")
    fun save(settings:ProjectSettings){file.writeText(JSONObject().put("name",settings.name).put("mainScene",settings.mainScene).put("width",settings.width).put("height",settings.height).put("pixelsPerUnit",settings.pixelsPerUnit).put("vsync",settings.vsync).put("targetFps",settings.targetFps).toString(2))}
    fun load():ProjectSettings { if(!file.exists())return ProjectSettings(); val o=JSONObject(file.readText()); return ProjectSettings(o.optString("name","Nova Game"),o.optString("mainScene","MainScene.scene"),o.optInt("width",1280),o.optInt("height",720),o.optDouble("pixelsPerUnit",100.0).toFloat(),o.optBoolean("vsync",true),o.optInt("targetFps",60)) }
}
