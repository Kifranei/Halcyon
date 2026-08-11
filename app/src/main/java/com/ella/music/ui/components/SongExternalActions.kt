package com.ella.music.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.net.Uri
import android.os.StrictMode
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import android.view.View
import androidx.core.content.FileProvider
import com.ella.music.R
import com.ella.music.data.isContentAudioSource
import com.ella.music.data.isHttpAudioSource
import com.ella.music.data.model.Song
import java.io.File
import kotlin.math.roundToInt

private const val ASPECT_PRO_PACKAGE = "com.andrewkhandr.aspectpro"
private const val ASPECT_PRO_ACTIVITY = "com.andrewkhandr.aspectpro.MainActivity"
private const val KASPEK_PACKAGE = "ka.spek"
private const val HEARUSY_PACKAGE = "com.hearusy.spectrum"
private const val HEARUSY_ACTIVITY = "com.hearusy.spectrum.MainActivity"
private const val MEDIA_INFO_PACKAGE = "net.mediaarea.mediainfo"
private const val MEDIA_INFO_ACTIVITY = "net.mediaarea.mediainfo.ReportListActivity"
private const val TAG = "SongExternalActions"

private fun independentExternalTaskFlags(): Int =
    Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

fun openSongSpectrumWithAspectPro(context: Context, song: Song) {
    openSongSpectrumWithExternalApp(context, song, SpectrumExternalApp.AspectPro)
}

fun openSongSpectrumWithKaspek(context: Context, song: Song) {
    openSongSpectrumWithExternalApp(context, song, SpectrumExternalApp.Kaspek)
}

fun openSongSpectrumWithHearusy(context: Context, song: Song) {
    openSongSpectrumWithExternalApp(context, song, SpectrumExternalApp.Hearusy)
}

private enum class SpectrumExternalApp {
    AspectPro,
    Kaspek,
    Hearusy
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
            launchIntent.addFlags(independentExternalTaskFlags())
            context.startActivity(launchIntent)
        }
        return
    }

    val uri = song.aspectProUri(context)
    val mimeType = song.aspectMimeType()
    val (targetPackage, targetActivity, failureMessage) = when (target) {
        SpectrumExternalApp.AspectPro -> Triple(
            ASPECT_PRO_PACKAGE,
            ASPECT_PRO_ACTIVITY,
            R.string.aspect_pro_open_failed
        )
        SpectrumExternalApp.Hearusy -> Triple(
            HEARUSY_PACKAGE,
            HEARUSY_ACTIVITY,
            R.string.hearusy_open_failed
        )
        SpectrumExternalApp.Kaspek -> error("Kaspek is handled above")
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        component = ComponentName(targetPackage, targetActivity)
        setDataAndType(uri, mimeType)
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra("android.intent.extra.STREAM", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(independentExternalTaskFlags())
        clipData = ClipData.newUri(context.contentResolver, song.title, uri)
    }
    runCatching {
        allowFileUriForLegacyAudioApp(uri)
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, context.getString(failureMessage), Toast.LENGTH_SHORT).show()
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
        setDataAndType(uri, song.shareMimeType())
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(independentExternalTaskFlags())
        clipData = ClipData.newUri(context.contentResolver, song.title, uri)
    }
    val packageManager = context.packageManager
    val resolved = packageManager.queryIntentActivities(intent, 0)
        .firstOrNull { it.activityInfo.packageName == MEDIA_INFO_PACKAGE }
        ?.activityInfo
    val launchIntent = if (resolved != null) {
        Intent(intent).setComponent(ComponentName(resolved.packageName, resolved.name))
    } else {
        Intent(intent).setComponent(ComponentName(MEDIA_INFO_PACKAGE, MEDIA_INFO_ACTIVITY))
    }
    launchIntent.addFlags(independentExternalTaskFlags())
    runCatching { context.startActivity(launchIntent) }
        .recoverCatching {
            context.packageManager.getLaunchIntentForPackage(MEDIA_INFO_PACKAGE)
                ?.also { fallback ->
                    fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    fallback.addFlags(independentExternalTaskFlags())
                    fallback.putExtra(Intent.EXTRA_STREAM, uri)
                    fallback.setDataAndType(uri, song.shareMimeType())
                    context.startActivity(fallback)
                }
                ?: error("MediaInfo is not installed")
        }
        .onFailure {
        Toast.makeText(context, context.getString(R.string.media_info_open_failed), Toast.LENGTH_SHORT).show()
    }
}

