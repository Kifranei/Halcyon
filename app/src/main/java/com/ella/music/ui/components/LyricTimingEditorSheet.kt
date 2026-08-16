package com.ella.music.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.ella.music.data.sanitizeExportFileName
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** First-party timing editor that keeps LRC, ELRC, and Apple lyric TTML data round-trippable. */
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
    var embedFormat by remember(song.path) { mutableStateOf(LyricTimingFormat.Lrc) }
    var pendingExportFormat by remember(song.path) { mutableStateOf<LyricTimingFormat?>(null) }

    LaunchedEffect(sourceLyrics, lyricText, hasInitializedFromLyrics) {
        if (!hasInitializedFromLyrics && lyricText.isBlank() && sourceLyrics.isNotEmpty()) {
            timedLines = sourceLyrics.map(LyricLine::toLyricTimingLine)
            lyricText = sourceLyrics.mapNotNull { it.text.takeIf(String::isNotBlank) }.joinToString("\n")
            embedFormat = if (sourceLyrics.any { it.isTtml }) LyricTimingFormat.Ttml
                else if (sourceLyrics.any { it.words.isNotEmpty() || it.backgroundWords.isNotEmpty() }) LyricTimingFormat.Elrc
                else LyricTimingFormat.Lrc
            hasInitializedFromLyrics = true
        }
    }

    val lines = remember(lyricText, timedLines) { lyricText.toLyricTimingLines(timedLines) }
    val selectedIndex = selectedLine.coerceIn(0, (lines.lastIndex).coerceAtLeast(0))
    val unsetLines = lines.count { it.timeMs == null }

    fun currentLines(): List<LyricTimingLine> = lyricText.toLyricTimingLines(timedLines)

    fun contentFor(format: LyricTimingFormat, currentLines: List<LyricTimingLine> = currentLines()): String = when (format) {
        LyricTimingFormat.Lrc -> currentLines.toEmbeddedLrc()
        LyricTimingFormat.Elrc -> currentLines.toEmbeddedElrc()
        LyricTimingFormat.Ttml -> currentLines.toEmbeddedTtml(song)
    }

    fun exportName(format: LyricTimingFormat): String {
        val extension = when (format) {
            LyricTimingFormat.Lrc -> "lrc"
            LyricTimingFormat.Elrc -> "elrc"
            LyricTimingFormat.Ttml -> "ttml"
        }
        val baseName = song.fileName.substringBeforeLast('.', song.fileName)
            .sanitizeExportFileName(fallback = "lyrics", maxLength = 110)
        return "$baseName.$extension"
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val format = pendingExportFormat
        pendingExportFormat = null
        if (uri == null || format == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = contentFor(format)
            val result = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
            }
            if (result == null) {
                Toast.makeText(context, R.string.lyric_timing_editor_export_failed, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.lyric_timing_editor_exported, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateLines(next: List<LyricTimingLine>, nextSelected: Int = selectedIndex) {
        timedLines = next
        lyricText = next.mapNotNull { it.text.takeIf(String::isNotBlank) }.joinToString("\n")
        selectedLine = nextSelected.coerceIn(0, (next.lastIndex).coerceAtLeast(0))
    }

    fun shiftSelected(deltaMs: Long) {
        if (lines.isEmpty()) return
        val selected = lines[selectedIndex]
        updateLines(lines.toMutableList().also { mutable ->
            mutable[selectedIndex] = selected.copy(timeMs = ((selected.timeMs ?: currentPosition) + deltaMs).coerceAtLeast(0L))
        })
    }

    fun shiftAll(deltaMs: Long) {
        updateLines(lines.map { line ->
            fun Long?.shift() = this?.plus(deltaMs)?.coerceAtLeast(0L)
            line.copy(
                timeMs = line.timeMs.shift(),
                words = line.words.map { it.copy(startMs = (it.startMs + deltaMs).coerceAtLeast(0L), endMs = (it.endMs + deltaMs).coerceAtLeast(0L)) },
                backgroundWords = line.backgroundWords.map { it.copy(startMs = (it.startMs + deltaMs).coerceAtLeast(0L), endMs = (it.endMs + deltaMs).coerceAtLeast(0L)) },
                backgroundStartMs = line.backgroundStartMs.shift(),
                backgroundEndMs = line.backgroundEndMs.shift(),
                endMs = line.endMs.shift()
            )
        })
    }

    fun changeSelectedAgent(agent: String?) {
        if (lines.isEmpty()) return
        updateLines(lines.toMutableList().also { it[selectedIndex] = it[selectedIndex].copy(agent = agent) })
    }

    fun timeSelectedBackground() {
        if (lines.isEmpty()) return
        val selected = lines[selectedIndex]
        val background = selected.backgroundText ?: return
        val start = currentPosition
        val end = selected.backgroundEndMs?.coerceAtLeast(start + 1L) ?: (start + 1_500L)
        updateLines(lines.toMutableList().also {
            it[selectedIndex] = selected.copy(
                backgroundStartMs = start,
                backgroundEndMs = end,
                backgroundWords = if (selected.backgroundWords.isEmpty()) listOf(com.ella.music.data.model.LyricWord(background, start, end)) else selected.backgroundWords
            )
        })
    }

    suspend fun saveTiming() {
        val currentLines = currentLines()
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
        val content = contentFor(embedFormat, currentLines)
        val tags = when (embedFormat) {
            // Embedded TTML has higher read priority. Blank its aliases when switching back to
            // LRC/ELRC so the just-saved standard lyric tag is what playback reloads.
            LyricTimingFormat.Lrc, LyricTimingFormat.Elrc -> AudioTagInfo(
                lyrics = content,
                customTags = ttmlTagAliases.associateWith { listOf("") }
            )
            LyricTimingFormat.Ttml -> AudioTagInfo(customTags = mapOf("TTMLLYRIC" to listOf(content)))
        }
        val result = mainViewModel.writeSongMetadata(song, tags)
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
        Text(
            text = stringResource(R.string.lyric_timing_editor_embed_format),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
        )
        EllaMiuixActionRow(
            actions = listOf(
                LyricTimingFormat.Lrc to R.string.lyric_timing_editor_format_lrc,
                LyricTimingFormat.Elrc to R.string.lyric_timing_editor_format_elrc,
                LyricTimingFormat.Ttml to R.string.lyric_timing_editor_format_ttml
            ).map { (format, label) ->
                EllaMiuixAction(
                    text = stringResource(label),
                    primary = format == embedFormat,
                    onClick = { embedFormat = format }
                )
            },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
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
            LyricTimingAdjustmentRow(onClick = ::shiftSelected, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
            LyricTimingAdjustmentRow(onClick = ::shiftSelected, positive = true, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
            Text(
                text = stringResource(R.string.lyric_timing_editor_ttml_roles),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
            EllaMiuixActionRow(
                actions = listOf(
                    EllaMiuixAction(stringResource(R.string.lyric_timing_editor_role_none), onClick = { changeSelectedAgent(null) }),
                    EllaMiuixAction("v1", primary = lines[selectedIndex].agent == "v1", onClick = { changeSelectedAgent("v1") }),
                    EllaMiuixAction("v2", primary = lines[selectedIndex].agent == "v2", onClick = { changeSelectedAgent("v2") })
                ),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
            EllaMiuixTextField(
                value = lines[selectedIndex].agent.orEmpty(),
                onValueChange = { value -> changeSelectedAgent(value.trim().takeIf(String::isNotBlank)) },
                label = stringResource(R.string.lyric_timing_editor_agent),
                singleLine = true,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
            EllaMiuixTextField(
                value = lines[selectedIndex].backgroundText.orEmpty(),
                onValueChange = { value ->
                    val selected = lines[selectedIndex]
                    updateLines(lines.toMutableList().also {
                        it[selectedIndex] = selected.copy(
                            backgroundText = value.takeIf(String::isNotBlank),
                            backgroundWords = if (value == selected.backgroundText) selected.backgroundWords else emptyList()
                        )
                    })
                },
                label = stringResource(R.string.lyric_timing_editor_background_text),
                singleLine = false,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
            EllaMiuixActionRow(
                actions = listOf(
                    EllaMiuixAction(
                        text = stringResource(R.string.lyric_timing_editor_background_time),
                        onClick = ::timeSelectedBackground
                    ),
                    EllaMiuixAction(
                        text = stringResource(R.string.lyric_timing_editor_background_clear),
                        onClick = {
                            val selected = lines[selectedIndex]
                            updateLines(lines.toMutableList().also {
                                it[selectedIndex] = selected.copy(backgroundText = null, backgroundWords = emptyList(), backgroundTranslation = null, backgroundStartMs = null, backgroundEndMs = null)
                            })
                        }
                    )
                ),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
            Text(
                text = stringResource(R.string.lyric_timing_editor_shift_all),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
            LyricTimingAdjustmentRow(onClick = ::shiftAll, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
            LyricTimingAdjustmentRow(onClick = ::shiftAll, positive = true, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
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
                        .background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.18f) else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f))
                        .clickable {
                            selectedLine = index
                            if (isCurrentSong) line.timeMs?.let(playerViewModel::seekTo)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "${index + 1}", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(
                        text = listOfNotNull(line.agent?.uppercase(), line.text, line.backgroundText?.let { "x-bg: $it" }).joinToString("  "),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = line.timeMs?.toTimingDisplay() ?: "--:--.--", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
        Text(
            text = stringResource(R.string.lyric_timing_editor_export),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
        EllaMiuixActionRow(
            actions = listOf(
                LyricTimingFormat.Lrc to R.string.lyric_timing_editor_format_lrc,
                LyricTimingFormat.Elrc to R.string.lyric_timing_editor_format_elrc,
                LyricTimingFormat.Ttml to R.string.lyric_timing_editor_format_ttml
            ).map { (format, label) ->
                EllaMiuixAction(
                    text = stringResource(label),
                    onClick = {
                        if (currentLines().any { it.timeMs == null }) {
                            Toast.makeText(context, R.string.lyric_timing_editor_complete_lines, Toast.LENGTH_SHORT).show()
                        } else {
                            pendingExportFormat = format
                            exportLauncher.launch(exportName(format))
                        }
                    }
                )
            },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
        )
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

private val ttmlTagAliases = listOf("TTML LYRICS", "TTML LYRIC", "TTMLLYRICS", "TTMLLYRIC", "TTML")

@Composable
private fun LyricTimingAdjustmentRow(
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    positive: Boolean = false
) {
    val sign = if (positive) 1L else -1L
    EllaMiuixActionRow(
        actions = listOf(100L, 50L, 10L).map { amount ->
            EllaMiuixAction(text = if (positive) "+$amount" else "-$amount", onClick = { onClick(sign * amount) })
        },
        modifier = modifier
    )
}
