package com.ella.music.data.repository

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.ella.music.data.SettingsManager
import com.ella.music.data.metadata.AudioTagRepository
import com.ella.music.data.metadata.WavMetadataReader
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.scanner.MusicScanner
import java.util.concurrent.ConcurrentHashMap

/**
 * Extracts and caches per-song audio quality info (format / bit rate / sample rate / bit depth /
 * channels) and ReplayGain values. Logic moved verbatim from [MusicRepository]; the repository
 * remains the public entry point and delegates here.
 *
 * [metadataPathResolver] must be the repository's `Song.effectiveLocalPathForMetadata()` so the
 * WebDAV header-cache resolution behavior stays identical.
 */
internal class MusicAudioInfoProvider(
    private val scanner: MusicScanner,
    private val audioTagRepository: AudioTagRepository,
    private val metadataPathResolver: (Song) -> String
) {
    private val audioInfoCache = ConcurrentHashMap<String, AudioInfo>()
    private val replayGainCache = ConcurrentHashMap<String, Float>()
    private val replayGainMissingCache = ConcurrentHashMap.newKeySet<String>()

    fun getReplayGain(song: Song, mode: Int = SettingsManager.REPLAY_GAIN_AUTO): Float? {
        val safeMode = mode.coerceIn(SettingsManager.REPLAY_GAIN_OFF, SettingsManager.REPLAY_GAIN_AUTO)
        if (safeMode == SettingsManager.REPLAY_GAIN_OFF) return null
        val cacheKey = "${song.metadataCacheKey()}:rg=$safeMode"
        replayGainCache[cacheKey]?.let { return it }
        if (replayGainMissingCache.contains(cacheKey)) return null
        val gain = scanner.extractReplayGain(metadataPathResolver(song), safeMode)
        if (gain == null) {
            replayGainCache.remove(cacheKey)
            replayGainMissingCache.add(cacheKey)
        } else {
            replayGainCache[cacheKey] = gain
            replayGainMissingCache.remove(cacheKey)
        }
        return gain
    }

    fun getAudioInfo(song: Song): AudioInfo {
        val cacheKey = song.metadataCacheKey()
        audioInfoCache[cacheKey]?.let { return it }
        val replayGainDb = getReplayGain(song)
        val metadataPath = metadataPathResolver(song)
        val wavMetadata = WavMetadataReader.read(metadataPath)
        val estimatedBitRate = song.estimatedBitRate()
        audioTagRepository.readQualityInfoBlocking(metadataPath)?.let { quality ->
            val bitRate = quality.bitRate.takeIf { it > 0 }
                ?: wavMetadata?.bitRate?.takeIf { it > 0 }
                ?: estimatedBitRate
            val sampleRate = quality.sampleRate.takeIf { it > 0 } ?: wavMetadata?.sampleRate ?: 0
            val bitDepth = quality.bitDepth.takeIf { it > 0 } ?: wavMetadata?.bitDepth ?: 0
            val channels = quality.channels.takeIf { it > 0 } ?: wavMetadata?.channels ?: 0
            val info = AudioInfo(
                format = song.audioFormatLabel(
                    mime = quality.mimeType,
                    bitRate = bitRate,
                    sampleRate = sampleRate,
                    bitDepth = bitDepth,
                    channels = channels,
                    estimatedBitRate = estimatedBitRate
                ),
                bitRate = bitRate,
                sampleRate = sampleRate,
                bitDepth = bitDepth,
                channels = channels,
                replayGainDb = replayGainDb
            )
            audioInfoCache[cacheKey] = info
            return info
        }
        wavMetadata?.takeIf { it.hasQuality }?.let { quality ->
            val info = AudioInfo(
                format = song.audioFormatLabel(
                    mime = "audio/wav",
                    bitRate = quality.bitRate.takeIf { it > 0 } ?: estimatedBitRate,
                    sampleRate = quality.sampleRate,
                    bitDepth = quality.bitDepth,
                    channels = quality.channels,
                    estimatedBitRate = estimatedBitRate
                ),
                bitRate = quality.bitRate.takeIf { it > 0 } ?: estimatedBitRate,
                sampleRate = quality.sampleRate,
                bitDepth = quality.bitDepth,
                channels = quality.channels,
                replayGainDb = replayGainDb
            )
            audioInfoCache[cacheKey] = info
            return info
        }
        val info = runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(metadataPath)
                var audioFormat: MediaFormat? = null
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        audioFormat = format
                        break
                    }
                }

                val format = audioFormat
                val extractedBitRate = format?.getIntOrZero(MediaFormat.KEY_BIT_RATE) ?: 0
                val bitRate = extractedBitRate.takeIf { it > 0 } ?: estimatedBitRate
                val sampleRate = (format?.getIntOrZero(MediaFormat.KEY_SAMPLE_RATE) ?: 0)
                    .takeIf { it > 0 } ?: wavMetadata?.sampleRate ?: 0
                val bitDepth = (format?.getIntOrZero("bits-per-sample") ?: 0)
                    .takeIf { it > 0 } ?: wavMetadata?.bitDepth ?: 0
                val channels = (format?.getIntOrZero(MediaFormat.KEY_CHANNEL_COUNT) ?: 0)
                    .takeIf { it > 0 } ?: wavMetadata?.channels ?: 0
                AudioInfo(
                    format = song.audioFormatLabel(
                        mime = format?.getString(MediaFormat.KEY_MIME),
                        bitRate = bitRate,
                        sampleRate = sampleRate,
                        bitDepth = bitDepth,
                        channels = channels,
                        estimatedBitRate = estimatedBitRate
                    ),
                    bitRate = bitRate,
                    sampleRate = sampleRate,
                    bitDepth = bitDepth,
                    channels = channels,
                    replayGainDb = replayGainDb
                )
            } finally {
                extractor.release()
            }
        }.getOrElse {
            Log.w("MusicRepo", "Failed to read audio info for ${song.path}", it)
            AudioInfo(
                format = song.audioFormatLabel(
                    mime = null,
                    estimatedBitRate = estimatedBitRate
                ),
                replayGainDb = replayGainDb
            )
        }
        audioInfoCache[cacheKey] = info
        return info
    }

    fun clearCache() {
        audioInfoCache.clear()
        replayGainCache.clear()
        replayGainMissingCache.clear()
    }

    fun clearMetadataCache(metadataPrefix: String) {
        audioInfoCache.removeKeysMatching { it.startsWith(metadataPrefix) }
        replayGainCache.removeKeysMatching { it.startsWith(metadataPrefix) }
        replayGainMissingCache.removeIf { it.startsWith(metadataPrefix) }
    }

    private fun Song.estimatedBitRate(): Int {
        if (fileSize <= 0L || duration <= 0L) return 0
        return ((fileSize * 8_000L) / duration).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
