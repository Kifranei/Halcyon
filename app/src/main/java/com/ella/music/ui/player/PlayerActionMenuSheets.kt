package com.ella.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.ActionMenuIds
import com.ella.music.data.model.Song
import com.ella.music.ui.components.EllaMiuixMenuItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun PlayerActionMenuHeader(
    song: Song?,
    embeddedCover: android.graphics.Bitmap?,
    onArtist: () -> Unit,
    onAlbum: () -> Unit,
    onPreviewCover: () -> Unit
) {
    val title = song?.let {
        it.title.ifBlank { it.fileName.ifBlank { stringResource(R.string.player_unknown_song) } }
    } ?: stringResource(R.string.player_no_song_playing)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallCover(
                song = song,
                embeddedCover = embeddedCover,
                modifier = Modifier
                    .size(68.dp)
                    .combinedClickable(onClick = onPreviewCover, onLongClick = onPreviewCover)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                PlayerActionMenuSubtitle(
                    song = song,
                    onArtist = onArtist,
                    onAlbum = onAlbum
                )
            }
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
    shortcutIds: List<String>,
    onActionClick: (String) -> Unit,
    sleepTimerEndRealtimeMs: Long? = null
) {
    val timerRemaining = rememberSleepTimerRemaining(sleepTimerEndRealtimeMs)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        shortcutIds.forEach { id ->
            PlayerActionShortcut(
                id = id,
                onClick = { onActionClick(id) },
                caption = if (id == ActionMenuIds.TIMER) timerRemaining else null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlayerActionShortcut(
    id: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    Column(
        modifier = modifier
            .heightIn(min = 82.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        com.ella.music.ui.settings.PlayerShortcutItemIcon(
            id = id,
            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.82f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        val label = com.ella.music.ui.settings.playerShortcutLabel(id)
        Text(
            text = if (!caption.isNullOrBlank()) "$label\n$caption" else label,
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
    com.ella.music.ui.components.EllaMiuixActionMenuGroup(content = content)
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
    outlined: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    top.yukonga.miuix.kmp.basic.Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (outlined) {
                    Modifier.border(
                        width = 1.dp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.38f),
                        shape = shape
                    )
                } else {
                    Modifier
                }
            ),
        minWidth = 0.dp,
        minHeight = 48.dp,
        cornerRadius = 14.dp,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 14.dp),
        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(
            color = if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
            else MiuixTheme.colorScheme.secondaryContainer,
            contentColor = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
        )
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.button,
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
    val sliderColors = SliderDefaults.sliderColors(
        foregroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.88f),
        backgroundColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.28f),
        thumbColor = Color.White,
        keyPointColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f),
        keyPointForegroundColor = Color.White.copy(alpha = 0.72f)
    )

    if (label == null) {
        Slider(
            value = safeValue,
            onValueChange = { next ->
                onValueChange(next.coerceIn(valueRange.start, valueRange.endInclusive))
            },
            onValueChangeFinished = { onValueChangeFinished?.invoke(safeValue) },
            valueRange = valueRange,
            steps = (steps - 1).coerceAtLeast(0),
            showKeyPoints = true,
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            colors = sliderColors,
            modifier = modifier
        )
    } else {
        val fraction = ((safeValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        BoxWithConstraints(modifier = modifier) {
            // Miuix owns drag semantics, keyboard/accessibility actions, haptics, and key-point
            // rendering. Keep the value bubble as a small overlay that follows the thumb.
            val labelWidth = 96.dp
            val maxLabelOffset = (maxWidth - labelWidth).coerceAtLeast(0.dp)
            val labelOffset = maxLabelOffset * fraction
            Box(modifier = Modifier.fillMaxSize()) {
                Slider(
                    value = safeValue,
                    onValueChange = { next ->
                        onValueChange(next.coerceIn(valueRange.start, valueRange.endInclusive))
                    },
                    onValueChangeFinished = { onValueChangeFinished?.invoke(safeValue) },
                    valueRange = valueRange,
                    // Miuix counts intermediate key points; the old helper counted intervals.
                    steps = (steps - 1).coerceAtLeast(0),
                    showKeyPoints = true,
                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                    colors = sliderColors,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(top = 26.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = labelOffset)
                        .width(labelWidth)
                        .padding(top = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MiuixTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MiuixTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlayerActionMenuItem(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    subtitle: String? = null
) {
    EllaMiuixMenuItem(text = text, onClick = onClick, danger = danger, icon = icon, subtitle = subtitle)
}
