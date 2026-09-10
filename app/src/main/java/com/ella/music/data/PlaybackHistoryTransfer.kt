package com.ella.music.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.ella.music.data.model.Song
import com.ella.music.data.repository.mediaStoreAlbumArtUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.Mac
import javax.crypto.CipherOutputStream
import javax.crypto.spec.SecretKeySpec

/** Formats understood by the listening-history migration screen. */
enum class PlaybackHistoryTransferFormat(val extension: String) {
    HALCYON("json"),
    PRISM_MUSIC("json"),
    CONE_MUSIC("zip"),
    LUNA_BEAT("zip"),
    RAWS_MUSIC("json")
}

data class PlaybackHistoryImportResult(
    val format: PlaybackHistoryTransferFormat,
    val addedCount: Int,
    val skippedCount: Int
)

private data class ParsedPlaybackHistory(
    val format: PlaybackHistoryTransferFormat,
    val entries: List<PlaybackHistoryEntry>
)

private data class HistorySeed(
    val id: String,
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val playedAt: Long,
    val durationMs: Long,
    val explicitListenedMs: Long,
    val trackKey: String
)

private data class TransferStat(
    val title: String,
    val artist: String,
    val album: String,
    val playCount: Int,
    val listenedMs: Long,
    val lastPlayedAt: Long
) {
    val trackKey: String
        get() = transferTrackKey(title, artist, album)
}

private data class ConeHistoryRow(
    val eventName: String,
    val eventDate: Long,
    val eventSession: String,
    val title: String,
    val artist: String,
    val album: String,
    val playTimeMs: Long
)

private data class LunaBeatDailyRow(
    val date: String,
    val songKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val totalDurationMs: Long,
    val lastPlayedAt: Long
) {
    val trackKey: String
        get() = transferTrackKey(title, artist, album)
}

private data class LunaBeatAggregateRow(
    val songKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val totalDurationMs: Long,
    val playCount: Int,
    val lastPlayedAt: Long
) {
    val trackKey: String
        get() = transferTrackKey(title, artist, album)
}

/**
 * Reads a history backup and merges it into the current store. The input is copied to the app
 * cache first because the Storage Access Framework stream cannot be rewound while format probing
 * and encrypted ZIP detection both need to inspect it.
 */
suspend fun importPlaybackHistoryFromUri(
    context: Context,
    uri: Uri,
    librarySongs: List<Song>,
    store: PlaybackStatsStore = PlaybackStatsStore.getInstance(context)
): PlaybackHistoryImportResult = withContext(Dispatchers.IO) {
    val inputFile = File(
        context.cacheDir,
        "playback_history_import_${System.currentTimeMillis()}_${System.nanoTime()}"
    )
    inputFile.parentFile?.mkdirs()
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            inputFile.outputStream().buffered().use { output ->
                copyToBounded(input, output, MAX_TRANSFER_INPUT_BYTES)
            }
        } ?: error("Unable to open playback history backup")

        val parsed = parsePlaybackHistoryFile(context, inputFile, librarySongs)
        // A recognized container with no rows otherwise looks like a silent no-op in the UI.
        // Treat it as an actionable import error so malformed/empty Cone and LunaBeat exports
        // report the actual stage instead of claiming success with zero records.
        require(parsed.entries.isNotEmpty()) { "No listening-history records found" }
        val added = store.mergeImportedHistory(parsed.entries)
        PlaybackHistoryImportResult(
            format = parsed.format,
            addedCount = added,
            skippedCount = (parsed.entries.size - added).coerceAtLeast(0)
        )
    } finally {
        inputFile.delete()
    }
}

/** Builds one of the selectable listening-history export formats. */
suspend fun buildPlaybackHistoryExportFile(
    context: Context,
    format: PlaybackHistoryTransferFormat,
    history: List<PlaybackHistoryEntry>,
    stats: List<SongPlaybackStats>,
    librarySongs: List<Song>
): File = withContext(Dispatchers.IO) {
    when (format) {
        PlaybackHistoryTransferFormat.CONE_MUSIC -> buildConeMusicBackupFile(
            context,
            history,
            librarySongs
        )
        PlaybackHistoryTransferFormat.LUNA_BEAT -> buildLunaBeatBackupFile(
            context,
            history,
            stats,
            librarySongs
        )
        PlaybackHistoryTransferFormat.HALCYON -> {
            val root = buildHalcyonHistoryJson(history, stats)
            writeTransferJson(context, "halcyon_history", root)
        }
        PlaybackHistoryTransferFormat.PRISM_MUSIC -> {
            val root = buildPrismHistoryJson(history, stats, librarySongs)
            writeTransferJson(context, "prism-listening-sessions", root)
        }
        PlaybackHistoryTransferFormat.RAWS_MUSIC -> {
            val root = buildRawsMusicBackupJson(history, stats)
            writeTransferJson(context, "rawsmusic_backup", root)
        }
    }
}

private fun parsePlaybackHistoryFile(
    context: Context,
    file: File,
    librarySongs: List<Song>
): ParsedPlaybackHistory {
    require(file.isFile && file.length() > 0L) { "Playback history backup is empty" }
    return when {
        fileLooksLikeJson(file) -> parseJsonPlaybackHistory(file, librarySongs)
        fileLooksLikeZip(file) -> parsePlainZipPlaybackHistory(file, librarySongs)
        else -> parseConeMusicBackup(context, file, librarySongs)
    }
}

private fun parseJsonPlaybackHistory(
    file: File,
    librarySongs: List<Song>
): ParsedPlaybackHistory {
    val root = JSONObject(readUtf8Bounded(file.inputStream().buffered(), MAX_TRANSFER_JSON_BYTES))
    val payload = root.optJSONObject("playback") ?: root

    if (payload.has("sessions") && !payload.has("history")) {
        return ParsedPlaybackHistory(
            format = PlaybackHistoryTransferFormat.PRISM_MUSIC,
            entries = parsePrismSessions(payload.optJSONArray("sessions") ?: JSONArray(), librarySongs)
        )
    }

    val isRaws = root.optString("format").equals("rawsmusic_backup", ignoreCase = true) ||
        payload.has("daily")
    return ParsedPlaybackHistory(
        format = if (isRaws) {
            PlaybackHistoryTransferFormat.RAWS_MUSIC
        } else {
            PlaybackHistoryTransferFormat.HALCYON
        },
        entries = parseHistoryPayload(
            payload = payload,
            librarySongs = librarySongs,
            namespace = if (isRaws) "rawsmusic" else "halcyon"
        )
    )
}

private fun parsePlainZipPlaybackHistory(
    file: File,
    librarySongs: List<Song>
): ParsedPlaybackHistory {
    val manifest = readZipEntryText(file, "manifest.json", MAX_TRANSFER_JSON_BYTES)
    val backupJson = readZipEntryText(file, "backup.json", MAX_TRANSFER_JSON_BYTES)
    if (backupJson != null) {
        return parseJsonPlaybackHistoryFromRoot(JSONObject(backupJson), librarySongs)
    }

    val isLunaBeat = manifest?.let {
        runCatching { JSONObject(it).optString("format") }
            .getOrNull()
            .equals("lunabeat_backup", ignoreCase = true)
    } == true || zipHasEntry(file, "databases/playback_stats.db")
    require(isLunaBeat) { "Unsupported listening-history ZIP" }

    val extracted = extractZipEntries(
        archive = file,
        requested = mapOf(
            "databases/playback_stats.db" to "playback_stats.db",
            "databases/playback_stats.db-wal" to "playback_stats.db-wal",
            "databases/playback_stats.db-shm" to "playback_stats.db-shm"
        )
    )
    return try {
        ParsedPlaybackHistory(
            format = PlaybackHistoryTransferFormat.LUNA_BEAT,
            entries = parseLunaBeatDatabase(extracted, librarySongs)
        )
    } finally {
        extracted.deleteRecursively()
    }
}

