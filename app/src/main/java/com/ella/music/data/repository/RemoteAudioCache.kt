package com.ella.music.data.repository

import android.content.Context
import android.util.Log
import com.ella.music.data.blockCredentialedHttpRequests
import com.ella.music.data.isHttpAudioSource
import com.ella.music.data.InputTooLargeException
import com.ella.music.data.copyToBoundedOrThrow
import com.ella.music.data.model.Song
import com.ella.music.data.remote.NavidromeService
import com.ella.music.data.remote.RemoteMusicProvider
import com.ella.music.data.remote.isSubsonicLike
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Downloads remote stream URLs (Navidrome / OpenSubsonic / Emby / plain HTTP) into app cache
 * so playback can prefer a local file when present.
 */
object RemoteAudioCache {
    private const val TAG = "RemoteAudioCache"
    private const val DIR_NAME = "remote_stream_audio"

    data class Progress(
        val active: Boolean = false,
        val completed: Int = 0,
        val total: Int = 0,
        val failed: Int = 0,
        val cancelled: Boolean = false
    )

    @Volatile
    private var cacheDir: File? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .blockCredentialedHttpRequests()
            .build()
    }

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private var runningJob: Job? = null
    private val quotaLock = Any()
    private var reservedCacheBytes = 0L

    // Also look in the legacy WebDAV full-audio cache dir.
    @Volatile
    private var webDavAudioDir: File? = null

    fun init(context: Context) {
        val app = context.applicationContext
        cacheDir = File(app.cacheDir, DIR_NAME).also { dir ->
            dir.mkdirs()
            val expiration = System.currentTimeMillis() - CACHE_MAX_AGE_MS
            dir.listFiles().orEmpty().filter { it.isFile && it.lastModified() < expiration }.forEach(File::delete)
        }
        webDavAudioDir = File(app.cacheDir, "webdav_audio")
    }

    fun cacheRoot(): File? = cacheDir

    fun isCacheableRemoteSong(song: Song): Boolean {
        if (song.path.isHttpAudioSource()) return true
        // A synced Navidrome/OpenSubsonic row can lose a usable stream URL and still be
        // downloadable once the current server config reissues one.
        return song.onlineId.isNotBlank() &&
            RemoteMusicProvider.fromId(song.onlineSource).isSubsonicLike
    }

    fun cacheFileFor(song: Song): File? {
        val dir = cacheDir ?: return null
        val key = when {
            song.onlineId.isNotBlank() -> "online:${song.onlineSource}:${song.onlineId}"
            else -> "path:${song.path}"
        }
        val ext = song.webDavCacheExtension().ifBlank { "audio" }
        return File(dir, "${key.sha256()}.$ext")
    }

    fun localFileIfCached(song: Song): File? {
        cacheFileFor(song)?.takeIf { it.exists() && it.length() > 0L }?.let { return it }
        // Prefer a complete WebDAV full-file cache when present.
        webDavAudioDir?.let { dir ->
            val webDav = song.webDavFullCacheFile(dir)
            if (webDav.exists() && webDav.length() > 0L &&
                (song.fileSize <= 0L || webDav.length() == song.fileSize)
            ) {
                return webDav
            }
        }
        return null
    }

    fun clearAll() {
        runCatching { cacheDir?.deleteRecursively() }
        cacheDir?.mkdirs()
    }

    fun cancel() {
        runningJob?.cancel()
        runningJob = null
        _progress.value = _progress.value.copy(active = false, cancelled = true)
    }

    /**
     * Start a cancellable batch download. Observe [progress] for UI updates.
     */
    fun cacheSongs(songs: List<Song>, scope: kotlinx.coroutines.CoroutineScope): Job {
        val targets = songs.filter(::isCacheableRemoteSong)
            .distinctBy { cacheFileFor(it)?.absolutePath ?: it.path }
        runningJob?.cancel()
        if (targets.isEmpty()) {
            _progress.value = Progress(active = false, completed = 0, total = 0, failed = 0)
            return scope.launch { }
        }
        _progress.value = Progress(active = true, completed = 0, total = targets.size, failed = 0)
        val job = scope.launch(Dispatchers.IO) {
            val completed = AtomicInteger(0)
            val failed = AtomicInteger(0)
            try {
                coroutineScope {
                    val workers = minOf(2, targets.size)
                    val next = AtomicInteger(0)
                    repeat(workers) {
                        launch {
                            while (true) {
                                ensureActive()
                                val index = next.getAndIncrement()
                                if (index >= targets.size) return@launch
                                val song = targets[index]
                                val ok = runCatching { downloadOne(song) }.getOrDefault(false)
                                if (ok) completed.incrementAndGet() else failed.incrementAndGet()
                                _progress.value = Progress(
                                    active = true,
                                    completed = completed.get(),
                                    total = targets.size,
                                    failed = failed.get()
                                )
                            }
                        }
                    }
                }
                _progress.value = Progress(
                    active = false,
                    completed = completed.get(),
                    total = targets.size,
                    failed = failed.get()
                )
            } catch (cancelled: CancellationException) {
                _progress.value = Progress(
                    active = false,
                    completed = completed.get(),
                    total = targets.size,
                    failed = failed.get(),
                    cancelled = true
                )
                throw cancelled
            } finally {
                runningJob = null
            }
        }
        runningJob = job
        return job
    }

    private fun downloadOne(song: Song): Boolean {
        val target = cacheFileFor(song) ?: return false
        if (target.exists() && target.length() > 0L &&
            (song.fileSize <= 0L || target.length() == song.fileSize)
        ) {
            return true
        }
        val tmp = File(target.parentFile, "${target.name}.partial")
        tmp.delete()
        val reservedBytes = reserveCacheBytes(target)
        if (reservedBytes <= 0L) return false
        return try {
            val request = Request.Builder()
                .url(song.path)
                .header("User-Agent", NavidromeService.USER_AGENT)
                .get()
                .build()
            val downloaded = httpClient.newCall(request).execute().use { response ->
                if (response.code !in 200..299) {
                    Log.w(TAG, "cache download HTTP ${response.code} for ${song.path.take(120)}")
                    return@use false
                }
                val body = response.body ?: return@use false
                if (body.contentLength() > reservedBytes) throw InputTooLargeException(reservedBytes)
                tmp.parentFile?.mkdirs()
                tmp.outputStream().use { output ->
                    body.byteStream().use { input -> input.copyToBoundedOrThrow(output, reservedBytes) }
                }
                true
            }
            if (!downloaded || !tmp.exists() || tmp.length() <= 0L) {
                tmp.delete()
                return false
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            true
        } catch (error: Throwable) {
            Log.w(TAG, "cache download failed for ${song.path.take(120)}", error)
            tmp.delete()
            false
        } finally {
            synchronized(quotaLock) { reservedCacheBytes = (reservedCacheBytes - reservedBytes).coerceAtLeast(0L) }
        }
    }

    private fun reserveCacheBytes(target: File): Long = synchronized(quotaLock) {
        val dir = target.parentFile ?: return@synchronized 0L
        val occupiedBytes = dir.listFiles().orEmpty()
            .filter { it.isFile && it != target && !it.name.endsWith(".partial") }
            .sumOf(File::length)
        val available = MAX_CACHE_BYTES - occupiedBytes - reservedCacheBytes
        val reservation = minOf(MAX_CACHE_FILE_BYTES, available)
        if (reservation > 0L) reservedCacheBytes += reservation
        reservation
    }

    private const val MAX_CACHE_FILE_BYTES = 1L * 1024L * 1024L * 1024L
    private const val MAX_CACHE_BYTES = 4L * 1024L * 1024L * 1024L
    private const val CACHE_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1_000L
}
