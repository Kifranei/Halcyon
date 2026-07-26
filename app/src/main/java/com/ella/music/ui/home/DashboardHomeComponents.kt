package com.ella.music.ui.home

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.CloverShape
import com.ella.music.ui.components.CookieShape
import com.ella.music.ui.components.ExplicitSongTitle
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.rememberSongArtworkState
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.effect.BgEffectBackground
import com.ella.music.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
internal fun AiMixCard(
    songCount: Int,
    isLoading: Boolean,
    onChat: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        cornerRadius = 16.dp,
        onClick = onPlay
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF3A0CA3), Color(0xFF4361EE), Color(0xFF4CC9F0))
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_ai_playlist),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = if (isLoading) {
                        stringResource(R.string.home_ai_playlist_loading)
                    } else {
                        stringResource(R.string.home_ai_playlist_summary, songCount)
                    },
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            IconButton(onClick = onChat) {
                Icon(
                    imageVector = MiuixIcons.Regular.Community,
                    contentDescription = stringResource(R.string.home_ai_chat_open),
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(26.dp)
                )
            }
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = MiuixIcons.Regular.Play,
                    contentDescription = stringResource(R.string.home_ai_playlist_play),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

internal data class HomeTileSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val color: Color,
    val route: String,
    val onClick: () -> Unit
)

@Composable
internal fun HomeTileSection(
    title: String,
    tiles: List<HomeTileSpec>,
    context: Context,
    showPinButtons: Boolean,
    cardColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    gradientEnabled: Boolean = false,
    gradientStartColor: Color? = null
) {
    if (tiles.isEmpty()) return
    SectionTitle(title)
    HomeTileGrid(
        tiles = tiles,
        context = context,
        showPinButtons = showPinButtons,
        cardColor = cardColor,
        gradientEnabled = gradientEnabled,
        gradientStartColor = gradientStartColor
    )
}

@Composable
internal fun HomeTileGrid(
    tiles: List<HomeTileSpec>,
    context: Context,
    showPinButtons: Boolean,
    cardColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    gradientEnabled: Boolean = false,
    gradientStartColor: Color? = null
) {
    tiles.chunked(2).forEachIndexed { index, rowTiles ->
        if (index > 0) Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            rowTiles.forEach { tile ->
                HomeTile(
                    title = tile.title,
                    subtitle = tile.subtitle,
                    onClick = tile.onClick,
                    onPinClick = if (showPinButtons) {
                        {
                            val ok = requestPinnedEllaShortcut(context, "home_${tile.id}", tile.title, tile.route)
                            Toast.makeText(
                                context,
                                if (ok) context.getString(R.string.playlist_shortcut_requested, tile.title) else context.getString(R.string.playlist_shortcut_unsupported),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else null,
                    cardColor = cardColor,
                    tileColor = tile.color,
                    gradientEnabled = gradientEnabled,
                    gradientStartColor = gradientStartColor,
                    modifier = Modifier.weight(1f)
                )
            }
            if (rowTiles.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun DailyMixCard(
    songs: List<Song>,
    featuredSongs: List<Song>,
    currentSongTitle: String?,
    mainViewModel: MainViewModel,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val contentColor = if (isDark) Color.White else Color(0xFF15151A)
    // Material 3 Expressive thumbnail shapes for the small covers, cycled across them.
    val coverShapes = listOf(CircleShape, CookieShape, CloverShape)
    Card(
        modifier = modifier,
        cornerRadius = 18.dp,
        onClick = onPlay
    ) {
        // HyperOS 3-style animated dynamic gradient (the About-page effect) as the card background.
        BgEffectBackground(
            dynamicBackground = true,
            effectBackground = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            featuredSongs.take(3).forEachIndexed { index, song ->
                val coverSize = listOf(72, 60, 50).getOrElse(index) { 50 }.dp
                val coverState = rememberSongArtworkState(
                    song = song,
                    albumArtUri = mainViewModel.getAlbumArtUri(song.albumId),
                    loadCoverArt = mainViewModel::getCoverArtBitmap,
                    usage = ArtworkUsage.ListThumbnail
                )
                SafeCoverImage(
                    model = coverState.model,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-14 - index * 30).dp, y = (16 + index * 18).dp)
                        .size(coverSize)
                        .clip(coverShapes[index % coverShapes.size]),
                    sizePx = 192
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 140.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_daily_mix),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = currentSongTitle?.let { stringResource(R.string.home_now_playing_song, it) }
                        ?: stringResource(R.string.home_random_song_count, songs.size),
                    fontSize = 14.sp,
                    color = contentColor.copy(alpha = 0.78f),
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Play,
                        contentDescription = stringResource(R.string.home_play_daily_mix),
                        tint = contentColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun CompactRecentSongRow(
    song: Song,
    mainViewModel: MainViewModel,
    cardText: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SafeCoverImage(
            model = mainViewModel.getAlbumArtUri(song.albumId),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp)),
            sizePx = 128
        )
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            ExplicitSongTitle(
                title = song.title,
                color = cardText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = song.artist,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
    )
}

@Composable
private fun HomeTile(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onPinClick: (() -> Unit)? = null,
    cardColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    tileColor: Color = MiuixTheme.colorScheme.primary,
    gradientEnabled: Boolean = false,
    gradientStartColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val background = tileColor
        .copy(alpha = if (isDark) 0.30f else 0.24f)
        .compositeOver(cardColor)
    val gradientBase = gradientStartColor ?: tileColor.copy(alpha = if (isDark) 0.18f else 0.14f)
    val gradientStart = gradientBase.copy(alpha = if (isDark) 0.52f else 0.44f).compositeOver(cardColor)
    val contentColor = if (background.luminance() < 0.42f) Color.White else Color(0xFF15151A)
    Column(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (gradientEnabled) {
                    Brush.linearGradient(
                        colors = listOf(gradientStart, background, background.copy(alpha = 0.92f))
                    )
                } else {
                    Brush.linearGradient(listOf(background, background))
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onPinClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (onPinClick != null) {
                Text(
                    text = "+",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.72f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onPinClick)
                        .padding(horizontal = 6.dp)
                )
            }
        }
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = contentColor.copy(alpha = 0.68f),
            maxLines = 1
        )
    }
}

internal fun String.csvIdSet(): Set<String> =
    split(',', '，', ';', '；')
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .toSet()

internal fun String.csvIds(defaultValue: String): List<String> {
    val ids = csvIdSet().toList()
    val defaults = defaultValue.csvIdSet().toList()
    return (ids + defaults).distinct()
}
