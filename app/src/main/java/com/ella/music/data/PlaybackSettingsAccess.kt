package com.ella.music.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.ella.music.data.SettingsManager.Companion.AUDIO_OUTPUT_BACKEND_AAUDIO
import com.ella.music.data.SettingsManager.Companion.AUDIO_OUTPUT_BACKEND_AUDIOTRACK
import com.ella.music.data.SettingsManager.Companion.AUDIO_OUTPUT_BACKEND_AUTO
import com.ella.music.data.SettingsManager.Companion.AUDIO_OUTPUT_BACKEND_HI_RES
import com.ella.music.data.SettingsManager.Companion.AUDIO_OUTPUT_BACKEND_OPENSLES
import com.ella.music.data.SettingsManager.Companion.AUDIO_OUTPUT_BIT_DEPTH_16
import com.ella.music.data.SettingsManager.Companion.AUDIO_OUTPUT_BIT_DEPTH_24
import com.ella.music.data.SettingsManager.Companion.AUDIO_OUTPUT_BIT_DEPTH_32
import com.ella.music.data.SettingsManager.Companion.AUDIO_OUTPUT_BIT_DEPTH_AUTO
import com.ella.music.data.SettingsManager.Companion.AUDIO_OUTPUT_BIT_DEPTH_FLOAT32
import com.ella.music.data.SettingsManager.Companion.AUDIO_OUTPUT_SAMPLE_RATE_AUTO
import com.ella.music.data.SettingsManager.Companion.AUDIO_OUTPUT_SAMPLE_RATES
import com.ella.music.data.SettingsManager.Companion.PREVIOUS_BUTTON_PREVIOUS
import com.ella.music.data.SettingsManager.Companion.PREVIOUS_BUTTON_REPLAY_CURRENT
import com.ella.music.data.SettingsManager.Companion.REPLAY_GAIN_AUTO
import com.ella.music.data.SettingsManager.Companion.REPLAY_GAIN_OFF
import com.ella.music.data.SettingsManager.Companion.SHUFFLE_MODE_PSEUDO
import com.ella.music.data.SettingsManager.Companion.SHUFFLE_MODE_TRUE_RANDOM
import com.ella.music.data.SettingsManager.Companion.STARTUP_PLAY_OFF
import com.ella.music.data.SettingsManager.Companion.STARTUP_PLAY_RANDOM
import com.ella.music.data.SettingsManager.Companion.STARTUP_PLAY_RESUME
import com.ella.music.data.SettingsManager.Companion.KEY_AUDIO_FOCUS_DISABLED
import com.ella.music.data.SettingsManager.Companion.KEY_AUDIO_OUTPUT_BACKEND
import com.ella.music.data.SettingsManager.Companion.KEY_AUDIO_OUTPUT_BIT_DEPTH
import com.ella.music.data.SettingsManager.Companion.KEY_AUDIO_OUTPUT_SAMPLE_RATE
import com.ella.music.data.SettingsManager.Companion.KEY_BLUETOOTH_AUTO_PLAY
import com.ella.music.data.SettingsManager.Companion.KEY_CROSSFADE_DURATION_MS
import com.ella.music.data.SettingsManager.Companion.KEY_CROSSFADE_CURVE
import com.ella.music.data.SettingsManager.Companion.KEY_DECODER_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_GAPLESS
import com.ella.music.data.SettingsManager.Companion.KEY_OPEN_PLAYER_ON_PLAY
import com.ella.music.data.SettingsManager.Companion.KEY_PREVIOUS_BUTTON_ACTION
import com.ella.music.data.SettingsManager.Companion.KEY_REPLAYGAIN_ENABLED
import com.ella.music.data.SettingsManager.Companion.KEY_REPLAYGAIN_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_RESUME_PLAYBACK_POSITION
import com.ella.music.data.SettingsManager.Companion.KEY_SHUFFLE_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_SLEEP_TIMER_CUSTOM_MINUTES
import com.ella.music.data.SettingsManager.Companion.KEY_SLEEP_TIMER_STOP_AFTER_CURRENT
import com.ella.music.data.SettingsManager.Companion.KEY_STARTUP_AUTO_PLAY
import com.ella.music.data.SettingsManager.Companion.KEY_STARTUP_PLAY_MODE
import com.ella.music.data.SettingsManager.Companion.KEY_USB_DAC_MODE
import com.ella.music.player.PlaybackOutputSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Playback behaviour and audio-output routing: gapless/crossfade, ReplayGain, shuffle,
 * decoder/output backend (bit depth, sample rate, USB DAC), startup/Bluetooth auto-play and sleep timer.
 *
 * Extracted verbatim from [SettingsManager], which implements this interface via class
 * delegation so every call site keeps using settingsManager.<member> unchanged. All flow
 * properties MUST stay eagerly-initialised stored properties (never computed get() =):
 * Compose collectAsState keys on the flow instance, and a fresh instance per access would
 * restart collection on every recomposition.
 */
