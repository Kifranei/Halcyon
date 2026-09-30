package com.ella.music.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/** Keeps pages still during a system back gesture, then dispatches the completed back normally. */
@Composable
internal fun NavigationBackScope(content: @Composable () -> Unit) {
    val owner = remember {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher()
        }
    }
    val input = remember { CompletedBackInput() }

    DisposableEffect(owner, input) {
        owner.navigationEventDispatcher.addInput(input)
        onDispose { owner.navigationEventDispatcher.dispose() }
    }

    // Register on the caller's dispatcher. NavHost and page-local BackHandlers below register
    // on their own dispatcher, preserving their usual priority without receiving gesture progress.
    // A cancelled gesture never invokes this callback, and an empty scope leaves Back to its owner.
    BackHandler(enabled = input.canHandleBack, onBack = input::completeBack)
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner, content = content)
}

private class CompletedBackInput : NavigationEventInput() {
    var canHandleBack by mutableStateOf(false)
        private set

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        canHandleBack = hasEnabledHandlers
    }

    fun completeBack() = dispatchOnBackCompleted()
}
