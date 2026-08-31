package com.ella.music.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.ella.music.data.NeteaseKeyInfo
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.isHttpAudioSource
import com.ella.music.data.isMediaStoreAlbumArtworkUri
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.components.CoverLoadLimiter
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PlayerSongPresentationState(
    val embeddedCover: Bitmap?,
    val paletteBitmap: Bitmap?,
    val palette: PlayerPalette,
    val lyricPalette: PlayerPalette,
    val audioInfo: AudioInfo?,
    val tagInfo: SongTagInfo?,
    val annotation: String,
    val neteaseInfo: NeteaseKeyInfo?
)

@Composable
internal fun rememberPlayerSongPresentationState(
    context: Context,
    song: Song?,
    playerViewModel: PlayerViewModel,
    playerLight: Boolean = false
): PlayerSongPresentationState {
    val paletteDefault = if (playerLight) PlayerPalette.LightDefault else PlayerPalette.Default
    val songKey = remember(song) { song?.presentationIdentityKey() }
    val artworkGeneration by com.ella.music.ui.components.artworkResolutionGeneration.collectAsState()
    // A local file may still carry a Media3/MediaStore artwork URI in coverUrl. That URI is only
    // an album-level hint and is not reliable on vendor providers; always give the local file's
    // embedded/sidecar artwork a chance before using that hint as a player fallback.
    val shouldResolveLocalArtwork = song?.let {
        it.onlineSource.isBlank() && !it.path.isHttpAudioSource()
    } == true
    val embeddedCover by produceState<Bitmap?>(
        initialValue = null,
        songKey,
        shouldResolveLocalArtwork,
        artworkGeneration
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                CoverLoadLimiter.run {
                    song?.takeIf {
                        shouldResolveLocalArtwork ||
                            it.coverUrl.isBlank() ||
                            it.coverUrl.isMediaStoreAlbumArtworkUri()
                    }?.let(playerViewModel::getCoverArtBitmap)
                }
            }.getOrNull()
        }
    }
    val paletteBitmap by produceState<Bitmap?>(initialValue = null, songKey, embeddedCover) {
        value = withContext(Dispatchers.IO) {
            embeddedCover ?: song?.let { loadPaletteCoverBitmap(context, it) }
        }
    }
    val palettePair by produceState(
        initialValue = paletteDefault to paletteDefault,
        paletteBitmap,
        playerLight
    ) {
        value = withContext(Dispatchers.Default) { PlayerPalette.pairFrom(paletteBitmap, playerLight) }
    }
    val audioInfo by produceState<AudioInfo?>(initialValue = null, songKey) {
        value = withContext(Dispatchers.IO) { song?.let(playerViewModel::getAudioInfo) }
    }
    val tagInfo by produceState<SongTagInfo?>(initialValue = null, songKey) {
        value = withContext(Dispatchers.IO) { song?.let(playerViewModel::getSongTagInfo) }
    }
    val neteaseInfo = remember(tagInfo?.neteaseKey) { decodeNeteaseKey(tagInfo?.neteaseKey.orEmpty()) }
    val annotation = remember(tagInfo?.displayComment, neteaseInfo?.aliases) {
        neteaseInfo
            ?.aliases
            .orEmpty()
            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .distinct()
            .joinToString(" · ")
            .ifBlank { tagInfo?.displayComment.orEmpty() }
    }

    return PlayerSongPresentationState(
        embeddedCover = embeddedCover,
        paletteBitmap = paletteBitmap,
        palette = palettePair.first,
        lyricPalette = palettePair.second,
        audioInfo = audioInfo,
        tagInfo = tagInfo,
        annotation = annotation,
        neteaseInfo = neteaseInfo
    )
}

private fun Song.presentationIdentityKey(): String =
    listOf(
        playlistIdentityKey(),
        id,
        albumId,
        coverUrl,
        dateModified,
        fileSize,
        title,
        artist,
        album,
        duration
    ).joinToString("|")
