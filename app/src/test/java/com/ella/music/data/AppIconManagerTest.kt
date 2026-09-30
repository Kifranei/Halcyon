package com.ella.music.data

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppIconManagerTest {
    @Test
    fun `traditional icon is selectable without replacing the default`() {
        assertEquals(SettingsManager.APP_ICON_STYLE_TRADITIONAL, AppIconManager.normalize("traditional"))
        assertEquals(SettingsManager.APP_ICON_STYLE_DEFAULT, AppIconManager.normalize(null))
        assertEquals(SettingsManager.APP_ICON_STYLE_DEFAULT, AppIconManager.normalize("unknown"))
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.exists() }.readText()
        val traditional = manifest.substringAfter("android:name=\"com.ella.music.TraditionalLauncherAlias\"")
            .substringBefore("</activity-alias>")
        assertTrue(traditional.contains("android:enabled=\"false\""))
        assertTrue(traditional.contains("@mipmap/ic_launcher_traditional"))
    }


    @Test
    fun `removed black hair preference migrates to default icon`() {
        assertEquals(SettingsManager.APP_ICON_STYLE_DEFAULT, AppIconManager.normalize("black_hair"))
    }

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
