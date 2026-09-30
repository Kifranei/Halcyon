package com.ella.music.ui.components

import android.app.Activity
import android.app.Application
import android.view.View
import com.ella.music.data.SettingsManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@Suppress("DEPRECATION")
class ImmersiveSystemBarsTest {
    @Test fun activityFocusAndPlayerCleanupCannotExposeBarsOverThePosterButtons() {
        val window = Robolectric.buildActivity(Activity::class.java).setup().get().window
        window.applyHalcyonSystemBars(SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH)
        val owner = Any()
        window.acquireImmersiveSystemBars(owner)
        window.applyHalcyonSystemBars(SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH, false)
        window.setPlayerImmersiveOverride(false)
        // These are the calls made by the Activity's global settings/focus callbacks.
        window.applyHalcyonSystemBars(SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH)
        assertTrue(window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
        assertTrue(window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0)
        val restore = window.releaseImmersiveSystemBars(owner)!!
        window.applyHalcyonSystemBars(restore.mode, restore.reserveSpace)
        assertEquals(0, window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    @Test fun overlappingSurfacesRestoreTheOriginalBarsAndScreenFlagRegardlessOfExitOrder() {
        val window = Robolectric.buildActivity(Activity::class.java).setup().get().window
        window.applyHalcyonSystemBars(SettingsManager.SYSTEM_BARS_MODE_HIDE_STATUS)
        window.decorView.keepScreenOn = false
        val first = Any(); val second = Any()
        window.acquireImmersiveSystemBars(first, true)
        window.applyHalcyonSystemBars(SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH, false)
        window.acquireImmersiveSystemBars(second, true)
        assertNull(window.releaseImmersiveSystemBars(first))
        assertTrue(window.decorView.keepScreenOn)
        val restore = window.releaseImmersiveSystemBars(second)!!
        window.applyHalcyonSystemBars(restore.mode, restore.reserveSpace)
        assertFalse(window.decorView.keepScreenOn)
        assertTrue(window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
        assertEquals(0, window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }
}
