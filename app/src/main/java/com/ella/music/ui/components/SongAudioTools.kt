package com.ella.music.ui.components

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.ella.music.R
import com.ella.music.data.isContentAudioSource
import com.ella.music.data.isFileUriAudioSource
import com.ella.music.data.isHttpAudioSource
import com.ella.music.data.model.Song
import com.ella.music.data.sanitizeExportFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

private enum class AudioExportFormat(
    val extension: String,
    val mimeType: String,
    val encoderArguments: List<String>,
    val outputFolder: String = "Converted"
) {
    Mp3("mp3", "audio/mpeg", listOf("-c:a", "libmp3lame", "-b:a", "192k")),
    M4a("m4a", "audio/mp4", listOf("-c:a", "aac", "-b:a", "256k")),
    Ogg("ogg", "audio/ogg", listOf("-c:a", "libvorbis", "-q:a", "6")),
    Opus("opus", "audio/opus", listOf("-c:a", "libopus", "-b:a", "160k")),
    Flac("flac", "audio/flac", listOf("-c:a", "flac", "-compression_level", "5")),
    Wav("wav", "audio/wav", listOf("-c:a", "pcm_s16le"));

    fun label(context: Context): String = when (this) {
        Mp3 -> context.getString(R.string.audio_tools_format_mp3)
        M4a -> context.getString(R.string.audio_tools_format_m4a)
        Ogg -> context.getString(R.string.audio_tools_format_ogg)
        Opus -> context.getString(R.string.audio_tools_format_opus)
        Flac -> context.getString(R.string.audio_tools_format_flac)
        Wav -> context.getString(R.string.audio_tools_format_wav)
    }
}

private sealed interface AudioToolsPage {
    data object Home : AudioToolsPage
    data object Format : AudioToolsPage
    data object Tracks : AudioToolsPage
    data object Cue : AudioToolsPage
}

private sealed interface AudioTrackProbeState {
    data object Idle : AudioTrackProbeState
    data object Loading : AudioTrackProbeState
    data class Ready(val tracks: List<ExtractableAudioTrack>) : AudioTrackProbeState
    data class Failed(val message: String) : AudioTrackProbeState
}

private enum class AudioToolsOperation {
    Convert,
    ExportStreams,
    SplitCue
}

private sealed interface CueProbeState {
    data object Idle : CueProbeState
    data object Loading : CueProbeState
    data class Ready(val album: CueAlbum) : CueProbeState
    data class NeedsAudioChoice(val album: CueAlbum, val candidates: List<File>) : CueProbeState
    data class Failed(val message: String) : CueProbeState
}

private data class CueTrack(
    val number: Int,
    val title: String,
    val performer: String,
    val composer: String,
    val startMs: Long
)

private data class CueAlbum(
    val cueFile: File,
    val sourceFile: File?,
    val title: String,
    val performer: String,
    val genre: String,
    val date: String,
    val tracks: List<CueTrack>
)

private data class ExtractableAudioTrack(
    val streamIndex: Long,
    val ordinal: Int,
    val codec: String,
    val channelLayout: String
)

private data class PreparedAudioSource(
    val path: String,
    val temporaryFile: File? = null
) {
    fun deleteTemporaryFile() {
        temporaryFile?.delete()
    }
}

/**
 * Provides local, explicit FFmpeg actions. Output is first produced privately and then published
 * through MediaStore so it is visible in the user's music folder under scoped storage.
 */
