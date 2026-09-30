package com.ella.music.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PinnedPreferencesTest {
    private class DelayedStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        val writes = Channel<CompletableDeferred<Unit>>(Channel.UNLIMITED)
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            val next = transform(data.value)
            val commit = CompletableDeferred<Unit>()
            writes.send(commit)
            commit.await()
            data.value = next
            next
        }
    }

    @Test
    fun pinsAreVisibleBeforeDiskCommitAndOlderWritesDoNotUndoNewerPins() = runBlocking {
        val store = DelayedStore()
        val pins = PinnedPreferences(store)
        val flow = pins.keys("album")
        assertSame(flow, pins.keys("album"))
        assertEquals(emptyList<String>(), flow.first())
        val first = launch(start = CoroutineStart.UNDISPATCHED) { pins.set("album", "A", true) }
        val firstCommit = store.writes.receive()
        assertEquals(listOf("A"), flow.first())
        assertEquals(null, store.data.value[stringPreferencesKey("pinned_album")])
        val second = launch(start = CoroutineStart.UNDISPATCHED) { pins.set("album", "B", true) }
        assertEquals(listOf("B", "A"), flow.first())
        firstCommit.complete(Unit)
        first.join()
        val secondCommit = store.writes.receive()
        assertEquals("A", store.data.value[stringPreferencesKey("pinned_album")])
        assertEquals(listOf("B", "A"), flow.first())
        secondCommit.complete(Unit)
        second.join()
        assertEquals("B\nA", store.data.value[stringPreferencesKey("pinned_album")])
        assertEquals(listOf("B", "A"), flow.first())
    }

    @Test
    fun failedWritesRollBackAndFolderUnpinIgnoresPathCasing() = runBlocking {
        val store = DelayedStore()
        val pins = PinnedPreferences(store)
        val flow = pins.keys("folder")
        flow.first()
        val pin = launch(start = CoroutineStart.UNDISPATCHED) { pins.set("folder", "/Music", true) }
        store.writes.receive().complete(Unit)
        pin.join()
        assertEquals(listOf("/Music"), flow.first())
        var failure: Throwable? = null
        val failed = launch(start = CoroutineStart.UNDISPATCHED) {
            try { pins.set("folder", "/music", false) } catch (error: IOException) { failure = error }
        }
        val failedCommit = store.writes.receive()
        assertEquals(emptyList<String>(), flow.first())
        failedCommit.completeExceptionally(IOException("Disk failure"))
        failed.join()
        assertEquals(true, failure is IOException)
        assertEquals(listOf("/Music"), flow.first())
        val unpin = launch(start = CoroutineStart.UNDISPATCHED) { pins.set("folder", "/music", false) }
        store.writes.receive().complete(Unit)
        unpin.join()
        assertEquals(emptyList<String>(), flow.first())
    }
}