interface PlaybackSettingsAccess {
    val gaplessPlayback: Flow<Boolean>
    val crossfadeDurationMs: Flow<Int>
    val crossfadeCurve: Flow<Int>
    val replayGainEnabled: Flow<Boolean>
    val replayGainMode: Flow<Int>
    val resumePlaybackPosition: Flow<Boolean>
    val audioFocusDisabled: Flow<Boolean>
    val audioOutputBackend: Flow<Int>
    val audioOutputBitDepth: Flow<Int>
    val audioOutputSampleRate: Flow<Int>
    val playbackOutputSettings: Flow<PlaybackOutputSettings>
    val shuffleMode: Flow<Int>
    val previousButtonAction: Flow<Int>
    val usbDacMode: Flow<Boolean>
    val sleepTimerCustomMinutes: Flow<Int>
    val sleepTimerStopAfterCurrent: Flow<Boolean>
    val openPlayerOnPlay: Flow<Boolean>
    val startupAutoPlay: Flow<Boolean>
    val bluetoothAutoPlay: Flow<Boolean>
    val startupPlayMode: Flow<Int>
    val decoderMode: Flow<Int>
    suspend fun setGaplessPlayback(enabled: Boolean)
    suspend fun setCrossfadeDurationMs(durationMs: Int)
    suspend fun setCrossfadeCurve(curve: Int)
    suspend fun setReplayGainEnabled(enabled: Boolean)
    suspend fun setReplayGainMode(mode: Int)
    suspend fun setResumePlaybackPosition(enabled: Boolean)
    suspend fun setAudioFocusDisabled(disabled: Boolean)
    suspend fun setShuffleMode(mode: Int)
    suspend fun setPreviousButtonAction(action: Int)
    suspend fun setUsbDacMode(enabled: Boolean)
    suspend fun setSleepTimerCustomMinutes(minutes: Int)
    suspend fun setSleepTimerStopAfterCurrent(enabled: Boolean)
    suspend fun setOpenPlayerOnPlay(enabled: Boolean)
    suspend fun setStartupAutoPlay(enabled: Boolean)
    suspend fun setBluetoothAutoPlay(enabled: Boolean)
    suspend fun setStartupPlayMode(mode: Int)
    suspend fun setDecoderMode(mode: Int)
    suspend fun setAudioOutputBackend(backend: Int)
    suspend fun setAudioOutputBitDepth(bitDepth: Int)
    suspend fun setAudioOutputSampleRate(sampleRate: Int)
}

internal class PlaybackSettingsAccessImpl(private val context: Context) : PlaybackSettingsAccess {

    override val gaplessPlayback: Flow<Boolean> = context.dataStore.data.map { it[KEY_GAPLESS] ?: true }
    override val crossfadeDurationMs: Flow<Int> = context.dataStore.data
        .map { (it[KEY_CROSSFADE_DURATION_MS] ?: 0).coerceIn(0, 12_000) }
    override val crossfadeCurve: Flow<Int> = context.dataStore.data.map {
        (it[KEY_CROSSFADE_CURVE] ?: SettingsManager.CROSSFADE_CURVE_EQUAL_POWER).coerceIn(
            SettingsManager.CROSSFADE_CURVE_EQUAL_POWER,
            SettingsManager.CROSSFADE_CURVE_FLAT
        )
    }

