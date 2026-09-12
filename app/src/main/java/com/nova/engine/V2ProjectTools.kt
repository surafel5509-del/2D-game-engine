package com.nova.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Multi-layer tilemap editor data with brush, rectangle fill, eraser and collision metadata. */
class V2TileMap(val width: Int = 64, val height: Int = 36, val layers: Int = 4) {
    private val cells = Array(layers) { IntArray(width * height) }
    private val collisions = Array(layers) { BooleanArray(width * height) }
    fun get(layer: Int, x: Int, y: Int): Int = if (valid(layer, x, y)) cells[layer][y * width + x] else 0
    fun set(layer: Int, x: Int, y: Int, tile: Int) { if (valid(layer, x, y)) cells[layer][y * width + x] = tile }
    fun setCollision(layer: Int, x: Int, y: Int, solid: Boolean) { if (valid(layer, x, y)) collisions[layer][y * width + x] = solid }
    fun isSolid(layer: Int, x: Int, y: Int): Boolean = valid(layer, x, y) && collisions[layer][y * width + x]
    fun paint(layer: Int, x: Int, y: Int, tile: Int, radius: Int = 0) {
        for (yy in y - radius..y + radius) for (xx in x - radius..x + radius) set(layer, xx, yy, tile)
    }
    fun fillRect(layer: Int, x0: Int, y0: Int, x1: Int, y1: Int, tile: Int) {
        for (y in minOf(y0, y1)..maxOf(y0, y1)) for (x in minOf(x0, x1)..maxOf(x0, x1)) set(layer, x, y, tile)
    }
    fun erase(layer: Int, x: Int, y: Int, radius: Int = 0) = paint(layer, x, y, 0, radius)
    fun count(layer: Int): Int = if (layer in 0 until layers) cells[layer].count { it != 0 } else 0
    fun serialize(): String = JSONObject().put("format", 2).put("width", width).put("height", height).put("layers", layers).apply {
        val layerArray = JSONArray()
        for (layer in 0 until layers) layerArray.put(JSONArray(cells[layer].toList()))
        put("cells", layerArray)
        val collisionArray = JSONArray()
        for (layer in 0 until layers) collisionArray.put(JSONArray(collisions[layer].map { it }))
        put("collisions", collisionArray)
    }.toString()
    private fun valid(layer: Int, x: Int, y: Int) = layer in 0 until layers && x in 0 until width && y in 0 until height
}

/** Import/export manager. Project files are copied into private storage and can be shared through Android's document UI. */
class V2ProjectIO(private val context: Context) {
    private val projectRoot get() = File(context.filesDir, "nova-v2").also { it.mkdirs() }
    private val scenes get() = File(projectRoot, "scenes").also { it.mkdirs() }
    private val scripts get() = File(projectRoot, "scripts").also { it.mkdirs() }
    private val assets get() = File(projectRoot, "assets").also { it.mkdirs() }

    fun sceneFile(name: String): File = File(scenes, if (name.endsWith(".nova")) name else "$name.nova")
    fun scriptFile(name: String): File = File(scripts, if (name.endsWith(".nova")) name else "$name.nova")
    fun assetFile(name: String): File = File(assets, name.replace(Regex("[^A-Za-z0-9_.-]"), "_"))
    fun allFiles(): List<File> = listOfNotNull(projectRoot.walkTopDown().filter { it.isFile }.toList())

    fun writeScene(scene: V2Scene): File = sceneFile(scene.name).also { file ->
        val root = JSONObject().put("format", 2).put("name", scene.name)
        val nodes = JSONArray()
        scene.nodes.forEach { n -> nodes.put(JSONObject().put("id", n.id).put("name", n.name).put("type", n.type).put("x", n.x).put("y", n.y).put("rotation", n.rotation).put("sx", n.scaleX).put("sy", n.scaleY).put("w", n.width).put("h", n.height).put("z", n.zIndex).put("visible", n.visible).put("locked", n.locked).put("texture", n.texture).put("script", n.script).put("body", n.body).put("collider", n.collider).put("animation", n.animation).put("parent", n.parentId)) }
        file.writeText(root.put("nodes", nodes).toString(2)); scene.modified = false
    }

    fun importFile(uri: android.net.Uri, displayName: String): File? = runCatching {
        val target = assetFile(displayName)
        context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
        target
    }.getOrNull()

    fun exportBundle(output: File, scene: V2Scene, scriptsByName: Map<String, String>): File = output.also { target ->
        writeScene(scene)
        scriptsByName.forEach { (name, source) -> scriptFile(name).writeText(source) }
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            allFiles().forEach { file ->
                val relative = projectRoot.toPath().relativize(file.toPath()).toString()
                zip.putNextEntry(ZipEntry(relative)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }
}

/** Build description used by the in-editor Build command and CI. The Android app delegates actual APK compilation to Gradle/CI. */
data class V2BuildConfig(
    var applicationId: String = "com.nova.game",
    var versionName: String = "2.0.0",
    var versionCode: Int = 200,
    var minSdk: Int = 24,
    var targetSdk: Int = 35,
    var orientation: String = "landscape",
    var debug: Boolean = true,
    var aab: Boolean = false
)

class V2BuildValidator {
    data class Report(val ok: Boolean, val errors: List<String>, val warnings: List<String>)
    fun validate(config: V2BuildConfig, scene: V2Scene, assets: List<File>): Report {
        val errors = mutableListOf<String>(); val warnings = mutableListOf<String>()
        if (!Regex("^[a-zA-Z][a-zA-Z0-9_.]*$").matches(config.applicationId)) errors += "Invalid Android applicationId"
        if (config.versionCode <= 0) errors += "versionCode must be positive"
        if (config.targetSdk < config.minSdk) errors += "targetSdk must be >= minSdk"
        if (scene.nodes.isEmpty()) warnings += "Main scene contains no nodes"
        if (assets.any { it.length() == 0L }) warnings += "Empty asset file detected"
        return Report(errors.isEmpty(), errors, warnings)
    }
}
