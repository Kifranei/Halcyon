package com.ella.music.ui.player

import android.app.Application
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.SemanticsActions
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w840dp-h400dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerScreenPageHostTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w840dp-h400dp-xxhdpi")
    fun landscapeClockFollowsThemeFontAndUsesBatteryStateIcons() {
        val family = mutableStateOf<FontFamily>(FontFamily.Monospace)
        val battery = mutableStateOf(ClockBattery(67, false))
        compose.setContent {
            val defaults = defaultTextStyles()
            MiuixTheme(textStyles = defaults.copy(main = defaults.main.copy(fontFamily = family.value))) {
                LandscapeClockContent("18:03", "October 6 Tuesday", "Slowly", Color.White, battery.value,
                    true, {}, {}, {}, {}, false, artwork = { Box(it.background(Color.DarkGray)) },
                    artist = "Fixture artist", modifier = Modifier.size(640.dp, 360.dp))
            }
        }
        fun checkFont() {
            compose.waitForIdle()
            for (tag in listOf("clock-time", "clock-date", "clock-title", "clock-artist", "clock-battery")) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                    it(layouts)
                }
                assertEquals("$tag must follow the global theme font", family.value, layouts.single().layoutInput.style.fontFamily)
                if (tag == "clock-time") {
                    val layout = layouts.single()
                    // Native paragraph widths can exceed their rounded IntSize by a fraction
                    // of a pixel; allow rounding while checking the entire clock fits.
                    assertTrue("Clock glyphs must fit the column",
                        layout.multiParagraph.intrinsics.maxIntrinsicWidth <= layout.layoutInput.constraints.maxWidth + 1f)
                    val last = layout.getBoundingBox(layout.layoutInput.text.lastIndex)
                    assertTrue("Clock must fit: size=${layout.size}, last=$last, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}",
                        last.right <= layout.size.width + 1f && last.bottom <= layout.size.height + 1f)
                }
            }
        }
        checkFont()
        compose.onNodeWithTag("clock-battery-icon", useUnmergedTree = true).assertExists()
        assertEquals(com.ella.music.R.drawable.ic_battery_android_frame_5, clockBatteryIcon(battery.value))
        compose.runOnIdle { family.value = FontFamily.Serif; battery.value = ClockBattery(67, true) }
        checkFont()
        assertEquals(com.ella.music.R.drawable.ic_battery_android_frame_bolt, clockBatteryIcon(battery.value))
        assertEquals(com.ella.music.R.drawable.ic_battery_android_frame_full, clockBatteryIcon(ClockBattery(100, false)))
        assertEquals(com.ella.music.R.drawable.ic_battery_android_frame_1, clockBatteryIcon(ClockBattery(0, false)))
        val image = compose.onNodeWithTag("landscape-cover-clock").captureToImage().asAndroidBitmap()
        val output = java.io.File("build/outputs/ruby-alignment/clock-font-battery.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun openingLyricsPaintsLyricsBeforeAndAfterTheResidentPagerIsAligned() {
        val pending = mutableStateOf<Int?>(PLAYER_PAGE_LYRICS)
        lateinit var scope: CoroutineScope
        lateinit var pager: androidx.compose.foundation.pager.PagerState
        compose.setContent {
            scope = rememberCoroutineScope()
            pager = rememberPagerState(initialPage = PLAYER_PAGE_COVER) { PLAYER_PAGE_COUNT }
            Box(Modifier.size(640.dp, 360.dp).testTag("page-host")) {
                PlayerScreenPageHost(false, true, pager, true, {}, {}, {}, {},
                    coverPage = { _, modifier -> Box(modifier.background(Color.Red)) },
                    lyricsPage = { _, _, _, _, modifier -> Box(modifier.background(Color.Green)) },
                    detailPage = { modifier -> Box(modifier.background(Color.Blue)) },
                    pendingEntryPage = pending.value)
            }
        }
        fun assertLyricsFrame() {
            compose.waitForIdle()
            val image = compose.onNodeWithTag("page-host").captureToImage().asAndroidBitmap()
            assertTrue(android.graphics.Color.green(image.getPixel(image.width / 2, image.height / 2)) > 245)
            assertTrue(android.graphics.Color.red(image.getPixel(image.width / 2, image.height / 2)) < 10)
        }
        assertLyricsFrame()
        compose.runOnIdle { scope.launch { pager.scrollToPage(PLAYER_PAGE_LYRICS); pending.value = null } }
        assertLyricsFrame()
        compose.runOnIdle {
            assertNull("The entry must finish aligning rather than remain on the temporary lyrics surface", pending.value)
            assertEquals(PLAYER_PAGE_LYRICS, pager.currentPage)
        }
    }
    @Test
    fun aDirectLyricsMorphDoesNotReplayTheResidentCoverArtwork() {
        val replay = mutableStateOf(true)
        compose.setContent {
            val artwork = rememberGraphicsLayer()
            val morph = remember { PlayerMorphState { 0.5f } }
            Box(Modifier.size(640.dp, 360.dp).testTag("lyrics-morph")
                .drawWithContent {
                    artwork.record { drawRect(Color.Red) }
                    val bounds = Rect(Offset.Zero, size)
                    morph.miniBounds = bounds
                    morph.miniArtwork = bounds
                    morph.artworkBounds = bounds
                    morph.artworkLayer = artwork
                    drawContent()
                }.playerMorphSurface(morph, false, replayArtwork = { replay.value })
                .background(Color.Green))
        }
        compose.waitForIdle()
        fun pixel() = compose.onNodeWithTag("lyrics-morph").captureToImage().asAndroidBitmap().let {
            it.getPixel(it.width / 2, it.height / 2)
        }
        assertTrue(android.graphics.Color.red(pixel()) > 245)
        compose.runOnIdle { replay.value = false }
        compose.waitForIdle()
        val lyricsPixel = pixel()
        assertTrue("The shared morph must retain the lyric page instead of overlaying its old cover",
            android.graphics.Color.green(lyricsPixel) > 245 && android.graphics.Color.red(lyricsPixel) < 10)
    }

    @Test
    fun immersiveEntryDoesNotWaitForAnAbsentPagerAndCanDismiss() {
        val showingLyrics = mutableStateOf(true)
        var aligned = false
        compose.setContent {
            val pager = rememberPagerState(initialPage = PLAYER_PAGE_COVER) { PLAYER_PAGE_COUNT }
            PlayerPagerEntryEffects(1, true, pager, PLAYER_PAGE_COVER) { aligned = true }
            Box(Modifier.size(640.dp, 360.dp).testTag("immersive-entry")) {
                PlayerScreenPageHost(true, showingLyrics.value, pager, true, {}, { showingLyrics.value = false }, {}, {},
                    coverPage = { _, modifier -> Box(modifier.background(Color.Red)) },
                    lyricsPage = { dismiss, _, _, _, modifier ->
                        Box(modifier.background(Color.Green).testTag("immersive-lyrics")) {
                            androidx.compose.foundation.text.BasicText("Close", Modifier.testTag("dismiss-lyrics")
                                .clickable(onClick = dismiss))
                        }
                    },
                    detailPage = { modifier -> Box(modifier.background(Color.Blue)) })
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("No pager layout exists in immersive mode", aligned) }
        compose.onNodeWithTag("dismiss-lyrics").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("immersive-lyrics").assertDoesNotExist()
    }

    @Test
    fun backAndDownwardSwipeDismissAResidentLyricsEntryAfterReopening() {
        val visible = mutableStateOf(true)
        val token = mutableStateOf(1)
        var dismissed = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        var density = 1f
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            density = LocalDensity.current.density
            val pager = rememberPagerState(initialPage = PLAYER_PAGE_COVER) { PLAYER_PAGE_COUNT }
            var aligned by remember(token.value) { mutableStateOf(false) }
            PlayerPagerEntryEffects(token.value, false, pager, PLAYER_PAGE_LYRICS) { aligned = true }
            Box(Modifier.size(640.dp, 360.dp).testTag("resident-player")) {
                PlayerDismissMotionHost(token.value, {}, { dismissed++; visible.value = false },
                    modifier = Modifier.graphicsLayer { alpha = if (visible.value) 1f else 0f },
                    backEnabled = visible.value) {
                    PlayerScreenPageHost(false, true, pager, true, {}, {}, {}, {},
                        coverPage = { _, modifier -> Box(modifier.background(Color.Red)) },
                        lyricsPage = { _, _, _, _, modifier -> Box(modifier.background(Color.Green)) },
                        detailPage = { modifier -> Box(modifier.background(Color.Blue)) },
                        playerVisible = visible.value,
                        pendingEntryPage = PLAYER_PAGE_LYRICS.takeUnless { aligned })
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, dismissed)
            assertFalse(visible.value)
            visible.value = true
            token.value++
        }
        compose.waitForIdle()
        compose.onNodeWithTag("resident-player").performTouchInput {
            swipe(Offset(width / 2f, 12f * density), Offset(width / 2f, height - 8f * density), 500)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, dismissed); assertFalse(visible.value) }
    }

    @Test
    fun aHiddenImmersiveLyricPageCannotInterceptBack() {
        var lyricsBackEnabled = true
        compose.setContent {
            val pager = rememberPagerState(initialPage = PLAYER_PAGE_COVER) { PLAYER_PAGE_COUNT }
            PlayerScreenPageHost(true, true, pager, true, {}, {}, {}, {},
                coverPage = { _, modifier -> Box(modifier) },
                lyricsPage = { _, _, backEnabled, _, modifier ->
                    lyricsBackEnabled = backEnabled
                    Box(modifier)
                }, detailPage = {}, playerVisible = false)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(lyricsBackEnabled) }
    }

    @Test
    fun lyricsRendererIsPausedWhilePagerIsMoving() {
        assertTrue(isPlayerLyricsPageVisible(PLAYER_PAGE_LYRICS, PLAYER_PAGE_LYRICS, false))
        assertFalse(isPlayerLyricsPageVisible(PLAYER_PAGE_LYRICS, PLAYER_PAGE_LYRICS, true))
        assertFalse(isPlayerLyricsPageVisible(PLAYER_PAGE_COVER, PLAYER_PAGE_LYRICS, false))
    }

    @Test
    fun backIsNotInterceptedOnPagedLyrics() {
        assertFalse(shouldInterceptPlayerPagerBack(true, PLAYER_PAGE_LYRICS))
        assertFalse(shouldInterceptPlayerPagerBack(true, PLAYER_PAGE_DETAILS))
        assertFalse(shouldInterceptPlayerPagerBack(true, PLAYER_PAGE_COVER))
        assertFalse(shouldInterceptPlayerPagerBack(false, PLAYER_PAGE_LYRICS))
    }
}