@Composable
internal fun SongAudioToolsSheet(
    song: Song,
    onDismiss: () -> Unit,
    onExported: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember(song.path) { mutableStateOf<AudioToolsPage>(AudioToolsPage.Home) }
    var probeState by remember(song.path) { mutableStateOf<AudioTrackProbeState>(AudioTrackProbeState.Idle) }
    var cueState by remember(song.path) { mutableStateOf<CueProbeState>(CueProbeState.Idle) }
    var operation by remember { mutableStateOf<AudioToolsOperation?>(null) }
    var operationJob by remember { mutableStateOf<Job?>(null) }
    var cueProgress by remember { mutableStateOf<CueSplitProgress?>(null) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val choice = cueState as? CueProbeState.NeedsAudioChoice ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val selected = withContext(Dispatchers.IO) { copySelectedAudio(context, uri) }
            cueState = CueProbeState.Ready(choice.album.copy(sourceFile = selected))
        }
    }
    val cuePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        page = AudioToolsPage.Cue
        cueState = CueProbeState.Loading
        scope.launch {
            cueState = withContext(Dispatchers.IO) {
                runCatching {
                    val cue = copyCueDocument(context, uri)
                    resolveCueAlbum(cue, sourceFileFor(song))
                }.fold(
                    onSuccess = { resolution ->
                        when (resolution) {
                            is CueResolution.Resolved -> CueProbeState.Ready(resolution.album)
                            is CueResolution.NeedsAudioChoice -> CueProbeState.NeedsAudioChoice(resolution.album, resolution.candidates)
                        }
                    },
                    onFailure = { CueProbeState.Failed(it.localizedMessage.orEmpty()) }
                )
            }
        }
    }

    LaunchedEffect(page, song.path) {
        if (page != AudioToolsPage.Tracks || probeState !is AudioTrackProbeState.Idle) return@LaunchedEffect
        probeState = AudioTrackProbeState.Loading
        probeState = withContext(Dispatchers.IO) {
            runCatching { inspectAudioTracks(context, song) }
                .fold(
                    onSuccess = { AudioTrackProbeState.Ready(it) },
                    onFailure = { AudioTrackProbeState.Failed(it.localizedMessage.orEmpty()) }
                )
        }
    }

    fun publishResult(result: Result<Int>) {
        operation = null
        result.onSuccess { exportedCount ->
            if (exportedCount > 0) {
                Toast.makeText(
                    context,
                    context.getString(R.string.audio_tools_saved, exportedCount),
                    Toast.LENGTH_SHORT
                ).show()
                onExported()
            }
        }.onFailure { error ->
            Toast.makeText(
                context,
                context.getString(
                    R.string.audio_tools_failed,
                    error.localizedMessage.orEmpty().ifBlank { error.javaClass.simpleName }
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun startConversion(format: AudioExportFormat) {
        operation = AudioToolsOperation.Convert
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { convertAudio(context, song, format) }
            }
            publishResult(result)
        }
    }

    fun startTrackExport(tracks: List<ExtractableAudioTrack>) {
        operation = AudioToolsOperation.ExportStreams
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { exportAudioTracks(context, song, tracks) }
            }
            publishResult(result)
        }
    }

    fun startCueSplit(album: CueAlbum) {
        operation = AudioToolsOperation.SplitCue
        cueProgress = CueSplitProgress(0, album.tracks.size, null)
        operationJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    splitCueAlbum(context, album) { progress ->
                        scope.launch { cueProgress = progress }
                    }
                }
            }
            cueProgress = null
            operationJob = null
            publishResult(result)
        }
    }

    val sheetTitle = when (page) {
        AudioToolsPage.Home -> stringResource(R.string.song_more_audio_tools)
        AudioToolsPage.Format -> stringResource(R.string.audio_tools_choose_format)
        AudioToolsPage.Tracks -> stringResource(R.string.audio_tools_stream_export)
        AudioToolsPage.Cue -> stringResource(R.string.audio_tools_cue_split)
    }
    EllaMiuixBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = sheetTitle,
        onDismissRequest = onDismiss
    ) {
        SongSheetColumn {
            ExplicitSongTitle(
                title = song.title.ifBlank { song.fileName },
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            when (page) {
                AudioToolsPage.Home -> {
                    SongMenuItem(stringResource(R.string.audio_tools_convert), onClick = {
                        page = AudioToolsPage.Format
                    })
                    SongMenuItem(stringResource(R.string.audio_tools_cue_split), onClick = {
                        cuePicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                    })
                    Text(
                        text = stringResource(R.string.audio_tools_cue_split_summary),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    SongMenuItem(stringResource(R.string.audio_tools_stream_export), onClick = {
                        page = AudioToolsPage.Tracks
                    })
                    Text(
                        text = stringResource(R.string.audio_tools_stream_export_summary),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    SongMenuItem(stringResource(R.string.common_cancel), onDismiss)
                }

                AudioToolsPage.Format -> {
                    AudioExportFormat.entries.forEach { format ->
                        SongMenuItem(format.label(context), onClick = { startConversion(format) })
                    }
                    SongMenuItem(stringResource(R.string.common_back), onClick = {
                        page = AudioToolsPage.Home
                    })
                }

                AudioToolsPage.Tracks -> when (val state = probeState) {
                    AudioTrackProbeState.Idle,
                    AudioTrackProbeState.Loading -> Text(
                        text = stringResource(R.string.audio_tools_detecting_tracks),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    is AudioTrackProbeState.Failed -> {
                        Text(
                            text = stringResource(
                                R.string.audio_tools_failed,
                                state.message.ifBlank { context.getString(R.string.common_unknown) }
                            ),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        SongMenuItem(stringResource(R.string.common_back), onClick = {
                            page = AudioToolsPage.Home
                        })
                    }

                    is AudioTrackProbeState.Ready -> {
                        if (state.tracks.size < 2) {
                            Text(
                                text = stringResource(R.string.audio_tools_no_multiple_tracks),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        } else {
                            state.tracks.forEach { track ->
                                val description = listOf(track.codec, track.channelLayout)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                SongMenuItem(
                                    title = context.getString(R.string.audio_tools_track, track.ordinal) +
                                        description.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
                                    onClick = { startTrackExport(listOf(track)) }
                                )
                            }
                            SongMenuItem(
                                stringResource(R.string.audio_tools_export_all_tracks),
                                onClick = { startTrackExport(state.tracks) }
                            )
                        }
                        SongMenuItem(stringResource(R.string.common_back), onClick = {
                            page = AudioToolsPage.Home
                        })
                    }
                }

                AudioToolsPage.Cue -> when (val state = cueState) {
                    CueProbeState.Idle,
                    CueProbeState.Loading -> Text(
                        text = stringResource(R.string.audio_tools_cue_reading),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    is CueProbeState.Failed -> {
                        Text(
                            text = stringResource(R.string.audio_tools_failed, state.message.ifBlank { context.getString(R.string.common_unknown) }),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        SongMenuItem(stringResource(R.string.common_back), onClick = { page = AudioToolsPage.Home })
                    }

                    is CueProbeState.NeedsAudioChoice -> {
                        Text(
                            text = stringResource(R.string.audio_tools_cue_choose_audio),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        state.candidates.forEach { candidate ->
                            SongMenuItem(candidate.name, onClick = {
                                cueState = CueProbeState.Ready(state.album.copy(sourceFile = candidate))
                            })
                        }
                        SongMenuItem(stringResource(R.string.audio_tools_cue_choose_other_audio), onClick = {
                            audioPicker.launch(arrayOf("audio/*", "application/octet-stream"))
                        })
                        SongMenuItem(stringResource(R.string.common_back), onClick = { page = AudioToolsPage.Home })
                    }

                    is CueProbeState.Ready -> {
                        CueAlbumPreview(album = state.album)
                        SongMenuItem(stringResource(R.string.audio_tools_cue_start), onClick = { startCueSplit(state.album) })
                        SongMenuItem(stringResource(R.string.common_back), onClick = { page = AudioToolsPage.Home })
                    }
                }
            }
        }
    }

    operation?.let { activeOperation ->
        EllaMiuixDialog(
            show = true,
            title = stringResource(
                if (activeOperation == AudioToolsOperation.Convert) {
                    R.string.audio_tools_converting
                } else if (activeOperation == AudioToolsOperation.SplitCue) {
                    R.string.audio_tools_cue_splitting
                } else {
                    R.string.audio_tools_exporting_streams
                }
            ),
            summary = cueProgress?.let { progress ->
                progress.trackTitle?.let { "$it (${progress.completed}/${progress.total})" }
                    ?: "${progress.completed}/${progress.total}"
            } ?: stringResource(R.string.audio_tools_processing_summary),
            onDismissRequest = {}
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                EllaLoadingIndicator()
                if (activeOperation == AudioToolsOperation.SplitCue) {
                    SongMenuItem(
                        stringResource(R.string.common_cancel),
                        onClick = {
                            FFmpegKit.cancel()
                            operationJob?.cancel()
                            operationJob = null
                            cueProgress = null
                            operation = null
                        }
                    )
                }
            }
        }
    }
}

private fun inspectAudioTracks(context: Context, song: Song): List<ExtractableAudioTrack> {
    val source = prepareAudioSource(context, song)
    return try {
        val session = FFprobeKit.getMediaInformation(source.path)
        val information = session.mediaInformation
            ?: throw IllegalStateException("FFprobe did not return media information")
        information.streams
            .asSequence()
            .filter { it.type.equals("audio", ignoreCase = true) }
            .mapIndexedNotNull { listIndex, stream ->
                stream.index?.let { streamIndex ->
                    ExtractableAudioTrack(
                        streamIndex = streamIndex,
                        ordinal = listIndex + 1,
                        codec = stream.codec.orEmpty(),
                        channelLayout = stream.channelLayout.orEmpty()
                    )
                }
            }
            .toList()
    } finally {
        source.deleteTemporaryFile()
    }
}

private sealed interface CueResolution {
    data class Resolved(val album: CueAlbum) : CueResolution
    data class NeedsAudioChoice(val album: CueAlbum, val candidates: List<File>) : CueResolution
}

private fun copyCueDocument(context: Context, uri: Uri): File {
    val cue = File(context.cacheDir, "cue-input-${UUID.randomUUID()}.cue")
    context.contentResolver.openInputStream(uri)?.use { input ->
        cue.outputStream().use(input::copyTo)
    } ?: throw IllegalStateException("Cannot open CUE file")
    return cue
}

private fun copySelectedAudio(context: Context, uri: Uri): File {
    val name = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        .orEmpty()
    val extension = name.substringAfterLast('.', "audio").take(12)
    val audio = File(context.cacheDir, "cue-audio-${UUID.randomUUID()}.$extension")
    context.contentResolver.openInputStream(uri)?.use { input -> audio.outputStream().use(input::copyTo) }
        ?: throw IllegalStateException("Cannot open selected audio")
    return audio
}

private fun sourceFileFor(song: Song): File? = when {
    song.path.isHttpAudioSource() || song.path.isContentAudioSource() -> null
    song.path.isFileUriAudioSource() -> Uri.parse(song.path).path?.let(::File)
    else -> File(song.path)
}?.takeIf { it.isFile }

private fun resolveCueAlbum(cueFile: File, preferredSource: File?): CueResolution {
    val parsed = parseCue(cueFile)
    val cueDirectory = preferredSource?.parentFile ?: cueFile.parentFile ?: throw IllegalStateException("CUE directory unavailable")
    val candidates = findCueAudioCandidates(cueDirectory, parsed.fileName, preferredSource)
    val skeleton = CueAlbum(
        cueFile = cueFile,
        sourceFile = candidates.firstOrNull(),
        title = parsed.albumTitle,
        performer = parsed.albumPerformer,
        genre = parsed.albumGenre,
        date = parsed.albumDate,
        tracks = parsed.tracks
    )
    return if (candidates.size == 1) CueResolution.Resolved(skeleton)
    else CueResolution.NeedsAudioChoice(skeleton, candidates)
}

private data class ParsedCue(
    val fileName: String,
    val albumTitle: String,
    val albumPerformer: String,
    val albumGenre: String,
    val albumDate: String,
    val tracks: List<CueTrack>
)

private fun parseCue(file: File): ParsedCue {
    val content = decodeCueText(file.readBytes())
    val filePattern = Regex("(?im)^\\s*FILE\\s+\\\"([^\\\"]+)\\\"")
    val trackPattern = Regex("(?im)^\\s*TRACK\\s+(\\d+)\\s+AUDIO\\s*$")
    val indexPattern = Regex("(?im)^\\s*INDEX\\s+01\\s+(\\d+):(\\d+):(\\d+)\\s*$")
    val titlePattern = Regex("(?im)^\\s*TITLE\\s+(?:\\\"([^\\\"]*)\\\"|(.*?))\\s*$")
    val performerPattern = Regex("(?im)^\\s*PERFORMER\\s+(?:\\\"([^\\\"]*)\\\"|(.*?))\\s*$")
    val genrePattern = Regex("(?im)^\\s*REM\\s+GENRE\\s+(?:\\\"([^\\\"]*)\\\"|(.*?))\\s*$")
    val datePattern = Regex("(?im)^\\s*REM\\s+DATE\\s+(?:\\\"([^\\\"]*)\\\"|(.*?))\\s*$")
    val composerPattern = Regex("(?im)^\\s*REM\\s+COMPOSER\\s+(?:\\\"([^\\\"]*)\\\"|(.*?))\\s*$")
    val fileName = filePattern.find(content)?.groupValues?.get(1)?.trim().orEmpty()
    require(fileName.isNotBlank()) { "The CUE file does not declare a FILE entry" }
    val blocks = trackPattern.findAll(content).toList()
    require(blocks.isNotEmpty()) { "The CUE file contains no audio TRACK entries" }
    val albumPrefix = content.substring(0, blocks.first().range.first)
    val albumTitle = titlePattern.find(albumPrefix).titleValue()
    val albumPerformer = performerPattern.find(albumPrefix).titleValue()
    val albumGenre = genrePattern.find(albumPrefix).titleValue()
    val albumDate = datePattern.find(albumPrefix).titleValue()
    val tracks = blocks.mapIndexedNotNull { index, match ->
        val end = blocks.getOrNull(index + 1)?.range?.first ?: content.length
        val block = content.substring(match.range.first, end)
        val position = indexPattern.find(block) ?: return@mapIndexedNotNull null
        CueTrack(
            number = match.groupValues[1].toIntOrNull() ?: index + 1,
            title = titlePattern.find(block).titleValue().ifBlank { "Track ${index + 1}" },
            performer = performerPattern.find(block).titleValue().ifBlank { albumPerformer },
            composer = composerPattern.find(block).titleValue(),
            startMs = cueTimeToMs(position.groupValues[1], position.groupValues[2], position.groupValues[3])
        )
    }
    require(tracks.isNotEmpty()) { "No TRACK contains an INDEX 01 time" }
    return ParsedCue(fileName, albumTitle, albumPerformer, albumGenre, albumDate, tracks)
}

private fun MatchResult?.titleValue(): String = this?.let { match ->
    match.groupValues[1].ifBlank { match.groupValues.getOrElse(2) { "" } }.trim()
}.orEmpty()

private fun cueTimeToMs(minutes: String, seconds: String, frames: String): Long =
    ((minutes.toLongOrNull() ?: 0L) * 60_000L) +
        ((seconds.toLongOrNull() ?: 0L) * 1_000L) +
        ((frames.toLongOrNull() ?: 0L) * 1_000L / 75L)

internal fun decodeCueText(bytes: ByteArray): String {
    val bomCharset = when {
        bytes.startsWith(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) -> StandardCharsets.UTF_8
        bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xFE.toByte())) -> StandardCharsets.UTF_16LE
        bytes.startsWith(byteArrayOf(0xFE.toByte(), 0xFF.toByte())) -> StandardCharsets.UTF_16BE
        else -> null
    }
    if (bomCharset != null) {
        return decodeCueStrict(bytes, bomCharset).orEmpty().removePrefix("\uFEFF")
    }
    // Valid UTF-8 is unambiguous. Scoring it against legacy decoders used to prefer mojibake
    // because the broken text contains more non-ASCII code points than the original title.
    decodeCueStrict(bytes, StandardCharsets.UTF_8)?.let { return it }
    val charsets = listOfNotNull(
        runCatching { Charset.forName("GB18030") }.getOrNull(),
        runCatching { Charset.forName("Big5") }.getOrNull(),
        runCatching { Charset.forName("Shift_JIS") }.getOrNull()
    ).distinct()
    return charsets.mapNotNull { charset ->
        decodeCueStrict(bytes, charset)?.removePrefix("\uFEFF")?.let { charset to it }
    }.maxByOrNull { (charset, text) -> cueDecodingScore(text, charset) }?.second.orEmpty()
}

private fun decodeCueStrict(bytes: ByteArray, charset: Charset): String? = runCatching {
    charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(bytes))
        .toString()
}.getOrNull()

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun cueDecodingScore(text: String, charset: Charset): Int {
    // CUE command words are ASCII under every candidate encoding. Prefer the title decoding
    // that has the fewest malformed glyphs, so GB18030 and Shift-JIS stay readable.
    val commands = Regex("(?im)^\\s*(FILE|TRACK|INDEX|TITLE|PERFORMER|REM\\s+(GENRE|DATE|COMPOSER))\\b")
        .findAll(text)
        .count()
    val replacements = text.count { it == '\uFFFD' }
    val controls = text.count { it.code in 0..8 || it.code in 14..31 }
    val readableNonAscii = text.count { it.code >= 0x80 && !it.isISOControl() }
    val mojibake = text.count { it in "澶瀛樻鏂闂锛鈥�ÃÂâðæåç" }
    val japaneseKana = text.count { it.code in 0x3040..0x30FF }
    val japaneseBonus = if (charset.name().contains("JIS", ignoreCase = true)) japaneseKana * 40 else 0
    return commands * 1_000 + readableNonAscii + japaneseBonus -
        replacements * 2_000 - controls * 30 - mojibake * 80
}

private fun findCueAudioCandidates(directory: File, declaredName: String, preferred: File?): List<File> {
    val allowedExtensions = setOf("flac", "wav", "ape", "tta", "tak", "m4a", "alac", "mp3")
    val files = directory.listFiles()
        ?.filter { it.isFile && it.extension.lowercase() in allowedExtensions }
        .orEmpty()
    val exact = files.filter { it.name.equals(declaredName, ignoreCase = true) }
    if (exact.isNotEmpty()) return exact
    val declaredBase = File(declaredName).nameWithoutExtension.lowercase()
    val sameBase = files.filter { it.nameWithoutExtension.equals(declaredBase, ignoreCase = true) }
    if (sameBase.isNotEmpty()) return sameBase
    preferred?.takeIf { it.parentFile?.canonicalFile == directory.canonicalFile }?.let { return listOf(it) }
    val related = files.filter { candidate ->
        val base = candidate.nameWithoutExtension.lowercase()
        base.contains(declaredBase) || declaredBase.contains(base)
    }
    if (related.isNotEmpty()) return related
    if (files.size == 1) return files
    // The tool was opened from this local song, making it an explicit fallback when the CUE
    // itself was copied into app storage and no sibling file can be inspected.
    return preferred?.let(::listOf).orEmpty()
}

@Composable
private fun CueAlbumPreview(album: CueAlbum) {
    Text(
        text = listOf(album.title, album.performer).filter { it.isNotBlank() }.joinToString(" · ")
            .ifBlank { album.sourceFile?.name.orEmpty() },
        color = MiuixTheme.colorScheme.onSurface,
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
    album.tracks.forEach { track ->
        Text(
            text = "%02d  %s".format(track.number, track.title),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp)
        )
    }
}

private data class CueSplitProgress(
    val completed: Int,
    val total: Int,
    val trackTitle: String?
)

private fun splitCueAlbum(
    context: Context,
    album: CueAlbum,
    onProgress: (CueSplitProgress) -> Unit
): Int {
    val sourceFile = requireNotNull(album.sourceFile) { "Choose a whole-album audio file first" }
    var succeeded = 0
    var firstFailure: Throwable? = null
    album.tracks.forEachIndexed { index, track ->
        onProgress(CueSplitProgress(index, album.tracks.size, track.title))
        val output = temporaryAudioFile(context, "flac")
        try {
            val nextStart = album.tracks.getOrNull(index + 1)?.startMs
            val arguments = buildList {
                addAll(listOf("-y", "-ss", cueTimeString(track.startMs), "-i", sourceFile.absolutePath))
                nextStart?.let { addAll(listOf("-t", cueTimeString((it - track.startMs).coerceAtLeast(1L)))) }
                addAll(listOf(
                    "-map", "0:a:0?", "-map", "0:v:0?", "-c:a", "flac", "-c:v", "copy",
                    "-disposition:v:0", "attached_pic", "-compression_level", "5",
                    "-metadata", "title=${track.title}",
                    "-metadata", "artist=${track.performer}",
                    "-metadata", "album=${album.title}",
                    "-metadata", "genre=${album.genre}",
                    "-metadata", "date=${album.date}",
                    "-metadata", "album_artist=${album.performer}",
                    "-metadata", "track=${track.number}"
                ))
                track.composer.takeIf { it.isNotBlank() }?.let {
                    addAll(listOf("-metadata", "composer=$it"))
                }
                add(output.absolutePath)
            }
            executeFfmpeg(arguments)
            publishCueTrack(context, output, album, track)
            succeeded++
            onProgress(CueSplitProgress(succeeded, album.tracks.size, track.title))
        } catch (error: Throwable) {
            if (firstFailure == null) firstFailure = error
        } finally {
            output.delete()
        }
    }
    if (succeeded == 0) throw firstFailure ?: IllegalStateException("No CUE track could be exported")
    return succeeded
}

private fun cueTimeString(ms: Long): String = "%.3f".format(java.util.Locale.US, ms / 1_000.0)

private fun publishCueTrack(context: Context, output: File, album: CueAlbum, track: CueTrack) {
    val displayName = "%02d - %s.flac".format(track.number, safeAudioName(track.title))
    publishAudioFile(
        context = context,
        temporaryOutput = output,
        displayName = displayName,
        mimeType = "audio/flac",
        outputFolder = "Cue Split/${safeAudioName(album.title.ifBlank { album.sourceFile?.nameWithoutExtension.orEmpty() })}",
        song = Song(
            id = -track.number.toLong(),
            title = track.title,
            artist = track.performer,
            album = album.title,
            albumId = 0L,
            duration = 0L,
            path = output.absolutePath,
            fileName = displayName,
            albumArtist = album.performer,
            genre = album.genre,
            year = album.date,
            composer = track.composer
        )
    )
}

private fun safeAudioName(value: String): String =
    value.sanitizeExportFileName(fallback = "audio", maxLength = 96)

private fun convertAudio(context: Context, song: Song, format: AudioExportFormat): Int {
    val source = prepareAudioSource(context, song)
    val temporaryOutput = temporaryAudioFile(context, format.extension)
    return try {
        executeFfmpeg(
            listOf(
                "-y",
                "-i", source.path,
                "-map", "0:a:0?",
                "-map_metadata", "0",
                "-vn"
            ) + format.encoderArguments + temporaryOutput.absolutePath
        )
        publishAudioFile(
            context = context,
            temporaryOutput = temporaryOutput,
            displayName = outputDisplayName(song, null, format.extension),
            mimeType = format.mimeType,
            outputFolder = format.outputFolder,
            song = song
        )
        1
    } finally {
        source.deleteTemporaryFile()
        temporaryOutput.delete()
    }
}

private fun exportAudioTracks(
    context: Context,
    song: Song,
    tracks: List<ExtractableAudioTrack>
): Int {
    require(tracks.isNotEmpty()) { "No audio tracks selected" }
    val source = prepareAudioSource(context, song)
    var exported = 0
    var firstFailure: Throwable? = null
    try {
        tracks.forEach { track ->
            runCatching {
                exportAudioTrack(context, source, song, track)
            }.onSuccess {
                exported += 1
            }.onFailure { error ->
                if (firstFailure == null) firstFailure = error
            }
        }
    } finally {
        source.deleteTemporaryFile()
    }
    if (exported == 0) throw firstFailure ?: IllegalStateException("No track could be exported")
    return exported
}

private fun exportAudioTrack(
    context: Context,
    source: PreparedAudioSource,
    song: Song,
    track: ExtractableAudioTrack
) {
    val temporaryOutput = temporaryAudioFile(context, "mka")
    try {
        executeFfmpeg(
            listOf(
                "-y",
                "-i", source.path,
                "-map", "0:${track.streamIndex}",
                "-map_metadata", "0",
                "-c", "copy",
                temporaryOutput.absolutePath
            )
        )
        publishAudioFile(
            context = context,
            temporaryOutput = temporaryOutput,
            displayName = outputDisplayName(song, track.ordinal, "mka"),
            mimeType = "audio/x-matroska",
            outputFolder = "Separated",
            song = song
        )
    } finally {
        temporaryOutput.delete()
    }
}

private fun executeFfmpeg(arguments: List<String>) {
    val session = FFmpegKit.executeWithArguments(arguments.toTypedArray())
    if (!ReturnCode.isSuccess(session.returnCode)) {
        val diagnostic = session.allLogsAsString
            .lineSequence()
            .toList()
            .takeLast(8)
            .joinToString(" ")
            .ifBlank { "FFmpeg exited with ${session.returnCode}" }
        throw IllegalStateException(diagnostic)
    }
}

private fun prepareAudioSource(context: Context, song: Song): PreparedAudioSource {
    require(!song.path.isHttpAudioSource()) { context.getString(R.string.audio_tools_local_only) }
    val sourceFile = when {
        song.path.isContentAudioSource() -> {
            val extension = sourceExtension(song).ifBlank { "audio" }
            val copy = File(context.cacheDir, "audio-tools-input-${UUID.randomUUID()}.$extension")
            val uri = Uri.parse(song.path)
            context.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use(input::copyTo)
            } ?: throw IllegalStateException("Cannot open selected audio")
            return PreparedAudioSource(copy.absolutePath, copy)
        }

        song.path.isFileUriAudioSource() -> File(Uri.parse(song.path).path.orEmpty())
        else -> File(song.path)
    }
    require(sourceFile.exists() && sourceFile.isFile) { "Audio file is unavailable" }
    return PreparedAudioSource(sourceFile.absolutePath)
}

private fun temporaryAudioFile(context: Context, extension: String): File =
    File(context.cacheDir, "audio-tools-output-${UUID.randomUUID()}.$extension")

private fun publishAudioFile(
    context: Context,
    temporaryOutput: File,
    displayName: String,
    mimeType: String,
    outputFolder: String,
    song: Song
) {
    require(temporaryOutput.exists() && temporaryOutput.length() > 0L) { "FFmpeg produced no output" }
    val resolver = context.contentResolver
    val safeDisplayName = nextAvailableDisplayName(resolver, displayName, outputFolder)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, safeDisplayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Halcyon/$outputFolder")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
        song.title.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.TITLE, it) }
        song.artist.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.ARTIST, it) }
        song.album.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.ALBUM, it) }
        song.albumArtist.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.ALBUM_ARTIST, it) }
        song.genre.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.AudioColumns.GENRE, it) }
        song.year.toIntOrNull()?.let { put(MediaStore.Audio.Media.YEAR, it) }
        song.composer.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.AudioColumns.COMPOSER, it) }
    }
    val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IllegalStateException("Cannot create output in MediaStore")
    try {
        resolver.openOutputStream(uri)?.use { output ->
            temporaryOutput.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot write output in MediaStore")
        resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null)
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw error
    }
}

