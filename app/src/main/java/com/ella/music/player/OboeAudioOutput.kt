package com.ella.music.player

import java.nio.ByteBuffer

/**
 * Thin JNI wrapper over the native Oboe output stream (see cpp/oboe_sink.cpp). One instance owns one
 * open Oboe stream. Used by [OboeAudioSink] to push decoded PCM to AAudio / OpenSL ES.
 *
 * PCM [encoding] ids: 0 = 16-bit, 1 = 24-bit packed, 2 = 32-bit, 3 = float32.
 * [audioApi] ids: 0 = unspecified, 1 = AAudio, 2 = OpenSL ES.
 *
 * When [exclusive] is true, native open **fails** unless the HAL actually grants
 * [oboe::SharingMode::Exclusive] (no silent Shared fallback).
 */
class OboeAudioOutput {

    private var handle: Long = 0L

    fun open(
        audioApi: Int,
        sampleRate: Int,
        channelCount: Int,
        encoding: Int,
        exclusive: Boolean,
        deviceId: Int
    ): Boolean {
        if (!ensureLoaded()) return false
        handle = nativeOpen(audioApi, sampleRate, channelCount, encoding, exclusive, deviceId)
        return handle != 0L
    }

    val isOpen: Boolean get() = handle != 0L

    /** True only when the live stream is actually Exclusive (not Shared). */
    val isExclusive: Boolean
        get() = handle != 0L && nativeIsExclusive(handle)

    /** Blocking write of a direct [buffer] region; returns bytes consumed, -1 on error, -2 on disconnect. */
    fun write(buffer: ByteBuffer, offset: Int, length: Int, timeoutNanos: Long): Int =
        if (handle == 0L) -1 else nativeWrite(handle, buffer, offset, length, timeoutNanos)

    fun framesRead(): Long = if (handle == 0L) 0L else nativeGetFramesRead(handle)

    fun outputSampleRate(): Int = if (handle == 0L) 0 else nativeGetSampleRate(handle)

    fun start() { if (handle != 0L) nativeStart(handle) }
    fun pause() { if (handle != 0L) nativePause(handle) }
    fun flush() { if (handle != 0L) nativeFlush(handle) }

    fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    private external fun nativeOpen(
        audioApi: Int, sampleRate: Int, channelCount: Int, encoding: Int, exclusive: Boolean, deviceId: Int
    ): Long
    private external fun nativeIsExclusive(handle: Long): Boolean
    private external fun nativeWrite(handle: Long, buffer: ByteBuffer, offset: Int, length: Int, timeoutNanos: Long): Int
    private external fun nativeGetFramesRead(handle: Long): Long
    private external fun nativeGetSampleRate(handle: Long): Int
    private external fun nativeStart(handle: Long)
    private external fun nativePause(handle: Long)
    private external fun nativeFlush(handle: Long)
    private external fun nativeClose(handle: Long)

    companion object {
        @Volatile
        private var loaded = false

        fun ensureLoaded(): Boolean {
            if (loaded) return true
            return synchronized(this) {
                if (loaded) return@synchronized true
                runCatching { System.loadLibrary("ella_oboe") }
                    .onSuccess { loaded = true }
                    .isSuccess
            }
        }
    }
}
