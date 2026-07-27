package com.ella.music.ui.components

import android.content.Context
import android.content.Intent
import com.ella.music.SpectrumViewerActivity
import com.ella.music.data.model.Song
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/** Small intent bridge for the standalone spectrum activity. */
internal object SpectrumViewerLauncher {
    const val BUILTIN = "builtin"
    const val ASPECT_PRO = "aspect_pro"
    const val KASPEK = "kaspek"
    private const val EXTRA_SONG = "spectrum_song"

    fun createIntent(context: Context, song: Song): Intent = Intent(context, SpectrumViewerActivity::class.java)
        .putExtra(EXTRA_SONG, song.toSpectrumJson().toString())

    fun songFrom(intent: Intent): Song? = intent.getStringExtra(EXTRA_SONG)
        ?.let(::JSONObject)
        ?.toSpectrumSong()

    suspend fun openSelected(context: Context, song: Song) {
        when (SettingsManager.getInstance(context).spectrumViewerId.first()) {
            ASPECT_PRO -> openSongSpectrumWithAspectPro(context, song)
            KASPEK -> openSongSpectrumWithKaspek(context, song)
            else -> context.startActivity(createIntent(context, song))
        }
    }

    private fun Song.toSpectrumJson(): JSONObject = JSONObject()
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

    private fun JSONObject.toSpectrumSong(): Song = Song(
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
        dateModified = optLong("dateModified")
    )
}
