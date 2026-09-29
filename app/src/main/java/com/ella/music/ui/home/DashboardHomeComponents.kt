package com.ella.music.ui.home
import kotlinx.coroutines.flow.first

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.ExplicitSongTitle
import com.ella.music.ui.components.LocalSettingsCardFrosting
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.frostedCardColor
import com.ella.music.ui.components.frostedCardModifier
import com.ella.music.ui.components.rememberSongArtworkState
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.viewmodel.MainViewModel
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Playlist
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
    val route: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null
)

@Composable
internal fun HomeTileSection(
    title: String,
    tiles: List<HomeTileSpec>,
    context: Context,
    showPinButtons: Boolean,
    cardColor: Color = MiuixTheme.colorScheme.surfaceContainer
) {
    if (tiles.isEmpty()) return
    val settings = remember(context) { com.ella.music.data.SettingsManager.getInstance(context) }
    val initialItems = remember(settings) {
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            settings.homeShortcutItems.first() to settings.homeFeatureItems.first()
        }
    }
    val shortcutIds by settings.homeShortcutItems.collectAsState(initial = initialItems.first)
    val featureIds by settings.homeFeatureItems.collectAsState(initial = initialItems.second)
    val byId = tiles.associateBy { it.id }
    val shortcuts = shortcutIds.split(',').distinct().mapNotNull { byId[it] }
    val features = featureIds.split(',').distinct().mapNotNull { byId[it] }
    val televisionDevice = remember(context) {
        com.ella.music.util.isTelevisionDevice(context)
    }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val shortcutColumns = homeShortcutColumnCount(
        isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        smallestScreenWidthDp = configuration.smallestScreenWidthDp
    )
    val (shortcutTiles, featureTiles) = if (televisionDevice) {
        emptyList<HomeTileSpec>() to (shortcuts + features).distinctBy { it.id }
    } else {
        shortcuts to features
    }
    if (shortcutTiles.isEmpty() && featureTiles.isNotEmpty()) {
        SectionTitle(title)
    }
    if (shortcutTiles.isNotEmpty()) {
        // A section-level inset keeps the header gap independent of 4/6/8-column layouts.
        Spacer(modifier = Modifier.height(16.dp))
        shortcutTiles.chunked(shortcutColumns).forEachIndexed { index, row ->
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))
            HomeShortcutRow(
                tiles = row,
                context = context,
                showPinButtons = showPinButtons,
                shortcutColumns = shortcutColumns
            )
        }
    }
    if (shortcutTiles.isNotEmpty() && featureTiles.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
    }
    HomeTileGrid(
        tiles = featureTiles,
        context = context,
        showPinButtons = showPinButtons,
        cardColor = cardColor
    )
}

internal fun splitHomeTileSections(
    tiles: List<HomeTileSpec>,
    shortcutCount: Int
): Pair<List<HomeTileSpec>, List<HomeTileSpec>> {
    val splitAt = shortcutCount.coerceAtLeast(0).coerceAtMost(tiles.size)
    return tiles.subList(0, splitAt) to tiles.subList(splitAt, tiles.size)
}

