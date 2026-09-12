package com.nova.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Nova Render V2.
 *
 * GPU-facing render architecture for a real 2D engine. Rendering is expressed
 * as commands first and submitted later. This separates scene traversal from
 * OpenGL state, makes batching deterministic, and gives the editor a place to
 * inspect draw calls, cameras, materials, sorting and culling.
 */

// -----------------------------------------------------------------------------
// Render math
// -----------------------------------------------------------------------------

data class RenderVec2(var x: Float = 0f, var y: Float = 0f) {
    fun set(x: Float, y: Float): RenderVec2 { this.x=x; this.y=y; return this }
    fun add(v: RenderVec2): RenderVec2 { x+=v.x; y+=v.y; return this }
    fun sub(v: RenderVec2): RenderVec2 { x-=v.x; y-=v.y; return this }
    fun scale(s: Float): RenderVec2 { x*=s; y*=s; return this }
    fun copyOf() = RenderVec2(x,y)
}

data class RenderRect(var x: Float, var y: Float, var width: Float, var height: Float) {
    fun contains(px: Float, py: Float): Boolean = px>=x && py>=y && px<=x+width && py<=y+height
    fun intersects(o: RenderRect): Boolean = x < o.x+o.width && x+width > o.x && y < o.y+o.height && y+height > o.y
}

data class RenderColor(var r: Float=1f,var g: Float=1f,var b: Float=1f,var a: Float=1f) {
    fun clampSelf(): RenderColor { r=r.coerceIn(0f,1f);g=g.coerceIn(0f,1f);b=b.coerceIn(0f,1f);a=a.coerceIn(0f,1f);return this }
    fun packed(): Int { val c=clampSelf();return ((c.a*255).toInt() shl 24) or ((c.r*255).toInt() shl 16) or ((c.g*255).toInt() shl 8) or (c.b*255).toInt() }
}

class RenderMatrix3 {
    val values = FloatArray(9)
    init { identity() }
    fun identity(): RenderMatrix3 { for(i in values.indices) values[i]=0f;values[0]=1f;values[4]=1f;values[8]=1f;return this }
    fun translate(x:Float,y:Float):RenderMatrix3 { values[6]+=x;values[7]+=y;return this }
    fun scale(x:Float,y:Float):RenderMatrix3 { values[0]*=x;values[4]*=y;return this }
    fun rotate(r:Float):RenderMatrix3 { val c=cos(r);val s=sin(r);val a=values[0];val b=values[1];val d=values[3];val e=values[4];values[0]=a*c+b*s;values[1]=-a*s+b*c;values[3]=d*c+e*s;values[4]=-d*s+e*c;return this }
    fun copyOf():RenderMatrix3=RenderMatrix3().also{values.copyInto(it.values)}
}

// -----------------------------------------------------------------------------
// Camera
// -----------------------------------------------------------------------------

class RenderCamera2D {
    var position=RenderVec2()
    var zoom=1f
    var rotation=0f
    var viewportWidth=1f
    var viewportHeight=1f
    var near=-1f
    var far=1f
    var pixelSnap=false
    var minZoom=0.05f
    var maxZoom=32f

    fun resize(width:Int,height:Int){viewportWidth=width.coerceAtLeast(1).toFloat();viewportHeight=height.coerceAtLeast(1).toFloat()}
    fun setZoom(value:Float){zoom=value.coerceIn(minZoom,maxZoom)}
    fun screenToWorld(sx:Float,sy:Float):RenderVec2{
        val x=(sx-viewportWidth*.5f)/zoom+position.x
        val y=(sy-viewportHeight*.5f)/zoom+position.y
        return if(pixelSnap) RenderVec2(kotlin.math.round(x),kotlin.math.round(y)) else RenderVec2(x,y)
    }
    fun worldToScreen(wx:Float,wy:Float):RenderVec2=RenderVec2((wx-position.x)*zoom+viewportWidth*.5f,(wy-position.y)*zoom+viewportHeight*.5f)
    fun visibleWorldBounds():RenderRect=RenderRect(position.x-viewportWidth/(2f*zoom),position.y-viewportHeight/(2f*zoom),viewportWidth/zoom,viewportHeight/zoom)
    fun buildMatrix():FloatArray{
        val sx=2f*zoom/viewportWidth;val sy=-2f*zoom/viewportHeight
        val c=cos(rotation);val s=sin(rotation)
        return floatArrayOf(sx*c,sx*s,0f,sy*s,sy*c,0f,-position.x*sx*c+position.y*sx*s,-position.x*sy*s-position.y*sy*c,1f)
    }
}

