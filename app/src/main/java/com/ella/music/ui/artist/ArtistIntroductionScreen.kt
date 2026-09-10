package com.ella.music.ui.artist

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
import com.ella.music.data.ArtistDescriptionRecord
import com.ella.music.data.ArtistDescriptionSaveResult
import com.ella.music.data.ArtistDescriptionStore
import com.ella.music.data.model.Song
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.ellaOverlayCardColor
import com.ella.music.ui.components.EllaMiuixSheetActions
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import com.ella.music.data.lastfm.ArtistBioMenuSource
import com.ella.music.data.lastfm.LastFmCloudflareChallengeException
import com.ella.music.data.lastfm.fetchLastFmArtistWiki
import com.ella.music.data.lastfm.LastFmSecureStore
import com.ella.music.ui.components.EllaMiuixBottomSheet
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.TextField
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
internal fun ArtistIntroductionScreen(
    artistName: String,
    songs: List<Song>,
    coverModel: Any?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { ArtistDescriptionStore.getInstance(context) }
    val displayName = artistName.ifBlank { stringResource(R.string.player_unknown_artist) }
    val contentKey = remember(artistName, songs) {
        buildString {
            append(artistName)
            songs.forEach { song ->
                append('|')
                append(song.path)
                append(':')
                append(song.dateModified)
            }
        }
    }
    var record by remember(contentKey) { mutableStateOf<ArtistDescriptionRecord?>(null) }
    var editing by remember(contentKey) { mutableStateOf(false) }
    var draft by remember(contentKey) { mutableStateOf("") }
    var saving by remember(contentKey) { mutableStateOf(false) }
    val lastFmCredentials by LastFmSecureStore.getInstance(context).credentials.collectAsState()
    var showFetchSheet by remember { mutableStateOf(false) }
    var fetching by remember { mutableStateOf(false) }
    var pendingFetchOption by remember { mutableStateOf<ArtistBioSourceOption?>(null) }
    var challengeUrl by remember { mutableStateOf<String?>(null) }
    var showCloudflareSheet by remember { mutableStateOf(false) }

    LaunchedEffect(contentKey) {
        record = withContext(Dispatchers.IO) { store.load(artistName, songs) }
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
                    stringResource(R.string.artist_introduction_edit)
                } else {
                    stringResource(R.string.artist_introduction_title)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.artist_introduction_fetch_action),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(enabled = !fetching) {
                        showFetchSheet = true
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (fetching) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.primary
            )
            if (!editing) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.artist_introduction_edit_action),
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
                        bottom = ArtistIntroductionBottomDockClearance
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = stringResource(R.string.artist_introduction_editor_hint),
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
                                    store.save(artistName, songs, draft)
                                }
                            }
                            saving = false
                            result.onSuccess { saveResult ->
                                record = withContext(Dispatchers.IO) { store.load(artistName, songs) }
                                draft = record?.text.orEmpty()
                                editing = false
                                val message = when (saveResult) {
                                    ArtistDescriptionSaveResult.SAVED_TO_NFO ->
                                        R.string.artist_introduction_saved_nfo
                                    ArtistDescriptionSaveResult.SAVED_LOCALLY ->
                                        R.string.artist_introduction_saved_local
                                    ArtistDescriptionSaveResult.CLEARED ->
                                        R.string.artist_introduction_cleared
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    R.string.artist_introduction_save_failed,
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
                    bottom = ArtistIntroductionBottomDockClearance
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
                                loadOriginal = true,
                                showDefaultPlaceholder = false
                            )
                        } else {
                            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = displayName,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = stringResource(R.string.artist_introduction_section, displayName),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = record?.text?.takeIf(String::isNotBlank)
                            ?: stringResource(R.string.artist_introduction_empty),
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

    fun executeFetch(option: ArtistBioSourceOption) {
        if (fetching) return
        fetching = true
        Toast.makeText(
            context,
            R.string.artist_introduction_fetch_loading,
            Toast.LENGTH_SHORT
        ).show()
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    fetchLastFmArtistWiki(
                        artistName = artistName,
                        regionCode = option.regionCode,
                        apiKey = lastFmCredentials.apiKey,
                        preferredSource = option.source
                    )
                }
            }
            fetching = false
            result.onSuccess { wiki ->
                val bioText = wiki.text.trim()
                if (bioText.isNotBlank()) {
                    if (editing) {
                        draft = bioText
                        Toast.makeText(
                            context,
                            R.string.artist_introduction_fetch_applied,
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        withContext(Dispatchers.IO) {
                            store.save(artistName, songs, bioText)
                        }
                        record = withContext(Dispatchers.IO) {
                            store.load(artistName, songs)
                        }
                        draft = record?.text.orEmpty()
                        Toast.makeText(
                            context,
                            R.string.artist_introduction_fetch_saved,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        context,
                        R.string.artist_introduction_fetch_empty,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure { error ->
                if (error is LastFmCloudflareChallengeException) {
                    pendingFetchOption = option
                    challengeUrl = error.url
                    showCloudflareSheet = true
                } else {
                    val errorMsg = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                    Toast.makeText(
                        context,
                        "${context.getString(R.string.artist_introduction_fetch_failed)}: $errorMsg",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    if (showFetchSheet) {
        val fetchOptions = remember {
            listOf(
                ArtistBioSourceOption(
                    titleRes = R.string.artist_image_source_netease,
                    langRes = R.string.artist_biography_lang_zh_cn,
                    source = ArtistBioMenuSource.Netease,
                    regionCode = "zh"
                ),
                ArtistBioSourceOption(
                    titleRes = R.string.artist_biography_source_wikipedia,
                    langRes = R.string.artist_biography_lang_zh_cn,
                    source = ArtistBioMenuSource.Wikipedia,
                    regionCode = "zh"
                ),
                ArtistBioSourceOption(
                    titleRes = R.string.artist_biography_source_wikipedia,
                    langRes = R.string.artist_biography_lang_zh_tw,
                    source = ArtistBioMenuSource.Wikipedia,
                    regionCode = "zh-tw"
                ),
                ArtistBioSourceOption(
                    titleRes = R.string.artist_biography_source_wikipedia,
                    langRes = R.string.artist_biography_lang_zh_hk,
                    source = ArtistBioMenuSource.Wikipedia,
                    regionCode = "zh-hk"
                ),
                ArtistBioSourceOption(
                    titleRes = R.string.artist_biography_source_wikipedia,
                    langRes = R.string.artist_biography_lang_en,
                    source = ArtistBioMenuSource.Wikipedia,
                    regionCode = "en"
                ),
                ArtistBioSourceOption(
                    titleRes = R.string.artist_biography_source_wikipedia,
                    langRes = R.string.artist_biography_lang_ja,
                    source = ArtistBioMenuSource.Wikipedia,
                    regionCode = "ja"
                ),
                ArtistBioSourceOption(
                    titleRes = R.string.artist_biography_source_wikipedia,
                    langRes = R.string.artist_biography_lang_ko,
                    source = ArtistBioMenuSource.Wikipedia,
                    regionCode = "ko"
                ),
                ArtistBioSourceOption(
                    titleRes = R.string.artist_image_source_lastfm,
                    langRes = R.string.artist_biography_lang_en,
                    source = ArtistBioMenuSource.LastFm,
                    regionCode = "en"
                ),
                ArtistBioSourceOption(
                    titleRes = R.string.artist_image_source_lastfm,
                    langRes = R.string.artist_biography_lang_zh_cn,
                    source = ArtistBioMenuSource.LastFm,
                    regionCode = "zh"
                )
            )
        }
        EllaMiuixBottomSheet(
            show = true,
            title = stringResource(R.string.artist_introduction_fetch_sheet_title),
            onDismissRequest = { showFetchSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fetchOptions.forEach { option ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        colors = CardDefaults.defaultColors(
                            color = ellaOverlayCardColor()
                        )
                    ) {
                        BasicComponent(
                            title = stringResource(option.titleRes),
                            summary = stringResource(option.langRes),
                            onClick = {
                                showFetchSheet = false
                                executeFetch(option)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCloudflareSheet && challengeUrl != null) {
        LastFmCloudflareVerificationSheet(
            url = challengeUrl!!,
            onDismissRequest = {
                showCloudflareSheet = false
                pendingFetchOption = null
            },
            onVerified = {
                showCloudflareSheet = false
                val pending = pendingFetchOption
                pendingFetchOption = null
                if (pending != null) {
                    executeFetch(pending)
                }
            }
        )
    }
}

private data class ArtistBioSourceOption(
    val titleRes: Int,
    val langRes: Int,
    val source: ArtistBioMenuSource,
    val regionCode: String
)

private val ArtistIntroductionBottomDockClearance = 132.dp
