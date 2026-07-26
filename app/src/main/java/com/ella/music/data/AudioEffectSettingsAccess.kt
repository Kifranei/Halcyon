package com.ella.music.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.ella.music.data.SettingsManager.Companion.KEY_BASS_BOOST_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_BASS_BOOST_STRENGTH
import com.ella.music.data.SettingsManager.Companion.KEY_CHANNEL_BALANCE
import com.ella.music.data.SettingsManager.Companion.KEY_COMP_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_COMP_MAKEUP_DB
import com.ella.music.data.SettingsManager.Companion.KEY_COMP_RATIO
import com.ella.music.data.SettingsManager.Companion.KEY_COMP_THRESHOLD_DB
import com.ella.music.data.SettingsManager.Companion.KEY_CROSSFEED_ATTENUATION_TENTHS_DB
import com.ella.music.data.SettingsManager.Companion.KEY_CROSSFEED_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_CROSSFEED_HIGH_CUT_HZ
import com.ella.music.data.SettingsManager.Companion.KEY_CROSSFEED_LOW_CUT_HZ
import com.ella.music.data.SettingsManager.Companion.KEY_DE_ESSER_AMOUNT
import com.ella.music.data.SettingsManager.Companion.KEY_DE_ESSER_FREQUENCY_HZ
import com.ella.music.data.SettingsManager.Companion.KEY_DYNAMIC_EQ_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_DYNAMIC_EQ_INTENSITY
import com.ella.music.data.SettingsManager.Companion.KEY_EQ_BANDS
import com.ella.music.data.SettingsManager.Companion.KEY_EQ_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_EQ_PRESET
import com.ella.music.data.SettingsManager.Companion.KEY_EQ_Q
import com.ella.music.data.SettingsManager.Companion.KEY_LOUDNESS_BALANCE_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_LOUDNESS_PERCENT
import com.ella.music.data.SettingsManager.Companion.KEY_MONO_BASS_AMOUNT
import com.ella.music.data.SettingsManager.Companion.KEY_MONO_BASS_CROSSOVER_HZ
import com.ella.music.data.SettingsManager.Companion.KEY_MONO_BASS_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_MOOG_LADDER_CUTOFF_HZ
import com.ella.music.data.SettingsManager.Companion.KEY_MOOG_LADDER_DRIVE_DB
import com.ella.music.data.SettingsManager.Companion.KEY_MOOG_LADDER_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_MOOG_LADDER_MIX
import com.ella.music.data.SettingsManager.Companion.KEY_MOOG_LADDER_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_MOOG_LADDER_RESONANCE
import com.ella.music.data.SettingsManager.Companion.KEY_PANORAMIC_360_AZIMUTH_DEGREES
import com.ella.music.data.SettingsManager.Companion.KEY_PANORAMIC_360_ELEVATION_DEGREES
import com.ella.music.data.SettingsManager.Companion.KEY_PANORAMIC_360_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_PANORAMIC_360_INTENSITY
import com.ella.music.data.SettingsManager.Companion.KEY_PEAK_LIMITER_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_PLATFORM_SPATIAL_AUDIO_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_REVERB_PRESET
import com.ella.music.data.SettingsManager.Companion.KEY_SPEAKER_OUTPUT_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_SPEAKER_OUTPUT_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_SPEAKER_OUTPUT_STRENGTH
import com.ella.music.data.SettingsManager.Companion.KEY_STEREO_WIDTH
import com.ella.music.data.SettingsManager.Companion.KEY_SURROUND_360_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_SURROUND_360_INTENSITY
import com.ella.music.data.SettingsManager.Companion.KEY_SURROUND_360_ROTATION_SPEED
import com.ella.music.data.SettingsManager.Companion.KEY_TONE_BASS_DB
import com.ella.music.data.SettingsManager.Companion.KEY_TONE_TREBLE_DB
import com.ella.music.data.SettingsManager.Companion.KEY_VIRTUALIZER_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_VIRTUALIZER_STRENGTH
import com.ella.music.player.AudioEffectSettings
import com.ella.music.player.FIXED_EQ_BAND_COUNT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * In-app DSP chain: EQ, bass boost, virtualizer, reverb, tone, compressor, stereo/360 spatial
 * effects, crossfeed, mono bass, speaker output, dynamic EQ, Moog ladder and peak limiter,
 * plus the combined [com.ella.music.player.AudioEffectSettings] snapshot for the PlaybackService.
 *
 * Extracted verbatim from [SettingsManager], which implements this interface via class
 * delegation so every call site keeps using settingsManager.<member> unchanged. All flow
 * properties MUST stay eagerly-initialised stored properties (never computed get() =):
 * Compose collectAsState keys on the flow instance, and a fresh instance per access would
 * restart collection on every recomposition.
 */
