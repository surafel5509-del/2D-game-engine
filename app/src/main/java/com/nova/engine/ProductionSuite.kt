package com.nova.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max

object NovaVersion { const val ENGINE = "1.0.0-production"; const val SCHEMA = 4 }

data class AssetRecord(val id:String,val path:String,val type:String,val bytes:Long,val hash:String,val importedAt:Long=System.currentTimeMillis())
class AssetImporter(private val context:Context) {
    private val records=LinkedHashMap<String,AssetRecord>()
    fun importAsset(file:File,type:String=file.extension.lowercase()):AssetRecord { val r=AssetRecord(file.name,file.absolutePath,type,file.length(),sha256(file.readBytes())); records[r.id]=r; return r }
    fun importBundled(name:String,type:String):AssetRecord { val bytes=context.assets.open(name).use{it.readBytes()}; val r=AssetRecord(name,"assets://$name",type,bytes.size.toLong(),sha256(bytes)); records[r.id]=r; return r }
    fun find(id:String)=records[id]; fun all()=records.values.toList()
    private fun sha256(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){ "%02x".format(it) }
}

data class AtlasRegion(val name:String,val x:Int,val y:Int,val width:Int,val height:Int,val pivotX:Float=.5f,val pivotY:Float=.5f)
data class SpriteAtlas(val id:String,val width:Int,val height:Int,val regions:List<AtlasRegion>,val filterLinear:Boolean=true)
class AtlasPacker { fun pack(names:List<String>,cell:Int=64,columns:Int=max(1,kotlin.math.sqrt(names.size.toDouble()).toInt()+1)):SpriteAtlas { val regions=names.mapIndexed{i,n->AtlasRegion(n,(i%columns)*cell,(i/columns)*cell,cell,cell)}; return SpriteAtlas("atlas-${names.hashCode()}",columns*cell,((names.size+columns-1)/columns)*cell,regions) } }

data class Prefab(val id:String,val name:String,val sceneJson:String,val version:Int=NovaVersion.SCHEMA)
class PrefabLibrary { private val items=LinkedHashMap<String,Prefab>(); fun save(p:Prefab){items[p.id]=p}; fun get(id:String)=items[id]; fun all()=items.values.toList(); fun remove(id:String){items.remove(id)} }

data class UiStyle(val fontSize:Float=16f,val opacity:Float=1f,val padding:Float=8f)
data class UiNode(val id:String,val type:String,val x:Float,val y:Float,val width:Float,val height:Float,val text:String="",val style:UiStyle=UiStyle(),val children:List<String> = emptyList())
class UiDocument { private val nodes=LinkedHashMap<String,UiNode>(); fun add(n:UiNode){nodes[n.id]=n}; fun get(id:String)=nodes[id]; fun all()=nodes.values.toList(); fun hit(x:Float,y:Float)=nodes.values.lastOrNull{x in it.x..(it.x+it.width)&&y in it.y..(it.y+it.height)} }

data class Keyframe(val time:Float,val value:Float)
data class Track(val property:String,val keys:List<Keyframe>)
class Timeline(private val length:Float=10f) { private val tracks=mutableListOf<Track>(); fun add(t:Track){tracks+=t}; fun sample(property:String,time:Float):Float { val k=tracks.firstOrNull{it.property==property}?.keys?.sortedBy{it.time}?:return 0f; if(k.isEmpty())return 0f; if(time<=k.first().time)return k.first().value; if(time>=k.last().time)return k.last().value; val b=k.indexOfLast{it.time<=time}; val a=b+1; val u=(time-k[b].time)/(k[a].time-k[b].time); return k[b].value+(k[a].value-k[b].value)*u }; fun duration()=length }

data class DebugSnapshot(val timestamp:Long,val fps:Float,val frameMs:Float,val entities:Int,val drawCalls:Int,val memoryBytes:Long,val state:String)
class Diagnostics { private val snapshots=CopyOnWriteArrayList<DebugSnapshot>(); fun record(s:DebugSnapshot){snapshots.add(s);while(snapshots.size>120)snapshots.removeAt(0)}; fun latest()=snapshots.lastOrNull(); fun history()=snapshots.toList(); fun averageFrameMs()=snapshots.map{it.frameMs}.average().takeIf{!it.isNaN()}?:0.0 }

class InputMap { private val actions=LinkedHashMap<String,MutableSet<Int>>(); fun bind(action:String,key:Int){actions.getOrPut(action){linkedSetOf()}.add(key)}; fun keys(action:String)=actions[action].orEmpty(); fun isBound(action:String,key:Int)=key in actions[action].orEmpty() }

class AudioService { private var pool:SoundPool?=null; private val sounds=HashMap<String,Int>(); fun start(){pool=SoundPool.Builder().setMaxStreams(16).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()}; fun load(context:Context,resId:Int,name:String){sounds[name]=pool?.load(context,resId,1)?:0}; fun play(name:String,volume:Float=1f,rate:Float=1f){val id=sounds[name]?:return; pool?.play(id,volume,volume,1,0,rate)}; fun release(){pool?.release();pool=null;sounds.clear()} }

class ProjectExporter(private val context:Context) { fun prepareDirectory()=File(context.filesDir,"exports").apply{mkdirs()}; fun writeManifest(name:String,version:String,assets:List<AssetRecord>):File { val f=File(prepareDirectory(),"$name.manifest.json"); val body=assets.joinToString(","){"{\"id\":\"${it.id}\",\"hash\":\"${it.hash}\",\"type\":\"${it.type}\"}"}; f.writeText("{\"engine\":\"${NovaVersion.ENGINE}\",\"version\":\"$version\",\"assets\":[$body]}"); return f } }

object EngineSelfTest { data class Result(val name:String,val passed:Boolean,val detail:String); fun run():List<Result>{ val atlas=AtlasPacker().pack(listOf("a","b","c")); val timeline=Timeline(2f).apply{add(Track("x",listOf(Keyframe(0f,0f),Keyframe(1f,100f))))}; val ui=UiDocument().apply{add(UiNode("button","Button",0f,0f,100f,40f))}; return listOf(Result("Atlas packing",atlas.regions.size==3,"${atlas.width}x${atlas.height}"),Result("Timeline interpolation",timeline.sample("x",.5f)==50f,"sample=${timeline.sample("x",.5f)}"),Result("UI hit testing",ui.hit(20f,20f)?.id=="button","button hit")) } }
