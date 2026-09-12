package com.nova.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class AssetPipeline(private val context: Context) {
    private val bitmaps = ConcurrentHashMap<String, Bitmap>()
    private val text = ConcurrentHashMap<String, String>()
    private val hashes = ConcurrentHashMap<String, String>()
    fun loadBitmap(path: String): Bitmap? = bitmaps[path] ?: runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()?.also { bitmaps[path] = it }
    fun loadText(path: String): String? = text[path] ?: runCatching { BufferedReader(InputStreamReader(context.assets.open(path))).use { it.readText() } }.getOrNull()?.also { text[path] = it }
    fun contentHash(path: String): String? = hashes[path] ?: loadText(path)?.let { sha256(it.toByteArray()) }?.also { hashes[path] = it }
    fun evict(path: String) { bitmaps.remove(path)?.recycle(); text.remove(path); hashes.remove(path) }
    fun clear() { bitmaps.values.forEach { if (!it.isRecycled) it.recycle() }; bitmaps.clear(); text.clear(); hashes.clear() }
    fun bitmapCount(): Int = bitmaps.size
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

data class AtlasRegion(val name: String, val x: Int, val y: Int, val width: Int, val height: Int, val pivotX: Float = .5f, val pivotY: Float = .5f)
class SpriteAtlas(val image: String, val width: Int, val height: Int) {
    private val regions = LinkedHashMap<String, AtlasRegion>()
    fun add(region: AtlasRegion): SpriteAtlas { regions[region.name] = region; return this }
    fun region(name: String): AtlasRegion? = regions[name]
    fun all(): List<AtlasRegion> = regions.values.toList()
    fun uv(name: String): FloatArray? = region(name)?.let { floatArrayOf(it.x.toFloat()/width, it.y.toFloat()/height, (it.x+it.width).toFloat()/width, (it.y+it.height).toFloat()/height) }
    companion object {
        fun parse(manifest: String): SpriteAtlas {
            val lines = manifest.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
            require(lines.firstOrNull() == "NOVA_ATLAS") { "Invalid NOVA_ATLAS manifest" }
            var image = ""; var width = 1; var height = 1; val pending = mutableListOf<AtlasRegion>()
            for (line in lines.drop(1)) { val p = line.split('|'); when (p.firstOrNull()) {
                "image" -> if (p.size > 1) image = p[1]
                "size" -> if (p.size > 2) { width = p[1].toInt(); height = p[2].toInt() }
                "region" -> if (p.size >= 6) pending += AtlasRegion(p[1], p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt(), p.getOrNull(6)?.toFloatOrNull() ?: .5f, p.getOrNull(7)?.toFloatOrNull() ?: .5f)
            } }
            return SpriteAtlas(image, width, height).also { pending.forEach(it::add) }
        }
    }
}

object AtlasPacker {
    fun pack(items: List<Pair<String, Pair<Int, Int>>>, maxWidth: Int = 2048, padding: Int = 2): List<AtlasRegion> {
        var x = padding; var y = padding; var rowHeight = 0; val result = mutableListOf<AtlasRegion>()
        for ((name, size) in items) { val w = size.first; val h = size.second
            if (x + w + padding > maxWidth && x > padding) { x = padding; y += rowHeight + padding; rowHeight = 0 }
            result += AtlasRegion(name, x, y, w, h); x += w + padding; rowHeight = maxOf(rowHeight, h)
        }
        return result
    }
}