private fun parseJsonPlaybackHistoryFromRoot(
    root: JSONObject,
    librarySongs: List<Song>
): ParsedPlaybackHistory {
    val payload = root.optJSONObject("playback") ?: root
    if (payload.has("sessions") && !payload.has("history")) {
        return ParsedPlaybackHistory(
            format = PlaybackHistoryTransferFormat.PRISM_MUSIC,
            entries = parsePrismSessions(payload.optJSONArray("sessions") ?: JSONArray(), librarySongs)
        )
    }
    val isRaws = root.optString("format").equals("rawsmusic_backup", ignoreCase = true) ||
        payload.has("daily")
    return ParsedPlaybackHistory(
        format = if (isRaws) PlaybackHistoryTransferFormat.RAWS_MUSIC else PlaybackHistoryTransferFormat.HALCYON,
        entries = parseHistoryPayload(
            payload,
            librarySongs,
            if (isRaws) "rawsmusic" else "halcyon"
        )
    )
}

private fun parseConeMusicBackup(
    context: Context,
    encryptedFile: File,
    librarySongs: List<Song>
): ParsedPlaybackHistory {
    val decryptedFile = File(
        context.cacheDir,
        "playback_history_cone_decrypted_${System.currentTimeMillis()}_${System.nanoTime()}.zip"
    )
    try {
        decryptConeArchive(encryptedFile, decryptedFile)
        val manifest = readZipEntryText(decryptedFile, "_manifest.txt", MAX_TRANSFER_JSON_BYTES)
        require(manifest?.lineSequence()?.any { it.trim() == "trantor_db" } == true) {
            "Unsupported encrypted listening-history ZIP"
        }
        val extracted = extractZipEntries(
            archive = decryptedFile,
            requested = mapOf(
                "trantor_db/trantor_db" to "trantor_db",
                "trantor_db/trantor_db-wal" to "trantor_db-wal",
                "trantor_db/trantor_db-shm" to "trantor_db-shm"
            )
        )
        return try {
            ParsedPlaybackHistory(
                format = PlaybackHistoryTransferFormat.CONE_MUSIC,
                entries = parseConeDatabase(extracted, librarySongs)
            )
        } finally {
            extracted.deleteRecursively()
        }
    } finally {
        decryptedFile.delete()
    }
}

private fun parseHistoryPayload(
    payload: JSONObject,
    librarySongs: List<Song>,
    namespace: String
): List<PlaybackHistoryEntry> {
    val stats = parseTransferStats(payload.optJSONArray("stats"))
    val daily = parseDailyMap(
        payload.optJSONObject("dailyListenMs") ?: payload.optJSONObject("daily")
    )
    val historyArray = payload.optJSONArray("history")
    val seeds = mutableListOf<HistorySeed>()
    if (historyArray != null) {
        for (index in 0 until historyArray.length()) {
            val item = historyArray.optJSONObject(index) ?: continue
            val title = item.optString("title").trim()
            val artist = item.optString("artist").trim()
            val album = item.optString("album").trim()
            val playedAt = item.optLong("playedAt")
                .takeIf { it > 0L }
                ?: item.optLong("endedAtMs").takeIf { it > 0L }
                ?: item.optLong("lastPlayedAt").takeIf { it > 0L }
                ?: continue
            if (title.isBlank() && artist.isBlank() && album.isBlank()) continue
            val song = resolveLibrarySong(title, artist, album, librarySongs)
            val key = transferTrackKey(title, artist, album)
            val externalId = item.optString("entryId").ifBlank { item.optString("uid") }
            val stableId = stableTransferEntryId(
                namespace,
                externalId.ifBlank { "$key|$playedAt|$index" }
            )
            seeds += HistorySeed(
                id = stableId,
                songId = song?.id ?: stableExternalSongId(key),
                title = title,
                artist = artist,
                album = album,
                playedAt = playedAt,
                durationMs = item.optLong("durationMs").coerceAtLeast(0L)
                    .takeIf { it > 0L }
                    ?: song?.duration?.coerceAtLeast(0L)
                    ?: 0L,
                explicitListenedMs = item.optLong("listenedMs")
                    .takeIf { it > 0L }
                    ?: item.optLong("playedMs").takeIf { it > 0L }
                    ?: 0L,
                trackKey = key
            )
        }
    }

    if (seeds.isEmpty() && stats.isNotEmpty()) {
        stats.forEach { stat ->
            val count = stat.playCount.coerceAtLeast(1)
            val song = resolveLibrarySong(stat.title, stat.artist, stat.album, librarySongs)
            repeat(count) { index ->
                val playedAt = (stat.lastPlayedAt.takeIf { it > 0L } ?: System.currentTimeMillis()) -
                    (count - index - 1).toLong()
                val key = stat.trackKey
                seeds += HistorySeed(
                    id = stableTransferEntryId(namespace, "$key|$playedAt|aggregate|$index"),
                    songId = song?.id ?: stableExternalSongId(key),
                    title = stat.title,
                    artist = stat.artist,
                    album = stat.album,
                    playedAt = playedAt,
                    durationMs = song?.duration?.coerceAtLeast(0L) ?: 0L,
                    explicitListenedMs = 0L,
                    trackKey = key
                )
            }
        }
    }

    return materializeHistorySeeds(seeds, stats, daily)
}

private fun parsePrismSessions(
    sessions: JSONArray,
    librarySongs: List<Song>
): List<PlaybackHistoryEntry> {
    val seeds = mutableListOf<HistorySeed>()
    for (index in 0 until sessions.length()) {
        val item = sessions.optJSONObject(index) ?: continue
        val title = item.optString("title").trim()
        val artist = item.optString("artist").trim()
        val album = item.optString("album").trim()
        if (title.isBlank() && artist.isBlank() && album.isBlank()) continue
        val endedAt = item.optLong("endedAtMs").takeIf { it > 0L }
            ?: item.optLong("playedAt").takeIf { it > 0L }
            ?: item.optLong("startedAtMs").takeIf { it > 0L }
            ?: continue
        val song = resolveLibrarySong(title, artist, album, librarySongs)
        val key = transferTrackKey(title, artist, album)
        val uid = item.optString("uid").ifBlank { item.optString("entryId") }
        seeds += HistorySeed(
            id = stableTransferEntryId("prism", uid.ifBlank { "$key|$endedAt|$index" }),
            songId = song?.id ?: stableExternalSongId(key),
            title = title,
            artist = artist,
            album = album,
            playedAt = endedAt,
            durationMs = item.optLong("durationMs").coerceAtLeast(0L)
                .takeIf { it > 0L }
                ?: song?.duration?.coerceAtLeast(0L)
                ?: 0L,
            explicitListenedMs = item.optLong("playedMs").coerceAtLeast(0L)
                .takeIf { it > 0L }
                ?: item.optLong("listenedMs").takeIf { it > 0L }
                ?: 0L,
            trackKey = key
        )
    }
    return materializeHistorySeeds(seeds, emptyList(), emptyMap())
}

private fun parseTransferStats(array: JSONArray?): List<TransferStat> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val title = item.optString("title").trim()
            val artist = item.optString("artist").trim()
            val album = item.optString("album").trim()
            if (title.isBlank() && artist.isBlank() && album.isBlank()) continue
            add(
                TransferStat(
                    title = title,
                    artist = artist,
                    album = album,
                    playCount = item.optInt("playCount").coerceAtLeast(0),
                    listenedMs = item.optLong("listenedMs").coerceAtLeast(0L)
                        .takeIf { it > 0L }
                        ?: item.optLong("totalDurationMs").coerceAtLeast(0L),
                    lastPlayedAt = item.optLong("lastPlayedAt").coerceAtLeast(0L)
                )
            )
        }
    }
}

private fun parseDailyMap(objectValue: JSONObject?): Map<String, Long> {
    if (objectValue == null) return emptyMap()
    val daily = mutableMapOf<String, Long>()
    objectValue.keys().forEach { key ->
        daily[key] = objectValue.optLong(key).coerceAtLeast(0L)
    }
    return daily
}

