package com.ella.music.data.parser

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object LrcParser {

    data class LrcResult(
        val lyrics: List<com.ella.music.data.model.LyricLine>,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val offset: Long = 0L
    )

    fun parse(lrcContent: String, ignoreHeaderTags: Boolean = false): LrcResult {
        val raw = EllaLyricsParser.parse(lrcContent, ignoreHeaderTags)
        // Post-process: fix x-bg/accompaniment text that appears as a concatenated
        // Latin blob (e.g. "Andthere'salotofcoolchicksoutthere"). This runs for BOTH
        // lyric inputs so display behavior stays consistent.
        val fixedLyrics = raw.lyrics.map(::fixBackgroundLineSpacing)
        return raw.copy(lyrics = fixedLyrics)
    }

    /**
     * Fixes background/accompaniment line text that appears as a concatenated Latin blob.
     *
     * If the line has [backgroundWords] with proper per-word text (multiple words), rebuild
     * the display text by joining the words' text with spaces. This handles the case where
     * TTML x-bg spans are adjacent without inter-span whitespace, causing the display text
     * to be a blob like "Andthere'salotofcoolchicksoutthere" even though the individual
     * words were parsed correctly.
     */
    private fun fixBackgroundLineSpacing(line: com.ella.music.data.model.LyricLine): com.ella.music.data.model.LyricLine {
        val bgText = line.backgroundText ?: return line
        if (bgText.isBlank()) return line
        // CJK text doesn't use inter-word spaces — skip
        if (bgText.any { isCjk(it) }) return line
        // Already well-spaced (at least 1 space per ~10 letters)
        val spaceCount = bgText.count { it == ' ' }
        val letterCount = bgText.count { it.isLetter() }
        if (letterCount < 8 || (spaceCount > 0 && letterCount.toFloat() / spaceCount < 9f)) return line

        // If we have multiple words with proper per-word text, rebuild the display text
        val bgWords = line.backgroundWords
        if (bgWords.isNotEmpty()) {
            val wordTexts = bgWords.map { it.text.trim() }.filter { it.isNotBlank() }
            if (wordTexts.size > 1) {
                val rebuilt = wordTexts.joinToString(" ")
                if (rebuilt != bgText.trim()) {
                    return line.copy(backgroundText = rebuilt)
                }
            }
        }

        // No usable words to rebuild from — leave as-is. We don't attempt dictionary-free
        // text splitting because it produces unreliable results (e.g. "Andthere'salot..."
        // can't be reliably split into "And there's a lot..." without a word dictionary).
        return line
    }

    private fun isCjk(ch: Char): Boolean {
        val block = Character.UnicodeBlock.of(ch)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES
    }

    private val lyricExtensions = listOf("lrc", "ttml", "elrc")

    fun findLrcFile(songPath: String): String? {
        val baseName = songPath.substringBeforeLast('.')
        for (ext in lyricExtensions) {
            readViaFd("$baseName.$ext")?.let { return it }
        }

        val parentDir = File(songPath).parentFile ?: return null
        val songName = File(songPath).nameWithoutExtension
        return try {
            parentDir.listFiles()
                ?.filter { file -> file.extension.lowercase() in lyricExtensions }
                ?.sortedWith(
                    compareBy<File> { lyricExtensions.indexOf(it.extension.lowercase()) }
                        .thenBy { it.name }
                )
                ?.firstNotNullOfOrNull { file ->
                    file.takeIf { it.nameWithoutExtension.contains(songName, ignoreCase = true) }
                        ?.let { readViaFd(it.absolutePath) }
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun readViaFd(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    val bytes = fis.readBytes()
                    if (bytes.isEmpty()) return null
                    readTextWithFallback(bytes)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readTextWithFallback(bytes: ByteArray): String {
        val charsets = listOf("UTF-8", "GB18030", "UTF-16LE", "UTF-16BE")
        for (charsetName in charsets) {
            val charset = Charset.forName(charsetName)
            try {
                val decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                return decoder.decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
            }
        }
        return String(bytes, Charsets.UTF_8)
    }
}