// -----------------------------------------------------------------------------
// Sprite and material descriptions
// -----------------------------------------------------------------------------

data class RenderTextureHandle(val key:String,val glId:Int,val width:Int,val height:Int)
data class RenderUvRect(val u0:Float=0f,val v0:Float=0f,val u1:Float=1f,val v1:Float=1f)
data class RenderSprite(
    var texture:String?=null,
    var region:RenderUvRect=RenderUvRect(),
    var width:Float=32f,
    var height:Float=32f,
    var pivotX:Float=.5f,
    var pivotY:Float=.5f,
    var flipX:Boolean=false,
    var flipY:Boolean=false,
    var tint:RenderColor=RenderColor()
)

data class RenderMaterial(
    var textureKey:String?=null,
    var blendMode:RenderBlendMode=RenderBlendMode.ALPHA,
    var filter:RenderFilter=RenderFilter.LINEAR,
    var color:RenderColor=RenderColor(),
    var shaderKey:String="sprite"
)

enum class RenderBlendMode { OPAQUE, ALPHA, ADDITIVE, MULTIPLY }
enum class RenderFilter { NEAREST, LINEAR }
enum class RenderSortMode { Z_INDEX, Y_SORT, MATERIAL, TEXTURE }

data class RenderTransform(
    var x:Float=0f,var y:Float=0f,var rotation:Float=0f,var scaleX:Float=1f,var scaleY:Float=1f
)

data class RenderCommand(
    val entityId:Int,
    val textureKey:String?,
    val materialKey:String,
    val transform:RenderTransform,
    val sprite:RenderSprite,
    val zIndex:Int=0,
    val visible:Boolean=true,
    val layer:Int=0
)

// -----------------------------------------------------------------------------
// Render queue
// -----------------------------------------------------------------------------

class RenderQueue(private val capacity:Int=4096){
    private val commands=ArrayList<RenderCommand>(capacity)
    var sortMode=RenderSortMode.Z_INDEX
    var submitted=0
        private set
    var culled=0
        private set
    fun clear(){commands.clear();submitted=0;culled=0}
    fun add(command:RenderCommand){if(command.visible)commands+=command}
    fun size():Int=commands.size
    fun all():List<RenderCommand>=commands
    fun sort(){
        when(sortMode){
            RenderSortMode.Z_INDEX->commands.sortWith(compareBy<RenderCommand>{it.layer}.thenBy{it.zIndex}.thenBy{it.materialKey}.thenBy{it.textureKey})
            RenderSortMode.Y_SORT->commands.sortWith(compareBy<RenderCommand>{it.transform.y}.thenBy{it.zIndex})
            RenderSortMode.MATERIAL->commands.sortWith(compareBy<RenderCommand>{it.materialKey}.thenBy{it.textureKey}.thenBy{it.zIndex})
            RenderSortMode.TEXTURE->commands.sortWith(compareBy<RenderCommand>{it.textureKey}.thenBy{it.materialKey}.thenBy{it.zIndex})
        }
    }
    fun cull(camera:RenderCamera2D){
        val view=camera.visibleWorldBounds()
        var removed=0
        val iterator=commands.iterator()
        while(iterator.hasNext()){
            val c=iterator.next();val w=absSize(c.sprite.width*c.transform.scaleX);val h=absSize(c.sprite.height*c.transform.scaleY)
            val r=RenderRect(c.transform.x-w*.5f,c.transform.y-h*.5f,w,h)
            if(!r.intersects(view)){iterator.remove();removed++}
        }
        culled+=removed
    }
    private fun absSize(v:Float)=kotlin.math.abs(v)
    fun markSubmitted(count:Int){submitted+=count}
}

// -----------------------------------------------------------------------------
// Texture registry and lifecycle
// -----------------------------------------------------------------------------

class RenderTextureRegistry(private val maxTextures:Int=512){
    private val handles=LinkedHashMap<String,RenderTextureHandle>(16,.75f,true)
    private val pinned=HashSet<String>()
    fun put(handle:RenderTextureHandle){handles[handle.key]=handle;trim()}
    fun pin(key:String){pinned+=key}
    fun unpin(key:String){pinned-=key}
    fun get(key:String):RenderTextureHandle?=handles[key]
    fun remove(key:String):RenderTextureHandle?=handles.remove(key)
    fun clear(){handles.clear();pinned.clear()}
    fun size()=handles.size
    fun keys():List<String>=handles.keys.toList()
    private fun trim(){
        val iterator=handles.entries.iterator()
        while(handles.size>maxTextures&&iterator.hasNext()){
            val e=iterator.next();if(e.key !in pinned)iterator.remove()
        }
    }
}

