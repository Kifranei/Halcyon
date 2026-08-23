package com.ella.music.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ella.music.LyricTimingEditorActivity
import com.ella.music.data.ExternalUriResolver
import com.ella.music.data.isContentAudioSource
import com.ella.music.data.model.Song
import java.io.File
import org.json.JSONObject

/** Launches the editor in the current task without making [Song] Parcelable. */
internal object LyricTimingEditorLauncher {
    private const val EXTRA_SONG = "lyric_timing_song"

    fun createIntent(context: Context, song: Song): Intent = Intent(context, LyricTimingEditorActivity::class.java)
        .putExtra(EXTRA_SONG, song.toEditorJson().toString())

    fun songFrom(context: Context, intent: Intent): Song? = runCatching {
        intent.getStringExtra(EXTRA_SONG)?.let(::JSONObject)?.toSong()
    }.getOrNull() ?: context.songFromExternalAudioToolIntent(intent)

    fun isExternalLaunch(intent: Intent): Boolean = !intent.hasExtra(EXTRA_SONG)

    suspend fun lyricsReadSongFromExternal(context: Context, intent: Intent, song: Song): Song {
        if (!isExternalLaunch(intent) || !song.path.isContentAudioSource()) return song
        val resolved = ExternalUriResolver(context).resolveForPlayback(
            uri = Uri.parse(song.path),
            grantFlags = intent.flags,
            preferredName = song.fileName
        )
        val readPath = if (resolved.playbackUri.scheme.equals("file", ignoreCase = true)) {
            resolved.playbackUri.path.orEmpty()
        } else {
            resolved.playbackUri.toString()
        }
        return song.copy(
            path = readPath,
            fileSize = if (resolved.copiedToCache) File(readPath).length() else song.fileSize
        )
    }

    private fun Song.toEditorJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("albumId", albumId)
        .put("duration", duration)
        .put("path", path)
        .put("fileName", fileName)
        .put("fileSize", fileSize)
        .put("mimeType", mimeType)
        .put("dateAdded", dateAdded)
        .put("dateModified", dateModified)
        .put("trackNumber", trackNumber)
        .put("discNumber", discNumber)
        .put("albumArtist", albumArtist)
        .put("genre", genre)
        .put("year", year)
        .put("composer", composer)
        .put("arranger", arranger)
        .put("lyricist", lyricist)
        .put("coverUrl", coverUrl)
        .put("onlineSource", onlineSource)
        .put("onlineId", onlineId)
        .put("onlineLyrics", onlineLyrics)
        .put("onlineLyricTranslation", onlineLyricTranslation)

    private fun JSONObject.toSong(): Song = Song(
        id = optLong("id"),
        title = optString("title"),
        artist = optString("artist"),
        album = optString("album"),
        albumId = optLong("albumId"),
        duration = optLong("duration"),
        path = optString("path"),
        fileName = optString("fileName"),
        fileSize = optLong("fileSize"),
        mimeType = optString("mimeType"),
        dateAdded = optLong("dateAdded"),
        dateModified = optLong("dateModified"),
        trackNumber = optInt("trackNumber"),
        discNumber = optInt("discNumber"),
        albumArtist = optString("albumArtist"),
        genre = optString("genre"),
        year = optString("year"),
        composer = optString("composer"),
        arranger = optString("arranger"),
        lyricist = optString("lyricist"),
        coverUrl = optString("coverUrl"),
        onlineSource = optString("onlineSource"),
        onlineId = optString("onlineId"),
        onlineLyrics = optString("onlineLyrics"),
        onlineLyricTranslation = optString("onlineLyricTranslation")
    )
}
