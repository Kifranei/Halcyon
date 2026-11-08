package com.ella.music.ui.poster

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.rememberSongArtworkState
import com.ella.music.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

internal val PosterInk = Color(0xFF10131B)
internal val PosterPanel = Color(0xFF1D2230)
internal val PosterMuted = Color(0xFFB5BED0)
val PosterWallIcon: ImageVector by lazy {
    ImageVector.Builder("PosterWall", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 3f); lineTo(13f, 3f); lineTo(13f, 13f); lineTo(3f, 13f); close()
            moveTo(15f, 3f); lineTo(21f, 3f); lineTo(21f, 9f); lineTo(15f, 9f); close()
            moveTo(15f, 11f); lineTo(21f, 11f); lineTo(21f, 21f); lineTo(15f, 21f); close()
            moveTo(3f, 15f); lineTo(13f, 15f); lineTo(13f, 21f); lineTo(3f, 21f); close()
        }
    }.build()
}

internal data class ExpandedPoster(val song: Song, val sourceIndex: Int, val origin: PosterRect, val originCornerRadius: Float = 13f,
    val stackKey: String? = null, val originRotation: Float = 0f)

internal val PosterFullscreenIcon: ImageVector by lazy {
    ImageVector.Builder("PosterFullscreen", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 3f); lineTo(10f, 3f); lineTo(10f, 5f); lineTo(5f, 5f); lineTo(5f, 10f); lineTo(3f, 10f); close()
            moveTo(14f, 3f); lineTo(21f, 3f); lineTo(21f, 10f); lineTo(19f, 10f); lineTo(19f, 5f); lineTo(14f, 5f); close()
            moveTo(3f, 14f); lineTo(5f, 14f); lineTo(5f, 19f); lineTo(10f, 19f); lineTo(10f, 21f); lineTo(3f, 21f); close()
            moveTo(19f, 14f); lineTo(21f, 14f); lineTo(21f, 21f); lineTo(14f, 21f); lineTo(14f, 19f); lineTo(19f, 19f); close()
        }
    }.build()
}

