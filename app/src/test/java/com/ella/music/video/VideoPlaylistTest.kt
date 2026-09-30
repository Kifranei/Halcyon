package com.ella.music.video
import org.junit.Assert.*
import org.junit.Test
class VideoPlaylistTest {
 @Test fun titlesAndRelativeUrlsAreResolvedWithoutLosingQueries() {
  val parsed=parseVideoPlaylist("\uFEFF#EXTM3U\n#EXTINF:-1,Demo\n../movie.mp4?token=x\n#EXTINF:10,Other\nhttps://cdn.example/b.mp4", "https://example.com/list/index.m3u")
  assertFalse(parsed.hls);assertEquals(2,parsed.entries.size)
  assertEquals("Demo",parsed.entries[0].title)
  assertEquals("https://example.com/movie.mp4?token=x",parsed.entries[0].source)
 }
 @Test fun finiteAndLiveHlsAreDistinguished() {
  val finite=parseVideoPlaylist("#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:2.5,\na.ts\n#EXTINF:3.25,\nb.ts\n#EXT-X-ENDLIST", "https://example.com/a.m3u8")
  assertTrue(finite.hls);assertTrue(finite.ended);assertEquals(5750L,finite.durationMs)
  assertFalse(parseVideoPlaylist("#EXTM3U\n#EXT-X-TARGETDURATION:5\n#EXTINF:5,\na.ts", "https://example.com/live.m3u8").ended)
 }
 @Test fun masterPlaylistKeepsVariantUrisAndRejectsExecutableSchemes() {
  val master=parseVideoPlaylist("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\n720/main.m3u8\n# comment\njavascript:alert(1)", "https://example.com/master.m3u8")
  assertTrue(master.master);assertEquals(1,master.entries.size)
  assertEquals("https://example.com/720/main.m3u8",master.entries.single().source)
  assertFalse(isVideoSource("javascript:alert(1)"));assertFalse(isVideoSource("https:/missing-host"))
  assertTrue(isVideoSource("content://documents/video/1"))
 }
 @Test fun hostileAndEmptyFileNamesAreSafe() {
  assertEquals("a_b_c_d.mp4",safeVideoName("a/b:c?d.m3u8","mp4"))
  assertEquals("Video.mp4",safeVideoName("", "mp4"))
  assertTrue(isPlaylistContentType("application/x-mpegURL; charset=utf-8"))
 }
}
