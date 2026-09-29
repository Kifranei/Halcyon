package com.ella.music.data.lyrics

import com.ella.music.data.model.LyricLine
import com.ella.music.data.parser.LrcParser
import com.ella.music.plugin.source.PluginLyricsRenderFormat
import com.ella.music.plugin.source.PluginLyricsRenderOptions
import com.ella.music.plugin.source.PluginLyricsResult
import com.ella.music.plugin.source.exportLyricLines
import com.ella.music.plugin.source.renderLyricsText
import com.ella.music.plugin.source.toEmbeddedLyricsText

/** External lyric file formats the online-lyrics match sheet can write next to a song. */
enum class LyricsSidecarFormat(val extension: String) {
    LRC("lrc"),
    TTML("ttml")
}

/**
 * Pure conversions that produce the text of an external (sidecar) lyric file. Output is always
 * something the app's own [LrcParser] reads back: LRC translations/romanizations are emitted as
 * extra lines sharing the original line's timestamp, TTML companions as `ttm:role` spans.
 */
object LyricsSidecarConverter {

    fun looksLikeTtml(text: String): Boolean =
        text.contains("<tt", ignoreCase = true) && text.contains("</tt", ignoreCase = true)

    /** Converts arbitrary lyric text (LRC variants or TTML) to [target]; same-kind input is kept as is. */
    fun convert(
        text: String,
        target: LyricsSidecarFormat,
        options: PluginLyricsRenderOptions = PluginLyricsRenderOptions()
    ): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        val sourceIsTtml = looksLikeTtml(trimmed)
        return when (target) {
            LyricsSidecarFormat.TTML ->
                if (sourceIsTtml) trimmed else linesToTtml(LrcParser.parse(trimmed).lyrics, options)
            LyricsSidecarFormat.LRC ->
                if (!sourceIsTtml) trimmed else linesToLrc(LrcParser.parse(trimmed).lyrics, options)
        }
    }

    /** Word-timed lines become enhanced LRC (`[mm:ss.xx]<mm:ss.xx>word…`), others line LRC. */
    fun linesToLrc(
        lines: List<LyricLine>,
        options: PluginLyricsRenderOptions = PluginLyricsRenderOptions()
    ): String {
        val usable = lines.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return ""
        val format = if (usable.any { it.words.size > 1 }) {
            PluginLyricsRenderFormat.ENHANCED_LRC
        } else {
            PluginLyricsRenderFormat.PLAIN_LRC
        }
        return usable.renderLyricsText(format, options)
    }

    fun linesToTtml(
        lines: List<LyricLine>,
        options: PluginLyricsRenderOptions = PluginLyricsRenderOptions()
    ): String {
        val sorted = lines.filter { it.text.isNotBlank() }.sortedBy { it.timeMs }
        if (sorted.isEmpty()) return ""
        // Line-timed LRC has no end times; end each line where the next begins instead of the
        // renderer's fixed 2 s fallback so the TTML paragraphs cover the whole song.
        val withEnds = sorted.mapIndexed { index, line ->
            if (line.endMs != null || line.words.isNotEmpty()) return@mapIndexed line
            val nextStart = sorted.getOrNull(index + 1)?.timeMs?.takeIf { it > line.timeMs }
            if (nextStart != null) line.copy(endMs = nextStart) else line
        }
        return withEnds.renderLyricsText(PluginLyricsRenderFormat.TTML, options)
    }

    /**
     * Sidecar text for the online-lyrics match sheet.
     *
     * [previewText] is what the sheet currently shows (possibly hand-edited). When it already has
     * the target's kind it is written verbatim. Otherwise an unedited [result] is re-rendered in
     * the target format (a raw TTML source is written as is); edited text is converted.
     */
    fun fromMatchedLyrics(
        target: LyricsSidecarFormat,
        previewText: String,
        result: PluginLyricsResult?,
        options: PluginLyricsRenderOptions = PluginLyricsRenderOptions(),
        previewEdited: Boolean = false
    ): String {
        val preview = previewText.trim()
        if (preview.isNotEmpty()) {
            val previewIsTtml = looksLikeTtml(preview)
            if (target == LyricsSidecarFormat.TTML && previewIsTtml) return preview
            if (target == LyricsSidecarFormat.LRC && !previewIsTtml) return preview
        }
        if (!previewEdited && result != null) {
            val rendered = when (target) {
                LyricsSidecarFormat.TTML -> result.rawTtml.trim()
                    .takeIf {
                        it.isNotEmpty() && looksLikeTtml(it) &&
                            options.includeTranslation && options.includeRomanization
                    }
                    ?: linesToTtml(result.exportLyricLines(), options)
                LyricsSidecarFormat.LRC -> {
                    val wordTimed = result.exportLyricLines().any { it.words.size > 1 }
                    result.toEmbeddedLyricsText(
                        options.copy(
                            format = if (wordTimed) {
                                PluginLyricsRenderFormat.ENHANCED_LRC
                            } else {
                                PluginLyricsRenderFormat.PLAIN_LRC
                            }
                        )
                    ).takeUnless { looksLikeTtml(it) }.orEmpty()
                }
            }
            if (rendered.isNotBlank()) return rendered.trim()
        }
        return convert(preview, target, options)
    }
}

