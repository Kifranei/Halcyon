package com.ella.music.data

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.ella.music.data.model.Song
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ConeJsonHistoryImportTest {
    private val base: Context = ApplicationProvider.getApplicationContext()
    private val folder = File(base.cacheDir, "cone-test-${System.nanoTime()}").apply { mkdirs() }
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = folder
    }
    private val store = PlaybackStatsStore::class.java.getDeclaredConstructor(Context::class.java)
        .apply { isAccessible = true }.newInstance(context)
    private val song = Song(42, "Sample", "A / B", "Album", 1, 200_000, "/sample.mp3", "sample.mp3")

    @Test fun matchesLibraryAndKeepsMillisecondsUnknownTracksAndZeroListenTime() = runBlocking {
        val rows = listOf(
            record("Sample", 1756197968920, 187577),
            record("Missing", 1756197969920, 1000).put("artist", "<unknown>"),
            record("Sample", 1756197970920, 0),
            record("Sample", 1756197971920, -20)
        )
        val result = import(rows)
        assertEquals(PlaybackHistoryTransferFormat.CONE_MUSIC, result.format)
        assertEquals(4, result.addedCount)
        val known = store.history.value.single { it.playedAt == 1756197968920 }
        assertEquals(42, known.songId)
        assertEquals(200_000, known.durationMs)
        assertEquals(187577, known.listenedMs)
        val unknown = store.history.value.single { it.title == "Missing" }
        assertTrue(unknown.songId < 0)
        assertEquals("<unknown>", unknown.artist)
        assertEquals(0, unknown.durationMs)
        assertTrue(store.history.value.filter { it.playedAt >= 1756197970920 }.all { it.listenedMs == 0L })
        assertEquals(188577, store.stats.value.sumOf { it.listenedMs })
    }

    @Test fun reorderedAndOverlappingExportsDoNotInflateHistoryOrStatistics() = runBlocking {
        val first = record("Sample", 1756197968920, 187577)
        val repeat = record("Sample", 1756197978920, 4000)
        assertEquals(2, import(listOf(first, repeat, first)).addedCount)
        assertEquals(0, import(listOf(repeat, first)).addedCount)
        assertEquals(2, store.history.value.size)
        assertEquals(2, store.stats.value.single().playCount)
        assertEquals(191577, store.stats.value.single().listenedMs)
        assertEquals(1, import(listOf(first, record("Sample", 1756197998920, 7000))).addedCount)
        assertEquals(3, store.stats.value.single().playCount)
        assertEquals(198577, store.stats.value.single().listenedMs)
    }

    @Test fun prefersEpochTimestampAndSkipsMalformedRowsWithDateFallback() = runBlocking {
        val rows = listOf(
            record("Sample", 1756197968920, 1).put("eventDate", "not a date"),
            record("Missing", 0, 2).put("eventDate", "2025-08-26 16:46:08"),
            record("Malformed", 0, 3).put("eventDate", "2025-99-99 12:00:00"),
            JSONObject().put("eventTimestamp", 1756197980000)
        )
        assertEquals(2, import(rows).addedCount)
        assertEquals(1756197968920, store.history.value.single { it.title == "Sample" }.playedAt)
        assertEquals(3, store.history.value.sumOf { it.listenedMs })
    }

    @Test fun existingHalcyonAndPrismJsonStillUseTheirOriginalAdapters() = runBlocking {
        val history = JSONObject().put("title", "Sample").put("artist", "A / B").put("album", "Album")
            .put("playedAt", 1756197968920).put("listenedMs", 1234)
        val native = JSONObject().put("history", JSONArray().put(history))
        assertEquals(PlaybackHistoryTransferFormat.HALCYON, importRoot(native).format)
        val prism = JSONObject().put("sessions", JSONArray().put(JSONObject()
            .put("title", "Sample").put("endedAtMs", 1756197969920).put("playedMs", 5678)))
        assertEquals(PlaybackHistoryTransferFormat.PRISM_MUSIC, importRoot(prism).format)
        assertEquals(6912, store.history.value.sumOf { it.listenedMs })
    }

    @Test fun attachedFullExportImportsOnceAndKeepsEveryRecordAndListenDuration() = runBlocking {
        // Optional local verification: personal listening data never becomes a repo fixture.
        val path = System.getenv("HALCYON_CONE_HISTORY_FIXTURE")
        assumeTrue(!path.isNullOrBlank() && File(path).isFile)
        val file = File(checkNotNull(path))
        val rows = JSONObject(file.readText()).getJSONArray("records")
        val result = importPlaybackHistoryFromUri(context, Uri.fromFile(file), emptyList(), store)
        assertEquals(5968, rows.length())
        assertEquals(rows.length(), result.addedCount)
        val total = (0 until rows.length()).sumOf { rows.getJSONObject(it).getLong("playTime") }
        assertEquals(402721811, total)
        assertEquals(total, store.history.value.sumOf { it.listenedMs })
        assertEquals(total, store.stats.value.sumOf { it.listenedMs })
        assertEquals(total, store.dailyListenMs.value.values.sum())
        val replayNow = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.OCTOBER, 6) }
        assertEquals(setOf(2026, 2025), com.ella.music.ui.analytics.buildReplayYears(
            store.history.value, store.dailyListenMs.value, replayNow).toSet())
        assertEquals(0, importPlaybackHistoryFromUri(context, Uri.fromFile(file), emptyList(), store).addedCount)
    }

    @Test fun moreThanSixThousandRecordsSurviveSavingAndReopeningBothStores() = runBlocking {
        // Isolate the immediate history singleton from other import cases.
        val singleton = RecentPlaybackStore::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val original = singleton.get(null)
        fun recent() = RecentPlaybackStore::class.java.getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }.newInstance(context)
        fun stats() = PlaybackStatsStore::class.java.getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }.newInstance(context)
        try {
            singleton.set(null, recent())
            val isolated = stats()
            val count = 7205
            val rows = JSONArray().apply {
                repeat(count) { put(record("Track ${it % 37}", 1_750_000_000_000L + it * 300_000L, 120_000)) }
            }
            val file = File(folder, "capacity.json").apply { writeText(JSONObject().put("records", rows).toString()) }
            assertEquals(count, importPlaybackHistoryFromUri(context, Uri.fromFile(file), emptyList(), isolated).addedCount)
            assertEquals(count, isolated.history.value.size)
            assertEquals(count, isolated.recentHistory.value.size)
            val reopenedRecent = recent()
            singleton.set(null, reopenedRecent)
            val reopened = stats()
            assertEquals(count, reopened.history.value.size)
            assertEquals(count, reopenedRecent.history.value.size)
            assertEquals(isolated.history.value.map { it.entryId }, reopened.history.value.map { it.entryId })
            assertEquals(count * 120_000L, reopened.history.value.sumOf { it.listenedMs })
        } finally { singleton.set(null, original) }
    }

    private fun record(title: String, timestamp: Long, listened: Long): JSONObject = JSONObject()
        .put("audioTitle", title).put("artist", "A / B").put("albumTitle", "Album").put("albumArtist", "")
        .put("eventTimestamp", timestamp).put("eventDate", "2025-08-26 16:46:08").put("playTime", listened)

    private suspend fun import(rows: List<JSONObject>) = importRoot(JSONObject()
        .put("appVersion", "v1.4.0-dev(sample)").put("exportedAt", "2026-10-05 20:14:40")
        .put("records", JSONArray(rows)))

    private suspend fun importRoot(root: JSONObject): PlaybackHistoryImportResult {
        val file = File(folder, "history-${System.nanoTime()}.json").apply { writeText(root.toString()) }
        return importPlaybackHistoryFromUri(context, Uri.fromFile(file), listOf(song), store)
    }
}
