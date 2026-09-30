package com.ella.music.ui.settings

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.ella.music.ui.navigation.NavigationBackScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsDetailNavigationTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var navController: NavHostController
    private lateinit var systemDispatcher: NavigationEventDispatcher
    private lateinit var ordinaryBackDispatcher: OnBackPressedDispatcher
    private lateinit var systemInput: DirectNavigationEventInput
    private val localBackEnabled = mutableStateOf(false)
    private var localBackCount = 0
    private var rootBackCount = 0

    @Test
    fun embeddedHomeDisplay_handlesBackInsideAppearancePage() {
        assertTrue(
            shouldHandleHomeDisplayBackLocally(
                showHomeDisplayPage = true,
                initialHomeDisplay = false
            )
        )
    }

    @Test
    fun directHomeDisplayRoute_leavesBackToNavigationController() {
        assertFalse(
            shouldHandleHomeDisplayBackLocally(
                showHomeDisplayPage = true,
                initialHomeDisplay = true
            )
        )
    }

    @Test
    fun appearancePage_leavesBackToNavigationController() {
        assertFalse(
            shouldHandleHomeDisplayBackLocally(
                showHomeDisplayPage = false,
                initialHomeDisplay = false
            )
        )
    }

    @Test
    fun cancelledGestureKeepsSettingsFullSizeAndNeverRevealsHome() {
        showSettings()
        val before = compose.onNodeWithTag("settings").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            systemInput.backStarted(backEvent(0f))
            systemInput.backProgressed(backEvent(0.65f))
        }
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithTag("settings").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("home").assertDoesNotExist()
        compose.runOnIdle { systemInput.backCancelled() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("settings", navController.currentDestination?.route) }
        compose.onNodeWithTag("home").assertDoesNotExist()
    }

    @Test
    fun completedGesturePopsOnceAndRootBackStillFallsThrough() {
        showSettings()
        compose.runOnIdle {
            systemInput.backStarted(backEvent(0f))
            systemInput.backProgressed(backEvent(0.65f))
            systemInput.backCompleted()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("home").assertExists()
        compose.onNodeWithTag("settings").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("home", navController.currentDestination?.route)
            assertEquals(0, rootBackCount)
            ordinaryBackDispatcher.onBackPressed()
            assertEquals(1, rootBackCount)
        }
    }

    @Test
    fun pageBackHandlerKeepsPriorityAndOrdinaryBackThenPopsNormally() {
        showSettings(handleLocalBack = true)
        compose.runOnIdle { ordinaryBackDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, localBackCount)
            assertEquals("settings", navController.currentDestination?.route)
            ordinaryBackDispatcher.onBackPressed()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, localBackCount)
            assertEquals("home", navController.currentDestination?.route)
            assertEquals(0, rootBackCount)
        }
    }

    private fun showSettings(handleLocalBack: Boolean = false) {
        localBackEnabled.value = handleLocalBack
        compose.setContent {
            navController = rememberNavController()
            systemDispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current)
                .navigationEventDispatcher
            ordinaryBackDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
                .onBackPressedDispatcher
            BackHandler { rootBackCount++ }
            NavigationBackScope {
                NavHost(navController, startDestination = "home") {
                    composable("home") {
                        Box(Modifier.fillMaxSize().testTag("home"))
                    }
                    composable("settings") {
                        BackHandler(enabled = localBackEnabled.value) {
                            localBackCount++
                            localBackEnabled.value = false
                        }
                        Box(Modifier.fillMaxSize().testTag("settings"))
                    }
                }
            }
        }
        compose.runOnIdle {
            systemInput = DirectNavigationEventInput()
            systemDispatcher.addInput(systemInput)
            navController.navigate("settings")
        }
        compose.waitForIdle()
    }

    private fun backEvent(progress: Float) = NavigationEvent(
        touchX = 0f,
        touchY = 100f,
        progress = progress,
        swipeEdge = NavigationEvent.EDGE_LEFT
    )
}