@Composable
internal fun PosterArtwork(song: Song, mainViewModel: MainViewModel, modifier: Modifier, large: Boolean = false, sizePx: Int = 384) {
    val loader = remember(mainViewModel, large, sizePx) {
        if (large) { current: Song -> mainViewModel.getCoverArtBitmap(current, 768) } else { current: Song -> mainViewModel.getCoverArtBitmap(current, sizePx) }
    }
    val artwork = rememberSongArtworkState(
        song, mainViewModel.getAlbumArtUri(song.albumId), loader,
        if (large) ArtworkUsage.LibraryDetail else ArtworkUsage.LibraryGrid,
        showDefaultWhenMissing = false, cacheVariant = if (large) "poster:expanded:768" else "poster:$sizePx"
    )
    val hue = remember(song.artist, song.album) { ((song.artist + song.album).hashCode().toLong() and 0x7fffffff).rem(360).toFloat() }
    Box(modifier.background(Brush.linearGradient(listOf(Color.hsl(hue, 0.38f, 0.32f), Color.hsl((hue + 35) % 360, 0.45f, 0.12f))))) {
        if (artwork.model != null) {
            val model = artwork.model
            if (model is android.graphics.Bitmap && !model.isRecycled) {
                androidx.compose.foundation.Image(model.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else SafeCoverImage(model, null, Modifier.fillMaxSize(), sizePx = if (large) 768 else sizePx, showDefaultPlaceholder = false)
        } else {
            // Real metadata forms the fallback poster; missing covers never turn the field into
            // a wall of identical placeholder icons.
            Text(
                song.title.ifBlank { song.fileName }.take(1).uppercase(), color = Color.White.copy(alpha = 0.15f),
                fontSize = if (large) 112.sp else 64.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
internal fun PosterTile(song: Song, rect: PosterRect, current: Boolean, artwork: @Composable (Song, Modifier) -> Unit, onExpand: () -> Unit, onMore: (() -> Unit)?, selected: Boolean = false, showInfo: Boolean = true,
    modifier: Modifier = Modifier, stackSize: Int = 0, dropTarget: Boolean = false, dropBefore: Boolean = false) {
    val accent = MiuixTheme.colorScheme.primary
    val shape = RoundedCornerShape(13.dp)
    val small = rect.width < 140f
    Box(
        Modifier.fillMaxSize().shadow(if (current) 8.dp else if (showInfo) 2.dp else 0.dp, shape).clip(shape)
            .focusProperties { canFocus = false }
            .combinedClickable(onClickLabel = stringResource(R.string.poster_wall_expand), onClick = onExpand,
                onLongClickLabel = stringResource(R.string.player_more_actions), onLongClick = onMore)
            .then(modifier)
            .then(when {
                dropTarget -> Modifier.border(4.dp, accent, shape)
                selected -> Modifier.border(4.dp, Color.White, shape)
                current -> Modifier.border(2.dp, accent.copy(alpha = 0.9f), shape)
                else -> Modifier.border(0.6.dp, Color.White.copy(alpha = 0.12f), shape)
            })
    ) {
        artwork(song, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.13f), Color.Black.copy(alpha = 0.86f)))))
        if (dropTarget) Text(stringResource(if (dropBefore) R.string.poster_wall_insert_below else R.string.poster_wall_insert_above),
            color = MiuixTheme.colorScheme.onPrimary, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.Center).clip(CircleShape).background(accent).padding(horizontal = 12.dp, vertical = 8.dp))
        if (showInfo && stackSize > 1) Text("$stackSize / $MAX_POSTER_STACK_SIZE", color = Color.White,
            fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 5.dp))
        if (current && showInfo) {
            Row(Modifier.align(Alignment.TopStart).padding(12.dp).clip(CircleShape).background(accent).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(MiuixTheme.colorScheme.onPrimary))
                if (!small) Text(stringResource(R.string.poster_wall_current), color = MiuixTheme.colorScheme.onPrimary, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 5.dp))
            }
        }
        if (showInfo) Column(Modifier.align(Alignment.BottomStart).padding(if (small) 11.dp else 16.dp)) {
            Text(song.title.ifBlank { song.fileName }, color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = if (small) 15.sp else 22.sp, lineHeight = if (small) 19.sp else 27.sp,
                maxLines = if (rect.height > 140f) 2 else 1, overflow = TextOverflow.Ellipsis)
            if (!small || rect.height > 140f) {
                Text(song.artist.ifBlank { stringResource(R.string.player_unknown_artist) }, color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

@Composable
internal fun PosterIconButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.size(44.dp).clip(CircleShape).onFocusChanged { focused = it.isFocused }
        .border(if (focused) 2.dp else 0.dp, if (focused) MiuixTheme.colorScheme.primary else Color.Transparent, CircleShape)
        .then(if (enabled) Modifier.clickable(onClickLabel = label, onClick = onClick) else Modifier), contentAlignment = Alignment.Center) { content() }
}

internal val LocalPosterTransportFocus = staticCompositionLocalOf<FocusRequester?> { null }

@Composable
internal fun PosterPlayControls(playing: Boolean, onPrevious: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit,
    showSkipButtons: Boolean = true) {
    if (showSkipButtons) PosterIconButton(stringResource(R.string.common_previous), onPrevious) {
        Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.common_previous), tint = Color.White, modifier = Modifier.size(19.dp))
    }
    val focus = LocalPosterTransportFocus.current
    PosterIconButton(stringResource(if (playing) R.string.common_pause else R.string.common_play), onToggle,
        Modifier.background(Color.White, CircleShape).then(if (focus != null) Modifier.focusRequester(focus) else Modifier)) {
        Icon(painterResource(if (playing) R.drawable.ic_player_pause else R.drawable.ic_player_play), stringResource(if (playing) R.string.common_pause else R.string.common_play), tint = PosterInk, modifier = Modifier.size(22.dp))
    }
    if (showSkipButtons) PosterIconButton(stringResource(R.string.common_next), onNext) {
        Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.common_next), tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

