package com.ella.music.data

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppIconManagerTest {
    @Test
    fun `launcher alias stays in source namespace after application id changes`() {
        assertEquals(
            "com.ella.music.DefaultLauncherAlias",
            AppIconManager.launcherAliasClassName(".DefaultLauncherAlias")
        )
    }

    @Test
    fun `launcher alias resolution remains compatible with Android 10 and 11`() {
        val candidates = listOf(
            File("src/main/java/com/ella/music/data/AppIconManager.kt"),
            File("app/src/main/java/com/ella/music/data/AppIconManager.kt")
        )
        val source = candidates.firstOrNull(File::exists)?.readText()
            ?: error("Cannot locate AppIconManager.kt")

        assertFalse(
            "Class.packageName requires Android 12 and must not run during application startup",
            source.contains("::class.java.packageName")
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
