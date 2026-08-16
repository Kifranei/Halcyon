package com.ella.music.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import com.ella.music.MainActivity
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.sanitizeExportFileName
import com.ella.music.data.repository.MusicRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveStream
import io.ktor.server.request.contentType
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Explicitly enabled, LAN-only browser player and file transfer service.
 *
 * This Beta intentionally has no Internet discovery or authentication. The settings screen makes
 * that limitation visible before the user enables the service.
 */
class WebMusicService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: EmbeddedServer<*, *>? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        scope.launch { startServer() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1_500)
        server = null
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun startServer() {
        runCatching {
            val repository = MusicRepository.getInstance(this@WebMusicService)
            if (repository.songs.value.isEmpty()) repository.loadCachedLibrary()
            server = embeddedServer(CIO, host = "0.0.0.0", port = PORT) {
                routing {
                    get("/") {
                        val page = assets.open(WEB_ASSET).use { it.readBytes() }
                        call.respondBytes(page, ContentType.Text.Html)
                    }
                    get("/api/songs") {
                        val query = call.request.queryParameters["q"].orEmpty().trim()
                        val songs = repository.songs.value
                            .asSequence()
                            .filter { it.onlineSource.isBlank() }
                            .filter {
                                query.isBlank() ||
                                    it.title.contains(query, ignoreCase = true) ||
                                    it.artist.contains(query, ignoreCase = true) ||
                                    it.album.contains(query, ignoreCase = true)
                            }
                            .take(MAX_RESULTS)
                            .toList()
                        call.respondText(
                            buildJsonArray {
                                songs.forEach { song ->
                                    add(buildJsonObject {
                                        put("id", song.id)
                                        put("title", song.title)
                                        put("artist", song.artist)
                                        put("album", song.album)
                                        put("duration", song.duration)
                                        put("cover", "/api/cover/${song.id}")
                                        put("stream", "/api/stream/${song.id}")
                                    })
                                }
                            }.toString(),
                            ContentType.Application.Json
                        )
                    }
                    get("/api/cover/{id}") {
                        val song = repository.findSong(call.parameters["id"])
                            ?: return@get call.respondText("Not found", status = HttpStatusCode.NotFound)
                        val bytes = withContext(Dispatchers.IO) {
                            runCatching { repository.getCoverArt(song) }
                                .getOrNull()
                                ?.takeIf { it.isNotEmpty() }
                                ?: readMediaStoreAlbumArt(song.albumId)?.takeIf { it.isNotEmpty() }
                        }
                            ?: return@get call.respondText("Not found", status = HttpStatusCode.NotFound)
                        call.respondBytes(bytes, detectImageContentType(bytes))
                    }
                    get("/api/lyrics/{id}") {
                        val song = repository.findSong(call.parameters["id"])
                            ?: return@get call.respondText("Not found", status = HttpStatusCode.NotFound)
                        val lyrics = repository.getLyrics(song)
                        call.respondText(
                            buildJsonArray {
                                lyrics.forEach { line ->
                                    add(buildJsonObject {
                                        put("timeMs", line.timeMs)
                                        put("endMs", line.endMs ?: -1L)
                                        put("text", line.text)
                                        line.translation?.takeIf { it.isNotBlank() }?.let { put("translation", it) }
                                        line.pronunciation?.takeIf { it.isNotBlank() }?.let { put("pronunciation", it) }
                                        line.backgroundText?.takeIf { it.isNotBlank() }?.let { put("backgroundText", it) }
                                        line.backgroundTranslation?.takeIf { it.isNotBlank() }
                                            ?.let { put("backgroundTranslation", it) }
                                        put("words", buildJsonArray {
                                            line.words.forEach { word ->
                                                add(buildJsonObject {
                                                    put("text", word.text)
                                                    put("startMs", word.startMs)
                                                    put("endMs", word.endMs)
                                                })
                                            }
                                        })
                                    })
                                }
                            }.toString(),
                            ContentType.Application.Json
                        )
                    }
                    get("/api/stream/{id}") {
                        val song = repository.findSong(call.parameters["id"])
                            ?: return@get call.respondText("Not found", status = HttpStatusCode.NotFound)
                        val contentType = ContentType.parse(
                            song.mimeType.takeIf { it.contains('/') } ?: "audio/mpeg"
                        )
                        val file = File(song.path)
                        if (file.isFile) {
                            call.respondFile(file)
                        } else {
                            val uri = Uri.parse(song.path)
                            call.respondOutputStream(contentType) {
                                contentResolver.openInputStream(uri)?.use { input -> input.copyTo(this) }
                            }
                        }
                    }
                    put("/api/upload") {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            return@put call.respondText(
                                "Upload requires Android 10 or later",
                                status = HttpStatusCode.NotImplemented
                            )
                        }
                        val fileName = sanitizeFileName(
                            call.request.queryParameters["name"].orEmpty()
                        )
                        if (fileName.isBlank()) {
                            return@put call.respondText(
                                "Missing file name",
                                status = HttpStatusCode.BadRequest
                            )
                        }
                        val uploadStream = call.receiveStream()
                        val result = saveUpload(fileName, call.request.contentType().toString()) {
                            uploadStream.use { input -> input.copyTo(it) }
                        }
                        if (result != null) {
                            call.respondText("""{"ok":true,"name":${jsonString(fileName)}}""", ContentType.Application.Json)
                        } else {
                            call.respondText("Upload failed", status = HttpStatusCode.InternalServerError)
                        }
                    }
                }
            }.start(wait = true)
        }.onFailure {
            Log.e(TAG, "Web music server failed", it)
            stopSelf()
        }
    }

    private fun MusicRepository.findSong(rawId: String?): Song? {
        val id = rawId?.toLongOrNull() ?: return null
        return songs.value.firstOrNull { it.id == id && it.onlineSource.isBlank() }
    }

    private fun readMediaStoreAlbumArt(albumId: Long): ByteArray? {
        if (albumId <= 0L) return null
        val uri = Uri.parse("content://media/external/audio/albumart/$albumId")
        return runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
    }

    private fun detectImageContentType(bytes: ByteArray): ContentType = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> ContentType.Image.JPEG
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        ) -> ContentType.Image.PNG
        bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a") -> ContentType.Image.GIF
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> ContentType.parse("image/webp")
        else -> ContentType.Image.Any
    }

    private fun saveUpload(
        fileName: String,
        mimeType: String,
        write: (java.io.OutputStream) -> Unit
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType.takeUnless { it == "null" } ?: "audio/*")
            put(
                MediaStore.Audio.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MUSIC}/Halcyon Web Uploads"
            )
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        return runCatching {
            contentResolver.openOutputStream(uri, "w")?.use(write)
                ?: error("Cannot open upload destination")
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            uri
        }.onFailure {
            contentResolver.delete(uri, null, null)
            Log.e(TAG, "Failed to save web upload", it)
        }.getOrNull()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.web_music_beta_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.web_music_beta_title))
            .setContentText(getString(R.string.web_music_beta_notification, PORT))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "WebMusicService"
        private const val CHANNEL_ID = "web_music_beta"
        private const val NOTIFICATION_ID = 7701
        private const val WEB_ASSET = "web_player/index.html"
        private const val MAX_RESULTS = 1_000
        const val PORT = 8199

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WebMusicService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WebMusicService::class.java))
        }

        fun accessAddresses(): List<String> = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses).asSequence() }
                .filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress }
                .map { "http://${it.hostAddress}:$PORT/" }
                .distinct()
                .toList()
        }.getOrDefault(emptyList())

        private fun sanitizeFileName(value: String): String =
            value.substringAfterLast('/').substringAfterLast('\\')
                .sanitizeExportFileName(fallback = "upload", maxLength = 180)

        private fun jsonString(value: String): String =
            buildJsonObject { put("value", value) }.toString()
                .removePrefix("""{"value":""")
                .removeSuffix("}")
    }
}
