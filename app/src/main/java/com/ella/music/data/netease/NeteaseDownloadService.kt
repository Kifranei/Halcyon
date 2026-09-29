package com.ella.music.data.netease

import android.app.*
import android.content.*
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.metadata.AudioCoverInfo
import com.ella.music.data.metadata.LyricoAudioTagReaderWriter
import com.ella.music.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Durable, serial download queue. Only completely tagged audio is published to MediaStore. */
class NeteaseDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = mutableListOf<JSONObject>()
    private var runner: Job? = null
    private val prefs by lazy { getSharedPreferences("netease_download_queue", MODE_PRIVATE) }
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(30, TimeUnit.MINUTES).build()

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.netease_download_channel), NotificationManager.IMPORTANCE_LOW))
        val saved = runCatching { JSONArray(prefs.getString("jobs", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until saved.length()) saved.optJSONObject(i)?.let(queue::add)
        // Recover a process death during publication without exposing a partial file or
        // downloading the same already-published item a second time.
        prefs.getString("publishingUri", null)?.let { raw ->
            runCatching {
                val uri = Uri.parse(raw)
                if (Build.VERSION.SDK_INT >= 29) contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_PENDING), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        if (cursor.getInt(0) == 1) contentResolver.delete(uri, null, null)
                        else queue.removeAll { it.optString("key") == prefs.getString("publishingKey", "") }
                    }
                }
            }
            prefs.edit().remove("publishingUri").remove("publishingKey").commit()
            persist()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION, notification(getString(R.string.player_download_started)).build())
        intent?.getStringExtra("job")?.let { raw ->
            val job = JSONObject(raw)
            if (queue.none { it.optString("key") == job.optString("key") }) { queue += job; persist() }
        }
        if (runner?.isActive != true) runner = scope.launch {
            while (queue.isNotEmpty()) {
                val job = queue.first()
                val song = job.getJSONObject("song").toPlaylistSong().toSong()
                try {
                    withContext(Dispatchers.IO) { download(job, song) }
                    NeteaseOfflineDownloads.revision.value++
                    notifications.notify(RESULT, notification(getString(R.string.netease_download_complete) + " · " + song.title).setOngoing(false).setAutoCancel(true).build())
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    android.util.Log.w("NeteaseDownload", "Download failed: ${error.javaClass.simpleName}")
                    notifications.notify(RESULT, notification(getString(R.string.netease_download_failed, song.title)).setOngoing(false).setAutoCancel(true).build())
                    Toast.makeText(this@NeteaseDownloadService, getString(R.string.netease_download_failed, song.title), Toast.LENGTH_LONG).show()
                }
                queue.removeAt(0)
                persist()
                prefs.edit().remove("publishingUri").remove("publishingKey").commit()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    private fun persist() { prefs.edit().putString("jobs", JSONArray(queue).toString()).commit() }
    override fun onBind(intent: Intent?) = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onTimeout(startId: Int, fgsType: Int) { scope.cancel(); stopSelf() }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(getString(R.string.netease_download_channel))
        .setContentText(text).setOngoing(true).setOnlyAlertOnce(true)

    private suspend fun download(job: JSONObject, originalSong: Song) {
        val client = CatClawNeteaseClient(this)
        val settings = SettingsManager.getInstance(this)
        val mvId = job.optString("mv")
        val isVideo = mvId.isNotBlank()
        val detail = if (!isVideo) client.songDetail(originalSong.onlineId) else null
        val song = detail?.let(::parseNeteaseSong) ?: originalSong
        val url = if (isVideo) client.musicVideoUrl(mvId, job.optInt("resolution", 720))
        else client.streamUrl(song.onlineId, NeteaseAccountStore.getInstance(this).account.value.cookie,
            job.optString("quality").ifBlank { settings.neteaseDownloadQuality.first() })
        val directory = File(cacheDir, "netease-offline-staging").apply { mkdirs() }
        val stem = if (isVideo) "mv-$mvId" else "song-${song.onlineId}"
        var stage: File? = null
        try {
            val (extension, mime) = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty download")
                val source = body.source()
                source.request(16)
                val prefix = source.peek().readByteArray(minOf(16L, source.buffer.size))
                val format = if (isVideo) "mp4" to "video/mp4" else detectNeteaseAudioFormat(prefix)
                val target = File(directory, "$stem.${format.first}").also { stage = it }
                val total = body.contentLength()
                var copied = 0L
                var lastUpdate = 0L
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count); copied += count
                        if (SystemClock.elapsedRealtime() - lastUpdate > 1000) {
                            notifications.notify(NOTIFICATION, notification(song.title)
                                .setProgress(100, if (total > 0) (copied * 100 / total).toInt() else 0, total <= 0).build())
                            lastUpdate = SystemClock.elapsedRealtime()
                        }
                    }
                }
                if (copied == 0L || (total > 0 && copied != total)) throw IOException("Incomplete download")
                format
            }
            val file = requireNotNull(stage)
            if (!isVideo) {
                notifications.notify(NOTIFICATION, notification(getString(R.string.netease_download_metadata)).build())
                val lyrics = client.lyrics(song.onlineId, NeteaseAccountStore.getInstance(this).account.value.cookie)
                val writer = LyricoAudioTagReaderWriter(this)
                writer.writeTags(file.path, offlineNeteaseTags(requireNotNull(detail), song, lyrics)).getOrThrow()
                if (song.coverUrl.isNotBlank()) {
                    val cover = http.newCall(Request.Builder().url(song.coverUrl).build()).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Cover unavailable")
                        val body = response.body ?: throw IOException("Empty cover")
                        if (body.source().request(20L * 1024 * 1024 + 1)) throw IOException("Cover too large")
                        AudioCoverInfo(body.bytes(), body.contentType()?.toString() ?: "image/jpeg")
                    }
                    writer.writeEmbeddedCover(file.path, cover).getOrThrow()
                }
            }
            val label = "${song.title} - ${song.artist} [$stem]".replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(60)
            publish(file, "$label.$extension", mime, song, isVideo, job.getString("key"))
        } finally { stage?.delete() }
    }

    private fun publish(file: File, name: String, mime: String, song: Song, video: Boolean, key: String) {
        val relative = if (video) "Movies/Halcyon/NcmOfflineMvs/" else NeteaseOfflineDownloads.RELATIVE_PATH
        if (Build.VERSION.SDK_INT < 29) {
            val target = File(Environment.getExternalStorageDirectory(), relative + name)
            target.parentFile?.mkdirs()
            // Never overwrite an earlier download without an explicit replacement request.
            val unique = if (target.exists()) File(target.parentFile, "${System.currentTimeMillis()}-$name") else target
            file.copyTo(unique)
            MediaScannerConnection.scanFile(this, arrayOf(unique.path), arrayOf(mime), null)
            return
        }
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.MediaColumns.TITLE, song.title)
            if (!video) {
                put(MediaStore.Audio.Media.ARTIST, song.artist); put(MediaStore.Audio.Media.ALBUM, song.album)
                put(MediaStore.Audio.Media.DURATION, song.duration); put(MediaStore.Audio.Media.IS_MUSIC, 1)
            }
        }
        val uri = contentResolver.insert(collection, values) ?: throw IOException("Cannot create download")
        try {
            prefs.edit().putString("publishingUri", uri.toString()).putString("publishingKey", key).commit()
            contentResolver.openOutputStream(uri, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Cannot write download")
            contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (error: Exception) {
            contentResolver.delete(uri, null, null)
            prefs.edit().remove("publishingUri").remove("publishingKey").commit()
            throw error
        }
    }

    companion object {
        private const val CHANNEL = "netease_downloads"
        private const val NOTIFICATION = 0x4E434D
        private const val RESULT = 0x4E434E
        fun enqueue(context: Context, song: Song, mvId: String = "") {
            // Snapshot the independent preference before enqueueing, not after earlier downloads finish.
            val app = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                try {
                    val settings = SettingsManager.getInstance(app)
                    val quality = settings.neteaseDownloadQuality.first()
                    val resolution = settings.neteaseMvDownloadResolution.first()
                    val job = JSONObject().put("song", song.toPlaylistSong().toJson())
                        .put("mv", mvId).put("quality", quality).put("resolution", resolution)
                        .put("key", if (mvId.isBlank()) "song:${song.onlineId}:$quality" else "mv:$mvId:$resolution")
                    ContextCompat.startForegroundService(app, Intent(app, NeteaseDownloadService::class.java).putExtra("job", job.toString()))
                    Toast.makeText(app, R.string.player_download_started, Toast.LENGTH_SHORT).show()
                } catch (_: Exception) { Toast.makeText(app, app.getString(R.string.netease_download_failed, song.title), Toast.LENGTH_LONG).show() }
            }
        }
    }
}

