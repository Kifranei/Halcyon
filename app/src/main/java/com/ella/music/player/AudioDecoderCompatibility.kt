package com.ella.music.player

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/** Xiaomi's Codec2 FLAC path can clamp input buffers to 32 KiB even when STREAMINFO requests
 * more. Media3 then skips every oversized frame, never producing PCM or a fatal player error.
 * Decode large-frame FLAC in software in every mode; ordinary FLAC and Dolby keep their order. */
@UnstableApi
internal fun needsCompatibleSoftwareAudioDecoder(format: Format): Boolean = when (format.sampleMimeType) {
    MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L1, MimeTypes.AUDIO_MPEG_L2 -> true
    MimeTypes.AUDIO_FLAC -> format.maxInputSize > 32 * 1024
    else -> false
}
