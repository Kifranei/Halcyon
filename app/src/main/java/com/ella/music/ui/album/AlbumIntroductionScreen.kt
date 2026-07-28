package com.ella.music.ui.album

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.AlbumDescriptionRecord
import com.ella.music.data.AlbumDescriptionSaveResult
import com.ella.music.data.AlbumDescriptionStore
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.EllaMiuixSheetActions
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.ellaPageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AlbumIntroductionScreen(
    album: Album?,
    songs: List<Song>,
    coverModel: Any?,
    releaseDate: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { AlbumDescriptionStore.getInstance(context) }
    val contentKey = remember(album, songs) {
        buildString {
            append(album?.id)
            songs.forEach { song ->
                append('|')
                append(song.path)
                append(':')
                append(song.dateModified)
            }
        }
    }
    var record by remember(contentKey) { mutableStateOf<AlbumDescriptionRecord?>(null) }
    var editing by remember(contentKey) { mutableStateOf(false) }
    var draft by remember(contentKey) { mutableStateOf("") }
    var saving by remember(contentKey) { mutableStateOf(false) }

    LaunchedEffect(contentKey) {
        record = withContext(Dispatchers.IO) { store.load(album, songs) }
        draft = record?.text.orEmpty()
    }

    fun leavePage() {
        if (editing) {
            editing = false
            draft = record?.text.orEmpty()
        } else {
            onBack()
        }
    }
    BackHandler(onBack = ::leavePage)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = ::leavePage) {
                Icon(
                    imageVector = MiuixIcons.Regular.Back,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
            Text(
                text = if (editing) {
                    stringResource(R.string.album_introduction_edit)
                } else {
                    stringResource(R.string.album_introduction_title)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            if (!editing) {
                Text(
                    text = stringResource(R.string.album_introduction_edit_action),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable {
                            draft = record?.text.orEmpty()
                            editing = true
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }

        if (editing) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 22.dp,
                        top = 12.dp,
                        end = 22.dp,
                        bottom = AlbumIntroductionBottomDockClearance
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EllaMiuixTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = stringResource(R.string.album_introduction_editor_hint),
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                EllaMiuixSheetActions(
                    cancelText = stringResource(R.string.common_cancel),
                    confirmText = stringResource(R.string.common_save),
                    onCancel = ::leavePage,
                    onConfirm = {
                        if (saving) return@EllaMiuixSheetActions
                        saving = true
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    store.save(album, songs, draft)
                                }
                            }
                            saving = false
                            result.onSuccess { saveResult ->
                                record = withContext(Dispatchers.IO) { store.load(album, songs) }
                                draft = record?.text.orEmpty()
                                editing = false
                                val message = when (saveResult) {
                                    AlbumDescriptionSaveResult.SAVED_TO_NFO ->
                                        R.string.album_introduction_saved_nfo
                                    AlbumDescriptionSaveResult.SAVED_LOCALLY ->
                                        R.string.album_introduction_saved_local
                                    AlbumDescriptionSaveResult.CLEARED ->
                                        R.string.album_introduction_cleared
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    R.string.album_introduction_save_failed,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 26.dp,
                    top = 14.dp,
                    end = 26.dp,
                    bottom = AlbumIntroductionBottomDockClearance
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverModel != null) {
                            SafeCoverImage(
                                model = coverModel,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                sizePx = 720,
                                loadOriginal = true
                            )
                        } else {
                            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = album?.name ?: stringResource(R.string.player_unknown_album),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    val albumArtist = album?.albumArtist?.ifBlank { album.artist }
                        ?.takeIf(String::isNotBlank)
                    if (albumArtist != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = albumArtist,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    val date = releaseDate?.takeIf(String::isNotBlank)
                        ?: album?.year?.takeIf(String::isNotBlank)
                    if (date != null) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = date,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = stringResource(R.string.album_introduction_section),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = record?.text?.takeIf(String::isNotBlank)
                            ?: stringResource(R.string.album_introduction_empty),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        color = if (record?.text.isNullOrBlank()) {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

private val AlbumIntroductionBottomDockClearance = 132.dp