// -----------------------------------------------------------------------------
// GPU shader utility
// -----------------------------------------------------------------------------

class RenderShaderProgram(private val vertexSource:String,private val fragmentSource:String){
    var programId=0
        private set
    var vertexAttribute=-1
        private set
    var uvAttribute=-1
        private set
    var matrixUniform=-1
        private set
    var textureUniform=-1
        private set
    var colorUniform=-1
        private set

    fun create(){
        if(programId!=0)return
        val vs=compile(GLES20.GL_VERTEX_SHADER,vertexSource);val fs=compile(GLES20.GL_FRAGMENT_SHADER,fragmentSource)
        programId=GLES20.glCreateProgram();GLES20.glAttachShader(programId,vs);GLES20.glAttachShader(programId,fs);GLES20.glLinkProgram(programId)
        val status=IntArray(1);GLES20.glGetProgramiv(programId,GLES20.GL_LINK_STATUS,status,0)
        if(status[0]!=GLES20.GL_TRUE){val log=GLES20.glGetProgramInfoLog(programId);GLES20.glDeleteProgram(programId);programId=0;throw IllegalStateException("Nova render link failed: $log")}
        GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs)
        vertexAttribute=GLES20.glGetAttribLocation(programId,"aPosition");uvAttribute=GLES20.glGetAttribLocation(programId,"aUv");matrixUniform=GLES20.glGetUniformLocation(programId,"uMvp");textureUniform=GLES20.glGetUniformLocation(programId,"uTexture");colorUniform=GLES20.glGetUniformLocation(programId,"uColor")
    }
    private fun compile(type:Int,source:String):Int{
        val id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,source);GLES20.glCompileShader(id);val status=IntArray(1);GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0)
        if(status[0]!=GLES20.GL_TRUE){val log=GLES20.glGetShaderInfoLog(id);GLES20.glDeleteShader(id);throw IllegalStateException("Nova render shader failed: $log")};return id
    }
    fun destroy(){if(programId!=0){GLES20.glDeleteProgram(programId);programId=0}}
}

// -----------------------------------------------------------------------------
// Batched quad renderer
// -----------------------------------------------------------------------------

class SpriteBatchV2(private val maxSprites:Int=2048){
    private val floatsPerVertex=4
    private val verticesPerSprite=4
    private val floatsPerSprite=floatsPerVertex*verticesPerSprite
    private val buffer:FloatBuffer=ByteBuffer.allocateDirect(maxSprites*floatsPerSprite*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var spriteCount=0
    private var activeTexture=-1
    private var activeMaterial=""
    var drawCalls=0
        private set
    var sprites=0
        private set
    var flushes=0
        private set

    fun beginFrame(){spriteCount=0;activeTexture=-1;activeMaterial="";drawCalls=0;sprites=0;flushes=0}
    fun beginBatch(textureId:Int,materialKey:String){
        if(spriteCount>0&&(textureId!=activeTexture||materialKey!=activeMaterial))flush()
        activeTexture=textureId;activeMaterial=materialKey
    }
    fun addQuad(command:RenderCommand,textureId:Int){
        beginBatch(textureId,command.materialKey)
        if(spriteCount>=maxSprites)flush()
        val x=command.transform.x;val y=command.transform.y
        val sx=command.sprite.width*command.transform.scaleX;val sy=command.sprite.height*command.transform.scaleY
        val left=x-sx*command.sprite.pivotX;val right=left+sx;val top=y-sy*command.sprite.pivotY;val bottom=top+sy
        val uv=command.sprite.region
        val u0=if(command.sprite.flipX)uv.u1 else uv.u0;val u1=if(command.sprite.flipX)uv.u0 else uv.u1
        val v0=if(command.sprite.flipY)uv.v1 else uv.v0;val v1=if(command.sprite.flipY)uv.v0 else uv.v1
        put(left,top,u0,v0);put(right,top,u1,v0);put(left,bottom,u0,v1);put(right,bottom,u1,v1)
        spriteCount++;sprites++
    }
    private fun put(x:Float,y:Float,u:Float,v:Float){buffer.put(x).put(y).put(u).put(v)}
    fun flush(){if(spriteCount==0)return;buffer.position(0);drawCalls++;flushes++;spriteCount=0}
    fun endFrame(){flush()}
    fun buffer():FloatBuffer{val duplicate=buffer.duplicate();duplicate.position(0);return duplicate}
}

// -----------------------------------------------------------------------------
// OpenGL state cache
// -----------------------------------------------------------------------------

class RenderStateCache{
    private var blend=false
    private var blendMode=RenderBlendMode.ALPHA
    private var boundTexture=-1
    private var boundProgram=-1
    fun reset(){blend=false;blendMode=RenderBlendMode.ALPHA;boundTexture=-1;boundProgram=-1}
    fun bindProgram(program:Int){if(program!=boundProgram){GLES20.glUseProgram(program);boundProgram=program}}
    fun bindTexture(texture:Int){if(texture!=boundTexture){GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);boundTexture=texture}}
    fun setBlend(mode:RenderBlendMode){
        if(mode==blendMode&&blend)return
        blendMode=mode
        when(mode){
            RenderBlendMode.OPAQUE->{if(blend){GLES20.glDisable(GLES20.GL_BLEND);blend=false}}
            RenderBlendMode.ALPHA->{GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);blend=true}
            RenderBlendMode.ADDITIVE->{GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE);blend=true}
            RenderBlendMode.MULTIPLY->{GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_DST_COLOR,GLES20.GL_ZERO);blend=true}
        }
    }
}

