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

data class RuntimeNode(val id: String = UUID.randomUUID().toString(), var name: String = "Node2D", var position: NovaVec2 = NovaVec2(), var rotation: Float = 0f, var scale: NovaVec2 = NovaVec2(1f,1f), var visible: Boolean = true, var zIndex: Int = 0, var parentId: String? = null, var texture: String = "", var script: String? = null, var bodyType: String = "NONE", var collider: String = "NONE", val children: MutableList<RuntimeNode> = mutableListOf())

class RuntimeScene(var name: String = "MainScene") {
    val roots = mutableListOf<RuntimeNode>(); var modified = false
    fun all(): List<RuntimeNode> { val out=mutableListOf<RuntimeNode>(); fun v(n:RuntimeNode){out+=n;n.children.forEach(::v)};roots.forEach(::v);return out }
    fun find(id:String)=all().firstOrNull{it.id==id}
    fun add(node:RuntimeNode,parent:String?=null){val p=parent?.let(::find);if(p==null)roots+=node else p.children+=node;node.parentId=parent;modified=true}
    fun remove(id:String):RuntimeNode?{fun r(list:MutableList<RuntimeNode>):RuntimeNode?{val i=list.indexOfFirst{it.id==id};if(i>=0)return list.removeAt(i);for(child in list){val removed=r(child.children);if(removed!=null)return removed};return null};return r(roots)?.also{modified=true}}
}

object RuntimeSceneCodec {
    fun encode(scene:RuntimeScene):String { fun node(n:RuntimeNode):JSONObject=JSONObject().apply{put("id",n.id);put("name",n.name);put("x",n.position.x);put("y",n.position.y);put("rotation",n.rotation);put("sx",n.scale.x);put("sy",n.scale.y);put("visible",n.visible);put("z",n.zIndex);put("parent",n.parentId);put("texture",n.texture);put("script",n.script);put("body",n.bodyType);put("collider",n.collider);put("children",JSONArray(n.children.map(::node)))};return JSONObject().put("format",1).put("name",scene.name).put("roots",JSONArray(scene.roots.map(::node))).toString(2) }
    fun decode(raw:String):RuntimeScene { val o=JSONObject(raw);val s=RuntimeScene(o.optString("name","MainScene"));fun node(v:JSONObject):RuntimeNode{val n=RuntimeNode(v.optString("id",UUID.randomUUID().toString()),v.optString("name","Node2D"),NovaVec2(v.optDouble("x").toFloat(),v.optDouble("y").toFloat()),v.optDouble("rotation").toFloat(),NovaVec2(v.optDouble("sx",1.0).toFloat(),v.optDouble("sy",1.0).toFloat()),v.optBoolean("visible",true),v.optInt("z"),v.optString("parent","").ifEmpty{null},v.optString("texture"),v.optString("script","").ifEmpty{null},v.optString("body","NONE"),v.optString("collider","NONE"));val a=v.optJSONArray("children")?:JSONArray();for(i in 0 until a.length())n.children+=node(a.getJSONObject(i));return n};val a=o.optJSONArray("roots")?:JSONArray();for(i in 0 until a.length())s.roots+=node(a.getJSONObject(i));s.modified=false;return s }
}

interface NovaEditCommand { val label:String; fun apply(); fun undo() }
class NovaUndoRedo(private val limit:Int=200){private val u=ArrayDeque<NovaEditCommand>();private val r=ArrayDeque<NovaEditCommand>();fun execute(c:NovaEditCommand){c.apply();u.addLast(c);if(u.size>limit)u.removeFirst();r.clear()};fun undo(){if(u.isNotEmpty())u.removeLast().also{it.undo();r.addLast(it)}};fun redo(){if(r.isNotEmpty())r.removeLast().also{it.apply();u.addLast(it)}};fun canUndo()=u.isNotEmpty();fun canRedo()=r.isNotEmpty()}
class FloatPropertyCommand(override val label:String,private val get:()->Float,private val set:(Float)->Unit,private val value:Float):NovaEditCommand{private var old=0f;override fun apply(){old=get();set(value)};override fun undo(){set(old)}}

