package com.ella.music

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ROUTE
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.navigation.SHORTCUT_ACTION_PLAY
import com.ella.music.ui.navigation.SHORTCUT_ACTION_SHUFFLE_ALL
import java.net.URLDecoder

internal fun Intent.resolveShortcutRoute(): String {
    val uri = data
    if (uri != null && uri.scheme == "halcyon") {
        uri.toHalcyonRoute()?.let { return it }
        uri.getQueryParameter("route")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }
    return getStringExtra(EXTRA_SHORTCUT_ROUTE)
        ?: ""
}

internal fun Intent.resolveShortcutAction(): String {
    data?.takeIf { it.scheme == "halcyon" }?.let { uri ->
        when (uri.host.orEmpty()) {
            "play" -> return SHORTCUT_ACTION_PLAY
            "shuffle_all" -> return SHORTCUT_ACTION_SHUFFLE_ALL
        }
    }
    return getStringExtra(com.ella.music.ui.navigation.EXTRA_SHORTCUT_ACTION)
        ?: ""
}

private fun Uri.toHalcyonRoute(): String? {
    val host = host.orEmpty()
    val path = pathSegments.map { it.urlDecode() }
    return when (host) {
        "search" -> {
            val keyword = getQueryParameter("keyword")
            Screen.LibrarySearch.createRoute(
                type = getQueryParameter("type"),
                keyword = keyword,
                focus = keyword.isNullOrBlank()
            )
        }
        "home", "main" -> Screen.Home.route
        "player" -> Screen.Player.route
        "shortcut" -> getQueryParameter("route")?.takeIf { it.isNotBlank() }
        "analytics" -> Screen.Analytics.route
        "settings" -> Screen.Settings.createRoute()
        "scan_settings" -> Screen.ScanSettings.createRoute()
        "library" -> Screen.Library.route
        "folder" -> path.joinToString("/")
            .takeIf { it.isNotBlank() }
            ?.let { Screen.FolderDetail.createRoute(it) }
            ?: Screen.Folder.createRoute()
        "album" -> Screen.Album.createRoute()
        "artist" -> path.joinToString("/")
            .takeIf { it.isNotBlank() }
            ?.let { Screen.ArtistDetail.createRoute(it) }
            ?: Screen.Artist.createRoute()
        "playlist" -> path.joinToString("/")
            .takeIf { it.isNotBlank() }
            ?.let { Screen.PlaylistDetail.createRoute(it) }
            ?: Screen.Playlists.createRoute()
        "folder_playlists" -> path.joinToString("/")
            .takeIf { it.isNotBlank() }
            ?.let { Screen.FolderPlaylistDetail.createRoute(it) }
            ?: Screen.FolderPlaylists.route
        "category" -> {
            val type = path.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
            val name = path.drop(1).joinToString("/").takeIf { it.isNotBlank() }
            if (name == null) Screen.MetadataCategory.createRoute(type)
            else Screen.MetadataCategoryDetail.createRoute(type, name)
        }
        else -> null
    }
}

/**
 * NavDestination.route is the composable pattern (`settings?fromDock={fromDock}`),
 * not the filled request. Rebuild query flags from the back-stack arguments so dock
 * Settings is treated as a first-level tab instead of a stacked secondary page.
 */
internal fun resolvedCurrentRoute(
    destinationRoute: String?,
    fromDock: Boolean? = null,
    metadataCategoryType: String? = null,
): String? {
    if (destinationRoute.isNullOrBlank()) return null
    return when {
        destinationRoute.isTopLevelRoute(Screen.Settings.baseRoute) ->
            Screen.Settings.createRoute(fromDock == true)
        destinationRoute.isTopLevelRoute(Screen.ScanSettings.baseRoute) ->
            Screen.ScanSettings.createRoute(fromDock = fromDock == true)
        destinationRoute == Screen.MetadataCategory.route ->
            metadataCategoryType?.takeIf { it.isNotBlank() }
                ?.let { Screen.MetadataCategory.createRoute(it, fromDock == true) }
                ?: destinationRoute
        else -> destinationRoute
    }
}

