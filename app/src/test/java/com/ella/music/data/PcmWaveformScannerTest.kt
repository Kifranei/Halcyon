package com.ella.music.data

import android.app.Application
import android.media.AudioFormat
import android.media.MediaFormat
import androidx.media3.common.MimeTypes
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.media3.inspector.MediaExtractorCompat
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ByteArrayDataSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PcmWaveformScannerTest {
    @Test fun floatingPointStereoEnergyKeepsTimingAndDoesNotCancelOppositePhaseChannels() {
        val buckets = PcmWaveformBuckets(4, 1_000_000)
        val first = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(0.5f); putFloat(-0.5f); putFloat(0.25f); putFloat(-0.25f); flip()
        }
        buckets.add(first, 0, 4, 2, AudioFormat.ENCODING_PCM_FLOAT)
        val second = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(0.75f); putFloat(-0.75f); putFloat(0f); putFloat(0f); flip()
        }
        buckets.add(second, 500_000, 4, 2, AudioFormat.ENCODING_PCM_FLOAT)
        assertArrayEquals(floatArrayOf(0.5f, 0.25f, 0.75f, 0f), buckets.result(), 0.0001f)
    }

    @Test fun integerPcmPrecisionsProduceTheSameEnvelope() {
        for ((encoding, bytes) in listOf(AudioFormat.ENCODING_PCM_8BIT to byteArrayOf(192.toByte()),
            AudioFormat.ENCODING_PCM_16BIT to byteArrayOf(0, 64),
            AudioFormat.ENCODING_PCM_24BIT_PACKED to byteArrayOf(0, 0, 64),
            AudioFormat.ENCODING_PCM_32BIT to byteArrayOf(0, 0, 0, 64))) {
            val buckets = PcmWaveformBuckets(1, 1_000_000)
            buckets.add(ByteBuffer.wrap(bytes), 0, 1, 1, encoding)
            assertEquals(0.5f, buckets.result().single(), 0.0001f)
        }
    }

    @Test fun missingPcmIsNotReturnedAsASuccessfulSilentWaveform() {
        assertTrue(PcmWaveformBuckets(4, 1_000_000).result().isEmpty())
        val buckets = PcmWaveformBuckets(4, 1_000_000)
        val invalid = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply { putFloat(Float.NaN); flip() }
        buckets.add(invalid,
            0, 4, 1, AudioFormat.ENCODING_PCM_FLOAT)
        assertTrue(buckets.result().isEmpty())
    }

    @Test fun alacConfigurationCopiesTheMagicCookieWithoutConsumingIt() {
        val cookie = ByteBuffer.wrap(ByteArray(24) { it.toByte() })
        val format = MediaFormat.createAudioFormat(MimeTypes.AUDIO_ALAC, 44100, 2).apply { setByteBuffer("csd-0", cookie) }
        val decoderFormat = waveformDecoderFormat(format)
        assertEquals(0, cookie.position())
        assertEquals(44100, decoderFormat.sampleRate)
        assertEquals(2, decoderFormat.channelCount)
        assertArrayEquals(cookie.array(), decoderFormat.initializationData.single())
    }

    @Test fun realAlacContainerYieldsCodecCookieAndEveryCompressedSample() {
        val path = System.getenv("HALCYON_ALAC_FIXTURE")
        assumeTrue(!path.isNullOrBlank() && File(path).isFile)
        val data = File(checkNotNull(path)).readBytes()
        val extractor = MediaExtractorCompat(DefaultExtractorsFactory(), DataSource.Factory { ByteArrayDataSource(data) })
        try {
            extractor.setDataSource("test-alac.m4a")
            val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == MimeTypes.AUDIO_ALAC }
            extractor.selectTrack(track)
            val format = waveformDecoderFormat(extractor.getTrackFormat(track))
            assertEquals(24, format.initializationData.single().size)
            assertEquals(44100, format.sampleRate)
            assertEquals(2, format.channelCount)
            var count = 0
            while (extractor.sampleSize >= 0) {
                val data = ByteBuffer.allocate(extractor.sampleSize.toInt())
                assertTrue(extractor.readSampleData(data, 0) > 0)
                count++
                extractor.advance()
            }
            assertTrue(count > 10)
            extractor.seekTo(0, MediaExtractorCompat.SEEK_TO_CLOSEST_SYNC)
            assertEquals(0, extractor.sampleTime)
        } finally { extractor.release() }
    }
}
