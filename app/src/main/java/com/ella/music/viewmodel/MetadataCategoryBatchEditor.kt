package com.ella.music.viewmodel

import com.ella.music.data.batchTagFieldValue
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.resolveBatchTagValue
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One editable tag operation for a batch run (#725):
 * - [keep] leaves the field untouched on every song;
 * - [match] non-null applies a partial replacement inside the current value;
 * - otherwise [replacement] overwrites the field (empty clears it; `<TAG>` placeholders resolve).
 */
data class BatchFieldOp(
    val tag: String,
    val keep: Boolean = false,
    val match: String? = null,
    val replacement: String = ""
)

data class MetadataBatchEditState(
    val type: String,
    val category: String,
    val value: String,
    val total: Int,
    val processed: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val running: Boolean = true,
    val permission: WritePermissionRequiredException? = null
)

/** Lives with the ViewModel: rotating or recreating the sheet cannot repeat file writes. */
class MetadataCategoryBatchEditor(
    private val scope: CoroutineScope,
    private val write: suspend (Song, Map<String, String>) -> Result<Song>,
    private val publish: suspend (List<Song>) -> Unit
) {
    private val mutableState = MutableStateFlow<MetadataBatchEditState?>(null)
    val state = mutableState.asStateFlow()
    private var permissionReply: CompletableDeferred<Boolean>? = null
    private var permissionLaunched = false

    fun start(
        type: String,
        category: String,
        ops: List<BatchFieldOp>,
        songs: List<Song>,
        keepModifiedTime: Boolean = true
    ) {
        if (mutableState.value?.running == true) return
        val activeOps = ops.filter { !it.keep }
        if (activeOps.isEmpty()) return
        val targets = songs.distinctBy { it.playlistIdentityKey() }
        val summary = activeOps.joinToString { "${it.tag}=${it.replacement.ifBlank { it.match.orEmpty() }}" }
        mutableState.value = MetadataBatchEditState(type, category, summary, targets.size)
        scope.launch {
            val edited = mutableListOf<Song>()
            try {
                for (song in targets) {
                    val writable = song.path.startsWith("/") || song.path.startsWith("content://") || song.path.startsWith("file://")
                    if (!writable) {
                        update { it.copy(processed = it.processed + 1, skipped = it.skipped + 1) }
                        continue
                    }
                    val resolved = buildMap {
                        activeOps.forEach { op ->
                            val current = song.batchTagFieldValue(op.tag)
                            val next = when {
                                op.match != null -> if (current.contains(op.match)) current.replace(op.match, op.replacement) else return@forEach
                                else -> song.resolveBatchTagValue(op.replacement)
                            }
                            if (next != current) put(op.tag, next)
                        }
                    }
                    if (resolved.isEmpty()) {
                        update { it.copy(processed = it.processed + 1, skipped = it.skipped + 1) }
                        continue
                    }
                    // #725: restore the file timestamp so batch edits do not reshuffle
                    // dateModified-sorted lists. Only real file paths accept setLastModified.
                    val file = song.path.takeIf { it.startsWith("/") || it.startsWith("file:") }
                        ?.removePrefix("file://")?.let(::File)
                    val mtime = if (keepModifiedTime) file?.takeIf { it.exists() }?.lastModified() ?: 0L else 0L
                    var result = safelyWrite(song, resolved)
                    val permission = result.exceptionOrNull() as? WritePermissionRequiredException
                    if (permission != null) {
                        val reply = CompletableDeferred<Boolean>()
                        permissionReply = reply
                        permissionLaunched = false
                        update { it.copy(permission = permission) }
                        val allowed = reply.await()
                        permissionReply = null
                        update { it.copy(permission = null) }
                        if (allowed) result = safelyWrite(song, resolved)
                    }
                    result.getOrNull()?.let { editedSong ->
                        if (mtime > 0L) {
                            file?.setLastModified(mtime)
                            edited += editedSong.copy(dateModified = song.dateModified)
                        } else edited += editedSong
                    }
                    update { it.copy(processed = it.processed + 1,
                        succeeded = it.succeeded + if (result.isSuccess) 1 else 0,
                        failed = it.failed + if (result.isFailure) 1 else 0) }
                }
            } finally {
                try { withContext(NonCancellable) { publish(edited) } }
                finally {
                    permissionReply = null
                    update { it.copy(running = false, permission = null) }
                }
            }
        }
    }

    fun answerPermission(allowed: Boolean) { permissionReply?.complete(allowed) }
    fun takePermissionLaunch(): WritePermissionRequiredException? {
        val permission = mutableState.value?.permission ?: return null
        if (permissionLaunched) return null
        permissionLaunched = true
        return permission
    }
    fun dismiss() { if (mutableState.value?.running != true) mutableState.value = null }
    private fun update(transform: (MetadataBatchEditState) -> MetadataBatchEditState) {
        mutableState.value?.let { mutableState.value = transform(it) }
    }
    private suspend fun safelyWrite(song: Song, values: Map<String, String>): Result<Song> = try {
        write(song, values)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }
}
