package com.ella.music

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ella.music.data.SettingsManager
import com.ella.music.ui.navigation.Screen
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Settings

/**
 * Single source of truth for both the real bottom dock and its settings preview.
 */
@Composable
internal fun bottomDockTabCatalog(): Map<String, BottomDockTab> = linkedMapOf(
    SettingsManager.BOTTOM_DOCK_ITEM_HOME to BottomDockTab(
        route = Screen.Home.route,
        label = stringResource(R.string.tab_home),
        icon = MiuixIcons.Regular.Home
    ),
    SettingsManager.BOTTOM_DOCK_ITEM_LIBRARY to BottomDockTab(
        route = Screen.Library.route,
        label = stringResource(R.string.tab_library),
        icon = MiuixIcons.Regular.Music
    ),
    SettingsManager.BOTTOM_DOCK_ITEM_PLAYLISTS to BottomDockTab(
        route = Screen.Playlists.createRoute(fromDock = true),
        label = stringResource(R.string.category_playlist),
        icon = MiuixIcons.Regular.Notes
    ),
    SettingsManager.BOTTOM_DOCK_ITEM_FOLDER to BottomDockTab(
        route = Screen.MetadataCategory.createRoute("folder", fromDock = true),
        label = stringResource(R.string.category_folder),
        icon = MiuixIcons.Regular.Folder
    ),
    SettingsManager.BOTTOM_DOCK_ITEM_FOLDER_TREE to BottomDockTab(
        route = Screen.Folder.createRoute(fromDock = true),
        label = stringResource(R.string.category_folder_tree),
        icon = MiuixIcons.Regular.Folder
    ),
    SettingsManager.BOTTOM_DOCK_ITEM_ARTIST to BottomDockTab(
        route = Screen.Artist.createRoute(fromDock = true),
        label = stringResource(R.string.category_artist),
        icon = MiuixIcons.Regular.ContactsCircle
    ),
    SettingsManager.BOTTOM_DOCK_ITEM_ALBUM to BottomDockTab(
        route = Screen.Album.createRoute(fromDock = true),
        label = stringResource(R.string.category_album),
        icon = MiuixIcons.Regular.Album
    ),
    SettingsManager.BOTTOM_DOCK_ITEM_SCAN_SETTINGS to BottomDockTab(
        route = Screen.ScanSettings.createRoute(fromDock = true),
        label = stringResource(R.string.folder_scan_settings),
        icon = MiuixIcons.Regular.Settings
    ),
    SettingsManager.BOTTOM_DOCK_ITEM_SETTINGS to BottomDockTab(
        route = Screen.Settings.createRoute(fromDock = true),
        label = stringResource(R.string.tab_settings),
        icon = MiuixIcons.Regular.Settings
    ),
    SettingsManager.BOTTOM_DOCK_ITEM_YEAR to BottomDockTab(
        route = Screen.MetadataCategory.createRoute("year", fromDock = true),
        label = stringResource(R.string.category_year),
        icon = MiuixIcons.Regular.Album
    ),
    SettingsManager.BOTTOM_DOCK_ITEM_GENRE to BottomDockTab(
        route = Screen.MetadataCategory.createRoute("genre", fromDock = true),
        label = stringResource(R.string.category_genre),
        icon = MiuixIcons.Regular.Music
    ),
    SettingsManager.BOTTOM_DOCK_ITEM_COMPOSER to BottomDockTab(
        route = Screen.MetadataCategory.createRoute("composer", fromDock = true),
        label = stringResource(R.string.category_composer),
        icon = MiuixIcons.Regular.ContactsCircle
    ),
    SettingsManager.BOTTOM_DOCK_ITEM_ARRANGER to BottomDockTab(
        route = Screen.MetadataCategory.createRoute("arranger", fromDock = true),
        label = stringResource(R.string.category_arranger),
        icon = MiuixIcons.Regular.ContactsCircle
    ),
    SettingsManager.BOTTOM_DOCK_ITEM_LYRICIST to BottomDockTab(
        route = Screen.MetadataCategory.createRoute("lyricist", fromDock = true),
        label = stringResource(R.string.category_lyricist),
        icon = MiuixIcons.Regular.ContactsCircle
    ),
    SettingsManager.BOTTOM_DOCK_ITEM_ANALYTICS to BottomDockTab(
        route = Screen.Analytics.route,
        label = stringResource(R.string.analytics_title),
        icon = MiuixIcons.Regular.Music
    )
)