@Composable
internal fun PosterExpandedCard(
    poster: ExpandedPoster, viewport: PosterRect, current: Boolean,
    artwork: @Composable (Song, Modifier) -> Unit,
    lyrics: @Composable (Modifier) -> Unit, seekBar: @Composable (Boolean) -> Unit,
    controls: @Composable () -> Unit,
    onPlay: () -> Unit, onMore: () -> Unit, onClosed: () -> Unit,
    onFullscreen: (() -> Unit)? = null,
    onStackSwipe: ((Int) -> Unit)? = null,
    stackPosition: String? = null,
    stackSongs: List<Song> = emptyList(), stackIndex: Int = 0
) {
    val progress = remember(poster.origin) { Animatable(0f) }
    var closing by remember(poster.origin) { mutableStateOf(false) }
    var swipeOffset by remember(poster.origin) { mutableFloatStateOf(0f) }
    val swipe by rememberUpdatedState(onStackSwipe)
    val displayedCard by rememberUpdatedState(poster.stackKey to poster.sourceIndex)
    val latestArtwork by rememberUpdatedState(artwork)
    // Move the already-loaded cover with its card when a rear card becomes the front.
    // Each stack occurrence has its own slot, including repeated songs in a queue.
    val artworkSlots = remember(poster.origin) {
        mutableMapOf<Int, @Composable (Song, Modifier) -> Unit>()
    }
    fun cardArtwork(index: Int): @Composable (Song, Modifier) -> Unit = artworkSlots.getOrPut(index) {
        movableContentOf { song: Song, modifier: Modifier -> latestArtwork(song, modifier) }
    }
    val swipeDensity = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    var releasing by remember(poster.origin) { mutableStateOf(false) }
    val enterAlpha = remember(poster.origin) { Animatable(1f) }
    val canSwipe = onStackSwipe != null && stackSongs.size > 1
    fun settle(commit: Boolean, widthPx: Float) {
        if (releasing) return
        releasing = true
        val start = swipeOffset
        scope.launch {
            try {
                val motion = Animatable(start)
                if (commit && kotlin.math.abs(start) >= 48f * swipeDensity) {
                    motion.animateTo(if (start < 0f) -widthPx * 1.25f else widthPx * 1.25f, tween(160)) {
                        swipeOffset = value
                    }
                    if (!closing) {
                        val previous = displayedCard
                        swipe?.invoke(if (start < 0f) 1 else -1)
                        // Do not return the outgoing song to the center before composition
                        // has committed the incoming song and moved its loaded cover.
                        kotlinx.coroutines.withTimeoutOrNull(1000) {
                            snapshotFlow { displayedCard }.first { it != previous }
                        }
                        withFrameNanos { }
                        // The promoted rear card is already lifted to the center, so the new
                        // front teleports home while invisible and fades in over it —
                        // snapping the offset to zero was the visible flicker.
                        enterAlpha.snapTo(0f)
                        swipeOffset = 0f
                        enterAlpha.animateTo(1f, tween(180))
                    }
                } else motion.animateTo(0f, spring(stiffness = 500f)) { swipeOffset = value }
            } finally {
                swipeOffset = 0f
                releasing = false
            }
        }
    }
    val closed by rememberUpdatedState(onClosed)
    val animation = spring<Float>(dampingRatio = 1f, stiffness = 320f, visibilityThreshold = 0.01f)
    LaunchedEffect(poster.origin) { progress.animateTo(1f, animation) }
    LaunchedEffect(closing) { if (closing) { progress.animateTo(0f, animation); closed() } }
    val close = { closing = true }
    val transportFocus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    val television = LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK ==
        android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    val controlsReady by remember { derivedStateOf { !closing && progress.value > 0.85f } }
    LaunchedEffect(television) { if (television) inputMode.requestInputMode(InputMode.Keyboard) }
    LaunchedEffect(controlsReady, current, inputMode.inputMode, television) {
        if (controlsReady && inputMode.inputMode == InputMode.Keyboard) transportFocus.requestFocus()
    }
    BackHandler(onBack = close)
    val width = minOf(420f, viewport.width - if (canSwipe) 48f else 32f)
    val height = minOf(560f, viewport.height - 24f)
    val target = PosterRect((viewport.width - width) / 2, (viewport.height - height) / 2, width, height)
    val rect = target
    val shape = RoundedCornerShape(24.dp)
    val closeLabel = stringResource(R.string.common_close)
    Box(Modifier.fillMaxSize().drawBehind { drawRect(PosterInk.copy(alpha = progress.value * 0.78f)) }
        .pointerInput(Unit) { detectTapGestures(onTap = { close() }) }
        .semantics { onClick(label = closeLabel) { close(); true } })
    if (canSwipe) {
        val direction by remember { derivedStateOf { if (swipeOffset > 0f) -1 else 1 } }
        // Only two rear popups are mounted, with all controls and lyrics inside the front one.
        for (depth in minOf(2, stackSongs.size - 1) downTo 1) {
            val rearIndex = Math.floorMod(stackIndex + direction * depth, stackSongs.size)
            val rear = poster.copy(song = stackSongs[rearIndex])
            Box(Modifier.testTag("poster-expanded-rear-$depth")
                .offset { IntOffset((rect.x * density).roundToInt(), (rect.y * density).roundToInt()) }
                .wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(rect.width.dp, rect.height.dp)
                .graphicsLayer {
                    val lift = if (depth == 1) (kotlin.math.abs(swipeOffset) / (width * swipeDensity)).coerceIn(0f, 1f) else 0f
                    val spacing = progress.value * (1f - lift)
                    val frame = posterMorphFrame(poster.origin, target, poster.originCornerRadius, progress.value)
                    translationX = (frame.rect.centerX - target.centerX) * density + depth * 8f * swipeDensity * direction * spacing
                    translationY = (frame.rect.centerY - target.centerY) * density + depth * 8f * swipeDensity * spacing
                    scaleX = frame.rect.width / target.width * (1f - depth * 0.035f * spacing)
                    scaleY = frame.rect.height / target.height * (1f - depth * 0.035f * spacing)
                    rotationZ = poster.originRotation * (1f - progress.value) + depth * 1.5f * direction * spacing
                    alpha = progress.value
                }.shadow(12.dp, shape).clip(shape).background(PosterPanel).clearAndSetSemantics { }) {
                PosterExpandedContent(rear, height, false, cardArtwork(rearIndex), {}, {}, {}, {}, {}, {}, null,
                    "${rearIndex + 1} / ${stackSongs.size}", transportFocus, controlsReady, interactive = false)
            }
        }
    }
    Box(
        Modifier.testTag("poster-expanded-card").offset { IntOffset((rect.x * density).roundToInt(), (rect.y * density).roundToInt()) }
            .wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(rect.width.dp, rect.height.dp)
            .graphicsLayer {
                val frame = posterMorphFrame(poster.origin, target, poster.originCornerRadius, progress.value)
                translationX = (frame.rect.centerX - target.centerX) * density + swipeOffset
                translationY = (frame.rect.centerY - target.centerY) * density
                scaleX = frame.rect.width / target.width
                scaleY = frame.rect.height / target.height
                this.shape = RoundedCornerShape(frame.cornerRadius.dp / maxOf(scaleX, scaleY))
                clip = true
                shadowElevation = (3f + 29f * progress.value) * density
                rotationZ = poster.originRotation * (1f - progress.value) + (swipeOffset / (width * swipeDensity)).coerceIn(-1f, 1f) * 12f
                alpha = progress.value * enterAlpha.value
            }
            .then(if (!canSwipe || !controlsReady || releasing) Modifier else Modifier.pointerInput(poster.origin, width, swipeDensity) {
                detectHorizontalDragGestures(
                    onDragCancel = { settle(false, width * swipeDensity) },
                    onDragEnd = { settle(true, width * swipeDensity) },
                    onHorizontalDrag = { change, amount -> change.consume(); swipeOffset += amount }
                )
            })
            .background(PosterPanel).pointerInput(Unit) { detectTapGestures(onTap = {}) }
    ) {
        PosterExpandedContent(poster, height, current, cardArtwork(stackIndex), lyrics, seekBar, controls,
            onPlay, onMore, close, onFullscreen, stackPosition, transportFocus, controlsReady)
    }
}

