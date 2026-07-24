package com.ella.music.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Full snapshot of the user's audio-effect configuration, persisted in settings and applied to
 * whichever audio session is currently playing.
 *
 * [eqBandLevelsMb] holds the per-band gain in millibels (1 dB = 100 mB). The equalizer is now
 * implemented in software by [EqualizerAudioProcessor], so this class only manages the legacy
 * system effects [BassBoost] and [Virtualizer] plus publishing fixed 10-band EQ capabilities.
 */
data class AudioEffectSettings(
    val eqEnabled: Boolean = false,
    val eqPreset: Int = PRESET_CUSTOM,
    val eqBandLevelsMb: List<Int> = emptyList(),
    val eqQ: Int = EQ_Q_DEFAULT,
    val bassGainDb: Int = 0,
    val trebleGainDb: Int = 0,
    val compressorEnabled: Boolean = false,
    val compressorThresholdDb: Int = -18,
    val compressorRatio: Int = 2,
    val compressorMakeupDb: Int = 0,
    val stereoWidth: Int = 100,
    val surround360Enabled: Boolean = false,
    val surround360Intensity: Int = 50,
    val surround360RotationSpeed: Int = 30,
    val panoramic360Enabled: Boolean = false,
    val panoramic360Intensity: Int = 50,
    val panoramic360AzimuthDegrees: Int = 0,
    val panoramic360ElevationDegrees: Int = 0,
    val loudnessBalanceEnabled: Boolean = false,
    val loudnessPercent: Int = 35,
    val channelBalance: Int = 0,
    val crossfeedEnabled: Boolean = false,
    val crossfeedLowCutHz: Int = 300,
    val crossfeedHighCutHz: Int = 2_000,
    val crossfeedAttenuationDbTenths: Int = 60,
    val monoBassEnabled: Boolean = false,
    val monoBassCrossoverHz: Int = 120,
    val monoBassAmount: Int = 100,
    val speakerOutputEnabled: Boolean = false,
    val speakerOutputMode: Int = 0,
    val speakerOutputStrength: Int = 82,
    val dynamicEqEnabled: Boolean = false,
    val dynamicEqIntensity: Int = 50,
    val deEsserAmount: Int = 45,
    val deEsserFrequencyHz: Int = 6_500,
    val moogLadderEnabled: Boolean = false,
    val moogLadderMode: Int = MOOG_LADDER_MODE_LOW_PASS_24,
    val moogLadderCutoffHz: Int = 12_000,
    val moogLadderResonance: Int = 20,
    val moogLadderDriveDb: Int = 0,
    val moogLadderMix: Int = 100,
    val peakLimiterEnabled: Boolean = true,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 0,
    val reverbPreset: Int = REVERB_PRESET_OFF
) {
    companion object {
        const val PRESET_CUSTOM = -1
        const val STRENGTH_MAX = 1000
        // Custom software DSP ranges (stored as scaled ints for DataStore / sliders).
        const val EQ_Q_DEFAULT = 141   // Q * 100 (1.41)
        const val EQ_Q_MIN = 30        // 0.3
        const val EQ_Q_MAX = 1000      // 10.0
        const val TONE_GAIN_MIN_DB = -12
        const val TONE_GAIN_MAX_DB = 12
        const val COMP_THRESHOLD_MIN_DB = -60
        const val COMP_THRESHOLD_MAX_DB = 0
        const val COMP_RATIO_MIN = 1
        const val COMP_RATIO_MAX = 20
        const val COMP_MAKEUP_MIN_DB = 0
        const val COMP_MAKEUP_MAX_DB = 24
        const val STEREO_WIDTH_MIN = 0
        const val STEREO_WIDTH_MAX = 200
        const val SURROUND_360_INTENSITY_MIN = 0
        const val SURROUND_360_INTENSITY_MAX = 100
        const val SURROUND_360_ROTATION_MIN = 0
        const val SURROUND_360_ROTATION_MAX = 360
        const val PANORAMIC_360_INTENSITY_MIN = 0
        const val PANORAMIC_360_INTENSITY_MAX = 100
        const val PANORAMIC_360_AZIMUTH_MIN = -180
        const val PANORAMIC_360_AZIMUTH_MAX = 180
        const val PANORAMIC_360_ELEVATION_MIN = -90
        const val PANORAMIC_360_ELEVATION_MAX = 90
        const val LOUDNESS_PERCENT_MIN = 0
        const val LOUDNESS_PERCENT_MAX = 100
        const val CHANNEL_BALANCE_MIN = -100
        const val CHANNEL_BALANCE_MAX = 100
        const val CROSSFEED_LOW_CUT_MIN_HZ = 50
        const val CROSSFEED_LOW_CUT_MAX_HZ = 1_000
        const val CROSSFEED_HIGH_CUT_MIN_HZ = 500
        const val CROSSFEED_HIGH_CUT_MAX_HZ = 8_000
        const val CROSSFEED_ATTENUATION_MIN_TENTHS_DB = 0
        const val CROSSFEED_ATTENUATION_MAX_TENTHS_DB = 150
        const val MONO_BASS_CROSSOVER_MIN_HZ = 60
        const val MONO_BASS_CROSSOVER_MAX_HZ = 300
        const val MONO_BASS_AMOUNT_MIN = 0
        const val MONO_BASS_AMOUNT_MAX = 100
        const val SPEAKER_OUTPUT_MODE_ELASTICITY = 0
        const val SPEAKER_OUTPUT_MODE_POWERFUL = 1
        const val SPEAKER_OUTPUT_MODE_WIDE = 2
        const val SPEAKER_OUTPUT_STRENGTH_MIN = 0
        const val SPEAKER_OUTPUT_STRENGTH_MAX = 100
        const val DYNAMIC_EQ_PERCENT_MIN = 0
        const val DYNAMIC_EQ_PERCENT_MAX = 100
        const val DE_ESSER_FREQUENCY_MIN_HZ = 4_000
        const val DE_ESSER_FREQUENCY_MAX_HZ = 10_000
        const val MOOG_LADDER_MODE_LOW_PASS_24 = 0
        const val MOOG_LADDER_MODE_LOW_PASS_12 = 1
        const val MOOG_LADDER_MODE_HIGH_PASS_24 = 2
        const val MOOG_LADDER_MODE_BAND_PASS_12 = 3
        const val MOOG_LADDER_MODE_NOTCH = 4
        const val MOOG_LADDER_CUTOFF_MIN_HZ = 20
        const val MOOG_LADDER_CUTOFF_MAX_HZ = 20_000
        const val MOOG_LADDER_RESONANCE_MIN = 0
        const val MOOG_LADDER_RESONANCE_MAX = 100
        const val MOOG_LADDER_DRIVE_MIN_DB = 0
        const val MOOG_LADDER_DRIVE_MAX_DB = 18
        const val MOOG_LADDER_MIX_MIN = 0
        const val MOOG_LADDER_MIX_MAX = 100
        const val REVERB_PRESET_OFF = 0
        const val REVERB_PRESET_STUDIO = 10
        const val REVERB_PRESET_SMALL_ROOM = 1
        const val REVERB_PRESET_MEDIUM_ROOM = 2
        const val REVERB_PRESET_LARGE_ROOM = 3
        const val REVERB_PRESET_HALL = 4
        const val REVERB_PRESET_CHURCH = 5
        const val REVERB_PRESET_PLATE = 6
    }
}

