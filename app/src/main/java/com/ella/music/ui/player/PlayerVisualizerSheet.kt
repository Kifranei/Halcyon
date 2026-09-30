package com.ella.music.ui.player

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.settings.SettingsCardGroup
import com.ella.music.ui.settings.SettingsIntSliderPreference
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun VisualizerSheetContent(
    enabled: Boolean,
    opacity: Int,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOpacityChange: (Int) -> Unit,
    showHeader: Boolean = true
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val rainbow by settingsManager.audioVisualizerRainbow.collectAsState(initial = false)
    val blurRadius by settingsManager.audioVisualizerBlur.collectAsState(initial = 0)
    val visualizerStyle by settingsManager.audioVisualizerStyle.collectAsState(
        initial = SettingsManager.DEFAULT_AUDIO_VISUALIZER_STYLE
    )
    val progressStyle by settingsManager.playerProgressStyle.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_PROGRESS_STYLE
    )
    val visualizerStyleLabels = listOf(
        stringResource(R.string.player_visualizer_style_flow),
        stringResource(R.string.player_visualizer_style_raws_spectrum),
        stringResource(R.string.player_visualizer_style_particles),
        stringResource(R.string.player_visualizer_style_strings),
        stringResource(R.string.player_visualizer_style_classic_bars),
        stringResource(R.string.player_visualizer_style_water_ripple),
        stringResource(R.string.player_visualizer_style_cover_overlay)
    )
    val visualizerHeight by settingsManager.audioVisualizerHeight.collectAsState(
        initial = SettingsManager.DEFAULT_AUDIO_VISUALIZER_HEIGHT
    )
    val visualizerHeightRange =
        SettingsManager.MIN_AUDIO_VISUALIZER_HEIGHT..SettingsManager.MAX_AUDIO_VISUALIZER_HEIGHT
    val waveformScaleAnimation by settingsManager.playerWaveformScaleAnimation.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_WAVEFORM_SCALE_ANIMATION
    )
    val waveformDensity by settingsManager.playerWaveformDensity.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_WAVEFORM_DENSITY
    )
    val waveformPeakHeight by settingsManager.playerWaveformPeakHeight.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_WAVEFORM_PEAK_HEIGHT
    )
    val progressStyleLabels = listOf(
        stringResource(R.string.player_progress_style_glow),
        stringResource(R.string.player_progress_style_waveform),
        stringResource(R.string.player_progress_style_segments)
    )

    if (showHeader) {
        HalfSheetTitle(title = stringResource(R.string.player_visualizer_settings), onBack = onBack)
        Spacer(modifier = Modifier.height(22.dp))
    }
    SettingsCardGroup {
        SwitchPreference(
            title = stringResource(R.string.player_music_visualizer),
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
        SwitchPreference(
            title = stringResource(R.string.player_visualizer_rainbow),
            checked = rainbow,
            onCheckedChange = { scope.launch { settingsManager.setAudioVisualizerRainbow(it) } }
        )
        WindowSpinnerPreference(
            title = stringResource(R.string.player_visualizer_style),
            summary = visualizerStyleLabels[visualizerStyle.coerceIn(visualizerStyleLabels.indices)],
            items = visualizerStyleLabels.map { DropdownItem(title = it) },
            selectedIndex = visualizerStyle.coerceIn(visualizerStyleLabels.indices),
            onSelectedIndexChange = { index ->
                scope.launch { settingsManager.setAudioVisualizerStyle(index) }
            }
        )
        WindowSpinnerPreference(
            title = stringResource(R.string.player_progress_style),
            summary = progressStyleLabels[progressStyle.coerceIn(progressStyleLabels.indices)],
            items = progressStyleLabels.map { DropdownItem(title = it) },
            selectedIndex = progressStyle.coerceIn(progressStyleLabels.indices),
            onSelectedIndexChange = { index ->
                scope.launch { settingsManager.setPlayerProgressStyle(index) }
            }
        )
        if (progressStyle != SettingsManager.PLAYER_PROGRESS_STYLE_GLOW) {
            PlayerWaveformTuningPreferences(
                scaleAnimationEnabled = waveformScaleAnimation,
                densityPercent = waveformDensity,
                peakHeightPercent = waveformPeakHeight,
                onScaleAnimationChange = { scope.launch { settingsManager.setPlayerWaveformScaleAnimation(it) } },
                onDensityChange = { scope.launch { settingsManager.setPlayerWaveformDensity(it) } },
                onPeakHeightChange = { scope.launch { settingsManager.setPlayerWaveformPeakHeight(it) } }
            )
        }
        SettingsIntSliderPreference(
            title = stringResource(R.string.player_visualizer_height),
            summary = stringResource(R.string.player_visualizer_height_summary),
            valueText = "${visualizerHeight.coerceIn(visualizerHeightRange)}%",
            value = visualizerHeight.coerceIn(visualizerHeightRange),
            valueRange = visualizerHeightRange,
            // 10% increments: 50, 60, ... 200.
            steps = AUDIO_VISUALIZER_HEIGHT_SLIDER_STEPS,
            onValueChange = {
                val next = snapAudioVisualizerHeight(it)
                scope.launch { settingsManager.setAudioVisualizerHeight(next) }
            }
        )
        SettingsIntSliderPreference(
            title = stringResource(R.string.player_visualizer_blur),
            summary = stringResource(R.string.player_visualizer_blur_summary),
            valueText = "$blurRadius dp",
            value = blurRadius,
            valueRange = 0..40,
            onValueChange = { scope.launch { settingsManager.setAudioVisualizerBlur(it) } }
        )
        SettingsIntSliderPreference(
            title = stringResource(R.string.player_visualizer_opacity),
            summary = stringResource(R.string.player_visualizer_opacity_summary),
            valueText = "$opacity%",
            value = opacity.coerceIn(20, 100),
            valueRange = 20..100,
            steps = 15,
            onValueChange = { onOpacityChange(it.coerceIn(20, 100)) }
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.player_visualizer_permission_summary),
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

/** Issue #674: waveform-style progress tuning, shown only for the waveform/segments styles. */
@Composable
internal fun PlayerWaveformTuningPreferences(
    scaleAnimationEnabled: Boolean,
    densityPercent: Int,
    peakHeightPercent: Int,
    onScaleAnimationChange: (Boolean) -> Unit,
    onDensityChange: (Int) -> Unit,
    onPeakHeightChange: (Int) -> Unit
) {
    val densityRange = SettingsManager.MIN_PLAYER_WAVEFORM_DENSITY..SettingsManager.MAX_PLAYER_WAVEFORM_DENSITY
    val peakHeightRange =
        SettingsManager.MIN_PLAYER_WAVEFORM_PEAK_HEIGHT..SettingsManager.MAX_PLAYER_WAVEFORM_PEAK_HEIGHT
    SwitchPreference(
        title = stringResource(R.string.player_waveform_scale_animation),
        summary = stringResource(R.string.player_waveform_scale_animation_summary),
        checked = scaleAnimationEnabled,
        onCheckedChange = onScaleAnimationChange
    )
    SettingsIntSliderPreference(
        title = stringResource(R.string.player_waveform_density),
        summary = stringResource(R.string.player_waveform_density_summary),
        valueText = "${densityPercent.coerceIn(densityRange)}%",
        value = densityPercent.coerceIn(densityRange),
        valueRange = densityRange,
        // 10% increments: 50, 60, ... 200.
        steps = WaveformProgressTuning.sliderSteps(densityRange, WaveformProgressTuning.DENSITY_SLIDER_STEP),
        onValueChange = { onDensityChange(WaveformProgressTuning.snapToStep(it, WaveformProgressTuning.DENSITY_SLIDER_STEP).coerceIn(densityRange)) }
    )
    SettingsIntSliderPreference(
        title = stringResource(R.string.player_waveform_peak_height),
        summary = stringResource(R.string.player_waveform_peak_height_summary),
        valueText = "${peakHeightPercent.coerceIn(peakHeightRange)}%",
        value = peakHeightPercent.coerceIn(peakHeightRange),
        valueRange = peakHeightRange,
        // 5% increments: 50, 55, ... 150.
        steps = WaveformProgressTuning.sliderSteps(peakHeightRange, WaveformProgressTuning.PEAK_HEIGHT_SLIDER_STEP),
        onValueChange = { onPeakHeightChange(WaveformProgressTuning.snapToStep(it, WaveformProgressTuning.PEAK_HEIGHT_SLIDER_STEP).coerceIn(peakHeightRange)) }
    )
}
