package com.ella.music.player

import com.ella.music.data.model.Song
import kotlin.random.Random

internal data class ShuffleQueuePlan(
    val queue: List<Song>,
    val currentIndex: Int
)

internal fun buildShuffleQueueKeepingCurrent(
    sourceOrder: List<Song>,
    current: Song,
    currentIndexHint: Int?,
    seed: Long
): ShuffleQueuePlan? {
    if (sourceOrder.size <= 1) return null
    val hintedIndex = currentIndexHint
        ?.takeIf { it in sourceOrder.indices }
        ?.takeIf { sourceOrder[it].isSamePlaybackIdentity(current) }
    val currentIndex = hintedIndex ?: sourceOrder.indexOfFirst { it.isSamePlaybackIdentity(current) }
        .takeIf { it >= 0 }
        ?: return null
    val currentSong = sourceOrder[currentIndex]
    val shuffled = sourceOrder
        .filterIndexed { index, _ -> index != currentIndex }
        .shuffled(Random(seed))
    return ShuffleQueuePlan(
        queue = listOf(currentSong) + shuffled,
        currentIndex = 0
    )
}

/** Builds the next pseudo-shuffle round without repeating the previous round's last song first. */
internal fun buildShuffleQueueForCycle(
    sourceOrder: List<Song>,
    previousLast: Song,
    seed: Long
): ShuffleQueuePlan? {
    if (sourceOrder.size <= 1) return null
    val occurrences = sourceOrder.mapIndexed { index, song -> index to song }
        .shuffled(Random(seed))
    val first = occurrences.firstOrNull { (_, song) ->
        !song.isSamePlaybackIdentity(previousLast)
    } ?: return null
    val queue = buildList {
        add(first.second)
        occurrences.filterNot { it.first == first.first }.forEach { add(it.second) }
    }
    return ShuffleQueuePlan(queue = queue, currentIndex = 0)
}

internal fun shouldDeferShuffleReorder(
    enableShuffle: Boolean,
    previousShuffle: Boolean,
    queueSize: Int,
    hasVirtualQueue: Boolean
): Boolean {
    return enableShuffle &&
        !previousShuffle &&
        queueSize > 1 &&
        !hasVirtualQueue
}

internal enum class PendingShuffleReorderAction {
    None,
    Materialize,
    Clear
}

internal fun pendingShuffleReorderAction(
    pending: Boolean,
    shuffleEnabled: Boolean,
    repeatOne: Boolean,
    queueSize: Int,
    hasVirtualQueue: Boolean
): PendingShuffleReorderAction {
    if (!pending) return PendingShuffleReorderAction.None
    if (!shuffleEnabled || repeatOne || queueSize <= 1 || hasVirtualQueue) {
        return PendingShuffleReorderAction.Clear
    }
    return PendingShuffleReorderAction.Materialize
}

internal data class PendingShuffleCleanupPlan(
    val pending: Boolean,
    val nativeShuffle: Boolean,
    val keepOriginalOrder: Boolean
)

internal fun clearPendingShufflePlan(
    hasOriginalOrder: Boolean,
    disableNativeShuffle: Boolean = true,
    clearOriginalOrder: Boolean = false
): PendingShuffleCleanupPlan {
    return PendingShuffleCleanupPlan(
        pending = false,
        nativeShuffle = !disableNativeShuffle,
        keepOriginalOrder = hasOriginalOrder && !clearOriginalOrder
    )
}

internal fun shouldAdoptNativeShuffleAsPending(
    appShuffleEnabled: Boolean,
    pending: Boolean,
    nativeShuffleEnabled: Boolean,
    queueSize: Int,
    hasVirtualQueue: Boolean
): Boolean {
    return nativeShuffleEnabled &&
        appShuffleEnabled &&
        !pending &&
        shouldDeferShuffleReorder(
            enableShuffle = true,
            previousShuffle = false,
            queueSize = queueSize,
            hasVirtualQueue = hasVirtualQueue
        )
}
