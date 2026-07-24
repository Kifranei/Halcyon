package com.ella.music.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.ella.music.dsp.Compressor
import com.ella.music.dsp.Crossfeed
import com.ella.music.dsp.DynamicEq
import com.ella.music.dsp.LoudnessBalance
import com.ella.music.dsp.MonoBass
import com.ella.music.dsp.MoogLadderFilter
import com.ella.music.dsp.Panoramic360
import com.ella.music.dsp.PeakLimiter
import com.ella.music.dsp.Reverb
import com.ella.music.dsp.ShelfEqualizer
import com.ella.music.dsp.SpeakerOutput
import com.ella.music.dsp.StereoWidener
import com.ella.music.dsp.Surround360
import com.ella.music.dsp.TenBandEqualizer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

/**
 * Immutable snapshot of the custom equalizer settings.
 *
 * Settings are stored as dB values to match the DSP core. The UI stores millibels,
 * so the owner of this processor must convert before calling [setSettings].
 */
data class EqualizerSettings(
    val enabled: Boolean = false,
    val bandGainsDb: FloatArray = FloatArray(TenBandEqualizer.BAND_COUNT) { 0f },
    val eqQ: Float = EQ_DEFAULT_Q,
    val bassGainDb: Float = 0f,
    val trebleGainDb: Float = 0f,
    val compressorEnabled: Boolean = false,
    val compressorThresholdDb: Float = -18f,
    val compressorRatio: Float = 2f,
    val compressorMakeupDb: Float = 0f,
    val stereoWidth: Float = 1f,
    val reverbPreset: Int = 0,
    val surround360Enabled: Boolean = false,
    val surround360Intensity: Float = 50f,
    val surround360RotationSpeed: Float = 30f,
    val panoramic360Enabled: Boolean = false,
    val panoramic360Intensity: Float = 50f,
    val panoramic360AzimuthDegrees: Float = 0f,
    val panoramic360ElevationDegrees: Float = 0f,
    val loudnessBalanceEnabled: Boolean = false,
    val loudnessPercent: Float = 35f,
    val channelBalance: Float = 0f,
    val crossfeedEnabled: Boolean = false,
    val crossfeedLowCutHz: Float = 300f,
    val crossfeedHighCutHz: Float = 2_000f,
    val crossfeedAttenuationDb: Float = 6f,
    val monoBassEnabled: Boolean = false,
    val monoBassCrossoverHz: Float = 120f,
    val monoBassAmount: Float = 100f,
    val speakerOutputEnabled: Boolean = false,
    val speakerOutputMode: Int = 0,
    val speakerOutputStrength: Float = 82f,
    val dynamicEqEnabled: Boolean = false,
    val dynamicEqIntensity: Float = 50f,
    val deEsserAmount: Float = 45f,
    val deEsserFrequencyHz: Float = 6_500f,
    val moogLadderEnabled: Boolean = false,
    val moogLadderMode: Int = 0,
    val moogLadderCutoffHz: Float = 12_000f,
    val moogLadderResonance: Float = 20f,
    val moogLadderDriveDb: Float = 0f,
    val moogLadderMix: Float = 100f,
    val peakLimiterEnabled: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EqualizerSettings) return false
        return enabled == other.enabled &&
            bandGainsDb.contentEquals(other.bandGainsDb) &&
            eqQ == other.eqQ &&
            bassGainDb == other.bassGainDb &&
            trebleGainDb == other.trebleGainDb &&
            compressorEnabled == other.compressorEnabled &&
            compressorThresholdDb == other.compressorThresholdDb &&
            compressorRatio == other.compressorRatio &&
            compressorMakeupDb == other.compressorMakeupDb &&
            stereoWidth == other.stereoWidth &&
            reverbPreset == other.reverbPreset &&
            surround360Enabled == other.surround360Enabled &&
            surround360Intensity == other.surround360Intensity &&
            surround360RotationSpeed == other.surround360RotationSpeed &&
            panoramic360Enabled == other.panoramic360Enabled &&
            panoramic360Intensity == other.panoramic360Intensity &&
            panoramic360AzimuthDegrees == other.panoramic360AzimuthDegrees &&
            panoramic360ElevationDegrees == other.panoramic360ElevationDegrees &&
            loudnessBalanceEnabled == other.loudnessBalanceEnabled &&
            loudnessPercent == other.loudnessPercent &&
            channelBalance == other.channelBalance &&
            crossfeedEnabled == other.crossfeedEnabled &&
            crossfeedLowCutHz == other.crossfeedLowCutHz &&
            crossfeedHighCutHz == other.crossfeedHighCutHz &&
            crossfeedAttenuationDb == other.crossfeedAttenuationDb &&
            monoBassEnabled == other.monoBassEnabled &&
            monoBassCrossoverHz == other.monoBassCrossoverHz &&
            monoBassAmount == other.monoBassAmount &&
            speakerOutputEnabled == other.speakerOutputEnabled &&
            speakerOutputMode == other.speakerOutputMode &&
            speakerOutputStrength == other.speakerOutputStrength &&
            dynamicEqEnabled == other.dynamicEqEnabled &&
            dynamicEqIntensity == other.dynamicEqIntensity &&
            deEsserAmount == other.deEsserAmount &&
            deEsserFrequencyHz == other.deEsserFrequencyHz &&
            moogLadderEnabled == other.moogLadderEnabled &&
            moogLadderMode == other.moogLadderMode &&
            moogLadderCutoffHz == other.moogLadderCutoffHz &&
            moogLadderResonance == other.moogLadderResonance &&
            moogLadderDriveDb == other.moogLadderDriveDb &&
            moogLadderMix == other.moogLadderMix &&
            peakLimiterEnabled == other.peakLimiterEnabled
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + bandGainsDb.contentHashCode()
        result = 31 * result + eqQ.hashCode()
        result = 31 * result + bassGainDb.hashCode()
        result = 31 * result + trebleGainDb.hashCode()
        result = 31 * result + compressorEnabled.hashCode()
        result = 31 * result + compressorThresholdDb.hashCode()
        result = 31 * result + compressorRatio.hashCode()
        result = 31 * result + compressorMakeupDb.hashCode()
        result = 31 * result + stereoWidth.hashCode()
        result = 31 * result + reverbPreset
        result = 31 * result + surround360Enabled.hashCode()
        result = 31 * result + surround360Intensity.hashCode()
        result = 31 * result + surround360RotationSpeed.hashCode()
        result = 31 * result + panoramic360Enabled.hashCode()
        result = 31 * result + panoramic360Intensity.hashCode()
        result = 31 * result + panoramic360AzimuthDegrees.hashCode()
        result = 31 * result + panoramic360ElevationDegrees.hashCode()
        result = 31 * result + loudnessBalanceEnabled.hashCode()
        result = 31 * result + loudnessPercent.hashCode()
        result = 31 * result + channelBalance.hashCode()
        result = 31 * result + crossfeedEnabled.hashCode()
        result = 31 * result + crossfeedLowCutHz.hashCode()
        result = 31 * result + crossfeedHighCutHz.hashCode()
        result = 31 * result + crossfeedAttenuationDb.hashCode()
        result = 31 * result + monoBassEnabled.hashCode()
        result = 31 * result + monoBassCrossoverHz.hashCode()
        result = 31 * result + monoBassAmount.hashCode()
        result = 31 * result + speakerOutputEnabled.hashCode()
        result = 31 * result + speakerOutputMode
        result = 31 * result + speakerOutputStrength.hashCode()
        result = 31 * result + dynamicEqEnabled.hashCode()
        result = 31 * result + dynamicEqIntensity.hashCode()
        result = 31 * result + deEsserAmount.hashCode()
        result = 31 * result + deEsserFrequencyHz.hashCode()
        result = 31 * result + moogLadderEnabled.hashCode()
        result = 31 * result + moogLadderMode
        result = 31 * result + moogLadderCutoffHz.hashCode()
        result = 31 * result + moogLadderResonance.hashCode()
        result = 31 * result + moogLadderDriveDb.hashCode()
        result = 31 * result + moogLadderMix.hashCode()
        result = 31 * result + peakLimiterEnabled.hashCode()
        return result
    }

    companion object {
        const val EQ_DEFAULT_Q = 1.414f
    }
}

