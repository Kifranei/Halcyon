package com.ella.music.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.buffer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ella.music.ui.analytics.libraryAnalysisCacheKey
import com.ella.music.ui.search.searchIdentityKey
import com.ella.music.data.model.isNeteaseStream
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.normalizedAudioFormat
import com.ella.music.data.normalizedBitDepth
import com.ella.music.data.model.AudioInfo
import com.ella.music.ui.components.EllaMiuixChip
import top.yukonga.miuix.kmp.basic.Text

internal val LibraryAudioFormats = listOf("MP3", "OGG", "AAC", "ALAC", "FLAC", "WAV", "APE", "OPUS", "M4A", "WMA", "AIFF", "DSD", "AC3", "EC3", "AC4")
internal const val OTHER_AUDIO_FORMAT = "OTHER"
internal data class LibraryAudioFilter(
    val qualities: Set<String> = emptySet(),
    val formats: Set<String> = emptySet(),
    val depths: Set<Int> = emptySet(),
    val rates: Set<Int> = emptySet()
) {
    val isEmpty get() = qualities.isEmpty() && formats.isEmpty() && depths.isEmpty() && rates.isEmpty()
    fun toggleQuality(quality: String): LibraryAudioFilter {
        val next = qualities.toggled(quality)
        return copy(qualities = next, depths = if ("HR" in next) depths else emptySet(), rates = if ("HR" in next) rates else emptySet())
    }
    fun matches(info: AudioInfo): Boolean {
        val format = normalizedAudioFormat(info.format)
        return (qualities.isEmpty() || audioQualitySummary(info).listTag in qualities) &&
            (formats.isEmpty() || format in formats || (OTHER_AUDIO_FORMAT in formats && format !in LibraryAudioFormats)) &&
            (depths.isEmpty() || normalizedBitDepth(info) in depths) &&
            (rates.isEmpty() || info.sampleRate in rates)
    }
}
internal fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
internal fun LibraryAudioFilterPanel(filter: LibraryAudioFilter, onChange: (LibraryAudioFilter) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("LQ", "HQ", "SQ", "HR", "MQ", com.ella.music.data.DOLBY_MARK, "SUR").forEach { quality ->
                val label = when (quality) {
                    com.ella.music.data.DOLBY_MARK -> androidx.compose.ui.res.stringResource(com.ella.music.R.string.netease_quality_dolby)
                    "SUR" -> androidx.compose.ui.res.stringResource(com.ella.music.R.string.netease_quality_surround)
                    else -> quality
                }
                EllaMiuixChip(text = label, selected = quality in filter.qualities, onClick = { onChange(filter.toggleQuality(quality)) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (LibraryAudioFormats + OTHER_AUDIO_FORMAT).forEach { format ->
                EllaMiuixChip(text = if (format == OTHER_AUDIO_FORMAT) androidx.compose.ui.res.stringResource(com.ella.music.R.string.library_audio_other) else format,
                    selected = format in filter.formats, onClick = { onChange(filter.copy(formats = filter.formats.toggled(format))) })
            }
        }
        if ("HR" in filter.qualities) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(16, 24).forEach { depth ->
                    EllaMiuixChip(text = "$depth-bit", selected = depth in filter.depths, onClick = { onChange(filter.copy(depths = filter.depths.toggled(depth))) })
                }
                Text("│")
                listOf(44100, 48000, 96000).forEach { rate ->
                    EllaMiuixChip(text = if (rate == 44100) "44.1 kHz" else "${rate / 1000} kHz", selected = rate in filter.rates,
                        onClick = { onChange(filter.copy(rates = filter.rates.toggled(rate))) })
                }
            }
        }
    }
}

/** A cheap format hint prevents a format-only filter from probing every song on disk. */
internal fun libraryAudioFormatHint(song: com.ella.music.data.model.Song): String? {
    val extension = song.fileName.ifBlank { song.path.substringBefore('?').substringAfterLast('/') }
        .substringAfterLast('.', "").uppercase(java.util.Locale.ROOT)
    return when (extension) {
        "MP3", "FLAC", "WAV", "APE", "AAC", "OGG", "OPUS", "WMA", "AIFF" -> extension
        "AC3", "EC3", "EAC3", "AC4" -> normalizedAudioFormat(extension)
        "AIF" -> "AIFF"
        "DSF", "DFF" -> "DSD"
        "M4A", "MP4", "ALAC", "" -> null // Container or unknown codec: inspect actual audio metadata.
        else -> extension
    }
}

