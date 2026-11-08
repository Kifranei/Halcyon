package com.ella.music.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.OutputStream
import java.util.Locale

internal enum class ExportImageKind(val directory: String) {
    Artist("artist_images"), Album("album_images"), Song("song_images"), MvScreenshot("mv_screenshots")
}

internal fun sanitizeMediaExportName(value: String, fallback: String = "media"): String {
    val replacements = mapOf('/' to '／', '\\' to '＼', ':' to '：', '*' to '＊', '?' to '？',
        '|' to '｜', '<' to '〈', '>' to '〉', '"' to '\'')
    val clean = value.map { replacements[it] ?: if (it.isISOControl()) ' ' else it }.joinToString("")
        .trim().trim('.')
    val result = StringBuilder()
    var bytes = 0
    val codePoints = clean.codePoints().iterator()
    while (codePoints.hasNext()) {
        val next = String(Character.toChars(codePoints.nextInt()))
        val size = next.toByteArray(Charsets.UTF_8).size
        if (bytes + size > 210) break
        bytes += size; result.append(next)
    }
    return result.toString().ifBlank { fallback }
}

internal fun musicVideoExportName(template: String, artist: String, title: String, album: String,
    quality: String, mvId: String): String {
    val fields = mapOf("artist" to artist, "title" to title, "album" to album, "quality" to quality, "mvId" to mvId)
    val text = Regex("\\{(artist|title|album|quality|mvId)\\}").replace(template.ifBlank { "{artist} - {title}" }) {
        fields.getValue(it.groupValues[1])
    }
    return sanitizeMediaExportName(text, "mv-$mvId") + ".mp4"
}

internal fun uniqueMediaExportName(requested: String, existing: Collection<String>): String {
    val names = existing.map { it.lowercase(Locale.ROOT) }.toHashSet()
    val stem = requested.substringBeforeLast('.', requested)
    val extension = requested.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
    var candidate = requested
    var suffix = 1
    while (candidate.lowercase(Locale.ROOT) in names || (candidate + ".source").lowercase(Locale.ROOT) in names) {
        candidate = "$stem (${suffix++})$extension"
    }
    return candidate
}

/** Keep image types separate; user-selected SAF directories are written without replacing files. */
internal object MediaExportStorage {
    private val lock = Any()
    fun write(context: Context, name: String, mime: String, relativePath: String, targetFolder: String = "",
        onPending: (Uri) -> Unit = {}, output: (OutputStream) -> Unit): Uri = synchronized(lock) {
        if (targetFolder.startsWith("content://")) {
            val folder = requireNotNull(DocumentFile.fromTreeUri(context, Uri.parse(targetFolder)))
            val unique = uniqueMediaExportName(name, folder.listFiles().mapNotNull { it.name })
            val file = requireNotNull(folder.createFile(mime, unique))
            try {
                requireNotNull(context.contentResolver.openOutputStream(file.uri)).use(output)
                file.uri
            } catch (error: Throwable) { file.delete(); throw error }
        } else if (targetFolder.isNotBlank()) {
            val folder = File(targetFolder.removePrefix("file://"))
            check(folder.isDirectory || folder.mkdirs())
            val unique = uniqueMediaExportName(name, folder.list().orEmpty().toList())
            val file = File(folder, unique)
            check(file.createNewFile())
            try { file.outputStream().use(output); Uri.fromFile(file) }
            catch (error: Throwable) { file.delete(); throw error }
        } else {
            val collection = when {
                mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                mime.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val names = context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf(relativePath), null)?.use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }.orEmpty()
            val unique = uniqueMediaExportName(name, names)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, unique)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = requireNotNull(context.contentResolver.insert(collection, values))
            try {
                onPending(uri)
                requireNotNull(context.contentResolver.openOutputStream(uri)).use(output)
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                uri
            } catch (error: Throwable) { context.contentResolver.delete(uri, null, null); throw error }
        }
    }
}
