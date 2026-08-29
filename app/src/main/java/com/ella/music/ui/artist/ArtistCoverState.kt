package com.ella.music.ui.artist

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.ella.music.data.ArtistCoverAsset
import com.ella.music.data.ArtistImageRepository
import com.ella.music.data.SettingsManager
import com.ella.music.data.lastfm.DEFAULT_LAST_FM_WIKI_REGION
import com.ella.music.data.lastfm.LastFmSecureStore
import com.ella.music.data.lastfm.isWifiConnected
import com.ella.music.data.model.Song
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.rememberSongArtworkState
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberArtistCoverUri(
    artistName: String,
    folderLocation: String,
    mainViewModel: MainViewModel
): Uri? {
    val state by produceState<Uri?>(
        initialValue = null,
        artistName,
        folderLocation
    ) {
        value = if (artistName.isBlank() || folderLocation.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                mainViewModel.getArtistCoverUri(artistName, folderLocation)
            }
        }
    }
    return state
}

/**
 * Resolves every artist image through the same chain: a custom artist asset first, then the
 * caller's policy-selected library song artwork. Keeping the custom-asset check here prevents
 * search cards, detail metadata and the artist list from drifting apart again.
 */
@Composable
internal fun rememberArtistCoverModel(
    artistName: String,
    representativeSong: Song?,
    folderLocation: String,
    mainViewModel: MainViewModel,
    coversEnabled: Boolean = true
): Any? {
    if (!coversEnabled) return null
    val context = LocalContext.current
    val settingsManager = mainViewModel.settingsManager
    val artistImageDownload by settingsManager.artistImageDownload.collectAsState(
        initial = SettingsManager.DEFAULT_ARTIST_IMAGE_DOWNLOAD
    )
    val artistImageSourceOrder by settingsManager.artistImageSourceOrder.collectAsState(
        initial = SettingsManager.DEFAULT_ARTIST_IMAGE_SOURCES
    )
    val lastFmCredentials by LastFmSecureStore.getInstance(context).credentials.collectAsState()
    val lastFmRegion by settingsManager.artistBioLastFmLang.collectAsState(
        initial = DEFAULT_LAST_FM_WIKI_REGION
    )
    val spotifyClientId by settingsManager.spotifyClientId.collectAsState(initial = "")
    val spotifyClientSecret by settingsManager.spotifyClientSecret.collectAsState(initial = "")
    val networkDownloadAllowed = when (artistImageDownload) {
        SettingsManager.ARTIST_IMAGE_DOWNLOAD_ALWAYS -> true
        SettingsManager.ARTIST_IMAGE_DOWNLOAD_WIFI -> isWifiConnected(context)
        else -> false
    }
    val albumArtUri = remember(coversEnabled, representativeSong?.albumId) {
        representativeSong
            ?.albumId
            ?.takeIf { coversEnabled && it > 0L }
            ?.let(mainViewModel::getAlbumArtUri)
    }
    val artworkState = rememberSongArtworkState(
        song = representativeSong,
        albumArtUri = albumArtUri,
        loadCoverArt = mainViewModel::getAlbumCoverArtBitmap,
        usage = ArtworkUsage.ArtistImage,
        showDefaultWhenMissing = false
    )
    val customArtistCoverUri = rememberArtistCoverUri(
        artistName = artistName,
        folderLocation = if (coversEnabled) folderLocation else "",
        mainViewModel = mainViewModel
    )
    val downloadedArtistCoverUri by produceState<Uri?>(
        initialValue = null,
        artistName,
        artistImageDownload,
        artistImageSourceOrder,
        networkDownloadAllowed,
        lastFmCredentials.apiKey,
        lastFmRegion,
        spotifyClientId,
        spotifyClientSecret
    ) {
        value = if (!networkDownloadAllowed) {
            null
        } else {
            ArtistImageRepository.resolve(
                context = context.applicationContext,
                artistName = artistName,
                sourceOrder = artistImageSourceOrder,
                lastFmApiKey = lastFmCredentials.apiKey,
                lastFmRegion = lastFmRegion,
                spotifyClientId = spotifyClientId,
                spotifyClientSecret = spotifyClientSecret
            )
        }
    }
    // The order mirrors issue #567: local artist assets, downloaded artist art, then the
    // representative song's carefully ranked embedded/album artwork.
    return customArtistCoverUri ?: downloadedArtistCoverUri ?: artworkState.model
}

@Composable
internal fun rememberArtistCoverAsset(
    artistName: String,
    folderLocation: String,
    mainViewModel: MainViewModel
): ArtistCoverAsset? {
    val state by produceState<ArtistCoverAsset?>(
        initialValue = null,
        artistName,
        folderLocation
    ) {
        value = if (artistName.isBlank() || folderLocation.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                mainViewModel.getArtistCoverAsset(artistName, folderLocation)
            }
        }
    }
    return state
}

@Composable
internal fun rememberArtistCoverAssets(
    artistName: String,
    folderLocation: String,
    mainViewModel: MainViewModel
): List<ArtistCoverAsset> {
    val state by produceState<List<ArtistCoverAsset>>(
        initialValue = emptyList(),
        artistName,
        folderLocation
    ) {
        value = if (artistName.isBlank() || folderLocation.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                mainViewModel.getArtistCoverAssets(artistName, folderLocation)
            }
        }
    }
    return state
}
