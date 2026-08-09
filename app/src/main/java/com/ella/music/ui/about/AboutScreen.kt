package com.ella.music.ui.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.BuildConfig
import com.ella.music.R
import com.ella.music.ui.effect.BgEffectBackground
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateToUpdate: () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    var logoHeightPx by remember { mutableIntStateOf(0) }
    val isDark = colorScheme.background.luminance() < 0.5f
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Loading) }

    val scrollProgress by remember {
        derivedStateOf {
            if (logoHeightPx <= 0) 0f
            else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    LaunchedEffect(Unit) {
        updateState = withContext(Dispatchers.IO) {
            runCatching { fetchLatestRelease() }
                .fold(
                    onSuccess = { release ->
                        UpdateUiState.Ready(
                            release = release,
                            hasUpdate = compareVersionNames(release.versionName, BuildConfig.VERSION_NAME) > 0
                        )
                    },
                    onFailure = { UpdateUiState.Error(it.localizedMessage.orEmpty()) }
                )
        }
    }

    Scaffold(
        topBar = {
            EllaSmallTopAppBar(
                title = stringResource(R.string.about),
                scrollBehavior = scrollBehavior,
                color = colorScheme.surface.copy(alpha = scrollProgress.coerceIn(0f, 1f)),
                titleColor = colorScheme.onSurface.copy(alpha = scrollProgress),
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.common_back),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AboutContent(
            padding = PaddingValues(top = innerPadding.calculateTopPadding()),
            scrollBehavior = scrollBehavior,
            scrollProgress = scrollProgress,
            lazyListState = lazyListState,
            onLogoHeightChanged = { logoHeightPx = it },
            updateState = updateState,
            onNavigateToUpdate = onNavigateToUpdate,
        )
    }
}

@Composable
private fun AboutContent(
    padding: PaddingValues,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    scrollProgress: Float,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    onLogoHeightChanged: (Int) -> Unit,
    updateState: UpdateUiState,
    onNavigateToUpdate: () -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val isDark = colorScheme.background.luminance() < 0.5f
    val blurEnable by remember { mutableStateOf(isRenderEffectSupported()) }
    val shaderSupported = remember { isRuntimeShaderSupported() }
    val uriHandler = LocalUriHandler.current

    val density = LocalDensity.current
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    val logoLiftPx = with(density) { 96.dp.toPx() }
    val heroTopPadding = 148.dp
    val heroBottomPadding = 112.dp

    val titleBlend = remember(isDark) { aboutTitleBlendColors(isDark) }

    val cardBlendColors = remember(isDark) { aboutCardBlendColors(isDark) }

    BgEffectBackground(
        dynamicBackground = true,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        effectBackground = true,
        isDarkTheme = isDark,
        alpha = {
            val fade = 1f - scrollProgress
            fade
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = (1f - scrollProgress * 1.35f).coerceIn(0f, 1f)
                    translationY = -logoLiftPx * scrollProgress
                }
                .padding(top = padding.calculateTopPadding() + heroTopPadding)
                .onSizeChanged { size -> with(density) { logoHeightDp = size.height.toDp() } },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier
                    .padding(top = 0.dp, bottom = 5.dp)
                    .then(
                        if (blurEnable) Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(16.dp),
                            blurRadius = 150f,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurColors(blendColors = titleBlend),
                            contentBlendMode = BlendMode.DstIn,
                            enabled = true,
                        ) else Modifier
                    ),
                text = stringResource(R.string.about_app_name),
                color = colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.onSurfaceVariantSummary,
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding()),
            overscrollEffect = null,
        ) {
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(logoHeightDp + heroTopPadding + padding.calculateTopPadding() + heroBottomPadding)
                        .onSizeChanged { size -> onLogoHeightChanged(size.height) },
                )
            }

            item {
                SmallTitle(text = stringResource(R.string.about_project))
                FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors, scrollProgress = scrollProgress) {
                    BasicComponent(
                        title = when {
                            updateState is UpdateUiState.Ready && updateState.hasUpdate ->
                                stringResource(R.string.update_new_version_found)
                            else -> stringResource(R.string.about_update)
                        },
                        summary = when (updateState) {
                            UpdateUiState.Loading -> stringResource(R.string.update_fetching_latest)
                            is UpdateUiState.Ready -> if (updateState.hasUpdate) {
                                stringResource(R.string.update_found_version, updateState.release.tagName)
                            } else {
                                stringResource(R.string.about_update_summary)
                            }
                            is UpdateUiState.Error -> stringResource(R.string.about_update_summary)
                        },
                        onClick = onNavigateToUpdate,
                    )
                    if (updateState is UpdateUiState.Ready && updateState.hasUpdate) {
                        Button(
                            onClick = onNavigateToUpdate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(text = stringResource(R.string.update_download))
                        }
                    }
                    BasicComponent(
                        title = stringResource(R.string.about_open_source),
                        summary = "Apache-2.0",
                        onClick = { uriHandler.openUri("https://www.apache.org/licenses/LICENSE-2.0") },
                    )
                }
            }

            item {
                SmallTitle(text = stringResource(R.string.about_acknowledgements))
                FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors, scrollProgress = scrollProgress) {
                    BasicComponent(
                        title = "BetterLyrics",
                        summary = stringResource(R.string.about_summary_betterlyrics),
                        onClick = { uriHandler.openUri("https://github.com/jayfunc/BetterLyrics") },
                    )
                    BasicComponent(
                        title = "Beautiful Lyrics",
                        summary = stringResource(R.string.about_summary_beautiful_lyrics),
                        onClick = { uriHandler.openUri("https://github.com/surfbryce/beautiful-lyrics") },
                    )
                    BasicComponent(
                        title = "LySy",
                        summary = stringResource(R.string.about_summary_lysy),
                        onClick = { uriHandler.openUri("https://github.com/pxeemo/LySy") },
                    )
                    BasicComponent(
                        title = stringResource(R.string.about_title_lightcone),
                        summary = stringResource(R.string.about_summary_lightcone),
                        onClick = { uriHandler.openUri("https://coneplayer.trantor.ink/") },
                    )
                }
            }

            item {
                SmallTitle(text = stringResource(R.string.about_open_source_projects))
                FrostedCard(backdrop = backdrop, blurEnable = blurEnable, cardBlendColors = cardBlendColors, scrollProgress = scrollProgress) {
                    BasicComponent(
                        title = "Miuix",
                        summary = stringResource(R.string.about_summary_miuix),
                        onClick = { uriHandler.openUri("https://github.com/compose-miuix-ui/miuix") },
                    )
                    BasicComponent(
                        title = "AndroidX Media3",
                        summary = stringResource(R.string.about_summary_media3),
                        onClick = { uriHandler.openUri("https://github.com/androidx/media") },
                    )
                    BasicComponent(
                        title = "FFmpeg",
                        summary = stringResource(R.string.about_summary_ffmpeg),
                        onClick = { uriHandler.openUri("https://ffmpeg.org") },
                    )
                    BasicComponent(
                        title = "FFmpegKit",
                        summary = stringResource(R.string.about_summary_ffmpegkit),
                        onClick = { uriHandler.openUri("https://github.com/arthenica/ffmpeg-kit") },
                    )
                    BasicComponent(
                        title = "Lyricon",
                        summary = stringResource(R.string.about_summary_lyricon),
                        onClick = { uriHandler.openUri("https://github.com/proify/lyricon") },
                    )
                    BasicComponent(
                        title = "SuperLyricApi",
                        summary = stringResource(R.string.about_summary_superlyricapi),
                        onClick = { uriHandler.openUri("https://github.com/HChenX/SuperLyricApi") },
                    )
                    BasicComponent(
                        title = "LyricGetter-API",
                        summary = stringResource(R.string.about_summary_lyricgetter),
                        onClick = { uriHandler.openUri("https://github.com/xiaowine/Lyric-Getter-Api") },
                    )
                    BasicComponent(
                        title = "Lyrico",
                        summary = stringResource(R.string.about_summary_lyrico),
                        onClick = { uriHandler.openUri("https://github.com/Replica0110/Lyrico") },
                    )
                    BasicComponent(
                        title = "163KeyDecrypter",
                        summary = stringResource(R.string.about_summary_163keydecrypter),
                        onClick = { uriHandler.openUri("https://github.com/lycode404/163KeyDecrypter") },
                    )
                    BasicComponent(
                        title = "Coil",
                        summary = stringResource(R.string.about_summary_coil),
                        onClick = { uriHandler.openUri("https://github.com/coil-kt/coil") },
                    )
                    BasicComponent(
                        title = "quickjs-wrapper Android",
                        summary = stringResource(R.string.about_summary_quickjs),
                        onClick = { uriHandler.openUri("https://github.com/HarlonWang/quickjs-wrapper") },
                    )
                    BasicComponent(
                        title = "LX Music Mobile",
                        summary = stringResource(R.string.about_summary_lxmusic),
                        onClick = { uriHandler.openUri("https://github.com/lyswhut/lx-music-mobile") },
                    )
                    BasicComponent(
                        title = "accompanist-lyrics-core",
                        summary = stringResource(R.string.about_summary_accompanist_lyrics_core),
                        onClick = { uriHandler.openUri("https://github.com/6xingyv/accompanist-lyrics-core") },
                    )
                    BasicComponent(
                        title = "Reorderable",
                        summary = stringResource(R.string.about_summary_reorderable),
                        onClick = { uriHandler.openUri("https://github.com/Calvin-LL/Reorderable") },
                    )
                    BasicComponent(
                        title = "RawS Music",
                        summary = stringResource(R.string.about_summary_raws_music),
                        onClick = { uriHandler.openUri("https://github.com/QFDY-GZC/RawS-Music") },
                    )
                }
            }

            item {
                Spacer(
                    Modifier
                        .height(160.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }
}

@Composable
private fun FrostedCard(
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop,
    blurEnable: Boolean,
    cardBlendColors: List<BlendColorEntry>,
    scrollProgress: Float,
    content: @Composable () -> Unit,
) {
    val isDark = colorScheme.background.luminance() < 0.5f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .then(
                if (blurEnable) Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(16.dp),
                    blurRadius = if (isDark) 72f else 64f,
                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                    colors = BlurColors(blendColors = cardBlendColors),
                    enabled = true,
                ) else Modifier
            ),
        colors = CardDefaults.defaultColors(
            if (blurEnable) {
                Color.Transparent
            } else if (isDark) {
                aboutCardFallbackColor(isDark).copy(alpha = 0.86f + 0.08f * scrollProgress.coerceIn(0f, 1f))
            } else {
                colorScheme.surfaceContainer
            },
            colorScheme.onSurface,
        ),
    ) {
        content()
    }
}
