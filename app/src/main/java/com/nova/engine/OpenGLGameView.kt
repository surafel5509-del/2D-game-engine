package com.nova.engine

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

/** OpenGL ES 2.0 renderer: the first GPU rendering backend for Nova 2D. */
class OpenGLGameView(context: Context) : GLSurfaceView(context) {
    private val native = NativeEngine()
    private val renderer: NovaRenderer
    private var player = 0
    private var lastNanos = System.nanoTime()

    init {
        setEGLContextClientVersion(2)
        renderer = NovaRenderer(native)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        native.init()
        player = native.createEntity(160f, 300f)
        isFocusable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val vx = ((event.x - width / 2f) * 2f).coerceIn(-500f, 500f)
            native.setVelocity(player, vx, -700f)
            return true
        }
        return true
    }
}

private class NovaRenderer(private val native: NativeEngine) : GLSurfaceView.Renderer {
    private lateinit var program: SimpleColorProgram
    private var width = 1
    private var height = 1
    private var lastNanos = System.nanoTime()
    private val camera = Camera2D()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.055f, 0.075f, 0.105f, 1f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        program = SimpleColorProgram()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = max(1, w); height = max(1, h)
        GLES20.glViewport(0, 0, width, height)
        camera.setViewport(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastNanos) / 1e9).toFloat().coerceIn(0f, 0.25f)
        lastNanos = now
        native.step(dt)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val transforms = native.getTransforms()
        program.begin(camera)
        var i = 0
        var entity = 0
        while (i + 3 < transforms.size) {
            val color = if (entity == 0) floatArrayOf(0.40f, 0.85f, 1f, 1f)
                        else floatArrayOf(1f, 0.78f, 0.34f, 1f)
            program.drawRect(transforms[i], transforms[i + 1], transforms[i + 2], transforms[i + 3], color)
            i += 4; entity++
        }
        program.end()
    }
}

private class Camera2D {
    private var w = 1f
    private var h = 1f
    var x = 0f
    var y = 0f
    var zoom = 1f

    fun setViewport(width: Int, height: Int) { w = width.toFloat(); h = height.toFloat() }
    fun matrix(): FloatArray {
        val sx = 2f * zoom / w
        val sy = -2f * zoom / h
        return floatArrayOf(
            sx, 0f, 0f, 0f,
            0f, sy, 0f, 0f,
            0f, 0f, 1f, 0f,
            -1f - x * sx, 1f - y * sy, 0f, 1f
        )
    }
}

private class SimpleColorProgram {
    private val vertex = """
        uniform mat4 uMvp;
        attribute vec2 aPosition;
        void main() { gl_Position = uMvp * vec4(aPosition, 0.0, 1.0); }
    """.trimIndent()
    private val fragment = """
        precision mediump float;
        uniform vec4 uColor;
        void main() { gl_FragColor = uColor; }
    """.trimIndent()
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val handle: Int
    private var position = 0
    private var color = 0
    private var mvp = 0

    init {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        handle = GLES20.glCreateProgram().also { p ->
            GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
            val status = IntArray(1); GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
            require(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(p) }
            GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        }
        position = GLES20.glGetAttribLocation(handle, "aPosition")
        color = GLES20.glGetUniformLocation(handle, "uColor")
        mvp = GLES20.glGetUniformLocation(handle, "uMvp")
    }

    fun begin(camera: Camera2D) {
        GLES20.glUseProgram(handle)
        GLES20.glUniformMatrix4fv(mvp, 1, false, camera.matrix(), 0)
        GLES20.glEnableVertexAttribArray(position)
    }

    fun drawRect(x: Float, y: Float, w: Float, h: Float, rgba: FloatArray) {
        vertices.clear()
        vertices.put(floatArrayOf(x,y, x+w,y, x,y+h, x+w,y+h)).position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glUniform4fv(color, 1, rgba, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun end() { GLES20.glDisableVertexAttribArray(position); GLES20.glUseProgram(0) }

    private fun compile(type: Int, source: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, source); GLES20.glCompileShader(s)
        val status = IntArray(1); GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0)
        require(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(s) }
        return s
    }
}
