package com.ella.music.ui.poster

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.AudioInfo
import com.ella.music.ui.player.MiniLyricsPreview
import com.ella.music.ui.player.LocalKaraokeRainbowOverride
import com.ella.music.ui.player.PlayerPalette
import com.ella.music.ui.player.PlayerProgressBlock
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w840dp-h900dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PosterWallFocusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun portraitExpansionKeepsPlayAndMoreActionsFullyVisible() = checkExpanded(412f, 760f, "portrait")
    @Test fun landscapeExpansionKeepsPlayAndMoreActionsFullyVisible() = checkExpanded(840f, 310f, "landscape")
    @Test fun portraitPlaybackFitsAboveTheMiniPlayerAndUsesSharedLyricsAndProgress() =
        checkExpanded(412f, 674f, "playback-portrait", current = true)
    @Test fun landscapePlaybackFitsAboveTheMiniPlayerAndKeepsSeekAndTransportTouchable() =
        checkExpanded(840f, 224f, "playback-landscape", current = true)
    @Test
    @Config(qualifiers = "w840dp-h900dp-television-xxhdpi")
    fun remoteFocusMovesToPlayAfterTheCardExpands() =
        checkExpanded(412f, 674f, "remote-playback", current = true, expectRemoteFocus = true)

    private fun checkExpanded(width: Float, height: Float, name: String, current: Boolean = false, expectRemoteFocus: Boolean = false) {
        var plays = 0
        var seek = -1f
        val allowTapSeek = mutableStateOf(true)
        var lyricClicks = 0
        var fullscreenRequests = 0
        var closed = false
        val song = Song(1, "A song with a long title that should wrap gracefully", "Artist · Album", "Album", 0, 200000, "/music/1.flac", "1.flac")
        compose.setContent {
            MiuixTheme {
                Box(Modifier.size(width.dp, height.dp).background(PosterInk).testTag("focus")) {
                    PosterExpandedCard(ExpandedPoster(song, 0, PosterRect(20f, 30f, 185f, 185f)), PosterRect(0f, 0f, width, height), current,
                        artwork = { _, modifier -> Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF49365F), Color(0xFF225F74))))) },
                        lyrics = { modifier -> CompositionLocalProvider(LocalKaraokeRainbowOverride provides false) { MiniLyricsPreview(
                            lyrics = listOf(LyricLine(0, "Shared lyric line", translation = "Translated lyric line", pronunciation = "Pronunciation line")),
                            currentIndex = 0, showTranslation = true, showPronunciation = true,
                            currentPositionMs = 5000, isPlaying = false, legacyWindow = true,
                            blurEnabled = false, edgeFeatherEnabled = true, primaryTextSizeOverrideSp = 28f,
                            onLineClick = { lyricClicks++ }, modifier = modifier.testTag("lyrics")) } },
                        seekBar = { compact -> Box(Modifier.fillMaxWidth().testTag("progress")) {
                            PlayerProgressBlock(currentPosition = 5000, duration = 200000, song = song,
                                audioInfo = AudioInfo("FLAC", sampleRate = 96000, bitDepth = 24), bluetoothDeviceName = null, palette = PlayerPalette.Default,
                                allowTapSeek = allowTapSeek.value, showTotalDuration = true, onSeek = { seek = it },
                                waveformHeight = if (compact) 36.dp else 72.dp, showInfo = false,
                                progressStyleOverride = com.ella.music.data.SettingsManager.PLAYER_PROGRESS_STYLE_GLOW)
                        } },
                        controls = { PosterPlayControls(false, {}, { plays++ }, {}, showSkipButtons = false) },
                        onPlay = { plays++ }, onMore = {}, onClosed = { closed = true },
                        onFullscreen = if (current) ({ fullscreenRequests++ }) else null)
                }
            }
        }
        compose.waitForIdle()
        val app = RuntimeEnvironment.getApplication()
        val play = if (current) compose.onNodeWithContentDescription(app.getString(R.string.common_play))
            else compose.onNodeWithText(app.getString(R.string.common_play))
        val more = compose.onNodeWithContentDescription(app.getString(R.string.player_more_actions))
        val rootBounds = compose.onNodeWithTag("focus").fetchSemanticsNode().boundsInRoot
        for (node in listOf(play, more)) {
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("Controls must fit inside the page at $width x $height", bounds.top >= rootBounds.top && bounds.bottom <= rootBounds.bottom && bounds.left >= rootBounds.left && bounds.right <= rootBounds.right)
        }
        if (expectRemoteFocus) play.assertIsFocused()
        if (current) {
            compose.onNodeWithContentDescription(app.getString(R.string.common_previous)).assertDoesNotExist()
            compose.onNodeWithContentDescription(app.getString(R.string.common_next)).assertDoesNotExist()
            compose.onNodeWithText(app.getString(R.string.player_quality_hi_res), useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithContentDescription(app.getString(R.string.poster_wall_fullscreen_player)).performClick()
            compose.runOnIdle { assertEquals(1, fullscreenRequests) }
            val progress = compose.onNodeWithTag("progress", useUnmergedTree = true)
            progress.assertIsDisplayed()
            val bounds = progress.fetchSemanticsNode().boundsInRoot
            assertTrue("Seek and timestamps must fit above transport", bounds.top >= rootBounds.top && bounds.bottom < play.fetchSemanticsNode().boundsInRoot.bottom)
            // The top 36dp is the actual shared seek bar; the timestamp row is below it.
            progress.performTouchInput { click(androidx.compose.ui.geometry.Offset(this.width * 0.75f, 18.dp.toPx())) }
            compose.runOnIdle { assertEquals(0.75f, seek, 0.05f) }
            compose.runOnIdle { allowTapSeek.value = false }
            compose.waitForIdle()
            progress.performTouchInput { click(androidx.compose.ui.geometry.Offset(this.width * 0.3f, 18.dp.toPx())) }
            compose.runOnIdle { assertEquals("Disabling tap seek must preserve playback position", 0.75f, seek, 0.05f) }
            progress.performTouchInput { swipe(androidx.compose.ui.geometry.Offset(this.width * 0.2f, 18.dp.toPx()), androidx.compose.ui.geometry.Offset(this.width * 0.65f, 18.dp.toPx()), 400) }
            compose.runOnIdle { assertEquals(0.65f, seek, 0.05f) }
            if (height > 350f) {
                compose.onNodeWithTag("lyrics", useUnmergedTree = true).assertIsDisplayed()
                compose.onNodeWithText("Shared lyric line", useUnmergedTree = true).performTouchInput { click(center) }
                compose.runOnIdle { assertEquals(1, lyricClicks) }
            }
        }
        val output = File("build/outputs/poster-wall-preview/focus-$name.png")
        output.parentFile?.mkdirs()
        compose.onNodeWithTag("focus").captureToImage().asAndroidBitmap().let { bitmap -> output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        play.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, plays) }
        compose.onNodeWithContentDescription(app.getString(R.string.common_close)).performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertTrue(closed) }
    }
}
