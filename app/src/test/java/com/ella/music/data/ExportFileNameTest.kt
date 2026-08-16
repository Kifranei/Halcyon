package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFileNameTest {
    @Test
    fun replacesEveryUnsupportedExportCharacterWithReadablePunctuation() {
        assertEquals(
            "a／b＼c：d＊e？f｜g〈h〉i'j",
            "a/b\\c:d*e?f|g<h>i\"j".sanitizeExportFileName()
        )
    }

    @Test
    fun usesFallbackWhenTheSanitizedNameIsBlank() {
        assertEquals("Halcyon", "\u0000  .".sanitizeExportFileName())
    }
}
