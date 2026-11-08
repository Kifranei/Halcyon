package com.ella.music.ui.about

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class UpdateReleaseTest {
    @Test fun sixtyFourBitDevicePrefersArm64WhenTheAbiListStartsWithV7a() {
        val assets = halcyonApks()
        val matched = matchAssetForDevice(
            assets,
            supportedAbis = arrayOf("armeabi-v7a", "arm64-v8a"),
            supported64BitAbis = arrayOf("arm64-v8a")
        )
        assertTrue(matched!!.name.contains("arm64-v8a"))
    }

    @Test fun sixtyFourBitDevicePrefersUniversalWhenNoArm64PackageExists() {
        val assets = halcyonApks().filterNot { it.name.contains("arm64-v8a") }
        val matched = matchAssetForDevice(
            assets,
            supportedAbis = arrayOf("armeabi-v7a", "arm64-v8a"),
            supported64BitAbis = arrayOf("arm64-v8a")
        )
        assertTrue(matched!!.name.contains("universal"))
    }

    @Test fun thirtyTwoBitDeviceStillSelectsTheV7aPackage() {
        val matched = matchAssetForDevice(
            halcyonApks(),
            supportedAbis = arrayOf("armeabi-v7a", "armeabi"),
            supported64BitAbis = emptyArray()
        )
        assertTrue(matched!!.name.contains("armeabi-v7a"))
    }

    private fun halcyonApks() = listOf(
        ReleaseApkAsset("Halcyon-1.3.0-be9dd1-armeabi-v7a-release.APK", "https://example/v7a", 1),
        ReleaseApkAsset("Halcyon-1.3.0-f90f05-arm64-v8a-release.APK", "https://example/arm64", 1),
        ReleaseApkAsset("Halcyon-1.3.0-dc35c5-universal-release.APK", "https://example/universal", 1)
    )

    @Test fun stableChannelRejectsMislabeledBetaRelease() {
        val releases = JSONArray("""[
            {"tag_name":"1.2.9-beta3","prerelease":false},
            {"tag_name":"1.2.8","prerelease":false}
        ]""")
        assertEquals("1.2.8", selectRelease(releases, false)?.getString("tag_name"))
        assertEquals("1.2.9-beta3", selectRelease(releases, true)?.getString("tag_name"))
    }

    @Test fun prereleaseSuffixesAreComparedNumerically() {
        assertTrue(compareVersionNames("1.2.9-beta3", "1.2.9-beta2") > 0)
        assertTrue(compareVersionNames("1.2.9-beta10", "1.2.9-beta9") > 0)
        assertTrue(compareVersionNames("1.2.9", "1.2.9-rc1") > 0)
        assertTrue(compareVersionNames("1.3.0-beta1", "1.2.9") > 0)
        assertEquals(0, compareVersionNames("v1.2.9+build4", "1.2.9"))
    }

    @Test fun channelSelectionSkipsDraftsAndKeepsStableUpgradeAfterBeta() {
        val releases = JSONArray("""[
            {"tag_name":"1.3.0-beta2","prerelease":true},
            {"tag_name":"1.3.0-beta10","prerelease":true},
            {"tag_name":"1.2.9","prerelease":false},
            {"tag_name":"9.0.0","draft":true}
        ]""")
        assertEquals("1.2.9", selectRelease(releases, false)?.getString("tag_name"))
        assertEquals("1.3.0-beta10", selectRelease(releases, true)?.getString("tag_name"))
        releases.put(org.json.JSONObject("""{"tag_name":"1.3.0","prerelease":false}"""))
        assertEquals("1.3.0", selectRelease(releases, true)?.getString("tag_name"))
    }
}
