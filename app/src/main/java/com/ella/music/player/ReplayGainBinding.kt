package com.ella.music.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.ella.music.data.SettingsManager
import com.ella.music.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

internal fun replayGainMultiplier(tagDb: Float?, preampDb: Float): Float {
    val tag = tagDb?.takeIf(Float::isFinite) ?: 0f
    val preamp = preampDb.takeIf(Float::isFinite)?.coerceIn(-30f, 30f) ?: 0f
    return 10f.pow((tag + preamp).coerceIn(-60f, 60f) / 20f)
}

/** Gain follows the decoder's actual item, including the independently preloaded incoming song. */
@UnstableApi
internal fun bindPlayerReplayGain(
    player: ExoPlayer,
    processor: CrossfadeGainAudioProcessor,
    settings: SettingsManager,
    repository: MusicRepository,
    scope: CoroutineScope
) {
    bindReplayGain(player, settings, repository, scope) { processor.replayGain = it }
}

/** Remote outputs do not pass through the local PCM processors. Keep their gain independent. */
@UnstableApi
internal fun bindRemotePlayerReplayGain(
    player: Player,
    settings: SettingsManager,
    repository: MusicRepository,
    scope: CoroutineScope
) {
    bindReplayGain(player, settings, repository, scope) { multiplier ->
        val volume = multiplier.coerceIn(0f, 1f)
        if (player.volume != volume) player.volume = volume
    }
}

private fun bindReplayGain(
    player: Player,
    settings: SettingsManager,
    repository: MusicRepository,
    scope: CoroutineScope,
    applyGain: (Float) -> Unit
) {
    val item = MutableStateFlow(player.currentMediaItem)
    player.addListener(object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { item.value = mediaItem }
    })
    scope.launch {
        combine(settings.replayGainMode, settings.replayGainPreampDb, item) { mode, preamp, media ->
            Triple(mode, preamp, media?.toSongFromMediaItemExtras())
        }.collectLatest { (mode, preamp, song) ->
            if (mode == SettingsManager.REPLAY_GAIN_OFF || song == null) {
                applyGain(1f)
            } else {
                applyGain(replayGainMultiplier(repository.getCachedReplayGain(song, mode), preamp))
                val tag = withContext(Dispatchers.IO) { repository.getReplayGain(song, mode) }
                applyGain(replayGainMultiplier(tag, preamp))
            }
        }
    }
}
