package com.nova.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/** Nova Render V2: camera, transforms, queue, culling, batching, materials and GPU state. */

data class RenderVec2(var x: Float = 0f, var y: Float = 0f) {
    fun set(nx: Float, ny: Float): RenderVec2 { x = nx; y = ny; return this }
    fun add(v: RenderVec2): RenderVec2 { x += v.x; y += v.y; return this }
    fun sub(v: RenderVec2): RenderVec2 { x -= v.x; y -= v.y; return this }
    fun scale(s: Float): RenderVec2 { x *= s; y *= s; return this }
    fun copyOf() = RenderVec2(x, y)
}

data class RenderRect(var x: Float, var y: Float, var width: Float, var height: Float) {
    fun contains(px: Float, py: Float): Boolean = px >= x && py >= y && px <= x + width && py <= y + height
    fun intersects(o: RenderRect): Boolean = x < o.x + o.width && x + width > o.x && y < o.y + o.height && y + height > o.y
}

data class RenderColor(var r: Float = 1f, var g: Float = 1f, var b: Float = 1f, var a: Float = 1f) {
    fun normalized(): RenderColor = copy(r.coerceIn(0f,1f), g.coerceIn(0f,1f), b.coerceIn(0f,1f), a.coerceIn(0f,1f))
    fun packed(): Int { val c = normalized(); return ((c.a*255).toInt() shl 24) or ((c.r*255).toInt() shl 16) or ((c.g*255).toInt() shl 8) or (c.b*255).toInt() }
}

class RenderMatrix3 {
    val values = FloatArray(9)
    init { identity() }
    fun identity(): RenderMatrix3 { values.fill(0f); values[0]=1f; values[4]=1f; values[8]=1f; return this }
    fun translate(x:Float,y:Float):RenderMatrix3 { values[6]+=x; values[7]+=y; return this }
    fun scale(x:Float,y:Float):RenderMatrix3 { values[0]*=x; values[4]*=y; return this }
    fun rotate(r:Float):RenderMatrix3 { val c=cos(r); val s=sin(r); val a=values[0]; val b=values[1]; val d=values[3]; val e=values[4]; values[0]=a*c+b*s; values[1]=-a*s+b*c; values[3]=d*c+e*s; values[4]=-d*s+e*c; return this }
    fun copyOf()=RenderMatrix3().also{values.copyInto(it.values)}
}

class RenderCamera2D {
    var position=RenderVec2()
    var zoom=1f
    var rotation=0f
    var viewportWidth=1f
    var viewportHeight=1f
    var pixelSnap=false
    var minZoom=.05f
    var maxZoom=32f
    fun resize(w:Int,h:Int){viewportWidth=w.coerceAtLeast(1).toFloat();viewportHeight=h.coerceAtLeast(1).toFloat()}
    fun setZoom(value:Float){zoom=value.coerceIn(minZoom,maxZoom)}
    fun screenToWorld(sx:Float,sy:Float):RenderVec2{
        val dx=(sx-viewportWidth*.5f)/zoom; val dy=(sy-viewportHeight*.5f)/zoom
        val c=cos(rotation);val s=sin(rotation);val x=dx*c-dy*s+position.x;val y=dx*s+dy*c+position.y
        return if(pixelSnap)RenderVec2(round(x),round(y))else RenderVec2(x,y)
    }
    fun worldToScreen(wx:Float,wy:Float):RenderVec2{
        val dx=wx-position.x;val dy=wy-position.y;val c=cos(rotation);val s=sin(rotation)
        return RenderVec2((dx*c+dy*s)*zoom+viewportWidth*.5f,(-dx*s+dy*c)*zoom+viewportHeight*.5f)
    }
    fun visibleWorldBounds():RenderRect=RenderRect(position.x-viewportWidth/(2f*zoom),position.y-viewportHeight/(2f*zoom),viewportWidth/zoom,viewportHeight/zoom)
    fun matrix4():FloatArray{
        val sx=2f*zoom/viewportWidth;val sy=-2f*zoom/viewportHeight;val c=cos(rotation);val s=sin(rotation)
        return floatArrayOf(sx*c,sy*s,0f,0f,-sx*s,sy*c,0f,0f,0f,0f,-1f,0f,-position.x*sx*c+position.y*sx*s,-position.x*sy*s-position.y*sy*c,0f,1f)
    }
}

