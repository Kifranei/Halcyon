package com.ella.music.ui.player

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.netease.*

/** Native resource comments are separate from the album/MV web destinations. */
@Composable
internal fun NeteaseNativeCommentsHost(currentSong: Song?) {
    val target by NeteaseLinks.commentSheetTarget.collectAsState()
    val request = target ?: return
    val title = stringResource(when (request.resource) {
        NeteaseCommentResource.Song -> R.string.netease_link_song_comments
        NeteaseCommentResource.Album -> R.string.netease_link_album_comments
        NeteaseCommentResource.MusicVideo -> R.string.netease_link_mv_comments
    })
    val header = if (request.resource == NeteaseCommentResource.Song && currentSong?.neteaseCommentSongId() == request.id) {
        currentSong
    } else Song(0L, title, "", "", 0L, 0L, "", "", onlineSource = NETEASE_SOURCE, onlineId = request.id)
    key(request) {
        NeteaseCommentsSheet(true, header, { NeteaseLinks.commentSheetTarget.value = null }, request.id, request.resource)
    }
}
