package com.nova.engine

import kotlin.math.max
import kotlin.math.min

/** Production-oriented runtime services kept independent from the editor UI. */
class ProductionRuntime {
    val input = InputMap()
    val audio = AudioMixer()
    val assets = AssetDatabase()
    val prefabs = PrefabDatabase()
    val scenes = SceneDatabase()
    val save = SaveManager()
    val profiler = RuntimeProfiler()

    private var running = false
    private var accumulator = 0.0
    private val fixedDelta = 1.0 / 60.0

    fun start() { running = true; accumulator = 0.0 }
    fun stop() { running = false }
    fun isRunning(): Boolean = running

    fun tick(deltaSeconds: Double): Int {
        if (!running) return 0
        accumulator += min(deltaSeconds, 0.25)
        var steps = 0
        while (accumulator >= fixedDelta && steps < 8) {
            accumulator -= fixedDelta
            steps++
        }
        profiler.recordFrame(deltaSeconds, steps)
        return steps
    }
}

data class InputAction(val name: String, val keys: MutableSet<String> = linkedSetOf())
class InputMap {
    private val actions = linkedMapOf<String, InputAction>()
    fun define(name: String, vararg keys: String) { actions[name] = InputAction(name, keys.toMutableSet()) }
    fun bind(name: String, key: String) { actions.getOrPut(name) { InputAction(name) }.keys += key }
    fun actions(): List<InputAction> = actions.values.toList()
}

data class AudioBus(val name: String, var volume: Float = 1f, var muted: Boolean = false, var parent: String? = null)
class AudioMixer {
    private val buses = linkedMapOf("Master" to AudioBus("Master"))
    fun createBus(name: String, parent: String = "Master") { buses[name] = AudioBus(name, parent = parent) }
    fun setVolume(name: String, value: Float) { buses[name]?.volume = value.coerceIn(0f, 2f) }
    fun setMuted(name: String, muted: Boolean) { buses[name]?.muted = muted }
    fun bus(name: String): AudioBus? = buses[name]
    fun allBuses(): List<AudioBus> = buses.values.toList()
}

data class AssetRecord(val id: String, val path: String, val type: String, var imported: Boolean = false, var hash: String = "")
class AssetDatabase {
    private val records = linkedMapOf<String, AssetRecord>()
    fun register(id: String, path: String, type: String, hash: String = "") { records[id] = AssetRecord(id, path, type, true, hash) }
    fun find(id: String): AssetRecord? = records[id]
    fun all(): List<AssetRecord> = records.values.toList()
    fun remove(id: String) { records.remove(id) }
}

data class Prefab(val id: String, val name: String, val sceneJson: String, val version: Int = 1)
class PrefabDatabase {
    private val prefabs = linkedMapOf<String, Prefab>()
    fun put(prefab: Prefab) { prefabs[prefab.id] = prefab }
    fun get(id: String): Prefab? = prefabs[id]
    fun all(): List<Prefab> = prefabs.values.toList()
}

data class SceneAsset(val id: String, val name: String, var json: String, var dirty: Boolean = false)
class SceneDatabase {
    private val scenes = linkedMapOf<String, SceneAsset>()
    var activeSceneId: String? = null
    fun put(scene: SceneAsset) { scenes[scene.id] = scene; if (activeSceneId == null) activeSceneId = scene.id }
    fun get(id: String): SceneAsset? = scenes[id]
    fun all(): List<SceneAsset> = scenes.values.toList()
}

class SaveManager {
    fun validateSceneJson(json: String): Boolean {
        val s = json.trim()
        return s.startsWith("{") && s.endsWith("}") && s.contains("scene")
    }
}

data class RuntimeFrameStats(var fps: Float = 0f, var frameMs: Float = 0f, var fixedSteps: Int = 0)
class RuntimeProfiler {
    private var last = RuntimeFrameStats()
    fun recordFrame(delta: Double, steps: Int) {
        val ms = (delta * 1000.0).toFloat().coerceAtLeast(0f)
        last = RuntimeFrameStats(if (ms > 0f) 1000f / ms else 0f, ms, steps)
    }
    fun stats(): RuntimeFrameStats = last
}

/** Small, dependency-free collision helper used by gameplay systems and tests. */
data class Aabb(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun intersects(other: Aabb): Boolean = x < other.x + other.width && x + width > other.x && y < other.y + other.height && y + height > other.y
    fun overlapX(other: Aabb): Float = min(x + width, other.x + other.width) - max(x, other.x)
    fun overlapY(other: Aabb): Float = min(y + height, other.y + other.height) - max(y, other.y)
}
