package com.ella.music.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.effect.BgEffectBackground
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import com.ella.music.ui.about.aboutCardBlendColors
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsWizardScreen(
    onBack: () -> Unit,
    onOpenScanFolders: () -> Unit = {},
    onOpenCoverMedia: () -> Unit = {},
    onFinish: () -> Unit = onBack
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val bgEffectVersion by settingsManager.bgEffectVersion.collectCachedAsState("bgEffectVersion", settingsManager.defaultBgEffectVersion)
    var step by rememberSaveable { mutableIntStateOf(0) }
    val lastStep = 4
    val playerPageStyle by settingsManager.playerPageStyle.collectCachedAsState(
        "wizardPlayerPageStyle",
        SettingsManager.DEFAULT_PLAYER_PAGE_STYLE
    )
    val playerImmersiveCover by settingsManager.playerImmersiveCover.collectCachedAsState("wizardPlayerImmersive", true)
    val playerShowSongAnnotation by settingsManager.playerShowSongAnnotation.collectCachedAsState(
        "wizardPlayerAnnotation",
        true
    )
    val filterVideoFiles by settingsManager.filterVideoFiles.collectCachedAsState("wizardFilterVideo", true)
    val playerPageStyleOptions = listOf(
        SettingsManager.PLAYER_PAGE_STYLE_HALCYON to stringResource(R.string.settings_player_page_style_halcyon),
        SettingsManager.PLAYER_PAGE_STYLE_APPLE_MUSIC to stringResource(R.string.settings_player_page_style_apple_music),
        SettingsManager.PLAYER_PAGE_STYLE_IMMERSIVE_LYRICS to stringResource(R.string.settings_player_page_style_immersive_lyrics)
    )
    val selectedPlayerPageStyle = playerPageStyleOptions.indexOfFirst { it.first == playerPageStyle }
        .takeIf { it >= 0 } ?: 0
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val heroColor = MiuixTheme.colorScheme.onBackground
    val backdrop = rememberLayerBackdrop()
    val blurEnable by remember { mutableStateOf(isRenderEffectSupported()) }
    val isOs1 = bgEffectVersion == SettingsManager.BG_EFFECT_OS1
    val effectiveBlurEnable = blurEnable && !isOs1
    val cardBlendColors = remember(isDark) { aboutCardBlendColors(isDark) }
    val frosting = remember(backdrop, effectiveBlurEnable, cardBlendColors, isOs1) {
        if (isOs1) null else SettingsCardFrosting(backdrop, effectiveBlurEnable, cardBlendColors)
    }

    fun completeWizard() {
        scope.launch { settingsManager.setSetupWizardCompleted(true) }
        onFinish()
    }

    BackHandler(enabled = step > 0) {
        step -= 1
    }

    BgEffectBackground(
        dynamicBackground = !isOs1,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        effectBackground = !isOs1,
        isDarkTheme = isDark,
        isOs3 = bgEffectVersion == SettingsManager.BG_EFFECT_OS3
    ) {
        CompositionLocalProvider(LocalSettingsCardFrosting provides frosting) {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (step > 0) {
                    IconButton(
                        onClick = { step -= 1 },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.common_back),
                            tint = heroColor
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.settings_setup_wizard_skip),
                    color = heroColor.copy(alpha = 0.88f),
                    fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable { completeWizard() }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberSettingsScrollState("settings_wizard_$step"))
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(36.dp))
                Text(
                    text = stringResource(R.string.settings_setup_wizard),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = heroColor
                )
                Text(
                    text = stringResource(R.string.settings_setup_wizard_step, step + 1, lastStep + 1),
                    fontSize = 14.sp,
                    color = heroColor.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
                )
            when (step) {
                0 -> WizardIntroCard()
                1 -> {
                    SmallTitle(text = stringResource(R.string.settings_library_scan))
                    SettingsCardGroup {
                        Column {
                            top.yukonga.miuix.kmp.preference.ArrowPreference(
                                title = stringResource(R.string.settings_scan_folders),
                                summary = stringResource(R.string.settings_scan_folders_summary),
                                onClick = onOpenScanFolders
                            )
                            SwitchPreference(
                                title = stringResource(R.string.settings_filter_video_files),
                                summary = stringResource(R.string.settings_filter_video_files_summary),
                                checked = filterVideoFiles,
                                onCheckedChange = { scope.launch { settingsManager.setFilterVideoFiles(it) } }
                            )
                        }
                    }
                }
                2 -> {
                    SmallTitle(text = stringResource(R.string.settings_player_page_style))
                    SettingsCardGroup {
                        Column {
                            WindowSpinnerPreference(
                                title = stringResource(R.string.settings_player_page_style),
                                summary = stringResource(R.string.settings_player_page_style_summary),
                                items = playerPageStyleOptions.map { DropdownItem(title = it.second) },
                                selectedIndex = selectedPlayerPageStyle,
                                onSelectedIndexChange = { index ->
                                    playerPageStyleOptions.getOrNull(index)?.first?.let { style ->
                                        scope.launch { settingsManager.setPlayerPageStyle(style) }
                                    }
                                }
                            )
                            SwitchPreference(
                                title = stringResource(R.string.settings_player_immersive_cover),
                                summary = stringResource(R.string.settings_player_immersive_cover_summary),
                                checked = playerImmersiveCover,
                                onCheckedChange = { scope.launch { settingsManager.setPlayerImmersiveCover(it) } }
                            )
                            SwitchPreference(
                                title = stringResource(R.string.settings_player_show_song_annotation),
                                summary = stringResource(R.string.settings_player_show_song_annotation_summary),
                                checked = playerShowSongAnnotation,
                                onCheckedChange = { scope.launch { settingsManager.setPlayerShowSongAnnotation(it) } }
                            )
                        }
                    }
                }
                3 -> {
                    SettingsDynamicCoverSection()
                    SettingsMusicVideoSection()
                    SettingsArtistCoverSection()
                    SettingsCardGroup {
                        top.yukonga.miuix.kmp.preference.ArrowPreference(
                            title = stringResource(R.string.settings_cover_media),
                            summary = stringResource(R.string.settings_cover_media_summary),
                            onClick = onOpenCoverMedia
                        )
                    }
                }
                else -> WizardIntroCard(done = true)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary)
                        .clickable {
                            if (step == lastStep) completeWizard() else step += 1
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (step == lastStep) MiuixIcons.Regular.Ok else MiuixIcons.Basic.ArrowRight,
                        contentDescription = stringResource(
                            if (step == lastStep) R.string.settings_setup_wizard_finish else R.string.settings_setup_wizard_next
                        ),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun WizardIntroCard(done: Boolean = false) {
    SettingsCardGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    if (done) R.string.settings_setup_wizard_done_title else R.string.settings_setup_wizard_welcome_title
                ),
                fontSize = 18.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (done) R.string.settings_setup_wizard_done_summary else R.string.settings_setup_wizard_welcome_summary
                ),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}
