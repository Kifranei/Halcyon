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
        this.metadataCategoryType() != null -> Screen.MetadataCategory.createRoute(this.metadataCategoryType().orEmpty(), fromDock = true)
        else -> null
    }
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
        this.metadataCategoryType() != null -> true
        else -> false
    }
}

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

internal fun NavHostController.navigateBottomDockRoute(
    route: String,
    currentRoute: String?
) {
    // Search is opened from the current page as a transient library tool. Keeping the
    // previous destination in the stack makes Back return to that page instead of Home.
    if (route.startsWith(Screen.LibrarySearch.baseRoute) && !currentRoute.isSearchRoute()) {
        navigate(route) { launchSingleTop = true }
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = currentRoute.isBottomDockRoute()
        }
        launchSingleTop = true
        restoreState = route.isBottomDockRoute()
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
