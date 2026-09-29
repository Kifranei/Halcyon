package com.ella.music.data.metadata

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.ella.music.data.isContentAudioSource
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Pure Kotlin reader for embedded audio artwork.
 *
 * Android's [android.media.MediaMetadataRetriever.getEmbeddedPicture] has a hardcoded ~1MB buffer
 * limit and ignores pictures whose ID3 picture type is not 3 (Cover Front). Native TagLib may also
 * fail when operating on anonymous ParcelFileDescriptors or non-standard tags.
 *
 * [EmbeddedArtworkReader] parses ID3v2 (v2.2, v2.3, v2.4), MP4 covr atoms, and FLAC picture blocks
 * directly in Kotlin, and detects image byte magic (JPEG, PNG, WebP, GIF, BMP) directly, ensuring
 * that covers of any picture type (e.g. 0 Other) or large file size (>1MB) are extracted faithfully.
 */
object EmbeddedArtworkReader {
    private const val TAG = "EmbeddedArtworkReader"
    private const val MAX_TAG_READ_BYTES = 32 * 1024 * 1024L // 32MB max tag read

    fun extractCoverArt(path: String, context: Context? = null): ByteArray? {
        if (path.isBlank()) return null
        return runCatching {
            if (path.isContentAudioSource()) {
                val resolver = context?.contentResolver ?: return null
                resolver.openFileDescriptor(Uri.parse(path), "r")?.use { pfd ->
                    extractCoverArt(pfd)
                }
            } else {
                val file = File(path)
                if (!file.exists() || !file.isFile || file.length() < 32) return null
                extractCoverArt(file)
            }
        }.onFailure {
            Log.d(TAG, "extractCoverArt failed for $path", it)
        }.getOrNull()
    }

