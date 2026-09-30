package com.ella.music.ui.poster

import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ella.music.ui.player.findActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PosterWallPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun naturalLandscapeDoesNotLockTheSensorAndExplicitLandscapeRestoresOrientation() {
        val force = mutableStateOf(false)
        val expanded = mutableStateOf(true)
        var activity: Activity? = null
        compose.setContent {
            activity = LocalContext.current.findActivity()
            if (expanded.value) com.ella.music.ui.player.ForceLandscapePlayerBars({}, forceLandscape = force.value)
        }
        compose.runOnIdle {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity!!.requestedOrientation)
            force.value = true
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, activity!!.requestedOrientation)
            expanded.value = false
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity!!.requestedOrientation) }
    }

    @Test fun leavingTheLyricsTheaterRestoresTheCallersOrientationAndKeepScreenOnFlag() {
        val visible = mutableStateOf(false)
        var activity: Activity? = null
        compose.setContent {
            activity = LocalContext.current.findActivity()
            if (visible.value) PosterImmersiveWindow(landscape = true)
        }
        compose.waitForIdle()
        var oldOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        var oldKeepScreenOn = false
        compose.runOnIdle {
            oldOrientation = activity!!.requestedOrientation
            oldKeepScreenOn = activity!!.window.decorView.keepScreenOn
            visible.value = true
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity!!.requestedOrientation)
            assertTrue(activity!!.window.decorView.keepScreenOn)
            visible.value = false
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(oldOrientation, activity!!.requestedOrientation)
            assertEquals(oldKeepScreenOn, activity!!.window.decorView.keepScreenOn)
        }
    }

    @Test fun nativeMiuixSearchEditsAndClearsTheQuery() {
        val query = mutableStateOf("")
        compose.setContent { MiuixTheme { PosterWallSearch(query.value, { query.value = it }, {}) } }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("song")
        compose.runOnIdle { assertEquals("song", query.value) }
        compose.onNodeWithContentDescription("Search Cleanup").performClick()
        compose.runOnIdle { assertEquals("", query.value) }
    }
}