private fun materializeHistorySeeds(
    seeds: List<HistorySeed>,
    stats: List<TransferStat>,
    daily: Map<String, Long>
): List<PlaybackHistoryEntry> {
    if (seeds.isEmpty()) return emptyList()
    val statsByKey = stats.associateBy { it.trackKey }
    val byTrack = seeds.groupBy { it.trackKey }
    val byDay = seeds.groupBy { it.playedAt.toTransferDateKey() }
    return seeds.map { seed ->
        val trackItems = byTrack[seed.trackKey].orEmpty()
        val trackIndex = trackItems.indexOf(seed)
        val statShare = statsByKey[seed.trackKey]?.let { stat ->
            splitTotal(stat.listenedMs, trackItems.size, trackIndex)
        } ?: 0L
        val dayItems = byDay[seed.playedAt.toTransferDateKey()].orEmpty()
        val dayIndex = dayItems.indexOf(seed)
        val dayShare = splitTotal(
            daily[seed.playedAt.toTransferDateKey()] ?: 0L,
            dayItems.size,
            dayIndex
        )
        val listenedMs = seed.explicitListenedMs.takeIf { it > 0L }
            ?: statShare.takeIf { it > 0L }
            ?: dayShare.takeIf { it > 0L }
            ?: seed.durationMs.takeIf { it > 0L }
            ?: 0L
        PlaybackHistoryEntry(
            entryId = seed.id,
            songId = seed.songId,
            title = seed.title,
            artist = seed.artist,
            album = seed.album,
            playedAt = seed.playedAt,
            durationMs = seed.durationMs,
            listenedMs = listenedMs,
            source = PlaybackHistorySource.LOCAL,
            playCounted = true
        )
    }.distinctBy(PlaybackHistoryEntry::entryId)
        .sortedByDescending(PlaybackHistoryEntry::playedAt)
}

private fun splitTotal(total: Long, count: Int, index: Int): Long {
    if (total <= 0L || count <= 0 || index !in 0 until count) return 0L
    val each = total / count
    val remainder = total % count
    return each + if (index.toLong() < remainder) 1L else 0L
}

private fun parseLunaBeatDatabase(
    extracted: File,
    librarySongs: List<Song>
): List<PlaybackHistoryEntry> {
    val databaseFile = File(extracted, "playback_stats.db")
    require(databaseFile.isFile) { "LunaBeat playback database is missing" }
    val database = SQLiteDatabase.openDatabase(
        databaseFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY
    )
    return try {
        val aggregateRows = if (database.hasTable("SongPlaybackStats")) {
            database.rawQuery(
                "SELECT songKey,title,artist,album,totalDurationMs,playCount,lastPlayedAt " +
                    "FROM SongPlaybackStats ORDER BY lastPlayedAt ASC",
                null
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            LunaBeatAggregateRow(
                                songKey = cursor.stringValue("songKey"),
                                title = cursor.stringValue("title"),
                                artist = cursor.stringValue("artist"),
                                album = cursor.stringValue("album"),
                                totalDurationMs = cursor.longValue("totalDurationMs"),
                                playCount = cursor.intValue("playCount"),
                                lastPlayedAt = cursor.longValue("lastPlayedAt")
                            )
                        )
                    }
                }
            }
        } else {
            emptyList()
        }
        val dailyRows = if (database.hasTable("DailySongPlaybackStats")) {
            database.rawQuery(
                "SELECT date,songKey,title,artist,album,totalDurationMs,lastPlayedAt " +
                    "FROM DailySongPlaybackStats ORDER BY date ASC,lastPlayedAt ASC",
                null
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            LunaBeatDailyRow(
                                date = cursor.stringValue("date"),
                                songKey = cursor.stringValue("songKey"),
                                title = cursor.stringValue("title"),
                                artist = cursor.stringValue("artist"),
                                album = cursor.stringValue("album"),
                                totalDurationMs = cursor.longValue("totalDurationMs"),
                                lastPlayedAt = cursor.longValue("lastPlayedAt")
                            )
                        )
                    }
                }
            }
        } else {
            emptyList()
        }
        lunaBeatRowsToHistory(dailyRows, aggregateRows, librarySongs)
    } finally {
        database.close()
    }
}

private fun lunaBeatRowsToHistory(
    dailyRows: List<LunaBeatDailyRow>,
    aggregateRows: List<LunaBeatAggregateRow>,
    librarySongs: List<Song>
): List<PlaybackHistoryEntry> {
    val aggregateByKey = aggregateRows.groupBy { it.trackKey }
    val groupedDaily = dailyRows.groupBy { it.trackKey }
    val entries = mutableListOf<PlaybackHistoryEntry>()

    groupedDaily.forEach { (trackKey, rows) ->
        val aggregate = aggregateByKey[trackKey]
            ?.maxByOrNull { it.lastPlayedAt }
        val targetCount = maxOf(
            rows.size,
            aggregate?.playCount?.coerceAtLeast(0) ?: 0,
            1
        )
        val occurrencesPerRow = IntArray(rows.size)
        repeat(targetCount) { index ->
            occurrencesPerRow[index % rows.size]++
        }
        rows.forEachIndexed { rowIndex, row ->
            val occurrenceCount = occurrencesPerRow[rowIndex]
            val song = resolveLibrarySong(row.title, row.artist, row.album, librarySongs)
            repeat(occurrenceCount) { occurrence ->
                val playedAt = (row.lastPlayedAt.takeIf { it > 0L }
                    ?: row.date.toDateEndMillis()
                    ?: System.currentTimeMillis()) -
                    (occurrenceCount - occurrence - 1).toLong()
                entries += PlaybackHistoryEntry(
                    entryId = stableTransferEntryId(
                        "lunabeat",
                        "$row.trackKey|${row.date}|$occurrence"
                    ),
                    songId = song?.id ?: stableExternalSongId(trackKey),
                    title = row.title,
                    artist = row.artist,
                    album = row.album,
                    playedAt = playedAt,
                    durationMs = song?.duration?.coerceAtLeast(0L) ?: 0L,
                    listenedMs = splitTotal(
                        row.totalDurationMs.coerceAtLeast(0L),
                        occurrenceCount,
                        occurrence
                    ),
                    source = PlaybackHistorySource.LOCAL,
                    playCounted = true
                )
            }
        }
    }

    // A database can contain a long-term aggregate after its daily rows have been compacted.
    // Preserve it as synthetic sessions instead of silently dropping the listening total.
    aggregateRows
        .filter { it.trackKey !in groupedDaily }
        .forEach { aggregate ->
            val count = maxOf(aggregate.playCount, 1)
            val song = resolveLibrarySong(aggregate.title, aggregate.artist, aggregate.album, librarySongs)
            repeat(count) { index ->
                val playedAt = aggregate.lastPlayedAt.takeIf { it > 0L }
                    ?: System.currentTimeMillis()
                entries += PlaybackHistoryEntry(
                    entryId = stableTransferEntryId(
                        "lunabeat",
                        "${aggregate.trackKey}|aggregate|$index"
                    ),
                    songId = song?.id ?: stableExternalSongId(aggregate.trackKey),
                    title = aggregate.title,
                    artist = aggregate.artist,
                    album = aggregate.album,
                    playedAt = playedAt - (count - index - 1).toLong(),
                    durationMs = song?.duration?.coerceAtLeast(0L) ?: 0L,
                    listenedMs = splitTotal(
                        aggregate.totalDurationMs.coerceAtLeast(0L),
                        count,
                        index
                    ),
                    source = PlaybackHistorySource.LOCAL,
                    playCounted = true
                )
            }
        }
    return entries.distinctBy(PlaybackHistoryEntry::entryId)
        .sortedByDescending(PlaybackHistoryEntry::playedAt)
}

