package com.ella.music.oem

import android.content.Context
import android.util.Log
import com.hihonor.android.magicx.media.audio.config.ResultCode
import com.hihonor.android.magicx.media.audio.interfaces.HnAudioClient
import com.hihonor.android.magicx.media.audio.interfaces.HnAudioPlayClient
import com.hihonor.android.magicx.media.audio.interfaces.IAudioServiceCallback

/**
 * Optional HONOR Audio Kit integration for high-sample-rate playback.
 *
 * The SDK owns the actual audio routing and format negotiation. Halcyon only connects to the
 * service, checks that the high-sample-rate feature is exposed, and enables it after the feature
 * service reports that it is ready. This keeps unsupported devices and non-HONOR ROMs on the
 * existing Media3 output path.
 */
internal class HonorHdAudioSupport(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    private var audioClient: HnAudioClient? = null

    @Volatile
    private var audioPlayClient: HnAudioPlayClient? = null

    @Volatile
    private var initialized = false

    @Volatile
    private var highSampleRateServiceReady = false

    @Volatile
    private var highSampleRateEnabled = false

    private val callback = object : IAudioServiceCallback {
        override fun onResult(resultCode: Int) {
            if (!initialized) return
            when (resultCode) {
                ResultCode.AUDIO_SERVICE_SUCCESS -> createAudioPlayClientIfSupported()
                ResultCode.AUDIO_PLAY_SERVICE_SUCCESS -> {
                    highSampleRateServiceReady = true
                    enableHighSampleRatePlayback()
                }
                ResultCode.AUDIO_SERVICE_DISCONNECTED,
                ResultCode.AUDIO_SERVICE_LINKFAILED,
                ResultCode.AUDIO_SERVICE_DIED -> {
                    highSampleRateServiceReady = false
                    highSampleRateEnabled = false
                    Log.w(TAG, "HONOR audio service disconnected: resultCode=$resultCode")
                }
                ResultCode.AUDIO_PLAY_SERVICE_DISCONNECTED,
                ResultCode.AUDIO_PLAY_SERVICE_LINKFAILED,
                ResultCode.AUDIO_PLAY_SERVICE_DIED -> {
                    highSampleRateServiceReady = false
                    highSampleRateEnabled = false
                    Log.w(TAG, "HONOR HD audio service disconnected: resultCode=$resultCode")
                }
                ResultCode.HIGHSAMPLERATE_PLAY_SERVICE_UNSUPPORTED -> {
                    Log.i(TAG, "HONOR HD audio playback is not supported on this device")
                }
            }
        }
    }

    fun initialize() {
        if (initialized) return
        initialized = true
        highSampleRateServiceReady = false
        highSampleRateEnabled = false

        val supported = runCatching {
            HnAudioClient.isDeviceSupported(appContext)
        }.getOrElse { error ->
            Log.w(TAG, "Unable to query HONOR audio service support", error)
            false
        }
        if (!supported) {
            Log.d(TAG, "HONOR audio service is unavailable")
            return
        }

        runCatching {
            audioClient = HnAudioClient(appContext, callback)
            audioClient?.initialize()
        }.onFailure { error ->
            audioClient = null
            Log.w(TAG, "Failed to initialize HONOR audio service", error)
        }
    }

    fun release() {
        val playClient = audioPlayClient
        val client = audioClient
        audioPlayClient = null
        audioClient = null
        initialized = false
        highSampleRateServiceReady = false
        highSampleRateEnabled = false

        runCatching { playClient?.destroy() }
            .onFailure { error -> Log.w(TAG, "Failed to release HONOR HD audio service", error) }
        runCatching { client?.destroy() }
            .onFailure { error -> Log.w(TAG, "Failed to release HONOR audio service", error) }
    }

    private fun createAudioPlayClientIfSupported() {
        if (audioPlayClient != null) return
        val client = audioClient ?: return
        val serviceType = HnAudioClient.ServiceType.HNAUDIO_SERVICE_HIGHSAMPLERATEPLAY
        val supportedServices = runCatching { client.getSupportedServices() }
            .getOrElse { error ->
                Log.w(TAG, "Unable to query HONOR audio services", error)
                return
            }
        if (serviceType.serviceType !in supportedServices) {
            Log.i(TAG, "HONOR device does not expose HD audio playback")
            return
        }

        val playClient = runCatching {
            client.createService<HnAudioPlayClient>(serviceType)
        }.getOrElse { error ->
            Log.w(TAG, "Failed to create HONOR HD audio service", error)
            return
        }
        if (playClient == null) {
            Log.w(TAG, "HONOR HD audio service creation returned null")
            return
        }
        audioPlayClient = playClient
        // The normal callback is AUDIO_PLAY_SERVICE_SUCCESS. This check also covers SDK builds
        // that complete feature initialization before delivering that callback.
        val serviceSupported = runCatching { playClient.isServiceSupported() }.getOrDefault(false)
        if (highSampleRateServiceReady || serviceSupported) {
            enableHighSampleRatePlayback()
        }
    }

    private fun enableHighSampleRatePlayback() {
        val playClient = audioPlayClient ?: return
        if (highSampleRateEnabled) return
        runCatching {
            playClient.enableHighSampleRatePlay(true)
        }.onSuccess {
            highSampleRateEnabled = true
            Log.i(TAG, "HONOR HD audio playback enabled")
        }.onFailure { error ->
            Log.w(TAG, "Failed to enable HONOR HD audio playback", error)
        }
    }

    private companion object {
        const val TAG = "HonorHdAudioSupport"
    }
}
