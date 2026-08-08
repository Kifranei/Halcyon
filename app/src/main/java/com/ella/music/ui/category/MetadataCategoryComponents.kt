package com.ella.music.ui.category

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.FolderOutlineIcon
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.isAppWallpaperVisible
import com.ella.music.ui.components.rememberSongArtworkState
import com.ella.music.viewmodel.MetadataCategoryItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun MetadataCategoryCard(
    type: String,
    item: MetadataCategoryItem,
    sortMode: MetadataCategorySortMode,
    albumArtUri: android.net.Uri?,
    representativeSong: Song? = null,
    loadCoverArt: ((Song) -> Bitmap?)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val coverState = rememberSongArtworkState(
        song = representativeSong,
        albumArtUri = albumArtUri,
        loadCoverArt = loadCoverArt,
        usage = ArtworkUsage.ArtistImage,
        showDefaultWhenMissing = false
    )
    val coverModel: Any? = coverState.model

    when (type) {
        "folder" -> {
            FolderCategoryRow(
                item = item,
                sortMode = sortMode,
                coverModel = coverModel,
                selectionMode = selectionMode,
                selected = selected,
                isPinned = isPinned,
                onClick = onClick,
                onLongClick = onLongClick
            )
            return
        }
        "composer", "arranger", "lyricist" -> {
            PersonCategoryRow(
                item = item,
                sortMode = sortMode,
                coverModel = coverModel,
                selectionMode = selectionMode,
                selected = selected,
                isPinned = isPinned,
                onClick = onClick,
                onLongClick = onLongClick
            )
            return
        }
    }

    val wallpaperVisible = isAppWallpaperVisible()
    val cardColor = remember(item.name, wallpaperVisible) {
        item.name.categoryCardColor().copy(alpha = if (wallpaperVisible) 0.62f else 1f)
    }
    val hasCover = coverModel != null
    val isGenreCard = type == "genre"
    val useSmallCover = type == "genre" || type == "year"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isGenreCard) 112.dp else 116.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        cardColor,
                        cardColor.darkenCategoryColor(0.78f)
                    )
                )
            )
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MiuixTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.08f))
        )
        if (coverModel != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
                    .size(if (useSmallCover) 54.dp else 78.dp)
                    .graphicsLayer {
                        rotationZ = 13f
                        translationX = if (useSmallCover) 9.dp.toPx() else 16.dp.toPx()
                        translationY = if (useSmallCover) 3.dp.toPx() else 6.dp.toPx()
                    }
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                SafeCoverImage(
                    model = coverModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    sizePx = if (useSmallCover) 128 else 220,
                    showDefaultPlaceholder = false
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, cardColor.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.16f))
                        )
                    )
            )
        }
        if (selectionMode) {
            SelectionBadge(
                selected = selected,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 14.dp,
                    top = if (isGenreCard) 12.dp else 13.dp,
                    end = if (hasCover) {
                        if (useSmallCover) 54.dp else 72.dp
                    } else {
                        14.dp
                    },
                    bottom = 12.dp
                ),
            // Small-cover cards (genre/year) keep the title and count on adjacent lines instead of
            // spreading them apart, so there is no empty gap between e.g. the year and song count.
            verticalArrangement = if (useSmallCover) Arrangement.Center else Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    fontSize = if (isGenreCard) 13.sp else 16.sp,
                    lineHeight = if (isGenreCard) 17.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = if (isGenreCard) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPinned) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        imageVector = MiuixIcons.Regular.Pin,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = item.categorySortSummary(sortMode),
                fontSize = if (isGenreCard) 10.sp else 12.sp,
                lineHeight = if (isGenreCard) 13.sp else 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FolderCategoryRow(
    item: MetadataCategoryItem,
    sortMode: MetadataCategorySortMode,
    coverModel: Any?,
    selectionMode: Boolean,
    selected: Boolean,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (coverModel != null) {
                SafeCoverImage(
                    model = coverModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    sizePx = 160,
                    showDefaultPlaceholder = false
                )
            } else {
                FolderOutlineIcon(
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name.substringAfterLast('/').ifBlank { item.name.ifBlank { stringResource(R.string.folder_root) } },
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPinned) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        imageVector = MiuixIcons.Regular.Pin,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${item.folderSortSummary(sortMode)} · ${item.name}",
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (selectionMode) {
            Spacer(modifier = Modifier.size(8.dp))
            SelectionBadge(selected = selected)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PersonCategoryRow(
    item: MetadataCategoryItem,
    sortMode: MetadataCategorySortMode,
    coverModel: Any?,
    selectionMode: Boolean,
    selected: Boolean,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (coverModel != null) {
                SafeCoverImage(
                    model = coverModel,
                    contentDescription = null,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    sizePx = 128,
                    showDefaultPlaceholder = false
                )
            } else {
                Icon(
                    imageVector = MiuixIcons.Regular.Music,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name.ifBlank { stringResource(R.string.common_unknown) },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPinned) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        imageVector = MiuixIcons.Regular.Pin,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = item.personSortSummary(sortMode),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selectionMode) {
            Spacer(modifier = Modifier.size(8.dp))
            SelectionBadge(selected = selected)
        }
    }
}

@Composable
private fun SelectionBadge(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(
                if (selected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.surfaceContainer
            )
            .border(
                width = 1.dp,
                color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.48f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Text(
                text = "✓",
                color = MiuixTheme.colorScheme.onPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun String.categoryCardColor(): Color {
    val palette = listOf(
        Color(0xFF141414),
        Color(0xFF825A58),
        Color(0xFFA92E4A),
        Color(0xFF626262),
        Color(0xFF352B28),
        Color(0xFF416B8D),
        Color(0xFF28295F),
        Color(0xFF9B463D),
        Color(0xFF6C4E86),
        Color(0xFF2A1024),
        Color(0xFFA88E24),
        Color(0xFF542231),
        Color(0xFF5EA91A)
    )
    val index = (lowercase(Locale.ROOT).hashCode() and Int.MAX_VALUE) % palette.size
    return palette[index]
}

internal fun String.prefersEmbeddedCategoryCardCover(): Boolean =
    this == "folder" || this in PERSON_METADATA_CATEGORY_TYPES

private fun Color.darkenCategoryColor(factor: Float): Color {
    return Color(
        red = red * factor,
        green = green * factor,
        blue = blue * factor,
        alpha = alpha
    )
}
