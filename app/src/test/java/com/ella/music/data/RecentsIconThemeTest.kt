package com.ella.music.data

import android.app.Activity
import android.app.Application
import com.ella.music.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class RecentsIconThemeTest {
    class TaskIconActivity : Activity() {
        var capturedDescription: android.app.ActivityManager.TaskDescription? = null
        override fun setTaskDescription(description: android.app.ActivityManager.TaskDescription) {
            capturedDescription = description
            super.setTaskDescription(description)
        }
    }

    @Test fun followingTheSystemClearsAPreviouslyForcedSquareBitmap() {
        val controller = Robolectric.buildActivity(TaskIconActivity::class.java).setup()
        val activity = controller.get()
        AppIconManager.updateTaskIcon(activity, SettingsManager.APP_ICON_STYLE_TRADITIONAL, false)
        assertNotNull(activity.capturedDescription?.icon)
        AppIconManager.updateTaskIcon(activity, SettingsManager.APP_ICON_STYLE_TRADITIONAL, true)
        val description = requireNotNull(activity.capturedDescription)
        assertNull(description.icon)
        assertEquals(activity.getString(R.string.app_name), description.label)
        assertTrue(SettingsManager.DEFAULT_RECENTS_ICON_FOLLOWS_SYSTEM_THEME)
        controller.pause().stop().destroy()
    }
}
