package com.ella.music.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.EllaMiuixMenuItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.round

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun PlayerActionMenuHeader(
    song: Song?,
    onArtist: () -> Unit,
    onAlbum: () -> Unit,
    onPreviewCover: () -> Unit
) {
    val title = song?.let {
        it.title.ifBlank { it.fileName.ifBlank { stringResource(R.string.player_unknown_song) } }
    } ?: stringResource(R.string.player_no_song_playing)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallCover(
            song = song,
            embeddedCover = null,
            modifier = Modifier
                .size(68.dp)
                .combinedClickable(onClick = {}, onLongClick = onPreviewCover)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 19.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(5.dp))
            PlayerActionMenuSubtitle(
                song = song,
                onArtist = onArtist,
                onAlbum = onAlbum
            )
        }
    }
}

@Composable
private fun PlayerActionMenuSubtitle(
    song: Song?,
    onArtist: () -> Unit,
    onAlbum: () -> Unit
) {
    if (song == null) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        return
    }
    val unknownArtist = stringResource(R.string.player_unknown_artist)
    val artist = song.artist.ifBlank { unknownArtist }
    val album = song.album.trim()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = artist,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = song.artist.isNotBlank(), onClick = onArtist)
        )
        if (album.isNotBlank()) {
            Text(
                text = album,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onAlbum)
            )
        }
    }
}

@Composable
internal fun PlayerActionShortcutRow(
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onTimer: () -> Unit,
    onSpeed: () -> Unit,
    onOpenEqualizer: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PlayerActionShortcut(
            label = stringResource(R.string.player_speed_pitch),
            kind = PlayerQuickActionKind.Speed,
            onClick = onSpeed,
            modifier = Modifier.weight(1f)
        )
        PlayerActionShortcut(
            label = stringResource(R.string.player_equalizer),
            kind = PlayerQuickActionKind.Equalizer,
            onClick = onOpenEqualizer,
            modifier = Modifier.weight(1f)
        )
        PlayerActionShortcut(
            label = stringResource(R.string.player_sleep_timer),
            kind = PlayerQuickActionKind.Timer,
            onClick = onTimer,
            modifier = Modifier.weight(1f)
        )
        PlayerActionShortcut(
            label = stringResource(R.string.player_add_to_playlist),
            kind = PlayerQuickActionKind.Add,
            onClick = onAddToPlaylist,
            modifier = Modifier.weight(1f)
        )
        PlayerActionShortcut(
            label = stringResource(R.string.song_more_play_next),
            kind = PlayerQuickActionKind.PlayNext,
            onClick = onPlayNext,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlayerActionShortcut(
    label: String,
    kind: PlayerQuickActionKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(82.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        QuickActionIcon(
            kind = kind,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.82f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun PlayerActionMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.60f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        content = content
    )
}

@Composable
internal fun HalfSheetTitle(title: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Back,
                contentDescription = stringResource(R.string.common_back),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
internal fun HalfSheetPill(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun DottedValueSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val safeValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val fraction = ((safeValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val activeDotColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.72f)
    val inactiveDotColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.28f)
    val activeLineColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.88f)
    val activeKnobColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.92f)

    var latestValue by remember(valueRange, steps) { mutableFloatStateOf(safeValue) }
    fun update(width: Float, x: Float): Float {
        val raw = valueRange.start + (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f) *
            (valueRange.endInclusive - valueRange.start)
        val stepped = if (steps > 0) {
            val stepSize = (valueRange.endInclusive - valueRange.start) / steps
            val stepIndex = round((raw - valueRange.start) / stepSize)
            (valueRange.start + stepIndex * stepSize).coerceIn(valueRange.start, valueRange.endInclusive)
        } else {
            raw
        }
        latestValue = stepped
        onValueChange(stepped)
        return stepped
    }

    BoxWithConstraints(modifier = modifier) {
        val knobOffset = (maxWidth - 46.dp) * fraction
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(valueRange, steps) {
                    detectTapGestures { offset ->
                        onValueChangeFinished?.invoke(update(size.width.toFloat(), offset.x))
                    }
                }
                .pointerInput(valueRange, steps) {
                    detectDragGestures(
                        onDragEnd = { onValueChangeFinished?.invoke(latestValue) },
                        onDragCancel = { onValueChangeFinished?.invoke(latestValue) }
                    ) { change, _ ->
                        change.consume()
                        update(size.width.toFloat(), change.position.x)
                    }
                }
        ) {
            val centerY = size.height * 0.60f
            val dotCount = 44
            val gap = size.width / (dotCount - 1).coerceAtLeast(1)
            for (index in 0 until dotCount) {
                val dotFraction = index.toFloat() / (dotCount - 1).coerceAtLeast(1)
                drawCircle(
                    color = if (dotFraction <= fraction) activeDotColor else inactiveDotColor,
                    radius = if (index % 5 == 0) 4.2f else 3.2f,
                    center = Offset(x = gap * index, y = centerY)
                )
            }
            val knobX = size.width * fraction
            drawLine(
                color = activeLineColor,
                start = Offset(knobX, centerY - 36f),
                end = Offset(knobX, centerY + 36f),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = activeKnobColor,
                radius = 24f,
                center = Offset(knobX, centerY - 54f)
            )
        }
        label?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = knobOffset)
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MiuixTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
internal fun PlayerActionMenuItem(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    EllaMiuixMenuItem(text = text, onClick = onClick, danger = danger)
}