/** Deterministic animation clock for the unified frame-name clip model. */
class NovaAnimationRuntime(private val clip:AnimationClip){var timeSeconds=0f;var playing=false;fun play(reset:Boolean=false){if(reset)timeSeconds=0f;playing=true};fun stop(){playing=false;timeSeconds=0f};fun update(dtSeconds:Float){if(!playing||clip.frames.isEmpty())return;timeSeconds+=dtSeconds.coerceAtLeast(0f);val duration=clip.frames.size/clip.fps;if(clip.mode==PlaybackMode.LOOP&&duration>0f)timeSeconds%=duration};fun frameIndex():Int{if(clip.frames.isEmpty())return 0;val raw=(timeSeconds*clip.fps).toInt();return when(clip.mode){PlaybackMode.LOOP->raw%clip.frames.size;PlaybackMode.ONCE->raw.coerceAtMost(clip.frames.lastIndex);PlaybackMode.PING_PONG->{if(clip.frames.size==1)0 else {val period=(clip.frames.size-1)*2;val p=raw%period;if(p<=clip.frames.lastIndex)p else period-p}}}}}

class NovaTileMap(val width:Int,val height:Int,val layers:Int=1){private val cells=IntArray(width*height*layers);private fun idx(l:Int,x:Int,y:Int)=l*width*height+y*width+x;fun get(l:Int,x:Int,y:Int)=if(l in 0 until layers&&x in 0 until width&&y in 0 until height)cells[idx(l,x,y)] else 0;fun set(l:Int,x:Int,y:Int,tile:Int){if(l in 0 until layers&&x in 0 until width&&y in 0 until height)cells[idx(l,x,y)]=tile};fun fill(l:Int,tile:Int){if(l in 0 until layers)for(y in 0 until height)for(x in 0 until width)set(l,x,y,tile)}}
data class NovaMaterial(var shader:String="sprite",var texture:String="",var normalMap:String="",var opacity:Float=1f,var blend:String="alpha")
data class NovaParticleSettings(var amount:Int=200,var lifetime:Float=1f,var speed:Float=80f,var spread:Float=360f,var gravity:NovaVec2=NovaVec2(0f,60f),var looping:Boolean=true)
data class NovaProjectSettings(var name:String="Nova Game",var mainScene:String="MainScene.nova",var width:Int=1280,var height:Int=720,var targetFps:Int=60,var pixelArt:Boolean=false)
class NovaProjectStore(private val context:Context){private val file get()=File(context.filesDir,"nova-project.json");fun save(s:NovaProjectSettings){file.writeText(JSONObject().put("name",s.name).put("mainScene",s.mainScene).put("width",s.width).put("height",s.height).put("targetFps",s.targetFps).put("pixelArt",s.pixelArt).toString(2))};fun load():NovaProjectSettings{if(!file.exists())return NovaProjectSettings();val o=JSONObject(file.readText());return NovaProjectSettings(o.optString("name","Nova Game"),o.optString("mainScene","MainScene.nova"),o.optInt("width",1280),o.optInt("height",720),o.optInt("targetFps",60),o.optBoolean("pixelArt",false))}}
class NovaAssetIndex(private val context:Context){private val file get()=File(context.filesDir,"nova-asset-index.json");private val records=linkedMapOf<String,Asset2D>();fun all()=records.values.toList();fun put(asset:Asset2D){records[asset.id]=asset;save()};fun remove(id:String){records.remove(id);save()};fun load(){if(!file.exists())return;val a=JSONArray(file.readText());for(i in 0 until a.length()){val o=a.getJSONObject(i);val type=runCatching{AssetType.valueOf(o.optString("type","OTHER"))}.getOrDefault(AssetType.OTHER);records[o.getString("id")]=Asset2D(o.getString("id"),o.getString("path"),type,o.optLong("size"))}};private fun save(){val a=JSONArray();records.values.forEach{a.put(JSONObject().put("id",it.id).put("path",it.path).put("type",it.type.name).put("size",it.sizeBytes))};file.writeText(a.toString())}}
class NovaInputMap{private val map=mutableMapOf<String,MutableSet<Int>>();private val down=mutableSetOf<Int>();fun bind(action:String,key:Int){map.getOrPut(action){mutableSetOf()}+=key};fun press(key:Int){down+=key};fun release(key:Int){down-=key};fun pressed(action:String)=map[action]?.any{it in down}==true}
data class NovaAudioBus(val name:String,var volume:Float=1f,var muted:Boolean=false)
class NovaAudioMixer{private val buses=mutableMapOf("Master" to NovaAudioBus("Master"));fun bus(name:String)=buses.getOrPut(name){NovaAudioBus(name)};fun volume(name:String,value:Float){bus(name).volume=max(0f,min(1f,value))}}
