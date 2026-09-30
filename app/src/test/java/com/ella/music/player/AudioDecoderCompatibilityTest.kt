package com.ella.music.player

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.lang.reflect.InvocationTargetException
import java.util.function.Predicate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AudioDecoderCompatibilityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val handler = Handler(Looper.getMainLooper())
    private val sink = DefaultAudioSink.Builder(context).build()
    private val selector = MediaCodecSelector { _, _, _ -> emptyList() }

    @Test fun theLogged192kHzFlacWith68634ByteInputUsesTheCompatibilityDecoder() {
        assertTrue(needsCompatibleSoftwareAudioDecoder(format(MimeTypes.AUDIO_FLAC, 68634)))
        assertFalse(needsCompatibleSoftwareAudioDecoder(format(MimeTypes.AUDIO_FLAC, 32768)))
        assertTrue(needsCompatibleSoftwareAudioDecoder(format(MimeTypes.AUDIO_FLAC, 32769)))
    }

    @Test fun normalAndUnknownSizeFlacPreserveTheSelectedDecoderOrder() {
        assertFalse(needsCompatibleSoftwareAudioDecoder(format(MimeTypes.AUDIO_FLAC, 16384)))
        assertFalse(needsCompatibleSoftwareAudioDecoder(format(MimeTypes.AUDIO_FLAC, Format.NO_VALUE)))
    }

    @Test fun aacAndMultichannelDolbyNeverEnterTheFlacCompatibilityPath() {
        listOf(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_RAW).forEach {
            assertFalse(needsCompatibleSoftwareAudioDecoder(format(it, 100_000)))
        }
        assertTrue(needsCompatibleSoftwareAudioDecoder(format(MimeTypes.AUDIO_MPEG, 4096)))
    }

    @Test fun everyDecoderModeInstallsTheFilteredCompatibilityRendererAndCapacityGuard() {
        val method = EllaRenderersFactory::class.java.getDeclaredMethod("buildAudioRenderers",
            Context::class.java, Int::class.javaPrimitiveType, MediaCodecSelector::class.java,
            Boolean::class.javaPrimitiveType, AudioSink::class.java, Handler::class.java,
            AudioRendererEventListener::class.java, java.util.ArrayList::class.java).apply { isAccessible = true }
        listOf(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF,
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER).forEach { mode ->
            val renderers = java.util.ArrayList<Renderer>()
            method.invoke(EllaRenderersFactory(context), context, mode, selector, mode != 0, sink, handler,
                object : AudioRendererEventListener {}, renderers)
            assertTrue(renderers.first() is FfmpegAudioRenderer)
            assertTrue(renderers.any { it is CapacityAwareAudioRenderer })
            val field = FfmpegAudioRenderer::class.java.getDeclaredField("formatFilter").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val filter = field.get(renderers.first()) as Predicate<Format>
            assertTrue(filter.test(format(MimeTypes.AUDIO_FLAC, 68634)))
            assertFalse(filter.test(format(MimeTypes.AUDIO_AAC, 68634)))
            assertFalse(filter.test(format(MimeTypes.AUDIO_FLAC, 16000)))
            assertTrue(filter.test(format(MimeTypes.AUDIO_MPEG, 4096)))
        }
    }

    @Test fun theLoggedBufferRejectionBecomesAFatalPlaybackErrorInsteadOfSilentFrameSkipping() {
        val renderer = renderer(object : AudioRendererEventListener {})
        val rejected = DecoderInputBuffer.InsufficientCapacityException(32768, 42185)
        try {
            errorCallback(renderer, rejected)
            fail("An oversized audio sample must reach the player error handler")
        } catch (failure: InvocationTargetException) {
            val error = failure.targetException as ExoPlaybackException
            assertEquals(PlaybackException.ERROR_CODE_DECODING_FAILED, error.errorCode)
            assertSame(rejected, error.cause)
        }
    }

    @Test fun otherCodecNotificationsRetainMedia3RecoveryBehavior() {
        var reported: Exception? = null
        val renderer = renderer(object : AudioRendererEventListener {
            override fun onAudioCodecError(audioCodecError: Exception) { reported = audioCodecError }
        })
        val original = IllegalStateException("recoverable codec notification")
        errorCallback(renderer, original)
        ShadowLooper.idleMainLooper()
        assertSame(original, reported)
    }

    private fun renderer(listener: AudioRendererEventListener) = CapacityAwareAudioRenderer(
        context, MediaCodecAdapter.Factory.getDefault(context), selector, false, handler, listener, sink)
    private fun errorCallback(renderer: CapacityAwareAudioRenderer, error: Exception) =
        CapacityAwareAudioRenderer::class.java.getDeclaredMethod("onCodecError", Exception::class.java)
            .apply { isAccessible = true }.invoke(renderer, error)
    private fun format(mime: String, size: Int) = Format.Builder().setSampleMimeType(mime)
        .setSampleRate(192000).setChannelCount(2).setMaxInputSize(size).build()
}
