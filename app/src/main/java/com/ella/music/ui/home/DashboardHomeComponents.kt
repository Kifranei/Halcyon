package com.ella.music.ui.home

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun AiMixCard(
    songCount: Int,
    isLoading: Boolean,
    onChat: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val aiCardContentColor = Color(0xFF123F49)
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
                        listOf(Color(0xFF91DFFF), Color(0xFFA4EBCF))
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
                    color = aiCardContentColor,
                    maxLines = 1
                )
                Text(
                    text = if (isLoading) {
                        stringResource(R.string.home_ai_playlist_loading)
                    } else {
                        stringResource(R.string.home_ai_playlist_summary, songCount)
                    },
                    fontSize = 13.sp,
                    color = aiCardContentColor.copy(alpha = 0.76f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            IconButton(onClick = onChat) {
                Icon(
                    imageVector = MiuixIcons.Regular.Community,
                    contentDescription = stringResource(R.string.home_ai_chat_open),
                    tint = aiCardContentColor.copy(alpha = 0.9f),
                    modifier = Modifier.size(26.dp)
                )
            }
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = MiuixIcons.Regular.Play,
                    contentDescription = stringResource(R.string.home_ai_playlist_play),
                    tint = aiCardContentColor,
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
    val televisionDevice = remember(context) {
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
            (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    }
    val tileIds = remember(tiles) { tiles.map { it.id } }
    val focusRequesters = remember(tileIds) {
        List(tileIds.size) { FocusRequester() }
    }
    val bringIntoViewRequesters = remember(tileIds) {
        List(tileIds.size) { BringIntoViewRequester() }
    }
    val focusScope = rememberCoroutineScope()
    tiles.chunked(2).forEachIndexed { index, rowTiles ->
        if (index > 0) Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
        ) {
            rowTiles.forEachIndexed { column, tile ->
                val tileIndex = index * 2 + column
                val rowStart = index * 2
                val previousRowStart = rowStart - 2
                val nextRowStart = rowStart + 2
                val previousRowIndex = (previousRowStart + column)
                    .takeIf { previousRowStart >= 0 && it < tiles.size }
                val nextRowIndex = (nextRowStart + column)
                    .takeIf { nextRowStart < tiles.size && it < tiles.size }
                    ?: (nextRowStart.takeIf { it < tiles.size })
                val onPinClick = if (showPinButtons) {
                    {
                        val ok = requestPinnedEllaShortcut(context, "home_${tile.id}", tile.title, tile.route)
                        Toast.makeText(
                            context,
                            if (ok) context.getString(R.string.playlist_shortcut_requested, tile.title) else context.getString(R.string.playlist_shortcut_unsupported),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else null
                val tileModifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequesters[tileIndex])
                        .focusProperties {
                            if (column > 0) left = focusRequesters[tileIndex - 1]
                            if (column == 0 && tileIndex + 1 < rowStart + rowTiles.size) {
                                right = focusRequesters[tileIndex + 1]
                            }
                            previousRowIndex?.let { up = focusRequesters[it] }
                            nextRowIndex?.let { down = focusRequesters[it] }
                        }
                        .bringIntoViewRequester(bringIntoViewRequesters[tileIndex])
                        .onFocusChanged { state ->
                            if (state.hasFocus) {
                                focusScope.launch {
                                    bringIntoViewRequesters[tileIndex].bringIntoView()
                                }
                            }
                        }
                val onDirectionalKeyEvent: (android.view.KeyEvent) -> Boolean = { nativeEvent ->
                    val target = when (nativeEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT ->
                            if (column > 0) focusRequesters.getOrNull(tileIndex - 1) else null
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT ->
                            if (column == 0 && tileIndex + 1 < rowStart + rowTiles.size) {
                                focusRequesters.getOrNull(tileIndex + 1)
                            } else null
                        android.view.KeyEvent.KEYCODE_DPAD_UP ->
                            previousRowIndex?.let(focusRequesters::get)
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN ->
                            nextRowIndex?.let(focusRequesters::get)
                        else -> null
                    }
                    if (target == null) {
                        false
                    } else {
                        if (nativeEvent.action == android.view.KeyEvent.ACTION_DOWN ||
                            nativeEvent.action == android.view.KeyEvent.ACTION_UP
                        ) {
                                focusScope.launch {
                                    delay(1L)
                                    runCatching { target.requestFocus() }
                                }
                        }
                        true
                    }
                }
                if (televisionDevice) {
                    TvNavigableHomeTile(
                        modifier = tileModifier,
                        onClick = tile.onClick,
                        onLongClick = onPinClick,
                        onDirectionalKeyEvent = onDirectionalKeyEvent
                    ) {
                        HomeTile(
                            title = tile.title,
                            subtitle = tile.subtitle,
                            onClick = {},
                            onPinClick = null,
                            cardColor = cardColor,
                            tileColor = tile.color,
                            gradientEnabled = gradientEnabled,
                            gradientStartColor = gradientStartColor,
                            interactive = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    HomeTile(
                        title = tile.title,
                        subtitle = tile.subtitle,
                        onClick = tile.onClick,
                        onPinClick = onPinClick,
                        cardColor = cardColor,
                        tileColor = tile.color,
                        gradientEnabled = gradientEnabled,
                        gradientStartColor = gradientStartColor,
                        modifier = tileModifier
                    )
                }
            }
            if (rowTiles.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun HomeFeatureWallpaperCard(
    uri: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        cornerRadius = 18.dp
    ) {
        AsyncImage(
            model = uri,
            contentDescription = stringResource(R.string.home_feature_wallpaper),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )
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
    val coverState = rememberSongArtworkState(
        song = song,
        albumArtUri = mainViewModel.getAlbumArtUri(song.albumId),
        loadCoverArt = mainViewModel::getCoverArtBitmap,
        usage = ArtworkUsage.ListThumbnail
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SafeCoverImage(
            model = coverState.model,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp)),
            sizePx = 128,
            showDefaultPlaceholder = coverState.showDefaultCover
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
    interactive: Boolean = true,
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
            .then(if (interactive) Modifier.combinedClickable(onClick = onClick, onLongClick = onPinClick) else Modifier)
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

@Composable
private fun TvNavigableHomeTile(
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onDirectionalKeyEvent: (android.view.KeyEvent) -> Boolean,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.hasFocus }
            .border(
                width = if (focused) 4.dp else 0.dp,
                color = if (focused) MiuixTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .onPreviewKeyEvent { event ->
                val nativeEvent = event.nativeKeyEvent
                when (nativeEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                        if (nativeEvent.action == android.view.KeyEvent.ACTION_UP) onClick()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN ->
                        onDirectionalKeyEvent(nativeEvent)
                    else -> false
                }
            }
            .focusable()
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() }
                )
            }
    ) {
        content()
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
