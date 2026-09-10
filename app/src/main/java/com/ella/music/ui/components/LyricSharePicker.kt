package com.ella.music.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Share

import androidx.compose.ui.graphics.luminance

private val LocalShareContentColor = staticCompositionLocalOf { Color.White }

@Composable
fun LyricSharePicker(
    song: Song?,
    lyrics: List<LyricLine>,
    initialLine: LyricLine,
    cover: Bitmap?,
    backgroundColors: List<Color>,
    contentColor: Color = Color.White,
    annotation: String = "",
    customInfo: String = "",
    shareTypeface: android.graphics.Typeface? = null,
    onDismiss: () -> Unit,
    onShare: (List<LyricLine>, LyricShareOptions) -> Unit,
    onCopy: (List<LyricLine>, LyricShareOptions) -> Unit,
    onSaveImage: (List<LyricLine>, LyricShareOptions) -> Unit,
    onVideoShare: ((List<LyricLine>, LyricShareOptions) -> Unit)? = null
) {
    BackHandler(onBack = onDismiss)

    val initialIndex = remember(lyrics, initialLine) {
        lyrics.indexOfFirst { it.timeMs == initialLine.timeMs && it.text == initialLine.text }
            .takeIf { it >= 0 }
            ?: lyrics.indexOf(initialLine).takeIf { it >= 0 }
            ?: 0
    }
    var selectedIndexes by remember(lyrics, initialIndex) { mutableStateOf(setOf(initialIndex)) }
    var includeOriginal by remember { mutableStateOf(true) }
    var includeTranslation by remember { mutableStateOf(true) }
    var includePronunciation by remember { mutableStateOf(true) }
    var appendEllipsis by remember { mutableStateOf(false) }
    var cardStyle by remember { mutableStateOf(LyricShareCardStyle.Current) }
    val listState = rememberLazyListState()

    LaunchedEffect(initialIndex) {
        listState.scrollToItem((initialIndex - 4).coerceAtLeast(0))
    }

    val selectedLines = remember(selectedIndexes, lyrics) {
        selectedIndexes
            .sorted()
            .mapNotNull(lyrics::getOrNull)
            .ifEmpty { listOf(initialLine) }
    }
    val colors = backgroundColors.ifEmpty {
        listOf(Color(0xFF301E1F), Color(0xFF221F1E), Color(0xFF0D0C0E))
    }

    val onToggleSelection: (Int, Boolean) -> Unit = { index, selected ->
        selectedIndexes = if (selected) {
            selectedIndexes - index
        } else {
            selectedIndexes + index
        }
    }

    val shareOptions = LyricShareOptions(
        includeOriginal = includeOriginal,
        includeTranslation = includeTranslation,
        includePronunciation = includePronunciation,
        appendEllipsis = appendEllipsis,
        style = cardStyle
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.first().copy(alpha = 0.98f),
                        colors.getOrElse(1) { colors.first() }.copy(alpha = 0.98f),
                        colors.last().copy(alpha = 1f)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        CompositionLocalProvider(LocalShareContentColor provides contentColor) {
        val landscape = maxWidth > maxHeight
        Column(modifier = Modifier.fillMaxSize()) {
            LyricShareHeader(
                selectedCount = selectedIndexes.size,
                shareEnabled = selectedIndexes.isNotEmpty(),
                selectedTotal = lyrics.size,
                videoShareEnabled = onVideoShare != null,
                onDismiss = onDismiss,
                onShare = {
                    selectedIndexes
                        .sorted()
                        .mapNotNull(lyrics::getOrNull)
                        .takeIf { it.isNotEmpty() }
                        ?.let { onShare(it, shareOptions) }
                },
                onCopy = {
                    selectedIndexes
                        .sorted()
                        .mapNotNull(lyrics::getOrNull)
                        .takeIf { it.isNotEmpty() }
                        ?.let { onCopy(it, shareOptions) }
                },
                onSaveImage = {
                    selectedIndexes
                        .sorted()
                        .mapNotNull(lyrics::getOrNull)
                        .takeIf { it.isNotEmpty() }
                        ?.let { onSaveImage(it, shareOptions) }
                },
                onVideoShare = onVideoShare?.let { callback ->
                    {
                        selectedIndexes
                            .sorted()
                            .mapNotNull(lyrics::getOrNull)
                            .takeIf { it.isNotEmpty() }
                            ?.let { callback(it, shareOptions) }
                    }
                }
            )

            if (landscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LyricSharePreviewCard(
                            song = song,
                            annotation = annotation,
                            customInfo = customInfo,
                            cover = cover,
                            colors = colors,
                            lines = selectedLines,
                            shareTypeface = shareTypeface,
                            options = shareOptions,
                            fitHeight = true,
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        LyricShareOptionsToggle(
                            options = shareOptions,
                            onOptionsChange = {
                                includeOriginal = it.includeOriginal
                                includeTranslation = it.includeTranslation
                                includePronunciation = it.includePronunciation
                                appendEllipsis = it.appendEllipsis
                                cardStyle = it.style
                            }
                        )
                        LyricShareLineList(
                            lyrics = lyrics,
                            selectedIndexes = selectedIndexes,
                            listState = listState,
                            onToggleSelection = onToggleSelection,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LyricSharePreviewCard(
                        song = song,
                        annotation = annotation,
                        customInfo = customInfo,
                        cover = cover,
                        colors = colors,
                        lines = selectedLines,
                        shareTypeface = shareTypeface,
                        options = shareOptions,
                        fitHeight = true,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
                LyricShareOptionsToggle(
                    options = shareOptions,
                    onOptionsChange = {
                        includeOriginal = it.includeOriginal
                        includeTranslation = it.includeTranslation
                        includePronunciation = it.includePronunciation
                        appendEllipsis = it.appendEllipsis
                        cardStyle = it.style
                    }
                )
                LyricShareLineList(
                    lyrics = lyrics,
                    selectedIndexes = selectedIndexes,
                    listState = listState,
                    onToggleSelection = onToggleSelection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
        } // close CompositionLocalProvider
    }
}

@Composable
private fun LyricShareHeader(
    selectedCount: Int,
    selectedTotal: Int,
    shareEnabled: Boolean,
    videoShareEnabled: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onSaveImage: () -> Unit,
    onVideoShare: (() -> Unit)? = null
) {
    val contentColor = LocalShareContentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = MiuixIcons.Regular.Back,
                contentDescription = stringResource(R.string.common_back),
                tint = contentColor
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.lyric_share_picker_title),
                color = contentColor,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(
                    R.string.lyric_share_selected_count,
                    selectedCount,
                    selectedTotal
                ),
                color = contentColor.copy(alpha = 0.56f),
                fontSize = 12.sp
            )
        }
        IconButton(onClick = onCopy) {
            Icon(
                imageVector = MiuixIcons.Regular.Copy,
                contentDescription = stringResource(R.string.lyric_share_copy),
                tint = if (!shareEnabled) contentColor.copy(alpha = 0.34f) else contentColor
            )
        }
        if (videoShareEnabled && onVideoShare != null) {
            IconButton(onClick = onVideoShare) {
                Icon(
                    imageVector = MiuixIcons.Regular.Play,
                    contentDescription = stringResource(R.string.lyric_video_share_chooser_title),
                    tint = if (!shareEnabled) contentColor.copy(alpha = 0.34f) else contentColor
                )
            }
        }
        IconButton(onClick = onShare) {
            Icon(
                imageVector = MiuixIcons.Regular.Share,
                contentDescription = stringResource(R.string.common_share),
                tint = if (!shareEnabled) contentColor.copy(alpha = 0.34f) else contentColor
            )
        }
        // Keep saving as the rightmost action, matching the share picker contract.
        IconButton(onClick = onSaveImage) {
            Icon(
                imageVector = MiuixIcons.Regular.Download,
                contentDescription = stringResource(R.string.lyric_share_save_image),
                tint = if (!shareEnabled) contentColor.copy(alpha = 0.34f) else contentColor
            )
        }
    }
}

@Composable
private fun LyricShareOptionsToggle(
    options: LyricShareOptions,
    onOptionsChange: (LyricShareOptions) -> Unit
) {
    val contentColor = LocalShareContentColor.current
    val fields = listOf(
        stringResource(R.string.lyric_share_original) to options.includeOriginal,
        stringResource(R.string.lyric_share_translation) to options.includeTranslation,
        stringResource(R.string.lyric_share_pronunciation) to options.includePronunciation
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(contentColor.copy(alpha = 0.10f))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        fields.forEachIndexed { index, (label, selected) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (selected) contentColor.copy(alpha = 0.20f) else Color.Transparent)
                    .clickable {
                        onOptionsChange(
                            when (index) {
                                0 -> options.copy(includeOriginal = !selected)
                                1 -> options.copy(includeTranslation = !selected)
                                else -> options.copy(includePronunciation = !selected)
                            }
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (selected) "✓ $label" else label,
                        color = contentColor.copy(alpha = if (selected) 1f else 0.56f),
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.lyric_share_style),
            color = contentColor.copy(alpha = 0.70f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 2.dp)
        )
        listOf(
            LyricShareCardStyle.Current to stringResource(R.string.lyric_share_style_current),
            LyricShareCardStyle.LegacyTopMetadata to stringResource(R.string.lyric_share_style_legacy)
        ).forEach { (style, label) ->
            val selected = options.style == style
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) contentColor.copy(alpha = 0.20f) else Color.Transparent)
                    .clickable { onOptionsChange(options.copy(style = style)) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = contentColor.copy(alpha = if (selected) 1f else 0.56f),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LyricShareLineList(
    lyrics: List<LyricLine>,
    selectedIndexes: Set<Int>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onToggleSelection: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(lyrics) { index, line ->
            val selected = index in selectedIndexes
            LyricSharePickerRow(
                line = line,
                selected = selected,
                onClick = { onToggleSelection(index, selected) }
            )
        }
        item { Spacer(modifier = Modifier.height(18.dp)) }
    }
}

@Composable
private fun LyricSharePickerRow(
    line: LyricLine,
    selected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = LocalShareContentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) contentColor.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) contentColor else contentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                // The checkmark dot contrasts with the circle: dark dot on light circle,
                // light dot on dark circle.
                val dotColor = if (contentColor.luminance() > 0.5f) Color(0xFF111111) else Color.White
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
        Column(
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f)
        ) {
            Text(
                text = line.text.ifBlank { line.backgroundText.orEmpty().ifBlank { "\u266a" } },
                color = contentColor.copy(alpha = if (selected) 1f else 0.76f),
                fontSize = 17.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            val secondary = listOfNotNull(
                line.translation?.takeIf { it.isNotBlank() },
                line.backgroundTranslation?.takeIf { it.isNotBlank() }
            ).firstOrNull()
            if (!secondary.isNullOrBlank()) {
                Text(
                    text = secondary,
                    color = contentColor.copy(alpha = if (selected) 0.62f else 0.42f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun LyricSharePreviewCard(
    song: Song?,
    annotation: String,
    customInfo: String,
    cover: Bitmap?,
    colors: List<Color>,
    lines: List<LyricLine>,
    shareTypeface: android.graphics.Typeface?,
    options: LyricShareOptions,
    fitHeight: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val backgroundPalette = remember(colors) {
        colors.map(Color::toArgb)
    }
    val content = remember(song, annotation, customInfo, lines, backgroundPalette, options) {
        buildLyricShareCardContent(
            context = context,
            song = song,
            lines = lines,
            backgroundColors = backgroundPalette,
            annotation = annotation,
            customInfo = customInfo,
            includeOriginal = options.includeOriginal,
            includeTranslation = options.includeTranslation,
            includePronunciation = options.includePronunciation,
            appendEllipsis = options.appendEllipsis,
            style = options.style
        )
    }
    val layout = remember(content, shareTypeface) {
        runCatching { calculateLyricShareLayout(content, shareTypeface = shareTypeface) }.getOrNull()
    }
    val previewBitmap = remember(content, layout, cover) {
        layout?.let { runCatching { renderLyricShareCardBitmap(content, it, cover) }.getOrNull() }
    }

    DisposableEffect(previewBitmap) {
        onDispose {
            previewBitmap?.takeUnless { it.isRecycled }?.recycle()
        }
    }

    if (previewBitmap != null && layout != null) {
        Image(
            bitmap = previewBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .aspectRatio(
                    ratio = layout.canvasWidth.toFloat() / layout.adaptiveCanvasHeight.toFloat(),
                    matchHeightConstraintsFirst = fitHeight
                )
        )
    }
}
