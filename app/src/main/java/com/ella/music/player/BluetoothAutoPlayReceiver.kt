package com.ella.music.player

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.os.Build
import android.util.Log

@Suppress("DEPRECATION")
class BluetoothAutoPlayReceiver(
    private val isAutoPlayEnabled: () -> Boolean,
    private val onDeviceConnected: () -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "BtAutoPlay"

        fun createIntentFilter(): IntentFilter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }

        fun hasBluetoothConnectPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        }

        fun isBluetoothAudioConnected(context: Context): Boolean {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            return runCatching {
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
                }
            }.getOrDefault(false)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, BluetoothA2dp.STATE_DISCONNECTED)
                if (state == BluetoothA2dp.STATE_CONNECTED) {
                    handleBluetoothAudioConnected(context, intent, "A2DP")
                }
            }
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED)
                if (state == BluetoothHeadset.STATE_CONNECTED) {
                    handleBluetoothAudioConnected(context, intent, "Headset")
                }
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                handleBluetoothAudioConnected(context, intent, "ACL")
            }
            AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                val state = intent.getIntExtra(
                    AudioManager.EXTRA_SCO_AUDIO_STATE,
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                )
                if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                    handleBluetoothAudioConnected(context, intent, "SCO")
                }
            }
        }
    }

    private fun handleBluetoothAudioConnected(context: Context, intent: Intent, source: String) {
        val device = intent.bluetoothDeviceExtra()
        val deviceName = try {
            if (hasBluetoothConnectPermission(context)) device?.name ?: "Unknown" else "Unknown"
        } catch (_: SecurityException) {
            "Unknown"
        }
        Log.i(TAG, "Bluetooth $source connected: $deviceName")
        if (isAutoPlayEnabled()) {
            val routeReady = isBluetoothAudioConnected(context)
            Log.i(TAG, "Bluetooth auto-play enabled, notifying service from $source routeReady=$routeReady")
            onDeviceConnected()
        }
    }

    private fun Intent.bluetoothDeviceExtra(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}
