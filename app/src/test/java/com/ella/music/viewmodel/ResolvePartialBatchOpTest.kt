package com.ella.music.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvePartialBatchOpTest {
    @Test
    fun blankOrKeepMatchWithARealReplacementOverwritesEverySong() {
        val blank = resolvePartialBatchOp("ARTIST", "  ", "Ada", listOf("Ada", "Bea"))
        assertFalse(blank.keep)
        assertNull(blank.match)
        assertEquals("Ada", blank.replacement)

        val keepMatch = resolvePartialBatchOp("ARTIST", "<keep>", "Ada", listOf("Ada"))
        assertFalse(keepMatch.keep)
        assertNull(keepMatch.match)
        assertEquals("Ada", keepMatch.replacement)
    }

    @Test
    fun matchThatHitsNothingOverwritesWhenReplacementIsNotKeep() {
        val op = resolvePartialBatchOp("GENRE", "Jazz", "Rock", listOf("Pop", "Soul"))
        assertFalse(op.keep)
        assertNull(op.match)
        assertEquals("Rock", op.replacement)
    }

    @Test
    fun matchThatHitsStaysAPartialReplacement() {
        val op = resolvePartialBatchOp("GENRE", "Pop", "Rock", listOf("Pop / Soul"))
        assertFalse(op.keep)
        assertEquals("Pop", op.match)
        assertEquals("Rock", op.replacement)
    }

    @Test
    fun keepReplacementStaysKeepWhenNothingMatches() {
        val op = resolvePartialBatchOp("GENRE", "", "<KEEP>", listOf("Pop"))
        assertTrue(op.keep)
        assertNull(op.match)
    }
}
