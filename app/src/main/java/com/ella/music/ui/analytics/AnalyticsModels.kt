package com.ella.music.ui.analytics

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.ella.music.R
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.SongPlaybackStats
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.artistNamesForSong
import com.ella.music.data.normalizedAudioFormat
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId
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
enum class AnalysisDimension(val labelRes: Int) {
    FORMAT(R.string.analytics_dimension_format),
    QUALITY(R.string.analytics_dimension_quality),
    SAMPLE_RATE(R.string.analytics_dimension_sample_rate),
    BIT_DEPTH(R.string.analytics_dimension_bit_depth)
}

enum class AnalysisMetric(val labelRes: Int) {
    SIZE(R.string.analytics_metric_size),
    COUNT(R.string.analytics_metric_count)
}

internal data class LibraryAnalysis(
    val formatBuckets: List<AnalysisBucket>,
    val qualityBuckets: List<AnalysisBucket>,
    val sampleRateBuckets: List<AnalysisBucket> = emptyList(),
    val bitDepthBuckets: List<AnalysisBucket> = emptyList(),
    val totalCount: Int,
    val totalSizeBytes: Long
) {
    fun getBuckets(dimension: AnalysisDimension): List<AnalysisBucket> = when (dimension) {
        AnalysisDimension.FORMAT -> formatBuckets
        AnalysisDimension.QUALITY -> qualityBuckets
        AnalysisDimension.SAMPLE_RATE -> sampleRateBuckets
        AnalysisDimension.BIT_DEPTH -> bitDepthBuckets
    }
}

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
    val favoriteAlbum: ListeningInsight?,
    val monthLabel: String = monthTitle,
    val year: Int = 0,
    val favoriteArtists: List<ListeningInsight> = emptyList()
)

internal data class ReplayMonthTab(
    val offsetFromCurrent: Int,
    val label: String,
    val year: Int
)

internal data class ListeningInsight(
    val labelRes: Int,
    val title: String,
    val subtitle: String,
    val playCount: Int,
    val song: Song?,
    val listenedMs: Long = 0L
)

internal data class TasteProfile(
    val topArtists: List<TasteInsight>,
    val topAlbums: List<TasteInsight>,
    val topGenres: List<TasteInsight>
)

internal data class TasteInsight(
    val labelRes: Int,
    val title: String,
    val subtitle: String,
    val listenedMs: Long,
    val playCount: Int,
    /** Album navigation target when this insight is an album (#698). */
    val albumId: Long? = null
)

internal data class TasteAccumulator(
    val title: String,
    val subtitle: String = "",
    var listenedMs: Long = 0L,
    var playCount: Int = 0,
    var albumId: Long? = null
)

internal data class ResolvedHistoryEntry(
    val entry: PlaybackHistoryEntry,
    val song: Song?
)

