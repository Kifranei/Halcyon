package com.ella.music.data.lyrics

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.ella.music.data.AllFilesAccess
import com.ella.music.data.isContentAudioSource
import com.ella.music.data.isFileUriAudioSource
import com.ella.music.data.isHttpAudioSource
import com.ella.music.data.model.Song
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal sealed interface LyricsSidecarSaveResult {
    /** [fileName] is the sidecar's display name, [location] a path or document uri for logs. */
    data class Saved(val fileName: String, val location: String) : LyricsSidecarSaveResult
    /** Streamed/remote songs (WebDAV, Navidrome, NetEase online…) have no writable folder. */
    data object RemoteUnsupported : LyricsSidecarSaveResult
    /** Neither direct file access nor a persisted SAF folder grant allows writing there. */
    data object NoWriteAccess : LyricsSidecarSaveResult
}

/**
 * Writes/reads external lyric files (`<song basename>.lrc|.ttml`) in the song's own folder.
 *
 * Resolution order for a write:
 *  1. direct [File] write next to the audio file (all-files access, legacy storage, app dirs);
 *  2. a persisted, writable SAF tree grant (scan-folder / USB picker) covering that folder,
 *     via [DocumentsContract] on the ExternalStorageProvider;
 *  3. otherwise [LyricsSidecarSaveResult.NoWriteAccess]. The audio file itself is never opened.
 */
internal object LyricsSidecarStorage {
    private const val TAG = "LyricsSidecar"
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    // Generic binary type: ExternalStorageProvider keeps the display name verbatim instead of
    // appending a MIME-derived extension (e.g. "song.lrc.txt").
    private const val SIDECAR_MIME_TYPE = "application/octet-stream"
    private const val MAX_SIDECAR_BYTES = 8L * 1024L * 1024L

    private const val PREFERRED_PREFS = "lyrics_sidecar_preferred"

    /**
     * Songs whose sidecar the user saved explicitly. Their external lyrics are tried before
     * embedded ones, otherwise a song with embedded lyrics would keep showing the old text.
     */
    fun prefersSidecar(context: Context, song: Song): Boolean =
        song.path.isNotBlank() &&
            context.getSharedPreferences(PREFERRED_PREFS, Context.MODE_PRIVATE).contains(song.path.trim())

    private fun markPreferred(context: Context, song: Song) {
        context.getSharedPreferences(PREFERRED_PREFS, Context.MODE_PRIVATE).edit()
            .putLong(song.path.trim(), System.currentTimeMillis()).apply()
    }

    fun save(context: Context, song: Song, format: LyricsSidecarFormat, content: String): LyricsSidecarSaveResult =
        saveInternal(context, song, format, content).also { result ->
            if (result is LyricsSidecarSaveResult.Saved) markPreferred(context, song)
        }

    private fun saveInternal(context: Context, song: Song, format: LyricsSidecarFormat, content: String): LyricsSidecarSaveResult {
        val path = song.path.trim()
        if (path.isBlank() || path.isHttpAudioSource()) return LyricsSidecarSaveResult.RemoteUnsupported
        val bytes = content.toByteArray(Charsets.UTF_8)

        val localPath = when {
            path.isContentAudioSource() -> {
                val uri = Uri.parse(path)
                treeDocumentLocation(uri)?.let { location ->
                    val name = LyricsSidecarPaths.sidecarFileName(location.audioName, format)
                        ?: return LyricsSidecarSaveResult.NoWriteAccess
                    val written = writeViaTree(context, location.treeUri, location.parentDocumentId, name, bytes)
                    return if (written != null) {
                        LyricsSidecarSaveResult.Saved(name, written.toString())
                    } else {
                        LyricsSidecarSaveResult.NoWriteAccess
                    }
                }
                mediaStoreDataPath(context, uri) ?: return LyricsSidecarSaveResult.NoWriteAccess
            }
            path.isFileUriAudioSource() -> Uri.parse(path).path ?: return LyricsSidecarSaveResult.NoWriteAccess
            else -> path
        }

        val audioFile = File(localPath)
        val parent = audioFile.parentFile ?: return LyricsSidecarSaveResult.NoWriteAccess
        val name = LyricsSidecarPaths.sidecarFileName(audioFile.name, format)
            ?: return LyricsSidecarSaveResult.NoWriteAccess
        val target = File(parent, name)
        if (writeViaFile(target, bytes)) {
            return LyricsSidecarSaveResult.Saved(name, target.absolutePath)
        }
        val parentDocumentId = LyricsSidecarPaths.storagePathToDocumentId(parent.absolutePath)
            ?: return LyricsSidecarSaveResult.NoWriteAccess
        grantedTreesCovering(context, parentDocumentId, requireWrite = true).forEach { treeUri ->
            if (writeViaTree(context, treeUri, parentDocumentId, name, bytes) != null) {
                return LyricsSidecarSaveResult.Saved(name, target.absolutePath)
            }
        }
        return LyricsSidecarSaveResult.NoWriteAccess
    }