private fun parseConeDatabase(
    extracted: File,
    librarySongs: List<Song>
): List<PlaybackHistoryEntry> {
    val databaseFile = File(extracted, "trantor_db")
    require(databaseFile.isFile) { "Cone playback database is missing" }
    val database = SQLiteDatabase.openDatabase(
        databaseFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY
    )
    return try {
        require(database.hasTable("AudioReportData")) { "Cone listening-history table is missing" }
        val rows = database.rawQuery(
            "SELECT event_name,event_date,event_session,audio_title,artist,album_title,play_time " +
                // Do not rely on SQLite's implicit rowid: newer Cone exports can use a
                // WITHOUT ROWID table. The event timestamp is the canonical playback order.
                "FROM AudioReportData WHERE event_name = ? ORDER BY event_date ASC",
            arrayOf("event_tick")
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ConeHistoryRow(
                            eventName = cursor.stringValue("event_name"),
                            eventDate = normalizeTransferTimestamp(cursor.longValue("event_date")),
                            eventSession = cursor.stringValue("event_session"),
                            title = cursor.stringValue("audio_title"),
                            artist = cursor.stringValue("artist"),
                            album = cursor.stringValue("album_title"),
                            playTimeMs = cursor.longValue("play_time")
                        )
                    )
                }
            }
        }
        coneRowsToHistory(rows, librarySongs)
    } finally {
        database.close()
    }
}

private fun coneRowsToHistory(
    rows: List<ConeHistoryRow>,
    librarySongs: List<Song>
): List<PlaybackHistoryEntry> {
    val sessions = mutableListOf<MutableList<ConeHistoryRow>>()
    rows.asSequence()
        .filter { it.eventName == "event_tick" && it.eventDate > 0L }
        .forEach { row ->
            val previous = sessions.lastOrNull()?.lastOrNull()
            val sameTrack = previous != null &&
                transferTrackKey(previous.title, previous.artist, previous.album) ==
                transferTrackKey(row.title, row.artist, row.album)
            val sameSession = previous != null &&
                row.eventSession.isNotBlank() &&
                row.eventSession == previous.eventSession
            val nearbyUntypedSession = previous != null &&
                row.eventSession.isBlank() &&
                previous.eventSession.isBlank() &&
                row.eventDate - previous.eventDate <= CONE_BLANK_SESSION_GAP_MS
            if (sameTrack && (sameSession || nearbyUntypedSession)) {
                sessions.last().add(row)
            } else {
                sessions += mutableListOf(row)
            }
        }

    return sessions.mapIndexedNotNull { index, session ->
        val first = session.firstOrNull() ?: return@mapIndexedNotNull null
        val title = first.title.trim()
        val artist = first.artist.trim()
        val album = first.album.trim()
        if (title.isBlank() && artist.isBlank() && album.isBlank()) return@mapIndexedNotNull null
        val song = resolveLibrarySong(title, artist, album, librarySongs)
        val trackKey = transferTrackKey(title, artist, album)
        val lastEventAt = session.maxOf { it.eventDate }
        val listenedMs = session.sumOf { it.playTimeMs.coerceAtLeast(0L) }
        PlaybackHistoryEntry(
            entryId = stableTransferEntryId(
                "cone",
                "${first.eventSession}|$trackKey|${first.eventDate}|$index"
            ),
            songId = song?.id ?: stableExternalSongId(trackKey),
            title = title,
            artist = artist,
            album = album,
            playedAt = lastEventAt,
            durationMs = song?.duration?.coerceAtLeast(0L) ?: 0L,
            listenedMs = listenedMs,
            source = PlaybackHistorySource.LOCAL,
            playCounted = true
        )
    }.sortedByDescending(PlaybackHistoryEntry::playedAt)
}

private fun resolveLibrarySong(
    title: String,
    artist: String,
    album: String,
    librarySongs: List<Song>
): Song? {
    val exact = librarySongs.filter {
        transferTrackKey(it.title, it.artist, it.album) == transferTrackKey(title, artist, album)
    }
    if (exact.size == 1) return exact.single()
    if (exact.isNotEmpty()) return exact.minByOrNull(Song::id)

    val titleArtist = librarySongs.filter {
        transferTextKey(it.title) == transferTextKey(title) &&
            transferTextKey(it.artist) == transferTextKey(artist)
    }
    if (titleArtist.size == 1) return titleArtist.single()
    if (titleArtist.isNotEmpty()) return titleArtist.minByOrNull(Song::id)

    val titleOnly = librarySongs.filter { transferTextKey(it.title) == transferTextKey(title) }
    return titleOnly.singleOrNull() ?: titleOnly.minByOrNull(Song::id)
}

private fun transferTrackKey(title: String, artist: String, album: String): String =
    listOf(title, artist, album).joinToString("|") { transferTextKey(it) }

private fun transferTextKey(value: String): String = value
    .trim()
    .replace('\u0000', ' ')
    .replace('＆', '&')
    .replace(Regex("\\s+"), " ")
    .lowercase(Locale.ROOT)

private fun stableTransferEntryId(namespace: String, value: String): String =
    "$namespace:${UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))}"

private fun stableExternalSongId(trackKey: String): Long {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(trackKey.toByteArray(StandardCharsets.UTF_8))
    var value = 0L
    digest.take(Long.SIZE_BYTES).forEach { byte ->
        value = (value shl 8) or (byte.toLong() and 0xffL)
    }
    val positive = value and Long.MAX_VALUE
    return -positive.coerceAtLeast(1L)
}

private fun buildHalcyonHistoryJson(
    history: List<PlaybackHistoryEntry>,
    stats: List<SongPlaybackStats>
): JSONObject = JSONObject()
    .put("format", "halcyon_playback_history")
    .put("version", 1)
    .put("exportedAtMs", System.currentTimeMillis())
    .put("stats", stats.toTransferJson())
    .put("history", history.toTransferHistoryJson())
    .put("dailyListenMs", history.toTransferDailyJson())

private fun buildPrismHistoryJson(
    history: List<PlaybackHistoryEntry>,
    stats: List<SongPlaybackStats>,
    librarySongs: List<Song>
): JSONObject = JSONObject()
    .put("version", 1)
    .put("exportedAtMs", System.currentTimeMillis())
    .put("sessions", history.toPrismSessions(stats, librarySongs))

private fun buildRawsMusicBackupJson(
    history: List<PlaybackHistoryEntry>,
    stats: List<SongPlaybackStats>
): JSONObject = JSONObject()
    .put("version", 2)
    .put("timestamp", System.currentTimeMillis())
    // Deliberately omit settings and playlists: Raws Music restores those sections when they
    // exist, and a listening-history export must never clear unrelated user data.
    .put(
        "playback",
        JSONObject()
            .put("stats", stats.toTransferJson())
            .put("history", history.toRawsHistoryJson())
            .put("daily", history.toTransferDailyJson())
    )

private fun List<SongPlaybackStats>.toTransferJson(): JSONArray = JSONArray().also { array ->
    forEach { stat ->
        array.put(
            JSONObject()
                .put("songId", stat.songId)
                .put("title", stat.title)
                .put("artist", stat.artist)
                .put("album", stat.album)
                .put("playCount", stat.playCount)
                .put("listenedMs", stat.listenedMs)
                .put("lastPlayedAt", stat.lastPlayedAt)
        )
    }
}

private fun List<PlaybackHistoryEntry>.toTransferHistoryJson(): JSONArray = JSONArray().also { array ->
    forEach { entry ->
        array.put(
            JSONObject()
                .put("entryId", entry.entryId)
                .put("songId", entry.songId)
                .put("title", entry.title)
                .put("artist", entry.artist)
                .put("album", entry.album)
                .put("playedAt", entry.playedAt)
                .put("durationMs", entry.durationMs)
                .put("listenedMs", entry.listenedMs)
                .put("source", entry.source)
                .put("playCounted", true)
        )
    }
}

