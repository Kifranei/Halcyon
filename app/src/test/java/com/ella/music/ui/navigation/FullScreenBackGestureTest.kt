package com.ella.music.ui.navigation

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FullScreenBackGestureTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController
    private val localMode = mutableStateOf(false)
    private var beforePops = 0
    private var localBacks = 0
    private var childDrags = 0
    private var rootBacks = 0
    private lateinit var systemBack: OnBackPressedDispatcher

    private fun show(childConsumes: Boolean = false, wallpaper: Boolean = false) {
        compose.setContent {
            MiuixTheme {
                nav = rememberNavController()
                systemBack = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                val entry by nav.currentBackStackEntryAsState()
                val sourceEntry = entry
                val targetEntry = nav.previousBackStackEntry
                val background = rememberNavigationPageBackground()
                CompositionLocalProvider(LocalNavigationPageBackground provides if (wallpaper) background else null) {
                    Box(Modifier.fillMaxSize()) {
                        if (wallpaper) Box(Modifier.fillMaxSize().recordNavigationPageBackground(background)
                            .background(Color(0x66336699)))
                        BackHandler { rootBacks++ }
                        NavigationBackScope(
                            modifier = Modifier.testTag("gesture-host"),
                            swipeEnabled = nav.previousBackStackEntry != null,
                            gestureKey = entry?.id,
                            onBackCompleted = {
                                if (sourceEntry != null && targetEntry != null && nav.currentBackStackEntry?.id == targetEntry.id) beforePops++
                            }
                        ) {
                            NavHost(nav, "home",
                                predictivePopEnterTransition = { EnterTransition.None },
                                predictivePopExitTransition = { slideOutHorizontally(tween(300, easing = LinearEasing)) { it } }
                            ) {
                                navigationPage("home") { Box(Modifier.fillMaxSize().background(Color.Magenta).testTag("home")) }
                                navigationPage("detail") {
                                    BackHandler(enabled = localMode.value) { localBacks++; localMode.value = false }
                                    Box(Modifier.fillMaxSize().testTag("detail").then(
                                        if (!childConsumes) Modifier else Modifier.pointerInput(Unit) {
                                            detectHorizontalDragGestures { change, _ -> change.consume(); childDrags++ }
                                        }
                                    ))
                                }
                                navigationPage("more") { Box(Modifier.fillMaxSize().testTag("more")) }
                            }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { nav.navigate("detail") }
        compose.waitForIdle()
    }

    private fun startDrag(fraction: Float) {
        compose.onNodeWithTag("gesture-host").performTouchInput {
            // Deliberately start away from either screen edge.
            down(Offset(width * 0.25f, height * 0.55f))
            moveBy(Offset(width * fraction, 0f), 400)
        }
        compose.waitForIdle()
    }

    private fun release() {
        compose.onNodeWithTag("gesture-host").performTouchInput {
            moveBy(Offset.Zero, 300)
            up()
        }
        compose.waitForIdle()
    }

    @Test fun middleOfScreenPreviewsTheRealPreviousPageAndShortDragCancels() {
        show()
        startDrag(0.22f)
        val detailBounds = compose.onNodeWithTag("detail").fetchSemanticsNode().boundsInRoot
        assertTrue(detailBounds.left > 0f)
        compose.onNodeWithTag("home").assertExists()
        val previousBounds = compose.onNodeWithTag("home").fetchSemanticsNode().boundsInRoot
        // The lower sheet remains fixed; the opaque upper sheet overlaps it as it moves.
        assertEquals(0f, previousBounds.left, 1f)
        assertTrue(previousBounds.right > detailBounds.left)
        compose.runOnIdle { assertEquals("detail", nav.currentDestination?.route) }
        release()
        compose.runOnIdle { assertEquals("detail", nav.currentDestination?.route); assertEquals(0, beforePops) }
        assertEquals(0f, compose.onNodeWithTag("detail").fetchSemanticsNode().boundsInRoot.left, 1f)
        compose.onNodeWithTag("home").assertDoesNotExist()
    }

    @Test fun longDragPopsExactlyOneDestinationAndRootDoesNotExit() {
        show()
        compose.runOnIdle { nav.navigate("more") }
        compose.waitForIdle()
        startDrag(0.6f)
        release()
        compose.runOnIdle { assertEquals("detail", nav.currentDestination?.route); assertEquals(1, beforePops) }
        startDrag(0.6f)
        release()
        compose.runOnIdle { assertEquals("home", nav.currentDestination?.route); assertEquals(2, beforePops) }
        startDrag(0.6f)
        release()
        compose.runOnIdle { assertEquals("home", nav.currentDestination?.route); assertEquals(0, rootBacks) }
    }

    @Test fun reversingAnInitiallyLongDragCancels() {
        show()
        startDrag(0.6f)
        compose.onNodeWithTag("gesture-host").performTouchInput { moveBy(Offset(-width * 0.5f, 0f), 400) }
        release()
        compose.runOnIdle { assertEquals("detail", nav.currentDestination?.route); assertEquals(0, beforePops) }
    }

    @Test fun verticalAndLeftwardDragsDoNotNavigate() {
        show()
        compose.onNodeWithTag("gesture-host").performTouchInput {
            swipe(Offset(width * 0.5f, height * 0.8f), Offset(width * 0.6f, height * 0.2f), 600)
            swipe(Offset(width * 0.7f, height * 0.5f), Offset(width * 0.2f, height * 0.5f), 600)
        }
        compose.runOnIdle { assertEquals("detail", nav.currentDestination?.route); assertEquals(0, beforePops) }
    }

    @Test fun horizontalChildKeepsItsGesture() {
        show(childConsumes = true)
        startDrag(0.6f)
        release()
        compose.runOnIdle { assertTrue(childDrags > 0); assertEquals("detail", nav.currentDestination?.route); assertEquals(0, beforePops) }
    }

    @Test fun localModeDisablesSwipeWithoutClosingModeOrMovingPage() {
        show()
        compose.runOnIdle { localMode.value = true }
        startDrag(0.6f)
        assertEquals(0f, compose.onNodeWithTag("detail").fetchSemanticsNode().boundsInRoot.left, 1f)
        compose.onNodeWithTag("home").assertDoesNotExist()
        release()
        compose.runOnIdle {
            assertTrue(localMode.value)
            assertEquals(0, localBacks)
            assertEquals("detail", nav.currentDestination?.route)
            assertEquals(0, beforePops)
            // System Back still closes the mode; only the custom drag is disabled.
            systemBack.onBackPressed()
        }
        compose.runOnIdle { assertFalse(localMode.value); assertEquals(1, localBacks) }
        startDrag(0.6f)
        release()
        compose.runOnIdle { assertEquals("home", nav.currentDestination?.route); assertEquals(1, beforePops) }
    }

    @Test fun opaqueSheetPreservesWallpaperWithoutShowingPreviousContentThroughIt() {
        show(wallpaper = true)
        val before = compose.onNodeWithTag("gesture-host").captureToImage().asAndroidBitmap()
        val x = (before.width * 0.8f).toInt()
        val y = before.height / 2
        val sheetPixel = before.getPixel(x, y)
        assertEquals(255, android.graphics.Color.alpha(sheetPixel))
        startDrag(0.22f)
        val during = compose.onNodeWithTag("gesture-host").captureToImage().asAndroidBitmap()
        // The exposed left strip is the real lower page, while the covered region keeps
        // exactly the upper page's wallpaper color, even though the lower page is magenta.
        assertEquals(Color.Magenta, Color(during.getPixel(2, y)))
        assertEquals(sheetPixel, during.getPixel(x, y))
        release()
    }

    @Test fun secondFingerCancelsThePreview() {
        show()
        startDrag(0.6f)
        compose.onNodeWithTag("gesture-host").performTouchInput {
            down(1, Offset(width * 0.5f, height * 0.3f))
            up(1)
            up(0)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("detail", nav.currentDestination?.route); assertEquals(0, beforePops) }
    }

    @Test fun fastFlingRequiresMinimumTravelAndAnOppositeFlingCancels() {
        assertFalse(shouldCompleteBackDrag(12f, 1000f, 2000f, 1f))
        assertTrue(shouldCompleteBackDrag(30f, 1000f, 1000f, 1f))
        assertFalse(shouldCompleteBackDrag(600f, 1000f, -800f, 1f))
        assertFalse(shouldCompleteBackDrag(500f, 0f, 1000f, 1f))
    }
}
