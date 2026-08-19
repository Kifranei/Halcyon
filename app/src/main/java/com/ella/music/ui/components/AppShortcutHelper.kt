package com.ella.music.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.ella.music.MainActivity
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ACTION
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ROUTE
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.navigation.SHORTCUT_ACTION_PLAY
import com.ella.music.ui.navigation.SHORTCUT_ACTION_SHUFFLE_ALL
import java.util.Locale

fun updateEllaDynamicShortcuts(
    context: Context,
    shortcutIds: List<String>,
    libraryLabel: String,
    searchLabel: String,
    shuffleLabel: String
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
    val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
    val resolvedLibraryLabel = libraryLabel.localizedShortcutDefault(
        SettingsManager.DEFAULT_SHORTCUT_LIBRARY_LABEL,
        context.getString(R.string.shortcut_library_short)
    )
    val resolvedSearchLabel = searchLabel.localizedShortcutDefault(
        SettingsManager.DEFAULT_SHORTCUT_PLAYLISTS_LABEL,
        context.getString(R.string.shortcut_search_short)
    )
    val resolvedShuffleLabel = shuffleLabel.localizedShortcutDefault(
        SettingsManager.DEFAULT_SHORTCUT_FOLDER_LABEL,
        context.getString(R.string.shortcut_shuffle_all_short)
    )
    val launcherLimit = shortcutManager.maxShortcutCountPerActivity
        .coerceAtLeast(0)
        .coerceAtMost(SettingsManager.MAX_APP_SHORTCUTS)
    val shortcuts = shortcutIds
        .asSequence()
        .map { it.trim().lowercase(Locale.ROOT) }
        .distinct()
        .take(launcherLimit)
        .mapNotNull { shortcutId ->
            context.appShortcutTarget(
                shortcutId = shortcutId,
                libraryLabel = resolvedLibraryLabel,
                searchLabel = resolvedSearchLabel,
                shuffleLabel = resolvedShuffleLabel
            )
        }
        .mapIndexed { rank, target -> target.build(context, rank) }
        .toList()

    // Assigning the complete list also removes dynamic shortcuts that the user unchecked. This
    // avoids leaving stale launcher entries behind when the selection is reduced to fewer than 5.
    runCatching { shortcutManager.dynamicShortcuts = shortcuts }
    runCatching { shortcutManager.updateShortcuts(shortcuts) }
}

private sealed interface AppShortcutTarget {
    val id: String
    val label: String
    val iconRes: Int

    fun build(context: Context, rank: Int): ShortcutInfo

    data class Route(
        override val id: String,
        override val label: String,
        override val iconRes: Int,
        val route: String
    ) : AppShortcutTarget {
        override fun build(context: Context, rank: Int): ShortcutInfo =
            context.buildEllaShortcut(id, label, route, iconRes, rank)
    }

    data class Action(
        override val id: String,
        override val label: String,
        override val iconRes: Int,
        val action: String
    ) : AppShortcutTarget {
        override fun build(context: Context, rank: Int): ShortcutInfo =
            context.buildEllaActionShortcut(id, label, action, iconRes, rank)
    }
}