private fun List<PlaybackHistoryEntry>.toRawsHistoryJson(): JSONArray = JSONArray().also { array ->
    forEach { entry ->
        // Raws Music ignores unknown fields, while the two duration fields preserve information
        // when a newer Raws build learns about per-session durations.
        array.put(
            JSONObject()
                .put("songId", entry.songId)
                .put("title", entry.title)
                .put("artist", entry.artist)
                .put("album", entry.album)
                .put("playedAt", entry.playedAt)
                .put("durationMs", entry.durationMs)
                .put("listenedMs", entry.listenedMs)
        )
    }
}

private fun List<PlaybackHistoryEntry>.toTransferDailyJson(): JSONObject = JSONObject().also { objectValue ->
    groupBy { it.playedAt.toTransferDateKey() }
        .toSortedMap()
        .forEach { (date, entries) ->
            objectValue.put(date, entries.sumOf { it.listenedMs.coerceAtLeast(0L) })
        }
}

private fun List<PlaybackHistoryEntry>.toPrismSessions(
    stats: List<SongPlaybackStats>,
    librarySongs: List<Song>
): JSONArray {
    val statBySongId = stats.associateBy { it.songId }
    val statByTrackKey = stats.associateBy {
        transferTrackKey(it.title, it.artist, it.album)
    }
    val libraryById = librarySongs.associateBy { it.id }
    return JSONArray().also { array ->
        forEach { entry ->
            val entryTrackKey = transferTrackKey(entry.title, entry.artist, entry.album)
            val idSong = libraryById[entry.songId]
            // A playback record imported from another app can carry a positive id that happens
            // to exist in Halcyon's database but belongs to a different song. Only trust the id
            // when its metadata agrees; otherwise resolve by the portable title/artist/album
            // fingerprint before borrowing duration or artwork.
            val song = idSong?.takeIf {
                transferTrackKey(it.title, it.artist, it.album) == entryTrackKey
            } ?: resolveLibrarySong(entry.title, entry.artist, entry.album, librarySongs)
            val stat = statBySongId[entry.songId]?.takeIf {
                transferTrackKey(it.title, it.artist, it.album) == entryTrackKey
            } ?: statByTrackKey[entryTrackKey]
            val durationMs = song?.duration?.coerceAtLeast(0L)
                ?: entry.durationMs.coerceAtLeast(0L)
            val playedMs = entry.listenedMs.takeIf { it > 0L }
                ?: stat?.let { aggregate ->
                    if (aggregate.playCount > 0) aggregate.listenedMs / aggregate.playCount
                    else aggregate.listenedMs
                }?.takeIf { it > 0L }
                ?: durationMs.takeIf { it > 0L }
                ?: 60_000L
            array.put(
                JSONObject()
                    .put("uid", entry.entryId)
                    // Prism 0.2.7 stores its own auto-increment songs.id and its importer does
                    // not remap title/artist/album to the receiver library. Halcyon's positive
                    // ids therefore collide with unrelated Prism songs. Use a deterministic
                    // negative metadata id so imported TOP rows remain distinct and can never
                    // silently start the wrong local song. A Prism-side metadata resolver is
                    // still required to make these rows directly playable in that app.
                    .put("songId", stableExternalSongId(entryTrackKey))
                    .put("title", entry.title)
                    .put("artist", entry.artist)
                    .put("album", entry.album)
                    // Keep album grouping independent of Halcyon's local album id. The latter
                    // is also app-local and was the source of mixed TOP album groups after a
                    // cross-app transfer.
                    .put("albumKey", prismAlbumKey(entry.artist, entry.album))
                    .put("durationMs", durationMs)
                    .put("playedMs", playedMs)
                    .put("startedAtMs", (entry.playedAt - playedMs).coerceAtLeast(1L))
                    .put("endedAtMs", entry.playedAt)
                    .put("dayBucket", entry.playedAt.toTransferDayBucket())
                    .put("cover", song?.prismTransferCoverUri().orEmpty())
            )
        }
    }
}

private fun prismAlbumKey(artist: String, album: String): String =
    "meta:${transferTextKey(artist)}|${transferTextKey(album)}"

private fun Song.prismTransferCoverUri(): String =
    // A MediaStore album-art URI is the only artwork reference that another local music app can
    // normally open. Keep the app-specific/remote URL as a fallback for receivers that support
    // it, but do not prefer it over a local content URI.
    mediaStoreAlbumArtUri(albumId)?.toString()
        ?: coverUrl.takeIf { it.isNotBlank() }
        ?: ""

private fun writeTransferJson(context: Context, prefix: String, root: JSONObject): File {
    val target = File(
        context.cacheDir,
        "${prefix}_${System.currentTimeMillis()}_${System.nanoTime()}.json"
    )
    target.parentFile?.mkdirs()
    target.outputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer ->
        writer.write(root.toString(2))
    }
    return target
}

private fun buildLunaBeatBackupFile(
    context: Context,
    history: List<PlaybackHistoryEntry>,
    stats: List<SongPlaybackStats>,
    librarySongs: List<Song>
): File {
    val databaseFile = File(
        context.cacheDir,
        "lunabeat_playback_stats_${System.currentTimeMillis()}_${System.nanoTime()}.db"
    )
    val databaseWalFile = File("${databaseFile.absolutePath}-wal")
    val databaseShmFile = File("${databaseFile.absolutePath}-shm")
    val archiveFile = File(
        context.cacheDir,
        "lunabeat_history_${System.currentTimeMillis()}_${System.nanoTime()}.zip"
    )
    try {
        createLunaBeatDatabase(databaseFile, history, stats, librarySongs)
        val manifest = JSONObject()
            .put("format", "lunabeat_backup")
            .put("version", 2)
            .put("createdAt", System.currentTimeMillis())
            .put("packageName", LUNA_BEAT_PACKAGE_NAME)
        val protectedFiles = JSONObject()
        val databaseEntries = buildList {
            add("databases/playback_stats.db" to databaseFile)
            if (databaseWalFile.isFile) add("databases/playback_stats.db-wal" to databaseWalFile)
            if (databaseShmFile.isFile) add("databases/playback_stats.db-shm" to databaseShmFile)
        }
        databaseEntries.forEach { (archivePath, source) ->
            protectedFiles.put(archivePath, source.lunaBeatSignatureBase64())
        }
        manifest.put("protectedPlaybackFiles", protectedFiles)

        ZipOutputStream(BufferedOutputStream(FileOutputStream(archiveFile))).use { zip ->
            // LunaBeat always writes this member.  An empty object is intentional: Halcyon must
            // not copy its own credentials into another player's backup, while LunaBeat's
            // restore path can safely parse the same container shape.
            zip.writeTransferEntry("secure_prefs.json") {
                it.write("{}".toByteArray(StandardCharsets.UTF_8))
            }
            zip.writeTransferEntry("manifest.json") {
                it.write(manifest.toString(2).toByteArray(StandardCharsets.UTF_8))
            }
            databaseEntries.forEach { (archivePath, source) ->
                zip.writeTransferEntry(archivePath) { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                }
            }
        }
        return archiveFile
    } catch (error: Throwable) {
        archiveFile.delete()
        throw error
    } finally {
        databaseFile.delete()
        databaseWalFile.delete()
        databaseShmFile.delete()
    }
}

