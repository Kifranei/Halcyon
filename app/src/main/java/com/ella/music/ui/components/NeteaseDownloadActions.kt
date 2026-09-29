package com.ella.music.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberNeteaseMvId(song: Song?): String {
    val context = LocalContext.current
    val id by produceState("", song?.path, song?.onlineMvId, song?.dateModified) {
        value = song?.let {
            if (it.onlineSource == "netease") it.onlineMvId
            else withContext(Dispatchers.IO) {
                decodeNeteaseKey(MusicRepository.getInstance(context).getSongTagInfo(it).neteaseKey)?.mvId.orEmpty()
            }
        }.orEmpty().takeIf { (it.toLongOrNull() ?: 0L) > 0 }.orEmpty()
    }
    return id
}