internal fun buildMonthlyListeningReport(
    history: List<PlaybackHistoryEntry>,
    dailyListenMs: Map<String, Long>,
    librarySongs: List<Song>,
    targetMonth: Calendar = Calendar.getInstance()
): MonthlyListeningReport {
    val now = Calendar.getInstance()
    val monthStart = (targetMonth.clone() as Calendar).apply {
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
    val monthLabelPattern = if (Locale.getDefault().language in setOf("zh", "ja", "ko")) {
        "M月"
    } else {
        "MMMM"
    }
    val monthLabel = SimpleDateFormat(monthLabelPattern, Locale.getDefault()).format(monthStart.time)

    val libraryById = librarySongs.associateBy { it.id }
    val libraryByStatsKey = librarySongs.associateBy { it.analyticsStatsKey() }
    val monthlyHistory = history
        .filter { it.playedAt >= monthStartMs && it.playedAt < nextMonthStartMs }
        .map { entry ->
            ResolvedHistoryEntry(
                entry = entry,
                song = libraryByStatsKey[entry.analyticsStatsKey()]
                    ?: libraryById[entry.songId]
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
    val isCurrentMonth = monthStart.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        monthStart.get(Calendar.MONTH) == now.get(Calendar.MONTH)
    val elapsedDaysInMonth = if (isCurrentMonth) {
        now.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
    } else {
        monthStart.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    return MonthlyListeningReport(
        monthTitle = monthTitle,
        monthLabel = monthLabel,
        year = monthStart.get(Calendar.YEAR),
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
        favoriteAlbum = monthlyHistory.favoriteAlbumInsight(),
        favoriteArtists = monthlyHistory.favoriteArtistInsights()
    )
}

internal fun buildReplayMonthTabs(
    count: Int? = null,
    now: Calendar = Calendar.getInstance(),
    year: Int = now.get(Calendar.YEAR)
): List<ReplayMonthTab> {
    if (year !in 1..now.get(Calendar.YEAR)) return emptyList()
    val lastMonth = if (year == now.get(Calendar.YEAR)) now.get(Calendar.MONTH) else Calendar.DECEMBER
    val resolvedCount = (count ?: (lastMonth + 1)).coerceAtMost(lastMonth + 1)
    if (resolvedCount <= 0) return emptyList()
    val locale = Locale.getDefault()
    val labelPattern = if (locale.language in setOf("zh", "ja", "ko")) "M月" else "MMM"
    return (lastMonth - resolvedCount + 1..lastMonth).map { index ->
        val offset = replayMonthOffset(year, index, now)
        val month = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -offset)
        }
        ReplayMonthTab(
            offsetFromCurrent = offset,
            label = SimpleDateFormat(labelPattern, locale).format(month.time),
            year = month.get(Calendar.YEAR)
        )
    }
}

/** Calendar years are derived from listening dates, including imports with no library match. */
internal fun buildReplayYears(
    history: List<PlaybackHistoryEntry>, dailyListenMs: Map<String, Long>,
    now: Calendar = Calendar.getInstance()
): List<Int> {
    val currentYear = now.get(Calendar.YEAR)
    val calendar = now.clone() as Calendar
    return buildSet {
        add(currentYear)
        history.filter { it.playedAt > 0L }.forEach {
            calendar.timeInMillis = it.playedAt
            calendar.get(Calendar.YEAR).takeIf { year -> year in 1..currentYear }?.let(::add)
        }
        dailyListenMs.filterValues { it > 0L }.keys.forEach { date ->
            runCatching { java.time.LocalDate.parse(date).year }.getOrNull()
                ?.takeIf { it in 1..currentYear }?.let(::add)
        }
    }.sortedDescending()
}

internal fun replayMonthOffset(year: Int, month: Int, now: Calendar): Int {
    val currentYear = now.get(Calendar.YEAR)
    val safeYear = year.coerceIn(1, currentYear)
    val lastMonth = if (safeYear == currentYear) now.get(Calendar.MONTH) else Calendar.DECEMBER
    return (currentYear - safeYear) * 12 + now.get(Calendar.MONTH) - month.coerceIn(0, lastMonth)
}

internal fun List<ResolvedHistoryEntry>.favoriteArtistInsight(): ListeningInsight? {
    return favoriteArtistInsights(limit = 1).firstOrNull()
}

internal fun List<ResolvedHistoryEntry>.favoriteArtistInsights(
    limit: Int = 5
): List<ListeningInsight> {
    if (limit <= 0) return emptyList()
    val rows = flatMap { row ->
        val artistText = row.song?.artist?.takeIf { it.isNotBlank() } ?: row.entry.artist
        (row.song?.let(::artistNamesForSong) ?: splitArtistNames(artistText))
            .ifEmpty { listOf(artistText.trim()) }
            .filter { it.isNotBlank() }
            .map { artist -> artist to row }
    }
    val grouped = rows
        .groupBy { it.first.lowercase(Locale.getDefault()) }
        .values
        .sortedWith(
            compareByDescending<List<Pair<String, ResolvedHistoryEntry>>> {
                it.sumOf { row -> row.second.entry.listenedMs.coerceAtLeast(0L) }
            }
                .thenByDescending { it.size }
                .thenBy { it.firstOrNull()?.first.orEmpty().lowercase(Locale.getDefault()) }
        )
    return grouped.take(limit).map { artistRows ->
        ListeningInsight(
            labelRes = R.string.analytics_month_favorite_artist,
            title = artistRows.first().first,
            subtitle = artistRows.firstNotNullOfOrNull {
                it.second.song?.album?.takeIf(String::isNotBlank)
            }.orEmpty(),
            playCount = artistRows.size,
            song = artistRows.firstNotNullOfOrNull { it.second.song },
            listenedMs = artistRows.sumOf { it.second.entry.listenedMs.coerceAtLeast(0L) }
        )
    }
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
        song = first.song,
        listenedMs = top.sumOf { it.entry.listenedMs.coerceAtLeast(0L) }
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
        subtitle = first.song?.albumArtist?.takeIf { it.isNotBlank() }.orEmpty(),
        playCount = top.size,
        song = first.song,
        listenedMs = top.sumOf { it.entry.listenedMs.coerceAtLeast(0L) }
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
        .map { stat ->
            stat to (
                libraryByStatsKey[stat.analyticsStatsKey()]
                    ?: libraryById[stat.songId]
                )
        }

    fun add(
        acc: MutableMap<String, TasteAccumulator>,
        rawTitle: String,
        subtitle: String,
        stat: SongPlaybackStats,
        albumId: Long? = null
    ) {
        val title = rawTitle.trim()
        if (title.isBlank()) return
        val key = title.lowercase(Locale.getDefault())
        val item = acc.getOrPut(key) { TasteAccumulator(title = title, subtitle = subtitle, albumId = albumId) }
        item.listenedMs += stat.listenedMs
        item.playCount += stat.playCount
        if (item.albumId == null) item.albumId = albumId
    }

    val artists = linkedMapOf<String, TasteAccumulator>()
    val albums = linkedMapOf<String, TasteAccumulator>()
    val genres = linkedMapOf<String, TasteAccumulator>()

    resolved.forEach { (stat, song) ->
        val artistText = song?.artist?.takeIf { it.isNotBlank() } ?: stat.artist
        (song?.let(::artistNamesForSong) ?: splitArtistNames(artistText))
            .ifEmpty { listOf(artistText) }
            .forEach { artist -> add(artists, artist, song?.album.orEmpty(), stat) }

        val album = song?.album?.takeIf { it.isNotBlank() } ?: stat.album
        add(
            albums, album, song?.albumArtist?.takeIf { it.isNotBlank() }.orEmpty(), stat,
            albumId = song?.albumIdentityId()
        )

        splitGenreNames(song?.genre.orEmpty()).forEach { genre -> add(genres, genre, "", stat) }
    }

    fun topN(labelRes: Int, acc: Map<String, TasteAccumulator>, n: Int = 3): List<TasteInsight> {
        return acc.values.sortedWith(
            compareByDescending<TasteAccumulator> { it.listenedMs }
                .thenByDescending { it.playCount }
        ).take(n).map { item ->
            TasteInsight(
                labelRes = labelRes,
                title = item.title,
                subtitle = item.subtitle,
                listenedMs = item.listenedMs,
                playCount = item.playCount,
                albumId = item.albumId
            )
        }
    }

    return TasteProfile(
        topArtists = topN(R.string.analytics_taste_top_artist, artists),
        topAlbums = topN(R.string.analytics_taste_top_album, albums),
        topGenres = topN(R.string.analytics_taste_top_genre, genres)
    )
}

/**
 * Time ranges for the ranking cards/page (#698, extended in #781). Picker order is the
 * declaration order: 全部时间 first, then the calendar-bounded "今" family, then the
 * rolling "最近" family.
 */
internal enum class AnalyticsRankingRange(val prefValue: String, val labelRes: Int) {
    All("all", R.string.analytics_range_all),
    /** Current calendar month (1st → today). */
    CurMonth("cm", R.string.analytics_range_cur_month),
    /** Current calendar month plus the two previous months. */
    CurMonth3("c3m", R.string.analytics_range_cur_month3),
    /** Current calendar quarter (months 1-3, 4-6, 7-9 or 10-12). */
    CurQuarter("cq", R.string.analytics_range_cur_quarter),
    /** Current calendar half-year (months 1-6 or 7-12). */
    CurHalf("ch", R.string.analytics_range_cur_half),
    /** Current calendar year. */
    CurYear("cy", R.string.analytics_range_cur_year),
    Week("7d", R.string.analytics_range_7d),
    Month1("1m", R.string.analytics_range_1m),
    Month3("3m", R.string.analytics_range_3m),
    Month6("6m", R.string.analytics_range_6m),
    Year1("1y", R.string.analytics_range_1y);

    fun cutoffMs(now: Long): Long {
        if (this == All) return 0L
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        when (this) {
            Week -> cal.add(Calendar.DAY_OF_YEAR, -7)
            Month1 -> cal.add(Calendar.MONTH, -1)
            Month3 -> cal.add(Calendar.MONTH, -3)
            Month6 -> cal.add(Calendar.MONTH, -6)
            Year1 -> cal.add(Calendar.YEAR, -1)
            CurMonth -> cal.moveToPeriodStart()
            CurMonth3 -> {
                cal.add(Calendar.MONTH, -2)
                cal.moveToPeriodStart()
            }
            CurQuarter -> {
                cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) / 3 * 3)
                cal.moveToPeriodStart()
            }
            CurHalf -> {
                cal.set(Calendar.MONTH, if (cal.get(Calendar.MONTH) < Calendar.JULY) Calendar.JANUARY else Calendar.JULY)
                cal.moveToPeriodStart()
            }
            CurYear -> {
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.moveToPeriodStart()
            }
            All -> Unit
        }
        return cal.timeInMillis
    }

    /** Moves to 00:00:00.000 on the first day of the currently-set month. */
    private fun Calendar.moveToPeriodStart() {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    companion object {
        fun fromPref(value: String?): AnalyticsRankingRange =
            entries.firstOrNull { it.prefValue == value } ?: All
    }
}

/**
 * Aggregates per-play history into `SongPlaybackStats`-compatible rows over a bounded range so
 * the ranking cards/page can be scoped by time (#698). Grouped by the same stats key the cards
 * resolve against, so remote/Last.fm entries merge correctly. `duration` sums listenedMs of
 * every entry; `count` only counts entries that met the play-count threshold.
 */
internal fun buildRangeRankingStats(
    history: List<PlaybackHistoryEntry>,
    range: AnalyticsRankingRange,
    kind: String,
    now: Long = System.currentTimeMillis()
): List<SongPlaybackStats> {
    val cutoff = range.cutoffMs(now)
    return history
        .asSequence()
        .filter { it.playedAt >= cutoff }
        .groupBy { it.analyticsStatsKey() }
        .values
        .map { entries ->
            val first = entries.first()
            SongPlaybackStats(
                songId = first.songId,
                title = first.title,
                artist = first.artist,
                album = first.album,
                playCount = entries.count { it.playCounted },
                listenedMs = entries.sumOf { it.listenedMs },
                lastPlayedAt = entries.maxOf { it.playedAt }
            )
        }
        .filter { if (kind == "count") it.playCount > 0 else it.listenedMs > 0 }
        .let { list ->
            if (kind == "count") list.sortedByDescending { it.playCount }
            else list.sortedByDescending { it.listenedMs }
        }
        .toList()
}

/**
 * Resolves history rows back to a library [Song] even after the recorded song went stale
 * (#781): retag/rescan changes title/artist/album or reuses the MediaStore id, leaving
 * dead entries in the rankings. Match order keeps the strongest identity first —
 * normalized title+artist+album stats key, online source id, the original file name
 * (from `mediaUri`, or the tag-less title which stores the file name), and finally the
 * raw song id as a last resort. Unmatchable rows return null and keep their recorded
 * title/artist as a fallback display.
 */
internal class HistorySongResolver(songs: List<Song>) {
    private val byId = songs.associateBy { it.id }
    private val byStatsKey = songs.associateBy { it.analyticsStatsKey() }
    private val byOnlineKey = songs
        .asSequence()
        .filter { it.onlineSource.isNotBlank() && it.onlineId.isNotBlank() }
        .associateBy { "${it.onlineSource}|${it.onlineId}" }
    private val byFileName = songs
        .asSequence()
        .mapNotNull { song -> song.libraryFileName().historyFileKey()?.let { it to song } }
        .toMap()
    private val byFileStem = songs
        .asSequence()
        .mapNotNull { song ->
            song.libraryFileName().substringBeforeLast('.').historyFileKey()?.let { it to song }
        }
        .toMap()

    fun resolve(stat: SongPlaybackStats): Song? =
        byStatsKey[stat.analyticsStatsKey()] ?: byId[stat.songId]

    fun resolve(entry: PlaybackHistoryEntry): Song? =
        byStatsKey[entry.analyticsStatsKey()]
            ?: entry.onlineMatchKey()?.let(byOnlineKey::get)
            ?: entry.fileNameCandidates().firstNotNullOfOrNull { raw ->
                val key = raw.historyFileKey() ?: return@firstNotNullOfOrNull null
                byFileName[key] ?: byFileStem[key]
            }
            ?: byId[entry.songId]

    private fun Song.libraryFileName(): String =
        fileName.ifBlank { path.substringAfterLast('/') }

    private fun PlaybackHistoryEntry.onlineMatchKey(): String? =
        if (onlineSource.isNotBlank() && onlineId.isNotBlank()) "$onlineSource|$onlineId" else null

    private fun PlaybackHistoryEntry.fileNameCandidates(): List<String> = buildList {
        // Local rows can carry the original path/uri; its last segment is the file name.
        mediaUri.substringAfterLast('/').substringBefore('?')
            .takeIf { it.isNotBlank() && it != mediaUri }
            ?.let { add(android.net.Uri.decode(it)) }
        // Tag-less local files keep the file name (with or without extension) as the title.
        if (title.isNotBlank()) add(title)
        if (title.contains('.')) add(title.substringBeforeLast('.'))
    }

    private fun String?.historyFileKey(): String? =
        this?.trim()?.lowercase(Locale.getDefault())?.replace(Regex("\\s+"), " ")
            ?.takeIf { it.isNotBlank() }
}

/**
 * Maps each stats key to the song resolved from its most recent history entry, so ranking
 * rows keep playing after the referenced song was retagged or re-added (#781).
 */
internal fun buildStatsSongMap(
    history: List<PlaybackHistoryEntry>,
    resolver: HistorySongResolver
): Map<String, Song> = history
    .groupBy { it.analyticsStatsKey() }
    .mapNotNull { (key, entries) ->
        entries.maxByOrNull { it.playedAt }
            ?.let(resolver::resolve)
            ?.let { key to it }
    }
    .toMap()

internal suspend fun buildLibraryAnalysis(
    songs: List<Song>,
    mainViewModel: MainViewModel
): LibraryAnalysis {
    val rows = loadLibraryQualityRows(songs, mainViewModel::getAudioQualityInfo)
    return LibraryAnalysis(
        formatBuckets = rows.toBuckets { formatLabel(it.song, it.info) },
        qualityBuckets = rows.toBuckets { qualityLabel(it.song, it.info) }
            .sortedWith(compareBy<AnalysisBucket> { qualityOrder.indexOf(it.label).let { index -> if (index < 0) Int.MAX_VALUE else index } }
                .thenByDescending { it.count }),
        sampleRateBuckets = rows.toBuckets { sampleRateLabel(it.info) }
            .sortedWith(compareByDescending<AnalysisBucket> { parseSampleRateKhz(it.label) }
                .thenByDescending { it.count }),
        bitDepthBuckets = rows.toBuckets { bitDepthLabel(it.info) }
            .sortedWith(compareByDescending<AnalysisBucket> { parseBitDepth(it.label) }
                .thenByDescending { it.count }),
        totalCount = songs.size,
        totalSizeBytes = songs.sumOf { it.fileSize }
    )
}

internal fun readCachedLibraryAnalysis(
    context: Context,
    songs: List<Song>
): LibraryAnalysis? {
    if (songs.isEmpty()) return LibraryAnalysis(emptyList(), emptyList(), emptyList(), emptyList(), 0, 0L)
    val cacheKey = songs.libraryAnalysisCacheKey()
    LibraryAnalysisSessionCache.get(cacheKey)?.let { return it }
    return runCatching {
        val file = libraryAnalysisCacheFile(context)
        if (!file.exists()) return@runCatching null
        val root = JSONObject(file.readText())
        if (root.optInt("version", 1) != 4) return@runCatching null
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
            .put("version", 4)
            .put("key", songs.libraryAnalysisCacheKey())
            .put("updatedAt", System.currentTimeMillis())
            .put("analysis", analysis.toJson())
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
        LibraryAnalysisSessionCache.put(songs.libraryAnalysisCacheKey(), analysis)
    }
}

internal suspend fun prewarmLibraryAnalysisCache(
    context: Context,
    songs: List<Song>,
    mainViewModel: MainViewModel
) {
    if (songs.isNotEmpty()) getOrBuildLibraryAnalysis(context, songs, mainViewModel)
}

internal fun invalidateLibraryAnalysisCache(context: Context) {
    LibraryAnalysisSessionCache.clear()
    libraryAnalysisCacheFile(context).delete()
}

private fun libraryAnalysisCacheFile(context: Context): File =
    File(context.applicationContext.filesDir, "library_analysis_cache.json")

internal object LibraryAnalysisSessionCache {
    @Volatile private var snapshot: Pair<String, LibraryAnalysis>? = null
    fun get(cacheKey: String): LibraryAnalysis? = snapshot?.takeIf { it.first == cacheKey }?.second
    fun clear() { snapshot = null }
    fun put(cacheKey: String, value: LibraryAnalysis) { snapshot = cacheKey to value }
}

internal fun List<Song>.libraryAnalysisCacheKey(): String {
    var sum = 0L
    var xor = 0L
    for (song in this) {
        var hash = 1469598103934665603L
        fun mix(value: Long) { hash = (hash xor value) * 1099511628211L }
        mix(song.id); mix(song.dateModified); mix(song.fileSize); mix(song.duration)
        song.path.forEach { mix(it.code.toLong()) }
        mix(-1L)
        song.mimeType.forEach { mix(it.code.toLong()) }
        sum += hash
        xor = xor xor java.lang.Long.rotateLeft(hash, (song.id and 63L).toInt())
    }
    return "$size:$sum:$xor"
}

private val libraryAnalysisBuildMutex = kotlinx.coroutines.sync.Mutex()

internal suspend fun getOrBuildLibraryAnalysis(context: Context, songs: List<Song>, mainViewModel: MainViewModel): LibraryAnalysis {
    libraryAnalysisBuildMutex.lock()
    try {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            readCachedLibraryAnalysis(context, songs) ?: buildLibraryAnalysis(songs, mainViewModel).also {
                writeCachedLibraryAnalysis(context, songs, it)
            }
        }
    } finally { libraryAnalysisBuildMutex.unlock() }
}

