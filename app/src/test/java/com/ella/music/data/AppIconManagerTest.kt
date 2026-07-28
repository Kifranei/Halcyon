package com.ella.music.data

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconManagerTest {
    @Test
    fun `launcher alias stays in source namespace after application id changes`() {
        assertEquals(
            "com.ella.music.DefaultLauncherAlias",
            AppIconManager.launcherAliasClassName(".DefaultLauncherAlias")
        )
    }

    @Test
    fun `default component state follows manifest without redundant package manager writes`() {
        assertFalse(
            AppIconManager.needsAliasStateChange(
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                manifestEnabled = true,
                desiredEnabled = true
            )
        )
        assertFalse(
            AppIconManager.needsAliasStateChange(
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                manifestEnabled = false,
                desiredEnabled = false
            )
        )
    }

    @Test
    fun `explicit component overrides are compared with desired state`() {
        assertTrue(
            AppIconManager.needsAliasStateChange(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                manifestEnabled = true,
                desiredEnabled = true
            )
        )
        assertTrue(
            AppIconManager.needsAliasStateChange(
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                manifestEnabled = false,
                desiredEnabled = false
            )
        )
    }
}
