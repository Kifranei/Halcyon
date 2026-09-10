package com.ella.music.data

import java.io.File

object LibraryNormalizer {
    fun cleanedTagText(value: String?): String {
        val text = value?.trim().orEmpty()
        if (text.isBlank() || isSystemUnknownPlaceholder(text) || text.looksLikeBrokenEncoding()) return ""
        return text
    }

    fun cleanedArtistText(value: String?): String {
        val text = cleanedTagText(value)
        if (isGeneratedUnknownArtistPlaceholder(text)) return ""
        return text
    }

    fun cleanedAlbumText(value: String?): String {
        val text = cleanedTagText(value)
        if (isGeneratedUnknownAlbumPlaceholder(text)) return ""
        return text
    }

    fun isUsableTagText(value: String?): Boolean =
        cleanedTagText(value).isNotBlank()

    fun isUsableArtistText(value: String?): Boolean =
        cleanedArtistText(value).isNotBlank()

    fun isUsableAlbumText(value: String?): Boolean =
        cleanedAlbumText(value).isNotBlank()

    fun isMissingTag(value: String?, fileName: String? = null): Boolean {
        val text = cleanedTagText(value)
        if (text.isBlank()) return true
        return fileName != null && text == fileName.substringBeforeLast('.')
    }

    fun isMissingArtistTag(value: String?): Boolean =
        cleanedArtistText(value).isBlank()

    fun isMissingAlbumTag(value: String?, fileName: String? = null): Boolean {
        val text = cleanedAlbumText(value)
        if (text.isBlank()) return true
        return fileName != null && text == fileName.substringBeforeLast('.')
    }

    fun isSystemUnknownPlaceholder(value: String?): Boolean =
        value?.trim()?.equals("<unknown>", ignoreCase = true) == true

    fun isGeneratedUnknownArtistPlaceholder(value: String?): Boolean {
        val normalized = value.normalizedUnknownPlaceholder()
        return normalized in setOf(
            "unknownartist",
            "unknownartists",
            "unknownsinger",
            "unknownperformer",
            "未知歌手",
            "未知艺术家",
            "未知艺人"
        )
    }

    fun isGeneratedUnknownAlbumPlaceholder(value: String?): Boolean {
        val normalized = value.normalizedUnknownPlaceholder()
        return normalized in setOf(
            "unknownalbum",
            "unknownalbums",
            "未知专辑",
            "未知唱片",
            "未知作品集"
        )
    }

    fun looksLikeLastFolderName(value: String, path: String): Boolean {
        val folderName = parentFolderName(path)
        return folderName.isNotBlank() && value.trim().equals(folderName, ignoreCase = true)
    }

    private fun String.looksLikeBrokenEncoding(): Boolean =
        '\uFFFD' in this || "锟斤拷" in this || Regex("""(?:锟|斤|拷){3,}""").containsMatchIn(this)

    private fun String?.normalizedUnknownPlaceholder(): String =
        cleanedTagText(this)
            .lowercase()
            .replace(Regex("""[\s_\-:：/\\|,.，。;；()\[\]{}<>《》「」『』]+"""), "")

    fun parentFolderName(path: String): String =
        runCatching {
            if (path.isHttpAudioSource()) {
                java.net.URI(path).path.orEmpty().trim('/').substringBeforeLast('/', "")
                    .substringAfterLast('/')
            } else {
                File(path).parentFile?.name.orEmpty()
            }
        }
            .getOrDefault("")
            .trim()

    /**
     * Album text for grouping/display when [folderNameAsAlbumWhenMissing] is disabled:
     * parent-folder lookalikes are treated as missing (#658).
     */
    fun effectiveAlbumForLibrary(
        album: String?,
        path: String,
        folderNameAsAlbumWhenMissing: Boolean
    ): String {
        val cleaned = cleanedAlbumText(album)
        if (cleaned.isBlank()) return ""
        if (!folderNameAsAlbumWhenMissing && looksLikeLastFolderName(cleaned, path)) return ""
        return cleaned
    }

}
