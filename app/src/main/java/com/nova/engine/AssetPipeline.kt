package com.nova.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/** Runtime asset cache for images and small text manifests. All paths are relative to assets/. */
class AssetPipeline(private val context: Context) {
    private val bitmaps = ConcurrentHashMap<String, Bitmap>()
    private val text = ConcurrentHashMap<String, String>()

    fun loadBitmap(path: String): Bitmap? = bitmaps[path] ?: runCatching {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()?.also { bitmaps[path] = it }

    fun loadText(path: String): String? = text[path] ?: runCatching {
        BufferedReader(InputStreamReader(context.assets.open(path))).use { it.readText() }
    }.getOrNull()?.also { text[path] = it }

    fun evict(path: String) { bitmaps.remove(path)?.recycle(); text.remove(path) }
    fun clear() { bitmaps.values.forEach { if (!it.isRecycled) it.recycle() }; bitmaps.clear(); text.clear() }
    fun bitmapCount(): Int = bitmaps.size
}

data class AtlasRegion(
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val pivotX: Float = 0.5f,
    val pivotY: Float = 0.5f
)

/** Dependency-free atlas format: name|x|y|w|h|pivotX|pivotY. */
class SpriteAtlas(val image: String, val width: Int, val height: Int) {
    private val regions = LinkedHashMap<String, AtlasRegion>()
    fun add(region: AtlasRegion): SpriteAtlas { regions[region.name] = region; return this }
    fun region(name: String): AtlasRegion? = regions[name]
    fun all(): List<AtlasRegion> = regions.values.toList()

    companion object {
        fun parse(manifest: String): SpriteAtlas {
            val lines = manifest.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
            require(lines.isNotEmpty() && lines.first().startsWith("NOVA_ATLAS")) { "Invalid NOVA_ATLAS manifest" }
            var image = ""; var width = 0; var height = 0
            val atlas = SpriteAtlas("", 0, 0)
            val pending = mutableListOf<AtlasRegion>()
            for (line in lines.drop(1)) {
                val p = line.split('|')
                when (p.firstOrNull()) {
                    "image" -> image = p[1]
                    "size" -> { width = p[1].toInt(); height = p[2].toInt() }
                    "region" -> pending += AtlasRegion(p[1], p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt(), p.getOrNull(6)?.toFloatOrNull() ?: .5f, p.getOrNull(7)?.toFloatOrNull() ?: .5f)
                }
            }
            val result = SpriteAtlas(image, width, height)
            pending.forEach(result::add)
            return result
        }
    }
}
