package com.ella.music.data

/** Container identity comes from response bytes, before a source's guessed filename. */
internal data class AudioContainerFormat(val extension: String, val mimeType: String)

internal fun detectAudioContainerFormat(
    prefix: ByteArray,
    contentType: String? = null,
    fallbackFileName: String = "",
    fallbackMimeType: String = ""
): AudioContainerFormat {
    fun matches(offset: Int, text: String): Boolean = offset >= 0 && offset + text.length <= prefix.size &&
        text.indices.all { prefix[offset + it] == text[it].code.toByte() }
    fun unsigned(index: Int) = prefix[index].toInt() and 0xff
    val id3 = matches(0, "ID3")
    val audioOffset = if (id3 && prefix.size >= 10 && (6..9).all { unsigned(it) < 128 }) {
        10 + (6..9).fold(0) { size, index -> (size shl 7) or unsigned(index) }
    } else 0
    val format = when {
        matches(0, "fLaC") || matches(audioOffset, "fLaC") -> AudioContainerFormat("flac", "audio/flac")
        matches(4, "ftyp") || matches(audioOffset + 4, "ftyp") -> AudioContainerFormat("m4a", "audio/mp4")
        matches(0, "OggS") -> if (prefix.toString(Charsets.ISO_8859_1).contains("OpusHead")) {
            AudioContainerFormat("opus", "audio/ogg")
        } else AudioContainerFormat("ogg", "audio/ogg")
        matches(0, "RIFF") && matches(8, "WAVE") -> AudioContainerFormat("wav", "audio/wav")
        matches(0, "FORM") && (matches(8, "AIFF") || matches(8, "AIFC")) -> AudioContainerFormat("aiff", "audio/aiff")
        prefix.size >= 2 && unsigned(0) == 255 && unsigned(1) and 0xf6 == 0xf0 ->
            AudioContainerFormat("aac", "audio/aac")
        prefix.size >= 2 && unsigned(0) == 255 && unsigned(1) and 0xe0 == 0xe0 &&
            unsigned(1) and 0x18 != 0x08 && unsigned(1) and 0x06 != 0 -> AudioContainerFormat("mp3", "audio/mpeg")
        else -> null
    }
    if (format != null) return format
    fun fromMime(value: String?): AudioContainerFormat? = when (value?.substringBefore(';')?.trim()?.lowercase()) {
        "audio/flac", "audio/x-flac" -> AudioContainerFormat("flac", "audio/flac")
        "audio/mp4", "audio/m4a", "audio/x-m4a", "video/mp4" -> AudioContainerFormat("m4a", "audio/mp4")
        "audio/mpeg", "audio/mp3" -> AudioContainerFormat("mp3", "audio/mpeg")
        "audio/aac", "audio/aacp" -> AudioContainerFormat("aac", "audio/aac")
        "audio/ogg", "application/ogg" -> AudioContainerFormat("ogg", "audio/ogg")
        "audio/opus" -> AudioContainerFormat("opus", "audio/ogg")
        "audio/wav", "audio/x-wav", "audio/wave" -> AudioContainerFormat("wav", "audio/wav")
        "audio/aiff", "audio/x-aiff" -> AudioContainerFormat("aiff", "audio/aiff")
        else -> null
    }
    fromMime(contentType)?.let { return it }
    // ID3 alone is also valid ahead of FLAC, so honor an explicit FLAC content type first.
    if (id3) return AudioContainerFormat("mp3", "audio/mpeg")
    val extension = fallbackFileName.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()
    val named = when (extension) {
        "flac" -> AudioContainerFormat("flac", "audio/flac")
        "m4a", "mp4" -> AudioContainerFormat("m4a", "audio/mp4")
        "aac" -> AudioContainerFormat("aac", "audio/aac")
        "ogg" -> AudioContainerFormat("ogg", "audio/ogg")
        "opus" -> AudioContainerFormat("opus", "audio/ogg")
        "wav" -> AudioContainerFormat("wav", "audio/wav")
        "aif", "aiff" -> AudioContainerFormat("aiff", "audio/aiff")
        "mp3" -> AudioContainerFormat("mp3", "audio/mpeg")
        else -> null
    }
    return named ?: fromMime(fallbackMimeType) ?: AudioContainerFormat("mp3", "audio/mpeg")
}
