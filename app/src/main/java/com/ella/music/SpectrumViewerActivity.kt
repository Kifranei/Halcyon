package com.ella.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.ella.music.data.SettingsManager
import com.ella.music.data.isContentAudioSource
import com.ella.music.data.isFileUriAudioSource
import com.ella.music.data.isHttpAudioSource
import com.ella.music.data.model.Song
import com.ella.music.ui.components.EllaMiuixChip
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.SpectrumViewerLauncher
import com.ella.music.ui.components.openSongSpectrumWithAspectPro
import com.ella.music.ui.components.openSongSpectrumWithKaspek
import com.ella.music.ui.theme.EllaTheme
import com.ella.music.ui.theme.THEME_FOLLOW_SYSTEM
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh

/** Shows an offline spectrogram generated from the selected local audio file. */
class SpectrumViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val song = SpectrumViewerLauncher.songFrom(intent) ?: run {
            finish()
            return
        }
        setContent {
            val settings = remember { SettingsManager.getInstance(this) }
            val themeMode by settings.themeMode.collectAsState(initial = THEME_FOLLOW_SYSTEM)
            EllaTheme(themeMode = themeMode) {
                SpectrumViewerScreen(song = song, onBack = ::finish)
            }
        }
    }
}

@Composable
private fun SpectrumViewerScreen(song: Song, onBack: () -> Unit) {
    val context = LocalContext.current
    var scanToken by remember { mutableIntStateOf(0) }
    var scanState by remember { mutableStateOf<SpectrumScanState>(SpectrumScanState.Loading) }

    LaunchedEffect(song.id, song.path, scanToken) {
        scanState = SpectrumScanState.Loading
        scanState = runCatching {
            withContext(Dispatchers.IO) { buildOfflineSpectrogram(context, song) }
        }.fold(
            onSuccess = SpectrumScanState::Ready,
            onFailure = { error -> SpectrumScanState.Failed(error.userMessage(context)) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF10131C), Color(0xFF202A40), Color(0xFF0B0D14))
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.song_more_view_spectrum),
            color = Color.Transparent,
            defaultWindowInsetsPadding = false,
            titleWindowInsetsPadding = false,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(MiuixIcons.Regular.Back, stringResource(R.string.common_back), tint = Color.White)
                }
            },
            actions = {
                IconButton(onClick = { scanToken++ }) {
                    Icon(
                        MiuixIcons.Regular.Refresh,
                        stringResource(R.string.spectrum_rescan),
                        tint = Color.White
                    )
                }
            }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = song.title.ifBlank { song.fileName },
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .height(360.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.34f)),
                contentAlignment = Alignment.Center
            ) {
                when (val state = scanState) {
                    SpectrumScanState.Loading -> Text(
                        text = stringResource(R.string.spectrum_scanning),
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 15.sp
                    )

                    is SpectrumScanState.Ready -> SpectrumChart(
                        bitmap = state.spectrogram.bitmap,
                        maxFrequencyHz = state.spectrogram.maxFrequencyHz,
                        duration = song.duration,
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    )

                    is SpectrumScanState.Failed -> Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = state.message,
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 14.sp
                        )
                        EllaMiuixChip(
                            stringResource(R.string.spectrum_rescan),
                            selected = false,
                            onClick = { scanToken++ }
                        )
                    }
                }
            }
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                EllaMiuixChip(
                    stringResource(R.string.spectrum_open_aspect_pro),
                    selected = false,
                    onClick = { openSongSpectrumWithAspectPro(context, song) }
                )
                EllaMiuixChip(
                    stringResource(R.string.spectrum_open_kaspek),
                    selected = false,
                    onClick = { openSongSpectrumWithKaspek(context, song) }
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

private sealed interface SpectrumScanState {
    data object Loading : SpectrumScanState
    data class Ready(val spectrogram: OfflineSpectrogram) : SpectrumScanState
    data class Failed(val message: String) : SpectrumScanState
}

private data class OfflineSpectrogram(
    val bitmap: Bitmap,
    val maxFrequencyHz: Int?
)

private const val SPECTRUM_COLUMNS = 720
private const val SPECTRUM_BINS = 320

private fun buildOfflineSpectrogram(context: Context, song: Song): OfflineSpectrogram {
    require(!song.path.isHttpAudioSource()) { context.getString(R.string.audio_tools_local_only) }
    val source = prepareSpectrumSource(context, song)
    val image = File(context.cacheDir, "spectrum-${UUID.randomUUID()}.png")
    try {
        val sourceSampleRateHz = readSpectrumSampleRate(source.file)
        // Let FFmpeg perform both decode and FFT in native code. The previous path decoded an
        // entire song to disk then repeated 720 FFTs in Kotlin, which was unnecessarily slow.
        val session = FFmpegKit.executeWithArguments(
            arrayOf(
                "-hide_banner", "-y", "-i", source.file.absolutePath, "-vn",
                "-filter_complex",
                // Keep the source sample rate. The spectrogram then uses the actual Nyquist
                // frequency (for example, 22.05 kHz for a 44.1 kHz file) instead of leaving a
                // large empty upper half after forcing every source to a fixed 192 kHz clock.
                "[0:a]aformat=channel_layouts=mono,showspectrumpic=" +
                    "s=${SPECTRUM_COLUMNS}x${SPECTRUM_BINS}:legend=disabled:mode=combined:" +
                    "color=fiery:scale=log:drange=120:win_func=hann[s]",
                "-map", "[s]", "-frames:v", "1", image.absolutePath
            )
        )
        val bitmap = BitmapFactory.decodeFile(image.absolutePath)
        if (!ReturnCode.isSuccess(session.returnCode) || bitmap == null) {
            val diagnostic = session.allLogsAsString.lineSequence().toList().takeLast(5).joinToString(" ")
            throw IllegalStateException(diagnostic.ifBlank { "FFmpeg could not generate a spectrogram" })
        }
        val resolvedSampleRateHz = sourceSampleRateHz ?: parseSpectrumSampleRate(session.allLogsAsString)
        return OfflineSpectrogram(
            bitmap = bitmap,
            maxFrequencyHz = resolvedSampleRateHz?.let { it / 2 }?.takeIf { it > 0 }
        )
    } finally {
        source.deleteIfTemporary()
        image.delete()
    }
}

private fun readSpectrumSampleRate(file: File): Int? {
    val extractorRate = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount)
                .asSequence()
                .map { extractor.getTrackFormat(it) }
                .firstOrNull { format ->
                    format.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
                }
                ?.takeIf { it.containsKey(MediaFormat.KEY_SAMPLE_RATE) }
                ?.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } finally {
            extractor.release()
        }
    }.getOrNull()?.takeIf { it > 0 }
    if (extractorRate != null) return extractorRate

    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
        } finally {
            retriever.release()
        }
    }.getOrNull()?.takeIf { it > 0 }
}

