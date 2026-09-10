package com.ella.music.ui.player

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.components.SelectionCheck
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun sleepTimerRemainingLabel(
    endRealtimeMs: Long,
    nowRealtimeMs: Long = SystemClock.elapsedRealtime()
): String? {
    val remaining = (endRealtimeMs - nowRealtimeMs).coerceAtLeast(0L)
    if (remaining <= 0L) return null
    return remaining.formatPlaybackDuration()
}

@Composable
internal fun rememberSleepTimerRemaining(endRealtimeMs: Long?): String? {
    var nowRealtimeMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(endRealtimeMs) {
        if (endRealtimeMs == null) return@LaunchedEffect
        while (SystemClock.elapsedRealtime() < endRealtimeMs) {
            nowRealtimeMs = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
        nowRealtimeMs = SystemClock.elapsedRealtime()
    }
    val end = endRealtimeMs ?: return null
    return sleepTimerRemainingLabel(end, nowRealtimeMs)
}

@Composable
internal fun TimerSheetContent(
    onBack: () -> Unit,
    sleepTimerEndRealtimeMs: Long?,
    stopAfterCurrentEnabled: Boolean,
    sleepTimerCustomMinutes: Int,
    sleepTimerStopAfterCurrent: Boolean,
    onStopAfterCurrent: (Boolean) -> Unit,
    onTimer: (Int) -> Unit,
    onCustomTimerMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    showHeader: Boolean = true
) {
    var customMinutes by remember(sleepTimerCustomMinutes) {
        mutableFloatStateOf(sleepTimerCustomMinutes.coerceIn(5, 120).toFloat())
    }
    var nowRealtimeMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val remainingMs = sleepTimerEndRealtimeMs
        ?.minus(nowRealtimeMs)
        ?.coerceAtLeast(0L)
    val timerActive = remainingMs != null && remainingMs > 0L

    LaunchedEffect(sleepTimerEndRealtimeMs) {
        while (sleepTimerEndRealtimeMs != null) {
            nowRealtimeMs = SystemClock.elapsedRealtime()
            delay(1000L)
        }
    }

    if (showHeader) {
        HalfSheetTitle(title = stringResource(R.string.player_sleep_timer_title), onBack = onBack)
        Spacer(modifier = Modifier.height(18.dp))
    }

    if (timerActive) {
        TimerStatusCard(
            title = stringResource(R.string.player_sleep_timer_running),
            subtitle = stringResource(R.string.player_sleep_timer_remaining, remainingMs.formatPlaybackDuration())
        )
        Spacer(modifier = Modifier.height(12.dp))
    } else {
        listOf(10, 15, 20, 30, 40, 60).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { minutes ->
                    HalfSheetPill(
                        text = stringResource(R.string.player_minutes_value, minutes),
                        onClick = { onTimer(minutes) },
                        outlined = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.player_custom_duration),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.player_minutes_value, customMinutes.toInt()),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Slider(
                value = customMinutes.coerceIn(5f, 120f),
                valueRange = 5f..120f,
                onValueChange = {
                    customMinutes = it
                    onCustomTimerMinutes(it.toInt().coerceIn(5, 120))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { onTimer(customMinutes.toInt().coerceAtLeast(1)) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.primary,
                contentColor = MiuixTheme.colorScheme.onPrimary
            )
        ) {
            Text(text = stringResource(R.string.player_start_timer_minutes, customMinutes.toInt()))
        }
        Spacer(modifier = Modifier.height(10.dp))
    }

    StopAfterCurrentRow(
        checked = stopAfterCurrentEnabled || sleepTimerStopAfterCurrent,
        onCheckedChange = onStopAfterCurrent
    )
    if (timerActive) {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onCancelTimer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.player_cancel_sleep_timer))
        }
    }
}

@Composable
private fun TimerStatusCard(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer
        )
    ) {
        BasicComponent(
            title = title,
            summary = subtitle,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun StopAfterCurrentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer
        )
    ) {
        BasicComponent(
            title = stringResource(R.string.player_pause_after_current_song),
            onClick = { onCheckedChange(!checked) },
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            endActions = {
                SelectionCheck(
                    selected = checked,
                    size = 22.dp,
                    unselectedColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.18f)
                )
            }
        )
    }
}
