package com.ella.music.data
import org.junit.Assert.*
import org.junit.Test
class VideoPlaybackSpeedTest {
 @Test fun defaultAndOfferedSpeedsMatchTheRequestedList() {
  assertEquals(1f, VideoPlaybackSpeedState().effective, 0f)
  assertEquals(listOf(.5f,1f,1.25f,1.5f,2f,2.5f,3f,3.5f,4f,5f), VIDEO_PLAYBACK_SPEEDS)
 }
 @Test fun releaseAndCancellationRestoreTheChosenSpeed() {
  val selected=VideoPlaybackSpeedState().select(1.5f)
  val held=selected.hold(2f)
  assertEquals(2f,held.effective,0f);assertEquals(1.5f,held.selected,0f)
  assertEquals(selected,held.release())
  assertEquals(selected,held.release().release())
 }
 @Test fun customHoldSpeedAndSelectionChangesAreIndependent() {
  val state=VideoPlaybackSpeedState().select(1.25f).hold(1.8f).select(3f)
  assertEquals(1.8f,state.effective,0f);assertEquals(3f,state.release().effective,0f)
  assertEquals(180,normalizeVideoHoldSpeedPercent(180))
 }
 @Test fun invalidSpeedValuesCannotReachThePlayer() {
  for(value in listOf(Float.NaN,Float.POSITIVE_INFINITY,0f,-1f,6f)) assertEquals(2f,VideoPlaybackSpeedState().hold(value).effective,0f)
  for(value in listOf(0,49,501,Int.MAX_VALUE)) assertEquals(200,normalizeVideoHoldSpeedPercent(value))
  assertEquals(1f,VideoPlaybackSpeedState().select(1.8f).selected,0f)
 }
}
