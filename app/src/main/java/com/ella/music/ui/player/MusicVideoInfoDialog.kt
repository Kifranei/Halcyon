package com.ella.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.ui.artist.ArtistMusicVideoMetadata
import com.ella.music.ui.artist.readArtistMusicVideoMetadata
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.openVideoWithMediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Displays the same metadata surface as the artist MV tab, styled identically to SongInfoSheet.
 */
@Composable
internal fun MusicVideoInfoDialog(
    source: DynamicCoverSource,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val metadata by produceState<ArtistMusicVideoMetadata?>(
        initialValue = null,
        source.failureKey,
        source.uri
    ) {
        value = withContext(Dispatchers.IO) {
            readArtistMusicVideoMetadata(context, source)
        }
    }
    val resolvedMetadata = metadata ?: ArtistMusicVideoMetadata(
        fileName = source.uri.lastPathSegment.orEmpty(),
        path = source.uri.toString(),
        realPath = source.uri.toString(),
        mimeType = "video/*"
    )

    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.artist_music_video_info),
        onDismissRequest = onDismiss
    ) {
        MusicVideoInfoContent(
            title = title,
            metadata = resolvedMetadata,
            onOpenMediaInfo = {
                onDismiss()
                openVideoWithMediaInfo(context, source.uri, resolvedMetadata.fileName.ifBlank { title }, resolvedMetadata.mimeType.ifBlank { "video/*" })
            }
        )
    }
}