private fun LibraryAnalysis.toJson(): JSONObject =
    JSONObject()
        .put("formatBuckets", formatBuckets.toJson())
        .put("qualityBuckets", qualityBuckets.toJson())
        .put("sampleRateBuckets", sampleRateBuckets.toJson())
        .put("bitDepthBuckets", bitDepthBuckets.toJson())
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
        sampleRateBuckets = optJSONArray("sampleRateBuckets").toAnalysisBuckets(),
        bitDepthBuckets = optJSONArray("bitDepthBuckets").toAnalysisBuckets(),
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

internal fun sampleRateLabel(info: AudioInfo): String {
    val rate = info.sampleRate
    if (rate <= 0) return "UNKNOWN"
    return if (rate % 1000 == 0) {
        "${rate / 1000} kHz"
    } else {
        "%.1f kHz".format(Locale.US, rate / 1000.0)
    }
}

internal fun bitDepthLabel(info: AudioInfo): String {
    val depth = info.bitDepth
    if (depth <= 0) return "UNKNOWN"
    return "${depth}-bit"
}

internal fun parseSampleRateKhz(label: String): Float =
    label.removeSuffix(" kHz").toFloatOrNull() ?: -1f

internal fun parseBitDepth(label: String): Int =
    label.removeSuffix("-bit").toIntOrNull() ?: -1

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

