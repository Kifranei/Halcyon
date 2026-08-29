package com.ella.music.ui.settings

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.ella.music.data.SettingsManager.Companion.KEY_APP_WALLPAPER_URI
import com.ella.music.data.SettingsManager.Companion.KEY_GLOBAL_CJK_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_GLOBAL_WESTERN_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_HI_RES_LOGO_URI
import com.ella.music.data.SettingsManager.Companion.KEY_HOME_FEATURE_WALLPAPER_URI
import com.ella.music.data.SettingsManager.Companion.KEY_LX_SOURCE_SCRIPT
import com.ella.music.data.SettingsManager.Companion.KEY_LX_SOURCES_JSON
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_CJK_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_ORIGINAL_CJK_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_ORIGINAL_WESTERN_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_TRANSLATION_CJK_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_TRANSLATION_WESTERN_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_LYRIC_WESTERN_FONT_PATH
import com.ella.music.data.SettingsManager.Companion.KEY_PLAYER_BACKGROUND_URI
import com.ella.music.data.SettingsManager.Companion.KEY_STARTUP_POSTER_URI
import com.ella.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal const val APPLICATION_BACKUP_ZIP_MIME = "application/zip"

private const val BACKUP_JSON_ENTRY = "backup.json"
private const val PORTABLE_ASSETS_FIELD = "portableAssets"
private const val ARCHIVE_REFERENCE_PREFIX = "archive://"
private const val EXTRACTED_ASSET_FILES_FIELD = "_portableAssetFiles"
private const val EXTRACTED_ASSET_DIR_FIELD = "_portableAssetDir"
private const val MAX_BACKUP_JSON_BYTES = 16L * 1024L * 1024L
private const val MAX_PORTABLE_ASSET_BYTES = 64L * 1024L * 1024L
private const val MAX_PORTABLE_ASSET_TOTAL_BYTES = 256L * 1024L * 1024L

private val portableImageSettingKeys = setOf(
    KEY_STARTUP_POSTER_URI.name,
    KEY_APP_WALLPAPER_URI.name,
    KEY_PLAYER_BACKGROUND_URI.name,
    KEY_HOME_FEATURE_WALLPAPER_URI.name,
    KEY_HI_RES_LOGO_URI.name
)

private val portableFontSettingKeys = setOf(
    KEY_LYRIC_FONT_PATH.name,
    KEY_LYRIC_WESTERN_FONT_PATH.name,
    KEY_LYRIC_CJK_FONT_PATH.name,
    KEY_GLOBAL_WESTERN_FONT_PATH.name,
    KEY_GLOBAL_CJK_FONT_PATH.name,
    KEY_LYRIC_ORIGINAL_WESTERN_FONT_PATH.name,
    KEY_LYRIC_ORIGINAL_CJK_FONT_PATH.name,
    KEY_LYRIC_TRANSLATION_WESTERN_FONT_PATH.name,
    KEY_LYRIC_TRANSLATION_CJK_FONT_PATH.name
)

private val portableTextSettingKeys = setOf(
    KEY_LX_SOURCE_SCRIPT.name,
    KEY_LX_SOURCES_JSON.name
)

private val portableSettingKeys =
    portableImageSettingKeys + portableFontSettingKeys + portableTextSettingKeys

internal suspend fun buildApplicationBackupZipFile(
    context: Context,
    selectedTypes: Set<BackupType> = BackupType.entries.toSet(),
    librarySongs: List<Song> = emptyList()
): File = withContext(Dispatchers.IO) {
    val root = buildApplicationBackupJson(
        context = context,
        selectedTypes = selectedTypes,
        librarySongs = librarySongs,
        includeDeviceLocalAssets = true
    )
    val settings = root.optJSONObject("settings") ?: JSONObject().also {
        root.put("settings", it)
    }
    val manifest = JSONObject()
    val archiveFile = File(
        context.cacheDir,
        "halcyon_backup_${System.currentTimeMillis()}_${System.nanoTime()}.zip"
    )
    archiveFile.parentFile?.mkdirs()

    ZipOutputStream(BufferedOutputStream(FileOutputStream(archiveFile))).use { zip ->
        portableSettingKeys.forEach { key ->
            if (key.backupType() !in selectedTypes) return@forEach
            val value = settings.optString(key, "")
            if (value.isBlank()) return@forEach

            val entryName = portableAssetEntryName(context, key, value)
            val copied = when {
                key in portableImageSettingKeys || key in portableFontSettingKeys ->
                    context.copySettingFileToZip(value, zip, entryName)
                key in portableTextSettingKeys -> {
                    zip.writeEntry(entryName) { output ->
                        output.write(value.toByteArray(StandardCharsets.UTF_8))
                    }
                    true
                }
                else -> false
            }
            if (copied) {
                manifest.put(key, entryName)
                settings.put(key, "$ARCHIVE_REFERENCE_PREFIX$entryName")
            } else {
                // A path that cannot be packed must not overwrite a valid setting on the target
                // device with a source-device path during restore.
                settings.remove(key)
            }
        }

        root.put("version", 2)
        if (manifest.length() > 0) {
            root.put(PORTABLE_ASSETS_FIELD, manifest)
        } else {
            root.remove(PORTABLE_ASSETS_FIELD)
        }
        zip.writeEntry(BACKUP_JSON_ENTRY) { output ->
            output.write(root.toString(2).toByteArray(StandardCharsets.UTF_8))
        }
    }
    archiveFile
}

