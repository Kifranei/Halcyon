package com.ella.music.player

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.ella.music.data.SettingsManager

@OptIn(UnstableApi::class)
internal class RepeatOneLockingPlayer(
    player: Player,
    private val previousButtonActionProvider: () -> Int,
    private val onExternalPlaybackChanged: () -> Unit
) : ForwardingPlayer(player) {
    override fun seekToNextMediaItem() {
        Log.d(PlaybackService.TIMING_TAG, "skipNext command received mediaId=${currentMediaItem?.mediaId}")
        if (!seekAdjacentMediaItemInRepeatOne(1)) {
            Log.d(PlaybackService.TIMING_TAG, "seekToNext called")
            super.seekToNextMediaItem()
            onExternalPlaybackChanged()
        }
    }

    override fun seekToNext() {
        // Some lock-screen and Dynamic Island implementations send the semantic SEEK_TO_NEXT
        // command while others send SEEK_TO_NEXT_MEDIA_ITEM. Keep both paths identical so the
        // queue and the published media-session metadata cannot drift apart.
        seekToNextMediaItem()
    }

    override fun seekToPreviousMediaItem() {
        Log.d(PlaybackService.TIMING_TAG, "skipPrevious command received mediaId=${currentMediaItem?.mediaId}")
        if (!restartCurrentFromPreviousButton() && !seekAdjacentMediaItemInRepeatOne(-1)) {
            Log.d(PlaybackService.TIMING_TAG, "seekToPrevious called")
            super.seekToPreviousMediaItem()
            onExternalPlaybackChanged()
        }
    }

    override fun seekToPrevious() {
        // Player.seekToPrevious() normally restarts the current item once playback has passed
        // Media3's threshold. Third-party lock-screen/Dynamic Island controllers commonly send
        // that command even when their button is visually a "previous track" button. Route it
        // through our explicit preference-aware media-item implementation instead.
        seekToPreviousMediaItem()
    }

    private fun restartCurrentFromPreviousButton(): Boolean {
        if (previousButtonActionProvider() != SettingsManager.PREVIOUS_BUTTON_REPLAY_CURRENT) return false
        if (currentPosition < SettingsManager.PREVIOUS_REPLAY_THRESHOLD_MS) return false
        val index = currentMediaItemIndex
        if (mediaItemCount <= 0 || index !in 0 until mediaItemCount) return false
        seekToDefaultPosition(index)
        play()
        onExternalPlaybackChanged()
        return true
    }

    private fun seekAdjacentMediaItemInRepeatOne(offset: Int): Boolean {
        if (repeatMode != Player.REPEAT_MODE_ONE) return false
        val index = currentMediaItemIndex
        if (mediaItemCount <= 0 || index !in 0 until mediaItemCount) return false
        val targetIndex = if (mediaItemCount == 1) {
            index
        } else {
            Math.floorMod(index + offset, mediaItemCount)
        }
        seekToDefaultPosition(targetIndex)
        play()
        onExternalPlaybackChanged()
        return true
    }
}
