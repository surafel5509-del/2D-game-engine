package com.nova.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

data class V2SpriteFrame(val id: String, val x: Int, val y: Int, val width: Int, val height: Int, val duration: Float = 1f / 12f)
data class V2SpriteSheet(val image: String, val frameWidth: Int, val frameHeight: Int, val columns: Int, val rows: Int) {
    fun frames(prefix: String = "frame"): List<V2SpriteFrame> = buildList {
        for (row in 0 until rows) for (col in 0 until columns) add(V2SpriteFrame("$prefix_${row}_$col", col * frameWidth, row * frameHeight, frameWidth, frameHeight))
    }
}

class V2AnimationMaker {
    private val clips = LinkedHashMap<String, AnimationClip>()
    fun create(name: String, frames: List<String>, fps: Float, mode: PlaybackMode = PlaybackMode.LOOP): AnimationClip =
        AnimationClip(name, frames.ifEmpty { listOf("empty") }, fps.coerceAtLeast(.1f), mode).also { clips[name] = it }
    fun fromSheet(name: String, sheet: V2SpriteSheet, fps: Float, mode: PlaybackMode = PlaybackMode.LOOP): AnimationClip = create(name, sheet.frames(name).map { it.id }, fps, mode)
    fun remove(name: String) { clips.remove(name) }
    fun all(): List<AnimationClip> = clips.values.toList()
    fun find(name: String): AnimationClip? = clips[name]
}

data class V2Keyframe(val time: Float, val value: Float)
data class V2Track(val property: String, val keys: MutableList<V2Keyframe> = mutableListOf()) {
    fun sample(time: Float): Float {
        if (keys.isEmpty()) return 0f
        val sorted = keys.sortedBy { it.time }
        if (time <= sorted.first().time) return sorted.first().value
        if (time >= sorted.last().time) return sorted.last().value
        val right = sorted.indexOfFirst { it.time >= time }.coerceAtLeast(1)
        val a = sorted[right - 1]; val b = sorted[right]
        val t = ((time - a.time) / max(.0001f, b.time - a.time)).coerceIn(0f, 1f)
        return a.value + (b.value - a.value) * t
    }
}
data class V2Timeline(val name: String, var duration: Float = 1f, val tracks: MutableList<V2Track> = mutableListOf()) {
    fun sample(property: String, time: Float): Float = tracks.firstOrNull { it.property == property }?.sample(time) ?: 0f
}

/** Deterministic sandbox script language used by the editor until a native bytecode VM is attached. */
class V2ScriptRuntime {
    data class State(val numbers: MutableMap<String, Float> = mutableMapOf(), val flags: MutableMap<String, Boolean> = mutableMapOf())
    data class Result(val logs: List<String>, val errors: List<String>, val changed: Boolean)
    fun execute(source: String, state: State = State(), dt: Float = 1f / 60f): Result {
        val logs = mutableListOf<String>(); val errors = mutableListOf<String>(); var changed = false
        source.lineSequence().mapIndexed { i, line -> i + 1 to line.trim() }.filter { it.second.isNotEmpty() && !it.second.startsWith("#") }.forEach { (line, code) ->
            val parts = code.split(Regex("\\s+")); val command = parts.firstOrNull()?.lowercase()
            when (command) {
                "set" -> if (parts.size >= 3) runCatching { state.numbers[parts[1]] = parts[2].toFloat(); changed = true }.onFailure { errors += "line $line: invalid number" } else errors += "line $line: set name value"
                "add" -> if (parts.size >= 3) runCatching { state.numbers[parts[1]] = (state.numbers[parts[1]] ?: 0f) + parts[2].toFloat(); changed = true }.onFailure { errors += "line $line: invalid add" } else errors += "line $line: add name value"
                "mul" -> if (parts.size >= 3) runCatching { state.numbers[parts[1]] = (state.numbers[parts[1]] ?: 0f) * parts[2].toFloat(); changed = true }.onFailure { errors += "line $line: invalid mul" } else errors += "line $line: mul name value"
                "move" -> if (parts.size >= 3) runCatching { state.numbers["x"] = (state.numbers["x"] ?: 0f) + parts[1].toFloat() * dt; state.numbers["y"] = (state.numbers["y"] ?: 0f) + parts[2].toFloat() * dt; changed = true }.onFailure { errors += "line $line: invalid move" } else errors += "line $line: move x y"
                "flag" -> if (parts.size >= 3) { state.flags[parts[1]] = parts[2].equals("true", true); changed = true } else errors += "line $line: flag name true|false"
                "print" -> logs += code.removePrefix("print").trim()
                else -> errors += "line $line: unknown command '${command ?: ""}'"
            }
        }
        return Result(logs, errors, changed)
    }
}

class V2AudioEngine(private val context: Context) {
    private val players = ConcurrentHashMap<String, MediaPlayer>()
    private val volumes = ConcurrentHashMap<String, Float>().apply { put("Master", 1f); put("Music", 1f); put("SFX", 1f) }
    fun setBusVolume(bus: String, value: Float) { volumes[bus] = value.coerceIn(0f, 1f); applyVolumes() }
    fun playFile(file: File, bus: String = "SFX", loop: Boolean = false): Boolean = runCatching {
        stop(file.name)
        val player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setDataSource(file.absolutePath); isLooping = loop
            setOnCompletionListener { if (!loop) { val p = players.remove(file.name); p?.release() } }
            prepare(); val v = (volumes["Master"] ?: 1f) * (volumes[bus] ?: 1f); setVolume(v, v); start()
        }
        players[file.name] = player; true
    }.getOrDefault(false)
    fun stop(id: String) { val p = players.remove(id) ?: return; runCatching { p.stop() }; p.release() }
    fun stopAll() { players.keys.toList().forEach(::stop) }
    fun active(): Int = players.size
    private fun applyVolumes() { val master = volumes["Master"] ?: 1f; players.values.forEach { it.setVolume(master, master) } }
}

class V2BitmapCache(private val maxEntries: Int = 96) {
    private val cache = LinkedHashMap<String, Bitmap>(maxEntries, .75f, true)
    @Synchronized fun get(path: String): Bitmap? = cache[path]
    @Synchronized fun put(path: String, bitmap: Bitmap) {
        cache[path]?.takeIf { it !== bitmap && !it.isRecycled }?.recycle(); cache[path] = bitmap
        while (cache.size > maxEntries) { val first = cache.entries.first(); cache.remove(first.key)?.takeIf { !it.isRecycled }?.recycle() }
    }
    @Synchronized fun load(file: File): Bitmap? = get(file.absolutePath) ?: BitmapFactory.decodeFile(file.absolutePath)?.also { put(file.absolutePath, it) }
    @Synchronized fun clear() { cache.values.forEach { if (!it.isRecycled) it.recycle() }; cache.clear() }
    @Synchronized fun size(): Int = cache.size
}