internal fun readApplicationBackupFile(context: Context, file: File): JSONObject {
    require(file.isFile) { "Backup file does not exist" }
    FileInputStream(file).buffered().use { input ->
        return readApplicationBackupStream(context, input)
    }
}

internal fun readApplicationBackupUri(context: Context, uri: Uri): JSONObject {
    context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
        return readApplicationBackupStream(context, input)
    } ?: error("Unable to open backup file")
}

private fun readApplicationBackupStream(context: Context, input: InputStream): JSONObject {
    val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
    buffered.mark(4)
    val magic = ByteArray(4)
    val read = buffered.read(magic)
    buffered.reset()
    return if (read >= 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()) {
        readApplicationBackupZip(context, buffered)
    } else {
        JSONObject(readUtf8Bounded(buffered, MAX_BACKUP_JSON_BYTES))
    }
}

private fun readApplicationBackupZip(context: Context, input: InputStream): JSONObject {
    val extractionDir = File(
        context.cacheDir,
        "halcyon_backup_extract_${System.currentTimeMillis()}_${System.nanoTime()}"
    ).apply { mkdirs() }
    var keepExtraction = false
    try {
        var backupJson: String? = null
        var extractedBytes = 0L
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                when {
                    name == BACKUP_JSON_ENTRY -> {
                        backupJson = readUtf8Bounded(zip, MAX_BACKUP_JSON_BYTES)
                    }
                    name.startsWith("assets/") || name.startsWith("scripts/") -> {
                        val target = safeExtractedFile(extractionDir, name)
                        target.parentFile?.mkdirs()
                        val written = target.outputStream().use { output ->
                            zip.copyToBounded(output, MAX_PORTABLE_ASSET_BYTES)
                        }
                        extractedBytes += written
                        require(extractedBytes <= MAX_PORTABLE_ASSET_TOTAL_BYTES) {
                            "Backup assets are too large"
                        }
                    }
                    else -> skipZipEntry(zip)
                }
                zip.closeEntry()
            }
        }

        val root = JSONObject(backupJson ?: error("Backup archive is missing backup.json"))
        val manifest = root.optJSONObject(PORTABLE_ASSETS_FIELD)
        if (manifest == null || manifest.length() == 0) {
            extractionDir.deleteRecursively()
            return root
        }

        val extractedFiles = JSONObject()
        val keys = manifest.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entryName = manifest.optString(key, "")
            if (!isSafePortableEntryName(entryName)) continue
            val extracted = safeExtractedFile(extractionDir, entryName)
            if (extracted.isFile && extracted.canRead() && extracted.length() > 0L) {
                extractedFiles.put(key, extracted.absolutePath)
            }
        }
        root.put(EXTRACTED_ASSET_FILES_FIELD, extractedFiles)
        root.put(EXTRACTED_ASSET_DIR_FIELD, extractionDir.absolutePath)
        keepExtraction = true
        return root
    } finally {
        if (!keepExtraction) extractionDir.deleteRecursively()
    }
}

/** Copies ZIP attachments into this installation and replaces archive references with local paths. */
internal suspend fun materializeApplicationBackupAssets(
    context: Context,
    root: JSONObject,
    selectedTypes: Set<BackupType>
) = withContext(Dispatchers.IO) {
    val extractedFiles = root.optJSONObject(EXTRACTED_ASSET_FILES_FIELD) ?: return@withContext
    val manifest = root.optJSONObject(PORTABLE_ASSETS_FIELD) ?: return@withContext
    val settings = root.optJSONObject("settings") ?: root
    val keys = manifest.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val reference = settings.optString(key, "")
        val sourcePath = extractedFiles.optString(key, "")
        val source = File(sourcePath)
        if (key.backupType() !in selectedTypes || !isSafeExtractedAsset(source)) {
            if (reference.startsWith(ARCHIVE_REFERENCE_PREFIX)) settings.remove(key)
            continue
        }

        runCatching {
            when {
                key in portableImageSettingKeys -> {
                    val targetDir = File(context.filesDir, "custom_images").apply { mkdirs() }
                    val target = uniquePortableTarget(targetDir, "backup_$key", source.extension)
                    source.copyTo(target, overwrite = true)
                    settings.put(key, Uri.fromFile(target).toString())
                }
                key in portableFontSettingKeys -> {
                    val targetDir = File(context.filesDir, "lyric_fonts").apply { mkdirs() }
                    val target = uniquePortableTarget(targetDir, "backup_$key", source.extension)
                    source.copyTo(target, overwrite = true)
                    settings.put(key, target.absolutePath)
                }
                key in portableTextSettingKeys -> {
                    settings.put(key, source.readText(Charsets.UTF_8))
                }
            }
        }.onFailure {
            settings.remove(key)
        }
    }
}