enum class RenderBlendMode { OPAQUE, ALPHA, ADDITIVE, MULTIPLY }
enum class RenderFilter { NEAREST, LINEAR }
enum class RenderSortMode { LAYER_Z, Y, MATERIAL, TEXTURE }

data class RenderUvRect(val u0:Float=0f,val v0:Float=0f,val u1:Float=1f,val v1:Float=1f)
data class RenderSprite(var texture:String?=null,var region:RenderUvRect=RenderUvRect(),var width:Float=32f,var height:Float=32f,var pivotX:Float=.5f,var pivotY:Float=.5f,var flipX:Boolean=false,var flipY:Boolean=false,var tint:RenderColor=RenderColor())
data class RenderTransform(var x:Float=0f,var y:Float=0f,var rotation:Float=0f,var scaleX:Float=1f,var scaleY:Float=1f)
data class RenderMaterial(var textureKey:String?=null,var blendMode:RenderBlendMode=RenderBlendMode.ALPHA,var filter:RenderFilter=RenderFilter.LINEAR,var color:RenderColor=RenderColor(),var shaderKey:String="sprite")
data class RenderCommand(val entityId:Int,val textureKey:String?,val materialKey:String,val transform:RenderTransform,val sprite:RenderSprite,val zIndex:Int=0,val layer:Int=0,val visible:Boolean=true)

data class RenderTextureHandle(val key:String,val glId:Int,val width:Int,val height:Int)
data class RenderAssetRecord(val key:String,val path:String,val width:Int,val height:Int,val filter:RenderFilter=RenderFilter.LINEAR,val premultipliedAlpha:Boolean=false)

class RenderQueue(private val capacity:Int=4096){
    private val commands=ArrayList<RenderCommand>(capacity)
    var sortMode=RenderSortMode.LAYER_Z
    var culled=0;private set
    fun clear(){commands.clear();culled=0}
    fun add(command:RenderCommand){if(command.visible&&commands.size<capacity)commands+=command}
    fun size()=commands.size
    fun all():List<RenderCommand>=commands
    fun sort(){when(sortMode){RenderSortMode.LAYER_Z->commands.sortWith(compareBy<RenderCommand>{it.layer}.thenBy{it.zIndex}.thenBy{it.materialKey}.thenBy{it.textureKey});RenderSortMode.Y->commands.sortWith(compareBy<RenderCommand>{it.transform.y}.thenBy{it.zIndex});RenderSortMode.MATERIAL->commands.sortWith(compareBy<RenderCommand>{it.materialKey}.thenBy{it.textureKey}.thenBy{it.zIndex});RenderSortMode.TEXTURE->commands.sortWith(compareBy<RenderCommand>{it.textureKey}.thenBy{it.materialKey}.thenBy{it.zIndex})}}
    fun cull(camera:RenderCamera2D){val view=camera.visibleWorldBounds();val it=commands.iterator();while(it.hasNext()){val c=it.next();val w=abs(c.sprite.width*c.transform.scaleX);val h=abs(c.sprite.height*c.transform.scaleY);val r=RenderRect(c.transform.x-w*c.sprite.pivotX,c.transform.y-h*c.sprite.pivotY,w,h);if(!r.intersects(view)){it.remove();culled++}}}
}

