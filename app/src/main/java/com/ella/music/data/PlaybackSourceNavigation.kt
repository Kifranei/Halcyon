package com.ella.music.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bridges queue surfaces that live inside the resident player to the app navigation host. */
internal object PlaybackSourceNavigation {
    private val _sourceKey = MutableStateFlow<String?>(null)
    val sourceKey = _sourceKey.asStateFlow()

    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests = _requests.asSharedFlow()

    fun updateSource(key: String?) {
        _sourceKey.value = key
    }

    fun request() {
        if (_sourceKey.value != null) _requests.tryEmit(Unit)
    }
}
