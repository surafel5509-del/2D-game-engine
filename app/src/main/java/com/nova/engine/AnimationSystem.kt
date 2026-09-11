package com.nova.engine

/** Deterministic frame animation runtime usable by the editor and gameplay. */
class AnimationPlayer {
    private val clips = LinkedHashMap<String, AnimationClip>()
    var current: String = ""
        private set
    var frameIndex: Int = 0
        private set
    var playing: Boolean = false
        private set
    private var elapsed = 0f

    fun add(clip: AnimationClip) { clips[clip.name] = clip }
    fun addAll(values: Iterable<AnimationClip>) { values.forEach(::add) }
    fun play(name: String, restart: Boolean = false) {
        if (name !in clips) return
        if (current != name || restart) { current = name; frameIndex = 0; elapsed = 0f }
        playing = true
    }
    fun stop() { playing = false }
    fun update(dtSeconds: Float) {
        if (!playing) return
        val clip = clips[current] ?: return
        if (clip.frames.isEmpty()) return
        elapsed += dtSeconds * clip.speed
        while (elapsed * 1000f >= clip.frames[frameIndex].durationMs) {
            elapsed -= clip.frames[frameIndex].durationMs / 1000f
            frameIndex++
            if (frameIndex >= clip.frames.size) {
                if (clip.loop) frameIndex = 0 else { frameIndex = clip.frames.lastIndex; playing = false; break }
            }
        }
    }
    fun currentFrame(): AnimationFrame? = clips[current]?.frames?.getOrNull(frameIndex)
    fun names(): List<String> = clips.keys.toList()
}

class AssetRegistry {
    private val assets = LinkedHashMap<String, Asset2D>()
    fun register(asset: Asset2D) { assets[asset.id] = asset }
    fun registerAll(values: Iterable<Asset2D>) { values.forEach(::register) }
    fun get(id: String): Asset2D? = assets[id]
    fun all(): List<Asset2D> = assets.values.toList()
    fun byType(type: AssetType): List<Asset2D> = assets.values.filter { it.type == type }
    fun remove(id: String) { assets.remove(id) }
    fun clear() = assets.clear()
}
