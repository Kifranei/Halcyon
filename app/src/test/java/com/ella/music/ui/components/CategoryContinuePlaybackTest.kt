package com.ella.music.ui.components

import com.ella.music.data.*
import com.ella.music.data.model.*
import org.junit.Assert.*
import org.junit.Test

class CategoryContinuePlaybackTest {
    private fun song(id: Long) = Song(id,"song$id","artist","album",1,1000,"/$id.mp3","$id.mp3")
    private fun entry(id: Long,time: Long,category: String,media: String="") = PlaybackHistoryEntry(
        songId=id,title="song$id",artist="artist",album="album",playedAt=time,categorySourceKey=category,mediaUri=media)
    @Test fun recentCategoryEntryOverridesStaleStoredResumeSong() {
        val songs=listOf(song(1),song(2))
        val latest=latestCategoryPlayback(listOf(entry(1,100,"playlist:a"),entry(2,200,"playlist:a")),"playlist:a")
        assertEquals(1,resolveContinuePlaybackIndex(songs,"playlist:a","album:1",null,songs[0].playlistIdentityKey(),latest))
    }
    @Test fun currentPlayingCategoryStillHidesContinueRow() {
        val songs=listOf(song(1),song(2))
        assertEquals(-1,resolveContinuePlaybackIndex(songs,"playlist:a","playlist:a",songs[1],songs[0].playlistIdentityKey(),entry(1,200,"playlist:a")))
    }
    @Test fun anotherCategoryAndMusicVideoDoNotReplaceSongResume() {
        val latest=latestCategoryPlayback(listOf(entry(1,100,"playlist:a"),entry(2,300,"playlist:b"),entry(3,400,"playlist:a","mv:3")),"playlist:a")
        assertEquals(1L,latest?.songId)
    }
    @Test fun removedRecentSongDoesNotFallBackToDifferentStaleSong() {
        val songs=listOf(song(1))
        assertEquals(-1,resolveContinuePlaybackIndex(songs,"playlist:a","album:1",null,songs[0].playlistIdentityKey(),entry(2,200,"playlist:a")))
    }
}
