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
}