internal fun String?.toCurrentTabRoute(): String? {
    return when {
        this == null -> null
        this == Screen.Home.route -> Screen.Home.route
        this == Screen.Library.route -> Screen.Library.route
        this.isSearchRoute() -> Screen.LibrarySearch.createRoute()
        this.isTopLevelRoute(Screen.Playlists.baseRoute) -> Screen.Playlists.createRoute(fromDock = true)
        this.isTopLevelRoute(Screen.Folder.baseRoute) -> Screen.Folder.createRoute(fromDock = true)
        this.isTopLevelRoute(Screen.Artist.baseRoute) -> Screen.Artist.createRoute(fromDock = true)
        this.isTopLevelRoute(Screen.Album.baseRoute) -> Screen.Album.createRoute(fromDock = true)
        this.isDockSettingsRoute() -> Screen.Settings.createRoute(fromDock = true)
        this.isDockScanSettingsRoute() -> Screen.ScanSettings.createRoute(fromDock = true)
        this == Screen.Analytics.route -> Screen.Analytics.route
        this == Screen.LibraryAnalysis.route -> Screen.LibraryAnalysis.route
        this.metadataCategoryType() != null -> Screen.MetadataCategory.createRoute(this.metadataCategoryType().orEmpty(), fromDock = true)
        else -> null
    }
}

internal fun String?.isSettingsGraphRoute(): Boolean {
    val path = this?.substringBefore('?') ?: return false
    return path.isTopLevelRoute(Screen.Settings.baseRoute) ||
        path.isTopLevelRoute(Screen.ScanSettings.baseRoute) ||
        path == "settings_detail" ||
        path == "settings_home_display" ||
        path == "settings_bottom_navigation" ||
        path == "library_settings" ||
        path == "integration_settings" ||
        path == "lastfm_settings" ||
        path == "lyric_settings" ||
        path == "lyric_plugin_sources" ||
        path == "lyric_font" ||
        path == "audio_settings" ||
        path == "equalizer" ||
        path == "backup_settings" ||
        path == "cover_media_settings" ||
        path == "settings_wizard" ||
        path == "settings_maintenance" ||
        path == "navidrome_server_settings" ||
        path == "opensubsonic_server_settings" ||
        path == "emby_server_settings" ||
        path == "lx_source_settings" ||
        path == "webdav" ||
        path == "logs" ||
        path == "appearance_subpage" ||
        path.startsWith("appearance_subpage/")
}

internal fun String?.isSearchRoute(): Boolean {
    return this?.startsWith(Screen.LibrarySearch.baseRoute) == true ||
        this == Screen.LibrarySearch.route
}

internal fun String?.isBottomDockRoute(): Boolean {
    return when {
        this == null -> false
        this.isSearchRoute() -> true
        this == Screen.Home.route -> true
        this == Screen.Library.route -> true
        this.isTopLevelRoute(Screen.Playlists.baseRoute) -> true
        this.isTopLevelRoute(Screen.Folder.baseRoute) -> true
        this.isTopLevelRoute(Screen.Artist.baseRoute) -> true
        this.isTopLevelRoute(Screen.Album.baseRoute) -> true
        this.isDockSettingsRoute() -> true
        this.isDockScanSettingsRoute() -> true
        this == Screen.Analytics.route -> true
        this == Screen.LibraryAnalysis.route -> true
        this.metadataCategoryType() != null -> true
        else -> false
    }
}

internal fun shouldRestoreBottomDockState(route: String, currentRoute: String?): Boolean =
    route.isBottomDockRoute() && !currentRoute.isSettingsGraphRoute()

internal fun String?.matchesRoute(route: String): Boolean {
    return when {
        route.startsWith(Screen.LibrarySearch.baseRoute) -> this.isSearchRoute()
        route.isTopLevelRoute(Screen.Playlists.baseRoute) -> this.isTopLevelRoute(Screen.Playlists.baseRoute)
        route.isTopLevelRoute(Screen.Folder.baseRoute) -> this.isTopLevelRoute(Screen.Folder.baseRoute)
        route.isTopLevelRoute(Screen.Artist.baseRoute) -> this.isTopLevelRoute(Screen.Artist.baseRoute)
        route.isTopLevelRoute(Screen.Album.baseRoute) -> this.isTopLevelRoute(Screen.Album.baseRoute)
        route.isDockSettingsRoute() -> this.isDockSettingsRoute()
        route.isDockScanSettingsRoute() -> this.isDockScanSettingsRoute()
        route.metadataCategoryType() != null -> this.metadataCategoryType() == route.metadataCategoryType()
        else -> this == route
    }
}

