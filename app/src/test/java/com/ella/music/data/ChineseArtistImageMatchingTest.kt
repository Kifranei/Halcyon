package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChineseArtistImageMatchingTest {
    @Test fun biographyFallbackUsesTitleLowerUpperThenOtherCase() {
        assertEquals(3, preferredArtistImageMatch("sweet ARMS", listOf("sWeEt ArMs", "SWEET ARMS", "sweet arms", "Sweet Arms")))
        assertEquals(2, preferredArtistImageMatch("sweet ARMS", listOf("sWeEt ArMs", "SWEET ARMS", "sweet arms")))
        assertEquals(1, preferredArtistImageMatch("sweet ARMS", listOf("sWeEt ArMs", "SWEET ARMS")))
        assertEquals(2, preferredArtistImageMatch("sweet ARMS", listOf("Sweet Arms", "SWEET ARMS", "sweet ARMS")))
    }
    @Test fun exactCaseWinsEvenWhenAnotherSingerAppearsFirst() {
        assertEquals(1, preferredArtistImageMatch("LISA", listOf("LiSA", "LISA")))
        assertEquals(1, preferredArtistImageMatch("LiSA", listOf("LISA", "LiSA")))
    }
    @Test fun decoratedExactCaseWinsBeforeInsensitiveFallback() {
        assertEquals(1, preferredArtistImageMatch("LISA", listOf("LiSA", "LISA（歌手）")))
        assertEquals(1, preferredArtistImageMatch("LISA", listOf("LISA (singer)", "LISA")))
    }
    @Test fun insensitiveMatchingOnlyAppliesWithoutAnExactCandidate() {
        assertEquals(0, preferredArtistImageMatch("LISA", listOf("LiSA", "Other")))
        assertEquals(-1, preferredArtistImageMatch("", listOf("")))
        assertEquals(-1, preferredArtistImageMatch("LISA", listOf("Other")))
    }
    @Test fun kuwoArtistImageUsesLargestCdnVariantOverHttps() {
        assertEquals(
            "https://img1.kuwo.cn/star/starheads/1000/s4s56/58/291211030.jpg",
            kuwoArtistImageUrl("http://img1.kuwo.cn/star/starheads/", "240/s4s56/58/291211030.jpg")
        )
        assertNull(kuwoArtistImageUrl("http://img1.kuwo.cn/star/starheads/", ""))
    }
}
