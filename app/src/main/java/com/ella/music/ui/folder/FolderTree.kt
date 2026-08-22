package com.ella.music.ui.folder

import android.content.Context
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId

internal data class FolderTreeEntry(
    val path: String,
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val duration: Long,
    val dateModified: Long = 0L
)

internal fun Song.folderPath(): String {
    val normalized = path.replace('\\', '/')
    val lastSlash = normalized.lastIndexOf('/')
    return if (lastSlash > 0) normalized.substring(0, lastSlash).normalizeFolderPath() else "/"
}

internal fun List<Song>.commonFolderRoot(): String {
    if (isEmpty()) return "/"
    var firstFolder: String? = null
    var allSameFolder = true
    var commonSegments: List<String>? = null
    forEach { song ->
        val folder = song.folderPath()
        val first = firstFolder
        if (first == null) {
            firstFolder = folder
            commonSegments = folder.pathSegments()
        } else {
            if (!folder.equals(first, ignoreCase = true)) allSameFolder = false
            val currentCommon = commonSegments.orEmpty()
            val next = folder.pathSegments()
            commonSegments = currentCommon.zip(next)
                .takeWhile { (left, right) -> left.equals(right, ignoreCase = true) }
                .map { it.first }
        }
    }
    if (allSameFolder) return firstFolder.orEmpty().parentFolderPath()
    return commonSegments.orEmpty().toFolderPath().ifBlank { "/" }
}

internal fun List<Song>.childFoldersOf(context: Context, parentPath: String): List<FolderTreeEntry> {
    val normalizedParent = parentPath.normalizeFolderPath()
    val rootName = context.getString(R.string.folder_root)
    val folders = LinkedHashMap<String, FolderAccumulator>()
    forEach { song ->
        val childPath = song.folderPath().immediateChildOf(normalizedParent) ?: return@forEach
        folders.getOrPut(childPath) { FolderAccumulator(childPath) }.add(song)
    }
    return folders.values.map { it.toEntry(rootName) }
}

private class FolderAccumulator(
    private val path: String
) {
    private var songCount = 0
    private var duration = 0L
    private var dateModified = 0L
    private val albumIds = HashSet<Long>()

    fun add(song: Song) {
        songCount++
        duration += song.duration
        if (song.dateModified > dateModified) dateModified = song.dateModified
        albumIds += song.albumIdentityId()
    }

    fun toEntry(rootName: String): FolderTreeEntry =
        FolderTreeEntry(
            path = path,
            name = path.folderDisplayName(rootName),
            songCount = songCount,
            albumCount = albumIds.size,
            duration = duration,
            dateModified = dateModified
        )
}

internal fun List<Song>.directSongsInFolder(folderPath: String): List<Song> {
    val normalizedFolder = folderPath.normalizeFolderPath()
    return filter { it.folderPath().equals(normalizedFolder, ignoreCase = true) }
}

internal fun List<Song>.recursiveSongsInFolder(folderPath: String): List<Song> {
    val normalizedFolder = folderPath.normalizeFolderPath()
    return filter { song ->
        val songFolder = song.folderPath()
        songFolder.equals(normalizedFolder, ignoreCase = true) ||
            songFolder.startsWith("${normalizedFolder.trimEnd('/')}/", ignoreCase = true)
    }
}

internal fun String.normalizeFolderPath(): String {
    val normalized = replace('\\', '/').trim().trimEnd('/')
    return normalized.ifBlank { "/" }
}

internal fun String.folderDisplayName(rootName: String): String {
    val normalized = normalizeFolderPath()
    if (normalized == "/") return rootName
    return normalized.substringAfterLast('/').ifBlank { rootName }
}

internal fun String.parentFolderPath(): String {
    val normalized = normalizeFolderPath()
    if (normalized == "/") return "/"
    val parent = normalized.substringBeforeLast('/', missingDelimiterValue = "")
    return parent.ifBlank { "/" }
}

internal data class FolderBreadcrumb(
    val label: String,
    val path: String
)

internal fun String.isSameOrAncestorOfFolder(childPath: String): Boolean {
    val parent = normalizeFolderPath()
    val child = childPath.normalizeFolderPath()
    if (parent.equals(child, ignoreCase = true)) return true
    if (parent == "/") return child.startsWith("/") && child != "/"
    val prefix = "${parent.trimEnd('/')}/"
    return child.startsWith(prefix, ignoreCase = true)
}

internal fun folderBreadcrumbDisplayPath(currentPath: String, deepestPath: String): String {
    val current = currentPath.normalizeFolderPath()
    val deepest = deepestPath.normalizeFolderPath()
    return when {
        deepest.isBlank() || deepest == "/" && current == "/" -> current
        current.isSameOrAncestorOfFolder(deepest) -> deepest
        else -> current
    }
}

internal object FolderHierarchyTrail {
    var deepestPath: String = ""
        private set

    fun visit(currentPath: String): String {
        val display = folderBreadcrumbDisplayPath(currentPath, deepestPath)
        deepestPath = display
        return display
    }

    fun reset() {
        deepestPath = ""
    }
}

internal fun String.folderBreadcrumbs(rootName: String): List<FolderBreadcrumb> {
    val normalized = normalizeFolderPath()
    if (normalized == "/" || normalized.isBlank()) {
        return listOf(FolderBreadcrumb(rootName, "/"))
    }
    val crumbs = mutableListOf<FolderBreadcrumb>()
    var accumulated = ""
    pathSegments().forEach { segment ->
        accumulated = if (accumulated.isEmpty()) "/$segment" else "$accumulated/$segment"
        crumbs += FolderBreadcrumb(label = segment, path = accumulated)
    }
    return crumbs.ifEmpty { listOf(FolderBreadcrumb(rootName, "/")) }
}

private fun String.immediateChildOf(parentPath: String): String? {
    val folder = normalizeFolderPath()
    val parent = parentPath.normalizeFolderPath()
    if (folder.equals(parent, ignoreCase = true)) return null

    val remainder = if (parent == "/") {
        folder.trimStart('/')
    } else {
        val prefix = "${parent.trimEnd('/')}/"
        if (!folder.startsWith(prefix, ignoreCase = true)) return null
        folder.substring(prefix.length)
    }
    val childName = remainder.substringBefore('/').takeIf { it.isNotBlank() } ?: return null
    return if (parent == "/") "/$childName" else "${parent.trimEnd('/')}/$childName"
}

private fun List<String>.toFolderPath(): String =
    if (isEmpty()) "/" else joinToString(prefix = "/", separator = "/")

internal fun String.pathSegments(): List<String> =
    trim('/').split('/').filter(String::isNotBlank)
