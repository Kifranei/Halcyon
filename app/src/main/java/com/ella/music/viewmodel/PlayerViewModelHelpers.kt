package com.ella.music.viewmodel

import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.player.LyriconBridge
import com.ella.music.player.SuperLyricBridge
import kotlin.math.pow

internal fun Float?.toReplayGainVolume(): Float {
    val gainDb = this?.coerceIn(-24f, 0f) ?: return 1f
    return 10f.pow(gainDb / 20f).coerceIn(0.05f, 1f)
}

internal fun replayGainPrefetchSongs(
    playlist: List<Song>,
    currentSong: Song,
    count: Int
): List<Song> {
    if (playlist.isEmpty() || count <= 0) return emptyList()
    val currentKey = currentSong.playlistIdentityKey()
    val currentIndex = playlist.indexOfFirst { it.playlistIdentityKey() == currentKey }
    if (currentIndex < 0) return emptyList()
    return (1..count.coerceAtMost(playlist.lastIndex))
        .map { offset -> playlist[(currentIndex + offset) % playlist.size] }
        .filterNot { it.playlistIdentityKey() == currentKey }
        .distinctBy { it.playlistIdentityKey() }
}

internal fun lyriconSecondaryMode(
    translationEnabled: Boolean,
    pronunciationEnabled: Boolean
): LyriconBridge.SecondaryMode = when {
    pronunciationEnabled -> LyriconBridge.SecondaryMode.Pronunciation
    translationEnabled -> LyriconBridge.SecondaryMode.Translation
    else -> LyriconBridge.SecondaryMode.Off
}

internal fun superLyricSecondaryMode(
    translationEnabled: Boolean,
    pronunciationEnabled: Boolean
): SuperLyricBridge.SecondaryMode = when {
    pronunciationEnabled -> SuperLyricBridge.SecondaryMode.Pronunciation
    translationEnabled -> SuperLyricBridge.SecondaryMode.Translation
    else -> SuperLyricBridge.SecondaryMode.Off
}

internal fun shouldReplayFromPreviousButton(
    manualSeekAfterPreviousButton: Boolean,
    previousButtonAction: Int,
    currentPositionMs: Long
): Boolean =
    !manualSeekAfterPreviousButton &&
        previousButtonAction == SettingsManager.PREVIOUS_BUTTON_REPLAY_CURRENT &&
        currentPositionMs >= SettingsManager.PREVIOUS_REPLAY_THRESHOLD_MS
