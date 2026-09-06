package com.ella.music.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.MusicVideoLauncher
import com.ella.music.R
import com.ella.music.data.DOLBY_MARK
import com.ella.music.data.SettingsManager
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SongItem(
    song: Song,
    isPlaying: Boolean = false,
    isCurrent: Boolean = false,
    albumArtUri: Uri? = null,
    loadCoverArt: ((Song) -> Bitmap?)? = null,
    loadAudioInfo: ((Song) -> AudioInfo)? = null,
    loadSongTagInfo: ((Song) -> SongTagInfo)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onPlayNext: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onMusicVideo: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    titleOverride: String? = null,
    leadingLabel: String? = null,
    leadingLabelBeforeCover: Boolean = false,
    showAlbumInSubtitle: Boolean = true,
    isFavorite: Boolean = false,
    loadSongRating: ((Song) -> Int)? = null,
    ratingRevision: Int = 0,
    ratingDisplayMode: Int? = null,
    titleMarqueeEnabledOverride: Boolean? = null,
    showPlayNextInLists: Boolean = false,
    compactMultiRow: Boolean = false,
    dragSelectedSongs: List<Song> = emptyList(),
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    showTrailingContentInSelectionMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val unknown = stringResource(R.string.common_unknown)
    val unknownArtist = stringResource(R.string.player_unknown_artist)
    val localMusicVideoDescription = stringResource(R.string.local_mv)
    val onlineMusicVideoDescription = stringResource(R.string.online_mv)
    val context = androidx.compose.ui.platform.LocalContext.current
    val televisionDevice = remember(context) {
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
            (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
    val sourceView = LocalView.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    // Large library screens can contain dozens of visible rows. Let their parent collect these
    // stable display preferences once and pass them down; other callers keep the self-contained
    // fallback collection for backwards-compatible behavior.
    val effectiveRatingDisplayMode = ratingDisplayMode ?: settingsManager.songRatingDisplayMode
        .collectAsState(initial = SettingsManager.SONG_RATING_DISPLAY_STAR_NUMBER)
        .value
    val titleMarqueeEnabled = titleMarqueeEnabledOverride ?: settingsManager.librarySongTitleMarquee
        .collectAsState(initial = true)
        .value
    val compactTitleMarquee = compactMultiRow && titleMarqueeEnabled
    val coverState = rememberSongArtworkState(
        song = song,
        albumArtUri = albumArtUri,
        loadCoverArt = loadCoverArt,
        usage = ArtworkUsage.ListThumbnail,
        showDefaultWhenMissing = false
    )
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song.id, loadAudioInfo) {
        value = withContext(Dispatchers.IO) { loadAudioInfo?.invoke(song) }
    }
    val videoActions = rememberSongListVideoActions(song, loadSongTagInfo, enabled = !selectionMode)
    val localMusicVideoSource = videoActions.localSource
    val onlineMusicVideoUrl = videoActions.onlineUrl
    val qualityTag = audioInfo?.let { audioQualitySummary(it).listTag }
    val rating by produceState<Int>(initialValue = 0, song.id, song.dateModified, ratingRevision, loadSongRating) {
        value = withContext(Dispatchers.IO) { loadSongRating?.invoke(song) ?: 0 }
    }
    val coverModel = coverState.model
    var focused by remember { mutableStateOf(false) }
    val itemShape = remember { RoundedCornerShape(14.dp) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (televisionDevice) {
                    Modifier
                        .clip(itemShape)
                        .onFocusChanged { focused = it.hasFocus }
                        .border(
                            width = if (focused) 3.dp else 0.dp,
                            color = if (focused) MiuixTheme.colorScheme.primary else Color.Transparent,
                            shape = itemShape
                        )
                } else {
                    Modifier
                }
            )
            .background(
                when {
                    televisionDevice && focused -> MiuixTheme.colorScheme.primary.copy(alpha = 0.20f)
                    selected -> MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
                    else -> Color.Transparent
                }
            )
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
            .padding(
                horizontal = if (compactMultiRow) 10.dp else 16.dp,
                vertical = if (compactMultiRow) 8.dp else 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            SelectionCheck(
                selected = selected,
                checkColor = Color.White
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        if (leadingLabelBeforeCover && !leadingLabel.isNullOrBlank()) {
            Text(
                text = leadingLabel,
                fontSize = 14.sp,
                color = if (isCurrent) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.width(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        val coverSize = if (compactMultiRow) 64.dp else 48.dp
        Box(
            modifier = Modifier
                .size(coverSize)
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (coverModel != null) {
                SafeCoverImage(
                    model = coverModel,
                    contentDescription = null,
                    modifier = Modifier.size(coverSize),
                    contentScale = ContentScale.Crop,
                    // These thumbnails render at 48/64dp. Decode at a modest 2x density target
                    // instead of 384/512px per row; this keeps the same crisp appearance while
                    // reducing bitmap memory and GPU upload work during fast scrolling.
                    sizePx = if (compactMultiRow) 320 else 256,
                    showDefaultPlaceholder = false
                )
            } else {
                DefaultAlbumCover(modifier = Modifier.size(coverSize))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (!leadingLabelBeforeCover && !leadingLabel.isNullOrBlank()) {
            Text(
                text = leadingLabel,
                fontSize = 14.sp,
                color = if (isCurrent) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.width(28.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExplicitSongTitle(
                    title = titleOverride?.takeIf { it.isNotBlank() } ?: song.title,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrent) androidx.compose.ui.text.font.FontWeight.Bold else null,
                    color = if (isCurrent) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = if (compactTitleMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                    titleModifier = if (compactTitleMarquee) {
                        Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately
                        )
                    } else {
                        Modifier
                    },
                    titleFillMaxWidth = compactTitleMarquee
                )
                if (isFavorite) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_notification_favorite_filled),
                        contentDescription = stringResource(R.string.common_favorite),
                        tint = Color(0xFFFF4D6D),
                        modifier = Modifier.size(13.dp)
                    )
                }
                if (rating > 0) {
                    Spacer(modifier = Modifier.width(5.dp))
                    SongRatingIndicator(
                        rating = rating,
                        displayMode = effectiveRatingDisplayMode
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (qualityTag != null) {
                    AudioQualityListBadge(qualityTag)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = if (showAlbumInSubtitle) {
                        listOf(song.artist, song.album)
                            .map { it.ifBlank { unknown } }
                            .joinToString(" · ")
                    } else {
                        song.artist.ifBlank { unknownArtist }
                    },
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = if (compactTitleMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (compactTitleMarquee) {
                                Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    animationMode = MarqueeAnimationMode.Immediately
                                )
                            } else {
                                Modifier
                            }
                        )
                )
            }
            if (compactMultiRow) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.durationText,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (!selectionMode && showPlayNextInLists && onPlayNext != null) {
                        PlayNextQuickButton(onClick = onPlayNext)
                    }
                    if (!selectionMode && onMore != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onMore),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Regular.More,
                                contentDescription = stringResource(R.string.player_quick_more),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        if (!compactMultiRow) Spacer(modifier = Modifier.width(8.dp))

        if (!selectionMode) {
            localMusicVideoSource?.let { source ->
                MusicVideoListAction(
                    label = stringResource(R.string.library_search_filter_mv),
                    contentDescription = localMusicVideoDescription,
                    color = MiuixTheme.colorScheme.primary,
                    onClick = { MusicVideoLauncher.open(context, song, source) }
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            onlineMusicVideoUrl?.takeUnless { localMusicVideoSource != null }?.let { url ->
                MusicVideoListAction(
                    label = stringResource(R.string.online_mv),
                    contentDescription = onlineMusicVideoDescription,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    onClick = { openSongExternalUrl(context, url) }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (onMusicVideo != null) {
                MusicVideoListAction(
                    label = stringResource(R.string.online_mv),
                    contentDescription = onlineMusicVideoDescription,
                    color = MiuixTheme.colorScheme.primary,
                    onClick = onMusicVideo
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        if (!compactMultiRow) Text(
            text = song.durationText,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        if (!compactMultiRow && !selectionMode && showPlayNextInLists && onPlayNext != null) {
            Spacer(modifier = Modifier.width(8.dp))
            PlayNextQuickButton(onClick = onPlayNext)
        }
        if (!selectionMode && onDownload != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(onClick = onDownload),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Download,
                    contentDescription = stringResource(R.string.player_download_lx_song),
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (!selectionMode && onRemove != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFE5484D).copy(alpha = 0.12f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "×",
                    fontSize = 16.sp,
                    color = Color(0xFFE5484D)
                )
            }
        }
        if (!compactMultiRow && !selectionMode && onMore != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.More,
                    contentDescription = stringResource(R.string.player_quick_more),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if ((showTrailingContentInSelectionMode || !selectionMode) && trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingContent()
        }
    }
}

@Composable
internal fun MusicVideoListAction(
    label: String,
    contentDescription: String,
    color: Color,
    onClick: () -> Unit
) {
    Text(
        text = label,
        fontSize = 11.sp,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.12f))
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 3.dp)
    )
}

internal fun openSongExternalUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable
fun PlayNextQuickButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playNextDescription = stringResource(R.string.song_more_play_next)
    Text(
        text = "+",
        fontSize = 18.sp,
        color = MiuixTheme.colorScheme.primary,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .semantics {
                contentDescription = playNextDescription
                role = Role.Button
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
internal fun AudioQualityListBadge(tag: String) {
    val color = audioQualityTagColor(tag)
    val isDolby = tag == DOLBY_MARK
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(
                horizontal = if (isDolby) 3.dp else 4.dp,
                vertical = if (isDolby) 2.dp else 1.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isDolby) {
            Icon(
                painter = painterResource(R.drawable.ic_dolby),
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .height(8.dp)
                    .aspectRatio(85f / 52f)
            )
        } else {
            Text(
                text = tag,
                fontSize = 9.sp,
                color = color
            )
        }
    }
}

internal fun audioQualityTagColor(tag: String): Color {
    return when (tag) {
        "AC3", "EC3", "EAC3", "SUR", DOLBY_MARK -> Color(0xFF6EE7FF)
        "MQ" -> Color(0xFFFF8F3D)
        "HR" -> Color(0xFFFFC23A)
        "SQ" -> Color(0xFF9B59FF)
        "HQ" -> Color(0xFF3D83FF)
        "LQ" -> Color(0xFF34C56E)
        else -> Color(0xFF9E9E9E)
    }
}