class RenderTextureRegistry(private val limit:Int=512){
    private val handles=LinkedHashMap<String,RenderTextureHandle>(16,.75f,true);private val pinned=HashSet<String>()
    fun put(h:RenderTextureHandle){handles[h.key]=h;trim()}
    fun get(key:String)=handles[key]
    fun pin(key:String){pinned+=key}
    fun unpin(key:String){pinned-=key}
    fun remove(key:String)=handles.remove(key)
    fun clear(){handles.clear();pinned.clear()}
    fun keys()=handles.keys.toList()
    fun size()=handles.size
    private fun trim(){val it=handles.entries.iterator();while(handles.size>limit&&it.hasNext()){val e=it.next();if(e.key !in pinned)it.remove()}}
}

class RenderAssetManifest{private val records=LinkedHashMap<String,RenderAssetRecord>();fun register(r:RenderAssetRecord){records[r.key]=r};fun find(k:String)=records[k];fun remove(k:String){records.remove(k)};fun all()=records.values.toList();fun clear(){records.clear()}}

class RenderStateCache{
    private var program=-1;private var texture=-1;private var blend=RenderBlendMode.OPAQUE
    var programBinds=0;private set
    var textureBinds=0;private set
    fun reset(){program=-1;texture=-1;blend=RenderBlendMode.OPAQUE;programBinds=0;textureBinds=0}
    fun bindProgram(id:Int){if(id!=program){GLES20.glUseProgram(id);program=id;programBinds++}}
    fun bindTexture(id:Int){if(id!=texture){GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id);texture=id;textureBinds++}}
    fun setBlend(mode:RenderBlendMode){if(mode==blend)return;blend=mode;when(mode){RenderBlendMode.OPAQUE->GLES20.glDisable(GLES20.GL_BLEND);RenderBlendMode.ALPHA->{GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA)};RenderBlendMode.ADDITIVE->{GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE)};RenderBlendMode.MULTIPLY->{GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_DST_COLOR,GLES20.GL_ZERO)}}}
}

class SpriteBatchV2(private val maxSprites:Int=2048){
    private val buffer:FloatBuffer=ByteBuffer.allocateDirect(maxSprites*16*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var count=0;private var activeTexture=-1;private var activeMaterial=""
    var sprites=0;private set;var drawCalls=0;private set;var flushes=0;private set
    fun beginFrame(){count=0;activeTexture=-1;activeMaterial="";sprites=0;drawCalls=0;flushes=0}
    fun add(command:RenderCommand,textureId:Int){if(count>0&&(textureId!=activeTexture||command.materialKey!=activeMaterial))flush();if(count>=maxSprites)flush();activeTexture=textureId;activeMaterial=command.materialKey;val t=command.transform;val s=command.sprite;val w=s.width*t.scaleX;val h=s.height*t.scaleY;val l=t.x-w*s.pivotX;val r=l+w;val top=t.y-h*s.pivotY;val b=top+h;val uv=s.region;val u0=if(s.flipX)uv.u1 else uv.u0;val u1=if(s.flipX)uv.u0 else uv.u1;val v0=if(s.flipY)uv.v1 else uv.v0;val v1=if(s.flipY)uv.v0 else uv.v1;put(l,top,u0,v0);put(r,top,u1,v0);put(l,b,u0,v1);put(r,b,u1,v1);count++;sprites++}
    private fun put(x:Float,y:Float,u:Float,v:Float){buffer.put(x).put(y).put(u).put(v)}
    fun flush(){if(count==0)return;buffer.position(0);drawCalls++;flushes++;count=0}
    fun endFrame(){flush()}
    fun data():FloatBuffer=buffer.duplicate().also{it.position(0)}
}

class RenderShaderProgram(private val vertexSource:String,private val fragmentSource:String){
    var id=0;private set;var position=-1;private set;var uv=-1;private set;var mvp=-1;private set;var color=-1;private set;var texture=-1;private set
    fun create(){if(id!=0)return;val vs=compile(GLES20.GL_VERTEX_SHADER,vertexSource);val fs=compile(GLES20.GL_FRAGMENT_SHADER,fragmentSource);id=GLES20.glCreateProgram();GLES20.glAttachShader(id,vs);GLES20.glAttachShader(id,fs);GLES20.glLinkProgram(id);val ok=IntArray(1);GLES20.glGetProgramiv(id,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]!=GLES20.GL_TRUE){val log=GLES20.glGetProgramInfoLog(id);GLES20.glDeleteProgram(id);id=0;throw IllegalStateException(log)};GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);position=GLES20.glGetAttribLocation(id,"aPosition");uv=GLES20.glGetAttribLocation(id,"aUv");mvp=GLES20.glGetUniformLocation(id,"uMvp");color=GLES20.glGetUniformLocation(id,"uColor");texture=GLES20.glGetUniformLocation(id,"uTexture")}
    private fun compile(type:Int,source:String):Int{val s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);val ok=IntArray(1);GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]!=GLES20.GL_TRUE){val log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw IllegalStateException(log)};return s}
    fun destroy(){if(id!=0){GLES20.glDeleteProgram(id);id=0}}
}

