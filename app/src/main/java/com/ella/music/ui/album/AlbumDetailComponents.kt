package com.ella.music.ui.album

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.model.Album
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.components.AppleStylePlayButton
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.ExplicitSongTitle
import com.ella.music.ui.components.PlayNextQuickButton
import com.ella.music.ui.components.RatingStarIcon
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.SelectionCheck
import com.ella.music.ui.components.startDraggingLocalSongs
import com.ella.music.ui.artist.rememberArtistCoverModel
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AlbumCopyrightFooter(
    copyright: String,
    publisher: String,
    year: AlbumMetadataDisplayItem?,
    genres: List<AlbumMetadataDisplayItem>,
    artists: List<AlbumMetadataDisplayItem>,
    composers: List<AlbumMetadataDisplayItem>,
    arrangers: List<AlbumMetadataDisplayItem>,
    lyricists: List<AlbumMetadataDisplayItem>,
    mainViewModel: MainViewModel,
    artistCoverFolderUri: String,
    onGenreClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onComposerClick: (String) -> Unit,
    onArrangerClick: (String) -> Unit,
    onLyricistClick: (String) -> Unit,
    onYearClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        copyright.lines().filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { values ->
            AlbumTextInfoSection(
                title = stringResource(R.string.album_copyright),
                values = values
            )
        }
        publisher.lines().filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { values ->
            AlbumTextInfoSection(
                title = stringResource(R.string.album_publisher),
                values = values
            )
        }
        year?.let { item ->
            AlbumMetadataSection(
                title = stringResource(R.string.category_year),
                items = listOf(item),
                circularCover = false,
                mainViewModel = mainViewModel,
                artistCoverFolderUri = artistCoverFolderUri,
                onItemClick = { onYearClick(item.name) }
            )
        }
        AlbumMetadataSection(
            title = stringResource(R.string.category_genre),
            items = genres,
            circularCover = false,
            mainViewModel = mainViewModel,
            artistCoverFolderUri = artistCoverFolderUri,
            onItemClick = onGenreClick
        )
        AlbumMetadataSection(
            title = stringResource(R.string.player_detail_artist),
            items = artists,
            circularCover = true,
            mainViewModel = mainViewModel,
            artistCoverFolderUri = artistCoverFolderUri,
            onItemClick = onArtistClick
        )
        AlbumMetadataSection(
            title = stringResource(R.string.player_detail_composer),
            items = composers,
            circularCover = true,
            mainViewModel = mainViewModel,
            artistCoverFolderUri = artistCoverFolderUri,
            onItemClick = onComposerClick
        )
        AlbumMetadataSection(
            title = stringResource(R.string.player_detail_arranger),
            items = arrangers,
            circularCover = true,
            mainViewModel = mainViewModel,
            artistCoverFolderUri = artistCoverFolderUri,
            onItemClick = onArrangerClick
        )
        AlbumMetadataSection(
            title = stringResource(R.string.player_detail_lyricist),
            items = lyricists,
            circularCover = true,
            mainViewModel = mainViewModel,
            artistCoverFolderUri = artistCoverFolderUri,
            onItemClick = onLyricistClick
        )
    }
}

internal data class AlbumMetadataDisplayItem(
    val name: String,
    val songCount: Int,
    val duration: Long,
    val albumCount: Int,
    val coverModel: Any?,
    val artistCoverName: String? = null,
    val artistCoverSong: Song? = null
)

