package com.nova.engine

/** Deterministic frame animation with looping, ping-pong and one-shot modes. */
enum class PlaybackMode { LOOP, ONCE, PING_PONG }

data class AnimationClip(
    val name: String,
    val frames: List<String>,
    val fps: Float = 12f,
    val mode: PlaybackMode = PlaybackMode.LOOP
) {
    init { require(frames.isNotEmpty()); require(fps > 0f) }
}

class AnimationPlayer {
    private val clips = LinkedHashMap<String, AnimationClip>()
    var current: String? = null
        private set
    var frameIndex: Int = 0
        private set
    var playing: Boolean = false
        private set
    var time: Float = 0f
        private set
    private var direction = 1

    fun add(clip: AnimationClip): AnimationPlayer { clips[clip.name] = clip; return this }
    fun play(name: String, restart: Boolean = false) {
        require(clips.containsKey(name)) { "Unknown animation: $name" }
        if (current != name || restart) { current = name; frameIndex = 0; time = 0f; direction = 1 }
        playing = true
    }
    fun stop() { playing = false; time = 0f; frameIndex = 0; direction = 1 }
    fun update(dt: Float) {
        if (!playing || dt <= 0f) return
        val clip = clips[current] ?: return
        time += dt
        val step = 1f / clip.fps
        while (time >= step) { time -= step; advance(clip) }
    }
    fun frame(): String? = current?.let { clips[it]?.frames?.getOrNull(frameIndex) }

    private fun advance(clip: AnimationClip) {
        when (clip.mode) {
            PlaybackMode.LOOP -> frameIndex = (frameIndex + 1) % clip.frames.size
            PlaybackMode.ONCE -> if (frameIndex < clip.frames.lastIndex) frameIndex++ else playing = false
            PlaybackMode.PING_PONG -> {
                if (clip.frames.size == 1) return
                frameIndex += direction
                if (frameIndex >= clip.frames.lastIndex) { frameIndex = clip.frames.lastIndex; direction = -1 }
                else if (frameIndex <= 0) { frameIndex = 0; direction = 1 }
            }
        }
    }
}

/** Maps animation state names to clips and supports simple transition rules. */
class AnimationStateMachine {
    private val players = LinkedHashMap<String, AnimationPlayer>()
    private val transitions = LinkedHashMap<String, MutableSet<String>>()
    var state: String? = null
        private set

    fun state(name: String, player: AnimationPlayer): AnimationStateMachine { players[name] = player; transitions.putIfAbsent(name, mutableSetOf()); return this }
    fun allow(from: String, to: String): AnimationStateMachine { require(players.containsKey(from) && players.containsKey(to)); transitions.getOrPut(from) { mutableSetOf() }.add(to); return this }
    fun setInitial(name: String) { require(players.containsKey(name)); state = name; players[name]!!.play(name, true) }
    fun transition(to: String): Boolean {
        val from = state ?: return false
        if (from != to && to !in (transitions[from] ?: emptySet())) return false
        state = to; players[to]?.play(to, true); return true
    }
    fun update(dt: Float) { state?.let { players[it]?.update(dt) } }
    fun frame(): String? = state?.let { players[it]?.frame() }
}
