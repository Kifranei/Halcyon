package com.ella.music.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal fun Context.persistImageReadPermission(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

internal suspend fun Context.copyCustomImageIntoApp(uri: Uri, name: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(filesDir, "custom_images").apply { mkdirs() }
        val extension = contentResolver.getType(uri)
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.takeIf { it.isNotBlank() }
            ?: "jpg"
        // Every replacement needs a new path. Reusing "$name.$extension" made currentUri point
        // at the newly copied file, which the picker callback then deleted as the "old" image.
        // A versioned path also gives Coil a fresh cache key immediately after replacement.
        val target = File(dir, "${name}_${System.currentTimeMillis()}_${System.nanoTime()}.$extension")
        contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        Uri.fromFile(target).toString()
    }.getOrNull()
}

internal suspend fun Context.deletePersistedCustomImage(uriString: String) = withContext(Dispatchers.IO) {
    runCatching {
        if (uriString.isBlank()) return@runCatching
        val uri = Uri.parse(uriString)
        if (uri.scheme != "file") return@runCatching
        val file = File(uri.path ?: return@runCatching)
        val dir = File(filesDir, "custom_images").canonicalFile
        val target = file.canonicalFile
        if (target.path.startsWith(dir.path) && target.isFile) target.delete()
    }
}
