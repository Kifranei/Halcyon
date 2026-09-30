package com.ella.music.data.netease

import android.app.Application
import android.content.Context
import android.content.Intent
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, manifest = Config.NONE)
class NeteaseLinkRoutingTest {
    @Before @After fun reset() {
        NeteaseLinks.update(RuntimeEnvironment.getApplication()) { NeteaseLinkSettings() }
        NeteaseLinks.commentSheetTarget.value = null
        NeteaseLinks.webSheetUrl.value = null
    }

    @Test fun blankCustomCommentsOpenEachNativeResource() {
        val context = RuntimeEnvironment.getApplication()
        NeteaseLinks.update(context) { NeteaseLinkSettings(NeteaseLinkTarget.Custom) }
        listOf(
            NeteaseLinkKind.Comment to NeteaseCommentResource.Song,
            NeteaseLinkKind.AlbumComment to NeteaseCommentResource.Album,
            NeteaseLinkKind.MusicVideoComment to NeteaseCommentResource.MusicVideo
        ).forEach { (kind, resource) ->
            NeteaseLinks.open(context, kind, "123")
            assertEquals(NeteaseCommentTarget("123", resource), NeteaseLinks.commentSheetTarget.value)
        }
    }

    @Test fun customCommentRulesAreIndependentForEachResource() {
        val settings = NeteaseLinkSettings(NeteaseLinkTarget.Custom, custom = mapOf(NeteaseLinkKind.Comment to "orpheus://comment/{id}"))
        assertFalse(NeteaseLinks.usesNativeComments(settings, NeteaseLinkKind.Comment))
        assertTrue(NeteaseLinks.usesNativeComments(settings, NeteaseLinkKind.AlbumComment))
        assertTrue(NeteaseLinks.usesNativeComments(settings, NeteaseLinkKind.MusicVideoComment))
    }

    @Test fun webAndBlankCustomAlbumsUseSystemWebIntent() {
        val context = RuntimeEnvironment.getApplication()
        listOf(NeteaseLinkTarget.Web, NeteaseLinkTarget.Custom).forEach { target ->
            NeteaseLinks.update(context) { NeteaseLinkSettings(target) }
            NeteaseLinks.open(context, NeteaseLinkKind.Album, "123")
            val intent = shadowOf(context).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("https://y.music.163.com/m/album?id=123", intent.dataString)
            assertNull(NeteaseLinks.webSheetUrl.value)
        }
    }

    @Test fun externalMusicVideoUsesConfiguredSystemLinkInsteadOfWebSheet() {
        val context = RuntimeEnvironment.getApplication()
        listOf(NeteaseLinkTarget.Web, NeteaseLinkTarget.Custom).forEach { target ->
            NeteaseLinks.update(context) {
                NeteaseLinkSettings(target = target, openMusicVideoExternally = true)
            }
            NeteaseLinks.webSheetUrl.value = null
            NeteaseLinks.open(context, NeteaseLinkKind.MusicVideo, "456")
            val intent = shadowOf(context).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("https://y.music.163.com/m/mv?id=456", intent.dataString)
            assertNull(NeteaseLinks.webSheetUrl.value)
        }
    }

    @Test fun customSchemeIntentFragmentPreservesPackageAndExtras() {
        val intent = NeteaseLinks.intentForUrl("orpheus://artist/123#Intent;package=com.netease.cloudmusic;S.source=Halcyon;end")
        assertEquals("orpheus://artist/123", intent.dataString)
        assertEquals("com.netease.cloudmusic", intent.`package`)
        assertEquals("Halcyon", intent.getStringExtra("source"))
    }

    @Test fun standardIntentAndAndroidAppFormatsRemainSupported() {
        val standard = NeteaseLinks.intentForUrl("intent://artist/123#Intent;scheme=orpheus;package=com.netease.cloudmusic;end")
        assertEquals("orpheus://artist/123", standard.dataString)
        assertEquals("com.netease.cloudmusic", standard.`package`)
        val app = NeteaseLinks.intentForUrl("android-app://com.netease.cloudmusic/orpheus/artist/123")
        assertEquals("com.netease.cloudmusic", app.`package`)
        assertEquals("orpheus://artist/123", app.dataString)
    }

    @Test fun missingAndInvalidCommentDefaultKeepTheExistingRecommendedTab() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("netease_links", Context.MODE_PRIVATE)
        prefs.edit().remove("comment_default_sort").apply()
        assertEquals(NeteaseCommentSort.Recommend, NeteaseLinks.read(context).defaultCommentSort)
        prefs.edit().putInt("comment_default_sort", 99).apply()
        assertEquals(NeteaseCommentSort.Recommend, NeteaseLinks.read(context).defaultCommentSort)
    }

    @Test fun selectedCommentDefaultSurvivesReloadAndIndependentLinkChanges() {
        val context = RuntimeEnvironment.getApplication()
        val custom = mapOf(NeteaseLinkKind.Comment to "orpheus://comment/{id}")
        NeteaseLinks.update(context) {
            NeteaseLinkSettings(NeteaseLinkTarget.Custom, openMusicVideoExternally = true, custom = custom)
        }
        for (sort in NeteaseCommentSort.entries) {
            NeteaseLinks.update(context) { it.copy(defaultCommentSort = sort) }
            val reloaded = NeteaseLinks.read(context)
            assertEquals(sort, reloaded.defaultCommentSort)
            assertEquals(NeteaseLinkTarget.Custom, reloaded.target)
            assertTrue(reloaded.openMusicVideoExternally)
            assertEquals(custom, reloaded.custom)
        }
        NeteaseLinks.update(context) { it.copy(target = NeteaseLinkTarget.Web) }
        assertEquals(NeteaseCommentSort.Latest, NeteaseLinks.read(context).defaultCommentSort)
    }
}
