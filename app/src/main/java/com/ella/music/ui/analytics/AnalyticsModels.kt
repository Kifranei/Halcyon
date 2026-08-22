package com.ella.music.ui.analytics

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.ella.music.R
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.SongPlaybackStats
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.normalizedAudioFormat
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.splitArtistNames
import com.ella.music.data.splitGenreNames
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.ui.search.searchIdentityKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
internal data class LibraryAnalysis(
    val formatBuckets: List<AnalysisBucket>,
    val qualityBuckets: List<AnalysisBucket>,
    val totalCount: Int,
    val totalSizeBytes: Long
)

internal data class AnalysisBucket(
    val label: String,
    val count: Int,
    val sizeBytes: Long,
    val songKeys: List<String> = emptyList()
)

internal data class SongWithInfo(
    val song: Song,
    val info: AudioInfo
)

internal data class MonthlyListeningReport(
    val monthTitle: String,
    val playCount: Int,
    val uniqueSongCount: Int,
    val listenedMs: Long,
    val activeDays: Int,
    val elapsedDaysInMonth: Int,
    val longestStreakDays: Int,
    val peakTimeLabelRes: Int?,
    val averagePerActiveDayMs: Long,
    val favoriteArtist: ListeningInsight?,
    val favoriteSong: ListeningInsight?,
    val favoriteAlbum: ListeningInsight?
)

internal data class ListeningInsight(
    val labelRes: Int,
    val title: String,
    val subtitle: String,
    val playCount: Int,
    val song: Song?
)

internal data class TasteProfile(
    val topArtist: TasteInsight?,
    val topAlbum: TasteInsight?,
    val topGenre: TasteInsight?
)

internal data class TasteInsight(
    val labelRes: Int,
    val title: String,
    val subtitle: String,
    val listenedMs: Long,
    val playCount: Int
)

internal data class TasteAccumulator(
    val title: String,
    val subtitle: String = "",
    var listenedMs: Long = 0L,
    var playCount: Int = 0
)

internal data class ResolvedHistoryEntry(
    val entry: PlaybackHistoryEntry,
    val song: Song?
)

