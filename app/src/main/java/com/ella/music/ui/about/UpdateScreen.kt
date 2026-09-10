package com.ella.music.ui.about

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.BuildConfig
import com.ella.music.R
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.ellaPageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class UpdateScreenMode {
    Centered,
    CurrentLog,
    NewUpdate
}

@Composable
fun UpdateScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = ellaPageBackground()

    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Loading) }
    var showCurrentLog by remember { mutableStateOf(false) }

    fun checkUpdate() {
        state = UpdateUiState.Loading
        scope.launch {
            state = withContext(Dispatchers.IO) {
                runCatching { fetchLatestRelease() }
                    .fold(
                        onSuccess = { release ->
                            val hasUpdate = compareVersionNames(release.versionName, BuildConfig.VERSION_NAME) > 0
                            UpdateUiState.Ready(release = release, hasUpdate = hasUpdate)
                        },
                        onFailure = { error ->
                            UpdateUiState.Error(error.localizedMessage ?: context.getString(R.string.update_check_failed))
                        }
                    )
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(250)
        checkUpdate()
    }

    val readyState = state as? UpdateUiState.Ready
    val hasUpdate = readyState?.hasUpdate == true
    val isChecking = state is UpdateUiState.Loading

    val downloadState by UpdateDownloadManager.downloadState.collectAsState()

    LaunchedEffect(readyState?.release?.matchedAsset) {
        val asset = readyState?.release?.matchedAsset
        if (asset != null && readyState.hasUpdate) {
            UpdateDownloadManager.checkExistingApk(
                context = context,
                asset = asset,
                expectedVersion = readyState.release.versionName
            )
        }
    }

    val displayMode = when {
        hasUpdate -> UpdateScreenMode.NewUpdate
        showCurrentLog && readyState != null -> UpdateScreenMode.CurrentLog
        else -> UpdateScreenMode.Centered
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EllaSmallTopAppBar(
                title = if (displayMode == UpdateScreenMode.Centered) "" else stringResource(R.string.about_update),
                color = pageBackground,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {}
            )

            val bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 116.dp

            Crossfade(
                targetState = displayMode,
                animationSpec = tween(280),
                label = "UpdateScreenCrossfade",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { mode ->
                when (mode) {
                    UpdateScreenMode.Centered -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            HyperOsNoUpdateView(
                                state = state,
                                isDark = isDark,
                                showCurrentLog = showCurrentLog,
                                onToggleCurrentLog = { showCurrentLog = !showCurrentLog }
                            )
                        }
                    }

                    UpdateScreenMode.CurrentLog -> {
                        val release = readyState?.release
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomContentPadding),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Halcyon",
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Halcyon v${BuildConfig.VERSION_NAME} · " + stringResource(R.string.update_already_latest),
                                        fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.50f)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showCurrentLog = false }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val linkColor = if (isDark) Color(0xFF6B9BFF) else Color(0xFF2655FF)
                                        Text(
                                            text = stringResource(R.string.update_collapse_changelog),
                                            fontSize = 13.sp,
                                            color = linkColor,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Icon(
                                            imageVector = MiuixIcons.Basic.ArrowRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = linkColor
                                        )
                                    }
                                }
                            }

                            if (release != null) {
                                item {
                                    HyperOsChangelogCard(
                                        title = stringResource(R.string.update_current_version_log),
                                        changelog = release.body
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }

                    UpdateScreenMode.NewUpdate -> {
                        val release = readyState?.release
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomContentPadding),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (release != null) {
                                item {
                                    HyperOsNewUpdateHeaderCard(
                                        release = release,
                                        isDark = isDark
                                    )
                                }

                                item {
                                    HyperOsChangelogCard(
                                        title = stringResource(R.string.update_view_changelog),
                                        changelog = release.body
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Bottom Action Button (mirrors check_btn / update_btn in Xiaomi Updater)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            pageBackground.copy(alpha = 0.85f),
                            pageBackground
                        )
                    )
                )
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            HyperOsBottomActionButton(
                hasUpdate = hasUpdate,
                isChecking = isChecking,
                isDark = isDark,
                downloadState = downloadState,
                onButtonClick = {
                    when (val current = downloadState) {
                        is UpdateDownloadState.Downloading -> {
                            // Already in progress
                        }
                        is UpdateDownloadState.Completed -> {
                            UpdateDownloadManager.installApk(context, current.apkFile)
                        }
                        is UpdateDownloadState.Failed -> {
                            val asset = readyState?.release?.matchedAsset
                            if (asset != null) {
                                UpdateDownloadManager.startDownload(
                                    context = context,
                                    asset = asset,
                                    expectedVersion = readyState.release.versionName
                                )
                            } else {
                                checkUpdate()
                            }
                        }
                        UpdateDownloadState.Idle -> {
                            if (readyState != null && readyState.hasUpdate) {
                                val asset = readyState.release.matchedAsset
                                if (asset != null) {
                                    UpdateDownloadManager.startDownload(
                                        context = context,
                                        asset = asset,
                                        expectedVersion = readyState.release.versionName
                                    )
                                } else {
                                    val release = readyState.release
                                    val targetUrl = release.downloadUrl ?: release.htmlUrl
                                    context.openUrl(targetUrl)
                                }
                            } else {
                                checkUpdate()
                            }
                        }
                    }
                }
            )
        }
    }
}