private fun createLunaBeatDatabase(
    databaseFile: File,
    history: List<PlaybackHistoryEntry>,
    stats: List<SongPlaybackStats>,
    librarySongs: List<Song>
) {
    val database = SQLiteDatabase.openOrCreateDatabase(databaseFile.absolutePath, null)
    try {
        database.enableWriteAheadLogging()
        database.execSQL("PRAGMA user_version = 3")
        LUNA_BEAT_SCHEMA.forEach(database::execSQL)
        database.beginTransaction()
        try {
            database.insert("android_metadata", null, ContentValues().apply { put("locale", "en_US") })

            val libraryById = librarySongs.associateBy { it.id }
            val historyByKey = history.groupBy { transferTrackKey(it.title, it.artist, it.album) }
            val statByKey = stats.associateBy { transferTrackKey(it.title, it.artist, it.album) }
            val keys = (historyByKey.keys + statByKey.keys).toSortedSet()
            val usedSongKeys = mutableSetOf<String>()
            val songKeyByTrackKey = buildMap {
                keys.forEach { key ->
                    val first = historyByKey[key]?.firstOrNull()
                    val stat = statByKey[key]
                    val song = first?.let { libraryById[it.songId] }
                        ?: first?.let { resolveLibrarySong(it.title, it.artist, it.album, librarySongs) }
                        ?: stat?.let { resolveLibrarySong(it.title, it.artist, it.album, librarySongs) }
                    val baseKey = song?.path?.takeIf { it.isNotBlank() } ?: key
                    var songKey = baseKey
                    var suffix = 1
                    while (!usedSongKeys.add(songKey)) {
                        songKey = "$baseKey#$suffix"
                        suffix += 1
                    }
                    put(key, songKey)
                }
            }

            val dailyRows = mutableListOf<LunaExportDailyRow>()
            historyByKey.values.flatten()
                .groupBy { it.playedAt.toTransferDateKey() to transferTrackKey(it.title, it.artist, it.album) }
                .forEach { (key, entries) ->
                    val first = entries.first()
                    val song = libraryById[first.songId]
                        ?: resolveLibrarySong(first.title, first.artist, first.album, librarySongs)
                    dailyRows += LunaExportDailyRow(
                        date = key.first,
                        songKey = songKeyByTrackKey.getValue(key.second),
                        title = first.title,
                        artist = first.artist,
                        album = first.album,
                        totalDurationMs = entries.sumOf(::transferListenedMs),
                        lastPlayedAt = entries.maxOf { it.playedAt }
                    )
                }

            val dailyByDate = dailyRows.groupBy { it.date }
            dailyByDate.forEach { (date, rows) ->
                database.insertOrThrow(
                    "DailyPlaybackStats",
                    ContentValues().apply {
                        put("date", date)
                        put("totalDurationMs", rows.sumOf { it.totalDurationMs })
                        put("updatedAt", rows.maxOf { it.lastPlayedAt })
                    }
                )
            }
            dailyRows.forEach { row ->
                database.insertOrThrow(
                    "DailySongPlaybackStats",
                    ContentValues().apply {
                        put("date", row.date)
                        put("songKey", row.songKey)
                        put("title", row.title)
                        put("artist", row.artist)
                        put("album", row.album)
                        put("totalDurationMs", row.totalDurationMs)
                        put("lastPlayedAt", row.lastPlayedAt)
                        put("updatedAt", row.lastPlayedAt)
                    }
                )
            }

            val trackRows = keys.mapNotNull { key ->
                val entries = historyByKey[key].orEmpty()
                val first = entries.firstOrNull()
                val stat = statByKey[key]
                if (first == null && stat == null) return@mapNotNull null
                val song = first?.let { libraryById[it.songId] }
                    ?: first?.let { resolveLibrarySong(it.title, it.artist, it.album, librarySongs) }
                    ?: stat?.let { resolveLibrarySong(it.title, it.artist, it.album, librarySongs) }
                val title = first?.title ?: stat?.title.orEmpty()
                val artist = first?.artist ?: stat?.artist.orEmpty()
                val album = first?.album ?: stat?.album.orEmpty()
                LunaExportTrackRow(
                    songKey = songKeyByTrackKey.getValue(key),
                    title = title,
                    artist = artist,
                    album = album,
                    totalDurationMs = maxOf(
                    entries.sumOf(::transferListenedMs),
                    stat?.listenedMs ?: 0L
                    ),
                    playCount = maxOf(entries.size, stat?.playCount ?: 0),
                    lastPlayedAt = maxOf(
                    entries.maxOfOrNull { it.playedAt } ?: 0L,
                    stat?.lastPlayedAt ?: 0L
                    )
                )
            }

            trackRows.forEach { row ->
                database.insertOrThrow(
                    "SongPlaybackStats",
                    ContentValues().apply {
                        put("songKey", row.songKey)
                        put("title", row.title)
                        put("artist", row.artist)
                        put("album", row.album)
                        put("totalDurationMs", row.totalDurationMs)
                        put("playCount", row.playCount)
                        put("lastPlayedAt", row.lastPlayedAt)
                        put("updatedAt", row.lastPlayedAt)
                    }
                )
            }

            trackRows.groupBy { it.artist }
                .forEach { (artist, rows) ->
                    database.insertOrThrow(
                        "ArtistPlaybackStats",
                        ContentValues().apply {
                            put("artistName", artist)
                            put("totalDurationMs", rows.sumOf { it.totalDurationMs })
                            put("playCount", rows.sumOf { it.playCount })
                            put("lastPlayedAt", rows.maxOf { it.lastPlayedAt })
                            put("updatedAt", rows.maxOf { it.lastPlayedAt })
                        }
                    )
                }
            trackRows.groupBy { it.artist to transferTextKey(it.album) }
                .forEach { (key, rows) ->
                    val row = rows.first()
                    database.insertOrThrow(
                        "AlbumPlaybackStats",
                        ContentValues().apply {
                            put("albumKey", "${key.first}|${key.second}")
                            put("albumName", row.album)
                            put("artistName", row.artist)
                            put("totalDurationMs", rows.sumOf { it.totalDurationMs })
                            put("playCount", rows.sumOf { it.playCount })
                            put("lastPlayedAt", rows.maxOf { it.lastPlayedAt })
                            put("updatedAt", rows.maxOf { it.lastPlayedAt })
                        }
                    )
                }

            dailyRows.groupBy { it.date to it.artist }
                .forEach { (key, rows) ->
                    database.insertOrThrow(
                        "DailyArtistPlaybackStats",
                        ContentValues().apply {
                            put("date", key.first)
                            put("artistName", key.second)
                            put("totalDurationMs", rows.sumOf { it.totalDurationMs })
                            put("lastPlayedAt", rows.maxOf { it.lastPlayedAt })
                            put("updatedAt", rows.maxOf { it.lastPlayedAt })
                        }
                    )
                }
            dailyRows.groupBy { it.date to "${it.artist}|${transferTextKey(it.album)}" }
                .forEach { (key, rows) ->
                    val row = rows.first()
                    database.insertOrThrow(
                        "DailyAlbumPlaybackStats",
                        ContentValues().apply {
                            put("date", key.first)
                            put("albumKey", key.second)
                            put("albumName", row.album)
                            put("artistName", row.artist)
                            put("totalDurationMs", rows.sumOf { it.totalDurationMs })
                            put("lastPlayedAt", rows.maxOf { it.lastPlayedAt })
                            put("updatedAt", rows.maxOf { it.lastPlayedAt })
                        }
                    )
                }

            database.insertOrThrow(
                "room_master_table",
                ContentValues().apply {
                    put("id", 42)
                    put("identity_hash", LUNA_BEAT_ROOM_IDENTITY)
                }
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    } finally {
        database.close()
    }
}

private data class LunaExportDailyRow(
    val date: String,
    val songKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val totalDurationMs: Long,
    val lastPlayedAt: Long
)

private data class LunaExportTrackRow(
    val songKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val totalDurationMs: Long,
    val playCount: Int,
    val lastPlayedAt: Long
)

private fun transferListenedMs(entry: PlaybackHistoryEntry): Long =
    entry.listenedMs.takeIf { it > 0L } ?: entry.durationMs.coerceAtLeast(0L)

private fun buildConeMusicBackupFile(
    context: Context,
    history: List<PlaybackHistoryEntry>,
    librarySongs: List<Song>
): File {
    val databaseFile = File(
        context.cacheDir,
        "cone_trantor_db_${System.currentTimeMillis()}_${System.nanoTime()}"
    )
    val plainZip = File(
        context.cacheDir,
        "cone_history_plain_${System.currentTimeMillis()}_${System.nanoTime()}.zip"
    )
    val encryptedZip = File(
        context.cacheDir,
        "cone_history_${System.currentTimeMillis()}_${System.nanoTime()}.zip"
    )
    try {
        createConeDatabase(databaseFile, history, librarySongs)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(plainZip))).use { zip ->
            zip.writeTransferEntry("_manifest.txt") {
                it.write("trantor_db\n".toByteArray(StandardCharsets.UTF_8))
            }
            zip.writeTransferEntry("trantor_db/trantor_db") { output ->
                databaseFile.inputStream().use { input -> input.copyTo(output) }
            }
        }
        encryptConeArchive(plainZip, encryptedZip)
        return encryptedZip
    } catch (error: Throwable) {
        encryptedZip.delete()
        throw error
    } finally {
        databaseFile.delete()
        plainZip.delete()
    }
}

