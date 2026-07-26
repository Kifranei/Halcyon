package com.ella.music

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.exoplayer.ExoPlayer
import com.ella.music.data.SettingsManager
import com.ella.music.ui.theme.EllaTheme
import com.ella.music.ui.theme.THEME_FOLLOW_SYSTEM

/** Independent, audible MV player used exclusively by the song-detail MV action. */
class MusicVideoActivity : ComponentActivity() {
    internal var activePlayer: ExoPlayer? = null
    private var landscapeImmersive = false
    private var resumeAfterArtistNavigation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val song = MusicVideoLauncher.songFrom(intent) ?: run {
            finish()
            return
        }
        val source = MusicVideoLauncher.sourceUriFrom(intent) ?: run {
            finish()
            return
        }
        val videoAspectRatio = MusicVideoLauncher.sourceAspectRatioFrom(intent)
        setContent {
            val settings = remember { SettingsManager.getInstance(this) }
            val themeMode by settings.themeMode.collectAsState(initial = THEME_FOLLOW_SYSTEM)
            EllaTheme(themeMode = themeMode) {
                DetailMusicVideoScreen(
                    song = song,
                    source = source,
                    videoAspectRatio = videoAspectRatio,
                    onBack = ::finish
                )
            }
        }
    }

    override fun onStop() {
        // An MV opened from the detail page is audible. It must never continue as an invisible
        // second player after navigating to an artist, sharing, or opening another MV.
        activePlayer?.pause()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && landscapeImmersive) applyLandscapeImmersiveMode()
    }

    internal fun setLandscapeImmersive(enabled: Boolean) {
        landscapeImmersive = enabled
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (enabled) applyLandscapeImmersiveMode()
        else WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    private fun applyLandscapeImmersiveMode() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    internal fun pauseForArtistNavigation() {
        resumeAfterArtistNavigation = true
        activePlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (resumeAfterArtistNavigation) {
            resumeAfterArtistNavigation = false
            activePlayer?.play()
        }
    }

    override fun onDestroy() {
        landscapeImmersive = false
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        activePlayer?.release()
        activePlayer = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A singleTop MV route can be reused while it is visible. Recompose it from the new
        // intent after releasing its old decoder instead of layering another audible player.
        activePlayer?.pause()
        recreate()
    }
}
