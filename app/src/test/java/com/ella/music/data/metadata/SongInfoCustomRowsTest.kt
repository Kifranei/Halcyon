package com.ella.music.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class SongInfoCustomRowsTest {
    @Test fun producerAndCustomTagsStaySeparateAndSorted() {
        val raw = mapOf(
            "GROUPING" to listOf("夜晚"),
            "PRODUCER" to listOf("A Producer"),
            "LABEL" to listOf("Halcyon"),
            "COPYRIGHT" to listOf("2026"),
            "LANGUAGE" to listOf("zho"),
            "ARRANGER" to listOf("Arranger")
        )
        assertEquals("A Producer", raw.producerTagValue())
        assertEquals(listOf("GROUPING" to "夜晚", "LABEL" to "Halcyon"), raw.songInfoCustomRows())
    }
}
