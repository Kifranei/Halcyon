package com.ella.music.player

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.ella.music.data.model.LyricLine

class DesktopLyricBridge(private val context: Context) {
    private companion object {
        // DesktopComposeLyricView extrapolates from the timestamp at the display refresh rate.
        // Sending a startService intent at 20 Hz only restarts that interpolation continuously,
        // costs battery, and can eventually starve other external lyric publishers.
        const val POSITION_ANCHOR_INTERVAL_MS = 250L
    }
    private var enabled = false
    private var lastLineKey: String? = null
    private var hostPage = DesktopLyricService.HOST_PAGE_NONE

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (enabled) {
            context.startService(Intent(context, DesktopLyricService::class.java).setAction(DesktopLyricService.ACTION_ENABLE))
            sendHostPage()
        } else {
            lastLineKey = null
            context.startService(Intent(context, DesktopLyricService::class.java).setAction(DesktopLyricService.ACTION_HIDE))
        }
    }

    fun isEnabled(): Boolean = enabled

    fun setHostPage(page: Int) {
        if (hostPage == page) return
        hostPage = page
        if (enabled) sendHostPage()
    }

    fun sendLyric(text: String?) {
        if (!enabled || !canDrawOverlay()) return
        val lyric = text?.takeIf { it.isNotBlank() } ?: return
        if (lyric == lastLineKey) return
        lastLineKey = lyric
        context.startService(
            Intent(context, DesktopLyricService::class.java)
                .setAction(DesktopLyricService.ACTION_SHOW)
                .putExtra(DesktopLyricService.EXTRA_TEXT, lyric)
                .putExtra(DesktopLyricService.EXTRA_HOST_PAGE, hostPage)
        )
    }

    fun sendLyric(
        line: LyricLine?,
        positionMs: Long,
        showTranslation: Boolean,
        showPronunciation: Boolean
    ) {
        if (!enabled || !canDrawOverlay()) return
        val lyricLine = line ?: return
        val key = "${lyricLine.timeMs}:${positionMs / POSITION_ANCHOR_INTERVAL_MS}:$showTranslation:$showPronunciation"
        if (key == lastLineKey) return
        lastLineKey = key
        context.startService(
            Intent(context, DesktopLyricService::class.java)
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
                .putExtra(DesktopLyricService.EXTRA_HOST_PAGE, hostPage)
        )
    }

    fun clearLyric() {
        lastLineKey = null
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
}
