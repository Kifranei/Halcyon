package com.ella.music.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.ella.music.isSearchRoute

class LibrarySearchDockState(initialQuery: String = "") {
    var query by mutableStateOf(initialQuery)
    var autoFocus by mutableStateOf<Boolean?>(null)
    var selectAll by mutableStateOf(false)
    var onSearch by mutableStateOf<(() -> Unit)?>(null)
}

val LocalLibrarySearchDockState = staticCompositionLocalOf<LibrarySearchDockState?> { null }

@Composable
internal fun rememberLibrarySearchDockState(): LibrarySearchDockState {
    var savedQuery by rememberSaveable { mutableStateOf("") }
    val state = remember { LibrarySearchDockState(savedQuery) }
    SideEffect {
        if (savedQuery != state.query) savedQuery = state.query
    }
    return state
}

internal fun usesSearchBottomDock(currentRoute: String?): Boolean = currentRoute.isSearchRoute()

internal fun searchDockReturnTabRoute(
    currentRoute: String?,
    currentTabRoute: String?,
    lastTabRoute: String?
): String? {
    if (currentRoute.isSearchRoute()) return lastTabRoute ?: currentTabRoute
    return currentTabRoute ?: lastTabRoute
}

internal fun applySearchReopenQuery(
    behavior: Int,
    currentQuery: String,
    incomingQuery: String?
): String {
    incomingQuery?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    return when (behavior) {
        com.ella.music.data.SettingsManager.SEARCH_REOPEN_CLEAR -> ""
        else -> currentQuery
    }
}

internal fun searchReopenSelectsQuery(behavior: Int, query: String): Boolean =
    behavior == com.ella.music.data.SettingsManager.SEARCH_REOPEN_SELECT && query.isNotBlank()