private fun parseSpectrumSampleRate(logs: String): Int? =
    Regex("(?:,|\\s)(\\d{4,6})\\s*Hz\\b")
        .find(logs)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it > 0 }

private data class SpectrumSource(val file: File, val temporary: Boolean) {
    fun deleteIfTemporary() {
        if (temporary) file.delete()
    }
}

private fun prepareSpectrumSource(context: Context, song: Song): SpectrumSource {
    if (song.path.isContentAudioSource()) {
        val extension = song.fileName.substringAfterLast('.', "audio").ifBlank { "audio" }
        val copied = File(context.cacheDir, "spectrum-source-${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(Uri.parse(song.path))?.use { input ->
            copied.outputStream().use(input::copyTo)
        } ?: throw IllegalStateException("Cannot open the selected audio file")
        return SpectrumSource(copied, temporary = true)
    }
    val file = if (song.path.isFileUriAudioSource()) File(Uri.parse(song.path).path.orEmpty()) else File(song.path)
    require(file.exists() && file.isFile) { "Audio file is unavailable" }
    return SpectrumSource(file, temporary = false)
}

@Composable
private fun SpectrumChart(
    bitmap: Bitmap,
    maxFrequencyHz: Int?,
    duration: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxHeight().width(42.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    maxFrequencyHz?.formatSpectrumFrequency() ?: stringResource(R.string.spectrum_frequency_unknown),
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 10.sp
                )
                Text("0 Hz", color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp)
            }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.song_more_view_spectrum),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Row(modifier = Modifier.fillMaxHeight().width(42.dp)) {
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFF8462A),
                                    Color(0xFFF7D649),
                                    Color(0xFF49D47F),
                                    Color(0xFF00A6CA),
                                    Color(0xFF142C77),
                                    Color(0xFF040814)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier.fillMaxHeight().padding(start = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("0", "-30", "-60", "-90", "-120").forEach { level ->
                        Text(level, color = Color.White.copy(alpha = 0.62f), fontSize = 9.sp)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 42.dp, end = 42.dp, top = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0:00", color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp)
            Text(duration.formatSpectrumTime(), color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp)
        }
    }
}

private fun Int.formatSpectrumFrequency(): String {
    val kilohertz = this / 1000.0
    val value = String.format(Locale.US, "%.2f", kilohertz)
        .trimEnd('0')
        .trimEnd('.')
    return "$value kHz"
}

private fun Long.formatSpectrumTime(): String {
    val totalSeconds = (coerceAtLeast(0L) / 1_000L).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun Throwable.userMessage(context: Context): String =
    message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.spectrum_scan_failed)
