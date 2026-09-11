package com.nova.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/** Runtime texture metadata. UVs are normalized and independent of source resolution. */
data class TextureRegion(val textureId: String, val u0: Float, val v0: Float, val u1: Float, val v1: Float, val width: Int, val height: Int)

data class AtlasRegion(val name: String, val x: Int, val y: Int, val width: Int, val height: Int, val rotated: Boolean = false)

data class SpriteAtlas(val texturePath: String, val width: Int, val height: Int, val regions: Map<String, AtlasRegion>) {
    fun region(name: String): AtlasRegion? = regions[name]
}

class TextureCache(private val context: Context) : Closeable {
    private val bitmaps = ConcurrentHashMap<String, Bitmap>()
    private val glTextures = ConcurrentHashMap<String, Int>()

    fun loadBitmap(path: String): Bitmap? {
        bitmaps[path]?.let { return it }
        val bitmap = runCatching {
            context.assets.open(path).use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null
        bitmaps[path] = bitmap
        return bitmap
    }

    fun upload(path: String): Int {
        glTextures[path]?.let { return it }
        val bitmap = loadBitmap(path) ?: return 0
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        glTextures[path] = ids[0]
        return ids[0]
    }

    fun evict(path: String) {
        glTextures.remove(path)?.let { GLES20.glDeleteTextures(1, intArrayOf(it), 0) }
        bitmaps.remove(path)?.recycle()
    }

    override fun close() {
        glTextures.values.forEach { GLES20.glDeleteTextures(1, intArrayOf(it), 0) }
        glTextures.clear()
        bitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
        bitmaps.clear()
    }
}

/** Minimal GLES2 sprite batch: one shader, one indexed quad, reused for every sprite. */
class SpriteBatchRenderer {
    private var program = 0
    private var position = -1
    private var uv = -1
    private var texture = -1
    private var color = -1

    fun initialize() {
        if (program != 0) return
        val vertex = """
            attribute vec2 aPosition;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main(){ vUv=aUv; gl_Position=vec4(aPosition,0.0,1.0); }
        """.trimIndent()
        val fragment = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec4 uColor;
            varying vec2 vUv;
            void main(){ gl_FragColor=texture2D(uTexture,vUv)*uColor; }
        """.trimIndent()
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs); GLES20.glAttachShader(program, fs); GLES20.glLinkProgram(program)
        val ok = IntArray(1); GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) { val log = GLES20.glGetProgramInfoLog(program); GLES20.glDeleteProgram(program); program = 0; error("Nova sprite shader link failed: $log") }
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        position = GLES20.glGetAttribLocation(program, "aPosition")
        uv = GLES20.glGetAttribLocation(program, "aUv")
        texture = GLES20.glGetUniformLocation(program, "uTexture")
        color = GLES20.glGetUniformLocation(program, "uColor")
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader)
        val ok = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { val log = GLES20.glGetShaderInfoLog(shader); GLES20.glDeleteShader(shader); error("Nova shader compile failed: $log") }
        return shader
    }

    fun release() { if (program != 0) { GLES20.glDeleteProgram(program); program = 0 } }
}