internal fun isAtPlaybackSourceRoute(
    destinationRoute: String?,
    argument: (String) -> Any?,
    target: String
): Boolean {
    if (target.isBlank() || destinationRoute.isNullOrBlank()) return false
    if (playbackSourceRoutesMatch(destinationRoute, target)) return true
    val folderHierarchy = target == Screen.Folder.createRoute() ||
        target == Screen.Folder.baseRoute ||
        target.startsWith("${Screen.Folder.baseRoute}?")
    if (folderHierarchy && (
            destinationRoute == Screen.Folder.route ||
                destinationRoute == Screen.Folder.baseRoute ||
                destinationRoute.startsWith("${Screen.Folder.baseRoute}?")
            )
    ) {
        return true
    }
    return when (destinationRoute) {
        Screen.Home.route -> target == Screen.Home.route
        Screen.Library.route -> target == Screen.Library.route
        Screen.AlbumDetail.route -> {
            val albumId = argument("albumId").toNavLong() ?: return false
            playbackSourceRoutesMatch(target, Screen.AlbumDetail.createRoute(albumId))
        }
        Screen.ArtistDetail.route -> {
            val name = argument("artistName")?.toString()?.takeIf { it.isNotBlank() } ?: return false
            playbackSourceRoutesMatch(target, Screen.ArtistDetail.createRoute(name)) ||
                playbackSourceRoutesMatch(target, "artist/$name")
        }
        Screen.PlaylistDetail.route -> {
            val playlistId = argument("playlistId")?.toString()?.takeIf { it.isNotBlank() } ?: return false
            playbackSourceRoutesMatch(target, Screen.PlaylistDetail.createRoute(playlistId)) ||
                playbackSourceRoutesMatch(target, "playlist/$playlistId")
        }
        Screen.FolderDetail.route -> {
            val folderPath = argument("folderPath")?.toString()?.takeIf { it.isNotBlank() } ?: return false
            playbackSourceRoutesMatch(target, Screen.FolderDetail.createRoute(folderPath)) ||
                playbackSourceRoutesMatch(target, "folder/$folderPath")
        }
        Screen.FolderPlaylistDetail.route -> {
            val playlistId = argument("playlistId")?.toString()?.takeIf { it.isNotBlank() } ?: return false
            playbackSourceRoutesMatch(target, Screen.FolderPlaylistDetail.createRoute(playlistId)) ||
                playbackSourceRoutesMatch(target, "folder_playlist/$playlistId")
        }
        Screen.MetadataCategoryDetail.route -> {
            val type = argument("type")?.toString()?.takeIf { it.isNotBlank() } ?: return false
            val name = argument("name")?.toString()?.takeIf { it.isNotBlank() } ?: return false
            playbackSourceRoutesMatch(target, Screen.MetadataCategoryDetail.createRoute(type, name))
        }
        Screen.LibraryAnalysisBucket.route -> {
            val kind = argument("kind")?.toString()?.takeIf { it.isNotBlank() } ?: return false
            val label = argument("label")?.toString()?.takeIf { it.isNotBlank() } ?: return false
            val decoded = decodeNavComponent(label)
            playbackSourceRoutesMatch(target, Screen.LibraryAnalysis.createBucketRoute(kind == "quality", decoded)) ||
                playbackSourceRoutesMatch(target, Screen.LibraryAnalysis.createBucketRoute(kind == "quality", label))
        }
        else -> false
    }
}

internal fun navRouteIdentity(
    destinationRoute: String?,
    argument: (String) -> Any?
): String {
    if (destinationRoute.isNullOrBlank()) return ""
    val extras = when (destinationRoute) {
        Screen.FolderDetail.route -> argument("folderPath")?.toString().orEmpty()
        Screen.FolderPlaylistDetail.route -> argument("playlistId")?.toString().orEmpty()
        Screen.AlbumDetail.route -> argument("albumId")?.toString().orEmpty()
        Screen.ArtistDetail.route -> argument("artistName")?.toString().orEmpty()
        Screen.PlaylistDetail.route -> argument("playlistId")?.toString().orEmpty()
        Screen.MetadataCategoryDetail.route ->
            listOf(argument("type"), argument("name")).joinToString("/") { it?.toString().orEmpty() }
        Screen.LibraryAnalysisBucket.route ->
            listOf(argument("kind"), argument("label")).joinToString("/") { it?.toString().orEmpty() }
        else -> ""
    }.trim().trim('/')
    return if (extras.isBlank()) destinationRoute else "$destinationRoute|$extras"
}

/** Back from artist/album/etc. should restore the player overlay instead of revealing Home. */
internal fun shouldRestorePlayerOnBack(
    returnToPlayerRoute: String?,
    previousRouteIdentity: String
): Boolean = !returnToPlayerRoute.isNullOrBlank() && previousRouteIdentity == returnToPlayerRoute

internal fun playerDismissBackEnabled(
    playerVisible: Boolean,
    restorePlayerOnBack: Boolean
): Boolean = playerVisible && !restorePlayerOnBack

internal fun playbackSourceRoutesMatch(left: String, right: String): Boolean {
    if (left == right) return true
    val decodedLeft = decodeNavComponent(decodeNavComponent(left))
    val decodedRight = decodeNavComponent(decodeNavComponent(right))
    return decodedLeft == decodedRight
}

