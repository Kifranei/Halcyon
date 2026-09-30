package com.ella.music.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberNeteaseSongLinks(song: Song?): NeteaseSongLinks {
    val context = LocalContext.current
    val links by produceState(NeteaseSongLinks(), song?.path, song?.onlineId, song?.onlineMvId, song?.dateModified) {
        value = song?.let {
            if (it.onlineSource == "netease") NeteaseSongLinks(it.onlineId, it.onlineMvId)
            else withContext(Dispatchers.IO) {
                val key = decodeNeteaseKey(MusicRepository.getInstance(context).getSongTagInfo(it).neteaseKey)
                NeteaseSongLinks(key?.musicId.orEmpty(), key?.mvId.orEmpty())
            }
        }?.let { links ->
            NeteaseSongLinks(
                links.songId.takeIf { (it.toLongOrNull() ?: 0L) > 0 }.orEmpty(),
                links.mvId.takeIf { (it.toLongOrNull() ?: 0L) > 0 }.orEmpty()
            )
        } ?: NeteaseSongLinks()
    }
    return links
}

internal data class NeteaseSongLinks(val songId: String = "", val mvId: String = "")

@Composable
internal fun rememberNeteaseMvId(song: Song?): String = rememberNeteaseSongLinks(song).mvId
