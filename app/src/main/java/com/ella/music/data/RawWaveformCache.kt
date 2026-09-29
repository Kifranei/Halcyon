// Adapted from RawS Music, Copyright 2024–2026 RawSMusic Contributors, Apache-2.0.
package com.ella.music.data

import android.content.Context

import com.ella.music.data.model.Song
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Offline waveform source for progress bars.
 *
 * UI never scans on the main thread. It first draws a neutral placeholder, then this cache
 * returns a real offline/native PCM waveform when available. The scanned result is a
 * stable file-level cache, not the realtime PCM visualizer stream.
 */
object RawWaveformCache {
    data class LoadResult(val values: FloatArray, val isReal: Boolean)

    const val MAX_SAMPLE_COUNT = 21_600
    private const val VERSION = 7
    private const val MAGIC = 0x52535746 // RSWF
    private const val DIR = "waveform_v7"
    private const val DEFAULT_SAMPLE_COUNT = 100
    private const val MASTER_SAMPLE_COUNT = 3600
    private val mutex = Mutex()
    private val memoryCache = ConcurrentHashMap<String, FloatArray>()
    private val partialCache = ConcurrentHashMap<String, FloatArray>()
    private val mutableRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val revision: kotlinx.coroutines.flow.StateFlow<Long> = mutableRevision

    fun clearMemory() {
        memoryCache.clear()
        partialCache.clear()
    }

    fun placeholder(sampleCount: Int = DEFAULT_SAMPLE_COUNT, style: Int = 0): FloatArray {
        val count = sampleCount.coerceIn(32, MAX_SAMPLE_COUNT)
        val level = if (style == 1) 0.035f else 0.055f
        return FloatArray(count) { level }
    }

    suspend fun loadOrScan(
        context: Context,
        audioFile: Song?,
        sampleCount: Int = DEFAULT_SAMPLE_COUNT
    ): FloatArray = loadOrScanResult(context, audioFile, sampleCount).values

