package com.ella.music.data

/**
 * Stream-quality preference shared by the LX and MusicFree sources. Stored ids follow LX naming
 * (128k / 320k / flac / flac24bit); each source maps them to its own vocabulary and only ever
 * falls back to a lower tier than the one requested.
 */
internal object OnlinePlaybackQuality {
    const val AUTO = "auto"
    val TIERS = listOf("128k", "320k", "flac", "flac24bit")

    /** Accepts ids written by older builds ("lossless" / "hires"). */
    fun normalize(value: String?): String = when (value?.trim()?.lowercase()) {
        null, "", AUTO -> AUTO
        "lossless", "sq" -> "flac"
        "hires", "hi-res", "hr" -> "flac24bit"
        "128", "standard", "low" -> "128k"
        "320", "high", "exhigh" -> "320k"
        else -> value.trim().lowercase().takeIf { it in TIERS } ?: AUTO
    }

    /** Highest available LX tier not above [requested]; null when [requested] is auto. */
    fun lxTier(requested: String, available: Collection<String>): String? {
        val wanted = normalize(requested).takeUnless { it == AUTO } ?: return null
        if (available.isEmpty() || wanted in available) return wanted
        val ranked = available.mapNotNull { key ->
            val rank = TIERS.indexOf(normalize(key))
            if (rank < 0) null else key to rank
        }
        // Keep the declared spelling (for example sd's hires) in the source request.
        return ranked.filter { it.second <= TIERS.indexOf(wanted) }.maxByOrNull { it.second }?.first
            ?: ranked.minByOrNull { it.second }?.first
            ?: available.last()
    }

    /** MusicFree's plugin vocabulary: low / standard / high / super. */
    fun musicFreeCandidates(requested: String): List<String> = when (normalize(requested)) {
        "128k" -> listOf("standard", "128k", "low")
        "320k" -> listOf("high", "320k", "standard", "low")
        "flac" -> listOf("super", "lossless", "flac", "high", "320k", "standard", "low")
        "flac24bit" -> listOf("super", "hires", "flac24bit", "lossless", "flac", "high", "320k", "standard", "low")
        else -> emptyList()
    }
}
