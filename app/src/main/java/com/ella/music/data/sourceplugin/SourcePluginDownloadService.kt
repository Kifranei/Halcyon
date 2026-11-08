package com.ella.music.data.sourceplugin

import android.content.Context
import android.widget.Toast
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.musicfree.MusicFreeStreamDownload
import com.ella.music.data.musicfree.MusicFreeStreamHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Downloads for account-source plugin songs: resolve through the plugin (fresh URL + headers),
 * then hand the marked URL to the shared plugin-download pipeline. MV downloads stream the
 * plugin's getMvUrl result into the video collection.
 */
object SourcePluginDownloadService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enqueue(context: Context, song: Song) {
        val app = context.applicationContext
        scope.launch {
            try {
                val resolved = SourcePluginPlaybackResolver(app).resolve(song)
                MusicFreeStreamDownload.enqueue(app, resolved, resolved.fileName.ifBlank { "${song.title}.mp3" })
            } catch (error: Exception) {
                val reason = error.localizedMessage ?: app.getString(R.string.source_plugin_resolve_failed)
                launch(Dispatchers.Main) {
                    Toast.makeText(app, app.getString(R.string.musicfree_download_failed, reason), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun enqueueMv(context: Context, song: Song, mvId: String) {
        val app = context.applicationContext
        scope.launch {
            val message = try {
                val pluginId = sourcePluginKeyOf(song.onlineSource)
                val config = SourcePluginRegistry.config(app, pluginId)
                    ?: throw SourcePluginException(app.getString(R.string.source_plugin_not_installed))
                val result = SourcePluginRegistry.client(app).getMvUrl(config, song, mvId, 720)
                val url = result.optString("url").takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?: throw SourcePluginException(app.getString(R.string.source_plugin_no_url))
                val headers = JSONObject().apply {
                    result.optJSONObject("headers")?.let { put("headers", it) }
                }
                val marked = MusicFreeStreamHeaders.getInstance(app).register(url, headers)
                val name = "${song.artist} - ${song.title}.mp4".replace(Regex("[\\\\/:*?\"<>|]"), "_")
                com.ella.music.data.MediaExportStorage.write(
                    app, name, "video/mp4", "${android.os.Environment.DIRECTORY_MOVIES}/Halcyon/"
                ) { out ->
                    val resolved = MusicFreeStreamHeaders.getInstance(app).resolveStream(marked, MV_HTTP)
                        ?: throw SourcePluginException(app.getString(R.string.source_plugin_resolve_failed))
                    resolved.client.newCall(okhttp3.Request.Builder().url(resolved.url).build()).execute().use { response ->
                        if (!response.isSuccessful) throw SourcePluginException("HTTP ${response.code}")
                        response.body?.byteStream()?.use { it.copyTo(out) }
                    }
                }
                app.getString(R.string.source_plugin_mv_downloaded, name)
            } catch (error: Exception) {
                app.getString(R.string.musicfree_download_failed, error.localizedMessage ?: "MV")
            }
            launch(Dispatchers.Main) { Toast.makeText(app, message, Toast.LENGTH_LONG).show() }
        }
    }

    private val MV_HTTP = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(30, java.util.concurrent.TimeUnit.MINUTES)
        .build()
}
