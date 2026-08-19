package com.ella.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ella.music.data.SettingsManager
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.neteaseMvUrl
import com.ella.music.ui.player.DynamicCoverSource
import com.ella.music.ui.player.musicVideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal data class SongListVideoActions(
    val localSource: DynamicCoverSource? = null,
    val onlineUrl: String? = null
)

/** MV folder and tag probing is intentionally tightly bounded across every song-list surface. */
private val SongListVideoActionLimiter = Semaphore(2)

@Composable
internal fun rememberSongListVideoActions(
    song: Song,
    loadSongTagInfo: ((Song) -> SongTagInfo)?,
    enabled: Boolean
): SongListVideoActions {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val showLocal by settingsManager.showLocalMusicVideoInLists.collectAsState(initial = true)
    val showOnline by settingsManager.showOnlineMusicVideoInLists.collectAsState(initial = true)
    val dynamicFolders by settingsManager.dynamicCoverCustomFolders.collectAsState(initial = emptyList())
    val videoFolders by settingsManager.musicVideoCustomFolders.collectAsState(initial = emptyList())
    val actions by produceState(
        initialValue = SongListVideoActions(),
        song.id,
        song.path,
        song.dateModified,
        song.fileSize,
        dynamicFolders,
        videoFolders,
        showLocal,
        showOnline,
        loadSongTagInfo,
        enabled
    ) {
        if (!enabled || (!showLocal && !showOnline)) {
            value = SongListVideoActions()
            return@produceState
        }
        value = SongListVideoActionLimiter.withPermit {
            withContext(Dispatchers.IO) {
                val localSource = if (showLocal) {
                    song.musicVideoSource(
                        context = context,
                        customRootPaths = dynamicFolders,
                        musicVideoCustomFolders = videoFolders
                    )
                } else null
                val onlineUrl = if (localSource == null && showOnline) {
                    decodeNeteaseKey(loadSongTagInfo?.invoke(song)?.neteaseKey.orEmpty())
                        ?.mvId
                        ?.takeIf { it.toLongOrNull()?.let { id -> id > 0L } == true }
                        ?.let(::neteaseMvUrl)
                } else null
                SongListVideoActions(localSource, onlineUrl)
            }
        }
    }
    return actions
}
