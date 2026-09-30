package com.ella.music.ui.player

import org.junit.Assert.*
import org.junit.Test

class CoverFlowPlaybackSyncTest {
    private fun state(playback: Int, page: Int, moving: Boolean = false, dragging: Boolean = false) =
        CoverFlowSyncSnapshot(playback, page, moving, dragging)
    @Test fun swipeCommitsOnceAndOldPlaybackCannotPullItBack() {
        val sync = CoverFlowPlaybackSync()
        assertEquals(CoverFlowSyncCommand.None, sync.update(state(0, 0)))
        sync.update(state(0, 0, true, true))
        assertEquals(CoverFlowSyncCommand.Select(1), sync.update(state(0, 1)))
        repeat(5) { assertEquals(CoverFlowSyncCommand.None, sync.update(state(0, 1))) }
        assertEquals(CoverFlowSyncCommand.None, sync.update(state(1, 1)))
    }
    @Test fun delayedAcknowledgementOfThePreviousSwipeDoesNotCancelTheNext() {
        val sync = CoverFlowPlaybackSync()
        sync.update(state(0, 0, true, true))
        sync.update(state(0, 1))
        val oldSerial = sync.requestSerial
        sync.update(state(0, 1, true, true))
        assertEquals(CoverFlowSyncCommand.Select(2), sync.update(state(0, 2)))
        assertFalse(sync.expirePending(oldSerial))
        assertEquals(CoverFlowSyncCommand.None, sync.update(state(1, 2)))
        assertEquals(CoverFlowSyncCommand.None, sync.update(state(2, 2)))
    }
    @Test fun touchingDuringExternalAnimationLetsTheFingerTakeOver() {
        val sync = CoverFlowPlaybackSync()
        assertEquals(CoverFlowSyncCommand.Scroll(2), sync.update(state(2, 0)))
        assertEquals(CoverFlowSyncCommand.CancelAnimation, sync.update(state(2, 0, true, true)))
        assertEquals(CoverFlowSyncCommand.Select(1), sync.update(state(2, 1)))
    }
    @Test fun programmaticSettlingDoesNotSelectTheOldTrackAgain() {
        val sync = CoverFlowPlaybackSync()
        sync.update(state(2, 0))
        assertEquals(CoverFlowSyncCommand.None, sync.update(state(2, 0, true)))
        assertEquals(CoverFlowSyncCommand.None, sync.update(state(2, 2)))
    }
    @Test fun timedOutOrExternallyOverriddenSelectionCanReconcile() {
        val sync = CoverFlowPlaybackSync()
        sync.update(state(0, 0, true, true))
        sync.update(state(0, 1))
        assertTrue(sync.expirePending(sync.requestSerial))
        assertEquals(CoverFlowSyncCommand.Scroll(0), sync.update(state(0, 1)))
    }
}
