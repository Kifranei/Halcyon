package com.ella.music

import android.app.PictureInPictureParams
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.ella.music.data.SettingsManager
import com.ella.music.ui.theme.EllaTheme
import com.ella.music.ui.theme.THEME_FOLLOW_SYSTEM
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Independent, audible MV player used exclusively by the song-detail MV action. */
class MusicVideoActivity : ComponentActivity() {
    internal var activePlayer: ExoPlayer? = null
        private set
    internal var pictureInPictureMode by mutableStateOf(false)
        private set
    private var musicVideoMediaSession: MediaSession? = null
    private val musicVideoMediaSessionId = "music_video_${UUID.randomUUID()}"
    private var landscapeImmersive = false
    private var resumeAfterArtistNavigation = false
    private var pictureInPictureAspectRatio = 16f / 9f

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
        val settingsManager = SettingsManager.getInstance(this)
        val orientationMode = runBlocking(Dispatchers.IO) {
            settingsManager.musicVideoOrientation.first()
        }
        val initialLandscape = when (orientationMode) {
            SettingsManager.MUSIC_VIDEO_ORIENTATION_SYSTEM ->
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            SettingsManager.MUSIC_VIDEO_ORIENTATION_PORTRAIT -> false
            SettingsManager.MUSIC_VIDEO_ORIENTATION_LANDSCAPE -> true
            else -> (videoAspectRatio ?: (16f / 9f)) >= 1f
        }
        requestedOrientation = when (orientationMode) {
            SettingsManager.MUSIC_VIDEO_ORIENTATION_SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else -> if (initialLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        setContent {
            val settings = remember { settingsManager }
            val themeMode by settings.themeMode.collectAsState(initial = THEME_FOLLOW_SYSTEM)
            EllaTheme(themeMode = themeMode) {
                DetailMusicVideoScreen(
                    song = song,
                    source = source,
                    videoAspectRatio = videoAspectRatio,
                    initialLandscape = initialLandscape,
                    initialOrientationMode = orientationMode,
                    onBack = ::finish
                )
            }
        }
    }

    override fun onStop() {
        // An MV opened from the detail page is audible. It must never continue as an invisible
        // second player after navigating to an artist, sharing, or opening another MV. PiP is the
        // only background state that deliberately keeps the audible player alive.
        if (!isInPictureInPictureMode) activePlayer?.pause()
        super.onStop()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ uses setAutoEnterEnabled for a smooth home gesture. Older supported Android
        // versions need an explicit request when the user sends the MV to the background.
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            activePlayer?.isPlaying == true &&
            !resumeAfterArtistNavigation
        ) {
            enterMusicVideoPictureInPicture()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pictureInPictureMode = isInPictureInPictureMode
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
        configurePictureInPicture(autoEnter = false)
        activePlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (resumeAfterArtistNavigation) {
            resumeAfterArtistNavigation = false
            activePlayer?.play()
        }
        configurePictureInPicture(autoEnter = activePlayer?.isPlaying == true)
    }

    internal fun configurePictureInPicture(
        aspectRatio: Float? = null,
        autoEnter: Boolean = activePlayer?.isPlaying == true
    ) {
        pictureInPictureAspectRatio = aspectRatio
            ?.takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(0.5f, 2f)
            ?: pictureInPictureAspectRatio
        runCatching {
            setPictureInPictureParams(buildPictureInPictureParams(autoEnter))
        }
    }

    internal fun attachMusicVideoPlayer(player: ExoPlayer) {
        if (activePlayer === player && musicVideoMediaSession != null) return
        musicVideoMediaSession?.release()
        musicVideoMediaSession = MediaSession.Builder(this, player)
            .setId(musicVideoMediaSessionId)
            .build()
        activePlayer = player
        configurePictureInPicture(autoEnter = player.isPlaying)
    }

    internal fun detachMusicVideoPlayer(player: ExoPlayer): Boolean {
        if (activePlayer !== player) return false
        configurePictureInPicture(autoEnter = false)
        musicVideoMediaSession?.release()
        musicVideoMediaSession = null
        activePlayer = null
        return true
    }

    internal fun enterMusicVideoPictureInPicture(aspectRatio: Float? = null): Boolean {
        configurePictureInPicture(aspectRatio = aspectRatio, autoEnter = true)
        return runCatching {
            enterPictureInPictureMode(buildPictureInPictureParams(autoEnter = true))
        }.getOrDefault(false)
    }

    private fun buildPictureInPictureParams(autoEnter: Boolean): PictureInPictureParams {
        val denominator = 1_000
        val numerator = (pictureInPictureAspectRatio * denominator).toInt().coerceAtLeast(1)
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(numerator, denominator))
            .setActions(listOf(buildPictureInPicturePlaybackAction()))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(autoEnter)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
    }

    private fun buildPictureInPicturePlaybackAction(): RemoteAction {
        val playing = activePlayer?.isPlaying == true
        val title = getString(if (playing) R.string.common_pause else R.string.common_play)
        val icon = Icon.createWithResource(
            this,
            if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        val intent = Intent(this, MusicVideoActivity::class.java)
            .setAction(ACTION_TOGGLE_PICTURE_IN_PICTURE_PLAYBACK)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            PICTURE_IN_PICTURE_PLAYBACK_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteAction(icon, title, title, pendingIntent)
    }

    override fun onDestroy() {
        landscapeImmersive = false
        pictureInPictureMode = false
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        musicVideoMediaSession?.release()
        musicVideoMediaSession = null
        activePlayer?.release()
        activePlayer = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_TOGGLE_PICTURE_IN_PICTURE_PLAYBACK) {
            activePlayer?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
                configurePictureInPicture(autoEnter = player.isPlaying)
            }
            return
        }
        setIntent(intent)
        // A singleTop MV route can be reused while it is visible. Recompose it from the new
        // intent after releasing its old decoder instead of layering another audible player.
        activePlayer?.pause()
        recreate()
    }

    private companion object {
        const val ACTION_TOGGLE_PICTURE_IN_PICTURE_PLAYBACK =
            "com.ella.music.action.TOGGLE_MUSIC_VIDEO_PIP_PLAYBACK"
        const val PICTURE_IN_PICTURE_PLAYBACK_REQUEST_CODE = 0x4D56
    }
}