/**
 * Static capabilities of the device's audio-effect hardware for the bound session, published so
 * the settings UI can render the right number of bands at the right frequencies without itself
 * touching AudioEffect APIs.
 */
data class EqualizerCapabilities(
    val supported: Boolean,
    val bandCount: Int,
    val centerFreqsHz: List<Int>,
    val displayBandCount: Int = FIXED_EQ_BAND_COUNT,
    val displayCenterFreqsHz: List<Int> = FIXED_EQ_CENTER_FREQS_HZ,
    val minLevelMb: Int,
    val maxLevelMb: Int,
    val presetNames: List<String>,
    /** presetIndex -> per-band levels in millibels, used by the UI when a preset is selected. */
    val presetBandLevelsMb: List<List<Int>>,
    val bassBoostSupported: Boolean,
    val virtualizerSupported: Boolean,
    val reverbSupported: Boolean,
    /** Whether the device exposes a *variable* strength control (vs. plain on/off). */
    val bassBoostStrengthAdjustable: Boolean,
    val virtualizerStrengthAdjustable: Boolean
) {
    companion object {
        val Fixed = EqualizerCapabilities(
            supported = true,
            bandCount = FIXED_EQ_BAND_COUNT,
            centerFreqsHz = FIXED_EQ_CENTER_FREQS_HZ,
            displayBandCount = FIXED_EQ_BAND_COUNT,
            displayCenterFreqsHz = FIXED_EQ_CENTER_FREQS_HZ,
            minLevelMb = -1500,
            maxLevelMb = 1500,
            presetNames = emptyList(),
            presetBandLevelsMb = FIXED_EQ_PRESET_BAND_LEVELS_MB,
            bassBoostSupported = false,
            virtualizerSupported = false,
            reverbSupported = true,
            bassBoostStrengthAdjustable = false,
            virtualizerStrengthAdjustable = false
        )

        val Unsupported = EqualizerCapabilities(
            supported = false,
            bandCount = 0,
            centerFreqsHz = emptyList(),
            displayBandCount = FIXED_EQ_BAND_COUNT,
            displayCenterFreqsHz = FIXED_EQ_CENTER_FREQS_HZ,
            minLevelMb = -1500,
            maxLevelMb = 1500,
            presetNames = emptyList(),
            presetBandLevelsMb = emptyList(),
            bassBoostSupported = false,
            virtualizerSupported = false,
            reverbSupported = false,
            bassBoostStrengthAdjustable = false,
            virtualizerStrengthAdjustable = false
        )
    }
}

