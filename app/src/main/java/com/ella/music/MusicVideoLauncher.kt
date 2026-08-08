package com.ella.music

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ella.music.data.model.Song
import com.ella.music.ui.player.DynamicCoverSource
import org.json.JSONObject
import java.io.File

/** Intent bridge for the audible MV opened from the song-detail page. */
internal object MusicVideoLauncher {
    private const val EXTRA_SONG = "music_video_song"
    private const val EXTRA_VIDEO_URI = "music_video_uri"
    private const val EXTRA_VIDEO_KEY = "music_video_key"
    private const val EXTRA_VIDEO_ASPECT_RATIO = "music_video_aspect_ratio"

    fun open(context: Context, song: Song?, source: DynamicCoverSource) {
        val resolvedSong = song ?: return
        context.startActivity(
            Intent(context, MusicVideoActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_SONG, resolvedSong.toMusicVideoJson().toString())
                .putExtra(EXTRA_VIDEO_URI, source.uri.toString())
                .putExtra(EXTRA_VIDEO_KEY, source.failureKey)
                .putExtra(EXTRA_VIDEO_ASPECT_RATIO, source.aspectRatio ?: 0f)
        )
    }

    fun songFrom(intent: Intent): Song? = intent.getStringExtra(EXTRA_SONG)
        ?.let(::JSONObject)
        ?.toMusicVideoSong()

    fun sourceUriFrom(intent: Intent): Uri? = intent.getStringExtra(EXTRA_VIDEO_URI)
        ?.takeIf { it.isNotBlank() }
        ?.let(Uri::parse)

    fun sourceAspectRatioFrom(intent: Intent): Float? =
        intent.getFloatExtra(EXTRA_VIDEO_ASPECT_RATIO, 0f).takeIf { it > 0f }

    fun share(context: Context, source: Uri, label: String) {
        val shareUri = source.asShareUri(context)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, label, shareUri)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(com.ella.music.R.string.common_share)))
    }

    fun share(context: Context, sources: List<Uri>, label: String) {
        val shareUris = ArrayList(sources.distinct().map { it.asShareUri(context) })
        if (shareUris.isEmpty()) return
        if (shareUris.size == 1) {
            share(context, shareUris.first(), label)
            return
        }
        val clips = ClipData.newUri(context.contentResolver, label, shareUris.first()).apply {
            shareUris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = clips
        }
        context.startActivity(Intent.createChooser(intent, context.getString(com.ella.music.R.string.common_share)))
    }

    private fun Uri.asShareUri(context: Context): Uri {
        if (!scheme.equals("file", ignoreCase = true)) return this
        val file = File(path.orEmpty())
        return if (file.exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            this
        }
    }

    private fun Song.toMusicVideoJson(): JSONObject = JSONObject()
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
        .put("coverUrl", coverUrl)

    private fun JSONObject.toMusicVideoSong(): Song = Song(
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
        coverUrl = optString("coverUrl")
    )
}
