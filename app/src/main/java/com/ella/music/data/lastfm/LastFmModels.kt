package com.ella.music.data.lastfm

import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.PlaybackHistorySource
import com.ella.music.data.model.Song
import java.security.MessageDigest
import java.util.Locale

/** The three history views exposed to the user. */
enum class ListeningHistorySource(val preferenceValue: Int) {
    Local(0),
    LastFm(1),
    Combined(2);

    val usesLocal: Boolean get() = this != LastFm
    val usesLastFm: Boolean get() = this != Local

    companion object {
        fun fromPreference(value: Int): ListeningHistorySource =
            entries.firstOrNull { it.preferenceValue == value } ?: Local
    }
}

data class LastFmCredentials(
    val apiKey: String = "",
    val sharedSecret: String = "",
    val username: String = "",
    val sessionKey: String = "",
    val pendingToken: String = ""
) {
    val hasAppCredentials: Boolean
        get() = apiKey.isNotBlank() && sharedSecret.isNotBlank()
    val isAuthorized: Boolean
        get() = hasAppCredentials && username.isNotBlank() && sessionKey.isNotBlank()
}

data class LastFmTrack(
    val title: String,
    val artist: String,
    val album: String,
    val playedAt: Long,
    val durationMs: Long = 0L,
    val albumArtUrl: String = "",
    val mbid: String = ""
) {
    val cacheKey: String
        get() = listOf(title, artist, album, playedAt.toString()).joinToString("|") { it.lastFmKeyPart() }

    fun toPlaybackHistoryEntry(): PlaybackHistoryEntry = PlaybackHistoryEntry(
        // Last.fm's cache key includes the play time and remains stable after a process restart,
        // so it can also be used for a local "hide this bad record" decision.
        entryId = "lastfm:$cacheKey",
        songId = stableLastFmSongId(title, artist, album),
        title = title,
        artist = artist,
        album = album,
        playedAt = playedAt,
        durationMs = durationMs,
        source = PlaybackHistorySource.LAST_FM
    )
}

data class LastFmRecentTracksPage(
    val tracks: List<LastFmTrack>,
    val page: Int,
    val totalPages: Int,
    val totalTracks: Int
)

data class LastFmSession(
    val username: String,
    val sessionKey: String
)

data class LastFmPendingScrobble(
    val id: String,
    val artist: String,
    val track: String,
    val album: String,
    val durationSeconds: Int,
    val startedAt: Long
)

sealed interface LastFmSyncStatus {
    data object Idle : LastFmSyncStatus
    data class Syncing(
        val page: Int,
        val totalPages: Int,
        val receivedTracks: Int
    ) : LastFmSyncStatus
    data class Complete(
        val totalTracks: Int,
        val syncedAt: Long
    ) : LastFmSyncStatus
    data class Failed(val message: String) : LastFmSyncStatus
}

class LastFmApiException(
    val code: Int,
    override val message: String
) : IllegalStateException(message) {
    val isRetryable: Boolean get() = code == 11 || code == 16 || code == 29
    val requiresReauthentication: Boolean get() = code == 9
}

internal fun Song.toLastFmPendingScrobble(startedAt: Long): LastFmPendingScrobble? {
    val safeTitle = title.trim()
    val safeArtist = artist.trim()
    if (safeTitle.isBlank() || safeArtist.isBlank()) return null
    val safeDurationSeconds = (duration / 1_000L).toInt().coerceAtLeast(0)
    return LastFmPendingScrobble(
        id = listOf(safeTitle, safeArtist, album.trim(), startedAt).joinToString("|").md5(),
        artist = safeArtist,
        track = safeTitle,
        album = album.trim(),
        durationSeconds = safeDurationSeconds,
        startedAt = startedAt
    )
}

internal fun stableLastFmSongId(title: String, artist: String, album: String): Long {
    val hash = listOf(title, artist, album).joinToString("|") { it.lastFmKeyPart() }
        .md5()
        .take(15)
        .toLongOrNull(16)
        ?: 0L
    // Keep synthetic remote ids negative so they cannot collide with MediaStore ids.
    return -(hash.coerceAtLeast(1L))
}

internal fun String.lastFmKeyPart(): String =
    trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

internal fun String.md5(): String = MessageDigest.getInstance("MD5")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
