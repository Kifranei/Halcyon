package com.ella.music.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import com.ella.music.ui.player.inferMusicVideoContainerMimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class VideoSource(val uri: String, val title: String, val mimeType: String? = null,
    val hls: Boolean = false, val durationMs: Long = 0, val exportable: Boolean = true, val needsResolution: Boolean = false)
internal object VideoSourceResolver {
    val http=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(40,TimeUnit.SECONDS).build()
    suspend fun resolve(context: Context, source: String, declaredType: String? = null): List<VideoSource> = withContext(Dispatchers.IO) {
        require(isVideoSource(source)) { context.getString(com.ella.music.R.string.video_invalid_source) }
        val uri=Uri.parse(source);val name=uri.lastPathSegment?.substringBefore('?').orEmpty().ifBlank { "Video" }
        if (isDirectVideoSource(source, declaredType)) {
            return@withContext listOf(VideoSource(source, name, inferMusicVideoContainerMimeType(source, declaredType)))
        }
        val hinted=name.substringAfterLast('.',"").lowercase() in listOf("m3u","m3u8") || isPlaylistContentType(declaredType)
        var type=declaredType;var finalSource=source
        // Read only the prefix of ordinary videos; extensionless playlist URLs are also detected.
        val text=if(uri.scheme in listOf("http","https")) {
            http.newCall(Request.Builder().url(source).build()).execute().use { response ->
                if(!response.isSuccessful) throw IOException("HTTP ${response.code}")
                type=response.header("Content-Type") ?: type;finalSource=response.request.url.toString()
                val body=response.body ?: throw IOException("Empty response")
                val prefix=body.source().peek().inputStream().readVideoBytes(128).toString(Charsets.UTF_8)
                if(hinted || isPlaylistContentType(type) || prefix.trimStart('\uFEFF',' ','\n','\r').startsWith("#EXTM3U")) {
                    val bytes=body.byteStream().use { input -> input.readVideoBytes(2*1024*1024+1) }
                    require(bytes.size<=2*1024*1024) { "Playlist is too large" };bytes.toString(Charsets.UTF_8)
                } else null
            }
        } else {
            type=runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: type
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffered=input.buffered();buffered.mark(256)
                val prefix=buffered.readVideoBytes(128).toString(Charsets.UTF_8);buffered.reset()
                if(hinted || isPlaylistContentType(type) || prefix.trimStart('\uFEFF',' ','\n','\r').startsWith("#EXTM3U")) {
                    val bytes=buffered.readVideoBytes(2*1024*1024+1);require(bytes.size<=2*1024*1024) { "Playlist is too large" };bytes.toString(Charsets.UTF_8)
                } else null
            } ?: throw IOException("Cannot open source")
        }
        if(text==null) return@withContext listOf(VideoSource(finalSource,name,inferMusicVideoContainerMimeType(finalSource,type)))
        val parsed=parseVideoPlaylist(text,finalSource)
        if(!parsed.hls) {
            require(parsed.entries.isNotEmpty()) { context.getString(com.ella.music.R.string.video_empty_playlist) }
            parsed.entries.map { VideoSource(it.source,it.title,needsResolution=true) }
        } else {
            // A master playlist delegates to the first variant only for finite/live detection.
            var duration=parsed.durationMs;var ended=parsed.ended
            if(parsed.master && parsed.entries.isNotEmpty()) {
                val variantUri=Uri.parse(parsed.entries.first().source)
                val data=if(variantUri.scheme in listOf("http","https")) {
                    http.newCall(Request.Builder().url(variantUri.toString()).build()).execute().use { response ->
                        if(!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        response.body?.byteStream()?.use { it.readVideoBytes(2*1024*1024+1) }
                    }
                } else context.contentResolver.openInputStream(variantUri)?.use { it.readVideoBytes(2*1024*1024+1) }
                require(data!=null && data.size<=2*1024*1024) { "Cannot read variant playlist" }
                val variant=parseVideoPlaylist(data.toString(Charsets.UTF_8),variantUri.toString())
                duration=variant.durationMs;ended=variant.ended
            }
            listOf(VideoSource(finalSource,name,MimeTypes.APPLICATION_M3U8,true,duration,ended))
        }
    }
}

internal fun java.io.InputStream.readVideoBytes(limit: Int): ByteArray {
    val result=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(minOf(limit,8192));var remaining=limit
    while(remaining>0) {
        val count=read(buffer,0,minOf(buffer.size,remaining));if(count<0) break
        result.write(buffer,0,count);remaining-=count
    }
    return result.toByteArray()
}
