package com.ella.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import coil3.compose.AsyncImage
import com.ella.music.data.ArtistImageCandidate
import com.ella.music.data.ArtistImageRepository
import com.ella.music.data.ArtistImageSearch
import com.ella.music.data.ArtistImageSearchConfig
import com.ella.music.data.SettingsManager
import com.ella.music.data.lastfm.DEFAULT_LAST_FM_WIKI_REGION
import com.ella.music.data.lastfm.LastFmSecureStore
import com.ella.music.ui.components.CoverPreviewDialog
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.theme.EllaTheme
import com.ella.music.ui.theme.MONET_COVER
import com.ella.music.ui.theme.THEME_FOLLOW_SYSTEM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Standalone, exported artist image search (#746); other apps may open it with an artist name. */
class ArtistImageSearchActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val language = runBlocking(Dispatchers.IO) { SettingsManager.getInstance(newBase).appLanguage.first() }
        super.attachBaseContext(newBase.withHalcyonLocale(language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val artist = intent.getStringExtra(EXTRA_ARTIST_NAME)
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.data?.getQueryParameter("keyword")
            ?: intent.data?.getQueryParameter("artist")
            ?: ""
        // halcyon://artist-image-search?source=qq&keyword=name filters to one source (#754).
        val requestedSource = intent.data?.getQueryParameter("source")?.let(::deeplinkSourceId)
        setContent {
            val settings = remember { SettingsManager.getInstance(this) }
            val themeMode by settings.themeMode.collectAsState(initial = THEME_FOLLOW_SYSTEM)
            val monetMode by settings.monetColorMode.collectAsState(initial = 0)
            EllaTheme(
                themeMode = themeMode,
                monetMode = if (monetMode == MONET_COVER) 0 else monetMode,
                systemDarkOverride = androidx.compose.foundation.isSystemInDarkTheme()
            ) {
                ArtistImageSearchScreen(initialArtist = artist.trim(), initialSource = requestedSource, onBack = ::finish)
            }
        }
    }

    companion object {
        const val EXTRA_ARTIST_NAME = "artist_name"

        fun open(context: Context, artistName: String) {
            context.startActivity(Intent(context, ArtistImageSearchActivity::class.java)
                .putExtra(EXTRA_ARTIST_NAME, artistName))
        }
    }
}

private const val ALL_SOURCES_TAB = "all"

/** Deeplink `source` values (#754); last.fm keeps its dot. */
private fun deeplinkSourceId(raw: String): String? = when (raw.trim().lowercase()) {
    "qq" -> SettingsManager.ARTIST_IMAGE_SOURCE_QQ
    "netease" -> SettingsManager.ARTIST_IMAGE_SOURCE_NETEASE
    "kugou" -> SettingsManager.ARTIST_IMAGE_SOURCE_KUGOU
    "kuwo" -> SettingsManager.ARTIST_IMAGE_SOURCE_KUWO
    "spotify" -> SettingsManager.ARTIST_IMAGE_SOURCE_SPOTIFY
    "last.fm", "lastfm" -> SettingsManager.ARTIST_IMAGE_SOURCE_LASTFM
    else -> null
}

@Composable
private fun ArtistImageSearchScreen(initialArtist: String, initialSource: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val settings = remember { SettingsManager.getInstance(context) }
    val sourceOrder by settings.artistImageSourceOrder.collectAsState(initial = SettingsManager.DEFAULT_ARTIST_IMAGE_SOURCES)
    val availableSources = remember(sourceOrder, initialSource) {
        // A deeplink explicitly names its source, even when it sits outside the enabled set.
        if (initialSource != null) listOf(initialSource)
        else SettingsManager.normalizeArtistImageSources(sourceOrder)
    }
    val lastFm by LastFmSecureStore.getInstance(context).credentials.collectAsState()
    val lastFmRegion by settings.artistImageRegion.collectAsState(initial = DEFAULT_LAST_FM_WIKI_REGION)
    val spotifyRegion by settings.artistSpotifyRegion.collectAsState(initial = "US")
    val spotifyClientId by settings.spotifyClientId.collectAsState(initial = "")
    val spotifyClientSecret by settings.spotifyClientSecret.collectAsState(initial = "")
    val config = ArtistImageSearchConfig(
        sources = availableSources,
        lastFmApiKey = lastFm.apiKey, lastFmRegion = lastFmRegion,
        spotifyClientId = spotifyClientId, spotifyClientSecret = spotifyClientSecret, spotifyRegion = spotifyRegion
    )
    var query by rememberSaveable { mutableStateOf(initialArtist) }
    var searchedArtist by rememberSaveable { mutableStateOf(initialArtist) }
    var selectedTab by rememberSaveable { mutableStateOf(initialSource ?: ALL_SOURCES_TAB) }
    var searchRequest by remember { mutableIntStateOf(if (initialArtist.isBlank()) 0 else 1) }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<Map<String, List<ArtistImageCandidate>>>(emptyMap()) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(searchRequest, config.sources) {
        if (searchRequest == 0 || searchedArtist.isBlank()) return@LaunchedEffect
        loading = true
        results = ArtistImageSearch.searchAll(searchedArtist, config)
        loading = false
    }
    fun submit() {
        focusManager.clearFocus()
        searchedArtist = query.trim()
        if (searchedArtist.isNotEmpty()) searchRequest++
    }

    // 「所有」 sorts by keyword match quality first, then source priority (#754); single-source
    // tabs keep each source's own order.
    val tabs = listOf(ALL_SOURCES_TAB) + config.sources
    val visible = if (selectedTab == ALL_SOURCES_TAB) {
        val query = searchedArtist.trim()
        fun rank(name: String): Int = when {
            name.trim() == query -> 0
            name.substringBefore('(').substringBefore('（').trim() == query -> 1
            com.ella.music.data.artistImageNameMatches(query, name) -> 2
            else -> 3
        }
        results.values.flatten().sortedWith(
            compareBy<ArtistImageCandidate> { rank(it.name) }
                .thenBy { config.sources.indexOf(it.source) }
        )
    } else results[selectedTab].orEmpty()
    val pageBackground = ellaPageBackground()
    Column(Modifier.fillMaxSize().background(pageBackground)) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.artist_image_search),
            color = pageBackground,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(MiuixIcons.Regular.Back, stringResource(R.string.common_back), Modifier.size(24.dp))
                }
            }
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = query, onValueChange = { query = it },
                label = stringResource(R.string.artist_image_search_hint), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = ::submit) {
                Icon(MiuixIcons.Regular.Search, stringResource(R.string.common_search), Modifier.size(24.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEach { tab ->
                val selected = tab == selectedTab
                val label = if (tab == ALL_SOURCES_TAB) stringResource(R.string.artist_image_search_all)
                    else ArtistImageRepository.sourceLabelRes(tab)?.let { stringResource(it) } ?: tab
                val count = if (tab == ALL_SOURCES_TAB) results.values.sumOf { it.size } else results[tab]?.size
                Text(
                    text = if (count != null && results.isNotEmpty()) "$label $count" else label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (selected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                        .clickable { selectedTab = tab; previewIndex = null }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                loading -> EllaCenteredLoadingIndicator()
                visible.isEmpty() -> Text(
                    stringResource(if (searchRequest == 0) R.string.artist_image_search_hint else R.string.artist_image_search_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().navigationBarsPadding()
                ) {
                    itemsIndexed(visible, key = { _, item -> item.source + item.url }) { index, item ->
                        Column(Modifier.clickable { previewIndex = index }) {
                            AsyncImage(
                                model = item.url, contentDescription = item.name, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                                    .background(MiuixTheme.colorScheme.surfaceContainer)
                            )
                            Text(item.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
                            Text(
                                listOfNotNull(
                                    ArtistImageRepository.sourceLabelRes(item.source)?.let { stringResource(it) },
                                    item.resolution
                                ).joinToString(" · "),
                                fontSize = 11.sp, maxLines = 1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
            }
        }
    }
    previewIndex?.let { index ->
        val models = visible.map { it.url }
        if (index in models.indices) {
            CoverPreviewDialog(
                model = models[index], models = models, initialIndex = index,
                title = searchedArtist, saveName = searchedArtist,
                imageKind = com.ella.music.data.ExportImageKind.Artist,
                onDismiss = { previewIndex = null }
            )
        } else previewIndex = null
    }
}
