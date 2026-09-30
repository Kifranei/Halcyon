package com.ella.music.ui.analytics

internal fun recentVideoType(uri: String): String = when {
    uri.startsWith("file:", true) || uri.startsWith("content:", true) || uri.startsWith('/') || ':' !in uri -> "local"
    else -> "online"
}

internal fun recentPlaybackTypeVisible(
    row: RecentPlaybackRow,
    tab: RecentPlaybackTab,
    folderTypes: Set<String>,
    mvTypes: Set<String>
): Boolean = when (tab) {
    RecentPlaybackTab.Folder -> (if (row.nestedFolder) "nested_folder" else "folder") in folderTypes
    RecentPlaybackTab.Mv -> recentVideoType(row.mediaUri) in mvTypes
    else -> true
}
