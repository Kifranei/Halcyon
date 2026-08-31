package com.ella.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.repository.MusicRepository
import com.ella.music.ui.settings.SettingsCardGroup
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

@Composable
internal fun LyricToggleButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(LocalPlayerContentColor.current.copy(alpha = if (active) 0.24f else 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = LocalPlayerContentColor.current.copy(alpha = if (active) 1f else 0.62f)
        )
    }
}

@Composable
internal fun LyricActionMenu(
    showPronunciation: Boolean,
    showTranslation: Boolean,
    keepScreenOn: Boolean,
    lyricFormatAvailability: MusicRepository.LyricFormatAvailability,
    preferTtmlLyrics: Boolean?,
    lyricSourceMode: Int,
    layoutProfile: PlayerLyricLayoutProfile,
    fontScale: Float,
    secondaryFontScale: Float,
    primaryTextSizeSp: Float,
    secondaryTextSizeSp: Float,
    perspectiveEffect: Boolean,
    perspectiveYAngle: Int,
    showPerspectiveToggle: Boolean = true,
    onTogglePronunciation: () -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onTogglePerspectiveEffect: () -> Unit,
    onPerspectiveYAngle: (Int) -> Unit,
    onLyricSourceMode: (Int) -> Unit,
    onLyricFormatPreference: (Boolean) -> Unit,
    onFontScale: (Float) -> Unit,
    onSecondaryFontScale: (Float) -> Unit,
    onPrimaryTextSize: (Float) -> Unit,
    onSecondaryTextSize: (Float) -> Unit,
    onStyleSettings: (() -> Unit)? = null,
    showSheetHeader: Boolean = false,
    onBack: (() -> Unit)? = null,
    applyScrollableContainer: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val pronunciationBelow by settingsManager.lyricPronunciationBelow.collectAsState(initial = false)
    val sustainThresholdMs by settingsManager.appleMusicLyricsSustainThresholdMs.collectAsState(
        initial = SettingsManager.DEFAULT_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS
    )
    var sustainThresholdPreview by remember(sustainThresholdMs) {
        mutableStateOf(sustainThresholdMs.toFloat())
    }
    val wordLiftEnabled by settingsManager.appleMusicLyricsWordLift.collectAsState(initial = true)
    val containerModifier = if (applyScrollableContainer) {
        modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    } else {
        modifier
    }
    Column(
        modifier = containerModifier
    ) {
        if (showSheetHeader) {
            HalfSheetTitle(
                title = stringResource(R.string.player_lyrics_display),
                onBack = { onBack?.invoke() }
            )
            Spacer(modifier = Modifier.height(18.dp))
        }
        SettingsCardGroup {
            SwitchPreference(
                title = stringResource(R.string.player_show_pronunciation),
                checked = showPronunciation,
                onCheckedChange = { onTogglePronunciation() }
            )
            if (showPronunciation) {
                SwitchPreference(
                    title = stringResource(R.string.player_pronunciation_below),
                    checked = pronunciationBelow,
                    onCheckedChange = { below ->
                        scope.launch { settingsManager.setLyricPronunciationBelow(below) }
                    }
                )
            }
            SwitchPreference(
                title = stringResource(R.string.player_show_translation),
                checked = showTranslation,
                onCheckedChange = { onToggleTranslation() }
            )
            SwitchPreference(
                title = stringResource(R.string.player_enable_lyrics_word_lift),
                checked = wordLiftEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { settingsManager.setAppleMusicLyricsWordLift(enabled) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.player_enable_keep_screen_on),
                checked = keepScreenOn,
                onCheckedChange = { onToggleKeepScreenOn() }
            )
            if (showPerspectiveToggle) {
                SwitchPreference(
                    title = stringResource(R.string.player_enable_perspective_effect),
                    checked = perspectiveEffect,
                    onCheckedChange = { onTogglePerspectiveEffect() }
                )
            }
            onStyleSettings?.let { openStyleSettings ->
                ArrowPreference(
                    title = stringResource(R.string.player_lyric_style_settings),
                    onClick = openStyleSettings
                )
            }
        }
        SettingsCardGroup {
            SliderPreference(
                title = stringResource(R.string.player_lyrics_sustain_threshold),
                value = sustainThresholdPreview,
                valueRange = SettingsManager.MIN_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS.toFloat()..
                    SettingsManager.MAX_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS.toFloat(),
                // A continuous slider rounds to a millisecond below. Rendering 2,700 tick marks
                // would itself cause the settings surface to stutter (#470).
                steps = 0,
                valueText = stringResource(
                    R.string.player_lyrics_sustain_threshold_value,
                    sustainThresholdPreview.toInt()
                ),
                onValueChange = { sustainThresholdPreview = it },
                onValueChangeFinished = {
                    scope.launch {
                        settingsManager.setAppleMusicLyricsSustainThresholdMs(
                            sustainThresholdPreview.toInt()
                        )
                    }
                }
            )
        }
        if (lyricFormatAvailability.hasBoth) {
            SettingsCardGroup {
                WindowSpinnerPreference(
                    title = stringResource(R.string.player_lyric_format),
                    items = listOf(
                        DropdownItem(title = stringResource(R.string.player_lyric_format_ttml)),
                        DropdownItem(title = stringResource(R.string.player_lyric_format_lrc))
                    ),
                    selectedIndex = if (preferTtmlLyrics != false) 0 else 1,
                    onSelectedIndexChange = { index ->
                        onLyricFormatPreference(index == 0)
                    }
                )
            }
        }
        SettingsCardGroup {
            WindowSpinnerPreference(
                title = stringResource(R.string.player_lyric_source),
                items = listOf(
                    DropdownItem(title = stringResource(R.string.player_lyric_source_auto)),
                    DropdownItem(title = stringResource(R.string.player_lyric_source_external)),
                    DropdownItem(title = stringResource(R.string.player_lyric_source_embedded))
                ),
                selectedIndex = when (lyricSourceMode) {
                    SettingsManager.LYRIC_SOURCE_EXTERNAL -> 1
                    SettingsManager.LYRIC_SOURCE_EMBEDDED -> 2
                    else -> 0
                },
                onSelectedIndexChange = { index ->
                    onLyricSourceMode(
                        when (index) {
                            1 -> SettingsManager.LYRIC_SOURCE_EXTERNAL
                            2 -> SettingsManager.LYRIC_SOURCE_EMBEDDED
                            else -> SettingsManager.LYRIC_SOURCE_AUTO
                        }
                    )
                }
            )
        }
    }
}

