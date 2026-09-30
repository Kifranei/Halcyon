package com.ella.music.data.netease
import org.junit.Test
import org.junit.Assert.*

class NeteaseCommentResourceTest {
    @Test fun allNativeCommentKindsUseTheirOwnThreadNamespace() {
        assertEquals("R_SO_4_123", NeteaseLinkKind.Comment.commentResource()!!.threadId("123"))
        assertEquals("R_AL_3_123", NeteaseLinkKind.AlbumComment.commentResource()!!.threadId("123"))
        assertEquals("R_MV_5_123", NeteaseLinkKind.MusicVideoComment.commentResource()!!.threadId("123"))
        assertNull(NeteaseLinkKind.Album.commentResource())
        assertNull(NeteaseLinkKind.MusicVideo.commentResource())
    }
    @Test(expected = IllegalArgumentException::class) fun invalidResourceIdsCannotCreateThreads() {
        NeteaseCommentResource.Album.threadId("0")
    }
}
