package com.ella.music.ui.player

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class KaraokeHaloBoundaryTest {
    private fun alpha(stops: Array<Pair<Float,Color>>, x: Float): Float {
        val right = stops.indexOfFirst { it.first >= x }.coerceAtLeast(0)
        if (right == 0) return stops[0].second.alpha
        val left = stops[right-1]
        val next = stops[right]
        val width = next.first-left.first
        if (width <= 0f) return next.second.alpha
        val t = (x-left.first)/width
        return left.second.alpha+(next.second.alpha-left.second.alpha)*t
    }
    @Test fun glowAndFillUseTheSameFeatherThatClearsByTheTimedEnd() {
        val mask=karaokeHaloMaskStops(0.5f,false,0.25f)
        assertEquals(1f,alpha(mask,0.2f),0.001f)
        assertEquals(0.5f,alpha(mask,0.5f),0.001f)
        assertEquals(0f,alpha(mask,0.625f),0.001f)
        assertEquals(0f,alpha(mask,0.8f),0.001f)
    }
    @Test fun rtlFeatherMirrorsTheSameSungBoundary() {
        val mask=karaokeHaloMaskStops(0.5f,true,0.25f)
        assertEquals(0f,alpha(mask,0.2f),0.001f)
        assertEquals(0.5f,alpha(mask,0.5f),0.001f)
        assertEquals(1f,alpha(mask,0.8f),0.001f)
    }
    @Test fun completedWordKeepsItsWholeHalo() {
        assertTrue(karaokeHaloMaskStops(1f,false,0.85f).all { it.second.alpha==1f })
        assertTrue(karaokeHaloMaskStops(0f,false,0.85f).all { it.second.alpha==0f })
    }
    @Test fun inactivePartsOfAHeldWordDoNotRunThePerLetterWave() {
        val word=AppleMusicRenderWord(com.ella.music.data.model.LyricWord("feeling",1000,3000),3000)
        assertFalse(flamingoWaveActiveAt(true,word,7,999))
        assertFalse(flamingoWaveActiveAt(true,word,7,1000))
        assertTrue(flamingoWaveActiveAt(true,word,7,2000))
        assertFalse(flamingoWaveActiveAt(true,word,7,3000))
        assertFalse(flamingoWaveActiveAt(true,word,7,3500))
    }

    @Test fun shortGlyphDoesNotFlashFullyBrightWhenTheNextUnitStarts() {
        for (feather in listOf(.15f, .5f, .85f)) {
            for (rtl in listOf(false, true)) {
                val before = karaokeHaloMaskStops(.9999f, rtl, feather)
                val completed = karaokeHaloMaskStops(1f, rtl, feather)
                for (x in listOf(.05f, .25f, .5f, .75f, .95f)) {
                    assertEquals(alpha(completed, x), alpha(before, x), .003f)
                }
                assertTrue(karaokeHaloMaskStops(0f, rtl, feather).all { it.second.alpha == 0f })
            }
        }
    }
}
