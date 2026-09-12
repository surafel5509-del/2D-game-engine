package com.nova.engine

/** Project configuration that can be serialized by the editor or a future desktop tooling layer. */
data class NovaProject(
    var name: String = "Nova 2D Game",
    var packageName: String = "com.nova.game",
    var versionName: String = "1.0.0",
    var versionCode: Int = 1,
    var width: Int = 1280,
    var height: Int = 720,
    var targetFps: Int = 60,
    var physicsHz: Int = 60,
    var mainScene: String = "res://scenes/MainScene.nova",
    var debug: Boolean = true
)

data class BuildProfile(
    val name: String,
    val minify: Boolean,
    val debug: Boolean,
    val output: String,
    val abiFilters: List<String> = listOf("arm64-v8a", "armeabi-v7a")
)

object BuildProfiles {
    val debug = BuildProfile("Debug", false, true, "apk")
    val release = BuildProfile("Release", true, false, "apk")
    val play = BuildProfile("Google Play", true, false, "aab")
}
