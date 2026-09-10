package com.ella.music.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.ella.music.data.AppLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates true USB DAC exclusive playback:
 * 1) Media-strategy routing ([UsbAudioController]) — gets the DAC selected system-wide
 * 2) AAudio Exclusive open pinned to the DAC device id, at a rate/channel the DAC advertises
 * 3) Strong [AUDIOFOCUS_GAIN] so other apps are paused/ducked while we hold the device
 *
 * Routing alone is NOT exclusive — other apps can still mix through the shared HAL.
 * Exclusive is only true when [UsbExclusiveState.ExclusiveActive].
 */
enum class UsbExclusiveState {
    Off,
    /** USB selected via AudioProductStrategy / setDevicesForMedia, but AAudio Exclusive not held. */
    RoutingOnly,
    /** AAudio SharingMode::Exclusive verified on the live stream. */
    ExclusiveActive,
    /** Exclusive was requested but the HAL refused / fell back to Shared. */
    ExclusiveFailed
}

data class UsbExclusiveStatus(
    val state: UsbExclusiveState = UsbExclusiveState.Off,
    val deviceName: String = "",
    val deviceId: Int = 0,
    val sampleRate: Int = 0,
    val channelCount: Int = 0,
    val detail: String = ""
)

object UsbExclusiveSession {
    private val _status = MutableStateFlow(UsbExclusiveStatus())
    val status: StateFlow<UsbExclusiveStatus> = _status.asStateFlow()

    @Volatile
    private var focusRequest: AudioFocusRequest? = null

    fun updateRoutingOnly(device: AudioDeviceInfo?) {
        if (device == null) {
            _status.value = UsbExclusiveStatus(state = UsbExclusiveState.Off)
            return
        }
        val current = _status.value
        if (current.state == UsbExclusiveState.ExclusiveActive && current.deviceId == device.id) return
        _status.value = UsbExclusiveStatus(
            state = UsbExclusiveState.RoutingOnly,
            deviceName = device.productName?.toString().orEmpty().ifBlank { "USB DAC" },
            deviceId = device.id,
            detail = "routed"
        )
    }

    fun markExclusiveActive(deviceId: Int, deviceName: String, sampleRate: Int, channelCount: Int) {
        _status.value = UsbExclusiveStatus(
            state = UsbExclusiveState.ExclusiveActive,
            deviceName = deviceName,
            deviceId = deviceId,
            sampleRate = sampleRate,
            channelCount = channelCount,
            detail = "aaudio-exclusive"
        )
    }

    fun markExclusiveFailed(deviceId: Int, deviceName: String, detail: String) {
        _status.value = UsbExclusiveStatus(
            state = UsbExclusiveState.ExclusiveFailed,
            deviceName = deviceName,
            deviceId = deviceId,
            detail = detail
        )
    }

    fun clear() {
        _status.value = UsbExclusiveStatus(state = UsbExclusiveState.Off)
    }

    fun requestExclusiveFocus(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener { /* keep holding; PlaybackService also listens */ }
                .build()
            focusRequest = req
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun abandonExclusiveFocus(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    /**
     * Candidate PCM configurations for Exclusive open, ordered best-first.
     * Prefers the DAC's advertised rates, then common hi-res rates, then [preferredRate].
     */
    fun exclusiveOpenCandidates(
        device: AudioDeviceInfo?,
        preferredRate: Int,
        preferredChannels: Int,
        encodingId: Int
    ): List<Triple<Int, Int, Int>> {
        // Bit-perfect: never change the content sample rate under Exclusive (no SRC).
        val rate = if (preferredRate > 0) preferredRate else 44100
        val channels = linkedSetOf<Int>()
        if (preferredChannels > 0) channels += preferredChannels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && device != null) {
            device.channelCounts?.filter { it in 1..8 }?.forEach { channels += it }
        }
        channels += listOf(2, 1)
        val encodings = linkedSetOf(encodingId, 0, 1, 2, 3)
        val out = ArrayList<Triple<Int, Int, Int>>(channels.size * encodings.size)
        for (enc in encodings) {
            for (ch in channels) {
                out += Triple(rate, ch, enc)
            }
        }
        return out
    }

    fun resolveUsbDevice(context: Context): AudioDeviceInfo? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
    }

    fun log(context: Context, message: String) {
        AppLogStore.warn(context, "UsbExclusive", message)
    }
}

