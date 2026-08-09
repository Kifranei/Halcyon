package com.ella.music.player

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.ella.music.data.model.LyricLine

class DesktopLyricBridge(private val context: Context) {
    private companion object {
        // DesktopComposeLyricView extrapolates from the timestamp at the display refresh rate.
        // Sending a startService intent at 20 Hz only restarts that interpolation continuously,
        // costs battery, and can eventually starve other external lyric publishers.
        const val POSITION_ANCHOR_INTERVAL_MS = 250L
        const val TAG = "DesktopLyricBridge"

        /**
         * The media notification is owned by PlaybackService, while the live lyric snapshot is
         * normally produced by PlayerViewModel. Keep the last complete service payload shared
         * between the two so enabling lyrics from a notification can show immediately even if
         * there is no visible Compose player to trigger another update.
         */
        @Volatile
        private var latestLyricPayload: Intent? = null
    }
    private var enabled = false
    private var lastLineKey: String? = null
    private var hostPage = DesktopLyricService.HOST_PAGE_NONE

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (enabled) {
            if (!canDrawOverlay()) {
                Log.w(TAG, "Cannot enable desktop lyrics: overlay permission is missing")
                return
            }
            context.startService(Intent(context, DesktopLyricService::class.java).setAction(DesktopLyricService.ACTION_ENABLE))
            sendHostPage()
            dispatchLatestLyric()
        } else {
            lastLineKey = null
            context.startService(Intent(context, DesktopLyricService::class.java).setAction(DesktopLyricService.ACTION_HIDE))
        }
    }

    fun unlock() {
        if (!canDrawOverlay()) return
        context.startService(
            Intent(context, DesktopLyricService::class.java)
                .setAction(DesktopLyricService.ACTION_UNLOCK)
        )
    }

    fun isEnabled(): Boolean = enabled

    fun setHostPage(page: Int) {
        if (hostPage == page) return
        hostPage = page
        if (enabled) sendHostPage()
    }

    fun sendLyric(text: String?) {
        val lyric = text?.takeIf { it.isNotBlank() } ?: return
        val payload = Intent(context, DesktopLyricService::class.java)
            .setAction(DesktopLyricService.ACTION_SHOW)
            .putExtra(DesktopLyricService.EXTRA_TEXT, lyric)
        cacheLatestLyric(payload)
        if (!enabled || !canDrawOverlay()) return
        if (lyric == lastLineKey) return
        lastLineKey = lyric
        dispatchLyric(payload)
    }

    fun sendLyric(
        line: LyricLine?,
        positionMs: Long,
        showTranslation: Boolean,
        showPronunciation: Boolean
    ) {
        val lyricLine = line ?: return
        val payload = Intent(context, DesktopLyricService::class.java)
            .setAction(DesktopLyricService.ACTION_UPDATE)
            .putExtra(DesktopLyricService.EXTRA_TEXT, lyricLine.text)
            .putExtra(DesktopLyricService.EXTRA_PRONUNCIATION, if (showPronunciation) lyricLine.pronunciation.orEmpty() else "")
            .putExtra(DesktopLyricService.EXTRA_TRANSLATION, if (showTranslation) lyricLine.translation.orEmpty() else "")
            .putExtra(DesktopLyricService.EXTRA_POSITION, positionMs)
            .putExtra(DesktopLyricService.EXTRA_LINE_START, lyricLine.timeMs)
            .putExtra(DesktopLyricService.EXTRA_LINE_END, lyricLine.endMs ?: -1L)
            .putExtra(DesktopLyricService.EXTRA_AGENT, lyricLine.agent.orEmpty())
            .putExtra(DesktopLyricService.EXTRA_IS_TTML, lyricLine.isTtml)
            .putExtra(DesktopLyricService.EXTRA_BACKGROUND_TEXT, lyricLine.backgroundText.orEmpty())
            .putExtra(DesktopLyricService.EXTRA_BACKGROUND_TRANSLATION, if (showTranslation) lyricLine.backgroundTranslation.orEmpty() else "")
            .putExtra(DesktopLyricService.EXTRA_BACKGROUND_START, lyricLine.backgroundStartMs ?: -1L)
            .putExtra(DesktopLyricService.EXTRA_BACKGROUND_END, lyricLine.backgroundEndMs ?: -1L)
            .putExtra(DesktopLyricService.EXTRA_WORD_TEXTS, lyricLine.words.map { it.text }.toTypedArray())
            .putExtra(DesktopLyricService.EXTRA_WORD_STARTS, lyricLine.words.map { it.startMs }.toLongArray())
            .putExtra(DesktopLyricService.EXTRA_WORD_ENDS, lyricLine.words.map { it.endMs }.toLongArray())
            .putExtra(DesktopLyricService.EXTRA_PRONUNCIATION_WORD_TEXTS, if (showPronunciation) lyricLine.pronunciationWords.map { it.text }.toTypedArray() else emptyArray())
            .putExtra(DesktopLyricService.EXTRA_PRONUNCIATION_WORD_STARTS, if (showPronunciation) lyricLine.pronunciationWords.map { it.startMs }.toLongArray() else LongArray(0))
            .putExtra(DesktopLyricService.EXTRA_PRONUNCIATION_WORD_ENDS, if (showPronunciation) lyricLine.pronunciationWords.map { it.endMs }.toLongArray() else LongArray(0))
            .putExtra(DesktopLyricService.EXTRA_BACKGROUND_WORD_TEXTS, lyricLine.backgroundWords.map { it.text }.toTypedArray())
            .putExtra(DesktopLyricService.EXTRA_BACKGROUND_WORD_STARTS, lyricLine.backgroundWords.map { it.startMs }.toLongArray())
            .putExtra(DesktopLyricService.EXTRA_BACKGROUND_WORD_ENDS, lyricLine.backgroundWords.map { it.endMs }.toLongArray())
        cacheLatestLyric(payload)
        if (!enabled || !canDrawOverlay()) return
        val key = "${lyricLine.timeMs}:${positionMs / POSITION_ANCHOR_INTERVAL_MS}:$showTranslation:$showPronunciation"
        if (key == lastLineKey) return
        lastLineKey = key
        dispatchLyric(payload)
    }

    fun clearLyric() {
        lastLineKey = null
        latestLyricPayload = null
        context.startService(Intent(context, DesktopLyricService::class.java).setAction(DesktopLyricService.ACTION_HIDE))
    }

    fun applySettings() {
        lastLineKey = null
        if (!enabled || !canDrawOverlay()) return
        context.startService(
            Intent(context, DesktopLyricService::class.java)
                .setAction(DesktopLyricService.ACTION_APPLY_SETTINGS)
        )
    }

    private fun canDrawOverlay(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    private fun sendHostPage() {
        if (!enabled || !canDrawOverlay()) return
        context.startService(
            Intent(context, DesktopLyricService::class.java)
                .setAction(DesktopLyricService.ACTION_SET_HOST_PAGE)
                .putExtra(DesktopLyricService.EXTRA_HOST_PAGE, hostPage)
        )
    }

    private fun cacheLatestLyric(payload: Intent) {
        latestLyricPayload = Intent(payload)
    }

    private fun dispatchLatestLyric() {
        val payload = latestLyricPayload
        if (payload == null) {
            Log.w(TAG, "Desktop lyric enabled but no lyric payload is cached yet")
            return
        }
        dispatchLyric(payload)
    }

    private fun dispatchLyric(payload: Intent) {
        context.startService(
            Intent(payload)
                .putExtra(DesktopLyricService.EXTRA_HOST_PAGE, hostPage)
        )
    }
}
