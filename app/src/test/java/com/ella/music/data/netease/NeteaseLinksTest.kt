package com.ella.music.data.netease

import org.junit.Assert.*
import org.junit.Test

class NeteaseLinksTest {
    @Test fun commentSchemesAndArtistWikiPresets() {
        assertEquals("orpheus://comment?threadId=R_AL_3_123", NeteaseLinks.build(NeteaseLinkSettings(NeteaseLinkTarget.App), NeteaseLinkKind.AlbumComment, "123"))
        assertEquals("honororpheus://comment?threadId=R_MV_5_123", NeteaseLinks.build(NeteaseLinkSettings(NeteaseLinkTarget.HonorApp), NeteaseLinkKind.MusicVideoComment, "123"))
        assertEquals("orpheus://rnpage?component=music-reactnative-artistwiki&artistId=123", NeteaseLinks.build(NeteaseLinkSettings(NeteaseLinkTarget.App), NeteaseLinkKind.ArtistWiki, "123"))
    }
    @Test fun customIntentTemplateKeepsFragmentAndSubstitutesId() {
        val template = "intent://artist/{id}#Intent;scheme=orpheus;package=com.netease.cloudmusic;end"
        assertEquals(template.replace("{id}", "123"), NeteaseLinks.build(NeteaseLinkSettings(NeteaseLinkTarget.Custom, custom = mapOf(NeteaseLinkKind.Artist to template)), NeteaseLinkKind.Artist, "123"))
    }
    @Test fun webCommentDefaultsAreSeparateResources() {
        assertEquals("https://music.163.com/#/album?id=123", NeteaseLinks.webUrl(NeteaseLinkKind.AlbumComment, "123"))
        assertEquals("https://music.163.com/#/mv?id=123", NeteaseLinks.webUrl(NeteaseLinkKind.MusicVideoComment, "123"))
    }
}
