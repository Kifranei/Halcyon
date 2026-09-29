package com.ella.music.ui.about

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class UpdateReleaseTest {
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