private fun Context.appShortcutTarget(
    shortcutId: String,
    libraryLabel: String,
    searchLabel: String,
    shuffleLabel: String
): AppShortcutTarget? = when (shortcutId) {
    SettingsManager.APP_SHORTCUT_LIBRARY -> AppShortcutTarget.Route(
        id = shortcutId,
        label = libraryLabel,
        route = Screen.Library.route,
        iconRes = R.drawable.ic_shortcut_library
    )
    SettingsManager.APP_SHORTCUT_SEARCH -> AppShortcutTarget.Route(
        id = shortcutId,
        label = searchLabel,
        route = Screen.LibrarySearch.createRoute(),
        iconRes = R.drawable.ic_shortcut_search
    )
    SettingsManager.APP_SHORTCUT_PLAY -> AppShortcutTarget.Action(
        id = shortcutId,
        label = getString(R.string.shortcut_play_short),
        action = SHORTCUT_ACTION_PLAY,
        iconRes = R.drawable.ic_player_play
    )
    SettingsManager.APP_SHORTCUT_SHUFFLE_ALL -> AppShortcutTarget.Action(
        id = shortcutId,
        label = shuffleLabel,
        action = SHORTCUT_ACTION_SHUFFLE_ALL,
        iconRes = R.drawable.ic_shuffle
    )
    SettingsManager.APP_SHORTCUT_PLAYLISTS -> appRouteShortcut(
        shortcutId, R.string.settings_library_tile_playlist, Screen.Playlists.createRoute(), R.drawable.ic_shortcut_playlist
    )
    SettingsManager.APP_SHORTCUT_FOLDERS -> appRouteShortcut(
        shortcutId, R.string.category_folder, Screen.MetadataCategory.createRoute("folder"), R.drawable.ic_shortcut_folder
    )
    SettingsManager.APP_SHORTCUT_FOLDER_TREE -> appRouteShortcut(
        shortcutId, R.string.category_folder_tree, Screen.Folder.createRoute(), R.drawable.ic_shortcut_folder_hierarchy
    )
    SettingsManager.APP_SHORTCUT_FOLDER_PLAYLISTS -> appRouteShortcut(
        shortcutId, R.string.folder_playlist_title, Screen.FolderPlaylists.route, R.drawable.ic_shortcut_playlist
    )
    SettingsManager.APP_SHORTCUT_ALBUMS -> appRouteShortcut(
        shortcutId, R.string.settings_library_tile_album, Screen.Album.createRoute(), R.drawable.ic_shortcut_album
    )
    SettingsManager.APP_SHORTCUT_ARTISTS -> appRouteShortcut(
        shortcutId, R.string.settings_library_tile_artist, Screen.Artist.createRoute(), R.drawable.ic_shortcut_artist
    )
    SettingsManager.APP_SHORTCUT_GENRES -> appRouteShortcut(
        shortcutId, R.string.category_genre, Screen.MetadataCategory.createRoute("genre"), R.drawable.ic_shortcut_tag
    )
    SettingsManager.APP_SHORTCUT_YEARS -> appRouteShortcut(
        shortcutId, R.string.category_year, Screen.MetadataCategory.createRoute("year"), R.drawable.ic_shortcut_calendar
    )
    SettingsManager.APP_SHORTCUT_COMPOSERS -> appRouteShortcut(
        shortcutId, R.string.category_composer, Screen.MetadataCategory.createRoute("composer"), R.drawable.ic_shortcut_composer
    )
    SettingsManager.APP_SHORTCUT_ARRANGERS -> appRouteShortcut(
        shortcutId, R.string.category_arranger, Screen.MetadataCategory.createRoute("arranger"), R.drawable.ic_shortcut_arranger
    )
    SettingsManager.APP_SHORTCUT_LYRICISTS -> appRouteShortcut(
        shortcutId, R.string.category_lyricist, Screen.MetadataCategory.createRoute("lyricist"), R.drawable.ic_shortcut_lyricist
    )
    SettingsManager.APP_SHORTCUT_ANALYTICS -> appRouteShortcut(
        shortcutId, R.string.settings_library_tile_analytics, Screen.Analytics.route, R.drawable.ic_music_note
    )
    SettingsManager.APP_SHORTCUT_LIBRARY_ANALYSIS -> appRouteShortcut(
        shortcutId, R.string.analytics_library_analysis, Screen.LibraryAnalysis.route, R.drawable.ic_music_note
    )
    SettingsManager.APP_SHORTCUT_SCAN_SETTINGS -> appRouteShortcut(
        shortcutId, R.string.folder_scan_settings, Screen.ScanSettings.createRoute(), R.drawable.ic_music_note
    )
    SettingsManager.APP_SHORTCUT_SETTINGS -> appRouteShortcut(
        shortcutId, R.string.settings, Screen.Settings.createRoute(), R.drawable.ic_music_note
    )
    else -> null
}

private fun Context.appRouteShortcut(
    id: String,
    labelRes: Int,
    route: String,
    iconRes: Int
): AppShortcutTarget.Route = AppShortcutTarget.Route(
    id = id,
    label = getString(labelRes),
    route = route,
    iconRes = iconRes
)

private fun String.localizedShortcutDefault(chineseDefault: String, localizedDefault: String): String {
    val value = trim()
    return if (value.isBlank() || value == chineseDefault) localizedDefault else value
}

