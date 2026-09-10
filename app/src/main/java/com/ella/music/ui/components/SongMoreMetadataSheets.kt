package com.ella.music.ui.components

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.metadata.AudioCoverInfo
import com.ella.music.data.metadata.AudioTagInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.ui.player.PluginLyricsMatchSheet
import com.ella.music.viewmodel.MainViewModel
import com.lonx.audiotag.model.AudioTagKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun RatingSheet(
    currentRating: Int,
    onRatingSelected: (Int) -> Unit
) {
    var rating by remember(currentRating) { mutableStateOf(currentRating.coerceIn(0, 5)) }
    var ratingRowWidthPx by remember { mutableStateOf(0f) }
    val hasRatingChanged = rating != currentRating.coerceIn(0, 5)
    // Keep the star card and the action button as siblings. SongSheetColumn adds a surrounding
    // action card, whose side edges remain visible around the button after the stars were moved
    // into their own card.
    EllaMiuixSheetColumn(
        verticalPadding = 8.dp,
        spacing = 0.dp,
        showHandle = false
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { ratingRowWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            rating = ratingForPosition(offset.x, ratingRowWidthPx)
                        },
                        onHorizontalDrag = { change, _ ->
                            rating = ratingForPosition(change.position.x, ratingRowWidthPx)
                            change.consume()
                        }
                    )
                },
            insideMargin = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 18.dp,
                vertical = 12.dp
            ),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                (1..5).forEach { star ->
                    RatingStarIcon(
                        filled = star <= rating,
                        tint = if (star <= rating) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { rating = if (rating == star) 0 else star }
                            .padding(5.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            enabled = hasRatingChanged,
            onClick = { onRatingSelected(rating) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                color = if (hasRatingChanged) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
                contentColor = if (hasRatingChanged) MiuixTheme.colorScheme.onPrimary
                else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.55f)
            )
        ) {
            Text(text = stringResource(R.string.common_save))
        }
    }
}

private fun ratingForPosition(positionX: Float, rowWidthPx: Float): Int {
    if (rowWidthPx <= 0f) return 0
    return ((positionX.coerceIn(0f, rowWidthPx) / rowWidthPx) * 5f)
        .toInt()
        .plus(1)
        .coerceIn(1, 5)
}

