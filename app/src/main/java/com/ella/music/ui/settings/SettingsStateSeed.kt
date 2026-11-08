package com.ella.music.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

/**
 * Seeds for [collectAsStateSeeded]. Settings flows in `SettingsManager` are cold
 * `DataStore.data.map { }` instances held as stable singletons. When a settings page leaves
 * composition (navigating into a sub-page) and returns, plain `collectAsState(initial = …)`
 * renders the hardcoded initial for a frame before DataStore re-emits, so a switch that is
 * actually ON visibly animates OFF → ON. Remembering the last emitted value per flow instance
 * and seeding with it keeps the switch fixed at its real state.
 */
private object SettingsStateSeeds {
    /** ConcurrentHashMap rejects null values; wrap them so nullable settings flows can be seeded. */
    private object NullValue

    private val seeds = ConcurrentHashMap<Flow<*>, Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T> seed(flow: Flow<T>, initial: T): T {
        if (!seeds.containsKey(flow)) return initial
        val stored = seeds[flow]
        return (if (stored === NullValue) null else stored) as T
    }

    fun <T> update(flow: Flow<T>, value: T) {
        seeds[flow] = if (value == null) NullValue else value
    }
}

/**
 * Drop-in replacement for `Flow.collectAsStateSeeded(initial = …)` for stable settings flows.
 * The seed is only a display hint for the first frame; the flow itself stays the source of truth.
 */
@Composable
fun <T> Flow<T>.collectAsStateSeeded(initial: T): State<T> {
    val state = collectAsState(initial = SettingsStateSeeds.seed(this, initial))
    val value = state.value
    SideEffect { SettingsStateSeeds.update(this, value) }
    return state
}
