package com.ella.music.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SpeedPitchSheetContent(
    speed: Float,
    pitch: Float,
    onBack: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit
) {
    HalfSheetTitle(title = stringResource(R.string.player_speed_pitch), onBack = onBack)
    Spacer(modifier = Modifier.height(22.dp))
    SpeedPitchSliderCard(
        title = stringResource(R.string.player_speed_playback),
        value = speed,
        onValueChange = onSpeed
    )
    Spacer(modifier = Modifier.height(12.dp))
    SpeedPitchSliderCard(
        title = stringResource(R.string.player_pitch_playback),
        value = pitch,
        onValueChange = onPitch
    )
}

@Composable
private fun SpeedPitchSliderCard(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.58f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${value.formatPlaybackStep()}x",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
        DottedValueSlider(
            value = value,
            valueRange = 0.5f..2f,
            steps = 30,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
        )
    }
}

private fun Float.formatPlaybackStep(): String = "%.2f".format(this.coerceIn(0.5f, 2f))