private fun nextAvailableDisplayName(
    resolver: android.content.ContentResolver,
    requestedName: String,
    outputFolder: String
): String {
    val relativePath = "${Environment.DIRECTORY_MUSIC}/Halcyon/$outputFolder"
    val dot = requestedName.lastIndexOf('.')
    val base = if (dot > 0) requestedName.substring(0, dot) else requestedName
    val extension = if (dot > 0) requestedName.substring(dot) else ""
    repeat(100) { index ->
        val candidate = if (index == 0) requestedName else "$base (${index + 1})$extension"
        val exists = resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(candidate, relativePath),
            null
        )?.use { it.moveToFirst() } ?: false
        if (!exists) return candidate
    }
    return "$base-${UUID.randomUUID().toString().take(8)}$extension"
}

private fun outputDisplayName(song: Song, trackOrdinal: Int?, extension: String): String {
    val rawName = song.title.ifBlank { song.fileName.substringBeforeLast('.') }
        .ifBlank { "audio" }
    val safeName = rawName.sanitizeExportFileName(fallback = "audio", maxLength = 96)
    val suffix = trackOrdinal?.let { " - Track $it" }.orEmpty()
    return "$safeName$suffix.$extension"
}

private fun sourceExtension(song: Song): String = song.fileName
    .substringAfterLast('.', "")
    .ifBlank { song.path.substringBefore('?').substringAfterLast('.', "") }
    .lowercase()
    .take(12)