/** Reuse the original complete popup for both the active card and its passive rear layers. */
@Composable
private fun PosterExpandedContent(
    poster: ExpandedPoster, height: Float, current: Boolean,
    artwork: @Composable (Song, Modifier) -> Unit, lyrics: @Composable (Modifier) -> Unit,
    seekBar: @Composable (Boolean) -> Unit, controls: @Composable () -> Unit,
    onPlay: () -> Unit, onMore: () -> Unit, close: () -> Unit, onFullscreen: (() -> Unit)?,
    stackPosition: String?, transportFocus: FocusRequester, showDetails: Boolean, interactive: Boolean = true
) {
    artwork(poster.song, Modifier.fillMaxSize())
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.97f)))))
    // Details wait for the expansion; rasterizing paragraphs into a moving tiny card causes
    // visible text reflow and wastes layout work on every spring frame.
    if (showDetails) {
        val compact = height < 350f
        Column(Modifier.fillMaxSize().padding(if (compact) 12.dp else 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (compact) poster.song.title.ifBlank { poster.song.fileName }
                    else listOfNotNull(stringResource(if (current) R.string.poster_wall_current else R.string.poster_wall_selected), stackPosition).joinToString(" · "),
                    color = Color.White.copy(alpha = if (compact) 1f else 0.72f),
                    fontSize = if (compact) 20.sp else 11.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (current && onFullscreen != null) PosterIconButton(
                    stringResource(R.string.poster_wall_fullscreen_player), onFullscreen,
                    if (compact) Modifier.size(32.dp) else Modifier, enabled = interactive
                ) {
                    Icon(PosterFullscreenIcon, stringResource(R.string.poster_wall_fullscreen_player), tint = Color.White, modifier = Modifier.size(20.dp))
                }
                PosterIconButton(stringResource(R.string.common_close), close, if (compact) Modifier.size(32.dp) else Modifier, enabled = interactive) {
                    Icon(MiuixIcons.Regular.Close, stringResource(R.string.common_close), tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            if (interactive && current && !compact) lyrics(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            if (!compact) Text(poster.song.title.ifBlank { poster.song.fileName }, color = Color.White, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(poster.song.artist.ifBlank { stringResource(R.string.player_unknown_artist) }, color = PosterMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (compact) 0.dp else 6.dp, bottom = if (compact) 4.dp else 12.dp))
            if (interactive && current) seekBar(compact) else Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (interactive && current) CompositionLocalProvider(LocalPosterTransportFocus provides transportFocus) { controls() }
                    else {
                        var focused by remember { mutableStateOf(false) }
                        Row(Modifier.clip(CircleShape).background(Color.White).focusRequester(transportFocus)
                            .onFocusChanged { focused = it.isFocused }
                            .border(if (focused) 2.dp else 0.dp, MiuixTheme.colorScheme.primary, CircleShape)
                            .then(if (interactive) Modifier.clickable(onClick = onPlay) else Modifier).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_player_play), null, tint = PosterInk, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.common_play), color = PosterInk, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                PosterIconButton(stringResource(R.string.player_more_actions), onMore, enabled = interactive) {
                    Icon(MiuixIcons.Regular.More, stringResource(R.string.player_more_actions), tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

internal data class PosterMorphFrame(val rect: PosterRect, val cornerRadius: Float)

internal fun posterMorphFrame(origin: PosterRect, target: PosterRect, originRadius: Float, progress: Float): PosterMorphFrame {
    val fraction = progress.coerceIn(0f, 1f)
    fun mix(start: Float, end: Float) = start + (end - start) * fraction
    return PosterMorphFrame(PosterRect(mix(origin.x, target.x), mix(origin.y, target.y),
        mix(origin.width, target.width), mix(origin.height, target.height)), mix(originRadius, 24f))
}
