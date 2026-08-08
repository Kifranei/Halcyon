package com.ella.music.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ella.music.R

/** Publishes the selected current lyric as an Android 16 Live Update-compatible notification. */
internal class LiveLyricNotificationBridge(context: Context) {
    private companion object {
        const val TAG = "LiveLyricNotification"
        const val CHANNEL_ID = "ella_live_lyric_updates_v1"
        const val NOTIFICATION_ID = 0x454c4c52
        // Android's notification service defaults to roughly five updates per second for an
        // existing non-progress notification. Keep headroom so word-level lyric updates are not
        // shed by the system while still keeping the chip responsive.
        const val MIN_UPDATE_INTERVAL_MS = 220L
    }

    private data class Payload(
        val songTitle: String,
        val lyric: String,
        val compactLyric: String,
        val secondaryText: String,
        val artwork: Bitmap?
    )

    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var enabled = false
    private var lastPayload: Payload? = null
    private var pendingPayload: Payload? = null
    private var pendingDispatch: Runnable? = null
    private var lastDispatchElapsedMs = 0L

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        if (!enabled) {
            clear()
        } else {
            lastPayload = null
            lastDispatchElapsedMs = 0L
        }
    }

    fun sendLyric(
        songTitle: String?,
        lyric: String?,
        compactLyric: String? = lyric,
        allowLongCompactLyric: Boolean = false,
        preserveCompactLyric: Boolean = false,
        secondaryLyric: String? = null,
        artwork: Bitmap? = null
    ) {
        if (!enabled) return
        val cleanLyric = lyric
            ?.replace(Regex("\\s*\\n\\s*"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: run {
                clear()
                return
            }
        val cleanCompactLyric = compactLyric
            ?.replace(Regex("\\s*\\n\\s*"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                if (preserveCompactLyric) it else {
                    compactLiveLyricText(it, preserveLongToken = allowLongCompactLyric)
                }
            }
            ?: compactLiveLyricText(cleanLyric)
        val cleanSongTitle = songTitle?.trim()?.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.app_name)
        val cleanSecondaryText = secondaryLyric
            ?.replace(Regex("\\s*\\n\\s*"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: cleanSongTitle
        val payload = Payload(
            songTitle = cleanSongTitle,
            lyric = cleanLyric,
            compactLyric = cleanCompactLyric,
            secondaryText = cleanSecondaryText,
            artwork = artwork
        )
        if (payload == lastPayload) {
            // The pending value may be an older word that was superseded by a timing correction.
            // Do not let that stale value get posted after the lyric has returned to the last
            // visible payload.
            cancelPendingDispatch()
            return
        }
        if (payload == pendingPayload) return

        pendingPayload = payload
        dispatchPendingPayload()
    }

    private fun dispatchPendingPayload() {
        val payload = pendingPayload ?: return
        val nowMs = SystemClock.elapsedRealtime()
        val remainingMs = if (lastDispatchElapsedMs == 0L) {
            0L
        } else {
            (MIN_UPDATE_INTERVAL_MS - (nowMs - lastDispatchElapsedMs)).coerceAtLeast(0L)
        }
        if (remainingMs > 0L) {
            if (pendingDispatch == null) {
                val dispatch = Runnable {
                    pendingDispatch = null
                    dispatchPendingPayload()
                }
                pendingDispatch = dispatch
                mainHandler.postDelayed(dispatch, remainingMs)
            }
            return
        }

        pendingDispatch?.let(mainHandler::removeCallbacks)
        pendingDispatch = null
        pendingPayload = null
        lastDispatchElapsedMs = nowMs

        ensureChannel()
        val openAppIntent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.let {
                PendingIntent.getActivity(
                    appContext,
                    NOTIFICATION_ID,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_flyme_ticker)
            // Android 16 uses the title as the prominent Live Update text. Keep the lyric here;
            // the track identity is useful as secondary context but must not replace the lyric.
            .setContentTitle(payload.lyric)
            .setContentText(payload.secondaryText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(payload.lyric)
                    .setSummaryText(payload.secondaryText)
            )
            .apply {
                payload.artwork?.let(::setLargeIcon)
            }
            .setContentIntent(openAppIntent)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)
            .setAutoCancel(false)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Android 16's compact status-bar chip does not use contentTitle/contentText. Its
            // dedicated short critical text is the only reliable way to put the current lyric
            // segment in the chip instead of the track title or an elapsed-time fallback.
            .setShortCriticalText(payload.compactLyric)
            // Android 16 promotes this standard, ongoing notification when the user allows it.
            // Older releases ignore the extra and still receive a normal lyric notification.
            .setRequestPromotedOngoing(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            lastPayload = payload
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Unable to post Live lyric notification", securityException)
        }
    }

    fun clear() {
        cancelPendingDispatch()
        lastPayload = null
        lastDispatchElapsedMs = 0L
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun cancelPendingDispatch() {
        pendingDispatch?.let(mainHandler::removeCallbacks)
        pendingDispatch = null
        pendingPayload = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.notification_channel_live_lyrics),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = appContext.getString(R.string.notification_channel_live_lyrics_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }
}
