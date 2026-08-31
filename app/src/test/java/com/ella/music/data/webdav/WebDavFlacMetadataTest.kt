package com.ella.music.data.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebDavFlacMetadataTest {
    @Test
    fun findsTheEndOfACompleteMetadataChain() {
        val bytes = flac(
            block(last = false, type = 0, payload = ByteArray(34)),
            block(last = false, type = 4, payload = "TITLE=Song".toByteArray()),
            block(last = true, type = 6, payload = ByteArray(12))
        )

        assertEquals(bytes.size, flacMetadataEnd(bytes))
    }

    @Test
    fun waitsForTheCompleteLastBlock() {
        val bytes = flac(
            block(last = false, type = 0, payload = ByteArray(34)),
            block(last = true, type = 4, payload = ByteArray(80))
        )

        assertNull(flacMetadataEnd(bytes.copyOf(bytes.size - 1)))
        assertEquals(bytes.size, flacMetadataEnd(bytes))
    }

    @Test
    fun rejectsNonFlacBytes() {
        assertNull(flacMetadataEnd("not flac".toByteArray()))
    }

    @Test
    fun acceptsAnId3PrefixBeforeTheFlacMarker() {
        val id3Header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0, 0, 5
        )
        val id3Payload = ByteArray(5)
        val stream = flac(block(last = true, type = 0, payload = ByteArray(34)))

        assertEquals(id3Header.size + id3Payload.size + stream.size, flacMetadataEnd(id3Header + id3Payload + stream))
    }

    private fun flac(vararg blocks: ByteArray): ByteArray =
        byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()) +
            blocks.fold(ByteArray(0)) { result, block -> result + block }

    private fun block(last: Boolean, type: Int, payload: ByteArray): ByteArray {
        val length = payload.size
        return byteArrayOf(
            ((if (last) 0x80 else 0) or type).toByte(),
            (length ushr 16).toByte(),
            (length ushr 8).toByte(),
            length.toByte()
        ) + payload
    }
}