    /**
     * Reads an exact-basename sidecar through SAF when a plain [File] lookup cannot see it:
     * `content://` tree documents (USB folders) and, without all-files access, folders the user
     * granted through the document picker (SAF-created files are hidden from raw paths).
     */
    fun readViaDocuments(context: Context, songPath: String, preferTtml: Boolean): String? = runCatching {
        val extensions = if (preferTtml) listOf("ttml") else listOf("lrc", "elrc")
        val path = songPath.trim()
        if (path.isBlank() || path.isHttpAudioSource()) return null
        if (path.isContentAudioSource()) {
            val location = treeDocumentLocation(Uri.parse(path)) ?: return null
            val base = location.audioName.substringBeforeLast('.', location.audioName)
            return extensions.firstNotNullOfOrNull { extension ->
                readDocument(
                    context,
                    DocumentsContract.buildDocumentUriUsingTree(
                        location.treeUri,
                        LyricsSidecarPaths.childDocumentId(location.parentDocumentId, "$base.$extension")
                    )
                )
            }
        }
        if (AllFilesAccess.isGranted(context)) return null
        val localPath = if (path.isFileUriAudioSource()) {
            Uri.parse(path).path ?: return null
        } else {
            path
        }
        val audioFile = File(localPath)
        val parent = audioFile.parentFile ?: return null
        val parentDocumentId = LyricsSidecarPaths.storagePathToDocumentId(parent.absolutePath) ?: return null
        val trees = grantedTreesCovering(context, parentDocumentId, requireWrite = false)
        if (trees.isEmpty()) return null
        val base = audioFile.nameWithoutExtension
        trees.forEach { treeUri ->
            extensions.forEach { extension ->
                readDocument(
                    context,
                    DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        LyricsSidecarPaths.childDocumentId(parentDocumentId, "$base.$extension")
                    )
                )?.let { return it }
            }
        }
        null
    }.getOrNull()

    private data class TreeDocumentLocation(
        val treeUri: Uri,
        val parentDocumentId: String,
        val audioName: String
    )

    /** For `content://…externalstorage…/tree/<tree>/document/<doc>` song uris (USB/SAF scans). */
    private fun treeDocumentLocation(uri: Uri): TreeDocumentLocation? {
        if (uri.authority != EXTERNAL_STORAGE_AUTHORITY) return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val parentDocumentId = LyricsSidecarPaths.parentDocumentId(documentId) ?: return null
        val audioName = documentId.substringAfter(':').substringAfterLast('/')
        if (audioName.isBlank()) return null
        return TreeDocumentLocation(
            treeUri = DocumentsContract.buildTreeDocumentUri(EXTERNAL_STORAGE_AUTHORITY, treeId),
            parentDocumentId = parentDocumentId,
            audioName = audioName
        )
    }

    private fun mediaStoreDataPath(context: Context, uri: Uri): String? {
        if (uri.authority != MediaStore.AUTHORITY) return null
        return runCatching {
            @Suppress("DEPRECATION")
            val column = MediaStore.MediaColumns.DATA
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun grantedTreesCovering(context: Context, documentId: String, requireWrite: Boolean): List<Uri> =
        runCatching {
            context.contentResolver.persistedUriPermissions
                .asSequence()
                .filter { permission -> if (requireWrite) permission.isWritePermission else permission.isReadPermission }
                .map { it.uri }
                .filter { it.authority == EXTERNAL_STORAGE_AUTHORITY }
                .filter { uri ->
                    val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
                    treeId != null && LyricsSidecarPaths.treeCovers(treeId, documentId)
                }
                .distinctBy { it.toString() }
                .toList()
        }.getOrDefault(emptyList())

    private fun writeViaFile(target: File, bytes: ByteArray): Boolean = runCatching {
        val parent = target.parentFile
        if (parent == null || !parent.isDirectory) return false
        target.writeBytes(bytes)
        target.isFile && target.length() == bytes.size.toLong()
    }.getOrElse { error ->
        Log.d(TAG, "Direct sidecar write failed for ${target.absolutePath}: ${error.message}")
        false
    }

    /** Overwrites `<parent>/<displayName>` or creates it; returns the written document uri. */
    private fun writeViaTree(
        context: Context,
        treeUri: Uri,
        parentDocumentId: String,
        displayName: String,
        bytes: ByteArray
    ): Uri? {
        val resolver = context.contentResolver
        val existing = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                LyricsSidecarPaths.childDocumentId(parentDocumentId, displayName)
            )
        }.getOrNull()
        if (existing != null && documentExists(context, existing) && writeDocument(context, existing, bytes)) {
            return existing
        }
        val parentUri = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
        }.getOrNull() ?: return null
        val created = runCatching {
            DocumentsContract.createDocument(resolver, parentUri, SIDECAR_MIME_TYPE, displayName)
        }.onFailure { Log.d(TAG, "SAF sidecar create failed in $parentUri: ${it.message}") }
            .getOrNull() ?: return null
        // Providers de-duplicate clashing names ("x (1).lrc"); the loader only matches the exact name.
        val createdName = queryDisplayName(context, created)
        if (createdName != null && createdName != displayName) {
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            return null
        }
        return created.takeIf { writeDocument(context, it, bytes) }
    }

    private fun documentExists(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null,
            null,
            null
        )?.use { it.moveToFirst() } == true
    }.getOrDefault(false)

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    private fun writeDocument(context: Context, uri: Uri, bytes: ByteArray): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(bytes)
            output.flush()
            true
        } == true
    }.getOrElse { error ->
        Log.d(TAG, "SAF sidecar write failed for $uri: ${error.message}")
        false
    }

    private fun readDocument(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                total += read
                if (total > MAX_SIDECAR_BYTES) return null
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray().takeIf { it.isNotEmpty() }?.let(::decodeLyricBytes)
        }
    }.getOrNull()

    private fun decodeLyricBytes(bytes: ByteArray): String {
        val hasUtf8Bom = bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        val payload = if (hasUtf8Bom) bytes.copyOfRange(3, bytes.size) else bytes
        for (charsetName in listOf("UTF-8", "GB18030")) {
            try {
                return Charset.forName(charsetName).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload))
                    .toString()
            } catch (_: CharacterCodingException) {
            }
        }
        return String(payload, Charsets.UTF_8)
    }
}
