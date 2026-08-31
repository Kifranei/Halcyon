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
    onOpacityChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val visualizerStyle by settingsManager.audioVisualizerStyle.collectAsState(
        initial = SettingsManager.DEFAULT_AUDIO_VISUALIZER_STYLE
    )
    val progressStyle by settingsManager.playerProgressStyle.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_PROGRESS_STYLE
    )
    val visualizerStyleLabels = listOf(
        stringResource(R.string.player_visualizer_style_flow),
        stringResource(R.string.player_visualizer_style_raws_spectrum)
    )
    val progressStyleLabels = listOf(
        stringResource(R.string.player_progress_style_glow),
        stringResource(R.string.player_progress_style_waveform),
        stringResource(R.string.player_progress_style_segments)
    )

    HalfSheetTitle(title = stringResource(R.string.player_visualizer_settings), onBack = onBack)
    Spacer(modifier = Modifier.height(22.dp))
    SettingsCardGroup {
        SwitchPreference(
            title = stringResource(R.string.player_music_visualizer),
            checked = enabled,
            onCheckedChange = onEnabledChange
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
