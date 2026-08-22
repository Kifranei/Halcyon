package com.ella.music

import com.ella.music.ui.navigation.Screen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceRouteTest {
    @Test
    fun albumDetailMatchesConcreteRouteFromNavArguments() {
        assertTrue(
            isAtPlaybackSourceRoute(
                destinationRoute = Screen.AlbumDetail.route,
                argument = { if (it == "albumId") 11L else null },
                target = Screen.AlbumDetail.createRoute(11L)
            )
        )
        assertFalse(
            isAtPlaybackSourceRoute(
                destinationRoute = Screen.AlbumDetail.route,
                argument = { if (it == "albumId") 11L else null },
                target = Screen.AlbumDetail.createRoute(12L)
            )
        )
    }

    @Test
    fun analysisBucketMatchesQualityAndFormatRoutes() {
        assertTrue(
            isAtPlaybackSourceRoute(
                destinationRoute = Screen.LibraryAnalysisBucket.route,
                argument = { name ->
                    when (name) {
                        "kind" -> "quality"
                        "label" -> "LOSSLESS"
                        else -> null
                    }
                },
                target = Screen.LibraryAnalysis.createBucketRoute(true, "LOSSLESS")
            )
        )
        assertFalse(
            isAtPlaybackSourceRoute(
                destinationRoute = Screen.LibraryAnalysisBucket.route,
                argument = { name ->
                    when (name) {
                        "kind" -> "format"
                        "label" -> "FLAC"
                        else -> null
                    }
                },
                target = Screen.LibraryAnalysis.createBucketRoute(true, "LOSSLESS")
            )
        )
    }

    @Test
    fun alreadyOnLibraryDoesNotNeedAnotherLibraryJump() {
        assertTrue(
            isAtPlaybackSourceRoute(
                destinationRoute = Screen.Library.route,
                argument = { null },
                target = Screen.Library.route
            )
        )
        assertFalse(
            isAtPlaybackSourceRoute(
                destinationRoute = Screen.Home.route,
                argument = { null },
                target = Screen.Library.route
            )
        )
    }

    @Test
    fun playlistDetailMatchesEncodedAndRawIds() {
        assertTrue(
            isAtPlaybackSourceRoute(
                destinationRoute = Screen.PlaylistDetail.route,
                argument = { if (it == "playlistId") "favorites" else null },
                target = Screen.PlaylistDetail.createRoute("favorites")
            )
        )
    }

    @Test
    fun folderDetailMatchesDecodedNavArgumentAgainstEncodedTarget() {
        val path = "/storage/emulated/0/Music/A B"
        assertTrue(
            isAtPlaybackSourceRoute(
                destinationRoute = Screen.FolderDetail.route,
                argument = { if (it == "folderPath") path else null },
                target = Screen.FolderDetail.createRoute(path)
            )
        )
    }

    @Test
    fun folderHierarchyMatchesDockRoute() {
        assertTrue(
            isAtPlaybackSourceRoute(
                destinationRoute = Screen.Folder.route,
                argument = { null },
                target = Screen.Folder.createRoute()
            )
        )
    }

    @Test
    fun folderDetailIdentitiesStayDistinctForDirectoryJumps() {
        val first = navRouteIdentity(Screen.FolderDetail.route) { if (it == "folderPath") "/Music/A" else null }
        val second = navRouteIdentity(Screen.FolderDetail.route) { if (it == "folderPath") "/Music/B" else null }
        val same = navRouteIdentity(Screen.FolderDetail.route) { if (it == "folderPath") "/Music/A" else null }
        assertTrue(first != second)
        assertTrue(first == same)
        assertTrue(
            navRouteIdentity(Screen.Folder.route) { null } != first
        )
    }
}
