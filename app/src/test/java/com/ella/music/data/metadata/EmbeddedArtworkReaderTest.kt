package com.ella.music.data.metadata

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmbeddedArtworkReaderTest {
    @Test
    fun extractCoverFromUploadedMedia0OtherPng() {
        val file = File("""C:\Users\Croilan\.gemini\antigravity\brain\3b2742e2-6450-4b82-8a74-b3ca2f6a0f6b\.user_uploaded\uploaded_media_0_1789189940201.mp3""")
        if (!file.exists()) return
        val cover = EmbeddedArtworkReader.extractCoverArt(file)
        assertNotNull("Expected cover art from uploaded_media_0", cover)
        assertTrue("Expected PNG header in extracted cover", cover!!.size > 100)
        assertTrue(
            "PNG magic check",
            cover[0] == 0x89.toByte() &&
                cover[1] == 'P'.code.toByte() &&
                cover[2] == 'N'.code.toByte() &&
                cover[3] == 'G'.code.toByte()
        )
    }

    @Test
    fun extractCoverFromUploadedMedia1LargeJpeg() {
        val file = File("""C:\Users\Croilan\.gemini\antigravity\brain\3b2742e2-6450-4b82-8a74-b3ca2f6a0f6b\.user_uploaded\uploaded_media_1_1789189940201.mp3""")
        if (!file.exists()) return
        val cover = EmbeddedArtworkReader.extractCoverArt(file)
        assertNotNull("Expected cover art from uploaded_media_1", cover)
        assertTrue("Expected JPEG header in extracted cover", cover!!.size > 100)
        assertTrue(
            "JPEG magic check",
            cover[0] == 0xFF.toByte() &&
                cover[1] == 0xD8.toByte() &&
                cover[2] == 0xFF.toByte()
        )
    }
}
