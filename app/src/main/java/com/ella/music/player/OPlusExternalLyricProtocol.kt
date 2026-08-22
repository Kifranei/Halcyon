package com.ella.music.player

import com.ella.music.data.model.Song
import java.util.Locale

/**
 * Direct v4 SystemUI broadcast contract used by ColorOS-Live-Lyrics-Bridge.
 *
 * Halcyon sends this itself (no extra Provider APK). Bridge still has to admit
 * `lyricprovider/halcyon` -> `com.ella.music` in its static registry.
 */
internal object OPlusExternalLyricProtocol {
    const val ACTION_DIRECT_V4 =
        "io.github.andrealtb.lockscreenlyrics.action.EXTERNAL_LYRIC_DIRECT_V4"
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val PROTOCOL_VERSION = 4
    const val SOURCE = "lyricprovider/halcyon"
    const val PLAYER_PACKAGE = "com.ella.music"
    const val SENDER_KIND_PROVIDER = "provider"
    const val EVENT_TRACK_CHANGED = "trackChanged"
    const val EVENT_LYRIC_READY = "lyricReady"

    const val EXTRA_PROTOCOL_VERSION = "protocolVersion"
    const val EXTRA_SOURCE = "source"
    const val EXTRA_PLAYER_PACKAGE = "playerPackage"
    const val EXTRA_SENDER_PACKAGE = "senderPackage"
    const val EXTRA_SENDER_KIND = "senderKind"
    const val EXTRA_CAPABILITIES = "capabilities"
    const val EXTRA_MATCH_POLICY = "matchPolicy"
    const val EXTRA_IDENTITY_CONFIDENCE = "identityConfidence"
    const val EXTRA_EVENT_TYPE = "eventType"
    const val EXTRA_TRACK_GENERATION = "trackGeneration"
    const val EXTRA_REQUEST_ID = "requestId"
    const val EXTRA_MEDIA_ID = "mediaId"
    const val EXTRA_MEDIA_URI = "mediaUri"
    const val EXTRA_TRACK_KEY = "trackKey"
    const val EXTRA_SONG_NAME = "songName"
    const val EXTRA_ARTIST = "artist"
    const val EXTRA_DURATION = "duration"
    const val EXTRA_LYRIC = "lyric"
    const val EXTRA_RAW_LYRIC = "rawLyric"
    const val EXTRA_TRANSLATION_LYRIC = "translationLyric"
    const val EXTRA_CAPTURED_AT = "capturedAt"
    const val EXTRA_LYRIC_INFO = "lyricInfo"

    const val CAPABILITIES =
        "trackGeneration,currentTrackAuthority,titleOnlyFallback,translationToggle"
    const val MATCH_POLICY_TITLE_ONLY = "titleOnly"
    const val IDENTITY_CONFIDENCE_CURRENT_TRACK = "currentTrack"

    fun trackKey(song: Song): String {
        val title = song.title.oplusTrackKeyPart()
        val artist = song.artist.oplusTrackKeyPart()
        return if (artist.isBlank()) title else "$title|$artist"
    }

    fun mediaId(song: Song): String = when {
        song.onlineSource.isNotBlank() && song.onlineId.isNotBlank() ->
            "${song.onlineSource}:${song.onlineId}"
        song.id > 0L -> song.id.toString()
        song.path.isNotBlank() -> song.path
        else -> trackKey(song)
    }

    fun mediaUri(song: Song): String = song.path

    fun matchPolicy(song: Song): String =
        if (song.artist.isBlank()) MATCH_POLICY_TITLE_ONLY else ""

    private fun String.oplusTrackKeyPart(): String =
        trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}

internal class OPlusExternalLyricSession {
    private var lastSongKey: String? = null
    private var lastLyricFingerprint: String? = null
    var generation: Long = 0L
        private set

    fun onSong(songKey: String): Boolean {
        if (lastSongKey == songKey) return false
        lastSongKey = songKey
        lastLyricFingerprint = null
        generation += 1L
        return true
    }

    fun shouldSendLyricReady(songKey: String, lyricInfoJson: String, force: Boolean): Boolean {
        if (lyricInfoJson.isBlank()) return false
        val fingerprint = "$songKey:$generation:$lyricInfoJson"
        if (!force && fingerprint == lastLyricFingerprint) return false
        lastLyricFingerprint = fingerprint
        return true
    }

    fun clear() {
        lastSongKey = null
        lastLyricFingerprint = null
    }
}
