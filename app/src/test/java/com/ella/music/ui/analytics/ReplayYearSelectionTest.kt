package com.ella.music.ui.analytics

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.ella.music.data.PlaybackHistoryEntry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class ReplayYearSelectionTest {
    @get:Rule val compose = createComposeRule()
    private val now = date(2026, Calendar.OCTOBER)
    private val history = listOf(
        entry("imported-2025", date(2025, Calendar.AUGUST), 90_000),
        entry("local-2026", date(2026, Calendar.JANUARY), 45_000)
    )
    private val daily = mapOf("2025-08-06" to 90_000L, "2026-01-06" to 45_000L)

    @Test fun importedAndDailyOnlyYearsAppearWithoutInventingFutureYears() {
        assertEquals(listOf(2026, 2025, 2024), buildReplayYears(history,
            daily + mapOf("2024-05-01" to 100L, "2027-01-01" to 100L, "2023-99-01" to 200L), now))
        assertEquals(listOf(2026), buildReplayYears(emptyList(), emptyMap(), now))
    }

    @Test fun pastYearHasAllTwelveMonthsAndReturningToThisYearClampsFutureMonths() {
        val tabs = buildReplayMonthTabs(now = now, year = 2025)
        assertEquals(12, tabs.size)
        assertTrue(tabs.all { it.year == 2025 })
        assertEquals(21, tabs.first().offsetFromCurrent)
        assertEquals(10, tabs.last().offsetFromCurrent)
        assertEquals(0, replayMonthOffset(2026, Calendar.DECEMBER, now))
        assertEquals(10, buildReplayMonthTabs(now = now).size)
    }

    @Test fun tappingTheYearPickerAndPastMonthShowsTheImportedMonthlyReport() {
        val offset = mutableIntStateOf(0)
        var report: MonthlyListeningReport? = null
        compose.setContent {
            val month = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -offset.intValue) }
            report = buildMonthlyListeningReport(history, daily, emptyList(), month)
            MiuixTheme {
                Box(Modifier.size(412.dp, 680.dp)) {
                    MonthlyListeningReportCard(checkNotNull(report),
                        monthTabs = buildReplayMonthTabs(now = now, year = month.get(Calendar.YEAR)),
                        selectedMonthOffset = offset.intValue, onMonthSelected = { offset.intValue = it },
                        availableYears = buildReplayYears(history, daily, now),
                        onYearSelected = { offset.intValue = replayMonthOffset(it, month.get(Calendar.MONTH), now) })
                }
            }
        }
        compose.onNodeWithTag("replay-year-selector").performClick()
        compose.onNodeWithText("2025").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2025, report!!.year) }
        val august = replayMonthOffset(2025, Calendar.AUGUST, now)
        compose.onNodeWithTag("replay-month-$august").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(2025, report!!.year)
            assertEquals(90_000L, report!!.listenedMs)
            assertEquals(1, report!!.playCount)
            assertEquals(1, report!!.uniqueSongCount)
        }
    }

    private fun date(year: Int, month: Int) = Calendar.getInstance().apply {
        clear(); set(year, month, 6, 12, 0, 0)
    }
    private fun entry(id: String, date: Calendar, duration: Long) = PlaybackHistoryEntry(
        entryId = id, songId = -1, title = id, artist = "Imported artist", album = "Album",
        playedAt = date.timeInMillis, listenedMs = duration)
}