    suspend fun loadOrScanResult(
        context: Context,
        audioFile: Song?,
        sampleCount: Int = DEFAULT_SAMPLE_COUNT
    ): LoadResult {
        val song = audioFile ?: return LoadResult(placeholder(sampleCount), false)
        val path = song.path
        if (path.isBlank()) return LoadResult(placeholder(sampleCount), false)
        val boundedSamples = sampleCount.coerceIn(32, MAX_SAMPLE_COUNT)
        val identity = identityFor(song, MASTER_SAMPLE_COUNT)
        memoryCache[identity]?.let { return LoadResult(resampleWaveform(it, boundedSamples), true) }
        val file = cacheFile(context, identity)
        readOrMigrate(context, song, identity, boundedSamples)?.let {
            if (memoryCache.size >= 12) memoryCache.clear()
            memoryCache[identity] = it
            return LoadResult(resampleWaveform(it, boundedSamples), true)
        }

        return mutex.withLock {
            memoryCache[identity]?.let { return@withLock LoadResult(resampleWaveform(it, boundedSamples), true) }
            read(file, identity)?.let {
                if (memoryCache.size >= 12) memoryCache.clear()
                memoryCache[identity] = it
                return@withLock LoadResult(resampleWaveform(it, boundedSamples), true)
            }
            val scanned = try {
                scanPcmWaveform(context, song, MASTER_SAMPLE_COUNT) { partial ->
                    partialCache[identity] = normalize(partial)
                    mutableRevision.value++
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                partialCache.remove(identity)
                throw cancel
            }
            val result = if (scanned.size == MASTER_SAMPLE_COUNT) normalize(scanned) else placeholder(boundedSamples)
            val isReal = scanned.size == MASTER_SAMPLE_COUNT
            if (isReal) {
                if (memoryCache.size >= 12) memoryCache.clear()
                memoryCache[identity] = result
                write(file, identity, result)
            }
            partialCache.remove(identity)
            mutableRevision.value++
            LoadResult(resampleWaveform(result, boundedSamples), isReal)
        }
    }

    fun tryReadCached(context: Context, audioFile: Song?, sampleCount: Int = DEFAULT_SAMPLE_COUNT): FloatArray? {
        val song = audioFile ?: return null
        if (song.path.isBlank()) return null
        val boundedSamples = sampleCount.coerceIn(32, MAX_SAMPLE_COUNT)
        val identity = identityFor(song, MASTER_SAMPLE_COUNT)
        memoryCache[identity]?.let { return resampleWaveform(it, boundedSamples) }
        return readOrMigrate(context, song, identity, boundedSamples)?.also {
            if (memoryCache.size >= 12) memoryCache.clear()
            memoryCache[identity] = it
        }?.let { resampleWaveform(it, boundedSamples) }
    }

    /** Memory-only UI lookup. Unscanned sections remain zero, never synthesized from playback. */
    fun peekAvailable(song: Song?, count: Int): FloatArray? {
        song ?: return null
        val identity = identityFor(song, MASTER_SAMPLE_COUNT)
        return (memoryCache[identity] ?: partialCache[identity])?.let { resampleWaveform(it, count) }
    }

    private fun normalize(values: FloatArray): FloatArray {
        if (values.isEmpty()) return values
        var maxValue = 0f
        values.forEach { maxValue = max(maxValue, it) }
        if (maxValue <= 0.0001f) return values
        val gain = 1f / maxValue
        return FloatArray(values.size) { index ->
            (values[index].coerceAtLeast(0f) * gain).coerceIn(0f, 1f)
        }
    }

    private fun identityFor(song: Song, sampleCount: Int): String {
        return buildString {
            append(song.path)
            append('|')
            append(song.fileSize)
            append('|')
            append(song.dateModified)
            append('|')
            append(0)
            append('|')
            append(0)
            append('|')
            append(0)
            append('|')
            append(sampleCount)
        }
    }

    private fun readOrMigrate(context: Context, song: Song, identity: String, requested: Int): FloatArray? {
        read(cacheFile(context, identity), identity)?.let { return it }
        // Existing v6 waveforms are genuine full-track data. Keep them useful after unifying
        // the cache instead of forcing every already-analyzed track to decode again.
        val previousCounts = listOf(MASTER_SAMPLE_COUNT, requested,
            (song.duration / 100L).toInt().coerceIn(480, 3600),
            kotlin.math.ceil(song.duration / 1000.0).toInt().coerceIn(32, MAX_SAMPLE_COUNT), 100).distinct()
        for (count in previousCounts) {
            val previousIdentity = identityFor(song, count)
            val oldFile = File(File(context.cacheDir, "waveform_v6"), sha256(previousIdentity) + ".rswf")
            val values = read(oldFile, previousIdentity, expectedVersion = 6) ?: continue
            return resampleWaveform(values, MASTER_SAMPLE_COUNT)
        }
        return null
    }

    private fun cacheFile(context: Context, identity: String): File {
        val dir = File(context.cacheDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, sha256(identity) + ".rswf")
    }

    private fun read(file: File, expectedIdentity: String, expectedVersion: Int = VERSION): FloatArray? {
        if (!file.isFile || file.length() < 24L) return null
        return runCatching {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                val magic = input.readInt()
                val version = input.readInt()
                if (magic != MAGIC || version != expectedVersion) return null
                val identity = input.readUTF()
                if (identity != expectedIdentity) return null
                val count = input.readInt().coerceIn(0, MAX_SAMPLE_COUNT)
                if (count <= 0) return null
                FloatArray(count) { input.readFloat().coerceIn(0f, 1f) }
            }
        }.getOrNull()
    }

    private fun write(file: File, identity: String, values: FloatArray) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            DataOutputStream(BufferedOutputStream(tmp.outputStream())).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeUTF(identity)
                output.writeInt(values.size)
                values.forEach { output.writeFloat(it.coerceIn(0f, 1f)) }
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { b -> append("%02x".format(b)) }
        }
    }
}

/** Shared whole-track envelope; both timeline styles reuse one decode regardless of duration/width. */
internal fun resampleWaveform(source: FloatArray, count: Int): FloatArray {
    if (source.size == count) return source
    if (source.isEmpty() || count <= 0) return FloatArray(count.coerceAtLeast(0))
    return FloatArray(count) { index ->
        val start = index.toDouble() * source.size / count
        val end = (index + 1.0) * source.size / count
        if (end - start >= 1.0) {
            var peak = 0f
            for (i in start.toInt() until kotlin.math.ceil(end).toInt().coerceAtMost(source.size)) peak = maxOf(peak, source[i])
            peak
        } else {
            val x = index.toFloat() * (source.size - 1) / (count - 1).coerceAtLeast(1)
            val left = x.toInt()
            val fraction = x - left
            source[left] * (1f - fraction) + source[(left + 1).coerceAtMost(source.lastIndex)] * fraction
        }
    }
}
