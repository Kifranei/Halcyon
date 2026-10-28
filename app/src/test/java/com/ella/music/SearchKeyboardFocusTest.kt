package com.ella.music

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.navigation.Screen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SearchKeyboardFocusTest {
    @get:Rule val compose = createComposeRule()
    private val active = mutableStateOf(true)
    private val query = mutableStateOf("")
    private val selectAll = mutableStateOf(false)
    private var selections = 0
    private var shows = 0
    private var hides = 0
    private val keyboard = object : SoftwareKeyboardController {
        override fun show() { shows++ }
        override fun hide() { hides++ }
    }

    private fun showField(autoFocus: Boolean = false) {
        compose.setContent {
            MiuixTheme {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    EllaSearchBar(query.value, { query.value = it }, "Search", {},
                        Modifier.testTag("field"), autoFocus = autoFocus, active = active.value,
                        autoSelectAll = selectAll.value,
                        onAutoSelectAllConsumed = { selections++; selectAll.value = false })
                }
            }
        }
    }

    private fun advance() {
        // Apply visibility changes before advancing past the delayed focus request.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
    }

    @Test fun ordinaryPageFieldWaitsForAnExplicitTapAndRemainsEditable() {
        showField()
        advance()
        compose.onNodeWithTag("field").assertIsNotFocused()
        assertEquals(0, shows)
        compose.onNodeWithTag("field").performClick().performTextInput("manual input")
        compose.onNodeWithTag("field").assertIsFocused().assertTextEquals("manual input")
    }

    @Test fun offscreenSearchKeepsSelectionPendingUntilItBecomesActive() {
        active.value = false
        query.value = "saved query"
        selectAll.value = true
        showField(autoFocus = true)
        advance()
        compose.onNodeWithTag("field").assertIsNotFocused()
        assertEquals(0, shows)
        assertEquals(0, selections)
        compose.runOnIdle { active.value = true }
        advance()
        compose.onNodeWithTag("field").assertIsFocused()
        assertTrue(shows > 0)
        assertEquals(1, selections)
    }

    @Test fun leavingBeforeTheDelayCancelsThePendingKeyboardRequest() {
        compose.mainClock.autoAdvance = false
        showField(autoFocus = true)
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { active.value = false }
        advance()
        compose.onNodeWithTag("field").assertIsNotFocused()
        assertEquals(0, shows)
    }

    @Test fun selectingSavedTextDoesNotBypassDisabledAutoFocus() {
        query.value = "saved query"
        selectAll.value = true
        showField(autoFocus = false)
        advance()
        compose.onNodeWithTag("field").assertIsNotFocused()
        assertEquals(0, shows)
        assertEquals(1, selections)
    }

    @Test fun leavingRetainedSearchClearsFocusAndDoesNotReopenWhileHidden() {
        showField(autoFocus = true)
        advance()
        compose.onNodeWithTag("field").assertIsFocused()
        val initialShows = shows
        compose.runOnIdle { active.value = false }
        advance()
        compose.onNodeWithTag("field").assertIsNotFocused()
        assertTrue(hides > 0)
        compose.runOnIdle { query.value = "changed while hidden"; selectAll.value = true }
        advance()
        assertEquals(initialShows, shows)
    }

    @Test fun pagerNeighbourCannotOpenKeyboardBeforeSearchSettles() {
        val search = Screen.LibrarySearch.createRoute()
        val routes = listOf(Screen.Home.route, Screen.Folder.createRoute(), search)
        val state = MainPageState(routes[1]).apply { this.routes = routes }
        val composed = mutableSetOf<String>()
        compose.setContent {
            MiuixTheme {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    MainPagePager(state, routes, true, Modifier.testTag("pager")) { route ->
                        SideEffect { composed.add(route) }
                        Box(Modifier.fillMaxSize()) {
                            if (route.isSearchRoute()) EllaSearchBar("", {}, "Search", {},
                                Modifier.testTag("search"), autoFocus = true, active = LocalMainPageActive.current)
                        }
                    }
                }
            }
        }
        advance()
        compose.onNodeWithTag("pager").performTouchInput {
            down(Offset(width * 0.8f, height * 0.45f))
            moveBy(Offset(-width * 0.3f, 0f), 400)
        }
        advance()
        assertTrue("Search must be composed during the partial drag", composed.any { it.isSearchRoute() })
        assertEquals(routes[1], state.selectedRoute)
        assertEquals(0, shows)
        compose.onNodeWithTag("pager").performTouchInput {
            moveBy(Offset(-width * 0.45f, 0f), 400)
            up()
        }
        advance()
        compose.runOnIdle { assertTrue(state.selectedRoute.isSearchRoute()) }
        compose.onNodeWithTag("search").assertIsFocused()
        assertTrue(shows > 0)
    }
}
