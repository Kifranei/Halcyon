package com.ella.music.ui.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Owns a submitted query and its pages; late responses cannot replace a newer search. */
internal class OnlineProviderSearchRequests {
    data class Request(val query: String, val revision: Long, val page: Int = 1, val attempt: Long = 0L)
    var revision by mutableStateOf(0L)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private var submitted = false
    private var submittedQuery = ""
    private var completedPage = 0
    private var pending: Request? = null
    private var latest: Request? = null
    private var attempt = 0L

    fun request(query: String, automatic: Boolean = false): Request? {
        val text = query.trim()
        if (text.isEmpty() || (automatic && !submitted)) return null
        invalidate()
        submitted = true
        submittedQuery = text
        return start(Request(text, revision))
    }

    fun nextPage(automatic: Boolean = false): Request? {
        if (isLoading || !hasMore || (automatic && failed)) return null
        return start(Request(submittedQuery, revision, completedPage + 1))
    }

    private fun start(request: Request): Request {
        val started = request.copy(attempt = ++attempt)
        pending = started
        latest = started
        isLoading = true
        failed = false
        return started
    }

    fun complete(request: Request, addedCount: Int, isEnd: Boolean? = null) {
        if (!isCurrent(request) || pending != request) return
        completedPage = request.page
        // Empty or repeated pages also stop plugins that ignore the requested page number.
        hasMore = addedCount > 0 && isEnd != true
        pending = null
        isLoading = false
    }

    fun fail(request: Request) {
        if (!isCurrent(request) || pending != request) return
        pending = null
        isLoading = false
        failed = true
    }

    fun cancel(request: Request) {
        if (!isCurrent(request) || pending != request) return
        pending = null
        isLoading = false
    }

    fun invalidate() {
        revision++
        pending = null
        latest = null
        completedPage = 0
        isLoading = false
        hasMore = false
        failed = false
    }
    fun isCurrent(request: Request): Boolean = request.revision == revision && request == latest
}

internal fun <T, K> appendDistinctSearchResults(current: List<T>, incoming: List<T>, key: (T) -> K): List<T> {
    val seen = current.mapTo(HashSet(), key)
    return current + incoming.filter { seen.add(key(it)) }
}