internal fun cleanupApplicationBackupAssets(context: Context, root: JSONObject) {
    val path = root.optString(EXTRACTED_ASSET_DIR_FIELD, "")
    if (path.isBlank()) return
    runCatching {
        val cacheDir = context.cacheDir.canonicalFile
        val extractionDir = File(path).canonicalFile
        val cachePrefix = cacheDir.path + File.separator
        if (extractionDir.path.startsWith(cachePrefix) &&
            extractionDir.name.startsWith("halcyon_backup_extract_")
        ) {
            extractionDir.deleteRecursively()
        }
    }
    root.remove(EXTRACTED_ASSET_FILES_FIELD)
    root.remove(EXTRACTED_ASSET_DIR_FIELD)
}

internal fun JSONObject.hasPortableBackupAssets(): Boolean =
    optJSONObject(PORTABLE_ASSETS_FIELD)?.length()?.let { it > 0 } == true

private fun Context.copySettingFileToZip(value: String, zip: ZipOutputStream, entryName: String): Boolean =
    runCatching {
        openSettingInput(value)?.use { input ->
            zip.writeEntry(entryName) { output -> input.copyTo(output) }
            true
        } ?: false
    }.getOrDefault(false)

private fun Context.openSettingInput(value: String): InputStream? {
    val uri = runCatching { Uri.parse(value) }.getOrNull()
    return when (uri?.scheme?.lowercase(Locale.ROOT)) {
        "file" -> uri.path?.let { File(it).takeIf(File::isFile)?.inputStream() }
        "content" -> contentResolver.openInputStream(uri)
        null, "" -> File(value).takeIf(File::isFile)?.inputStream()
        else -> null
    }
}

private fun portableAssetEntryName(context: Context, key: String, value: String): String {
    return when {
        key in portableImageSettingKeys ->
            "assets/images/$key.${sourceExtension(context, value, "jpg")}"
        key in portableFontSettingKeys ->
            "assets/fonts/$key.${sourceExtension(context, value, "ttf")}"
        key == KEY_LX_SOURCE_SCRIPT.name -> "scripts/lx_source_script.js"
        key == KEY_LX_SOURCES_JSON.name -> "scripts/lx_sources.json"
        else -> "assets/$key.bin"
    }
}

private fun sourceExtension(context: Context, value: String, fallback: String): String {
    val uri = runCatching { Uri.parse(value) }.getOrNull()
    val fromPath = uri?.path?.substringAfterLast('.', "")
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
    if (fromPath != null) return fromPath
    val fromMime = uri?.let { context.contentResolver.getType(it) }
        ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
    return fromMime ?: fallback
}

private fun uniquePortableTarget(dir: File, prefix: String, extension: String): File {
    val safePrefix = prefix.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val safeExtension = extension.lowercase(Locale.ROOT).takeIf {
        it.matches(Regex("[a-z0-9]{1,8}"))
    } ?: "bin"
    return File(dir, "${safePrefix}_${System.currentTimeMillis()}_${System.nanoTime()}.$safeExtension")
}

private fun safeExtractedFile(root: File, name: String): File {
    require(isSafePortableEntryName(name)) { "Unsafe backup entry" }
    val rootPath = root.canonicalFile.path + File.separator
    val target = File(root, name).canonicalFile
    require(target.path.startsWith(rootPath)) { "Unsafe backup entry" }
    return target
}

private fun isSafePortableEntryName(name: String): Boolean {
    if (name.isBlank() || name.startsWith('/') || name.contains(':')) return false
    return name.split('/').none { it.isBlank() || it == "." || it == ".." }
}

private fun isSafeExtractedAsset(file: File): Boolean =
    file.isFile && file.canRead() && file.length() in 1..MAX_PORTABLE_ASSET_BYTES

private fun readUtf8Bounded(input: InputStream, maxBytes: Long): String {
    val output = ByteArrayOutputStream()
    input.copyToBounded(output, maxBytes)
    return String(output.toByteArray(), StandardCharsets.UTF_8)
}

private fun InputStream.copyToBounded(output: OutputStream, maxBytes: Long): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) continue
        total += count
        require(total <= maxBytes) { "Backup entry is too large" }
        output.write(buffer, 0, count)
    }
    output.flush()
    return total
}

private fun skipZipEntry(input: InputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
    }
}

private fun ZipOutputStream.writeEntry(name: String, write: (OutputStream) -> Unit) {
    putNextEntry(ZipEntry(name))
    try {
        write(this)
    } finally {
        closeEntry()
    }
}
