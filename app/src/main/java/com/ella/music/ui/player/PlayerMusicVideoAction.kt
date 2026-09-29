package com.ella.music.ui.player

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ella.music.MusicVideoLauncher
import com.ella.music.data.SettingsManager
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** MV reachable from the player "查看 MV" action: a local MV file wins over a NetEase MV id. */
@Immutable
internal data class PlayerMusicVideoTarget(
    val localSource: DynamicCoverSource? = null,
    val neteaseMvId: String = ""
) {
    val available: Boolean get() = localSource != null || neteaseMvId.isNotBlank()

    fun open(context: Context, song: Song?) {
        val local = localSource
        when {
            local != null -> MusicVideoLauncher.open(context, song, local)
            neteaseMvId.isNotBlank() -> MusicVideoLauncher.openNetease(context, song, neteaseMvId)
        }
    }
}

/**
 * Resolves the current song's MV off the main thread. [knownNeteaseMvId] lets callers that
 * already decoded the 163 key skip the tag read. Nothing is probed while [enabled] is false.
 */
@Composable
internal fun rememberPlayerMusicVideoTarget(
    song: Song?,
    enabled: Boolean = true,
    knownNeteaseMvId: String? = null
): PlayerMusicVideoTarget {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val dynamicFolders by settingsManager.dynamicCoverCustomFolders.collectAsState(initial = emptyList())
    val videoFolders by settingsManager.musicVideoCustomFolders.collectAsState(initial = emptyList())
    val target by produceState(
        initialValue = PlayerMusicVideoTarget(),
        song?.dynamicCoverResolutionKey(),
        song?.onlineMvId,
        dynamicFolders,
        videoFolders,
        enabled,
        knownNeteaseMvId
    ) {
        // Clear first so the previous song's MV never lingers while the next lookup runs.
        value = PlayerMusicVideoTarget()
        val current = song
        if (!enabled || current == null) return@produceState
        value = withContext(Dispatchers.IO) {
            val localSource = runCatching {
                current.musicVideoSource(
                    context = context,
                    customRootPaths = dynamicFolders,
                    musicVideoCustomFolders = videoFolders
                )
            }.getOrNull()
            val mvId = if (localSource != null) {
                ""
            } else {
                (knownNeteaseMvId ?: if (current.onlineSource == "netease") {
                    current.onlineMvId
                } else {
                    runCatching {
                        decodeNeteaseKey(MusicRepository.getInstance(context).getSongTagInfo(current).neteaseKey)?.mvId
                    }.getOrNull().orEmpty()
                }).takeIf { (it.toLongOrNull() ?: 0L) > 0L }.orEmpty()
            }
            PlayerMusicVideoTarget(localSource, mvId)
        }
    }
    return target
}