/** GitHub-contribution-style columns: seven weekday rows, with blanks before the first day. */
internal fun recentHeatmapWeeks(days: Int): List<List<String?>> {
    val keys = recentDateKeys(days)
    if (keys.isEmpty()) return emptyList()
    val first = keys.first().split('-').mapNotNull { it.toIntOrNull() }
    val leadingBlanks = if (first.size == 3) {
        Calendar.getInstance().apply { set(first[0], first[1] - 1, first[2]) }
            .get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    } else {
        0
    }
    return (List(leadingBlanks.coerceAtLeast(0)) { null } + keys).chunked(7)
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

internal val xiaomiStoragePalette = listOf(
    Color(0xFFFFB300), // Yellow / Gold
    Color(0xFF00B0FF), // Cyan / Sky Blue
    Color(0xFF3D5AFE), // Royal Blue
    Color(0xFFAA00FF), // Violet / Purple
    Color(0xFFFF5252), // Coral / Red
    Color(0xFF00E676), // Bright Green
    Color(0xFF9E9E9E)  // Slate Grey
)

internal val sampleRatePalette = listOf(
    Color(0xFFFFB300), // Amber / Gold (192 kHz+)
    Color(0xFF00B0FF), // Sky Blue (96 kHz)
    Color(0xFF3D5AFE), // Deep Blue (48 kHz)
    Color(0xFF00E676), // Green (44.1 kHz)
    Color(0xFFAA00FF), // Violet (DSD / Other)
    Color(0xFF9E9E9E)  // Grey
)

internal val bitDepthPalette = listOf(
    Color(0xFFAA00FF), // Purple (32-bit)
    Color(0xFFFFB300), // Amber (24-bit)
    Color(0xFF00B0FF), // Cyan (16-bit)
    Color(0xFF9E9E9E)  // Grey
)