/** Pure document-id / file-name helpers for sidecar files (no Android framework calls). */
object LyricsSidecarPaths {

    /** `song.flac` -> `song.lrc`; a name without extension just gets one appended. */
    fun sidecarFileName(audioFileName: String, format: LyricsSidecarFormat): String? {
        val name = audioFileName.substringAfterLast('/').trim()
        if (name.isEmpty()) return null
        val base = name.substringBeforeLast('.', name).ifEmpty { name }
        return "$base.${format.extension}"
    }

    /**
     * Maps a filesystem path to an ExternalStorageProvider document id:
     * `/storage/emulated/0/Music/A` -> `primary:Music/A`, `/storage/1234-ABCD/Music` -> `1234-ABCD:Music`.
     */
    fun storagePathToDocumentId(path: String): String? {
        var normalized = path.replace('\\', '/').trimEnd('/')
        if (normalized.equals("/sdcard", ignoreCase = true) || normalized.startsWith("/sdcard/", ignoreCase = true)) {
            normalized = "/storage/emulated/0" + normalized.substring("/sdcard".length)
        }
        val primaryPrefix = "/storage/emulated/0"
        if (normalized.equals(primaryPrefix, ignoreCase = true) ||
            normalized.startsWith("$primaryPrefix/", ignoreCase = true)
        ) {
            val relative = normalized.substring(primaryPrefix.length).trimStart('/')
            return "primary:$relative"
        }
        val match = Regex("""^/storage/([^/]+)(?:/(.*))?$""").matchEntire(normalized) ?: return null
        val volume = match.groupValues[1]
        if (volume.equals("emulated", ignoreCase = true) || volume.equals("self", ignoreCase = true)) return null
        return "$volume:${match.groupValues[2].trim('/')}"
    }

    /** `vol:Music/a.flac` -> `vol:Music`; `vol:a.flac` -> `vol:`. */
    fun parentDocumentId(documentId: String): String? {
        val colon = documentId.indexOf(':')
        if (colon < 0) return null
        val relative = documentId.substring(colon + 1).trim('/')
        if (relative.isEmpty()) return null
        val volume = documentId.substring(0, colon + 1)
        val parentRelative = relative.substringBeforeLast('/', "")
        return volume + parentRelative
    }

    fun childDocumentId(parentDocumentId: String, childName: String): String =
        if (parentDocumentId.endsWith(':')) parentDocumentId + childName else "$parentDocumentId/$childName"

    /** Whether a granted tree (by its tree document id) contains [documentId]. */
    fun treeCovers(treeDocumentId: String, documentId: String): Boolean {
        val tree = treeDocumentId.trimEnd('/')
        if (documentId.equals(tree, ignoreCase = true)) return true
        return if (tree.endsWith(':')) {
            documentId.startsWith(tree, ignoreCase = true)
        } else {
            documentId.startsWith("$tree/", ignoreCase = true)
        }
    }
}
