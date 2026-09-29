package com.ella.music.ui.analytics

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.artist.ArtistMusicVideo
import com.ella.music.ui.artist.ArtistMusicVideoInfoSheet
import com.ella.music.ui.artist.ArtistMusicVideoMetadata
import com.ella.music.ui.artist.readArtistMusicVideoMetadata
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.openVideoWithMediaInfo
import com.ella.music.ui.player.DynamicCoverSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun RecentMusicVideoInfo(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val source = DynamicCoverSource(Uri.parse(song.path), song.path)
    val metadata by produceState(ArtistMusicVideoMetadata(), song.path) {
        value = withContext(Dispatchers.IO) { readArtistMusicVideoMetadata(context, source) }
    }
    EllaMiuixBottomSheet(show = true, title = stringResource(R.string.artist_music_video_info), onDismissRequest = onDismiss) {
        ArtistMusicVideoInfoSheet(
            item = ArtistMusicVideo(song, source, song.duration, metadata.preview, metadata),
            onOpenMediaInfo = {
                onDismiss()
                openVideoWithMediaInfo(context, source.uri, metadata.fileName, metadata.mimeType)
            },
            onDismiss = onDismiss
        )
    }
}
