package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.NeteaseKeyInfo
import com.ella.music.data.matchesGenreName
import com.ella.music.data.splitArtistNames
import com.ella.music.data.splitGenreNames
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.formatPlaybackDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ella.music.ui.artist.rememberArtistCoverModel
import com.ella.music.ui.artist.selectArtistCoverSong
import com.ella.music.ui.components.ExplicitSongTitle
import com.ella.music.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun PlayerDetailPage(
    song: Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    tagInfo: SongTagInfo?,
    neteaseInfo: NeteaseKeyInfo?,
    librarySongs: List<Song>,
    albumArtForAlbum: (Long) -> Any?,
    artistCoverFolderUri: String,
    mainViewModel: MainViewModel,
    palette: PlayerPalette,
    currentPositionMs: Long,
    isPlaying: Boolean,
    beautifulLyricsBackground: Boolean,
    useBlurBackground: Boolean,
    playerBackgroundEnabled: Boolean,
    customBackgroundUri: String,
    customBackgroundOpacity: Float = 1f,
    customBackgroundDim: Float = 0.26f,
    drawBackground: Boolean = true,
    dynamicFlowEnabled: Boolean = false,
    onAlbum: () -> Unit,
    onArtist: (String) -> Unit,
    onComposer: (String) -> Unit,
    onArranger: (String) -> Unit,
    onLyricist: (String) -> Unit,
    onYear: (String) -> Unit,
    onGenre: (String) -> Unit,
    onNeteaseSong: () -> Unit,
    onNeteaseMusicVideo: () -> Unit,
    onNeteaseArtist: (String) -> Unit,
    onNeteaseAlbum: () -> Unit,
    musicVideoEnabled: Boolean = false,
    musicVideoCustomFolders: List<String> = emptyList(),
    dynamicCoverCustomFolders: List<String> = emptyList(),
    onMusicVideo: (DynamicCoverSource) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val showAlbumArtists by mainViewModel.settingsManager.showAlbumArtists.collectAsState(initial = true)
    val composerNames = remember(tagInfo?.composer, song?.composer) {
        splitArtistNames(tagInfo?.composer?.ifBlank { song?.composer.orEmpty() }.orEmpty())
    }
    val arrangerNames = remember(tagInfo?.arranger, song?.arranger) {
        splitArtistNames(tagInfo?.arranger?.ifBlank { song?.arranger.orEmpty() }.orEmpty())
    }
    val lyricistNames = remember(tagInfo?.lyricist, song?.lyricist) {
        splitArtistNames(tagInfo?.lyricist?.ifBlank { song?.lyricist.orEmpty() }.orEmpty())
    }
    val artistNames = remember(tagInfo?.artist, song?.artist) {
        splitArtistNames(tagInfo?.artist?.ifBlank { song?.artist.orEmpty() }.orEmpty())
    }
    var showNeteaseArtistPicker by remember(neteaseInfo) { mutableStateOf(false) }
    val neteaseArtists = remember(neteaseInfo) {
        neteaseInfo?.artists.orEmpty().filter { it.id.isNotBlank() }
    }
    val aliasText = remember(neteaseInfo?.aliases) {
        neteaseInfo
            ?.aliases
            .orEmpty()
            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .distinct()
            .joinToString(" · ")
    }
    val translatedNameText = remember(neteaseInfo?.translatedNames) {
        neteaseInfo
            ?.translatedNames
            .orEmpty()
            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .distinct()
            .joinToString(" · ")
    }
    val effectiveLibrarySongs = remember(librarySongs, song) {
        librarySongs.ifEmpty { song?.let(::listOf).orEmpty() }
    }
    val artistDetails = remember(artistNames, effectiveLibrarySongs) {
        artistNames.map { name ->
            PlayerDetailEntity(
                name = name,
                songs = effectiveLibrarySongs.filter { candidate ->
                    splitArtistNames(candidate.artist).any { it.equals(name, ignoreCase = true) }
                }
            )
        }
    }
    val composerDetails = remember(composerNames, effectiveLibrarySongs) {
        composerNames.map { name ->
            PlayerDetailEntity(
                name = name,
                songs = effectiveLibrarySongs.filter { candidate ->
                    splitArtistNames(candidate.composer).any { it.equals(name, ignoreCase = true) }
                }
            )
        }
    }
    val arrangerDetails = remember(arrangerNames, effectiveLibrarySongs) {
        arrangerNames.map { name ->
            PlayerDetailEntity(
                name = name,
                songs = effectiveLibrarySongs.filter { candidate ->
                    splitArtistNames(candidate.arranger).any { it.equals(name, ignoreCase = true) }
                }
            )
        }
    }
    val lyricistDetails = remember(lyricistNames, effectiveLibrarySongs) {
        lyricistNames.map { name ->
            PlayerDetailEntity(
                name = name,
                songs = effectiveLibrarySongs.filter { candidate ->
                    splitArtistNames(candidate.lyricist).any { it.equals(name, ignoreCase = true) }
                }
            )
        }
    }
    val albumSongs = remember(song?.albumIdentityId(), effectiveLibrarySongs) {
        val albumId = song?.albumIdentityId() ?: 0L
        effectiveLibrarySongs.filter { it.albumIdentityId() == albumId }
    }
    val year = remember(song?.year) {
        Regex("""\d{4}""").find(song?.year.orEmpty())?.value.orEmpty()
    }
    val genre = song?.genre.orEmpty().trim()
    val genreNames = remember(genre) { splitGenreNames(genre) }
    val singleGenreName = genreNames.singleOrNull().orEmpty()
    val yearSongs = remember(year, effectiveLibrarySongs) {
        effectiveLibrarySongs.filter { candidate ->
            Regex("""\d{4}""").find(candidate.year)?.value == year
        }
    }
    val singleGenreSongs = remember(singleGenreName, effectiveLibrarySongs) {
        if (singleGenreName.isBlank()) emptyList()
        else effectiveLibrarySongs.filter { it.genre.matchesGenreName(singleGenreName) }
    }
    val musicVideoSource by produceState<DynamicCoverSource?>(
        initialValue = null,
        song?.dynamicCoverResolutionKey(),
        musicVideoEnabled,
        musicVideoCustomFolders,
        dynamicCoverCustomFolders
    ) {
        val currentSong = song
        // A producer retains its previous value when its keys change. Clear it before the new
        // file lookup so the previous song's MV row cannot linger during a slower SAF scan.
        value = null
        value = if (musicVideoEnabled && currentSong != null) {
            withContext(Dispatchers.IO) {
                currentSong.musicVideoSource(
                    context,
                    customRootPaths = dynamicCoverCustomFolders,
                    musicVideoCustomFolders = musicVideoCustomFolders
                )
            }
        } else {
            null
        }
    }
    val musicVideoDurationMs by produceState(
        initialValue = 0L,
        musicVideoSource?.failureKey
    ) {
        val currentSource = musicVideoSource
        value = 0L
        value = currentSource?.let { source ->
            withContext(Dispatchers.IO) { context.readMusicVideoDurationMs(source.uri) }
        } ?: 0L
    }
    val musicVideoPreviewFrame by produceState<Bitmap?>(
        initialValue = null,
        musicVideoSource?.failureKey
    ) {
        val currentSource = musicVideoSource
        value = null
        value = currentSource?.let { source ->
            withContext(Dispatchers.IO) { context.readMusicVideoPreviewFrame(source.uri) }
        }
    }

    if (showNeteaseArtistPicker) {
        PlayerDetailNeteaseArtistPickerSheet(
            artists = neteaseArtists,
            onDismiss = { showNeteaseArtistPicker = false },
            onArtistSelected = { artistId ->
                showNeteaseArtistPicker = false
                onNeteaseArtist(artistId)
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (drawBackground) {
            SharedPlayerPageBackground(
                song = song,
                embeddedCover = embeddedCover,
                paletteBitmap = paletteBitmap,
                palette = palette,
                currentPositionMs = currentPositionMs,
                isPlaying = isPlaying,
                playerBackgroundEnabled = playerBackgroundEnabled,
                playerBackgroundUri = customBackgroundUri,
                playerBackgroundOpacity = customBackgroundOpacity,
                playerBackgroundDim = customBackgroundDim,
                beautifulLyricsBackground = beautifulLyricsBackground,
                dynamicFlowEnabled = dynamicFlowEnabled,
                useBlurBackground = useBlurBackground,
                modifier = Modifier.fillMaxSize()
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.player_song_details),
                    color = LocalPlayerContentColor.current,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.player_detail_song),
                    color = LocalPlayerContentColor.current.copy(alpha = 0.68f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                ExplicitSongTitle(
                    title = song?.title.orEmpty().ifBlank { stringResource(R.string.player_unknown_song) },
                    color = LocalPlayerContentColor.current.copy(alpha = 0.96f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.fillMaxWidth()
                )
                translatedNameText.takeIf { it.isNotBlank() }?.let { translatedName ->
                    Spacer(modifier = Modifier.height(8.dp))
                    PlayerDetailInfoLine(
                        stringResource(R.string.player_detail_translated_name),
                        translatedName
                    )
                }
                aliasText.takeIf { it.isNotBlank() }?.let { alias ->
                    Spacer(modifier = Modifier.height(8.dp))
                    PlayerDetailInfoLine(stringResource(R.string.player_detail_alias), alias)
                }
                tagInfo?.displayComment?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    PlayerDetailInfoLine(stringResource(R.string.player_detail_comment), it)
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            if (artistDetails.isNotEmpty()) {
                item {
                    PlayerDetailGroupCard(title = stringResource(R.string.player_detail_artist_label)) {
                        artistDetails.forEach { detail ->
                            val representativeSong = remember(effectiveLibrarySongs, detail.name) {
                                selectArtistCoverSong(effectiveLibrarySongs, detail.name)
                            }
                            val artistCoverModel = rememberArtistCoverModel(
                                artistName = detail.name,
                                representativeSong = representativeSong,
                                folderLocation = artistCoverFolderUri,
                                mainViewModel = mainViewModel
                            )
                            val artistAlbumCount = remember(detail.name, effectiveLibrarySongs, showAlbumArtists) {
                                effectiveLibrarySongs
                                    .filter { candidate ->
                                        splitArtistNames(candidate.artist).any {
                                            it.equals(detail.name, ignoreCase = true)
                                        } || (showAlbumArtists && splitArtistNames(candidate.albumArtist).any {
                                            it.equals(detail.name, ignoreCase = true)
                                        })
                                    }
                                    .map { it.albumIdentityId() }
                                    .distinct()
                                    .size
                            }
                            PlayerDetailGroupedActionRow(
                                title = detail.name,
                                summary = detail.songs.stats(artistAlbumCount).personSummary(),
                                coverModel = artistCoverModel,
                                circularCover = true,
                                onClick = { onArtist(detail.name) }
                            )
                        }
                    }
                }
            }

            if (song?.album.orEmpty().isNotBlank()) {
                item {
                    val albumArtist = song?.albumArtist.orEmpty()
                    PlayerDetailGroupCard(title = stringResource(R.string.player_detail_album)) {
                        PlayerDetailGroupedActionRow(
                            title = song?.album.orEmpty(),
                            summary = albumSongs.stats().albumSummary(albumArtist),
                            coverModel = song?.coverUrl?.takeIf { it.isNotBlank() }
                                ?: song?.albumId?.let(albumArtForAlbum),
                            onClick = onAlbum
                        )
                    }
                }
            }

            musicVideoSource?.let { source ->
                item {
                    PlayerDetailGroupCard(title = stringResource(R.string.player_detail_music_video)) {
                        PlayerDetailGroupedActionRow(
                            title = song?.title.orEmpty().ifBlank {
                                stringResource(R.string.player_unknown_song)
                            },
                            summary = listOf(
                                song?.artist.orEmpty().ifBlank {
                                    stringResource(R.string.player_unknown_artist)
                                },
                                musicVideoDurationMs.formatPlaybackDuration()
                            ).filter(String::isNotBlank).joinToString(" · "),
                            // Video stills are deliberately shown in a wide frame, rather than
                            // pretending the MV is square album artwork.
                            coverModel = musicVideoPreviewFrame ?: source.uri,
                            coverAspectRatio = 16f / 9f,
                            onClick = { onMusicVideo(source) }
                        )
                    }
                }
            }

            if (year.isNotBlank() || singleGenreName.isNotBlank()) {
                item {
                    PlayerDetailDualInfoCard(
                        year = year,
                        yearSongCount = yearSongs.size,
                        yearDuration = yearSongs.sumOf { it.duration },
                        genre = singleGenreName,
                        genreSongCount = singleGenreSongs.size,
                        genreDuration = singleGenreSongs.sumOf { it.duration },
                        onYearClick = { onYear(year) },
                        onGenreClick = { onGenre(singleGenreName) }
                    )
                }
            }

            if (genreNames.size > 1) {
                item {
                    PlayerDetailGroupCard(title = stringResource(R.string.player_detail_genre)) {
                        genreNames.forEach { genreName ->
                            val songsForGenre = remember(genreName, effectiveLibrarySongs) {
                                effectiveLibrarySongs.filter { it.genre.matchesGenreName(genreName) }
                            }
                            PlayerDetailGroupedActionRow(
                                title = genreName,
                                summary = stringResource(
                                    R.string.player_detail_song_count_duration,
                                    songsForGenre.size,
                                    songsForGenre.sumOf { it.duration }.formatPlaybackDuration()
                                ),
                                onClick = { onGenre(genreName) }
                            )
                        }
                    }
                }
            }

            if (composerDetails.isNotEmpty()) {
                item {
                    PlayerDetailGroupCard(title = stringResource(R.string.player_detail_composer)) {
                        composerDetails.forEach { detail ->
                            PlayerDetailGroupedActionRow(
                                title = detail.name,
                                summary = detail.songs.stats().personSummary(),
                                onClick = { onComposer(detail.name) }
                            )
                        }
                    }
                }
            }

            if (arrangerDetails.isNotEmpty()) {
                item {
                    PlayerDetailGroupCard(title = stringResource(R.string.player_detail_arranger)) {
                        arrangerDetails.forEach { detail ->
                            PlayerDetailGroupedActionRow(
                                title = detail.name,
                                summary = detail.songs.stats().personSummary(),
                                onClick = { onArranger(detail.name) }
                            )
                        }
                    }
                }
            }

            if (lyricistDetails.isNotEmpty()) {
                item {
                    PlayerDetailGroupCard(title = stringResource(R.string.player_detail_lyricist)) {
                        lyricistDetails.forEach { detail ->
                            PlayerDetailGroupedActionRow(
                                title = detail.name,
                                summary = detail.songs.stats().personSummary(),
                                onClick = { onLyricist(detail.name) }
                            )
                        }
                    }
                }
            }

            if (neteaseInfo?.hasDecodedContent == true) {
                item {
                    PlayerDetailGroupCard(title = stringResource(R.string.player_netease_section)) {
                        if (neteaseInfo.musicId.isNotBlank()) {
                            PlayerDetailGroupedActionRow(
                                title = stringResource(R.string.player_netease_song_page),
                                summary = neteaseInfo.musicName.ifBlank { neteaseInfo.musicId },
                                onClick = onNeteaseSong
                            )
                        }
                        neteaseInfo.artists
                            .joinToString(" / ") { it.name.ifBlank { it.id } }
                            .takeIf { it.isNotBlank() }
                            ?.let { artistSummary ->
                                PlayerDetailGroupedActionRow(
                                    title = stringResource(R.string.player_netease_artist_page),
                                    summary = artistSummary,
                                    enabled = neteaseArtists.isNotEmpty(),
                                    onClick = {
                                        if (neteaseArtists.size == 1) {
                                            onNeteaseArtist(neteaseArtists.first().id)
                                        } else {
                                            showNeteaseArtistPicker = true
                                        }
                                    }
                                )
                            }
                        if (neteaseInfo.albumId.isNotBlank()) {
                            PlayerDetailGroupedActionRow(
                                title = stringResource(R.string.player_netease_album_page),
                                summary = neteaseInfo.albumName.ifBlank { neteaseInfo.albumId },
                                onClick = onNeteaseAlbum
                            )
                        }
                        neteaseInfo.mvId.takeIf(String::isNotBlank)?.let { mvId ->
                            PlayerDetailGroupedActionRow(
                                title = stringResource(R.string.player_detail_music_video),
                                summary = neteaseInfo.musicName.ifBlank { mvId },
                                onClick = onNeteaseMusicVideo
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PlayerDetailEntity(
    val name: String,
    val songs: List<Song>
)

private data class PlayerDetailStats(
    val songCount: Int,
    val totalDuration: Long,
    val albumCount: Int
)

private fun List<Song>.stats(albumCountOverride: Int? = null): PlayerDetailStats = PlayerDetailStats(
    songCount = size,
    totalDuration = sumOf { it.duration },
    albumCount = albumCountOverride ?: map { it.albumIdentityId() }.distinct().count()
)

@Composable
private fun PlayerDetailStats.personSummary(): String = stringResource(
    R.string.player_detail_person_summary,
    songCount,
    totalDuration.formatPlaybackDuration(),
    albumCount
)

@Composable
private fun PlayerDetailStats.albumSummary(albumArtist: String): String =
    if (albumArtist.isBlank()) {
        stringResource(
            R.string.player_detail_song_count_duration,
            songCount,
            totalDuration.formatPlaybackDuration()
        )
    } else {
        stringResource(
            R.string.player_detail_album_summary,
            songCount,
            totalDuration.formatPlaybackDuration(),
            albumArtist
        )
    }
