package com.ella.music.player

import android.content.Context
import android.media.AudioAttributes as PlatformAudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.Spatializer
import android.os.Build
import androidx.media3.common.AudioAttributes

/**
 * Lightweight platform spatial-audio delegation adapted for Halcyon's Media3 output path.
 * It only requests Android's renderer when the OS reports that stereo music can be spatialized;
 * custom 360 rendering explicitly opts out to avoid applying two spatial processors in series.
 */
object AndroidSpatialAudio {
    data class Snapshot(
        val apiSupported: Boolean,
        val supported: Boolean,
        val available: Boolean,
        val enabled: Boolean,
        val stereoCanBeSpatialized: Boolean
    ) {
        val usable: Boolean get() = apiSupported && supported && available && enabled && stereoCanBeSpatialized
    }

    fun mediaAttributes(context: Context, platformRequested: Boolean, customSpatialRenderer: Boolean): AudioAttributes {
        val usePlatform = platformRequested && !customSpatialRenderer && snapshot(context).usable
        return AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setSpatializationBehavior(
                if (usePlatform && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
                    PlatformAudioAttributes.SPATIALIZATION_BEHAVIOR_AUTO
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
                    PlatformAudioAttributes.SPATIALIZATION_BEHAVIOR_NEVER
                } else {
                    0
                }
            )
            .setIsContentSpatialized(customSpatialRenderer)
            .build()
    }

    fun snapshot(context: Context): Snapshot {
        // API 31 exposes Spatializer but API 32 introduced per-stream AUTO/NEVER behavior.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) {
            return Snapshot(false, false, false, false, false)
        }
        return runCatching {
            val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return@runCatching Snapshot(true, false, false, false, false)
            val spatializer = audioManager.spatializer
            val supported = spatializer.immersiveAudioLevel != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE
            if (!supported) return@runCatching Snapshot(true, false, false, false, false)
            val attributes = PlatformAudioAttributes.Builder()
                .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
                .setContentType(PlatformAudioAttributes.CONTENT_TYPE_MUSIC)
                .setSpatializationBehavior(PlatformAudioAttributes.SPATIALIZATION_BEHAVIOR_AUTO)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(48_000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            Snapshot(
                apiSupported = true,
                supported = true,
                available = spatializer.isAvailable,
                enabled = spatializer.isEnabled,
                stereoCanBeSpatialized = spatializer.canBeSpatialized(attributes, format)
            )
        }.getOrElse { Snapshot(true, false, false, false, false) }
    }
}
