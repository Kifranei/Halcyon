package com.ella.music.ui.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Physical holder owners survive role changes, matching RawS's temporary reattach policy. */
@Stable
internal class LibraryPresentationLanes(initialLayout: Int) {
    private var layoutA by mutableIntStateOf(initialLayout)
    private var layoutB by mutableIntStateOf(initialLayout)
    private var preparedBoth by mutableStateOf(false)

    fun bind(settledUsesA: Boolean, transitioning: Boolean, source: Int, target: Int, settled: Int) {
        if (settledUsesA) {
            layoutA = if (transitioning) source else settled
            if (transitioning) layoutB = target
        } else {
            layoutB = if (transitioning) source else settled
            if (transitioning) layoutA = target
        }
        if (transitioning) preparedBoth = true
    }

    fun layout(usesA: Boolean): Int = if (usesA) layoutA else layoutB
    fun releaseInactive() { preparedBoth = false }
    fun retained(usesA: Boolean, settledUsesA: Boolean): Boolean = preparedBoth || usesA == settledUsesA
}
