package com.ella.music.data

import java.io.StringReader
import org.junit.Assert.*
import org.junit.Test

class BoundedAppLogBufferTest {
    @Test fun prolongedDecoderLoggingKeepsRecentEntriesWithinTheBudget() {
        val buffer = BoundedAppLogBuffer(maxChars = 16_384)
        repeat(100_000) { index ->
            buffer.add(AppLogEntry(index.toLong(), "DEBUG", "Decoder", "Frame $index " + "x".repeat(256)))
            assertTrue(buffer.retainedChars <= 16_384)
        }
        val entries = buffer.snapshot()
        assertEquals(99_999L, entries.first().time)
        assertTrue(entries.size < 100)
        buffer.clear()
        assertEquals(0, buffer.retainedChars)
        assertTrue(buffer.snapshot().isEmpty())
    }
    @Test fun giantSingleEntryAndNativeContinuationCannotGrowWithoutLimit() {
        val buffer = BoundedAppLogBuffer()
        buffer.add(AppLogEntry(1, "ERROR", "Decoder", "Error", detail = "x".repeat(500_000)))
        assertTrue(buffer.snapshot().single().detail!!.length <= 32_768)
        var pending = AppLogEntry(1, "ERROR", "Decoder", "Error")
        repeat(2000) {
            pending = AppLogcatParser.consumeLine("at " + "x".repeat(1000), pending).second!!
        }
        assertTrue(pending.detail!!.length <= 32_768)
    }
    @Test fun ordinaryExportStaysCompleteAndOversizedDumpStopsAtTheSafetyLimit() {
        assertEquals("small log", StringReader("small log").readBoundedLogText(1024))
        val result = StringReader("x".repeat(100_000)).readBoundedLogText(8192)
        assertTrue(result.startsWith("x".repeat(8192)))
        assertTrue(result.length < 8300)
    }
}
