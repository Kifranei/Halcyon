package com.ella.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.ella.music.R
import com.ella.music.MainActivity
import com.ella.music.data.AppIconManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.json.JSONObject

/** A separate SuperX card: Media3 continues to own foreground notification 1001. */
@OptIn(UnstableApi::class)
internal class OriginOsMediaIslandController(
    context: Context,
    private val player: Player,
    enabledSettings: Flow<Boolean>,
    islandOnlySettings: Flow<Boolean>,
    scope: CoroutineScope,
    lyrics: Flow<OriginOsMediaIslandLyric?> = OriginOsMediaIslandLyrics.current
) {
    internal companion object {
        const val NOTIFICATION_ID = 0x48414c49
        const val CHANNEL_ID = "halcyon_originos_media_island"
        const val NOTIFICATION_GROUP = "halcyon_originos_playback"
        const val PAUSE_TIMEOUT_MS = 5_000L
        private const val SYSTEM_TAG = "VIVO_SUPERX_TAG"
        private const val TAG = "HalcyonOriginIsland"
    }

    private data class Presentation(
        val mediaId: String,
        val title: String,
        val artist: String,
        val lyric: String?,
        val playWhenReady: Boolean,
        val isPlaying: Boolean,
        val artworkGeneration: Int?,
        val darkUi: Boolean,
        val rtlControls: Boolean,
        val islandOnly: Boolean
    )

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)
    private val handler = Handler(player.applicationLooper)
    private val scene = "HALCYON_MEDIA_${appContext.packageName}"
    private var enabled = false
    private var islandOnly = false
    private var closed = false
    private var darkUi = superIslandSystemUiIsDark(appContext.resources.configuration.uiMode)
    private var rtlControls = appContext.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    private var artworkMediaId: String? = null
    private var artwork: Bitmap? = null
    private var currentLyric: OriginOsMediaIslandLyric? = null
    private var lastPresentation: Presentation? = null
    private var node = 0
    private var pausedMediaId: String? = null
    private var pauseExpired = false
    private val refresh = Runnable { refreshNow() }
    private val expirePause = Runnable {
        pauseExpired = true
        cancelNotification()
    }
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = requestRefresh()
    }
    private val configurationCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            darkUi = superIslandSystemUiIsDark(newConfig.uiMode)
            rtlControls = newConfig.layoutDirection == View.LAYOUT_DIRECTION_RTL
            requestRefresh()
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() = Unit
    }
    private val controlIcons = mutableMapOf<Pair<Int, Int>, Icon>()
    private val homeActivity: PendingIntent = PendingIntent.getActivity(
        appContext, NOTIFICATION_ID,
        Intent(appContext, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_HOME_FROM_ISLAND)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    private val settingsJob: kotlinx.coroutines.Job
    private val lyricsJob: kotlinx.coroutines.Job

    init {
        // Withdraw a stale card left by a reclaimed service, including OriginOS's rewritten tag.
        cancelNotification(force = true)
        player.addListener(listener)
        appContext.registerComponentCallbacks(configurationCallbacks)
        settingsJob = scope.launch {
            // Wait for both persisted values before publishing the first card.
            combine(enabledSettings, islandOnlySettings) { enabled, islandOnly ->
                enabled to islandOnly
            }.distinctUntilChanged().collect { (nextEnabled, nextIslandOnly) ->
                enabled = nextEnabled
                islandOnly = nextIslandOnly
                // The explicit settings toggle registers the scene. OriginOS persists it across
                // reboots, so ordinary playback must not require a running Shizuku daemon.
                requestRefresh()
            }
        }
        lyricsJob = scope.launch {
            lyrics.distinctUntilChanged().collect { lyric ->
                currentLyric = lyric
                requestRefresh()
            }
        }
    }

    fun updateArtwork(mediaId: String?, bitmap: Bitmap?) {
        if (closed || (artworkMediaId == mediaId && artwork === bitmap)) return
        artworkMediaId = mediaId
        artwork = bitmap
        requestRefresh()
    }

    private fun requestRefresh() {
        if (closed) return
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    private fun refreshNow() {
        val item = player.currentMediaItem
        if (!enabled || item == null || player.playbackState == Player.STATE_IDLE ||
            player.playbackState == Player.STATE_ENDED
        ) {
            resetPause()
            cancelNotification()
            return
        }
        if (player.playWhenReady) {
            resetPause()
        } else {
            if (pausedMediaId != item.mediaId) {
                // Restoring a paused queue must not create an unsolicited live activity.
                if (lastPresentation == null) return
                handler.removeCallbacks(expirePause)
                pausedMediaId = item.mediaId
                pauseExpired = false
                handler.postDelayed(expirePause, PAUSE_TIMEOUT_MS)
            }
            // Metadata/lyric changes must not restart the pause timer or resurrect this card.
            if (pauseExpired) return
        }

        val song = item.toSongFromMediaItemExtras()
        val title = song?.title?.takeIf { it.isNotBlank() }
            ?: item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.app_name)
        val artist = song?.artist?.takeIf { it.isNotBlank() }
            ?: song?.album?.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.app_name)
        val cover = artwork?.takeIf { artworkMediaId == item.mediaId && !it.isRecycled }
        val lyric = currentLyric?.takeIf { it.songKey == song?.playbackStackKey() }?.text
        val presentation = Presentation(
            item.mediaId, title, artist, lyric, player.playWhenReady, player.isPlaying,
            cover?.generationId, darkUi, rtlControls, islandOnly
        )
        if (presentation == lastPresentation) return
        try {
            ensureChannel()
            // A swipe suppresses this node. A new track or explicit resume can appear again,
            // while cover/lyric updates must respect the user's dismissal.
            val newNode = if (lastPresentation == null ||
                lastPresentation?.mediaId != presentation.mediaId ||
                (lastPresentation?.playWhenReady == false && presentation.playWhenReady)
            ) (node + 1).coerceAtLeast(1) else node
            manager.notify(NOTIFICATION_ID, buildNotification(presentation, cover, newNode))
            node = newNode
            lastPresentation = presentation
        } catch (error: Exception) {
            Log.w(TAG, "Unable to publish OriginOS playback island", error)
        }
    }

    private fun buildNotification(state: Presentation, cover: Bitmap?, newNode: Int): Notification {
        val coverIcon = cover?.let(Icon::createWithBitmap)
            ?: Icon.createWithResource(appContext, AppIconManager.notificationIconRes())
        val click = homeActivity
        val left = Bundle().apply { putParcelable("island.superx.leftInfo.icon", coverIcon) }
        val right = Bundle().apply {
            putInt("island.superx.rightInfo.waveState", if (state.isPlaying) 1 else 0)
            // AtomMainIslandRightTemplate blindly reads indices 0 AND 1. One color crashes
            // the system island process and can trigger a three-hour crash-loop lockout.
            putIntegerArrayList("island.superx.rightInfo.waveColor", arrayListOf(Color.WHITE, Color.WHITE))
        }
        val island = Bundle().apply {
            putInt("island.superx.leftTemplate", 1)
            putInt("island.superx.rightTemplate", 1)
            putInt("island.superx.showTime", -1)
            putString("island.superx.scene", scene)
            putString("island.superx.landingInfo", JSONObject().apply {
                put("mType", "app")
                put("isDeepLink", false)
                put("mPkgName", appContext.packageName)
            }.toString())
            putBundle("island.superx.leftInfo", left)
            putBundle("island.superx.rightInfo", right)
            // The expanded island stays dark even when the notification shade is light.
            putParcelable("island.superx.customTemplate", buildCard(
                state, coverIcon, darkBackground = true, showLyric = true))
            putParcelable("island.superx.clickResp", click)
        }
        val base = Bundle().apply {
            putCharSequence("notification.superx.baseInfos.title", state.title)
            putCharSequence("notification.superx.baseInfos.content", state.artist)
            putParcelable("notification.superx.baseInfos.icon", coverIcon)
        }
        val extras = Bundle().apply {
            putInt("notification.superx.operation", 0)
            putInt("notification.superx.newNode", newNode)
            putInt("notification.superx.displays", 511)
            putBoolean("notification.superx.sound", false)
            putBoolean("notification.superx.dismissWhenKill", true)
            // Keep the fallback notification quiet. Valid generic SuperX cards ignore
            // showNotify for shade visibility; the group summary below hides that row.
            putBoolean("notification.superx.showNotify", !state.islandOnly)
            // The firmware's template 8 only wires the three buttons, leaving its header inert.
            // Template 7 hosts our RemoteViews with a separate header Activity PendingIntent.
            putInt("notification.superx.template", 7)
            putBoolean("notification.superx.disableInvertColor", true)
            putString("notification.superx.scene", scene)
            putInt("notification.superx.changedRecord", 0)
            putParcelable("notification.superx.clickResp", click)
            putBundle("notification.superx.baseInfos", base)
            putParcelable("notification.superx.customTemplate", buildCard(state, coverIcon, state.darkUi))
            // Keep the compact card's image and two strings populated for vendor fallbacks.
            putBundle("notification.superx.shortInfos", Bundle().apply {
                putString("notification.superx.shortInfos.describeShort", state.title)
                putString("notification.superx.shortInfos.coreInfoShort", state.artist)
                putParcelable("notification.superx.shortInfos.image", coverIcon)
            })
            putBundle("notification.superx.island", island)
            putString("notification.superx.eventInfo", JSONObject().apply {
                put("island_content", state.title)
                put("island_status", state.artist)
            }.toString())
            putBundle("notification.superx.capsule", Bundle().apply {
                putInt("notification.superx.capsule.state", 1)
                putCharSequence("notification.superx.capsule.content", state.title)
                putParcelable("notification.superx.capsule.icon", coverIcon)
            })
        }
        return Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(AppIconManager.notificationIconRes())
            .setLargeIcon(cover)
            .setContentTitle(state.title)
            .setContentText(state.artist)
            .setContentIntent(click)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            // SystemUI prunes the summary of a group with one child, leaving Media3's
            // standard card visible. The island listener still receives this record.
            .setGroup(if (state.islandOnly) NOTIFICATION_GROUP else null)
            .setGroupSummary(state.islandOnly)
            .addExtras(extras)
            .build()
    }

    private fun mediaAction(action: String, index: Int): PendingIntent = PendingIntent.getService(
        appContext, NOTIFICATION_ID + index,
        Intent(appContext, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildCard(
        state: Presentation, cover: Icon, darkBackground: Boolean, showLyric: Boolean = false
    ): RemoteViews {
        val color = if (darkBackground) 0xFFDADFE7.toInt() else 0xFF3E444D.toInt()
        val lyric = state.lyric.takeIf { showLyric }
        return RemoteViews(appContext.packageName, R.layout.originos_media_island_card).apply {
            setTextViewText(R.id.island_title, lyric ?: state.title)
            setTextViewText(R.id.island_artist, if (lyric != null) "${state.title} - ${state.artist}" else state.artist)
            setTextColor(R.id.island_title, if (darkBackground) 0xFFE1E5EC.toInt() else 0xFF303742.toInt())
            setTextColor(R.id.island_artist, if (darkBackground) 0xFFA6AFBD.toInt() else 0xFF697382.toInt())
            setImageViewIcon(R.id.island_cover, cover)
            setOnClickPendingIntent(R.id.island_header, homeActivity)
            setImageViewIcon(R.id.island_previous, controlIcon(R.drawable.ic_skip_previous, color))
            setImageViewIcon(R.id.island_play_pause, controlIcon(
                if (state.playWhenReady) R.drawable.ic_player_pause else R.drawable.ic_player_play, color))
            setImageViewIcon(R.id.island_next, controlIcon(R.drawable.ic_skip_next, color))
            setContentDescription(R.id.island_play_pause, appContext.getString(
                if (state.playWhenReady) R.string.common_pause else R.string.common_play))
            setOnClickPendingIntent(R.id.island_previous, mediaAction(PlaybackService.ACTION_SKIP_PREVIOUS, 0))
            setOnClickPendingIntent(R.id.island_play_pause, mediaAction(PlaybackService.ACTION_PLAY_PAUSE, 1))
            setOnClickPendingIntent(R.id.island_next, mediaAction(PlaybackService.ACTION_SKIP_NEXT, 2))
        }
    }

    private fun controlIcon(resId: Int, color: Int): Icon = controlIcons.getOrPut(resId to color) {
        // ImageButtons center the bitmap directly; VButton's old empty-label offset is unnecessary.
        Icon.createWithBitmap(renderTintedDrawableBitmap(appContext, resId, color, 72))
    }

    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.originos_media_island_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        })
    }

    private fun resetPause() {
        handler.removeCallbacks(expirePause)
        pausedMediaId = null
        pauseExpired = false
    }

    private fun cancelNotification(force: Boolean = false) {
        if (!force && lastPresentation == null) return
        runCatching {
            manager.cancel(NOTIFICATION_ID)
            manager.cancel(SYSTEM_TAG, NOTIFICATION_ID)
        }.onFailure { Log.w(TAG, "Unable to withdraw OriginOS playback island", it) }
        lastPresentation = null
    }

    fun close() {
        if (closed) return
        closed = true
        settingsJob.cancel()
        lyricsJob.cancel()
        player.removeListener(listener)
        appContext.unregisterComponentCallbacks(configurationCallbacks)
        handler.removeCallbacks(refresh)
        resetPause()
        cancelNotification(force = true)
        artwork = null
        currentLyric = null
        controlIcons.clear()
    }
}
