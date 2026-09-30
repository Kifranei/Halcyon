package com.ella.music.ui.poster

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.ella.music.data.model.Song
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PosterWallPositioningTest {
    @get:Rule val compose = createComposeRule()
    private val request = mutableIntStateOf(0)
    private val index = mutableIntStateOf(5)
    private val ready = mutableStateOf(true)
    private val wall = PosterWallState()
    private val items = (0 until 200).map {
        PosterWallItem(Song(it.toLong(), "Track $it", "Artist", "Album", 0, 1000, "/$it", "$it"), it, "$it")
    }
    private val geometry = PosterWallGeometry(items.size)

    @Test fun aLocateClickFinishesItsAnimationInsteadOfCancellingOnTheNextComposition() {
        content()
        compose.runOnIdle { wall.transform(Offset(-1500f, -1400f), 1f, Offset.Zero, geometry) }
        val before = wall.camera
        compose.onNodeWithTag("locate").performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        compose.runOnIdle { assertNotEquals(before, wall.camera); assertLocated(5) }
    }

    @Test fun repeatedClicksRetargetToTheLatestPlayingSong() {
        content()
        compose.runOnIdle { index.intValue = 80 }
        compose.onNodeWithTag("locate").performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { index.intValue = 155 }
        compose.onNodeWithTag("locate").performClick()
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        compose.runOnIdle { assertLocated(155) }
    }

    @Test fun locatingDuringAFilterOrSourceReloadWaitsForTheNewDataset() {
        content()
        compose.runOnIdle { ready.value = false; index.intValue = 80 }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithTag("locate").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { ready.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        compose.runOnIdle { assertLocated(80) }
    }

    private fun content() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val data = if (ready.value) items else emptyList()
            LaunchedEffect(Unit) { wall.resize(412f, 500f, geometry) }
            PosterWallPositioning(wall, data, geometry, index.intValue, request.intValue)
            Box(Modifier.size(60.dp).testTag("locate").clickable { request.intValue++ })
        }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
    }

    private fun assertLocated(target: Int) {
        val destination = wall.camera.centeredOn(geometry.rect(target), 412f, 500f).clamped(geometry, 412f, 500f)
        assertEquals(destination.x, wall.camera.x, 1f)
        assertEquals(destination.y, wall.camera.y, 1f)
    }
}
