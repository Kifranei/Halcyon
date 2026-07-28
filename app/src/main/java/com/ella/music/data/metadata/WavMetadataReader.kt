package com.ella.music.data.metadata

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

data class WavMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val composer: String? = null,
    val arranger: String? = null,
    val lyricist: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val bitRate: Int = 0,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 0,
    val durationMs: Long = 0L
) {
    val hasTags: Boolean
        get() = listOf(title, artist, album, albumArtist, genre, year, composer, arranger, lyricist)
            .any { !it.isNullOrBlank() } || trackNumber != null || discNumber != null

    val hasQuality: Boolean
        get() = bitRate > 0 || sampleRate > 0 || bitDepth > 0 || channels > 0
}

object WavMetadataReader {
    private const val TAG = "WavMetadataReader"
    private const val MAX_ID3_CHUNK_BYTES = 4 * 1024 * 1024L

    fun read(path: String): WavMetadata? = read(File(path))

    fun read(file: File): WavMetadata? {
        val extension = file.extension.lowercase()
        if (extension !in setOf("wav", "wave")) return null
        if (!file.exists() || !file.isFile) return null

        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                if (input.length() < 12L) return@use null
                val riff = input.readFourCc()
                input.readUnsignedIntLe()
                val wave = input.readFourCc()
                if (riff !in setOf("RIFF", "RF64") || wave != "WAVE") return@use null

                val infoValues = linkedMapOf<String, String>()
                val id3Values = linkedMapOf<String, String>()
                var wavFormat = WavFormat()
                var dataBytes = 0L
                val fileLength = input.length()

                while (input.filePointer + 8L <= fileLength) {
                    val chunkId = input.readFourCc()
                    val chunkSize = input.readUnsignedIntLe()
                    val chunkStart = input.filePointer
                    if (chunkStart > fileLength) break
                    val chunkEnd = (chunkStart + chunkSize).coerceAtMost(fileLength)
                    if (chunkEnd <= chunkStart) break

                    when {
                        chunkId == "fmt " -> wavFormat = input.readWavFormat(chunkEnd)
                        chunkId == "data" -> dataBytes = chunkEnd - chunkStart
                        chunkId == "LIST" && chunkSize >= 4L -> input.readListChunk(chunkEnd, infoValues)
                        chunkId.equals("id3 ", ignoreCase = true) || chunkId == "ID3 " -> {
                            val length = (chunkEnd - chunkStart).coerceAtMost(MAX_ID3_CHUNK_BYTES).toInt()
                            val bytes = ByteArray(length)
                            input.readFully(bytes)
                            parseId3Chunk(bytes, id3Values)
                        }
                    }

                    input.seek((chunkEnd + (chunkSize and 1L)).coerceAtMost(fileLength))
                }

                val metadata = WavMetadata(
                    title = id3Values.firstId3Value("TIT2", "TITLE")
                        ?: infoValues.firstInfoValue("INAM", "TITL", "NAME", "TIT2"),
                    artist = id3Values.firstId3Value("TPE1", "ARTIST")
                        ?: infoValues.firstInfoValue("IART", "ART", "AUTH", "TPE1"),
                    album = id3Values.firstId3Value("TALB", "ALBUM")
                        ?: infoValues.firstInfoValue("IPRD", "IALB", "ALBM", "TALB"),
                    albumArtist = id3Values.firstId3Value("TPE2", "ALBUMARTIST", "ALBUM ARTIST")
                        ?: infoValues.firstInfoValue("TPE2", "AART", "ALBA"),
                    genre = id3Values.firstId3Value("TCON", "GENRE")
                        ?: infoValues.firstInfoValue("IGNR", "GENR", "TCON"),
                    year = (id3Values.firstId3Value("TDRC", "TYER", "DATE", "YEAR")
                        ?: infoValues.firstInfoValue("ICRD", "YEAR", "DATE", "IDIT"))?.normalizeYear(),
                    composer = id3Values.firstId3Value("TCOM", "COMPOSER")
                        ?: infoValues.firstInfoValue("IMUS", "TCOM", "COMP"),
                    arranger = id3Values.firstId3Value("TPE4", "ARRANGER", "ARRANGEDBY", "ARRANGED BY")
                        ?: infoValues.firstInfoValue("IARR", "ARRANGER", "ARRANG", "TPE4"),
                    lyricist = id3Values.firstId3Value("TEXT", "LYRICIST", "WRITER")
                        ?: infoValues.firstInfoValue("IWRI", "TEXT", "WRIT"),
                    trackNumber = id3Values.firstId3Value("TRCK", "TRACKNUMBER", "TRACK")
                        ?.normalizedNumberFromTag()
                        ?: infoValues.firstInfoValue("IPRT", "ITRK", "TRCK")?.normalizedNumberFromTag(),
                    discNumber = id3Values.firstId3Value("TPOS", "DISCNUMBER", "DISC")
                        ?.normalizedNumberFromTag()
                        ?: infoValues.firstInfoValue("TPOS", "DISC")?.normalizedNumberFromTag(),
                    bitRate = wavFormat.bitRate,
                    sampleRate = wavFormat.sampleRate,
                    bitDepth = wavFormat.bitDepth,
                    channels = wavFormat.channels
                )
                metadata.copy(
                    bitRate = metadata.bitRate.takeIf { it > 0 } ?: metadata.estimatedPcmBitRate(),
                    durationMs = if (wavFormat.byteRate > 0) dataBytes * 1_000L / wavFormat.byteRate else 0L
                )
                    .takeIf { it.hasTags || it.hasQuality }
            }
        }.onFailure {
            Log.d(TAG, "WAV metadata extraction failed for ${file.path}", it)
        }.getOrNull()
    }

    private fun RandomAccessFile.readListChunk(chunkEnd: Long, values: MutableMap<String, String>) {
        val listType = readFourCc()
        if (listType != "INFO") return
        while (filePointer + 8L <= chunkEnd) {
            val key = readFourCc()
            val valueSize = readUnsignedIntLe()
            val valueEnd = (filePointer + valueSize).coerceAtMost(chunkEnd)
            val valueLength = (valueEnd - filePointer).toInt().coerceAtLeast(0)
            val bytes = ByteArray(valueLength)
            readFully(bytes)
            bytes.decodeInfoText().takeIf { it.isNotBlank() }?.let { values[key.uppercase()] = it }
            val paddedEnd = valueEnd + (valueSize and 1L)
            seek(paddedEnd.coerceAtMost(chunkEnd))
        }
    }

    private fun RandomAccessFile.readWavFormat(chunkEnd: Long): WavFormat {
        if (chunkEnd - filePointer < 16L) return WavFormat()
        readUnsignedShortLe()
        val channels = readUnsignedShortLe()
        val sampleRate = readUnsignedIntLe().coerceToInt()
        val byteRate = readUnsignedIntLe().coerceToInt()
        readUnsignedShortLe()
        val bitDepth = readUnsignedShortLe()
        return WavFormat(
            bitRate = (byteRate.toLong() * 8L).coerceToInt().takeIf { it > 0 }
                ?: (sampleRate.toLong() * channels.toLong() * bitDepth.toLong()).coerceToInt(),
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            byteRate = byteRate
        )
    }

    private fun parseId3Chunk(bytes: ByteArray, values: MutableMap<String, String>) {
        if (bytes.size < 10 || bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) return
        val major = bytes[3].toInt() and 0xFF
        val flags = bytes[5].toInt() and 0xFF
        val tagSize = bytes.synchsafeIntAt(6)
        if (tagSize <= 0) return
        var offset = 10
        val end = (10 + tagSize).coerceAtMost(bytes.size)

        if ((flags and 0x40) != 0 && offset + 4 <= end) {
            val extendedSize = if (major >= 4) bytes.synchsafeIntAt(offset) else bytes.int32At(offset)
            offset += if (major >= 4) extendedSize else extendedSize + 4
        }

        while (offset + 10 <= end) {
            val frameId = String(bytes, offset, 4, StandardCharsets.ISO_8859_1)
            if (frameId.all { it == ' ' }) break
            val frameSize = if (major >= 4) bytes.synchsafeIntAt(offset + 4) else bytes.int32At(offset + 4)
            val dataStart = offset + 10
            val dataEnd = (dataStart + frameSize).coerceAtMost(end)
            if (frameSize <= 0 || dataEnd <= dataStart) break
            val data = bytes.copyOfRange(dataStart, dataEnd)

            when {
                frameId.startsWith("T") -> {
                    decodeTextFrame(data).takeIf { it.isNotBlank() }?.let { values.putIfAbsent(frameId, it) }
                }
                frameId == "COMM" -> {
                    decodeCommentFrame(data).takeIf { it.isNotBlank() }?.let { values.putIfAbsent("COMMENT", it) }
                }
            }
            offset = dataEnd
        }
    }

    private fun decodeTextFrame(data: ByteArray): String {
        if (data.isEmpty()) return ""
        return data.copyOfRange(1, data.size)
            .decodeId3Text(data[0].toInt() and 0xFF)
            .replace(' ', ';')
            .replace(Regex("""\s*;\s*"""), "; ")
            .cleanTagText()
    }

    private fun decodeCommentFrame(data: ByteArray): String {
        if (data.size <= 4) return ""
        val encoding = data[0].toInt() and 0xFF
        val text = data.copyOfRange(4, data.size).decodeId3Text(encoding)
        val parts = text.split(' ').map { it.cleanTagText() }.filter { it.isNotBlank() }
        return parts.lastOrNull().orEmpty()
    }

    private fun RandomAccessFile.readFourCc(): String {
        val bytes = ByteArray(4)
        readFully(bytes)
        return String(bytes, StandardCharsets.US_ASCII)
    }

    private fun RandomAccessFile.readUnsignedShortLe(): Int {
        val b0 = read()
        val b1 = read()
        if (b0 < 0 || b1 < 0) return 0
        return (b0 and 0xFF) or ((b1 and 0xFF) shl 8)
    }

    private fun RandomAccessFile.readUnsignedIntLe(): Long {
        val b0 = read()
        val b1 = read()
        val b2 = read()
        val b3 = read()
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) return 0L
        return (b0.toLong() and 0xFF) or
            ((b1.toLong() and 0xFF) shl 8) or
            ((b2.toLong() and 0xFF) shl 16) or
            ((b3.toLong() and 0xFF) shl 24)
    }

    private fun ByteArray.decodeInfoText(): String {
        val trimmed = dropLastWhile { it == 0.toByte() || it == 0x20.toByte() }.toByteArray()
        if (trimmed.isEmpty()) return ""
        val text = when {
            trimmed.size >= 2 && trimmed[0] == 0xFF.toByte() && trimmed[1] == 0xFE.toByte() ->
                String(trimmed, StandardCharsets.UTF_16LE)
            trimmed.size >= 2 && trimmed[0] == 0xFE.toByte() && trimmed[1] == 0xFF.toByte() ->
                String(trimmed, StandardCharsets.UTF_16BE)
            trimmed.size >= 4 && trimmed.count { it == 0.toByte() } > trimmed.size / 4 ->
                String(trimmed, StandardCharsets.UTF_16LE)
            else -> {
                val utf8 = String(trimmed, StandardCharsets.UTF_8)
                if ('�' in utf8) String(trimmed, Charset.forName("GB18030")) else utf8
            }
        }
        return text.cleanTagText()
    }

    private fun ByteArray.decodeId3Text(encoding: Int): String {
        if (isEmpty()) return ""
        val text = when (encoding) {
            1 -> String(this, StandardCharsets.UTF_16)
            2 -> String(this, StandardCharsets.UTF_16BE)
            3 -> String(this, StandardCharsets.UTF_8)
            else -> decodeInfoText()
        }
        return text.cleanTagText()
    }

    private fun ByteArray.synchsafeIntAt(offset: Int): Int {
        if (offset + 3 >= size) return 0
        return ((this[offset].toInt() and 0x7F) shl 21) or
            ((this[offset + 1].toInt() and 0x7F) shl 14) or
            ((this[offset + 2].toInt() and 0x7F) shl 7) or
            (this[offset + 3].toInt() and 0x7F)
    }

    private fun ByteArray.int32At(offset: Int): Int {
        if (offset + 3 >= size) return 0
        return ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
    }

    private fun Map<String, String>.firstInfoValue(vararg keys: String): String? {
        for (key in keys) {
            get(key.uppercase())?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val normalizedKeys = keys.map { it.normalizedPropertyKey() }.toSet()
        for ((key, value) in this) {
            if (key.normalizedPropertyKey() in normalizedKeys && value.isNotBlank()) return value
        }
        return null
    }

    private fun Map<String, String>.firstId3Value(vararg keys: String): String? {
        for (key in keys) {
            get(key.uppercase())?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun String.normalizedPropertyKey(): String =
        uppercase().replace(" ", "").replace("_", "")

    private fun String.normalizeYear(): String =
        Regex("""\d{4}""").find(this)?.value ?: cleanTagText()

    private fun String.normalizedNumberFromTag(): Int? =
        substringBefore('/').trim().toIntOrNull()

    private fun String.cleanTagText(): String =
        trim('\uFEFF', '\u0000', ' ', '\t', '\r', '\n')
            .replace(Regex("""\s+"""), " ")

    private fun WavMetadata.estimatedPcmBitRate(): Int =
        (sampleRate.toLong() * channels.toLong() * bitDepth.toLong())
            .takeIf { sampleRate > 0 && channels > 0 && bitDepth > 0 }
            ?.coerceToInt()
            ?: 0

    private fun Long.coerceToInt(): Int =
        coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private data class WavFormat(
        val bitRate: Int = 0,
        val sampleRate: Int = 0,
        val bitDepth: Int = 0,
        val channels: Int = 0,
        val byteRate: Int = 0
    )
}
