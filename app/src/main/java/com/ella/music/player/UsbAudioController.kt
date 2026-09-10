package com.ella.music.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.ella.music.data.AppLogStore
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class UsbAudioController private constructor(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val usbAudioDevices: Flow<List<AudioDeviceInfo>> = callbackFlow {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                trySend(getUsbAudioDevices(audioManager))
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                trySend(getUsbAudioDevices(audioManager))
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        trySend(getUsbAudioDevices(audioManager))
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    val preferredUsbDevice: Flow<AudioDeviceInfo?> = usbAudioDevices.map { devices ->
        devices.firstOrNull { isUsbDevice(it) }
    }

    suspend fun applyUsbRoutingIfEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val enabled = settingsManager.usbDacMode.first()
        if (!enabled) {
            clearUsbRoutingInternal(audioManager)
            UsbExclusiveSession.abandonExclusiveFocus(context)
            UsbExclusiveSession.clear()
            return
        }
        val devices = getUsbAudioDevices(audioManager)
        val preferred = devices.firstOrNull { isUsbDevice(it) }
        if (preferred != null) {
            if (!setDevicesForMediaInternal(audioManager, listOf(preferred))) {
                AppLogStore.warn(
                    context,
                    "UsbAudio",
                    "Failed to route media to USB DAC ${preferred.productName ?: preferred.id}"
                )
            }
            UsbExclusiveSession.updateRoutingOnly(preferred)
            UsbExclusiveSession.requestExclusiveFocus(context)
        } else {
            if (!clearUsbRoutingInternal(audioManager)) {
                AppLogStore.warn(context, "UsbAudio", "Failed to clear USB DAC media routing")
            }
            UsbExclusiveSession.abandonExclusiveFocus(context)
            UsbExclusiveSession.clear()
        }
    }

    /**
     * Requests USB access permission for the connected USB-audio device (shows the system
     * authorization dialog). AAudio exclusive routing does not strictly need this, but it surfaces
     * the "authorize" prompt users expect and is a prerequisite for future direct-USB output.
     */
    fun requestUsbAudioPermission() {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? android.hardware.usb.UsbManager ?: return
        val device = usbManager.deviceList.values.firstOrNull { device ->
            (0 until device.interfaceCount).any {
                device.getInterface(it).interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_AUDIO
            }
        } ?: return
        if (usbManager.hasPermission(device)) return
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        val intent = android.content.Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pendingIntent = android.app.PendingIntent.getBroadcast(context, 0, intent, flags)
        runCatching { usbManager.requestPermission(device, pendingIntent) }
    }

    fun clearUsbRouting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!clearUsbRoutingInternal(audioManager)) {
                AppLogStore.warn(context, "UsbAudio", "Failed to clear USB DAC media routing")
            }
        }
        UsbExclusiveSession.abandonExclusiveFocus(context)
        UsbExclusiveSession.clear()
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.ella.music.action.USB_PERMISSION"

        @Volatile
        private var instance: UsbAudioController? = null

        fun getInstance(context: Context): UsbAudioController =
            instance ?: synchronized(this) {
                instance ?: UsbAudioController(
                    context.applicationContext,
                    SettingsManager.getInstance(context)
                ).also { instance = it }
            }
    }
}

private fun getUsbAudioDevices(audioManager: AudioManager): List<AudioDeviceInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { isUsbDevice(it) }
    } else {
        emptyList()
    }
}

private fun isUsbDevice(device: AudioDeviceInfo): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
    } else {
        false
    }
}

private fun setDevicesForMediaInternal(audioManager: AudioManager, devices: List<AudioDeviceInfo>): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val attributes = devices.mapNotNull(::toAudioDeviceAttributes)
    if (attributes.isEmpty()) return false
    val routed = runCatching {
        val method = AudioManager::class.java.getMethod("setDevicesForMedia", List::class.java)
        method.invoke(audioManager, attributes)
        true
    }.getOrElse { false }
    if (routed) return true
    return setPreferredDevicesForMediaStrategy(audioManager, attributes)
}

private fun clearUsbRoutingInternal(audioManager: AudioManager): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val cleared = runCatching {
        val method = AudioManager::class.java.getMethod("clearDevicesForMedia")
        method.invoke(audioManager)
        true
    }.getOrElse { false }
    if (cleared) return true
    return clearPreferredDevicesForMediaStrategy(audioManager)
}

private fun toAudioDeviceAttributes(device: AudioDeviceInfo): Any? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return runCatching {
        val clazz = Class.forName("android.media.AudioDeviceAttributes")
        val ctor = clazz.getConstructor(AudioDeviceInfo::class.java)
        ctor.newInstance(device)
    }.getOrNull()
}

private fun setPreferredDevicesForMediaStrategy(
    audioManager: AudioManager,
    deviceAttributes: List<Any>
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || deviceAttributes.isEmpty()) return false
    val strategyClass = runCatching {
        Class.forName("android.media.audiopolicy.AudioProductStrategy")
    }.getOrNull() ?: return false
    val mediaStrategy = resolveMediaAudioProductStrategy(strategyClass) ?: return false
    return runCatching {
        val method = AudioManager::class.java.getMethod(
            "setPreferredDevicesForStrategy",
            strategyClass,
            List::class.java
        )
        (method.invoke(audioManager, mediaStrategy, deviceAttributes) as? Boolean) == true
    }.getOrElse { false }
}

private fun clearPreferredDevicesForMediaStrategy(audioManager: AudioManager): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val strategyClass = runCatching {
        Class.forName("android.media.audiopolicy.AudioProductStrategy")
    }.getOrNull() ?: return false
    val mediaStrategy = resolveMediaAudioProductStrategy(strategyClass) ?: return false
    return runCatching {
        val method = AudioManager::class.java.getMethod(
            "removePreferredDeviceForStrategy",
            strategyClass
        )
        (method.invoke(audioManager, mediaStrategy) as? Boolean) == true
    }.getOrElse { false }
}

private fun resolveMediaAudioProductStrategy(strategyClass: Class<*>): Any? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return runCatching {
        val getStrategies = strategyClass.getMethod("getAudioProductStrategies")
        val supportsAudioAttributes = strategyClass.getMethod("supportsAudioAttributes", AudioAttributes::class.java)
        val mediaAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val strategies = getStrategies.invoke(null) as? List<*> ?: return@runCatching null
        strategies.firstOrNull { strategy ->
            (supportsAudioAttributes.invoke(strategy, mediaAttributes) as? Boolean) == true
        }
    }.getOrNull()
}
