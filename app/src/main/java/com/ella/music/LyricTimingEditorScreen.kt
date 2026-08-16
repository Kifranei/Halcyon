package com.ella.music

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.metadata.AudioTagInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.Song
import com.ella.music.data.sanitizeExportFileName
import com.ella.music.ui.components.EllaMiuixChip
import com.ella.music.ui.components.EllaMiuixDialog
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.LyricTimingFormat
import com.ella.music.ui.components.LyricTimingLine
import com.ella.music.ui.components.toEmbeddedElrc
import com.ella.music.ui.components.toEmbeddedLrc
import com.ella.music.ui.components.toEmbeddedTtml
import com.ella.music.ui.components.toLyricTimingLine
import com.ella.music.ui.components.toLyricTimingLines
import com.ella.music.ui.components.toTimingDisplay
import com.ella.music.ui.components.withGeneratedWords
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.icon.extended.Redo
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class TimingMode { Line, Word }

@Composable
internal fun LyricTimingEditorScreen(
    song: Song,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentSong by playerViewModel.currentSong.collectAsStateWithLifecycle()
    val currentPosition by playerViewModel.currentPosition.collectAsStateWithLifecycle()
    val playerLyrics by playerViewModel.lyrics.collectAsStateWithLifecycle()
    val loadedLyrics by produceState<List<LyricLine>>(emptyList(), song.path, song.dateModified) {
        value = withContext(Dispatchers.IO) { mainViewModel.repository.getLyrics(song) }
    }
    val sourceLyrics = if (currentSong?.let { it.id == song.id && it.path == song.path } == true && playerLyrics.isNotEmpty()) {
        playerLyrics
    } else {
        loadedLyrics
    }
    val isCurrentSong = currentSong?.let { it.id == song.id && it.path == song.path } == true
    val isPlayerPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()

    // Timing must follow the song being edited. The former sheet showed the global player time
    // even when another song was playing, so a tap could write a timestamp from the wrong track.
    LaunchedEffect(song.id, song.path) {
        if (!isCurrentSong) playerViewModel.playSong(song)
    }

    var lyricText by remember(song.path) { mutableStateOf("") }
    var timedLines by remember(song.path) { mutableStateOf(emptyList<LyricTimingLine>()) }
    var selectedLine by remember(song.path) { mutableIntStateOf(0) }
    var selectedWord by remember(song.path) { mutableIntStateOf(0) }
    var timingMode by remember(song.path) { mutableStateOf(TimingMode.Line) }
    var embedFormat by remember(song.path) { mutableStateOf(LyricTimingFormat.Ttml) }
    var initialized by remember(song.path) { mutableStateOf(false) }
    var pendingExportFormat by remember(song.path) { mutableStateOf<LyricTimingFormat?>(null) }
    var showExportFormatDialog by remember(song.path) { mutableStateOf(false) }
    var pendingWriteRetry by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var undoSnapshots by remember(song.path) { mutableStateOf(emptyList<List<LyricTimingLine>>()) }
    var redoSnapshots by remember(song.path) { mutableStateOf(emptyList<List<LyricTimingLine>>()) }
    var followPlayback by remember(song.path) { mutableStateOf(true) }
    val lyricListState = rememberLazyListState()

    LaunchedEffect(sourceLyrics, initialized) {
        if (!initialized && sourceLyrics.isNotEmpty()) {
            timedLines = sourceLyrics.map(LyricLine::toLyricTimingLine)
            lyricText = sourceLyrics.mapNotNull { it.text.takeIf(String::isNotBlank) }.joinToString("\n")
            embedFormat = if (sourceLyrics.any { it.isTtml }) LyricTimingFormat.Ttml
            else if (sourceLyrics.any { it.words.isNotEmpty() || it.backgroundWords.isNotEmpty() }) LyricTimingFormat.Elrc
            else LyricTimingFormat.Lrc
            initialized = true
        }
    }
    val lines = remember(lyricText, timedLines) { lyricText.toLyricTimingLines(timedLines) }
    val selectedIndex = selectedLine.coerceIn(0, lines.lastIndex.coerceAtLeast(0))
    val selected = lines.getOrNull(selectedIndex)
    val selectedWords = selected
        ?.withGeneratedWords(
            selected.timeMs ?: currentPosition,
            selected.endMs ?: (selected.timeMs ?: currentPosition) + 3_000L
        )
        ?.words
        .orEmpty()
    val selectedWordIndex = selectedWord.coerceIn(0, selectedWords.lastIndex.coerceAtLeast(0))
    val activeLineIndex = remember(lines, currentPosition) {
        lines.indexOfLast { line ->
            val start = line.timeMs ?: return@indexOfLast false
            start <= currentPosition && (line.endMs == null || currentPosition < line.endMs)
        }.takeIf { it >= 0 }
            ?: lines.indexOfLast { (it.timeMs ?: Long.MAX_VALUE) <= currentPosition }.takeIf { it >= 0 }
    }
    val activeWordIndex = activeLineIndex?.let { index ->
        lines[index].withGeneratedWords(
            lines[index].timeMs ?: 0L,
            lines[index].endMs ?: (lines[index].timeMs ?: 0L) + 3_000L
        ).words.indexOfLast { word ->
            word.startMs <= currentPosition && currentPosition < word.endMs
        }.takeIf { it >= 0 }
    }

    // Follow the audio clock until the user deliberately edits another line. The extra list
    // header is item zero, hence the one-item offset when bringing the active line into view.
    LaunchedEffect(activeLineIndex, followPlayback, isPlayerPlaying) {
        if (followPlayback && isPlayerPlaying && activeLineIndex != null) {
            lyricListState.animateScrollToItem(activeLineIndex + 1)
        }
    }

    fun updateLines(
        next: List<LyricTimingLine>,
        nextLine: Int = selectedIndex,
        nextWord: Int = 0,
        saveUndo: Boolean = true
    ) {
        if (saveUndo && next != lines) {
            undoSnapshots = (undoSnapshots + listOf(lines)).takeLast(MAX_EDITOR_HISTORY)
            redoSnapshots = emptyList()
        }
        timedLines = next
        lyricText = next.mapNotNull { it.text.takeIf(String::isNotBlank) }.joinToString("\n")
        selectedLine = nextLine.coerceIn(0, next.lastIndex.coerceAtLeast(0))
        selectedWord = nextWord.coerceAtLeast(0)
    }

    fun restoreHistory(snapshot: List<LyricTimingLine>) {
        updateLines(snapshot, selectedIndex, selectedWordIndex, saveUndo = false)
    }

    fun undo() {
        val previous = undoSnapshots.lastOrNull() ?: return
        undoSnapshots = undoSnapshots.dropLast(1)
        redoSnapshots = (redoSnapshots + listOf(lines)).takeLast(MAX_EDITOR_HISTORY)
        restoreHistory(previous)
    }

    fun redo() {
        val next = redoSnapshots.lastOrNull() ?: return
        redoSnapshots = redoSnapshots.dropLast(1)
        undoSnapshots = (undoSnapshots + listOf(lines)).takeLast(MAX_EDITOR_HISTORY)
        restoreHistory(next)
    }

    fun applyLineStart(moveToNext: Boolean) {
        val line = selected ?: return
        val next = lines.toMutableList()
        val nextLine = line.copy(timeMs = currentPosition)
        next[selectedIndex] = nextLine
        updateLines(next, if (moveToNext) (selectedIndex + 1).coerceAtMost(next.lastIndex) else selectedIndex)
    }

    fun applyLineEnd() {
        val line = selected ?: return
        val start = line.timeMs ?: currentPosition
        val next = lines.toMutableList()
        next[selectedIndex] = line.copy(
            timeMs = start,
            endMs = currentPosition.coerceAtLeast(start + 1L)
        )
        updateLines(next, selectedIndex)
    }

    fun applyWordStart(endWord: Boolean = false) {
        val line = selected ?: return
        val baseStart = line.timeMs ?: currentPosition
        val generated = line.withGeneratedWords(baseStart, line.endMs ?: baseStart + 3_000L)
        val words = generated.words.toMutableList()
        if (words.isEmpty()) return
        val index = selectedWordIndex
        val current = words[index]
        words[index] = if (endWord) {
            current.copy(endMs = currentPosition.coerceAtLeast(current.startMs + 1L))
        } else {
            current.copy(startMs = currentPosition, endMs = current.endMs.coerceAtLeast(currentPosition + 1L))
        }
        val nextLine = generated.copy(
            timeMs = generated.timeMs ?: words.first().startMs,
            words = words,
            endMs = words.maxOf { it.endMs }
        )
        val next = lines.toMutableList()
        next[selectedIndex] = nextLine
        updateLines(next, selectedIndex, if (endWord) (index + 1).coerceAtMost(words.lastIndex) else index)
    }

    fun applyWordContinuous() {
        val line = selected ?: return
        val baseStart = line.timeMs ?: currentPosition
        val generated = line.withGeneratedWords(baseStart, line.endMs ?: baseStart + 3_000L)
        val words = generated.words.toMutableList()
        if (words.isEmpty()) return
        val index = selectedWordIndex
        val current = words[index]
        words[index] = current.copy(endMs = currentPosition.coerceAtLeast(current.startMs + 1L))
        val nextIndex = (index + 1).coerceAtMost(words.lastIndex)
        if (nextIndex != index) {
            val following = words[nextIndex]
            words[nextIndex] = following.copy(
                startMs = currentPosition,
                endMs = following.endMs.coerceAtLeast(currentPosition + 1L)
            )
        }
        val next = lines.toMutableList()
        next[selectedIndex] = generated.copy(
            timeMs = generated.timeMs ?: words.first().startMs,
            words = words,
            endMs = words.maxOf { it.endMs }
        )
        updateLines(next, selectedIndex, nextIndex)
    }

    fun adjustSelected(delta: Long) {
        val line = selected ?: return
        val next = lines.toMutableList()
        if (timingMode == TimingMode.Word && selectedWords.isNotEmpty()) {
            val words = line.withGeneratedWords(
                line.timeMs ?: currentPosition,
                line.endMs ?: (line.timeMs ?: currentPosition) + 3_000L
            ).words.toMutableList()
            val word = words[selectedWordIndex]
            words[selectedWordIndex] = word.copy(
                startMs = (word.startMs + delta).coerceAtLeast(0L),
                endMs = (word.endMs + delta).coerceAtLeast(1L)
            )
            next[selectedIndex] = line.copy(words = words, endMs = words.maxOf { it.endMs })
        } else {
            next[selectedIndex] = line.copy(timeMs = ((line.timeMs ?: currentPosition) + delta).coerceAtLeast(0L))
        }
        updateLines(next, selectedIndex, selectedWordIndex)
    }

    fun changeRole(agent: String?) {
        val line = selected ?: return
        updateLines(lines.toMutableList().also { it[selectedIndex] = line.copy(agent = agent) }, selectedIndex, selectedWordIndex)
    }

    fun updateSelected(transform: (LyricTimingLine) -> LyricTimingLine) {
        val line = selected ?: return
        updateLines(lines.toMutableList().also { it[selectedIndex] = transform(line) }, selectedIndex, selectedWordIndex)
    }

    fun applyBackgroundTime(end: Boolean) {
        val line = selected ?: return
        val background = line.backgroundText?.takeIf(String::isNotBlank) ?: return
        val start = if (end) line.backgroundStartMs ?: currentPosition else currentPosition
        val endTime = if (end) currentPosition.coerceAtLeast(start + 1L) else line.backgroundEndMs ?: (currentPosition + 1_500L)
        updateSelected {
            it.copy(
                backgroundStartMs = start,
                backgroundEndMs = endTime,
                backgroundWords = if (it.backgroundWords.isEmpty()) listOf(LyricWord(background, start, endTime)) else it.backgroundWords
            )
        }
    }

    fun currentLines(): List<LyricTimingLine> = lyricText.toLyricTimingLines(timedLines)
    fun contentFor(format: LyricTimingFormat): String = when (format) {
        LyricTimingFormat.Lrc -> currentLines().toEmbeddedLrc()
        LyricTimingFormat.Elrc -> currentLines().toEmbeddedElrc()
        LyricTimingFormat.Ttml -> currentLines().toEmbeddedTtml(song)
    }
    fun exportName(format: LyricTimingFormat): String = song.fileName.substringBeforeLast('.', song.fileName)
        .sanitizeExportFileName(fallback = "lyrics", maxLength = 110) + when (format) {
        LyricTimingFormat.Lrc -> ".lrc"
        LyricTimingFormat.Elrc -> ".elrc"
        LyricTimingFormat.Ttml -> ".ttml"
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val format = pendingExportFormat
        pendingExportFormat = null
        if (uri == null || format == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(contentFor(format)) }
            }
            Toast.makeText(context, if (written == null) R.string.lyric_timing_editor_export_failed else R.string.lyric_timing_editor_exported, Toast.LENGTH_SHORT).show()
        }
    }
    val writePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        pendingWriteRetry?.let { retry ->
            pendingWriteRetry = null
            scope.launch { retry() }
        }
    }

    suspend fun saveTiming() {
        val current = currentLines()
        when {
            current.isEmpty() -> {
                Toast.makeText(context, R.string.lyric_timing_editor_no_lines, Toast.LENGTH_SHORT).show()
                return
            }
            current.any { it.timeMs == null } -> {
                Toast.makeText(context, R.string.lyric_timing_editor_complete_lines, Toast.LENGTH_SHORT).show()
                return
            }
        }
        val tags = when (embedFormat) {
            LyricTimingFormat.Ttml -> AudioTagInfo(customTags = mapOf("TTMLLYRIC" to listOf(contentFor(embedFormat))))
            else -> AudioTagInfo(
                lyrics = contentFor(embedFormat),
                customTags = ttmlTagAliases.associateWith { listOf("") }
            )
        }
        val result = mainViewModel.writeSongMetadata(song, tags)
        if (result.isSuccess) {
            if (isCurrentSong) playerViewModel.reloadCurrentLyrics()
            Toast.makeText(context, R.string.lyric_timing_editor_saved, Toast.LENGTH_SHORT).show()
            onBack()
        } else {
            val error = result.exceptionOrNull()
            if (error is WritePermissionRequiredException) {
                pendingWriteRetry = { saveTiming() }
                writePermissionLauncher.launch(IntentSenderRequest.Builder(error.intentSender).build())
            } else {
                Toast.makeText(context, error?.localizedMessage ?: context.getString(R.string.song_more_metadata_save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)
    Column(
        Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings_lyric_timing_editor),
            color = pageBackground,
            defaultWindowInsetsPadding = false,
            titleWindowInsetsPadding = false,
            titleEndPadding = 120.dp,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(MiuixIcons.Regular.Back, stringResource(R.string.common_back), tint = MiuixTheme.colorScheme.onSurface)
                }
            },
            actions = {
                IconButton(onClick = { showExportFormatDialog = true }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Download,
                        contentDescription = stringResource(R.string.common_export),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { scope.launch { saveTiming() } }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Ok,
                        contentDescription = stringResource(R.string.common_save),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
            }
        )
        EditorSongInfo(
            title = song.title.ifBlank { song.fileName },
            artist = song.artist
        )
        EditorModeAndFormatBar(
            timingMode = timingMode,
            onTimingModeChange = { timingMode = it },
            embedFormat = embedFormat,
            onEmbedFormatChange = { embedFormat = it }
        )
        EditorHistoryBar(
            canUndo = undoSnapshots.isNotEmpty(),
            canRedo = redoSnapshots.isNotEmpty(),
            onUndo = ::undo,
            onRedo = ::redo
        )
        if (lines.isEmpty()) {
            EllaMiuixTextField(
                value = lyricText,
                onValueChange = { lyricText = it; timedLines = emptyList() },
                label = stringResource(R.string.lyric_timing_editor_text),
                singleLine = false,
                modifier = Modifier.padding(18.dp).weight(1f)
            )
        } else {
            LazyColumn(
                state = lyricListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.lyric_timing_editor_selected_line, selectedIndex + 1, lines.size),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }
                itemsIndexed(lines) { index, line ->
                    TimingLineCard(
                        index = index,
                        line = line,
                        selected = index == selectedIndex,
                        active = index == activeLineIndex,
                        onClick = {
                            followPlayback = false
                            selectedLine = index
                            selectedWord = 0
                            if (isCurrentSong) line.timeMs?.let(playerViewModel::seekTo)
                        }
                    )
                    if (index == selectedIndex) {
                        TimingLineEditor(
                            line = line,
                            timingMode = timingMode,
                            selectedWord = selectedWordIndex,
                            activeWord = if (index == activeLineIndex) activeWordIndex else null,
                            onSelectWord = { selectedWord = it },
                            onRoleChange = ::changeRole,
                            onLineChange = { text -> updateSelected { it.copy(text = text) } },
                            onTranslationChange = { translation -> updateSelected { it.copy(translation = translation.takeIf(String::isNotBlank)) } },
                            onPronunciationChange = { pronunciation -> updateSelected { it.copy(pronunciation = pronunciation.takeIf(String::isNotBlank)) } },
                            onBackgroundStart = { applyBackgroundTime(end = false) },
                            onBackgroundEnd = { applyBackgroundTime(end = true) },
                            onBackgroundChange = { background ->
                                val current = lines[selectedIndex]
                                updateLines(lines.toMutableList().also {
                                    it[selectedIndex] = current.copy(
                                        backgroundText = background.takeIf(String::isNotBlank),
                                        backgroundWords = if (background == current.backgroundText) current.backgroundWords else emptyList()
                                    )
                                }, selectedIndex, selectedWordIndex)
                            },
                            onGenerateWords = {
                                val current = lines[selectedIndex]
                                val start = current.timeMs ?: currentPosition
                                updateLines(
                                    lines.toMutableList().also {
                                        it[selectedIndex] = current.withGeneratedWords(
                                            start,
                                            current.endMs ?: start + 3_000L
                                        )
                                    },
                                    selectedIndex,
                                    selectedWordIndex
                                )
                            }
                        )
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
        TimingTransportBar(
            currentPosition = currentPosition,
            duration = song.duration,
            isPlaying = isPlayerPlaying,
            timingMode = timingMode,
            lineAvailable = selected != null,
            wordsAvailable = selectedWords.isNotEmpty(),
            onTogglePlay = playerViewModel::togglePlayPause,
            onSeekBack = { playerViewModel.seekTo((currentPosition - 2_000L).coerceAtLeast(0L)) },
            onSeekForward = { playerViewModel.seekTo((currentPosition + 2_000L).coerceAtMost(song.duration)) },
            onSeekTo = playerViewModel::seekTo,
            onSetStart = { if (timingMode == TimingMode.Word) applyWordStart() else applyLineStart(moveToNext = false) },
            onSetContinuous = {
                if (timingMode == TimingMode.Word) applyWordContinuous()
                else applyLineStart(moveToNext = true)
            },
            onSetEnd = { if (timingMode == TimingMode.Word) applyWordStart(endWord = true) else applyLineEnd() },
            onAdjust = ::adjustSelected,
            onLocatePlayback = {
                followPlayback = true
                activeLineIndex?.let { index ->
                    scope.launch { lyricListState.animateScrollToItem(index + 1) }
                }
            }
        )
    }

    if (showExportFormatDialog) {
        EllaMiuixDialog(
            show = true,
            title = stringResource(R.string.lyric_timing_editor_export),
            summary = stringResource(R.string.lyric_timing_editor_embed_format),
            onDismissRequest = { showExportFormatDialog = false }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LyricTimingFormat.entries.forEach { format ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showExportFormatDialog = false
                                if (currentLines().any { it.timeMs == null }) {
                                    Toast.makeText(context, R.string.lyric_timing_editor_complete_lines, Toast.LENGTH_SHORT).show()
                                } else {
                                    pendingExportFormat = format
                                    exportLauncher.launch(exportName(format))
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(format.name.uppercase(), fontWeight = FontWeight.SemiBold)
                        Icon(
                            imageVector = MiuixIcons.Regular.File,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_EDITOR_HISTORY = 32

@Composable
private fun EditorHistoryBar(canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { if (canUndo) onUndo() }) {
            Icon(
                MiuixIcons.Regular.Undo,
                stringResource(R.string.lyric_timing_editor_undo),
                tint = if (canUndo) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.38f)
            )
        }
        IconButton(onClick = { if (canRedo) onRedo() }) {
            Icon(
                MiuixIcons.Regular.Redo,
                stringResource(R.string.lyric_timing_editor_redo),
                tint = if (canRedo) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
private fun EditorSongInfo(title: String, artist: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 58.dp)
            .padding(horizontal = 18.dp, vertical = 3.dp)
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (artist.isNotBlank()) {
            Text(
                text = artist,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EditorModeAndFormatBar(
    timingMode: TimingMode,
    onTimingModeChange: (TimingMode) -> Unit,
    embedFormat: LyricTimingFormat,
    onEmbedFormatChange: (LyricTimingFormat) -> Unit
) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EllaMiuixChip(
                stringResource(R.string.lyric_timing_editor_mode_line),
                timingMode == TimingMode.Line,
                { onTimingModeChange(TimingMode.Line) }
            )
            EllaMiuixChip(
                stringResource(R.string.lyric_timing_editor_mode_word),
                timingMode == TimingMode.Word,
                { onTimingModeChange(TimingMode.Word) }
            )
            Spacer(Modifier.weight(1f))
            LyricTimingFormat.entries.forEach { format ->
                EllaMiuixChip(format.name.uppercase(), embedFormat == format, { onEmbedFormatChange(format) }, horizontalPadding = 10.dp)
            }
        }
    }
}

@Composable
private fun TimingLineCard(
    index: Int,
    line: LyricTimingLine,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    active -> MiuixTheme.colorScheme.primary.copy(alpha = 0.26f)
                    selected -> MiuixTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else -> MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.74f)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${index + 1}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        Text(
            text = listOfNotNull(line.agent?.uppercase(), line.text, line.backgroundText?.let { "x-bg: $it" }).joinToString("  "),
            color = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(line.timeMs?.toTimingDisplay() ?: "--:--.--", color = MiuixTheme.colorScheme.primary, fontSize = 12.sp)
    }
}

@Composable
private fun TimingLineEditor(
    line: LyricTimingLine,
    timingMode: TimingMode,
    selectedWord: Int,
    activeWord: Int?,
    onSelectWord: (Int) -> Unit,
    onRoleChange: (String?) -> Unit,
    onLineChange: (String) -> Unit,
    onTranslationChange: (String) -> Unit,
    onPronunciationChange: (String) -> Unit,
    onBackgroundStart: () -> Unit,
    onBackgroundEnd: () -> Unit,
    onBackgroundChange: (String) -> Unit,
    onGenerateWords: () -> Unit
) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EllaMiuixChip("无", line.agent.isNullOrBlank(), { onRoleChange(null) })
            EllaMiuixChip("v1", line.agent == "v1", { onRoleChange("v1") })
            EllaMiuixChip("v2", line.agent == "v2", { onRoleChange("v2") })
            EllaMiuixChip("v1000", line.agent == "v1000", { onRoleChange("v1000") })
        }
        EllaMiuixTextField(
            value = line.agent.orEmpty(),
            onValueChange = { onRoleChange(it.trim().takeIf(String::isNotBlank)) },
            label = stringResource(R.string.lyric_timing_editor_agent),
            singleLine = true,
            modifier = Modifier.padding(top = 8.dp)
        )
        EllaMiuixTextField(
            value = line.text,
            onValueChange = onLineChange,
            label = stringResource(R.string.lyric_timing_editor_line_text),
            singleLine = false,
            modifier = Modifier.padding(top = 8.dp)
        )
        EllaMiuixTextField(
            value = line.pronunciation.orEmpty(),
            onValueChange = onPronunciationChange,
            label = stringResource(R.string.lyric_timing_editor_pronunciation),
            singleLine = true,
            modifier = Modifier.padding(top = 8.dp)
        )
        EllaMiuixTextField(
            value = line.translation.orEmpty(),
            onValueChange = onTranslationChange,
            label = stringResource(R.string.lyric_timing_editor_translation),
            singleLine = true,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (timingMode == TimingMode.Word) {
            val displayWords = line.withGeneratedWords(line.timeMs ?: 0L, line.endMs ?: (line.timeMs ?: 0L) + 3_000L).words
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.lyric_timing_editor_word_grid),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f)
                )
                if (line.words.isEmpty()) {
                    EllaMiuixChip(
                        stringResource(R.string.lyric_timing_editor_generate_words),
                        false,
                        onGenerateWords,
                        horizontalPadding = 10.dp
                    )
                }
            }
            WordTimingGrid(displayWords, selectedWord, activeWord, onSelectWord)
        }
        EllaMiuixTextField(
            value = line.backgroundText.orEmpty(),
            onValueChange = onBackgroundChange,
            label = stringResource(R.string.lyric_timing_editor_background_text),
            singleLine = true,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (!line.backgroundText.isNullOrBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                EllaMiuixChip(stringResource(R.string.lyric_timing_editor_background_start), false, onBackgroundStart)
                EllaMiuixChip(stringResource(R.string.lyric_timing_editor_background_end), false, onBackgroundEnd)
            }
        }
    }
}

@Composable
private fun WordTimingGrid(
    words: List<LyricWord>,
    selectedWord: Int,
    activeWord: Int?,
    onSelectWord: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        words.chunked(3).forEachIndexed { row, rowWords ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                rowWords.forEachIndexed { cell, word ->
                    val index = row * 3 + cell
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    index == activeWord -> MiuixTheme.colorScheme.primary.copy(alpha = 0.28f)
                                    index == selectedWord -> MiuixTheme.colorScheme.primary.copy(alpha = 0.20f)
                                    else -> MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
                                }
                            )
                            .clickable { onSelectWord(index) }
                            .padding(vertical = 7.dp, horizontal = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(word.startMs.toTimingDisplay(), color = Color(0xFF65B978), fontSize = 10.sp)
                        Text(word.text, color = MiuixTheme.colorScheme.onSurface, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(word.endMs.toTimingDisplay(), color = Color(0xFFE06C75), fontSize = 10.sp)
                    }
                }
                repeat(3 - rowWords.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TimingTransportBar(
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    timingMode: TimingMode,
    lineAvailable: Boolean,
    wordsAvailable: Boolean,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetStart: () -> Unit,
    onSetContinuous: () -> Unit,
    onSetEnd: () -> Unit,
    onAdjust: (Long) -> Unit,
    onLocatePlayback: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f))
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorTransportTextButton("-2s", onSeekBack)
            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) MiuixIcons.Regular.Pause else MiuixIcons.Regular.Play,
                    contentDescription = stringResource(if (isPlaying) R.string.common_pause else R.string.common_play),
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            EditorTransportTextButton("+2s", onSeekForward)
            Text(
                currentPosition.toTimingDisplay(),
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onLocatePlayback) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_my_location),
                    contentDescription = stringResource(R.string.player_locate_current_song),
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp)
                )
            }
        }
        Slider(
            value = currentPosition.toFloat().coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
            onValueChange = { onSeekTo(it.toLong().coerceIn(0L, duration.coerceAtLeast(0L))) },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorTransportTextButton("-50", { onAdjust(-50L) })
            EditorTransportTextButton(
                stringResource(R.string.lyric_timing_editor_start),
                onSetStart,
                primary = lineAvailable,
                modifier = Modifier.weight(1f)
            )
            EditorTransportTextButton(
                stringResource(R.string.lyric_timing_editor_continuous),
                onSetContinuous,
                primary = lineAvailable,
                modifier = Modifier.weight(1f)
            )
            EditorTransportTextButton(
                stringResource(R.string.lyric_timing_editor_end),
                onSetEnd,
                primary = timingMode == TimingMode.Word && wordsAvailable,
                modifier = Modifier.weight(1f)
            )
            EditorTransportTextButton("+50", { onAdjust(50L) })
        }
    }
}

@Composable
private fun EditorTransportTextButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = if (primary) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

private val ttmlTagAliases = listOf("TTML LYRICS", "TTML LYRIC", "TTMLLYRICS", "TTMLLYRIC", "TTML")
