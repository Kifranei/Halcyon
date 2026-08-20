package com.ella.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.ella.music.data.PlaybackSourceNavigation

@Composable
internal fun RememberPlaybackSourceScreen(key: String) {
    DisposableEffect(key) {
        PlaybackSourceNavigation.setActiveScreen(key)
        onDispose { PlaybackSourceNavigation.clearActiveScreen(key) }
    }
}
