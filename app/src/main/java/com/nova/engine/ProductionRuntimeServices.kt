package com.nova.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Safe, dependency-free production runtime services used by the editor and game runtime. */
class AssetImporter(private val context: Context) {
    data class ImportResult(val id: String, val path: String, val width: Int, val height: Int, val bytes: Long)

    fun importImage(uri: Uri, destinationDir: File, id: String): ImportResult {
        require(id.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid asset id" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open asset" }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported or corrupt image" }
        destinationDir.mkdirs()
        val out = File(destinationDir, "$id.png")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to read asset" }
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return ImportResult(id, out.absolutePath, bounds.outWidth, bounds.outHeight, out.length())
    }
}

class SpriteAnimationPlayer {
    private var clip: AnimationClip? = null
    private var index = 0
    private var elapsedMs = 0f
    var playing = false
        private set

    fun play(next: AnimationClip) {
        clip = next
        index = 0
        elapsedMs = 0f
        playing = next.frames.isNotEmpty()
    }

    fun stop() { playing = false; elapsedMs = 0f; index = 0 }

    fun update(deltaMs: Float): String? {
        val current = clip ?: return null
        if (!playing || current.frames.isEmpty()) return current.frames.getOrNull(index)?.textureId
        elapsedMs += deltaMs.coerceAtLeast(0f) * current.speed.coerceAtLeast(0f)
        while (elapsedMs >= current.frames[index].durationMs.coerceAtLeast(1)) {
            elapsedMs -= current.frames[index].durationMs.coerceAtLeast(1)
            if (index + 1 < current.frames.size) index++
            else if (current.loop) index = 0
            else { playing = false; break }
        }
        return current.frames[index].textureId
    }
}

class AudioService(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build())
        .build()
    private val sounds = ConcurrentHashMap<String, Int>()

    fun load(id: String, uri: Uri): Int {
        context.contentResolver.openAssetFileDescriptor(uri, "r").use { afd ->
            requireNotNull(afd) { "Unable to open audio asset" }
            val soundId = pool.load(afd.fileDescriptor, afd.startOffset, afd.length, 1)
            sounds[id] = soundId
            return soundId
        }
    }

    fun play(id: String, volume: Float = 1f, rate: Float = 1f): Int {
        val soundId = sounds[id] ?: return 0
        return pool.play(soundId, volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f), 1, 0, rate.coerceIn(.5f, 2f))
    }

    fun unload(id: String) { sounds.remove(id)?.let(pool::unload) }
    fun release() { sounds.clear(); pool.release() }
}

class RuntimeDiagnostics {
    data class Snapshot(val fps: Float, val frameMs: Float, val fixedSteps: Int, val entities: Int, val memoryMb: Float)
    fun snapshot(frameMs: Float, fixedSteps: Int, entities: Int): Snapshot {
        val runtime = Runtime.getRuntime()
        val used = (runtime.totalMemory() - runtime.freeMemory()).toFloat() / (1024f * 1024f)
        return Snapshot(if (frameMs > 0f) 1000f / frameMs else 0f, frameMs, fixedSteps, entities, used)
    }
}
