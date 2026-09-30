package com.ella.music.ui.poster

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ella.music.data.SettingsManager
import com.ella.music.ui.player.LocalAppleMusicLyricsViewPreferences
import com.ella.music.ui.player.LocalPlayerContentColor
import com.ella.music.ui.player.LocalKaraokeRainbowOverride
import com.ella.music.ui.player.MiniLyricsPreview
import com.ella.music.ui.player.MiniNoLyricsPreview
import com.ella.music.ui.player.PlayerPalette
import com.ella.music.ui.player.PlayerProgressBlock
import com.ella.music.ui.player.hasMiniLyric
import com.ella.music.ui.player.rememberAppleMusicLyricsViewPreferences
import com.ella.music.ui.player.rememberPlayerLyricFontState
import com.ella.music.viewmodel.PlayerViewModel

// These adapters only bind playback state and settings. Rendering and gestures belong to the
// same components used by the player; the app's bottom dock supplies the mini-player itself.
@Composable
internal fun PosterPlayerLyrics(
    playerViewModel: PlayerViewModel,
    onOpenPlayer: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val settings = remember(context) { SettingsManager.getInstance(context) }
    val fonts = rememberPlayerLyricFontState(context, settings)
    val preferences = rememberAppleMusicLyricsViewPreferences(settings)
    val wordLift by settings.appleMusicLyricsWordLift.collectAsState(initial = true)
    val lyrics by playerViewModel.lyrics.collectAsState()
    val loading by playerViewModel.lyricsLoading.collectAsState()
    val index by playerViewModel.currentLyricIndex.collectAsState()
    val position by playerViewModel.currentPosition.collectAsState()
    val playing by playerViewModel.isPlaying.collectAsState()
    val playWhenReady by playerViewModel.playWhenReady.collectAsState()
    val translation by playerViewModel.showLyricTranslation.collectAsState()
    val pronunciation by playerViewModel.showLyricPronunciation.collectAsState()
    val hasLyrics = remember(lyrics) { lyrics.any { it.hasMiniLyric() } }
    val openLyrics = {
        playerViewModel.setShowLyrics(true)
        onOpenPlayer()
    }
    CompositionLocalProvider(
        LocalAppleMusicLyricsViewPreferences provides preferences,
        LocalKaraokeRainbowOverride provides false
    ) {
        when {
            hasLyrics -> MiniLyricsPreview(
                lyrics = lyrics,
                currentIndex = index,
                showTranslation = translation,
                showPronunciation = pronunciation,
                currentPositionMs = position,
                isPlaying = playing,
                isPaused = !playing && !playWhenReady,
                fontFamily = fonts.originalFontFamily,
                translationFontFamily = fonts.translationFontFamily,
                fontWeight = fonts.fontWeight,
                legacyWindow = true,
                contentColor = Color.White,
                wordLiftEnabled = wordLift,
                blurEnabled = false,
                edgeFeatherEnabled = true,
                primaryTextSizeOverrideSp = 28f,
                onLineClick = { openLyrics() },
                modifier = modifier
            )
            !loading -> MiniNoLyricsPreview(
                contentColor = Color.White,
                fontWeight = fonts.fontWeight,
                onClick = openLyrics,
                modifier = modifier
            )
            else -> Box(modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun PosterPlayerProgress(playerViewModel: PlayerViewModel, compact: Boolean) {
    val context = LocalContext.current
    val settings = remember(context) { SettingsManager.getInstance(context) }
    val song by playerViewModel.currentSong.collectAsState()
    val position by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val allowTapSeek by settings.playerTapSeekEnabled.collectAsState(initial = true)
    val showTotalDuration by settings.playerShowTotalDuration.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_SHOW_TOTAL_DURATION
    )
    CompositionLocalProvider(LocalPlayerContentColor provides Color.White) {
        Box(Modifier.padding(bottom = 12.dp)) {
            PlayerProgressBlock(
                currentPosition = position,
                duration = duration,
                song = song,
                audioInfo = null,
                bluetoothDeviceName = null,
                palette = PlayerPalette.Default.copy(accent = Color.White),
                allowTapSeek = allowTapSeek,
                showTotalDuration = showTotalDuration,
                waveformHeight = if (compact) 36.dp else 72.dp,
                showInfo = false,
                progressStyleOverride = SettingsManager.PLAYER_PROGRESS_STYLE_GLOW,
                onSeek = { playerViewModel.seekToProgress(it, playerViewModel.duration.value) }
            )
        }
    }
}
