package com.ella.music.ui.player

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.components.SelectionCheck
import com.ella.music.ui.components.ellaOverlayCardColor
import com.ella.music.ui.settings.SettingsNumberInputDialog
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SLEEP_TIMER_MAX_HOURS = SettingsManager.SLEEP_TIMER_MAX_HOURS
private const val SLEEP_TIMER_MIN_MINUTE = SettingsManager.SLEEP_TIMER_MIN_MINUTES
private const val SLEEP_TIMER_MAX_MINUTE = 59
private const val SLEEP_TIMER_MAX_TOTAL_MINUTES = SettingsManager.SLEEP_TIMER_MAX_MINUTES

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

/** Whole hours keep minute zero; a zero-hour timer starts at one minute. */
internal fun splitSleepTimerMinutes(totalMinutes: Int): Pair<Int, Int> {
    val total = totalMinutes.coerceIn(SLEEP_TIMER_MIN_MINUTE, SLEEP_TIMER_MAX_TOTAL_MINUTES)
    return total / 60 to total % 60
}

internal fun sleepTimerMinuteRange(hours: Int): IntRange =
    (if (hours > 0) 0 else SLEEP_TIMER_MIN_MINUTE)..SLEEP_TIMER_MAX_MINUTE

internal fun combineSleepTimerMinutes(hours: Int, minutes: Int): Int {
    val h = hours.coerceIn(0, SLEEP_TIMER_MAX_HOURS)
    val m = minutes.coerceIn(sleepTimerMinuteRange(h))
    return (h * 60 + m).coerceIn(SLEEP_TIMER_MIN_MINUTE, SLEEP_TIMER_MAX_TOTAL_MINUTES)
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
    val initial = remember(sleepTimerCustomMinutes) { splitSleepTimerMinutes(sleepTimerCustomMinutes) }
    var customHours by remember(sleepTimerCustomMinutes) { mutableIntStateOf(initial.first) }
    var customMinutePart by remember(sleepTimerCustomMinutes) { mutableIntStateOf(initial.second) }
    var showDurationInput by remember { mutableStateOf(false) }
    val customTotalMinutes = combineSleepTimerMinutes(customHours, customMinutePart)
    var nowRealtimeMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val remainingMs = sleepTimerEndRealtimeMs
        ?.minus(nowRealtimeMs)
        ?.coerceAtLeast(0L)
    val timerActive = remainingMs != null && remainingMs > 0L

    fun persistCustom(hours: Int, minutes: Int) {
        val total = combineSleepTimerMinutes(hours, minutes)
        customHours = hours.coerceIn(0, SLEEP_TIMER_MAX_HOURS)
        customMinutePart = minutes.coerceIn(sleepTimerMinuteRange(customHours))
        onCustomTimerMinutes(total)
    }

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
                color = ellaOverlayCardColor()
            )
        ) {
            Text(
                text = stringResource(R.string.player_custom_duration),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().clickable { showDurationInput = true }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberPicker(
                    value = customHours.coerceIn(0, SLEEP_TIMER_MAX_HOURS),
                    onValueChange = { persistCustom(it, customMinutePart) },
                    range = 0..SLEEP_TIMER_MAX_HOURS,
                    modifier = Modifier.width(88.dp)
                )
                Text(
                    text = stringResource(R.string.player_hours_unit),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 2.dp, end = 12.dp)
                )
                NumberPicker(
                    value = customMinutePart.coerceIn(sleepTimerMinuteRange(customHours)),
                    onValueChange = { persistCustom(customHours, it) },
                    range = sleepTimerMinuteRange(customHours),
                    modifier = Modifier.width(88.dp)
                )
                Text(
                    text = stringResource(R.string.player_minutes_unit),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 2.dp, end = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { onTimer(customTotalMinutes) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.primary,
                contentColor = MiuixTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = if (customHours > 0) {
                    stringResource(
                        R.string.player_start_timer_hours_minutes,
                        customHours,
                        customMinutePart
                    )
                } else {
                    stringResource(R.string.player_start_timer_minutes, customMinutePart)
                }
            )
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
    SettingsNumberInputDialog(
        show = showDurationInput,
        title = "${stringResource(R.string.player_custom_duration)} (${stringResource(R.string.player_minutes_unit)})",
        value = customTotalMinutes.toFloat(), valueRange = 1f..SLEEP_TIMER_MAX_TOTAL_MINUTES.toFloat(),
        onDismissRequest = { showDurationInput = false },
        onSave = { total ->
            val (hours, minutes) = splitSleepTimerMinutes(total.toInt())
            persistCustom(hours, minutes)
        }
    )
}

@Composable
private fun TimerStatusCard(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = ellaOverlayCardColor()
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
            color = ellaOverlayCardColor()
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