internal fun detectNeteaseAudioFormat(prefix: ByteArray): Pair<String, String> = when {
    prefix.take(4).toByteArray().toString(Charsets.US_ASCII) == "fLaC" -> "flac" to "audio/flac"
    prefix.size >= 8 && prefix.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp" -> "m4a" to "audio/mp4"
    prefix.take(3).toByteArray().toString(Charsets.US_ASCII) == "ID3" ||
        (prefix.size >= 2 && prefix[0].toInt() and 255 == 255 && prefix[1].toInt() and 224 == 224) -> "mp3" to "audio/mpeg"
    else -> throw IOException("Unsupported audio format")
}

internal object NeteaseOfflineDownloads {
    const val RELATIVE_PATH = "Music/Halcyon/NcmOfflineSongs/"
    val path: String get() = File(Environment.getExternalStorageDirectory(), RELATIVE_PATH).path.trimEnd('/')
    val revision = MutableStateFlow(0L)
    fun songs(context: Context): List<Song> {
        val columns = arrayOf("_id", "title", "artist", "album", "album_id", "duration", "_data", "_display_name", "_size", "mime_type")
        return buildList {
            context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns,
                "_data LIKE ?", arrayOf("$path/%"), "date_added DESC")?.use { c ->
                while (c.moveToNext()) add(Song(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(),
                    c.getString(3).orEmpty(), c.getLong(4), c.getLong(5), c.getString(6).orEmpty(),
                    c.getString(7).orEmpty(), fileSize = c.getLong(8), mimeType = c.getString(9).orEmpty()))
            }
        }
    }
}
