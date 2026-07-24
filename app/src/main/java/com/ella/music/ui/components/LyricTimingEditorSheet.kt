package com.ella.music.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.metadata.AudioTagInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A deliberately focused first-party LRC timing editor; advanced TTML editing remains separate. */
@Composable
internal fun LyricTimingEditorSheet(
    song: Song,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onWritePermissionRequired: (WritePermissionRequiredException, suspend () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val playerLyrics by playerViewModel.lyrics.collectAsState()
    val loadedLyrics by produceState<List<LyricLine>>(
        initialValue = emptyList(),
        song.path,
        song.dateModified
    ) {
        value = withContext(Dispatchers.IO) { mainViewModel.repository.getLyrics(song) }
    }
    val isCurrentSong = currentSong?.let { it.path == song.path && it.id == song.id } == true
    val sourceLyrics = if (isCurrentSong && playerLyrics.isNotEmpty()) playerLyrics else loadedLyrics

    var lyricText by remember(song.path) { mutableStateOf("") }
    var timedLines by remember(song.path) { mutableStateOf(emptyList<LyricTimingLine>()) }
    var selectedLine by remember(song.path) { mutableStateOf(0) }
    var hasInitializedFromLyrics by remember(song.path) { mutableStateOf(false) }

    LaunchedEffect(sourceLyrics, lyricText, hasInitializedFromLyrics) {
        if (!hasInitializedFromLyrics && lyricText.isBlank() && sourceLyrics.isNotEmpty()) {
            timedLines = sourceLyrics.map { line -> LyricTimingLine(line.text, line.timeMs) }
            lyricText = sourceLyrics.joinToString("\n") { it.text }
            hasInitializedFromLyrics = true
        }
    }

    val lines = remember(lyricText, timedLines) { lyricText.toLyricTimingLines(timedLines) }
    val selectedIndex = selectedLine.coerceIn(0, (lines.lastIndex).coerceAtLeast(0))
    val unsetLines = lines.count { it.timeMs == null }

    fun updateLines(next: List<LyricTimingLine>, nextSelected: Int = selectedIndex) {
        timedLines = next
        lyricText = next.joinToString("\n") { it.text }
        selectedLine = nextSelected.coerceIn(0, (next.lastIndex).coerceAtLeast(0))
    }

    fun shiftSelected(deltaMs: Long) {
        if (lines.isEmpty()) return
        val selected = lines[selectedIndex]
        updateLines(lines.toMutableList().also { mutable ->
            mutable[selectedIndex] = selected.copy(timeMs = (selected.timeMs ?: currentPosition) + deltaMs)
        })
    }

    fun shiftAll(deltaMs: Long) {
        updateLines(lines.map { line ->
            line.copy(timeMs = line.timeMs?.plus(deltaMs)?.coerceAtLeast(0L))
        })
    }

    suspend fun saveTiming() {
        val currentLines = lyricText.toLyricTimingLines(timedLines)
        when {
            currentLines.isEmpty() -> {
                Toast.makeText(context, R.string.lyric_timing_editor_no_lines, Toast.LENGTH_SHORT).show()
                return
            }
            currentLines.any { it.timeMs == null } -> {
                Toast.makeText(context, R.string.lyric_timing_editor_complete_lines, Toast.LENGTH_SHORT).show()
                return
            }
        }
        val result = mainViewModel.writeSongMetadata(song, AudioTagInfo(lyrics = currentLines.toEmbeddedLrc()))
        if (result.isSuccess) {
            if (isCurrentSong) playerViewModel.reloadCurrentLyrics()
            Toast.makeText(context, R.string.lyric_timing_editor_saved, Toast.LENGTH_SHORT).show()
            onDismiss()
            return
        }
        val error = result.exceptionOrNull()
        if (error is WritePermissionRequiredException) {
            onWritePermissionRequired(error) { saveTiming() }
        } else {
            Toast.makeText(
                context,
                error?.localizedMessage ?: context.getString(R.string.song_more_metadata_save_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    SongSheetColumn {
        Text(
            text = stringResource(R.string.lyric_timing_editor_text_summary),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
        )
        EllaMiuixTextField(
            value = lyricText,
            onValueChange = { lyricText = it },
            label = stringResource(R.string.lyric_timing_editor_text),
            singleLine = false,
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 4.dp)
                .height(128.dp)
        )
        Text(
            text = stringResource(R.string.lyric_timing_editor_current_position, currentPosition.toTimingDisplay()),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
        if (lines.isNotEmpty()) {
            Text(
                text = stringResource(R.string.lyric_timing_editor_selected_line, selectedIndex + 1, lines.size),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
            EllaMiuixActionRow(
                actions = listOf(
                    EllaMiuixAction(
                        text = stringResource(R.string.lyric_timing_editor_tap_to_time),
                        primary = true,
                        onClick = {
                            val next = lines.toMutableList()
                            next[selectedIndex] = next[selectedIndex].copy(timeMs = currentPosition)
                            updateLines(next, (selectedIndex + 1).coerceAtMost(next.lastIndex))
                        }
                    ),
                    EllaMiuixAction(
                        text = stringResource(R.string.lyric_timing_editor_undo),
                        onClick = {
                            val previous = (selectedIndex - 1).coerceAtLeast(0)
                            val next = lines.toMutableList()
                            next[selectedIndex] = next[selectedIndex].copy(timeMs = null)
                            updateLines(next, previous)
                        }
                    )
                ),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            )
            LyricTimingAdjustmentRow(
                onClick = { delta -> shiftSelected(delta) },
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
            LyricTimingAdjustmentRow(
                onClick = { delta -> shiftSelected(delta) },
                positive = true,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
            Text(
                text = stringResource(R.string.lyric_timing_editor_shift_all),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
            LyricTimingAdjustmentRow(
                onClick = { delta -> shiftAll(delta) },
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
            LyricTimingAdjustmentRow(
                onClick = { delta -> shiftAll(delta) },
                positive = true,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
            if (unsetLines > 0) {
                Text(
                    text = stringResource(R.string.lyric_timing_editor_unset_lines, unsetLines),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
            lines.forEachIndexed { index, line ->
                val selected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 2.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f)
                        )
                        .clickable {
                            selectedLine = index
                            if (isCurrentSong) line.timeMs?.let(playerViewModel::seekTo)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Text(
                        text = line.text,
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = line.timeMs?.toTimingDisplay() ?: "--:--.--",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        EllaMiuixSheetActions(
            cancelText = stringResource(R.string.common_cancel),
            confirmText = stringResource(R.string.common_save),
            onCancel = onDismiss,
            onConfirm = { scope.launch { saveTiming() } },
            modifier = Modifier.padding(horizontal = 18.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun LyricTimingAdjustmentRow(
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    positive: Boolean = false
) {
    val sign = if (positive) 1L else -1L
    EllaMiuixActionRow(
        actions = listOf(100L, 50L, 10L).map { amount ->
            EllaMiuixAction(
                text = if (positive) "+$amount" else "-$amount",
                onClick = { onClick(sign * amount) }
            )
        },
        modifier = modifier
    )
}
