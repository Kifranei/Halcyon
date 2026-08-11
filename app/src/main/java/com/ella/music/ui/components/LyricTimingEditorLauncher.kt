package com.ella.music.ui.components

import android.content.Context
import android.content.Intent
import com.ella.music.LyricTimingEditorActivity
import com.ella.music.data.model.Song
import org.json.JSONObject

/** Launches the editor in the current task without making [Song] Parcelable. */
internal object LyricTimingEditorLauncher {
    private const val EXTRA_SONG = "lyric_timing_song"

    fun createIntent(context: Context, song: Song): Intent = Intent(context, LyricTimingEditorActivity::class.java)
        .putExtra(EXTRA_SONG, song.toEditorJson().toString())

    fun songFrom(intent: Intent): Song? = intent.getStringExtra(EXTRA_SONG)
        ?.let(::JSONObject)
        ?.toSong()

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
