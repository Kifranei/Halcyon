package com.ella.music.ui.player

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song

internal val LocalPlayerTimelinePlaying = staticCompositionLocalOf { false }

/** RawS Music's actual timeline renderers, with Halcyon's audio source and seek preferences. */
@Composable
internal fun PlayerWaveformSeekBar(
    value: Float, song: Song?, duration: Long, style: Int, onSeek: (Float) -> Unit,
    accent: Color, allowTapSeek: Boolean, onPreviewProgressChange: (Float?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val scaleAnimationEnabled by settingsManager.playerWaveformScaleAnimation.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_WAVEFORM_SCALE_ANIMATION
    )
    val densityPercent by settingsManager.playerWaveformDensity.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_WAVEFORM_DENSITY
    )
    val peakHeightPercent by settingsManager.playerWaveformPeakHeight.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_WAVEFORM_PEAK_HEIGHT
    )
    val content = LocalPlayerContentColor.current
    val colors = ImmersiveWaveformColors(
        played = content.copy(alpha = .38f), remaining = content.copy(alpha = .92f),
        climaxPlayed = accent.copy(alpha = .46f), climaxRemaining = accent,
        needle = content, time = content.copy(alpha = .72f)
    )
    val position = (value.coerceIn(0f, 1f) * duration).toLong()
    if (style == SettingsManager.PLAYER_PROGRESS_STYLE_SEGMENTS) {
        ImmersiveSecondProgressBar(song, position, duration, LocalPlayerTimelinePlaying.current,
            colors, {}, onSeek, allowTapSeek, onPreviewProgressChange, modifier = modifier,
            scaleAnimationEnabled = scaleAnimationEnabled, densityPercent = densityPercent,
            peakHeightPercent = peakHeightPercent)
    } else {
        ImmersiveWaveformProgressBar(song, position, duration, LocalPlayerTimelinePlaying.current,
            colors, climaxEnabled = false,
            // Density resamples the cached analysis envelope at draw time; no re-scan.
            waveformBarCount = WaveformProgressTuning.waveformVisibleBarCount(
                WaveformProgressTuning.BASE_WAVEFORM_BAR_COUNT, densityPercent
            ),
            onSeekStart = {}, onSeekStop = onSeek,
            allowTapSeek = allowTapSeek, onPreview = onPreviewProgressChange, modifier = modifier,
            scaleAnimationEnabled = scaleAnimationEnabled, peakHeightPercent = peakHeightPercent)
    }
}
