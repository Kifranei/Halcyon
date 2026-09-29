package com.ella.music.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothSpeakerDetectionTest {

    @Test
    fun matchesChineseSpeakerKeywords() {
        assertTrue(isBluetoothSpeakerKeyword("小爱音箱"))
        assertTrue(isBluetoothSpeakerKeyword("小爱音箱 Pro"))
        assertTrue(isBluetoothSpeakerKeyword("漫步者音响"))
        assertTrue(isBluetoothSpeakerKeyword("客厅音響"))
        assertTrue(isBluetoothSpeakerKeyword("广场舞大喇叭"))
    }

    @Test
    fun matchesEnglishSpeakerKeywords() {
        assertTrue(isBluetoothSpeakerKeyword("JBL Flip 6 Speaker"))
        assertTrue(isBluetoothSpeakerKeyword("Mi Soundbox"))
        assertTrue(isBluetoothSpeakerKeyword("Sony Soundbar"))
        assertTrue(isBluetoothSpeakerKeyword("Home Subwoofer"))
        assertTrue(isBluetoothSpeakerKeyword("Loudspeaker Test"))
    }

    @Test
    fun ignoresNonSpeakerDevices() {
        assertFalse(isBluetoothSpeakerKeyword("AirPods Pro"))
        assertFalse(isBluetoothSpeakerKeyword("Sony WH-1000XM4"))
        assertFalse(isBluetoothSpeakerKeyword("Galaxy Buds2 Pro"))
        assertFalse(isBluetoothSpeakerKeyword("蓝牙耳机"))
        assertFalse(isBluetoothSpeakerKeyword("Bluetooth Audio"))
        assertFalse(isBluetoothSpeakerKeyword(null))
        assertFalse(isBluetoothSpeakerKeyword(""))
        assertFalse(isBluetoothSpeakerKeyword("   "))
    }

    @Test
    fun playbackAudioOutputStateDefaultAndCustomValues() {
        val defaultState = PlaybackAudioOutputState()
        assertFalse(defaultState.isBluetooth)
        assertFalse(defaultState.isHeadphones)
        assertFalse(defaultState.isBluetoothSpeaker)
        org.junit.Assert.assertNull(defaultState.deviceName)

        val speakerState = PlaybackAudioOutputState(
            isBluetooth = true,
            isHeadphones = false,
            isBluetoothSpeaker = true,
            deviceName = "小爱音箱"
        )
        assertTrue(speakerState.isBluetooth)
        assertTrue(speakerState.isBluetoothSpeaker)
        assertFalse(speakerState.isHeadphones)
        org.junit.Assert.assertEquals("小爱音箱", speakerState.deviceName)
    }

    @Test
    fun stripsHyperOsInternalVolumePrefixes() {
        org.junit.Assert.assertEquals("小爱音箱-6040", sanitizeAudioDeviceName("dontapplycevolume小爱音箱-6040"))
        org.junit.Assert.assertEquals("小爱音箱-6040", sanitizeAudioDeviceName("dontapplyvolume小爱音箱-6040"))
        org.junit.Assert.assertEquals("Bluetooth Device", sanitizeAudioDeviceName("applycevolumeBluetooth Device"))
        assertTrue(isBluetoothSpeakerKeyword("dontapplycevolume小爱音箱-6040"))
    }
}
