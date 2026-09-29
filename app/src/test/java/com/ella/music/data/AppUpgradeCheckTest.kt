package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpgradeCheckTest {
    @Test
    fun freshInstallWithoutMarkerOrDataDoesNotPrompt() {
        assertEquals(
            AppUpgradeCheck.FreshInstall,
            evaluateAppUpgrade(previousVersionName = null, currentVersionName = "1.2.9", hasExistingInstallData = false)
        )
    }

    @Test
    fun sameVersionDoesNotPrompt() {
        assertEquals(
            AppUpgradeCheck.SameVersion,
            evaluateAppUpgrade(previousVersionName = "1.2.9", currentVersionName = "1.2.9", hasExistingInstallData = true)
        )
    }

    @Test
    fun differentVersionNamePromptsWithPreviousVersion() {
        listOf("1.2.3", "1.2.8", "1.2.9-beta3").forEach { previous ->
            assertEquals(
                AppUpgradeCheck.Upgraded(previous),
                evaluateAppUpgrade(previousVersionName = previous, currentVersionName = "1.2.9", hasExistingInstallData = true)
            )
        }
    }

    @Test
    fun markerDecidesEvenWhenDataFileIsMissing() {
        assertEquals(
            AppUpgradeCheck.Upgraded("1.2.8"),
            evaluateAppUpgrade(previousVersionName = "1.2.8", currentVersionName = "1.2.9", hasExistingInstallData = false)
        )
    }

    @Test
    fun existingInstallPredatingMarkerPromptsAsUnknownOldVersion() {
        assertEquals(
            AppUpgradeCheck.Upgraded(null),
            evaluateAppUpgrade(previousVersionName = null, currentVersionName = "1.2.9", hasExistingInstallData = true)
        )
        assertEquals(
            AppUpgradeCheck.Upgraded(null),
            evaluateAppUpgrade(previousVersionName = "  ", currentVersionName = "1.2.9", hasExistingInstallData = true)
        )
    }
}
