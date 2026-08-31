package com.ella.music.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.Locale

class ArtistCoverRepository private constructor(
    private val context: Context
) {
    @Volatile
    private var cachedFolderLocation: String? = null
    @Volatile
    private var cachedIgnoreCase: Boolean? = null
    @Volatile
    private var cachedIndex: Map<String, List<ArtistCoverAsset>> = emptyMap()
    private val indexLock = Any()

    fun getArtistCoverUri(
        artistName: String,
        folderLocation: String
    ): Uri? {
        return getArtistCoverAssets(artistName, folderLocation)
            .firstOrNull { it.kind == ArtistCoverKind.Image }
            ?.uri
    }

    fun getArtistCoverAsset(
        artistName: String,
        folderLocation: String
    ): ArtistCoverAsset? {
        return getArtistCoverAssets(artistName, folderLocation).preferredCover()
    }

    /**
     * 返回某位艺术家的全部封面（有序）：既包含单张 "<Artist>.<ext>"，也包含
     * 编号形式 "<Artist>_01/_02/…" 的多张图，以及 mp4 等动态封面。
     */
    fun getArtistCoverAssets(
        artistName: String,
        folderLocation: String
    ): List<ArtistCoverAsset> {
        val ignoreCase = NameSplitConfigStore.tagIgnoreCase
        val artistKey = normalizeArtistCoverKey(artistName, ignoreCase)
        val safeFolderLocation = folderLocation.trim()
        if (artistKey.isBlank() || safeFolderLocation.isBlank()) return emptyList()
        return ensureIndex(safeFolderLocation, ignoreCase)[artistKey].orEmpty()
    }

    private fun ensureIndex(
        folderLocation: String,
        ignoreCase: Boolean
    ): Map<String, List<ArtistCoverAsset>> {
        cachedFolderLocation
            ?.takeIf { it == folderLocation && cachedIgnoreCase == ignoreCase }
            ?.let { return cachedIndex }

        synchronized(indexLock) {
            cachedFolderLocation
                ?.takeIf { it == folderLocation && cachedIgnoreCase == ignoreCase }
                ?.let { return cachedIndex }

            val built = buildIndex(folderLocation, ignoreCase)
            cachedFolderLocation = folderLocation
            cachedIgnoreCase = ignoreCase
            cachedIndex = built
            return built
        }
    }

    private fun buildIndex(
        folderLocation: String,
        ignoreCase: Boolean
    ): Map<String, List<ArtistCoverAsset>> {
        return when {
            folderLocation.startsWith("content://", ignoreCase = true) -> {
                val root = DocumentFile.fromTreeUri(context, Uri.parse(folderLocation)) ?: return emptyMap()
                buildTreeIndex(root, ignoreCase)
            }

            else -> buildLocalFolderIndex(File(folderLocation), ignoreCase)
        }
    }

    private fun buildTreeIndex(
        root: DocumentFile,
        ignoreCase: Boolean
    ): Map<String, List<ArtistCoverAsset>> {
        val accumulator = ArtistCoverAccumulator()

        fun visit(node: DocumentFile) {
            val children = runCatching { node.listFiles() }.getOrElse { emptyArray() }
            children.forEach { child ->
                when {
                    child.isDirectory -> visit(child)
                    child.isFile -> {
                        val match = artistCoverMatch(child.name.orEmpty(), child.type, ignoreCase)
                            ?: return@forEach
                        accumulator.add(match, child.uri)
                    }
                }
            }
        }

        visit(root)
        return accumulator.build()
    }

    private fun buildLocalFolderIndex(
        root: File,
        ignoreCase: Boolean
    ): Map<String, List<ArtistCoverAsset>> {
        if (!root.exists() || !root.isDirectory) return emptyMap()
        val accumulator = ArtistCoverAccumulator()
        runCatching {
            root.walkTopDown().forEach { file ->
                if (!file.isFile) return@forEach
                val match = artistCoverMatch(file.name, ignoreCase = ignoreCase) ?: return@forEach
                accumulator.add(match, Uri.fromFile(file))
            }
        }
        return accumulator.build()
    }

    companion object {
        @Volatile
        private var instance: ArtistCoverRepository? = null

        fun getInstance(context: Context): ArtistCoverRepository {
            return instance ?: synchronized(this) {
                instance ?: ArtistCoverRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

data class ArtistCoverAsset(
    val uri: Uri,
    val kind: ArtistCoverKind
)

enum class ArtistCoverKind {
    Image,
    Video
}

internal data class ArtistCoverMatch(
    val key: String,
    val kind: ArtistCoverKind,
    // 编号后缀（"_01" → 1）；无编号为 0，用于排序，让不带编号的封面排在最前。
    val order: Int = 0
)

private val supportedArtistCoverExtensions = setOf(
    "jpg",
    "jpeg",
    "png",
    "webp",
    "bmp",
    "gif",
    "avif",
    "heic",
    "heif"
)

private val supportedArtistCoverVideoExtensions = setOf(
    "mp4",
    "m4v",
    "mov",
    "webm",
    "mkv"
)

// 匹配 "<Artist>_01"、"<Artist>_02" 之类的编号后缀。
private val numberedArtistCoverSuffix = Regex("""_(\d{1,3})$""")

/**
 * 每个艺术家 key 累积一组有序封面。带编号后缀的图按编号排序，未编号者（order=0）排在最前。
 */
private class ArtistCoverAccumulator {
    private val entries = linkedMapOf<String, MutableList<OrderedAsset>>()

    fun add(match: ArtistCoverMatch, uri: Uri) {
        entries.getOrPut(match.key) { mutableListOf() }
            .add(OrderedAsset(match.order, ArtistCoverAsset(uri, match.kind)))
    }

    fun build(): Map<String, List<ArtistCoverAsset>> =
        entries.mapValues { (_, list) ->
            // sortedBy 稳定：同一 order（如重复编号）保持发现顺序。
            list.sortedBy { it.order }.map { it.asset }
        }

    private data class OrderedAsset(val order: Int, val asset: ArtistCoverAsset)
}

private fun List<ArtistCoverAsset>.preferredCover(): ArtistCoverAsset? =
    firstOrNull { it.kind == ArtistCoverKind.Video } ?: firstOrNull()

internal fun artistCoverMatchKey(
    fileName: String,
    mimeType: String? = null,
    ignoreCase: Boolean = true
): String? {
    return artistCoverMatch(fileName, mimeType, ignoreCase)?.key
}

internal fun artistCoverMatch(
    fileName: String,
    mimeType: String? = null,
    ignoreCase: Boolean = true
): ArtistCoverMatch? {
    val trimmedName = fileName.trim()
    if (trimmedName.isBlank()) return null
    val extension = trimmedName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val kind = when {
        mimeType?.startsWith("video/", ignoreCase = true) == true ||
            extension in supportedArtistCoverVideoExtensions -> ArtistCoverKind.Video
        mimeType?.startsWith("image/", ignoreCase = true) == true ||
            extension in supportedArtistCoverExtensions -> ArtistCoverKind.Image
        else -> return null
    }
    val baseName = trimmedName.substringBeforeLast('.', trimmedName)
    val numberedMatch = numberedArtistCoverSuffix.find(baseName)
    val order = numberedMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val coreName = if (numberedMatch != null) {
        baseName.substring(0, numberedMatch.range.first)
    } else {
        baseName
    }
    val key = normalizeArtistCoverKey(coreName, ignoreCase).takeIf { it.isNotBlank() } ?: return null
    return ArtistCoverMatch(key = key, kind = kind, order = order)
}

internal fun normalizeArtistCoverKey(
    value: String,
    ignoreCase: Boolean = true
): String {
    val cleaned = LibraryNormalizer.cleanedArtistText(value)
        .replace(Regex("""\s+"""), " ")
        .trim()
    return if (ignoreCase) cleaned.lowercase(Locale.ROOT) else cleaned
}