fun requestPinnedEllaShortcut(
    context: Context,
    id: String,
    label: String,
    route: String
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
    if (!shortcutManager.isRequestPinShortcutSupported) return false
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(EXTRA_SHORTCUT_ROUTE, route)
    }
    val shortcutLabel = label.shortcutLabelForRoute(route)
    val appName = context.getString(R.string.app_name)
    val shortcut = ShortcutInfo.Builder(context, id.toShortcutId())
        .setShortLabel(shortcutLabel.take(10).ifBlank { appName })
        .setLongLabel(shortcutLabel.ifBlank { appName })
        .setIcon(Icon.createWithResource(context, shortcutIconForRoute(route)))
        .setIntent(intent)
        .build()
    shortcutManager.requestPinShortcut(shortcut, null)
    return true
}

private fun String.toShortcutId(): String =
    "halcyon_${replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)}"

private fun Context.buildEllaShortcut(
    id: String,
    label: String,
    route: String,
    iconRes: Int,
    rank: Int
): ShortcutInfo {
    val appName = getString(R.string.app_name)
    return ShortcutInfo.Builder(this, id)
        .setShortLabel(label.take(10).ifBlank { appName })
        .setLongLabel(label.ifBlank { appName })
        .setIcon(Icon.createWithResource(this, iconRes))
        .setIntent(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_SHORTCUT_ROUTE, route)
            }
        )
        .setRank(rank)
        .build()
}

private fun Context.buildEllaActionShortcut(
    id: String,
    label: String,
    shortcutAction: String,
    iconRes: Int,
    rank: Int
): ShortcutInfo {
    val appName = getString(R.string.app_name)
    return ShortcutInfo.Builder(this, id)
        .setShortLabel(label.take(10).ifBlank { appName })
        .setLongLabel(label.ifBlank { appName })
        .setIcon(Icon.createWithResource(this, iconRes))
        .setIntent(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_SHORTCUT_ACTION, shortcutAction)
            }
        )
        .setRank(rank)
        .build()
}

private fun shortcutIconForRoute(route: String): Int = when (route) {
    Screen.Library.route -> R.drawable.ic_shortcut_library
    Screen.Playlists.createRoute() -> R.drawable.ic_shortcut_playlist
    Screen.Folder.createRoute() -> R.drawable.ic_shortcut_folder
    Screen.Album.createRoute() -> R.drawable.ic_shortcut_album
    Screen.Artist.createRoute() -> R.drawable.ic_shortcut_artist
    "category/folder" -> R.drawable.ic_shortcut_folder
    "category/genre" -> R.drawable.ic_shortcut_tag
    "category/year" -> R.drawable.ic_shortcut_calendar
    "category/composer" -> R.drawable.ic_shortcut_composer
    "category/arranger" -> R.drawable.ic_shortcut_arranger
    "category/lyricist" -> R.drawable.ic_shortcut_lyricist
    else -> shortcutIconForRoutePrefix(route)
}

private fun shortcutIconForRoutePrefix(route: String): Int = when {
    route == Screen.Album.baseRoute || route.startsWith("${Screen.Album.baseRoute}?") -> R.drawable.ic_shortcut_album
    route == Screen.Artist.baseRoute || route.startsWith("${Screen.Artist.baseRoute}?") -> R.drawable.ic_shortcut_artist
    route == Screen.Folder.baseRoute || route.startsWith("${Screen.Folder.baseRoute}?") -> R.drawable.ic_shortcut_folder
    route == Screen.Playlists.baseRoute || route.startsWith("${Screen.Playlists.baseRoute}?") -> R.drawable.ic_shortcut_playlist
    route.startsWith("album/") -> R.drawable.ic_shortcut_album
    route.startsWith("artist/") -> R.drawable.ic_shortcut_artist
    route.startsWith("folder/") -> R.drawable.ic_shortcut_folder_hierarchy
    route.startsWith("playlist/") -> R.drawable.ic_shortcut_playlist
    route.startsWith("category/folder/") -> R.drawable.ic_shortcut_folder
    route.startsWith("category/genre/") -> R.drawable.ic_shortcut_tag
    route.startsWith("category/year/") -> R.drawable.ic_shortcut_calendar
    route.startsWith("category/composer/") -> R.drawable.ic_shortcut_composer
    route.startsWith("category/arranger/") -> R.drawable.ic_shortcut_arranger
    route.startsWith("category/lyricist/") -> R.drawable.ic_shortcut_lyricist
    else -> R.drawable.ic_music_note
}

private fun String.shortcutLabelForRoute(route: String): String {
    return if (route.startsWith("folder/") || route.startsWith("category/folder/")) {
        trim()
            .trimEnd('/', '\\')
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { this }
    } else {
        this
    }
}
