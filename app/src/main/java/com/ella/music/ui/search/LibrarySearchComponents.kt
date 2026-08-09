package com.ella.music.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Album
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.viewmodel.MetadataCategoryItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SearchPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
internal fun SearchSectionHeader(
    text: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    collapsed: Boolean? = null,
    onHeaderClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onHeaderClick != null) {
                    Modifier.clickable(onClick = onHeaderClick)
                } else {
                    Modifier
                }
            )
            .padding(start = 16.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f)
        )
        collapsed?.let { isCollapsed ->
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(20.dp)
                    .rotate(if (isCollapsed) 0f else 90f)
            )
        }
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
internal fun HistoryRow(text: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.common_delete),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDelete)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun AlbumResultRow(album: Album, coverModel: Any?, query: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val subtitle = buildList {
        add("${album.songCount} ${stringResource(R.string.library_search_song_count_unit)}")
        if (album.year.isNotBlank()) add(album.year)
        album.albumArtist.ifBlank { album.artist }
            .takeIf { it.isNotBlank() }
            ?.let(::add)
    }.joinToString(" · ")
    SearchResultRow(
        title = album.name,
        subtitle = subtitle,
        coverModel = coverModel,
        query = query,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
internal fun ArtistResultRow(result: ArtistSearchResult, coverModel: Any?, query: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val subtitle = stringResource(
        R.string.library_search_artist_summary,
        result.artist.songCount,
        result.participatedAlbumCount
    )
    SearchResultRow(
        title = result.artist.name,
        subtitle = subtitle,
        coverModel = coverModel,
        roundCover = true,
        query = query,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
internal fun PlaylistResultRow(playlist: UserPlaylist, coverModel: Any?, query: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    SearchResultRow(
        title = playlist.name,
        subtitle = stringResource(R.string.library_search_playlist_summary, playlist.songs.size),
        coverModel = coverModel,
        query = query,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
internal fun MetadataCategoryResultRow(
    item: MetadataCategoryItem,
    displayName: String = item.name,
    coverModel: Any?,
    roundCover: Boolean = false,
    query: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    SearchResultRow(
        title = displayName,
        subtitle = stringResource(R.string.library_search_category_summary, item.songCount, item.albumCount),
        coverModel = coverModel,
        roundCover = roundCover,
        query = query,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String,
    coverModel: Any?,
    roundCover: Boolean = false,
    query: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val highlightColor = MiuixTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(if (roundCover) CircleShape else RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            SafeCoverImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                sizePx = 128
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = highlightedText(title, query, highlightColor),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = highlightedText(subtitle, query, highlightColor),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun highlightedText(text: String, query: String, highlightColor: Color): AnnotatedString =
    buildAnnotatedString {
        appendHighlightedQuery(text, query, highlightColor)
    }

@Composable
internal fun EmptySearchHint(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
        Text(text = text, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
internal fun LyricSearchMatchLine(snippet: String, query: String) {
    Text(
        text = buildAnnotatedString {
            append(stringResource(R.string.library_search_lyric_prefix))
            appendHighlightedQuery(snippet, query, MiuixTheme.colorScheme.primary)
        },
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 76.dp, end = 16.dp, bottom = 8.dp)
    )
}

@Composable
internal fun SongSearchMatchLine(match: SongSearchMatch, query: String) {
    Text(
        text = buildAnnotatedString {
            append(match.displayLabel ?: stringResource(match.labelRes))
            append(": ")
            appendHighlightedQuery(match.value, query, MiuixTheme.colorScheme.primary)
        },
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 76.dp, end = 16.dp, bottom = 8.dp)
    )
}

private fun AnnotatedString.Builder.appendHighlightedQuery(text: String, query: String, highlightColor: Color) {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) {
        append(text)
        return
    }
    var start = 0
    while (start < text.length) {
        val index = text.indexOf(normalizedQuery, startIndex = start, ignoreCase = true)
        if (index < 0) {
            append(text.substring(start))
            break
        }
        if (index > start) append(text.substring(start, index))
        pushStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold))
        append(text.substring(index, index + normalizedQuery.length))
        pop()
        start = index + normalizedQuery.length
    }
}