// -----------------------------------------------------------------------------
// Renderer statistics and frame graph
// -----------------------------------------------------------------------------

data class RenderFrameStats(var frameId:Long=0,var commands:Int=0,var visible:Int=0,var culled:Int=0,var sprites:Int=0,var drawCalls:Int=0,var flushes:Int=0,var textureBinds:Int=0,var shaderBinds:Int=0,var cpuMs:Float=0f,var gpuMs:Float=0f)

class RenderFrameGraph{
    private val passes=ArrayList<String>()
    fun clear(){passes.clear()}
    fun add(name:String){passes+=name}
    fun names():List<String>=passes.toList()
    fun contains(name:String)=passes.contains(name)
}

class RenderProfiler(private val capacity:Int=240){
    private val cpu=ArrayDeque<Float>();private val gpu=ArrayDeque<Float>();private val draws=ArrayDeque<Int>()
    fun record(cpuMs:Float,gpuMs:Float,drawCalls:Int){push(cpu,cpuMs);push(gpu,gpuMs);push(draws,drawCalls)}
    private fun <T>push(q:ArrayDeque<T>,v:T){q.addLast(v);if(q.size>capacity)q.removeFirst()}
    fun averageCpu():Float=cpu.averageSafe()
    fun averageGpu():Float=gpu.averageSafe()
    fun averageDraws():Float=draws.averageSafe()
    private fun ArrayDeque<Float>.averageSafe()=if(isEmpty())0f else average().toFloat()
    private fun ArrayDeque<Int>.averageSafe()=if(isEmpty())0f else average().toFloat()
}

// -----------------------------------------------------------------------------
// High-level render engine
// -----------------------------------------------------------------------------

class RenderEngineV2(private val maxSprites:Int=2048){
    val camera=RenderCamera2D()
    val queue=RenderQueue()
    val batch=SpriteBatchV2(maxSprites)
    val textures=RenderTextureRegistry()
    val state=RenderStateCache()
    val frameGraph=RenderFrameGraph()
    val profiler=RenderProfiler()
    var frameId=0L
        private set
    var clearColor=RenderColor(.055f,.075f,.105f,1f)
    var sortMode=RenderSortMode.Z_INDEX
    var cullingEnabled=true
    var initialized=false
        private set

    fun initialize(width:Int,height:Int){camera.resize(width,height);state.reset();initialized=true}
    fun begin(){require(initialized){"RenderEngineV2 must be initialized before begin()"};frameId++;frameGraph.clear();queue.clear();queue.sortMode=sortMode;batch.beginFrame();frameGraph.add("begin")}
    fun submit(command:RenderCommand){queue.add(command)}
    fun prepare(){queue.sort();frameGraph.add("sort");if(cullingEnabled){val before=queue.size();queue.cull(camera);queue.culled+=0;frameGraph.add("cull:$before->${queue.size()}")}}
    fun end(){batch.endFrame();frameGraph.add("submit");profiler.record(0f,0f,batch.drawCalls);frameGraph.add("end")}
    fun stats():RenderFrameStats=RenderFrameStats(frameId,queue.size(),queue.size(),queue.culled,batch.sprites,batch.drawCalls,batch.flushes)
}

// -----------------------------------------------------------------------------
// Picking and viewport tools
// -----------------------------------------------------------------------------