@Composable
private fun AlbumTextInfoSection(
    title: String,
    values: List<String>
) {
    if (values.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary
        )
        values.forEach { value ->
            Text(
                text = value,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

@Composable
private fun AlbumMetadataSection(
    title: String,
    items: List<AlbumMetadataDisplayItem>,
    circularCover: Boolean,
    mainViewModel: MainViewModel,
    artistCoverFolderUri: String,
    onItemClick: (String) -> Unit
) {
    if (items.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary
        )
        items.forEach { item ->
            AlbumMetadataRow(
                item = item,
                circularCover = circularCover,
                mainViewModel = mainViewModel,
                artistCoverFolderUri = artistCoverFolderUri,
                onClick = { onItemClick(item.name) }
            )
        }
    }
}

@Composable
private fun AlbumMetadataRow(
    item: AlbumMetadataDisplayItem,
    circularCover: Boolean,
    mainViewModel: MainViewModel,
    artistCoverFolderUri: String,
    onClick: () -> Unit,
) {
    val coverModel = item.artistCoverName?.let { artistName ->
        rememberArtistCoverModel(
            artistName = artistName,
            representativeSong = item.artistCoverSong,
            folderLocation = artistCoverFolderUri,
            mainViewModel = mainViewModel
        )
    } ?: item.coverModel
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SafeCoverImage(
            model = coverModel,
            contentDescription = item.name,
            modifier = Modifier
                .size(52.dp)
                .clip(if (circularCover) CircleShape else RoundedCornerShape(12.dp)),
            sizePx = 256
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.player_detail_person_summary,
                    item.songCount,
                    item.duration.formatPlaybackDuration(),
                    item.albumCount
                ),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun DiscHeader(group: AlbumDiscGroup) {
    Text(
        text = stringResource(
            R.string.album_disc_header,
            group.discNumber,
            group.songs.size,
            group.songs.sumOf { it.duration }.formatPlaybackDuration()
        ),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
internal fun AlbumSongRow(
    song: Song,
    index: Int,
    sortedAlbumSongs: List<Song>,
    currentSongId: Long?,
    isFavorite: Boolean,
    showTrackNumber: Boolean,
    mainViewModel: MainViewModel,
    ratingRevision: Int,
    playerViewModel: PlayerViewModel,
    openPlayerOnPlay: Boolean,
    onNavigateToPlayer: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onLongClick: () -> Unit,
    onSelectionClick: () -> Unit,
    onMore: () -> Unit,
    showPlayNextInLists: Boolean,
    titleOverride: String? = null,
    dragSelectedSongs: List<Song> = emptyList()
) {
    AlbumTrackRow(
        song = song,
        isCurrent = currentSongId == song.id,
        loadAudioInfo = mainViewModel::getAudioInfo,
        isFavorite = isFavorite,
        loadSongRating = mainViewModel::getSongRating,
        ratingRevision = ratingRevision,
        leadingLabel = if (showTrackNumber) song.displayTrackNumber() else null,
        selectionMode = selectionMode,
        selected = selected,
        onLongClick = onLongClick,
        dragSelectedSongs = dragSelectedSongs,
        onClick = {
            if (selectionMode) {
                onSelectionClick()
                return@AlbumTrackRow
            }
            val safeIndex = index.coerceAtLeast(0)
            playerViewModel.setPlaylist(sortedAlbumSongs, safeIndex)
            if (openPlayerOnPlay) onNavigateToPlayer()
        },
        showPlayNextInLists = showPlayNextInLists,
        onPlayNext = { playerViewModel.playNext(song) },
        onMore = onMore,
        titleOverride = titleOverride
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AlbumTrackRow(
    song: Song,
    isCurrent: Boolean,
    loadAudioInfo: (Song) -> AudioInfo,
    isFavorite: Boolean,
    loadSongRating: (Song) -> Int,
    ratingRevision: Int,
    leadingLabel: String?,
    selectionMode: Boolean,
    selected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    showPlayNextInLists: Boolean,
    onPlayNext: () -> Unit,
    onMore: () -> Unit,
    titleOverride: String? = null,
    dragSelectedSongs: List<Song> = emptyList()
) {
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song.id, song.dateModified, loadAudioInfo) {
        value = withContext(Dispatchers.IO) { loadAudioInfo(song) }
    }
    val rating by produceState(initialValue = 0, song.id, song.dateModified, ratingRevision, loadSongRating) {
        value = withContext(Dispatchers.IO) { loadSongRating(song) }
    }
    val qualityTag = audioInfo?.let { audioQualitySummary(it).listTag }
    val context = LocalContext.current
    val sourceView = LocalView.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    val dragStarted = if (selectionMode && selected && dragSelectedSongs.isNotEmpty()) {
                        startDraggingLocalSongs(sourceView, context, dragSelectedSongs)
                    } else {
                        false
                    }
                    if (!dragStarted) onLongClick()
                }
            )
            .padding(start = 26.dp, end = 16.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            SelectionCheck(
                selected = selected,
                checkColor = Color.White
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = leadingLabel.orEmpty(),
            fontSize = 16.sp,
            color = if (isCurrent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.width(46.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExplicitSongTitle(
                    title = titleOverride ?: song.title,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isFavorite) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "♥", fontSize = 12.sp, color = Color(0xFFFF4D6D))
                }
                if (rating > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    RatingStarIcon(
                        filled = true,
                        tint = Color(0xFFFFB703),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(text = rating.toString(), fontSize = 11.sp, color = Color(0xFFFFB703))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!qualityTag.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(albumDetailQualityColor(qualityTag).copy(alpha = 0.18f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = qualityTag,
                            fontSize = 9.sp,
                            color = albumDetailQualityColor(qualityTag)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = song.artist.ifBlank { stringResource(R.string.player_unknown_artist) },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = song.duration.formatPlaybackDuration(),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(end = 4.dp)
        )
        if (!selectionMode && showPlayNextInLists) {
            Spacer(modifier = Modifier.width(8.dp))
            PlayNextQuickButton(onClick = onPlayNext)
        }
        Text(
            text = "⋮",
            fontSize = 24.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onMore)
                .padding(horizontal = 10.dp, vertical = 2.dp)
        )
    }
}

@Composable
internal fun AlbumHeader(
    album: Album?,
    releaseDate: String?,
    albumCoverModel: Any?,
    songCount: Int,
    duration: Long,
    hasNeteaseAlbum: Boolean,
    onNeteaseAlbumClick: () -> Unit,
    onAlbumArtistClick: () -> Unit,
    onReleaseYearClick: () -> Unit,
    onIntroductionClick: () -> Unit,
    onCoverClick: () -> Unit,
    onPlayAll: () -> Unit
) {
    val albumArtist = album?.albumArtist?.takeIf { it.isNotBlank() }
        ?: album?.artist?.takeIf { it.isNotBlank() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 26.dp, end = 26.dp, top = 82.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer)
                    .clickable(onClick = onCoverClick),
                contentAlignment = Alignment.Center
            ) {
                if (albumCoverModel != null) {
                    SafeCoverImage(
                        model = albumCoverModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        sizePx = 512,
                        loadOriginal = true
                    )
                } else {
                    DefaultAlbumCover(modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(modifier = Modifier.width(22.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(124.dp)
                    .padding(top = 2.dp)
            ) {
                Text(
                    text = album?.name ?: stringResource(R.string.player_unknown_album),
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (!albumArtist.isNullOrBlank()) {
                    Text(
                        text = albumArtist,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onAlbumArtistClick)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                val albumYearText = releaseDate?.takeIf { it.isNotBlank() }
                    ?: album?.year?.takeIf { it.isNotBlank() }
                if (albumYearText != null) {
                    Text(
                        text = albumYearText,
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.clickable(onClick = onReleaseYearClick)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onIntroductionClick)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.album_introduction_entry),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Icon(
                        imageVector = MiuixIcons.Basic.ArrowRight,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppleStylePlayButton(
                text = stringResource(R.string.album_play_all),
                onClick = onPlayAll,
                modifier = Modifier.weight(1f)
            )
            if (hasNeteaseAlbum) {
                Text(
                    text = stringResource(R.string.player_netease_album_page),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .clickable(onClick = onNeteaseAlbumClick)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
        }
    }
}

internal fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
