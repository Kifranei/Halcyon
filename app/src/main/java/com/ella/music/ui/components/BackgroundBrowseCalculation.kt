package com.ella.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun <T> rememberBackgroundBrowseCalculation(initialValue: T, vararg keys: Any?, calculate: () -> T): State<T> =
    produceState(initialValue, *keys) {
        value = withContext(Dispatchers.Default) { calculate() }
    }

/** The keys identify the inputs actually used, rather than a previous retained result. */
internal data class BackgroundBrowseResult<T>(val value: T, val inputs: List<Any?>? = null) {
    fun isReadyFor(vararg keys: Any?): Boolean = inputs == keys.toList()
}

@Composable
internal fun <T> rememberBackgroundBrowseResult(
    initialValue: T, vararg keys: Any?, calculate: () -> T
): State<BackgroundBrowseResult<T>> = produceState(BackgroundBrowseResult(initialValue), *keys) {
    val result = withContext(Dispatchers.Default) { calculate() }
    value = BackgroundBrowseResult(result, keys.toList())
}
