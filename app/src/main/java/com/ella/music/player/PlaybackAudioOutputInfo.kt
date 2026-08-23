package com.ella.music.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

data class PlaybackAudioOutputInfo(
    val sourceMimeType: String = "",
    val sourceContainerMimeType: String = "",
    val sourceCodecs: String = "",
    val sourceSampleRate: Int = 0,
    val sourceChannelCount: Int = 0,
    val sourceBitRate: Int = 0,
    val sourcePcmEncoding: Int = C.ENCODING_INVALID,
    val outputBackend: String = "AudioTrack",
    val outputSampleRate: Int = 0,
    val outputChannelCount: Int = 0,
    val outputEncoding: Int = C.ENCODING_INVALID
) {
    val isResampling: Boolean
        get() = sourceSampleRate > 0 && outputSampleRate > 0 && sourceSampleRate != outputSampleRate
}

object PlaybackAudioOutputState {
    private val _info = MutableStateFlow(PlaybackAudioOutputInfo())
    val info = _info.asStateFlow()

    fun updateSource(format: Format?) {
        _info.value = _info.value.copy(
            sourceMimeType = format?.sampleMimeType.orEmpty(),
            sourceContainerMimeType = format?.containerMimeType.orEmpty(),
            sourceCodecs = format?.codecs.orEmpty(),
            sourceSampleRate = format?.sampleRate?.takeIf { it > 0 } ?: 0,
            sourceChannelCount = format?.channelCount?.takeIf { it > 0 } ?: 0,
            sourceBitRate = format?.bitrate?.takeIf { it > 0 } ?: 0,
            sourcePcmEncoding = format?.pcmEncoding?.takeIf { it != C.ENCODING_INVALID }
                ?: C.ENCODING_INVALID
        )
    }

    fun updateBackend(backend: String) {
        _info.value = _info.value.copy(outputBackend = backend)
    }

    fun updatePcmOutput(format: AudioProcessor.AudioFormat) {
        _info.value = _info.value.copy(
            outputSampleRate = format.sampleRate,
            outputChannelCount = format.channelCount,
            outputEncoding = format.encoding
        )
    }

    fun clear() {
        _info.value = PlaybackAudioOutputInfo()
    }
}

@UnstableApi
internal class OutputProbeAudioProcessor(
    private val onFormat: (AudioProcessor.AudioFormat) -> Unit
) : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        onFormat(inputAudioFormat)
        // This processor only observes the negotiated PCM format. Returning the input format
        // marks it as an active processor, which makes Media3 feed its own recycled output buffer
        // back as input in some pipelines. Copying that buffer onto itself then crashes every
        // playback attempt with "The source buffer is this buffer". Keep the probe inactive: the
        // configure callback still runs, while audio passes through without an unnecessary copy.
        return AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        error("Inactive output-format probe must not receive audio buffers")
    }
}

internal fun playbackPcmEncodingLabel(encoding: Int): String = when (encoding) {
    C.ENCODING_PCM_8BIT -> "8-bit PCM"
    C.ENCODING_PCM_16BIT -> "16-bit PCM"
    C.ENCODING_PCM_24BIT -> "24-bit PCM"
    C.ENCODING_PCM_32BIT -> "32-bit PCM"
    C.ENCODING_PCM_FLOAT -> "Float32"
    else -> "—"
}

internal fun playbackPcmBitDepth(encoding: Int): Int = when (encoding) {
    C.ENCODING_PCM_8BIT -> 8
    C.ENCODING_PCM_16BIT -> 16
    C.ENCODING_PCM_24BIT -> 24
    C.ENCODING_PCM_32BIT,
    C.ENCODING_PCM_FLOAT -> 32
    else -> 0
}

internal fun playbackFormatRequiresConversion(
    sourceSampleRate: Int,
    outputSampleRate: Int,
    sourceBitDepth: Int,
    outputEncoding: Int
): Boolean {
    val sampleRateChanged = sourceSampleRate > 0 &&
        outputSampleRate > 0 &&
        sourceSampleRate != outputSampleRate
    val outputBitDepth = playbackPcmBitDepth(outputEncoding)
    val bitDepthChanged = sourceBitDepth > 0 &&
        outputBitDepth > 0 &&
        sourceBitDepth != outputBitDepth
    return sampleRateChanged || bitDepthChanged
}