private fun createConeDatabase(
    databaseFile: File,
    history: List<PlaybackHistoryEntry>,
    librarySongs: List<Song>
) {
    val database = SQLiteDatabase.openOrCreateDatabase(databaseFile.absolutePath, null)
    try {
        database.execSQL("PRAGMA user_version = 24")
        CONE_SCHEMA.forEach(database::execSQL)
        database.beginTransaction()
        try {
            database.insert("android_metadata", null, ContentValues().apply { put("locale", "en_US") })
            history.forEach { entry ->
                val song = librarySongs.firstOrNull { it.id == entry.songId }
                database.insertOrThrow(
                    "AudioReportData",
                    ContentValues().apply {
                        put("event_name", "event_tick")
                        put("event_date", entry.playedAt)
                        put("event_session", "halcyon_${entry.entryId}")
                        put("audio_title", entry.title)
                        put("album_title", entry.album)
                        put("artist", entry.artist)
                        put("album_artist", song?.albumArtist.orEmpty())
                        put("play_time", transferListenedMs(entry))
                        put("list_type", -1)
                        put("list_name", "")
                        put("genre", song?.genre.orEmpty())
                        put("headphone", "")
                        put("playback_device", "")
                    }
                )
            }
            database.insertOrThrow(
                "room_master_table",
                ContentValues().apply {
                    put("id", 42)
                    put("identity_hash", CONE_ROOM_IDENTITY)
                }
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    } finally {
        database.close()
    }
}

private fun File.lunaBeatSignatureBase64(): String {
    // LunaBeat's SecureStorage signs protected playback files with
    // HmacSHA256, using its native default key and standard Base64. A plain
    // file digest looks plausible but is rejected by its restore validator.
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(
        SecretKeySpec(
            LUNA_BEAT_BACKUP_KEY.toByteArray(StandardCharsets.UTF_8),
            "HmacSHA256"
        )
    )
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) mac.update(buffer, 0, count)
        }
    }
    return android.util.Base64.encodeToString(mac.doFinal(), android.util.Base64.NO_WRAP)
}

private fun encryptConeArchive(source: File, target: File) {
    val cipher = Cipher.getInstance(CONE_CIPHER_TRANSFORMATION).apply {
        init(Cipher.ENCRYPT_MODE, CONE_KEY)
    }
    FileOutputStream(target).use { output ->
        CipherOutputStream(BufferedOutputStream(output), cipher).use { encrypted ->
            source.inputStream().buffered().use { input -> input.copyTo(encrypted) }
        }
    }
}

private fun decryptConeArchive(source: File, target: File) {
    val cipher = Cipher.getInstance(CONE_CIPHER_TRANSFORMATION).apply {
        init(Cipher.DECRYPT_MODE, CONE_KEY)
    }
    FileInputStream(source).buffered().use { input ->
        CipherInputStream(input, cipher).use { decrypted ->
            target.outputStream().buffered().use { output ->
                copyToBounded(decrypted, output, MAX_TRANSFER_INPUT_BYTES)
            }
        }
    }
}

private fun fileLooksLikeZip(file: File): Boolean = FileInputStream(file).use { input ->
    val magic = ByteArray(4)
    input.read(magic) >= 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
}

private fun fileLooksLikeJson(file: File): Boolean = FileInputStream(file).buffered().use { input ->
    var result = false
    while (true) {
        val value = input.read()
        if (value < 0) break
        if (!value.toChar().isWhitespace()) {
            result = value == '{'.code || value == '['.code
            break
        }
    }
    result
}

private fun readZipEntryText(file: File, wantedName: String, maxBytes: Long): String? {
    ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name.replace('\\', '/')
            if (!entry.isDirectory && name == wantedName) {
                val bytes = ByteArrayOutputStream()
                copyToBounded(zip, bytes, maxBytes)
                zip.closeEntry()
                return String(bytes.toByteArray(), StandardCharsets.UTF_8)
            }
            zip.closeEntry()
        }
    }
    return null
}

private fun zipHasEntry(file: File, wantedName: String): Boolean {
    ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name.replace('\\', '/')
            if (!entry.isDirectory && name == wantedName) return true
            zip.closeEntry()
        }
    }
    return false
}

private fun extractZipEntries(
    archive: File,
    requested: Map<String, String>
): File {
    val destination = File(
        archive.parentFile ?: archive.absoluteFile.parentFile,
        "playback_history_extract_${System.currentTimeMillis()}_${System.nanoTime()}"
    ).apply { mkdirs() }
    var totalBytes = 0L
    try {
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                val targetName = requested[name]
                if (entry.isDirectory || targetName == null) {
                    zip.closeEntry()
                    continue
                }
                require(targetName.indexOf("..") < 0 && !targetName.startsWith('/')) {
                    "Unsafe playback backup entry"
                }
                val target = File(destination, targetName)
                require(!target.exists()) { "Duplicate playback backup entry" }
                target.parentFile?.mkdirs()
                val written = target.outputStream().buffered().use { output ->
                    copyToBounded(zip, output, MAX_TRANSFER_ENTRY_BYTES)
                }
                totalBytes += written
                require(totalBytes <= MAX_TRANSFER_TOTAL_BYTES) {
                    "Playback backup is too large"
                }
                zip.closeEntry()
            }
        }
    } catch (error: Throwable) {
        destination.deleteRecursively()
        throw error
    }
    return destination
}

private fun readUtf8Bounded(input: InputStream, maxBytes: Long): String {
    val bytes = ByteArrayOutputStream()
    copyToBounded(input, bytes, maxBytes)
    return String(bytes.toByteArray(), StandardCharsets.UTF_8)
}

private fun copyToBounded(input: InputStream, output: OutputStream, maxBytes: Long): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        total += count
        require(total <= maxBytes) { "Playback backup entry is too large" }
        output.write(buffer, 0, count)
    }
    output.flush()
    return total
}

private fun ZipOutputStream.writeTransferEntry(name: String, write: (OutputStream) -> Unit) {
    putNextEntry(ZipEntry(name))
    try {
        write(this)
    } finally {
        closeEntry()
    }
}

private fun SQLiteDatabase.hasTable(name: String): Boolean = rawQuery(
    "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
    arrayOf(name)
).use { it.moveToFirst() }

private fun SQLiteDatabase.insertOrThrow(table: String, values: ContentValues): Long =
    insertOrThrow(table, null, values)

private fun Cursor.stringValue(name: String): String {
    val index = getColumnIndex(name)
    return if (index >= 0 && !isNull(index)) getString(index).orEmpty() else ""
}

private fun Cursor.longValue(name: String): Long {
    val index = getColumnIndex(name)
    return if (index >= 0 && !isNull(index)) getLong(index) else 0L
}

private fun Cursor.intValue(name: String): Int = longValue(name).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

private fun String.toDateEndMillis(): Long? = runCatching {
    SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(this)?.let { date ->
        Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
}.getOrNull()

private fun Long.toTransferDateKey(): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = this@toTransferDateKey }
    return "%04d-%02d-%02d".format(
        Locale.ROOT,
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )
}

