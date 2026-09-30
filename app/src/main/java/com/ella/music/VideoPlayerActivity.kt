package com.ella.music

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ella.music.data.SettingsManager
import com.ella.music.ui.theme.EllaTheme
import com.ella.music.ui.theme.THEME_FOLLOW_SYSTEM
import top.yukonga.miuix.kmp.basic.Text
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ella.music.video.VideoSourceResolver
import com.ella.music.video.isDirectVideoSource
import com.ella.music.video.isVideoSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Public URI entry point; all actual playback belongs to the existing MV player. */
class VideoPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openSource(intent)
    }

    private fun openSource(request: Intent) {
        val source = externalVideoSource(request) ?: run { finish(); return }
        val uri = Uri.parse(source)
        val mime = request.type ?: runCatching { contentResolver.getType(uri) }.getOrNull()
        val title = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "Video" }
        if (isDirectVideoSource(source, mime)) {
            MusicVideoLauncher.openVideo(this, uri, title, mime)
            finish()
            return
        }
        setContent {
            val settings = androidx.compose.runtime.remember { SettingsManager.getInstance(this) }
            val theme by settings.themeMode.collectAsState(initial = THEME_FOLLOW_SYSTEM)
            EllaTheme(themeMode = theme) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(getString(R.string.video_resolving))
                }
            }
        }
        lifecycleScope.launch {
            try {
                var item = VideoSourceResolver.resolve(this@VideoPlayerActivity, source, mime).first()
                if (item.needsResolution) item = VideoSourceResolver.resolve(this@VideoPlayerActivity, item.uri).first()
                MusicVideoLauncher.openVideo(this@VideoPlayerActivity, Uri.parse(item.uri), item.title, item.mimeType)
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) {
                Toast.makeText(this@VideoPlayerActivity, failure.message ?: getString(R.string.video_invalid_source), Toast.LENGTH_LONG).show()
            } finally { finish() }
        }
    }
}

internal fun externalVideoSource(request: Intent): String? {
    @Suppress("DEPRECATION")
    val stream = request.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
    return (request.data ?: stream ?: request.clipData?.getItemAt(0)?.uri)?.toString()
        ?.takeIf(::isVideoSource)
        ?: request.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf(::isVideoSource)
}
