package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSearchHistoryTest {
    @Test
    fun recordPutsNewestQueryFirstAndDropsDuplicates() {
        val next = SettingsSearchHistory.record(
            current = listOf("主题", "均衡器"),
            query = "  主题  "
        )
        assertEquals(listOf("主题", "均衡器"), next)
    }

    @Test
    fun recordIgnoresBlankQueriesAndRespectsLimit() {
        assertEquals(emptyList<String>(), SettingsSearchHistory.record(emptyList(), "  "))
        val filled = (1..20).fold(emptyList<String>()) { acc, index ->
            SettingsSearchHistory.record(acc, "query-$index")
        }
        assertEquals(SettingsSearchHistory.LIMIT, filled.size)
        assertEquals("query-20", filled.first())
        assertEquals("query-9", filled.last())
    }
}
