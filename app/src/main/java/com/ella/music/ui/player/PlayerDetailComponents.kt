package com.ella.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import com.ella.music.data.NeteaseArtist
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.components.SafeCoverImage
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PlayerDetailInfoLine(label: String, value: String) {
    if (value.isBlank()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = LocalPlayerContentColor.current.copy(alpha = 0.44f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = LocalPlayerContentColor.current.copy(alpha = 0.88f),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun PlayerDetailActionRow(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LocalPlayerContentColor.current.copy(alpha = if (enabled) 0.11f else 0.055f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = LocalPlayerContentColor.current.copy(alpha = if (enabled) 0.92f else 0.42f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summary.ifBlank { stringResource(R.string.player_no_info) },
                color = LocalPlayerContentColor.current.copy(alpha = if (enabled) 0.58f else 0.30f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "›",
            color = LocalPlayerContentColor.current.copy(alpha = if (enabled) 0.72f else 0.24f),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun PlayerDetailGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(LocalPlayerContentColor.current.copy(alpha = 0.11f))
            .padding(vertical = 14.dp)
    ) {
        Text(
            text = title,
            color = LocalPlayerContentColor.current.copy(alpha = 0.92f),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
        )
        Spacer(modifier = Modifier.size(6.dp))
        content()
    }
}

@Composable
internal fun PlayerDetailGroupedActionRow(
    title: String,
    summary: String,
    coverModel: Any? = null,
    circularCover: Boolean = false,
    coverAspectRatio: Float = 1f,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val coverShape = if (circularCover) CircleShape else RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (coverModel != null) {
            Box(
                modifier = Modifier
                    .height(52.dp)
                    .aspectRatio(coverAspectRatio)
                    .clip(coverShape)
                    .background(LocalPlayerContentColor.current.copy(alpha = 0.10f))
            ) {
                SafeCoverImage(
                    model = coverModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    sizePx = 128,
                    showDefaultPlaceholder = false
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { stringResource(R.string.player_no_info) },
                color = LocalPlayerContentColor.current.copy(alpha = if (enabled) 0.92f else 0.42f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary.ifBlank { stringResource(R.string.player_no_info) },
                color = LocalPlayerContentColor.current.copy(alpha = if (enabled) 0.58f else 0.30f),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun PlayerDetailDualInfoCard(
    year: String,
    yearSongCount: Int,
    yearDuration: Long,
    genre: String,
    genreSongCount: Int,
    genreDuration: Long,
    onYearClick: () -> Unit,
    onGenreClick: () -> Unit
) {
    if (year.isBlank() && genre.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(LocalPlayerContentColor.current.copy(alpha = 0.11f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (year.isNotBlank()) {
            PlayerDetailStaticInfo(
                label = stringResource(R.string.player_detail_year),
                value = year,
                summary = stringResource(R.string.player_detail_song_count_duration, yearSongCount, yearDuration.formatPlaybackDuration()),
                onClick = onYearClick,
                modifier = Modifier.weight(1f)
            )
        }
        if (genre.isNotBlank()) {
            PlayerDetailStaticInfo(
                label = stringResource(R.string.player_detail_genre),
                value = genre,
                summary = stringResource(R.string.player_detail_song_count_duration, genreSongCount, genreDuration.formatPlaybackDuration()),
                onClick = onGenreClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlayerDetailStaticInfo(
    label: String,
    value: String,
    summary: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(2.dp)
    ) {
        Text(
            text = label,
            color = LocalPlayerContentColor.current.copy(alpha = 0.92f),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = value,
            color = LocalPlayerContentColor.current.copy(alpha = 0.88f),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = summary,
            color = LocalPlayerContentColor.current.copy(alpha = 0.56f),
            fontSize = 13.sp
        )
    }
}

@Composable
internal fun PlayerDetailArtistPickerRow(
    title: String,
    onClick: () -> Unit
) {
    Text(
        text = title,
        color = MiuixTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

@Composable
internal fun PlayerDetailNeteaseArtistPickerSheet(
    artists: List<NeteaseArtist>,
    onDismiss: () -> Unit,
    onArtistSelected: (String) -> Unit
) {
    if (artists.isEmpty()) return

    WindowBottomSheet(
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.player_choose_netease_artist),
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            artists.forEach { artist ->
                PlayerDetailArtistPickerRow(
                    title = artist.name.ifBlank { "ID ${artist.id}" },
                    onClick = { onArtistSelected(artist.id) }
                )
            }
        }
    }
}