/**
 * ExoPlayer [AudioProcessor] that applies a custom 10-band parametric EQ.
 *
 * This runs entirely in software and does not depend on the system [android.media.audiofx.Equalizer].
 * The DSP core is a Kotlin port of the BiQuad/ParametricEQ implementation from RawS-Music.
 */
@UnstableApi
class EqualizerAudioProcessor : AudioProcessor {

    private var inputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET

    private val settingsRef = AtomicReference(EqualizerSettings())
    private var equalizer: TenBandEqualizer? = null
    private var shelf: ShelfEqualizer? = null
    private var compressor: Compressor? = null
    private val stereoWidener = StereoWidener()
    private var reverb: Reverb? = null
    private var surround360: Surround360? = null
    private var panoramic360: Panoramic360? = null
    private var loudnessBalance: LoudnessBalance? = null
    private var crossfeed: Crossfeed? = null
    private var monoBass: MonoBass? = null
    private var speakerOutput: SpeakerOutput? = null
    private var dynamicEq: DynamicEq? = null
    private var moogLadder: MoogLadderFilter? = null
    private var peakLimiter: PeakLimiter? = null

    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private var tempFloatBuffer: FloatArray = FloatArray(0)

    private var reusableOutputBuffer: ByteBuffer? = null

    fun setSettings(settings: EqualizerSettings) {
        settingsRef.set(settings)
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT
            || inputAudioFormat.channelCount !in 1..2
        ) {
            this.inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
            this.outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
            equalizer = null
            return AudioProcessor.AudioFormat.NOT_SET
        }

        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat

        equalizer = TenBandEqualizer(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount
        )
        shelf = ShelfEqualizer(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        compressor = Compressor(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        reverb = Reverb(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        surround360 = Surround360(inputAudioFormat.sampleRate)
        panoramic360 = Panoramic360(inputAudioFormat.sampleRate)
        loudnessBalance = LoudnessBalance(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        crossfeed = Crossfeed(inputAudioFormat.sampleRate)
        monoBass = MonoBass(inputAudioFormat.sampleRate)
        speakerOutput = SpeakerOutput(inputAudioFormat.sampleRate)
        dynamicEq = DynamicEq(inputAudioFormat.sampleRate)
        moogLadder = MoogLadderFilter(inputAudioFormat.sampleRate)
        peakLimiter = PeakLimiter(inputAudioFormat.sampleRate)
        applySettings(force = true)
        return outputAudioFormat
    }

    override fun isActive(): Boolean {
        // Always active so enabling/disabling the EQ does not require rebuilding the audio sink.
        // The DSP core bypasses itself when disabled.
        return inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val remaining = limit - position
        if (remaining == 0) {
            return
        }

        val eq = equalizer ?: return
        applySettings()

        val channels = inputAudioFormat.channelCount
        val frames = remaining / (channels * BYTES_PER_SAMPLE)
        val requiredFloats = frames * channels
        if (tempFloatBuffer.size < requiredFloats) {
            tempFloatBuffer = FloatArray(requiredFloats)
        }

        // 16-bit PCM -> float [-1.0, 1.0]
        for (i in 0 until requiredFloats) {
            val sample = inputBuffer.getShort(position + i * BYTES_PER_SAMPLE).toInt()
            tempFloatBuffer[i] = sample / 32768f
        }

        eq.process(tempFloatBuffer, frames)
        shelf?.process(tempFloatBuffer, frames)
        loudnessBalance?.process(tempFloatBuffer, frames)
        monoBass?.process(tempFloatBuffer, frames, channels)
        dynamicEq?.process(tempFloatBuffer, frames, channels)
        moogLadder?.process(tempFloatBuffer, frames, channels)
        compressor?.process(tempFloatBuffer, frames)
        reverb?.process(tempFloatBuffer, frames)
        // The spatial stage is intentionally exclusive: stacking crossfeed or width after 360
        // spatialization destroys the binaural cues those effects rely on.
        if (settingsRef.get().panoramic360Enabled) {
            panoramic360?.process(tempFloatBuffer, frames, channels)
        } else if (settingsRef.get().surround360Enabled) {
            surround360?.process(tempFloatBuffer, frames, channels)
        } else {
            crossfeed?.process(tempFloatBuffer, frames, channels)
            stereoWidener.process(tempFloatBuffer, frames, channels)
        }
        speakerOutput?.process(tempFloatBuffer, frames, channels)
        peakLimiter?.process(tempFloatBuffer, frames, channels)

        // float -> 16-bit PCM, matching RawS-Music's conversion sign handling.
        outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.order(ByteOrder.nativeOrder())
        for (i in 0 until requiredFloats) {
            val clamped = tempFloatBuffer[i].coerceIn(-1f, 1f)
            val intSample = if (clamped < 0) {
                (clamped * 32768f).toInt()
            } else {
                (clamped * 32767f).toInt()
            }
            outputBuffer.putShort(intSample.toShort())
        }
        outputBuffer.flip()

        inputBuffer.position(limit)
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        equalizer?.reset()
        shelf?.reset()
        compressor?.reset()
        reverb?.reset()
        surround360?.reset()
        panoramic360?.reset()
        loudnessBalance?.reset()
        crossfeed?.reset()
        monoBass?.reset()
        speakerOutput?.reset()
        dynamicEq?.reset()
        moogLadder?.reset()
        peakLimiter?.reset()
        applySettings(force = true)
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        equalizer = null
        shelf = null
        compressor = null
        reverb = null
        surround360 = null
        panoramic360 = null
        loudnessBalance = null
        crossfeed = null
        monoBass = null
        speakerOutput = null
        dynamicEq = null
        moogLadder = null
        peakLimiter = null
        reusableOutputBuffer = null
    }

    private fun applySettings(force: Boolean = false) {
        val eq = equalizer ?: return
        val settings = settingsRef.get()
        if (!force && settings == lastAppliedSettings) return

        eq.setEnabled(settings.enabled)
        eq.setGlobalQ(settings.eqQ)
        settings.bandGainsDb.forEachIndexed { index, gainDb ->
            eq.setBandGain(index, gainDb)
        }
        // Tone / dynamics / width run regardless of the graphic-EQ enable flag; each bypasses itself
        // when it has no effect (0 dB shelves, disabled compressor, width == 1.0).
        shelf?.setGains(settings.bassGainDb, settings.trebleGainDb)
        compressor?.setParams(
            settings.compressorEnabled,
            settings.compressorThresholdDb,
            settings.compressorRatio,
            settings.compressorMakeupDb
        )
        stereoWidener.setWidth(settings.stereoWidth)
        reverb?.setPreset(settings.reverbPreset)
        surround360?.setParams(
            enabled = settings.surround360Enabled,
            intensityPercent = settings.surround360Intensity,
            azimuthDegrees = 0f,
            rotationDegreesPerSecond = settings.surround360RotationSpeed
        )
        panoramic360?.setParams(
            enabled = settings.panoramic360Enabled,
            intensityPercent = settings.panoramic360Intensity,
            azimuthDegrees = settings.panoramic360AzimuthDegrees,
            elevationDegrees = settings.panoramic360ElevationDegrees
        )
        loudnessBalance?.setParams(
            enabled = settings.loudnessBalanceEnabled,
            loudnessPercent = settings.loudnessPercent,
            balancePercent = settings.channelBalance
        )
        crossfeed?.setParams(
            enabled = settings.crossfeedEnabled,
            lowCutHz = settings.crossfeedLowCutHz,
            highCutHz = settings.crossfeedHighCutHz,
            attenuationDb = settings.crossfeedAttenuationDb
        )
        monoBass?.setParams(
            enabled = settings.monoBassEnabled,
            crossoverHz = settings.monoBassCrossoverHz,
            amountPercent = settings.monoBassAmount
        )
        speakerOutput?.setParams(
            enabled = settings.speakerOutputEnabled,
            mode = settings.speakerOutputMode,
            strengthPercent = settings.speakerOutputStrength
        )
        dynamicEq?.setParams(
            enabled = settings.dynamicEqEnabled,
            intensityPercent = settings.dynamicEqIntensity,
            deEsserPercent = settings.deEsserAmount,
            deEsserFrequencyHz = settings.deEsserFrequencyHz
        )
        moogLadder?.setParams(
            enabled = settings.moogLadderEnabled,
            mode = settings.moogLadderMode,
            cutoffHz = settings.moogLadderCutoffHz,
            resonancePercent = settings.moogLadderResonance,
            driveDb = settings.moogLadderDriveDb,
            mixPercent = settings.moogLadderMix
        )
        peakLimiter?.setEnabled(settings.peakLimiterEnabled)
        lastAppliedSettings = settings
    }

    private var lastAppliedSettings: EqualizerSettings? = null

    private fun replaceOutputBuffer(count: Int): ByteBuffer {
        val existing = reusableOutputBuffer
        return if (existing != null && existing.capacity() >= count) {
            existing.clear()
            existing.limit(count)
            existing
        } else {
            ByteBuffer.allocateDirect(count).order(ByteOrder.nativeOrder()).also {
                reusableOutputBuffer = it
            }
        }
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        private const val BYTES_PER_SAMPLE = 2
    }
}
