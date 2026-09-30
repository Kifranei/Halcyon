package com.ella.music.ui.poster

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import com.ella.music.data.model.Song
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PosterWallInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp-television-xxhdpi")
    fun searchKeepsKeyboardFocusAndFilteredPostersRemainReachable() {
        var opened = -1
        val state = PosterWallState()
        val items = (0 until 100).map {
            PosterWallItem(Song(it.toLong(), "Track $it", "Artist", "Album", 0, 1000, "/$it", "$it"), it, "$it")
        }
        val geometry = PosterWallGeometry(items.size)
        compose.setContent {
            val input = LocalInputModeManager.current
            val searchFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { input.requestInputMode(InputMode.Keyboard); searchFocus.requestFocus() }
            MiuixTheme {
                Column(Modifier.size(412.dp, 680.dp)) {
                    BasicText("Search", Modifier.testTag("search-focus").focusRequester(searchFocus).focusable())
                    // The backdrop and poster layout overlap, as they do in the actual screen.
                    Box(Modifier.weight(1f)) {
                    PosterWallCanvas(items, geometry, state, 0,
                        artwork = { _, modifier, _ -> Box(modifier.background(Color.DarkGray)) },
                        scope = rememberCoroutineScope(), onExpand = { index, _ -> opened = index }, onMore = {}, searching = true)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("search-focus").assertIsFocused()
        compose.onNodeWithTag("poster-wall-canvas").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("poster-wall-canvas").performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.Enter) }
        compose.runOnIdle { assertTrue(opened >= 0) }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-television-xxhdpi")
    fun remoteArrowsRevealOffscreenPostersAndConfirmAndMenuTargetTheSelection() {
        val state = PosterWallState()
        var opened = -1
        var more = -1L
        var openedRect: PosterRect? = null
        setWall(state, onMore = { more = it.id }) { index, rect -> opened = index; openedRect = rect }
        compose.waitForIdle()
        val before = state.camera
        compose.onNodeWithTag("wall").performKeyInput {
            repeat(15) { pressKey(Key.DirectionDown) }
            pressKey(Key.DirectionRight)
            pressKey(Key.Enter)
        }
        compose.runOnIdle {
            assertTrue(opened >= 0)
            assertNotEquals(before, state.camera)
            val rect = checkNotNull(openedRect)
            assertTrue(rect.centerX in 0f..state.viewport.x && rect.centerY in 0f..state.viewport.y)
        }
        compose.onNodeWithTag("wall").performKeyInput { pressKey(Key.Menu) }
        compose.runOnIdle { assertEquals(opened.toLong(), more) }
    }

    @Test fun tappingAPosterExpandsItWithoutChangingTheCamera() {
        val state = PosterWallState()
        var expanded = -1
        setWall(state) { index, _ -> expanded = index }
        compose.waitForIdle()
        val original = state.camera
        compose.onNodeWithText("Track 0").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(0, expanded); assertEquals(original, state.camera) }
    }

    @Test fun draggingOverACardPansWithoutAccidentallyPlayingOrExpanding() {
        val state = PosterWallState()
        var expanded = -1
        setWall(state) { index, _ -> expanded = index }
        val before = state.camera
        compose.onNodeWithTag("wall").performTouchInput { swipe(center, center + Offset(-220f, -300f), durationMillis = 400) }
        compose.runOnIdle { assertTrue(state.camera != before); assertEquals(-1, expanded); state.stop() }
    }

    @Test fun portraitAndLandscapeCamerasKeepNearbyPostersReachable() {
        val state = PosterWallState()
        setWall(state) { _, _ -> }
        compose.waitForIdle()
        compose.runOnIdle {
            val geometry = PosterWallGeometry(10000)
            state.resize(840f, 360f, geometry)
            state.transform(Offset(-3000f, -2000f), 1.8f, Offset(420f, 180f), geometry)
            val nearby = geometry.visibleIndices(state.camera.bounds(840f, 360f))
            assertTrue(nearby.isNotEmpty()); assertTrue(nearby.size < 150)
        }
    }

    @Test
    @Config(qualifiers = "w915dp-h915dp-xxhdpi")
    fun enteringInPortraitThenRotatingKeepsTheComposedPostersAndLayoutInSync() =
        checkRotation(DpSize(412.dp, 680.dp), DpSize(840.dp, 310.dp))

    @Test
    @Config(qualifiers = "w915dp-h915dp-xxhdpi")
    fun enteringInLandscapeThenRotatingKeepsTheComposedPostersAndLayoutInSync() =
        checkRotation(DpSize(840.dp, 310.dp), DpSize(412.dp, 680.dp))

    @Test
    @Config(qualifiers = "w915dp-h915dp-xxhdpi")
    fun rotatingAnInfiniteWallKeepsTheVisibleCellsAndLayoutInSync() =
        checkRotation(DpSize(412.dp, 680.dp), DpSize(840.dp, 310.dp), infinite = true)

    @Test fun repeatedPostersExpandTheOriginalQueueIndex() {
        val state = PosterWallState()
        var expanded = -1
        setWall(state, infinite = true, count = 1) { index, _ -> expanded = index }
        compose.waitForIdle()
        val camera = state.camera
        val geometry = PosterWallGeometry(1, infinite = true)
        val rect = geometry.visiblePosters(camera.bounds(state.viewport.x, state.viewport.y)).first {
            camera.x + it.rect.centerX * camera.scale in 0f..state.viewport.x &&
                camera.y + it.rect.centerY * camera.scale in 0f..state.viewport.y
        }.rect
        compose.onNodeWithTag("wall").performTouchInput {
            val scale = this.width / state.viewport.x
            click(Offset((camera.x + rect.centerX * camera.scale) * scale, (camera.y + rect.centerY * camera.scale) * scale))
        }
        compose.runOnIdle { assertEquals(0, expanded) }
    }

    private fun checkRotation(initialSize: DpSize, rotatedSize: DpSize, infinite: Boolean = false) {
        val state = PosterWallState()
        val viewportSize = mutableStateOf(initialSize)
        var expanded = -1
        setWall(state, viewportSize, infinite) { index, _ -> expanded = index }
        compose.waitForIdle()
        // Retain the same composition and camera, as MainActivity does on orientation changes.
        // Changing only state.resize() misses the measure/place pass where the crash occurs.
        repeat(6) { turn ->
            val nextSize = if (turn % 2 == 0) rotatedSize else initialSize
            compose.runOnIdle { viewportSize.value = nextSize }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(Offset(nextSize.width.value, nextSize.height.value), state.viewport)
                assertTrue(PosterWallGeometry(10000, infinite = infinite).visiblePosters(state.camera.bounds(state.viewport.x, state.viewport.y)).isNotEmpty())
                assertEquals(-1, expanded)
            }
            compose.onNodeWithTag("wall").performTouchInput {
                swipe(center, center + Offset(-120f, -80f), durationMillis = 400)
            }
            compose.runOnIdle { state.stop() }
        }
    }

    @Test fun pinchingAcrossTwoClickablePostersZoomsInsteadOfExpandingEither() {
        val state = PosterWallState()
        var expanded = -1
        setWall(state) { index, _ -> expanded = index }
        val initialScale = state.camera.scale
        compose.onNodeWithTag("wall").performTouchInput {
            down(0, center - Offset(100f, 0f)); down(1, center + Offset(100f, 0f))
            moveTo(0, center - Offset(180f, 0f)); moveTo(1, center + Offset(180f, 0f))
            up(0); up(1)
        }
        compose.runOnIdle { assertTrue(state.camera.scale > initialScale); assertEquals(-1, expanded) }
    }

    @Test fun changingCoverResolutionInvalidatesOnlyItsArtworkCacheVariant() {
        val requested = androidx.compose.runtime.mutableIntStateOf(192)
        var resolvedWidth = 0
        val calls = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val song = Song(101, "Variant cover", "Artist", "Album", 0, 100000, "/music/variant.flac", "variant.flac")
        compose.setContent {
            val size = requested.intValue
            val state = com.ella.music.ui.components.rememberSongArtworkState(song, null,
                loadCoverArt = { calls.add(size); Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888) },
                usage = com.ella.music.ui.components.ArtworkUsage.LibraryGrid, cacheVariant = "poster-test:$size")
            androidx.compose.runtime.SideEffect { resolvedWidth = (state.model as? Bitmap)?.width ?: 0 }
            androidx.compose.foundation.text.BasicText("Cover ${ (state.model as? Bitmap)?.width ?: 0 }")
        }
        compose.waitUntil(5000) { resolvedWidth == 192 }
        compose.runOnIdle { requested.intValue = 512 }
        compose.waitForIdle()
        try { compose.waitUntil(5000) { resolvedWidth == 512 } }
        catch (failure: Throwable) { throw AssertionError("Resolved width $resolvedWidth; cover loads $calls", failure) }
    }

    private fun setWall(state: PosterWallState, viewportSize: State<DpSize>? = null, infinite: Boolean = false, count: Int = 10000, onMore: (Song) -> Unit = {}, onExpand: (Int, PosterRect) -> Unit) {
        val items = (0 until count).map { index ->
            val song = Song(id = index.toLong(), title = "Track $index", artist = "Artist ${index % 9}", album = "Album", albumId = 0, duration = 180000, path = "/music/$index.flac", fileName = "$index.flac")
            PosterWallItem(song, index, "$index")
        }
        val geometry = PosterWallGeometry(items.size, infinite = infinite)
        compose.setContent {
            MiuixTheme {
                Box(Modifier.size(viewportSize?.value ?: DpSize(412.dp, 680.dp)).background(PosterInk).testTag("wall")) {
                    PosterWallCanvas(items, geometry, state, 0, artwork = { song, modifier, _ ->
                        val hue = (song.id * 41 % 360).toFloat()
                        Box(modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.hsl(hue, 0.55f, 0.45f), Color.hsl((hue + 60) % 360, 0.65f, 0.17f)))))
                    }, scope = rememberCoroutineScope(), onExpand = onExpand, onMore = onMore)
                }
            }
        }
    }
}
