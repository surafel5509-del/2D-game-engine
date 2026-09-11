package com.nova.engine

/** Core data model used by the in-app 2D editor. */
data class Asset2D(val id: String, val path: String, val type: AssetType, val sizeBytes: Long = 0)
enum class AssetType { TEXTURE, AUDIO, SCENE, SCRIPT, TILEMAP, FONT, OTHER }

data class AnimationFrame(val textureId: String, val durationMs: Int)
data class AnimationClip(val name: String, val frames: MutableList<AnimationFrame> = mutableListOf(), var loop: Boolean = true, var speed: Float = 1f)

data class EditorProject(
    var projectName: String = "Nova Game",
    var sceneName: String = "MainScene",
    val assets: MutableList<Asset2D> = mutableListOf(),
    val animations: MutableList<AnimationClip> = mutableListOf()
) {
    fun seedDemoContent() {
        if (assets.isEmpty()) {
            assets += Asset2D("player", "assets/sprites/player.png", AssetType.TEXTURE)
            assets += Asset2D("tiles", "assets/tiles/terrain.png", AssetType.TEXTURE)
            assets += Asset2D("music", "assets/audio/music.ogg", AssetType.AUDIO)
            assets += Asset2D("main", "scenes/MainScene.nova", AssetType.SCENE)
        }
        if (animations.isEmpty()) {
            animations += AnimationClip("Idle", mutableListOf(
                AnimationFrame("player", 120), AnimationFrame("player", 120)
            ))
            animations += AnimationClip("Run", mutableListOf(
                AnimationFrame("player", 90), AnimationFrame("player", 90), AnimationFrame("player", 90), AnimationFrame("player", 90)
            ))
        }
    }
}

data class EditorEntity(
    val id: Int,
    var name: String,
    var x: Float,
    var y: Float,
    var width: Float = 96f,
    var height: Float = 96f,
    var visible: Boolean = true,
    var locked: Boolean = false,
    var layer: Int = 0,
    var textureId: String = "player",
    var tag: String = "Player"
)
