package com.ella.music.ui.components

/**
 * True while a library-backed list is still resolving and would otherwise render as empty.
 * Detail/list screens should show a spinner in this state instead of "未找到".
 */
fun showLibraryLoadingPlaceholder(
    libraryCacheLoaded: Boolean,
    contentResolved: Boolean,
    isEmpty: Boolean
): Boolean = isEmpty && (!libraryCacheLoaded || !contentResolved)
