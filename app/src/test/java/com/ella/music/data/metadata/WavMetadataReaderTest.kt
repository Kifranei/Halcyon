package com.ella.music.data.metadata

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WavMetadataReaderTest {
    @Test
    fun derivesDurationFromPcmDataWhenMediaStoreHasNoDuration() {
        val sampleRate = 8_000
        val channels = 1
        val bitsPerSample = 16
        val durationSeconds = 2
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val pcm = ByteArray(byteRate * durationSeconds)
        val file = File.createTempFile("halcyon-wav-duration", ".wav")
        try {
            file.writeBytes(buildPcmWav(sampleRate, channels, bitsPerSample, pcm))

            val metadata = WavMetadataReader.read(file)

            assertNotNull(metadata)
            assertEquals(durationSeconds * 1_000L, metadata?.durationMs)
        } finally {
            file.delete()
        }
    }

    private fun buildPcmWav(
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        pcm: ByteArray
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray())
            writeLeInt(36 + pcm.size)
            write("WAVEfmt ".toByteArray())
            writeLeInt(16)
            writeLeShort(1)
            writeLeShort(channels)
            writeLeInt(sampleRate)
            writeLeInt(byteRate)
            writeLeShort(blockAlign)
            writeLeShort(bitsPerSample)
            write("data".toByteArray())
            writeLeInt(pcm.size)
            write(pcm)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLeInt(value: Int) {
        write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }

    private fun ByteArrayOutputStream.writeLeShort(value: Int) {
        write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
    }
}
