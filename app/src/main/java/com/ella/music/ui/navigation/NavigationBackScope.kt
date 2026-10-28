package com.ella.music.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.navigation.compose.NavBackStackEntryInfo
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHistory
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/** System Back keeps its existing behavior; unclaimed in-app drags can preview the NavHost pop. */
@Composable
internal fun NavigationBackScope(
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = false,
    gestureKey: Any? = null,
    canStartSwipe: () -> Boolean = { true },
    onBackCompleted: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val owner = remember {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher()
        }
    }
    val input = remember { CompletedBackInput() }
    val latestCanStartSwipe by rememberUpdatedState(canStartSwipe)
    val latestOnBackCompleted by rememberUpdatedState(onBackCompleted)
    val background = LocalNavigationPageBackground.current
    var viewportOrigin by remember { mutableStateOf(Offset.Zero) }
    input.afterBack = { latestOnBackCompleted() }

    DisposableEffect(owner, input) {
        owner.navigationEventDispatcher.addInput(input)
        onDispose { owner.navigationEventDispatcher.dispose() }
    }

    // Register on the caller's dispatcher. NavHost and page-local BackHandlers below register
    // on their own dispatcher, preserving their usual priority without receiving gesture progress.
    // A cancelled gesture never invokes this callback, and an empty scope leaves Back to its owner.
    BackHandler(enabled = input.canHandleBack, onBack = input::completeBack)
    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides owner,
        LocalNavigationViewportOrigin provides viewportOrigin
    ) {
        Box(modifier.fillMaxSize().clipToBounds().onGloballyPositioned {
            viewportOrigin = it.positionInRoot() - (background?.origin ?: Offset.Zero)
        }.fullScreenBackGesture(
            enabled = swipeEnabled,
            gestureKey = gestureKey,
            canStart = { input.canPreviewEntry(gestureKey) && latestCanStartSwipe() },
            onStarted = input::startBack,
            onProgressed = input::progressBack,
            onCancelled = input::cancelBack,
            onCompleted = input::completeBack
        )) { content() }
    }
}

private class CompletedBackInput : NavigationEventInput() {
    var canHandleBack by mutableStateOf(false)
        private set
    private var started = false
    private var navigationEntryId: String? = null
    var afterBack: () -> Unit = {}

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        canHandleBack = hasEnabledHandlers
    }

    override fun onHistoryChanged(history: NavigationEventHistory) {
        // Back history also includes underlying handlers. Only the CURRENT handler's info
        // identifies who would actually receive this drag: page-local modes use Info.None.
        navigationEntryId = (history.mergedHistory.getOrNull(history.currentIndex) as? NavBackStackEntryInfo)
            ?.visibleEntry?.id
    }

    fun canPreviewEntry(entryKey: Any?): Boolean =
        canHandleBack && navigationEntryId != null && navigationEntryId == entryKey

    fun startBack(position: Offset) {
        started = true
        dispatchOnBackStarted(backEvent(position, 0f))
    }

    fun progressBack(position: Offset, progress: Float) {
        if (started) dispatchOnBackProgressed(backEvent(position, progress))
    }

    fun cancelBack() {
        if (!started) return
        started = false
        dispatchOnBackCancelled()
    }

    fun completeBack() {
        started = false
        dispatchOnBackCompleted()
        afterBack()
    }

    private fun backEvent(position: Offset, progress: Float) = NavigationEvent(
        swipeEdge = NavigationEvent.EDGE_LEFT,
        touchX = position.x,
        touchY = position.y,
        progress = progress.coerceIn(0f, 1f)
    )
}
