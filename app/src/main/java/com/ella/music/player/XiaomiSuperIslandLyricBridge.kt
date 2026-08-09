package com.ella.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.ella.music.MainActivity
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.XiaomiSuperIslandSettings
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ROUTE
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.player.PlayerPalette
import com.xzakota.hyper.notification.focus.FocusNotification
import com.xzakota.hyper.notification.focus.template.CustomFocusTemplate
import com.xzakota.hyper.notification.focus.template.CustomFocusTemplateV3
import com.xzakota.hyper.notification.island.model.BigIslandArea
import com.xzakota.hyper.notification.island.model.TextInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Publishes Xiaomi HyperOS Super Island lyrics without coupling them to Android Live Update. */
internal class XiaomiSuperIslandLyricBridge(
    context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        const val TAG = "HalcyonSuperIsland"
        // The old channel was created as LOW and Android never allows raising an existing channel.
        // A new id is therefore required for HyperOS to treat the payload as a Focus notification.
        const val CHANNEL_ID = "ella_xiaomi_super_island_lyrics_v2"
        const val NOTIFICATION_ID = 0x454c4c53
        const val DEFAULT_ACCENT = 0xFF3482FF.toInt()
        // Every Focus update can make HyperOS re-inflate the island several times. Keep the
        // first lyric immediate, then give SystemUI 1.5 seconds to settle before moving the
        // visible word window again; Live Update remains responsible for finer word timing.
        const val MIN_RENDER_INTERVAL_MS = 1_500L
    }

    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val appIcon by lazy {
        Icon.createWithResource(appContext, R.mipmap.ic_launcher)
    }
    private val networkMutex = Mutex()

    @Volatile
    private var enabled = false
    @Volatile
    private var settings = XiaomiSuperIslandSettings()
    private var lastPayloadKey: String? = null
    private var networkJob: Job? = null
    private var pauseDismissJob: Job? = null
    private var pendingRenderJob: Job? = null
    private var pendingRenderRequest: RenderRequest? = null
    private var lastRenderElapsedMs = 0L
    private var dispatchGeneration = 0L
    private var xmsfNetworkingBlocked = false
    private var aggressiveTrackKey: String? = null

    /**
     * Keep the expensive Focus notification construction behind the same cadence as the
     * SystemUI update. Word-timed lyrics can tick every frame, while building RemoteViews,
     * artwork Icons and Focus extras for every tick is enough to jank the initial island.
     */
    private data class RenderRequest(
        val song: Song,
        val displayLyric: String,
        val fullLyric: String,
        val progressPercent: Int,
        val accentColor: Int,
        val artwork: Bitmap?,
        val settings: XiaomiSuperIslandSettings,
        val trackKey: String
    )

    /**
     * Focus picture entries are parcelled into SystemUI. Recreating four full-size Icons for
     * every lyric update makes the island upload the same album art repeatedly. Keep the scaled
     * variants for the current track and reuse them until the artwork object changes.
     */
    private data class ArtworkResources(
        val source: Bitmap,
        val avatar: Icon,
        val island: Icon,
        val smallIsland: Icon,
        val share: Icon,
        val expandBitmap: Bitmap,
        val tinyBitmap: Bitmap
    )

    private var artworkResources: ArtworkResources? = null

    @Synchronized
    private fun cachedArtworkResources(artwork: Bitmap?): ArtworkResources? {
        if (artwork == null || artwork.isRecycled) return null
        artworkResources?.takeIf { it.source === artwork }?.let { return it }

        val resources = ArtworkResources(
            source = artwork,
            avatar = Icon.createWithBitmap(scaleArtwork(artwork, 480)),
            island = Icon.createWithBitmap(scaleArtwork(artwork, 120)),
            smallIsland = Icon.createWithBitmap(scaleArtwork(artwork, 88)),
            share = Icon.createWithBitmap(scaleArtwork(artwork, 224)),
            expandBitmap = scaleArtwork(artwork, 116),
            tinyBitmap = scaleArtwork(artwork, 64)
        )
        artworkResources = resources
        return resources
    }

    private fun scaleArtwork(source: Bitmap, targetSize: Int): Bitmap {
        if (source.width == targetSize && source.height == targetSize) return source
        return Bitmap.createScaledBitmap(source, targetSize, targetSize, true)
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        lastPayloadKey = null
        if (enabled) ensureChannel() else clear()
    }

    fun setSettings(settings: XiaomiSuperIslandSettings) {
        val previousMode = this.settings.xmsfBypassMode
        this.settings = settings.sanitized()
        lastPayloadKey = null
        if (
            previousMode == XiaomiSuperIslandSettings.XMSF_MODE_AGGRESSIVE &&
            this.settings.xmsfBypassMode != XiaomiSuperIslandSettings.XMSF_MODE_AGGRESSIVE
        ) {
            restoreXmsfNetworkingAsync()
        }
    }

    fun isEnabled(): Boolean = enabled

    fun sendLyric(
        song: Song,
        line: LyricLine,
        positionMs: Long,
        durationMs: Long,
        artwork: Bitmap?
    ) {
        if (!enabled) return
        pauseDismissJob?.cancel()
        val activeSettings = settings
        val liveMode = when (activeSettings.lyricTextMode) {
            XiaomiSuperIslandSettings.TEXT_TRANSLATION ->
                SettingsManager.LIVE_UPDATE_LYRIC_MODE_TRANSLATION
            XiaomiSuperIslandSettings.TEXT_PRONUNCIATION ->
                SettingsManager.LIVE_UPDATE_LYRIC_MODE_PRONUNCIATION
            else -> SettingsManager.LIVE_UPDATE_LYRIC_MODE_ORIGINAL
        }
        val resolved = buildLiveLyricNotificationText(line, liveMode, positionMs) ?: return
        val fullLyric = resolved.fullLyric.trim().ifBlank { return }
        val displayLyric = when {
            activeSettings.lyricMode == XiaomiSuperIslandSettings.LYRIC_MODE_FULL -> fullLyric
            !activeSettings.scrollingEnabled -> fullLyric
            else -> resolved.lyric.trim().ifBlank { fullLyric }
        }
        val progress = if (durationMs > 0L) {
            ((positionMs.coerceIn(0L, durationMs) * 100L) / durationMs).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val accentColor = resolveAccentColor(activeSettings, artwork)
        val trackKey = listOf(song.id, song.path, song.title, song.artist).joinToString("|")
        val payloadKey = listOf(
            trackKey,
            displayLyric,
            fullLyric,
            activeSettings.hashCode(),
            accentColor
        ).joinToString("|")
        synchronized(this) {
            if (!enabled || payloadKey == lastPayloadKey) return
            lastPayloadKey = payloadKey
        }

        renderThrottled(
            RenderRequest(
                song = song,
                displayLyric = displayLyric,
                fullLyric = fullLyric,
                progressPercent = progress,
                accentColor = accentColor,
                artwork = artwork,
                settings = activeSettings,
                trackKey = trackKey
            )
        )
    }

    fun onPlaybackPaused() {
        restoreXmsfNetworkingAsync()
        pauseDismissJob?.cancel()
        val dismissDelay = settings.dismissDelayMs.toLong()
        if (dismissDelay <= 0L) {
            clear()
        } else {
            pauseDismissJob = scope.launch {
                delay(dismissDelay)
                clear()
            }
        }
    }

    fun clear() {
        pauseDismissJob?.cancel()
        pauseDismissJob = null
        pendingRenderJob?.cancel()
        pendingRenderJob = null
        pendingRenderRequest = null
        lastRenderElapsedMs = 0L
        lastPayloadKey = null
        artworkResources = null
        XiaomiSuperIslandLyricService.stop(appContext)
        notificationManager.cancel(NOTIFICATION_ID)
        networkJob?.cancel()
        val generation = synchronized(this) { ++dispatchGeneration }
        networkJob = scope.launch(Dispatchers.IO) {
            networkMutex.withLock {
                if (generation == dispatchGeneration) restoreXmsfNetworking()
            }
        }
    }

    fun destroy() {
        enabled = false
        clear()
    }

    private fun renderThrottled(request: RenderRequest) {
        val now = SystemClock.elapsedRealtime()
        val remaining = (lastRenderElapsedMs + MIN_RENDER_INTERVAL_MS - now).coerceAtLeast(0L)
        if (remaining == 0L) {
            pendingRenderJob?.cancel()
            pendingRenderJob = null
            pendingRenderRequest = null
            lastRenderElapsedMs = now
            renderAndDispatch(request)
            return
        }

        pendingRenderRequest = request
        pendingRenderJob?.cancel()
        pendingRenderJob = scope.launch {
            delay(remaining)
            val latestRequest = pendingRenderRequest ?: return@launch
            pendingRenderRequest = null
            pendingRenderJob = null
            lastRenderElapsedMs = SystemClock.elapsedRealtime()
            renderAndDispatch(latestRequest)
        }
    }

    private fun renderAndDispatch(request: RenderRequest) {
        val notification = buildNotification(
            song = request.song,
            displayLyric = request.displayLyric,
            fullLyric = request.fullLyric,
            progressPercent = request.progressPercent,
            accentColor = request.accentColor,
            artwork = request.artwork,
            activeSettings = request.settings
        )
        dispatch(notification, request.trackKey, request.settings)
    }

    private fun dispatch(
        notification: Notification,
        trackKey: String,
        activeSettings: XiaomiSuperIslandSettings
    ) {
        val mode = activeSettings.xmsfBypassMode
        val generation = synchronized(this) { ++dispatchGeneration }
        networkJob?.cancel()
        if (mode == XiaomiSuperIslandSettings.XMSF_MODE_DISABLED) {
            restoreXmsfNetworkingAsync(expectedGeneration = generation)
            XiaomiSuperIslandLyricService.publish(appContext, notification)
            return
        }

        networkJob = scope.launch(Dispatchers.IO) {
            networkMutex.withLock {
                if (generation != dispatchGeneration || !enabled) return@withLock
                if (mode == XiaomiSuperIslandSettings.XMSF_MODE_AGGRESSIVE) {
                    if (!xmsfNetworkingBlocked) blockXmsfNetworking()
                    if (generation != dispatchGeneration || !enabled) return@withLock
                    aggressiveTrackKey = trackKey
                    XiaomiSuperIslandLyricService.publish(appContext, notification)
                    return@withLock
                }

                if (!xmsfNetworkingBlocked) blockXmsfNetworking()
                if (generation != dispatchGeneration || !enabled) return@withLock
                // A failed firewall backend must not swallow the Focus notification. HyperOS
                // versions that do not need the workaround can still render the island directly.
                XiaomiSuperIslandLyricService.publish(appContext, notification)
                val duration = if (mode == XiaomiSuperIslandSettings.XMSF_MODE_CUSTOM) {
                    activeSettings.xmsfCustomDurationMs.toLong()
                } else {
                    XiaomiSuperIslandSettings.XMSF_STANDARD_DURATION_MS.toLong()
                }
                try {
                    delay(duration)
                } catch (_: CancellationException) {
                    // A newer lyric owns the next restore window.
                }
                if (generation == dispatchGeneration) restoreXmsfNetworking()
            }
        }
    }

    private suspend fun blockXmsfNetworking() {
        val blocked = withContext(NonCancellable) {
            ShizukuXmsfNetworkHelper.setXmsfNetworkingEnabled(appContext, false)
        }
        xmsfNetworkingBlocked = blocked
        if (!blocked) Log.w(TAG, "XMSF bypass unavailable; sending Focus notification directly")
    }

    private fun restoreXmsfNetworkingAsync(expectedGeneration: Long? = null) {
        scope.launch(Dispatchers.IO) {
            networkMutex.withLock {
                if (expectedGeneration != null && expectedGeneration != dispatchGeneration) return@withLock
                restoreXmsfNetworking()
            }
        }
    }

    private suspend fun restoreXmsfNetworking() {
        aggressiveTrackKey = null
        if (!xmsfNetworkingBlocked) return
        withContext(NonCancellable) {
            ShizukuXmsfNetworkHelper.setXmsfNetworkingEnabled(appContext, true)
        }
        xmsfNetworkingBlocked = false
    }

    private fun buildNotification(
        song: Song,
        displayLyric: String,
        fullLyric: String,
        progressPercent: Int,
        accentColor: Int,
        artwork: Bitmap?,
        activeSettings: XiaomiSuperIslandSettings
    ): Notification {
        val actionBundle = Bundle()
        val cachedArtwork = cachedArtworkResources(artwork)
        val subText = if (song.artist.isNotBlank()) {
            "${song.title.ifBlank { song.fileName }} - ${song.artist}"
        } else {
            song.title.ifBlank { song.fileName }
        }
        val hexColor = String.format("#FF%06X", 0xFFFFFF and accentColor)
        val highlightColor = if (activeSettings.textColorEnabled) hexColor else "#757575"
        val progressColor = if (activeSettings.progressColorEnabled) hexColor else "#757575"

        val focusExtras = FocusNotification.buildV3 {
            business = "lyric_display"
            isShowNotification = true
            enableFloat = false
            updatable = true
            islandFirstFloat = false
            aodTitle = displayLyric.take(20).ifBlank { "♪" }

            val avatarKey = cachedArtwork?.avatar?.let { createPicture("miui.focus.pic_avatar", it) }
            val islandKey = cachedArtwork?.island?.let { createPicture("miui.focus.pic_island", it) }
            val smallIslandKey = cachedArtwork?.smallIsland?.let { createPicture("miui.land.pic_island", it) }
            val shareKey = cachedArtwork?.share?.let { createPicture("miui.focus.pic_share", it) }
            val appKey = createPicture(
                "miui.focus.pic_app",
                appIcon
            )

            ticker = displayLyric.ifBlank { fullLyric }
            tickerPic = appKey

            chatInfo {
                picProfile = avatarKey
                title = fullLyric
                content = subText
                appIconPkg = appContext.packageName
            }

            if (activeSettings.actionStyle == XiaomiSuperIslandSettings.ACTION_STYLE_MEDIA_CONTROLS) {
                actions {
                    val useThreeButtons =
                        activeSettings.notificationStyle == XiaomiSuperIslandSettings.NOTIFICATION_STYLE_ADVANCED ||
                            activeSettings.mediaButtonLayout == XiaomiSuperIslandSettings.MEDIA_BUTTON_LAYOUT_THREE
                    if (useThreeButtons) {
                        addActionInfo {
                            type = 0
                            action = createMediaAction(
                                actionBundle = actionBundle,
                                key = "miui.focus.action_prev",
                                requestCode = 3610,
                                action = PlaybackService.ACTION_SKIP_PREVIOUS,
                                iconResId = R.drawable.ic_skip_previous,
                                title = "Previous"
                            )
                            actionIcon = createPicture(
                                "miui.focus.pic_btn_prev",
                                Icon.createWithResource(appContext, R.drawable.ic_skip_previous)
                            )
                            clickWithCollapse = false
                        }
                    }
                    addActionInfo {
                        type = 0
                        action = createMediaAction(
                            actionBundle = actionBundle,
                            key = "miui.focus.action_play_pause",
                            requestCode = 3611,
                            action = PlaybackService.ACTION_PLAY_PAUSE,
                            iconResId = R.drawable.ic_player_pause,
                            title = "Play/Pause"
                        )
                        actionIcon = createPicture(
                            "miui.focus.pic_btn_play_pause",
                            Icon.createWithResource(appContext, R.drawable.ic_player_pause)
                        )
                        clickWithCollapse = false
                    }
                    addActionInfo {
                        type = 0
                        action = createMediaAction(
                            actionBundle = actionBundle,
                            key = "miui.focus.action_next",
                            requestCode = 3612,
                            action = PlaybackService.ACTION_SKIP_NEXT,
                            iconResId = R.drawable.ic_skip_next,
                            title = "Next"
                        )
                        actionIcon = createPicture(
                            "miui.focus.pic_btn_next",
                            Icon.createWithResource(appContext, R.drawable.ic_skip_next)
                        )
                        clickWithCollapse = false
                    }
                }
            } else {
                progressInfo {
                    progress = progressPercent
                    colorProgress = progressColor
                    colorProgressEnd = progressColor
                }
            }

            island {
                islandProperty = 1
                if (activeSettings.textColorEnabled) this.highlightColor = hexColor
                bigIslandArea {
                    applyLyrics(
                        settings = activeSettings,
                        displayLyric = displayLyric,
                        fullLyric = fullLyric,
                        song = song,
                        islandKey = islandKey,
                        showHighlightColor = activeSettings.textColorEnabled
                    )
                }
                if (activeSettings.shareEnabled) {
                    shareData {
                        pic = shareKey
                        title = song.title.ifBlank { "♪" }
                        content = fullLyric
                        this.shareContent = this@XiaomiSuperIslandLyricBridge.shareContent(
                            song,
                            fullLyric,
                            activeSettings.shareFormat
                        )
                    }
                }
                smallIslandArea {
                    combinePicInfo {
                        if (smallIslandKey != null) {
                            picInfo {
                                type = 1
                                pic = smallIslandKey
                            }
                        }
                        progressInfo {
                            progress = progressPercent
                            colorReach = highlightColor
                            colorUnReach = "#333333"
                        }
                    }
                }
            }
        }
        // HyperOS accepts the standard Focus payload for lyric-only and progress layouts.
        // Custom Focus is reserved for the explicit advanced media-controls configuration;
        // treating every non-control state as custom causes the system to reject the package.
        val useAdvancedFocus = activeSettings.notificationStyle == XiaomiSuperIslandSettings.NOTIFICATION_STYLE_ADVANCED &&
            activeSettings.actionStyle == XiaomiSuperIslandSettings.ACTION_STYLE_MEDIA_CONTROLS
        val notificationExtras = if (useAdvancedFocus) {
            buildAdvancedFocusExtras(
                song = song,
                displayLyric = displayLyric,
                fullLyric = fullLyric,
                subText = subText,
                progressPercent = progressPercent,
                accentColor = accentColor,
                artwork = artwork,
                activeSettings = activeSettings,
                standardExtras = focusExtras
            )
        } else {
            focusExtras
        }
        if (!actionBundle.isEmpty) {
            notificationExtras.putBundle("miui.focus.actions", actionBundle)
        }
        Log.d(
            TAG,
            "Publishing Super Island lyric advanced=$useAdvancedFocus " +
                "text=${displayLyric.take(48)}"
        )

        val builder = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(fullLyric)
            .setContentText(subText)
            .setSubText(appContext.packageName)
            .setContentIntent(createContentIntent(activeSettings.clickStyle))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setLocalOnly(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(if (activeSettings.actionStyle == XiaomiSuperIslandSettings.ACTION_STYLE_MEDIA_CONTROLS) 0xFF757575.toInt() else accentColor)
            .addExtras(notificationExtras)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    /**
     * FocusNotification's standard compact island API can only draw an image and a progress
     * strip. HyperOS renders it as the cover-plus-waveform card seen in the original report.
     * The custom Focus template is the supported path for a lyric-bearing compact island.
     */
    private fun buildAdvancedFocusExtras(
        song: Song,
        displayLyric: String,
        fullLyric: String,
        subText: String,
        progressPercent: Int,
        accentColor: Int,
        artwork: Bitmap?,
        activeSettings: XiaomiSuperIslandSettings,
        standardExtras: Bundle
    ): Bundle {
        val trackTitle = song.title.ifBlank { song.fileName }.ifBlank { "♪" }
        val hexColor = String.format("#FF%06X", 0xFFFFFF and accentColor)
        val progressColor = if (activeSettings.progressColorEnabled) hexColor else "#FF757575"
        val cachedArtwork = cachedArtworkResources(artwork)
        val customExtras = FocusNotification.buildCustomV3 {
            business = "lyric_display"
            isShowNotification = true
            enableFloat = false
            updatable = true
            islandFirstFloat = false
            hideDeco = true
            aodTitle = displayLyric.take(20).ifBlank { "♪" }

            val avatarKey = cachedArtwork?.avatar?.let { createPicture("miui.focus.pic_avatar", it) }
            val islandKey = cachedArtwork?.island?.let { createPicture("miui.focus.pic_island", it) }
            val smallIslandKey = cachedArtwork?.smallIsland?.let { createPicture("miui.land.pic_island", it) }
            val shareKey = cachedArtwork?.share?.let { createPicture("miui.focus.pic_share", it) }
            val appKey = createPicture(
                "miui.focus.pic_app",
                appIcon
            )

            ticker = displayLyric.ifBlank { fullLyric.ifBlank { trackTitle } }
            tickerPic = appKey ?: smallIslandKey ?: avatarKey
            val customLightViews = createAdvancedExpandViews(
                lyric = displayLyric,
                subText = subText,
                progressPercent = progressPercent,
                accentColor = accentColor,
                artwork = cachedArtwork?.expandBitmap ?: artwork,
                darkMode = false,
                showControls = activeSettings.actionStyle == XiaomiSuperIslandSettings.ACTION_STYLE_MEDIA_CONTROLS
            )
            val customDarkViews = createAdvancedExpandViews(
                lyric = displayLyric,
                subText = subText,
                progressPercent = progressPercent,
                accentColor = accentColor,
                artwork = cachedArtwork?.expandBitmap ?: artwork,
                darkMode = true,
                showControls = activeSettings.actionStyle == XiaomiSuperIslandSettings.ACTION_STYLE_MEDIA_CONTROLS
            )
            val tinyViews = createAdvancedTinyViews(
                lyric = displayLyric,
                subText = subText,
                progressPercent = progressPercent,
                accentColor = accentColor,
                artwork = cachedArtwork?.tinyBitmap ?: artwork
            )
            createRemoteViews(CustomFocusTemplate.LAYOUT, customLightViews)
            createRemoteViews(CustomFocusTemplate.LAYOUT_NIGHT, customDarkViews)
            createRemoteViews(CustomFocusTemplate.LAYOUT_FLIP_TINY, tinyViews)
            createRemoteViews(CustomFocusTemplate.LAYOUT_FLIP_TINY_NIGHT, tinyViews)
            createRemoteViews(CustomFocusTemplateV3.LAYOUT_ISLAND_EXPAND, customDarkViews)

            island {
                islandProperty = 1
                if (activeSettings.textColorEnabled) highlightColor = hexColor
                bigIslandArea {
                    applyLyrics(
                        settings = activeSettings,
                        displayLyric = displayLyric,
                        fullLyric = fullLyric,
                        song = song,
                        islandKey = islandKey,
                        showHighlightColor = activeSettings.textColorEnabled
                    )
                }
                if (activeSettings.shareEnabled) {
                    shareData {
                        pic = shareKey
                        title = trackTitle
                        content = fullLyric
                        this.shareContent = this@XiaomiSuperIslandLyricBridge.shareContent(
                            song,
                            fullLyric,
                            activeSettings.shareFormat
                        )
                    }
                }
                smallIslandArea {
                    combinePicInfo {
                        if (smallIslandKey != null) {
                            picInfo {
                                type = 1
                                pic = smallIslandKey
                            }
                        }
                        progressInfo {
                            progress = progressPercent
                            colorReach = progressColor
                            colorUnReach = "#333333"
                        }
                    }
                }
            }
        }
        return mergeCustomFocusWithStandardIsland(customExtras, standardExtras)
    }

    private fun createAdvancedTinyViews(
        lyric: String,
        subText: String,
        progressPercent: Int,
        accentColor: Int,
        artwork: Bitmap?
    ): RemoteViews {
        return RemoteViews(appContext.packageName, R.layout.super_island_custom_tiny).apply {
            setTextViewText(R.id.super_island_tiny_lyric, lyric.ifBlank { "♪" })
            setTextViewText(R.id.super_island_tiny_subtitle, subText)
            setTextColor(R.id.super_island_tiny_lyric, Color.WHITE)
            setTextColor(R.id.super_island_tiny_subtitle, Color.argb(180, 255, 255, 255))
            setImageViewBitmap(R.id.super_island_tiny_progress, createProgressBitmap(44, 4, progressPercent, accentColor, true))
            applyArtwork(R.id.super_island_tiny_cover, artwork, 64)
            setOnClickPendingIntent(R.id.super_island_tiny_lyric, createContentIntent(XiaomiSuperIslandSettings.CLICK_STYLE_OPEN_APP))
        }
    }

    private fun createAdvancedExpandViews(
        lyric: String,
        subText: String,
        progressPercent: Int,
        accentColor: Int,
        artwork: Bitmap?,
        darkMode: Boolean,
        showControls: Boolean
    ): RemoteViews {
        val primaryColor = if (darkMode) Color.WHITE else Color.rgb(17, 17, 17)
        val secondaryColor = if (darkMode) Color.argb(180, 255, 255, 255) else Color.argb(150, 0, 0, 0)
        return RemoteViews(appContext.packageName, R.layout.super_island_custom_expand).apply {
            setTextViewText(R.id.super_island_expand_lyric, lyric.ifBlank { "♪" })
            setTextViewText(R.id.super_island_expand_subtitle, subText)
            setTextColor(R.id.super_island_expand_lyric, primaryColor)
            setTextColor(R.id.super_island_expand_subtitle, secondaryColor)
            setImageViewBitmap(R.id.super_island_expand_progress, createProgressBitmap(320, 6, progressPercent, accentColor, darkMode))
            applyArtwork(R.id.super_island_expand_cover, artwork, 116)
            setViewVisibility(
                R.id.super_island_expand_controls,
                if (showControls) android.view.View.VISIBLE else android.view.View.GONE
            )
            if (showControls) {
                setImageViewResource(R.id.super_island_expand_play_pause, R.drawable.ic_player_pause)
                setOnClickPendingIntent(
                    R.id.super_island_expand_previous,
                    createMediaCommandIntent(3610, PlaybackService.ACTION_SKIP_PREVIOUS)
                )
                setOnClickPendingIntent(
                    R.id.super_island_expand_play_pause,
                    createMediaCommandIntent(3611, PlaybackService.ACTION_PLAY_PAUSE)
                )
                setOnClickPendingIntent(
                    R.id.super_island_expand_next,
                    createMediaCommandIntent(3612, PlaybackService.ACTION_SKIP_NEXT)
                )
                setInt(R.id.super_island_expand_previous, "setColorFilter", primaryColor)
                setInt(R.id.super_island_expand_play_pause, "setColorFilter", primaryColor)
                setInt(R.id.super_island_expand_next, "setColorFilter", primaryColor)
            }
            setOnClickPendingIntent(
                R.id.super_island_expand_lyric,
                createContentIntent(XiaomiSuperIslandSettings.CLICK_STYLE_OPEN_APP)
            )
        }
    }

    private fun RemoteViews.applyArtwork(viewId: Int, artwork: Bitmap?, sizePx: Int) {
        if (artwork == null) {
            setImageViewResource(viewId, R.drawable.ic_music_note)
        } else {
            setImageViewBitmap(viewId, Bitmap.createScaledBitmap(artwork, sizePx, sizePx, true))
        }
    }

    private fun createProgressBitmap(
        width: Int,
        height: Int,
        progressPercent: Int,
        accentColor: Int,
        darkMode: Boolean
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = height / 2f
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (darkMode) Color.argb(85, 255, 255, 255) else Color.argb(56, 0, 0, 0)
        }
        val foreground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, background)
        val completedWidth = width * (progressPercent.coerceIn(0, 100) / 100f)
        if (completedWidth > 0f) {
            canvas.drawRoundRect(0f, 0f, completedWidth, height.toFloat(), radius, radius, foreground)
        }
        return bitmap
    }

    private fun mergeCustomFocusWithStandardIsland(customExtras: Bundle, standardExtras: Bundle): Bundle {
        val merged = Bundle(customExtras)
        val customJson = customExtras.getString("miui.focus.param.custom") ?: return merged
        val standardJson = standardExtras.getString("miui.focus.param") ?: return merged
        runCatching {
            val customRoot = JSONObject(customJson)
            val standardRoot = JSONObject(standardJson)
            val island = standardRoot.optJSONObject("param_v2")?.optJSONObject("param_island")
            if (island != null) {
                customRoot.put("param_island", island)
                merged.putString("miui.focus.param.custom", customRoot.toString())
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to merge standard island parameters into custom Focus payload", error)
        }
        return merged
    }

    private fun BigIslandArea.applyLyrics(
        settings: XiaomiSuperIslandSettings,
        displayLyric: String,
        fullLyric: String,
        song: Song,
        islandKey: String?,
        showHighlightColor: Boolean
    ) {
        val titleWithArtist = if (song.artist.isNotBlank()) {
            "${song.title.ifBlank { song.fileName }} - ${song.artist}"
        } else {
            song.title.ifBlank { song.fileName }
        }
        val showLeftCover = islandKey != null &&
            (settings.lyricMode != XiaomiSuperIslandSettings.LYRIC_MODE_FULL || settings.fullLyricShowLeftCover)
        val leftWeight = XiaomiSuperIslandLyricLayout.weightForCharacters(
            if (showLeftCover) settings.leftWithCoverTextChars else settings.leftWithoutCoverTextChars
        )
        val rightWeight = XiaomiSuperIslandLyricLayout.weightForCharacters(settings.rightTextChars)
        val text = if (settings.lyricMode == XiaomiSuperIslandSettings.LYRIC_MODE_FULL) {
            XiaomiSuperIslandLyricLayout.splitFullLyric(
                text = fullLyric,
                showLeftCover = showLeftCover,
                leftMaxWeight = leftWeight,
                rightMaxWeight = rightWeight
            )
        } else {
            XiaomiSuperIslandLyricLayout.Split(
                left = XiaomiSuperIslandLyricLayout.takeByWeight(titleWithArtist, leftWeight),
                right = XiaomiSuperIslandLyricLayout.takeByWeight(displayLyric, rightWeight)
            )
        }
        imageTextInfoLeft {
            type = 1
            if (showLeftCover) {
                picInfo {
                    type = 1
                    pic = islandKey
                }
            }
            textInfo {
                title = text.left.ifBlank { "♪" }
                this.showHighlightColor = showHighlightColor
                narrowFont = false
            }
        }
        this.textInfo = TextInfo().apply {
            title = text.right.ifBlank { "♪" }
            this.showHighlightColor = showHighlightColor
            narrowFont = false
        }
    }

    private fun createMediaAction(
        actionBundle: Bundle,
        key: String,
        requestCode: Int,
        action: String,
        iconResId: Int,
        title: String
    ): String {
        val pendingIntent = createMediaCommandIntent(requestCode, action)
        val notificationAction = Notification.Action.Builder(
            Icon.createWithResource(appContext, iconResId),
            title,
            pendingIntent
        ).build()
        actionBundle.putParcelable(key, notificationAction)
        return key
    }

    private fun createMediaCommandIntent(requestCode: Int, action: String): PendingIntent {
        return PendingIntent.getService(
            appContext,
            requestCode,
            Intent(appContext, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createContentIntent(clickStyle: Int): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (clickStyle == XiaomiSuperIslandSettings.CLICK_STYLE_MEDIA_CONTROLS) {
                putExtra(EXTRA_SHORTCUT_ROUTE, Screen.Player.route)
            }
        }
        return PendingIntent.getActivity(
            appContext,
            3600 + clickStyle,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun shareContent(song: Song, lyric: String, format: Int): String {
        val title = song.title.ifBlank { song.fileName.ifBlank { "未知歌曲" } }
        val artist = song.artist.ifBlank { "未知歌手" }
        return when (format) {
            XiaomiSuperIslandSettings.SHARE_FORMAT_INLINE -> "$lyric -$artist，$title"
            XiaomiSuperIslandSettings.SHARE_FORMAT_ARTIST_AND_SONG -> "$lyric\n$artist，$title"
            else -> "$lyric\n$title by $artist"
        }
    }

    private fun resolveAccentColor(settings: XiaomiSuperIslandSettings, artwork: Bitmap?): Int {
        if (settings.colorSource == XiaomiSuperIslandSettings.COLOR_SOURCE_CUSTOM) {
            return settings.customColor
        }
        return PlayerPalette.seedColor(artwork)?.toArgb() ?: DEFAULT_ACCENT
    }

    private fun ensureChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "小米超级岛歌词",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "HyperOS Super Island lyric updates"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }
}