interface AudioEffectSettingsAccess {
    val eqEnabled: Flow<Boolean>
    val eqPreset: Flow<Int>
    val eqBandLevelsMb: Flow<List<Int>>
    val bassBoostEnabled: Flow<Boolean>
    val bassBoostStrength: Flow<Int>
    val virtualizerEnabled: Flow<Boolean>
    val virtualizerStrength: Flow<Int>
    val reverbPreset: Flow<Int>
    val eqQ: Flow<Int>
    val toneBassDb: Flow<Int>
    val toneTrebleDb: Flow<Int>
    val compressorEnabled: Flow<Boolean>
    val compressorThresholdDb: Flow<Int>
    val compressorRatio: Flow<Int>
    val compressorMakeupDb: Flow<Int>
    val stereoWidth: Flow<Int>
    val surround360Enabled: Flow<Boolean>
    val surround360Intensity: Flow<Int>
    val surround360RotationSpeed: Flow<Int>
    val panoramic360Enabled: Flow<Boolean>
    val panoramic360Intensity: Flow<Int>
    val panoramic360AzimuthDegrees: Flow<Int>
    val panoramic360ElevationDegrees: Flow<Int>
    val loudnessBalanceEnabled: Flow<Boolean>
    val loudnessPercent: Flow<Int>
    val channelBalance: Flow<Int>
    val crossfeedEnabled: Flow<Boolean>
    val crossfeedLowCutHz: Flow<Int>
    val crossfeedHighCutHz: Flow<Int>
    val crossfeedAttenuationTenthsDb: Flow<Int>
    val monoBassEnabled: Flow<Boolean>
    val monoBassCrossoverHz: Flow<Int>
    val monoBassAmount: Flow<Int>
    val speakerOutputEnabled: Flow<Boolean>
    val speakerOutputMode: Flow<Int>
    val speakerOutputStrength: Flow<Int>
    val dynamicEqEnabled: Flow<Boolean>
    val dynamicEqIntensity: Flow<Int>
    val deEsserAmount: Flow<Int>
    val deEsserFrequencyHz: Flow<Int>
    val moogLadderEnabled: Flow<Boolean>
    val moogLadderMode: Flow<Int>
    val moogLadderCutoffHz: Flow<Int>
    val moogLadderResonance: Flow<Int>
    val moogLadderDriveDb: Flow<Int>
    val moogLadderMix: Flow<Int>
    val peakLimiterEnabled: Flow<Boolean>
    val platformSpatialAudioEnabled: Flow<Boolean>
    val audioEffectSettings: Flow<AudioEffectSettings>
    suspend fun setEqEnabled(enabled: Boolean)
    suspend fun setEqPreset(preset: Int)
    suspend fun setEqPresetWithBands(preset: Int, bandLevelsMb: List<Int>)
    suspend fun setEqBandLevelsMb(bandLevelsMb: List<Int>)
    suspend fun setBassBoostEnabled(enabled: Boolean)
    suspend fun setBassBoostStrength(strength: Int)
    suspend fun setVirtualizerEnabled(enabled: Boolean)
    suspend fun setVirtualizerStrength(strength: Int)
    suspend fun setReverbPreset(preset: Int)
    suspend fun setEqQ(q: Int)
    suspend fun setToneBassDb(db: Int)
    suspend fun setToneTrebleDb(db: Int)
    suspend fun setCompressorEnabled(enabled: Boolean)
    suspend fun setCompressorThresholdDb(db: Int)
    suspend fun setCompressorRatio(ratio: Int)
    suspend fun setCompressorMakeupDb(db: Int)
    suspend fun setStereoWidth(width: Int)
    suspend fun setSurround360Enabled(enabled: Boolean)
    suspend fun setSurround360Intensity(intensity: Int)
    suspend fun setSurround360RotationSpeed(speed: Int)
    suspend fun setPanoramic360Enabled(enabled: Boolean)
    suspend fun setPanoramic360Intensity(intensity: Int)
    suspend fun setPanoramic360AzimuthDegrees(degrees: Int)
    suspend fun setPanoramic360ElevationDegrees(degrees: Int)
    suspend fun setLoudnessBalanceEnabled(enabled: Boolean)
    suspend fun setLoudnessPercent(percent: Int)
    suspend fun setChannelBalance(balance: Int)
    suspend fun setCrossfeedEnabled(enabled: Boolean)
    suspend fun setCrossfeedLowCutHz(hz: Int)
    suspend fun setCrossfeedHighCutHz(hz: Int)
    suspend fun setCrossfeedAttenuationTenthsDb(tenthsDb: Int)
    suspend fun setMonoBassEnabled(enabled: Boolean)
    suspend fun setMonoBassCrossoverHz(hz: Int)
    suspend fun setMonoBassAmount(amount: Int)
    suspend fun setSpeakerOutputEnabled(enabled: Boolean)
    suspend fun setSpeakerOutputMode(mode: Int)
    suspend fun setSpeakerOutputStrength(strength: Int)
    suspend fun setDynamicEqEnabled(enabled: Boolean)
    suspend fun setDynamicEqIntensity(intensity: Int)
    suspend fun setDeEsserAmount(amount: Int)
    suspend fun setDeEsserFrequencyHz(frequencyHz: Int)
    suspend fun setMoogLadderEnabled(enabled: Boolean)
    suspend fun setMoogLadderMode(mode: Int)
    suspend fun setMoogLadderCutoffHz(cutoffHz: Int)
    suspend fun setMoogLadderResonance(resonance: Int)
    suspend fun setMoogLadderDriveDb(driveDb: Int)
    suspend fun setMoogLadderMix(mix: Int)
    suspend fun setPeakLimiterEnabled(enabled: Boolean)
    suspend fun setPlatformSpatialAudioEnabled(enabled: Boolean)
}

internal class AudioEffectSettingsAccessImpl(private val context: Context) : AudioEffectSettingsAccess {

