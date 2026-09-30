package com.ella.music.ui.album

internal data class AlbumGridScrollTarget(val index: Int, val offset: Int, val restoredRequest: Int)

internal fun albumGridScrollTarget(
    orderingReady: Boolean,
    albumIds: List<Long>,
    request: Int,
    restoredRequest: Int,
    anchorId: Long?,
    anchorOffset: Int,
    needsInitialPosition: Boolean
): AlbumGridScrollTarget? {
    if (!orderingReady || albumIds.isEmpty()) return null
    if (request > restoredRequest && anchorId != null) {
        val index = albumIds.indexOf(anchorId).coerceAtLeast(0)
        return AlbumGridScrollTarget(index, anchorOffset.coerceAtLeast(0), request)
    }
    if (needsInitialPosition) return AlbumGridScrollTarget(0, 0, restoredRequest)
    return null
}
