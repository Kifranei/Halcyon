package com.ella.music.viewmodel

import com.ella.music.data.CustomMetadataCategory
import com.ella.music.data.model.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import com.ella.music.data.exception.WritePermissionRequiredException
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MetadataCategoryBatchEditorTest {
    private fun song(id: Long, path: String = "/$id.flac") = Song(id, "Song $id", "Artist", "Album", 0, 1000, path, "$id.flac")

    @Test fun batchDeduplicatesSkipsRemoteTracksPublishesOnceAndDoesNotRestartWhileRunning() = runTest {
        val gate = CompletableDeferred<Unit>()
        val writes = mutableListOf<Pair<Long, String>>()
        val publications = mutableListOf<List<Song>>()
        val editor = MetadataCategoryBatchEditor(this, write = { song, values ->
            gate.await(); writes += song.id to values.keys.single()
            if (song.id == 2L) throw IllegalStateException("read only") else Result.success(song.copy(genre = values.getValue("GENRE")))
        }, publish = { publications += it })
        val targets = listOf(song(1), song(1), song(2), song(3, "smb://host/Music/3.flac"))
        editor.start("genre", "Old", listOf(BatchFieldOp("GENRE", replacement = "New")), targets)
        editor.start("year", "2025", listOf(BatchFieldOp("DATE", replacement = "2026")), listOf(song(9)))
        editor.dismiss()
        assertTrue(editor.state.value!!.running)
        gate.complete(Unit)
        runCurrent()
        val state = editor.state.value!!
        assertEquals(3, state.total)
        assertEquals(3, state.processed)
        assertEquals(1, state.succeeded)
        assertEquals(1, state.failed)
        assertEquals(1, state.skipped)
        assertFalse(state.running)
        assertEquals(listOf(1L to "GENRE", 2L to "GENRE"), writes)
        assertEquals(listOf("New"), publications.single().map { it.genre })
        editor.dismiss()
        assertNull(editor.state.value)
    }

    @Test fun customTagsUseTheirActualKeyAndPermissionRequestsResumeOnlyTheFailedSong() = runTest {
        val pending = PendingIntent.getActivity(RuntimeEnvironment.getApplication(), 100, Intent("permission"), PendingIntent.FLAG_IMMUTABLE)
        val permission = WritePermissionRequiredException(pending.intentSender)
        var calls = 0
        var writtenKey = ""
        val editor = MetadataCategoryBatchEditor(this, write = { song, values ->
            writtenKey = values.keys.single(); calls++
            if (calls == 1) Result.failure(permission) else Result.success(song)
        }, publish = {})
        editor.start(CustomMetadataCategory("Arranger", "ARRANGER").type, "Old", listOf(BatchFieldOp("ARRANGER", replacement = "New")), listOf(song(1)))
        runCurrent()
        assertEquals(permission, editor.state.value!!.permission)
        assertEquals(permission, editor.takePermissionLaunch())
        assertNull(editor.takePermissionLaunch())
        assertEquals(0, editor.state.value!!.processed)
        editor.answerPermission(true)
        runCurrent()
        assertEquals("ARRANGER", writtenKey)
        assertEquals(2, calls)
        assertEquals(1, editor.state.value!!.succeeded)
    }
}