    override val eqEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_EQ_ENABLED] ?: false }
    override val eqPreset: Flow<Int> =
        context.dataStore.data.map { it[KEY_EQ_PRESET] ?: AudioEffectSettings.PRESET_CUSTOM }
    override val eqBandLevelsMb: Flow<List<Int>> =
        context.dataStore.data.map { parseEqBands(it[KEY_EQ_BANDS]) }
    override val bassBoostEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BASS_BOOST_ENABLED] ?: false }
    override val bassBoostStrength: Flow<Int> =
        context.dataStore.data.map { it[KEY_BASS_BOOST_STRENGTH] ?: 0 }
    override val virtualizerEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_VIRTUALIZER_ENABLED] ?: false }
    override val virtualizerStrength: Flow<Int> =
        context.dataStore.data.map { it[KEY_VIRTUALIZER_STRENGTH] ?: 0 }
    override val reverbPreset: Flow<Int> =
        context.dataStore.data.map { normalizeReverbPreset(it[KEY_REVERB_PRESET] ?: AudioEffectSettings.REVERB_PRESET_OFF) }
    override val eqQ: Flow<Int> =
        context.dataStore.data.map { (it[KEY_EQ_Q] ?: AudioEffectSettings.EQ_Q_DEFAULT).coerceIn(AudioEffectSettings.EQ_Q_MIN, AudioEffectSettings.EQ_Q_MAX) }
    override val toneBassDb: Flow<Int> =
        context.dataStore.data.map { (it[KEY_TONE_BASS_DB] ?: 0).coerceIn(AudioEffectSettings.TONE_GAIN_MIN_DB, AudioEffectSettings.TONE_GAIN_MAX_DB) }
    override val toneTrebleDb: Flow<Int> =
        context.dataStore.data.map { (it[KEY_TONE_TREBLE_DB] ?: 0).coerceIn(AudioEffectSettings.TONE_GAIN_MIN_DB, AudioEffectSettings.TONE_GAIN_MAX_DB) }
    override val compressorEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_COMP_ENABLED] ?: false }
    override val compressorThresholdDb: Flow<Int> =
        context.dataStore.data.map { (it[KEY_COMP_THRESHOLD_DB] ?: -18).coerceIn(AudioEffectSettings.COMP_THRESHOLD_MIN_DB, AudioEffectSettings.COMP_THRESHOLD_MAX_DB) }
    override val compressorRatio: Flow<Int> =
        context.dataStore.data.map { (it[KEY_COMP_RATIO] ?: 2).coerceIn(AudioEffectSettings.COMP_RATIO_MIN, AudioEffectSettings.COMP_RATIO_MAX) }
    override val compressorMakeupDb: Flow<Int> =
        context.dataStore.data.map { (it[KEY_COMP_MAKEUP_DB] ?: 0).coerceIn(AudioEffectSettings.COMP_MAKEUP_MIN_DB, AudioEffectSettings.COMP_MAKEUP_MAX_DB) }
    override val stereoWidth: Flow<Int> =
        context.dataStore.data.map { (it[KEY_STEREO_WIDTH] ?: 100).coerceIn(AudioEffectSettings.STEREO_WIDTH_MIN, AudioEffectSettings.STEREO_WIDTH_MAX) }
    override val surround360Enabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SURROUND_360_ENABLED] ?: false }
    override val surround360Intensity: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_SURROUND_360_INTENSITY] ?: 50).coerceIn(
                AudioEffectSettings.SURROUND_360_INTENSITY_MIN,
                AudioEffectSettings.SURROUND_360_INTENSITY_MAX
            )
        }
    override val surround360RotationSpeed: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_SURROUND_360_ROTATION_SPEED] ?: 30).coerceIn(
                AudioEffectSettings.SURROUND_360_ROTATION_MIN,
                AudioEffectSettings.SURROUND_360_ROTATION_MAX
            )
        }
    override val panoramic360Enabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PANORAMIC_360_ENABLED] ?: false }
    override val panoramic360Intensity: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_PANORAMIC_360_INTENSITY] ?: 50).coerceIn(
                AudioEffectSettings.PANORAMIC_360_INTENSITY_MIN,
                AudioEffectSettings.PANORAMIC_360_INTENSITY_MAX
            )
        }
    override val panoramic360AzimuthDegrees: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_PANORAMIC_360_AZIMUTH_DEGREES] ?: 0).coerceIn(
                AudioEffectSettings.PANORAMIC_360_AZIMUTH_MIN,
                AudioEffectSettings.PANORAMIC_360_AZIMUTH_MAX
            )
        }
    override val panoramic360ElevationDegrees: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_PANORAMIC_360_ELEVATION_DEGREES] ?: 0).coerceIn(
                AudioEffectSettings.PANORAMIC_360_ELEVATION_MIN,
                AudioEffectSettings.PANORAMIC_360_ELEVATION_MAX
            )
        }
    override val loudnessBalanceEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_LOUDNESS_BALANCE_ENABLED] ?: false }
    override val loudnessPercent: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_LOUDNESS_PERCENT] ?: 35).coerceIn(
                AudioEffectSettings.LOUDNESS_PERCENT_MIN,
                AudioEffectSettings.LOUDNESS_PERCENT_MAX
            )
        }
    override val channelBalance: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_CHANNEL_BALANCE] ?: 0).coerceIn(
                AudioEffectSettings.CHANNEL_BALANCE_MIN,
                AudioEffectSettings.CHANNEL_BALANCE_MAX
            )
        }
    override val crossfeedEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_CROSSFEED_ENABLED] ?: false }
    override val crossfeedLowCutHz: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_CROSSFEED_LOW_CUT_HZ] ?: 300).coerceIn(
                AudioEffectSettings.CROSSFEED_LOW_CUT_MIN_HZ,
                AudioEffectSettings.CROSSFEED_LOW_CUT_MAX_HZ
            )
        }
    override val crossfeedHighCutHz: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_CROSSFEED_HIGH_CUT_HZ] ?: 2_000).coerceIn(
                AudioEffectSettings.CROSSFEED_HIGH_CUT_MIN_HZ,
                AudioEffectSettings.CROSSFEED_HIGH_CUT_MAX_HZ
            )
        }
    override val crossfeedAttenuationTenthsDb: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_CROSSFEED_ATTENUATION_TENTHS_DB] ?: 60).coerceIn(
                AudioEffectSettings.CROSSFEED_ATTENUATION_MIN_TENTHS_DB,
                AudioEffectSettings.CROSSFEED_ATTENUATION_MAX_TENTHS_DB
            )
        }
    override val monoBassEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MONO_BASS_ENABLED] ?: false }
    override val monoBassCrossoverHz: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_MONO_BASS_CROSSOVER_HZ] ?: 120).coerceIn(
                AudioEffectSettings.MONO_BASS_CROSSOVER_MIN_HZ,
                AudioEffectSettings.MONO_BASS_CROSSOVER_MAX_HZ
            )
        }
    override val monoBassAmount: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_MONO_BASS_AMOUNT] ?: 100).coerceIn(
                AudioEffectSettings.MONO_BASS_AMOUNT_MIN,
                AudioEffectSettings.MONO_BASS_AMOUNT_MAX
            )
        }
    override val speakerOutputEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SPEAKER_OUTPUT_ENABLED] ?: false }
    override val speakerOutputMode: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_SPEAKER_OUTPUT_MODE] ?: AudioEffectSettings.SPEAKER_OUTPUT_MODE_ELASTICITY).coerceIn(
                AudioEffectSettings.SPEAKER_OUTPUT_MODE_ELASTICITY,
                AudioEffectSettings.SPEAKER_OUTPUT_MODE_WIDE
            )
        }
    override val speakerOutputStrength: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_SPEAKER_OUTPUT_STRENGTH] ?: 82).coerceIn(
                AudioEffectSettings.SPEAKER_OUTPUT_STRENGTH_MIN,
                AudioEffectSettings.SPEAKER_OUTPUT_STRENGTH_MAX
            )
        }
    override val dynamicEqEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_DYNAMIC_EQ_ENABLED] ?: false }
    override val dynamicEqIntensity: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_DYNAMIC_EQ_INTENSITY] ?: 50).coerceIn(
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MIN,
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MAX
            )
        }
    override val deEsserAmount: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_DE_ESSER_AMOUNT] ?: 45).coerceIn(
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MIN,
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MAX
            )
        }
    override val deEsserFrequencyHz: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_DE_ESSER_FREQUENCY_HZ] ?: 6_500).coerceIn(
                AudioEffectSettings.DE_ESSER_FREQUENCY_MIN_HZ,
                AudioEffectSettings.DE_ESSER_FREQUENCY_MAX_HZ
            )
        }
    override val moogLadderEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MOOG_LADDER_ENABLED] ?: false }
    override val moogLadderMode: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_MOOG_LADDER_MODE] ?: AudioEffectSettings.MOOG_LADDER_MODE_LOW_PASS_24).coerceIn(
                AudioEffectSettings.MOOG_LADDER_MODE_LOW_PASS_24,
                AudioEffectSettings.MOOG_LADDER_MODE_NOTCH
            )
        }
    override val moogLadderCutoffHz: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_MOOG_LADDER_CUTOFF_HZ] ?: 12_000).coerceIn(
                AudioEffectSettings.MOOG_LADDER_CUTOFF_MIN_HZ,
                AudioEffectSettings.MOOG_LADDER_CUTOFF_MAX_HZ
            )
        }
    override val moogLadderResonance: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_MOOG_LADDER_RESONANCE] ?: 20).coerceIn(
                AudioEffectSettings.MOOG_LADDER_RESONANCE_MIN,
                AudioEffectSettings.MOOG_LADDER_RESONANCE_MAX
            )
        }
    override val moogLadderDriveDb: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_MOOG_LADDER_DRIVE_DB] ?: 0).coerceIn(
                AudioEffectSettings.MOOG_LADDER_DRIVE_MIN_DB,
                AudioEffectSettings.MOOG_LADDER_DRIVE_MAX_DB
            )
        }
    override val moogLadderMix: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_MOOG_LADDER_MIX] ?: 100).coerceIn(
                AudioEffectSettings.MOOG_LADDER_MIX_MIN,
                AudioEffectSettings.MOOG_LADDER_MIX_MAX
            )
        }
    override val peakLimiterEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PEAK_LIMITER_ENABLED] ?: true }
    override val platformSpatialAudioEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLATFORM_SPATIAL_AUDIO_ENABLED] ?: false }
    private data class CustomDspSettings(
        val eqQ: Int,
        val bassDb: Int,
        val trebleDb: Int,
        val compEnabled: Boolean,
        val compThresholdDb: Int,
        val compRatio: Int,
        val compMakeupDb: Int,
        val stereoWidth: Int,
        val surround360Enabled: Boolean,
        val surround360Intensity: Int,
        val surround360RotationSpeed: Int,
        val panoramic360Enabled: Boolean,
        val panoramic360Intensity: Int,
        val panoramic360AzimuthDegrees: Int,
        val panoramic360ElevationDegrees: Int,
        val loudnessBalanceEnabled: Boolean,
        val loudnessPercent: Int,
        val channelBalance: Int,
        val crossfeedEnabled: Boolean,
        val crossfeedLowCutHz: Int,
        val crossfeedHighCutHz: Int,
        val crossfeedAttenuationTenthsDb: Int,
        val monoBassEnabled: Boolean,
        val monoBassCrossoverHz: Int,
        val monoBassAmount: Int,
        val speakerOutputEnabled: Boolean,
        val speakerOutputMode: Int,
        val speakerOutputStrength: Int,
        val dynamicEqEnabled: Boolean,
        val dynamicEqIntensity: Int,
        val deEsserAmount: Int,
        val deEsserFrequencyHz: Int,
        val moogLadderEnabled: Boolean,
        val moogLadderMode: Int,
        val moogLadderCutoffHz: Int,
        val moogLadderResonance: Int,
        val moogLadderDriveDb: Int,
        val moogLadderMix: Int,
        val peakLimiterEnabled: Boolean
    )
    private data class SpatialEnhancementSettings(
        val loudnessBalanceEnabled: Boolean,
        val loudnessPercent: Int,
        val channelBalance: Int,
        val crossfeedEnabled: Boolean,
        val crossfeedLowCutHz: Int,
        val crossfeedHighCutHz: Int,
        val crossfeedAttenuationTenthsDb: Int,
        val monoBassEnabled: Boolean,
        val monoBassCrossoverHz: Int,
        val monoBassAmount: Int,
        val speakerOutputEnabled: Boolean,
        val speakerOutputMode: Int,
        val speakerOutputStrength: Int,
        val dynamicEqEnabled: Boolean,
        val dynamicEqIntensity: Int,
        val deEsserAmount: Int,
        val deEsserFrequencyHz: Int,
        val moogLadderEnabled: Boolean,
        val moogLadderMode: Int,
        val moogLadderCutoffHz: Int,
        val moogLadderResonance: Int,
        val moogLadderDriveDb: Int,
        val moogLadderMix: Int,
        val peakLimiterEnabled: Boolean
    )
    private val spatialEnhancements: Flow<SpatialEnhancementSettings> = combine(
        combine(loudnessBalanceEnabled, loudnessPercent, channelBalance) { enabled, loudness, balance ->
            Triple(enabled, loudness, balance)
        },
        combine(crossfeedEnabled, crossfeedLowCutHz, crossfeedHighCutHz, crossfeedAttenuationTenthsDb) { enabled, low, high, attenuation ->
            listOf(if (enabled) 1 else 0, low, high, attenuation)
        },
        combine(monoBassEnabled, monoBassCrossoverHz, monoBassAmount) { enabled, crossover, amount ->
            Triple(enabled, crossover, amount)
        },
        combine(speakerOutputEnabled, speakerOutputMode, speakerOutputStrength) { enabled, mode, strength ->
            Triple(enabled, mode, strength)
        },
        combine(
            combine(dynamicEqEnabled, dynamicEqIntensity, deEsserAmount, deEsserFrequencyHz) { enabled, intensity, deEsser, frequency ->
                listOf(if (enabled) 1 else 0, intensity, deEsser, frequency)
            },
            combine(moogLadderEnabled, moogLadderMode, moogLadderCutoffHz) { enabled, mode, cutoff ->
                listOf(if (enabled) 1 else 0, mode, cutoff)
            },
            combine(moogLadderResonance, moogLadderDriveDb, moogLadderMix) { resonance, drive, mix ->
                listOf(resonance, drive, mix)
            },
            peakLimiterEnabled
        ) { dynamicEq, moogPrimary, moogSecondary, limiter ->
            listOf(
                dynamicEq[0], dynamicEq[1], dynamicEq[2], dynamicEq[3],
                moogPrimary[0], moogPrimary[1], moogPrimary[2], moogSecondary[0], moogSecondary[1], moogSecondary[2],
                if (limiter) 1 else 0
            )
        }
    ) { loudness, crossfeed, monoBass, speaker, dynamicEq ->
        SpatialEnhancementSettings(
            loudnessBalanceEnabled = loudness.first,
            loudnessPercent = loudness.second,
            channelBalance = loudness.third,
            crossfeedEnabled = crossfeed[0] == 1,
            crossfeedLowCutHz = crossfeed[1],
            crossfeedHighCutHz = crossfeed[2],
            crossfeedAttenuationTenthsDb = crossfeed[3],
            monoBassEnabled = monoBass.first,
            monoBassCrossoverHz = monoBass.second,
            monoBassAmount = monoBass.third,
            speakerOutputEnabled = speaker.first,
            speakerOutputMode = speaker.second,
            speakerOutputStrength = speaker.third,
            dynamicEqEnabled = dynamicEq[0] == 1,
            dynamicEqIntensity = dynamicEq[1],
            deEsserAmount = dynamicEq[2],
            deEsserFrequencyHz = dynamicEq[3],
            moogLadderEnabled = dynamicEq[4] == 1,
            moogLadderMode = dynamicEq[5],
            moogLadderCutoffHz = dynamicEq[6],
            moogLadderResonance = dynamicEq[7],
            moogLadderDriveDb = dynamicEq[8],
            moogLadderMix = dynamicEq[9],
            peakLimiterEnabled = dynamicEq[10] == 1
        )
    }
    private val toneAndDynamics: Flow<CustomDspSettings> = combine(
        combine(eqQ, toneBassDb, toneTrebleDb) { q, bass, treble -> Triple(q, bass, treble) },
        combine(compressorEnabled, compressorThresholdDb, compressorRatio, compressorMakeupDb) { en, th, ra, mk ->
            listOf(if (en) 1 else 0, th, ra, mk)
        },
        stereoWidth,
        combine(
            combine(surround360Enabled, surround360Intensity, surround360RotationSpeed) { enabled, intensity, speed ->
                Triple(enabled, intensity, speed)
            },
            combine(
                panoramic360Enabled,
                panoramic360Intensity,
                panoramic360AzimuthDegrees,
                panoramic360ElevationDegrees
            ) { enabled, intensity, azimuth, elevation ->
                listOf(if (enabled) 1 else 0, intensity, azimuth, elevation)
            }
        ) { surround, panoramic -> surround to panoramic
        },
        spatialEnhancements
    ) { tone, comp, width, spatial, enhancements ->
        val surround = spatial.first
        val panoramic = spatial.second
        CustomDspSettings(
            eqQ = tone.first,
            bassDb = tone.second,
            trebleDb = tone.third,
            compEnabled = comp[0] == 1,
            compThresholdDb = comp[1],
            compRatio = comp[2],
            compMakeupDb = comp[3],
            stereoWidth = width,
            surround360Enabled = surround.first,
            surround360Intensity = surround.second,
            surround360RotationSpeed = surround.third,
            panoramic360Enabled = panoramic[0] == 1,
            panoramic360Intensity = panoramic[1],
            panoramic360AzimuthDegrees = panoramic[2],
            panoramic360ElevationDegrees = panoramic[3],
            loudnessBalanceEnabled = enhancements.loudnessBalanceEnabled,
            loudnessPercent = enhancements.loudnessPercent,
            channelBalance = enhancements.channelBalance,
            crossfeedEnabled = enhancements.crossfeedEnabled,
            crossfeedLowCutHz = enhancements.crossfeedLowCutHz,
            crossfeedHighCutHz = enhancements.crossfeedHighCutHz,
            crossfeedAttenuationTenthsDb = enhancements.crossfeedAttenuationTenthsDb,
            monoBassEnabled = enhancements.monoBassEnabled,
            monoBassCrossoverHz = enhancements.monoBassCrossoverHz,
            monoBassAmount = enhancements.monoBassAmount,
            speakerOutputEnabled = enhancements.speakerOutputEnabled,
            speakerOutputMode = enhancements.speakerOutputMode,
            speakerOutputStrength = enhancements.speakerOutputStrength,
            dynamicEqEnabled = enhancements.dynamicEqEnabled,
            dynamicEqIntensity = enhancements.dynamicEqIntensity,
            deEsserAmount = enhancements.deEsserAmount,
            deEsserFrequencyHz = enhancements.deEsserFrequencyHz,
            moogLadderEnabled = enhancements.moogLadderEnabled,
            moogLadderMode = enhancements.moogLadderMode,
            moogLadderCutoffHz = enhancements.moogLadderCutoffHz,
            moogLadderResonance = enhancements.moogLadderResonance,
            moogLadderDriveDb = enhancements.moogLadderDriveDb,
            moogLadderMix = enhancements.moogLadderMix,
            peakLimiterEnabled = enhancements.peakLimiterEnabled
        )
    }

    /** Combined audio-effect snapshot consumed by PlaybackService's AudioEffectController. */
    override val audioEffectSettings: Flow<AudioEffectSettings> = combine(
        combine(eqEnabled, eqPreset, eqBandLevelsMb) { enabled, preset, bands ->
            Triple(enabled, preset, bands)
        },
        combine(bassBoostEnabled, bassBoostStrength) { enabled, strength -> enabled to strength },
        combine(virtualizerEnabled, virtualizerStrength) { enabled, strength -> enabled to strength },
        reverbPreset,
        toneAndDynamics
    ) { eq, bass, virt, reverb, dsp ->
        AudioEffectSettings(
            eqEnabled = eq.first,
            eqPreset = eq.second,
            eqBandLevelsMb = eq.third,
            eqQ = dsp.eqQ,
            bassGainDb = dsp.bassDb,
            trebleGainDb = dsp.trebleDb,
            compressorEnabled = dsp.compEnabled,
            compressorThresholdDb = dsp.compThresholdDb,
            compressorRatio = dsp.compRatio,
            compressorMakeupDb = dsp.compMakeupDb,
            stereoWidth = dsp.stereoWidth,
            surround360Enabled = dsp.surround360Enabled,
            surround360Intensity = dsp.surround360Intensity,
            surround360RotationSpeed = dsp.surround360RotationSpeed,
            panoramic360Enabled = dsp.panoramic360Enabled,
            panoramic360Intensity = dsp.panoramic360Intensity,
            panoramic360AzimuthDegrees = dsp.panoramic360AzimuthDegrees,
            panoramic360ElevationDegrees = dsp.panoramic360ElevationDegrees,
            loudnessBalanceEnabled = dsp.loudnessBalanceEnabled,
            loudnessPercent = dsp.loudnessPercent,
            channelBalance = dsp.channelBalance,
            crossfeedEnabled = dsp.crossfeedEnabled,
            crossfeedLowCutHz = dsp.crossfeedLowCutHz,
            crossfeedHighCutHz = dsp.crossfeedHighCutHz,
            crossfeedAttenuationDbTenths = dsp.crossfeedAttenuationTenthsDb,
            monoBassEnabled = dsp.monoBassEnabled,
            monoBassCrossoverHz = dsp.monoBassCrossoverHz,
            monoBassAmount = dsp.monoBassAmount,
            speakerOutputEnabled = dsp.speakerOutputEnabled,
            speakerOutputMode = dsp.speakerOutputMode,
            speakerOutputStrength = dsp.speakerOutputStrength,
            dynamicEqEnabled = dsp.dynamicEqEnabled,
            dynamicEqIntensity = dsp.dynamicEqIntensity,
            deEsserAmount = dsp.deEsserAmount,
            deEsserFrequencyHz = dsp.deEsserFrequencyHz,
            moogLadderEnabled = dsp.moogLadderEnabled,
            moogLadderMode = dsp.moogLadderMode,
            moogLadderCutoffHz = dsp.moogLadderCutoffHz,
            moogLadderResonance = dsp.moogLadderResonance,
            moogLadderDriveDb = dsp.moogLadderDriveDb,
            moogLadderMix = dsp.moogLadderMix,
            peakLimiterEnabled = dsp.peakLimiterEnabled,
            bassBoostEnabled = bass.first,
            bassBoostStrength = bass.second,
            virtualizerEnabled = virt.first,
            virtualizerStrength = virt.second,
            reverbPreset = reverb
        )
    }

    override suspend fun setEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EQ_ENABLED] = enabled }
    }

    override suspend fun setEqPreset(preset: Int) {
        context.dataStore.edit { it[KEY_EQ_PRESET] = preset }
    }

    /** Persist preset selection and the concrete band levels it resolves to in one write. */
    override suspend fun setEqPresetWithBands(preset: Int, bandLevelsMb: List<Int>) {
        context.dataStore.edit {
            it[KEY_EQ_PRESET] = preset
            it[KEY_EQ_BANDS] = bandLevelsMb.normalizedEqBands().joinToString(",")
        }
    }

    override suspend fun setEqBandLevelsMb(bandLevelsMb: List<Int>) {
        context.dataStore.edit {
            it[KEY_EQ_BANDS] = bandLevelsMb.normalizedEqBands().joinToString(",")
            it[KEY_EQ_PRESET] = AudioEffectSettings.PRESET_CUSTOM
        }
    }

    override suspend fun setBassBoostEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BASS_BOOST_ENABLED] = enabled }
    }

    override suspend fun setBassBoostStrength(strength: Int) {
        context.dataStore.edit { it[KEY_BASS_BOOST_STRENGTH] = strength.coerceIn(0, AudioEffectSettings.STRENGTH_MAX) }
    }

    override suspend fun setVirtualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VIRTUALIZER_ENABLED] = enabled }
    }

    override suspend fun setVirtualizerStrength(strength: Int) {
        context.dataStore.edit { it[KEY_VIRTUALIZER_STRENGTH] = strength.coerceIn(0, AudioEffectSettings.STRENGTH_MAX) }
    }

    override suspend fun setReverbPreset(preset: Int) {
        context.dataStore.edit { it[KEY_REVERB_PRESET] = normalizeReverbPreset(preset) }
    }

    override suspend fun setEqQ(q: Int) {
        context.dataStore.edit { it[KEY_EQ_Q] = q.coerceIn(AudioEffectSettings.EQ_Q_MIN, AudioEffectSettings.EQ_Q_MAX) }
    }

    override suspend fun setToneBassDb(db: Int) {
        context.dataStore.edit { it[KEY_TONE_BASS_DB] = db.coerceIn(AudioEffectSettings.TONE_GAIN_MIN_DB, AudioEffectSettings.TONE_GAIN_MAX_DB) }
    }

    override suspend fun setToneTrebleDb(db: Int) {
        context.dataStore.edit { it[KEY_TONE_TREBLE_DB] = db.coerceIn(AudioEffectSettings.TONE_GAIN_MIN_DB, AudioEffectSettings.TONE_GAIN_MAX_DB) }
    }

    override suspend fun setCompressorEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_COMP_ENABLED] = enabled }
    }

    override suspend fun setCompressorThresholdDb(db: Int) {
        context.dataStore.edit { it[KEY_COMP_THRESHOLD_DB] = db.coerceIn(AudioEffectSettings.COMP_THRESHOLD_MIN_DB, AudioEffectSettings.COMP_THRESHOLD_MAX_DB) }
    }

    override suspend fun setCompressorRatio(ratio: Int) {
        context.dataStore.edit { it[KEY_COMP_RATIO] = ratio.coerceIn(AudioEffectSettings.COMP_RATIO_MIN, AudioEffectSettings.COMP_RATIO_MAX) }
    }

    override suspend fun setCompressorMakeupDb(db: Int) {
        context.dataStore.edit { it[KEY_COMP_MAKEUP_DB] = db.coerceIn(AudioEffectSettings.COMP_MAKEUP_MIN_DB, AudioEffectSettings.COMP_MAKEUP_MAX_DB) }
    }

    override suspend fun setStereoWidth(width: Int) {
        context.dataStore.edit { it[KEY_STEREO_WIDTH] = width.coerceIn(AudioEffectSettings.STEREO_WIDTH_MIN, AudioEffectSettings.STEREO_WIDTH_MAX) }
    }

    override suspend fun setSurround360Enabled(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_SURROUND_360_ENABLED] = enabled
            if (enabled) it[KEY_PANORAMIC_360_ENABLED] = false
        }
    }

    override suspend fun setSurround360Intensity(intensity: Int) {
        context.dataStore.edit {
            it[KEY_SURROUND_360_INTENSITY] = intensity.coerceIn(
                AudioEffectSettings.SURROUND_360_INTENSITY_MIN,
                AudioEffectSettings.SURROUND_360_INTENSITY_MAX
            )
        }
    }

    override suspend fun setSurround360RotationSpeed(speed: Int) {
        context.dataStore.edit {
            it[KEY_SURROUND_360_ROTATION_SPEED] = speed.coerceIn(
                AudioEffectSettings.SURROUND_360_ROTATION_MIN,
                AudioEffectSettings.SURROUND_360_ROTATION_MAX
            )
        }
    }

    override suspend fun setPanoramic360Enabled(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_PANORAMIC_360_ENABLED] = enabled
            if (enabled) it[KEY_SURROUND_360_ENABLED] = false
        }
    }

    override suspend fun setPanoramic360Intensity(intensity: Int) {
        context.dataStore.edit {
            it[KEY_PANORAMIC_360_INTENSITY] = intensity.coerceIn(
                AudioEffectSettings.PANORAMIC_360_INTENSITY_MIN,
                AudioEffectSettings.PANORAMIC_360_INTENSITY_MAX
            )
        }
    }

    override suspend fun setPanoramic360AzimuthDegrees(degrees: Int) {
        context.dataStore.edit {
            it[KEY_PANORAMIC_360_AZIMUTH_DEGREES] = degrees.coerceIn(
                AudioEffectSettings.PANORAMIC_360_AZIMUTH_MIN,
                AudioEffectSettings.PANORAMIC_360_AZIMUTH_MAX
            )
        }
    }

    override suspend fun setPanoramic360ElevationDegrees(degrees: Int) {
        context.dataStore.edit {
            it[KEY_PANORAMIC_360_ELEVATION_DEGREES] = degrees.coerceIn(
                AudioEffectSettings.PANORAMIC_360_ELEVATION_MIN,
                AudioEffectSettings.PANORAMIC_360_ELEVATION_MAX
            )
        }
    }

    override suspend fun setLoudnessBalanceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LOUDNESS_BALANCE_ENABLED] = enabled }
    }

    override suspend fun setLoudnessPercent(percent: Int) {
        context.dataStore.edit {
            it[KEY_LOUDNESS_PERCENT] = percent.coerceIn(
                AudioEffectSettings.LOUDNESS_PERCENT_MIN,
                AudioEffectSettings.LOUDNESS_PERCENT_MAX
            )
        }
    }

    override suspend fun setChannelBalance(balance: Int) {
        context.dataStore.edit {
            it[KEY_CHANNEL_BALANCE] = balance.coerceIn(
                AudioEffectSettings.CHANNEL_BALANCE_MIN,
                AudioEffectSettings.CHANNEL_BALANCE_MAX
            )
        }
    }

    override suspend fun setCrossfeedEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_CROSSFEED_ENABLED] = enabled }
    }

    override suspend fun setCrossfeedLowCutHz(hz: Int) {
        context.dataStore.edit {
            it[KEY_CROSSFEED_LOW_CUT_HZ] = hz.coerceIn(
                AudioEffectSettings.CROSSFEED_LOW_CUT_MIN_HZ,
                AudioEffectSettings.CROSSFEED_LOW_CUT_MAX_HZ
            )
        }
    }

    override suspend fun setCrossfeedHighCutHz(hz: Int) {
        context.dataStore.edit {
            it[KEY_CROSSFEED_HIGH_CUT_HZ] = hz.coerceIn(
                AudioEffectSettings.CROSSFEED_HIGH_CUT_MIN_HZ,
                AudioEffectSettings.CROSSFEED_HIGH_CUT_MAX_HZ
            )
        }
    }

    override suspend fun setCrossfeedAttenuationTenthsDb(tenthsDb: Int) {
        context.dataStore.edit {
            it[KEY_CROSSFEED_ATTENUATION_TENTHS_DB] = tenthsDb.coerceIn(
                AudioEffectSettings.CROSSFEED_ATTENUATION_MIN_TENTHS_DB,
                AudioEffectSettings.CROSSFEED_ATTENUATION_MAX_TENTHS_DB
            )
        }
    }

    override suspend fun setMonoBassEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MONO_BASS_ENABLED] = enabled }
    }

    override suspend fun setMonoBassCrossoverHz(hz: Int) {
        context.dataStore.edit {
            it[KEY_MONO_BASS_CROSSOVER_HZ] = hz.coerceIn(
                AudioEffectSettings.MONO_BASS_CROSSOVER_MIN_HZ,
                AudioEffectSettings.MONO_BASS_CROSSOVER_MAX_HZ
            )
        }
    }

    override suspend fun setMonoBassAmount(amount: Int) {
        context.dataStore.edit {
            it[KEY_MONO_BASS_AMOUNT] = amount.coerceIn(
                AudioEffectSettings.MONO_BASS_AMOUNT_MIN,
                AudioEffectSettings.MONO_BASS_AMOUNT_MAX
            )
        }
    }

    override suspend fun setSpeakerOutputEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SPEAKER_OUTPUT_ENABLED] = enabled }
    }

    override suspend fun setSpeakerOutputMode(mode: Int) {
        context.dataStore.edit {
            it[KEY_SPEAKER_OUTPUT_MODE] = mode.coerceIn(
                AudioEffectSettings.SPEAKER_OUTPUT_MODE_ELASTICITY,
                AudioEffectSettings.SPEAKER_OUTPUT_MODE_WIDE
            )
        }
    }

    override suspend fun setSpeakerOutputStrength(strength: Int) {
        context.dataStore.edit {
            it[KEY_SPEAKER_OUTPUT_STRENGTH] = strength.coerceIn(
                AudioEffectSettings.SPEAKER_OUTPUT_STRENGTH_MIN,
                AudioEffectSettings.SPEAKER_OUTPUT_STRENGTH_MAX
            )
        }
    }

    override suspend fun setDynamicEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_EQ_ENABLED] = enabled }
    }

    override suspend fun setDynamicEqIntensity(intensity: Int) {
        context.dataStore.edit {
            it[KEY_DYNAMIC_EQ_INTENSITY] = intensity.coerceIn(
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MIN,
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MAX
            )
        }
    }

    override suspend fun setDeEsserAmount(amount: Int) {
        context.dataStore.edit {
            it[KEY_DE_ESSER_AMOUNT] = amount.coerceIn(
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MIN,
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MAX
            )
        }
    }

    override suspend fun setDeEsserFrequencyHz(frequencyHz: Int) {
        context.dataStore.edit {
            it[KEY_DE_ESSER_FREQUENCY_HZ] = frequencyHz.coerceIn(
                AudioEffectSettings.DE_ESSER_FREQUENCY_MIN_HZ,
                AudioEffectSettings.DE_ESSER_FREQUENCY_MAX_HZ
            )
        }
    }

    override suspend fun setMoogLadderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MOOG_LADDER_ENABLED] = enabled }
    }

    override suspend fun setMoogLadderMode(mode: Int) {
        context.dataStore.edit {
            it[KEY_MOOG_LADDER_MODE] = mode.coerceIn(
                AudioEffectSettings.MOOG_LADDER_MODE_LOW_PASS_24,
                AudioEffectSettings.MOOG_LADDER_MODE_NOTCH
            )
        }
    }

    override suspend fun setMoogLadderCutoffHz(cutoffHz: Int) {
        context.dataStore.edit {
            it[KEY_MOOG_LADDER_CUTOFF_HZ] = cutoffHz.coerceIn(
                AudioEffectSettings.MOOG_LADDER_CUTOFF_MIN_HZ,
                AudioEffectSettings.MOOG_LADDER_CUTOFF_MAX_HZ
            )
        }
    }

    override suspend fun setMoogLadderResonance(resonance: Int) {
        context.dataStore.edit {
            it[KEY_MOOG_LADDER_RESONANCE] = resonance.coerceIn(
                AudioEffectSettings.MOOG_LADDER_RESONANCE_MIN,
                AudioEffectSettings.MOOG_LADDER_RESONANCE_MAX
            )
        }
    }

    override suspend fun setMoogLadderDriveDb(driveDb: Int) {
        context.dataStore.edit {
            it[KEY_MOOG_LADDER_DRIVE_DB] = driveDb.coerceIn(
                AudioEffectSettings.MOOG_LADDER_DRIVE_MIN_DB,
                AudioEffectSettings.MOOG_LADDER_DRIVE_MAX_DB
            )
        }
    }

    override suspend fun setMoogLadderMix(mix: Int) {
        context.dataStore.edit {
            it[KEY_MOOG_LADDER_MIX] = mix.coerceIn(
                AudioEffectSettings.MOOG_LADDER_MIX_MIN,
                AudioEffectSettings.MOOG_LADDER_MIX_MAX
            )
        }
    }

    override suspend fun setPeakLimiterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PEAK_LIMITER_ENABLED] = enabled }
    }

    override suspend fun setPlatformSpatialAudioEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLATFORM_SPATIAL_AUDIO_ENABLED] = enabled }
    }

    private fun parseEqBands(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return List(FIXED_EQ_BAND_COUNT) { 0 }
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }.normalizedEqBands()
    }

    private fun List<Int>.normalizedEqBands(): List<Int> =
        List(FIXED_EQ_BAND_COUNT) { index -> getOrElse(index) { 0 } }

    private fun normalizeReverbPreset(preset: Int): Int =
        when (preset) {
            AudioEffectSettings.REVERB_PRESET_OFF,
            AudioEffectSettings.REVERB_PRESET_STUDIO,
            AudioEffectSettings.REVERB_PRESET_SMALL_ROOM,
            AudioEffectSettings.REVERB_PRESET_MEDIUM_ROOM,
            AudioEffectSettings.REVERB_PRESET_LARGE_ROOM,
            AudioEffectSettings.REVERB_PRESET_HALL,
            AudioEffectSettings.REVERB_PRESET_CHURCH,
            AudioEffectSettings.REVERB_PRESET_PLATE -> preset
            else -> AudioEffectSettings.REVERB_PRESET_OFF
        }
}
