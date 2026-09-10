package com.ella.music.ui.artist

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.ella.music.data.ArtistCoverAsset
import com.ella.music.data.ArtistCoverKind
import com.ella.music.data.ArtistImageRepository
import com.ella.music.data.ResolvedArtistImage
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
    coversEnabled: Boolean = true,
    includeLibraryArtwork: Boolean = true
): Any? {
    return rememberArtistCoverResolution(
        artistName = artistName,
        representativeSong = representativeSong,
        folderLocation = folderLocation,
        mainViewModel = mainViewModel,
        coversEnabled = coversEnabled,
        includeLibraryArtwork = includeLibraryArtwork
    ).model
}

internal data class ArtistCoverResolution(
    val model: Any?,
    val downloadSource: String? = null
)

@Composable
internal fun rememberArtistCoverResolution(
    artistName: String,
    representativeSong: Song?,
    folderLocation: String,
    mainViewModel: MainViewModel,
    coversEnabled: Boolean = true,
    includeLibraryArtwork: Boolean = true
): ArtistCoverResolution {
    if (!coversEnabled) return ArtistCoverResolution(null)
    val context = LocalContext.current
    val settingsManager = mainViewModel.settingsManager
    val artistImageDownload by settingsManager.artistImageDownload.collectAsState(
        initial = SettingsManager.DEFAULT_ARTIST_IMAGE_DOWNLOAD
    )
    val artistImageSourceOrder by settingsManager.artistImageSourceOrder.collectAsState(
        initial = SettingsManager.DEFAULT_ARTIST_IMAGE_SOURCES
    )
    val lastFmCredentials by LastFmSecureStore.getInstance(context).credentials.collectAsState()
    val lastFmRegion by settingsManager.artistImageRegion.collectAsState(
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
    val artistCoverDownloadFolderUri by settingsManager.artistCoverDownloadFolderUri.collectAsState(initial = "")
    val artworkState = rememberSongArtworkState(
        song = representativeSong,
        albumArtUri = albumArtUri,
        loadCoverArt = mainViewModel::getArtistCoverArtBitmap,
        usage = ArtworkUsage.ArtistImage,
        showDefaultWhenMissing = false
    )
    val customArtistCoverUri = rememberArtistCoverUri(
        artistName = artistName,
        folderLocation = if (coversEnabled) folderLocation else "",
        mainViewModel = mainViewModel
    ) ?: rememberArtistCoverUri(
        artistName = artistName,
        folderLocation = if (coversEnabled && artistCoverDownloadFolderUri.isNotBlank() && artistCoverDownloadFolderUri != folderLocation) artistCoverDownloadFolderUri else "",
        mainViewModel = mainViewModel
    )
    val downloadedArtistCover by produceState<ResolvedArtistImage?>(
        initialValue = null,
        artistName,
        artistImageDownload,
        artistImageSourceOrder,
        networkDownloadAllowed,
        lastFmCredentials.apiKey,
        lastFmRegion,
        spotifyClientId,
        spotifyClientSecret,
        artistCoverDownloadFolderUri,
        folderLocation
    ) {
        value = if (!networkDownloadAllowed) {
            ArtistImageRepository.findCached(
                context = context.applicationContext,
                artistName = artistName,
                sourceOrder = artistImageSourceOrder,
                lastFmRegion = lastFmRegion,
                spotifyClientId = spotifyClientId,
                downloadFolderUri = artistCoverDownloadFolderUri,
                customFolderUri = folderLocation
            )
        } else {
            ArtistImageRepository.resolveDetailed(
                context = context.applicationContext,
                artistName = artistName,
                sourceOrder = artistImageSourceOrder,
                lastFmApiKey = lastFmCredentials.apiKey,
                lastFmRegion = lastFmRegion,
                spotifyClientId = spotifyClientId,
                spotifyClientSecret = spotifyClientSecret,
                downloadFolderUri = artistCoverDownloadFolderUri,
                customFolderUri = folderLocation
            )
        }
    }
    // The order mirrors issue #567: local artist assets, downloaded artist art, then the
    // representative song's carefully ranked embedded/album artwork.
    val libraryArtwork = if (includeLibraryArtwork) artworkState.model else null
    return ArtistCoverResolution(
        model = customArtistCoverUri ?: downloadedArtistCover?.uri ?: libraryArtwork,
        downloadSource = downloadedArtistCover?.source?.takeIf { customArtistCoverUri == null }
    )
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
    mainViewModel: MainViewModel,
    songs: List<Song> = emptyList()
): List<ArtistCoverAsset> {
    val artistCoverDownloadFolderUri by mainViewModel.settingsManager.artistCoverDownloadFolderUri.collectAsState(initial = "")
    val dynamicCoverCustomFolders by mainViewModel.settingsManager.dynamicCoverCustomFolders.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val state by produceState<List<ArtistCoverAsset>>(
        initialValue = emptyList(),
        artistName,
        folderLocation,
        artistCoverDownloadFolderUri,
        dynamicCoverCustomFolders,
        songs.size
    ) {
        value = if (artistName.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                val collected = mutableListOf<ArtistCoverAsset>()
                val ignoreCase = com.ella.music.data.NameSplitConfigStore.tagIgnoreCase
                val safeArtistKey = com.ella.music.data.normalizeArtistCoverKey(artistName, ignoreCase)

                // 1. Primary custom folder
                if (folderLocation.isNotBlank()) {
                    collected.addAll(mainViewModel.getArtistCoverAssets(artistName, folderLocation))
                }
                // 2. Download folder
                if (artistCoverDownloadFolderUri.isNotBlank() && artistCoverDownloadFolderUri != folderLocation) {
                    collected.addAll(mainViewModel.getArtistCoverAssets(artistName, artistCoverDownloadFolderUri))
                }
                // 3. Dynamic cover custom folders
                dynamicCoverCustomFolders.forEach { folder ->
                    if (folder.isNotBlank() && folder != folderLocation && folder != artistCoverDownloadFolderUri) {
                        collected.addAll(mainViewModel.getArtistCoverAssets(artistName, folder))
                    }
                }
                // 4. Default dynamic cover directories
                val roots = com.ella.music.ui.player.dynamicCoverRootDirectories(context, dynamicCoverCustomFolders)
                roots.forEach { root ->
                    if (root.exists() && root.isDirectory) {
                        root.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                val match = com.ella.music.data.artistCoverMatch(file.name, ignoreCase = ignoreCase)
                                if (match != null && match.key == safeArtistKey) {
                                    collected.add(ArtistCoverAsset(Uri.fromFile(file), match.kind))
                                }
                            }
                        }
                    }
                }
                // 5. Folders of songs by this artist
                val songDirs = songs.mapNotNull { song ->
                    song.path.takeIf { it.isNotBlank() && !it.startsWith("http://") && !it.startsWith("https://") }
                        ?.let { java.io.File(it).parentFile }
                }.distinctBy { it.absolutePath }
                songDirs.forEach { dir ->
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                val match = com.ella.music.data.artistCoverMatch(file.name, ignoreCase = ignoreCase)
                                if (match != null && match.key == safeArtistKey) {
                                    collected.add(ArtistCoverAsset(Uri.fromFile(file), match.kind))
                                }
                            }
                        }
                    }
                }
                // Deduplicate by URI and ensure Videos are always ordered first
                val distinct = collected.distinctBy { it.uri.toString() }
                val videos = distinct.filter { it.kind == ArtistCoverKind.Video }
                val images = distinct.filter { it.kind == ArtistCoverKind.Image }
                videos + images
            }
        }
    }
    return state
}