internal fun buildMonthlyListeningReport(
    history: List<PlaybackHistoryEntry>,
    dailyListenMs: Map<String, Long>,
    librarySongs: List<Song>
): MonthlyListeningReport {
    val now = Calendar.getInstance()
    val monthStart = Calendar.getInstance().apply {
        set(Calendar.YEAR, now.get(Calendar.YEAR))
        set(Calendar.MONTH, now.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val nextMonthStart = (monthStart.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
    val monthStartMs = monthStart.timeInMillis
    val nextMonthStartMs = nextMonthStart.timeInMillis
    val monthPrefix = "%04d-%02d-".format(
        monthStart.get(Calendar.YEAR),
        monthStart.get(Calendar.MONTH) + 1
    )
    val monthTitle = SimpleDateFormat("yyyy MMMM", Locale.getDefault()).format(monthStart.time)

    val libraryById = librarySongs.associateBy { it.id }
    val libraryByStatsKey = librarySongs.associateBy { it.analyticsStatsKey() }
    val monthlyHistory = history
        .filter { it.playedAt >= monthStartMs && it.playedAt < nextMonthStartMs }
        .map { entry ->
            ResolvedHistoryEntry(
                entry = entry,
                song = libraryById[entry.songId] ?: libraryByStatsKey[entry.analyticsStatsKey()]
            )
        }

    val uniqueSongCount = monthlyHistory
        .map { row -> row.song?.analyticsStatsKey() ?: row.entry.analyticsStatsKey() }
        .distinct()
        .size
    val listenedMs = dailyListenMs
        .filterKeys { it.startsWith(monthPrefix) }
        .values
        .sum()
    val activeDays = dailyListenMs.count { (date, ms) -> date.startsWith(monthPrefix) && ms > 0L }
    val activeDateKeys = dailyListenMs
        .filter { (date, ms) -> date.startsWith(monthPrefix) && ms > 0L }
        .keys
        .toSet()
    val elapsedDaysInMonth = now.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)

    return MonthlyListeningReport(
        monthTitle = monthTitle,
        playCount = monthlyHistory.size,
        uniqueSongCount = uniqueSongCount,
        listenedMs = listenedMs,
        activeDays = activeDays,
        elapsedDaysInMonth = elapsedDaysInMonth,
        longestStreakDays = calculateLongestStreak(monthStart, elapsedDaysInMonth, activeDateKeys),
        peakTimeLabelRes = monthlyHistory.peakListeningTimeLabelRes(),
        averagePerActiveDayMs = if (activeDays > 0) listenedMs / activeDays else 0L,
        favoriteArtist = monthlyHistory.favoriteArtistInsight(),
        favoriteSong = monthlyHistory.favoriteSongInsight(),
        favoriteAlbum = monthlyHistory.favoriteAlbumInsight()
    )
}

internal fun List<ResolvedHistoryEntry>.favoriteArtistInsight(): ListeningInsight? {
    val rows = flatMap { row ->
        val artistText = row.song?.artist?.takeIf { it.isNotBlank() } ?: row.entry.artist
        splitArtistNames(artistText)
            .ifEmpty { listOf(artistText.trim()) }
            .filter { it.isNotBlank() }
            .map { artist -> artist to row }
    }
    val top = rows
        .groupBy { it.first.lowercase(Locale.getDefault()) }
        .maxByOrNull { it.value.size }
        ?.value
        .orEmpty()
    if (top.isEmpty()) return null
    return ListeningInsight(
        labelRes = R.string.analytics_month_favorite_artist,
        title = top.first().first,
        subtitle = top.firstNotNullOfOrNull { it.second.song?.album?.takeIf(String::isNotBlank) }.orEmpty(),
        playCount = top.size,
        song = top.firstNotNullOfOrNull { it.second.song }
    )
}

internal fun List<ResolvedHistoryEntry>.favoriteSongInsight(): ListeningInsight? {
    val top = groupBy { it.song?.analyticsStatsKey() ?: it.entry.analyticsStatsKey() }
        .maxByOrNull { it.value.size }
        ?.value
        .orEmpty()
    if (top.isEmpty()) return null
    val first = top.first()
    return ListeningInsight(
        labelRes = R.string.analytics_month_favorite_song,
        title = first.song?.title ?: first.entry.title,
        subtitle = first.song?.artist ?: first.entry.artist,
        playCount = top.size,
        song = first.song
    )
}

internal fun List<ResolvedHistoryEntry>.favoriteAlbumInsight(): ListeningInsight? {
    val top = filter { row -> (row.song?.album ?: row.entry.album).isNotBlank() }
        .groupBy { row -> (row.song?.album ?: row.entry.album).trim().lowercase(Locale.getDefault()) }
        .maxByOrNull { it.value.size }
        ?.value
        .orEmpty()
    if (top.isEmpty()) return null
    val first = top.first()
    return ListeningInsight(
        labelRes = R.string.analytics_month_favorite_album,
        title = first.song?.album ?: first.entry.album,
        subtitle = first.song?.albumArtist?.takeIf { it.isNotBlank() } ?: first.song?.artist ?: first.entry.artist,
        playCount = top.size,
        song = first.song
    )
}

internal fun calculateLongestStreak(
    monthStart: Calendar,
    elapsedDaysInMonth: Int,
    activeDateKeys: Set<String>
): Int {
    val cursor = (monthStart.clone() as Calendar)
    var current = 0
    var longest = 0
    repeat(elapsedDaysInMonth.coerceAtLeast(1)) {
        val key = "%04d-%02d-%02d".format(
            cursor.get(Calendar.YEAR),
            cursor.get(Calendar.MONTH) + 1,
            cursor.get(Calendar.DAY_OF_MONTH)
        )
        if (key in activeDateKeys) {
            current += 1
            longest = maxOf(longest, current)
        } else {
            current = 0
        }
        cursor.add(Calendar.DAY_OF_MONTH, 1)
    }
    return longest
}

internal fun List<ResolvedHistoryEntry>.peakListeningTimeLabelRes(): Int? {
    if (isEmpty()) return null
    val buckets = IntArray(4)
    forEach { row ->
        val hour = Calendar.getInstance().apply { timeInMillis = row.entry.playedAt }.get(Calendar.HOUR_OF_DAY)
        val index = when (hour) {
            in 0..5 -> 0
            in 6..11 -> 1
            in 12..17 -> 2
            else -> 3
        }
        buckets[index] += 1
    }
    val topIndex = buckets.indices.maxByOrNull { buckets[it] } ?: return null
    if (buckets[topIndex] <= 0) return null
    return when (topIndex) {
        0 -> R.string.analytics_time_midnight
        1 -> R.string.analytics_time_morning
        2 -> R.string.analytics_time_afternoon
        else -> R.string.analytics_time_evening
    }
}

internal fun buildTasteProfile(
    stats: List<SongPlaybackStats>,
    libraryById: Map<Long, Song>,
    libraryByStatsKey: Map<String, Song>
): TasteProfile {
    val resolved = stats
        .filter { it.listenedMs > 0L || it.playCount > 0 }
        .map { stat -> stat to (libraryById[stat.songId] ?: libraryByStatsKey[stat.analyticsStatsKey()]) }

    fun add(acc: MutableMap<String, TasteAccumulator>, rawTitle: String, subtitle: String, stat: SongPlaybackStats) {
        val title = rawTitle.trim()
        if (title.isBlank()) return
        val key = title.lowercase(Locale.getDefault())
        val item = acc.getOrPut(key) { TasteAccumulator(title = title, subtitle = subtitle) }
        item.listenedMs += stat.listenedMs
        item.playCount += stat.playCount
    }

    val artists = linkedMapOf<String, TasteAccumulator>()
    val albums = linkedMapOf<String, TasteAccumulator>()
    val genres = linkedMapOf<String, TasteAccumulator>()

    resolved.forEach { (stat, song) ->
        val artistText = song?.artist?.takeIf { it.isNotBlank() } ?: stat.artist
        splitArtistNames(artistText)
            .ifEmpty { listOf(artistText) }
            .forEach { artist -> add(artists, artist, song?.album.orEmpty(), stat) }

        val album = song?.album?.takeIf { it.isNotBlank() } ?: stat.album
        add(albums, album, song?.albumArtist?.takeIf { it.isNotBlank() } ?: artistText, stat)

        splitGenreNames(song?.genre.orEmpty()).forEach { genre -> add(genres, genre, "", stat) }
    }

    fun top(labelRes: Int, acc: Map<String, TasteAccumulator>): TasteInsight? {
        val item = acc.values.maxWithOrNull(
            compareBy<TasteAccumulator> { it.listenedMs }
                .thenBy { it.playCount }
        ) ?: return null
        return TasteInsight(
            labelRes = labelRes,
            title = item.title,
            subtitle = item.subtitle,
            listenedMs = item.listenedMs,
            playCount = item.playCount
        )
    }

    return TasteProfile(
        topArtist = top(R.string.analytics_taste_top_artist, artists),
        topAlbum = top(R.string.analytics_taste_top_album, albums),
        topGenre = top(R.string.analytics_taste_top_genre, genres)
    )
}

internal fun buildLibraryAnalysis(
    songs: List<Song>,
    mainViewModel: MainViewModel
): LibraryAnalysis {
    val rows = songs.map { song -> SongWithInfo(song, mainViewModel.getAudioInfo(song)) }
    return LibraryAnalysis(
        formatBuckets = rows.toBuckets { formatLabel(it.song, it.info) },
        qualityBuckets = rows.toBuckets { qualityLabel(it.song, it.info) }
            .sortedWith(compareBy<AnalysisBucket> { qualityOrder.indexOf(it.label).let { index -> if (index < 0) Int.MAX_VALUE else index } }
                .thenByDescending { it.count }),
        totalCount = songs.size,
        totalSizeBytes = songs.sumOf { it.fileSize }
    )
}

internal fun readCachedLibraryAnalysis(
    context: Context,
    songs: List<Song>
): LibraryAnalysis? {
    if (songs.isEmpty()) return LibraryAnalysis(emptyList(), emptyList(), 0, 0L)
    val cacheKey = songs.libraryAnalysisCacheKey()
    LibraryAnalysisSessionCache.get(cacheKey)?.let { return it }
    return runCatching {
        val file = libraryAnalysisCacheFile(context)
        if (!file.exists()) return@runCatching null
        val root = JSONObject(file.readText())
        if (root.optInt("version", 1) < 2) return@runCatching null
        if (root.optString("key") != cacheKey) return@runCatching null
        root.optJSONObject("analysis")?.toLibraryAnalysis()
    }.getOrNull()?.also { LibraryAnalysisSessionCache.put(cacheKey, it) }
}

internal fun writeCachedLibraryAnalysis(
    context: Context,
    songs: List<Song>,
    analysis: LibraryAnalysis
) {
    runCatching {
        val file = libraryAnalysisCacheFile(context)
        val root = JSONObject()
            .put("version", 2)
            .put("key", songs.libraryAnalysisCacheKey())
            .put("updatedAt", System.currentTimeMillis())
            .put("analysis", analysis.toJson())
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
        LibraryAnalysisSessionCache.put(songs.libraryAnalysisCacheKey(), analysis)
    }
}

internal fun prewarmLibraryAnalysisCache(
    context: Context,
    songs: List<Song>,
    mainViewModel: MainViewModel
) {
    if (songs.isEmpty() || readCachedLibraryAnalysis(context, songs) != null) return
    val analysis = buildLibraryAnalysis(songs, mainViewModel)
    writeCachedLibraryAnalysis(context, songs, analysis)
}

private fun libraryAnalysisCacheFile(context: Context): File =
    File(context.applicationContext.filesDir, "library_analysis_cache.json")

internal object LibraryAnalysisSessionCache {
    @Volatile private var key: String? = null
    @Volatile private var analysis: LibraryAnalysis? = null

    fun get(cacheKey: String): LibraryAnalysis? = analysis.takeIf { key == cacheKey }

    fun put(cacheKey: String, value: LibraryAnalysis) {
        key = cacheKey
        analysis = value
    }
}

internal fun List<Song>.libraryAnalysisCacheKey(): String {
    var idHash = 1125899906842597L
    var modifiedHash = 1469598103934665603L
    var sizeSum = 0L
    for (song in this) {
        idHash = 31L * idHash + song.id
        modifiedHash = 1099511628211L * (modifiedHash xor song.dateModified)
        sizeSum += song.fileSize
    }
    return "$size-$idHash-$modifiedHash-$sizeSum"
}

private fun LibraryAnalysis.toJson(): JSONObject =
    JSONObject()
        .put("formatBuckets", formatBuckets.toJson())
        .put("qualityBuckets", qualityBuckets.toJson())
        .put("totalCount", totalCount)
        .put("totalSizeBytes", totalSizeBytes)

private fun List<AnalysisBucket>.toJson(): JSONArray =
    JSONArray().also { array ->
        forEach { bucket ->
            array.put(
                JSONObject()
                    .put("label", bucket.label)
                    .put("count", bucket.count)
                    .put("sizeBytes", bucket.sizeBytes)
                    .put("songKeys", JSONArray(bucket.songKeys))
            )
        }
    }

private fun JSONObject.toLibraryAnalysis(): LibraryAnalysis =
    LibraryAnalysis(
        formatBuckets = optJSONArray("formatBuckets").toAnalysisBuckets(),
        qualityBuckets = optJSONArray("qualityBuckets").toAnalysisBuckets(),
        totalCount = optInt("totalCount", 0),
        totalSizeBytes = optLong("totalSizeBytes", 0L)
    )

private fun JSONArray?.toAnalysisBuckets(): List<AnalysisBucket> {
    if (this == null) return emptyList()
    return List(length()) { index ->
        val obj = optJSONObject(index) ?: JSONObject()
        AnalysisBucket(
            label = obj.optString("label"),
            count = obj.optInt("count", 0),
            sizeBytes = obj.optLong("sizeBytes", 0L),
            songKeys = obj.optJSONArray("songKeys").toStringList()
        )
    }.filter { it.label.isNotBlank() }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return List(length()) { index -> optString(index) }.filter { it.isNotBlank() }
}

internal fun LibraryAnalysis.songsForBucket(
    songs: List<Song>,
    quality: Boolean,
    label: String
): List<Song> {
    val keys = (if (quality) qualityBuckets else formatBuckets)
        .firstOrNull { it.label == label }?.songKeys?.toSet().orEmpty()
    if (keys.isEmpty()) return emptyList()
    return songs.filter { it.searchIdentityKey() in keys }
}

internal fun List<SongWithInfo>.toBuckets(labelOf: (SongWithInfo) -> String): List<AnalysisBucket> {
    return groupBy(labelOf)
        .map { (label, rows) ->
            AnalysisBucket(
                label = label,
                count = rows.size,
                sizeBytes = rows.sumOf { it.song.fileSize },
                songKeys = rows.map { it.song.searchIdentityKey() }
            )
        }
        .sortedByDescending { it.count }
}

internal fun formatLabel(song: Song, info: AudioInfo): String {
    val parsedFormat = normalizedAudioFormat(info.format)
    if (parsedFormat in setOf("ALAC", "AAC", "AC3", "EC3", "EAC3", "AC4")) return parsedFormat
    val extension = song.fileExtension()
    return when {
        extension == "mp3" -> "MP3"
        extension == "m4a" || extension == "mp4" -> "M4A"
        extension == "flac" -> "FLAC"
        extension == "wav" || extension == "wave" -> "WAV"
        extension == "ogg" -> "OGG"
        extension == "opus" -> "OPUS"
        extension == "aac" -> "AAC"
        info.format.equals("ALAC/M4A", ignoreCase = true) -> "ALAC"
        info.format.isNotBlank() -> info.format.uppercase()
        else -> "OTHER"
    }
}

internal fun qualityLabel(song: Song, info: AudioInfo): String {
    val label = audioQualitySummary(info).analyticsLabel
    return when {
        label == "未知" && !song.mimeType.contains("audio", ignoreCase = true) -> "OTHER"
        label == "未知" -> "UNKNOWN"
        label == "无损" -> "LOSSLESS"
        else -> label
    }
}

internal fun Song.fileExtension(): String {
    val source = fileName.ifBlank { path.substringAfterLast('/') }
    return source.substringAfterLast('.', missingDelimiterValue = "").lowercase()
}

internal fun formatListenDuration(context: Context, ms: Long): String {
    val totalMinutes = (ms / 60_000).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> context.getString(R.string.analytics_duration_hours_minutes, hours, minutes)
        minutes > 0 -> context.getString(R.string.analytics_duration_minutes, minutes)
        else -> context.getString(R.string.analytics_duration_less_than_minute)
    }
}

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024.0) {
        "%.1f GB".format(mb / 1024.0)
    } else {
        "%.1f MB".format(mb)
    }
}