private fun decodeNavComponent(value: String): String =
    runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

private fun Any?.toNavLong(): Long? = when (this) {
    is Long -> this
    is Int -> toLong()
    is String -> toLongOrNull()
    else -> null
}

internal fun NavHostController.navigateBottomDockRoute(
    route: String,
    currentRoute: String?
) {
    // Search is opened from the current page as a transient library tool. Keep its Back
    // behaviour, but remove it before switching to another bottom-dock destination. Otherwise
    // restoreState can resurrect the search entry and make a later Settings click land back on
    // Search.
    val leavingSearch = currentRoute.isSearchRoute()
    if (leavingSearch) {
        popBackStack()
    }
    if (route.startsWith(Screen.LibrarySearch.baseRoute) && !leavingSearch) {
        navigate(route) { launchSingleTop = true }
        return
    }
    val restoringDockState = shouldRestoreBottomDockState(route, currentRoute)
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = currentRoute.isBottomDockRoute()
        }
        launchSingleTop = true
        // A settings child is intentionally left on the back stack when a source is opened. If
        // the user taps the Settings dock item after returning, start at Settings home instead
        // of restoring that child state again.
        restoreState = restoringDockState
    }
}

/**
 * Routes emitted by shared song-information surfaces may be either a real detail page or one of
 * the first-level destinations. Keep both cases on the same navigation policy as the bottom dock
 * so a jump cannot accidentally leave a stale search/settings entry on top of the destination.
 */
internal fun NavHostController.navigateAppRoute(
    route: String,
    currentRoute: String?
) {
    if (route.isBottomDockRoute()) {
        navigateBottomDockRoute(route, currentRoute)
    } else {
        navigate(route)
    }
}

/** Leave Settings as a first-level tab instead of stacking the playback source on top of it. */
internal fun NavHostController.navigatePlaybackSourceRoute(
    route: String,
    currentRoute: String?
) {
    if (currentRoute.isSettingsGraphRoute()) {
        // Keep the settings destination beneath the source page. Back therefore returns to the
        // exact settings child the user was editing, instead of jumping to Home.
        navigate(route) {
            launchSingleTop = true
            restoreState = false
        }
        return
    }
    if (route.isBottomDockRoute()) {
        navigateBottomDockRoute(route, currentRoute)
        return
    }
    navigate(route) {
        launchSingleTop = true
        restoreState = false
    }
}

private fun String?.isTopLevelRoute(baseRoute: String): Boolean =
    this == baseRoute || this?.startsWith("$baseRoute?") == true

private fun String?.hasBooleanQueryFlag(name: String): Boolean =
    this?.substringAfter('?', "")
        ?.split('&')
        ?.firstOrNull { it.startsWith("$name=") }
        ?.substringAfter('=')
        ?.equals("true", ignoreCase = true) == true

private fun String?.isDockSettingsRoute(): Boolean =
    this.isTopLevelRoute(Screen.Settings.baseRoute) && this.hasBooleanQueryFlag("fromDock")

private fun String?.isDockScanSettingsRoute(): Boolean =
    this.isTopLevelRoute(Screen.ScanSettings.baseRoute) && this.hasBooleanQueryFlag("fromDock")

private fun String?.metadataCategoryType(): String? {
    val route = this?.substringBefore('?') ?: return null
    val parts = route.split('/')
    if (parts.size != 2 || parts[0] != Screen.MetadataCategory.baseRoute) return null
    return parts[1].urlDecode().takeIf { type ->
        type in setOf("folder", "genre", "year", "composer", "arranger", "lyricist")
    }
}

private fun String.urlDecode(): String =
    runCatching { URLDecoder.decode(this, "UTF-8") }.getOrDefault(this)

internal fun String.isMusicSymbolOnly(): Boolean {
    val content = trim()
    if (content.isBlank()) return true

    return content.all { char ->
        char.isWhitespace() ||
            char in setOf('♪', '♫', '♬', '♩', '♭', '♯', '♮') ||
            Character.UnicodeBlock.of(char) == Character.UnicodeBlock.MUSICAL_SYMBOLS
    }
}

internal fun Uri.toPrimaryStoragePath(): String? {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull() ?: return null
    val parts = documentId.split(':', limit = 2)
    val volume = parts.firstOrNull().orEmpty()
    val path = parts.getOrNull(1).orEmpty().trim('/')
    return when {
        volume.equals("primary", ignoreCase = true) && path.isBlank() -> "/storage/emulated/0"
        volume.equals("primary", ignoreCase = true) -> "/storage/emulated/0/$path"
        else -> null
    }
}
