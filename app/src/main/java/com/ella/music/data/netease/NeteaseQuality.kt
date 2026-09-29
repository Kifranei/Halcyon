package com.ella.music.data.netease

import com.ella.music.R

internal enum class NeteaseQuality(val id: String, val titleRes: Int) {
    Auto("auto", R.string.netease_quality_auto),
    Standard("standard", R.string.netease_quality_standard),
    Extreme("exhigh", R.string.netease_quality_extreme),
    Lossless("lossless", R.string.netease_quality_lossless),
    HiRes("hires", R.string.netease_quality_hires),
    Surround("jyeffect", R.string.netease_quality_surround),
    Immersive("sky", R.string.netease_quality_immersive),
    Dolby("vivid", R.string.netease_quality_dolby),
    Master("jymaster", R.string.netease_quality_master);

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: Auto
    }
}

internal fun neteaseQualityLevels(preference: String): List<String> {
    val quality = NeteaseQuality.fromId(preference)
    val stereo = listOf("jymaster", "hires", "lossless", "exhigh", "standard")
    return when (quality) {
        NeteaseQuality.Auto -> stereo
        NeteaseQuality.Surround, NeteaseQuality.Immersive, NeteaseQuality.Dolby -> listOf(quality.id) + stereo.drop(1)
        else -> stereo.drop(stereo.indexOf(quality.id))
    }
}

internal fun neteaseQualityRequest(songId: String, level: String): org.json.JSONObject =
    org.json.JSONObject().put("ids", "[$songId]").put("level", level)
        .put("encodeType", if (level == "vivid") "mp3" else "flac")
        .apply { if (level == "sky") put("immerseType", "c51") }

/**
 * Builds the badge/detail AudioInfo from what NetEase actually served. The virtual song URI has no
 * readable container, so without this the player only knows "Audio" even for a Hi-Res FLAC stream.
 */
internal fun neteaseStreamAudioInfo(info: NeteaseStreamInfo): com.ella.music.data.model.AudioInfo {
    val type = info.type.lowercase()
    val lossless = type == "flac" || type == "alac" || type == "wav"
    val format = when {
        info.level == "vivid" || type.contains("ac3") || type.contains("ec3") -> "EC3"
        type.isBlank() -> if (info.level in setOf("standard", "exhigh", "higher")) "MP3" else "Audio"
        else -> type.uppercase()
    }
    val bitDepth = when {
        !lossless -> 0
        info.level in setOf("hires", "jymaster", "sky", "jyeffect") -> 24
        else -> 16
    }
    val channels = when (info.level) { "sky" -> 6; "vivid" -> 0; else -> 2 }
    return com.ella.music.data.model.AudioInfo(
        format = format,
        bitRate = info.bitRate.coerceAtLeast(0),
        sampleRate = info.sampleRate.coerceAtLeast(0),
        bitDepth = bitDepth,
        channels = channels
    )
}