internal fun formatPercent(percent: Float): String {
    return if (percent < 1f && percent > 0f) "<1%" else "${percent.toInt()}%"
}

internal fun recentDateKeys(days: Int): List<String> {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -(days - 1))
    return List(days) {
        val key = "%04d-%02d-%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        key
    }
}

internal fun heatmapColor(listenedMs: Long, maxMs: Long): Color {
    if (listenedMs <= 0L) return Color(0x1F8E8E8E)
    val level = (listenedMs.toFloat() / maxMs.toFloat()).coerceIn(0.12f, 1f)
    return Color(
        red = 0.18f + 0.05f * level,
        green = 0.48f + 0.34f * level,
        blue = 0.84f - 0.36f * level,
        alpha = 0.40f + 0.56f * level
    )
}

internal fun formatHistoryTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestampMs }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val sameDay = sameYear &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = when {
        sameDay -> "HH:mm"
        sameYear -> "MM-dd HH:mm"
        else -> "yyyy-MM-dd"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(then.time)
}

private fun historyDateKey(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampMs))
}

internal fun formatHistoryDateChip(dateKey: String): String {
    val date = parseHistoryDateKey(dateKey) ?: return dateKey
    val then = Calendar.getInstance().apply { time = date }
    return "%02d-%02d".format(
        then.get(Calendar.MONTH) + 1,
        then.get(Calendar.DAY_OF_MONTH)
    )
}