internal data class LibraryAudioFilterProgress(
    val songs: List<com.ella.music.data.model.Song>, val checked: Int, val total: Int,
    val complete: Boolean
)

/** Emits initial empty results, then checkpoints; cancellation stops an obsolete selection. */
internal fun filterLibraryAudio(
    songs: List<com.ella.music.data.model.Song>,
    filter: LibraryAudioFilter,
    metadataRevision: Long? = null,
    loadAudioInfo: (com.ella.music.data.model.Song) -> AudioInfo
): kotlinx.coroutines.flow.Flow<LibraryAudioFilterProgress> = kotlinx.coroutines.flow.flow {
    if (filter.isEmpty) {
        emit(LibraryAudioFilterProgress(songs, songs.size, songs.size, true))
        return@flow
    }
    val key = metadataRevision?.takeUnless { songs.any { it.isNeteaseStream() } }?.let { LibraryAudioFilterResultKey(songs.libraryAnalysisCacheKey(), filter, it) }
    key?.let { LibraryAudioFilterResultCache.get(it) }?.let { identities ->
        val matches = songs.filter { it.searchIdentityKey() in identities }
        emit(LibraryAudioFilterProgress(matches, songs.size, songs.size, true))
        return@flow
    }
    emit(LibraryAudioFilterProgress(emptyList(), 0, songs.size, songs.isEmpty()))
    val matches = ArrayList<com.ella.music.data.model.Song>()
    suspend fun accepts(song: com.ella.music.data.model.Song): Boolean {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val format = libraryAudioFormatHint(song)
        val formatMatches = format == null || filter.formats.isEmpty() || format in filter.formats ||
            (OTHER_AUDIO_FORMAT in filter.formats && format !in LibraryAudioFormats)
        if (!formatMatches) return false
        val metadataRequired = filter.qualities.isNotEmpty() || filter.depths.isNotEmpty() ||
            filter.rates.isNotEmpty() || format == null
        return !metadataRequired || filter.matches(loadAudioInfo(song))
    }
    if (songs.isNotEmpty()) {
        if (accepts(songs[0])) matches.add(songs[0])
        emit(LibraryAudioFilterProgress(matches.toList(), 1, songs.size, songs.size == 1))
    }
    var lastPublish = System.nanoTime()
    val parallel = filter.qualities.isNotEmpty() || filter.depths.isNotEmpty() || filter.rates.isNotEmpty() ||
        songs.any { libraryAudioFormatHint(it) == null }
    for (start in 1 until songs.size step 64) {
        val end = minOf(start + 64, songs.size)
        val accepted = BooleanArray(end - start)
        if (parallel) kotlinx.coroutines.coroutineScope {
            val next = java.util.concurrent.atomic.AtomicInteger(start)
            List(minOf(4, end - start)) {
                async(kotlinx.coroutines.Dispatchers.IO) {
                    while (true) {
                        val index = next.getAndIncrement()
                        if (index >= end) break
                        accepted[index - start] = accepts(songs[index])
                    }
                }
            }.forEach { it.await() }
        } else {
            for (index in start until end) accepted[index - start] = accepts(songs[index])
        }
        for (index in start until end) if (accepted[index - start]) matches.add(songs[index])
        val now = System.nanoTime()
        if (end == songs.size || now - lastPublish >= 200_000_000L) {
            emit(LibraryAudioFilterProgress(matches.toList(), end, songs.size, end == songs.size))
            lastPublish = now
        }
    }
    if (key != null) LibraryAudioFilterResultCache.put(key, matches.mapTo(HashSet()) { it.searchIdentityKey() })
}.flowOn(kotlinx.coroutines.Dispatchers.IO).buffer(0)

private data class LibraryAudioFilterResultKey(val library: String, val filter: LibraryAudioFilter, val revision: Long)
private object LibraryAudioFilterResultCache {
    private val entries = object : LinkedHashMap<LibraryAudioFilterResultKey, Set<String>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LibraryAudioFilterResultKey, Set<String>>): Boolean = size > 8
    }
    @Synchronized fun get(key: LibraryAudioFilterResultKey): Set<String>? = entries[key]
    @Synchronized fun put(key: LibraryAudioFilterResultKey, keys: Set<String>) { entries[key] = keys }
}