/** Opens a local or document-backed video in the installed MediaInfo viewer. */
fun openVideoWithMediaInfo(context: Context, uri: Uri, title: String, mimeType: String = "video/*") {
    val shareUri = uri.externalShareUri(context)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(shareUri, mimeType.ifBlank { "video/*" })
        putExtra(Intent.EXTRA_STREAM, shareUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(independentExternalTaskFlags())
        clipData = ClipData.newUri(context.contentResolver, title, shareUri)
    }
    val packageManager = context.packageManager
    val resolved = packageManager.queryIntentActivities(intent, 0)
        .firstOrNull { it.activityInfo.packageName == MEDIA_INFO_PACKAGE }
        ?.activityInfo
    val launchIntent = if (resolved != null) {
        Intent(intent).setComponent(ComponentName(resolved.packageName, resolved.name))
    } else {
        Intent(intent).setComponent(ComponentName(MEDIA_INFO_PACKAGE, MEDIA_INFO_ACTIVITY))
    }
    launchIntent.addFlags(independentExternalTaskFlags())
    runCatching { context.startActivity(launchIntent) }
        .recoverCatching {
            context.packageManager.getLaunchIntentForPackage(MEDIA_INFO_PACKAGE)
                ?.also { fallback ->
                    fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    fallback.addFlags(independentExternalTaskFlags())
                    fallback.putExtra(Intent.EXTRA_STREAM, shareUri)
                    fallback.setDataAndType(shareUri, mimeType.ifBlank { "video/*" })
                    context.startActivity(fallback)
                }
                ?: error("MediaInfo is not installed")
        }
        .onFailure {
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

/**
 * Starts a global drag containing the currently selected local audio files.
 *
 * A drag source must expose content URIs rather than raw file paths so the target window can
 * read the files after the drag crosses an application boundary. For a raw local path, prefer a
 * FileProvider URI: QQ and other window targets can request the temporary grant without needing
 * access to our MediaStore query result.
 */
fun startDraggingLocalSongs(sourceView: View, context: Context, songs: List<Song>): Boolean {
    val draggedSongs = songs
        .asSequence()
        .filterNot { it.path.isHttpAudioSource() }
        .mapNotNull { song ->
            runCatching { song.dragShareUri(context) }
                .getOrNull()
                ?.takeIf { it.isReadableForDrag(context) }
                ?.let { uri -> song to uri }
        }
        .toList()
    if (draggedSongs.isEmpty()) return false
    val uris = draggedSongs.map { it.second }.distinct()
    val mimeTypes = draggedSongs
        .map { (song, uri) -> context.contentResolver.getType(uri) ?: song.shareMimeType() }
        .filter { it.startsWith("audio/") }
        .ifEmpty { listOf("audio/*") }
        .distinct()
        .toTypedArray()

    // Keep the first item URI-only, matching Xiaomi's arbitrary-file example. QQ's drop target
    // checks the first item as an audio URI and silently ignores a text-first or mixed item.
    val firstSong = draggedSongs.first().first
    val firstLabel = listOf(firstSong.title.trim(), firstSong.artist.trim())
        .filter(String::isNotBlank)
        .joinToString(" - ")
        .ifBlank { firstSong.fileName.trim() }
    val clipData = ClipData(
        ClipDescription(firstLabel.ifBlank { "Halcyon audio" }, mimeTypes),
        ClipData.Item(uris.first())
    )
    uris.drop(1).forEach { uri -> clipData.addItem(ClipData.Item(uri)) }
    val started = sourceView.startDragAndDrop(
        clipData,
        SongDragShadowBuilder(context, uris.size, draggedSongs.first().first.title),
        null,
        View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
    )
    Log.d(TAG, "Global song drag started=$started count=${uris.size} mime=${mimeTypes.joinToString()}")
    return started
}

/** A compact drag card keeps Compose's root view from being used as the drag preview. */
private class SongDragShadowBuilder(
    context: Context,
    private val songCount: Int,
    private val firstTitle: String
) : View.DragShadowBuilder() {
    private val density = context.resources.displayMetrics.density
    private val width = (280f * density).roundToInt()
    private val height = (64f * density).roundToInt()
    private val radius = 14f * density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(38, 40, 48)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15f * density
        isFakeBoldText = true
    }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
        textSize = 12f * density
    }

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(width, height)
        outShadowTouchPoint.set(width / 2, height / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, backgroundPaint)
        val title = firstTitle.trim().ifBlank { "Halcyon" }.take(22)
        canvas.drawText(title, 18f * density, 27f * density, titlePaint)
        canvas.drawText("$songCount 首歌曲", 18f * density, 49f * density, countPaint)
    }
}

private fun Uri.isReadableForDrag(context: Context): Boolean {
    if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) return true
    return runCatching {
        context.contentResolver.openAssetFileDescriptor(this, "r")?.use { true } ?: false
    }.getOrDefault(false)
}

private fun Uri.externalShareUri(context: Context): Uri {
    if (!scheme.equals("file", ignoreCase = true)) return this
    val file = File(path.orEmpty())
    return if (file.exists()) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } else {
        this
    }
}

private fun Song.localShareUri(context: Context): Uri {
    if (path.isContentAudioSource()) {
        return Uri.parse(path)
    }
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

private fun Song.dragShareUri(context: Context): Uri {
    if (path.isContentAudioSource()) return Uri.parse(path)

    // Prefer the system MediaStore URI when the file is indexed. QQ and other targets commonly
    // accept this provider directly; fall back to our FileProvider for files outside MediaStore.
    mediaStoreUriByPath(context)?.let { return it }

    val file = when {
        path.startsWith("file://", ignoreCase = true) -> {
            runCatching { File(Uri.parse(path).path.orEmpty()) }.getOrNull()
        }

        else -> File(path)
    }
    file?.takeIf { it.isFile }?.let { localFile ->
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
        }.getOrNull()?.let { return it }
    }
    return localShareUri(context)
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
