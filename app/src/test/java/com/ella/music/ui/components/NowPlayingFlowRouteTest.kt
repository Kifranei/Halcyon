package com.ella.music.ui.components

import com.ella.music.ui.navigation.Screen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingFlowRouteTest {
    @Test
    fun topLevelBrowsingPages_areSupported() {
        listOf(
            Screen.Home.route,
            Screen.Library.route,
            Screen.Album.route,
            Screen.Artist.route,
            Screen.Folder.route,
            Screen.FolderPlaylists.route,
            Screen.Playlists.route,
            Screen.MetadataCategory.route
        ).forEach { route -> assertTrue(route, supportsNowPlayingFlowBackground(route)) }
    }

    @Test
    fun detailAndSettingsPages_remainOpaque() {
        listOf(
            Screen.AlbumDetail.route,
            Screen.ArtistDetail.route,
            Screen.PlaylistDetail.route,
            Screen.FolderDetail.route,
            Screen.Settings.route,
            null
        ).forEach { route -> assertFalse(route, supportsNowPlayingFlowBackground(route)) }
    }
}