class RenderPicker(private val camera:RenderCamera2D){
    fun pick(commands:List<RenderCommand>,screenX:Float,screenY:Float):RenderCommand?{
        val world=camera.screenToWorld(screenX,screenY)
        return commands.asReversed().firstOrNull{c->
            if(!c.visible)return@firstOrNull false
            val w=kotlin.math.abs(c.sprite.width*c.transform.scaleX);val h=kotlin.math.abs(c.sprite.height*c.transform.scaleY)
            RenderRect(c.transform.x-w*c.sprite.pivotX,c.transform.y-h*c.sprite.pivotY,w,h).contains(world.x,world.y)
        }
    }
}

class RenderViewportTools(private val camera:RenderCamera2D){
    fun pan(screenDx:Float,screenDy:Float){camera.position.x-=screenDx/camera.zoom;camera.position.y-=screenDy/camera.zoom}
    fun zoomAt(screenX:Float,screenY:Float,factor:Float){
        val before=camera.screenToWorld(screenX,screenY);camera.setZoom(camera.zoom*factor);val after=camera.screenToWorld(screenX,screenY)
        camera.position.x+=before.x-after.x;camera.position.y+=before.y-after.y
    }
    fun rotate(delta:Float){camera.rotation+=delta}
}

// -----------------------------------------------------------------------------
// Render diagnostics
// -----------------------------------------------------------------------------

data class RenderCapabilities(val maxTextureSize:Int,val maxTextureUnits:Int,val maxVertexAttribs:Int,val glVersion:String,val vendor:String,val renderer:String)

object RenderDiagnostics{
    fun capabilities():RenderCapabilities{
        val maxTex=IntArray(1);val units=IntArray(1);val attribs=IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,maxTex,0);GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_IMAGE_UNITS,units,0);GLES20.glGetIntegerv(GLES20.GL_MAX_VERTEX_ATTRIBS,attribs,0)
        return RenderCapabilities(maxTex[0],units[0],attribs[0],GLES20.glGetString(GLES20.GL_VERSION)? :"",GLES20.glGetString(GLES20.GL_VENDOR)? :"",GLES20.glGetString(GLES20.GL_RENDERER)? :"")
    }
}

// -----------------------------------------------------------------------------
// Render asset manifest
// -----------------------------------------------------------------------------

data class RenderAssetRecord(val key:String,val path:String,val width:Int,val height:Int,val filter:RenderFilter,val premultipliedAlpha:Boolean=false)

class RenderAssetManifest{
    private val records=LinkedHashMap<String,RenderAssetRecord>()
    fun register(record:RenderAssetRecord){records[record.key]=record}
    fun remove(key:String){records.remove(key)}
    fun find(key:String):RenderAssetRecord?=records[key]
    fun all():List<RenderAssetRecord>=records.values.toList()
    fun clear(){records.clear()}
}

// -----------------------------------------------------------------------------
// Render command builder
// -----------------------------------------------------------------------------

class RenderCommandBuilder(private val entityId:Int){
    private var texture:String?=null
    private var material="sprite"
    private var transform=RenderTransform()
    private var sprite=RenderSprite()
    private var z=0
    private var visible=true
    private var layer=0
    fun texture(key:String?):RenderCommandBuilder{texture=key;sprite.texture=key;return this}
    fun material(key:String):RenderCommandBuilder{material=key;return this}
    fun position(x:Float,y:Float):RenderCommandBuilder{transform.x=x;transform.y=y;return this}
    fun rotation(radians:Float):RenderCommandBuilder{transform.rotation=radians;return this}
    fun scale(x:Float,y:Float=x):RenderCommandBuilder{transform.scaleX=x;transform.scaleY=y;return this}
    fun size(width:Float,height:Float):RenderCommandBuilder{sprite.width=width;sprite.height=height;return this}
    fun pivot(x:Float,y:Float):RenderCommandBuilder{sprite.pivotX=x;sprite.pivotY=y;return this}
    fun tint(color:RenderColor):RenderCommandBuilder{sprite.tint=color;return this}
    fun uv(region:RenderUvRect):RenderCommandBuilder{sprite.region=region;return this}
    fun zIndex(value:Int):RenderCommandBuilder{z=value;return this}
    fun layer(value:Int):RenderCommandBuilder{layer=value;return this}
    fun visible(value:Boolean):RenderCommandBuilder{visible=value;return this}
    fun flip(horizontal:Boolean,vertical:Boolean):RenderCommandBuilder{sprite.flipX=horizontal;sprite.flipY=vertical;return this}
    fun build():RenderCommand=RenderCommand(entityId,texture,material,transform.copy(),sprite.copy(tint=sprite.tint.copy()),z,visible,layer)
}
