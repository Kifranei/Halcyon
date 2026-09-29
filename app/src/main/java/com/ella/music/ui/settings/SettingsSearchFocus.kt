package com.ella.music.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Transient navigation request, never saved as a user preference. */
internal object SettingsSearchFocus {
    data class Request(val titleRes: Int, val sheet: String, val token: Long)
    var request by mutableStateOf<Request?>(null)
        private set
    private var nextToken = 0L
    private var revealedTitleRes by mutableStateOf(0)

    fun reveals(vararg titles: Int): Boolean = revealedTitleRes != 0 && revealedTitleRes in titles

    fun reset() {
        request = null
        revealedTitleRes = 0
    }

    fun select(titleRes: Int, sheet: String) {
        revealedTitleRes = titleRes
        request = Request(titleRes, sheet, ++nextToken)
    }

    fun clear(request: Request) {
        if (this.request == request) this.request = null
    }
}

@Composable
internal fun SettingsSearchAnchor(titleRes: Int, content: @Composable () -> Unit) {
    val request = SettingsSearchFocus.request?.takeIf { it.titleRes == titleRes }
    LaunchedEffect(request) {
        if (request != null) {
            delay(2500)
            SettingsSearchFocus.clear(request)
        }
    }
    SettingsFocusAnchor(active = request != null, requestToken = request?.token, content = content)
}
