package com.nova.engine

import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min

/** Deterministic runtime clock with a bounded catch-up step. */
class EngineClock(private val fixedDelta: Float = 1f / 60f, private val maxFrameDelta: Float = .25f) {
    private var last = SystemClock.elapsedRealtimeNanos()
    private var accumulator = 0f
    var timeScale = 1f
    var frameDelta = fixedDelta
        private set
    var stepsThisFrame = 0
        private set

    fun tick(step: (Float) -> Unit) {
        val now = SystemClock.elapsedRealtimeNanos()
        frameDelta = min(max((now - last) / 1_000_000_000f, 0f), maxFrameDelta) * timeScale
        last = now
        accumulator += frameDelta
        stepsThisFrame = 0
        while (accumulator >= fixedDelta && stepsThisFrame < 8) {
            step(fixedDelta)
            accumulator -= fixedDelta
            stepsThisFrame++
        }
        if (stepsThisFrame == 8) accumulator = 0f
    }
}

data class Camera2D(var x: Float = 0f, var y: Float = 0f, var zoom: Float = 1f, var rotation: Float = 0f) {
    var minZoom = .05f
    var maxZoom = 20f
    fun setZoom(value: Float) { zoom = value.coerceIn(minZoom, maxZoom) }
    fun screenToWorld(sx: Float, sy: Float, viewportW: Float, viewportH: Float): Vec2 {
        return Vec2((sx - viewportW / 2f) / zoom + x, (sy - viewportH / 2f) / zoom + y)
    }
    fun worldToScreen(wx: Float, wy: Float, viewportW: Float, viewportH: Float): Vec2 {
        return Vec2((wx - x) * zoom + viewportW / 2f, (wy - y) * zoom + viewportH / 2f)
    }
}

data class Contact2D(val a: String, val b: String, val normalX: Float, val normalY: Float, val penetration: Float, val sensor: Boolean)

class CollisionDispatcher {
    private val listeners = mutableListOf<(Contact2D) -> Unit>()
    fun subscribe(listener: (Contact2D) -> Unit) { listeners += listener }
    fun emit(contact: Contact2D) { listeners.toList().forEach { it(contact) } }
    fun clear() = listeners.clear()
}

/** Runtime facade. Editor data remains separate from the native simulation and can be hot-reloaded. */
class Runtime2D {
    val clock = EngineClock()
    val camera = Camera2D()
    val collisions = CollisionDispatcher()
    private val native = NativeEngine()
    private var running = false
    private var playerId = -1

    fun start() {
        native.init()
        playerId = native.createEntity(160f, 120f)
        running = true
    }

    fun update() {
        if (!running) return
        clock.tick { native.step(it) }
    }

    fun setPlayerVelocity(vx: Float, vy: Float) {
        if (playerId >= 0) native.setVelocity(playerId, vx, vy)
    }

    fun transforms(): FloatArray = native.getTransforms()
    fun stop() { running = false; native.clear(); playerId = -1 }
    fun isRunning() = running
}

/** Lightweight frame profiler for the in-editor diagnostics overlay. */
class FrameProfiler(private val capacity: Int = 120) {
    private val frames = ArrayDeque<Float>()
    fun record(ms: Float) { frames.addLast(ms); if (frames.size > capacity) frames.removeFirst() }
    fun averageMs(): Float = if (frames.isEmpty()) 0f else frames.sum() / frames.size
    fun fps(): Float = if (averageMs() <= 0f) 0f else 1000f / averageMs()
    fun samples(): List<Float> = frames.toList()
}
