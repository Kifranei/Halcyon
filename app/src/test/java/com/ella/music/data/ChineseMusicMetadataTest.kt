package com.ella.music.data

import com.ella.music.data.lastfm.*
import org.junit.Assert.*
import org.junit.Test

class ChineseMusicMetadataTest {
    @Test fun albumMatchRequiresArtistAndTitle() {
        assertTrue(metadataAlbumMatches("范特西", "周杰伦", "范特西", "周杰伦"))
        assertFalse(metadataAlbumMatches("范特西", "周杰伦", "范特西", "翻唱歌手"))
        assertFalse(metadataAlbumMatches("范特西", "周杰伦", "依然范特西", "周杰伦"))
        assertTrue(metadataAlbumMatches("Back In Black", "AC/DC", "Back In Black", "AC/DC"))
    }

    @Test fun legacyChineseEncodingAndProviderMarkupAreDecoded() {
        assertEquals("周杰伦", decodeMusicMetadata("周杰伦".toByteArray(charset("GB18030"))))
        assertEquals("周杰伦", decodeMusicMetadata("周杰伦".toByteArray(Charsets.UTF_8)))
        assertEquals("A\nB 'C'", cleanMusicMetadata("<![CDATA[A<br>B&nbsp;&apos;C&apos;]]>"))
        assertEquals("A\nB", cleanMusicMetadata("A\\\\n;B"))
    }

    @Test fun selectedBiographyProviderNeverFallsBackToAnotherPlatform() {
        assertEquals(listOf(ArtistWikiSource.QQ), artistWikiSourceOrder("zh", true, ArtistBioMenuSource.QQ))
        assertEquals(listOf(ArtistWikiSource.Kugou), artistWikiSourceOrder("zh", true, ArtistBioMenuSource.Kugou))
        assertEquals(listOf(ArtistWikiSource.Kuwo), artistWikiSourceOrder("zh", true, ArtistBioMenuSource.Kuwo))
        assertEquals(AlbumInfoSource.Netease, AlbumInfoSource.fromId("obsolete"))
    }
}