@Composable
internal fun SongMetadataEditorSheet(
    song: Song,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (AudioTagInfo, AudioCoverInfo?, Boolean) -> Unit,
    onWritePermissionRequired: (WritePermissionRequiredException, suspend () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tagInfo by produceState<SongTagInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { mainViewModel.getSongTagInfo(song) }
    }
    val fullTagInfo by produceState<AudioTagInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { mainViewModel.getFullAudioTagInfo(song) }
    }

    var title by remember(tagInfo) { mutableStateOf(tagInfo?.title.orEmpty()) }
    var artist by remember(tagInfo) { mutableStateOf(tagInfo?.artist.orEmpty()) }
    var album by remember(tagInfo) { mutableStateOf(tagInfo?.album.orEmpty()) }
    var albumArtist by remember(tagInfo) { mutableStateOf(tagInfo?.albumArtist.orEmpty()) }
    var genre by remember(tagInfo) { mutableStateOf(tagInfo?.genre.orEmpty()) }
    var year by remember(tagInfo) { mutableStateOf(tagInfo?.year.orEmpty()) }
    var trackNumber by remember(tagInfo) { mutableStateOf(tagInfo?.track.orEmpty()) }
    var discNumber by remember(fullTagInfo) { mutableStateOf(fullTagInfo?.discNumber?.toString().orEmpty()) }
    var composer by remember(tagInfo) { mutableStateOf(tagInfo?.composer.orEmpty()) }
    var arranger by remember(tagInfo) { mutableStateOf(tagInfo?.arranger.orEmpty()) }
    var lyricist by remember(tagInfo) { mutableStateOf(tagInfo?.lyricist.orEmpty()) }
    var songwriters by remember(tagInfo) { mutableStateOf(tagInfo?.songwriters.orEmpty().ifBlank { fullTagInfo?.songwriters.orEmpty() }) }
    var copyright by remember(tagInfo) { mutableStateOf(tagInfo?.copyright.orEmpty()) }
    var comment by remember(tagInfo) { mutableStateOf(tagInfo?.comment.orEmpty()) }
    val initialLyrics = fullTagInfo.standardEmbeddedLyrics()
    val initialTtmlLyrics = fullTagInfo.ttmlEmbeddedLyrics()
    val initialTtmlLyricTagKey = fullTagInfo.ttmlEmbeddedLyricTagKey() ?: "TTMLLYRIC"
    var lyrics by remember(initialLyrics) { mutableStateOf(initialLyrics) }
    var ttmlLyrics by remember(initialTtmlLyrics) { mutableStateOf(initialTtmlLyrics) }
    var rating by remember(tagInfo) { mutableStateOf(tagInfo?.rating ?: 0) }
    val currentCover by produceState<Any?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { mainViewModel.getOriginalCoverModel(song) }
    }
    var selectedCover by remember(song.id) { mutableStateOf<AudioCoverInfo?>(null) }
    var selectedCoverPreview by remember(song.id) { mutableStateOf<Any?>(null) }
    var coverChanged by remember(song.id) { mutableStateOf(false) }
    var coverPreviewVisible by remember(song.id) { mutableStateOf(false) }
    var bitmapToCrop by remember(song.id) { mutableStateOf<Bitmap?>(null) }
    var showCoverCropSheet by remember(song.id) { mutableStateOf(false) }
    fun applyCroppedCover(bitmap: Bitmap) {
        val jpeg = bitmap.toEmbeddedCoverJpeg()
        selectedCover = AudioCoverInfo(bytes = jpeg, mimeType = "image/jpeg")
        selectedCoverPreview = jpeg
        coverChanged = true
        if (bitmap !== bitmapToCrop) bitmap.recycle()
    }
    fun openCoverCrop(source: Any?) {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeCoverSource(context, source) }
            if (bitmap == null) {
                Toast.makeText(context, R.string.song_more_metadata_cover_crop_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            bitmapToCrop = bitmap
            showCoverCropSheet = true
        }
    }
    val coverPicker = rememberLauncherForActivityResult(GetContentImageContract()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        openCoverCrop(uri)
    }
    var customTags: MutableList<Pair<String, String>> by remember(fullTagInfo) {
        val initial: MutableList<Pair<String, String>> = fullTagInfo?.customTags
            ?.filter { entry -> !AudioTagKeys.isReserved(entry.key) && !entry.key.isTtmlLyricTag() }
            ?.map { entry -> entry.key to entry.value.joinToString("; ") }
            ?.toMutableList()
            ?: mutableListOf()
        mutableStateOf(initial)
    }
    var showAddTag by remember { mutableStateOf(false) }
    var showLyricoMatch by remember(song.id) { mutableStateOf(false) }

    fun performSave() {
        val currentTagInfo = tagInfo ?: return
        val ctMap: MutableMap<String, MutableList<String>> = mutableMapOf()
        for (pair in customTags) {
            if (pair.first.isNotBlank()) {
                ctMap.getOrPut(pair.first) { mutableListOf() }.add(pair.second)
            }
        }
        if (ttmlLyrics != initialTtmlLyrics) {
            ctMap.getOrPut(initialTtmlLyricTagKey) { mutableListOf() }.add(ttmlLyrics)
        }
        val tags = AudioTagInfo(
            title = title.takeIf { v -> v != currentTagInfo.title },
            artist = artist.takeIf { v -> v != currentTagInfo.artist },
            album = album.takeIf { v -> v != currentTagInfo.album },
            albumArtist = albumArtist.takeIf { v -> v != currentTagInfo.albumArtist },
            genre = genre.takeIf { v -> v != currentTagInfo.genre },
            year = year.takeIf { v -> v != currentTagInfo.year },
            trackNumber = trackNumber.toIntOrNull()?.takeIf { v -> v.toString() != currentTagInfo.track },
            discNumber = discNumber.toIntOrNull()?.takeIf { v -> v != fullTagInfo?.discNumber },
            composer = composer.takeIf { v -> v != currentTagInfo.composer },
            arranger = arranger.takeIf { v -> v != currentTagInfo.arranger },
            lyricist = lyricist.takeIf { v -> v != currentTagInfo.lyricist },
            songwriters = songwriters.takeIf { v -> v != currentTagInfo.songwriters && v != fullTagInfo?.songwriters },
            copyright = copyright.takeIf { v -> v != currentTagInfo.copyright },
            comment = comment.takeIf { v -> v != currentTagInfo.comment },
            lyrics = lyrics.takeIf { v -> v != initialLyrics },
            rating = rating.takeIf { v -> v != currentTagInfo.rating },
            customTags = ctMap
        )
        onSave(tags, selectedCover, coverChanged)
    }

    EllaMiuixBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = stringResource(R.string.song_more_metadata_editor_title),
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = MiuixIcons.Regular.Close,
                    contentDescription = stringResource(R.string.common_cancel),
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
        },
        endAction = {
            IconButton(
                enabled = tagInfo != null,
                onClick = ::performSave
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Ok,
                    contentDescription = stringResource(R.string.common_save),
                    tint = if (tagInfo != null) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        },
        onDismissRequest = onDismiss
    ) {
        EllaMiuixSheetColumn(
            verticalPadding = 8.dp,
            spacing = 8.dp,
            showHandle = false
        ) {
            SectionHeader(stringResource(R.string.song_more_metadata_section_cover))
            EllaMiuixActionMenuGroup {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val previewModel = selectedCoverPreview ?: currentCover
                    if (previewModel != null) {
                        SafeCoverImage(
                            model = previewModel,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth(0.52f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(onClick = {}, onLongClick = { coverPreviewVisible = true }),
                            contentScale = ContentScale.Crop,
                            sizePx = 3000,
                            loadOriginal = true
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.song_more_metadata_cover_empty),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(vertical = 34.dp)
                        )
                    }
                }
                EllaMiuixActionRow(
                    actions = listOf(
                        EllaMiuixAction(
                            text = stringResource(R.string.song_more_metadata_cover_choose),
                            onClick = { coverPicker.launch(arrayOf("image/*")) },
                            primary = true
                        ),
                        EllaMiuixAction(
                            text = stringResource(R.string.song_more_metadata_cover_crop),
                            onClick = {
                                openCoverCrop(selectedCover?.bytes ?: selectedCoverPreview ?: currentCover)
                            }
                        ),
                        EllaMiuixAction(
                            text = stringResource(R.string.song_more_metadata_cover_remove),
                            onClick = {
                                selectedCover = null
                                selectedCoverPreview = null
                                coverChanged = true
                            }
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    spacing = 8.dp
                )
                if (coverChanged && selectedCover == null) {
                    Text(
                        text = stringResource(R.string.song_more_metadata_cover_remove_pending),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 10.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            SectionHeader(stringResource(R.string.song_more_metadata_section_lyrico))
            EllaMiuixActionMenuGroup {
                Spacer(modifier = Modifier.height(8.dp))
                EllaMiuixActionRow(
                    actions = listOf(
                        EllaMiuixAction(
                            text = stringResource(R.string.song_more_metadata_match_lyrico),
                            onClick = { showLyricoMatch = true },
                            primary = true
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )
                Text(
                    text = stringResource(R.string.song_more_metadata_match_lyrico_summary),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 10.dp)
                )
            }

            SectionHeader(stringResource(R.string.song_more_metadata_section_basic))
            EllaMiuixActionMenuGroup {
                Spacer(modifier = Modifier.height(6.dp))
                MetadataField(stringResource(R.string.song_more_metadata_title), title) { title = it }
                MetadataField(stringResource(R.string.song_more_metadata_artist), artist) { artist = it }
                MetadataField(stringResource(R.string.song_more_metadata_album), album) { album = it }
                MetadataField(stringResource(R.string.song_more_metadata_album_artist), albumArtist) { albumArtist = it }
                MetadataField(stringResource(R.string.song_more_metadata_genre), genre) { genre = it }
                MetadataField(stringResource(R.string.song_more_metadata_year), year) { year = it }
                Spacer(modifier = Modifier.height(6.dp))
            }

            SectionHeader(stringResource(R.string.song_more_metadata_section_track))
            EllaMiuixActionMenuGroup {
                Spacer(modifier = Modifier.height(6.dp))
                MetadataField(stringResource(R.string.song_more_metadata_track_number), trackNumber) { trackNumber = it }
                MetadataField(stringResource(R.string.song_more_metadata_disc_number), discNumber) { discNumber = it }
                Spacer(modifier = Modifier.height(6.dp))
            }

            SectionHeader(stringResource(R.string.song_more_metadata_section_credits))
            EllaMiuixActionMenuGroup {
                Spacer(modifier = Modifier.height(6.dp))
                MetadataField(stringResource(R.string.song_more_metadata_composer), composer) { composer = it }
                MetadataField(stringResource(R.string.song_more_metadata_arranger), arranger) { arranger = it }
                MetadataField(stringResource(R.string.song_more_metadata_lyricist), lyricist) { lyricist = it }
                MetadataField(stringResource(R.string.song_more_metadata_songwriters), songwriters) { songwriters = it }
                MetadataField(stringResource(R.string.song_more_metadata_copyright), copyright) { copyright = it }
                MetadataField(stringResource(R.string.song_more_metadata_comment), comment) { comment = it }
                Spacer(modifier = Modifier.height(6.dp))
            }

            SectionHeader(stringResource(R.string.song_more_metadata_section_lyrics))
            EllaMiuixActionMenuGroup {
                Spacer(modifier = Modifier.height(6.dp))
                MetadataField(
                    label = stringResource(R.string.song_more_metadata_lyrics),
                    value = lyrics,
                    singleLine = false,
                    modifier = Modifier.height(150.dp)
                ) { lyrics = it }
                if (lyrics.isBlank()) {
                    Text(
                        text = stringResource(R.string.song_more_metadata_no_lyrics),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
                    )
                }
                MetadataField(
                    label = stringResource(R.string.song_more_metadata_ttml_lyrics),
                    value = ttmlLyrics,
                    singleLine = false,
                    modifier = Modifier.height(170.dp)
                ) { ttmlLyrics = it }
                if (ttmlLyrics.isBlank()) {
                    Text(
                        text = stringResource(R.string.song_more_metadata_no_ttml_lyrics),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            SectionHeader(stringResource(R.string.song_more_metadata_section_rating))
            EllaMiuixActionMenuGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (1..5).forEach { star ->
                        RatingStarIcon(
                            filled = star <= rating,
                            tint = if (star <= rating) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { rating = if (rating == star) 0 else star }
                                .padding(4.dp)
                        )
                    }
                    if (rating > 0) {
                        Text(
                            text = "✕",
                            fontSize = 16.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .padding(start = 8.dp, top = 2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { rating = 0 }
                                .padding(4.dp)
                        )
                    }
                }
            }

            SectionHeader(stringResource(R.string.song_more_metadata_section_custom_tags))
            EllaMiuixActionMenuGroup(insideMargin = PaddingValues(0.dp)) {
                Spacer(modifier = Modifier.height(6.dp))
                for (index in customTags.indices) {
                    val pair = customTags[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Basic metadata fields fill the Card edge-to-edge. Keep custom
                            // tag rows on that same grid instead of leaving an 18dp gutter on
                            // both sides of the two editable fields.
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = pair.first,
                            onValueChange = { newKey -> customTags = customTags.toMutableList().apply { set(index, newKey to pair.second) } },
                            label = stringResource(R.string.song_more_custom_tag_name),
                            modifier = Modifier.weight(1f)
                        )
                        TextField(
                            value = pair.second,
                            onValueChange = { newValue -> customTags = customTags.toMutableList().apply { set(index, pair.first to newValue) } },
                            label = stringResource(R.string.song_more_custom_tag_value),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "✕",
                            fontSize = 16.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { customTags = customTags.toMutableList().apply { removeAt(index) } }
                                .padding(4.dp)
                        )
                    }
                }
                if (showAddTag) {
                    var newKey by remember { mutableStateOf("") }
                    var newValue by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = newKey,
                            onValueChange = { newKey = it },
                            label = stringResource(R.string.song_more_custom_tag_name),
                            modifier = Modifier.weight(1f)
                        )
                        TextField(
                            value = newValue,
                            onValueChange = { newValue = it },
                            label = stringResource(R.string.song_more_custom_tag_value),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = MiuixIcons.Regular.Ok,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    if (newKey.isNotBlank()) {
                                        customTags = customTags.toMutableList().apply { add(newKey to newValue) }
                                        newKey = ""
                                        newValue = ""
                                        showAddTag = false
                                    }
                                }
                                .padding(4.dp)
                                .size(18.dp)
                        )
                    }
                }
                EllaMiuixActionRow(
                    actions = listOf(
                        EllaMiuixAction(
                            text = stringResource(R.string.song_more_metadata_add_custom_tag),
                            onClick = { showAddTag = !showAddTag }
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showLyricoMatch) {
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_metadata_lyrico_title),
            onDismissRequest = { showLyricoMatch = false }
        ) {
            PluginLyricsMatchSheet(
                song = song,
                mainViewModel = mainViewModel,
                onDismiss = { showLyricoMatch = false },
                onWritePermissionRequired = onWritePermissionRequired,
                onApplyToTagEditor = { match ->
                    title = match.tags.title ?: title
                    artist = match.tags.artist ?: artist
                    album = match.tags.album ?: album
                    albumArtist = match.tags.albumArtist ?: albumArtist
                    genre = match.tags.genre ?: genre
                    year = match.tags.year ?: year
                    trackNumber = match.tags.trackNumber?.toString() ?: trackNumber
                    discNumber = match.tags.discNumber?.toString() ?: discNumber
                    composer = match.tags.composer ?: composer
                    arranger = match.tags.arranger ?: arranger
                    lyricist = match.tags.lyricist ?: lyricist
                    songwriters = match.tags.songwriters ?: songwriters
                    copyright = match.tags.copyright ?: copyright
                    comment = match.tags.comment ?: comment
                    if (match.isTtml) {
                        ttmlLyrics = match.lyrics
                    } else {
                        lyrics = match.lyrics
                        ttmlLyrics = ""
                    }
                    showLyricoMatch = false
                }
            )
        }
    }
    if (coverPreviewVisible) {
        val previewModel = selectedCoverPreview ?: currentCover
        if (previewModel != null) {
            CoverPreviewDialog(
                model = previewModel,
                title = listOf(
                    title.ifBlank { song.title.ifBlank { song.fileName } },
                    artist.ifBlank { song.artist }.takeIf(String::isNotBlank)
                ).filterNotNull().joinToString(" - "),
                saveName = listOf(
                    artist.ifBlank { song.artist }.takeIf(String::isNotBlank),
                    title.ifBlank { song.title.ifBlank { song.fileName } }
                ).filterNotNull().joinToString(" - "),
                onDismiss = { coverPreviewVisible = false }
            )
        } else {
            coverPreviewVisible = false
        }
    }
    bitmapToCrop?.let { cropBitmap ->
        val cropperState = rememberCoverImageCropperState(cropBitmap)
        EllaMiuixBottomSheet(
            show = showCoverCropSheet,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_metadata_cover_crop_title),
            endAction = {
                IconButton(
                    onClick = {
                        applyCroppedCover(cropperState.crop())
                        showCoverCropSheet = false
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Ok,
                        contentDescription = stringResource(R.string.common_save),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
            },
            onDismissRequest = { showCoverCropSheet = false },
            onDismissFinished = { bitmapToCrop = null }
        ) {
            CoverImageCropper(
                state = cropperState,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
    )
}

private val ttmlLyricTagKeys = setOf(
    "TTML LYRICS",
    "TTML LYRIC",
    "TTMLLYRICS",
    "TTMLLYRIC",
    "TTML"
)

private val standardLyricTagKeys = listOf(
    "SYNCEDLYRICS",
    "UNSYNCEDLYRICS",
    "UNSYNCED LYRICS",
    "LYRICS",
    "USLT",
    "SYLT",
    "©lyr",
    "\u00a9lyr",
    "LYRIC"
)

private fun AudioTagInfo?.standardEmbeddedLyrics(): String {
    if (this == null) return ""
    customTags.firstMatchingValue(standardLyricTagKeys)?.let { return it }
    return lyrics.orEmpty()
}

private fun AudioTagInfo?.ttmlEmbeddedLyrics(): String {
    if (this == null) return ""
    customTags.firstMatchingValue(ttmlLyricTagKeys)?.let { return it }
    return ""
}

private fun AudioTagInfo?.ttmlEmbeddedLyricTagKey(): String? =
    this?.customTags?.keys?.firstOrNull { it.isTtmlLyricTag() }

private fun Map<String, List<String>>.firstMatchingValue(keys: Iterable<String>): String? =
    entries.firstNotNullOfOrNull { (key, values) ->
        values.firstOrNull { value ->
            keys.any { wanted -> key.equals(wanted, ignoreCase = true) } && value.isNotBlank()
        }
    }

private fun String.isTtmlLyricTag(): Boolean =
    ttmlLyricTagKeys.any { equals(it, ignoreCase = true) }

@Composable
private fun MetadataField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = singleLine,
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
    )
}
