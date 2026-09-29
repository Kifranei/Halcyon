package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistImageStorageTest {
    @Test fun internalStorageNeverFallsBackToTheCoverFolder() {
        assertEquals("", artistImageDownloadFolder(ARTIST_IMAGE_INTERNAL_STORAGE, "content://covers"))
        assertEquals("content://covers", artistImageDownloadFolder("", "content://covers"))
        assertEquals("content://downloads", artistImageDownloadFolder("content://downloads", "content://covers"))
    }
}
