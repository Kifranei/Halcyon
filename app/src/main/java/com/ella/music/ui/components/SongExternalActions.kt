package com.ella.music.ui.components

import android.content.ClipData
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ella.music.R
import com.ella.music.data.isContentAudioSource
import com.ella.music.data.isHttpAudioSource
import com.ella.music.data.model.Song
import java.io.File

private const val ASPECT_PRO_PACKAGE = "com.andrewkhandr.aspectpro"
private const val ASPECT_PRO_ACTIVITY = "com.andrewkhandr.aspectpro.MainActivity"
private const val KASPEK_PACKAGE = "ka.spek"
private const val MEDIA_INFO_PACKAGE = "net.mediaarea.mediainfo"
private const val MEDIA_INFO_ACTIVITY = "net.mediaarea.mediainfo.ReportListActivity"

fun openSongSpectrumWithAspectPro(context: Context, song: Song) {
    openSongSpectrumWithExternalApp(context, song, SpectrumExternalApp.AspectPro)
}

fun openSongSpectrumWithKaspek(context: Context, song: Song) {
    openSongSpectrumWithExternalApp(context, song, SpectrumExternalApp.Kaspek)
}

private enum class SpectrumExternalApp {
    AspectPro,
    Kaspek
}

private fun openSongSpectrumWithExternalApp(
    context: Context,
    song: Song,
    target: SpectrumExternalApp
) {
    if (song.path.isHttpAudioSource()) {
        Toast.makeText(context, context.getString(R.string.aspect_pro_requires_local_audio), Toast.LENGTH_SHORT).show()
        return
    }
    if (target == SpectrumExternalApp.Kaspek) {
        // Kaspek does not declare an ACTION_VIEW intent filter, so an external file URI cannot
        // be delivered to it. Launch its public entry point instead of showing a false failure.
        val launchIntent = context.packageManager.getLaunchIntentForPackage(KASPEK_PACKAGE)
        if (launchIntent == null) {
            Toast.makeText(context, context.getString(R.string.kaspek_open_failed), Toast.LENGTH_SHORT).show()
        } else {
            context.startActivity(launchIntent)
        }
        return
    }

    val uri = song.aspectProUri(context)
    val mimeType = song.aspectMimeType()
    val intent = Intent(Intent.ACTION_VIEW).apply {
        component = ComponentName(ASPECT_PRO_PACKAGE, ASPECT_PRO_ACTIVITY)
        setDataAndType(uri, mimeType)
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra("android.intent.extra.STREAM", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, song.title, uri)
    }
    runCatching {
        allowFileUriForLegacyAudioApp(uri)
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.aspect_pro_open_failed), Toast.LENGTH_SHORT).show()
    }
}

/** Opens the installed MediaInfo viewer with the same scoped-storage read grant. */
fun openSongWithMediaInfo(context: Context, song: Song) {
    if (song.path.isHttpAudioSource()) {
        Toast.makeText(context, context.getString(R.string.aspect_pro_requires_local_audio), Toast.LENGTH_SHORT).show()
        return
    }
    val uri = song.localShareUri(context)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        component = ComponentName(MEDIA_INFO_PACKAGE, MEDIA_INFO_ACTIVITY)
        setDataAndType(uri, song.shareMimeType())
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, song.title, uri)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, context.getString(R.string.media_info_open_failed), Toast.LENGTH_SHORT).show()
    }
}

fun shareLocalSong(context: Context, song: Song) {
    if (song.path.isHttpAudioSource()) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${song.title} - ${song.artist}\n${song.path}")
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_song)))
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.share_no_available_app), Toast.LENGTH_SHORT).show()
        }
        return
    }

    val uri = song.localShareUri(context)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = song.shareMimeType()
        putExtra(Intent.EXTRA_TITLE, "${song.title} - ${song.artist}")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, song.title, uri)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_song)))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.share_no_available_app), Toast.LENGTH_SHORT).show()
    }
}

fun shareLocalSongs(context: Context, songs: List<Song>) {
    val local = songs.filterNot { it.path.isHttpAudioSource() }
    if (local.size <= 1) {
        songs.firstOrNull()?.let { shareLocalSong(context, it) }
        return
    }
    val uris = ArrayList<Uri>()
    local.forEach { song -> runCatching { uris.add(song.localShareUri(context)) } }
    if (uris.isEmpty()) {
        songs.firstOrNull()?.let { shareLocalSong(context, it) }
        return
    }
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "audio/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_song)))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.share_no_available_app), Toast.LENGTH_SHORT).show()
    }
}

private fun Song.localShareUri(context: Context): Uri {
    mediaStoreUriByPath(context)?.let { return it }
    return runCatching {
        val file = File(path)
        if (file.exists() && file.isFile) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        }
    }.getOrElse {
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    }
}

private fun Song.aspectProUri(context: Context): Uri {
    mediaStoreUriByPath(context)?.let { return it }
    val file = File(path)
    if (file.exists() && file.isFile) return Uri.fromFile(file)
    return localShareUri(context)
}

@Suppress("DEPRECATION")
private fun Song.mediaStoreUriByPath(context: Context): Uri? {
    val filePath = path.takeIf { it.isNotBlank() && !it.isContentAudioSource() } ?: return null
    return runCatching {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.MediaColumns.DATA}=?",
            arrayOf(filePath),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0)
                )
            } else {
                null
            }
        }
    }.getOrNull()
}

private fun Song.shareMimeType(): String {
    val declaredMime = mimeType.trim().lowercase()
    if (declaredMime.startsWith("audio/")) return declaredMime

    val lowerName = fileName
        .ifBlank { path.substringAfterLast('/') }
        .substringBefore('?')
        .lowercase()
    return when {
        lowerName.endsWith(".mp3") -> "audio/mpeg"
        lowerName.endsWith(".flac") -> "audio/flac"
        lowerName.endsWith(".m4a") || lowerName.endsWith(".alac") -> "audio/mp4"
        lowerName.endsWith(".ogg") || lowerName.endsWith(".oga") -> "audio/ogg"
        lowerName.endsWith(".opus") -> "audio/opus"
        lowerName.endsWith(".wav") || lowerName.endsWith(".wave") -> "audio/wav"
        lowerName.endsWith(".ape") -> "audio/x-ape"
        else -> "audio/*"
    }
}

private fun Song.aspectMimeType(): String {
    val lowerName = fileName.ifBlank { path.substringAfterLast('/') }.lowercase()
    return when {
        lowerName.endsWith(".flac") -> "application/flac"
        lowerName.endsWith(".ape") -> "application/ape"
        lowerName.endsWith(".ogg") || lowerName.endsWith(".oga") -> "application/ogg"
        lowerName.endsWith(".mp3") -> "application/mpeg"
        lowerName.endsWith(".m4a") || lowerName.endsWith(".alac") -> "application/itunes"
        mimeType.isNotBlank() -> mimeType
        else -> "audio/*"
    }
}

private fun allowFileUriForLegacyAudioApp(uri: Uri) {
    if (uri.scheme != "file") return
    runCatching {
        StrictMode::class.java
            .getMethod("disableDeathOnFileUriExposure")
            .invoke(null)
    }
}
