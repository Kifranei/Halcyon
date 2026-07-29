package com.ella.music.data.metadata

import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.audiotag.model.artistPictureOrFallback
import com.lonx.audiotag.model.pictureOfType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricoAudioPictureSelectionTest {

    @Test
    fun `artist selection follows Lyrico artist lead artist band priority`() {
        val band = picture(AudioPictureType.Band, 1)
        val leadArtist = picture(AudioPictureType.LeadArtist, 2)
        val artist = picture(AudioPictureType.Artist, 3)

        assertEquals(artist, listOf(band, leadArtist, artist).artistPictureOrFallback())
        assertEquals(leadArtist, listOf(band, leadArtist).artistPictureOrFallback())
        assertEquals(band, listOf(band).artistPictureOrFallback())
    }

    @Test
    fun `typed picture lookup does not silently return a cover`() {
        val cover = picture(AudioPictureType.FrontCover, 1)

        assertEquals(cover, listOf(cover).pictureOfType(AudioPictureType.FrontCover))
        assertNull(listOf(cover).artistPictureOrFallback())
    }

    private fun picture(type: AudioPictureType, marker: Byte): AudioPicture =
        AudioPicture(
            data = byteArrayOf(marker),
            pictureType = type.tagLibName
        )
}