private fun Long.toTransferDayBucket(): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = this@toTransferDayBucket }
    return calendar.get(Calendar.YEAR) * 10_000 +
        (calendar.get(Calendar.MONTH) + 1) * 100 +
        calendar.get(Calendar.DAY_OF_MONTH)
}

private fun normalizeTransferTimestamp(value: Long): Long = when {
    value in 1_000_000_000L..10_000_000_000L -> value * 1_000L
    else -> value
}

private val CONE_KEY: SecretKeySpec by lazy {
    val material = "ljflkadsjfliasjglkasdjglsafkoawierpoifasdljglasdjgjoiwaejfl;asedjf"
    SecretKeySpec(
        MessageDigest.getInstance("SHA-256").digest(material.toByteArray(StandardCharsets.UTF_8)),
        "AES"
    )
}

private const val CONE_CIPHER_TRANSFORMATION = "AES/ECB/PKCS5Padding"
private const val CONE_BLANK_SESSION_GAP_MS = 30_000L
private const val MAX_TRANSFER_INPUT_BYTES = 256L * 1024L * 1024L
private const val MAX_TRANSFER_ENTRY_BYTES = 128L * 1024L * 1024L
private const val MAX_TRANSFER_TOTAL_BYTES = 256L * 1024L * 1024L
private const val MAX_TRANSFER_JSON_BYTES = 32L * 1024L * 1024L
private const val LUNA_BEAT_ROOM_IDENTITY = "46ef2b9de1e648c8c140d4be81d8da8b"
private const val LUNA_BEAT_PACKAGE_NAME = "com.example.LyricBox"
private const val LUNA_BEAT_BACKUP_KEY = "244394"
private const val CONE_ROOM_IDENTITY = "44dccf7225fc7ab449cd73ff24209042"

private val LUNA_BEAT_SCHEMA = listOf(
    "CREATE TABLE IF NOT EXISTS android_metadata (locale TEXT)",
    "CREATE TABLE `DailyPlaybackStats` (`date` TEXT NOT NULL, `totalDurationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))",
    "CREATE TABLE `DailySongPlaybackStats` (`date` TEXT NOT NULL, `songKey` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `totalDurationMs` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`, `songKey`))",
    "CREATE TABLE `DailyArtistPlaybackStats` (`date` TEXT NOT NULL, `artistName` TEXT NOT NULL, `totalDurationMs` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`, `artistName`))",
    "CREATE TABLE `DailyAlbumPlaybackStats` (`date` TEXT NOT NULL, `albumKey` TEXT NOT NULL, `albumName` TEXT NOT NULL, `artistName` TEXT NOT NULL, `totalDurationMs` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`, `albumKey`))",
    "CREATE TABLE `SongPlaybackStats` (`songKey` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `totalDurationMs` INTEGER NOT NULL, `playCount` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`songKey`))",
    "CREATE TABLE `ArtistPlaybackStats` (`artistName` TEXT NOT NULL, `totalDurationMs` INTEGER NOT NULL, `playCount` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`artistName`))",
    "CREATE TABLE `AlbumPlaybackStats` (`albumKey` TEXT NOT NULL, `albumName` TEXT NOT NULL, `artistName` TEXT NOT NULL, `totalDurationMs` INTEGER NOT NULL, `playCount` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`albumKey`))",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
)

private val CONE_SCHEMA = listOf(
    "CREATE TABLE IF NOT EXISTS android_metadata (locale TEXT)",
    "CREATE TABLE `StringKV` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `_key` TEXT NOT NULL, `_value` TEXT NOT NULL)",
    "CREATE TABLE `BooleanKV` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `_key` TEXT NOT NULL, `_value` INTEGER NOT NULL)",
    "CREATE TABLE `audio_hourly_trend` (`date` TEXT NOT NULL, `hour` INTEGER NOT NULL, `total_play_time` INTEGER NOT NULL, PRIMARY KEY(`date`, `hour`))",
    "CREATE TABLE `monthly_audio_stats` (`yearMonth` TEXT NOT NULL, `totalPlayTime` INTEGER NOT NULL, `totalPlayCount` INTEGER NOT NULL, `topArtist` TEXT NOT NULL, `topArtistPlayTime` INTEGER NOT NULL, `topAlbum` TEXT NOT NULL, `topAlbumPlayTime` INTEGER NOT NULL, `topSong` TEXT NOT NULL, `topSongPlayTime` INTEGER NOT NULL, PRIMARY KEY(`yearMonth`))",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    "CREATE TABLE `FavArtist` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `artist` TEXT NOT NULL, `fav_time` INTEGER NOT NULL)",
    "CREATE TABLE `FavAlbum` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `album` TEXT NOT NULL, `album_artist` TEXT NOT NULL, `fav_time` INTEGER NOT NULL)",
    "CREATE TABLE `FavDir` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dir` TEXT NOT NULL, `fav_time` INTEGER NOT NULL)",
    "CREATE TABLE `BlockDir` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dir` TEXT NOT NULL, `block_time` INTEGER NOT NULL)",
    "CREATE TABLE `IntKV` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `_key` TEXT NOT NULL, `_value` INTEGER NOT NULL)",
    "CREATE TABLE `playlist` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `coverPath` TEXT, `description` TEXT, `createTime` INTEGER NOT NULL, `updateTime` INTEGER NOT NULL, `isPined` INTEGER NOT NULL DEFAULT false, `type` INTEGER NOT NULL DEFAULT 0)",
    "CREATE TABLE `playlist_song` (`playlistId` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `updateTime` INTEGER NOT NULL DEFAULT 0, `orderIndex` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `title`, `artist`, `album`))",
    "CREATE INDEX `index_playlist_song_playlistId` ON `playlist_song` (`playlistId`)",
    "CREATE INDEX `index_playlist_song_title` ON `playlist_song` (`title`)",
    "CREATE INDEX `index_playlist_song_artist` ON `playlist_song` (`artist`)",
    "CREATE INDEX `index_playlist_song_album` ON `playlist_song` (`album`)",
    "CREATE TABLE `daily_audio_report` (`day` TEXT NOT NULL, `total_play_time` INTEGER NOT NULL, `play_count` INTEGER NOT NULL, `song_count` INTEGER NOT NULL, `album_count` INTEGER NOT NULL, `artist_count` INTEGER NOT NULL, PRIMARY KEY(`day`))",
    "CREATE TABLE `skip_configs` (`targetKey` TEXT NOT NULL, `matchType` INTEGER NOT NULL, `skipIntro` INTEGER NOT NULL, `skipOutro` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`targetKey`, `matchType`))",
    "CREATE TABLE `album_video` (`album` TEXT NOT NULL, `album_artist` TEXT NOT NULL, `video_uri` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`album`, `album_artist`))",
    "CREATE TABLE `audio_video` (`audio_path` TEXT NOT NULL, `video_uri` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`audio_path`))",
    "CREATE TABLE IF NOT EXISTS `AudioReportData` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `event_name` TEXT NOT NULL, `event_date` INTEGER NOT NULL, `event_session` TEXT NOT NULL DEFAULT '', `audio_title` TEXT NOT NULL, `album_title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album_artist` TEXT NOT NULL DEFAULT '', `play_time` INTEGER NOT NULL, `list_type` INTEGER NOT NULL DEFAULT -1, `list_name` TEXT NOT NULL DEFAULT '', `genre` TEXT NOT NULL DEFAULT '', `headphone` TEXT NOT NULL DEFAULT '', `playback_device` TEXT NOT NULL DEFAULT '')",
    "CREATE INDEX `index_AudioReportData_event_date` ON `AudioReportData` (`event_date`)",
    "CREATE TABLE `user_eq_preset` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `gainsJson` TEXT NOT NULL, `bassGain` REAL NOT NULL, `trebleGain` REAL NOT NULL, `preampGain` REAL NOT NULL, `spatialAmount` REAL NOT NULL, `createTime` INTEGER NOT NULL, `updateTime` INTEGER NOT NULL)"
)
