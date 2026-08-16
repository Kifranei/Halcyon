package com.ella.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.repository.MusicRepository
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    lyricParserEngine: Int,
    layoutProfile: PlayerLyricLayoutProfile,
    fontScale: Float,
    secondaryFontScale: Float,
    primaryTextSizeSp: Float,
    secondaryTextSizeSp: Float,
    perspectiveEffect: Boolean,
    perspectiveYAngle: Int,
    onTogglePronunciation: () -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onTogglePerspectiveEffect: () -> Unit,
    onPerspectiveYAngle: (Int) -> Unit,
    onLyricSourceMode: (Int) -> Unit,
    onLyricFormatPreference: (Boolean) -> Unit,
    onLyricParserEngine: (Int) -> Unit,
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
        PlayerActionMenuItem(
            text = stringResource(if (showPronunciation) R.string.player_hide_pronunciation else R.string.player_show_pronunciation),
            onClick = onTogglePronunciation
        )
        if (showPronunciation) {
            PlayerActionMenuItem(
                text = stringResource(
                    if (pronunciationBelow) R.string.player_pronunciation_above else R.string.player_pronunciation_below
                ),
                onClick = { scope.launch { settingsManager.setLyricPronunciationBelow(!pronunciationBelow) } }
            )
        }
        PlayerActionMenuItem(
            text = stringResource(if (showTranslation) R.string.player_hide_translation else R.string.player_show_translation),
            onClick = onToggleTranslation
        )
        val wordLiftEnabled by settingsManager.appleMusicLyricsWordLift.collectAsState(initial = true)
        PlayerActionMenuItem(
            text = stringResource(
                if (wordLiftEnabled) R.string.player_disable_lyrics_word_lift
                else R.string.player_enable_lyrics_word_lift
            ),
            onClick = {
                scope.launch { settingsManager.setAppleMusicLyricsWordLift(!wordLiftEnabled) }
            }
        )
        Text(
            text = stringResource(R.string.player_lyrics_sustain_threshold),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        DottedValueSlider(
            value = sustainThresholdPreview,
            valueRange = SettingsManager.MIN_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS.toFloat()..
                SettingsManager.MAX_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS.toFloat(),
            steps = (SettingsManager.MAX_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS -
                SettingsManager.MIN_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS) /
                SettingsManager.STEP_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS,
            label = stringResource(R.string.player_lyrics_sustain_threshold_value, sustainThresholdPreview.toInt()),
            onValueChange = { sustainThresholdPreview = it },
            onValueChangeFinished = { value ->
                scope.launch { settingsManager.setAppleMusicLyricsSustainThresholdMs(value.toInt()) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
        )
        PlayerActionMenuItem(
            text = stringResource(if (keepScreenOn) R.string.player_disable_keep_screen_on else R.string.player_enable_keep_screen_on),
            onClick = onToggleKeepScreenOn
        )
        PlayerActionMenuItem(
            text = stringResource(if (perspectiveEffect) R.string.player_disable_perspective_effect else R.string.player_enable_perspective_effect),
            onClick = onTogglePerspectiveEffect
        )
        onStyleSettings?.let { openStyleSettings ->
            PlayerActionMenuItem(
                text = stringResource(R.string.player_lyric_style_settings),
                onClick = openStyleSettings
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (lyricFormatAvailability.hasBoth) {
            Text(
                text = stringResource(R.string.player_lyric_format),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LyricSourceChip(
                    text = stringResource(R.string.player_lyric_format_ttml),
                    selected = preferTtmlLyrics != false,
                    onClick = { onLyricFormatPreference(true) },
                    modifier = Modifier.weight(1f)
                )
                LyricSourceChip(
                    text = stringResource(R.string.player_lyric_format_lrc),
                    selected = preferTtmlLyrics == false,
                    onClick = { onLyricFormatPreference(false) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = stringResource(R.string.player_lyric_source),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LyricSourceChip(
                text = stringResource(R.string.player_lyric_source_auto),
                selected = lyricSourceMode == SettingsManager.LYRIC_SOURCE_AUTO,
                onClick = { onLyricSourceMode(SettingsManager.LYRIC_SOURCE_AUTO) },
                modifier = Modifier.weight(1f)
            )
            LyricSourceChip(
                text = stringResource(R.string.player_lyric_source_external),
                selected = lyricSourceMode == SettingsManager.LYRIC_SOURCE_EXTERNAL,
                onClick = { onLyricSourceMode(SettingsManager.LYRIC_SOURCE_EXTERNAL) },
                modifier = Modifier.weight(1f)
            )
            LyricSourceChip(
                text = stringResource(R.string.player_lyric_source_embedded),
                selected = lyricSourceMode == SettingsManager.LYRIC_SOURCE_EMBEDDED,
                onClick = { onLyricSourceMode(SettingsManager.LYRIC_SOURCE_EMBEDDED) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "解析引擎",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LyricSourceChip(
                text = "Halcyon",
                selected = lyricParserEngine == SettingsManager.LYRIC_PARSER_ENGINE_ELLA,
                onClick = { onLyricParserEngine(SettingsManager.LYRIC_PARSER_ENGINE_ELLA) },
                modifier = Modifier.weight(1f)
            )
            LyricSourceChip(
                text = "Accompanist",
                selected = lyricParserEngine == SettingsManager.LYRIC_PARSER_ENGINE_AUTO,
                onClick = { onLyricParserEngine(SettingsManager.LYRIC_PARSER_ENGINE_AUTO) },
                modifier = Modifier.weight(1f)
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
    modifier: Modifier = Modifier
) {
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

    Column(modifier = modifier) {
        if (showSheetHeader) {
            HalfSheetTitle(
                title = stringResource(R.string.player_lyric_style_settings),
                onBack = onBack
            )
            Spacer(modifier = Modifier.height(18.dp))
        }
        if (perspectiveEffect) {
            Text(
                text = stringResource(R.string.player_perspective_y_angle),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
            DottedValueSlider(
                value = previewPerspectiveYAngle.coerceIn(0f, 45f),
                valueRange = 0f..45f,
                steps = 9,
                label = "${previewPerspectiveYAngle.toInt()}°",
                onValueChange = {
                    previewPerspectiveYAngle = it
                    onPerspectiveYAngle(it.toInt())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
            )
        }
        Text(
            text = stringResource(R.string.player_lyric_font_scale),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        DottedValueSlider(
            value = previewFontScale,
            valueRange = fontScaleRange.first / 100f..fontScaleRange.last / 100f,
            steps = (fontScaleRange.last - fontScaleRange.first) / 5,
            label = "${(previewFontScale * 100f).roundToInt()}%",
            onValueChange = {
                previewFontScale = it
                onFontScale(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
        )
        Text(
            text = stringResource(R.string.player_lyric_font_size),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        DottedValueSlider(
            value = previewPrimaryTextSize,
            valueRange = primaryTextSizeRange.first.toFloat()..primaryTextSizeRange.last.toFloat(),
            steps = primaryTextSizeRange.last - primaryTextSizeRange.first,
            label = "${previewPrimaryTextSize.roundToInt()}sp",
            onValueChange = {
                previewPrimaryTextSize = it
                onPrimaryTextSize(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
        )
        Text(
            text = stringResource(R.string.player_lyric_secondary_font_scale),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        DottedValueSlider(
            value = previewSecondaryFontScale,
            valueRange = secondaryFontScaleRange.first / 100f..secondaryFontScaleRange.last / 100f,
            steps = (secondaryFontScaleRange.last - secondaryFontScaleRange.first) / 5,
            label = "${(previewSecondaryFontScale * 100f).roundToInt()}%",
            onValueChange = {
                previewSecondaryFontScale = it
                onSecondaryFontScale(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
        )
        Text(
            text = stringResource(R.string.player_lyric_secondary_font_size),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        DottedValueSlider(
            value = previewSecondaryTextSize,
            valueRange = secondaryTextSizeRange.first.toFloat()..secondaryTextSizeRange.last.toFloat(),
            steps = secondaryTextSizeRange.last - secondaryTextSizeRange.first,
            label = "${previewSecondaryTextSize.roundToInt()}sp",
            onValueChange = {
                previewSecondaryTextSize = it
                onSecondaryTextSize(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
        )
    }
}

@Composable
internal fun LyricSourceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (selected) "✓ $text" else text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}