    fun extractCoverArt(file: File): ByteArray? {
        if (!file.exists() || !file.isFile || file.length() < 32) return null
        // 1. Direct stream extraction (ID3v2, FLAC, OGG)
        runCatching {
            FileInputStream(file).use { input ->
                extractFromStream(input, file.length())
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        // 2. RandomAccessFile extraction (MP4 / WAV chunk walk)
        runCatching {
            RandomAccessFile(file, "r").use { raf ->
                extractFromRandomAccessFile(raf)
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        // 3. Optional ffmpeg CLI fallback (if available on local host/desktop)
        runCatching {
            extractWithFfmpeg(file.absolutePath)
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        return null
    }

    fun extractCoverArt(pfd: ParcelFileDescriptor): ByteArray? {
        return runCatching {
            FileInputStream(pfd.fileDescriptor).use { input ->
                extractFromStream(input, pfd.statSize)
            }
        }.getOrNull()
    }

    fun extractFromStream(input: InputStream, totalSize: Long = -1L): ByteArray? {
        val magic = ByteArray(4)
        if (input.readFullyOrLess(magic) < 4) return null

        // ID3v2 tag (MP3, AIFF, or prepended to audio files)
        if (magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte()) {
            return extractFromId3v2Header(magic, input)
        }

        // FLAC audio stream
        if (magic[0] == 'f'.code.toByte() && magic[1] == 'L'.code.toByte() &&
            magic[2] == 'a'.code.toByte() && magic[3] == 'C'.code.toByte()
        ) {
            return extractFromFlacBlocks(input)
        }

        // RIFF header (WAV)
        if (magic[0] == 'R'.code.toByte() && magic[1] == 'I'.code.toByte() &&
            magic[2] == 'F'.code.toByte() && magic[3] == 'F'.code.toByte()
        ) {
            return extractFromRiffStream(input)
        }

        return null
    }

    private fun extractFromRandomAccessFile(raf: RandomAccessFile): ByteArray? {
        if (raf.length() < 12) return null
        raf.seek(0)
        val header = ByteArray(12)
        raf.readFully(header)

        // Check MP4 / M4A (ftyp or moov)
        val fourCc = String(header, 4, 4, StandardCharsets.ISO_8859_1)
        if (fourCc == "ftyp" || fourCc == "moov") {
            return extractFromMp4Raf(raf)
        }

        // Check WAV RIFF
        if (String(header, 0, 4, StandardCharsets.ISO_8859_1) == "RIFF" &&
            String(header, 8, 4, StandardCharsets.ISO_8859_1) == "WAVE"
        ) {
            return extractFromWavRaf(raf)
        }

        return null
    }

    private fun extractFromId3v2Header(firstFourBytes: ByteArray, input: InputStream): ByteArray? {
        val restOfHeader = ByteArray(6)
        if (input.readFullyOrLess(restOfHeader) < 6) return null

        val major = firstFourBytes[3].toInt() and 0xFF
        val flags = restOfHeader[1].toInt() and 0xFF
        val tagSize = restOfHeader.synchsafeIntAt(2)
        if (tagSize <= 0 || tagSize > MAX_TAG_READ_BYTES) return null

        val tagData = ByteArray(tagSize)
        val readTotal = input.readFullyOrLess(tagData)
        if (readTotal < 10) return null

        return parseId3v2TagPayload(tagData, readTotal, major, flags)
    }

    internal fun parseId3v2TagPayload(
        bytes: ByteArray,
        length: Int,
        major: Int,
        flags: Int
    ): ByteArray? {
        var offset = 0
        val end = length

        // Extended header
        if ((flags and 0x40) != 0 && offset + 4 <= end) {
            val extSize = if (major >= 4) bytes.synchsafeIntAt(offset) else bytes.int32At(offset)
            offset += if (major >= 4) extSize else extSize + 4
        }

        while (offset + (if (major == 2) 6 else 10) <= end) {
            val frameId: String
            val frameSize: Int
            val headerSize: Int

            if (major == 2) {
                frameId = String(bytes, offset, 3, StandardCharsets.ISO_8859_1)
                frameSize = ((bytes[offset + 3].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 4].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 5].toInt() and 0xFF)
                headerSize = 6
            } else {
                frameId = String(bytes, offset, 4, StandardCharsets.ISO_8859_1)
                frameSize = if (major >= 4) {
                    val sync = bytes.synchsafeIntAt(offset + 4)
                    if (sync <= 0 || offset + 10 + sync > end) bytes.int32At(offset + 4) else sync
                } else {
                    bytes.int32At(offset + 4)
                }
                headerSize = 10
            }

            if (frameId.isBlank() || frameId.all { it == '\u0000' }) break
            val dataStart = offset + headerSize
            val dataEnd = (dataStart + frameSize).coerceAtMost(end)
            if (frameSize <= 0 || dataEnd <= dataStart) break

            if (frameId == "APIC" || frameId == "PIC") {
                val imageBytes = extractImageFromFrameData(bytes, dataStart, dataEnd)
                if (imageBytes != null && imageBytes.isNotEmpty()) {
                    return imageBytes
                }
            }

            offset = dataEnd
        }
        return null
    }

    internal fun extractImageFromFrameData(bytes: ByteArray, dataStart: Int, dataEnd: Int): ByteArray? {
        val scanLimit = minOf(dataEnd, dataStart + 1024)
        for (i in dataStart until scanLimit) {
            // Check JPEG: FF D8 FF
            if (i + 2 < dataEnd &&
                bytes[i] == 0xFF.toByte() &&
                bytes[i + 1] == 0xD8.toByte() &&
                bytes[i + 2] == 0xFF.toByte()
            ) {
                return bytes.copyOfRange(i, dataEnd)
            }
            // Check PNG: 89 50 4E 47 0D 0A 1A 0A
            if (i + 7 < dataEnd &&
                bytes[i] == 0x89.toByte() &&
                bytes[i + 1] == 0x50.toByte() &&
                bytes[i + 2] == 0x4E.toByte() &&
                bytes[i + 3] == 0x47.toByte() &&
                bytes[i + 4] == 0x0D.toByte() &&
                bytes[i + 5] == 0x0A.toByte() &&
                bytes[i + 6] == 0x1A.toByte() &&
                bytes[i + 7] == 0x0A.toByte()
            ) {
                return bytes.copyOfRange(i, dataEnd)
            }
            // Check WEBP: RIFF....WEBP
            if (i + 11 < dataEnd &&
                bytes[i] == 'R'.code.toByte() &&
                bytes[i + 1] == 'I'.code.toByte() &&
                bytes[i + 2] == 'F'.code.toByte() &&
                bytes[i + 3] == 'F'.code.toByte() &&
                bytes[i + 8] == 'W'.code.toByte() &&
                bytes[i + 9] == 'E'.code.toByte() &&
                bytes[i + 10] == 'B'.code.toByte() &&
                bytes[i + 11] == 'P'.code.toByte()
            ) {
                return bytes.copyOfRange(i, dataEnd)
            }
            // Check GIF: GIF87a or GIF89a
            if (i + 5 < dataEnd &&
                bytes[i] == 'G'.code.toByte() &&
                bytes[i + 1] == 'I'.code.toByte() &&
                bytes[i + 2] == 'F'.code.toByte() &&
                bytes[i + 3] == '8'.code.toByte()
            ) {
                return bytes.copyOfRange(i, dataEnd)
            }
            // Check BMP: BM
            if (i + 13 < dataEnd &&
                bytes[i] == 'B'.code.toByte() &&
                bytes[i + 1] == 'M'.code.toByte()
            ) {
                return bytes.copyOfRange(i, dataEnd)
            }
        }
        return null
    }

    private fun extractFromFlacBlocks(input: InputStream): ByteArray? {
        var isLast = false
        while (!isLast) {
            val blockHeader = ByteArray(4)
            if (input.readFullyOrLess(blockHeader) < 4) break
            val headerByte = blockHeader[0].toInt() and 0xFF
            isLast = (headerByte and 0x80) != 0
            val blockType = headerByte and 0x7F
            val length = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                ((blockHeader[2].toInt() and 0xFF) shl 8) or
                (blockHeader[3].toInt() and 0xFF)

            if (length <= 0) continue

            if (blockType == 6) { // METADATA_BLOCK_PICTURE
                val blockData = ByteArray(length)
                val readBytes = input.readFullyOrLess(blockData)
                val image = extractImageFromFrameData(blockData, 0, readBytes)
                if (image != null && image.isNotEmpty()) return image
            } else {
                input.skipBytes(length.toLong())
            }
        }
        return null
    }

    private fun extractFromRiffStream(input: InputStream): ByteArray? {
        // Read remaining 8 bytes of RIFF header (size + format)
        val header = ByteArray(8)
        if (input.readFullyOrLess(header) < 8) return null
        val format = String(header, 4, 4, StandardCharsets.ISO_8859_1)
        if (format != "WAVE") return null

        while (true) {
            val chunkHeader = ByteArray(8)
            if (input.readFullyOrLess(chunkHeader) < 8) break
            val chunkId = String(chunkHeader, 0, 4, StandardCharsets.ISO_8859_1)
            val chunkSize = ((chunkHeader[4].toInt() and 0xFF)) or
                ((chunkHeader[5].toInt() and 0xFF) shl 8) or
                ((chunkHeader[6].toInt() and 0xFF) shl 16) or
                ((chunkHeader[7].toInt() and 0xFF) shl 24)

            if (chunkSize <= 0) break

            if (chunkId.equals("id3 ", ignoreCase = true) || chunkId == "ID3 ") {
                val id3Data = ByteArray(chunkSize.coerceAtMost(MAX_TAG_READ_BYTES.toInt()))
                val readBytes = input.readFullyOrLess(id3Data)
                if (readBytes >= 10 && id3Data[0] == 'I'.code.toByte() && id3Data[1] == 'D'.code.toByte() && id3Data[2] == '3'.code.toByte()) {
                    val major = id3Data[3].toInt() and 0xFF
                    val flags = id3Data[5].toInt() and 0xFF
                    val payload = id3Data.copyOfRange(10, readBytes)
                    val img = parseId3v2TagPayload(payload, payload.size, major, flags)
                    if (img != null) return img
                }
            } else {
                val pad = chunkSize % 2
                input.skipBytes(chunkSize.toLong() + pad)
            }
        }
        return null
    }

    private fun extractFromWavRaf(raf: RandomAccessFile): ByteArray? {
        raf.seek(12)
        val fileLength = raf.length()
        while (raf.filePointer + 8 <= fileLength) {
            val chunkHeader = ByteArray(8)
            raf.readFully(chunkHeader)
            val chunkId = String(chunkHeader, 0, 4, StandardCharsets.ISO_8859_1)
            val chunkSize = ((chunkHeader[4].toLong() and 0xFF)) or
                ((chunkHeader[5].toLong() and 0xFF) shl 8) or
                ((chunkHeader[6].toLong() and 0xFF) shl 16) or
                ((chunkHeader[7].toLong() and 0xFF) shl 24)

            if (chunkSize <= 0) break

            if (chunkId.equals("id3 ", ignoreCase = true) || chunkId == "ID3 ") {
                val size = chunkSize.coerceAtMost(MAX_TAG_READ_BYTES).toInt()
                val chunkData = ByteArray(size)
                raf.readFully(chunkData)
                if (size >= 10 && chunkData[0] == 'I'.code.toByte() && chunkData[1] == 'D'.code.toByte() && chunkData[2] == '3'.code.toByte()) {
                    val major = chunkData[3].toInt() and 0xFF
                    val flags = chunkData[5].toInt() and 0xFF
                    val payload = chunkData.copyOfRange(10, size)
                    val img = parseId3v2TagPayload(payload, payload.size, major, flags)
                    if (img != null) return img
                }
            } else {
                val pad = chunkSize % 2
                raf.seek(raf.filePointer + chunkSize + pad)
            }
        }
        return null
    }

    private fun extractFromMp4Raf(raf: RandomAccessFile): ByteArray? {
        raf.seek(0)
        return findMp4CovrAtom(raf, 0, raf.length())
    }

    private fun findMp4CovrAtom(raf: RandomAccessFile, boxStart: Long, boxEnd: Long): ByteArray? {
        var cursor = boxStart
        while (cursor + 8 <= boxEnd) {
            raf.seek(cursor)
            val size = raf.readInt().toLong() and 0xFFFFFFFFL
            val typeBytes = ByteArray(4)
            raf.readFully(typeBytes)
            val type = String(typeBytes, StandardCharsets.ISO_8859_1)

            val actualSize = if (size == 1L) {
                raf.readLong()
            } else if (size == 0L) {
                boxEnd - cursor
            } else {
                size
            }

            if (actualSize < 8) break
            val contentStart = if (size == 1L) cursor + 16 else cursor + 8
            val contentEnd = cursor + actualSize

            if (type == "covr") {
                // Inside covr, find data atom
                return findMp4DataAtom(raf, contentStart, contentEnd)
            }

            if (type in CONTAINER_ATOMS) {
                val innerStart = if (type == "meta") contentStart + 4 else contentStart
                val result = findMp4CovrAtom(raf, innerStart, contentEnd)
                if (result != null) return result
            }

            cursor += actualSize
        }
        return null
    }

    private fun findMp4DataAtom(raf: RandomAccessFile, covrStart: Long, covrEnd: Long): ByteArray? {
        var cursor = covrStart
        while (cursor + 8 <= covrEnd) {
            raf.seek(cursor)
            val size = raf.readInt().toLong() and 0xFFFFFFFFL
            val typeBytes = ByteArray(4)
            raf.readFully(typeBytes)
            val type = String(typeBytes, StandardCharsets.ISO_8859_1)

            val actualSize = if (size == 1L) raf.readLong() else size
            if (actualSize < 8) break
            val contentStart = if (size == 1L) cursor + 16 else cursor + 8
            val contentEnd = cursor + actualSize

            if (type == "data") {
                // data box: 4 bytes type flags + 4 bytes locale = 8 bytes prefix
                val payloadStart = contentStart + 8
                if (contentEnd > payloadStart) {
                    val len = (contentEnd - payloadStart).toInt()
                    val data = ByteArray(len)
                    raf.seek(payloadStart)
                    raf.readFully(data)
                    return data
                }
            }
            cursor += actualSize
        }
        return null
    }

    private val CONTAINER_ATOMS = setOf("moov", "udta", "meta", "ilst")

    private fun extractWithFfmpeg(filePath: String): ByteArray? {
        val tempOutput = File.createTempFile("halcyon_art_", ".img")
        return try {
            val process = ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i",
                filePath,
                "-an",
                "-vcodec",
                "copy",
                tempOutput.absolutePath
            )
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)
            if (finished && process.exitValue() == 0 && tempOutput.length() > 0) {
                tempOutput.readBytes()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            tempOutput.delete()
        }
    }

    private fun InputStream.readFullyOrLess(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun InputStream.skipBytes(n: Long) {
        var remaining = n
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) {
                if (read() == -1) break
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
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
}
