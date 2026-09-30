package com.ella.music.ui.components

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ScrollIndicatorInteractionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: LazyListState
    private lateinit var scope: CoroutineScope
    private var moreClicks = 0

    @Test fun programmaticLocationAndAnimatedSourceJumpNeverSummonTheThumb() {
        content()
        compose.runOnIdle { scope.launch { state.scrollToItem(40) } }
        compose.waitForIdle()
        compose.onNodeWithTag("scroll-indicator-thumb").assertDoesNotExist()
        compose.onNodeWithTag("more-40").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, moreClicks); scope.launch { state.animateScrollToItem(70) } }
        compose.waitForIdle()
        compose.onNodeWithTag("scroll-indicator-thumb").assertDoesNotExist()
        compose.onNodeWithTag("more-70").performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, moreClicks) }
    }

    @Test fun userScrollShowsGrabbableThumbAndSubsequentLocationClearsIt() {
        content()
        compose.onNodeWithTag("songs").performTouchInput {
            down(center)
            moveTo(center.copy(y = center.y - 80f), delayMillis = 250)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("scroll-indicator-thumb").assertIsDisplayed()
        compose.onNodeWithTag("songs").performTouchInput { up() }
        compose.runOnIdle { scope.launch { state.scrollToItem(40) } }
        compose.waitForIdle()
        compose.onNodeWithTag("scroll-indicator-thumb").assertDoesNotExist()
        compose.onNodeWithTag("more-40").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, moreClicks) }
    }

    @Test fun draggingTheThumbReachesTheEndAndLeavesTheTrailingActionClickable() {
        content()
        compose.onNodeWithTag("songs").performTouchInput { swipeUp(durationMillis = 600) }
        compose.onNodeWithTag("scroll-indicator-thumb").performTouchInput {
            swipe(center, center + Offset(0f, 600f), durationMillis = 500)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(false, state.canScrollForward) }
        compose.onNodeWithTag("more-99").performTouchInput {
            click(Offset(width - 1f, center.y))
        }
        compose.runOnIdle { assertEquals(1, moreClicks) }
    }

    private fun content() {
        compose.setContent {
            MiuixTheme {
                state = rememberLazyListState()
                scope = rememberCoroutineScope()
                Box(Modifier.size(360.dp, 480.dp)) {
                    LazyColumn(Modifier.fillMaxSize().testTag("songs"), state = state) {
                        items(100) { index ->
                            Box(Modifier.fillMaxWidth().height(64.dp).padding(end = ScrollIndicatorListEndPadding + 16.dp)) {
                                Box(Modifier.align(Alignment.CenterEnd).size(32.dp)
                                    .testTag("more-$index").clickable { moreClicks++ })
                            }
                        }
                    }
                    LazyListScrollIndicator(state, Modifier.align(Alignment.CenterEnd))
                }
            }
        }
        compose.waitForIdle()
    }
}