class RenderPicker(private val camera:RenderCamera2D){fun pick(commands:List<RenderCommand>,sx:Float,sy:Float):RenderCommand?{val p=camera.screenToWorld(sx,sy);return commands.asReversed().firstOrNull{val s=it.sprite;val w=abs(s.width*it.transform.scaleX);val h=abs(s.height*it.transform.scaleY);RenderRect(it.transform.x-w*s.pivotX,it.transform.y-h*s.pivotY,w,h).contains(p.x,p.y)}}}

class RenderViewportTools(private val camera:RenderCamera2D){fun pan(dx:Float,dy:Float){camera.position.x-=dx/camera.zoom;camera.position.y-=dy/camera.zoom};fun zoomAt(sx:Float,sy:Float,factor:Float){val before=camera.screenToWorld(sx,sy);camera.setZoom(camera.zoom*factor);val after=camera.screenToWorld(sx,sy);camera.position.x+=before.x-after.x;camera.position.y+=before.y-after.y};fun rotate(delta:Float){camera.rotation+=delta}}

data class RenderFrameStats(val frame:Long,val submitted:Int,val culled:Int,val sprites:Int,val drawCalls:Int,val flushes:Int,val textureBinds:Int,val programBinds:Int)

class RenderEngineV2(private val maxSprites:Int=2048){
    val camera=RenderCamera2D();val queue=RenderQueue();val textures=RenderTextureRegistry();val assets=RenderAssetManifest();val state=RenderStateCache();val batch=SpriteBatchV2(maxSprites);val picker=RenderPicker(camera);val tools=RenderViewportTools(camera)
    var cullingEnabled=true;var frame=0L;private set
    fun initialize(width:Int,height:Int){camera.resize(width,height);state.reset()}
    fun beginFrame(){frame++;queue.clear();batch.beginFrame()}
    fun submit(command:RenderCommand){queue.add(command)}
    fun prepare(){queue.sort();if(cullingEnabled)queue.cull(camera)}
    fun render(textureResolver:(String?)->Int){queue.all().forEach{batch.add(it,textureResolver(it.textureKey))};batch.endFrame()}
    fun endFrame():RenderFrameStats=RenderFrameStats(frame,queue.size()+queue.culled,queue.culled,batch.sprites,batch.drawCalls,batch.flushes,state.textureBinds,state.programBinds)
}

object RenderDefaults{
    const val VERTEX="uniform mat4 uMvp; attribute vec2 aPosition; attribute vec2 aUv; varying vec2 vUv; void main(){vUv=aUv;gl_Position=uMvp*vec4(aPosition,0.0,1.0);}"
    const val FRAGMENT="precision mediump float; uniform sampler2D uTexture; uniform vec4 uColor; varying vec2 vUv; void main(){gl_FragColor=texture2D(uTexture,vUv)*uColor;}"
}
