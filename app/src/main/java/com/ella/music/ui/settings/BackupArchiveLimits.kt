package com.ella.music.ui.settings

import android.content.Context
import android.net.Uri
import com.ella.music.data.copyToBoundedOrThrow
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

internal fun validateApplicationBackupFileBudgets(file: File) {
    require(file.isFile) { "Backup file does not exist" }
    require(file.length() <= MAX_BACKUP_FILE_BYTES) { "Backup file exceeds the size limit" }
    FileInputStream(file).buffered().use(::validateBackupStreamBudgets)
}

internal fun copyAndValidateApplicationBackupUri(context: Context, uri: Uri): File {
    val file = File(context.cacheDir, "backup_import_${UUID.randomUUID()}.tmp")
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyToBoundedOrThrow(output, MAX_BACKUP_FILE_BYTES) }
        } ?: error("Unable to open backup file")
        validateApplicationBackupFileBudgets(file)
        return file
    } catch (error: Throwable) {
        file.delete()
        throw error
    }
}

private fun validateBackupStreamBudgets(input: InputStream) {
    val buffered = input as? BufferedInputStream ?: BufferedInputStream(input)
    buffered.mark(4)
    val magic = ByteArray(4)
    val read = buffered.read(magic)
    buffered.reset()
    if (read < 2 || magic[0] != 'P'.code.toByte() || magic[1] != 'K'.code.toByte()) return

    var entries = 0
    var totalBytes = 0L
    ZipInputStream(buffered).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            entries++
            require(entries <= MAX_ENTRIES) { "Backup archive contains too many entries" }
            val remainingArchiveBytes = MAX_ARCHIVE_BYTES - totalBytes
            require(remainingArchiveBytes >= 0L) { "Backup archive expands beyond the size limit" }
            val entryLimit = minOf(MAX_ENTRY_BYTES, remainingArchiveBytes)
            val written = zip.copyToBoundedOrThrow(discardOutput, entryLimit)
            totalBytes += written
            zip.closeEntry()
        }
    }
}

private val discardOutput = object : OutputStream() {
    override fun write(value: Int) = Unit
    override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
}

private const val MAX_ENTRIES = 2_048
private const val MAX_ENTRY_BYTES = 64L * 1024L * 1024L
private const val MAX_ARCHIVE_BYTES = 320L * 1024L * 1024L
private const val MAX_BACKUP_FILE_BYTES = 384L * 1024L * 1024L
