package com.ella.music.ui.analytics

import androidx.compose.ui.graphics.Color
import com.ella.music.data.DOLBY_MARK
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.model.Song
import com.ella.music.data.model.formatPlaybackDuration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal data class ListeningTimelineEntry(
    val entry: PlaybackHistoryEntry,
    val song: Song?
)

internal data class ListeningDayAggregate(
    val dateKey: String,
    val dayOfMonth: Int,
    val entries: List<ListeningTimelineEntry>,
    val playCount: Int,
    val totalListenedMs: Long,
    val uniqueSongsCount: Int,
    val representativeSong: Song?,
    val heatValue: Long,
    val maxHeatValue: Long = 1L
)

internal data class ListeningMonthSection(
    val label: String,
    val year: Int,
    val month: Int,
    val weeks: List<List<ListeningDayAggregate?>>
)

internal fun buildListeningDayAggregates(
    history: List<PlaybackHistoryEntry>,
    dailyListenMs: Map<String, Long>,
    libraryById: Map<Long, Song>,
    libraryByStatsKey: Map<String, Song>
): Map<String, ListeningDayAggregate> {
    val groupedHistory = history
        .groupBy(::historyDateKey)
        .mapValues { (_, entries) -> entries.sortedBy { it.playedAt } }
    val allDateKeys = (groupedHistory.keys + dailyListenMs.keys)
        .filter { it.isNotBlank() }
        .distinct()
        .sortedDescending()

    val rawAggregates = allDateKeys.associateWith { dateKey ->
        val entries = groupedHistory[dateKey].orEmpty().map { entry ->
            // MediaStore ids can be reused after a rescan. Match stable metadata first so an old
            // history row cannot borrow the cover (or file) of an unrelated new song with that id.
            ListeningTimelineEntry(entry, libraryByStatsKey[entry.calendarStatsKey()] ?: libraryById[entry.songId])
        }
        // The calendar is a listening-history surface. Its total must use the same actual
        // session duration shown on every history row and must never fall back to track length.
        val totalListenedMs = entries.sumOf { it.entry.listenedMs.coerceAtLeast(0L) }
        val dayOfMonth = dateKey.substringAfterLast('-').toIntOrNull() ?: 1
        val representativeSong = entries
            .groupBy { it.song?.id ?: -1L }
            .maxByOrNull { (_, rows) -> rows.size }
            ?.value
            ?.firstOrNull()
            ?.song
            ?: entries.firstOrNull()?.song
        ListeningDayAggregate(
            dateKey = dateKey,
            dayOfMonth = dayOfMonth,
            entries = entries,
            playCount = entries.size,
            totalListenedMs = totalListenedMs,
            uniqueSongsCount = entries.map { it.entry.songId }.distinct().size,
            representativeSong = representativeSong,
            heatValue = totalListenedMs
                .takeIf { it > 0L }
                ?: dailyListenMs[dateKey]?.coerceAtLeast(0L)
                ?: entries.size.toLong()
        )
    }
    val maxHeatValue = rawAggregates.values.maxOfOrNull { it.heatValue }?.coerceAtLeast(1L) ?: 1L
    return rawAggregates.mapValues { (_, day) -> day.copy(maxHeatValue = maxHeatValue) }
}

internal fun buildListeningMonths(dayAggregates: Map<String, ListeningDayAggregate>): List<ListeningMonthSection> {
    if (dayAggregates.isEmpty()) return emptyList()
    val monthKeys = dayAggregates.keys
        .mapNotNull(::yearMonthParts)
        .distinct()
        .sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })

    return monthKeys.map { (year, month) ->
        val daysInMonth = daysInMonth(year, month)
        val firstWeekdayOffset = firstWeekdayOffset(year, month)
        val cells = buildList<ListeningDayAggregate?> {
            repeat(firstWeekdayOffset) { add(null) }
            (1..daysInMonth).forEach { day ->
                val key = "%04d-%02d-%02d".format(year, month, day)
                add(dayAggregates[key] ?: ListeningDayAggregate(
                    dateKey = key,
                    dayOfMonth = day,
                    entries = emptyList(),
                    playCount = 0,
                    totalListenedMs = 0L,
                    uniqueSongsCount = 0,
                    representativeSong = null,
                    heatValue = 0L,
                    maxHeatValue = dayAggregates.values.firstOrNull()?.maxHeatValue ?: 1L
                ))
            }
            while (size % 7 != 0) add(null)
        }
        ListeningMonthSection(
            label = "%04d-%02d".format(year, month),
            year = year,
            month = month,
            weeks = cells.chunked(7)
        )
    }
}

private fun firstWeekdayOffset(year: Int, month: Int): Int {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    return calendar.get(Calendar.DAY_OF_WEEK) - 1
}

private fun daysInMonth(year: Int, month: Int): Int {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
}

private fun yearMonthParts(dateKey: String): Pair<Int, Int>? {
    val parts = dateKey.split('-')
    if (parts.size < 2) return null
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return year to month
}

internal fun listeningHeatColor(value: Long, maxValue: Long): Color {
    if (value <= 0L) return Color(0xFF7B7B7B).copy(alpha = 0.38f)
    val level = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
    return when {
        level < 0.20f -> Color(0xFFE9F4E8)
        level < 0.45f -> Color(0xFFAFE7A7)
        level < 0.75f -> Color(0xFF5FD05A)
        else -> Color(0xFF1A8815)
    }
}

internal fun qualityTagColor(tag: String): Color {
    return when (tag) {
        "HR" -> Color(0xFFFFA726)
        "HQ" -> Color(0xFF4A90FF)
        "LQ" -> Color(0xFF45D06B)
        "SQ" -> Color(0xFFD16CFF)
        "MQ" -> Color(0xFFFF6E40)
        "AC3", "EC3", "EAC3", "AC4", "Surround", DOLBY_MARK -> Color(0xFF2FD8FF)
        else -> Color.White.copy(alpha = 0.72f)
    }
}

internal fun formatCalendarDetailDate(dateKey: String): String {
    val date = parseHistoryDateKey(dateKey) ?: return dateKey
    return SimpleDateFormat("yyyy/M/d", Locale.getDefault()).format(date)
}

internal fun formatCalendarTotalListenDuration(listenedMs: Long): String {
    return listenedMs.coerceAtLeast(0L).formatPlaybackDuration()
}

internal fun formatHistoryListenDuration(listenedMs: Long): String {
    return if (listenedMs > 0L) listenedMs.formatPlaybackDuration() else "--:--"
}

internal fun formatCalendarHistoryClock(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
}

private fun historyDateKey(entry: PlaybackHistoryEntry): String = historyDateKey(entry.playedAt)

private fun historyDateKey(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampMs))
}

private fun parseHistoryDateKey(dateKey: String): Date? {
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateKey)
    }.getOrNull()
}

internal fun Song.calendarStatsKey(): String =
    listOf(title, artist, album).joinToString("|") { it.calendarKeyPart() }

private fun PlaybackHistoryEntry.calendarStatsKey(): String =
    listOf(title, artist, album).joinToString("|") { it.calendarKeyPart() }

private fun String.calendarKeyPart(): String =
    trim().lowercase().replace(Regex("\\s+"), " ")