@Composable
internal fun LyricStyleSettingsContent(
    layoutProfile: PlayerLyricLayoutProfile,
    fontScale: Float,
    secondaryFontScale: Float,
    primaryTextSizeSp: Float,
    secondaryTextSizeSp: Float,
    perspectiveEffect: Boolean,
    perspectiveYAngle: Int,
    onPerspectiveYAngle: (Int) -> Unit,
    onFontScale: (Float) -> Unit,
    onSecondaryFontScale: (Float) -> Unit,
    onPrimaryTextSize: (Float) -> Unit,
    onSecondaryTextSize: (Float) -> Unit,
    onBack: () -> Unit,
    showSheetHeader: Boolean = true,
    applyScrollableContainer: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val nonCurrentBlurPercent by settingsManager.lyricNonCurrentBlurPercent.collectAsState(initial = 40)
    val configuration = LocalConfiguration.current
    val ultraWideLandscape = isUltraWideLandscapePlayerLayout(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp
    )
    val fontScaleRange = layoutProfile.primaryScaleRangePercent(ultraWideLandscape)
    val secondaryFontScaleRange = layoutProfile.secondaryScaleRangePercent(ultraWideLandscape)
    val primaryTextSizeRange = layoutProfile.primaryTextSizeRangeSp()
    val secondaryTextSizeRange = layoutProfile.secondaryTextSizeRangeSp()
    val safeFontScale = fontScale.coerceIn(fontScaleRange.first / 100f, fontScaleRange.last / 100f)
    val safeSecondaryFontScale = secondaryFontScale.coerceIn(
        secondaryFontScaleRange.first / 100f,
        secondaryFontScaleRange.last / 100f
    )
    val safePrimaryTextSize = primaryTextSizeSp.coerceIn(
        primaryTextSizeRange.first.toFloat(),
        primaryTextSizeRange.last.toFloat()
    )
    val safeSecondaryTextSize = secondaryTextSizeSp.coerceIn(
        secondaryTextSizeRange.first.toFloat(),
        secondaryTextSizeRange.last.toFloat()
    )
    var previewPerspectiveYAngle by remember(perspectiveYAngle) { mutableStateOf(perspectiveYAngle.toFloat()) }
    var previewFontScale by remember(fontScaleRange) { mutableStateOf(safeFontScale) }
    var previewPrimaryTextSize by remember(primaryTextSizeRange) { mutableStateOf(safePrimaryTextSize) }
    var previewSecondaryFontScale by remember(secondaryFontScaleRange) { mutableStateOf(safeSecondaryFontScale) }
    var previewSecondaryTextSize by remember(secondaryTextSizeRange) { mutableStateOf(safeSecondaryTextSize) }
    var previewNonCurrentBlur by remember(nonCurrentBlurPercent) {
        mutableStateOf(nonCurrentBlurPercent.toFloat())
    }
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    val containerModifier = if (applyScrollableContainer) {
        modifier
            .heightIn(max = maxSheetHeight)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    } else {
        modifier
    }

    Column(modifier = containerModifier) {
        if (showSheetHeader) {
            HalfSheetTitle(
                title = stringResource(R.string.player_lyric_style_settings),
                onBack = onBack
            )
            Spacer(modifier = Modifier.height(18.dp))
        }
        SettingsCardGroup {
            if (perspectiveEffect) {
                SliderPreference(
                    title = stringResource(R.string.player_perspective_y_angle),
                    value = previewPerspectiveYAngle.coerceIn(0f, 45f),
                    valueRange = 0f..45f,
                    steps = 9,
                    valueText = "${previewPerspectiveYAngle.toInt()}°",
                    onValueChange = {
                        previewPerspectiveYAngle = it
                        onPerspectiveYAngle(it.toInt())
                    }
                )
            }
            SliderPreference(
                title = stringResource(R.string.settings_lyric_non_current_blur),
                summary = stringResource(R.string.settings_lyric_non_current_blur_summary),
                value = previewNonCurrentBlur.coerceIn(0f, 100f),
                valueRange = 0f..100f,
                steps = 100,
                valueText = "${previewNonCurrentBlur.roundToInt()}%",
                onValueChange = {
                    previewNonCurrentBlur = it
                    scope.launch { settingsManager.setLyricNonCurrentBlurPercent(it.roundToInt()) }
                }
            )
        }
        SettingsCardGroup {
            SliderPreference(
                title = stringResource(R.string.player_lyric_font_scale),
                value = previewFontScale,
                valueRange = fontScaleRange.first / 100f..fontScaleRange.last / 100f,
                steps = (fontScaleRange.last - fontScaleRange.first) / 5,
                valueText = "${(previewFontScale * 100f).roundToInt()}%",
                onValueChange = {
                    previewFontScale = it
                    onFontScale(it)
                }
            )
            SliderPreference(
                title = stringResource(R.string.player_lyric_font_size),
                value = previewPrimaryTextSize,
                valueRange = primaryTextSizeRange.first.toFloat()..primaryTextSizeRange.last.toFloat(),
                steps = primaryTextSizeRange.last - primaryTextSizeRange.first,
                valueText = "${previewPrimaryTextSize.roundToInt()}sp",
                onValueChange = {
                    previewPrimaryTextSize = it
                    onPrimaryTextSize(it)
                }
            )
            SliderPreference(
                title = stringResource(R.string.player_lyric_secondary_font_scale),
                value = previewSecondaryFontScale,
                valueRange = secondaryFontScaleRange.first / 100f..secondaryFontScaleRange.last / 100f,
                steps = (secondaryFontScaleRange.last - secondaryFontScaleRange.first) / 5,
                valueText = "${(previewSecondaryFontScale * 100f).roundToInt()}%",
                onValueChange = {
                    previewSecondaryFontScale = it
                    onSecondaryFontScale(it)
                }
            )
            SliderPreference(
                title = stringResource(R.string.player_lyric_secondary_font_size),
                value = previewSecondaryTextSize,
                valueRange = secondaryTextSizeRange.first.toFloat()..secondaryTextSizeRange.last.toFloat(),
                steps = secondaryTextSizeRange.last - secondaryTextSizeRange.first,
                valueText = "${previewSecondaryTextSize.roundToInt()}sp",
                onValueChange = {
                    previewSecondaryTextSize = it
                    onSecondaryTextSize(it)
                }
            )
        }
    }
}
