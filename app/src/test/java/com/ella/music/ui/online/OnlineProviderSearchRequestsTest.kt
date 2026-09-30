package com.ella.music.ui.online

import org.junit.Assert.*
import org.junit.Test

class OnlineProviderSearchRequestsTest {
    @Test fun switchingBeforeFirstSubmissionDoesNotSearch() {
        assertNull(OnlineProviderSearchRequests().request("醒来折花", automatic = true))
    }
    @Test fun switchingAfterSubmissionUsesTheCurrentEditedQuery() {
        val requests = OnlineProviderSearchRequests()
        requests.request("old")
        assertEquals("醒来折花", requests.request("  醒来折花  ", automatic = true)!!.query)
    }
    @Test fun clearingQueryPreventsAutomaticRequests() {
        val requests = OnlineProviderSearchRequests()
        requests.request("query")
        assertNull(requests.request("  ", automatic = true))
    }
    @Test fun oldProviderCannotOverwriteNewProviderResults() {
        val requests = OnlineProviderSearchRequests()
        val first = requests.request("query")!!
        val second = requests.request("query", automatic = true)!!
        assertFalse(requests.isCurrent(first))
        assertTrue(requests.isCurrent(second))
        requests.invalidate()
        assertFalse(requests.isCurrent(second))
    }

    @Test fun lxSearchAppendsSecondAndThirdPagesBeyondThirtySongs() {
        val requests = OnlineProviderSearchRequests()
        var results = emptyList<Int>()
        repeat(3) { index ->
            val request = if (index == 0) requests.request("query")!! else requests.nextPage()!!
            assertEquals(index + 1, request.page)
            val before = results.size
            results = appendDistinctSearchResults(results, (index * 30 until (index + 1) * 30).toList()) { it }
            requests.complete(request, results.size - before)
        }
        assertEquals((0 until 90).toList(), results)
        assertTrue(requests.hasMore)
    }

    @Test fun musicFreeDoesNotAssumeThirtySongsPerPageAndHonorsTheFinalPage() {
        val requests = OnlineProviderSearchRequests()
        requests.complete(requests.request("query")!!, addedCount = 20, isEnd = false)
        val second = requests.nextPage()!!
        assertEquals(2, second.page)
        requests.complete(second, addedCount = 7, isEnd = true)
        assertFalse(requests.hasMore)
        assertNull(requests.nextPage())
    }

    @Test fun onlyOnePageCanBeInFlight() {
        val requests = OnlineProviderSearchRequests()
        val first = requests.request("query")!!
        assertNull(requests.nextPage())
        requests.complete(first, 30)
        assertNotNull(requests.nextPage())
        assertNull(requests.nextPage())
    }

    @Test fun loadingMoreRetainsTheSubmittedQueryUntilAnotherSearch() {
        val requests = OnlineProviderSearchRequests()
        requests.complete(requests.request("  first query  ")!!, 30)
        assertEquals("first query", requests.nextPage()!!.query)
        val newSearch = requests.request("second query")!!
        assertEquals(1, newSearch.page)
        assertEquals("second query", newSearch.query)
    }

    @Test fun switchingProviderDiscardsAnOldPageAndRestartsAtPageOne() {
        val requests = OnlineProviderSearchRequests()
        requests.complete(requests.request("query")!!, 30)
        val oldPage = requests.nextPage()!!
        requests.invalidate()
        val newSearch = requests.request("query", automatic = true)!!
        assertEquals(1, newSearch.page)
        assertFalse(requests.isCurrent(oldPage))
        requests.complete(oldPage, 30)
        requests.fail(oldPage)
        requests.cancel(oldPage)
        assertTrue(requests.isLoading)
        assertFalse(requests.failed)
        requests.complete(newSearch, 20, isEnd = false)
        assertEquals(2, requests.nextPage()!!.page)
    }

    @Test fun failedPageWaitsForManualRetryAndRetainsItsPageNumber() {
        val requests = OnlineProviderSearchRequests()
        requests.complete(requests.request("query")!!, 30)
        val second = requests.nextPage(automatic = true)!!
        requests.fail(second)
        assertTrue(requests.failed)
        assertFalse(requests.isLoading)
        assertNull(requests.nextPage(automatic = true))
        val retry = requests.nextPage()!!
        assertEquals(second.page, retry.page)
        assertFalse(requests.failed)
        requests.complete(retry, 30)
        assertEquals(3, requests.nextPage()!!.page)
    }

    @Test fun emptyOrRepeatedPagesStopInsteadOfLoadingForever() {
        for (incoming in listOf(emptyList(), (0 until 30).toList())) {
            val requests = OnlineProviderSearchRequests()
            val current = (0 until 30).toList()
            requests.complete(requests.request("query")!!, current.size)
            val second = requests.nextPage()!!
            val combined = appendDistinctSearchResults(current, incoming) { it }
            requests.complete(second, combined.size - current.size, isEnd = false)
            assertEquals(current, combined)
            assertFalse(requests.hasMore)
            assertNull(requests.nextPage())
        }
    }

    @Test fun overlappingPagesKeepOrderAndOnlyAppendNewSongs() {
        assertEquals((0 until 45).toList(), appendDistinctSearchResults(
            (0 until 30).toList(), (20 until 45).toList() + listOf(44, 44)
        ) { it })
    }

    @Test fun leavingDuringAPageLoadAllowsTheSamePageToResume() {
        val requests = OnlineProviderSearchRequests()
        requests.complete(requests.request("query")!!, 30)
        val second = requests.nextPage()!!
        requests.cancel(second)
        assertFalse(requests.isLoading)
        val retry = requests.nextPage(automatic = true)!!
        assertEquals(2, retry.page)
        assertFalse(requests.isCurrent(second))
        requests.complete(second, 30)
        requests.cancel(second)
        assertTrue(requests.isLoading)
        requests.complete(retry, 30)
        assertEquals(3, requests.nextPage()!!.page)
    }
}