private fun parseHistoryDateKey(dateKey: String): Date? {
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateKey)
    }.getOrNull()
}

internal fun Song.analyticsStatsKey(): String =
    listOf(title, artist, album).joinToString("|") { it.analyticsKeyPart() }

internal fun PlaybackHistoryEntry.analyticsStatsKey(): String =
    listOf(title, artist, album).joinToString("|") { it.analyticsKeyPart() }

internal fun SongPlaybackStats.analyticsStatsKey(): String =
    listOf(title, artist, album).joinToString("|") { it.analyticsKeyPart() }

internal fun String.analyticsKeyPart(): String =
    trim().lowercase().replace(Regex("\\s+"), " ")

internal val qualityOrder = listOf("AC3", "EC3", "EAC3", "AC4", "Surround", "MQ", "Hi-Res", "LOSSLESS", "HQ", "LQ", "UNKNOWN", "OTHER")

internal val formatPalette = listOf(
    Color(0xFF4C6F9F),
    Color(0xFFA9B98D),
    Color(0xFFD9B99E),
    Color(0xFFC13561),
    Color(0xFFB97784),
    Color(0xFF6C89C8),
    Color(0xFF8E8E8E)
)

internal val qualityPalette = listOf(
    Color(0xFF2FD8FF),
    Color(0xFFE95D38),
    Color(0xFFFFA21A),
    Color(0xFF9A3AC7),
    Color(0xFF2E6BFF),
    Color(0xFF17B55E),
    Color(0xFF8E8E8E),
    Color(0xFFB0A08F)
)

internal fun qualityBucketColor(label: String): Color = when (label.uppercase()) {
    "LQ" -> Color(0xFF17B55E)
    "HQ" -> Color(0xFF2E6BFF)
    "LOSSLESS" -> Color(0xFF9A3AC7)
    "HI-RES" -> Color(0xFFFFA21A)
    else -> qualityPalette[
        qualityOrder.indexOf(label).takeIf { it >= 0 }?.rem(qualityPalette.size) ?: 6
    ]
}
