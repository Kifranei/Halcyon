package com.ella.music.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.ella.music.data.model.Song

private const val EXTRA_ID = "ella_song_id"
private const val EXTRA_TITLE = "ella_song_title"
private const val EXTRA_ARTIST = "ella_song_artist"
private const val EXTRA_ALBUM = "ella_song_album"
private const val EXTRA_ALBUM_ID = "ella_song_album_id"
private const val EXTRA_DURATION = "ella_song_duration"
private const val EXTRA_PATH = "ella_song_path"
private const val EXTRA_FILE_NAME = "ella_song_file_name"
private const val EXTRA_FILE_SIZE = "ella_song_file_size"
private const val EXTRA_MIME_TYPE = "ella_song_mime_type"
private const val EXTRA_DATE_ADDED = "ella_song_date_added"
private const val EXTRA_DATE_MODIFIED = "ella_song_date_modified"
private const val EXTRA_TRACK_NUMBER = "ella_song_track_number"
private const val EXTRA_DISC_NUMBER = "ella_song_disc_number"
private const val EXTRA_ALBUM_ARTIST = "ella_song_album_artist"
private const val EXTRA_GENRE = "ella_song_genre"
private const val EXTRA_YEAR = "ella_song_year"
private const val EXTRA_COMPOSER = "ella_song_composer"
private const val EXTRA_ARRANGER = "ella_song_arranger"
private const val EXTRA_LYRICIST = "ella_song_lyricist"
private const val EXTRA_COVER_URL = "ella_song_cover_url"
private const val EXTRA_ONLINE_SOURCE = "ella_song_online_source"
private const val EXTRA_ONLINE_ID = "ella_song_online_id"
private const val EXTRA_ONLINE_LYRICS = "ella_song_online_lyrics"
private const val EXTRA_ONLINE_LYRIC_TRANSLATION = "ella_song_online_lyric_translation"

internal const val EXTRA_METADATA_PATCH_REASON = "com.ella.music.extra.METADATA_PATCH_REASON"
internal const val PATCH_REASON_NOTIFICATION_ARTWORK = "notification_artwork"
internal const val PATCH_REASON_BASE_SESSION_METADATA = "base_session_metadata"
internal const val PATCH_REASON_SESSION_PRESENTATION = "session_presentation"

internal fun Bundle.markMetadataOnlyPatch(reason: String): Bundle = apply {
    putString(EXTRA_METADATA_PATCH_REASON, reason)
}

internal fun MediaItem.metadataPatchReason(): String? =
    mediaMetadata.extras
        ?.getString(EXTRA_METADATA_PATCH_REASON)
        ?.takeIf { it.isNotBlank() }

internal fun MediaMetadata.metadataPatchReason(): String? =
    extras
        ?.getString(EXTRA_METADATA_PATCH_REASON)
        ?.takeIf { it.isNotBlank() }

internal fun MediaItem.isMetadataOnlyPatch(): Boolean =
    metadataPatchReason() != null

internal fun MediaMetadata.isMetadataOnlyPatch(): Boolean =
    metadataPatchReason() != null

internal fun Song.toMediaItemExtras(): Bundle = Bundle().apply {
    putLong(EXTRA_ID, id)
    putString(EXTRA_TITLE, title)
    putString(EXTRA_ARTIST, artist)
    putString(EXTRA_ALBUM, album)
    putLong(EXTRA_ALBUM_ID, albumId)
    putLong(EXTRA_DURATION, duration)
    putString(EXTRA_PATH, path)
    putString(EXTRA_FILE_NAME, fileName)
    putLong(EXTRA_FILE_SIZE, fileSize)
    putString(EXTRA_MIME_TYPE, mimeType)
    putLong(EXTRA_DATE_ADDED, dateAdded)
    putLong(EXTRA_DATE_MODIFIED, dateModified)
    putInt(EXTRA_TRACK_NUMBER, trackNumber)
    putInt(EXTRA_DISC_NUMBER, discNumber)
    putString(EXTRA_ALBUM_ARTIST, albumArtist)
    putString(EXTRA_GENRE, genre)
    putString(EXTRA_YEAR, year)
    putString(EXTRA_COMPOSER, composer)
    putString(EXTRA_ARRANGER, arranger)
    putString(EXTRA_LYRICIST, lyricist)
    putString(EXTRA_COVER_URL, coverUrl)
    putString(EXTRA_ONLINE_SOURCE, onlineSource)
    putString(EXTRA_ONLINE_ID, onlineId)
    putString(EXTRA_ONLINE_LYRICS, onlineLyrics)
    putString(EXTRA_ONLINE_LYRIC_TRANSLATION, onlineLyricTranslation)
}

internal fun MediaItem.toSongFromMediaItemExtras(): Song? {
    val extras = mediaMetadata.extras ?: return null
    val title = extras.getString(EXTRA_TITLE).orEmpty()
    val artist = extras.getString(EXTRA_ARTIST).orEmpty()
    val path = extras.getString(EXTRA_PATH).orEmpty()

    if (title.isBlank() && path.isBlank()) return null

    return Song(
        id = extras.getLong(EXTRA_ID, mediaId.toLongOrNull() ?: 0L),
        title = title.ifBlank { mediaMetadata.title?.toString().orEmpty() },
        artist = artist.ifBlank { mediaMetadata.artist?.toString().orEmpty() },
        album = extras.getString(EXTRA_ALBUM).orEmpty(),
        albumId = extras.getLong(EXTRA_ALBUM_ID, 0L),
        duration = extras.getLong(EXTRA_DURATION, 0L),
        path = path,
        fileName = extras.getString(EXTRA_FILE_NAME).orEmpty(),
        fileSize = extras.getLong(EXTRA_FILE_SIZE, 0L),
        mimeType = extras.getString(EXTRA_MIME_TYPE).orEmpty(),
        dateAdded = extras.getLong(EXTRA_DATE_ADDED, 0L),
        dateModified = extras.getLong(EXTRA_DATE_MODIFIED, 0L),
        trackNumber = extras.getInt(EXTRA_TRACK_NUMBER, 0),
        discNumber = extras.getInt(EXTRA_DISC_NUMBER, 0),
        albumArtist = extras.getString(EXTRA_ALBUM_ARTIST).orEmpty(),
        genre = extras.getString(EXTRA_GENRE).orEmpty(),
        year = extras.getString(EXTRA_YEAR).orEmpty(),
        composer = extras.getString(EXTRA_COMPOSER).orEmpty(),
        arranger = extras.getString(EXTRA_ARRANGER).orEmpty(),
        lyricist = extras.getString(EXTRA_LYRICIST).orEmpty(),
        coverUrl = extras.getString(EXTRA_COVER_URL).orEmpty(),
        onlineSource = extras.getString(EXTRA_ONLINE_SOURCE).orEmpty(),
        onlineId = extras.getString(EXTRA_ONLINE_ID).orEmpty(),
        onlineLyrics = extras.getString(EXTRA_ONLINE_LYRICS).orEmpty(),
        onlineLyricTranslation = extras.getString(EXTRA_ONLINE_LYRIC_TRANSLATION).orEmpty()
    )
}
