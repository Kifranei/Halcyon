package com.ella.music.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryContentGateTest {
    @Test
    fun emptyListShowsSpinnerUntilDerivedContentResolves() {
        assertTrue(
            showLibraryLoadingPlaceholder(
                libraryCacheLoaded = true,
                contentResolved = false,
                isEmpty = true
            )
        )
    }

    @Test
    fun resolvedEmptyListIsARealEmptyState() {
        assertFalse(
            showLibraryLoadingPlaceholder(
                libraryCacheLoaded = true,
                contentResolved = true,
                isEmpty = true
            )
        )
    }

    @Test
    fun populatedListNeverShowsLoadingPlaceholder() {
        assertFalse(
            showLibraryLoadingPlaceholder(
                libraryCacheLoaded = false,
                contentResolved = false,
                isEmpty = false
            )
        )
    }
}