/** Process-global publisher for the bound session's effect capabilities (like [PlaybackAudioSession]). */
object AudioEffectState {
    private val _capabilities = MutableStateFlow<EqualizerCapabilities?>(null)
    val capabilities: StateFlow<EqualizerCapabilities?> = _capabilities.asStateFlow()

    internal fun publish(capabilities: EqualizerCapabilities?) {
        _capabilities.value = capabilities
    }
}

const val FIXED_EQ_BAND_COUNT = 10
val FIXED_EQ_CENTER_FREQS_HZ = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

/**
 * Built-in graphic-EQ presets, ordered to match the preset-name string list in the settings UI.
 * Each row is the per-band gain in dB for the 10 [FIXED_EQ_CENTER_FREQS_HZ] bands; the UI supplies
 * the localized display names.
 */
private val FIXED_EQ_PRESET_BAND_LEVELS_DB: List<List<Int>> = listOf(
    listOf(5, 4, 3, 1, -1, -1, 1, 3, 4, 5),      // Rock
    listOf(-1, 0, 2, 4, 4, 3, 1, 0, -1, -1),     // Pop
    listOf(4, 3, 1, 2, -1, -1, 0, 1, 3, 4),      // Jazz
    listOf(5, 4, 3, 2, -1, -1, 0, 2, 3, 4),      // Classical
    listOf(6, 5, 3, 0, 0, -1, -2, -1, 2, 4),     // Dance
    listOf(5, 4, 1, 0, -2, 2, 1, 1, 4, 5),       // Electronic
    listOf(6, 5, 3, 2, -1, -1, 1, 0, 2, 3),      // Hip-Hop
    listOf(-2, -1, 0, 2, 4, 4, 3, 2, 0, -1),     // Vocal
    listOf(4, 4, 2, 1, 2, 2, 3, 3, 2, 1),        // Acoustic
    listOf(7, 6, 5, 3, 1, 0, 0, 0, 0, 0),        // Bass Boost
    listOf(0, 0, 0, 0, 0, 1, 3, 5, 6, 7)         // Treble Boost
)

/** Preset band levels expressed in millibels (1 dB = 100 mB) as consumed by the settings UI. */
val FIXED_EQ_PRESET_BAND_LEVELS_MB: List<List<Int>> =
    FIXED_EQ_PRESET_BAND_LEVELS_DB.map { levels -> levels.map { it * 100 } }

/**
 * Owns the legacy system audio effects ([BassBoost], [Virtualizer]) for playback.
 * The equalizer is now applied in software by [EqualizerAudioProcessor] and does not need a
 * system [android.media.audiofx.Equalizer].
 *
 * Created and driven by [PlaybackService] so effects stay alive for the whole playback
 * lifetime (independent of any UI). The settings UI communicates only through persisted
 * [AudioEffectSettings] and reads [AudioEffectState] for rendering.
 */
class AudioEffectController {

    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var boundSessionId: Int = -1
    private var lastSettings: AudioEffectSettings = AudioEffectSettings()

    /** Attach effects to [sessionId], publish its capabilities, and re-apply the last settings. */
    fun bind(sessionId: Int) {
        if (sessionId <= 0) return
        if (sessionId == boundSessionId && bassBoost != null) return
        release()
        boundSessionId = sessionId

        bassBoost = runCatching { BassBoost(0, sessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, sessionId) }.getOrNull()

        AudioEffectState.publish(captureCapabilities())
        apply(lastSettings)
    }

    /** Persist [settings] as the active configuration and push it onto the live effects. */
    fun apply(settings: AudioEffectSettings) {
        lastSettings = settings
        applyBassBoost(settings)
        applyVirtualizer(settings)
    }

    private fun applyBassBoost(settings: AudioEffectSettings) {
        val effect = bassBoost ?: return
        runCatching {
            effect.enabled = settings.bassBoostEnabled
            if (settings.bassBoostEnabled && effect.strengthSupported) {
                effect.setStrength(settings.bassBoostStrength.coerceIn(0, AudioEffectSettings.STRENGTH_MAX).toShort())
            }
        }
    }

    private fun applyVirtualizer(settings: AudioEffectSettings) {
        val effect = virtualizer ?: return
        runCatching {
            effect.enabled = settings.virtualizerEnabled
            if (settings.virtualizerEnabled && effect.strengthSupported) {
                effect.setStrength(settings.virtualizerStrength.coerceIn(0, AudioEffectSettings.STRENGTH_MAX).toShort())
            }
        }
    }

    private fun captureCapabilities(): EqualizerCapabilities {
        return EqualizerCapabilities.Fixed.copy(
            bassBoostSupported = bassBoost != null,
            virtualizerSupported = virtualizer != null,
            reverbSupported = true,
            bassBoostStrengthAdjustable = runCatching { bassBoost?.strengthSupported == true }.getOrDefault(false),
            virtualizerStrengthAdjustable = runCatching { virtualizer?.strengthSupported == true }.getOrDefault(false)
        )
    }

    fun release() {
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        bassBoost = null
        virtualizer = null
        boundSessionId = -1
    }
}
