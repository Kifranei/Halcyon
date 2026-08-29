package com.ella.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.tagIdentityKey
import com.ella.music.ui.artist.rememberArtistCoverModel
import com.ella.music.ui.artist.selectArtistCoverSong
import com.ella.music.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ArtistPickerSheet(
    artists: List<String>,
    mainViewModel: MainViewModel,
    onArtistSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.62f)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.98f))
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        SheetHandle()
        Text(
            text = stringResource(R.string.common_select_artist),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        ArtistPickerRows(
            artists = artists,
            mainViewModel = mainViewModel,
            onArtistSelected = onArtistSelected
        )
        BasicComponent(
            title = stringResource(R.string.common_cancel),
            onClick = onDismiss
        )
    }
}

/**
 * Shared rows for every local "select artist" entry point.
 *
 * A custom artist picture takes precedence; otherwise the artwork fallback is the #266 order:
 * solo album artist, solo song artist, collaboration album artist, collaboration song artist.
 */
@Composable
internal fun ColumnScope.ArtistPickerRows(
    artists: List<String>,
    mainViewModel: MainViewModel,
    onArtistSelected: (String) -> Unit
) {
    val songs by mainViewModel.songs.collectAsState()
    val artistCoverFolderUri by mainViewModel.settingsManager.artistCoverFolderUri
        .collectAsState(initial = "")

    artists
        .asSequence()
        .filter { it.isNotBlank() }
        .distinctBy { it.tagIdentityKey() }
        .forEach { artist ->
            ArtistPickerRow(
                artist = artist,
                mainViewModel = mainViewModel,
                songs = songs,
                artistCoverFolderUri = artistCoverFolderUri,
                onClick = { onArtistSelected(artist) }
            )
        }
}

@Composable
private fun ArtistPickerRow(
    artist: String,
    mainViewModel: MainViewModel,
    songs: List<Song>,
    artistCoverFolderUri: String,
    onClick: () -> Unit
) {
    val representativeSong = remember(songs, artist) {
        selectArtistCoverSong(songs, artist)
    }
    val coverModel = rememberArtistCoverModel(
        artistName = artist,
        representativeSong = representativeSong,
        folderLocation = artistCoverFolderUri,
        mainViewModel = mainViewModel
    )

    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (coverModel != null) {
                SafeCoverImage(
                    model = coverModel,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    sizePx = 112,
                    showDefaultPlaceholder = false
                )
            } else {
                Icon(
                    imageVector = MiuixIcons.Regular.Music,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = artist,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.16f))
        )
    }
}
