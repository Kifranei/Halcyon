package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One shared current-song background used behind the app's top-level browsing pages. */
@Composable
internal fun AppNowPlayingFlowBackground(
    song: Song,
    mainViewModel: MainViewModel,
    currentPositionMs: Long,
    isPlaying: Boolean,
    light: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val beautifulLyrics by settingsManager.playerBeautifulLyricsBackground.collectAsState(initial = false)
    val dynamicFlowEnabled by settingsManager.playerDynamicFlowEnabled.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_DYNAMIC_FLOW_ENABLED
    )
    val songKey = remember(song) {
        listOf(
            song.playlistIdentityKey(), song.id, song.path, song.coverUrl,
            song.dateModified, song.fileSize
        ).joinToString("|")
    }
    val coverBitmap by produceState<Bitmap?>(initialValue = null, songKey) {
        value = withContext(Dispatchers.IO) {
            runCatching { mainViewModel.getMiniPlayerCoverArtBitmap(song) }.getOrNull()
        }
    }
    val palette by produceState(
        initialValue = if (light) PlayerPalette.LightDefault else PlayerPalette.Default,
        coverBitmap,
        light
    ) {
        value = withContext(Dispatchers.Default) { PlayerPalette.from(coverBitmap, light) }
    }

    if (beautifulLyrics) {
        BeautifulLyricsDynamicBackground(
            palette = palette,
            coverBitmap = coverBitmap,
            positionMs = currentPositionMs,
            isPlaying = isPlaying,
            modifier = modifier.fillMaxSize()
        )
    } else {
        AppleCoverFlowBackground(
            coverBitmap = coverBitmap,
            backgroundColor = palette.middle,
            isDark = !palette.isLight,
            isPlaying = isPlaying,
            animate = dynamicFlowEnabled,
            modifier = modifier.fillMaxSize()
        )
    }
}
