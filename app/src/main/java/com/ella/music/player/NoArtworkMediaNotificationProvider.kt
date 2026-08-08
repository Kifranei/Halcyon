package com.ella.music.player

import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.ella.music.R
import com.ella.music.data.PlaylistStore
import com.ella.music.data.SettingsManager
import com.google.common.collect.ImmutableList

@OptIn(UnstableApi::class)
internal class NoArtworkMediaNotificationProvider(
    private val service: PlaybackService
) : MediaNotification.Provider {
    private companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "ella_music_playback"
        const val FLAG_ALWAYS_SHOW_TICKER_FALLBACK = 0x1000000
        const val FLAG_ONLY_UPDATE_TICKER_FALLBACK = 0x2000000
        const val LARGE_ICON_MAX_SIZE = 512
    }
    private data class PlaybackModeAction(
        val icon: Int,
        val title: String
    )

    private val largeIconCache = object : LruCache<String, Bitmap>(6 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }
    private var lastMediaSession: MediaSession? = null
    private var lastMediaButtonPreferences: ImmutableList<CommandButton>? = null
    private var lastActionFactory: MediaNotification.ActionFactory? = null
    private var lastCallback: MediaNotification.Provider.Callback? = null

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        lastMediaSession = mediaSession
        lastMediaButtonPreferences = mediaButtonPreferences
        lastActionFactory = actionFactory
        lastCallback = onNotificationChangedCallback
        PlaybackTickerState.setRefreshCallback {
            onNotificationChangedCallback.onNotificationChanged(
                createNotification(
                    mediaSession,
                    mediaButtonPreferences,
                    actionFactory,
                    onNotificationChangedCallback
                )
            )
        }
        ensureChannel()
        val player = mediaSession.player
        val metadata = player.mediaMetadata
        val tickerPayload = PlaybackTickerState.current()
        val largeIcon = resolveLargeIcon(metadata)
        // Render the app-owned card from the ticker snapshot. Session metadata is published
        // separately for MiPlay/Flyme/Bluetooth clients. Reading the session copy here caused
        // two card rebuilds for each line and visible flashing on ColorOS media controls.
        val contentTitle = tickerPayload?.text
            ?: metadata.title?.takeIf { it.isNotBlank() }
            ?: service.getString(R.string.app_name)
        val contentText = tickerPayload?.translation
            ?: metadata.artist?.takeIf { it.isNotBlank() }
            ?: metadata.albumTitle
            ?: ""
        val builder = NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_flyme_ticker)
            .setLargeIcon(largeIcon)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setTicker(tickerPayload?.text)
            .setContentIntent(mediaSession.sessionActivity)
            .setDeleteIntent(actionFactory.createNotificationDismissalIntent(mediaSession))
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val compactIndices = mutableListOf<Int>()
        var actionCount = 0
        fun addMediaAction(command: Int, icon: Int, title: String, compact: Boolean = false) {
            val index = actionCount++
            builder.addAction(
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(service, icon),
                    title,
                    command
                )
            )
            if (compact) compactIndices += index
        }

        fun addCustomAction(action: String, icon: Int, title: String, compact: Boolean = false) {
            val index = actionCount++
            builder.addAction(
                actionFactory.createCustomAction(
                    mediaSession,
                    IconCompat.createWithResource(service, icon),
                    title,
                    action,
                    Bundle.EMPTY
                )
            )
            if (compact) compactIndices += index
        }

        val currentSong = player.currentMediaItem?.toSongFromMediaItemExtras()
        val isFavorite = currentSong?.let {
            PlaylistStore.getInstance(service).isFavorite(it)
        } == true
        val playbackModeAction = player.playbackModeAction()
        val selectedButtons = service.mediaNotificationButtonIds.toSet()

        if (SettingsManager.MEDIA_NOTIFICATION_BUTTON_DESKTOP_LYRIC in selectedButtons) {
            addCustomAction(
                PlaybackService.ACTION_TOGGLE_DESKTOP_LYRIC,
                service.desktopLyricNotificationIcon(),
                service.getString(R.string.notification_action_desktop_lyric),
                compact = false
            )
        }
        if (SettingsManager.MEDIA_NOTIFICATION_BUTTON_FAVORITE in selectedButtons) {
            addCustomAction(
                PlaybackService.ACTION_TOGGLE_FAVORITE,
                if (isFavorite) R.drawable.ic_notification_favorite_filled else R.drawable.ic_notification_favorite,
                if (isFavorite) service.getString(R.string.common_unfavorite) else service.getString(R.string.common_favorite),
                compact = false
            )
        }

        addMediaAction(
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            R.drawable.ic_skip_previous,
            service.getString(R.string.common_previous),
            compact = true
        )

        addMediaAction(
            Player.COMMAND_PLAY_PAUSE,
            if (player.isPlaying) {
                R.drawable.ic_player_pause
            } else {
                R.drawable.ic_player_play
            },
            if (player.isPlaying) service.getString(R.string.common_pause) else service.getString(R.string.common_play),
            compact = true
        )

        addMediaAction(
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            R.drawable.ic_skip_next,
            service.getString(R.string.common_next),
            compact = true
        )

        if (SettingsManager.MEDIA_NOTIFICATION_BUTTON_PLAYBACK_MODE in selectedButtons) {
            addCustomAction(
                PlaybackService.ACTION_TOGGLE_SHUFFLE,
                playbackModeAction.icon,
                playbackModeAction.title,
                compact = false
            )
        }

        val style = MediaStyleNotificationHelper.MediaStyle(mediaSession)
            .setShowActionsInCompactView(*compactIndices.toIntArray())
        builder.setStyle(style)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
        }
        val notification = builder.build()
        currentSong?.let { song ->
            service.xiaomiMediaIslandShareParams(song)?.let { shareParams ->
                notification.extras.putString("miui.focus.param.media", shareParams)
            }
        }
        Log.d(PlaybackService.TIMING_TAG, "notification update mediaId=${player.currentMediaItem?.mediaId}")
        if (tickerPayload != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                notification.extras.putBoolean("ticker_icon_switch", false)
                notification.extras.putInt("ticker_icon", R.drawable.ic_flyme_ticker)
                notification.extras.putString("ticker_text", tickerPayload.text)
                notification.extras.putString("lyric", tickerPayload.text)
                tickerPayload.translation?.let { notification.extras.putString("ticker_translation", it) }
            }
            notification.flags = notification.flags or FLAG_ALWAYS_SHOW_TICKER_FALLBACK
            notification.flags = notification.flags or FLAG_ONLY_UPDATE_TICKER_FALLBACK
        }
        return MediaNotification(NOTIFICATION_ID, notification)
    }

    fun refresh() {
        val mediaSession = lastMediaSession ?: return
        val mediaButtonPreferences = lastMediaButtonPreferences ?: return
        val actionFactory = lastActionFactory ?: return
        val callback = lastCallback ?: return
        callback.onNotificationChanged(
            createNotification(
                mediaSession,
                mediaButtonPreferences,
                actionFactory,
                callback
            )
        )
    }

    private fun Player.playbackModeAction(): PlaybackModeAction {
        return when {
            service.appShuffleEnabled -> PlaybackModeAction(
                icon = R.drawable.ic_notification_shuffle,
                title = service.getString(R.string.notification_action_shuffle)
            )

            repeatMode == Player.REPEAT_MODE_ONE -> PlaybackModeAction(
                icon = R.drawable.ic_repeat_one,
                title = service.getString(R.string.notification_action_repeat_one)
            )

            repeatMode == Player.REPEAT_MODE_ALL -> PlaybackModeAction(
                icon = R.drawable.ic_repeat,
                title = service.getString(R.string.notification_action_repeat_all)
            )

            else -> PlaybackModeAction(
                icon = R.drawable.ic_playback_order,
                title = service.getString(R.string.notification_action_order)
            )
        }
    }

    private fun resolveLargeIcon(metadata: MediaMetadata): Bitmap? {
        metadata.artworkData?.takeIf { it.isNotEmpty() }?.let { data ->
            val key = "data:${data.contentHashCode()}:${data.size}"
            largeIconCache.get(key)?.let { return it }
            decodeArtworkData(data)?.also {
                largeIconCache.put(key, it)
                return it
            }
        }

        val uri = metadata.artworkUri ?: return defaultLargeIcon()
        if (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
            return defaultLargeIcon()
        }
        val key = "uri:$uri"
        largeIconCache.get(key)?.let { return it }
        return decodeArtworkUri(uri)
            ?.also { largeIconCache.put(key, it) }
            ?: defaultLargeIcon()
    }

    private fun defaultLargeIcon(): Bitmap {
        val key = "default"
        largeIconCache.get(key)?.let { return it }
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                intArrayOf(
                    android.graphics.Color.rgb(94, 155, 255),
                    android.graphics.Color.rgb(62, 99, 216),
                    android.graphics.Color.rgb(32, 42, 104)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.argb(42, 255, 255, 255)
        canvas.drawCircle(size * 0.52f, size * 0.50f, size * 0.34f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.035f
        paint.color = android.graphics.Color.argb(66, 255, 255, 255)
        canvas.drawCircle(size * 0.52f, size * 0.50f, size * 0.24f, paint)
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.argb(36, 0, 0, 0)
        canvas.drawCircle(size * 0.52f, size * 0.50f, size * 0.06f, paint)
        largeIconCache.put(key, bitmap)
        return bitmap
    }

    private fun decodeArtworkData(data: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = bounds.notificationArtworkSampleSize()
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching {
            BitmapFactory.decodeByteArray(data, 0, data.size, options)?.centerCropSquare()
        }.getOrNull()
    }

    private fun decodeArtworkUri(uri: Uri): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            service.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = bounds.notificationArtworkSampleSize()
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            service.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }?.centerCropSquare()
        }.getOrNull()
    }

    private fun BitmapFactory.Options.notificationArtworkSampleSize(): Int {
        var sample = 1
        while (outWidth / sample > LARGE_ICON_MAX_SIZE || outHeight / sample > LARGE_ICON_MAX_SIZE) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun Bitmap.centerCropSquare(): Bitmap {
        if (width == height) return this
        val size = minOf(width, height)
        val x = ((width - size) / 2).coerceAtLeast(0)
        val y = ((height - size) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(this, x, y, size, size)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean {
        return service.handleNotificationCustomAction(action)
    }

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
        return MediaNotification.Provider.NotificationChannelInfo(
            CHANNEL_ID,
            service.getString(R.string.playback_service_notification_channel)
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = service.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                CHANNEL_ID,
                service.getString(R.string.playback_service_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }
}