    override val replayGainEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_REPLAYGAIN_ENABLED] ?: false }
    override val replayGainMode: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_REPLAYGAIN_MODE]
            ?: if (preferences[KEY_REPLAYGAIN_ENABLED] == true) REPLAY_GAIN_AUTO else REPLAY_GAIN_OFF
    }.map { it.coerceIn(REPLAY_GAIN_OFF, REPLAY_GAIN_AUTO) }
    override val resumePlaybackPosition: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_RESUME_PLAYBACK_POSITION] ?: false }
    override val audioFocusDisabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUDIO_FOCUS_DISABLED] ?: false }
    override val audioOutputBackend: Flow<Int> =
        context.dataStore.data.map { normalizeAudioOutputBackend(it[KEY_AUDIO_OUTPUT_BACKEND]) }
    override val audioOutputBitDepth: Flow<Int> =
        context.dataStore.data.map { normalizeAudioOutputBitDepth(it[KEY_AUDIO_OUTPUT_BIT_DEPTH]) }
    override val audioOutputSampleRate: Flow<Int> =
        context.dataStore.data.map { normalizeAudioOutputSampleRate(it[KEY_AUDIO_OUTPUT_SAMPLE_RATE]) }
    override val playbackOutputSettings: Flow<PlaybackOutputSettings> = combine(
        audioOutputBackend,
        audioOutputBitDepth,
        audioOutputSampleRate,
        context.dataStore.data.map { it[KEY_USB_DAC_MODE] ?: false }
    ) { backend, bitDepth, sampleRate, usbExclusive ->
        PlaybackOutputSettings(
            backend = backend,
            bitDepth = bitDepth,
            sampleRate = sampleRate,
            usbExclusive = usbExclusive
        )
    }
    override val shuffleMode: Flow<Int> =
        context.dataStore.data.map { it[KEY_SHUFFLE_MODE] ?: SHUFFLE_MODE_PSEUDO }
    override val previousButtonAction: Flow<Int> =
        context.dataStore.data.map { it[KEY_PREVIOUS_BUTTON_ACTION] ?: PREVIOUS_BUTTON_PREVIOUS }

    override val usbDacMode: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_USB_DAC_MODE] ?: false }

    override val sleepTimerCustomMinutes: Flow<Int> =
        context.dataStore.data.map { it[KEY_SLEEP_TIMER_CUSTOM_MINUTES]?.coerceIn(5, 120) ?: 45 }
    override val sleepTimerStopAfterCurrent: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SLEEP_TIMER_STOP_AFTER_CURRENT] ?: false }

    override val openPlayerOnPlay: Flow<Boolean> = context.dataStore.data.map { it[KEY_OPEN_PLAYER_ON_PLAY] ?: false }
    override val startupAutoPlay: Flow<Boolean> = context.dataStore.data.map { it[KEY_STARTUP_AUTO_PLAY] ?: false }
    override val bluetoothAutoPlay: Flow<Boolean> = context.dataStore.data.map { it[KEY_BLUETOOTH_AUTO_PLAY] ?: false }
    override val startupPlayMode: Flow<Int> = context.dataStore.data.map {
        it[KEY_STARTUP_PLAY_MODE]
            ?: if (it[KEY_STARTUP_AUTO_PLAY] == true) STARTUP_PLAY_RANDOM else STARTUP_PLAY_OFF
    }

    override val decoderMode: Flow<Int> = context.dataStore.data.map { it[KEY_DECODER_MODE] ?: 2 }

    override suspend fun setGaplessPlayback(enabled: Boolean) {
        context.dataStore.edit { it[KEY_GAPLESS] = enabled }
    }

    override suspend fun setCrossfadeDurationMs(durationMs: Int) {
        context.dataStore.edit { it[KEY_CROSSFADE_DURATION_MS] = durationMs.coerceIn(0, 12_000) }
    }

    override suspend fun setCrossfadeCurve(curve: Int) {
        context.dataStore.edit {
            it[KEY_CROSSFADE_CURVE] = curve.coerceIn(
                SettingsManager.CROSSFADE_CURVE_EQUAL_POWER,
                SettingsManager.CROSSFADE_CURVE_FLAT
            )
        }
    }

    override suspend fun setReplayGainEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_REPLAYGAIN_ENABLED] = enabled
            it[KEY_REPLAYGAIN_MODE] = if (enabled) REPLAY_GAIN_AUTO else REPLAY_GAIN_OFF
        }
    }

    override suspend fun setReplayGainMode(mode: Int) {
        val safeMode = mode.coerceIn(REPLAY_GAIN_OFF, REPLAY_GAIN_AUTO)
        context.dataStore.edit {
            it[KEY_REPLAYGAIN_MODE] = safeMode
            it[KEY_REPLAYGAIN_ENABLED] = safeMode != REPLAY_GAIN_OFF
        }
    }

    override suspend fun setResumePlaybackPosition(enabled: Boolean) {
        context.dataStore.edit { it[KEY_RESUME_PLAYBACK_POSITION] = enabled }
    }

    override suspend fun setAudioFocusDisabled(disabled: Boolean) {
        context.dataStore.edit { it[KEY_AUDIO_FOCUS_DISABLED] = disabled }
    }

    override suspend fun setShuffleMode(mode: Int) {
        context.dataStore.edit { it[KEY_SHUFFLE_MODE] = mode.coerceIn(SHUFFLE_MODE_PSEUDO, SHUFFLE_MODE_TRUE_RANDOM) }
    }

    override suspend fun setPreviousButtonAction(action: Int) {
        context.dataStore.edit {
            it[KEY_PREVIOUS_BUTTON_ACTION] = action.coerceIn(PREVIOUS_BUTTON_PREVIOUS, PREVIOUS_BUTTON_REPLAY_CURRENT)
        }
    }

    override suspend fun setUsbDacMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_USB_DAC_MODE] = enabled }
    }

    override suspend fun setSleepTimerCustomMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_SLEEP_TIMER_CUSTOM_MINUTES] = minutes.coerceIn(5, 120) }
    }

    override suspend fun setSleepTimerStopAfterCurrent(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SLEEP_TIMER_STOP_AFTER_CURRENT] = enabled }
    }

    override suspend fun setOpenPlayerOnPlay(enabled: Boolean) {
        context.dataStore.edit { it[KEY_OPEN_PLAYER_ON_PLAY] = enabled }
    }

    override suspend fun setStartupAutoPlay(enabled: Boolean) {
        setStartupPlayMode(if (enabled) STARTUP_PLAY_RANDOM else STARTUP_PLAY_OFF)
    }

    override suspend fun setBluetoothAutoPlay(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLUETOOTH_AUTO_PLAY] = enabled }
    }

    override suspend fun setStartupPlayMode(mode: Int) {
        val safeMode = mode.coerceIn(STARTUP_PLAY_OFF, STARTUP_PLAY_RESUME)
        context.dataStore.edit {
            it[KEY_STARTUP_PLAY_MODE] = safeMode
            it[KEY_STARTUP_AUTO_PLAY] = safeMode != STARTUP_PLAY_OFF
        }
    }

    override suspend fun setDecoderMode(mode: Int) {
        context.dataStore.edit { it[KEY_DECODER_MODE] = mode.coerceIn(0, 2) }
    }

    override suspend fun setAudioOutputBackend(backend: Int) {
        context.dataStore.edit { it[KEY_AUDIO_OUTPUT_BACKEND] = normalizeAudioOutputBackend(backend) }
    }

    override suspend fun setAudioOutputBitDepth(bitDepth: Int) {
        context.dataStore.edit { it[KEY_AUDIO_OUTPUT_BIT_DEPTH] = normalizeAudioOutputBitDepth(bitDepth) }
    }

    override suspend fun setAudioOutputSampleRate(sampleRate: Int) {
        context.dataStore.edit { it[KEY_AUDIO_OUTPUT_SAMPLE_RATE] = normalizeAudioOutputSampleRate(sampleRate) }
    }

    private fun normalizeAudioOutputBackend(backend: Int?): Int =
        when (backend) {
            AUDIO_OUTPUT_BACKEND_OPENSLES,
            AUDIO_OUTPUT_BACKEND_AAUDIO,
            AUDIO_OUTPUT_BACKEND_HI_RES,
            AUDIO_OUTPUT_BACKEND_AUDIOTRACK -> backend
            else -> AUDIO_OUTPUT_BACKEND_AUTO
        }

    private fun normalizeAudioOutputBitDepth(bitDepth: Int?): Int =
        when (bitDepth) {
            AUDIO_OUTPUT_BIT_DEPTH_16,
            AUDIO_OUTPUT_BIT_DEPTH_24,
            AUDIO_OUTPUT_BIT_DEPTH_32,
            AUDIO_OUTPUT_BIT_DEPTH_FLOAT32 -> bitDepth
            else -> AUDIO_OUTPUT_BIT_DEPTH_AUTO
        }

    private fun normalizeAudioOutputSampleRate(sampleRate: Int?): Int =
        if (sampleRate != null && AUDIO_OUTPUT_SAMPLE_RATES.contains(sampleRate)) {
            sampleRate
        } else {
            AUDIO_OUTPUT_SAMPLE_RATE_AUTO
        }
}
