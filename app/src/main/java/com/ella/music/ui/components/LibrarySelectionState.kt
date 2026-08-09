package com.ella.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Shared multi-select state for library screens: selection mode flag, selected ids,
 * and the anchor/target pair driving range selection. Ordering-dependent operations
 * take the caller's current visible order so per-tab or per-sort lists keep working.
 */
@Stable
class LibrarySelectionState<T : Any> {
    var selectionMode by mutableStateOf(false)
    var selectedIds by mutableStateOf<Set<T>>(emptySet())
    var rangeAnchorId by mutableStateOf<T?>(null)
    var rangeTargetId by mutableStateOf<T?>(null)

    fun finishSelectionMode() {
        selectionMode = false
        selectedIds = emptySet()
        rangeAnchorId = null
        rangeTargetId = null
    }

    fun updateRangeAnchorsForManualSelection(id: T, selectedNow: Boolean) {
        if (selectedNow) {
            when {
                rangeAnchorId == null -> rangeAnchorId = id
                rangeAnchorId == id -> Unit
                else -> rangeTargetId = id
            }
        } else {
            if (rangeTargetId == id) rangeTargetId = null
            if (rangeAnchorId == id) {
                rangeAnchorId = rangeTargetId ?: selectedIds.firstOrNull { it != id }
                rangeTargetId = null
            }
        }
    }

    fun toggleSelection(id: T) {
        val selecting = id !in selectedIds
        selectedIds = if (selecting) selectedIds + id else selectedIds - id
        updateRangeAnchorsForManualSelection(id, selecting)
    }

    /**
     * [selectedIds] is built through ordered set operations, so its iteration order reflects
     * the user's selection sequence. Expose that intent at the call site instead of making
     * each category screen re-sort by its current grid/list order.
     */
    fun selectedIdsInSelectionOrder(): List<T> = selectedIds.toList()

    fun isRangeSelectionAvailable(indexById: Map<T, Int>): Boolean {
        val anchor = rangeAnchorId
        val target = rangeTargetId
        return anchor != null &&
            target != null &&
            anchor != target &&
            anchor in selectedIds &&
            target in selectedIds &&
            anchor in indexById &&
            target in indexById
    }

    fun applyRangeSelection(orderedIds: List<T>, indexById: Map<T, Int>) {
        val anchor = rangeAnchorId ?: return
        val target = rangeTargetId ?: return
        val anchorIndex = indexById[anchor] ?: return
        val targetIndex = indexById[target] ?: return
        if (anchorIndex == targetIndex) return
        val bounds = if (anchorIndex < targetIndex) anchorIndex..targetIndex else targetIndex..anchorIndex
        selectedIds = selectedIds + bounds.map { orderedIds[it] }
        // A completed range is one selection gesture. The next two manual taps must create a
        // fresh anchor/target pair instead of extending the previous range from its old target.
        rangeAnchorId = null
        rangeTargetId = null
    }

    fun toggleSelectAll(orderedIds: List<T>) {
        if (orderedIds.isEmpty()) return
        val ids = orderedIds.toSet()
        if (ids.all { it in selectedIds }) {
            selectedIds = selectedIds - ids
            rangeAnchorId = null
            rangeTargetId = null
        } else {
            selectedIds = selectedIds + ids
            rangeAnchorId = orderedIds.first()
            rangeTargetId = orderedIds.last()
        }
        selectionMode = true
    }
}

@Composable
fun <T : Any> rememberLibrarySelectionState(): LibrarySelectionState<T> =
    remember { LibrarySelectionState() }
