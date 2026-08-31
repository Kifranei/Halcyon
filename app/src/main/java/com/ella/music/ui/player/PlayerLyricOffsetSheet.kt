package com.ella.music.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.ui.settings.SettingsCardGroup
import kotlin.math.round
import top.yukonga.miuix.kmp.preference.SliderPreference

@Composable
internal fun LyricOffsetSheetContent(
    offsetMs: Long,
    onBack: () -> Unit,
    onOffsetChange: (Long) -> Unit
) {
    HalfSheetTitle(title = stringResource(R.string.player_lyric_offset), onBack = onBack)
    Spacer(modifier = Modifier.height(20.dp))
    SettingsCardGroup {
        SliderPreference(
            title = stringResource(R.string.player_lyric_offset),
            summary = stringResource(R.string.player_lyric_offset_summary),
            value = offsetMs.toFloat(),
            valueRange = -5000f..5000f,
            steps = 100,
            valueText = offsetMs.formatLyricOffset(),
            onValueChange = { onOffsetChange(it.toLong().roundToStep(100L)) }
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        HalfSheetPill(
            text = "-500ms",
            onClick = { onOffsetChange((offsetMs - 500L).coerceIn(-5000L, 5000L)) },
            modifier = Modifier.weight(1f)
        )
        HalfSheetPill(
            text = stringResource(R.string.common_reset),
            selected = offsetMs == 0L,
            onClick = { onOffsetChange(0L) },
            modifier = Modifier.weight(1f)
        )
        HalfSheetPill(
            text = "+500ms",
            onClick = { onOffsetChange((offsetMs + 500L).coerceIn(-5000L, 5000L)) },
            modifier = Modifier.weight(1f)
        )
    }
}

private fun Long.formatLyricOffset(): String =
    if (this > 0) "+${this}ms" else "${this}ms"

private fun Long.roundToStep(step: Long): Long =
    (round(this.toFloat() / step).toLong() * step).coerceIn(-5000L, 5000L)
