package com.ella.music.ui.player

internal data class CoverFlowSyncSnapshot(
    val playbackPage: Int, val settledPage: Int, val scrolling: Boolean,
    val dragging: Boolean, val reconcileRevision: Int = 0
)

internal sealed interface CoverFlowSyncCommand {
    data object None : CoverFlowSyncCommand
    data object CancelAnimation : CoverFlowSyncCommand
    data class Select(val page: Int) : CoverFlowSyncCommand
    data class Scroll(val page: Int) : CoverFlowSyncCommand
}

/** Keeps a user's settled selection authoritative while transport acknowledgement is pending. */
internal class CoverFlowPlaybackSync {
    private var gestureActive = false
    private var pendingPage: Int? = null
    private var programmaticPage: Int? = null
    private val pendingHistory = linkedSetOf<Int>()
    var requestSerial: Int = 0
        private set

    fun update(state: CoverFlowSyncSnapshot): CoverFlowSyncCommand {
        if (pendingPage == state.playbackPage) {
            pendingPage = null
            pendingHistory.clear()
        } else if (pendingPage != null && state.playbackPage !in pendingHistory) {
            // A selection from elsewhere in the app supersedes our pending swipe.
            pendingPage = null
            pendingHistory.clear()
        }
        if (state.dragging || (state.scrolling && programmaticPage == null)) {
            gestureActive = true
            if (programmaticPage != null) {
                programmaticPage = null
                return CoverFlowSyncCommand.CancelAnimation
            }
        }
        if (state.scrolling || state.dragging) return CoverFlowSyncCommand.None
        if (gestureActive) {
            gestureActive = false
            programmaticPage = null
            if ((state.settledPage != state.playbackPage && state.settledPage != pendingPage) ||
                (pendingPage != null && state.settledPage != pendingPage)) {
                pendingHistory += state.playbackPage
                pendingHistory += state.settledPage
                // Bound history for a stream of rapid gestures whose acknowledgements lag.
                while (pendingHistory.size > 16) pendingHistory.remove(pendingHistory.first())
                pendingPage = state.settledPage
                requestSerial++
                return CoverFlowSyncCommand.Select(state.settledPage)
            }
            return CoverFlowSyncCommand.None
        }
        if (pendingPage != null) return CoverFlowSyncCommand.None
        if (programmaticPage == state.settledPage) programmaticPage = null
        if (state.playbackPage != state.settledPage && programmaticPage != state.playbackPage) {
            programmaticPage = state.playbackPage
            return CoverFlowSyncCommand.Scroll(state.playbackPage)
        }
        return CoverFlowSyncCommand.None
    }

    fun expirePending(serial: Int): Boolean {
        if (serial != requestSerial || pendingPage == null) return false
        pendingPage = null
        pendingHistory.clear()
        return true
    }
}
