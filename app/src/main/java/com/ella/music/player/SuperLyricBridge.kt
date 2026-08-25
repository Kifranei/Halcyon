package com.ella.music.player

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import com.hchen.superlyricapi.SuperLyricLine
import com.hchen.superlyricapi.SuperLyricWord

class SuperLyricBridge {
    private var enabled = false
    private var registered = false
    private var secondaryMode = SecondaryMode.Translation
    private var lastKey: String? = null
    private var lastSongKey: String? = null
    private var lastSong: Song? = null
    private var consecutiveFailures = 0
    private var retryAfterElapsedMs = 0L

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        lastKey = null
        lastSongKey = null
        resetRetryState()
        if (enabled) {
            register()
        } else {
            sendStop()
            unregister()
        }
    }

    fun isEnabled(): Boolean = enabled

    fun setSecondaryMode(mode: SecondaryMode) {
        if (secondaryMode == mode) return
        secondaryMode = mode
        lastKey = null
    }

    fun sendSong(song: Song) {
        if (!enabled) return
        val songKey = song.superLyricSongKey()
        if (songKey == lastSongKey) return
        lastSongKey = songKey
        lastKey = null
        lastSong = song
        register()
    }

    fun sendLyric(line: LyricLine?, positionMs: Long, showTranslation: Boolean, force: Boolean = false) {
        if (!enabled || line == null) return
        val song = lastSong
        val secondary = line.secondaryForSuperLyric(showTranslation)
        val pronunciation = line.pronunciation?.takeIf { secondaryMode == SecondaryMode.Pronunciation && it.isNotBlank() }
        val key = "${song?.id}:${line.timeMs}:${line.endMs}:$secondaryMode:$showTranslation:${secondary.orEmpty()}"
        if (force && !registered) lastKey = null
        if (key == lastKey) return
        if (!register()) return
        runCatching {
            val end = line.endMs ?: (line.words.maxOfOrNull { it.endMs } ?: (line.timeMs + 3000L))
            SuperLyricHelper.sendLyric(
                SuperLyricData()
                    .setTitle(song?.title)
                    .setArtist(song?.artist)
                    .setAlbum(song?.album)
                    .setLyric(
                        SuperLyricLine(
                            line.text.ifBlank { line.backgroundText.orEmpty() },
                            line.words.toSuperWords(line.text),
                            line.timeMs,
                            end
                        )
                    )
                    .setSecondary(line.backgroundText?.takeIf { it.isNotBlank() }?.let {
                        SuperLyricLine(it, line.backgroundWords.toSuperWords(it), line.timeMs, end)
                    })
                    .setTranslation(
                        secondary?.takeIf { it.isNotBlank() }?.let {
                            SuperLyricLine(it, line.timeMs, end)
                        }
                    )
                    .setExtra(pronunciation?.let {
                        Bundle().apply {
                            putString("pronunciation", it)
                            putString("phonetic", it)
                        }
                    })
            )
            lastKey = key
            resetRetryState()
        }.onFailure {
            recordFailure("send", it)
        }
    }

    fun sendStop() {
        if (!registered) return
        runCatching {
            SuperLyricHelper.sendStop(
                SuperLyricData()
                    .setTitle(lastSong?.title)
                    .setArtist(lastSong?.artist)
                    .setAlbum(lastSong?.album)
            )
        }
        lastKey = null
    }

    fun destroy() {
        sendStop()
        unregister()
        lastSong = null
        lastKey = null
        lastSongKey = null
        resetRetryState()
    }

    private fun Song.superLyricSongKey(): String =
        listOf(id, title, artist, album, duration, path).joinToString("|")

    private fun LyricLine.secondaryForSuperLyric(showTranslation: Boolean): String? {
        return when (secondaryMode) {
            SecondaryMode.Off -> null
            SecondaryMode.Translation -> {
                if (showTranslation) translation ?: backgroundTranslation else null
            }
            SecondaryMode.Pronunciation -> pronunciation?.takeIf { it.isNotBlank() }
        }
    }

    enum class SecondaryMode {
        Off,
        Translation,
        Pronunciation
    }

    private fun register(): Boolean {
        if (!enabled) return false
        if (registered) return true
        if (SystemClock.elapsedRealtime() < retryAfterElapsedMs) return false

        val available = runCatching { SuperLyricHelper.isAvailable() }
            .getOrElse {
                recordFailure("availability check", it)
                return false
            }
        if (!available) {
            recordFailure("attach")
            return false
        }

        return runCatching {
            SuperLyricHelper.registerPublisher()
            SuperLyricHelper.setSystemPlayStateListenerEnabled(true)
            registered = true
            true
        }.getOrElse {
            recordFailure("register", it)
            false
        }
    }

    private fun unregister() {
        if (!registered) return
        runCatching { SuperLyricHelper.unregisterPublisher() }
        registered = false
    }

    private fun recordFailure(operation: String, cause: Throwable? = null) {
        registered = false
        consecutiveFailures++
        val delayMs = superLyricRetryDelayMs(consecutiveFailures)
        retryAfterElapsedMs = SystemClock.elapsedRealtime() + delayMs
        val reason = cause?.let { " (${it.javaClass.simpleName}: ${it.message.orEmpty()})" }.orEmpty()
        Log.w(TAG, "SuperLyric $operation unavailable; retry in ${delayMs / 1000}s$reason")
    }

    private fun resetRetryState() {
        consecutiveFailures = 0
        retryAfterElapsedMs = 0L
    }

    private fun List<com.ella.music.data.model.LyricWord>.toSuperWords(lineText: String): Array<SuperLyricWord>? {
        if (isEmpty()) return null
        return withLineSpacing(lineText)
            .map { SuperLyricWord(it.text, it.startMs, it.endMs) }
            .toTypedArray()
    }

    private fun List<com.ella.music.data.model.LyricWord>.withLineSpacing(
        lineText: String
    ): List<com.ella.music.data.model.LyricWord> {
        if (isEmpty() || lineText.isBlank() || !lineText.any { it.isWhitespace() }) return this

        val result = mutableListOf<com.ella.music.data.model.LyricWord>()
        var cursor = 0

        forEachIndexed { index, word ->
            val start = lineText.indexOf(word.text, startIndex = cursor)
            if (start < 0) {
                result += word
                return@forEachIndexed
            }

            val end = start + word.text.length
            val nextText = getOrNull(index + 1)?.text
            val nextStart = if (nextText != null) lineText.indexOf(nextText, startIndex = end) else -1
            val suffix = when {
                nextStart > end -> lineText.substring(end, nextStart)
                nextText == null && end < lineText.length -> lineText.substring(end)
                else -> ""
            }
            result += word.copy(text = word.text + suffix)
            cursor = end + suffix.length
        }

        return result.takeIf { it.size == size } ?: this
    }

    private companion object {
        const val TAG = "SuperLyricBridge"
    }
}

internal fun superLyricRetryDelayMs(failureCount: Int): Long {
    val exponent = (failureCount.coerceAtLeast(1) - 1).coerceAtMost(4)
    return (SUPER_LYRIC_INITIAL_RETRY_MS * (1L shl exponent))
        .coerceAtMost(SUPER_LYRIC_MAX_RETRY_MS)
}

private const val SUPER_LYRIC_INITIAL_RETRY_MS = 30_000L
private const val SUPER_LYRIC_MAX_RETRY_MS = 5 * 60_000L