@Composable
private fun HomeShortcutRow(
    tiles: List<HomeTileSpec>,
    context: Context,
    showPinButtons: Boolean,
    shortcutColumns: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top
    ) {
        tiles.forEachIndexed { index, tile ->
            val onPinClick = if (showPinButtons) {
                {
                    val ok = requestPinnedEllaShortcut(context, "home_${tile.id}", tile.title, tile.route)
                    Toast.makeText(
                        context,
                        if (ok) context.getString(R.string.playlist_shortcut_requested, tile.title)
                        else context.getString(R.string.playlist_shortcut_unsupported),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else null
            HomeShortcutTile(
                tile = tile,
                accentIndex = index,
                onPinClick = onPinClick,
                modifier = Modifier.weight(1f)
            )
        }
        repeat((shortcutColumns - tiles.size).coerceAtLeast(0)) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun HomeTileGrid(
    tiles: List<HomeTileSpec>,
    context: Context,
    showPinButtons: Boolean,
    cardColor: Color = MiuixTheme.colorScheme.surfaceContainer
) {
    val featureSettings = remember(context) { SettingsManager.getInstance(context) }
    val initialFeatureStyle = remember(featureSettings) {
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { featureSettings.homeFeatureStyle.first() }
    }
    val featureStyle by featureSettings.homeFeatureStyle.collectAsState(initial = initialFeatureStyle)
    val televisionDevice = remember(context) {
        com.ella.music.util.isTelevisionDevice(context)
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
                            featureStyle = featureStyle,
                            accentIndex = tileIndex,
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
                            featureStyle = featureStyle,
                            accentIndex = tileIndex,
                        modifier = tileModifier
                    )
                }
            }
            if (rowTiles.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private val homeTileAccents = listOf(
    Color(0xFFFFC928), Color(0xFF438CFF), Color(0xFFFF9633), Color(0xFFF05C65),
    Color(0xFF39B58A), Color(0xFF9C68F2), Color(0xFF34A8D8), Color(0xFFEB75B1)
)

internal fun homeShortcutColumnCount(
    isLandscape: Boolean,
    smallestScreenWidthDp: Int
): Int = when {
    !isLandscape -> 4
    smallestScreenWidthDp >= 600 -> 8
    else -> 6
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeShortcutTile(
    tile: HomeTileSpec,
    accentIndex: Int,
    onPinClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val accent = homeTileAccents[accentIndex % homeTileAccents.size]
    val frosting = LocalSettingsCardFrosting.current
    val ringFill = if (frosting != null) {
        Color.White.copy(alpha = 0.055f)
    } else {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.48f)
    }
    Column(
        modifier = modifier
            .combinedClickable(onClick = tile.onClick, onLongClick = onPinClick)
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(minOf(62.dp, maxWidth))
                    .clip(CircleShape)
                    .background(ringFill)
                    .border(1.dp, accent.copy(alpha = 0.34f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tile.icon ?: MiuixIcons.Regular.Play,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(27.dp)
                )
            }
        }
        Text(
            text = tile.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp)
        )
        Text(
            text = tile.subtitle,
            fontSize = 10.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun HomeFeatureWallpaperCard(
    uri: String,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardHeight = if (isLandscape) {
        minOf(320.dp, (configuration.screenHeightDp * 0.72f).dp).coerceAtLeast(240.dp)
    } else {
        180.dp
    }
    Card(
        modifier = modifier,
        cornerRadius = 18.dp
    ) {
        AsyncImage(
            model = uri,
            contentDescription = stringResource(R.string.home_feature_wallpaper),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
        )
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
    featureStyle: Int = 0,
    accentIndex: Int = 0,
    interactive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val frosting = LocalSettingsCardFrosting.current
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val hasCustomCardColor = cardColor != MiuixTheme.colorScheme.surfaceContainer
    val hasSharedBackground = frosting != null
    val effectiveCardColor = when {
        featureStyle != 0 -> Color.Transparent
        hasCustomCardColor -> cardColor
        hasSharedBackground -> Color.Transparent
        isDark -> MiuixTheme.colorScheme.surfaceContainer
        else -> Color.White
    }
    val tileModifier = if (featureStyle == 0 && hasSharedBackground && !hasCustomCardColor) {
        frostedCardModifier(
            modifier = modifier.height(96.dp),
            cornerRadius = 16.dp,
            frosting = frosting
        )
    } else {
        modifier.height(96.dp)
    }
    val background = effectiveCardColor
    val contentColor = MiuixTheme.colorScheme.onSurface
    Column(
        modifier = tileModifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .then(if (featureStyle == 2) Modifier.border(1.dp, homeTileAccents[accentIndex % homeTileAccents.size].copy(alpha = .34f), RoundedCornerShape(16.dp)) else Modifier)
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
